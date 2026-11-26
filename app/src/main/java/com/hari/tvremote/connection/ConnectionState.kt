package com.hari.tvremote.connection

import com.hari.tvremote.protocol.TvTextField
import com.hari.tvremote.protocol.TvVolume

/** Connection states from the spec's "Connection states" table (InUseElsewhere dropped: the TV allows two clients). */
enum class ConnectionState(val label: String, val short: String) {
    /** Nothing tried yet, or the background service stopped. */
    Idle("Not connected", "Not connected"),
    Searching("Finding TV…", "Finding…"),
    Waking("Waking TV…", "Waking…"),
    Connected("Connected", "On"),
    TvStandby("TV is in standby", "Standby"),
    Asleep("TV is asleep", "Asleep"),
    Reconnecting("Reconnecting…", "Reconnecting…"),
    Off("TV is off. Switch it on at the wall", "Off"),
    NotHomeWifi("Can't reach the TV. Are you on home Wi-Fi?", "Not on home Wi-Fi"),
    NeedsRepair("TV needs re-pairing", "Needs re-pairing");

    /** A live link: commands can be sent. */
    val hasLink: Boolean get() = this == Connected || this == TvStandby

    val isBusy: Boolean get() = this == Searching || this == Waking
}

/** What the TV last reported over the live link. */
data class TvInfo(
    val volume: TvVolume? = null,
    val currentApp: String = "",
    /** Non-null while the TV keyboard is open. [imeSeq] changes each time it opens. */
    val textField: TvTextField? = null,
    val imeSeq: Int = 0,
)
