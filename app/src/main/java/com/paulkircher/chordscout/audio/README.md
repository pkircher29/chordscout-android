# Audio Decoding & Resampling (`com.paulkircher.chordscout.audio`)

## Purpose

Decodes compressed or uncompressed local audio URIs (`MP3`, `WAV`, `FLAC`, `M4A`, `OGG`) into mono `FloatArray` PCM in `[-1.0f, 1.0f]` and resamples to the analyzer's target sample rate (`22050 Hz`) using an anti-aliased windowed-sinc filter.

## Public & Internal Interfaces

- **[AudioDecoder.kt:AudioDecoder]**:
  - `const val MAX_DECODE_SECONDS = 900`: Maximum supported audio track duration (`15` minutes) enforced during decoding to bound peak heap usage.
  - `suspend fun decodeToMonoPcm(context: Context, uri: Uri, targetSampleRate: Int = 22050, onProgress: (Float) -> Unit = {}): FloatArray`:
    - Runs on `Dispatchers.IO`.
    - Selects the first `audio/*` track from `MediaExtractor`, configures and starts a platform `MediaCodec` decoder, and drains decoded output buffers into mono `FloatArray` chunks via `[PcmConvert.kt:PcmConvert]`.
    - Reports throttled decode progress in `[0.05f, 0.40f]` (`>= 0.01f` steps) via `onProgress`.
    - Concatenates chunks into `fullPcm` (releasing each chunk array slot as it is copied), clears `pcmChunks`, and resamples to `targetSampleRate` via `[PcmResampler.kt:PcmResampler]` inside `runInterruptible` when `decodedSampleRate != targetSampleRate`.
- **[PcmConvert.kt:PcmConvert]** (`internal object`):
  - `const val ENCODING_PCM_16BIT = 2`, `const val ENCODING_PCM_8BIT = 3`, `const val ENCODING_PCM_FLOAT = 4`, `const val ENCODING_PCM_24BIT_PACKED = 21`, `const val ENCODING_PCM_32BIT = 22` (matching `AudioFormat.ENCODING_PCM_*` constants).
  - `fun toMono(buffer: ByteBuffer, encoding: Int, channelCount: Int): FloatArray`: Averages multi-channel frames to mono `FloatArray` in `[-1.0f, 1.0f]`, handling 8-bit unsigned (`(u - 128) / 128.0f`), 16-bit signed (`short / 32768.0f`), 24-bit packed signed (`int24 / 8388608.0f`), 32-bit signed (`int32 / 2147483648.0f`), and 32-bit float (`asFloatBuffer()`) PCM buffers.
- **[PcmResampler.kt:PcmResampler]**:
  - `fun resample(input: FloatArray, sourceRate: Int, targetRate: Int): FloatArray`:
    - Returns `input` unchanged if `input.isEmpty()`, `sourceRate <= 0`, `targetRate <= 0`, or `sourceRate == targetRate`.
    - Applies a 49-tap (`HALF_TAPS = 24`) Hann-windowed sinc filter scaled to `min(1.0, targetRate / sourceRate)` to suppress energy above the target Nyquist frequency before downsampling.

## Invariants & Resource Safety

- **Coroutine Cancellation**: `AudioDecoder.decodeToMonoPcm` calls `ensureActive()` on every iteration of the `while (!sawOutputEOS)` decode loop so cancelling an in-flight analysis job immediately stops decoding.
- **Bounded Drain & Deterministic Native Cleanup**: `MediaExtractor` is released in an outer `finally` block, `MediaCodec` is stopped and released in an inner `finally` block, and post-input-EOS output draining terminates after at most `MAX_EMPTY_DRAIN_TRIES = 500` empty polls if a decoder omits `BUFFER_FLAG_END_OF_STREAM`.
- **Format Change Handling**: `AudioDecoder` updates `decodedSampleRate`, `decodedChannels`, and `pcmEncoding` on `MediaCodec.INFO_OUTPUT_FORMAT_CHANGED`, and guards initial `MediaFormat` lookups with `containsKey` defaults (`44100 Hz`, `1` channel, `ENCODING_PCM_16BIT`).

## Verification

Covered by `[PcmResamplerTest.kt:PcmResamplerTest]`:

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests "com.paulkircher.chordscout.PcmResamplerTest"
```
