package com.hari.tvremote.background

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.hari.tvremote.connection.ConnectionState
import com.hari.tvremote.graph
import com.hari.tvremote.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Quick Settings tile "TV": opens the remote and acts like its Power button. */
class TvTileService : TileService() {
    private var watch: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        watch?.cancel()
        watch = CoroutineScope(Dispatchers.Main.immediate).launch {
            graph.connection.state.collect(::render)
        }
    }

    override fun onStopListening() {
        watch?.cancel()
        watch = null
        super.onStopListening()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_POWER, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val open = Runnable {
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(
                    PendingIntent.getActivity(
                        this, 0, intent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
        if (isLocked) unlockAndRun(open) else open.run()
    }

    private fun render(state: ConnectionState) {
        val tile = qsTile ?: return
        tile.label = "TV"
        tile.state = if (state.hasLink) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = state.short
        tile.updateTile()
    }
}
