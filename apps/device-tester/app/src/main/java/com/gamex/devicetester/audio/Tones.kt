package com.gamex.devicetester.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.sin

object Tones {
    const val RATE = 44100

    /** [count] short beeps with a lead-in silence. */
    fun beeps(count: Int, freq: Double = 1000.0): ShortArray {
        val beep = (0.22 * RATE).toInt()
        val gap = (0.38 * RATE).toInt()
        val lead = (0.3 * RATE).toInt()
        val out = ShortArray(lead + count * (beep + gap))
        for (b in 0 until count) {
            val start = lead + b * (beep + gap)
            for (i in 0 until beep) {
                val v = sin(2.0 * PI * freq * i / RATE) * envelope(i, beep) * 0.9
                out[start + i] = (v * Short.MAX_VALUE).toInt().toShort()
            }
        }
        return out
    }

    /** Logarithmic sine sweep — reveals rattle, buzzing and dead frequency ranges. */
    fun sweep(from: Double = 100.0, to: Double = 8000.0, seconds: Double = 4.0): ShortArray {
        val n = (seconds * RATE).toInt()
        val out = ShortArray(n)
        val k = ln(to / from)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / n
            val f = from * Math.exp(k * t)
            phase += 2.0 * PI * f / RATE
            val v = sin(phase) * envelope(i, n) * 0.8
            out[i] = (v * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun envelope(i: Int, n: Int): Double {
        val fade = (0.01 * RATE).toInt()
        return when {
            i < fade -> i.toDouble() / fade
            i > n - fade -> (n - i).toDouble() / fade
            else -> 1.0
        }
    }
}

/** Plays a PCM buffer once with the given audio usage (media, voice call…). */
class TonePlayer(
    private val usage: Int = AudioAttributes.USAGE_MEDIA,
    private val contentType: Int = AudioAttributes.CONTENT_TYPE_SONIFICATION,
) {
    private var track: AudioTrack? = null
    private val handler = Handler(Looper.getMainLooper())

    fun play(data: ShortArray, onDone: (() -> Unit)? = null): Boolean {
        stop()
        return try {
            val attrs = AudioAttributes.Builder().setUsage(usage).setContentType(contentType).build()
            val fmt = AudioFormat.Builder()
                .setSampleRate(Tones.RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            val t = AudioTrack(attrs, fmt, data.size * 2, AudioTrack.MODE_STATIC, AudioManager.AUDIO_SESSION_ID_GENERATE)
            t.write(data, 0, data.size)
            t.play()
            track = t
            val ms = data.size * 1000L / Tones.RATE + 200
            handler.postDelayed({ onDone?.invoke() }, ms)
            true
        } catch (e: Exception) {
            handler.post { onDone?.invoke() }
            false
        }
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        track?.let {
            try {
                it.stop()
            } catch (e: Exception) {
                // already stopped
            }
            it.release()
        }
        track = null
    }
}
