package com.farhannz.kaitou

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.farhannz.kaitou.helpers.Logger

/**
 * Invisible trampoline that only hosts the MediaProjection consent dialog.
 *
 * Launched by OverlayService (or ScreenshotServiceRework on token rejection)
 * when a capture is requested but no valid consent is stored. The inherited
 * application theme is translucent, so the dialog appears to float over the
 * app the user came from. On grant it persists the consent and starts
 * START_AND_CAPTURE; the pending capture in OverlayService then resumes and
 * the OCR screen appears. This activity never shows any Kaitou UI.
 */
class ConsentRequestActivity : ComponentActivity() {
    private val LOG_TAG = ConsentRequestActivity::class.simpleName
    private val logger = Logger(LOG_TAG!!)

    private val consentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            logger.DEBUG("${result.resultCode} - ${result.data}")
            MediaProjectionPermissionStore.save(this, result.resultCode, result.data!!)
            val intent = Intent(this, ScreenshotServiceRework::class.java).apply {
                action = "START_AND_CAPTURE"
                putExtra("resultCode", result.resultCode)
                putExtra("data", result.data)
            }
            ContextCompat.startForegroundService(this, intent)
        } else {
            Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mediaProjectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        consentLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }
}
