package com.hari.tvremote.protocol

import com.google.protobuf.InvalidProtocolBufferException
import com.hari.tvremote.proto.RemoteDirection
import com.hari.tvremote.proto.RemoteKeyCode
import com.hari.tvremote.proto.RemoteMessage
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLSocket

/**
 * One open, TLS-authenticated link to the TV. Answers the handshake and pings by itself
 * (exactly as androidtvremote2 RemoteProtocol._handle_message), tracks the TV state it reports,
 * and sends key / text / app-link messages. A remote_error from the TV is reported, never fatal.
 *
 * Threads: one reader thread per session; all writes go through one writer thread, so callers may
 * send from any thread (including Android's main thread).
 */
class RemoteSession internal constructor(
    val host: String,
    private val socket: SSLSocket,
    private val log: (String) -> Unit,
) {
    interface Listener {
        fun onStarted(started: Boolean) {}
        fun onVolume(volume: TvVolume) {}
        fun onCurrentApp(pkg: String) {}

        /** The TV keyboard opened ([field] non-null) or the TV asked to hide it (null). */
        fun onTextField(field: TvTextField?) {}
        fun onTvError(description: String) {}
        fun onClosed(reason: CloseReason) {}
    }

    /** Set after the session is chosen; the state below covers anything reported before that. */
    @Volatile var listener: Listener? = null

    @Volatile var started: Boolean? = null
        private set
    @Volatile var volume: TvVolume? = null
        private set
    @Volatile var currentApp: String = ""
        private set
    @Volatile var textField: TvTextField? = null
        private set
    @Volatile var closeReason: CloseReason? = null
        private set

    val isClosed: Boolean get() = closed.get()

    @Volatile private var activeFeatures = Features.REQUESTED
    @Volatile private var configured = false
    @Volatile private var imeCounter = 0
    @Volatile private var fieldCounter = 0

    private val closed = AtomicBoolean(false)
    private val ready = CountDownLatch(1)
    private val out = BufferedOutputStream(socket.outputStream)
    private val writer = Executors.newSingleThreadExecutor { r ->
        Thread(r, "tv-writer").apply { isDaemon = true }
    }

    fun sendKey(code: RemoteKeyCode, direction: RemoteDirection = RemoteDirection.SHORT): Boolean =
        send(Messages.key(code, direction))

    /** Replaces the TV field's text with [text] (empty clears it). */
    fun sendText(text: String): Boolean = send(Messages.text(text, imeCounter, fieldCounter))

    fun sendAppLink(link: String): Boolean = send(Messages.appLink(link))

    fun close() = close(CloseReason.Local)

    internal fun start() {
        Thread(::readLoop, "tv-reader").apply { isDaemon = true }.start()
    }

    /** Waits for remote_start (or the end of the session). True when the TV is ready for commands. */
    internal fun awaitReady(timeoutMs: Long): Boolean {
        ready.await(timeoutMs, TimeUnit.MILLISECONDS)
        return started != null && !closed.get()
    }

    private fun readLoop() {
        try {
            val input = BufferedInputStream(socket.inputStream)
            while (!closed.get()) {
                val frame = Framing.read(input)
                if (frame == null) {
                    close(CloseReason.Eof)
                    return
                }
                handle(frame)
            }
        } catch (e: SocketTimeoutException) {
            close(CloseReason.Timeout)
        } catch (e: Throwable) {
            close(if (!configured && isTlsAuthFailure(e)) CloseReason.AuthRejected(e) else CloseReason.IoError(e))
        }
    }

    private fun handle(frame: ByteArray) {
        val msg = try {
            RemoteMessage.parseFrom(frame)
        } catch (e: InvalidProtocolBufferException) {
            log("Couldn't parse a message from the TV: ${e.message}")
            return
        }
        when {
            msg.hasRemoteConfigure() -> {
                configured = true
                activeFeatures = Features.REQUESTED and msg.remoteConfigure.code1
                log("TV configure code1=${msg.remoteConfigure.code1} -> reply $activeFeatures")
                send(Messages.configureReply(activeFeatures))
            }
            msg.hasRemoteSetActive() -> {
                log("TV set_active -> reply $activeFeatures")
                send(Messages.setActiveReply(activeFeatures))
            }
            msg.hasRemoteImeKeyInject() -> {
                currentApp = msg.remoteImeKeyInject.appInfo.appPackage
                log("TV current app: $currentApp")
                listener?.onCurrentApp(currentApp)
            }
            msg.hasRemoteImeBatchEdit() -> {
                imeCounter = msg.remoteImeBatchEdit.imeCounter
                fieldCounter = msg.remoteImeBatchEdit.fieldCounter
                log("TV ime counters: ime=$imeCounter field=$fieldCounter")
            }
            msg.hasRemoteImeShowRequest() -> {
                val show = msg.remoteImeShowRequest
                val field = if (show.hasRemoteTextFieldStatus()) {
                    TvTextField(show.remoteTextFieldStatus.value, show.remoteTextFieldStatus.label)
                } else {
                    null
                }
                textField = field
                log("TV keyboard ${if (field != null) "shown" else "hidden"}")
                listener?.onTextField(field)
            }
            msg.hasRemoteSetVolumeLevel() -> {
                val v = msg.remoteSetVolumeLevel
                val vol = TvVolume(v.volumeLevel, v.volumeMax, v.volumeMuted)
                volume = vol
                listener?.onVolume(vol)
            }
            msg.hasRemoteStart() -> {
                val s = msg.remoteStart.started
                started = s
                log("TV remote_start started=$s")
                ready.countDown()
                listener?.onStarted(s)
            }
            msg.hasRemotePingRequest() -> send(Messages.pingResponse(msg.remotePingRequest.val1))
            msg.hasRemoteError() -> {
                // androidtvremote2 logs this; some callers drop the link. We keep it open.
                val description = msg.remoteError.toString().replace('\n', ' ').trim()
                log("TV reported an error: $description")
                listener?.onTvError(description)
            }
            else -> log("Unhandled message from the TV (${frame.size} bytes)")
        }
    }

    private fun send(msg: RemoteMessage): Boolean {
        if (closed.get()) return false
        val bytes = msg.toByteArray()
        return try {
            writer.execute {
                if (closed.get()) return@execute
                try {
                    synchronized(out) {
                        Framing.write(out, bytes)
                        out.flush()
                    }
                } catch (e: IOException) {
                    close(CloseReason.IoError(e))
                }
            }
            true
        } catch (e: RejectedExecutionException) {
            false
        }
    }

    private fun close(reason: CloseReason) {
        if (!closed.compareAndSet(false, true)) return
        closeReason = reason
        log("Link to $host closed: $reason")
        writer.shutdown()
        // Closing an SSL socket may write a close_notify; keep that off the caller's thread.
        Thread({ runCatching { socket.close() } }, "tv-close").apply { isDaemon = true }.start()
        ready.countDown()
        listener?.onClosed(reason)
    }
}
