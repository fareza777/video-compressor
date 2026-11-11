package com.f7developer.videofit.core.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.util.Log
import com.f7developer.videofit.core.model.EncodePlan
import com.f7developer.videofit.core.model.HdrTransfer
import com.f7developer.videofit.core.probe.VideoProbe
import com.f7developer.videofit.core.probe.optInt
import com.f7developer.videofit.core.probe.optLong
import java.nio.ByteBuffer

/**
 * Video track: MediaExtractor -> hardware decoder -> GL (scale/rotate/tone map)
 * -> hardware encoder -> MediaMuxer.
 */
class VideoTranscoder(
    private val context: Context,
    private val source: Uri,
    private val plan: EncodePlan,
    private val hdr: HdrTransfer,
    private val rotationDegrees: Int,
    private val durationUs: Long,
    private val muxer: MuxerGate,
    private val onProgress: (Long) -> Unit,
    private val isCancelled: () -> Boolean,
) {

    private val renderer = GlVideoRenderer()

    fun run() {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var encoderSurface: android.view.Surface? = null
        try {
            VideoProbe.openExtractor(extractor, source, context)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            } ?: throw IllegalStateException("No video track to encode.")
            extractor.selectTrack(trackIndex)
            val sourceFormat = extractor.getTrackFormat(trackIndex)
            val sourceWidth = sourceFormat.optInt(MediaFormat.KEY_WIDTH).let {
                if (it > 0) it else plan.sourceWidth
            }
            val sourceHeight = sourceFormat.optInt(MediaFormat.KEY_HEIGHT).let {
                if (it > 0) it else plan.sourceHeight
            }

            val height = even(plan.height)
            val width = even(plan.width)

            encoder = createEncoder(width, height)
            encoderSurface = encoder.createInputSurface()
            encoder.start()

            renderer.init(
                encoderInputSurface = encoderSurface,
                outputWidth = width,
                outputHeight = height,
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
                rotation = rotationDegrees,
                hdr = hdr,
            )

            val decoderMime = sourceFormat.getString(MediaFormat.KEY_MIME) ?: "video/avc"
            decoder = MediaCodec.createDecoderByType(decoderMime)
            // Copy the extractor's track format: it carries csd-0/csd-1
            // (SPS/PPS). A hand-built format configures happily but then never
            // decodes a frame on some codec stacks, because the codec-specific
            // data is missing.
            val decoderFormat = MediaFormat(sourceFormat)
            // Android 14+ can ask the decoder itself for a tone-mapped SDR frame,
            // which is much better than approximating PQ/HLG in a shader.
            if (hdr.isHdr && Build.VERSION.SDK_INT >= 34) {
                runCatching {
                    decoderFormat.setInteger(
                        MediaFormat.KEY_COLOR_TRANSFER_REQUEST,
                        MediaFormat.COLOR_TRANSFER_SDR_VIDEO,
                    )
                }
            }
            decoder.configure(decoderFormat, renderer.decoderSurface, null, 0)
            decoder.start()

            encodeLoop(extractor, decoder, encoder)
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { encoder?.stop() }
            runCatching { encoder?.release() }
            runCatching { encoderSurface?.release() }
            renderer.release()
            runCatching { extractor.release() }
        }
    }

    private fun even(value: Int): Int = (value / 2) * 2

    private fun createEncoder(width: Int, height: Int): MediaCodec {
        val mime = plan.codec.mimeType
        val candidates = CodecSelector.videoEncoderChoices(mime, width, height, plan.fps)
        var lastError: Throwable? = null
        for (choice in candidates) {
            var codec: MediaCodec? = null
            try {
                codec = MediaCodec.createByCodecName(choice.name)
                codec.configure(
                    buildFormat(mime, width, height, choice.profile, choice.level),
                    null,
                    null,
                    MediaCodec.CONFIGURE_FLAG_ENCODE,
                )
                Log.d(TAG, "encoder ${choice.name} profile=${choice.profile} hw=${choice.hardware}")
                return codec
            } catch (t: Throwable) {
                lastError = t
                runCatching { codec?.release() }
            }
        }
        // Last resort: let the platform pick.
        val codec = MediaCodec.createEncoderByType(mime)
        codec.configure(
            buildFormat(mime, width, height, null, null),
            null,
            null,
            MediaCodec.CONFIGURE_FLAG_ENCODE,
        )
        if (lastError != null) Log.w(TAG, "fell back to default encoder", lastError)
        return codec
    }

    private fun buildFormat(
        mime: String,
        width: Int,
        height: Int,
        profile: Int?,
        level: Int?,
    ): MediaFormat = MediaFormat.createVideoFormat(mime, width, height).apply {
        setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        setInteger(MediaFormat.KEY_BIT_RATE, plan.videoKbps * 1000)
        setInteger(MediaFormat.KEY_FRAME_RATE, plan.fps.toInt().coerceAtLeast(1))
        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setInteger(
                MediaFormat.KEY_BITRATE_MODE,
                MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR,
            )
        }
        if (mime == MediaFormat.MIMETYPE_VIDEO_AVC) {
            // Keep VBR peaks close to the target so a hard cap is not overshot.
            setInteger("max-bitrate", (plan.videoKbps * 1.35).toInt() * 1000)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT709)
            setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
            setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED)
        }
        if (profile != null) setInteger(MediaFormat.KEY_PROFILE, profile)
        if (level != null) setInteger(MediaFormat.KEY_LEVEL, level)
    }

    private fun encodeLoop(extractor: MediaExtractor, decoder: MediaCodec, encoder: MediaCodec) {
        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        var eosSignaled = false
        var lastEmittedUs = -1L
        var lastActivityAt = System.currentTimeMillis()
        val frameIntervalUs = (1_000_000.0 / plan.fps).toLong().coerceAtLeast(1L)
        var videoTrack = -1

        while (!outputDone) {
            if (isCancelled()) throw InterruptedException("Cancelled")
            if (System.currentTimeMillis() - lastActivityAt > STALL_TIMEOUT_MS) {
                throw IllegalStateException(
                    "The decoder stopped producing frames (codec stall).",
                )
            }

            if (!inputDone) {
                val inIndex = decoder.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    val buffer = decoder.getInputBuffer(inIndex)
                    val sampleSize = if (buffer != null) extractor.readSampleData(buffer, 0) else -1
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        decoder.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val outIndex = decoder.dequeueOutputBuffer(bufferInfo, 10_000)
            if (outIndex >= 0) {
                lastActivityAt = System.currentTimeMillis()
                val isFrame = bufferInfo.size != 0
                if (isFrame) {
                    val ptsUs = bufferInfo.presentationTimeUs
                    decoder.releaseOutputBuffer(outIndex, true)
                    if (renderer.awaitFrame(4_000)) {
                        // Frame-rate reduction drops frames instead of blurring them.
                        if (lastEmittedUs < 0 || ptsUs - lastEmittedUs >= frameIntervalUs - 500) {
                            lastEmittedUs = ptsUs
                            renderer.drawFrame(ptsUs * 1000)
                        }
                    }
                    onProgress(ptsUs)
                } else {
                    decoder.releaseOutputBuffer(outIndex, false)
                }

                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    if (!eosSignaled) {
                        encoder.signalEndOfInputStream()
                        eosSignaled = true
                    }
                    drainEncoder(encoder, bufferInfo, true) { track -> videoTrack = track }
                    outputDone = true
                    break
                }
            }

            drainEncoder(encoder, bufferInfo, false) { track -> videoTrack = track }
            if (isCancelled()) throw InterruptedException("Cancelled")
        }

        if (!outputDone) {
            encoder.signalEndOfInputStream()
            drainEncoder(encoder, bufferInfo, true) { track -> videoTrack = track }
        }
        Log.d(TAG, "video finished, track=$videoTrack")
        if (videoTrack < 0) throw IllegalStateException("The encoder produced no video frames.")
    }

    private inline fun drainEncoder(
        encoder: MediaCodec,
        bufferInfo: MediaCodec.BufferInfo,
        endOfStream: Boolean,
        onTrack: (Int) -> Unit,
    ) {
        var track = -1
        val deadline = System.currentTimeMillis() + if (endOfStream) EOS_TIMEOUT_MS else 0
        while (true) {
            if (isCancelled() && !endOfStream) return
            val index = encoder.dequeueOutputBuffer(
                bufferInfo,
                if (endOfStream) 10_000 else 0,
            )
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) return
                    // Some encoders never emit end-of-stream if no frame ever
                    // reached them; do not spin forever waiting for it.
                    if (System.currentTimeMillis() > deadline) {
                        Log.w(TAG, "no end-of-stream from encoder; finishing")
                        if (track >= 0) onTrack(track)
                        return
                    }
                }
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    track = muxer.addVideoTrack(encoder.outputFormat)
                    onTrack(track)
                }
                index >= 0 -> {
                    val buffer = encoder.getOutputBuffer(index)
                    if (buffer != null) {
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            bufferInfo.size = 0
                        }
                        if (bufferInfo.size > 0) {
                            if (track < 0) {
                                track = muxer.addVideoTrack(encoder.outputFormat)
                                onTrack(track)
                            }
                            muxer.write(track, buffer, bufferInfo)
                        }
                    }
                    val isEos = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    encoder.releaseOutputBuffer(index, false)
                    if (isEos) return
                }
            }
        }
    }

    private fun ByteBuffer.remainingSafe(): Int = remaining()

    companion object {
        private const val TAG = "VideoTranscoder"

        /** No decoder output for this long means the codec stack has stalled. */
        private const val STALL_TIMEOUT_MS = 60_000L

        /** Give the encoder this long to flush end-of-stream before moving on. */
        private const val EOS_TIMEOUT_MS = 20_000L
    }
}
