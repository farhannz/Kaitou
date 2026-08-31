package com.farhannz.kaitou.presentation.components

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Monitor
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.farhannz.kaitou.OcrEngineProvider
import com.farhannz.kaitou.OverlayService

private const val PREFS_FILE = "kaitou_prefs"
private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"

private enum class OnboardingStep { Welcome, Permissions, Warmup }

/**
 * Root router for MainActivity.
 *
 * First run: onboarding (welcome -> permissions -> warm-up -> finish).
 * Subsequent runs: landing page with status and quick actions.
 *
 * Note: bubble-triggered capture re-consent does NOT go through here —
 * ConsentRequestActivity hosts that dialog invisibly over the user's
 * current app.
 */
@Composable
fun KaitouApp(
    onGrantOverlay: () -> Unit,
    onGrantCapture: () -> Unit,
    onGrantNotifications: () -> Unit,
    onStartOverlayService: () -> Unit,
    onStopServices: () -> Unit,
    onMinimize: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE) }
    var onboarded by remember {
        mutableStateOf(prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false))
    }
    val permissionState = rememberPermissionState()
    val engineState by OcrEngineProvider.state.collectAsState()

    val useDarkTheme = isSystemInDarkTheme()
    val colors =
        if (useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    MaterialTheme(colorScheme = colors) {
        Surface(modifier = Modifier.fillMaxSize()) {
            if (!onboarded) {
                OnboardingFlow(
                    permissionState = permissionState,
                    engineState = engineState,
                    onGetStarted = onGrantNotifications,
                    onGrant = { permission ->
                        when (permission) {
                            KaitouPermission.Overlay -> onGrantOverlay()
                            KaitouPermission.Capture -> onGrantCapture()
                            KaitouPermission.Notifications -> onGrantNotifications()
                        }
                    },
                    onFinish = {
                        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETE, true).apply()
                        onboarded = true
                        onStartOverlayService()
                        onMinimize()
                    }
                )
            } else {
                LandingScreen(
                    permissionState = permissionState,
                    engineState = engineState,
                    overlayRunning = OverlayService.isRunning.value,
                    onGrant = { permission ->
                        when (permission) {
                            KaitouPermission.Overlay -> onGrantOverlay()
                            KaitouPermission.Capture -> onGrantCapture()
                            KaitouPermission.Notifications -> onGrantNotifications()
                        }
                    },
                    onStartService = onStartOverlayService,
                    onStopServices = onStopServices,
                    onMinimize = onMinimize
                )
            }
        }
    }
}

@Composable
private fun OnboardingFlow(
    permissionState: PermissionState,
    engineState: OcrEngineProvider.EngineState,
    onGetStarted: () -> Unit,
    onGrant: (KaitouPermission) -> Unit,
    onFinish: () -> Unit
) {
    var step by remember { mutableStateOf(OnboardingStep.Welcome) }

    // Auto-advance as soon as every permission is granted. Permissions are
    // granted in system UI, so this fires right after the user returns.
    LaunchedEffect(permissionState.allGranted, step) {
        if (step == OnboardingStep.Permissions && permissionState.allGranted) {
            step = OnboardingStep.Warmup
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (step) {
            OnboardingStep.Welcome -> {
                Icon(
                    imageVector = Icons.Rounded.Translate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Kaitou", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Instant Japanese OCR. Tap the floating bubble to scan text " +
                            "on your screen, then tap any result for dictionary lookup.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = {
                    step = OnboardingStep.Permissions
                    onGetStarted()
                }) {
                    Text(text = "Get started")
                }
            }

            OnboardingStep.Permissions -> {
                Text(text = "Set up permissions", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Kaitou needs the following to work",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                PermissionCheckCard(state = permissionState, onGrant = onGrant)
            }

            OnboardingStep.Warmup -> {
                WarmupContent(
                    engineState = engineState,
                    finishLabel = "Finish",
                    onFinish = onFinish
                )
            }
        }
    }
}

@Composable
private fun WarmupContent(
    engineState: OcrEngineProvider.EngineState,
    finishLabel: String,
    onFinish: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (engineState) {
            is OcrEngineProvider.EngineState.Ready -> {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                )
                Text(text = "You're all set", style = MaterialTheme.typography.headlineSmall)
                Button(onClick = onFinish) {
                    Text(text = finishLabel)
                }
            }

            is OcrEngineProvider.EngineState.Error -> {
                Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(56.dp)
                )
                Text(
                    text = "OCR engine failed to load",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = engineState.throwable.message ?: "Unknown error",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            else -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.6f))
                Text(text = "Warming up OCR engine…", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "First load can take a few seconds",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LandingScreen(
    permissionState: PermissionState,
    engineState: OcrEngineProvider.EngineState,
    overlayRunning: Boolean,
    onGrant: (KaitouPermission) -> Unit,
    onStartService: () -> Unit,
    onStopServices: () -> Unit,
    onMinimize: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "Kaitou", style = MaterialTheme.typography.headlineMedium)

        StatusRow(
            icon = Icons.Rounded.Layers,
            label = if (overlayRunning) "Overlay service running" else "Overlay service stopped",
            ok = overlayRunning
        )

        when (engineState) {
            is OcrEngineProvider.EngineState.Ready -> StatusRow(
                icon = Icons.Rounded.Monitor,
                label = "OCR engine ready",
                ok = true
            )

            is OcrEngineProvider.EngineState.Error -> Column {
                StatusRow(
                    icon = Icons.Rounded.ErrorOutline,
                    label = "OCR engine failed to load",
                    ok = false
                )
                TextButton(onClick = { OcrEngineProvider.retry(context) }) {
                    Text(text = "Retry")
                }
            }

            else -> Column {
                Text(text = "Loading OCR models…", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        if (!permissionState.allGranted) {
            Text(
                text = "Some permissions are missing",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            PermissionCheckCard(state = permissionState, onGrant = onGrant)
        }

//        DetectionBenchmarkCard()

        Spacer(modifier = Modifier.weight(1f))

        if (overlayRunning) {
            Button(onClick = onStopServices, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Stop Kaitou")
            }
        } else {
            Button(
                onClick = onStartService,
                enabled = permissionState.overlay && permissionState.capture,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Start overlay service")
            }
        }
        OutlinedButton(onClick = onMinimize, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Minimize")
        }
    }
}

@Composable
private fun StatusRow(icon: ImageVector, label: String, ok: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        if (ok) {
            Box(modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Ready",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }
    }
}