# UI Components (`com.paulkircher.chordscout.ui.components`)

## Purpose

Renders the five primary Jetpack Compose UI views used by `[MainActivity.kt:MainScreen]`: the initial file/demo landing card, the interactive chord timeline scrubber, the HUD transport and practice bar, the 6-string rosewood fretboard diagram, and the 4-column lead-sheet grid.

## Public Interfaces

- **[DropHeroView.kt:DropHeroView]**:
  - `@Composable fun DropHeroView(onSelectFile: () -> Unit, onTryDemo: () -> Unit, modifier: Modifier = Modifier)`
  - Landing view displayed when no audio file is loaded, showing supported format chips (`MP3`, `WAV`, `FLAC`, `M4A`, `OGG`), a **Choose Audio File** button, and a **Try Demo Song (C-G-Am-F)** button.
- **[WaveformTimeline.kt:WaveformTimeline]**:
  - `@Composable fun WaveformTimeline(segments: List<ChordSegment>, durationSeconds: Float, currentPlaybackSeconds: Float, onSeek: (Float) -> Unit, modifier: Modifier = Modifier)`
  - `60.dp`-tall custom `Canvas` timeline drawing color-coded chord blocks (`getChordColor`), segment dividers, centered chord labels (when block width `> 25.dp` and `seg.chord != "N"`), and an `AccentAmber` playhead laser with tap-to-seek gesture handling (`detectTapGestures`).
- **[HudTransportBar.kt:HudTransportBar]**:
  - `@Composable fun HudTransportBar(currentChord: String, nextChord: String?, nextCountdownSeconds: Float?, keyEstimate: String?, isPlaying: Boolean, currentTimeSec: Float, totalTimeSec: Float, playbackSpeed: Float, isLooping: Boolean, onTogglePlay: () -> Unit, onPrevChord: () -> Unit, onNextChord: () -> Unit, onToggleLoop: () -> Unit, onSpeedSelected: (Float) -> Unit, onStrumChord: () -> Unit = {}, modifier: Modifier = Modifier)`
  - Displays the active chord badge (tappable to trigger `onStrumChord`), upcoming chord preview with `Locale.US` countdown (`"in %.1fs"`), estimated key, elapsed/total timestamps, transport controls (Prev, Play/Pause, Next, Loop), and practice speed selector (`0.5x`, `0.75x`, `1.0x`, `1.25x`).
- **[RosewoodFretboard.kt:RosewoodFretboard]**:
  - `@Composable fun RosewoodFretboard(chordName: String, onStrumChord: () -> Unit = {}, modifier: Modifier = Modifier)`
  - Resolves `chordName` via `[GuitarChordLibrary.kt:GuitarChordLibrary]` and renders a 6-string, 4-fret rosewood fretboard `Canvas` with nut or `"${baseFret}fr"` base-fret position indicator, pearloid fret inlays (single dot on frets `3, 5, 7, 9`; double dot on fret `12`), wound bronze (`E, A, D`) and steel (`G, B, e`) strings, muted (`x`), open (`o`), and numbered finger dots (`1..4`), plus **Strum** button and tap-on-fretboard triggers invoking `onStrumChord`.
- **[LeadSheetGrid.kt:LeadSheetGrid]**:
  - `@Composable fun LeadSheetGrid(segments: List<ChordSegment>, activeSegmentIndex: Int, onSegmentSelected: (Int, Float) -> Unit, modifier: Modifier = Modifier)`
  - 4-column `LazyVerticalGrid` displaying each `[ChordSegment.kt:ChordSegment]` with its start timestamp (`MM:SS`), chord label, confidence percentage (`0..100%`), and active-segment amber highlight; tapping any cell seeks playback to that segment.

## Component Invariants

- **Stateless Callback Boundary**: None of the UI components import `audio` or `dsp` directly; audio playback and guitar synthesis side-effects are delegated to `[MainActivity.kt:MainScreen]` via callbacks (`onSeek`, `onTogglePlay`, `onStrumChord`, etc.).
- **Canvas Allocation Hygiene**: `nativeCanvas` `android.graphics.Paint` instances in `WaveformTimeline` and `RosewoodFretboard` are cached via `remember` outside the `Canvas` draw scope; only density-dependent `textSize` (`11.sp.toPx()`) is updated per draw pass.

## Verification

```bash
./gradlew --no-daemon :app:compileDebugKotlin
```
