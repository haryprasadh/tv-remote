package com.hari.tvremote.protocol

import java.io.ByteArrayInputStream
import java.net.Socket
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509KeyManager
import javax.net.ssl.X509TrustManager

/**
 * TLS setup for the TV: our paired client identity (PKCS#12) plus the TV's self-signed server
 * certificate pinned by the SHA-256 of its DER encoding.
 */
class TlsIdentity private constructor(val socketFactory: SSLSocketFactory) {
    companion object {
        fun create(pkcs12: ByteArray, password: CharArray, serverSha256Hex: String): TlsIdentity {
            val store = KeyStore.getInstance("PKCS12")
            store.load(ByteArrayInputStream(pkcs12), password)
            val alias = store.aliases().toList().firstOrNull { store.isKeyEntry(it) }
                ?: throw IllegalArgumentException("No key entry in the client PKCS#12")
            val key = store.getKey(alias, password) as PrivateKey
            val chain = store.getCertificateChain(alias).map { it as X509Certificate }.toTypedArray()
            val context = SSLContext.getInstance("TLS")
            context.init(
                arrayOf(SingleKeyManager(alias, key, chain)),
                arrayOf(PinnedTrustManager(serverSha256Hex.trim().lowercase())),
                null,
            )
            return TlsIdentity(context.socketFactory)
        }
    }
}

/** Thrown when the TV presents a certificate other than the pinned one (TV reset or a different device). */
class PinMismatchException(message: String) : CertificateException(message)

private class PinnedTrustManager(private val pinHex: String) : X509TrustManager {
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val leaf = chain?.firstOrNull() ?: throw CertificateException("TV sent no certificate")
        val actual = MessageDigest.getInstance("SHA-256").digest(leaf.encoded).toHex()
        if (actual != pinHex) throw PinMismatchException("TV certificate does not match the pinned one")
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
        throw CertificateException("Client mode only")

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

/** Always offers our one client certificate, whatever CA list the TV asks for (matches OpenSSL). */
private class SingleKeyManager(
    private val alias: String,
    private val key: PrivateKey,
    private val chain: Array<X509Certificate>,
) : X509KeyManager {
    override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?) = alias
    override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?) = arrayOf(alias)
    override fun getCertificateChain(alias: String?) = chain
    override fun getPrivateKey(alias: String?) = key
    override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String? = null
    override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

/**
 * True when a TLS failure means "the TV does not trust us" (or we do not trust it), as opposed to a
 * dropped or reset connection. Checks the cause chain for the pin failure and for TLS alert names
 * as reported by both the JVM ("Received fatal alert: bad_certificate") and Conscrypt
 * ("SSLV3_ALERT_BAD_CERTIFICATE", "TLSV1_ALERT_CERTIFICATE_REQUIRED").
 */
fun isTlsAuthFailure(error: Throwable): Boolean {
    var e: Throwable? = error
    var depth = 0
    while (e != null && depth < 8) {
        if (e is PinMismatchException) return true
        val msg = (e.message ?: "").lowercase()
        if ("alert" in msg && AUTH_ALERTS.any { it in msg }) return true
        e = e.cause
        depth++
    }
    return false
}

private val AUTH_ALERTS = listOf(
    "bad_certificate", "certificate_required", "certificate_unknown", "unsupported_certificate",
    "certificate_revoked", "certificate_expired", "unknown_ca", "access_denied", "handshake_failure",
    "decrypt_error",
)
