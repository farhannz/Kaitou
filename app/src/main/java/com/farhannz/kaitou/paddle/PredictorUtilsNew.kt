package com.farhannz.kaitou.paddle

import android.graphics.Bitmap
import android.os.Environment
import com.baidu.paddle.lite.Tensor
import com.farhannz.kaitou.helpers.Logger
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import org.opencv.core.CvType
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.RotatedRect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import java.io.File
import java.io.FileOutputStream
import kotlin.math.hypot
import clipper2.Clipper
import clipper2.core.Path64
import clipper2.core.Paths64
import clipper2.core.Point64
import clipper2.offset.ClipperOffset
import clipper2.offset.EndType
import clipper2.offset.JoinType
import io.reactivex.annotations.Experimental
import org.opencv.core.Core
import org.opencv.core.Rect
import org.opencv.utils.Converters
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sqrt


val LOG_TAG = "PredictorUtilsNew"
val logger = Logger(LOG_TAG)
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