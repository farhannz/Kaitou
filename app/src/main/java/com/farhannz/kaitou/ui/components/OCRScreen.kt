package com.farhannz.kaitou.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ModifierLocalBeyondBoundsLayout
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.farhannz.kaitou.data.models.*
import com.farhannz.kaitou.bridges.OCRBridge.runOCR
import com.farhannz.kaitou.bridges.OCRBridge.initPaddle
import kotlinx.coroutines.delay
import org.apache.lucene.analysis.ja.JapaneseAnalyzer
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

    Box(
        modifier = Modifier
            .offset { IntOffset(data.bbox.x1.toInt(), data.bbox.y1.toInt()) }
            .size(width.dp, height.dp)
            .border(2.dp, Color.Red)
            .clickable {
                onClicked()
            }
    ) {
        if (isVertical(data.bbox)) {
            VerticalJapaneseText(data.word)
        }
        else {
            Text(text = data.word, color = Color.White)
        }
    }
}


@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
//fun OCRScreen(onClicked : () -> Unit, sudachiTokenizer: SudachiTokenizer) {
fun OCRScreen(onClicked : () -> Unit,) {
    val tokenizer = JapaneseTokenizer(null, false, JapaneseTokenizer.Mode.NORMAL)
    // Collect the dictionary popup state from the ViewModel
//    val dictState by sudachiTokenizer.dictUiState.collectAsState()

    var ocrState by remember { mutableStateOf<OCRUIState>(OCRUIState.Loading) }
    var selectedResult by remember { mutableStateOf<OCRResult?>(null) }
    var results = arrayListOf<OCRResult>(
        OCRResult("こんにちは", BoundingBox(20f, 150f, 100f, 180f)),
        OCRResult("日本語", BoundingBox(20f, 300f, 70f, 330f)),
        OCRResult("明日は映画を見に行きます", BoundingBox(300f,60f,330f,465f)),
        OCRResult("東京で友達とラーメンを食べた", BoundingBox(460f,50f,490f,465f)),
    )

    // Your mock data
//    val results = remember {
//        listOf(
//            OCRResult("こんにちは", BoundingBox(20f, 150f, 100f, 180f)),
//            OCRResult("日本語", BoundingBox(20f, 300f, 70f, 330f))
//        )
//    }

    LaunchedEffect(Unit) {
//        val results = runOCRAsync(screenshotBitmap)
        Log.i("ORC C++ Bridge", runOCR())

        delay(1000)
        ocrState = OCRUIState.Done(results)
    }

    when (val state = ocrState) {
        is OCRUIState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x88000000)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        is OCRUIState.Done -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xAA000000)) // semi-transparent
                    .clickable {
                        onClicked()
                    }
            ) {
                Text(text="Test", color = Color.Red)
                for (res in results) {
                    val tokens = tokenizeWithPOS(res.word)
                    val grouped = groupTokens(tokens)
                    BoundingBoxOverlay(res, onClicked = {
                        Log.i("OCRScreen", res.word)
                        grouped.forEach { group ->
                            Log.i("Tokenizer", "${group.joinToString(" ") {it.surface}} - ${group.joinToString(" ") { it.baseForm.toString() }}")
                        }
                    })
                }
                selectedResult?.let(
                    { PopUpDict() }
                )
            }
        }
    }



//    Scaffold(modifier = Modifier.fillMaxSize().background(Color.Transparent)) {
//    }
}

@Preview
@Composable
fun PreviewOCRScreen() {
//    var sudachiTokenizer : SudachiTokenizer = viewModel()
//    OCRScreen(onClicked = {}, sudachiTokenizer)
    OCRScreen(onClicked = {})
}