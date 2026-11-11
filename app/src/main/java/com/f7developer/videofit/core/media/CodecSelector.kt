package com.f7developer.videofit.core.media

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.util.Log

/**
 * Picks a hardware encoder when the device has one, and a profile/level pair
 * that actually buys quality (Baseline H.264 has no CABAC or B-frames, which is
 * why so many "compressed" videos look worse than they need to).
 */
object CodecSelector {

    private const val TAG = "CodecSelector"

    data class EncoderChoice(
        val name: String,
        val profile: Int?,
        val level: Int?,
        val hardware: Boolean,
    )

    fun videoEncoderChoices(mime: String, width: Int, height: Int, fps: Float): List<EncoderChoice> {
        val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        val infos = list.codecInfos
            .filter { it.isEncoder && it.supportedTypes.any { t -> t.equals(mime, true) } }
            .filter { supportsSurfaceInput(it, mime) }
            .sortedByDescending { hardwareScore(it) }

        val out = mutableListOf<EncoderChoice>()
        for (info in infos) {
            val profiles = preferredProfiles(info, mime)
            val hardware = hardwareScore(info) > 0
            for ((profile, level) in profiles) {
                out += EncoderChoice(info.name, profile, level, hardware)
            }
            out += EncoderChoice(info.name, null, null, hardware)
        }
        // Always keep createEncoderByType as the last resort.
        return out.distinctBy { "${it.name}:${it.profile}:${it.level}" }
    }

    private fun hardwareScore(info: MediaCodecInfo): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return when {
                info.isHardwareAccelerated -> 3
                info.isSoftwareOnly -> 0
                else -> 1
            }
        }
        val name = info.name.lowercase()
        return when {
            name.startsWith("omx.qcom") || name.startsWith("c2.qti") -> 3
            name.startsWith("omx.exynos") || name.startsWith("c2.exynos") -> 3
            name.startsWith("omx.mtk") || name.startsWith("c2.mtk") -> 3
            name.contains("hw") -> 2
            name.contains("google") || name.contains("sw") || name.contains("ffmpeg") -> 0
            else -> 1
        }
    }

    private fun supportsSurfaceInput(info: MediaCodecInfo, mime: String): Boolean = try {
        val caps = info.getCapabilitiesForType(mime)
        caps.colorFormats.any { it == MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface }
    } catch (t: Throwable) {
        false
    }

    private fun preferredProfiles(info: MediaCodecInfo, mime: String): List<Pair<Int, Int?>> {
        val caps = try {
            info.getCapabilitiesForType(mime)
        } catch (t: Throwable) {
            return emptyList()
        }
        val supportedProfiles = caps.profileLevels.map { it.profile }.toSet()
        return when (mime) {
            MediaFormat.MIMETYPE_VIDEO_AVC -> {
                val preferred = listOf(
                    MediaCodecInfo.CodecProfileLevel.AVCProfileHigh,
                    MediaCodecInfo.CodecProfileLevel.AVCProfileMain,
                    MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline,
                )
                preferred.firstOrNull { it in supportedProfiles }
                    ?.let { listOf(it to MediaCodecInfo.CodecProfileLevel.AVCLevel41) }
                    ?: emptyList()
            }
            // HEVC levels are not exposed as public constants on every SDK, so
            // only the profile is pinned and the encoder picks a level.
            MediaFormat.MIMETYPE_VIDEO_HEVC ->
                if (MediaCodecInfo.CodecProfileLevel.HEVCProfileMain in supportedProfiles) {
                    listOf(MediaCodecInfo.CodecProfileLevel.HEVCProfileMain to null)
                } else {
                    emptyList()
                }
            else -> emptyList()
        }
    }

    fun describeVideoEncoders(mime: String): String {
        val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        return list.codecInfos
            .filter { it.isEncoder && it.supportedTypes.any { t -> t.equals(mime, true) } }
            .joinToString(", ") { info ->
                val hw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && info.isHardwareAccelerated) "hw" else "sw?"
                "${info.name}($hw)"
            }
            .ifEmpty { "none" }
            .also { Log.d(TAG, "encoders for $mime: $it") }
    }
}
