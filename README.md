# ChordScout Android (`com.paulkircher.chordscout`)

## System Overview

Offline, on-device Android application (`v0.4.0`, `minSdk = 26`, `targetSdk = 35`) for guitar chord recognition, fretboard visualization, and interactive practice from local audio files (`MP3`, `WAV`, `FLAC`, `M4A`, `OGG`). Built with Kotlin `2.1.20`, Jetpack Compose (Material 3), Android `MediaExtractor` / `MediaCodec`, and Media3 `ExoPlayer` (`1.5.1`). It extracts 12-pitch constant-Q chromagrams, runs two-pass key-aware Viterbi chord estimation across 24 major and minor triad templates by default (or 48 templates including dominant 7th and minor 7th when `includeSevenths = true`), and presents the detected chord progression on a color-coded waveform timeline, a rosewood 6-string fretboard diagram with synthesized audio preview, and a 4-column lead-sheet grid with practice loop and speed controls (`0.5x`, `0.75x`, `1.0x`, `1.25x`).

## Architecture & Execution Pipeline

```
  Local Audio URI (Picker / ACTION_VIEW / ACTION_SEND) or Bundled Asset (demo_song.mp3)
                                       │
                                       ▼
 ┌───────────────────────────────────────────────────────────────────────────────────┐
 │ 1. Audio Decode & Resample (com.paulkircher.chordscout.audio)                     │
 │    • AudioDecoder.decodeToMonoPcm: MediaExtractor + MediaCodec -> PCM chunks      │
 │      (enforces MAX_DECODE_SECONDS = 900s ceiling and bounded output EOS drain)    │
 │    • PcmConvert.toMono: 8/16/24/32-bit int or 32-bit float -> mono FloatArray     │
 │    • PcmResampler.resample: 49-tap Hann-windowed sinc filter -> 22,050 Hz mono    │
 └───────────────────────────────────────────────────────────────────────────────────┘
                                       │
                                       ▼
 ┌───────────────────────────────────────────────────────────────────────────────────┐
 │ 2. Constant-Q Chromagram & Viterbi Chord Analysis (com.paulkircher.chordscout.dsp)│
 │    • Peak-normalizes PCM in-place (when peak >= 1e-5f and |peak - 1f| >= 1e-4f)   │
 │    • ChromaExtractor.extractChromagram: 61 Hann-windowed CQT kernels (C2..C7,     │
 │      MIDI 36..96), hopSize = 1024 (~46.4 ms), log1p compression + L2 norm         │
 │    • 3-frame temporal median filter per pitch class (CHROMA_MEDIAN_WIDTH = 3)     │
 │    • Mean-centered Pearson correlation against L2-normalized chord templates      │
 │    • Pass 1 Viterbi decode (SWITCH_PENALTY = 0.9, CLARITY_POWER = 2.0)            │
 │    • Key estimation from Pass 1 diatonic coverage (ChordAnalyzer.keyFromChords),  │
 │      falling back to Krumhansl-Schmuckler global chroma correlation (estimateKey) │
 │    • Pass 2 Viterbi decode with diatonic key bonus (KEY_BONUS = 0.05)             │
 │    • Segment merging (duration-weighted confidence) & micro-segment absorption    │
 └───────────────────────────────────────────────────────────────────────────────────┘
                                       │
                                       ▼
 ┌───────────────────────────────────────────────────────────────────────────────────┐
 │ 3. Synchronized Practice UI (MainActivity + com.paulkircher.chordscout.ui)        │
 │    • Media3 ExoPlayer with audio focus management & Player.Listener state sync    │
 │    • WaveformTimeline: color-coded chord blocks + tap-to-seek playhead laser      │
 │    • HudTransportBar: active/upcoming chord countdown, key, loop & speed          │
 │    • RosewoodFretboard / LeadSheetGrid + GuitarSynth additive harmonic preview    │
 └───────────────────────────────────────────────────────────────────────────────────┘
```

## Build & Verification

Requires JDK `17` and Android SDK Platform `35` (`build-tools;35.0.0`).

- **[build.gradle.kts:plugins]**: Root Gradle build script declaring Android Application (`8.9.2`), Kotlin Android (`2.1.20`), and Kotlin Compose (`2.1.20`) plugins.
- **[settings.gradle.kts:rootProject]**: Configures Google/MavenCentral repositories and includes `:app` (`rootProject.name = "ChordScoutAndroid"`).
- **[gradle.properties:org.gradle.jvmargs]**: Sets `-Xmx3g -Dfile.encoding=UTF-8`, `android.useAndroidX=true`, `kotlin.code.style=official`, and `android.nonTransitiveRClass=true`.
- **[.github/workflows/android-debug.yml:build]**: GitHub Actions workflow (`Android Debug APK`) running on `ubuntu-24.04` with Temurin JDK `17` and Android SDK `35` that executes `:app:assembleDebug :app:testDebugUnitTest` and uploads the debug APK and unit test reports.

Run the JUnit 4 unit test suite and assemble the debug APK from the repository root:

```bash
./gradlew --no-daemon --stacktrace :app:testDebugUnitTest :app:assembleDebug
```

## System Invariants

- **Offline Execution**: Zero network permissions (`INTERNET` is not declared in `[app/src/main/AndroidManifest.xml:manifest]`). All decoding, resampling, constant-Q chromagram extraction, Viterbi chord recognition, and guitar synthesis run locally on-device.
- **Native Resource Lifecycle**:
  - `[AudioDecoder.kt:AudioDecoder]` wraps `MediaExtractor` in `try / finally { extractor.release() }`, wraps `MediaCodec` in `try / finally { try { codec.stop() } catch (_: Exception) {}; codec.release() }`, checks `ensureActive()` inside the decode loop, bounds post-EOS output draining (`MAX_EMPTY_DRAIN_TRIES = 500`), enforces a 15-minute track ceiling (`MAX_DECODE_SECONDS = 900`), clears each chunk slot during compaction, and executes `PcmResampler.resample` inside `runInterruptible`.
  - `[MainActivity.kt:MainActivity]` configures `ExoPlayer` with `AudioAttributes.DEFAULT` (`handleAudioFocus = true`), pauses `exoPlayer` and releases `[GuitarSynth.kt:GuitarSynth]` in `onStop()`, and releases both `GuitarSynth` and `exoPlayer` in `onDestroy()`.
  - `[MainActivity.kt:MainScreen]` tracks `analysisJob` and cancels any in-flight analysis before launching a new one, executing `[ChordAnalyzer.kt:ChordAnalyzer]` inside `runInterruptible(Dispatchers.Default)` with structured, throttled, strictly monotonic progress updates (`0.05f..0.40f` decode, `0.45f` chromagram, `0.70f` key/chord matching, `0.90f` smoothing, `1.0f` done).
- **Locale-Deterministic Formatting**: Timestamp and countdown formatters (`[ChordSegment.kt:ChordSegment]`, `[HudTransportBar.kt:HudTransportBar]`) explicitly pass `Locale.US` so decimal separators remain `.` regardless of device locale, and `ChordSegment.formatTimestamp` rounds tenths of a second before splitting minutes so values in `[59.95f, 60.0f)` format as `"01:00.0"`.
- **Harmonic & Temporal Stability**:
  - Downsampling to `22050 Hz` must use `[PcmResampler.kt:PcmResampler]` (Hann-windowed sinc anti-aliasing filter) rather than linear interpolation so high-frequency pick attack and string noise do not alias into the constant-Q chord band.
  - `[ChordAnalyzer.kt:ChordAnalyzer]` produces contiguous, non-overlapping `[ChordSegment.kt:ChordSegment]` lists spanning `[0f, totalDuration]` with duration-weighted confidence and micro-segments shorter than `MIN_SEGMENT_DURATION` (`0.10f` s) absorbed into neighbors.

## Subdirectory Map

- [./app/README.md](./app/README.md): `:app` Android application module build configuration, ProGuard rules, and source/test tree map.
  - [./app/src/main/README.md](./app/src/main/README.md): Main application manifest (`AndroidManifest.xml`), bundled demo asset (`assets/demo_song.mp3`), Android resources (`res/`), and Kotlin package root.
    - [./app/src/main/java/com/paulkircher/chordscout/README.md](./app/src/main/java/com/paulkircher/chordscout/README.md): Application entry point (`MainActivity`, `MainScreen`, `extractAudioUri`, `resolveDisplayName`) and subpackage map.
      - [./app/src/main/java/com/paulkircher/chordscout/audio/README.md](./app/src/main/java/com/paulkircher/chordscout/audio/README.md): Audio decoding (`AudioDecoder`), multi-bit PCM-to-mono conversion (`PcmConvert`), and windowed-sinc resampling (`PcmResampler`).
      - [./app/src/main/java/com/paulkircher/chordscout/dsp/README.md](./app/src/main/java/com/paulkircher/chordscout/dsp/README.md): Constant-Q chromagram extraction (`ChromaExtractor`), two-pass key-aware Viterbi chord analysis (`ChordAnalyzer`), and additive guitar synthesis (`GuitarSynth`).
      - [./app/src/main/java/com/paulkircher/chordscout/model/README.md](./app/src/main/java/com/paulkircher/chordscout/model/README.md): Domain models (`AnalysisResult`, `SongMetadata`, `ChordSegment`, `GuitarChord`) and the 48-voicing `GuitarChordLibrary`.
      - [./app/src/main/java/com/paulkircher/chordscout/ui/README.md](./app/src/main/java/com/paulkircher/chordscout/ui/README.md): Jetpack Compose UI layer map (`components` and `theme`).
        - [./app/src/main/java/com/paulkircher/chordscout/ui/components/README.md](./app/src/main/java/com/paulkircher/chordscout/ui/components/README.md): Compose UI components (`DropHeroView`, `WaveformTimeline`, `HudTransportBar`, `RosewoodFretboard`, `LeadSheetGrid`).
        - [./app/src/main/java/com/paulkircher/chordscout/ui/theme/README.md](./app/src/main/java/com/paulkircher/chordscout/ui/theme/README.md): Material 3 dark theme (`ChordScoutTheme`) and root-pitch color mapping (`getChordColor`).
  - [./app/src/test/java/com/paulkircher/chordscout/README.md](./app/src/test/java/com/paulkircher/chordscout/README.md): JUnit 4 unit test suite (`ChordAnalyzerTest`, `PcmResamplerTest`) verifying CQT chromagrams, Viterbi chord recognition, key detection, resampler anti-aliasing, model utilities, and multi-bit PCM conversion.
