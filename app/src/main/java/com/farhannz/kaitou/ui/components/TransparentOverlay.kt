package com.farhannz.kaitou.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.*
import kotlin.math.roundToInt

@Composable
fun TransparentOverlay(onCaptureClick: () -> Unit) {
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
    ) {
        FloatingActionButton(
            onClick = onCaptureClick,
            containerColor = Color(0xAA000000),
            contentColor = Color.White,
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                }
        ) {
            Icon(Icons.Default.Camera, contentDescription = "Capture")
        }
    }
}

@Composable
fun DraggableOverlayContent(onCaptureClick: () -> Unit, onDrag: (Float, Float) -> Unit) {
    // These offsets are relative to the *ComposeView itself*.
    // Since the ComposeView is now WRAP_CONTENT, and we're moving the entire window
    // with onDrag, these internal offsets can largely be removed, or used for
    // internal content positioning if needed. For a simple FAB, it's not needed.

    // Let's remove the internal offsetX/Y state from here and rely on onDrag
    // updating the WindowManager.
    FloatingActionButton(
        onClick = onCaptureClick,
        containerColor = Color(0xAA000000),
        contentColor = Color.White,
        modifier = Modifier
            .size(60.dp) // Give it a specific size
            .background(Color.Transparent) // Ensure the FAB background doesn't interfere
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    // Pass the drag amount up to the Service to update window position
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
    ) {
        Icon(Icons.Default.Camera, contentDescription = "Capture")
    }
}
