package com.hari.tvremote

/** Every TV-specific fact and tunable lives here. */
object TvConfig {
    /** Fallback when nothing better is known (also what the emulator relies on: it can't see mDNS). */
    const val DEFAULT_HOST = "192.168.1.2"
    const val PORT = 6466
    const val MDNS_TYPE = "_androidtvremote2._tcp"

    /** Wi-Fi MAC, for Wake-on-LAN. (…:0F:50 is the Bluetooth MAC; WoL to it does nothing.) */
    const val WOL_MAC = "a0:91:a2:a6:0f:4f"

    /** The TV's mDNS TXT "bt" value; tells our TV apart from other Android TVs on the network. */
    const val MDNS_BT_MAC = "A0:91:A2:A6:0F:50"

    const val CLIENT_P12_ASSET = "client.p12"
    const val CLIENT_P12_PASSWORD = "tvremote"
    const val SERVER_PIN_ASSET = "server_sha256.txt"

    // Timing (ms)
    const val SEARCH_WINDOW_MS = 4_000L
    const val WAKE_WINDOW_MS = 40_000L
    const val RECONNECT_WINDOW_MS = 30_000L
    const val POWER_OFF_GRACE_MS = 5_000L
    const val OFF_PROBE_INTERVAL_MS = 15_000L
    const val SERVICE_LINGER_MS = 15 * 60_000L
    const val HOLD_THRESHOLD_MS = 400L

    /** Apps tab tiles, opened via market://launch?id=<pkg>. */
    val APPS = listOf(
        TvApp("Netflix", "com.netflix.ninja", 0xFFE50914),
        TvApp("Prime Video", "com.amazon.amazonvideo.livingroom", 0xFF00A8E1),
        TvApp("YouTube", "com.google.android.youtube.tv", 0xFFFF0033),
        TvApp("Hotstar", "in.startv.hotstar", 0xFF1F3FBF),
        TvApp("YouTube Music", "com.google.android.youtube.tvmusic", 0xFF212121),
    )
}

data class TvApp(val label: String, val pkg: String, val color: Long)
