package com.hari.tvremote.protocol

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Wire framing used by the Android TV Remote protocol v2: every protobuf message is
 * prefixed by its byte length encoded as a varint (same as androidtvremote2/base.py).
 */
object Framing {
    /** Messages from the TV are tiny; anything bigger means the stream is out of sync. */
    private const val MAX_MESSAGE_BYTES = 1 shl 20

    fun write(out: OutputStream, message: ByteArray) {
        var len = message.size
        while (len and 0x7F.inv() != 0) {
            out.write((len and 0x7F) or 0x80)
            len = len ushr 7
        }
        out.write(len)
        out.write(message)
    }

    /** Reads one framed message. Returns null on a clean end of stream before a new frame. */
    fun read(input: InputStream): ByteArray? {
        var len = 0
        var shift = 0
        while (true) {
            val b = input.read()
            if (b < 0) {
                if (shift == 0) return null
                throw EOFException("Stream ended inside a length prefix")
            }
            len = len or ((b and 0x7F) shl shift)
            if (b and 0x80 == 0) break
            shift += 7
            if (shift > 28) throw IOException("Corrupt length prefix")
        }
        if (len < 0 || len > MAX_MESSAGE_BYTES) throw IOException("Bad message length $len")
        val buf = ByteArray(len)
        var off = 0
        while (off < len) {
            val n = input.read(buf, off, len - off)
            if (n < 0) throw EOFException("Stream ended inside a message")
            off += n
        }
        return buf
    }
}
