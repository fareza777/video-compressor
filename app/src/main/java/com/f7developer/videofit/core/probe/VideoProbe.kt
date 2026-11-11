package com.f7developer.videofit.core.probe

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.f7developer.videofit.core.model.HdrTransfer
import com.f7developer.videofit.core.model.VideoInfo
import java.io.FileNotFoundException

/**
 * Reads everything the planner needs using only platform APIs:
 * MediaMetadataRetriever for display geometry, duration and rotation, and
 * MediaExtractor for codec, bitrate, frame rate and colour metadata.
 */
class VideoProbe(private val context: Context) {

    fun probe(uri: Uri, fallbackName: String? = null): VideoInfo {
        val (name, size) = queryNameAndSize(uri, fallbackName)

        val retriever = MediaMetadataRetriever()
        var durationMs = 0L
        var rotation = 0
        var retrieverWidth = 0
        var retrieverHeight = 0
        try {
            retriever.setDataSource(context, uri)
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            retrieverWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            retrieverHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
        } catch (t: Throwable) {
            throw IllegalArgumentException("This file could not be opened as a video.", t)
        } finally {
            runCatching { retriever.release() }
        }

        val extractor = MediaExtractor()
        var videoFormat: MediaFormat? = null
        var audioFormat: MediaFormat? = null
        var audioTracks = 0
        var subtitleTracks = 0
        try {
            openExtractor(extractor, uri, context)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                when {
                    mime.startsWith("video/") && videoFormat == null -> videoFormat = format
                    mime.startsWith("audio/") -> {
                        audioTracks++
                        if (audioFormat == null) audioFormat = format
                    }
                    mime.startsWith("text/") || mime.startsWith("application/") -> subtitleTracks++
                }
            }
        } catch (t: Throwable) {
            Log.w("VideoProbe", "extractor failed for $uri", t)
            throw IllegalArgumentException(
                "This file could not be read as a video (${t.javaClass.simpleName}).", t,
            )
        } finally {
            runCatching { extractor.release() }
        }

        val vf = videoFormat ?: throw IllegalArgumentException("No video stream found in this file.")

        if (durationMs <= 0) {
            durationMs = vf.optLong(MediaFormat.KEY_DURATION) / 1000
        }
        val codedWidth = vf.optInt(MediaFormat.KEY_WIDTH).takeIf { it > 0 } ?: retrieverWidth
        val codedHeight = vf.optInt(MediaFormat.KEY_HEIGHT).takeIf { it > 0 } ?: retrieverHeight
        val swap = rotation == 90 || rotation == 270
        val width = if (swap) codedHeight else codedWidth
        val height = if (swap) codedWidth else codedHeight

        val transfer = vf.optInt("color-transfer", -1)
        val hdr = when (transfer) {
            6 -> HdrTransfer.PQ
            7 -> HdrTransfer.HLG
            else -> HdrTransfer.NONE
        }
        val profile = vf.optInt(MediaFormat.KEY_PROFILE, -1)
        val tenBit = hdr.isHdr || isTenBitProfile(profile)

        val fps = vf.optFloat(MediaFormat.KEY_FRAME_RATE)
            .takeIf { it > 0.5f }
            ?: vf.optFloat("capture-rate").takeIf { it > 0.5f }
            ?: 0f

        return VideoInfo(
            uriString = uri.toString(),
            displayName = name,
            durationMs = durationMs,
            sizeBytes = size,
            width = width,
            height = height,
            rotationDegrees = ((rotation % 360) + 360) % 360,
            fps = fps,
            videoBitrateKbps = vf.optInt(MediaFormat.KEY_BIT_RATE) / 1000,
            videoMime = vf.getString(MediaFormat.KEY_MIME) ?: "video/avc",
            videoCodecName = (vf.getString(MediaFormat.KEY_MIME) ?: "video/avc").removePrefix("video/"),
            hasAudio = audioFormat != null,
            audioChannels = audioFormat?.optInt(MediaFormat.KEY_CHANNEL_COUNT) ?: 0,
            audioSampleRate = audioFormat?.optInt(MediaFormat.KEY_SAMPLE_RATE) ?: 0,
            audioBitrateKbps = (audioFormat?.optInt(MediaFormat.KEY_BIT_RATE) ?: 0) / 1000,
            audioMime = audioFormat?.getString(MediaFormat.KEY_MIME),
            audioTracks = audioTracks,
            subtitleTracks = subtitleTracks,
            hdr = hdr,
            colorTransfer = if (transfer >= 0) transfer.toString() else null,
            tenBit = tenBit,
        )
    }

    private fun isTenBitProfile(profile: Int): Boolean =
        profile == 2 || profile == 110 || profile == 122 || profile == 244

    private fun queryNameAndSize(uri: Uri, fallbackName: String?): Pair<String, Long> {
        var name = fallbackName ?: uri.lastPathSegment?.substringAfterLast('/') ?: "video"
        var size = 0L
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                        name = cursor.getString(nameIndex)
                    }
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            }
        }
        if (name.isBlank() || name == "video") {
            name = fallbackName ?: "video.mp4"
        }
        return name to size
    }

    companion object {
        fun openExtractor(extractor: MediaExtractor, uri: Uri, context: Context) {
            try {
                extractor.setDataSource(context, uri, null)
            } catch (t: Throwable) {
                val descriptor = context.contentResolver.openAssetFileDescriptor(uri, "r")
                    ?: throw FileNotFoundException("Could not open $uri")
                descriptor.use {
                    extractor.setDataSource(it.fileDescriptor, it.startOffset, it.declaredLength)
                }
            }
        }
    }
}

internal fun MediaFormat.optInt(key: String, def: Int = 0): Int =
    try {
        if (containsKey(key)) getInteger(key) else def
    } catch (t: Throwable) {
        def
    }

internal fun MediaFormat.optLong(key: String): Long =
    try {
        if (containsKey(key)) getLong(key) else 0L
    } catch (t: Throwable) {
        try {
            if (containsKey(key)) getInteger(key).toLong() else 0L
        } catch (t2: Throwable) {
            0L
        }
    }

internal fun MediaFormat.optFloat(key: String): Float =
    try {
        if (containsKey(key)) getFloat(key) else 0f
    } catch (t: Throwable) {
        try {
            if (containsKey(key)) getInteger(key).toFloat() else 0f
        } catch (t2: Throwable) {
            0f
        }
    }
