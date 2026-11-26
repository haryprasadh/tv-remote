package com.hari.tvremote.background

import android.content.Context
import android.media.AudioManager
import android.media.VolumeProvider
import android.media.session.MediaSession
import android.media.session.PlaybackState
import com.hari.tvremote.protocol.TvVolume
import com.hari.tvremote.remote.TvKey

/**
 * MediaSession with remote (relative) volume, so the phone's volume keys send VOLUME_UP/DOWN to the
 * TV. Active only while there's a live link; shows the TV's level in the system volume panel.
 */
class VolumeSession(
    context: Context,
    private val isMuted: () -> Boolean,
    private val press: (TvKey) -> Unit,
) {
    private val session = MediaSession(context, "TvRemote")
    private var max = 100
    private var provider = newProvider(max, 0)

    init {
        session.setCallback(object : MediaSession.Callback() {
            override fun onPlay() = press(TvKey.PLAY_PAUSE)
            override fun onPause() = press(TvKey.PLAY_PAUSE)
        })
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE)
                .setState(PlaybackState.STATE_PLAYING, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build(),
        )
        session.setPlaybackToRemote(provider)
    }

    fun setActive(active: Boolean) {
        if (session.isActive != active) session.isActive = active
    }

    fun setVolume(volume: TvVolume) {
        val m = volume.max.coerceAtLeast(1)
        val level = volume.level.coerceIn(0, m)
        if (m != max) {
            max = m
            provider = newProvider(m, level)
            session.setPlaybackToRemote(provider)
        } else {
            provider.currentVolume = level
        }
    }

    fun release() {
        session.isActive = false
        session.release()
    }

    private fun newProvider(max: Int, current: Int) =
        object : VolumeProvider(VOLUME_CONTROL_RELATIVE, max, current) {
            override fun onAdjustVolume(direction: Int) {
                when (direction) {
                    AudioManager.ADJUST_RAISE -> press(TvKey.VOLUME_UP)
                    AudioManager.ADJUST_LOWER -> press(TvKey.VOLUME_DOWN)
                    AudioManager.ADJUST_TOGGLE_MUTE -> press(TvKey.MUTE)
                    AudioManager.ADJUST_MUTE -> if (!isMuted()) press(TvKey.MUTE)
                    AudioManager.ADJUST_UNMUTE -> if (isMuted()) press(TvKey.MUTE)
                }
            }
        }
}
