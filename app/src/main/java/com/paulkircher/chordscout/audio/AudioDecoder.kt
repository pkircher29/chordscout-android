package com.paulkircher.chordscout.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioDecoder {

    /**
     * Decode any local audio file Uri into mono float array PCM sampled at targetSampleRate (default 22050 Hz).
     */
    suspend fun decodeToMonoPcm(
        context: Context,
        uri: Uri,
        targetSampleRate: Int = 22050,
        onProgress: (Float) -> Unit = {},
    ): FloatArray = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)

        var audioTrackIndex = -1
        var inputFormat: MediaFormat? = null

        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                audioTrackIndex = i
                inputFormat = format
                break
            }
        }

        if (audioTrackIndex < 0 || inputFormat == null) {
            extractor.release()
            throw IllegalArgumentException("No audio track found in selected file.")
        }

        extractor.selectTrack(audioTrackIndex)
        val mime = inputFormat.getString(MediaFormat.KEY_MIME)!!
        var decodedSampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var decodedChannels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
        var pcmEncoding = if (inputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
            inputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
        } else {
            AudioFormat.ENCODING_PCM_16BIT
        }
        val durationUs = if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) {
            inputFormat.getLong(MediaFormat.KEY_DURATION)
        } else {
            1_000_000L
        }

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(inputFormat, null, null, 0)
        codec.start()

        val info = MediaCodec.BufferInfo()
        val pcmChunks = ArrayList<FloatArray>()
        var totalSamples = 0
        var sawInputEOS = false
        var sawOutputEOS = false

        val timeoutUs = 5000L

        try {
            while (!sawOutputEOS) {
                if (!sawInputEOS) {
                    val inputBufferId = codec.dequeueInputBuffer(timeoutUs)
                    if (inputBufferId >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputBufferId)!!
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)

                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inputBufferId, 0, 0, 0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            sawInputEOS = true
                        } else {
                            val presentationTimeUs = extractor.sampleTime
                            codec.queueInputBuffer(inputBufferId, 0, sampleSize, presentationTimeUs, 0)
                            extractor.advance()

                            if (durationUs > 0) {
                                val prog = (presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 1f)
                                onProgress(prog * 0.4f)
                            }
                        }
                    }
                }

                val outputBufferId = codec.dequeueOutputBuffer(info, timeoutUs)
                if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val outputFormat = codec.outputFormat
                    if (outputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        decodedSampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    if (outputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        decodedChannels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                    }
                    if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        pcmEncoding = outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    }
                } else if (outputBufferId >= 0) {
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        sawOutputEOS = true
                    }

                    if (info.size > 0) {
                        val outputBuffer = codec.getOutputBuffer(outputBufferId)!!
                        outputBuffer.position(info.offset)
                        outputBuffer.limit(info.offset + info.size)
                        outputBuffer.order(ByteOrder.LITTLE_ENDIAN)

                        val convertEncoding = if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
                            PcmConvert.ENCODING_PCM_FLOAT
                        } else {
                            PcmConvert.ENCODING_PCM_16BIT
                        }
                        val monoChunk = PcmConvert.toMono(outputBuffer, convertEncoding, decodedChannels)
                        pcmChunks.add(monoChunk)
                        totalSamples += monoChunk.size
                    }

                    codec.releaseOutputBuffer(outputBufferId, false)
                }
            }
        } finally {
            codec.stop()
            codec.release()
            extractor.release()
        }

        // Concatenate chunks
        val fullPcm = FloatArray(totalSamples)
        var offset = 0
        for (chunk in pcmChunks) {
            System.arraycopy(chunk, 0, fullPcm, offset, chunk.size)
            offset += chunk.size
        }

        if (decodedSampleRate != targetSampleRate && totalSamples > 0) {
            return@withContext PcmResampler.resample(fullPcm, decodedSampleRate, targetSampleRate)
        }

        return@withContext fullPcm
    }
}
