package com.farhannz.kaitou.helpers

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.farhannz.kaitou.OverlayService

class ScreenshotPermissionActivity : ComponentActivity() {
//    private val permissionLauncher = registerForActivityResult(
//        ActivityResultContracts.StartActivityForResult()
//    ) { result ->
//        val overlayService = getSystemService(NotificationHelper.CAPTURE_CHANNEL_ID) as? OverlayService
//        if (overlayService != null) {
////            overlayService.handleCaptureResult(result.resultCode, result.data)
//        } else {
//            // Fallback if service isn't available
//            Toast.makeText(
//                this,
//                "Overlay service not running",
//                Toast.LENGTH_SHORT
//            ).show()
//        }
//        finish()
//    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        sendBroadcast(Intent("CAPTURE_RESULT").apply {
            putExtra("resultCode", result.resultCode)
            putExtra("data", result.data)
        })
        finish()
    }


    @SuppressLint("ServiceCast")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        permissionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }
}