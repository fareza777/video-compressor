package com.f7developer.videofit.core.plan

import com.f7developer.videofit.core.model.EncodePlan
import com.f7developer.videofit.core.model.PlanWarning
import com.f7developer.videofit.core.model.VideoCodec
import com.f7developer.videofit.core.model.VideoInfo
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Target-size planner.
 *
 * This is a direct port of the algorithm in the MIT-licensed `halfsize`
 * reference (https://github.com/YKLovePython/halfsize, src/plan.mjs):
 *
 * 1. total bitrate = target bytes * 8 / duration, minus ~3% container overhead
 * 2. take a share of that for audio (18%, capped by the source and a preference)
 * 3. walk a resolution ladder from the source size down to 240p and pick the
 *    largest size whose pixels fit the remaining budget at the codec's
 *    bits-per-pixel target
 * 4. warn instead of silently missing an impossible target
 *
 * Android additions: a resolution cap for the free tier, and an automatic
 * frame-rate drop when the target is so tight that fewer frames per second
 * buys visibly more detail.
 */
object CompressionPlanner {

    /** Landscape tiers; portrait sources get the swapped variant. */
    private val TIERS = listOf(
        3840 to 2160,
        2560 to 1440,
        1920 to 1080,
        1600 to 900,
        1280 to 720,
        960 to 540,
        854 to 480,
        640 to 360,
        426 to 240,
    )

    private fun even(value: Int): Int = max(2, (value / 2.0).roundToInt() * 2)

    data class Dimensions(val width: Int, val height: Int)

    fun candidates(info: VideoInfo, maxShortSide: Int? = null): List<Dimensions> {
        val out = mutableListOf<Dimensions>()
        val seen = mutableSetOf<String>()
        fun push(w: Int, h: Int) {
            val key = "${w}x$h"
            if (seen.add(key)) out.add(Dimensions(w, h))
        }

        push(even(info.width), even(info.height))
        val portrait = info.height > info.width
        for ((tw, th) in TIERS) {
            val (targetW, targetH) = if (portrait) th to tw else tw to th
            val scale = min(1.0, min(targetW.toDouble() / info.width, targetH.toDouble() / info.height))
            push(even((info.width * scale).roundToInt()), even((info.height * scale).roundToInt()))
        }

        if (maxShortSide == null) return out
        val capped = out.filter { min(it.width, it.height) <= maxShortSide }
        return capped.ifEmpty { listOf(out.last()) }
    }

    private fun audioKbps(info: VideoInfo, totalKbps: Double): Int {
        if (!info.hasAudio) return 0
        val preferred = when {
            totalKbps > 2500 -> 160
            totalKbps > 800 -> 128
            totalKbps > 300 -> 96
            else -> 64
        }
        val source = if (info.audioBitrateKbps > 0) info.audioBitrateKbps else preferred
        val share = totalKbps * 0.18
        val value = min(preferred.toDouble(), min(source.toDouble(), share))
        return max(32, ((value / 8).roundToInt() * 8))
    }

    private fun requiredKbps(dim: Dimensions, fps: Float, codec: VideoCodec): Double =
        dim.width.toDouble() * dim.height * fps * codec.bpp / 1000.0

    fun capFps(fps: Float): Float {
        if (!fps.isFinite() || fps <= 0.5f) return 30f
        return min(60f, fps)
    }

    /**
     * @param targetBytes exact size the user asked for
     * @param maxShortSide resolution ceiling (free plan), null for unlimited
     */
    fun plan(
        info: VideoInfo,
        targetBytes: Long,
        codec: VideoCodec,
        allowDownscale: Boolean = true,
        maxShortSide: Int? = null,
        includeAudio: Boolean = true,
    ): EncodePlan {
        require(info.durationMs > 0) { "Could not read the duration of this file." }
        require(targetBytes > 0) { "A target size is required." }

        val warnings = mutableListOf<PlanWarning>()
        val sourceFps = capFps(info.fps)
        val durationSeconds = info.durationSeconds

        if (info.audioTracks > 1) warnings += PlanWarning.AUDIO_TRACKS
        if (info.subtitleTracks > 0) warnings += PlanWarning.SUBTITLES_DROPPED
        if (info.audioChannels > 2) warnings += PlanWarning.AUDIO_DOWNMIX
        if (info.fps > 60.001f) warnings += PlanWarning.FPS_CAPPED
        if (info.hdr.isHdr) warnings += PlanWarning.HDR_TONEMAP
        if (!includeAudio && info.hasAudio) warnings += PlanWarning.AUDIO_DROPPED
        if (targetBytes >= info.sizeBytes) warnings += PlanWarning.NOTHING_TO_GAIN
        if (maxShortSide != null && min(info.width, info.height) > maxShortSide) {
            warnings += PlanWarning.RESOLUTION_CAP
        }

        val totalKbps = (targetBytes.toDouble() * 8.0 / durationSeconds / 1000.0) * 0.97
        val audioBitrate = if (includeAudio) audioKbps(info, totalKbps) else 0
        val videoKbps = max(40, (totalKbps - audioBitrate).roundToInt())
        val audioChannels = if (info.audioChannels == 1) 1 else 2

        val sizes = candidates(info, maxShortSide)
        val original = sizes.first()
        var chosen = sizes.last()
        for (size in sizes) {
            if (requiredKbps(size, sourceFps, codec) <= videoKbps) {
                chosen = size
                break
            }
        }

        val requiredAtChosen = requiredKbps(chosen, sourceFps, codec)
        if (chosen == original && requiredAtChosen > videoKbps) {
            warnings += PlanWarning.AGGRESSIVE
        }
        if (!allowDownscale) {
            if (chosen != original) chosen = original
            warnings += PlanWarning.NO_DOWNSCALE
        }
        if (videoKbps < 120) warnings += PlanWarning.LOW_BITRATE

        // Tight targets: fewer frames per second at the same bitrate keeps far
        // more detail than a starved frame at the source rate.
        var outFps = sourceFps
        if (requiredAtChosen > videoKbps * 0.92 && outFps > 24f) {
            var candidateFps = outFps
            while (candidateFps > 24f && requiredKbps(chosen, candidateFps, codec) > videoKbps * 0.92) {
                candidateFps = max(24f, candidateFps / 2f)
            }
            if (candidateFps < outFps - 0.01f) {
                outFps = candidateFps
                warnings += PlanWarning.FPS_REDUCED
            }
        }
        outFps = max(1f, outFps.roundToInt().toFloat())

        val minimumBytes = ((videoKbps + audioBitrate).toDouble() * 1000 * durationSeconds / 8).toLong()
        val infeasible = minimumBytes > targetBytes
        if (infeasible) warnings += PlanWarning.INFEASIBLE

        val estimated = ((videoKbps + audioBitrate).toDouble() * 1000 * durationSeconds / 8).toLong()

        return EncodePlan(
            codec = codec,
            width = chosen.width,
            height = chosen.height,
            fps = outFps,
            videoKbps = videoKbps,
            audioKbps = audioBitrate,
            audioChannels = audioChannels,
            targetBytes = targetBytes,
            estimatedBytes = estimated,
            sourceWidth = info.width,
            sourceHeight = info.height,
            sourceFps = sourceFps,
            downscaled = chosen != original,
            fpsReduced = outFps < sourceFps - 0.01f,
            toneMap = info.hdr.isHdr,
            infeasible = infeasible,
            minimumBytes = minimumBytes,
            warnings = warnings.distinct(),
        )
    }

    /**
     * After a real encode, if the file still overshoots, scale the video
     * bitrate by how far off we are. Mirrors `tighterPlan` in the reference.
     * Returns null when another pass cannot help.
     */
    fun tighten(plan: EncodePlan, actualBytes: Long, targetBytes: Long): Int? {
        if (actualBytes <= targetBytes) return null
        if (plan.videoKbps <= 40) return null
        val wanted = (plan.videoKbps * (targetBytes * 0.98 / actualBytes)).toInt()
        val next = max(40, min(plan.videoKbps - 1, wanted))
        return if (next >= plan.videoKbps) null else next
    }
}
