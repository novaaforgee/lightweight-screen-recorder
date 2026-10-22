# Lightweight Android Screen Recorder (Kotlin)

A lightweight, zero-watermark, and battery-efficient screen recording app for Android built with Kotlin and native MediaProjection API.

## Features
- **Ultra-lightweight:** No bloatware, zero third-party ads.
- **MediaProjection & MediaRecorder:** Native hardware-accelerated H.264 video encoding.
- **High Resolution:** Auto-detects device native resolution and DPI (up to 1080p+ @ 30 FPS).
- **Audio Switch:** Toggle microphone commentary audio on/off.
- **Foreground Service:** Persistent recording even when app goes to background, with stop button directly in notification drawer.
- **Auto-save:** Recordings saved automatically to `Movies/LiteScreenRecorder`.

## Cloud Build (No Android Studio Needed on PC)
Push to GitHub repository and **GitHub Actions** will automatically build the APK for you! Download the ready `.apk` directly from the Actions tab.
