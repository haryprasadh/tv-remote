package com.hari.tvremote.connection

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.hari.tvremote.TvConfig
import com.hari.tvremote.proto.RemoteKeyCode
import com.hari.tvremote.protocol.CloseReason
import com.hari.tvremote.protocol.RemoteSession
import com.hari.tvremote.protocol.TvTextField
import com.hari.tvremote.protocol.TvVolume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress

/**
 * The connection state machine from the spec ("Data flow" + "Connection states").
 *
 * Rules worth remembering:
 *  - Only user actions ([connect], [power] with no live link, [onAppOpened]) may send Wake-on-LAN,
 *    and only on the home network. Automatic reconnects, the Off listener and network-change
 *    reconnects never do, so turning the TV off can't make the app turn it back on.
 *  - All state changes run on one serial dispatcher.
 */
class ConnectionManager(private val context: Context, appScope: CoroutineScope) {
    private val log: (String) -> Unit = { Log.d(TAG, it) }
    private val serial = Dispatchers.Default.limitedParallelism(1)
    private val scope = CoroutineScope(appScope.coroutineContext + serial)
    private val io = CoroutineScope(appScope.coroutineContext + Dispatchers.IO)

    private val _state = MutableStateFlow(ConnectionState.Idle)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _info = MutableStateFlow(TvInfo())
    val info: StateFlow<TvInfo> = _info.asStateFlow()

    private val _tvErrors = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** remote_error messages from the TV (e.g. an app link it couldn't open). The link stays up. */
    val tvErrors: SharedFlow<String> = _tvErrors.asSharedFlow()

    /** The live session, for sending commands from any thread. */
    @Volatile var live: RemoteSession? = null
        private set

    private val store = TvStore(context)
    private val network = HomeNetwork(context) { scope.launch { onNetworkChanged() } }
    private val finder = TvFinder(context, log)
    private val identity by lazy { runCatching { TvIdentityLoader.load(context) } }
    private val dialer = TvDialer({ identity }, network, finder, store, io, log)

    // Serial-dispatcher state
    private var session: RemoteSession? = null
    private var flow: Job? = null
    private var listenJob: Job? = null
    private var active = false
    private var uiVisible = false
    private var pendingTurnOn = false
    private var powerOffAt = 0L
    private var tvReportedStandby = false

    // ---- User actions -------------------------------------------------------------------

    /** Retry / share / generic user action: find the TV, waking it if needed. */
    fun connect(turnOn: Boolean = false) {
        scope.launch { userConnect(turnOn) }
    }

    /** Opening the app. A TV we know is asleep is only probed (no Wake-on-LAN); Power wakes it. */
    fun onAppOpened() {
        scope.launch {
            if (_state.value == ConnectionState.Asleep) {
                active = true
                if (flow?.isActive != true) flow = scope.launch { quietProbe() }
            } else {
                userConnect(turnOn = false)
            }
        }
    }

    /** Power button / tile / notification, per the "Power button / tile" column. */
    fun power() {
        scope.launch {
            when (_state.value) {
                ConnectionState.Connected -> {
                    session?.sendKey(RemoteKeyCode.KEYCODE_POWER)
                    powerOffAt = now()
                }
                ConnectionState.TvStandby -> session?.sendKey(RemoteKeyCode.KEYCODE_POWER)
                ConnectionState.Searching, ConnectionState.Waking -> pendingTurnOn = true
                ConnectionState.NeedsRepair -> Unit
                else -> userConnect(turnOn = true)
            }
        }
    }

    /** Connects (as a user action) and waits for the attempt to finish. True if a live link is up. */
    suspend fun connectAndAwait(turnOn: Boolean): Boolean {
        val job = withContext(serial) { userConnect(turnOn) }
        job?.join()
        return _state.value.hasLink
    }

    fun setUiVisible(visible: Boolean) {
        scope.launch {
            uiVisible = visible
            if (!visible) {
                listenJob?.cancel()
            } else if (_state.value == ConnectionState.Off) {
                startOffListen()
            }
        }
    }

    /** The background service is stopping: drop the link and go idle. */
    fun shutdown() {
        scope.launch {
            active = false
            cancelJobs()
            dropSession()?.close()
            _info.value = TvInfo()
            setState(ConnectionState.Idle)
        }
    }

    // ---- Flows ---------------------------------------------------------------------------

    private fun userConnect(turnOn: Boolean): Job? {
        active = true
        val current = _state.value
        if (current.hasLink) {
            if (turnOn && current == ConnectionState.TvStandby) session?.sendKey(RemoteKeyCode.KEYCODE_POWER)
            return null
        }
        if (current.isBusy && flow?.isActive == true) {
            pendingTurnOn = pendingTurnOn || turnOn
            return flow
        }
        cancelJobs()
        pendingTurnOn = turnOn
        setState(ConnectionState.Searching)
        return scope.launch { runUserConnect() }.also { flow = it }
    }

    private suspend fun runUserConnect() {
        val first = dialer.race(TvConfig.SEARCH_WINDOW_MS)
        if (finish(first)) return
        if (!network.wolAllowed(store.homeNetwork)) {
            settleFailure(first.sawTv)
            return
        }
        setState(ConnectionState.Waking)
        val woke = wakeAndWait()
        if (finish(woke)) return
        settleFailure(first.sawTv || woke.sawTv)
    }

    /** Wake-on-LAN, then keep trying (re-resolving via mDNS) for ~40 s. */
    private suspend fun wakeAndWait(): TvDialer.Outcome {
        val start = now()
        var lastWol = 0L
        var sawTv = false
        finder.acquire()
        try {
            while (now() - start < TvConfig.WAKE_WINDOW_MS) {
                if (now() - lastWol >= 5_000 && now() - start < 25_000) {
                    sendWakeOnLan()
                    lastWol = now()
                }
                val out = dialer.race(3_000)
                if (out.session != null || out.authRejected != null) return out
                sawTv = sawTv || out.sawTv
                delay(1_000)
            }
        } finally {
            finder.release()
        }
        return TvDialer.Outcome(sawTv = sawTv)
    }

    /** Automatic reconnect after an unexpected close: backoff for ~30 s, never Wake-on-LAN. */
    private suspend fun reconnectLoop() {
        pendingTurnOn = false
        val start = now()
        var backoff = 500L
        var sawTv = false
        while (now() - start < TvConfig.RECONNECT_WINDOW_MS) {
            val out = dialer.race(3_000)
            if (finish(out)) return
            sawTv = sawTv || out.sawTv
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(8_000)
        }
        settleFailure(sawTv, allowForeign = false)
    }

    /** One connect attempt with no Wake-on-LAN that leaves the state alone when it fails. */
    private suspend fun quietProbe() {
        pendingTurnOn = false
        val out = dialer.race(TvConfig.SEARCH_WINDOW_MS)
        if (out.session != null) adopt(out.session)
    }

    /** Off: listen on mDNS (and probe the last IP now and then) while the app is open; no Wake-on-LAN. */
    private fun startOffListen() {
        if (!uiVisible || listenJob?.isActive == true) return
        listenJob = scope.launch {
            finder.acquire()
            try {
                var lastProbe = now()
                while (_state.value == ConnectionState.Off) {
                    val tvVisible = finder.hosts.value.isNotEmpty()
                    if (tvVisible || now() - lastProbe >= TvConfig.OFF_PROBE_INTERVAL_MS) {
                        lastProbe = now()
                        val out = dialer.race(3_000)
                        if (out.session != null) {
                            adopt(out.session)
                            return@launch
                        }
                        if (out.authRejected != null) {
                            setState(ConnectionState.NeedsRepair)
                            return@launch
                        }
                        // A cold boot takes ~1 min: keep knocking while mDNS sees the TV.
                        if (tvVisible) delay(2_000)
                    }
                    delay(1_000)
                }
            } finally {
                finder.release()
            }
        }
    }

    private fun autoConnect() {
        if (flow?.isActive == true) return
        flow = scope.launch {
            pendingTurnOn = false
            val out = dialer.race(TvConfig.SEARCH_WINDOW_MS)
            if (!finish(out)) settleFailure(out.sawTv)
        }
    }

    /** True when the outcome ends the attempt (connected, or the TV rejected us). */
    private fun finish(out: TvDialer.Outcome): Boolean {
        out.session?.let {
            adopt(it)
            return true
        }
        if (out.authRejected != null) {
            log("Needs re-pairing: ${out.authRejected.message}")
            setState(ConnectionState.NeedsRepair)
            return true
        }
        return false
    }

    private fun settleFailure(sawTv: Boolean, allowForeign: Boolean = true) {
        val next = when {
            !network.isUp -> ConnectionState.NotHomeWifi
            allowForeign && network.isForeign(store.homeNetwork) -> ConnectionState.NotHomeWifi
            sawTv || finder.seenWithin(10_000) -> ConnectionState.Asleep
            else -> ConnectionState.Off
        }
        setState(next)
        if (next == ConnectionState.Off) startOffListen()
    }

    // ---- Session -------------------------------------------------------------------------

    private fun adopt(s: RemoteSession) {
        dropSession()?.close()
        listenJob?.cancel()
        session = s
        live = s
        // Listener first, then the snapshot: events in between are applied after this (same dispatcher).
        s.listener = SessionEvents(s)
        store.lastHost = s.host
        network.fingerprint()?.let { store.homeNetwork = it }
        val started = s.started == true
        tvReportedStandby = !started
        _info.update {
            TvInfo(
                volume = s.volume,
                currentApp = s.currentApp,
                textField = s.textField,
                imeSeq = if (s.textField != null) it.imeSeq + 1 else it.imeSeq,
            )
        }
        setState(if (started) ConnectionState.Connected else ConnectionState.TvStandby)
        if (pendingTurnOn && !started) {
            log("Turning the TV on (it reported standby)")
            s.sendKey(RemoteKeyCode.KEYCODE_POWER)
        }
        pendingTurnOn = false
        if (s.isClosed) onSessionClosed(s, s.closeReason ?: CloseReason.Eof)
    }

    private fun dropSession(): RemoteSession? {
        val s = session
        session = null
        live = null
        return s
    }

    private fun onSessionClosed(s: RemoteSession, reason: CloseReason) {
        if (session !== s) return
        dropSession()
        _info.update { it.copy(textField = null) }
        if (!active || reason == CloseReason.Local) return
        // Classification: first match wins.
        val next = when {
            reason is CloseReason.AuthRejected -> ConnectionState.NeedsRepair
            !network.isUp -> ConnectionState.NotHomeWifi
            now() - powerOffAt < TvConfig.POWER_OFF_GRACE_MS || tvReportedStandby -> ConnectionState.Asleep
            else -> ConnectionState.Reconnecting
        }
        setState(next)
        if (next == ConnectionState.Reconnecting) {
            flow?.cancel()
            flow = scope.launch { reconnectLoop() }
        }
    }

    private inner class SessionEvents(private val s: RemoteSession) : RemoteSession.Listener {
        private fun onSerial(block: () -> Unit) {
            scope.launch { if (session === s) block() }
        }

        override fun onStarted(started: Boolean) = onSerial {
            tvReportedStandby = !started
            setState(if (started) ConnectionState.Connected else ConnectionState.TvStandby)
        }

        override fun onVolume(volume: TvVolume) = onSerial { _info.update { it.copy(volume = volume) } }

        override fun onCurrentApp(pkg: String) = onSerial {
            _info.update {
                // Switching apps closes whatever text field was open.
                val appChanged = it.currentApp.isNotEmpty() && pkg != it.currentApp
                it.copy(currentApp = pkg, textField = if (appChanged) null else it.textField)
            }
        }

        override fun onTextField(field: TvTextField?) = onSerial {
            _info.update { it.copy(textField = field, imeSeq = if (field != null) it.imeSeq + 1 else it.imeSeq) }
        }

        override fun onTvError(description: String) {
            _tvErrors.tryEmit(description)
        }

        override fun onClosed(reason: CloseReason) {
            scope.launch { onSessionClosed(s, reason) }
        }
    }

    // ---- Helpers -------------------------------------------------------------------------

    private fun onNetworkChanged() {
        if (!active) return
        when (_state.value) {
            ConnectionState.NotHomeWifi -> if (network.isUp) autoConnect()
            ConnectionState.Reconnecting -> if (!network.isUp) {
                flow?.cancel()
                setState(ConnectionState.NotHomeWifi)
            }
            else -> Unit
        }
    }

    private fun sendWakeOnLan() {
        val targets = buildList {
            add(InetAddress.getByAddress(byteArrayOf(-1, -1, -1, -1)))
            network.subnetBroadcast()?.let(::add)
            store.lastHost?.let { runCatching { InetAddress.getByName(it) }.getOrNull() }?.let(::add)
        }
        io.launch {
            log("Wake-on-LAN to ${TvConfig.WOL_MAC} via $targets")
            runCatching { WakeOnLan.send(TvConfig.WOL_MAC, targets, network::bind) }
                .onFailure { log("Wake-on-LAN failed: ${it.message}") }
        }
    }

    private fun cancelJobs() {
        flow?.cancel()
        flow = null
        listenJob?.cancel()
        listenJob = null
    }

    private fun setState(next: ConnectionState) {
        if (_state.value != next) log("State ${_state.value} -> $next")
        _state.value = next
    }

    private fun now() = SystemClock.elapsedRealtime()

    private companion object {
        const val TAG = "TvRemote"
    }
}
