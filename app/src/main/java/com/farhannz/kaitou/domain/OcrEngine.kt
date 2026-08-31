package com.farhannz.kaitou.domain

interface OcrEngine {
    suspend fun infer(image: RawImage): OcrResult
}

interface TextRecognizer {
    suspend fun recognize(image: RawImage, boxes: List<List<Point>>, selectedIndices: List<Int>): List<RecognizedText>
}

data class RecognizedText(
    val text: String,
    val box: List<Point>
)

data class Point(
    val x: Float,
    val y: Float
)

data class DetectionResult(
    val boxes: List<List<Point>>,  // [ [ (x, y), (x, y), ... ], ... ]
    val scores: List<Float>
)

data class Group(
    val region: List<Point>,         // The grouped region polygon
    val memberBoxIndices: List<Int>  // Indices into DetectionResult.boxes
)

data class GroupedResult(
    val detections: DetectionResult,
    val grouped: List<Group>
)

data class RawImage(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
    val channels: Int = 3
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RawImage

        if (width != other.width) return false
        if (height != other.height) return false
        if (channels != other.channels) return false
        if (!bytes.contentEquals(other.bytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + channels
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}

sealed class OcrResult {
    data class Detection(val det: GroupedResult) : OcrResult()
    data class Recognition(val texts: List<String>) : OcrResult()
    data class Error(val message: String, val exception: Throwable? = null) : OcrResult()
}