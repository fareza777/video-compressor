package com.f7developer.videofit

import com.f7developer.videofit.core.model.HdrTransfer
import com.f7developer.videofit.core.model.PlanWarning
import com.f7developer.videofit.core.model.VideoCodec
import com.f7developer.videofit.core.model.VideoInfo
import com.f7developer.videofit.core.plan.CompressionPlanner
import com.f7developer.videofit.core.util.Format
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mirrors the reference planner's behaviour on the numbers its README quotes. */
class CompressionPlannerTest {

    private fun video(
        width: Int = 1920,
        height: Int = 1080,
        durationMs: Long = 8_000,
        fps: Float = 30f,
        bitrateKbps: Int = 4_000,
        sizeBytes: Long = 4_153_847,
        audio: Boolean = true,
        audioBitrateKbps: Int = 128,
        audioChannels: Int = 2,
        audioTracks: Int = 1,
        hdr: HdrTransfer = HdrTransfer.NONE,
        rotation: Int = 0,
    ) = VideoInfo(
        uriString = "content://test/$width",
        displayName = "sample.mp4",
        durationMs = durationMs,
        sizeBytes = sizeBytes,
        width = width,
        height = height,
        rotationDegrees = rotation,
        fps = fps,
        videoBitrateKbps = bitrateKbps,
        videoMime = "video/avc",
        videoCodecName = "avc",
        hasAudio = audio,
        audioChannels = audioChannels,
        audioSampleRate = 44_100,
        audioBitrateKbps = audioBitrateKbps,
        audioMime = if (audio) "audio/mp4a-latm" else null,
        audioTracks = audioTracks,
        subtitleTracks = 0,
        hdr = hdr,
        colorTransfer = null,
        tenBit = hdr.isHdr,
    )

    @Test
    fun `two megabytes of 1080p lands on 540p`() {
        val plan = CompressionPlanner.plan(video(), 2_000_000L, VideoCodec.H264)
        assertEquals(960, plan.width)
        assertEquals(540, plan.height)
        assertTrue(plan.downscaled)
        // 2 MB * 8 / 8 s * 0.97 = 1940 kbps total, minus 128 kbps audio.
        assertEquals(1812, plan.videoKbps)
        assertEquals(128, plan.audioKbps)
        assertTrue(plan.estimatedBytes <= 2_000_000L)
        assertFalse(plan.infeasible)
    }

    @Test
    fun `hevc keeps more resolution than h264 at the same target`() {
        val source = video()
        val h264 = CompressionPlanner.plan(source, 2_000_000L, VideoCodec.H264)
        val hevc = CompressionPlanner.plan(source, 2_000_000L, VideoCodec.HEVC)
        assertEquals(960, h264.width)
        assertEquals(1280, hevc.width)
        assertEquals(720, hevc.height)
    }

    @Test
    fun `audio gets at most 18 percent of the budget`() {
        val plan = CompressionPlanner.plan(video(audioBitrateKbps = 320), 1_000_000L, VideoCodec.H264)
        val total = plan.videoKbps + plan.audioKbps
        assertTrue("audio ${plan.audioKbps} of $total", plan.audioKbps <= total * 0.18 + 8)
    }

    @Test
    fun `frame rate is capped at 60`() {
        val plan = CompressionPlanner.plan(video(fps = 120f), 20_000_000L, VideoCodec.H264)
        assertEquals(60f, plan.fps)
        assertTrue(plan.warnings.contains(PlanWarning.FPS_CAPPED))
    }

    @Test
    fun `an impossible target is reported instead of silently missed`() {
        val long = video(durationMs = 3_600_000)
        val plan = CompressionPlanner.plan(long, 1_000_000L, VideoCodec.H264)
        assertTrue(plan.infeasible)
        assertTrue(plan.warnings.contains(PlanWarning.INFEASIBLE))
        assertTrue(plan.minimumBytes > 1_000_000L)
    }

    @Test
    fun `downscaling can be disabled`() {
        val plan = CompressionPlanner.plan(video(), 2_000_000L, VideoCodec.H264, allowDownscale = false)
        assertEquals(1920, plan.width)
        assertFalse(plan.downscaled)
        assertTrue(plan.warnings.contains(PlanWarning.NO_DOWNSCALE))
    }

    @Test
    fun `hdr sources request tone mapping`() {
        val plan = CompressionPlanner.plan(
            video(hdr = HdrTransfer.HLG, audio = false),
            5_000_000L,
            VideoCodec.H264,
        )
        assertTrue(plan.toneMap)
        assertTrue(plan.warnings.contains(PlanWarning.HDR_TONEMAP))
        assertEquals(0, plan.audioKbps)
    }

    @Test
    fun `portrait sources keep portrait rungs of the ladder`() {
        val plan = CompressionPlanner.plan(
            video(width = 1080, height = 1920, rotation = 0),
            2_000_000L,
            VideoCodec.H264,
        )
        assertTrue("expected portrait, got ${plan.width}x${plan.height}", plan.height > plan.width)
        assertEquals(540, plan.width)
        assertEquals(960, plan.height)
    }

    @Test
    fun `resolution cap keeps free tier at 1080p`() {
        val plan = CompressionPlanner.plan(
            video(width = 3840, height = 2160, bitrateKbps = 40_000, sizeBytes = 120_000_000),
            40_000_000L,
            VideoCodec.H264,
            maxShortSide = 1080,
        )
        assertEquals(1920, plan.width)
        assertEquals(1080, plan.height)
        assertTrue(plan.warnings.contains(PlanWarning.RESOLUTION_CAP))
    }

    @Test
    fun `tighten lowers bitrate only when the encode overshoots`() {
        val plan = CompressionPlanner.plan(video(), 2_000_000L, VideoCodec.H264)
        assertNull(CompressionPlanner.tighten(plan, 1_900_000L, 2_000_000L))
        val tightened = CompressionPlanner.tighten(plan, 2_400_000L, 2_000_000L)
        assertNotNull(tightened)
        assertTrue("tightened $tightened should be below ${plan.videoKbps}", tightened!! < plan.videoKbps)
        assertTrue(tightened >= 40)
    }

    @Test
    fun `a tight target that still fits at 240p keeps the frame rate`() {
        val plan = CompressionPlanner.plan(video(durationMs = 60_000), 3_000_000L, VideoCodec.H264)
        assertEquals(426, plan.width)
        assertEquals(240, plan.height)
        assertEquals(30f, plan.fps)
        assertFalse(plan.warnings.contains(PlanWarning.FPS_REDUCED))
    }

    @Test
    fun `very tight targets trade frame rate for detail`() {
        // 1 MB for 60 s leaves ~97 kbps of video, which not even 240p holds at
        // 30 fps, so the planner drops to 24 fps instead of starving the frames.
        val plan = CompressionPlanner.plan(video(durationMs = 60_000), 1_000_000L, VideoCodec.H264)
        assertEquals(24f, plan.fps)
        assertTrue(plan.warnings.contains(PlanWarning.FPS_REDUCED))
        assertTrue(plan.estimatedBytes <= 1_000_000L)
    }

    @Test
    fun `size parsing accepts the formats the UI allows`() {
        assertEquals(20_000_000L, Format.parseSizeToBytes("20"))
        assertEquals(20_000_000L, Format.parseSizeToBytes("20mb"))
        assertEquals(18_000_000L, Format.parseSizeToBytes("18 MB"))
        assertEquals(700_000L, Format.parseSizeToBytes("700k"))
        assertEquals(1_500_000_000L, Format.parseSizeToBytes("1.5gb"))
        assertNull(Format.parseSizeToBytes("abc"))
    }

    @Test
    fun `formatting matches the reference output`() {
        assertEquals("4.15 MB", Format.bytes(4_153_847))
        assertEquals("8s", Format.duration(8.0))
        assertEquals("1m 30s", Format.duration(90.0))
        assertEquals("1.81 Mbps", Format.bitrate(1812))
        assertEquals("96 kbps", Format.bitrate(96))
    }
}
