package com.farhannz.kaitou

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.*
import androidx.savedstate.*
import com.farhannz.kaitou.helpers.Logger
import com.farhannz.kaitou.helpers.NotificationHelper
import com.farhannz.kaitou.ui.components.FloatingButton
import com.farhannz.kaitou.ui.components.OCRScreen
import kotlin.math.roundToInt


typealias ComposableContent = @Composable () -> Unit

class OverlayService() : Service(), SavedStateRegistryOwner {
    private lateinit var composeView: ComposeView
    private lateinit var windowManager: WindowManager
    private val LOG_TAG = OverlayService::class.simpleName
    private val logger = Logger(LOG_TAG!!)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val lifecycleRegistry = LifecycleRegistry(this)
    private var isBound = false
    private val isButtonVisibleState = mutableStateOf(true)

    private var overlayActive = false


    private var currentX = 0
    private var currentY = 100
    private var ocrScreen: ComposeView? = null


    override val lifecycle: Lifecycle
        get() = lifecycleRegistry
    var hostActivity: OverlayHostActivity? = null

    companion object {
        const val OVERLAY_NOTIFICATION_ID = 1770
        var instance: OverlayService? = null
            private set
    }

    private val connection = object : ServiceConnection {

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ScreenshotServiceRework.LocalBinder
            val screenshotService = binder.getService()
            isBound = true
            screenshotService.onScreenshotTaken = { bitmap ->
                logger.DEBUG("Screenshot received! ${bitmap.width}x${bitmap.height}")
                showOCRScreen(bitmap)
            }
            screenshotService.rc = MainActivity.MediaProjectionPermissionStore.resultCode
            screenshotService.dataIntent = MainActivity.MediaProjectionPermissionStore.dataIntent
            screenshotService.requestCapture()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
//            screenshotService = null
        }
    }


    private fun createComposeView(content: ComposableContent): ComposeView {
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
        val intent = Intent(this, OverlayHostActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        ocrScreen = createComposeView {
            OCRScreen(
                onClicked = {
                    removeOverlay()
                    hostActivity?.finish()
                },
                inputImage = image
            )
        }
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        windowManager.addView(ocrScreen, layoutParams)
        overlayActive = true
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
        bindService(intent, connection, BIND_AUTO_CREATE)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
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
            val haptic = LocalHapticFeedback.current
            FloatingButton(
                onDrag = { dx, dy ->
                    currentX -= dx
                    currentY -= dy
                    layoutParams.x = currentX
                    layoutParams.y = currentY
                    windowManager.updateViewLayout(composeView, layoutParams)
                },
                onTap = {
                    captureScreenshot()
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                },
                onLongPress = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    logger.DEBUG("LONG PRESSED")
                },
                isButtonVisibleState = isButtonVisibleState
            )
        }
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        windowManager.addView(composeView, layoutParams)
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
        instance = null
    }


    fun removeOverlay() {
        ocrScreen?.let {
            try {
                if (overlayActive) {
                    windowManager.removeView(it)
                    overlayActive = false
                }
            } catch (e: Exception) {
                logger.ERROR("Failed to remove overlay ${e.message.toString()}")
            }
            isButtonVisibleState.value = true
            ocrScreen = null
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

}

