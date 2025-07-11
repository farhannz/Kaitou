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
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.farhannz.kaitou.bridges.OCRBridge.prepareInitModel
import com.farhannz.kaitou.helpers.DatabaseManager
import com.farhannz.kaitou.helpers.Logger
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {

    private val LOG_TAG = MainActivity::class.simpleName
    private val logger = Logger(LOG_TAG!!)

    object MediaProjectionPermissionStore {
        var resultCode: Int = Int.MIN_VALUE
        var dataIntent: Intent? = null
    }
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            startOverlayService()
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
        }
    }

    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        ContextCompat.startForegroundService(this, intent)
        moveTaskToBack(true)
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
                requestOverlayPermission()
            }
            ContextCompat.startForegroundService(this, intent)
            moveTaskToBack(true)
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        database = DictionaryDatabase.getDatabase(this)
        enableEdgeToEdge()
        lifecycleScope.launch {
            DatabaseManager.initializeWordsCache()
//            val dictionaryDao = DatabaseManager.getDatabase().dictionaryDao()
//            logger.DEBUG(dictionaryDao.lookupWordsByText("たべます").toString())
        }
        requestScreenShotPermission()
//        prepareJmdictJson(this)
        prepareInitModel(application)
    }
}
