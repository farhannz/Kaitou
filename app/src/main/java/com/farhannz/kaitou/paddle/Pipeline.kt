package com.farhannz.kaitou.paddle

import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import androidx.core.graphics.createBitmap
import com.farhannz.kaitou.data.models.GroupedResult
import com.farhannz.kaitou.helpers.Logger
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.File
import java.util.*

object OCRPipeline {
    private val LOG_TAG = OCRPipeline::class.simpleName
    private val logger = Logger(LOG_TAG!!)
    private lateinit var detection: DetectionPredictor // Text Detection Model
    private lateinit var recognizer: RecognitionPredictor // Text Recognizer Model
    private val batchSize = 1

    private fun validateMat(input: Mat): Mat {
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
//        detection.initialize(context, "paddle", "ppocrv5_det.nb")
        recognizer.initialize(context, "paddle", "ppocrv5_rec.nb")
    }

    fun detectTexts(inputImage: Bitmap): GroupedResult {
        return detection.runInference(inputImage)
    }

    fun extractTexts(inputImage: Bitmap, groupedResult: GroupedResult, selectedIndices: List<Int>): List<String> {
        val inputMat = Mat()
        try {
            Utils.bitmapToMat(inputImage, inputMat)
            val tolerance = 20  // Tune depending on character width

            val boxes = selectedIndices
                .map { groupedResult.detections.boxes[it] }
                .groupBy { box -> (box.minOf { it.x } / tolerance).toInt() } // group by column
                .toSortedMap(reverseOrder()) // right-to-left columns
                .flatMap { (_, columnBoxes) ->
                    columnBoxes.sortedBy { it.minOf { p -> p.y } } // top-to-bottom in column
                }

            val textResults = mutableListOf<String>()
            for (batchStart in boxes.indices step batchSize) {
                val batchEnd = minOf(batchStart + batchSize, boxes.size)
                val batchBoxes = boxes.subList(batchStart, batchEnd)
                // Prepare batch of cropped images
                val croppedImages = batchBoxes.mapIndexed { index, box ->
                    val cropped = cropFromBox(inputMat, box)
                    val isVertical = (cropped.height().toFloat() / cropped.width().toFloat()) > 1.25f
                    if (isVertical) {
                        Core.rotate(cropped, cropped, Core.ROTATE_90_COUNTERCLOCKWISE)
                    }
                    val bm = createBitmap(cropped.width(), cropped.height(), Bitmap.Config.ARGB_8888)
                    Utils.matToBitmap(cropped, bm)
//                    val file = File(Environment.getExternalStorageDirectory(), "Pictures/PPOCR/cropped_$index.png")
//                    logger.DEBUG(file.absolutePath)
//                    saveBitmapToFileDirectly(bm, file.absolutePath)
                    bm
                }
                val batchTexts = recognizer.runBatchInference(croppedImages)
                textResults.addAll(batchTexts)

                logger.DEBUG("Processed batch ${batchStart / batchSize + 1}: ${batchEnd - batchStart} images")
            }
            return textResults
        } finally {
            inputMat.release()
        }
    }

    fun endToEndPipeline(inputImage: Bitmap): Pair<GroupedResult, List<String>> {
        val e2e = Date()
        val inputMat = Mat()
        Utils.bitmapToMat(inputImage, inputMat)
        var start = Date()
        val detResult = detection.runInference(inputImage)
        logger.INFO("[stat] Detection time ${Date().time - start.time}")
        val textResults = mutableListOf<String>()
        start = Date()
// Process detections in batches
        for (batchStart in detResult.detections.boxes.indices step batchSize) {
            val batchEnd = minOf(batchStart + batchSize, detResult.detections.boxes.size)
            val batchBoxes = detResult.detections.boxes.subList(batchStart, batchEnd)

            // Prepare batch of cropped images
            val croppedImages = batchBoxes.map { box ->
                val cropped = cropFromBox(inputMat, box)
                val isVertical = (cropped.height().toFloat() / cropped.width().toFloat()) > 1.25f
                if (isVertical) {
                    Core.rotate(cropped, cropped, Core.ROTATE_90_COUNTERCLOCKWISE)
                }

                val bm = createBitmap(cropped.width(), cropped.height(), Bitmap.Config.ARGB_8888)
                Utils.matToBitmap(cropped, bm)
                bm
            }

            // Run batch inference
            val batchTexts = recognizer.runBatchInference(croppedImages)
            textResults.addAll(batchTexts)

            logger.DEBUG("Processed batch ${batchStart / batchSize + 1}: ${batchEnd - batchStart} images")
        }

        logger.INFO("[stat] Recognition time ${Date().time - start.time}")

        logger.INFO("[stat] Total end to end ${Date().time - e2e.time}")
        return Pair(detResult, textResults)
    }
}