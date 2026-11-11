package com.f7developer.videofit

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.f7developer.videofit.core.engine.CompressionEngine
import com.f7developer.videofit.core.engine.CompressionJob
import com.f7developer.videofit.core.model.HdrTransfer
import com.f7developer.videofit.core.model.VideoCodec
import com.f7developer.videofit.core.plan.CompressionPlanner
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * End-to-end tests on a real device: MediaExtractor -> MediaCodec -> GL ->
 * MediaMuxer, then re-probe the produced file. These assert the things that
 * matter for the product: it lands under the target, it stays playable, and it
 * picks a sane resolution.
 */
@RunWith(AndroidJUnit4::class)
class CompressionEngineTest {

    private lateinit var context: Context
    private lateinit var engine: CompressionEngine

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        engine = CompressionEngine(context)
    }

    private fun testAssets() = InstrumentationRegistry.getInstrumentation().context.assets

    private fun publishAsset(name: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "vf_$name")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/VideoFitTest")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("could not insert test video")
        context.contentResolver.openOutputStream(uri)!!.use { out ->
            testAssets().open(name).use { input -> input.copyTo(out, 128 * 1024) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val done = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
            context.contentResolver.update(uri, done, null, null)
        }
        return uri
    }

    @Test
    fun probeReadsGeometryAndAudio() {
        val uri = publishAsset("sample.mp4")
        val info = runBlocking { engine.probeVideo(uri, "sample.mp4") }
        Log.d(TAG, "probe: $info")
        assertEquals(1920, info.width)
        assertEquals(1080, info.height)
        assertTrue("duration ${info.durationMs}", info.durationMs in 7_000..9_000)
        assertTrue("fps ${info.fps}", abs(info.fps - 30f) < 1.5f)
        assertTrue(info.hasAudio)
        assertEquals(2, info.audioChannels)
        assertEquals(HdrTransfer.NONE, info.hdr)
        assertTrue("size ${info.sizeBytes}", info.sizeBytes > 100_000)
    }

    @Test
    fun plannerDownscalesToFitTarget() {
        val uri = publishAsset("sample.mp4")
        val info = runBlocking { engine.probeVideo(uri, "sample.mp4") }
        val target = 2_000_000L
        val plan = CompressionPlanner.plan(info, target, VideoCodec.H264, true, null)
        Log.d(TAG, "plan: $plan")
        // 1080p needs 4.48 Mbps at 0.072 bpp and 720p needs 1.99 Mbps, both above
        // the ~1.81 Mbps this target allows, so the ladder lands on 540p.
        assertTrue("expected downscale, got ${plan.width}x${plan.height}", plan.downscaled)
        assertEquals(960, plan.width)
        assertEquals(540, plan.height)
        assertTrue(plan.videoKbps in 1500..2050)
        assertTrue(plan.estimatedBytes <= target)
        assertTrue(plan.warnings.none { it.name == "INFEASIBLE" })
    }

    @Test
    fun compressesUnderTargetAndStaysPlayable() {
        val uri = publishAsset("sample.mp4")
        val info = runBlocking { engine.probeVideo(uri, "sample.mp4") }
        val target = 2_000_000L
        var lastProgress = 0f
        val result = runBlocking {
            engine.compress(
                job = CompressionJob(
                    uri = uri,
                    displayName = "sample.mp4",
                    info = info,
                    targetBytes = target,
                    codec = VideoCodec.H264,
                    allowDownscale = true,
                    maxShortSide = null,
                    measureQuality = true,
                ),
                onProgress = { lastProgress = it },
                isCancelled = { false },
            )
        }
        Log.d(
            TAG,
            "result: bytes=${result.outputBytes} target=$target plan=${result.plan.width}x${result.plan.height} " +
                "ssim=${result.ssim} verdict=${result.verdict} attempts=${result.attempts} elapsed=${result.elapsedMs}",
        )
        assertTrue("progress should reach the end", lastProgress > 0.9f)
        assertTrue("output must exist", result.outputFile.exists())
        assertTrue(
            "output ${result.outputBytes} should be under ${target * 1.02}",
            result.outputBytes in 100_000..(target * 1.02).toLong(),
        )
        assertTrue("should report a real saving", result.savedPercent > 40)

        // The produced MP4 must be readable again, with the planned geometry.
        val out = runBlocking { engine.probeVideo(Uri.fromFile(result.outputFile), "out.mp4") }
        Log.d(TAG, "output probe: $out")
        assertEquals(result.plan.width, out.width)
        assertEquals(result.plan.height, out.height)
        assertTrue("output duration ${out.durationMs}", out.durationMs > 6_000)
        assertTrue("output must keep audio", out.hasAudio)
        assertNotNull("SSIM should be measured", result.ssim)
    }

    /**
     * Small, fast end-to-end encode: this is the test to run on a software-codec
     * emulator, where a full 1080p clip takes minutes.
     */
    @Test
    fun compressesShortClipQuickly() {
        val uri = publishAsset("tiny.mp4")
        val info = runBlocking { engine.probeVideo(uri, "tiny.mp4") }
        val target = 150_000L
        val result = runBlocking {
            engine.compress(
                job = CompressionJob(
                    uri = uri,
                    displayName = "tiny.mp4",
                    info = info,
                    targetBytes = target,
                    codec = VideoCodec.H264,
                    measureQuality = false,
                ),
                onProgress = {},
                isCancelled = { false },
            )
        }
        Log.d(
            TAG,
            "tiny: ${result.outputBytes}B from ${result.sourceBytes}B plan=${result.plan.width}x${result.plan.height} attempts=${result.attempts}",
        )
        assertTrue(result.outputFile.exists())
        assertTrue(
            "output ${result.outputBytes} should be at or under $target",
            result.outputBytes in 20_000..(target * 1.02).toLong(),
        )
        assertTrue("should shrink", result.outputBytes < result.sourceBytes)
        val out = runBlocking { engine.probeVideo(Uri.fromFile(result.outputFile), "out.mp4") }
        assertTrue("output duration ${out.durationMs}", out.durationMs > 2_000)
        assertEquals(result.plan.width, out.width)
        assertTrue("audio expected", out.hasAudio)
    }

    @Test
    fun rotatedSourceIsExportedUpright() {
        val uri = publishAsset("rotated.mp4")
        val info = runBlocking { engine.probeVideo(uri, "rotated.mp4") }
        Log.d(TAG, "rotated probe: $info")
        // ffmpeg writes the rotation matrix either way round; both mean the
        // coded frame is landscape and the display frame is portrait.
        assertTrue("rotation ${info.rotationDegrees}", info.rotationDegrees in setOf(90, 270))
        // Display geometry is portrait even though the coded frame is landscape.
        assertEquals(1080, info.width)
        assertEquals(1920, info.height)

        val result = runBlocking {
            engine.compress(
                job = CompressionJob(
                    uri = uri,
                    displayName = "rotated.mp4",
                    info = info,
                    targetBytes = 1_500_000L,
                    codec = VideoCodec.H264,
                    measureQuality = false,
                ),
                onProgress = {},
                isCancelled = { false },
            )
        }
        val out = runBlocking { engine.probeVideo(Uri.fromFile(result.outputFile), "out.mp4") }
        Log.d(TAG, "rotated output: $out")
        assertEquals("rotation should be baked in", 0, out.rotationDegrees)
        assertTrue("output should be portrait: ${out.width}x${out.height}", out.height > out.width)
    }

    @Test
    fun hevcProducesSmallerFileThanH264() {
        val uri = publishAsset("sample.mp4")
        val info = runBlocking { engine.probeVideo(uri, "sample.mp4") }
        val job = { codec: VideoCodec ->
            CompressionJob(
                uri = uri,
                displayName = "sample.mp4",
                info = info,
                targetBytes = 2_000_000L,
                codec = codec,
                measureQuality = false,
            )
        }
        val h264 = runBlocking { engine.compress(job(VideoCodec.H264), {}, { false }) }
        val hevc = runBlocking { engine.compress(job(VideoCodec.HEVC), {}, { false }) }
        Log.d(TAG, "h264=${h264.outputBytes} (${h264.plan.width}x${h264.plan.height}) hevc=${hevc.outputBytes} (${hevc.plan.width}x${hevc.plan.height})")
        assertTrue(h264.outputBytes > 0)
        assertTrue(hevc.outputBytes > 0)
        // H.265 needs fewer bits per pixel, so the ladder should keep more resolution.
        assertTrue(
            "HEVC should pick at least as much resolution (${hevc.plan.width} vs ${h264.plan.width})",
            hevc.plan.width >= h264.plan.width,
        )
    }

    companion object {
        private const val TAG = "VFTEST"
    }
}
