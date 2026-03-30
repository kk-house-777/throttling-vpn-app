package okoge.house.throttling_app.testapp.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import okoge.house.throttling_app.testapp.TestResult
import okoge.house.throttling_app.testapp.TestState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TestDashboard(
    state: TestState,
    onStartStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val baselineDownload = state.results.lastOrNull()?.downloadSpeedKbps ?: 0.0
    val isThrottled = state.results.size >= 2 &&
        state.currentDownloadKbps > 0 &&
        baselineDownload > 0 &&
        state.currentDownloadKbps < baselineDownload * 0.5

    val hasError = state.results.firstOrNull()?.error != null

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Start/Stop button
        item {
            Button(
                onClick = onStartStop,
                modifier = Modifier.fillMaxWidth(),
                colors = if (state.isRunning) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) {
                Text(if (state.isRunning) "Stop" else "Start")
            }
        }

        // Speed display
        item {
            SpeedCard(state = state)
        }

        // Status indicator
        item {
            StatusIndicator(
                isRunning = state.isRunning,
                isThrottled = isThrottled,
                hasError = hasError,
            )
        }

        // History header
        if (state.results.isNotEmpty()) {
            item {
                Text(
                    text = "History",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Result history
        items(state.results) { result ->
            ResultRow(result = result)
            HorizontalDivider()
        }
    }
}

@Composable
private fun SpeedCard(state: TestState, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Current Speed",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                SpeedValue(
                    label = "Download",
                    value = formatSpeed(state.currentDownloadKbps),
                    prefix = "\u2193",
                )
                SpeedValue(
                    label = "Upload",
                    value = formatSpeed(state.currentUploadKbps),
                    prefix = "\u2191",
                )
                SpeedValue(
                    label = "Latency",
                    value = "${state.averageLatencyMs}ms",
                    prefix = "\u23f1",
                )
            }
        }
    }
}

@Composable
private fun SpeedValue(label: String, value: String, prefix: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$prefix $value",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusIndicator(
    isRunning: Boolean,
    isThrottled: Boolean,
    hasError: Boolean,
    modifier: Modifier = Modifier,
) {
    val (color, label) = when {
        !isRunning -> Color.Gray to "STOPPED"
        hasError -> Color.Red to "ERROR"
        isThrottled -> Color.Red to "THROTTLED"
        else -> Color(0xFF4CAF50) to "NORMAL"
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = "  $label",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

@Composable
private fun ResultRow(result: TestResult, modifier: Modifier = Modifier) {
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val time = timeFormat.format(Date(result.timestamp))

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = time, style = MaterialTheme.typography.bodySmall)
        if (result.error != null) {
            Text(
                text = result.error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            Text(
                text = "\u2193${formatSpeed(result.downloadSpeedKbps)}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "\u2191${formatSpeed(result.uploadSpeedKbps)}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "${result.latencyMs}ms",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun formatSpeed(kbps: Double): String {
    return if (kbps >= 1000) {
        String.format(Locale.US, "%.1f Mbps", kbps / 1000.0)
    } else {
        String.format(Locale.US, "%.0f kbps", kbps)
    }
}
