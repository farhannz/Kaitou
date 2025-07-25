package com.farhannz.kaitou

import android.app.Service.STOP_FOREGROUND_REMOVE
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.farhannz.kaitou.helpers.Logger
import androidx.core.net.toUri

class MainActivity : ComponentActivity() {

    private val LOG_TAG = MainActivity::class.simpleName
    private val logger = Logger(LOG_TAG!!)
    private var overlayGranted = mutableStateOf(false)
    private var screenshotGranted = mutableStateOf(false)

    private var currentPermissionRequest: PermissionRequestState? = null

    object MediaProjectionPermissionStore {
        var resultCode: Int = Int.MIN_VALUE
        var dataIntent: Intent? = null
    }

    private data class PermissionRequestState(
        val requestCapture: Boolean = false,
        val timestamp: Long = System.currentTimeMillis()
    )

    private fun tryMoveToBackground() {
        if (overlayGranted.value && screenshotGranted.value) {
            moveTaskToBack(true)
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            startOverlayService()
            overlayGranted.value = true
            tryMoveToBackground()
        } else {
            Toast.makeText(this, "Overlay permission is required!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri())
            overlayPermissionLauncher.launch(intent)
        } else {
            startOverlayService()
            overlayGranted.value = true
            tryMoveToBackground()
        }
    }

    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        ContextCompat.startForegroundService(this, intent)
//        moveTaskToBack(true)
    }


    //    This is for the reworked version of ScreenshotService
    //    Requesting Permission with the intent of Starting Service
    //    and caching the permission result via putExtra
    private val screenshotPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            logger.DEBUG("${result.resultCode} - ${result.data}")
            val actionRequest =
                if (currentPermissionRequest?.requestCapture == true) "START_AND_CAPTURE" else "START_SERVICE"
            logger.DEBUG(actionRequest)
            val intent = Intent(this, ScreenshotServiceRework::class.java).apply {
                action = actionRequest
                putExtra("resultCode", result.resultCode)
                putExtra("data", result.data)
                MediaProjectionPermissionStore.dataIntent = result.data
                MediaProjectionPermissionStore.resultCode = result.resultCode
            }
            screenshotGranted.value = true
            ContextCompat.startForegroundService(this, intent)
        } else {
            Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestScreenShotPermission(requestCapture: Boolean = false) {
        val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = mediaProjectionManager.createScreenCaptureIntent()
        currentPermissionRequest = PermissionRequestState(requestCapture = requestCapture)
        screenshotPermissionLauncher.launch(intent)
    }

    private val screenshotPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "REQUEST_SCREENSHOT_PERMISSION" -> {
                    requestScreenShotPermission(requestCapture = true)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (Settings.canDrawOverlays(this) && !overlayGranted.value) {
            overlayGranted.value = true
            startOverlayService()
            tryMoveToBackground()
        } else {
            requestOverlayPermission()
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        registerReceiver(
            screenshotPermissionReceiver,
            IntentFilter("REQUEST_SCREENSHOT_PERMISSION"),
            RECEIVER_NOT_EXPORTED
        )
//        OCRPipeline.initialize(this)
        requestScreenShotPermission()
    }

    override fun onDestroy() {
        unregisterReceiver(screenshotPermissionReceiver)
        super.onDestroy()
    }
}
