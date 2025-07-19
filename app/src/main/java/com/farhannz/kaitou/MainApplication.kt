package com.farhannz.kaitou

import android.app.Application
import com.farhannz.kaitou.domain.OcrEngine
import com.farhannz.kaitou.domain.TextRecognizer
import com.farhannz.kaitou.helpers.DatabaseManager
import com.farhannz.kaitou.helpers.Logger
import com.farhannz.kaitou.impl.EngineType
import com.farhannz.kaitou.impl.PaddleEngineFactory
import com.farhannz.kaitou.impl.PaddleTextRecognizer
import org.opencv.android.OpenCVLoader

class MainApplication : Application() {
    private val LOG_TAG = MainApplication::class.simpleName
    private val logger = Logger(LOG_TAG!!)
    lateinit var detectionEngine: OcrEngine
        private set

    lateinit var recognitionEngine: OcrEngine
        private set
    lateinit var textRecognizer: TextRecognizer
        private set

    override fun onCreate() {
        super.onCreate()
        DatabaseManager.initialize(this)
        if (OpenCVLoader.initLocal()) {
            logger.DEBUG("OpenCV initialized successfully")
        } else {
            logger.ERROR("OpenCV initialization failed")
        }
        detectionEngine = PaddleEngineFactory.create(this, EngineType.Detection)
        recognitionEngine = PaddleEngineFactory.create(this, EngineType.Recognition)
        textRecognizer = PaddleTextRecognizer(recognitionEngine)
    }
}