package com.paulkircher.chordscout.audio

import java.nio.ByteBuffer

/**
 * Mix a MediaCodec PCM buffer down to mono float in -1..1.
 * Decoders usually emit 16-bit, but some devices emit float; reading those
 * bytes as shorts scrambles pitch and the matcher sticks on one chord.
 */
internal object PcmConvert {
    const val ENCODING_PCM_16BIT = 2
    const val ENCODING_PCM_FLOAT = 4

    fun toMono(buffer: ByteBuffer, encoding: Int, channelCount: Int): FloatArray {
        val channels = channelCount.coerceAtLeast(1)
        if (encoding == ENCODING_PCM_FLOAT) {
            val floats = buffer.asFloatBuffer()
            val frames = floats.remaining() / channels
            val mono = FloatArray(frames)
            for (frame in 0 until frames) {
                var sum = 0f
                for (channel in 0 until channels) sum += floats.get()
                mono[frame] = sum / channels
            }
            return mono
        }

        val frames = (buffer.remaining() / 2) / channels
        val mono = FloatArray(frames)
        for (frame in 0 until frames) {
            var sum = 0f
            for (channel in 0 until channels) sum += buffer.short / 32768.0f
            mono[frame] = sum / channels
        }
        return mono
    }
}
