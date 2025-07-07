package com.farhannz.kaitou

import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
//import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.NotificationCompat
import androidx.lifecycle.*
import androidx.savedstate.*
import com.farhannz.kaitou.ui.components.DraggableOverlayContent
import com.farhannz.kaitou.ui.components.OCRScreen
//import com.farhannz.kaitou.ui.viewmodels.SudachiTokenizer
import kotlinx.coroutines.delay
import kotlin.math.roundToInt


typealias ComposableContent = @Composable () -> Unit
class OverlayService() : Service(), SavedStateRegistryOwner {
    private lateinit var composeView: ComposeView
    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val lifecycleRegistry = LifecycleRegistry(this)

    private var currentX = 0
    private var currentY = 100

    private var ocrScreen: ComposeView? = null

//    private lateinit var sudachiTokenizer: SudachiTokenizer

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

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
    private fun captureScreenshot() {
        Log.i("Overlay Service", "Screenshot Taken")
//        sudachiTokenizer = SudachiTokenizer(this.application)
//        ocrScreen = createComposeView {
//            OCRScreen(onClicked = {removeOverlay()}, sudachiTokenizer)
//        }
        ocrScreen = createComposeView {
            OCRScreen(onClicked = {removeOverlay()})
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
        composeView.viewTreeObserver.addOnGlobalLayoutListener {
            Log.d("OverlayService", "Post-layout size: width=${composeView.width}, height=${composeView.height}")
        }

    }

    private fun startForegroundServiceWithNotification() {
        val channelId = "overlay_channel"
        val channel = NotificationChannel(channelId, "Overlay", NotificationManager.IMPORTANCE_LOW)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Overlay Service")
            .setContentText("Overlay is running")
            .setSmallIcon(R.drawable.ic_menu_view)
            .build()
        Log.i("Overlay Service","Staring service....")
        startForeground(1, notification)
        Log.i("Overlay Service","Overlay service started....")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::composeView.isInitialized) {
            windowManager.removeView(composeView)
        }
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED // Important for cleanup
    }


    private fun removeOverlay() {
        ocrScreen?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e("Overlay", "Failed to remove overlay", e)
            }
            ocrScreen = null
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

}

