package com.farhannz.kaitou

import android.R
import android.app.Activity.RESULT_OK
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.app.NotificationCompat
import androidx.window.layout.WindowMetrics
import androidx.core.graphics.createBitmap
import androidx.core.view.ViewCompat
import com.farhannz.kaitou.helpers.Logger
import com.farhannz.kaitou.helpers.NotificationHelper
import com.farhannz.kaitou.helpers.NotificationHelper.CAPTURE_CHANNEL_ID
import java.nio.ByteBuffer


fun Image.toBitmap(): Bitmap {
    try {
        val plane = planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width

        return createBitmap(width + rowPadding / pixelStride, height).apply {
            copyPixelsFromBuffer(buffer)
        }
    } catch (e: Exception) {
        throw RuntimeException("Failed to convert image to bitmap", e)
    }
}

class ScreenshotServiceRework : Service () {


    val LOG_TAG = this::class.simpleName;
    private val logger = Logger(LOG_TAG!!)
    private var rc : Int = Int.MIN_VALUE
    private var dataIntent:Intent? = null
    private val binder = LocalBinder()

    private var mediaProjectionManager: MediaProjectionManager? = null
    private var mediaProjection : MediaProjection? = null
    private var imageReader : ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null

//    Callback
    var onScreenshotTaken: ((Bitmap) -> Unit)? = null

    inner class LocalBinder : Binder() {
        fun getService(): ScreenshotServiceRework = this@ScreenshotServiceRework
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }


    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(LOG_TAG, "Received Request")
        when (intent?.action) {
            "CAPTURE_SCREENSHOT" -> {
                logger.INFO("Screenshot Captured")
            }
            "START_SERVICE" -> {
                val captured = mapOf(
                    "resultCode" to intent.getIntExtra("resultCode", Int.MIN_VALUE),
                    "data" to intent.getParcelableExtra("data", Intent::class.java)
                )
                if (captured["resultCode"] == RESULT_OK && captured["data"] != null) {
                    rc = captured["resultCode"] as Int
                    dataIntent = captured["data"] as Intent
                    logger.DEBUG("MediaProjection Permission Granted")
                }
            }
        }
        return START_STICKY
    }

    fun requestCapture() {
        logger.DEBUG("Captured")
        logger.DEBUG("$rc - $dataIntent")
        onScreenshotTaken?.invoke(createBitmap(100, 100))
    }

    override fun onCreate() {
        super.onCreate()
        val captureChannel = NotificationChannel(
            "ScreenshotRework",
            "Screen Capture",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Handles screen captures"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(captureChannel)
        val notification = NotificationCompat.Builder(this, "ScreenshotRework")
            .setContentTitle("Kaitou")
            .setContentText("Screen Capture is running")
            .setSmallIcon(R.drawable.ic_menu_view)
            .build()
        startForeground(1991, notification)
    }
}


class ScreenshotService : Service() {

    private lateinit var mediaProjectionManager: MediaProjectionManager
    private lateinit var mediaProjection: MediaProjection

    private var binder = ScreenshotServiceBinder()
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var density: Int = 0
    private var width: Int = 0
    private var height: Int = 0

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    // Request code for MediaProjection permission
    companion object {
        const val CAPTURE_NOTIFICATION_ID = 1771
        const val MEDIA_PROJECTION_REQUEST_CODE = 1001
        const val ACTION_START_PROJECTION = "com.farhannz.kaitou.ACTION_START_PROJECTION"
        const val ACTION_STOP_PROJECTION = "com.farhannz.kaitou.ACTION_STOP_PROJECTION"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA_INTENT = "dataIntent"
    }

    private var callback: ScreenshotCallback? = null

    interface ScreenshotCallback {
        fun onCaptureComplete(bitmap: ImageBitmap)
        fun onCaptureFailed(error: String)
    }

    inner class ScreenshotServiceBinder : Binder() {
        fun registerCallback(cb: ScreenshotCallback) {
            callback = cb
        }
        fun getService(): ScreenshotService = this@ScreenshotService
    }

    fun captureScreen(resultCode: Int, data: Intent?) {
        try {
            val metrics = resources.displayMetrics
            val width = metrics.widthPixels
            val height = metrics.heightPixels

            Log.d("ScreenCapture", "${width}x${height}")
            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 1).apply {
                setOnImageAvailableListener({ reader ->
                    try {
                        val image = reader.acquireLatestImage()
                        if (image == null) {
                            Log.w("ScreenCapture", "Null image received, retrying...")
                            callback?.onCaptureFailed("Failed to capture, retrying...")
                            retryCapture(resultCode, data!!)
                            return@setOnImageAvailableListener
                        }

                        try {
                            val bitmap = image.toBitmap().asImageBitmap()
                            callback?.onCaptureComplete(bitmap)
                        } finally {
                            image.close()
                        }
                    } catch (e: Exception) {
                        callback?.onCaptureFailed("Image processing failed: ${e.message}")
                    } finally {
                        tearDownCapture()
                    }
                }, Handler(Looper.getMainLooper()))
            }
        } catch (e: Exception) {
            callback?.onCaptureFailed("Capture setup failed: ${e.message}")
        }

        // Implement your actual capture logic here
//            takeScreenshot()
    }

    private fun retryCapture(resultCode: Int, data: Intent) {
        Handler(Looper.getMainLooper()).postDelayed({
            captureScreen(resultCode, data)
        }, 300) // Retry after 300ms
    }

    private fun takeScreenshot() {
        Log.i("ScreenCaptureService", "Taking screenshot...")


        val dummyBitmap = createBitmap(100, 100).asImageBitmap()
        callback?.onCaptureComplete(dummyBitmap)
        // Dummy implementation - replace with actual capture code
        // In a real implementation, this would use MediaProjection
    }


    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannels(this)
        startForeground(CAPTURE_NOTIFICATION_ID, createNotification())
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            val resultCode = it.getIntExtra("resultCode", -1)
            val data = it.getParcelableExtra("data", Intent::class.java)

            if (resultCode == RESULT_OK && data != null) {
                setupMediaProjection(resultCode, data)
                captureScreen(resultCode,data)
                // Dummy capture - just log for now

//                Log.d("ScreenCapture", "Dummy capture initiated")
            }
        }
        return START_STICKY
    }

    private fun setupMediaProjection(resultCode: Int, data: Intent) {
        try {
            mediaProjection = (getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager)
                .getMediaProjection(resultCode, data)

            val metrics = resources.displayMetrics
            virtualDisplay = mediaProjection.createVirtualDisplay(
                "ScreenCapture",
                metrics.widthPixels,
                metrics.heightPixels,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                object : VirtualDisplay.Callback() {
                    override fun onPaused() {
                        Log.w("ScreenCapture", "VirtualDisplay paused")
                    }

                    override fun onResumed() {
                        Log.w("ScreenCapture", "VirtualDisplay resumed")
                    }

                    override fun onStopped() {
                        Log.w("ScreenCapture", "VirtualDisplay stopped")
                    }
                },
                null
            )
        } catch (e: Exception) {
            callback?.onCaptureFailed("Failed to setup projection: ${e.message}")
        }
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, ScreenshotService::class.java).apply {
            action = ACTION_STOP_PROJECTION
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NotificationHelper.CAPTURE_CHANNEL_ID)
            .setContentTitle("Screen Capture")
            .setContentText("Capturing screen...")
            .setSmallIcon(R.drawable.ic_notification_overlay)
            .addAction(R.drawable.ic_delete, "Stop", stopPendingIntent)
            .build()
    }
//
//    private fun createNotificationChannel() {
//        val channel = NotificationChannel(
//            CHANNEL_ID,
//            "Screen Capture",
//            NotificationManager.IMPORTANCE_LOW
//        )
//        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
//    }


    private fun tearDownCapture() {
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection.stop()
        virtualDisplay = null
        imageReader = null
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaProjection.stop()
        Log.d("ScreenCapture", "Service destroyed")
    }

}