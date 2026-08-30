package com.farhannz.kaitou

import android.app.Activity.RESULT_OK
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.farhannz.kaitou.helpers.Logger
import com.farhannz.kaitou.impl.ScreenshotStore
import com.farhannz.kaitou.presentation.utils.toBitmap

class ScreenshotServiceRework : Service() {


    private val LOG_TAG = ScreenshotServiceRework::class.simpleName
    private val logger = Logger(LOG_TAG!!)
    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null

    var rc: Int = Int.MIN_VALUE
    var dataIntent: Intent? = null

    // Real display resolution. Must come from maximumWindowMetrics, NOT
    // resources.displayMetrics: the latter is the app-usable window size and
    // varies with nav-bar mode, display zoom, cutouts and windowing. Sizing the
    // VirtualDisplay/ImageReader from it would silently scale the mirrored
    // frame and skew every downstream OCR coordinate.
    private var displayWidth = 0
    private var displayHeight = 0

    private val shutdownReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "SHUTDOWN_SERVICES") {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }


    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun startScreenshotService(intent: Intent) {
        val captured = mapOf(
            "resultCode" to intent.getIntExtra("resultCode", Int.MIN_VALUE),
            "data" to intent.getParcelableExtra("data", Intent::class.java)
        )
        if (captured["resultCode"] == RESULT_OK && captured["data"] != null) {
            rc = captured["resultCode"] as Int
            dataIntent = captured["data"] as Intent
            mediaProjection =
                (applicationContext.getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).getMediaProjection(
                    rc,
                    dataIntent!!
                )
            logger.DEBUG("MediaProjection Permission Granted")
            val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            val bounds = windowManager.maximumWindowMetrics.bounds
            displayWidth = bounds.width()
            displayHeight = bounds.height()
            val density = resources.displayMetrics.densityDpi
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "SingleShot",
                displayWidth,
                displayHeight,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                null
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null


    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(LOG_TAG, "Received Request")
        when (intent?.action) {
            "CAPTURE_SCREENSHOT" -> {
                requestCapture()
            }

            "START_SERVICE" -> {
                startScreenshotService(intent)
            }

            "START_AND_CAPTURE" -> {
                startScreenshotService(intent)
                requestCapture()
            }
        }
        return START_NOT_STICKY
    }

    fun prepareScreenshot() {
        logger.DEBUG("rc = $rc, dataIntent = $dataIntent")
        if (displayWidth == 0 || displayHeight == 0) {
            val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            val bounds = windowManager.maximumWindowMetrics.bounds
            displayWidth = bounds.width()
            displayHeight = bounds.height()
        }
        imageReader = ImageReader.newInstance(displayWidth, displayHeight, PixelFormat.RGBA_8888, 2)
        virtualDisplay?.surface = imageReader?.surface
        var captured = false
        val handler = Handler(Looper.getMainLooper())
        imageReader?.setOnImageAvailableListener({ reader ->
            if (captured) return@setOnImageAvailableListener
            captured = true

            try {
                val image = reader.acquireLatestImage()
                if (image == null) {
                    logger.WARNING("Image is null")
                    return@setOnImageAvailableListener
                }

                // Keep the full display frame (no inset cropping). The bitmap is
                // a 1:1 mirror of the display, so the edge-to-edge overlay can
                // draw it without inset compensation. Cropping here while the
                // UI re-applied safeDrawing insets double-subtracted them
                // inconsistently across devices.
                val bitmap = image.toBitmap()
                image.close()
                val displayManager = getSystemService(DISPLAY_SERVICE) as DisplayManager
                val rotation = displayManager.getDisplay(Display.DEFAULT_DISPLAY).rotation
                ScreenshotStore.updateScreenshot(
                    bitmap,
                    Rect(0, 0, bitmap.width, bitmap.height),
                    rotation
                )

            } catch (e: Throwable) {
                logger.ERROR(e.message!!)
            } finally {
                imageReader?.close()
                virtualDisplay?.surface = null
            }
        }, handler)

    }

    fun requestScreenshotPermission() {
        val broadcast = Intent("REQUEST_SCREENSHOT_PERMISSION")
        sendBroadcast(broadcast)
    }

    fun requestCapture() {
        logger.DEBUG("Captured")
        logger.DEBUG("$rc - $dataIntent")
        if ((rc != RESULT_OK) || (dataIntent == null)) {
            requestScreenshotPermission()
        } else {
            prepareScreenshot()
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate() {
        super.onCreate()
        registerReceiver(shutdownReceiver, IntentFilter("SHUTDOWN_SERVICES"), RECEIVER_NOT_EXPORTED)
        val captureChannel = NotificationChannel(
            "ScreenshotRework",
            "Screen Capture",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Handles screen captures"
        }

//        Creating notification channel
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(captureChannel)
        val notification = NotificationCompat.Builder(this, "ScreenshotRework")
            .setContentTitle("Kaitou")
            .setContentText("Screen Capture is running")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()
        startForeground(1991, notification)
    }

    override fun onDestroy() {
        imageReader?.close()
        imageReader = null

        virtualDisplay?.surface = null
        virtualDisplay?.release()
        virtualDisplay = null

        mediaProjection?.stop()
        mediaProjection = null

        unregisterReceiver(shutdownReceiver)
        super.onDestroy()
    }
}