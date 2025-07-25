package com.farhannz.kaitou

import android.app.Activity.RESULT_OK
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.PixelFormat
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
import android.view.WindowInsets
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.farhannz.kaitou.helpers.Logger
import com.farhannz.kaitou.impl.ScreenshotStore
import com.farhannz.kaitou.presentation.utils.toBitmap

class ScreenshotServiceRework : Service() {


    private val LOG_TAG = ScreenshotServiceRework::class.simpleName;
    private val logger = Logger(LOG_TAG!!)
    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null

    var rc: Int = Int.MIN_VALUE
    var dataIntent: Intent? = null

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
            val metrics = resources.displayMetrics
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "SingleShot",
                width,
                height,
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

    fun excludeWindowInsets(bitmap: Bitmap): Bitmap {
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val windowMetrics = windowManager.currentWindowMetrics

        val insets = windowMetrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())

        val cropTop = insets.top
        val cropBottom = insets.bottom
        val cropLeft = insets.left
        val cropRight = insets.right
        logger.DEBUG(insets.toString())

        val cropWidth = bitmap.width - cropLeft - cropRight
        val cropHeight = bitmap.height - cropTop - cropBottom

        return Bitmap.createBitmap(bitmap, cropLeft, cropTop, cropWidth, cropHeight)
    }

    fun prepareScreenshot() {
        logger.DEBUG("rc = $rc, dataIntent = $dataIntent")
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
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

                val bitmap = excludeWindowInsets(image.toBitmap())
                image.close()
                ScreenshotStore.updateScreenshot(bitmap)

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