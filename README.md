# Blurrr for Android (MAZ)

Native Android project based on the supplied Blurrr iOS reference screens. The Presets tab is intentionally omitted. The first milestone adds the dark home screen, project setup, local project list, editor layout foundation, accent theme choices, and GitHub release discovery.

## Build
GitHub Actions builds a debug APK on pushes and manual runs. Download it from the workflow run's Artifacts.

## Signed releases and updates
Android requires all app updates to use the same signing certificate. Before publishing the first tagged release, create a private Android keystore and add these repository Actions secrets:
- ANDROID_KEYSTORE_BASE64
- ANDROID_KEYSTORE_PASSWORD
- ANDROID_KEY_ALIAS
- ANDROID_KEY_PASSWORD

Keep the keystore/passwords private and backed up. Never rotate the key for normal updates. Push a version bump and tag it (for example v1.0.1) to publish blurrr-android.apk in GitHub Releases. The app checks the latest GitHub release for that asset.

This is the initial native UI foundation, not yet a complete video-rendering engine. Media selection and timeline editing/rendering will be developed in later milestones.
