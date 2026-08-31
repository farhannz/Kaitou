package com.farhannz.kaitou.impl

import android.graphics.Bitmap
import android.graphics.Rect
import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A captured screenshot together with the provenance needed to map OCR
 * coordinates (bitmap pixel space) back onto the display.
 *
 * @param bitmapRectInDisplay the region of the physical display the bitmap
 *   covers, in display pixels. Equal to the full display bounds while the
 *   service captures uncropped full frames; if the capture is ever cropped
 *   (e.g. system bars removed), this rect records exactly where the cropped
 *   content sits so the visualization layer can compensate.
 * @param rotation the display rotation at capture time
 *   ([Surface.ROTATION_0], [Surface.ROTATION_90], ...).
 */
data class ScreenshotData(
    val id: Long,
    val bitmap: Bitmap,
    val bitmapRectInDisplay: Rect = Rect(0, 0, bitmap.width, bitmap.height),
    val rotation: Int = Surface.ROTATION_0
)

object ScreenshotStore {
    private val _latestScreenshot = MutableStateFlow<ScreenshotData?>(null)
    val latestScreenshot = _latestScreenshot.asStateFlow()

    fun updateScreenshot(bitmap: Bitmap, bitmapRectInDisplay: Rect, rotation: Int) {
        _latestScreenshot.value =
            ScreenshotData(System.currentTimeMillis(), bitmap, bitmapRectInDisplay, rotation)
    }

    fun clear() {
        _latestScreenshot.value = null
    }
}
