package com.paulkircher.chordscout.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object FastFourierTransform {

    /**
     * Compute in-place Cooley-Tukey Radix-2 FFT.
     * real and imag must have length equal to a power of 2.
     */
    fun fft(real: FloatArray, imag: FloatArray) {
        val n = real.size
        require(n and (n - 1) == 0) { "FFT length must be a power of 2, got $n" }

        // Bit-reversal permutation
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                val tempR = real[i]
                real[i] = real[j]
                real[j] = tempR

                val tempI = imag[i]
                imag[i] = imag[j]
                imag[j] = tempI
            }
            var k = n shr 1
            while (k <= j) {
                j -= k
                k = k shr 1
            }
            j += k
        }

        // Cooley-Tukey butterflies
        var len = 2
        while (len <= n) {
            val halfLen = len shr 1
            val angle = (-2.0 * PI / len).toFloat()
            val wStepR = cos(angle.toDouble()).toFloat()
            val wStepI = sin(angle.toDouble()).toFloat()

            var i = 0
            while (i < n) {
                var wR = 1.0f
                var wI = 0.0f
                for (k in 0 until halfLen) {
                    val pos = i + k
                    val partner = pos + halfLen

                    val tR = wR * real[partner] - wI * imag[partner]
                    val tI = wR * imag[partner] + wI * real[partner]

                    real[partner] = real[pos] - tR
                    imag[partner] = imag[pos] - tI
                    real[pos] += tR
                    imag[pos] += tI

                    val nextWR = wR * wStepR - wI * wStepI
                    val nextWI = wR * wStepI + wI * wStepR
                    wR = nextWR
                    wI = nextWI
                }
                i += len
            }
            len = len shl 1
        }
    }
}
