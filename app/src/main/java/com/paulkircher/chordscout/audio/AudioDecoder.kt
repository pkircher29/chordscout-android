package com.paulkircher.chordscout.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.nio.ByteOrder

object AudioDecoder {

    const val MAX_DECODE_SECONDS = 900
    private const val MAX_EMPTY_DRAIN_TRIES = 500

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
        try {
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
                throw IllegalArgumentException("No audio track found in selected file.")
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: throw IllegalArgumentException("Audio track MIME type is missing.")
            var decodedSampleRate = if (inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(1)
            } else {
                44100
            }
            var decodedChannels = if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            } else {
                1
            }
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
            val pcmChunks = ArrayList<FloatArray>()
            var totalSamples = 0
            try {
                codec.configure(inputFormat, null, null, 0)
                codec.start()

                val info = MediaCodec.BufferInfo()
                var sawInputEOS = false
                var sawOutputEOS = false
                var emptyDrainCount = 0
                var lastReportedProgress = 0.05f
                val timeoutUs = 5000L

                while (!sawOutputEOS) {
                    ensureActive()
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
                                    val mapped = 0.05f + prog * 0.35f
                                    if (mapped - lastReportedProgress >= 0.01f) {
                                        lastReportedProgress = mapped
                                        onProgress(mapped)
                                    }
                                }
                            }
                        }
                    }

                    val outputBufferId = codec.dequeueOutputBuffer(info, timeoutUs)
                    if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val outputFormat = codec.outputFormat
                        if (outputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            decodedSampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(1)
                        }
                        if (outputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            decodedChannels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                        }
                        if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            pcmEncoding = outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        }
                        emptyDrainCount = 0
                    } else if (outputBufferId >= 0) {
                        emptyDrainCount = 0
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            sawOutputEOS = true
                        }

                        if (info.size > 0) {
                            val outputBuffer = codec.getOutputBuffer(outputBufferId)!!
                            outputBuffer.position(info.offset)
                            outputBuffer.limit(info.offset + info.size)
                            outputBuffer.order(ByteOrder.LITTLE_ENDIAN)

                            val monoChunk = PcmConvert.toMono(outputBuffer, pcmEncoding, decodedChannels)
                            val maxSamples = decodedSampleRate.toLong() * MAX_DECODE_SECONDS
                            if (totalSamples.toLong() + monoChunk.size > maxSamples) {
                                codec.releaseOutputBuffer(outputBufferId, false)
                                throw IllegalArgumentException(
                                    "Audio file exceeds maximum supported length (${MAX_DECODE_SECONDS / 60} minutes)."
                                )
                            }
                            pcmChunks.add(monoChunk)
                            totalSamples += monoChunk.size
                        }

                        codec.releaseOutputBuffer(outputBufferId, false)
                    } else if (sawInputEOS) {
                        emptyDrainCount++
                        if (emptyDrainCount >= MAX_EMPTY_DRAIN_TRIES) {
                            sawOutputEOS = true
                        }
                    }
                }
            } finally {
                try {
                    codec.stop()
                } catch (_: Exception) {
                }
                codec.release()
            }

            // Concatenate chunks and release each chunk slot as it is copied to minimize peak heap
            val fullPcm = FloatArray(totalSamples)
            var offset = 0
            for (idx in pcmChunks.indices) {
                val chunk = pcmChunks[idx]
                System.arraycopy(chunk, 0, fullPcm, offset, chunk.size)
                offset += chunk.size
                pcmChunks[idx] = FloatArray(0)
            }
            pcmChunks.clear()

            if (decodedSampleRate != targetSampleRate && totalSamples > 0) {
                return@withContext runInterruptible {
                    PcmResampler.resample(fullPcm, decodedSampleRate, targetSampleRate)
                }
            }

            return@withContext fullPcm
        } finally {
            extractor.release()
        }
    }
}
