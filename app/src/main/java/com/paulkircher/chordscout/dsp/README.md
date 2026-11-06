# DSP & Synthesis Engine (`com.paulkircher.chordscout.dsp`)

## Purpose

Implements constant-Q chromagram extraction, two-pass key-aware Viterbi chord progression recognition, and additive 6-string guitar chord synthesis for offline audio analysis and interactive chord preview.

## Public Interfaces

- **[ChromaExtractor.kt:ChromaExtractor]**:
  - `class ChromaExtractor(val sampleRate: Int = 22050, val windowSize: Int = 2048, val hopSize: Int = 1024)`
  - Precomputes 61 Hann-windowed complex sinusoid kernels (`MIDI_LOW = 36` `C2` to `MIDI_HIGH = 96` `C7`, `BINS_PER_OCTAVE = 12`, `MAX_KERNEL_SECONDS = 0.30`), each L2-normalized to unit energy.
  - `fun extractChromagram(pcm: FloatArray): List<FloatArray>`: Returns one 12-element pitch-class vector (`C, C#, D, D#, E, F, F#, G, G#, A, A#, B`) per `hopSize` (`1024` samples, `~46.4 ms` at `22050 Hz`), log-compressed with `ln(1 + 10 * x)` and L2-normalized. Returns `emptyList()` when `pcm.size < windowSize`.
  - `fun frameCenter(frame: Int): Int`: Returns `frame * hopSize + hopSize / 2`.
- **[ChordAnalyzer.kt:ChordAnalyzer]**:
  - Constants: `PITCH_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")`, `CONFIDENCE_THRESHOLD = 0.25f`, `MIN_SEGMENT_DURATION = 0.10f`, `SILENCE_RMS_THRESHOLD = 0.015f`, `CHROMA_MEDIAN_WIDTH = 3`, `SWITCH_PENALTY = 0.9f`, `KEY_BONUS = 0.05f`, `CLARITY_POWER = 2f`.
  - `fun buildChordTemplates(includeSevenths: Boolean = false): Map<String, FloatArray>`: Builds L2-normalized 12-bin pitch-class templates for 12 major triads (`1.0, 0.8, 0.8`) and 12 minor triads (`1.0, 0.8, 0.8`) (`24` total by default in production), plus 12 dominant 7th (`1.0, 0.75, 0.75, 0.65`) and 12 minor 7th (`1.0, 0.75, 0.75, 0.65`) templates (`48` total) when `includeSevenths = true`.
  - `fun analyze(pcm: FloatArray, sampleRate: Int = 22050, fileName: String = "Track", includeSevenths: Boolean = false, confidenceThreshold: Float = CONFIDENCE_THRESHOLD, minSegmentDuration: Float = MIN_SEGMENT_DURATION, silenceRmsThreshold: Float = SILENCE_RMS_THRESHOLD, chromaMedianWidth: Int = CHROMA_MEDIAN_WIDTH, switchPenalty: Float = SWITCH_PENALTY, keyBonus: Float = KEY_BONUS, clarityPower: Float = CLARITY_POWER, onProgress: (Float, String) -> Unit = { _, _ -> }): AnalysisResult`
    - Evaluates each hop against the chord templates plus a final `"N"` (no-chord / silence) state that scores `1.0f` on silent frames (`frameRms < silenceRmsThreshold`) and `confidenceThreshold` (`0.25f`) on voiced frames. Voiced frames map their correlation score to per-frame segment confidence `max(0.1f, min(1.0f, (scores[i][state] - 0.2f) / 0.5f))` (`1.0f` for `"N"`).
    - Reports monotonic progress stages (`0.45f` chromagram extraction, `0.70f` key/chord matching, `0.90f` segment smoothing, `1.0f` done) following `AudioDecoder`'s `0.05f..0.40f` decode stage.
  - `fun estimateKey(chromaMean: FloatArray): String`: Krumhansl-Schmuckler 12-key major/minor profile correlation used as a fallback when `keyFromChords` returns `null`.
  - `fun isDiatonic(chord: String, tonic: Int): Boolean`: Returns `true` if `chord` is `I, IV, V` (major triad), `V7` (dominant 7th), or `ii, iii, vi` (minor triad / minor 7th) of `tonic` major.
  - `fun keyFromChords(path: IntArray, chordNames: Array<String>): Pair<Int, Boolean>?`: Infers `(majorTonic, isRelativeMinor)` from decoded chord frame coverage when at least `43` voiced frames (`~2.0 s`) exist; returns `null` on shorter clips.
- **[GuitarSynth.kt:GuitarSynth]**:
  - `suspend fun playChord(chord: GuitarChord, durationSec: Float = 1.5f)`: Synthesizes a 6-string strummed chord on `Dispatchers.Default` at `44100 Hz` (`32 ms` inter-string strum delay, fundamental + 2nd + 3rd harmonics with per-string exponential decay, normalized to `0.85f` peak), then switches to `Dispatchers.Main`, verifies `ensureActive()`, checks `releaseGeneration` so a concurrent `release()` during synthesis aborts playback, and plays a static `AudioTrack`.
  - `suspend fun playChord(chordName: String)`: Looks up `chordName` in `[GuitarChordLibrary.kt:GuitarChordLibrary]` and delegates to `playChord(GuitarChord)`.
  - `fun release()`: Increments `releaseGeneration`, stops and releases `currentTrack`, and clears the reference.

## Algorithmic Invariants

1. **In-Place Peak Normalization**: `ChordAnalyzer.analyze` peak-normalizes `pcm` in-place unless `peak < max(1e-5f, silenceRmsThreshold)` or `abs(peak - 1f) < 1e-4f` so quiet recordings reach the logarithmic regime of `ln(1 + 10 * x)` in `ChromaExtractor` without amplifying sub-threshold silence noise.
2. **Mean-Centered Correlation & Clarity-Weighted Viterbi**: Both frame chroma vectors and the templates returned by `buildChordTemplates` are mean-centered and L2 re-normalized via `centered(vec)` before dot-product scoring (Pearson correlation), and each frame's evidence weight scales by `min(1f, (best / 0.5f)^clarityPower)` so low-correlation frames cannot overcome `switchPenalty`.
3. **Two-Pass Key-Aware Decoding & Weighted Smoothing**: Pass 1 decodes with uniform priors; if `keyFromChords` identifies a tonic (≥ 43 voiced frames), Pass 2 adds `keyBonus` (`+0.05f`) to diatonic chords and re-runs Viterbi before `mergeAndSmooth` coalesces adjacent identical chords with duration-weighted average confidence and absorbs segments shorter than `minSegmentDuration` (`0.10f` s).

## Verification

Covered by `[ChordAnalyzerTest.kt:ChordAnalyzerTest]`:

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests "com.paulkircher.chordscout.ChordAnalyzerTest"
```
