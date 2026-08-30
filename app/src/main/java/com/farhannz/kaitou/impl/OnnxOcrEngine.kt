package com.farhannz.kaitou.impl

import android.content.Context
import com.farhannz.kaitou.domain.OcrEngine
import com.farhannz.kaitou.helpers.Logger
import com.farhannz.kaitou.domain.OcrResult
import com.farhannz.kaitou.domain.Point
import com.farhannz.kaitou.domain.RawImage
import com.farhannz.kaitou.domain.RecognizedText
import com.farhannz.kaitou.domain.TextRecognizer
import com.farhannz.kaitou.impl.onnxruntime.DetectionModel
import com.farhannz.kaitou.impl.onnxruntime.RecognitionModel
import com.farhannz.kaitou.presentation.utils.toMat
import org.opencv.core.Core
import org.opencv.core.Point as CvPoint
import java.io.File
import java.io.FileOutputStream

enum class OnnxEngineType {
    Detection, Recognition
}

class OnnxEngine(val predictor: Any) : OcrEngine {
    override suspend fun infer(image: RawImage): OcrResult {
        return when (predictor) {
            is DetectionModel -> {
                val result = predictor.predict(image.toMat())
                if (result.detections.boxes.isEmpty()) {
                    OcrResult.Error("No text detected")
                } else {
                    OcrResult.Detection(result)
                }
            }

            is RecognitionModel -> {
                val result = predictor.predict(image.toMat())
                if (result.isEmpty()) {
                    OcrResult.Error("No texts detected")
                } else {
                    OcrResult.Recognition(listOf(result))
                }
            }

            else -> throw IllegalStateException("Unknown predictor type")
        }
    }
}

object OnnxEngineFactory {
    fun create(context: Context, engine: OnnxEngineType): OcrEngine = when (engine) {
        OnnxEngineType.Detection -> {
            val modelPath = copyAssetToCache(context, "onnx", "PP-OCRv6_tiny_det.onnx")
            OnnxEngine(DetectionModel(modelPath))
        }

        OnnxEngineType.Recognition -> {
            val modelPath = copyAssetToCache(context, "onnx", "PP-OCRv6_small_rec_static.onnx")
            val characterList =
                context.assets.open("onnx/ppocrv6_dict.txt").bufferedReader().readLines()
            OnnxEngine(RecognitionModel(modelPath, characterList))
        }
    }

    private fun copyAssetToCache(context: Context, assetFolder: String, assetName: String): String {
        val assetSubPath = "$assetFolder/$assetName"
        val outDir = File(context.cacheDir, "onnx")
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

class OnnxTextRecognizer(private val recognitionEngine: OcrEngine) : TextRecognizer {
    private val logger = Logger("OnnxTextRecognizer")

    override suspend fun recognize(
        image: RawImage,
        boxes: List<List<Point>>,
        selectedIndices: List<Int>
    ): List<RecognizedText> {
        val e2eStart = System.nanoTime()

        val tolerance = 20
        val sortedBoxes = selectedIndices
            .map { boxes[it] }
            .groupBy { box -> (box.minOf { it.x } / tolerance).toInt() }
            .toSortedMap(reverseOrder())
            .flatMap { (_, columnBoxes) ->
                columnBoxes.sortedBy { it.minOf { p -> p.y } }
            }

        val mat = image.toMat()
        val crops = sortedBoxes.map { box ->
            cropFromBox(mat, box.map { CvPoint(it.x.toDouble(), it.y.toDouble()) }).apply {
                val isVertical = (height().toFloat() / width().toFloat()) > 1.25f
                if (isVertical) Core.rotate(this, this, Core.ROTATE_90_COUNTERCLOCKWISE)
            }
        }

        val predictor = (recognitionEngine as OnnxEngine).predictor as RecognitionModel
        val texts = predictor.predictBatch(crops)

        crops.forEach { it.release() }

        val results = sortedBoxes.zip(texts).map { (box, text) ->
            RecognizedText(text, box)
        }

        val e2eTime = (System.nanoTime() - e2eStart) / 1_000_000.0
        logger.INFO("[latency] OnnxTextRecognizer e2e: %.2f ms, boxes=${boxes.size}".format(e2eTime))

        return results
    }
}