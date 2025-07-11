package com.farhannz.kaitou.ui.components

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import com.farhannz.kaitou.data.models.*
import com.farhannz.kaitou.helpers.DatabaseManager
import com.farhannz.kaitou.helpers.Logger
import com.farhannz.kaitou.helpers.TokenManager
import dev.shreyaspatil.capturable.controller.rememberCaptureController
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.apache.lucene.analysis.ja.JapaneseTokenizer
import org.apache.lucene.analysis.ja.tokenattributes.*
import org.apache.lucene.analysis.tokenattributes.*
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.StringReader

const val LOG_TAG = "OCRScreen"
val logger = Logger(LOG_TAG)

fun tokenizeWithPOS(text: String): List<TokenInfo> {
    val tokenizer = JapaneseTokenizer(null, false, JapaneseTokenizer.Mode.NORMAL)
    tokenizer.setReader(StringReader(text))
    tokenizer.reset()

    val surfaceAttr = tokenizer.getAttribute(CharTermAttribute::class.java)
    val baseAttr = tokenizer.getAttribute(BaseFormAttribute::class.java)
    val posAttr = tokenizer.getAttribute(PartOfSpeechAttribute::class.java)

    val result = mutableListOf<TokenInfo>()

    while (tokenizer.incrementToken()) {
        result.add(
            TokenInfo(
                surface = surfaceAttr.toString(),
                baseForm = baseAttr?.baseForm ?: surfaceAttr.toString(),
                partOfSpeech = posAttr.partOfSpeech ?: "未知"
            )
        )
    }

    tokenizer.end()
    tokenizer.close()

    return result
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordPolygonsOverlay(
    wordsWithPolys: List<Pair<String, List<List<Float>>>>,
    onClicked: () -> Unit,
    screenSize: Pair<Int, Int>
) {
    var showPopup by remember { mutableStateOf(false) }
    var tokens by remember { mutableStateOf<List<TokenInfo>>(emptyList()) }
    var selectedWord by remember { mutableStateOf("") }

    // Precompute bounding boxes for hit testing
    val boundingBoxes: List<Pair<String, Rect>> = remember(wordsWithPolys, screenSize) {
        wordsWithPolys.map { (word, poly) ->
            val minX = poly.minOf { it[0] }
            val minY = poly.minOf { it[1] }
            val maxX = poly.maxOf { it[0] }
            val maxY = poly.maxOf { it[1] }
            word to Rect(minX.toInt(), minY.toInt(), maxX.toInt(), maxY.toInt())
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x51000000))
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(wordsWithPolys, boundingBoxes) {
                    logger.DEBUG(boundingBoxes.toString())
                    detectTapGestures { offset: Offset ->
                        logger.DEBUG(offset.toString())
                        val canvasWidth = size.width
                        val canvasHeight = size.height
                        val scaleX = canvasWidth / screenSize.first.toFloat()
                        val scaleY = canvasHeight / screenSize.second.toFloat()

                        // Check if tap is inside any polygon's bounding box
                        val tappedIndex = boundingBoxes.indexOfFirst { (_, rect) ->
                            offset.x in (rect.left * scaleX)..(rect.right * scaleX) &&
                                    offset.y in (rect.top * scaleY)..(rect.bottom * scaleY)
                        }
                        if (tappedIndex != -1) {
                            logger.DEBUG("$offset - $tappedIndex")
                            // Verify exact polygon hit
                            val (word, poly) = wordsWithPolys[tappedIndex]
                            selectedWord = word
                            logger.DEBUG("$word, $poly")
                            tokens = tokenizeWithPOS(word)
                            showPopup = true
                        } else {
                            if (!showPopup) onClicked()
                        }
                    }
                }
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val scaleX = canvasWidth / screenSize.first.toFloat()
            val scaleY = canvasHeight / screenSize.second.toFloat()

            // Draw all polygons
            wordsWithPolys.forEach { (_, poly) ->
                val path = Path().apply {
                    moveTo(poly[0][0] * scaleX, poly[0][1] * scaleY)
                    poly.drop(1).forEach { point ->
                        lineTo(point[0] * scaleX, point[1] * scaleY)
                    }
                    close()
                }

                drawPath(
                    path = path,
                    color = Color.Red.copy(alpha = 0.1f),
                    style = Fill
                )
                drawPath(
                    path = path,
                    color = Color.Red,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }

        if (showPopup) {
            Surface(
                modifier = Modifier
                    .clickable { showPopup = false }
                    .background(Color.Transparent)
                    .fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .background(Color.Transparent)
                        .matchParentSize()
                ) {
                    Text(
                        text = selectedWord,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(16.dp)
                    )
                    logger.DEBUG(tokens.toString())
                    val merged = TokenManager().mergeWithDictionary(tokens, DatabaseManager.getCache())
                    logger.DEBUG("Merged : $merged")
                    logger.DEBUG("Merged length ${merged.size}")
                    merged.forEach {
                        PopUpDict(it)
                    }
                }
            }
        }
    }
}
fun bitmapToByteArray(bitmap: Bitmap): ByteArray {
    val stream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
    return stream.toByteArray()
}
fun sendBitmapToServer(bitmap: Bitmap, callback: Callback) {
    val client = OkHttpClient()
    val mediaType = "image/jpeg".toMediaTypeOrNull()
    val imageBytes = bitmapToByteArray(bitmap)

    val requestBody = MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart(
            "file", "image.jpg",
            imageBytes.toRequestBody(mediaType)
        )
        .build()

    val request = Request.Builder()
        .url(" http://192.168.101.6:8000/ocr")
        .post(requestBody)
        .build()
    client.newCall(request).enqueue(callback)
}



fun saveImageToGallery(context: Context, bitmap: Bitmap, filename: String?) {
    val values = ContentValues()
    values.put(MediaStore.Images.Media.DISPLAY_NAME, filename)
    values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg") // or "image/png"

    val resolver: ContentResolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

    if (uri != null) {
        try {
            resolver.openOutputStream(uri).use { fos ->
                if (fos != null) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos) // Adjust quality as needed
                    Toast.makeText(context, "Image saved to gallery!", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error saving image", Toast.LENGTH_SHORT).show()
        }
    } else {
        Toast.makeText(context, "Failed to create new MediaStore entry", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalComposeApi::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun OCRScreen(onClicked: () -> Unit, inputImage : Bitmap) {
    var ocrState by remember { mutableStateOf<OCRUIState>(OCRUIState.ProcessingOCR) }

    // Mock data for demonstration
    val results = remember {
        mutableStateListOf<OCRResult>()
//        listOf(
//            OCRResult("こんにちは", BoundingBox(20f, 150f, 100f, 180f)),
//            OCRResult("日本語", BoundingBox(20f, 300f, 70f, 330f)),
//            OCRResult("明日は映画を見に行きます", BoundingBox(300f, 60f, 330f, 465f)),
//            OCRResult("東京で友達とラーメンを食べた", BoundingBox(460f, 50f, 490f, 465f)),
//            OCRResult("「よくある話」と言われたけれど", BoundingBox(600f, 500f, 690f, 930f))
//
//        )
    }

    val captureController = rememberCaptureController()
    val zipped = remember { mutableStateListOf<Pair<String, List<List<Float>>>>() }
    when (val state = ocrState) {
        is OCRUIState.ProcessingOCR -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x88000000)),
                contentAlignment = Alignment.Center
            ) {
                LaunchedEffect(ocrState) {
                    sendBitmapToServer(inputImage, object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            logger.ERROR("Failed: ${e.message}")
                            ocrState = OCRUIState.Failed
                        }

                        override fun onResponse(call: Call, response: Response) {
                            val responseText = response.body?.string()
                            logger.DEBUG(responseText!!)
                            val jsonIgnoreUnknown = Json {ignoreUnknownKeys = true}
                            val response : PpOcrResponse = jsonIgnoreUnknown.decodeFromString<PpOcrResponse>(responseText)
                            zipped.addAll(response.texts.zip(response.boxes))
                            ocrState = OCRUIState.Done(results)
                        }

                    })
                }
                CircularProgressIndicator()
            }
        }
        is OCRUIState.Failed -> {
            Toast.makeText(LocalContext.current,"Failed while processing OCR",Toast.LENGTH_SHORT).show()
            onClicked()
        }
        is OCRUIState.Done -> {
            WordPolygonsOverlay(zipped, onClicked, Pair<Int,Int>(inputImage.width, inputImage.height))
        }
    }
}

@Preview
@Composable
fun PreviewOCRScreen() {
    OCRScreen(onClicked = {}, inputImage = createBitmap(100,100))
}