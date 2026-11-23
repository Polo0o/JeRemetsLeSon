package com.durandestebe.jrls

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings

/**
 * Garantit RF05 : le volume musique ne dépasse jamais le plafond parental.
 *
 * Stratégie MVP à double sécurité :
 * 1. Un ContentObserver sur les réglages système réagit à tout changement
 *    de volume (boutons physiques inclus).
 * 2. Un clamp périodique (1 s) couvre les cas où l'observer est silencieux
 *    (comportements OEM variables).
 */
class VolumeGuard(
    private val context: Context,
    private val volumeMaxPct: () -> Int
) {

    private val audio =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var observer: ContentObserver? = null

    fun maxIndex(): Int {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return (max * volumeMaxPct().coerceIn(0, 100)) / 100
    }

    fun clamp() {
        val cap = maxIndex()
        val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (current > cap) {
            runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, cap, 0) }
        }
    }

    private val periodic = object : Runnable {
        override fun run() {
            clamp()
            handler.postDelayed(this, 1000L)
        }
    }

    fun start() {
        clamp()
        val o = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                clamp()
            }
        }
        observer = o
        context.contentResolver.registerContentObserver(
            Settings.System.CONTENT_URI, true, o
        )
        handler.postDelayed(periodic, 1000L)
    }

    fun stop() {
        observer?.let {
            runCatching { context.contentResolver.unregisterContentObserver(it) }
        }
        observer = null
        handler.removeCallbacks(periodic)
    }
}
