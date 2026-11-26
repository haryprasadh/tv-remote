package com.hari.tvremote.connection

import android.os.SystemClock
import com.hari.tvremote.TvConfig
import com.hari.tvremote.protocol.RemoteConnector
import com.hari.tvremote.protocol.RemoteSession
import com.hari.tvremote.protocol.TlsIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * One "find and connect" attempt: tries the last-known IP, the default host and every TV that
 * mDNS reports, in parallel; the first session to open wins and the others are closed.
 * Never sends Wake-on-LAN (the caller decides that).
 */
class TvDialer(
    private val identity: () -> Result<TlsIdentity>,
    private val network: HomeNetwork,
    private val finder: TvFinder,
    private val store: TvStore,
    private val io: CoroutineScope,
    private val log: (String) -> Unit,
) {
    data class Outcome(
        val session: RemoteSession? = null,
        /** TLS rejected (or identity unreadable): the TV needs re-pairing. */
        val authRejected: Throwable? = null,
        /** mDNS saw our TV during the attempt. */
        val sawTv: Boolean = false,
    )

    /** Give mDNS at least this long before giving up when every direct attempt has failed. */
    private val minMdnsWaitMs = 2_500L

    suspend fun race(windowMs: Long): Outcome {
        val tls = identity().getOrElse { return Outcome(authRejected = it) }
        val lock = Any()
        var winner: RemoteSession? = null
        var done = false
        val authError = AtomicReference<Throwable?>(null)
        val inFlight = AtomicInteger(0)
        val tried = HashSet<String>()

        fun attempt(host: String) {
            if (!tried.add(host)) return
            inFlight.incrementAndGet()
            io.launch {
                try {
                    when (val r = RemoteConnector.open(host, TvConfig.PORT, tls, network.socketFactory(), log)) {
                        is RemoteConnector.Result.Opened -> {
                            val keep = synchronized(lock) {
                                if (done || winner != null) false else { winner = r.session; true }
                            }
                            if (!keep) r.session.close()
                        }
                        is RemoteConnector.Result.AuthRejected -> {
                            log("$host rejected our certificate: ${r.error.message}")
                            authError.compareAndSet(null, r.error)
                        }
                        is RemoteConnector.Result.Unreachable -> log("$host: no answer (${r.error.message})")
                        is RemoteConnector.Result.Failed -> log("$host: ${r.reason}")
                    }
                } finally {
                    inFlight.decrementAndGet()
                }
            }
        }

        val start = SystemClock.elapsedRealtime()
        finder.acquire()
        try {
            store.lastHost?.let(::attempt)
            attempt(TvConfig.DEFAULT_HOST)
            while (true) {
                finder.hosts.value.values.forEach(::attempt)
                val elapsed = SystemClock.elapsedRealtime() - start
                val won = synchronized(lock) { winner != null }
                if (won || elapsed >= windowMs) break
                if (inFlight.get() == 0 && (authError.get() != null || elapsed >= minMdnsWaitMs)) break
                delay(100)
            }
            val session = synchronized(lock) {
                done = true
                winner
            }
            return Outcome(
                session = session,
                authRejected = if (session == null) authError.get() else null,
                sawTv = finder.hosts.value.isNotEmpty() || finder.seenWithin(windowMs + 1_000),
            )
        } finally {
            // Cancelled mid-race: drop anything that already won.
            synchronized(lock) {
                if (!done) {
                    done = true
                    winner?.close()
                }
            }
            finder.release()
        }
    }
}
