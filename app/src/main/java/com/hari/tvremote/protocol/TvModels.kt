package com.hari.tvremote.protocol

/** TV volume as reported by remote_set_volume_level. */
data class TvVolume(val level: Int, val max: Int, val muted: Boolean)

/** The TV's focused text field, from remote_ime_show_request. */
data class TvTextField(val value: String, val label: String)

/** Why a session ended. */
sealed interface CloseReason {
    /** TLS rejected our client certificate, or the TV's certificate no longer matches the pin. */
    data class AuthRejected(val error: Throwable) : CloseReason

    /** No message from the TV within the idle window (the TV pings every ~5 s). */
    data object Timeout : CloseReason

    /** The TV closed the stream. */
    data object Eof : CloseReason

    /** Socket error or unreadable stream. */
    data class IoError(val error: Throwable) : CloseReason

    /** We closed it. */
    data object Local : CloseReason
}
