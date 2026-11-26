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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hari.tvremote.R
import com.hari.tvremote.protocol.TvVolume
import com.hari.tvremote.remote.TvKey
import com.hari.tvremote.remote.TvRemote

/** D-pad + OK, Back / Home / Play-Pause, Vol − / Mute / Vol +, and the TV's volume level. */
@Composable
fun RemoteTab(remote: TvRemote, enabled: Boolean, volume: TvVolume?, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        VolumeBar(volume)
        Spacer(Modifier.height(20.dp))
        DPad(remote, enabled)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            TvKeyButton(TvKey.BACK, R.drawable.ic_back, remote, enabled)
            TvKeyButton(TvKey.HOME, R.drawable.ic_home, remote, enabled)
            TvKeyButton(TvKey.PLAY_PAUSE, R.drawable.ic_play_pause, remote, enabled)
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            TvKeyButton(TvKey.VOLUME_DOWN, R.drawable.ic_volume_down, remote, enabled)
            TvKeyButton(TvKey.MUTE, R.drawable.ic_volume_off, remote, enabled)
            TvKeyButton(TvKey.VOLUME_UP, R.drawable.ic_volume_up, remote, enabled)
        }
    }
}

@Composable
private fun VolumeBar(volume: TvVolume?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        val muted = volume?.muted == true
        Icon(
            painterResource(if (muted) R.drawable.ic_volume_off else R.drawable.ic_volume_up),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(12.dp))
        LinearProgressIndicator(
            progress = { if (volume == null || volume.max <= 0) 0f else volume.level.toFloat() / volume.max },
            color = if (muted) MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f) else MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(6.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            when {
                volume == null -> "–"
                muted -> "Muted"
                else -> volume.level.toString()
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun DPad(remote: TvRemote, enabled: Boolean) {
    val ring = 288.dp
    val arrow = 92.dp
    Box(
        modifier = Modifier.size(ring).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        val arrowShape = RoundedCornerShape(24.dp)
        val clear = Color.Transparent
        TvKeyButton(TvKey.UP, R.drawable.ic_up, remote, enabled, Modifier.align(Alignment.TopCenter),
            size = arrow, shape = arrowShape, container = clear, showLabel = false)
        TvKeyButton(TvKey.DOWN, R.drawable.ic_down, remote, enabled, Modifier.align(Alignment.BottomCenter),
            size = arrow, shape = arrowShape, container = clear, showLabel = false)
        TvKeyButton(TvKey.LEFT, R.drawable.ic_left, remote, enabled, Modifier.align(Alignment.CenterStart),
            size = arrow, shape = arrowShape, container = clear, showLabel = false)
        TvKeyButton(TvKey.RIGHT, R.drawable.ic_right, remote, enabled, Modifier.align(Alignment.CenterEnd),
            size = arrow, shape = arrowShape, container = clear, showLabel = false)
        PressKey(
            label = "OK",
            enabled = enabled,
            onTap = { remote.press(TvKey.OK) },
            size = 108.dp,
            container = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            showLabel = false,
            modifier = Modifier.align(Alignment.Center),
        ) { tint ->
            Text("OK", color = tint, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        }
    }
}
