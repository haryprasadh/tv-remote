package com.hari.tvremote.connection

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.hari.tvremote.TvConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.net.Inet4Address
import java.net.InetAddress

/**
 * Finds the TV over mDNS (`_androidtvremote2._tcp`). Discovery is reference-counted: it runs while
 * anyone holds it and stops a few seconds after the last release. Only services whose TXT "bt"
 * matches our TV are accepted (services without it are accepted too).
 */
class TvFinder(context: Context, private val log: (String) -> Unit) {
    private val nsd = context.getSystemService(NsdManager::class.java)
    private val main = Handler(Looper.getMainLooper())

    private val _hosts = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Service name -> IPv4 address, for TVs currently visible. */
    val hosts: StateFlow<Map<String, String>> = _hosts

    @Volatile private var lastSeenAt = 0L

    // Main-thread state
    private var refs = 0
    private var listener: NsdManager.DiscoveryListener? = null
    private val pending = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private val stopLater = Runnable { stopNow() }

    fun acquire() {
        main.post {
            refs++
            main.removeCallbacks(stopLater)
            if (listener == null) startNow()
        }
    }

    fun release() {
        main.post {
            refs = (refs - 1).coerceAtLeast(0)
            if (refs == 0) main.postDelayed(stopLater, 3_000)
        }
    }

    /** True if mDNS showed our TV within the last [ms]. */
    fun seenWithin(ms: Long): Boolean = lastSeenAt != 0L && SystemClock.elapsedRealtime() - lastSeenAt < ms

    private fun startNow() {
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                log("mDNS discovery failed to start ($errorCode)")
                main.post { if (listener === this) listener = null }
            }

            override fun onServiceFound(info: NsdServiceInfo) {
                main.post { if (listener === this) enqueue(info) }
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                main.post { _hosts.update { it - info.serviceName } }
            }
        }
        listener = l
        try {
            nsd.discoverServices(TvConfig.MDNS_TYPE, NsdManager.PROTOCOL_DNS_SD, l)
        } catch (e: RuntimeException) {
            log("mDNS discovery unavailable: ${e.message}")
            listener = null
        }
    }

    private fun stopNow() {
        val l = listener ?: return
        listener = null
        runCatching { nsd.stopServiceDiscovery(l) }
        pending.clear()
        _hosts.value = emptyMap()
    }

    private fun enqueue(info: NsdServiceInfo) {
        pending.addLast(info)
        pump()
    }

    /** Resolves one service at a time (older Android rejects concurrent resolves). */
    @Suppress("DEPRECATION")
    private fun pump() {
        if (resolving) return
        val next = pending.removeFirstOrNull() ?: return
        resolving = true
        try {
            nsd.resolveService(next, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                    main.post {
                        resolving = false
                        pump()
                    }
                }

                override fun onServiceResolved(info: NsdServiceInfo) {
                    main.post {
                        resolving = false
                        accept(info)
                        pump()
                    }
                }
            })
        } catch (e: RuntimeException) {
            resolving = false
        }
    }

    @Suppress("DEPRECATION")
    private fun accept(info: NsdServiceInfo) {
        val bt = info.attributes["bt"]?.let { String(it, Charsets.UTF_8) }
        if (bt != null && hexOnly(bt) != hexOnly(TvConfig.MDNS_BT_MAC)) {
            log("mDNS: ignoring another TV (${info.serviceName})")
            return
        }
        val addresses: List<InetAddress> =
            if (Build.VERSION.SDK_INT >= 34) info.hostAddresses else listOfNotNull(info.host)
        val ipv4 = addresses.firstOrNull { it is Inet4Address }?.hostAddress ?: return
        lastSeenAt = SystemClock.elapsedRealtime()
        log("mDNS: TV ${info.serviceName} at $ipv4")
        _hosts.update { it + (info.serviceName to ipv4) }
    }

    private fun hexOnly(s: String) = s.filter { it.isLetterOrDigit() }.uppercase()
}
