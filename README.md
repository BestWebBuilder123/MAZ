# MAZ for Android

MAZ is a clean native Android app shell. The previous editor implementation and its app features have been removed.

## Build

Pushes to `main` build a debug APK with GitHub Actions. Open the repository's **Actions** tab, select a successful **Android APK** run, and download the `maz-android-apk` artifact.

Tagged builds publish `maz-android.apk` as a GitHub Release after release signing is configured.

## App identity

- Display name: MAZ
- Application ID: `com.maz.blurrr` (preserved so the existing app identity remains update-compatible)
- Minimum Android version: Android 8.0 (API 26)
