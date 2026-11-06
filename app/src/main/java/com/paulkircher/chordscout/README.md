# Root Package (`com.paulkircher.chordscout`)

## Purpose

Coordinates the Android activity lifecycle, incoming audio file intents (`ACTION_VIEW`, `ACTION_SEND`, and system file picker), background decoding + DSP chord analysis coroutine execution, `ExoPlayer` playback synchronization, and plain-text chord chart sharing.

## Public Interface & Entry Points

- **[MainActivity.kt:MainActivity]**: `ComponentActivity` hosting the Compose UI and managing a single `ExoPlayer` instance configured with `AudioAttributes.DEFAULT` and automatic audio focus (`handleAudioFocus = true`).
  - `onCreate(savedInstanceState: Bundle?)`: Initializes `ExoPlayer`, captures the launch `Intent`, and mounts `[MainActivity.kt:MainScreen]` inside `[Theme.kt:ChordScoutTheme]`.
  - `onNewIntent(intent: Intent)`: Updates `currentIntent` state so sharing or opening a new audio file while the activity is already running triggers re-analysis.
  - `onStop()`: Pauses `exoPlayer` and calls `GuitarSynth.release()` so audio playback never continues in the background when the user leaves the app or locks the screen.
  - `onDestroy()`: Calls `GuitarSynth.release()` and `exoPlayer?.release()`.
  - Private helper `shareChordSheet(text: String)` launches an `Intent.ACTION_SEND` chooser (`text/plain`) with the formatted chord progression.
- **[MainActivity.kt:extractAudioUri]**: `fun extractAudioUri(intent: Intent?): Uri?` — Extracts an audio `Uri` from `intent.data`, `Intent.EXTRA_STREAM` (via type-safe `IntentCompat.getParcelableExtra`), or `intent.clipData`.
- **[MainActivity.kt:resolveDisplayName]**: `fun resolveDisplayName(context: Context, uri: Uri, fallback: String): String` — Queries `ContentResolver` for `OpenableColumns.DISPLAY_NAME` on `content://` URIs and falls back to `uri.lastPathSegment` or `fallback`.
- **[MainActivity.kt:MainScreen]**: `@Composable fun MainScreen(exoPlayer: ExoPlayer, onShareChords: (String) -> Unit, initialIntent: Intent?, onConsumeIntent: () -> Unit = {})` — Top-level stateful screen orchestrating:
  - Single-active `analysisJob` coroutine cancellation so selecting a new file cancels any in-flight decode/analysis, resets transport state (`isPlaying = false`, `isLoopingChord = false`, `playbackSpeed = 1.0f`), decodes audio via `[AudioDecoder.kt:AudioDecoder]`, and runs `[ChordAnalyzer.kt:ChordAnalyzer]` inside `runInterruptible(Dispatchers.Default)` with child-scoped (`jobScope.launch`), monotonic Main-thread progress updates (`0.05f..0.40f` overall pipeline percentage during decode, then `0.45f -> 0.70f -> 0.90f -> 1.0f`).
  - Authoritative `isPlaying` state synchronization via a `DisposableEffect(exoPlayer)` `Player.Listener` (`onIsPlayingChanged`) and a `50 ms` position-polling loop (`LaunchedEffect(isPlaying, isLoopingChord, currentResult)`) that runs only while `isPlaying` is `true`, locks onto the current `activeSegmentIndex` when `isLoopingChord` is enabled (seeking back to `lockedSeg.startTime` when `sec >= lockedSeg.endTime - 0.08f`, where the `80 ms` lookahead compensates for the `50 ms` polling interval), and otherwise updates `activeSegmentIndex` from `res.getChordAtTime(sec)`.
  - Dedicated `seekToSeconds(sec: Float)` (timeline scrubbing and `STATE_ENDED` restart) and `seekToSegment(index: Int)` (prev/next chord buttons and lead-sheet grid cell selection) helpers that immediately synchronize `exoPlayer`, `currentPlaybackSec`, and `activeSegmentIndex` whether playing or paused.

## Subpackage Map

- [./audio/README.md](./audio/README.md): `MediaExtractor` + `MediaCodec` audio decoding, multi-bit PCM mono downmixing, and windowed-sinc resampling to `22050 Hz`.
- [./dsp/README.md](./dsp/README.md): Constant-Q filterbank chromagram extraction, two-pass key-aware Viterbi chord recognition, and additive guitar synthesis.
- [./model/README.md](./model/README.md): Data structures (`AnalysisResult`, `SongMetadata`, `ChordSegment`, `GuitarChord`) and the 48-voicing `GuitarChordLibrary`.
- [./ui/README.md](./ui/README.md): Jetpack Compose UI components and Material 3 dark theme.

## Verification

```bash
./gradlew --no-daemon :app:compileDebugKotlin :app:testDebugUnitTest
```
