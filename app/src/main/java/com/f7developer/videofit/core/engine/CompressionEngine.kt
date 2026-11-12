package com.f7developer.videofit.core.engine

import android.content.Context
import android.net.Uri
import android.os.PowerManager
import android.os.Process
import android.util.Log
import com.f7developer.videofit.core.media.AudioTranscoder
import com.f7developer.videofit.core.media.MuxerGate
import com.f7developer.videofit.core.media.OutputStore
import com.f7developer.videofit.core.media.VideoTranscoder
import com.f7developer.videofit.core.model.EncodePlan
import com.f7developer.videofit.core.model.VideoCodec
import com.f7developer.videofit.core.model.VideoInfo
import com.f7developer.videofit.core.plan.CompressionPlanner
import com.f7developer.videofit.core.probe.VideoProbe
import com.f7developer.videofit.core.quality.QualityAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

/** One file to compress, with the options chosen in the UI. */
data class CompressionJob(
    val uri: Uri,
    val displayName: String,
    val info: VideoInfo,
    val targetBytes: Long,
    val codec: VideoCodec,
    val allowDownscale: Boolean = true,
    val maxShortSide: Int? = null,
    val measureQuality: Boolean = true,
)

data class CompressionResult(
    val displayName: String,
    val outputFile: File,
    val outputBytes: Long,
    val sourceBytes: Long,
    val sourceUriString: String,
    val elapsedMs: Long,
    val attempts: Int,
    val plan: EncodePlan,
    val ssim: Double?,
    val verdict: QualityAnalyzer.Verdict,
    val savedToGallery: Boolean,
    val durationMs: Long = 0L,
    /** True when the source already fit the target and was copied untouched. */
    val skipped: Boolean = false,
) {
    val savedPercent: Int
        get() = if (sourceBytes <= 0) 0
        else (((sourceBytes - outputBytes).toDouble() / sourceBytes) * 100).roundToInt().coerceIn(0, 100)
    val hitTarget: Boolean get() = outputBytes <= plan.targetBytes * 1.02
}

class CompressionException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Orchestrates probe -> plan -> encode -> verify, including the reference
 * implementation's "tighten and retry" step when an encode overshoots.
 */
class CompressionEngine(private val context: Context) {

    private val probe = VideoProbe(context)
    private val quality = QualityAnalyzer(context)

    suspend fun probeVideo(uri: Uri, fallbackName: String?): VideoInfo = withContext(Dispatchers.IO) {
        probe.probe(uri, fallbackName)
    }

    fun planFor(
        info: VideoInfo,
        targetBytes: Long,
        codec: VideoCodec,
        allowDownscale: Boolean,
        maxShortSide: Int?,
    ): EncodePlan {
        // Some devices have no H.265 encoder at all; fall back instead of failing
        // at configure time.
        val usableCodec = if (codec == VideoCodec.HEVC && !hasEncoder("video/hevc")) {
            VideoCodec.H264
        } else {
            codec
        }
        val planned = CompressionPlanner.plan(
            info = info,
            targetBytes = targetBytes,
            codec = usableCodec,
            allowDownscale = allowDownscale,
            maxShortSide = maxShortSide,
        )
        return if (usableCodec != codec) {
            planned.copy(warnings = planned.warnings + com.f7developer.videofit.core.model.PlanWarning.HEVC_UNSUPPORTED)
        } else {
            planned
        }
    }

    private fun hasEncoder(mime: String): Boolean = runCatching {
        android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
            info.isEncoder && info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
        }
    }.getOrDefault(true)

    suspend fun compress(
        job: CompressionJob,
        onProgress: (Float) -> Unit,
        isCancelled: () -> Boolean,
    ): CompressionResult = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val info = job.info
        val plan = planFor(info, job.targetBytes, job.codec, job.allowDownscale, job.maxShortSide)

        // Already small enough: re-encoding would only add size and lose detail.
        if (info.sizeBytes in 1 until job.targetBytes) {
            val output = OutputStore.uniqueOutputFile(context, job.displayName, info.sizeBytes)
            copySource(job.uri, output)
            onProgress(1f)
            return@withContext CompressionResult(
                displayName = job.displayName,
                outputFile = output,
                outputBytes = output.length(),
                sourceBytes = info.sizeBytes,
                sourceUriString = job.uri.toString(),
                elapsedMs = System.currentTimeMillis() - started,
                attempts = 0,
                plan = plan,
                ssim = 1.0,
                verdict = QualityAnalyzer.Verdict.INDISTINGUISHABLE,
                savedToGallery = false,
                durationMs = info.durationMs,
                skipped = true,
            )
        }

        var resizedPlan = plan
        var attempt = 0
        var resultFile: File? = null
        var lastBytes = 0L

        while (true) {
            attempt++
            val temp = File(
                OutputStore.tempDirectory(context),
                "vf_${System.currentTimeMillis()}_$attempt.mp4",
            )
            lastBytes = encodeOnce(job, resizedPlan, temp, onProgress, isCancelled)
            resultFile?.delete()
            resultFile = temp
            if (lastBytes <= resizedPlan.targetBytes * 1.005 || attempt >= 3) break
            val tightened = CompressionPlanner.tighten(resizedPlan, lastBytes, resizedPlan.targetBytes)
            if (tightened == null) break
            Log.d(TAG, "overshoot ${lastBytes}B, retrying at $tightened kbps")
            resizedPlan = resizedPlan.copy(videoKbps = tightened)
        }

        val temp = resultFile ?: throw CompressionException("The encoder produced no output.")
        val finalFile = OutputStore.uniqueOutputFile(context, job.displayName, job.targetBytes)
        if (!temp.renameTo(finalFile)) {
            temp.copyTo(finalFile, overwrite = true)
            temp.delete()
        }

        val qualitySample = if (job.measureQuality) {
            quality.measure(job.uri, Uri.fromFile(finalFile), info.durationMs)
        } else {
            QualityAnalyzer.Sample(null, null, 0)
        }

        CompressionResult(
            displayName = job.displayName,
            outputFile = finalFile,
            outputBytes = finalFile.length().takeIf { it > 0 } ?: lastBytes,
            sourceBytes = info.sizeBytes,
            sourceUriString = job.uri.toString(),
            elapsedMs = System.currentTimeMillis() - started,
            attempts = attempt,
            plan = resizedPlan,
            ssim = qualitySample.ssim,
            verdict = qualitySample.verdict,
            savedToGallery = false,
            durationMs = info.durationMs,
        )
    }

    private fun encodeOnce(
        job: CompressionJob,
        plan: EncodePlan,
        temp: File,
        onProgress: (Float) -> Unit,
        isCancelled: () -> Boolean,
    ): Long {
        val durationUs = (job.info.durationMs * 1000).coerceAtLeast(1)
        val expectAudio = job.info.hasAudio && plan.audioKbps > 0
        val muxer = MuxerGate(temp.absolutePath, expectAudio)
        val failure = AtomicReference<Throwable?>(null)
        val progressLock = Any()
        var videoProgress = 0f
        var audioProgress = 0f

        fun emit() {
            val combined = (videoProgress * 0.92f + audioProgress * 0.08f).coerceIn(0f, 1f)
            onProgress(combined)
        }

        val videoThread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY)
            try {
                VideoTranscoder(
                    context = context,
                    source = job.uri,
                    plan = plan,
                    hdr = job.info.hdr,
                    rotationDegrees = job.info.rotationDegrees,
                    durationUs = durationUs,
                    muxer = muxer,
                    onProgress = { us ->
                        synchronized(progressLock) {
                            videoProgress = (us.toFloat() / durationUs).coerceIn(0f, 1f)
                            emit()
                        }
                    },
                    isCancelled = isCancelled,
                ).run()
            } catch (t: Throwable) {
                failure.compareAndSet(null, t)
            }
        }, "vf-video")

        val audioThread = if (expectAudio) {
            Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
                try {
                    AudioTranscoder(
                        context = context,
                        source = job.uri,
                        bitrateKbps = plan.audioKbps,
                        channels = plan.audioChannels,
                        durationUs = durationUs,
                        muxer = muxer,
                        onProgress = { us ->
                            synchronized(progressLock) {
                                audioProgress = (us.toFloat() / durationUs).coerceIn(0f, 1f)
                            }
                        },
                        isCancelled = isCancelled,
                    ).run()
                } catch (t: Throwable) {
                    failure.compareAndSet(null, t)
                }
            }, "vf-audio")
        } else null

        try {
            videoThread.start()
            audioThread?.start()
            videoThread.join()
            audioThread?.join()
            if (expectAudio && !muxer.hasAudioTrack()) {
                // The audio pass gave up; let the muxer open with video only so
                // the user still gets a playable file.
                muxer.audioUnavailable()
            }
        } catch (t: InterruptedException) {
            Thread.currentThread().interrupt()
            throw CompressionException("Cancelled", t)
        } finally {
            runCatching { muxer.finish() }
        }

        val error = failure.get()
        if (isCancelled()) {
            temp.delete()
            throw CompressionException("Cancelled")
        }
        if (error != null) {
            temp.delete()
            throw CompressionException(friendlyMessage(error), error)
        }
        if (!temp.exists() || temp.length() < 1024) {
            throw CompressionException("The encoder produced an empty file.")
        }
        onProgress(1f)
        return temp.length()
    }

    private fun copySource(uri: Uri, destination: File) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw CompressionException("Could not read the selected file.")
        input.use { source ->
            destination.outputStream().use { out -> source.copyTo(out, 256 * 1024) }
        }
    }

    private fun friendlyMessage(error: Throwable): String {
        val raw = error.message ?: error.javaClass.simpleName
        return when {
            raw.contains("CodecException", true) || raw.contains("configure", true) ->
                "This phone's encoder could not handle the selected settings. Try H.264 or another video."
            raw.contains("memory", true) -> "Not enough memory to encode this video."
            else -> "Compression failed: $raw"
        }
    }

    fun thermalStatus(): Int {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return 0
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return 0
        return power.currentThermalStatus
    }

    companion object {
        private const val TAG = "CompressionEngine"
    }
}
