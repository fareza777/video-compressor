# VideoFit architecture

## Pipeline

```
photo picker (SAF) → VideoProbe → CompressionPlanner → CompressionEngine
                                                          │
                              ┌───────────────────────────┴─────────────────────┐
                              ▼                                                 ▼
    MediaExtractor → MediaCodec decoder → SurfaceTexture → GL → encoder surface   MediaExtractor
                              (hardware path, no CPU copy)                         │
                              ▼                                                 ▼
                    MediaCodec H.264/H.265 encoder  ──► MuxerGate ──► MP4   MediaCodec AAC
                                                                              encoder
```

* **VideoProbe** — `MediaMetadataRetriever` for display geometry, rotation and
  duration; `MediaExtractor` for codec, bitrate, frame rate and colour transfer.
  Rotation from metadata is applied to the reported width/height, mirroring
  `probe.mjs`.
* **CompressionPlanner** — the halfsize planner, ported 1:1: total bitrate from
  target bytes ÷ duration × 0.97, audio capped at 18% of the budget, resolution
  ladder from source size down to 240p against a bits-per-pixel target
  (H.264 0.072, H.265 0.045), plus the `infeasible` check that reports the
  smallest size a duration can hold instead of silently missing the target.
* **GlVideoRenderer** — EGL + `SurfaceTexture`. Does three jobs the CPU must not
  do: bilinear downscale, baking the display rotation into pixels (so every
  player shows the MP4 upright, not only those that honour rotation metadata),
  and HDR tone mapping for HLG/PQ sources when the decoder cannot do it itself.
* **VideoTranscoder** — decoder → GL → encoder with presentation timestamps
  passed through, VBR + a 1.35× peak cap, keyframes every second, and frame
  dropping when the planner decided the target only supports a lower frame rate.
* **AudioTranscoder** — decodes to PCM and re-encodes AAC-LC at the allocated
  bitrate; falls back to stream copy for codecs the device cannot decode.
* **MuxerGate** — serialises the two encode threads onto one `MediaMuxer`
  (add both tracks, then start, then write under a lock), so audio and video
  encode in parallel instead of one after the other.
* **CompressionEngine** — orchestrates, then applies `tighterPlan` when the
  output overshoots by more than 0.5%, up to three attempts.

## Memory, thermals and battery

* No frame is ever copied to the CPU: decode and encode are bound to GPU
  textures, so peak memory stays flat regardless of resolution.
* The encoder runs on a `THREAD_PRIORITY_DISPLAY` thread, audio at
  `THREAD_PRIORITY_AUDIO`.
* A partial wake lock and a `dataSync` foreground service keep the job alive with
  the screen off; the notification exposes Cancel.
* `PowerManager.currentThermalStatus` drives a "your phone is warm" banner; the
  encoder is hardware, so it throttles itself rather than cooking the SoC.
* H.265 is offered because the same target size buys ~30% more quality, which
  means less heat for the same result.

## Freemium

`ProRepository` returns a `Limits` object (max short side, batch size, HEVC,
quality report). The UI reads it; the planner receives `maxShortSide`. The
entitlement is a single DataStore boolean so wiring Play Billing later is a
one-line change (`unlockPro()` after acknowledgement).

## Why not FFmpeg

The reference tool shells out to FFmpeg with libx264/libx265 (GPL). Shipping
that on Android would mean a 30–60 MB GPL binary, software encoding at several
times the power draw, and a licensing obligation to publish the app's source.
The same algorithm with `MediaCodec` keeps the app small, uses the dedicated
encode hardware, respects the original MIT license for the parts that were
adapted, and keeps the APK clean for Play. Where FFmpeg has a capability the
platform lacks — real VMAF, deep tone mapping — this app either approximates it
in a shader or reports the limitation honestly (SSIM instead of VMAF).
