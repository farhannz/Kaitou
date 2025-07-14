package com.farhannz.kaitou.paddle

import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.util.Size
import android.widget.ImageView
import com.baidu.paddle.lite.MobileConfig
import com.baidu.paddle.lite.PaddlePredictor
import java.io.File
import java.io.FileOutputStream
import java.util.*
import androidx.core.graphics.scale
import com.baidu.paddle.lite.Tensor
import com.farhannz.kaitou.data.models.DetectionResult
import com.farhannz.kaitou.helpers.Logger
import com.google.android.material.animation.ImageMatrixProperty
import okhttp3.internal.wait
import org.opencv.BuildConfig
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Core.norm
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.imgcodecs.Imgcodecs
//import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.sqrt

object PredictorManager {
    private val LOG_TAG = PredictorManager::class.simpleName
    private val logger = Logger(LOG_TAG!!)
    private lateinit var detection: DetectionPredictor // Text Detection Model
    private lateinit var recognizer : BasePredictor // Text Recognizer Model

    fun initialize(context: Context) {
        detection = DetectionPredictor()
        detection.initialize(context, "paddle", "ppocrv5_det.nb")
    }

    fun runDetection(inputImage : Bitmap): DetectionResult {
        return detection.runInference(inputImage)
    }
}

abstract class BasePredictor {
    open lateinit var modelName : String
    open lateinit var folderPath : String
    open lateinit var predictor : PaddlePredictor
    open lateinit var config : MobileConfig
    init {
        System.loadLibrary("paddle_lite_jni")
    }
    open fun initialize(context: Context, dirPath: String, fileName: String) {
        // Add this before using any Paddle Lite functions
        folderPath = dirPath
        modelName = fileName
        val absolutePath = copyAssetToCache(context, folderPath, modelName)
        config = MobileConfig().apply {
            modelFromFile = absolutePath
            threads = 1
        }
        predictor = PaddlePredictor.createPaddlePredictor(config)
    }
    fun copyAssetToCache(context: Context, assetFolder: String = "", assetName: String): String {
        val assetSubPath = "${assetFolder}/$assetName" // use relative asset path
        val outDir = File(context.cacheDir, "paddle")
        if (!outDir.exists()) outDir.mkdirs()

        val outFile = File(outDir, assetName)
        if (!outFile.exists()) {
            context.assets.open(assetSubPath).use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
        return outFile.absolutePath
    }
}


class RecognitionPredictor : BasePredictor() {
    private val LOG_TAG = DetectionPredictor::class.simpleName
    private val logger = Logger(LOG_TAG!!)

    private val input_shape = longArrayOf(3,48,320)
    private var ratioHW: FloatArray = floatArrayOf(1f, 1f)

    fun runInference(inputImage : Bitmap) {
//        NTRTLabelEncode
//        Resize image to 3,48,320
        val success = predictor.run()
        if (!success) return
//        CTCLabelDecode
        return
    }

    fun preprocess(bitmap: Bitmap) {
        //        Resize image to 3,48,320
        val (resized, ratios) = resizeToMultipleOf32(bitmap, 320)
    }

    fun postprocess(preds: Tensor) {

    }
}


// Reimplementation of c++ from
// https://github.com/PaddlePaddle/Paddle-Lite-Demo/blob/develop/ocr/android/app/cxx/ppocr_demo/app/src/main/cpp/det_process.cc

class DetectionPredictor : BasePredictor() {
    private val LOG_TAG = DetectionPredictor::class.simpleName
    private val logger = Logger(LOG_TAG!!)

    private val input_shape = longArrayOf(3,960,960)
    private val postprocessor = DBPostProcess(boxThresh = 0.6, thresh = 0.25, unclipRatio = 1.25)
    private var ratioHW: FloatArray = floatArrayOf(1f, 1f)
    fun runInference(inputImage : Bitmap): DetectionResult {
        val end2end = Date()
        logger.INFO("Running inference")
        var start = Date()
        val (preprocessed, resized) = preprocess(inputImage, input_shape.maxOrNull()?.toInt() ?:0)
        var end = Date()
        var inferenceTime = (end.time - start.time)
        logger.INFO("[stat] Preprocessing time $inferenceTime ms");


        val inputTensor = predictor.getInput(0)
        inputTensor.resize(longArrayOf(1, 3, resized.width.toLong(), resized.height.toLong()))
        val inputShape = inputTensor.shape()  // Returns LongArray
        logger.DEBUG("Input shape: ${inputShape.joinToString(",")}")

        val requiredSize = inputShape.fold(1L, Long::times).toInt()
        if (preprocessed.size != requiredSize) {
            logger.ERROR("Data size mismatch! Expected $requiredSize, got ${preprocessed.size}")
            throw IllegalStateException("Data size mismatch")
        }
        try {
            if (preprocessed.isEmpty()) throw IllegalArgumentException("Empty input data")
            inputTensor.setData(preprocessed)
        } catch (e: Throwable) {
            logger.ERROR("Tensor data setting failed: ${e.stackTraceToString()}")
            throw e
        }

        start = Date()
        val success = predictor.run()
        if (!success) {
            logger.ERROR("Failed while doing an inference")
            throw Throwable("Failed while doing an inference")
        }
        end = Date()
        inferenceTime = (end.time - start.time)
        logger.INFO("[stat] Inference time $inferenceTime ms");

        val detConfig = mapOf(
            "det_db_thresh" to 0.3
        )
        start = Date()
        val postprocessed = postprocess(inputImage, detConfig,true)
        end = Date()
        inferenceTime = (end.time - start.time)
        logger.INFO("[stat] Postprocessing time $inferenceTime ms");
        logger.INFO("[stat] End to end time ${Date().time - end2end.time} ms");

//        ONLY FOR DEBUGGING
//        Uncomment this to visualize the cropped polygons result
//        if (BuildConfig.DEBUG) {
//            postprocessed.boxes.forEachIndexed { index,  box ->
//                val mat = Mat()
//                Utils.bitmapToMat(inputImage, mat)
//                val cropped = cropFromBox(mat, box, true)
//                val isVertical = (cropped.height().toFloat() / cropped.width().toFloat()) > 1.5f
//                if (isVertical) {
//                    Core.rotate(cropped, cropped, Core.ROTATE_90_COUNTERCLOCKWISE)
//                }
//                val filePath = File(Environment.getExternalStorageDirectory(), "Download/PPOCR/cropped_$index.png").absolutePath
//                Imgcodecs.imwrite(filePath, cropped)
//            }
//        }
        return postprocessed
    }

    fun preprocess(bitmap: Bitmap, maxSideLen: Int): Pair<FloatArray, Size> {
        val (resized, ratios) = resizeToMultipleOf32(bitmap, maxSideLen)
//        val file = File(Environment.getExternalStorageDirectory(), "Download/ocr_input_preview.png")
//        saveBitmapToFileDirectly(resized,file.absolutePath)
        ratioHW = ratios

        // Convert to float array with normalization
        val floatArray = bitmapToFloatArray(resized, normalize = true)


        return Pair(floatArray, Size(resized.width, resized.height))
    }

    fun postprocess(
        originalBitmap: Bitmap,
        config: Map<String, Double>,
        useDilate: Boolean = false
    ): DetectionResult {
        val outputTensor = predictor.getOutput(0)
        val width = outputTensor.shape()[2]
        val height = outputTensor.shape()[3]
        val outputMat =  Mat(height.toInt(), width.toInt(), CvType.CV_32F).apply {
            put(0,0, outputTensor.floatData)
        }
        logger.DEBUG("outputTensor : (HxW) $height x $width")
        logger.DEBUG("outputMat : (HxW) ${outputMat.height()} x ${outputMat.width()}")

        val result = postprocessor.process(
            outputMat,
            useDilate,
            doubleArrayOf
                (originalBitmap.height.toDouble(),
                originalBitmap.width.toDouble(),
                ratioHW[0].toDouble(),
                ratioHW[1].toDouble())
        )

        logger.DEBUG("Post process finished")
        logger.DEBUG("Results : $result.size")
        return result
    }
}

private fun DetectionPredictor.cropFromBox(image: Mat, box: List<Point>, bool: Boolean): Mat {
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
    Imgproc.warpPerspective(image, warped, transform, org.opencv.core.Size(maxWidth.toDouble(), maxHeight.toDouble()))

    return warped
}
