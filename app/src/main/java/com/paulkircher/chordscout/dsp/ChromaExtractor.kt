package com.paulkircher.chordscout.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.math.sqrt

class ChromaExtractor(
    val sampleRate: Int = 22050,
    val windowSize: Int = 2048,
    val hopSize: Int = 1024,
) {
    private val hanningWindow = FloatArray(windowSize) { i ->
        (0.5 * (1.0 - cos(2.0 * PI * i / (windowSize - 1)))).toFloat()
    }

    /**
     * Compute a sequence of 12-semitone chroma vectors from mono PCM audio.
     * Returns List of FloatArray (each of size 12).
     */
    fun extractChromagram(pcm: FloatArray): List<FloatArray> {
        if (pcm.size < windowSize) return emptyList()
        val numFrames = 1 + (pcm.size - windowSize) / hopSize

        val chromagram = ArrayList<FloatArray>(numFrames)
        val real = FloatArray(windowSize)
        val imag = FloatArray(windowSize)

        for (frame in 0 until numFrames) {
            val offset = frame * hopSize

            // Windowed frame
            for (i in 0 until windowSize) {
                real[i] = pcm[offset + i] * hanningWindow[i]
                imag[i] = 0.0f
            }

            FastFourierTransform.fft(real, imag)

            // Compute Chroma bins (12 semitones: 0=C, 1=C#, ..., 9=A, 11=B)
            val chroma = FloatArray(12)
            val halfN = windowSize / 2
            val freqResolution = sampleRate.toFloat() / windowSize

            for (k in 1 until halfN) {
                val freq = k * freqResolution
                if (freq in 60.0f..2500.0f) {
                    val mag = sqrt(real[k] * real[k] + imag[k] * imag[k])
                    val midi = 12.0 * log2((freq / 440.0).toDouble()) + 69.0
                    val pitchClass = ((midi.roundToInt() % 12) + 12) % 12
                    chroma[pitchClass] += mag
                }
            }

            // Logarithmic compression and L2 normalization
            var normSq = 0.0f
            for (p in 0 until 12) {
                chroma[p] = ln(1.0f + 10.0f * chroma[p])
                normSq += chroma[p] * chroma[p]
            }

            val norm = sqrt(normSq)
            if (norm > 1e-6f) {
                for (p in 0 until 12) {
                    chroma[p] /= norm
                }
            }

            chromagram.add(chroma)
        }

        return chromagram
    }
}
