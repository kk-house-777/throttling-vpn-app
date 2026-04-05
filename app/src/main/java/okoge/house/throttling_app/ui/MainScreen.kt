package okoge.house.throttling_app.ui

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.ln
import kotlin.math.exp
import kotlin.math.roundToInt

enum class VpnMode {
    Block,
    Throttle,
    Unlimited,
}

@Composable
fun MainScreen(
    isVpnRunning: Boolean,
    isTransitioning: Boolean,
    selectedMode: VpnMode,
    sliderValue: Float,
    targetAppCount: Int,
    onModeChange: (VpnMode) -> Unit,
    onSliderChange: (Float) -> Unit,
    onSliderChangeFinished: () -> Unit,
    onStartStop: () -> Unit,
    onNavigateToAppList: () -> Unit,
    onNavigateToLicenses: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = "Throttle VPN",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = when {
                !isVpnRunning -> "VPN is OFF"
                selectedMode == VpnMode.Block -> "VPN is ON — BLOCKED"
                selectedMode == VpnMode.Unlimited -> "VPN is ON — Unlimited"
                else -> "VPN is ON — ${formatKbps(sliderToKbps(sliderValue))}"
            },
            style = MaterialTheme.typography.titleLarge,
            color = if (isVpnRunning) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Target: $targetAppCount apps")
        TextButton(onClick = onNavigateToAppList) {
            Text("Manage →")
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Mode selector
        Text(
            text = "Mode",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ModeButton("Block", selectedMode == VpnMode.Block, { onModeChange(VpnMode.Block) }, Modifier.weight(1f))
            ModeButton("Throttle", selectedMode == VpnMode.Throttle, { onModeChange(VpnMode.Throttle) }, Modifier.weight(1f))
            ModeButton("Unlimited", selectedMode == VpnMode.Unlimited, { onModeChange(VpnMode.Unlimited) }, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(24.dp))

        val isThrottleMode = selectedMode == VpnMode.Throttle
        val kbps = sliderToKbps(sliderValue)

        Text(
            text = formatKbps(kbps),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = if (isThrottleMode) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            },
        )

        Text(
            text = networkGeneration(kbps),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Slider(
            value = sliderValue,
            onValueChange = onSliderChange,
            onValueChangeFinished = onSliderChangeFinished,
            valueRange = 0f..1f,
            enabled = isThrottleMode,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("50 kbps", style = MaterialTheme.typography.bodySmall)
            Text("5 Mbps", style = MaterialTheme.typography.bodySmall)
        }

        Text(
            text = "Actual speed may vary depending on conditions.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(32.dp))

        val canStart = isVpnRunning || targetAppCount > 0
        Button(
            onClick = onStartStop,
            enabled = canStart && !isTransitioning,
            modifier = Modifier.fillMaxWidth(),
            colors = if (isVpnRunning) {
                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            } else {
                ButtonDefaults.buttonColors()
            },
        ) {
            if (isTransitioning) {
                CircularProgressIndicator()
            } else {
                Text(
                    text = if (isVpnRunning) "Stop VPN" else "Start VPN",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (!canStart) {
                "Add target apps first to start VPN."
            } else {
                "VPN stops automatically when the app is closed."
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (!canStart) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )

        // TODO: Enable after aboutlibraries plugin is configured
        // Spacer(modifier = Modifier.height(16.dp))
        // TextButton(onClick = onNavigateToLicenses) {
        //     Text("Open Source Licenses")
        // }
    }
}

@Composable
private fun ModeButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(label) }
    }
}

// Logarithmic scale mapping: Slider 0.0–1.0 → 50–5000 kbps.
// Low speeds (50–384 kbps) get finer control, high speeds (1–5 Mbps) coarser.

private const val MIN_KBPS = 50f     // ~2G
private const val MAX_KBPS = 5000f   // ~3G/HSPA+
private val LN_MIN = ln(MIN_KBPS)
private val LN_MAX = ln(MAX_KBPS)

/** Slider value (0..1) → kbps */
fun sliderToKbps(slider: Float): Int {
    val kbps = exp(LN_MIN + slider * (LN_MAX - LN_MIN))
    return kbps.roundToInt().coerceIn(MIN_KBPS.toInt(), MAX_KBPS.toInt())
}

/** kbps → Slider value (0..1) */
fun kbpsToSlider(kbps: Int): Float {
    return ((ln(kbps.toFloat()) - LN_MIN) / (LN_MAX - LN_MIN)).coerceIn(0f, 1f)
}

/** kbps → KB/s (value passed to Go layer) */
fun kbpsToKBps(kbps: Int): Int {
    return (kbps / 8f).roundToInt().coerceAtLeast(1)
}

/** kbps → display string */
fun formatKbps(kbps: Int): String {
    return if (kbps >= 1000) {
        String.format("%.1f Mbps", kbps / 1000f)
    } else {
        "$kbps kbps"
    }
}

/** kbps → network generation label */
fun networkGeneration(kbps: Int): String {
    return when {
        kbps <= 100 -> "≈ 2G"
        kbps <= 500 -> "≈ Slow 3G"
        kbps <= 2000 -> "≈ Fast 3G"
        else -> "≈ 3G/HSPA+"
    }
}
