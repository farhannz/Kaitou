package com.farhannz.kaitou.ui.components

import android.widget.Button
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt


@Preview
@Composable
fun FloatingButtonExpand() {
    Column {
        Text("Settings")
        Text("Exit")
    }
}


@Composable
fun FloatingButton(
    onDrag: (Int, Int) -> Unit,
    onLongPress: () -> Unit,
    onTap: () -> Unit,
    isButtonVisibleState: MutableState<Boolean>
) {
    val haptic = LocalHapticFeedback.current
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .graphicsLayer {
                alpha = if (isButtonVisibleState.value) 1f else 0f
            },
    ) {
        Box(
            modifier = Modifier
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { onLongPress() },
                        onTap = { onTap() }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDragEnd = {
                            haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                        }
                    ) { change, dragAmount ->
                        onDrag(dragAmount.x.roundToInt(), dragAmount.y.roundToInt())
                    }
                }
                .size(60.dp)
                .background(Color(0xAA000000)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Camera, contentDescription = "Capture", tint = Color.White)
        }
    }
}