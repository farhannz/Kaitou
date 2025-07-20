package com.farhannz.kaitou

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.farhannz.kaitou.helpers.Logger
import androidx.core.net.toUri

class MainActivity : ComponentActivity() {

    private val LOG_TAG = MainActivity::class.simpleName
    private val logger = Logger(LOG_TAG!!)
    private var overlayGranted = mutableStateOf(false)
    private var screenshotGranted = mutableStateOf(false)

    object MediaProjectionPermissionStore {
        var resultCode: Int = Int.MIN_VALUE
        var dataIntent: Intent? = null
    }

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
            val intent = Intent(this, ScreenshotServiceRework::class.java).apply {
                action = "START_SERVICE"
                putExtra("resultCode", result.resultCode)
                putExtra("data", result.data)
                MediaProjectionPermissionStore.dataIntent = result.data
                MediaProjectionPermissionStore.resultCode = result.resultCode
            }
            screenshotGranted.value = true
            ContextCompat.startForegroundService(this, intent)
        } else {
            Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
            requestScreenShotPermission()
        }
    }

    private fun requestScreenShotPermission() {
        val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = mediaProjectionManager.createScreenCaptureIntent()
        screenshotPermissionLauncher.launch(intent)
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
//        OCRPipeline.initialize(this)
        requestScreenShotPermission()
    }
}
