package com.farhannz.kaitou

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.farhannz.kaitou.helpers.Logger
import com.farhannz.kaitou.presentation.components.KaitouApp
import androidx.core.net.toUri

class MainActivity : ComponentActivity() {

    private val LOG_TAG = MainActivity::class.simpleName
    private val logger = Logger(LOG_TAG!!)

    private var currentPermissionRequest: PermissionRequestState? = null

    private data class PermissionRequestState(
        val requestCapture: Boolean = false,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Overlay permission is required!", Toast.LENGTH_SHORT).show()
        }
        // rememberPermissionState() refreshes on ON_RESUME; the router advances on its own.
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent =
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri())
            overlayPermissionLauncher.launch(intent)
        }
    }

    fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    //    This is for the reworked version of ScreenshotService
    //    Requesting Permission with the intent of Starting Service
    //    and caching the permission result via putExtra
    private val screenshotPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            logger.DEBUG("${result.resultCode} - ${result.data}")
            MediaProjectionPermissionStore.save(this, result.resultCode, result.data!!)
            val actionRequest =
                if (currentPermissionRequest?.requestCapture == true) "START_AND_CAPTURE" else "START_SERVICE"
            logger.DEBUG(actionRequest)
            val intent = Intent(this, ScreenshotServiceRework::class.java).apply {
                action = actionRequest
                putExtra("resultCode", result.resultCode)
                putExtra("data", result.data)
            }
            ContextCompat.startForegroundService(this, intent)
        } else {
            Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestScreenShotPermission(requestCapture: Boolean = false) {
        val mediaProjectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = mediaProjectionManager.createScreenCaptureIntent()
        currentPermissionRequest = PermissionRequestState(requestCapture = requestCapture)
        screenshotPermissionLauncher.launch(intent)
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        logger.DEBUG("POST_NOTIFICATIONS granted: $granted")
    }

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
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

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        registerReceiver(
            screenshotPermissionReceiver,
            IntentFilter("REQUEST_SCREENSHOT_PERMISSION"),
            RECEIVER_NOT_EXPORTED
        )
        setContent {
            KaitouApp(
                onGrantOverlay = { requestOverlayPermission() },
                onGrantCapture = { requestScreenShotPermission() },
                onGrantNotifications = { requestNotificationPermission() },
                onStartOverlayService = { startOverlayService() },
                onStopServices = { sendBroadcast(Intent("SHUTDOWN_SERVICES")) },
                onMinimize = { moveTaskToBack(true) }
            )
        }
    }

    override fun onDestroy() {
        unregisterReceiver(screenshotPermissionReceiver)
        super.onDestroy()
    }
}