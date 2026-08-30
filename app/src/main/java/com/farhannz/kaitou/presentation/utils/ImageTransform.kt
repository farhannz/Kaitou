package com.farhannz.kaitou.presentation.utils

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.min

/**
 * Single source of truth for mapping between image pixel space (the screenshot
 * the OCR ran on) and canvas space (the Compose overlay). Drawing and
 * hit-testing MUST both use the same instance/derivation so taps can never
 * drift away from the drawn boxes.
 */
data class ImageTransform(val scale: Float, val offsetX: Float, val offsetY: Float) {

    fun toScreen(p: Offset): Offset = Offset(p.x * scale + offsetX, p.y * scale + offsetY)

    fun toImage(p: Offset): Offset = Offset((p.x - offsetX) / scale, (p.y - offsetY) / scale)

    companion object {
        /**
         * Uniform fit (letterboxed) of [imageSize] into [canvasSize]. With a
         * full-display screenshot drawn on an edge-to-edge canvas the scale is
         * 1 and offsets are 0; the general form keeps the mapping correct if
         * the two ever diverge (e.g. cropped captures, different aspect).
         */
        fun fit(imageSize: Size, canvasSize: Size): ImageTransform {
            if (imageSize.width <= 0f || imageSize.height <= 0f ||
                canvasSize.width <= 0f || canvasSize.height <= 0f
            ) {
                return ImageTransform(1f, 0f, 0f)
            }
            val scale = min(
                canvasSize.width / imageSize.width,
                canvasSize.height / imageSize.height
            )
            return ImageTransform(
                scale = scale,
                offsetX = (canvasSize.width - imageSize.width * scale) / 2f,
                offsetY = (canvasSize.height - imageSize.height * scale) / 2f
            )
        }
    }
}
