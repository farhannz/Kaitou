package com.farhannz.kaitou

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.projection.*
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.NotificationCompat
import androidx.lifecycle.*
import androidx.savedstate.*
import com.farhannz.kaitou.helpers.Logger
import com.farhannz.kaitou.helpers.NotificationHelper
import com.farhannz.kaitou.ui.components.DraggableOverlayContent
import com.farhannz.kaitou.ui.components.OCRScreen
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
    private val isButtonVisibleState = mutableStateOf(true)

    private lateinit var mediaProjectionManager: MediaProjectionManager


    private var currentX = 0
    private var currentY = 100
    private var ocrScreen: ComposeView? = null


    override val lifecycle: Lifecycle
        get() = lifecycleRegistry


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
                showOCRScreen(bitmap)
                isButtonVisibleState.value = true
            }
//            // Trigger screenshot
            screenshotService.rc = MainActivity.MediaProjectionPermissionStore.resultCode
            screenshotService.dataIntent = MainActivity.MediaProjectionPermissionStore.dataIntent
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

    private fun showOCRScreen(image: Bitmap) {
        ocrScreen = createComposeView {
            OCRScreen(onClicked = {
                    removeOverlay()
                },
                inputImage = image
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
        isButtonVisibleState.value = false
        if (isBound) {
            unbindService(connection)
        }
        logger.INFO("Requesting screenshot capture")
        logger.DEBUG("${MainActivity.MediaProjectionPermissionStore.resultCode} - ${MainActivity.MediaProjectionPermissionStore.dataIntent}")
        val intent = Intent(this@OverlayService, ScreenshotServiceRework::class.java).also {
            it.action = "CAPTURE_SCREENSHOT"
            it.putExtra("resultCode", MainActivity.MediaProjectionPermissionStore.resultCode)
            it.putExtra("data", MainActivity.MediaProjectionPermissionStore.dataIntent)
        }
        bindService(intent,connection, BIND_AUTO_CREATE)
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
                },
                isButtonVisibleState.value
            )
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
        NotificationHelper.createNotificationChannels(this)

        val notification = NotificationCompat.Builder(this, NotificationHelper.OVERLAY_CHANNEL_ID)
            .setContentTitle("Kaitou")
            .setContentText("Kaitou is running")
            .setSmallIcon(android.R.drawable.ic_notification_overlay)
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
        if (isBound) {
            unbindService(connection)
        }
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

