package com.f7developer.videofit.core.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import com.f7developer.videofit.core.probe.VideoProbe
import com.f7developer.videofit.core.probe.optInt
import java.nio.ByteBuffer

/**
 * Audio track: decode to PCM, re-encode to AAC at the bitrate the planner
 * allocated. Re-encoding (rather than copying) is what makes the total size
 * land on target; passthrough is the fallback for codecs the device cannot
 * decode.
 */
class AudioTranscoder(
    private val context: Context,
    private val source: Uri,
    private val bitrateKbps: Int,
    private val channels: Int,
    private val durationUs: Long,
    private val muxer: MuxerGate,
    private val onProgress: (Long) -> Unit,
    private val isCancelled: () -> Boolean,
) {

    fun run() {
        try {
            transcode()
        } catch (t: Throwable) {
            if (t is InterruptedException) throw t
            Log.w(TAG, "AAC transcode failed, falling back to stream copy", t)
            passthrough()
        }
    }

    private fun transcode() {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        try {
            VideoProbe.openExtractor(extractor, source, context)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return
            extractor.selectTrack(trackIndex)
            val sourceFormat = extractor.getTrackFormat(trackIndex)
            val mime = sourceFormat.getString(MediaFormat.KEY_MIME) ?: "audio/mp4a-latm"
            val sampleRate = sourceFormat.optInt(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(8_000)
            val sourceChannels = sourceFormat.optInt(MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1, 8)
            val targetChannels = minOf(channels.coerceIn(1, 2), sourceChannels)

            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(sourceFormat, null, null, 0)
            decoder.start()

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            val outFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                sampleRate,
                targetChannels,
            ).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrateKbps * 1000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 32 * 1024)
            }
            encoder.configure(outFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            val pcmQueue = ArrayDeque<ByteArray>()
            var pcmOffset = 0
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var encoderDone = false
            var audioTrack = -1
            var lastProgressUs = -1L

            while (!outputDone) {
                if (isCancelled()) throw InterruptedException("Cancelled")

                if (!inputDone) {
                    val index = decoder.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        val buffer = decoder.getInputBuffer(index)
                        val size = if (buffer != null) extractor.readSampleData(buffer, 0) else -1
                        if (size < 0) {
                            decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIndex = decoder.dequeueOutputBuffer(info, 5_000)
                if (outIndex >= 0) {
                    val buffer = decoder.getOutputBuffer(outIndex)
                    if (buffer != null && info.size > 0) {
                        val chunk = ByteArray(info.size)
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        buffer.get(chunk)
                        pcmQueue.add(chunk)
                    }
                    val wasEos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    decoder.releaseOutputBuffer(outIndex, false)
                    if (wasEos) encoderDone = false
                }

                // Feed the encoder what we have.
                while (pcmQueue.isNotEmpty()) {
                    val index = encoder.dequeueInputBuffer(0)
                    if (index < 0) break
                    val buffer = encoder.getInputBuffer(index) ?: break
                    val head = pcmQueue.first()
                    val available = head.size - pcmOffset
                    val capacity = buffer.capacity()
                    val toWrite = minOf(available, capacity)
                    buffer.clear()
                    buffer.put(head, pcmOffset, toWrite)
                    encoder.queueInputBuffer(index, 0, toWrite, 0, 0)
                    pcmOffset += toWrite
                    if (pcmOffset >= head.size) {
                        pcmQueue.removeFirst()
                        pcmOffset = 0
                    }
                }

                if (inputDone && pcmQueue.isEmpty() && !encoderDone) {
                    val index = encoder.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        encoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        encoderDone = true
                    }
                }

                // Drain the encoder.
                while (true) {
                    val index = encoder.dequeueOutputBuffer(info, 0)
                    if (index == MediaCodec.INFO_TRY_AGAIN_LATER) break
                    if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        if (audioTrack < 0) audioTrack = muxer.addAudioTrack(encoder.outputFormat)
                        continue
                    }
                    if (index < 0) continue
                    val buffer = encoder.getOutputBuffer(index)
                    if (buffer != null) {
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0) {
                            if (audioTrack < 0) audioTrack = muxer.addAudioTrack(encoder.outputFormat)
                            muxer.write(audioTrack, buffer, info)
                            if (info.presentationTimeUs > lastProgressUs) {
                                lastProgressUs = info.presentationTimeUs
                                onProgress(lastProgressUs)
                            }
                        }
                    }
                    val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    encoder.releaseOutputBuffer(index, false)
                    if (eos) {
                        outputDone = true
                        break
                    }
                }

                if (inputDone && encoderDone && pcmQueue.isEmpty() && !outputDone) {
                    // Encoder still flushes; loop until EOS is seen.
                }
            }
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { encoder?.stop() }
            runCatching { encoder?.release() }
            runCatching { extractor.release() }
        }
    }

    /** Copy the source audio track untouched, for sources we cannot decode. */
    private fun passthrough() {
        val extractor = MediaExtractor()
        var track = -1
        try {
            VideoProbe.openExtractor(extractor, source, context)
            val index = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return
            extractor.selectTrack(index)
            track = muxer.addAudioTrack(extractor.getTrackFormat(index))
            val info = MediaCodec.BufferInfo()
            val buffer = ByteBuffer.allocateDirect(256 * 1024)
            while (true) {
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                info.set(0, size, extractor.sampleTime, 0)
                muxer.write(track, buffer, info)
                extractor.advance()
            }
        } finally {
            runCatching { extractor.release() }
        }
    }

    companion object {
        private const val TAG = "AudioTranscoder"
    }
}
