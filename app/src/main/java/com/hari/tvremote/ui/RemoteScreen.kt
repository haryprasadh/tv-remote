package com.hari.tvremote.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hari.tvremote.R
import com.hari.tvremote.connection.ConnectionManager
import com.hari.tvremote.remote.TvRemote

private enum class Tab(val label: String, val icon: Int) {
    Remote("Remote", R.drawable.ic_remote),
    Apps("Apps", R.drawable.ic_apps),
    Type("Type", R.drawable.ic_keyboard),
}

/** Status bar on top, the three tabs at the bottom. Jumps to Type when the TV keyboard opens. */
@Composable
fun RemoteScreen(connection: ConnectionManager, remote: TvRemote) {
    val state by connection.state.collectAsStateWithLifecycle()
    val info by connection.info.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(Tab.Remote.ordinal) }
    val linked = state.hasLink

    LaunchedEffect(info.imeSeq) {
        if (info.textField != null) tab = Tab.Type.ordinal
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { StatusBar(state, onPower = remote::power, onRetry = { connection.connect() }) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t.ordinal,
                        onClick = { tab = t.ordinal },
                        icon = { Icon(painterResource(t.icon), contentDescription = null) },
                        label = { Text(t.label, style = MaterialTheme.typography.labelLarge) },
                    )
                }
            }
        },
    ) { padding ->
        val content = Modifier.padding(padding)
        when (Tab.entries[tab]) {
            Tab.Remote -> RemoteTab(remote, linked, info.volume, content)
            Tab.Apps -> AppsTab(remote, linked, content)
            Tab.Type -> TypeTab(remote, linked, info.textField, info.imeSeq, content)
        }
    }
}
