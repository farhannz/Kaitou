package com.farhannz.kaitou.paddle

import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import androidx.compose.ui.tooling.data.Group
import androidx.core.graphics.createBitmap
import com.farhannz.kaitou.data.models.DetectionResult
import com.farhannz.kaitou.data.models.GroupedResult
import com.farhannz.kaitou.helpers.Logger
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.File
import java.util.Date
import kotlin.math.ceil
import kotlin.math.max

object OCRPipeline {
    private val LOG_TAG = OCRPipeline::class.simpleName
    private val logger = Logger(LOG_TAG!!)
    private lateinit var detection: DetectionPredictor // Text Detection Model
    private lateinit var recognizer : RecognitionPredictor // Text Recognizer Model
    private val batchSize = 3

    private fun validateMat(input :Mat) : Mat{
        var validMat = when (input.channels()) {
            1 -> input  // CV_8UC1
            3 -> input  // CV_8UC3
            4 -> input  // CV_8UC4
            else -> {
                // Convert to 3-channel BGR as default
                val converted = Mat()
                Imgproc.cvtColor(input, converted, Imgproc.COLOR_RGBA2BGR) // or CV_32F to CV_8U conversion if needed
                converted
            }
        }

// If the depth is not CV_8U, convert it
        if (validMat.depth() != CvType.CV_8U) {
            val temp = Mat()
            validMat.convertTo(temp, CvType.CV_8U, 255.0)  // assuming float input range [0,1]
            validMat.release()
            validMat = temp
        }
        return validMat
    }
    fun initialize(context: Context) {
        detection = DetectionPredictor()
        recognizer = RecognitionPredictor()
        detection.initialize(context, "paddle", "ppocrv5_det.nb")
        recognizer.initialize(context, "paddle", "ppocrv5_rec.nb")
    }
    fun extractTexts(inputImage: Bitmap): Pair<GroupedResult, List<String>> {
        val e2e = Date()
        val inputMat = Mat()
        Utils.bitmapToMat(inputImage, inputMat)
        val det_result = detection.runInference(inputImage)
        val totalBatches = ceil((det_result.detections.boxes.size.toDouble() / batchSize.toDouble())).toInt()
        var batch_idx = mutableListOf<Int>()
        for (i in 0 until totalBatches - 1 ) {
            val start = i * batchSize
            logger.DEBUG("Batch : $i")
            for (j in start until max(start + batchSize, det_result.detections.boxes.size - 1)) {
                logger.DEBUG("current file index : $j")
            }
        }
        val textResults = mutableListOf<String>()
        det_result.detections.boxes.forEachIndexed { index, it ->
            val cropped = cropFromBox(inputMat,it)
            val isVertical = (cropped.height().toFloat() / cropped.width().toFloat()) > 1.25f
            if (isVertical) {
                Core.rotate(cropped, cropped, Core.ROTATE_90_COUNTERCLOCKWISE)
            }

//            val bgrCropped = Mat()
//            Imgproc.cvtColor(cropped,bgrCropped,Imgproc.COLOR_RGB2BGR)
//            val filePath = File(Environment.getExternalStorageDirectory(), "Download/PPOCR/cropped_$index.png").absolutePath
//            Imgcodecs.imwrite(filePath,bgrCropped)
            val bm = createBitmap(cropped.width(), cropped.height(),Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(cropped,bm)
            val text = recognizer.runInference(bm)
            textResults.add(text)
        }

        logger.INFO("[stat] Total end to end ${Date().time  - e2e.time}")
        return Pair(det_result, textResults)
    }
}