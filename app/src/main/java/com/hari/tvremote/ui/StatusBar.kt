package com.hari.tvremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.hari.tvremote.R
import com.hari.tvremote.connection.ConnectionState

/** Top bar: connection state in words, a coloured dot, the Power button, and Retry when useful. */
@Composable
fun StatusBar(state: ConnectionState, onPower: () -> Unit, onRetry: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp, shadowElevation = 2.dp) {
        Column(Modifier.statusBarsPadding()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            ) {
                Box(Modifier.size(16.dp).clip(CircleShape).background(dotColor(state)))
                Spacer(Modifier.width(14.dp))
                Text(
                    state.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                PressKey(
                    label = "Power",
                    enabled = state != ConnectionState.NeedsRepair,
                    onTap = onPower,
                    size = 64.dp,
                    container = PowerRed,
                    contentColor = Color.White,
                    showLabel = false,
                ) { tint ->
                    Icon(painterResource(R.drawable.ic_power), null, tint = tint, modifier = Modifier.size(32.dp))
                }
            }
            hint(state)?.let { text ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, bottom = 12.dp),
                ) {
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        modifier = Modifier.weight(1f),
                    )
                    if (showRetry(state)) {
                        Button(onClick = onRetry, modifier = Modifier.height(52.dp)) {
                            Text("Retry", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            if (state.isBusy || state == ConnectionState.Reconnecting) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }
}

private fun showRetry(state: ConnectionState) = when (state) {
    ConnectionState.Off, ConnectionState.NotHomeWifi, ConnectionState.Idle -> true
    else -> false
}

private fun hint(state: ConnectionState): String? = when (state) {
    ConnectionState.Asleep -> "Tap the red Power button to turn it on."
    ConnectionState.TvStandby -> "Tap the red Power button to turn the screen on."
    ConnectionState.Off -> "I'll connect by myself once it's on."
    ConnectionState.NotHomeWifi -> "Connect the phone to the home Wi-Fi."
    ConnectionState.NeedsRepair -> "Ask Hari to pair the TV again."
    ConnectionState.Idle -> "Tap Retry to connect."
    else -> null
}

private fun dotColor(state: ConnectionState): Color = when (state) {
    ConnectionState.Connected -> Color(0xFF2E7D32)
    ConnectionState.TvStandby, ConnectionState.Searching, ConnectionState.Waking,
    ConnectionState.Reconnecting -> Color(0xFFF9A825)
    ConnectionState.NeedsRepair -> Color(0xFFC62828)
    else -> Color(0xFF9E9E9E)
}
