package com.farhannz.kaitou.impl

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SystemInsets(val left: Int, val top: Int, val right: Int, val bottom: Int)

data class ScreenshotData(
    val id: Long,
    val bitmap: Bitmap,
    val insets: SystemInsets = SystemInsets(0, 0, 0, 0)
)

object ScreenshotStore {
    private val _latestScreenshot = MutableStateFlow<ScreenshotData?>(null)
    val latestScreenshot = _latestScreenshot.asStateFlow()

    fun updateScreenshot(bitmap: Bitmap) {
        _latestScreenshot.value = ScreenshotData(System.currentTimeMillis(), bitmap)
    }

    fun clear() {
        _latestScreenshot.value = null
    }
}