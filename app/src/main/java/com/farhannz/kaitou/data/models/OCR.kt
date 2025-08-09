package com.farhannz.kaitou.data.models

import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.opencv.core.Point


data class BoundingBox(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float
)

data class OCRResult(
    val word: String,
    val bbox: BoundingBox
)


@Serializable
data class PpOcrResponse(
    @SerialName("rec_texts") val texts: List<String>,
    @SerialName("rec_polys") val boxes: List<List<List<Float>>>
    // You can add other fields like rec_scores if needed
)

data class TokenInfo(
    val surface: String,   // raw form, for bbox
    val baseForm: String?,  // dictionary form, for lookup
    val partOfSpeech: String,
    val reading: String = "",
    val inflectionType: String = "",
    val inflectionForm: String = "",
    val metadata: Map<String, Any> = emptyMap()
) {
    fun katakanaToHiragana(katakana: String): String {
        return katakana.map { ch ->
            if (ch in 'ァ'..'ン') (ch.code - 0x60).toChar() else ch
        }.joinToString("")
    }
}

sealed class OCRUIState {
    object ProcessingOCR : OCRUIState()
    object NoDetections : OCRUIState()
    object Failed : OCRUIState()
    object Done : OCRUIState()
}


//Detection Result = [
//  [Point(x,y), Point(x,y), Point(x,y), Point(x,y)],
//  [Point(x,y), Point(x,y), Point(x,y), Point(x,y)],
//  [Point(x,y), Point(x,y), Point(x,y), Point(x,y)],
//  [Point(x,y), Point(x,y), Point(x,y), Point(x,y)]
// ]

data class GroupedResult(
    val detections: DetectionResult,
    val grouped: List<Pair<List<Point>, List<Int>>>
)

data class DetectionResult(val boxes: List<List<Point>>, val scores: List<Double>)