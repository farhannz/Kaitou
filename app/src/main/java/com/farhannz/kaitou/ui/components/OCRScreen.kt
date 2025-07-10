package com.farhannz.kaitou.ui.components

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Context.WINDOW_SERVICE
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import com.farhannz.kaitou.data.models.*
import com.farhannz.kaitou.helpers.Logger
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
import okhttp3.internal.wait
import org.apache.lucene.analysis.ja.JapaneseTokenizer
import org.apache.lucene.analysis.ja.tokenattributes.*
import org.apache.lucene.analysis.tokenattributes.*
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.StringReader

const val LOG_TAG = "OCRScreen"
val logger = Logger(LOG_TAG)

data class TokenInfo(
    val surface: String,   // raw form, for bbox
    val baseForm: String?,  // dictionary form, for lookup
    val partOfSpeech: String
)

fun isVertical(box: BoundingBox): Boolean {
    val width = box.x2 - box.x1
    val height = box.y2 - box.y1
    return height > width
}

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


fun groupTokens(tokens: List<TokenInfo>): List<List<TokenInfo>> {
    val groups = mutableListOf<MutableList<TokenInfo>>()
    var currentGroup = mutableListOf<TokenInfo>()

    fun flush() {
        if (currentGroup.isNotEmpty()) {
            groups.add(currentGroup)
            currentGroup = mutableListOf()
        }
    }

    for (token in tokens) {
        when {
            token.partOfSpeech.startsWith("助詞") || token.partOfSpeech.startsWith("記号") -> {
                flush()
                groups.add(mutableListOf(token)) // isolate particle
            }

            else -> {
                currentGroup.add(token)
            }
        }
    }

    flush()
    return groups
}



@Composable
fun VerticalJapaneseText(text: String, color: Color = Color.White) {
    Column {
        text.forEach { char ->
            Text(
                text = char.toString(),
                color = color,
                fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 1.dp)
            )
        }
    }
}
@Composable
fun BoundingBoxOverlay(data: OCRResult, onClicked: () -> Unit) {

    val width = data.bbox.x2 - data.bbox.x1
    val height = data.bbox.y2 - data.bbox.y1
    // This overlay takes an absolute BoundingBox and places a clickable border at that exact location.
    Box(
        modifier = Modifier
            .offset { IntOffset(data.bbox.x1.toInt(), data.bbox.y1.toInt()) }
            .size(width.dp, height.dp)
            .border(2.dp, Color.Red)
            .clickable { onClicked() }
    )
//    Box(
//        modifier = Modifier
//            .offset { IntOffset(data.bbox.x1.toInt(), data.bbox.y1.toInt()) }
//            .size(width.dp, height.dp)
//            .border(2.dp, Color.Red)
//            .clickable {
//                onClicked()
//            }
//    ) {
////        if (isVertical(data.bbox)) {
//            VerticalJapaneseText(data.word)
////        }
////        else {
////            Text(text = data.word, color = Color.White)
////        }
//    }
}

@Composable
fun getExactScreenSize(): Size {
    val context = LocalContext.current
    return remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = (context.getSystemService(WINDOW_SERVICE) as WindowManager).maximumWindowMetrics
            Size(
                windowMetrics.bounds.width(),
                windowMetrics.bounds.height()
            )
        } else {
            val displayMetrics = context.resources.displayMetrics
            Size(displayMetrics.widthPixels, displayMetrics.heightPixels)
        }
    }
}
@Composable
fun getAvailableScreenSize(): DpSize {
    val windowInsets = WindowInsets.systemBars
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val displayCutout = WindowInsets.displayCutout.asPaddingValues()

    return with(density) {
        DpSize(
            width = (LocalConfiguration.current.screenWidthDp.dp),
            height = (LocalConfiguration.current.screenHeightDp.dp -
                    windowInsets.getTop(density).dp -
                    windowInsets.getBottom(density).dp)
        )
    }
}

@Composable
fun PolygonOverlay(
    points: List<List<Float>>,
    onClicked: () -> Unit,
    screenSize: Pair<Int, Int>  // Original screenshot dimensions
) {
    if (points.isEmpty()) return

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClicked() })
            }
    ) {
        // Get the actual drawing area size (already safe area-padded)
        val canvasWidth = size.width
        val canvasHeight = size.height

        // Calculate scale factors
        val scaleX = canvasWidth / screenSize.first.toFloat()
        val scaleY = canvasHeight / screenSize.second.toFloat()

        // Create path from original points
        val path = Path().apply {
            val first = points[0]
            moveTo(first[0] * scaleX, first[1] * scaleY)
            for (i in 1 until points.size) {
                val point = points[i]
                lineTo(point[0] * scaleX, point[1] * scaleY)
            }
            close()
        }

        // Draw the polygon
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordPolygonsOverlay(
    wordsWithPolys: List<Pair<String, List<List<Float>>>>,
    onClicked: () -> Unit,
    screenSize: Pair<Int, Int>
) {
    var showPopup by remember { mutableStateOf(false) }
    var tokens by remember { mutableStateOf<List<TokenInfo>>(emptyList()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x51000000))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .clickable { onClicked() }
    ) {
        // Draw all polygons directly in the safe area
        for ((word, poly) in wordsWithPolys) {
            PolygonOverlay(
                screenSize = screenSize,
                points = poly,
                onClicked = {
                    tokens = tokenizeWithPOS(word)
                    showPopup = true
                }
            )
        }

        if (showPopup) {
            Surface(
                modifier = Modifier
                    .clickable { showPopup = false }
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .widthIn(max = 280.dp)
                ) {
                    tokens.forEach { token ->
                        PopUpDict(token.baseForm ?: token.surface)
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
//    client.newCall(request).enqueue(object:Callback {
//        override fun onFailure(call: Call, e: IOException) {
//            logger.ERROR("Failed: ${e.message}")
//        }
//
//        override fun onResponse(call: Call, response: Response) {
//            val responseText = response.body?.string()
//            logger.DEBUG("Success: $responseText")
//        }
//    })
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

    val context = LocalContext.current
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
    val scope = rememberCoroutineScope()
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
//                    delay(500) // Simulate loading
//                    val ocrString = MockResult().result()
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
//                    var jsonIgnoreUnknown = Json {ignoreUnknownKeys = true}
//                    val response : PpOcrResponse = jsonIgnoreUnknown.decodeFromString<PpOcrResponse>(ocrString)
//                    zipped.addAll(response.texts.zip(response.boxes))
//
//                    ocrState = OCRUIState.Done(results)
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
//            for (pair in zipped) {
//                val text = pair.first
//                val boxes = pair.second
//                Log.i("PPOcrResponse", "${text} - ${boxes}")
//            }
//            Box(
//                modifier = Modifier
//                    .fillMaxSize()
//                    .background(Color(0xAA000000))
//                    .clickable { onClicked() }
//            ) {
//                state.results.forEach { sentenceResult ->
//                    val tokens = tokenizeWithPOS(sentenceResult.word)
//                    val totalWidth = sentenceResult.bbox.x2 - sentenceResult.bbox.x1
//                    val totalHeight = sentenceResult.bbox.y2 - sentenceResult.bbox.y1
//
//                    val isVertical = totalHeight > totalWidth
//                    BoundingBoxOverlay(OCRResult(sentenceResult.word, sentenceResult.bbox)) {
//                        Log.i("OCRScreen", sentenceResult.word)
//                    }
//                    if (sentenceResult.word.isEmpty()) return@forEach // Skip empty results
//
////                    if (isVertical) {
////                        // --- Vertical Text Logic ---
////                        val avgHeightPerChar = totalHeight / sentenceResult.word.length
////                        var currentY = sentenceResult.bbox.y1
////
////                        tokens.forEach { token ->
////                            val estimatedTokenHeight = token.surface.length * avgHeightPerChar
////                            val tokenBBox = BoundingBox(
////                                x1 = sentenceResult.bbox.x1,
////                                x2 = sentenceResult.bbox.x2,
////                                y1 = currentY,
////                                y2 = currentY + estimatedTokenHeight
////                            )
////
////                            BoundingBoxOverlay(OCRResult(token.surface, tokenBBox)) {
////                                Log.i("OCRScreen", "Clicked token: ${token.surface}, Base: ${token.baseForm}")
////                            }
////
////                            // ✅ CORRECT: Increment by the height of the token we just placed
////                            currentY += estimatedTokenHeight
////                        }
////
////                    } else {
////                        // --- Horizontal Text Logic ---
////                        val avgWidthPerChar = totalWidth / sentenceResult.word.length
////                        var currentX = sentenceResult.bbox.x1
////
////                        tokens.forEach { token ->
////                            val estimatedTokenWidth = token.surface.length * avgWidthPerChar
////                            val tokenBBox = BoundingBox(
////                                x1 = currentX,
////                                x2 = currentX + estimatedTokenWidth,
////                                y1 = sentenceResult.bbox.y1,
////                                y2 = sentenceResult.bbox.y2
////                            )
////
////                            BoundingBoxOverlay(OCRResult(token.surface, tokenBBox)) {
////                                Log.i("OCRScreen", "Clicked token: ${token.surface}, Base: ${token.baseForm}")
////                            }
////
////                            // ✅ CORRECT: Increment by the width of the token we just placed
////                            currentX += estimatedTokenWidth
////                        }
////                    }
//                }
//            }
        }
    }
}

@Preview
@Composable
fun PreviewOCRScreen() {
//    var sudachiTokenizer : SudachiTokenizer = viewModel()
//    OCRScreen(onClicked = {}, sudachiTokenizer)
    OCRScreen(onClicked = {}, inputImage = createBitmap(100,100))
}