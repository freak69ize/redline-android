# Redline — Android local player

Kotlin sources for the local-only shelf. Open this folder in Android Studio (Ladybug or newer). Studio writes `local.properties` and can generate the Gradle wrapper if `gradlew` is missing.

There are no `.java` files. The Kotlin Android plugin compiles these sources. You do not add a second Java copy of the same screens.

Playback uses the file the user picks. There is no stream URL and no `INTERNET` permission.

This tree was not compiled in the environment that packaged the zip (no Android SDK there). Android Studio is the build.

## Android TV

The same APK runs on a phone and on Android TV. `AndroidManifest.xml` already has:

- `android.hardware.touchscreen` required = false
- `android.software.leanback` required = false (not TV-only)
- `LEANBACK_LAUNCHER` plus the normal launcher
- `android:banner="@drawable/tv_banner"` (320×180)

On a television the on-screen D-pad is hidden. Use the remote: focus ring is red, the first card takes focus, and the shelf is inset for overscan. Play/pause on the remote is the same as A. Double-tap Left still opens settings.

## Shelf settings

Double-tap Left, then:

- **Shelf title** replaces the word next to the logo. Blank falls back to Redline. This does not change the launcher name (`app_name` in `strings.xml` stays Redline).
- **Use a JPG or PNG** replaces the red play icon. Remove picture restores it.
- **Background color** defaults to `#0F0F0F`. Swatches and a hex field change the solid color. GIF backgrounds still sit on top of it.

## Plugins (root `build.gradle.kts`)

```kotlin
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
```

Module plugins (`app/build.gradle.kts`):

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
```

`compileSdk` / `targetSdk` 35, `minSdk` 26, Java 17.

## Dependencies — paste into `app/build.gradle.kts`

The project already contains this block. Copy it only if you are merging into another module.

```kotlin
dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-gif:2.7.0")
}
```

### Same lines for a Groovy `build.gradle`

```groovy
dependencies {
    def composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation composeBom
    implementation "androidx.compose.ui:ui"
    implementation "androidx.compose.foundation:foundation"
    implementation "androidx.compose.material3:material3"
    implementation "androidx.compose.material:material-icons-core"
    implementation "androidx.activity:activity-compose:1.9.3"
    implementation "androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7"
    implementation "androidx.lifecycle:lifecycle-runtime-compose:2.8.7"
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0"

    implementation "androidx.media3:media3-exoplayer:1.4.1"
    implementation "androidx.media3:media3-ui:1.4.1"

    implementation "io.coil-kt:coil-compose:2.7.0"
    implementation "io.coil-kt:coil-gif:2.7.0"
}
```

Do not add Media3 HLS/DASH, OkHttp, or Retrofit. Those are for streaming.

## Permissions

Permissions are **not** Gradle lines. They belong in `AndroidManifest.xml`.

This project does **not** declare a storage or network permission. `OpenMultipleDocuments` / `OpenDocument` return a URI, and the app calls `takePersistableUriPermission` so the grant survives reboot.

Add these only if you replace the picker with a full MediaStore scan:

```xml
<uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
<uses-permission
    android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />
```

`READ_MEDIA_VIDEO` is Android 13+. `READ_EXTERNAL_STORAGE` is only for Android 12 and below. Do not add `INTERNET`.

## Controls

| Input | Action |
| --- | --- |
| D-pad / arrows | Move. Focus ring is red. |
| Left, twice within 280ms | Open or close settings. On the shelf the first tap does not move focus. |
| Left / Right while playing | Seek 10 seconds |
| Up / Down while playing | Volume |
| A, Enter, D-pad center | Play or pause while a video is open. On the shelf it activates the focused card. |
| X | Favorite the focused or playing video |
| Y | Remove it from the shelf (not from the device) |
| B or Back | Close the player, then settings |

Bundled GIF backgrounds live in `app/src/main/assets/backdrops/`. The user can also pick a GIF on device.
