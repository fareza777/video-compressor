# VideoFit — Video Compressor

Compress any video to an exact target size in MB, fully offline, on the phone's
hardware codec. Kotlin + Jetpack Compose + Material 3.

- **App name:** Video Compressor: VideoFit
- **Package:** `com.f7developer.videofit`
- **Developer:** F7 Developer
- **Short description:** Compress videos to any target size in MB. Offline, fast and high quality.
- **Min SDK:** 26 (Android 8) · **Target/compile SDK:** 35

## What it does

Type a size — 5 MB, 16 MB, 20 MB, anything — and VideoFit hits it. It computes
the bitrate budget from the target and duration, picks the resolution that still
looks sharp at that budget, encodes with MediaCodec (H.264 or H.265), and reports
what the result cost in quality.

| Feature | Where |
|---|---|
| Target-size compression (any MB) | `core/plan/CompressionPlanner.kt` |
| Resolution/bitrate optimisation | `core/plan/CompressionPlanner.kt` |
| H.264 / H.265 hardware encode | `core/media/VideoTranscoder.kt` |
| Batch compression | `core/service/CompressionService.kt` |
| WhatsApp / email / social presets | `core/model/EncodePlan.kt` |
| Before-after preview | `ui/result/ResultScreen.kt` |
| Size estimate + plan preview | `ui/home/HomeScreen.kt` |
| Real-time progress + ETA + notification | `ui/progress/ProgressScreen.kt` |
| Hardware acceleration | GL/EGL pipeline, `core/media/GlVideoRenderer.kt` |
| HDR (HLG/PQ) tone mapping | `GlVideoRenderer` shader + API 34 decoder request |
| Quality check (SSIM/PSNR) | `core/quality/QualityAnalyzer.kt` |

## Architecture

```
photo picker → VideoProbe → CompressionPlanner → CompressionEngine
                                                   │
                  ┌────────────────────────────────┴──────────────┐
                  ▼                                               ▼
   decoder → SurfaceTexture → GL (scale/rotate/tone map)      AAC encoder
                  ▼                                               │
        H.264/H.265 encoder → MuxerGate (thread-safe) → MP4 ◄─────┘
```

Details, including why MediaCodec is used instead of shipped FFmpeg binaries,
are in [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## Build

Requirements: JDK 17+, Android SDK 35, and the Android build tools. The Gradle
wrapper pins Gradle 8.14.3.

```bash
./gradlew assembleDebug          # debug APK
./gradlew testDebugUnitTest      # planner/format unit tests (JVM)
./gradlew connectedDebugAndroidTest   # on-device codec tests (needs a phone)
./gradlew assembleRelease        # signed APK (R8)
./gradlew bundleRelease          # signed AAB for Google Play
```

Signing uses `keystore.properties` + `keystore/videofit-upload.jks` (both
git-ignored). Without them the release build is unsigned.

## Testing

* `app/src/test/.../CompressionPlannerTest.kt` — the ported planner maths: the
  540p choice for 2 MB over 8 s, H.265 keeping more resolution, the 18% audio
  share, the impossible-target report, the frame-rate trade, size parsing.
* `app/src/androidTest/.../CompressionEngineTest.kt` — real device codec tests:
  probe geometry, encode under target, output re-probe, rotation baked upright,
  HEVC vs H.264 resolution choice.

## Privacy

The app declares **no `INTERNET` permission**. There is no analytics, no ads, no
account and no server. Videos are picked through the system photo picker and
processed on-device; results go to app-specific storage and optionally the
gallery via MediaStore.

## Monetization

Freemium without a backend: free gives unlimited compressions, any target size,
full H.264 quality and up to 1080p output. Pro (one-time purchase) unlocks H.265,
above-1080p output, unlimited batch and the quality report. The entitlement is a
single DataStore boolean (`ProRepository.unlockPro()`), designed to be flipped by
Play Billing acknowledgement. Steps are in
[`docs/PLAY_RELEASE.md`](docs/PLAY_RELEASE.md).

## Open-source notice

The target-size planning algorithm is adapted from the MIT-licensed
[halfsize](https://github.com/YKLovePython/halfsize) project. See
[`NOTICE.md`](NOTICE.md) for the full attribution and the codec licensing note.
