package com.farhannz.kaitou.presentation.components

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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import com.farhannz.kaitou.OcrEngineProvider
import com.farhannz.kaitou.data.models.*
import com.farhannz.kaitou.domain.OcrResult
import com.farhannz.kaitou.helpers.Logger
import com.farhannz.kaitou.helpers.TokenHelper
import com.farhannz.kaitou.impl.JMDict
import com.farhannz.kaitou.presentation.ocr.PopupViewModel
import com.farhannz.kaitou.presentation.utils.ImageTransform
import com.farhannz.kaitou.presentation.utils.toCurrentImpl
import com.farhannz.kaitou.presentation.utils.toRawImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.farhannz.kaitou.domain.Point as DomainPoint

const val LOG_TAG = "UI.Components"
val logger = Logger(LOG_TAG)

@Composable
fun BottomPopup(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val swipeThreshold = with(LocalDensity.current) { 80.dp.toPx() }
    var offsetY by remember { mutableFloatStateOf(0f) }

    var internalVisible by remember { mutableStateOf(false) }
    val useDarkTheme = isSystemInDarkTheme()
    val colors =
        if (useDarkTheme) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(
            LocalContext.current
        )
    LaunchedEffect(Unit) {
        internalVisible = true
    }
    MaterialTheme(colorScheme = colors) {
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
                        .background(
                            MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                        )
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
    val imageSize = Size(screenSize.first.toFloat(), screenSize.second.toFloat())
    Canvas(
        modifier = Modifier.fillMaxSize()
    ) {
        // No safeDrawing padding here: the screenshot is a full uncropped
        // display mirror and this canvas is edge-to-edge, so the transform is
        // the only mapping between OCR space and screen space. Padding here
        // would shift boxes by the inset amount (device dependent).
        val transform = ImageTransform.fit(imageSize, size)

        rawPaths.forEachIndexed { index, poly ->
            val isSelected = selectedIndices.contains(index)
            val path = Path().apply {
                val first = transform.toScreen(poly[0])
                moveTo(first.x, first.y)
                poly.drop(1).forEach {
                    val p = transform.toScreen(it)
                    lineTo(p.x, p.y)
                }
                close()
            }
            val color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF448888)
            val width = if (isSelected) 2.dp.toPx() else 1.dp.toPx()
            drawPath(path, color = color.copy(alpha = 0.1f), style = Fill)
            drawPath(path, color = color, style = Stroke(width = width))
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
            DrawPolygons(wordsWithPolys, screenSize, listOf(tappedIndex.intValue))
        }.map { it.measure(constraints) }

        val overlayPlaceable = subcompose("Overlay") {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x10000000))
                    .pointerInput(wordsWithPolys, boundingBoxes) {
                        detectTapGestures { offset: Offset ->
                            logger.DEBUG(offset.toString())
                            // Same transform as DrawPolygons: convert the tap
                            // into image space and hit-test against the raw
                            // rects, so taps always land on the drawn boxes.
                            val transform = ImageTransform.fit(
                                Size(screenSize.first.toFloat(), screenSize.second.toFloat()),
                                Size(size.width.toFloat(), size.height.toFloat())
                            )
                            val imagePoint = transform.toImage(offset)

                            tappedIndex.intValue = boundingBoxes.indexOfFirst { (_, rect) ->
                                imagePoint.x in rect.left.toFloat()..rect.right.toFloat() &&
                                        imagePoint.y in rect.top.toFloat()..rect.bottom.toFloat()
                            }
                            if (tappedIndex.intValue != -1 && !showPopup) {
                                selectedIndices.clear()
                                logger.DEBUG("$offset - $tappedIndex")
                                selectedIndices.addAll(grouped.grouped[tappedIndex.intValue].second)
                                showPopup = true
                            } else {
                                selectedIndices.clear()
                                if (!showPopup) onClicked()
                                else showPopup = false
                            }
                        }
                    }
            ) {
                if (showPopup) {
                    val useDarkTheme = isSystemInDarkTheme()
                    val colors =
                        if (useDarkTheme) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(
                            LocalContext.current
                        )
                    val coroutineScope = rememberCoroutineScope()
                    val popupViewModel = remember { PopupViewModel(coroutineScope) }

                    MaterialTheme(colorScheme = colors) {
                        BottomPopup(
                            onDismiss = {
                                popupViewModel.clearStates()
                                showPopup = false
                            }
                        ) {
                            var merged by remember { mutableStateOf<List<TokenInfo>?>(null) }
                            var selectedWord by remember { mutableStateOf("") }
                            var selectedEmbedding by remember { mutableStateOf(FloatArray(128)) }

                            LaunchedEffect(selectedIndices.joinToString("")) {
                                merged = null
                                popupViewModel.clearStates()
                                withContext(Dispatchers.Default) {
                                    val engine = OcrEngineProvider.awaitReady().textRecognizer
                                    val raw = originalImage.toRawImage()
                                    val domainBoxes = grouped.detections.boxes.map { box ->
                                        box.map {
                                            DomainPoint(it.x.toFloat(), it.y.toFloat())
                                        }
                                    }
                                    selectedWord =
                                        engine.recognize(raw, domainBoxes, selectedIndices)
                                            .joinToString("") { it.text }
                                    logger.DEBUG(selectedWord)
                                    val tokens = TokenHelper.tokenizeWithPOS(selectedWord)
                                    logger.DEBUG("Raw Tokens : ${tokens.size}")
                                    JMDict.clearCache()
                                    merged = tokens
                                }
                            }
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
                                BottomSheetContent(
                                    merged = merged!!,
                                    selectedWord = selectedWord,
                                    selectedEmbedding = selectedEmbedding,
                                    viewModel = popupViewModel
                                ) {
                                    popupViewModel.clearStates()
                                    showPopup = false
                                }
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

@OptIn(ExperimentalComposeUiApi::class, ExperimentalComposeApi::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun OCRScreen(onClicked: () -> Unit, inputImage: Bitmap) {
    var ocrState by remember { mutableStateOf<OCRUIState>(OCRUIState.ProcessingOCR) }
    val groupedResult = remember { mutableListOf<GroupedResult>() }
    val engineState by OcrEngineProvider.state.collectAsState()
    when (ocrState) {
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
                        val engine = OcrEngineProvider.awaitReady().detectionEngine
                        val dets = engine.infer(inputImage.toRawImage())
                        when (dets) {
                            is OcrResult.Detection -> {
                                groupedResult.add(dets.det.toCurrentImpl())
                                ocrState = OCRUIState.Done
                            }

                            is OcrResult.Error -> {
                                logger.ERROR(dets.message)
                                ocrState = OCRUIState.NoDetections
                            }

                            is OcrResult.Recognition -> {
                                logger.ERROR("Unexpected results, Expected: ${dets::class.simpleName} - Found: RecognitionResult")
                                ocrState = OCRUIState.NoDetections
                            }
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = if (engineState is OcrEngineProvider.EngineState.Ready) {
                            "Scanning for text…"
                        } else {
                            "Warming up OCR engine…"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }
            }
        }

        is OCRUIState.Failed -> {
            Toast.makeText(LocalContext.current, "Failed while processing OCR", Toast.LENGTH_SHORT)
                .show()
            onClicked()
        }

        is OCRUIState.NoDetections -> {
            Toast.makeText(
                LocalContext.current,
                "No texts detected, try zooming the image",
                Toast.LENGTH_SHORT
            ).show()
            onClicked()
        }

        is OCRUIState.Done -> {
            WordPolygonsOverlay(
                groupedResult.firstOrNull(),
                inputImage,
                onClicked,
                Pair(inputImage.width, inputImage.height)
            )
        }
    }
}