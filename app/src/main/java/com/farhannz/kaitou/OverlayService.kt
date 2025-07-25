package com.farhannz.kaitou

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.farhannz.kaitou.helpers.Logger
import com.farhannz.kaitou.helpers.NotificationHelper
import com.farhannz.kaitou.impl.ScreenshotStore
import com.farhannz.kaitou.presentation.components.FloatingButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch


typealias ComposableContent = @Composable () -> Unit

class OverlayService() : Service(), SavedStateRegistryOwner {
    private lateinit var composeView: ComposeView
    private lateinit var windowManager: WindowManager
    private val LOG_TAG = OverlayService::class.simpleName
    private val logger = Logger(LOG_TAG!!)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val isButtonVisibleState = mutableStateOf(true)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)


    private var currentX = 0
    private var currentY = 100


    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    companion object {
        const val OVERLAY_NOTIFICATION_ID = 1770
    }

    private val overlayBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "SHUTDOWN_SERVICES" -> {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }

                "OVERLAY_DISMISSED" -> removeOverlay()
            }
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

    private fun captureScreenshot() {
        isButtonVisibleState.value = false
        logger.INFO("Requesting screenshot capture")
        logger.DEBUG("${MainActivity.MediaProjectionPermissionStore.resultCode} - ${MainActivity.MediaProjectionPermissionStore.dataIntent}")
        val intent = Intent(this@OverlayService, ScreenshotServiceRework::class.java).also {
            it.action = "CAPTURE_SCREENSHOT"
            it.putExtra("resultCode", MainActivity.MediaProjectionPermissionStore.resultCode)
            it.putExtra("data", MainActivity.MediaProjectionPermissionStore.dataIntent)
        }
        startService(intent)
        scope.launch {
            // Act as overlay trigger after screenshot is ready
            ScreenshotStore.latestScreenshot
                .filterNotNull()
                .first()
            val intent = Intent(this@OverlayService, OverlayHostActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate() {
        super.onCreate()
        registerReceiver(overlayBroadcastReceiver, IntentFilter().apply {
            addAction("SHUTDOWN_SERVICES")
            addAction("OVERLAY_DISMISSED")
        }, RECEIVER_NOT_EXPORTED)
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

        logger.INFO("Staring service...")
        startForeground(OVERLAY_NOTIFICATION_ID, notification)
        logger.INFO("Service started!")
    }

    override fun onDestroy() {
        removeOverlay()
        if (::composeView.isInitialized) {
            windowManager.removeView(composeView)
        }
        unregisterReceiver(overlayBroadcastReceiver)
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED // Important for cleanup
        super.onDestroy()
    }


    fun removeOverlay() {
        isButtonVisibleState.value = true
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

}

