# ChordScout Android 🎸

Local-first, privacy-respecting audio chord recognition app for Android built with Kotlin and Jetpack Compose.

ChordScout listens to or decodes an audio file (MP3, WAV, FLAC, M4A, OGG) completely on-device without any internet connection, cloud upload, or telemetry. It extracts pitch chromagrams, performs template-matching chord estimation across major, minor, and 7th chords, and displays the song's progression on an interactive rosewood fretboard and synchronized timeline.

Companion Android port of [ChordScout Desktop](https://github.com/pkircher29/ChordScout).

---

## ✨ Features

- **100% Local & Offline DSP:**
  - Android `MediaExtractor` & `MediaCodec` decoding to mono float PCM, including 16-bit and float decoder output, then an anti-aliased resample to 22.05 kHz.
  - Constant-Q chromagram, one kernel per semitone from C2 to C7, so guitar harmonics are not over-weighted the way a raw FFT bin sum is.
  - Chord template matching by centered (Pearson) correlation, decoded with Viterbi over the whole track. Each chord change costs a fixed penalty (0.9), and a new chord is kept only when its lead, summed over every frame it lasts, pays that off. Sustained changes land on the frame where the evidence flips; near-ties and passing tones never add up.
  - Two passes: the first pass's chords give the song's key (the key whose chords cover the most playing time, which is also the key shown in the app). The second pass gives in-key chords a small head start (0.05), so thin passages stop guessing out-of-key chords.
  - Each frame's evidence is weighted by how clearly it holds a chord, so thin passages need longer to change and stop flickering, while clearly played quick changes still switch at the normal cost.
  - The no-chord cutoff is 0.25 on the centered score (desktop's 0.35 is a cosine score and would blank out chords in a full mix). Silence RMS (0.015), hop (1024) and minimum segment (0.10 s) match the desktop app, and a 3-frame chroma median removes single-frame flicker.
- **Studio Dark Aesthetic:**
  - **Rosewood Guitar Fretboard:** Hardware-accelerated canvas rendering with nickel frets, mother-of-pearl position inlays (3rd, 5th, 7th, 9th, 12th double-dot), brass/bronze wound strings, gold finger dot markers, and muted/open string status badges.
  - **Acoustic Strum Synthesizer:** Tap the fretboard or chord badge to hear a physical plucked-string Karplus-Strong / harmonic acoustic strum synthesized in real-time via `AudioTrack`.
  - **Mirrored Audio Waveform Timeline:** Interactive scrubbing timeline displaying synchronized chord blocks and laser playhead.
  - **HUD Transport Bar:** Big chord readout, upcoming chord countdown timer, playback controls, loop region, and tempo speed multiplier (0.5x, 0.75x, 1.0x, 1.25x).
  - **Lead Sheet Grid:** 4-chord measure layout highlighting the active chord bar in real-time.
  - **Transposition & Capo:** Transpose chords on the fly (±11 semitones) or set a capo position with automatically updated fingering diagrams.
- **Modern Android Architecture:**
  - 100% Jetpack Compose UI (no XML layouts).
  - Media3 ExoPlayer integration for seamless playback.
  - Kotlin Coroutines & Flow for asynchronous audio decoding and DSP processing.
  - System file picker (`ActivityResultContracts.GetContent`) + Intent filter to handle "Open with ChordScout" from file managers.

---

## 📱 Screenshots & Architecture

```
com.paulkircher.chordscout
├── dsp/
│   ├── FastFourierTransform.kt  # Radix-2 Cooley-Tukey FFT
│   ├── ChromaExtractor.kt       # Constant-Q 12-pitch chromagram
│   ├── ChordAnalyzer.kt         # Template matching, key estimation & smoothing
│   └── GuitarSynth.kt           # Karplus-Strong acoustic guitar strum
├── audio/
│   ├── AudioDecoder.kt          # MediaExtractor + MediaCodec to FloatArray
│   ├── PcmConvert.kt            # 16-bit and float PCM downmix
│   └── PcmResampler.kt          # Anti-aliased resample to 22.05 kHz
├── ui/
│   ├── theme/                   # Studio dark theme, Amber/Gold accents
│   ├── RosewoodFretboard.kt     # Canvas-drawn guitar fretboard & markers
│   ├── WaveformTimeline.kt      # Mirrored waveform & chord timeline
│   ├── HudTransportBar.kt       # Transport HUD & upcoming chord countdown
│   ├── LeadSheetGrid.kt         # 4-chord measure progression grid
│   └── DropHeroView.kt          # Empty-state file picker & bundled demo
└── MainActivity.kt              # ExoPlayer controller & state management
```

---

## 🛠️ Building & Running

### Requirements
- Android SDK 35 (`minSdk = 26`, `targetSdk = 35`)
- JDK 17 (e.g. Eclipse Temurin 17)
- Gradle 8.11.1 (via included wrapper)

### Build Debug APK

For a GitHub build, open **Actions → Android Debug APK → Run workflow**.
The workflow also runs for pushes and pull requests to `main`. Successful runs
provide a `chordscout-android-debug-<run number>` artifact containing the debug APK
(retained for 30 days), plus unit-test reports. Download and unzip the artifact
to install the APK. This is a debug build, not a Play Store release.

```bash
./gradlew assembleDebug
```
The APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.

### Run Unit Tests
```bash
./gradlew test
```

### Install onto Connected Device / Emulator
```bash
./gradlew installDebug
```

---

## 📄 License

GPL-3.0 License. See [LICENSE](LICENSE) for details.
