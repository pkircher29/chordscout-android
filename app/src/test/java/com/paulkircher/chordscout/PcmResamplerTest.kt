package com.paulkircher.chordscout

import com.paulkircher.chordscout.audio.PcmConvert
import com.paulkircher.chordscout.audio.PcmResampler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class PcmResamplerTest {

    @Test
    fun testSameRateIsUnchanged() {
        val pcm = floatArrayOf(0.1f, -0.2f, 0.3f)
        assertSame(pcm, PcmResampler.resample(pcm, 22050, 22050))
    }

    @Test
    fun testDownsampleKeepsLowToneAndRejectsAlias() {
        val sourceRate = 44100
        val targetRate = 22050
        val sampleCount = sourceRate / 2
        val low = FloatArray(sampleCount) { index ->
            sin(2.0 * PI * 1000.0 * index / sourceRate).toFloat()
        }
        val high = FloatArray(sampleCount) { index ->
            sin(2.0 * PI * 15000.0 * index / sourceRate).toFloat()
        }

        val lowOut = PcmResampler.resample(low, sourceRate, targetRate)
        val highOut = PcmResampler.resample(high, sourceRate, targetRate)

        assertTrue(rms(lowOut) > 0.5)
        assertTrue("aliased 15 kHz rms=${rms(highOut)}", rms(highOut) < 0.02)
    }

    @Test
    fun testSixteenBitStereoMixesToMono() {
        val buffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putShort(0)
        buffer.putShort(16384)
        buffer.putShort(-16384)
        buffer.putShort(-16384)
        buffer.flip()

        val mono = PcmConvert.toMono(buffer, PcmConvert.ENCODING_PCM_16BIT, channelCount = 2)
        assertEquals(2, mono.size)
        assertEquals(0.25f, mono[0], 0.01f)
        assertEquals(-0.5f, mono[1], 0.01f)
    }

    @Test
    fun testFloatPcmIsNotReadAsShorts() {
        val buffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putFloat(0.5f)
        buffer.putFloat(-0.25f)
        buffer.flip()

        val mono = PcmConvert.toMono(buffer, PcmConvert.ENCODING_PCM_FLOAT, channelCount = 1)
        assertEquals(2, mono.size)
        assertEquals(0.5f, mono[0], 1e-5f)
        assertEquals(-0.25f, mono[1], 1e-5f)
    }

    private fun rms(samples: FloatArray): Double {
        val from = samples.size / 10
        val to = samples.size * 9 / 10
        var sum = 0.0
        for (index in from until to) {
            sum += samples[index] * samples[index]
        }
        return sqrt(sum / (to - from))
    }
}
