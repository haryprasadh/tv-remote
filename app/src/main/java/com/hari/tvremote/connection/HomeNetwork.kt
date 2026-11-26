package com.hari.tvremote.connection

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import javax.net.SocketFactory

/**
 * Tracks the phone's Wi-Fi (or Ethernet) link and identifies the home network by gateway + subnet
 * from LinkProperties, which needs no location permission. Sockets to the TV are bound to this
 * network so they don't leak onto mobile data when the Wi-Fi has no internet.
 */
class HomeNetwork(context: Context, private val onChange: () -> Unit) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)

    @Volatile private var network: Network? = null
    @Volatile private var link: LinkProperties? = null

    init {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(n: Network) {
                network = n
                link = cm.getLinkProperties(n)
                onChange()
            }

            override fun onLinkPropertiesChanged(n: Network, lp: LinkProperties) {
                if (network == null || network == n) {
                    network = n
                    link = lp
                }
                onChange()
            }

            override fun onLost(n: Network) {
                if (network == n) {
                    network = null
                    link = null
                }
                onChange()
            }
        })
    }

    val isUp: Boolean get() = network != null

    /** "gateway|subnet", e.g. "192.168.1.1|192.168.1.0/24"; null when not on Wi-Fi. */
    fun fingerprint(): String? {
        val lp = link ?: return null
        val address = ipv4Address(lp) ?: return null
        val gateway = lp.routes
            .firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
            ?.gateway?.hostAddress ?: "-"
        return "$gateway|${maskedAddress(address, invert = false).hostAddress}/${address.prefixLength}"
    }

    /** Step 0 of the spec: Wake-on-LAN only when on Wi-Fi and it is the saved home network (or none is saved yet). */
    fun wolAllowed(savedHome: String?): Boolean = isUp && (savedHome == null || fingerprint() == savedHome)

    /** On Wi-Fi, a home network is saved, and this isn't it. */
    fun isForeign(savedHome: String?): Boolean {
        val current = fingerprint()
        return isUp && savedHome != null && current != null && current != savedHome
    }

    /** Directed broadcast for the current subnet, e.g. 192.168.1.255. */
    fun subnetBroadcast(): InetAddress? = link?.let(::ipv4Address)?.let { maskedAddress(it, invert = true) }

    fun socketFactory(): SocketFactory = network?.socketFactory ?: SocketFactory.getDefault()

    fun bind(socket: DatagramSocket) {
        network?.let { runCatching { it.bindSocket(socket) } }
    }

    private fun ipv4Address(lp: LinkProperties): LinkAddress? =
        lp.linkAddresses.firstOrNull { it.address is Inet4Address }

    /** invert=false: network address; invert=true: broadcast address. */
    private fun maskedAddress(la: LinkAddress, invert: Boolean): InetAddress {
        val bytes = la.address.address
        val prefix = la.prefixLength
        for (i in bytes.indices) {
            val bitsInByte = (prefix - i * 8).coerceIn(0, 8)
            val mask = (0xFF shl (8 - bitsInByte)) and 0xFF
            val b = bytes[i].toInt() and 0xFF
            bytes[i] = (if (invert) b or mask.inv() else b and mask).toByte()
        }
        return InetAddress.getByAddress(bytes)
    }
}
