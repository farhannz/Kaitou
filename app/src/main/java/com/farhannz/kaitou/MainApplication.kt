package com.farhannz.kaitou

import android.app.Application
import com.farhannz.kaitou.helpers.DatabaseManager
import com.farhannz.kaitou.helpers.Logger
import org.opencv.android.OpenCVLoader

class MainApplication : Application() {
    private val LOG_TAG = MainApplication::class.simpleName
    private val logger = Logger(LOG_TAG!!)
    override fun onCreate() {
        super.onCreate()
        DatabaseManager.initialize(this)
        if (OpenCVLoader.initLocal()) {
            logger.DEBUG("OpenCV initialized successfully")
        } else {
            logger.ERROR("OpenCV initialization failed")
        }
    }
}