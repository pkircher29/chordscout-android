# `:app` Module (`app/`)

## Purpose

Single-module Android application configuration (`com.paulkircher.chordscout`), ProGuard rules, and root container for the `main` application source set and `test` JUnit 4 unit test suite.

## Module Build Contract

- **[build.gradle.kts:android]**:
  - `namespace = "com.paulkircher.chordscout"`, `compileSdk = 35`, `minSdk = 26`, `targetSdk = 35`, `versionCode = 5`, `versionName = "0.4.0"`.
  - Java/Kotlin toolchain target: `JavaVersion.VERSION_17` / `jvmTarget = "17"`.
  - `buildFeatures`: `compose = true`.
  - `testOptions.unitTests.isReturnDefaultValues = true` so pure-JVM unit tests run against Android SDK stubs without instrumentation.
  - Dependencies:
    - AndroidX Core & Activity/Lifecycle: `androidx.core:core-ktx:1.15.0`, `androidx.lifecycle:lifecycle-runtime-ktx:2.8.7`, `androidx.activity:activity-compose:1.10.1`
    - Jetpack Compose BOM `2025.04.01` (`ui`, `foundation`, `material3`, `material-icons-extended`)
    - Media3 ExoPlayer (`androidx.media3:media3-exoplayer:1.5.1`)
    - Kotlin Coroutines Android (`org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1`)
    - JUnit 4 (`junit:junit:4.13.2` in `testImplementation`)
- **[proguard-rules.pro:keepattributes]**: Keeps `*Annotation*`, `SourceFile`, and `LineNumberTable` attributes for release builds referenced by `buildTypes.release.proguardFiles`.

## Subdirectory Map

- [./src/main/README.md](./src/main/README.md): Android manifest, bundled demo audio asset, XML resources, and Kotlin source packages.
- [./src/test/java/com/paulkircher/chordscout/README.md](./src/test/java/com/paulkircher/chordscout/README.md): JVM unit tests for DSP chord recognition, constant-Q chromagrams, windowed-sinc resampling, model utilities, and PCM buffer conversion.

## Verification

```bash
./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug
```
