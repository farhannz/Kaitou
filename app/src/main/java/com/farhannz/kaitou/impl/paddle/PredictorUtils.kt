package com.farhannz.kaitou.impl.paddle

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.farhannz.kaitou.helpers.Logger
import androidx.core.graphics.scale
import com.baidu.paddle.lite.Tensor
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import androidx.core.graphics.createBitmap

private val LOG_TAG = "PredictorUtilsNew"
private val logger = Logger(LOG_TAG)


object PaddleHelper {
    init {
        System.loadLibrary("paddle_helper")
    }

    @JvmStatic
    external fun getTensorBufferAddress(tensorAddr: Long, length: Int): Long

    @JvmStatic
    external fun copyBufferToAddress(buffer: FloatBuffer, nativePtr: Long, length: Int)
}

fun Tensor.safeNativeHandle(): Long {
    return try {
        val field = this::class.java.getDeclaredField("nativePointer")
        field.isAccessible = true
        field.getLong(this)
    } catch (e: Exception) {
        throw IllegalStateException("Unable to access native pointer", e)
    }
}

private fun feedTensor(tensor: Tensor, mat: Mat, inputShape: List<Long>, buffer: FloatBuffer) {
    require(mat.rows() == inputShape[1].toInt() && mat.cols() == inputShape[2].toInt())
    buffer.clear()
    val tmp = FloatArray(3)
    for (h in 0 until mat.rows()) {
        for (w in 0 until mat.cols()) {
            mat.get(h, w, tmp)
            buffer.put(tmp)
        }
    }
    buffer.flip()
    PaddleHelper.copyBufferToAddress(
        buffer,
        tensor.safeNativeHandle(),
        ((1 * inputShape[0] * inputShape[1] * inputShape[2]).toInt())
    )
}

fun letterboxBitmap(
    bitmap: Bitmap,
    targetWidth: Int = 960,
    targetHeight: Int = 960,
    padColor: Int = Color.WHITE
): Pair<Bitmap, FloatArray> {
    val scale = min(
        targetWidth.toFloat() / bitmap.width,
        targetHeight.toFloat() / bitmap.height
    )

    val newW = (bitmap.width * scale).toInt()
    val newH = (bitmap.height * scale).toInt()

    val resized = bitmap.scale(newW, newH)
    val padded = createBitmap(targetWidth, targetHeight)

    val canvas = Canvas(padded)
    canvas.drawColor(padColor)
    canvas.drawBitmap(resized, ((targetWidth - newW) / 2f), ((targetHeight - newH) / 2f), null)

    val padX = (targetWidth - newW) / 2f
    val padY = (targetHeight - newH) / 2f

    // Scale + pad info, useful for restoring box coords later
    return padded to floatArrayOf(scale, padX, padY)
}

fun resizeBitmapToModelInput(
    bitmap: Bitmap,
    maxSizeLen: Int = 960
): Pair<Bitmap, FloatArray> {
    val w = bitmap.width
    val h = bitmap.height
    val maxWh = maxOf(w, h)

    val scaleRatio = if (maxWh > maxSizeLen) maxSizeLen.toFloat() / maxWh else 1f

    var resizedW = (w * scaleRatio).toInt().coerceAtLeast(32)
    var resizedH = (h * scaleRatio).toInt().coerceAtLeast(32)

    resizedW = (resizedW / 32) * 32
    resizedH = (resizedH / 32) * 32

    val resizedBitmap = bitmap.scale(resizedW, resizedH)

    val ratioH = resizedH.toFloat() / h
    val ratioW = resizedW.toFloat() / w

    return Pair(resizedBitmap, floatArrayOf(ratioH, ratioW))
}


fun norm(p1: Point, p2: Point): Double {
    val dx = p1.x - p2.x
    val dy = p1.y - p2.y
    return sqrt(dx * dx + dy * dy)
}

fun cropFromBox(image: Mat, box: List<Point>): Mat {

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
    srcMat.release()
    dstMat.release()
    transform.release()
    return warped
}

fun bitmapToFloatArray(
    bitmap: Bitmap,
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