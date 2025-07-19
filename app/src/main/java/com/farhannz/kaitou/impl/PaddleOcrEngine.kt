package com.farhannz.kaitou.impl

import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.util.Log
import androidx.core.graphics.createBitmap
import com.farhannz.kaitou.domain.OcrEngine
import com.farhannz.kaitou.domain.OcrResult
import com.farhannz.kaitou.domain.Point
import com.farhannz.kaitou.domain.RawImage
import com.farhannz.kaitou.domain.RecognizedText
import com.farhannz.kaitou.domain.TextRecognizer
import com.farhannz.kaitou.paddle.BasePredictor
import com.farhannz.kaitou.paddle.DetectionPredictor
import com.farhannz.kaitou.paddle.RecognitionPredictor
import com.farhannz.kaitou.paddle.cropFromBox
import com.farhannz.kaitou.ui.components.utils.toMat
import com.farhannz.kaitou.ui.components.utils.toRawImage
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.imgcodecs.Imgcodecs
import java.io.File
import kotlin.collections.component1
import kotlin.collections.component2
import org.opencv.core.Point as CvPoint

enum class EngineType {
    Detection, Recognition
}


class PaddleEngine(val predictor: BasePredictor) : OcrEngine {
    override suspend fun infer(image: RawImage): OcrResult {
        return predictor.infer(image)
    }
}

object PaddleEngineFactory {
    fun create(context: Context, engine: EngineType): OcrEngine =
        when (engine) {
            EngineType.Detection -> {
                val predictor = DetectionPredictor()
                predictor.initialize(context, "paddle", "ppocrv5_det.nb")
                PaddleEngine(predictor)
            }

            EngineType.Recognition -> {
                val predictor = RecognitionPredictor()
                predictor.initialize(context, "paddle", "ppocrv5_rec.nb")
                PaddleEngine(predictor)
            }
        }
}

class PaddleTextRecognizer(
    private val recognitionEngine: OcrEngine
) : TextRecognizer {

    override suspend fun recognize(
        image: RawImage,
        boxes: List<List<Point>>,
        selectedIndices: List<Int>
    ): List<RecognizedText> {
        Log.d(PaddleTextRecognizer::class.simpleName, boxes.joinToString(";"))
        val tolerance = 20
        val boxes = selectedIndices
            .map { boxes[it] }
            .groupBy { box -> (box.minOf { it.x } / tolerance).toInt() } // group by column
            .toSortedMap(reverseOrder()) // right-to-left columns
            .flatMap { (_, columnBoxes) ->
                columnBoxes.sortedBy { it.minOf { p -> p.y } } // top-to-bottom in column
            }
        val mat = image.toMat()
        var idx = 0
        return boxes.mapNotNull { box ->
            val cropped = cropFromBox(mat, box.map { CvPoint(it.x.toDouble(), it.y.toDouble()) })
            val isVertical = (cropped.height().toFloat() / cropped.width().toFloat()) > 1.25f
            if (isVertical) {
                Core.rotate(cropped, cropped, Core.ROTATE_90_COUNTERCLOCKWISE)
            }
            val bm = createBitmap(cropped.width(), cropped.height(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(cropped, bm)
            val croppedRaw = bm.toRawImage()
            when (val result = recognitionEngine.infer(croppedRaw)) {
                is OcrResult.Recognition -> {
                    idx++
                    RecognizedText(result.texts.joinToString(), box)
                }

                else -> null
            }
        }
    }
}