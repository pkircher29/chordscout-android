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
        val empty = FloatArray(0)
        assertSame(empty, PcmResampler.resample(empty, 44100, 22050))
        assertSame(pcm, PcmResampler.resample(pcm, 0, 22050))

        val upsampled = PcmResampler.resample(FloatArray(100) { 0.5f }, 11025, 22050)
        assertEquals(200, upsampled.size)
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

    @Test
    fun testEightTwentyFourAndThirtyTwoBitPcmToMono() {
        // 8-bit unsigned: 128 -> 0.0, 255 -> ~+1.0, 0 -> -1.0
        val buf8 = ByteBuffer.wrap(byteArrayOf(128.toByte(), 0.toByte()))
        val mono8 = PcmConvert.toMono(buf8, PcmConvert.ENCODING_PCM_8BIT, channelCount = 1)
        assertEquals(2, mono8.size)
        assertEquals(0.0f, mono8[0], 1e-4f)
        assertEquals(-1.0f, mono8[1], 1e-4f)

        // 24-bit packed little-endian: +4194304 (0x400000 -> +0.5f)
        val buf24 = ByteBuffer.wrap(byteArrayOf(0x00, 0x00, 0x40))
        val mono24 = PcmConvert.toMono(buf24, PcmConvert.ENCODING_PCM_24BIT_PACKED, channelCount = 1)
        assertEquals(1, mono24.size)
        assertEquals(0.5f, mono24[0], 1e-4f)

        // 32-bit signed int: 1073741824 (0x40000000 -> +0.5f)
        val buf32 = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(0x40000000)
        buf32.flip()
        val mono32 = PcmConvert.toMono(buf32, PcmConvert.ENCODING_PCM_32BIT, channelCount = 1)
        assertEquals(1, mono32.size)
        assertEquals(0.5f, mono32[0], 1e-4f)
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
