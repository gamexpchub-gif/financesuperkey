package com.gamex.devicetester.tests

import android.Manifest
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.ProgressBar
import com.gamex.devicetester.audio.TonePlayer
import com.gamex.devicetester.audio.Tones
import com.gamex.devicetester.core.BaseTestActivity
import com.gamex.devicetester.ui.Checklist
import com.gamex.devicetester.ui.Palette
import com.gamex.devicetester.ui.actionButton
import com.gamex.devicetester.ui.dp
import com.gamex.devicetester.ui.label
import com.gamex.devicetester.ui.lpMatchWrap
import com.google.android.material.button.MaterialButton
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Loudspeaker or earpiece: random beep count the tester must identify + frequency sweep. */
class SpeakerTestActivity : BaseTestActivity() {
    companion object {
        const val EXTRA_MODE = "speaker_mode"
        const val MODE_LOUD = "loud"
        const val MODE_EAR = "ear"
    }

    private lateinit var am: AudioManager
    private lateinit var player: TonePlayer
    private var stream = AudioManager.STREAM_MUSIC
    private var prevVolume = -1
    private var prevMode = AudioManager.MODE_NORMAL
    private val earpiece get() = intent.getStringExtra(EXTRA_MODE) == MODE_EAR

    override fun onCreateTest(savedInstanceState: Bundle?) {
        am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        player = if (earpiece) {
            TonePlayer(AudioAttributes.USAGE_VOICE_COMMUNICATION, AudioAttributes.CONTENT_TYPE_SPEECH)
        } else {
            TonePlayer(AudioAttributes.USAGE_MEDIA, AudioAttributes.CONTENT_TYPE_SONIFICATION)
        }
        setInstruction(
            if (earpiece) "Hold the phone to your ear like a call. Beeps play only through the earpiece (top call speaker). Count them, then choose the number."
            else "Beeps play through the loudspeaker at high volume. Count them and choose the number. Use the sweep to listen for rattle or distortion.",
        )
        routeAudio()

        val col = scrollColumn()
        val checks = Checklist(this).add("Audio output route", "Volume level", "Test tone playback", "Count verification")
        col.addView(checks, lpMatchWrap(bottom = dp(12)))
        checks.set("Audio output route", Checklist.OK, if (earpiece) "✓ Earpiece" else "✓ Loudspeaker")
        val volPct = am.getStreamVolume(stream) * 100 / max(1, am.getStreamMaxVolume(stream))
        checks.set("Volume level", if (volPct > 0) Checklist.OK else Checklist.FAIL, "✓ $volPct%")
        if (earpiece) {
            val hasEarpiece = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .any { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
            if (!hasEarpiece) setStatus("No earpiece reported by this device (tablet?)", Palette.SKIP)
        }
        addCountQuiz(col, "beeps", "▶ Play beeps") { n, done ->
            checks.set("Test tone playback", Checklist.RUNNING, "Playing…")
            checks.set("Count verification", Checklist.PENDING)
            holo?.level = 1f
            player.play(Tones.beeps(n)) {
                holo?.level = 0.25f
                checks.set("Test tone playback", Checklist.OK)
                checks.set("Count verification", Checklist.RUNNING, "Your answer…")
                done()
            }
        }
        if (!earpiece) {
            col.addView(
                actionButton("〰 Frequency sweep 100 Hz → 8 kHz", Palette.CARD_ALT) {
                    holo?.level = 1f
                    player.play(Tones.sweep()) { holo?.level = 0.25f }
                },
                lpMatchWrap(top = dp(8)),
            )
        }
    }

    private fun routeAudio() {
        stream = if (earpiece) AudioManager.STREAM_VOICE_CALL else AudioManager.STREAM_MUSIC
        try {
            prevVolume = am.getStreamVolume(stream)
            val maxVol = am.getStreamMaxVolume(stream)
            am.setStreamVolume(stream, max(1, (maxVol * 0.85).roundToInt()), 0)
        } catch (e: Exception) {
            prevVolume = -1 // Do-Not-Disturb can block volume changes
        }
        if (earpiece) {
            prevMode = am.mode
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            if (Build.VERSION.SDK_INT >= 31) {
                am.availableCommunicationDevices
                    .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                    ?.let { am.setCommunicationDevice(it) }
            } else {
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = false
            }
        }
    }

    override fun onDestroy() {
        player.stop()
        try {
            if (prevVolume >= 0) am.setStreamVolume(stream, prevVolume, 0)
        } catch (e: Exception) {
            // ignore
        }
        if (earpiece) {
            if (Build.VERSION.SDK_INT >= 31) am.clearCommunicationDevice()
            am.mode = prevMode
        }
        super.onDestroy()
    }
}

/** Records 3 s from the main (MIC) or secondary (CAMCORDER) microphone, meters it and plays it back. */
class MicTestActivity : BaseTestActivity() {
    companion object {
        const val EXTRA_MODE = "mic_mode"
        const val MODE_MAIN = "main"
        const val MODE_SECONDARY = "secondary"
    }

    private lateinit var meter: ProgressBar
    private lateinit var recBtn: MaterialButton
    private lateinit var checks: Checklist
    private val player = TonePlayer(AudioAttributes.USAGE_MEDIA, AudioAttributes.CONTENT_TYPE_SPEECH)
    @Volatile private var stopFlag = false
    private var peakDb = -90.0
    private val main get() = intent.getStringExtra(EXTRA_MODE) != MODE_SECONDARY

    override fun currentNote(): String = String.format("Peak level %.0f dBFS", peakDb)

    override fun onCreateTest(savedInstanceState: Bundle?) {
        setInstruction(
            if (main) "Tap Record and speak normally for 3 seconds near the BOTTOM of the phone. The recording plays back so you can check clarity."
            else "This uses the secondary microphone (usually at the TOP or back, used for video & noise-cancelling). Tap Record and speak near the top of the phone.",
        )
        val col = scrollColumn()
        checks = Checklist(this).add("Microphone access", "Input stream", "Signal level", "Playback")
        col.addView(checks, lpMatchWrap(bottom = dp(12)))
        meter = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        col.addView(label("Input level", 13f, Palette.MUTED))
        col.addView(meter, lpMatchWrap(top = dp(4), bottom = dp(16)).apply { height = dp(24) })
        recBtn = actionButton("● Record 3 seconds", Palette.FAIL) {
            withPermissions(Manifest.permission.RECORD_AUDIO) { ok ->
                checks.set("Microphone access", if (ok) Checklist.OK else Checklist.FAIL)
                if (ok) startRecording() else setStatus("Microphone permission denied", Palette.FAIL)
            }
        }
        col.addView(recBtn, lpMatchWrap())
    }

    private fun levelPercent(peak: Int): Int {
        if (peak <= 0) return 0
        val db = 20 * log10(peak / 32767.0)
        return ((db + 60) / 60 * 100).roundToInt().coerceIn(0, 100)
    }

    private fun startRecording() {
        val source = if (main) MediaRecorder.AudioSource.MIC else MediaRecorder.AudioSource.CAMCORDER
        val rate = Tones.RATE
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) {
            setStatus("Microphone not supported on this device", Palette.FAIL)
            return
        }
        val rec = try {
            AudioRecord(source, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(minBuf, 4096) * 2)
        } catch (e: Exception) {
            setStatus("Could not open microphone: ${e.message}", Palette.FAIL)
            return
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            setStatus("Could not open microphone (busy or faulty)", Palette.FAIL)
            return
        }
        player.stop()
        recBtn.isEnabled = false
        setStatus("● Recording… speak now", Palette.FAIL)
        checks.set("Input stream", Checklist.OK, "✓ Open")
        checks.set("Signal level", Checklist.RUNNING, "Listening…")
        stopFlag = false
        Thread {
            val total = rate * 3
            val data = ShortArray(total)
            val buf = ShortArray(2048)
            var off = 0
            var peak = 0
            try {
                rec.startRecording()
                while (off < total && !stopFlag) {
                    val n = rec.read(buf, 0, min(buf.size, total - off))
                    if (n <= 0) break
                    var blockPeak = 0
                    for (i in 0 until n) {
                        data[off + i] = buf[i]
                        val a = abs(buf[i].toInt())
                        if (a > blockPeak) blockPeak = a
                    }
                    if (blockPeak > peak) peak = blockPeak
                    off += n
                    val lvl = levelPercent(blockPeak)
                    val s1 = buf[0] / 32767f * 3f
                    val s2 = buf[n / 3] / 32767f * 3f
                    val s3 = buf[2 * n / 3] / 32767f * 3f
                    runOnUiThread {
                        meter.progress = lvl
                        holo?.level = lvl / 100f
                        holo?.push(s1)
                        holo?.push(s2)
                        holo?.push(s3)
                    }
                }
                rec.stop()
            } catch (e: Exception) {
                // device error — handled by the peak check below
            } finally {
                rec.release()
            }
            val recorded = data.copyOf(off)
            val pk = peak
            runOnUiThread { if (!isFinishing) onRecorded(recorded, pk) }
        }.start()
    }

    private fun onRecorded(data: ShortArray, peak: Int) {
        peakDb = if (peak > 0) 20 * log10(peak / 32767.0) else -90.0
        meter.progress = 0
        if (data.isEmpty()) {
            setStatus("No audio data received — microphone faulty?", Palette.FAIL)
            recBtn.isEnabled = true
            return
        }
        setStatus(String.format("Peak %.0f dBFS — playing back…", peakDb), Palette.TEXT)
        checks.set("Signal level", if (peak > 1200) Checklist.OK else Checklist.FAIL, String.format("%.0f dBFS", peakDb))
        checks.set("Playback", Checklist.RUNNING, "Playing…")
        player.play(data) {
            recBtn.isEnabled = true
            recBtn.text = "● Record again"
            checks.set("Playback", Checklist.OK)
            if (peak > 1200) {
                autoPass(String.format("Peak level %.0f dBFS, playback OK", peakDb), 1500)
            } else {
                setStatus("Very low signal — microphone may be blocked or faulty. Try again louder.", Palette.FAIL)
            }
        }
    }

    override fun onDestroy() {
        stopFlag = true
        player.stop()
        super.onDestroy()
    }
}

/** Detects a wired / USB headset automatically and plays a tone through it. */
class HeadsetTestActivity : BaseTestActivity() {
    private lateinit var am: AudioManager
    private val player = TonePlayer()
    private var detected: String? = null
    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = refresh()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = refresh()
    }

    override fun currentNote(): String? = detected?.let { "Detected: $it" }

    override fun onCreateTest(savedInstanceState: Bundle?) {
        am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        setInstruction("Plug in wired earphones (3.5 mm jack or USB-C headset). Detection is automatic. Play the tone to check both ears. If the phone has no jack, tap Skip.")
        val col = scrollColumn()
        col.addView(actionButton("▶ Play tone in earphones") {
            holo?.level = 1f
            player.play(Tones.beeps(3, 800.0)) { holo?.level = 0.25f }
        }, lpMatchWrap())
        am.registerAudioDeviceCallback(callback, handler)
        refresh()
    }

    private fun refresh() {
        val wiredTypes = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET,
        )
        val dev = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.type in wiredTypes }
        if (dev != null) {
            val kind = when (dev.type) {
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headset (with mic)"
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired headphones"
                else -> "USB headset"
            }
            detected = kind
            setStatus("🎧 Connected: $kind — play the tone, then Pass", Palette.PASS)
        } else {
            detected = null
            setStatus("Waiting for earphones…", Palette.MUTED)
        }
    }

    override fun onDestroy() {
        am.unregisterAudioDeviceCallback(callback)
        player.stop()
        super.onDestroy()
    }
}

/** Random number of vibration pulses; tester must count them. */
class VibrationTestActivity : BaseTestActivity() {
    private lateinit var vib: Vibrator

    override fun onCreateTest(savedInstanceState: Bundle?) {
        vib = if (Build.VERSION.SDK_INT >= 31) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        setInstruction("Hold the phone in your hand. It will vibrate a random number of times — count the pulses.")
        val col = scrollColumn()
        if (!vib.hasVibrator()) setStatus("No vibration motor reported by the system", Palette.FAIL)
        addCountQuiz(col, "vibrations", "▶ Start vibration") { n, done ->
            val pattern = LongArray(1 + n * 2)
            pattern[0] = 300
            for (i in 0 until n) {
                pattern[1 + i * 2] = 350
                pattern[2 + i * 2] = 450
            }
            if (Build.VERSION.SDK_INT >= 26) {
                vib.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(pattern, -1)
            }
            handler.postDelayed({ done() }, pattern.sum() + 200)
        }
    }

    override fun onDestroy() {
        vib.cancel()
        super.onDestroy()
    }
}
