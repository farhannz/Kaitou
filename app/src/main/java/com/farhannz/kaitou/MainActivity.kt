package com.farhannz.kaitou

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.farhannz.kaitou.bridges.OCRBridge.prepareInitModel
import com.farhannz.kaitou.helpers.NotificationHelper


class MainActivity : ComponentActivity() {

    private lateinit var mediaProjectionManager : MediaProjectionManager
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            startOverlayService()
        } else {
            Toast.makeText(this, "Overlay permission is required!", Toast.LENGTH_SHORT).show()
        }
    }

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            // Permission granted, start capturing
            startCaptureService(result.resultCode, result.data!!)
        } else {
            // Permission denied
            Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
        }
    }
    private fun requestScreenCapturePermission() {
        val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val captureIntent = mediaProjectionManager.createScreenCaptureIntent()

        // Launch the permission request using the launcher
        screenCaptureLauncher.launch(captureIntent)
    }


    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            overlayPermissionLauncher.launch(intent)
        } else {
            startOverlayService()
        }
    }

    private fun startCaptureService(resultCode: Int, data: Intent) {
        val serviceIntent = Intent(this, ScreenshotService::class.java).apply {
            putExtra("resultCode", resultCode)
            putExtra("data", data)
        }
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationHelper.createNotificationChannels(this)
        enableEdgeToEdge()
        requestOverlayPermission()
        requestScreenCapturePermission()
        moveTaskToBack(true)
        prepareInitModel(application)
    }
}
