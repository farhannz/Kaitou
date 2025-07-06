package com.farhannz.kaitou.data.models


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



sealed class OCRUIState {
    object Loading : OCRUIState()
    data class Done(val results: List<OCRResult>) : OCRUIState()
}