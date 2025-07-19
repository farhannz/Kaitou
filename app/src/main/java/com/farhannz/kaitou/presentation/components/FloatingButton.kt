package com.farhannz.kaitou.presentation.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt


@Preview
@Composable
fun FloatingButtonExpand() {
    Column {
        Box {
            Text("Settings", color = Color.White)
        }
        Box {
            Text("Exit", color = Color.White)
        }
    }
}

// <<< NEW: single menu row
@Composable
fun MenuItem(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        modifier = Modifier
            .clickable { onClick() }
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        color = Color.Black
    )
}

@Composable
private fun PopupMenuAbove(
    anchorBounds: Rect,               // position of the button in window coordinates
    onDismiss: () -> Unit,
    content: @Composable (() -> Unit)
) {
    val density = LocalDensity.current
    val popupOffset = remember(anchorBounds) {
        // popup top-left = (center-x of button, top of button - menu height)
        // We let Compose measure the menu, so we only know the offset in onGloballyPositioned
        IntOffset(
            x = (anchorBounds.left + anchorBounds.width / 2).toInt(),
            y = anchorBounds.top.toInt()
        )
    }

    Popup(
        popupPositionProvider = remember {
            object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect,
                    windowSize: IntSize,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize
                ): IntOffset {
                    return IntOffset(
                        // horizontally centered
                        x = popupOffset.x - popupContentSize.width / 2,
                        // vertically above the button
                        y = popupOffset.y - popupContentSize.height
                    )
                }
            }
        },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) { content() }
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

    // <<< NEW: local state for the menu
    var showMenu by remember { mutableStateOf(false) }

    // <<< NEW: we need the local density to convert pixels → dp
    val density = LocalDensity.current
    var buttonBounds by remember { mutableStateOf(Rect.Zero) }
    // <<< NEW: we wrap everything in a Box so we can anchor the popup
    Box(modifier = Modifier.onGloballyPositioned { layoutCoordinates ->
        buttonBounds = layoutCoordinates.boundsInWindow()
    }) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .graphicsLayer {
                    alpha = if (isButtonVisibleState.value) 1f else 0f
                }
        ) {
            Box(
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showMenu = true                 // <<< NEW
                                onLongPress()
                            },
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
                        ) { _, dragAmount ->
                            onDrag(
                                dragAmount.x.roundToInt(),
                                dragAmount.y.roundToInt()
                            )
                        }
                    }
                    .size(60.dp)
                    .background(Color(0xAA000000))
                    .align(Alignment.End),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Camera, contentDescription = "Capture", tint = Color.White)
            }
        }


        // <<< NEW: render the popup on top
        if (showMenu) {
            PopupMenuAbove(
                anchorBounds = buttonBounds,
                onDismiss = { showMenu = false }
            ) {
                val context = LocalContext.current
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .fillMaxWidth(0.05f)
                ) {
                    MenuItem("Option 1") { /* TODO */ }
                    MenuItem("Option 2") { /* TODO */ }
                    MenuItem("Shutdown") {
//                        val stopOverlayService = Intent(context, OverlayService::class.java)
//                        val stopScreenshotService = Intent(context, ScreenshotServiceRework::class.java)
//                        context.stopService(stopOverlayService)
//                        context.stopService(stopScreenshotService)
                        context.sendBroadcast(Intent("SHUTDOWN_SERVICES"))
                    }
                }
            }
        }
    }
}