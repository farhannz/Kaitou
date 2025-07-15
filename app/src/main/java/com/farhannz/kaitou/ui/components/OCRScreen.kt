package com.farhannz.kaitou.ui.components

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import com.farhannz.kaitou.data.models.*
import com.farhannz.kaitou.helpers.BoundaryViterbi
import com.farhannz.kaitou.helpers.DatabaseManager
import com.farhannz.kaitou.helpers.Logger
import com.farhannz.kaitou.helpers.TokenHelper
import com.farhannz.kaitou.paddle.OCRPipeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.apache.lucene.analysis.ja.JapaneseTokenizer
import org.apache.lucene.analysis.ja.tokenattributes.*
import org.apache.lucene.analysis.tokenattributes.*
import java.io.ByteArrayOutputStream
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

@Composable
fun BottomPopup(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val swipeThreshold = with(LocalDensity.current) { 80.dp.toPx() }
    var offsetY by remember { mutableFloatStateOf(0f) }

    var internalVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        internalVisible = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        AnimatedVisibility(
            visible = internalVisible,
            enter = slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight },
                animationSpec = tween(300)
            ),
            exit = slideOutVertically(
                targetOffsetY = { fullHeight -> fullHeight },
                animationSpec = tween(200)
            ),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, offsetY.toInt()) }
                    .background(Color.White, shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (offsetY > swipeThreshold) {
                                    internalVisible = false
                                    onDismiss()
                                } else {
                                    offsetY = 0f
                                }
                            },
                            onDragCancel = { offsetY = 0f },
                            onVerticalDrag = { _, dragAmount ->
                                offsetY = (offsetY + dragAmount).coerceAtLeast(0f)
                            }
                        )
                    }
            ) {
                content()
            }
        }
    }
}

@Composable
fun DrawPolygons(
    wordsWithPolys: List<Pair<String, List<List<Float>>>>,
    screenSize: Pair<Int, Int>,
    selectedIndices: List<Int> = emptyList()
) {
    val rawPaths = remember(wordsWithPolys) {
        wordsWithPolys.map { (_, poly) ->
            poly.map { point -> Offset(point[0], point[1]) }
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val scaleX = size.width / screenSize.first.toFloat()
        val scaleY = size.height / screenSize.second.toFloat()

        rawPaths.forEachIndexed { index, poly ->
            val isSelected = selectedIndices.contains(index)
            val path = Path().apply {
                moveTo(poly[0].x * scaleX, poly[0].y * scaleY)
                poly.drop(1).forEach {
                    lineTo(it.x * scaleX, it.y * scaleY)
                }
                close()
            }
            val color = if (isSelected) Color.Blue else Color.Red
            drawPath(path, color = color.copy(alpha = 0.1f), style = Fill)
            drawPath(path, color = color, style = Stroke(width = 1.dp.toPx()))
        }
    }
}
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WordPolygonsOverlay(
    grouped: GroupedResult?,
    originalImage: Bitmap,
    onClicked: () -> Unit,
    screenSize: Pair<Int, Int>
) {
    var showPopup by remember { mutableStateOf(false) }
    var tokens by remember { mutableStateOf<List<TokenInfo>>(emptyList()) }
    var selectedWord by remember { mutableStateOf("") }
    val selectedIndices = remember { mutableStateListOf<Int>() }
    if (grouped == null) return

    val texts = remember { mutableListOf<String>() }
    val results = remember { mutableListOf<List<List<Float>>>() }

    grouped.grouped.forEachIndexed { idx, box ->
        texts.add("placeholder_$idx")
        results.add(box.first.map { listOf(it.x.toFloat(), it.y.toFloat()) })
    }

    val wordsWithPolys = texts.zip(results)

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

    val tappedIndex = remember { mutableIntStateOf(-1) }
    SubcomposeLayout { constraints ->
        val canvasPlaceable = subcompose("Canvas") {
            DrawPolygons(wordsWithPolys, screenSize, listOf(tappedIndex.value))
        }.map { it.measure(constraints) }

        val overlayPlaceable = subcompose("Overlay") {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x51000000))
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .pointerInput(wordsWithPolys, boundingBoxes) {
//                logger.DEBUG(boundingBoxes.toString())
                        detectTapGestures { offset: Offset ->
                            logger.DEBUG(offset.toString())
                            val canvasWidth = size.width
                            val canvasHeight = size.height
                            val scaleX = canvasWidth / screenSize.first.toFloat()
                            val scaleY = canvasHeight / screenSize.second.toFloat()

                            // Check if tap is inside any polygon's bounding box
                            tappedIndex.value = boundingBoxes.indexOfFirst { (_, rect) ->
                                offset.x in (rect.left * scaleX)..(rect.right * scaleX) &&
                                        offset.y in (rect.top * scaleY)..(rect.bottom * scaleY)
                            }
                            if (tappedIndex.value != -1) {
                                selectedIndices.clear()
                                logger.DEBUG("$offset - $tappedIndex")
                                selectedIndices.addAll(grouped.grouped[tappedIndex.value].second)
                                showPopup = true
                            } else {
                                if (!showPopup) onClicked()
                            }
                        }
                    }
            ) {
                if (showPopup) {
                    BottomPopup(
                        onDismiss = { showPopup = false }
                    ) {
                        var merged by remember { mutableStateOf<List<TokenInfo>?>(null) }
                        var selectedWord by remember { mutableStateOf("") }

                        LaunchedEffect(selectedIndices) {
                            merged = null // reset before loading
                            withContext(Dispatchers.Default) {
                                val texts = OCRPipeline.extractTexts(originalImage, grouped, selectedIndices.reversed())
                                selectedWord = texts.joinToString("")
                                val tokens = tokenizeWithPOS(selectedWord)
                                logger.DEBUG(selectedWord)
                                val passiveProcessed = BoundaryViterbi.preProcessPassive(tokens).let {
                                    TokenHelper().correctAuxiliaryNegative(it)
                                }
                                val result = BoundaryViterbi.segment(passiveProcessed, DatabaseManager.getCache()!!)
                                logger.DEBUG(result.joinToString("\n"))
                                merged = result
                            }
                        }

// Show loading until `merged` is ready
                        if (merged == null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        } else {
                            BottomSheetContent(merged!!, selectedWord) {
                                showPopup = false
                            }
                        }
                    }
                }
            }
        }.map { it.measure(constraints) }

        layout(constraints.maxWidth, constraints.maxHeight) {
            canvasPlaceable.forEach { it.place(0, 0) }
            overlayPlaceable.forEach { it.place(0, 0) }
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
    val results = remember { mutableStateListOf<OCRResult>()}

    val zipped = remember { mutableStateListOf<Pair<String, List<List<Float>>>>() }
    val groupedResult = remember {mutableListOf<GroupedResult>()}
    val boxes = remember {mutableListOf<DetectionResult>()}
    when (val state = ocrState) {
        is OCRUIState.ProcessingOCR -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x88000000))
                    .clickable(onClick = onClicked),
                contentAlignment = Alignment.Center
            ) {
                LaunchedEffect(ocrState) {
                    withContext(Dispatchers.Default) {
                        val dets = OCRPipeline.detectTexts(inputImage)
                        groupedResult.add(dets)
                        ocrState = OCRUIState.Done
                    }
                }
                CircularProgressIndicator()
            }
        }
        is OCRUIState.Failed -> {
            Toast.makeText(LocalContext.current,"Failed while processing OCR",Toast.LENGTH_SHORT).show()
            onClicked()
        }
        is OCRUIState.Done -> {
            WordPolygonsOverlay(groupedResult.firstOrNull(), inputImage,onClicked, Pair<Int,Int>(inputImage.width, inputImage.height))
        }
    }
}

@Preview
@Composable
fun PreviewOCRScreen() {
    OCRScreen(onClicked = {}, inputImage = createBitmap(100,100))
}