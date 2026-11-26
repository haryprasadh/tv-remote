package com.hari.tvremote.connection

import android.content.Context
import com.hari.tvremote.TvConfig
import com.hari.tvremote.protocol.TlsIdentity

/** Reads the embedded client identity and the TV certificate pin from the APK's assets. */
object TvIdentityLoader {
    fun load(context: Context): TlsIdentity {
        val p12 = context.assets.open(TvConfig.CLIENT_P12_ASSET).use { it.readBytes() }
        val pin = context.assets.open(TvConfig.SERVER_PIN_ASSET).bufferedReader().use { it.readText() }
        return TlsIdentity.create(p12, TvConfig.CLIENT_P12_PASSWORD.toCharArray(), pin)
    }
}
