# Blurrr for Android (MAZ)

Native Android app recreating the supplied Blurrr workflow. The Presets tab is intentionally omitted.

## Implemented foundation

- Native Jetpack Compose home and project setup screens.
- Local project persistence, including migration of projects made by the first build.
- Import of video, photo, and audio media with detected MIME type, display name, and duration.
- Separate visual and audio timeline media.
- Per-clip trim start/end controls and clip reordering.
- MP4 export using AndroidX Media3 Transformer Composition: sequential video/photo media with an optional looping audio sequence.
- Completed exports are copied to the device's Movies/Blurrr folder.

## Build

GitHub Actions builds a debug APK for development pushes. A tagged, signed release is published as \`blurrr-android.apk\`.

## Signed releases and updates

Android requires all app updates to use the same signing certificate. Before publishing a release, create a private Android keystore and add these repository Actions secrets:

- \`ANDROID_KEYSTORE_BASE64\`
- \`ANDROID_KEYSTORE_PASSWORD\`
- \`ANDROID_KEY_ALIAS\`
- \`ANDROID_KEY_PASSWORD\`

Keep the keystore and passwords private and backed up. Never rotate the key for normal updates. Increment \`versionCode\` and \`versionName\` for every release, then push a tag such as \`v1.0.1\`.

The current update screen discovers a newer GitHub release. The in-app APK download/install flow and production signing setup are still being completed; Android may require the user to approve app installs from this app.

## Remaining editor work

This is an active build based on the supplied screen references, not a claim of full Blurrr feature parity. The project uses Android Gradle Plugin 8.13.2, Kotlin 2.3.10, API 36 compile SDK, and Media3 Transformer 1.11.1. Advanced effects, text/shape layers, keyframe animation, transitions, precise multi-track timeline playback, and export options still need implementation and device testing.
