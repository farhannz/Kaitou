package com.farhannz.kaitou.paddle

import android.graphics.Bitmap
import android.graphics.Color
import com.farhannz.kaitou.helpers.Logger
import androidx.core.graphics.scale
import java.io.File
import java.io.FileOutputStream


private val LOG_TAG = "PredictorUtilsNew"
private val logger = Logger(LOG_TAG)
fun resizeToMultipleOf32(bitmap: Bitmap, maxSizeLen: Int): Pair<Bitmap, FloatArray> {
    val (w, h) = bitmap.width to bitmap.height
    var ratio = 1f
    val maxWh = maxOf(w, h)

    if (maxWh > maxSizeLen) {
        ratio = if (h > w) maxSizeLen.toFloat() / h else maxSizeLen.toFloat() / w
    }

    var resizeH = (h * ratio).toInt()
    var resizeW = (w * ratio).toInt()

    resizeH = when {
        resizeH % 32 == 0 -> resizeH
        resizeH / 32 < 1 + 1e-5 -> 32
        else -> (resizeH / 32) * 32
    }

    resizeW = when {
        resizeW % 32 == 0 -> resizeW
        resizeW / 32 < 1 + 1e-5 -> 32
        else -> (resizeW / 32) * 32
    }

    val resized = bitmap.scale(resizeW, resizeH)
    val ratioHW = floatArrayOf(
        resizeH.toFloat() / h,
        resizeW.toFloat() / w
    )
    return Pair(resized, ratioHW)
}

fun bitmapToFloatArray(bitmap: Bitmap,
                       normalize: Boolean,
                       mean: FloatArray = floatArrayOf(0.485f, 0.456f, 0.406f),
                       std: FloatArray = floatArrayOf(0.229f, 0.224f, 0.225f)
): FloatArray {
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

    return FloatArray(3 * bitmap.width * bitmap.height).apply {
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = Color.red(pixel) / 255f
            val g = Color.green(pixel) / 255f
            val b = Color.blue(pixel) / 255f

            if (normalize) {
                this[i] = ((r - mean[0]) / std[0])
                this[i + bitmap.width * bitmap.height] = ((g - mean[1]) / std[1])
                this[i + 2 * bitmap.width * bitmap.height] = ((b - mean[2]) / std[2])
            } else {
                this[i] = r
                this[i + bitmap.width * bitmap.height] = g
                this[i + 2 * bitmap.width * bitmap.height] = b
            }
        }
    }
}
fun saveBitmapToFileDirectly(bitmap: Bitmap, path: String): Boolean {
    return try {
        val file = File(path)
        file.parentFile?.mkdirs() // Create directories if needed
        val outputStream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        outputStream.flush()
        outputStream.close()
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}