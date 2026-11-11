package com.f7developer.videofit.core.model

/** Transfer function of the source video. Only PQ and HLG are real HDR. */
enum class HdrTransfer {
    NONE,
    PQ,
    HLG;

    val isHdr: Boolean get() = this != NONE
}

/**
 * Video codec target. The bits-per-pixel targets are carried over from the
 * halfsize reference planner: 0.072 bpp for H.264 and 0.045 for H.265, i.e. the
 * resolution ladder keeps the same "bits per pixel per frame" budget the
 * reference implementation uses to decide when to downscale.
 */
enum class VideoCodec(
    val label: String,
    val mimeType: String,
    val bpp: Double,
) {
    H264("H.264", "video/avc", 0.072),
    HEVC("H.265", "video/hevc", 0.045);

    val isHardwarePreferred: Boolean get() = true
}

/** Everything the planner needs to know about a source file. */
data class VideoInfo(
    val uriString: String,
    val displayName: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val fps: Float,
    val videoBitrateKbps: Int,
    val videoMime: String,
    val videoCodecName: String,
    val hasAudio: Boolean,
    val audioChannels: Int,
    val audioSampleRate: Int,
    val audioBitrateKbps: Int,
    val audioMime: String?,
    val audioTracks: Int,
    val subtitleTracks: Int,
    val hdr: HdrTransfer,
    val colorTransfer: String?,
    val tenBit: Boolean,
) {
    val durationSeconds: Double get() = durationMs / 1000.0
    val aspectRatio: Float
        get() = if (height <= 0) 1f else width.toFloat() / height
    val megabits: Double get() = sizeBytes / 1_000_000.0
}
