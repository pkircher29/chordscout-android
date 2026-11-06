# Theme & Palette (`com.paulkircher.chordscout.ui.theme`)

## Purpose

Defines the dark slate color palette, Material 3 `darkColorScheme` wrapper, and root-pitch chord color mapping used across the timeline and lead-sheet views.

## Public Interfaces

- **[Color.kt:BgMain]**:
  - Background & border tokens: `BgMain` (`#0B0D14`), `BgPanel` (`#131722`), `BgCard` (`#1A202F`), `BorderSubtle` (`#1E2638`), `BorderMedium` (`#2E3952`).
  - Text tokens: `TextPrimary` (`#F8FAFC`), `TextSecondary` (`#94A3B8`), `TextMuted` (`#64748B`).
  - Accent tokens: `AccentCyan` (`#38BDF8`), `AccentAmber` (`#F59E0B`), `AccentEmerald` (`#10B981`), `AccentPurple` (`#A855F7`).
- **[Color.kt:getChordColor]**:
  - `fun getChordColor(chord: String): Color` — Maps each chord root (`C, C#/Db, D, D#/Eb, E, F, F#/Gb, G, G#/Ab, A, A#/Bb, B`) to a distinct pitch-class display color for timeline blocks and lead-sheet borders, returning muted slate `Color(0xFF1E2330)` for `"N"` or empty strings and `AccentCyan` for non-chord placeholders (such as `"—"`).
- **[Theme.kt:ChordScoutTheme]**:
  - `@Composable fun ChordScoutTheme(content: @Composable () -> Unit)` — Wraps `MaterialTheme` with `DarkColorScheme` (`primary = AccentCyan`, `onPrimary = BgMain`, `secondary = AccentAmber`, `onSecondary = BgMain`, `tertiary = AccentEmerald`, `background = BgMain`, `onBackground = TextPrimary`, `surface = BgPanel`, `onSurface = TextPrimary`, `surfaceVariant = BgCard`, `onSurfaceVariant = TextSecondary`, `outline = BorderMedium`).

## Verification

```bash
./gradlew --no-daemon :app:compileDebugKotlin :app:testDebugUnitTest --tests "com.paulkircher.chordscout.ChordAnalyzerTest.testModelTimestampsAndLookupAndThemeColors"
```
