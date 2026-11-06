# Unit Test Suite (`app/src/test/java/com/paulkircher/chordscout`)

## Purpose

Pure-JVM JUnit 4 test suite (`24` tests across `2` test classes) verifying constant-Q chromagram extraction, two-pass key-aware Viterbi chord recognition, harmonic guitar synthesis regression cases, chord voicing lookups, timestamp/model utilities, windowed-sinc anti-alias resampling, and multi-bit PCM-to-mono buffer conversion.

## Test Inventory

- **[ChordAnalyzerTest.kt:ChordAnalyzerTest]** (`19` `@Test` methods + `8` private synthesis/evaluation helpers `concatChords`, `synthGuitarChord`, `synthHarmonicTone`, `synthChordTones`, `addScaled`, `addPickClicks`, `synthFlickerThird`, `voiced`):
  - `testBuildTemplates`: Verifies 24 L2-normalized triad templates (`includeSevenths = false`) and 48 templates (`includeSevenths = true`), asserting every template name resolves in `[GuitarChordLibrary.kt:GuitarChordLibrary]`.
  - `testGuitarChordLibrary`: Verifies canonical voicings (`C`, `Am`), enharmonic flat resolution across major, minor, dominant 7th, and minor 7th chords (`Db`, `Eb`, `Gb`, `Ab`, `Bb`, `Dbm`, `Ebm`, `Gbm`, `Abm`, `Bbm`, `Db7`, `Ebm7`), `null` return for `"N"` / blank / unknown chords, and `GuitarChord` `equals`/`hashCode`.
  - `testModelTimestampsAndLookupAndThemeColors`: Verifies `ChordSegment.formatTimestamp` (negative clamping, `includeMs = false`, and `59.96f -> "01:00.0"` boundary rounding), `AnalysisResult.getChordAtTime`, and `getChordColor`.
  - `testAnalyzeShortAndSilentBuffers`: Verifies fallback `"N"` segment emission when `pcm.size < windowSize` or when all frames fall below `SILENCE_RMS_THRESHOLD`.
  - `testAnalyzeSyntheticProgression`: Verifies 2s `C` -> 2s `G` pure-triad detection.
  - `testChromaExtractorIncludesExactWindow`: Verifies frame count on an exact `windowSize` (`2048`) buffer.
  - `testAnalyzePreservesQuickChordChanges`: Verifies `0.4s` chord changes (`C -> G -> Am -> F`).
  - `testChromaOfC4PeaksOnC`: Verifies C4 (`261.63 Hz`) peaks on pitch class `0` (`C`).
  - `testHarmonicOpenFStaysF`: Verifies a 28-harmonic detuned open `F` guitar chord does not misclassify as `Am`.
  - `testHarmonicProgressionKeepsEachChange`: Verifies `0.55s` harmonic guitar chords (`C -> G -> Am -> F`).
  - `testNearTieDoesNotFlipUntilItClearsTheMargin`: Verifies `SWITCH_PENALTY` prevents G/Gm power-chord and weak-third flickering.
  - `testShortBurstDoesNotCreateAChange`: Verifies a `50 ms` transient burst inside a `2.0 s` `C` chord is absorbed.
  - `testPickClicksDoNotSplitARingingChord`: Verifies periodic `2.4 kHz` pick clicks do not split a ringing `C` chord.
  - `testOpenChordsKeepTheirQuality`: Verifies open `E`, `A`, `G`, and `F` harmonic guitar voicings retain their quality.
  - `testStrummedProgressionChangesOnTime`: Verifies an 8-chord strummed progression (`C -> Am -> F -> G -> C -> Em -> Am -> G`) transitions within `±120 ms` of each `1.0 s` boundary.
  - `testKeyComesFromTheDecodedChords`: Verifies `C -> G -> Am -> F` estimates `"C major"`.
  - `testKeyFromChordsPicksRelativeMinorWhenViOutlastsI`: Verifies `ChordAnalyzer.keyFromChords` major vs. relative-minor disambiguation and `< 43`-frame `null` threshold.
  - `testDiatonicChords`: Verifies `ChordAnalyzer.isDiatonic` for diatonic and non-diatonic chords in C major.
  - `testConfidenceThresholdCanRejectEverything`: Verifies `confidenceThreshold = 0.99f` rejects all frames to `"N"`.
- **[PcmResamplerTest.kt:PcmResamplerTest]** (`5` `@Test` methods + `1` private helper `rms`):
  - `testSameRateIsUnchanged`: Verifies `PcmResampler.resample` returns the input array reference when `sourceRate == targetRate`, when `input` is empty, or when sample rates are non-positive, and verifies upsampling output length (`11025 Hz -> 22050 Hz`).
  - `testDownsampleKeepsLowToneAndRejectsAlias`: Verifies `44100 Hz -> 22050 Hz` downsampling preserves a `1 kHz` tone (`RMS > 0.5`) and suppresses a `15 kHz` tone above Nyquist (`RMS < 0.02`).
  - `testSixteenBitStereoMixesToMono`: Verifies `PcmConvert.toMono` stereo 16-bit signed short downmixing.
  - `testFloatPcmIsNotReadAsShorts`: Verifies `PcmConvert.toMono` 32-bit float PCM handling.
  - `testEightTwentyFourAndThirtyTwoBitPcmToMono`: Verifies `PcmConvert.toMono` 8-bit unsigned, 24-bit packed signed, and 32-bit signed integer PCM conversion.

## Verification

```bash
./gradlew --no-daemon :app:testDebugUnitTest
```
