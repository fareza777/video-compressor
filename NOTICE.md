# Third-party notices

VideoFit (com.f7developer.videofit) includes or adapts the following works.

## halfsize — MIT

The target-size planning algorithm (bitrate budget, resolution ladder driven by
bits-per-pixel, audio share, tighten-and-retry after an overshoot, SSIM verdict
thresholds) is adapted from the **halfsize** project:

- Source: https://github.com/YKLovePython/halfsize
- Copyright (c) 2026 YKLovePython
- License: MIT

```
MIT License

Copyright (c) 2026 YKLovePython

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

VideoFit does **not** ship halfsize's FFmpeg binaries. The Node.js/CLI code is
not redistributed; only the algorithm and its constants were ported to Kotlin.

## AndroidX, Jetpack Compose, Media3 — Apache License 2.0

- Copyright (c) The Android Open Source Project
- License: Apache License, Version 2.0 — https://www.apache.org/licenses/LICENSE-2.0

## Kotlin standard library and coroutines — Apache License 2.0

- Copyright (c) JetBrains s.r.o.

## Codec licensing note

VideoFit does not bundle an H.264/H.265 encoder. Encoding and decoding go through
the platform `MediaCodec` implementation supplied by the device, so codec patent
licensing is handled by the device manufacturer's licensed implementation, the
same way the phone's own camera app works. No GPL codec binaries (x264/x265) are
distributed with this app, which keeps the APK's licensing clean and lets the
hardware encoder do the work at far lower power cost.

VMAF is **not** included: Netflix's libvmaf is a sizeable native dependency with
its own model files. VideoFit measures SSIM and PSNR instead and scores them with
the same thresholds the reference project uses for SSIM.
