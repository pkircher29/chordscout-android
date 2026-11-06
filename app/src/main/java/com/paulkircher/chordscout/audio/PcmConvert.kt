package com.paulkircher.chordscout.audio

import java.nio.ByteBuffer

/**
 * Mix a MediaCodec PCM buffer down to mono float in -1..1.
 * Decoders usually emit 16-bit, but some devices emit float or 24/32-bit PCM;
 * reading those bytes as shorts scrambles pitch and the matcher sticks on one chord.
 */
internal object PcmConvert {
    const val ENCODING_PCM_16BIT = 2
    const val ENCODING_PCM_8BIT = 3
    const val ENCODING_PCM_FLOAT = 4
    const val ENCODING_PCM_24BIT_PACKED = 21
    const val ENCODING_PCM_32BIT = 22

    fun toMono(buffer: ByteBuffer, encoding: Int, channelCount: Int): FloatArray {
        val channels = channelCount.coerceAtLeast(1)
        when (encoding) {
            ENCODING_PCM_FLOAT -> {
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
            ENCODING_PCM_8BIT -> {
                val frames = buffer.remaining() / channels
                val mono = FloatArray(frames)
                for (frame in 0 until frames) {
                    var sum = 0f
                    for (channel in 0 until channels) {
                        val unsigned = buffer.get().toInt() and 0xFF
                        sum += (unsigned - 128) / 128.0f
                    }
                    mono[frame] = sum / channels
                }
                return mono
            }
            ENCODING_PCM_24BIT_PACKED -> {
                val frames = (buffer.remaining() / 3) / channels
                val mono = FloatArray(frames)
                for (frame in 0 until frames) {
                    var sum = 0f
                    for (channel in 0 until channels) {
                        val b0 = buffer.get().toInt() and 0xFF
                        val b1 = buffer.get().toInt() and 0xFF
                        val b2 = buffer.get().toInt() // sign-extended MSB
                        val sample = (b2 shl 16) or (b1 shl 8) or b0
                        sum += sample / 8388608.0f
                    }
                    mono[frame] = sum / channels
                }
                return mono
            }
            ENCODING_PCM_32BIT -> {
                val ints = buffer.asIntBuffer()
                val frames = ints.remaining() / channels
                val mono = FloatArray(frames)
                for (frame in 0 until frames) {
                    var sum = 0f
                    for (channel in 0 until channels) {
                        sum += ints.get() / 2147483648.0f
                    }
                    mono[frame] = sum / channels
                }
                return mono
            }
            else -> {
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
    }
}
