package com.paulkircher.chordscout

import com.paulkircher.chordscout.dsp.FastFourierTransform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class FastFourierTransformTest {

    @Test
    fun testFftImpulse() {
        // Delta impulse at 0 should have flat frequency response
        val real = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        val imag = FloatArray(8)

        FastFourierTransform.fft(real, imag)

        for (i in 0 until 8) {
            val mag = sqrt(real[i] * real[i] + imag[i] * imag[i])
            assertEquals(1.0f, mag, 1e-4f)
        }
    }

    @Test
    fun testFftSinePeak() {
        val n = 64
        val real = FloatArray(n)
        val imag = FloatArray(n)
        val targetBin = 4 // Frequency = 4 cycles per N samples

        for (i in 0 until n) {
            real[i] = sin(2.0 * PI * targetBin * i / n).toFloat()
        }

        FastFourierTransform.fft(real, imag)

        val mag4 = sqrt(real[targetBin] * real[targetBin] + imag[targetBin] * imag[targetBin])
        assertTrue("Bin 4 should dominate spectrum", mag4 > 10.0f)
    }
}
