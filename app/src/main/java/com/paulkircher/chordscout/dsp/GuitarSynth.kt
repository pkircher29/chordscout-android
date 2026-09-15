package com.paulkircher.chordscout.dsp

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.paulkircher.chordscout.model.GuitarChord
import com.paulkircher.chordscout.model.GuitarChordLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

object GuitarSynth {

    private val STANDARD_TUNING_HZ = floatArrayOf(82.41f, 110.00f, 146.83f, 196.00f, 246.94f, 329.63f)
    private const val SAMPLE_RATE = 44100

    private var currentTrack: AudioTrack? = null

    suspend fun playChord(chord: GuitarChord, durationSec: Float = 1.5f) = withContext(Dispatchers.Default) {
        val numSamples = (SAMPLE_RATE * durationSec).toInt()
        val waveform = FloatArray(numSamples)
        val strumDelaySec = 0.032f

        for (sIdx in chord.frets.indices) {
            val fret = chord.frets[sIdx]
            if (fret < 0) continue

            val baseHz = STANDARD_TUNING_HZ[sIdx]
            val freq = baseHz * Math.pow(2.0, fret / 12.0).toFloat()

            val delaySamples = (sIdx * strumDelaySec * SAMPLE_RATE).toInt()
            val stringSamples = numSamples - delaySamples
            if (stringSamples <= 0) continue

            val decayRate = 3.2f + (sIdx * 0.4f)
            val dt = 1.0f / SAMPLE_RATE

            for (i in 0 until stringSamples) {
                val t = i * dt
                val decay = exp(-decayRate * t)
                val attack = if (i < 150) (i / 150.0f) else 1.0f

                val fund = sin(2.0 * PI * freq * t).toFloat()
                val h2 = 0.5f * sin(2.0 * PI * 2.0 * freq * t).toFloat() * exp(-1.5f * decayRate * t)
                val h3 = 0.25f * sin(2.0 * PI * 3.0 * freq * t).toFloat() * exp(-2.5f * decayRate * t)

                waveform[delaySamples + i] += (fund + h2 + h3) * decay * attack
            }
        }

        // Normalize
        var maxPeak = 0.0f
        for (v in waveform) {
            val absV = Math.abs(v)
            if (absV > maxPeak) maxPeak = absV
        }
        if (maxPeak > 1e-5f) {
            val scale = 0.85f / maxPeak
            for (i in waveform.indices) waveform[i] *= scale
        }

        withContext(Dispatchers.Main) {
            try {
                currentTrack?.stop()
                currentTrack?.release()
            } catch (_: Exception) {}

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(numSamples * 4)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(waveform, 0, numSamples, AudioTrack.WRITE_BLOCKING)
            track.play()
            currentTrack = track
        }
    }

    suspend fun playChord(chordName: String) {
        val gc = GuitarChordLibrary.getChord(chordName) ?: return
        playChord(gc)
    }
}
