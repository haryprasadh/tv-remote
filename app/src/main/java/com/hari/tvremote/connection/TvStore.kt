package com.hari.tvremote.connection

import android.content.Context

/** What survives restarts: the TV's last IP and the home network seen at the last good connect. */
class TvStore(context: Context) {
    private val prefs = context.getSharedPreferences("tv", Context.MODE_PRIVATE)

    var lastHost: String?
        get() = prefs.getString("last_host", null)
        set(value) = prefs.edit().putString("last_host", value).apply()

    /** "gateway|subnet" of the Wi-Fi at the last successful connect. */
    var homeNetwork: String?
        get() = prefs.getString("home_network", null)
        set(value) = prefs.edit().putString("home_network", value).apply()
}
