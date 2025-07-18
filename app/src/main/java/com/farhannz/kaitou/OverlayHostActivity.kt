package com.farhannz.kaitou

import android.app.Activity
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.ui.platform.LocalContext
import com.farhannz.kaitou.helpers.Logger

class OverlayHostActivity : Activity() {
    private val LOG_TAG = OverlayHostActivity::class.simpleName
    private val logger = Logger(LOG_TAG!!)

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OverlayService.instance?.hostActivity = this
        logger.DEBUG("Created")
    }

    override fun onPause() {
        super.onPause()
        OverlayService.instance?.removeOverlay()
    }

    override fun onStop() {
        super.onStop()
        logger.DEBUG("Stopped")
        OverlayService.instance?.removeOverlay()
    }

    override fun onDestroy() {
        super.onDestroy()
        OverlayService.instance?.hostActivity = null
    }
}