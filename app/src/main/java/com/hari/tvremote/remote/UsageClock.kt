package com.hari.tvremote.remote

import android.os.SystemClock

/** When the user last did anything; the background service stops 15 min after this. */
class UsageClock {
    @Volatile private var lastUse = SystemClock.elapsedRealtime()

    fun touch() {
        lastUse = SystemClock.elapsedRealtime()
    }

    fun idleMillis(): Long = SystemClock.elapsedRealtime() - lastUse
}
