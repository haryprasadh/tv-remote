package com.hari.tvremote.connection

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/** Sends Wake-on-LAN magic packets (6 x 0xFF, then the MAC 16 times) to UDP ports 9 and 7. */
object WakeOnLan {
    fun send(mac: String, targets: List<InetAddress>, bind: (DatagramSocket) -> Unit) {
        val macBytes = mac.split(':', '-').map { it.toInt(16).toByte() }.toByteArray()
        require(macBytes.size == 6) { "Bad MAC $mac" }
        val payload = ByteArray(6 + 16 * 6)
        for (i in 0 until 6) payload[i] = 0xFF.toByte()
        for (r in 0 until 16) macBytes.copyInto(payload, 6 + r * 6)
        DatagramSocket().use { socket ->
            bind(socket)
            socket.broadcast = true
            for (target in targets) {
                for (port in intArrayOf(9, 7)) {
                    runCatching { socket.send(DatagramPacket(payload, payload.size, target, port)) }
                }
            }
        }
    }
}
