package com.f7developer.videofit.core.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/** Output file placement: app-private working folder, then the user's gallery. */
object OutputStore {

    private const val FOLDER = "VideoFit"

    fun workingDirectory(context: Context): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
        return File(base, FOLDER).apply { mkdirs() }
    }

    fun tempDirectory(context: Context): File =
        File(context.cacheDir, "compress").apply { mkdirs() }

    fun uniqueOutputFile(context: Context, displayName: String, targetBytes: Long): File {
        val dir = workingDirectory(context)
        val base = displayName.substringBeforeLast('.', displayName)
            .replace(Regex("[^A-Za-z0-9 _-]"), "")
            .trim()
            .ifBlank { "video" }
            .take(48)
        val size = "${(targetBytes / 1_000_000.0).let { if (it % 1.0 == 0.0) it.toInt().toString() else String.format("%.1f", it) }}mb"
        var candidate = File(dir, "${base}_${size}.mp4")
        var index = 1
        while (candidate.exists()) {
            candidate = File(dir, "${base}_${size}_$index.mp4")
            index++
        }
        return candidate
    }

    /** Copies the finished file into Movies/VideoFit so it shows up in the gallery. */
    fun saveToGallery(context: Context, file: File, displayName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$FOLDER")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values,
                ) ?: return false
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { input -> input.copyTo(out, 256 * 1024) }
                } ?: return false
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                true
            } else {
                val target = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    FOLDER,
                ).apply { mkdirs() }
                val dest = File(target, displayName)
                file.inputStream().use { input ->
                    FileOutputStream(dest).use { out -> input.copyTo(out, 256 * 1024) }
                }
                android.media.MediaScannerConnection.scanFile(
                    context, arrayOf(dest.absolutePath), arrayOf("video/mp4"), null,
                )
                true
            }
        } catch (t: Throwable) {
            Log.w("OutputStore", "could not save to gallery", t)
            false
        }
    }

    fun deleteQuietly(file: File?) {
        runCatching { file?.delete() }
    }

    fun fileUriString(file: File): String = Uri.fromFile(file).toString()
}
