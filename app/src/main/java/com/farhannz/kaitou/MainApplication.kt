package com.farhannz.kaitou

import android.app.Application
import com.farhannz.kaitou.domain.OcrEngine
import com.farhannz.kaitou.domain.TextRecognizer
import com.farhannz.kaitou.helpers.DatabaseManager
import com.farhannz.kaitou.helpers.Logger
import com.farhannz.kaitou.helpers.TransformerManager
import com.farhannz.kaitou.impl.OnnxEngineFactory
import com.farhannz.kaitou.impl.OnnxEngineType
import com.farhannz.kaitou.impl.OnnxTextRecognizer
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
        TransformerManager.initialize(this)
        if (OpenCVLoader.initLocal()) {
            logger.DEBUG("OpenCV initialized successfully")
        } else {
            logger.ERROR("OpenCV initialization failed")
        }
        detectionEngine = OnnxEngineFactory.create(this, OnnxEngineType.Detection)
        recognitionEngine = OnnxEngineFactory.create(this, OnnxEngineType.Recognition)
        textRecognizer = OnnxTextRecognizer(recognitionEngine)
    }
}