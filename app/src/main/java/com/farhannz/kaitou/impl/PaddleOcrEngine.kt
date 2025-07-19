package com.farhannz.kaitou.impl

import android.content.Context
import com.farhannz.kaitou.domain.DetectionResult
import com.farhannz.kaitou.domain.GroupedResult
import com.farhannz.kaitou.domain.OcrEngine
import com.farhannz.kaitou.domain.OcrResult
import com.farhannz.kaitou.domain.RawImage
import com.farhannz.kaitou.paddle.BasePredictor
import com.farhannz.kaitou.paddle.DetectionPredictor
import com.farhannz.kaitou.paddle.RecognitionPredictor

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

            EngineType.Recognition -> TODO("Recognition predictor not yet implemented")
        }
}