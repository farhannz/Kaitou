package com.farhannz.kaitou.paddle

import android.graphics.Bitmap
import android.graphics.Color
import com.farhannz.kaitou.helpers.Logger
import androidx.core.graphics.scale
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.sqrt

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


fun cropFromBox(image: Mat, box: List<Point>): Mat {
    fun norm(p1: Point, p2: Point): Double {
        val dx = p1.x - p2.x
        val dy = p1.y - p2.y
        return sqrt(dx * dx + dy * dy)
    }

    if (box.size != 4) throw IllegalArgumentException("Box must have 4 points")

    val widthA = norm(box[2], box[3])
    val widthB = norm(box[1], box[0])
    val maxWidth = max(widthA, widthB).toInt()

    val heightA = norm(box[1], box[2])
    val heightB = norm(box[0], box[3])
    val maxHeight = max(heightA, heightB).toInt()

    val dst = listOf(
        Point(0.0, 0.0),
        Point(maxWidth - 1.0, 0.0),
        Point(maxWidth - 1.0, maxHeight - 1.0),
        Point(0.0, maxHeight - 1.0)
    )

    val srcMat = MatOfPoint2f(*box.toTypedArray())
    val dstMat = MatOfPoint2f(*dst.toTypedArray())

    val transform = Imgproc.getPerspectiveTransform(srcMat, dstMat)
    val warped = Mat()
    Imgproc.warpPerspective(image, warped, transform, CvSize(maxWidth.toDouble(), maxHeight.toDouble()))

    return warped
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