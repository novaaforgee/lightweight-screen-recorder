# Lightweight Screen Recorder for Android

A lightweight, zero-bloat, open-source Android screen recording utility written in Kotlin. Built using native Android MediaProjection APIs and hardware-accelerated MediaRecorder pipelines to deliver high performance with minimal battery footprint.

---

## Features

- **Native Performance:** Utilizes hardware-accelerated H.264 video encoding at 30 FPS.
- **Adaptive Resolution:** Automatically matches device native display metrics and DPI.
- **Audio Recording:** Optional mic capture via high-efficiency AAC audio encoder (128 kbps, 44.1 kHz).
- **Persistent Service:** Android Foreground Service with continuous status indicators.
- **Direct Notification Controls:** Instant one-tap stop action accessible from the notification shade.
- **Clean Storage:** Videos are output directly to public storage (`Movies/LiteScreenRecorder`) without watermarks or duration limits.

---

## Architecture & Tech Stack

- **Language:** Kotlin 1.9
- **UI Toolkit:** Android Material Design 3, ViewBinding, ConstraintLayout
- **Minimum SDK:** Android 7.0 (API 24)
- **Target SDK:** Android 14 (API 34)
- **Architecture:** Android Service-driven pipeline (`MediaProjectionManager` + `VirtualDisplay` + `MediaRecorder`)

---

## Permissions

| Permission | Purpose |
|---|---|
| `RECORD_AUDIO` | Optional voice/microphone recording |
| `FOREGROUND_SERVICE` | Keeps capture pipeline active when app is minimized |
| `FOREGROUND_SERVICE_MEDIA_PROJECTION` | Required for screen capture on Android 10+ |
| `POST_NOTIFICATIONS` | Notification shade controls on Android 13+ |

---

## Building from Source

### Prerequisites
- JDK 17
- Android SDK (API Level 34)

### CLI Build Commands

```bash
# Clone the repository
git clone https://github.com/novaaforgee/lightweight-screen-recorder.git
cd lightweight-screen-recorder

# Build release/debug APK
./gradlew assembleRelease
```

Compiled APK will be generated at:
`app/build/outputs/apk/release/app-release-unsigned.apk`

---

## Releases & Automated Builds

Continuous Integration via GitHub Actions automatically builds release assets on every push to `main` and publishes download-ready APKs directly to **GitHub Releases**.

---

## License

This project is licensed under the Apache License 2.0.
