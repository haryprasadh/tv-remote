package com.hari.tvremote.remote

import com.hari.tvremote.proto.RemoteKeyCode

/** The keys the app offers. [holdable] keys send START_LONG/END_LONG while held so the TV repeats them. */
enum class TvKey(val code: RemoteKeyCode, val label: String, val holdable: Boolean = false) {
    UP(RemoteKeyCode.KEYCODE_DPAD_UP, "Up", holdable = true),
    DOWN(RemoteKeyCode.KEYCODE_DPAD_DOWN, "Down", holdable = true),
    LEFT(RemoteKeyCode.KEYCODE_DPAD_LEFT, "Left", holdable = true),
    RIGHT(RemoteKeyCode.KEYCODE_DPAD_RIGHT, "Right", holdable = true),
    OK(RemoteKeyCode.KEYCODE_DPAD_CENTER, "OK"),
    BACK(RemoteKeyCode.KEYCODE_BACK, "Back"),
    HOME(RemoteKeyCode.KEYCODE_HOME, "Home"),
    PLAY_PAUSE(RemoteKeyCode.KEYCODE_MEDIA_PLAY_PAUSE, "Play / Pause"),
    VOLUME_UP(RemoteKeyCode.KEYCODE_VOLUME_UP, "Vol +", holdable = true),
    VOLUME_DOWN(RemoteKeyCode.KEYCODE_VOLUME_DOWN, "Vol −", holdable = true),

    /** Speaker mute (KEYCODE_MUTE would mute the TV's microphone instead). */
    MUTE(RemoteKeyCode.KEYCODE_VOLUME_MUTE, "Mute"),
    ENTER(RemoteKeyCode.KEYCODE_ENTER, "Enter"),
    POWER(RemoteKeyCode.KEYCODE_POWER, "Power"),
}
