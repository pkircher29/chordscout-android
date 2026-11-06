# Domain Models & Chord Voicing Library (`com.paulkircher.chordscout.model`)

## Purpose

Defines the immutable data models for analyzed songs, time-aligned chord segments, 6-string guitar chord voicings, and the 48-voicing `GuitarChordLibrary` lookup table.

## Public Interfaces

- **[AnalysisResult.kt:SongMetadata]**:
  - `data class SongMetadata(val fileName: String, val durationSeconds: Float, val sampleRate: Int = 22050, val estimatedKey: String? = null)`
- **[AnalysisResult.kt:AnalysisResult]**:
  - `data class AnalysisResult(val metadata: SongMetadata, val segments: List<ChordSegment>)`
  - `fun getChordAtTime(seconds: Float): ChordSegment?`: Returns the segment where `seconds in [startTime, endTime)`, falling back to `segments.lastOrNull()`.
- **[ChordSegment.kt:ChordSegment]**:
  - `data class ChordSegment(val startTime: Float, val endTime: Float, val chord: String, val confidence: Float = 1.0f)`
  - `val duration: Float` (`maxOf(0.0f, endTime - startTime)`)
  - `val formattedStartTime: String`, `val formattedEndTime: String`
  - `companion object fun formatTimestamp(seconds: Float, includeMs: Boolean = true): String`: Formats non-negative seconds using `Locale.US` (`"%02d:%04.1f"` when `includeMs = true`, `"%02d:%02d"` when `false`), rounding tenths of a second before computing `minutes = totalTenths / 600` so values in `[59.95f, 60.0f)` format as `"01:00.0"`.
- **[GuitarChord.kt:GuitarChord]**:
  - `data class GuitarChord(val name: String, val frets: IntArray, val fingers: IntArray = intArrayOf(0, 0, 0, 0, 0, 0), val baseFret: Int = 1)`
  - `frets`: 6-element array from 6th string (low E, index `0`) to 1st string (high e, index `5`), where `-1` = muted (`x`), `0` = open (`0`), and `> 0` = absolute fret number on the guitar neck (rendered on the 4-fret diagram at `relFret = fret - baseFret + 1`).
  - `fingers`: 6-element array where `0` = unfretted (muted or open) and `1..4` = left-hand finger number (`1` = index, `2` = middle, `3` = ring, `4` = little).
  - `val stringDisplay: String`: Space-separated tablature string (e.g., `"x 3 2 0 1 0"` for `C`).
  - Structural `equals` / `hashCode` comparing `name`, `frets.contentEquals`, `fingers.contentEquals`, and `baseFret`.
- **[GuitarChordLibrary.kt:GuitarChordLibrary]**:
  - `fun getChord(chordName: String): GuitarChord?`:
    - Returns `null` for `"N"`, empty/blank strings, or unknown chord names.
    - Provides canonical 6-string voicings for all `48` analyzer templates: `12` major triads (`C`..`B`), `12` minor triads (`Cm`..`Bm`), `12` dominant 7ths (`C7`..`B7`), and `12` minor 7ths (`Cm7`..`Bm7`).
    - Normalizes flat roots (`Db -> C#`, `Eb -> D#`, `Gb -> F#`, `Ab -> G#`, `Bb -> A#`) via `FLAT_TO_SHARP` and returns a copy preserving the requested enharmonic `name`.

## Verification

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests "com.paulkircher.chordscout.ChordAnalyzerTest.testGuitarChordLibrary" --tests "com.paulkircher.chordscout.ChordAnalyzerTest.testModelTimestampsAndLookupAndThemeColors"
```
