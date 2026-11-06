# Main Source Set (`app/src/main`)

## Purpose

Contains the Android application manifest, bundled demo MP3 asset, app icon/theme XML resources, and the `com.paulkircher.chordscout` Kotlin source tree.

## Manifest, Assets & Resources

- **[AndroidManifest.xml:manifest]**:
  - Accesses user-selected or shared audio strictly through `ContentResolver` URI grants (`ActivityResultContracts.GetContent`, `Intent.ACTION_VIEW`, `Intent.ACTION_SEND`); declares zero network or broad storage runtime permissions.
  - Registers `.MainActivity` (`android:exported="true"`, `android:windowSoftInputMode="adjustResize"`, `android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout|keyboardHidden|uiMode|density"`) with three `<intent-filter>` blocks:
    1. `android.intent.action.MAIN` + `android.intent.category.LAUNCHER`
    2. `android.intent.action.VIEW` (`DEFAULT`, `BROWSABLE`, `mimeType="audio/*"`)
    3. `android.intent.action.SEND` (`DEFAULT`, `mimeType="audio/*"`)
- **Bundled Assets (`assets/`)**:
  - **[assets/demo_song.mp3:binary]**: Bundled C–G–Am–F demo audio track extracted on `Dispatchers.IO` to `context.cacheDir/demo_classic_pop.mp3` when the user taps **Try Demo Song (C-G-Am-F)** in `[DropHeroView.kt:DropHeroView]`.
- **Android Resources (`res/`)**:
  - **[res/drawable/ic_launcher_foreground.xml:vector]**: Vector launcher icon (`108dp x 108dp` viewport with a cyan guitar pick silhouette `#38BDF8`, dark slate center circle cutout `#0B0D14` at radius `14`, and vertical gold center stroke `#F59E0B`).
  - **[res/values/strings.xml:app_name]**: Application display name (`ChordScout`) referenced by `android:label="@string/app_name"` in `[AndroidManifest.xml:manifest]`.
  - **[res/values/themes.xml:Theme.ChordScout]**: Base platform theme (`android:Theme.Material.NoActionBar` with `#0B0D14` `statusBarColor`, `navigationBarColor`, and `windowBackground`).

## Subdirectory Map

- [./java/com/paulkircher/chordscout/README.md](./java/com/paulkircher/chordscout/README.md): Root Kotlin package containing `MainActivity` and subpackages `audio`, `dsp`, `model`, and `ui`.

## Verification

```bash
./gradlew --no-daemon :app:processDebugManifest :app:mergeDebugResources
```
