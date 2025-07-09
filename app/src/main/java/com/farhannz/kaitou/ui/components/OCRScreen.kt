package com.farhannz.kaitou.ui.components

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.projection.MediaProjectionManager
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import com.farhannz.kaitou.data.models.*
import dev.shreyaspatil.capturable.controller.rememberCaptureController
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import org.apache.lucene.analysis.ja.JapaneseTokenizer
import org.apache.lucene.analysis.ja.tokenattributes.*
import org.apache.lucene.analysis.tokenattributes.*
import java.io.StringReader


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
fun PolygonOverlay(
    points: List<List<Int>>, // your polygon from rec_polys
    onClicked: () -> Unit
) {
    if (points.isEmpty()) return

    val scaleX = 1080.toFloat() / 738.toFloat()
    val scaleY = 2340.toFloat() / 1600.toFloat()

    val scaledPoints = points.map { point ->
        Offset(
            point[0].toFloat() * scaleX,
            point[1].toFloat() * scaleY
        )
    }

//    val offsetPoints = points.map { Offset(it[0].toFloat(), it[1].toFloat()) }

    // Calculate the bounding box to position the canvas efficiently
    val minX = scaledPoints.minOf { it.x }
    val minY = scaledPoints.minOf { it.y }
    val maxX = scaledPoints.maxOf { it.x }
    val maxY = scaledPoints.maxOf { it.y }


    val width = (maxX - minX).toInt()
    val height = (maxY - minY).toInt()

    val density = LocalDensity.current
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
            moveTo(scaledPoints[0].x - minX, scaledPoints[0].y - minY)
            for (i in 1 until scaledPoints.size) {
                lineTo(scaledPoints[i].x - minX, scaledPoints[i].y - minY)
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
    wordsWithPolys: List<Pair<String, List<List<Int>>>>,
    onClicked: () -> Unit
) {
    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color(0xAA000000))
        .clickable { onClicked() }) {
        for ((word, poly) in wordsWithPolys) {
            PolygonOverlay(
                points = poly,
                onClicked = {
                    Log.d("Overlay", "Clicked: $word")
                    val tokens = tokenizeWithPOS(word)
                    for (token in tokens) {
                        Log.i("Overlay", "${token.surface} - ${token.baseForm}")
                    }
                    // Do something with the word
                }
            )
        }
    }
}


fun saveImageToGallery(context: Context, bitmap: Bitmap, filename: String?) {
    val values = ContentValues()
    values.put(MediaStore.Images.Media.DISPLAY_NAME, filename)
    values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg") // or "image/png"

    val resolver: ContentResolver = context.getContentResolver()
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
    val zipped = remember { mutableStateListOf<Pair<String, List<List<Int>>>>() }
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
                    var jsonIgnoreUnknown = Json {ignoreUnknownKeys = true}
                    val response : PpOcrResponse = jsonIgnoreUnknown.decodeFromString<PpOcrResponse>(ocrString)
                    zipped.addAll(response.texts.zip(response.boxes))

                    ocrState = OCRUIState.Done(results)
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