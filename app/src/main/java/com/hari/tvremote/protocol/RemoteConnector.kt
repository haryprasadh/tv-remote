package com.hari.tvremote.protocol

import java.io.IOException
import java.net.InetSocketAddress
import javax.net.SocketFactory
import javax.net.ssl.SSLSocket

/** Opens a [RemoteSession]: TCP connect, TLS handshake, then waits for the TV's remote_start. */
object RemoteConnector {
    const val CONNECT_TIMEOUT_MS = 2_500
    const val HANDSHAKE_TIMEOUT_MS = 5_000

    /** No message within this window closes the link (TV pings every ~5 s; matches remote.py's 16 s). */
    const val IDLE_TIMEOUT_MS = 16_000
    const val READY_TIMEOUT_MS = 8_000L

    sealed interface Result {
        data class Opened(val session: RemoteSession) : Result

        /** Nothing accepted a TCP connection on the port. */
        data class Unreachable(val error: Throwable) : Result

        /** TLS said the TV and this app don't trust each other: needs re-pairing. */
        data class AuthRejected(val error: Throwable) : Result

        /** Reached the port but the session didn't get going (reset, timeout, early close). */
        data class Failed(val reason: String) : Result
    }

    /** Blocking. Call from a background thread. */
    fun open(
        host: String,
        port: Int,
        tls: TlsIdentity,
        socketFactory: SocketFactory = SocketFactory.getDefault(),
        log: (String) -> Unit = {},
    ): Result {
        val raw = try {
            socketFactory.createSocket().apply {
                connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                tcpNoDelay = true
            }
        } catch (e: IOException) {
            return Result.Unreachable(e)
        }
        val ssl = try {
            (tls.socketFactory.createSocket(raw, host, port, true) as SSLSocket).apply {
                soTimeout = HANDSHAKE_TIMEOUT_MS
                startHandshake()
                soTimeout = IDLE_TIMEOUT_MS
            }
        } catch (e: IOException) {
            runCatching { raw.close() }
            return if (isTlsAuthFailure(e)) {
                Result.AuthRejected(e)
            } else {
                Result.Failed("TLS handshake failed: ${e.message}")
            }
        }
        val session = RemoteSession(host, ssl, log)
        session.start()
        if (session.awaitReady(READY_TIMEOUT_MS)) return Result.Opened(session)
        val reason = session.closeReason
        session.close()
        return if (reason is CloseReason.AuthRejected) {
            Result.AuthRejected(reason.error)
        } else {
            Result.Failed("TV didn't start the session (${reason ?: "timeout"})")
        }
    }
}
