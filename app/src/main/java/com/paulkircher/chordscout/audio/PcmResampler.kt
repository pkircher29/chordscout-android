package com.paulkircher.chordscout.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/**
 * Windowed-sinc resampler. Linear interpolation (the previous downsampler)
 * keeps energy above the new Nyquist and folds pick attack / string hiss
 * back into the chord band. That extra energy is enough to flip an
 * already-close template match.
 */
object PcmResampler {

    private const val HALF_TAPS = 24

    fun resample(input: FloatArray, sourceRate: Int, targetRate: Int): FloatArray {
        if (input.isEmpty() || sourceRate <= 0 || targetRate <= 0 || sourceRate == targetRate) {
            return input
        }
        val scale = min(1.0, targetRate.toDouble() / sourceRate.toDouble())
        val step = sourceRate.toDouble() / targetRate.toDouble()
        val outLength = (input.size / step).toInt()
        if (outLength <= 0) return FloatArray(0)

        val output = FloatArray(outLength)
        for (i in 0 until outLength) {
            val sourcePos = i * step
            val origin = floor(sourcePos).toInt()
            var acc = 0.0
            for (k in -HALF_TAPS..HALF_TAPS) {
                val index = origin + k
                if (index < 0 || index >= input.size) continue
                val offset = index - sourcePos
                if (offset > HALF_TAPS || offset < -HALF_TAPS) continue
                val window = 0.5 + 0.5 * cos(PI * offset / HALF_TAPS)
                val sinc = if (abs(offset) < 1e-8) {
                    scale
                } else {
                    sin(PI * scale * offset) / (PI * offset)
                }
                acc += input[index] * sinc * window
            }
            output[i] = acc.toFloat()
        }
        return output
    }
}
