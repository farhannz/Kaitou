package com.farhannz.kaitou.ui.components.utils

import android.graphics.Bitmap
import com.farhannz.kaitou.domain.RawImage
import org.opencv.core.CvType
import org.opencv.core.Mat


fun Bitmap.toRawImage(): RawImage {
    val width = this.width
    val height = this.height
    val pixels = IntArray(width * height)
    this.getPixels(pixels, 0, width, 0, 0, width, height)

    val bytes = ByteArray(width * height * 3)
    for (i in pixels.indices) {
        val color = pixels[i]
        bytes[i * 3] = ((color shr 16) and 0xFF).toByte()
        bytes[i * 3 + 1] = ((color shr 8) and 0xFF).toByte()
        bytes[i * 3 + 2] = (color and 0xFF).toByte()
    }

    return RawImage(bytes, width, height)
}

fun RawImage.toBitmap(): Bitmap {
    val pixels = IntArray(width * height)

    when (channels) {
        3 -> { // RGB
            for (i in 0 until width * height) {
                val r = bytes[i * 3].toInt() and 0xFF
                val g = bytes[i * 3 + 1].toInt() and 0xFF
                val b = bytes[i * 3 + 2].toInt() and 0xFF
                pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        4 -> { // RGBA
            for (i in 0 until width * height) {
                val r = bytes[i * 4].toInt() and 0xFF
                val g = bytes[i * 4 + 1].toInt() and 0xFF
                val b = bytes[i * 4 + 2].toInt() and 0xFF
                val a = bytes[i * 4 + 3].toInt() and 0xFF
                pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        else -> throw IllegalArgumentException("Unsupported number of channels: $channels")
    }

    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}


fun RawImage.toMat(): Mat {
    require(channels in 1..4) { "Unsupported channel count: $channels" }

    val matType = when (channels) {
        1 -> CvType.CV_8UC1
        2 -> CvType.CV_8UC2
        3 -> CvType.CV_8UC3
        4 -> CvType.CV_8UC4
        else -> throw IllegalArgumentException("Unsupported number of channels: $channels")
    }
    val mat = Mat(
        this.height,
        this.width,
        matType
    )
    mat.put(0, 0, this.bytes)
    return mat
}