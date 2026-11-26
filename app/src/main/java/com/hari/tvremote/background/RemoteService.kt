package com.hari.tvremote.background

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.hari.tvremote.TvConfig
import com.hari.tvremote.graph
import com.hari.tvremote.remote.TvKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Foreground service (type connectedDevice) that keeps the TV link while the app is open and for
 * 15 minutes after last use. Hosts the notification remote and the volume-key MediaSession.
 */
class RemoteService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var notification: RemoteNotification
    private lateinit var volume: VolumeSession

    override fun onCreate() {
        super.onCreate()
        val graph = graph
        notification = RemoteNotification(this)
        goForeground()
        volume = VolumeSession(
            context = this,
            isMuted = { graph.connection.info.value.volume?.muted == true },
            press = { key -> graph.remote.press(key) },
        )
        scope.launch {
            graph.connection.state.collect { state ->
                notification.update(state)
                volume.setActive(state.hasLink)
            }
        }
        scope.launch {
            graph.connection.info.map { it.volume }.distinctUntilChanged().collect { v ->
                if (v != null) volume.setVolume(v)
            }
        }
        scope.launch {
            while (true) {
                delay(30_000)
                if (!graph.uiVisible && graph.usage.idleMillis() > TvConfig.SERVICE_LINGER_MS) {
                    Log.d("TvRemote", "Idle for 15 min, stopping")
                    stopSelf()
                    break
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        val remote = graph.remote
        when (intent?.action) {
            ACTION_KEY -> intent.getStringExtra(EXTRA_KEY)
                ?.let { name -> TvKey.entries.firstOrNull { it.name == name } }
                ?.let(remote::press)
            ACTION_POWER -> remote.power()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        volume.release()
        graph.connection.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun goForeground() {
        val n = notification.build(graph.connection.state.value)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(RemoteNotification.ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(RemoteNotification.ID, n)
        }
    }

    companion object {
        const val ACTION_KEY = "com.hari.tvremote.KEY"
        const val ACTION_POWER = "com.hari.tvremote.POWER"
        const val EXTRA_KEY = "key"

        /** Starts (or refreshes) the service. Safe to call repeatedly; only from a foreground context. */
        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, RemoteService::class.java))
            } catch (e: RuntimeException) {
                // e.g. ForegroundServiceStartNotAllowedException; the link still works while the app is open.
                Log.w("TvRemote", "Couldn't start the background service: ${e.message}")
            }
        }
    }
}
