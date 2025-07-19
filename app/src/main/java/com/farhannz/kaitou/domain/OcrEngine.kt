package com.farhannz.kaitou.domain

interface OcrEngine {
    suspend fun infer(image: RawImage): OcrResult
}

data class DetectionResult(
    val boxes: List<List<Pair<Float, Float>>>,  // [ [ (x, y), (x, y), ... ], ... ]
    val scores: List<Float>
)


data class GroupedResult(
    val detections: DetectionResult,
    val grouped: List<Pair<List<Pair<Float, Float>>, List<Int>>>
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
    data class Detection(val result: GroupedResult) : OcrResult()
    data class Recognition(val texts: List<String>) : OcrResult()
    data class Error(val message: String, val exception: Throwable? = null) : OcrResult()
}