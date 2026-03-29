package okoge.house.throttling_app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
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

        // Slider（対数スケール: 50kbps〜5Mbps）
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
            text = "※ 実際の速度は環境により異なります",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onStartStop,
            enabled = !isTransitioning,
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

        // TODO: aboutlibraries プラグインの設定後に有効化
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

// --- 対数スケール変換 ---
// Slider の 0.0〜1.0 を 50kbps〜5000kbps に対数マッピングする。
// 対数スケールにすることで、低速側（50〜384kbps）を細かく、
// 高速側（1Mbps〜5Mbps）を粗く操作できる。

private const val MIN_KBPS = 50f     // 2G 相当
private const val MAX_KBPS = 5000f   // 標準 3G/HSPA+ 相当
private val LN_MIN = ln(MIN_KBPS)
private val LN_MAX = ln(MAX_KBPS)

/** Slider 値 (0..1) → kbps */
fun sliderToKbps(slider: Float): Int {
    val kbps = exp(LN_MIN + slider * (LN_MAX - LN_MIN))
    return kbps.roundToInt().coerceIn(MIN_KBPS.toInt(), MAX_KBPS.toInt())
}

/** kbps → Slider 値 (0..1) */
fun kbpsToSlider(kbps: Int): Float {
    return ((ln(kbps.toFloat()) - LN_MIN) / (LN_MAX - LN_MIN)).coerceIn(0f, 1f)
}

/** kbps → KB/s（Go 側に渡す値） */
fun kbpsToKBps(kbps: Int): Int {
    return (kbps / 8f).roundToInt().coerceAtLeast(1)
}

/** kbps → 表示文字列 */
fun formatKbps(kbps: Int): String {
    return if (kbps >= 1000) {
        String.format("%.1f Mbps", kbps / 1000f)
    } else {
        "$kbps kbps"
    }
}

/** kbps → 世代ラベル */
fun networkGeneration(kbps: Int): String {
    return when {
        kbps <= 100 -> "≈ 2G"
        kbps <= 500 -> "≈ 低速 3G"
        kbps <= 2000 -> "≈ Fast 3G"
        else -> "≈ 3G/HSPA+"
    }
}
