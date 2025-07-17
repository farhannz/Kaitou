package com.farhannz.kaitou

import android.app.Activity
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import com.farhannz.kaitou.helpers.Logger

class OverlayHostActivity : Activity() {
    private val LOG_TAG= OverlayHostActivity::class.simpleName
    private val logger = Logger(LOG_TAG!!)
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logger.DEBUG("Created")
    }

    override fun onPause() {
        super.onPause()
        logger.DEBUG("Paused")
        OverlayService.instance?.removeOverlay()
        finish() // Ensure this activity closes immediately
    }
}