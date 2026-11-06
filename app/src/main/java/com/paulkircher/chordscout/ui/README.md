# UI Layer (`com.paulkircher.chordscout.ui`)

## Purpose

Contains the Jetpack Compose visual components and Material 3 dark theme for ChordScout Android. All components in `components/` are stateless `@Composable` views driven by state and callbacks from `[MainActivity.kt:MainScreen]` with no direct dependency on `audio` or `dsp`.

## Subpackage Map

- [./components/README.md](./components/README.md): Compose views (`DropHeroView`, `WaveformTimeline`, `HudTransportBar`, `RosewoodFretboard`, `LeadSheetGrid`).
- [./theme/README.md](./theme/README.md): Dark slate color tokens (`BgMain`, `BgPanel`, `AccentCyan`, `AccentAmber`, etc.), root-note chord color mapping (`getChordColor`), and `ChordScoutTheme`.

## Verification

```bash
./gradlew --no-daemon :app:compileDebugKotlin
```
