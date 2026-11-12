# VideoFit — Google Play release guide

Package `com.f7developer.videofit` · Brand **VideoFit** · Developer **F7 Developer**

Everything here is local-only: the app has **no `INTERNET` permission**, no
account system, no analytics SDK and no server. That is the core differentiator
and should stay true in every future release.

---

## 1. Store listing (ASO)

**App name (30 chars max)**

```
Video Compressor: VideoFit
```

**Short description (80 chars max)**

```
Compress videos to any target size in MB. Offline, fast and high quality.
```

**Full description (4000 chars max)**

```
Compress any video to an exact size in MB — offline, on your phone, with the quality you actually want to keep.

Every messenger has a limit. WhatsApp stops at 16 MB, email chokes around 20 MB, Discord caps free uploads at 20 MB. VideoFit lets you type the size you need and hits it: it calculates the bitrate budget, picks the resolution that still looks sharp at that budget, and encodes with your phone's hardware codec.

WHY VIDEOFIT
• Exact target size — 5 MB, 16 MB, 20 MB, or any custom number you type
• Smart resolution ladder — 4K sources are downscaled only as far as needed, because 20 MB of 4K looks worse than 720p with the same bits
• H.264 and H.265 (HEVC) — H.265 gives about 30% smaller files at the same quality
• Hardware accelerated — MediaCodec encode and decode, so it is fast and easy on the battery
• Batch compression — queue several videos with one target size
• Before / after preview — watch the original and the result side by side
• Size estimate before you start — resolution, bitrate and expected MB
• Real-time progress — with an ETA, in a notification, and it keeps going in the background
• Quality report — SSIM comparison against the original, so you know what you lost
• HDR aware — HLG and PQ videos are tone-mapped to SDR instead of turning grey

PRESETS THAT MATCH REAL LIMITS
WhatsApp 16 MB · Email 18 MB · Discord 20 MB · Instagram 25 MB · Stories 8 MB · or type your own.

PRIVATE BY DESIGN
No uploads. No accounts. No ads. No analytics. No internet permission at all — your videos cannot leave the device even if someone wanted them to.

FREE AND PRO
The free plan keeps the promise: unlimited compressions, any target size, full H.264 quality. VideoFit Pro adds H.265, 4K output, unlimited batch and the quality report — a single purchase, no subscription.

Built by F7 Developer.
```

**Keywords** (Play has no keyword field — work these into the description naturally):
video compressor, compress video, reduce video size, video size reducer, 20mb
video, WhatsApp video compressor, offline video compressor, HEVC, H.265, batch
compression, video shrinker.

**Category**: Video Players & Editors (alternative: Tools)
**Content rating**: Everyone
**Ads**: No
**In-app purchases**: Yes
**Privacy policy URL**: required — host a static page stating "VideoFit does not
collect, transmit or share any data; all processing happens on-device."

### Graphic assets required

| Asset | Size |
|---|---|
| App icon | 512 × 512 PNG (32-bit, no alpha) |
| Feature graphic | 1024 × 500 PNG/JPEG |
| Phone screenshots | 2–8 at 1080 × 1920 or larger |

Screenshots that convert: home with a target size set, the plan card with the
resolution/bitrate maths, live progress ring, and the before/after result with
"73% smaller".

---

## 2. Build and sign

The upload keystore lives in `keystore/videofit-upload.jks` and its passwords in
`keystore.properties` (both git-ignored). **Back this file up** — losing it means
you cannot update the app on Play (unless Play App Signing is enrolled, in which
case the upload key can be reset).

```bash
./gradlew assembleRelease      # signed APK  -> app/build/outputs/apk/release/
./gradlew bundleRelease        # signed AAB  -> app/build/outputs/bundle/release/
```

The AAB is the artifact Play requires. Before every upload:

1. Bump `versionCode` (must increase) and `versionName` in `app/build.gradle.kts`.
2. `./gradlew lintDebug` and fix anything marked error.
3. `./gradlew testDebugUnitTest connectedDebugAndroidTest` on a real device.
4. Test the release build (`assembleRelease` + `adb install`) — R8 is on, so a
   screen that works in debug can still break.

---

## 3. Play Console setup

1. **Create app** → name, default language (English), App, Free, declarations.
2. **App content**
   - Privacy policy: URL from §1.
   - Ads: No.
   - Content rating questionnaire: category "Utility", no sensitive content.
   - Target audience: 13+ / adults (not designed for children).
   - **Data safety**: "No data collected", "No data shared". The app has no
     network permission, which makes this accurate and easy to justify.
   - Government apps: No. Financial features: No. Health: No.
3. **Permissions declaration** — no sensitive permissions are requested:
   - `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC`: the compression job
     must finish while the screen is off.
   - `POST_NOTIFICATIONS`: progress notification.
   - `WRITE_EXTERNAL_STORAGE` capped at `maxSdkVersion=28`: gallery export on
     Android 8–9 only.
   - Videos are picked with the system photo picker, so no media read permission
     is needed at all.
4. **Store listing** → paste §1 text and upload graphics.
5. **Testing** → Internal testing first, then Closed, then Production.

---

## 4. Monetization (freemium)

Already implemented in code:

| Capability | Free | Pro |
|---|---|---|
| Compressions | Unlimited | Unlimited |
| Custom target size | Yes | Yes |
| H.264, any resolution up to 1080p | Yes | Yes |
| H.265 / HEVC | — | Yes |
| Output above 1080p (1440p, 4K) | — | Yes |
| Batch size | 3 per run | Unlimited |
| Quality report (SSIM) | — | Yes |

Gates live in `core/data/ProRepository.kt`; the entitlement is a single boolean
in DataStore, read through `ProRepository.limits(settings)`.

### Wiring Google Play Billing

The paywall currently unlocks through `ProRepository.unlockPro()`, reachable in
debug builds only (`BuildConfig.ALLOW_DEV_UNLOCK`). To make it a real purchase:

1. Play Console → **Monetize → Products → In-app products** → create a
   non-consumable product, e.g. `videofit_pro` (one-time).
2. Activate it, and add the licence testers under **Setup → License testing**.
3. Add the dependency: `implementation("com.android.billingclient:billing-ktx:7.1.1")`.
4. Implement a thin `BillingRepository` that:
   - connects, queries `videofit_pro`, launches the purchase flow from the
     paywall's "Unlock Pro" button;
   - on `PURCHASE` state `PURCHASED`, calls `acknowledgePurchase`, then
     `ProRepository.unlockPro()`;
   - on app start, queries `queryPurchasesAsync(INAPP)` and restores the
     entitlement for reinstall / new device;
   - fails soft: if Billing is unavailable, keep the screen usable and show the
     server-free message already in `PaywallScreen`.
5. Keep the debug unlock behind `BuildConfig.ALLOW_DEV_UNLOCK` so the shipped
   build cannot be unlocked without paying.

No backend is needed: the purchase is a local non-consumable entitlement and
Play restores it. Do not add a subscription unless you also add a server-side
check, which would break the "no server" promise.

---

## 5. Store-review readiness checklist

- [x] All UI in English, no placeholder text.
- [x] No `INTERNET` permission, so no unexpected data transmission.
- [x] No ads SDK, no analytics, no trackers.
- [x] Foreground service declared with `dataSync` type and a matching permission.
- [x] Notification channel + cancel action during long work.
- [x] Files written to app-specific storage; gallery export via MediaStore.
- [x] Sharing through `FileProvider` with a scoped path config.
- [x] Adaptive icon, monochrome layer, splash screen.
- [x] R8 shrinking enabled for release.
- [x] Licence attribution for the adapted open-source planner (see `NOTICE.md`).
- [ ] Privacy policy page hosted and linked.
- [ ] Store graphics exported at Play's exact sizes.
- [ ] Billing product created and tested with a licence tester account.

---

## 6. Versioning

`versionCode` increments by 1 per upload. Keep `versionName` semantic
(`1.0.0` → `1.0.1` for fixes, `1.1.0` for features). Release notes are written
in the Play Console per track, in English.

## Ads and the remove-ads product

Everything in the app is unlocked. Revenue comes from ads plus a one-time
"remove ads" purchase.

* AdMob ids live in `app/build.gradle.kts`:
  `manifestPlaceholders["admobAppId"]` (also injected into the manifest),
  `ADMOB_BANNER_ID`, `ADMOB_INTERSTITIAL_ID`, `ADMOB_REWARDED_ID`.
  They currently point at **Google's official test units**. Replace all four with
  the ids from your AdMob account before publishing — test ids never earn.
* Create the product in Play Console → Monetize → Products → In-app products:
  id `remove_ads`, type one-time, price **$4.99**. The app shows the localized
  Play price and falls back to "$4.99" when the store is unreachable.
* Purchases are acknowledged in `BillingManager` and stored as `ads_removed` in
  DataStore. `restore()` re-queries owned purchases, so reinstalls and other
  devices restore the entitlement.
* Rewarded ads grant 24 hours without banners (`ad_free_until`), independent of
  the purchase.
* The banner is rendered inside the home layout below the primary button, never
  as an overlay, so it cannot cover app controls or the system navigation bar.
* `INTERNET` and `ACCESS_NETWORK_STATE` are declared for the ad SDK only.
  Compressed videos, file names and pixels never leave the device.
