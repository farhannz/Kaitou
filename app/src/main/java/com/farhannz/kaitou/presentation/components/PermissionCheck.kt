package com.farhannz.kaitou.presentation.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Monitor
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.farhannz.kaitou.MediaProjectionPermissionStore

enum class KaitouPermission {
    Overlay,
    Capture,
    Notifications
}

data class PermissionState(
    val overlay: Boolean,
    val capture: Boolean,
    val notifications: Boolean
) {
    val allGranted: Boolean
        get() = overlay && capture && notifications
}

private fun computePermissionState(context: Context): PermissionState {
    val notificationsGranted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
    return PermissionState(
        overlay = Settings.canDrawOverlays(context),
        capture = MediaProjectionPermissionStore.isValid(context),
        notifications = notificationsGranted
    )
}

/**
 * Observable permission state. All three permissions are granted through
 * system UI, so the app is backgrounded whenever they change; re-compute on
 * every ON_RESUME to avoid stale rows after returning from Settings/dialogs.
 */
@Composable
fun rememberPermissionState(): PermissionState {
    val context = LocalContext.current
    var state by remember { mutableStateOf(computePermissionState(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                state = computePermissionState(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return state
}

@Composable
fun PermissionCheckCard(
    state: PermissionState,
    onGrant: (KaitouPermission) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        PermissionRow(
            icon = Icons.Rounded.Layers,
            title = "Display over other apps",
            description = "Shows the floating bubble on top of other apps",
            granted = state.overlay,
            onGrant = { onGrant(KaitouPermission.Overlay) }
        )
        HorizontalDivider()
        PermissionRow(
            icon = Icons.Rounded.Monitor,
            title = "Screen capture",
            description = "Reads Japanese text from your screen",
            granted = state.capture,
            onGrant = { onGrant(KaitouPermission.Capture) }
        )
        HorizontalDivider()
        PermissionRow(
            icon = Icons.Rounded.Notifications,
            title = "Notifications",
            description = "Keeps Kaitou visible while running in the background",
            granted = state.notifications,
            onGrant = { onGrant(KaitouPermission.Notifications) }
        )
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    onGrant: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (granted) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (granted) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = "Granted",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = "Not granted",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onGrant) {
                Text(text = "Grant", textAlign = TextAlign.Center)
            }
        }
    }
}
