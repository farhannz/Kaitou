package com.farhannz.kaitou

import android.app.Application
import com.farhannz.kaitou.helpers.DatabaseManager
import com.farhannz.kaitou.helpers.Logger
import com.farhannz.kaitou.helpers.TransformerManager

class MainApplication : Application() {
    private val LOG_TAG = MainApplication::class.simpleName
    private val logger = Logger(LOG_TAG!!)

    override fun onCreate() {
        super.onCreate()
        DatabaseManager.initialize(this)
        // Everything heavy (OpenCV native load, ONNX sessions, tokenizer,
        // reranker assets) runs off the main thread. The UI renders
        // immediately and observes OcrEngineProvider.state for warm-up.
        TransformerManager.initialize(this)
        OcrEngineProvider.initializeAsync(this)
    }
}
