package com.hari.tvremote.background

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.hari.tvremote.R
import com.hari.tvremote.connection.ConnectionState
import com.hari.tvremote.remote.TvKey
import com.hari.tvremote.ui.MainActivity

/**
 * The foreground-service notification: a row of five buttons (Play/Pause, Vol −, Vol +, Mute, Power).
 * A custom view is used because standard notifications show at most three actions.
 */
class RemoteNotification(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        val channel = NotificationChannel(CHANNEL, "TV remote", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Remote buttons while the TV is connected"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun build(state: ConnectionState): Notification {
        val collapsed = buttons(R.layout.notification_remote)
        val expanded = buttons(R.layout.notification_remote_big).apply {
            setTextViewText(R.id.status, state.label)
        }
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_tv)
            .setContentTitle("TV")
            .setContentText(state.label)
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setCustomContentView(collapsed)
            .setCustomBigContentView(expanded)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .apply {
                if (Build.VERSION.SDK_INT >= 31) {
                    setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                }
            }
            .build()
    }

    fun update(state: ConnectionState) {
        runCatching { manager.notify(ID, build(state)) }
    }

    private fun buttons(layout: Int) = RemoteViews(context.packageName, layout).apply {
        setOnClickPendingIntent(R.id.btn_play_pause, keyIntent(TvKey.PLAY_PAUSE, 1))
        setOnClickPendingIntent(R.id.btn_vol_down, keyIntent(TvKey.VOLUME_DOWN, 2))
        setOnClickPendingIntent(R.id.btn_vol_up, keyIntent(TvKey.VOLUME_UP, 3))
        setOnClickPendingIntent(R.id.btn_mute, keyIntent(TvKey.MUTE, 4))
        setOnClickPendingIntent(R.id.btn_power, serviceIntent(RemoteService.ACTION_POWER, null, 5))
    }

    private fun keyIntent(key: TvKey, requestCode: Int) =
        serviceIntent(RemoteService.ACTION_KEY, key.name, requestCode)

    private fun serviceIntent(action: String, key: String?, requestCode: Int): PendingIntent {
        val intent = Intent(context, RemoteService::class.java).setAction(action)
        if (key != null) intent.putExtra(RemoteService.EXTRA_KEY, key)
        return PendingIntent.getService(
            context, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        const val ID = 1
        private const val CHANNEL = "remote"
    }
}
