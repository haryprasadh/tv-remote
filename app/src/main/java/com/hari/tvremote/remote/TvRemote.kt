package com.hari.tvremote.remote

import com.hari.tvremote.connection.ConnectionManager
import com.hari.tvremote.connection.ConnectionState
import com.hari.tvremote.proto.RemoteDirection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * What the UI, notification, volume keys and share sheet call. Every call counts as "use" for the
 * service's 15-minute linger. Commands need a live link; they return false (and do nothing) without one.
 */
class TvRemote(private val connection: ConnectionManager, private val usage: UsageClock) {

    fun press(key: TvKey): Boolean {
        if (key == TvKey.POWER) {
            power()
            return true
        }
        return send(key, RemoteDirection.SHORT)
    }

    /** Finger down on a holdable key (after the hold threshold). Pair with [release]. */
    fun hold(key: TvKey): Boolean = send(key, RemoteDirection.START_LONG)

    fun release(key: TvKey): Boolean = send(key, RemoteDirection.END_LONG)

    /** Mirrors the phone's text field into the focused TV field (whole text each time). */
    fun type(text: String): Boolean {
        usage.touch()
        return connection.live?.sendText(text) ?: false
    }

    fun enter(): Boolean = press(TvKey.ENTER)

    /** Opens an installed TV app; the TV launcher handles market://launch. */
    fun openApp(pkg: String): Boolean = openLink("market://launch?id=$pkg")

    fun openLink(link: String): Boolean {
        usage.touch()
        return connection.live?.sendAppLink(link) ?: false
    }

    /** Power per the connection state: off when on, on when in standby, wake + on when unreachable. */
    fun power() {
        usage.touch()
        connection.power()
    }

    /** For the share sheet: connect (turning the TV on if it's in standby), then open [link]. */
    suspend fun openLinkWhenReady(link: String): Boolean {
        usage.touch()
        if (!connection.connectAndAwait(turnOn = true)) return false
        if (connection.state.value == ConnectionState.TvStandby) {
            // We just sent POWER; give the TV a moment to report it's on.
            withTimeoutOrNull(3_000) { connection.state.first { it == ConnectionState.Connected } }
        }
        return openLink(link)
    }

    private fun send(key: TvKey, direction: RemoteDirection): Boolean {
        usage.touch()
        return connection.live?.sendKey(key.code, direction) ?: false
    }
}
