# Blurrr for Android (MAZ)

Native Android recreation of the Blurrr editing workflow. The custom dark theme is used, and the Presets tab is intentionally omitted.

## Implemented

- Native Jetpack Compose home, project setup, editor, and settings screens.
- Local project persistence, including existing-project migration.
- Import video, photo, and audio files with MIME type, display name, and duration detection.
- Separate horizontal video and audio timeline tracks, with clip selection, reordering, and removal.
- Per-clip trim start/end, volume, and playback-speed controls.
- Media3 preview playback for selected video/audio clips, including scrubbing and live speed/volume changes.
- Add, edit, and remove text layers with text, color, size, bold, and top/center/bottom placement.
- HSL color adjustments and quick looks.
- MP4 export with project aspect ratio, resolution, frame rate, clip trimming, clip speed/volume, HSL adjustments, text overlays, and an optional looping audio sequence.
- Exports are saved to the device's Movies/Blurrr folder.
- GitHub release update discovery, APK download, and Android package-installer handoff. Android still requires the user to approve installation.

## Build and download

GitHub Actions builds a debug APK artifact on development pushes. Open the repository's **Actions** tab, select a successful **Android APK** run, and download its `blurrr-android-apk` artifact.

A tagged release publishes `blurrr-android.apk` only after the release APK is signed and its signature is verified.

## Signed releases and automatic updates

Android requires every update to use the same signing certificate. Before publishing the first installable release, create a private Android keystore and add these repository Actions secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Keep the keystore and passwords private and backed up. Never rotate the key for normal updates. Increment `versionCode` and `versionName` for each release, then push a tag such as `v1.0.2`.

The in-app updater checks the latest GitHub release and downloads the `blurrr-android.apk` asset. Android may ask the user to allow installs from Blurrr and always shows its own installation confirmation; updates are not silent.

## Still in development

This is not yet full Blurrr feature parity. Remaining work includes keyframe animation and curves, time remapping/velocity ramps, transitions, a larger effect library, masks, shapes/vector drawing, 3D layers/camera, multi-layer compositing, full-timeline playback, export controls, and real-device testing. CI compilation does not replace testing actual imports, previews, and MP4 exports on supported Android devices.
