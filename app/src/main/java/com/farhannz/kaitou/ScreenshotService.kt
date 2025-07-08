package com.farhannz.kaitou

import android.R
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
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalView
import androidx.core.app.NotificationCompat
import androidx.window.layout.WindowMetrics
import androidx.core.graphics.createBitmap
import androidx.core.view.ViewCompat
import com.farhannz.kaitou.helpers.NotificationHelper
import java.nio.ByteBuffer

class ScreenshotService : Service() {

    private lateinit var mediaProjectionManager: MediaProjectionManager
    private lateinit var mediaProjection: MediaProjection
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var density: Int = 0
    private var width: Int = 0
    private var height: Int = 0

    override fun onBind(intent: Intent): IBinder {
        return ScreenshotServiceBinder()
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

        fun captureScreen(resultCode: Int, data: Intent) {
            Log.i("ScreenCaptureService", "Received capture request")
            val metrics = resources.displayMetrics
            val width = metrics.widthPixels
            val height = metrics.heightPixels

            Log.i("ScreenCaptureService", "Taking screenshot...")
            imageReader= ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 1).apply {
                setOnImageAvailableListener(
                    { reader ->
                        val image = reader.acquireLatestImage()
                        val width = image.width
                        val height = image.height
                        val plane = image.planes[0]
                        val buffer: ByteBuffer = plane.buffer

                        val pixelStride = plane.pixelStride  // should be 4 for RGBA_8888
                        val rowStride = plane.rowStride
                        val rowPadding = rowStride - pixelStride * width
                        val bitmap = createBitmap(width + rowPadding / pixelStride, height)

                        bitmap.copyPixelsFromBuffer(buffer)
                        image.close()
                        callback?.onCaptureComplete(bitmap.asImageBitmap())
                        // Immediately clean up
                        tearDownCapture()
                    }, Handler(Looper.getMainLooper())
                )
            }

            val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
            virtualDisplay = mediaProjection.createVirtualDisplay(
                "TempCapture",
                width, height, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, null
            )
        }
        fun getService(): ScreenshotService = this@ScreenshotService
    }

//    private fun takeScreenshot() {
//        Log.i("ScreenCaptureService", "Taking screenshot...")
//
//
//        val dummyBitmap = createBitmap(100, 100).asImageBitmap()
//        callback?.onCaptureComplete(dummyBitmap)
//        // Dummy implementation - replace with actual capture code
//        // In a real implementation, this would use MediaProjection
//    }


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

            if (resultCode != -1 && data != null) {
                setupMediaProjection(resultCode, data)
                // Dummy capture - just log for now
                Log.d("ScreenCapture", "Dummy capture initiated")
            }
        }
        return START_STICKY
    }

    private fun setupMediaProjection(resultCode: Int, data: Intent) {
        val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)

        // In a real implementation, you would set up ImageReader and VirtualDisplay here
        // For this minimal version, we're just keeping the reference
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