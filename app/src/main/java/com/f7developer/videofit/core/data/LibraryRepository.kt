package com.f7developer.videofit.core.data

import android.content.Context
import android.media.MediaMetadataRetriever
import com.f7developer.videofit.core.media.OutputStore
import java.io.File

data class LibraryItem(
    val file: File,
    val name: String,
    val bytes: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val modifiedAt: Long,
) {
    val resolution: String get() = if (width > 0 && height > 0) "${width}x$height" else "—"
}

/** Reads the app's own output folder; metadata is probed lazily per file. */
class LibraryRepository(private val context: Context) {

    fun list(): List<LibraryItem> {
        val dir = OutputStore.workingDirectory(context)
        val files = dir.listFiles { f -> f.isFile && f.length() > 0 && f.extension.equals("mp4", true) }
            ?: return emptyList()
        return files.map { file ->
            var width = 0
            var height = 0
            var duration = 0L
            runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(file.absolutePath)
                    width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                        ?.toIntOrNull() ?: 0
                    height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                        ?.toIntOrNull() ?: 0
                    duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull() ?: 0L
                    val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                        ?.toIntOrNull() ?: 0
                    if (rotation == 90 || rotation == 270) {
                        val swap = width
                        width = height
                        height = swap
                    }
                } finally {
                    runCatching { retriever.release() }
                }
            }
            LibraryItem(
                file = file,
                name = file.name,
                bytes = file.length(),
                width = width,
                height = height,
                durationMs = duration,
                modifiedAt = file.lastModified(),
            )
        }.sortedByDescending { it.modifiedAt }
    }

    fun delete(item: LibraryItem): Boolean = runCatching { item.file.delete() }.getOrDefault(false)

    fun totalBytes(items: List<LibraryItem>): Long = items.sumOf { it.bytes }
}
