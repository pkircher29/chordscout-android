package com.paulkircher.chordscout.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 12-bin chromagram from a constant-Q filterbank (one kernel per semitone, C2–C7).
 *
 * Summing raw FFT bins into pitch classes under-detects guitar chords. A 2048-point
 * FFT at 22.05 kHz is ~11 Hz wide, so low strings are not resolved, and high
 * harmonics contribute many more bins than the fundamentals. Those partials
 * (especially the 5th, a major third) fill in a *different* triad — open F then
 * ties with Am, the frame label flickers, and the segment smoother deletes the
 * real change. A constant-Q kernel gives every semitone the same weight, which is
 * what the desktop app gets from `librosa.chroma_cqt`.
 *
 * Frame rate stays [hopSize] samples so chord timing matches the desktop hop.
 */
class ChromaExtractor(
    val sampleRate: Int = 22050,
    val windowSize: Int = 2048,
    val hopSize: Int = 1024,
) {
    private class Kernel(
        val pitchClass: Int,
        val real: FloatArray,
        val imag: FloatArray,
    )

    private val kernels: List<Kernel>
    private val qFactor: Double = 1.0 / (2.0.pow(1.0 / BINS_PER_OCTAVE) - 1.0)

    init {
        val built = ArrayList<Kernel>(MIDI_HIGH - MIDI_LOW + 1)
        for (midi in MIDI_LOW..MIDI_HIGH) {
            val freq = 440.0 * 2.0.pow((midi - 69) / 12.0)
            var length = (qFactor * sampleRate / freq).roundToInt()
            length = maxOf(length, (2.0 * sampleRate / freq).toInt())
            length = minOf(length, (MAX_KERNEL_SECONDS * sampleRate).toInt())
            if (length % 2 == 0) length += 1
            if (length < 3) continue

            val real = FloatArray(length)
            val imag = FloatArray(length)
            val center = length / 2
            var energy = 0.0
            for (i in 0 until length) {
                val window = 0.5 * (1.0 - cos(2.0 * PI * i / (length - 1)))
                val time = (i - center).toDouble() / sampleRate
                val phase = 2.0 * PI * freq * time
                val re = window * cos(phase)
                val im = window * -sin(phase)
                real[i] = re.toFloat()
                imag[i] = im.toFloat()
                energy += re * re + im * im
            }
            val norm = sqrt(energy)
            if (norm > 1e-12) {
                for (i in 0 until length) {
                    real[i] = (real[i] / norm).toFloat()
                    imag[i] = (imag[i] / norm).toFloat()
                }
            }
            built.add(Kernel(midi % 12, real, imag))
        }
        kernels = built
    }

    /**
     * One L2-normalized, log-compressed chroma vector per hop.
     * Returns an empty list when [pcm] is shorter than [windowSize].
     */
    fun extractChromagram(pcm: FloatArray): List<FloatArray> {
        if (pcm.size < windowSize || kernels.isEmpty()) return emptyList()
        val numFrames = 1 + (pcm.size - windowSize) / hopSize
        val chromagram = ArrayList<FloatArray>(numFrames)
        val centerOffset = windowSize / 2

        for (frame in 0 until numFrames) {
            val center = frame * hopSize + centerOffset
            val chroma = FloatArray(12)
            for (kernel in kernels) {
                val coeffs = kernel.real
                val start = center - coeffs.size / 2
                val from = maxOf(0, start)
                val to = minOf(pcm.size, start + coeffs.size)
                var re = 0.0
                var im = 0.0
                var index = from - start
                for (sample in from until to) {
                    val value = pcm[sample].toDouble()
                    re += coeffs[index] * value
                    im += kernel.imag[index] * value
                    index++
                }
                chroma[kernel.pitchClass] += sqrt(re * re + im * im).toFloat()
            }

            var normSq = 0.0f
            for (pitch in 0 until 12) {
                chroma[pitch] = ln(1.0f + 10.0f * chroma[pitch])
                normSq += chroma[pitch] * chroma[pitch]
            }
            val norm = sqrt(normSq)
            if (norm > 1e-6f) {
                for (pitch in 0 until 12) chroma[pitch] /= norm
            }
            chromagram.add(chroma)
        }
        return chromagram
    }

    companion object {
        const val BINS_PER_OCTAVE = 12
        const val MIDI_LOW = 36 // C2
        const val MIDI_HIGH = 96 // C7
        const val MAX_KERNEL_SECONDS = 0.30
    }
}
