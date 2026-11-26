package com.hari.tvremote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hari.tvremote.TvApp
import com.hari.tvremote.TvConfig
import com.hari.tvremote.remote.TvRemote

/** Big coloured tiles that open TV apps (list in [TvConfig.APPS]). */
@Composable
fun AppsTab(remote: TvRemote, enabled: Boolean, modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        items(TvConfig.APPS, key = { it.pkg }) { app -> AppTile(app, enabled) { remote.openApp(app.pkg) } }
    }
}

@Composable
private fun AppTile(app: TvApp, enabled: Boolean, onOpen: () -> Unit) {
    PressKey(
        label = app.label,
        enabled = enabled,
        onTap = onOpen,
        size = null,
        shape = RoundedCornerShape(20.dp),
        container = Color(app.color),
        contentColor = Color.White,
        showLabel = false,
        modifier = Modifier.fillMaxWidth(),
    ) { tint ->
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(116.dp)) {
            Text(app.label, color = tint, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}
