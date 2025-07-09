package com.farhannz.kaitou

import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.graphics.Bitmap
//import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.*
import android.media.ImageReader
import android.media.projection.*
import android.os.Binder
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.NotificationCompat
import androidx.lifecycle.*
import androidx.savedstate.*
import com.farhannz.kaitou.helpers.Logger
import com.farhannz.kaitou.helpers.NotificationHelper
import com.farhannz.kaitou.helpers.ScreenshotPermissionActivity
import com.farhannz.kaitou.ui.components.DraggableOverlayContent
import com.farhannz.kaitou.ui.components.OCRScreen
import dev.shreyaspatil.capturable.capturable
import dev.shreyaspatil.capturable.controller.CaptureController
import dev.shreyaspatil.capturable.controller.rememberCaptureController
//import com.farhannz.kaitou.ui.viewmodels.SudachiTokenizer
import kotlinx.coroutines.launch
import kotlin.math.roundToInt


typealias ComposableContent = @Composable () -> Unit
class OverlayService() : Service(), SavedStateRegistryOwner {
    private lateinit var composeView: ComposeView
    private lateinit var windowManager: WindowManager
    private val LOG_TAG  = OverlayService::class.simpleName
    private val logger = Logger(LOG_TAG!!)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val lifecycleRegistry = LifecycleRegistry(this)
    private var isBound = false
    private var screenshotServiceBinder: ScreenshotService.ScreenshotServiceBinder? = null
    private var pendingCaptureRequest = false

    private lateinit var mediaProjectionManager: MediaProjectionManager


    private var currentX = 0
    private var currentY = 100
    private var ocrScreen: ComposeView? = null


//    private lateinit var sudachiTokenizer: SudachiTokenizer

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry


//    private val permissionResultReceiver = object : BroadcastReceiver() {
//        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
//        override fun onReceive(context: Context, intent: Intent) {
//            val resultCode = intent.getIntExtra("resultCode", -1)
//            val data = intent.getParcelableExtra("data", Intent::class.java)
//            handleCaptureResult(resultCode, data)
//        }
//
//        private fun handleCaptureResult(resultCode: Int, data: Intent?) {
//            screenshotServiceBinder?.registerCallback(object : ScreenshotService.ScreenshotCallback {
//                override fun onCaptureComplete(bitmap: ImageBitmap) {
//                    Log.i("OverlayService", "Screenshot Captured ${bitmap.width}x${bitmap.height}")
//                    showOCRScreen()
//                }
//                override fun onCaptureFailed(error: String) {
//                    Toast.makeText(this@OverlayService, error, Toast.LENGTH_SHORT).show()
//                }
//            }) ?: run {
//                Toast.makeText(this@OverlayService, "Capture service not ready", Toast.LENGTH_SHORT).show()
//            }
//            screenshotServiceBinder?.captureScreen(resultCode,data)
//        }
//    }
//    private val connection = object : ServiceConnection {
//        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
//        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
//            isBound = true
//            Log.d("OverlayService", "Connected to ScreenshotService")
//            screenshotServiceBinder = service as ScreenshotService.ScreenshotServiceBinder
//            if (pendingCaptureRequest) {
//                executeCapture()
//            }
//        }
//        override fun onServiceDisconnected(p0: ComponentName?) {
//            isBound = false
//            Log.i("OverlayService", "Disconnected from ScreenshotService")
//            screenshotServiceBinder = null
//        }
//    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun executeCapture() {
//        val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
//        val permissionIntent = mediaProjectionManager.createScreenCaptureIntent()
//        permissionIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
//        startActivity(permissionIntent)

//        registerReceiver(permissionResultReceiver, IntentFilter("CAPTURE_RESULT"), RECEIVER_NOT_EXPORTED)
//        val intent = Intent(this, ScreenshotPermissionActivity::class.java).apply {
//            flags = Intent.FLAG_ACTIVITY_NEW_TASK
//        }
//        startActivity(intent)
    }

//    private fun bindToCaptureService() {
//        try {
//            val intent = Intent(this, ScreenshotService::class.java)
//            bindService(intent, connection, Context.BIND_AUTO_CREATE)
//        } catch (e: Exception) {
//            Log.e("OverlayService", "Binding failed: ${e.message}")
//            pendingCaptureRequest = false
//            Toast.makeText(this, "Failed to connect capture service", Toast.LENGTH_SHORT).show()
//        }
//    }


    private val connection = object : ServiceConnection {

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ScreenshotServiceRework.LocalBinder
            val screenshotService = binder.getService()
            isBound = true
            // Register callback
            screenshotService.onScreenshotTaken = { bitmap ->
                // Handle the captured screenshot
                logger.DEBUG("Screenshot received! ${bitmap.width}x${bitmap.height}")
                // Do OCR or show it
                showOCRScreen()
            }

            // Trigger screenshot
            screenshotService.requestCapture()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
//            screenshotService = null
        }
    }

    private fun createComposeView(content: ComposableContent) : ComposeView {
        return ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeViewModelStoreOwner(
                viewModelStoreOwner = object : ViewModelStoreOwner {
                    override val viewModelStore: ViewModelStore
                        get() = ViewModelStore()
                }
            )
            setContent {
                content()
            }
        }
    }

    private fun showOCRScreen() {
        ocrScreen = createComposeView {
            OCRScreen(onClicked = {
                removeOverlay()}
            )
        }
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        windowManager.addView(ocrScreen,layoutParams)
    }
    private fun captureScreenshot() {
        if (isBound) {
            unbindService(connection)
        }
        logger.INFO("Requesting screenshot capture")
        val intent = Intent(this@OverlayService, ScreenshotServiceRework::class.java).also {
            it.action = "CAPTURE_SCREENSHOT"
        }
        bindService(intent,connection, BIND_AUTO_CREATE)


//        Intent(this@OverlayService, ScreenshotServiceRework::class.java).also {
//            it.action = "CAPTURE_SCREENSHOT"
//            startService(it)
//            showOCRScreen()
//        }

//        sudachiTokenizer = SudachiTokenizer(this.application)
//        ocrScreen = createComposeView {
//            OCRScreen(onClicked = {removeOverlay()}, sudachiTokenizer)
//        }
//        if (isBound) {
////            executeCapture()
//            val intent = Intent(this, ScreenshotPermissionActivity::class.java).apply {
//                flags = Intent.FLAG_ACTIVITY_NEW_TASK
//            }
//            startActivity(intent)
//        } else {
//            pendingCaptureRequest = true
//            bindToCaptureService()
//        }
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performAttach() // you can ignore this line, becase performRestore method will auto call performAttach() first.
        savedStateRegistryController.performRestore(null)
        startForegroundServiceWithNotification()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager


        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        layoutParams.gravity = Gravity.BOTTOM or Gravity.END
        layoutParams.x = currentX
        layoutParams.y = currentY
        composeView = createComposeView {
            DraggableOverlayContent(onCaptureClick = { captureScreenshot() }, onDrag = { dx, dy ->
                currentX -= dx.roundToInt()
                currentY -= dy.roundToInt()
                layoutParams.x = currentX
                layoutParams.y = currentY
                windowManager.updateViewLayout(composeView, layoutParams)
            })
        }

//        overlayView = composeView
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        windowManager.addView(composeView, layoutParams)
//        composeView.viewTreeObserver.addOnGlobalLayoutListener {
//            Log.d("OverlayService", "Post-layout size: width=${composeView.width}, height=${composeView.height}")
//        }

    }


    companion object {
        const val OVERLAY_NOTIFICATION_ID = 1770
    }
    private fun startForegroundServiceWithNotification() {
//        val channelId = "com.farhannz.kaitou.overlay_channel"
//        val channel = NotificationChannel(channelId, "Overlay", NotificationManager.IMPORTANCE_LOW)
//        val manager = getSystemService(NotificationManager::class.java)
//        manager.createNotificationChannel(channel)

        NotificationHelper.createNotificationChannels(this)

        val notification = NotificationCompat.Builder(this, NotificationHelper.OVERLAY_CHANNEL_ID)
            .setContentTitle("Kaitou")
            .setContentText("Kaitou is running")
            .setSmallIcon(R.drawable.ic_menu_view)
            .build()

//        Log.i("Overlay Service","Staring service....")
        logger.INFO("Staring service...")
        startForeground(OVERLAY_NOTIFICATION_ID, notification)
        logger.INFO("Service started!")
//        Log.i("Overlay Service","Overlay service started....")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::composeView.isInitialized) {
            windowManager.removeView(composeView)
        }
//        if (isBound) {
//            try {
//                unbindService(connection)
//            } catch (e: IllegalArgumentException) {
//                Log.e("OverlayService", "Service was not registered: ${e.message}")
//            }
//        }
        unbindService(connection)
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED // Important for cleanup
    }


    private fun removeOverlay() {
        ocrScreen?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                logger.ERROR("Failed to remove overlay")
//                Log.e("Overlay", "Failed to remove overlay", e)
            }
            ocrScreen = null
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

}

