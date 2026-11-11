package com.f7developer.videofit.core.media

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import java.nio.ByteBuffer

/**
 * One MediaMuxer shared by the video and audio threads.
 *
 * MediaMuxer needs every track added before start() and is not thread safe, so
 * this gate serialises track registration and sample writes. Samples that
 * arrive before the second track is known are buffered (that window is a few
 * hundred milliseconds) and flushed on start.
 */
class MuxerGate(outputPath: String, private var expectAudio: Boolean) {

    private val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

    private data class Pending(val track: Int, val data: ByteArray, val info: MediaCodec.BufferInfo)

    private val lock = Object()
    private val pending = ArrayList<Pending>()
    private var pendingBytes = 0L
    private var videoTrack = -1
    private var audioTrack = -1
    private var started = false
    private var finished = false
    private var bytesWritten = 0L

    fun addVideoTrack(format: MediaFormat): Int = synchronized(lock) {
        if (videoTrack < 0) videoTrack = muxer.addTrack(format)
        maybeStart()
        videoTrack
    }

    fun addAudioTrack(format: MediaFormat): Int = synchronized(lock) {
        if (audioTrack < 0) audioTrack = muxer.addTrack(format)
        maybeStart()
        audioTrack
    }

    fun hasAudioTrack(): Boolean = synchronized(lock) { audioTrack >= 0 }

    /**
     * Called when the audio thread finished without registering a track (an
     * undecodable source, for example). Without this the muxer would wait for a
     * track that is never coming and the video thread would stall.
     */
    fun audioUnavailable() {
        synchronized(lock) {
            if (started || audioTrack >= 0) return
            expectAudio = false
            maybeStart()
        }
    }

    private fun maybeStart() {
        if (started || videoTrack < 0) return
        if (expectAudio && audioTrack < 0) return
        muxer.setOrientationHint(0)
        muxer.start()
        started = true
        flushPending()
    }

    private fun flushPending() {
        for (p in pending) {
            writeInternal(p.track, ByteBuffer.wrap(p.data), p.info)
        }
        pendingBytes = 0
        pending.clear()
    }

    private fun writeInternal(track: Int, buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        if (info.size <= 0) return
        buffer.position(info.offset)
        buffer.limit(info.offset + info.size)
        muxer.writeSampleData(track, buffer, info)
        bytesWritten += info.size
    }

    fun write(track: Int, buffer: ByteBuffer, info: MediaCodec.BufferInfo) = synchronized(lock) {
        if (finished || track < 0) return
        if (!started) {
            val copy = ByteArray(info.size)
            buffer.position(info.offset)
            buffer.limit(info.offset + info.size)
            buffer.get(copy)
            val copyInfo = MediaCodec.BufferInfo().apply {
                set(0, copy.size, info.presentationTimeUs, info.flags)
            }
            pending.add(Pending(track, copy, copyInfo))
            pendingBytes += copy.size
            if (pendingBytes > MAX_PENDING_BYTES) {
                throw IllegalStateException("Muxer start stalled; too much buffered output.")
            }
            maybeStart()
            return
        }
        writeInternal(track, buffer, info)
    }

    fun bytesWritten(): Long = synchronized(lock) { bytesWritten }

    fun finish() {
        synchronized(lock) {
            if (finished) return
            finished = true
            pending.clear()
            pendingBytes = 0
            if (started) {
                runCatching { muxer.stop() }
            }
            runCatching { muxer.release() }
        }
    }

    fun cancel() {
        finish()
    }

    companion object {
        private const val MAX_PENDING_BYTES = 48L * 1024 * 1024
    }
}
