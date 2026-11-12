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

Compression is fully on-device: videos are picked through the system photo
picker, processed by the phone's codecs, and written to app-specific storage
(optionally the gallery via MediaStore). The only network use is the AdMob SDK
that renders the banner, interstitial and rewarded ads — no video, file name or
pixel is ever uploaded. No account, no analytics.

## Monetization

Everything in the app is unlocked for every user: any target size, H.264 and
H.265, 4K output, unlimited batch and the quality report.

Ads pay for it: an AdMob banner under the primary button on the home screen
(inside the layout, so it never covers a control or the system navigation bar),
an interstitial after a finished compression (90 s cooldown), and a rewarded ad
that grants 24 hours without banners.

**Remove ads** is a one-time $4.99 in-app purchase (`remove_ads`) handled by Play
Billing 7: acknowledged client-side, stored as a DataStore flag, and restored on
reinstall through `queryPurchasesAsync`. Ad unit ids and the product id live in
`app/build.gradle.kts`; they point at Google's test units until real ones are
added — see [`docs/PLAY_RELEASE.md`](docs/PLAY_RELEASE.md).

## Library and history

* **Library** (`ui/library/LibraryScreen.kt`) lists every compressed file with a
  real thumbnail, size, resolution, duration and date. Search, sort by
  newest/largest/smallest/name, play in place, share, save to the gallery or
  delete — plus "save all to gallery" and a storage total.
* **History** (`ui/history/HistoryScreen.kt`) records every run in
  `history.json` (capped at 300 entries): before/after size, target, codec,
  resolution, SSIM, duration, timestamp and failures. It shows total bytes saved,
  run count, average saving, last run, search and All/Success/Skipped/Failed
  filters, and can clear itself without touching the video files.
* **Theme** — Light (default) / Dark / System, switchable live in Settings, with
  matching splash colours from `values/` and `values-night/`.

## Open-source notice

The target-size planning algorithm is adapted from the MIT-licensed
[halfsize](https://github.com/YKLovePython/halfsize) project. See
[`NOTICE.md`](NOTICE.md) for the full attribution and the codec licensing note.

## Look and feel

* **Adaptive icon** (`res/drawable/ic_launcher_foreground.xml`) — a video frame
  with two arrows pushing together (lime down, mint up) over a dark
  radial-glow background, plus a `monochrome` layer so Android 13 themed icons
  work.
* **Splash** — `Theme.VideoFit.Splash` keeps the same mark on the dark surface
  colour, so there is no white flash between the tap and the first frame.
* **Onboarding** — three screens (`ui/onboarding/OnboardingScreen.kt`), shown
  once on first launch and gated by `onboardingDone` in DataStore. Each screen
  has a drawn illustration, one headline and one sentence, and never scrolls.
* **Type scale** — body 15sp, captions 13sp, labels 11sp. The home screen is
  built to fit a single phone screen: advanced switches are collapsed behind the
  tune icon, and the plan is a one-line strip.

## Store assets

`store/` holds Play Console-ready images generated by
`tools/make_store_assets.py` (Pillow, no network):

| File | Size | Use |
|---|---|---|
| `store/icon_512.png` | 512×512 | Play Store app icon |
| `store/feature_graphic_1024x500.png` | 1024×500 | Store listing header |

Regenerate with `python tools/make_store_assets.py`.
