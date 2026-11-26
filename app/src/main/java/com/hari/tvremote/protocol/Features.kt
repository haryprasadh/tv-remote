package com.hari.tvremote.protocol

/**
 * Feature bitmask exchanged in RemoteConfigure / RemoteSetActive (androidtvremote2 remote.Feature).
 * Leaving a bit out makes that feature fail silently on the TV.
 */
object Features {
    const val PING = 1 shl 0
    const val KEY = 1 shl 1
    const val IME = 1 shl 2
    const val VOICE = 1 shl 3
    const val POWER = 1 shl 5
    const val VOLUME = 1 shl 6
    const val APP_LINK = 1 shl 9

    /** What this app asks for: 615. Voice is deliberately not requested. */
    const val REQUESTED = PING or KEY or IME or POWER or VOLUME or APP_LINK
}
