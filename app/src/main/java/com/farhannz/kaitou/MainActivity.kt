package com.farhannz.kaitou

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.farhannz.kaitou.helpers.DatabaseManager
import com.farhannz.kaitou.helpers.Logger
import com.farhannz.kaitou.paddle.OCRPipeline
import kotlinx.coroutines.launch
import org.opencv.android.OpenCVLoader

class MainActivity : ComponentActivity() {

    private val LOG_TAG = MainActivity::class.simpleName
    private val logger = Logger(LOG_TAG!!)
    private var overlayGranted =  mutableStateOf(false)
    private var screenshotGranted =  mutableStateOf(false)

    object MediaProjectionPermissionStore {
        var resultCode: Int = Int.MIN_VALUE
        var dataIntent: Intent? = null
    }

    private fun tryMoveToBackground() {
        if (overlayGranted.value && screenshotGranted.value) {
            moveTaskToBack(true)
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            startOverlayService()
            overlayGranted.value = true
            tryMoveToBackground()
        } else {
            Toast.makeText(this, "Overlay permission is required!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            overlayPermissionLauncher.launch(intent)
        } else {
            startOverlayService()
            overlayGranted.value = true
            tryMoveToBackground()
        }
    }

    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        ContextCompat.startForegroundService(this, intent)
//        moveTaskToBack(true)
    }


//    This is for the reworked version of ScreenshotService
//    Requesting Permission with the intent of Starting Service
//    and caching the permission result via putExtra
    private val screenshotPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            logger.DEBUG("${result.resultCode} - ${result.data}")
            val intent = Intent(this, ScreenshotServiceRework::class.java).apply {
                action = "START_SERVICE"
                putExtra("resultCode", result.resultCode)
                putExtra("data", result.data)
                MediaProjectionPermissionStore.dataIntent = result.data
                MediaProjectionPermissionStore.resultCode = result.resultCode
            }
            screenshotGranted.value = true
            ContextCompat.startForegroundService(this, intent)
        } else {
            // Permission denied
            Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
        }
}
    private fun requestScreenShotPermission() {
        val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = mediaProjectionManager.createScreenCaptureIntent()
        screenshotPermissionLauncher.launch(intent)
    }

    override fun onResume() {
        super.onResume()
        if (Settings.canDrawOverlays(this) && !overlayGranted.value) {
            overlayGranted.value = true
            startOverlayService()
            tryMoveToBackground()
        } else {
            requestOverlayPermission()
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (OpenCVLoader.initLocal()) {
            logger.DEBUG("OpenCV initialized successfully")
        } else {
            logger.ERROR("OpenCV initialization failed")
        }
//        database = DictionaryDatabase.getDatabase(this)
        enableEdgeToEdge()
        OCRPipeline.initialize(this)
        lifecycleScope.launch {
//            try {
//                System.loadLibrary("paddle_lite_jni")
//                val dummy = Mat.zeros(1000,1000, CvType.CV_8UC3)
//                Imgproc.putText(
//                    dummy,
//                    "THIS IS ONLY A TEST", // Corrected typo
//                    Point(50.0, 100.0),   // Adjusted position to fit better
//                    Imgproc.FONT_HERSHEY_COMPLEX,
//                    3.0,
//                    Scalar(0.0, 255.0, 255.0) // Yellow color in BGR
//                )
//
//                // Add the second line of text
//                Imgproc.putText(
//                    dummy,
//                    "MENGHADEUH CUY",
//                    Point(50.0, 300.0),   // Adjusted position to fit better
//                    Imgproc.FONT_HERSHEY_COMPLEX,
//                    3.0,
//                    Scalar(0.0, 255.0, 255.0) // Yellow color in BGR
//                )
//
//                val boxes = PredictorManager.runDetection(matToBitmap(dummy))
//                logger.DEBUG("${boxes.size}")
//            } catch (e: Throwable) {
//                logger.ERROR(e.message.toString())
//            }

            DatabaseManager.initializeWordsCache()
        }
        requestScreenShotPermission()
    }
}
