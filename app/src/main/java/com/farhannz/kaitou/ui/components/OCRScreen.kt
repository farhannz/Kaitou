package com.farhannz.kaitou.ui.components

import android.R
import android.annotation.SuppressLint
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
import com.farhannz.kaitou.data.models.*


data class BoundingBox (
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float
)

data class OCRResult(
    val word: String,
    val bbox : BoundingBox
)

@Composable
fun BoundingBoxOverlay(data: OCRResult, onClicked: () -> Unit) {

    val width = data.bbox.x2 - data.bbox.x1
    val height = data.bbox.y2 - data.bbox.y1

    Box(
        modifier = Modifier
            .offset { IntOffset(data.bbox.x1.toInt(), data.bbox.y1.toInt()) }
            .size(width.dp, height.dp)
            .border(2.dp, Color.Red)
            .clickable{
                onClicked()
            }
    ) {
        Text(text = data.word, color = Color.White)
    }
}


@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun OCRScreen(onClicked : () -> Unit) {

    var selectedResult by remember { mutableStateOf<OCRResult?>(null) }

    var results = arrayListOf<OCRResult>(
        OCRResult("こんにちは", BoundingBox(20f, 150f, 100f, 180f)),
        OCRResult("日本語", BoundingBox(20f, 300f, 70f, 330f))
    )

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
            BoundingBoxOverlay(res, onClicked = {
                selectedResult = res
            })
        }
        selectedResult?.let(
            { PopUpDict() }
        )
    }


//    Scaffold(modifier = Modifier.fillMaxSize().background(Color.Transparent)) {
//    }
}

@Preview
@Composable
fun PreviewOCRScreen() {
    OCRScreen(onClicked = {})
}