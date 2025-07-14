package com.farhannz.kaitou.paddle

import android.graphics.Bitmap
import com.farhannz.kaitou.data.models.DetectionResult
import com.farhannz.kaitou.helpers.Logger
import org.opencv.core.Point
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

object OCRPipeline {
    private val LOG_TAG = OCRPipeline::class.simpleName
    private val logger = Logger(LOG_TAG!!)
    val batchSize = 3
    fun extractTexts(bitmap: Bitmap): Pair<DetectionResult, Int> {
        val det_result = PredictorManager.runDetection(bitmap)
        val totalBatches = ceil((det_result.boxes.size.toDouble() / batchSize.toDouble())).toInt()
        var batch_idx = mutableListOf<Int>()
        for (i in 0 until totalBatches ) {
            val start = i * batchSize
            logger.DEBUG("Batch : $i")
            for (j in start until min(start + batchSize, det_result.boxes.size)) {
                logger.DEBUG("current file index : $j")
            }
        }
        return Pair<DetectionResult, Int>(det_result, 1)
    }
}