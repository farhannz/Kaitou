package com.farhannz.kaitou.paddle

import android.graphics.Bitmap
import androidx.compose.ui.tooling.data.Group
import com.farhannz.kaitou.data.models.DetectionResult
import com.farhannz.kaitou.data.models.GroupedResult
import com.farhannz.kaitou.helpers.Logger
import org.opencv.core.Point
import kotlin.math.ceil
import kotlin.math.max

object OCRPipeline {
    private val LOG_TAG = OCRPipeline::class.simpleName
    private val logger = Logger(LOG_TAG!!)
    val batchSize = 3
    fun extractTexts(bitmap: Bitmap): Pair<GroupedResult, Int> {
        val det_result = PredictorManager.runDetection(bitmap)
        val totalBatches = ceil((det_result.detections.boxes.size.toDouble() / batchSize.toDouble())).toInt()
        var batch_idx = mutableListOf<Int>()
        for (i in 0 until totalBatches - 1 ) {
            val start = i * batchSize
            logger.DEBUG("Batch : $i")
            for (j in start until max(start + batchSize, det_result.detections.boxes.size - 1)) {
                logger.DEBUG("current file index : $j")
            }
        }
        return Pair(det_result, 1)
    }
}