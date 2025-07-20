package com.farhannz.kaitou


import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.farhannz.kaitou.helpers.Logger
import com.farhannz.kaitou.impl.ScreenshotStore
import com.farhannz.kaitou.presentation.components.OCRScreen

class OverlayHostActivity : ComponentActivity() {
    private val LOG_TAG = OverlayHostActivity::class.simpleName
    private val logger = Logger(LOG_TAG!!)

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        logger.DEBUG("Created")
        setContent {
            val bitmap by ScreenshotStore.latestScreenshot.collectAsState()
            bitmap?.let {
                OCRScreen(
                    onClicked = {
                        finish()
                        ScreenshotStore.clear()
                    },
                    inputImage = it.bitmap
                )
            }
        }
    }

    override fun onPause() {
        sendBroadcast(Intent("OVERLAY_DISMISSED"))
        super.onPause()
    }

    override fun onStop() {
        sendBroadcast(Intent("OVERLAY_DISMISSED"))
        super.onStop()
    }

    override fun onDestroy() {
        sendBroadcast(Intent("OVERLAY_DISMISSED"))
        super.onDestroy()
    }
}