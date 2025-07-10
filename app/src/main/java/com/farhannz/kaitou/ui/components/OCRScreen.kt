package com.farhannz.kaitou.ui.components

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.provider.MediaStore
import android.util.Log
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import androidx.core.graphics.createBitmap
import com.farhannz.kaitou.data.models.*
import com.farhannz.kaitou.helpers.Logger
import dev.shreyaspatil.capturable.controller.rememberCaptureController
import kotlinx.coroutines.delay
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
fun rememberWindowSafeArea(): PaddingValues {
    val insets = WindowInsets.systemBars
    return PaddingValues(
        start = insets.getLeft(
            LocalDensity.current,
            layoutDirection = LayoutDirection.Ltr
        ).dp,
        top = insets.getTop(LocalDensity.current).dp,
        end = insets.getRight(
            LocalDensity.current,
            layoutDirection = LayoutDirection.Ltr
        ).dp,
        bottom = insets.getBottom(LocalDensity.current).dp
    )
}

@Composable
fun PolygonOverlay(
    points: List<List<Float>>, // your polygon from rec_polys
    onClicked: () -> Unit,
    tokens: List<TokenInfo>
) {
    if (points.isEmpty()) return

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenSize = DpSize(configuration.screenWidthDp.dp,configuration.screenHeightDp.dp)

    val offsetPoints = points.map {
        with (density) {
            Offset(
                it[0] * screenSize.width.toPx(),
                it[1] * screenSize.height.toPx()
            )
        }
    }

    // Calculate the bounding box to position the canvas efficiently
    val minX = offsetPoints.minOf { it.x }
    val minY = offsetPoints.minOf { it.y }
    val maxX = offsetPoints.maxOf { it.x }
    val maxY = offsetPoints.maxOf { it.y }


    val width = (maxX - minX).toInt()
    val height = (maxY - minY).toInt()

    val xDp = with(density) { width.toDp() }
    val yDp = with(density) { height.toDp() }

    Canvas(
        modifier = Modifier
            .offset { IntOffset(minX.toInt(), minY.toInt()) }
            .size(xDp, yDp)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClicked() })
            }
    ) {
        val path = Path().apply {
            moveTo(offsetPoints[0].x - minX, offsetPoints[0].y - minY)
            for (i in 1 until offsetPoints.size) {
                lineTo(offsetPoints[i].x - minX, offsetPoints[i].y - minY)
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
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

@Composable
fun WordPolygonsOverlay(
    wordsWithPolys: List<Pair<String, List<List<Float>>>>,
    onClicked: () -> Unit
) {
    var showPopup by remember {mutableStateOf(false)}
    var tokens by remember { mutableStateOf<List<TokenInfo>>(emptyList())}
    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color(0xAA000000))
        .clickable { onClicked() }) {
        if (!showPopup) {
            for ((word, poly) in wordsWithPolys) {
                PolygonOverlay(
                    tokens = tokens,
                    points = poly,
                    onClicked = {
                        tokens = tokenizeWithPOS(word)
                        showPopup = true
                        logger.DEBUG("Clicked $word")
                        for (token in tokens) {
                            logger.INFO("${token.surface} - ${token.baseForm}")
                        }
                        // Do something with the word
                    }
                )
            }
        }

        if (showPopup) {
            logger.DEBUG("Tokens sizes : ${tokens.size}")
            Surface (modifier=Modifier.clickable(onClick = {showPopup = false})) {
                Column (modifier= Modifier.verticalScroll(rememberScrollState())) {
                    tokens.forEach {
                        logger.DEBUG("${it.surface} - ${it.baseForm}")
                            PopUpDict()
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
        .url(" http://10.0.2.2:8000/ocr")
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
                    delay(500) // Simulate loading
                    val ocrString = MockResult().result()
                    sendBitmapToServer(inputImage, object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            logger.ERROR("Failed: ${e.message}")
                        }

                        override fun onResponse(call: Call, response: Response) {
                            val responseText = response.body?.string()
                            logger.DEBUG(responseText!!)
                            var jsonIgnoreUnknown = Json {ignoreUnknownKeys = true}
                            val response : PpOcrResponse = jsonIgnoreUnknown.decodeFromString<PpOcrResponse>(responseText!!)
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
        is OCRUIState.Done -> {
            WordPolygonsOverlay(zipped, onClicked)
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