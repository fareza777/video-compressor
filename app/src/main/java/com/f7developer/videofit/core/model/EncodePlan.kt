package com.f7developer.videofit.core.model

/** A resolved encode plan: the answer to "how do we hit this many bytes?". */
data class EncodePlan(
    val codec: VideoCodec,
    val width: Int,
    val height: Int,
    val fps: Float,
    val videoKbps: Int,
    val audioKbps: Int,
    val audioChannels: Int,
    val targetBytes: Long,
    val estimatedBytes: Long,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val sourceFps: Float,
    val downscaled: Boolean,
    val fpsReduced: Boolean,
    val toneMap: Boolean,
    val infeasible: Boolean,
    val minimumBytes: Long,
    val warnings: List<PlanWarning>,
) {
    val totalKbps: Int get() = videoKbps + audioKbps
    val resolutionLabel: String get() = "${width}x$height"
    val shrinkFactor: Double
        get() = if (estimatedBytes <= 0) 1.0 else targetBytes.toDouble() / estimatedBytes
}

enum class PlanWarning(val message: String) {
    AUDIO_TRACKS("Only the first audio track is kept."),
    SUBTITLES_DROPPED("Subtitle tracks are not carried over to the MP4."),
    AUDIO_DOWNMIX("Audio is downmixed to stereo."),
    FPS_CAPPED("Frame rate is capped at 60 fps."),
    HDR_TONEMAP("HDR source is converted to SDR BT.709 so it does not look washed out."),
    AGGRESSIVE("This target is aggressive for the resolution; keeping full size will look soft."),
    NO_DOWNSCALE("Downscaling is off, so the target may be missed."),
    LOW_BITRATE("Very low bitrate. A larger target or H.265 will look better."),
    INFEASIBLE("The target is below what this duration needs at the lowest bitrate."),
    NOTHING_TO_GAIN("This file is already smaller than the target."),
    RESOLUTION_CAP("Output resolution is limited on the free plan."),
    FPS_REDUCED("Frame rate reduced to keep detail at this size."),
    AUDIO_DROPPED("Audio will be removed."),
    HEVC_UNSUPPORTED("No H.265 encoder on this device; H.264 is used instead."),
}

/** A destination cap, e.g. WhatsApp's 16 MB. */
data class SizePreset(
    val id: String,
    val label: String,
    val megabytes: Int,
    val hint: String,
) {
    val bytes: Long get() = megabytes.toLong() * 1_000_000L
}

object Presets {
    /** Caps change over time; `--to` style custom sizes always win. */
    val ALL = listOf(
        SizePreset("whatsapp", "WhatsApp", 16, "16 MB media limit"),
        SizePreset("email", "Email", 18, "Safe for a 25 MB inbox"),
        SizePreset("discord", "Discord", 20, "Free tier upload cap"),
        SizePreset("instagram", "Instagram", 25, "Feed and Reels friendly"),
        SizePreset("story", "Story", 8, "Light stories and status"),
    )

    val WHATSAPP = ALL[0]
    val EMAIL = ALL[1]
}
