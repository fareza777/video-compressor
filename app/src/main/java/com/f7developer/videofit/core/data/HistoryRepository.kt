package com.f7developer.videofit.core.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One finished (or failed) compression, kept for the History screen. */
data class HistoryEntry(
    val id: String,
    val displayName: String,
    val sourceBytes: Long,
    val outputBytes: Long,
    val targetBytes: Long,
    val codec: String,
    val width: Int,
    val height: Int,
    val ssim: Double?,
    val verdict: String,
    val elapsedMs: Long,
    val at: Long,
    val skipped: Boolean,
    val failed: Boolean,
    val failure: String?,
    val outputPath: String?,
) {
    val savedBytes: Long get() = (sourceBytes - outputBytes).coerceAtLeast(0)
    val savedPercent: Int
        get() = if (sourceBytes <= 0) 0
        else ((savedBytes.toDouble() / sourceBytes) * 100).toInt().coerceIn(-999, 100)
    val hitTarget: Boolean get() = !failed && targetBytes > 0 && outputBytes <= targetBytes * 1.02
}

/**
 * Tiny JSON-file store: no database dependency, survives app restarts, capped
 * at 300 entries so it can never grow without bound.
 */
class HistoryRepository(private val context: Context) {

    private val file: File get() = File(context.filesDir, "history.json")
    private val lock = Any()

    fun load(): List<HistoryEntry> = synchronized(lock) { read() }

    fun add(entry: HistoryEntry) = synchronized(lock) {
        val all = read().toMutableList()
        all.add(0, entry)
        write(all.take(MAX_ENTRIES))
    }

    fun remove(id: String) = synchronized(lock) {
        write(read().filterNot { it.id == id })
    }

    fun clear() = synchronized(lock) { write(emptyList()) }

    private fun read(): List<HistoryEntry> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    add(
                        HistoryEntry(
                            id = o.getString("id"),
                            displayName = o.optString("name"),
                            sourceBytes = o.optLong("source"),
                            outputBytes = o.optLong("output"),
                            targetBytes = o.optLong("target"),
                            codec = o.optString("codec"),
                            width = o.optInt("width"),
                            height = o.optInt("height"),
                            ssim = if (o.isNull("ssim")) null else o.optDouble("ssim"),
                            verdict = o.optString("verdict"),
                            elapsedMs = o.optLong("elapsed"),
                            at = o.optLong("at"),
                            skipped = o.optBoolean("skipped"),
                            failed = o.optBoolean("failed"),
                            failure = if (o.isNull("failure")) null else o.optString("failure"),
                            outputPath = if (o.isNull("path")) null else o.optString("path"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun write(items: List<HistoryEntry>) {
        runCatching {
            val array = JSONArray()
            items.forEach { e ->
                array.put(
                    JSONObject().apply {
                        put("id", e.id)
                        put("name", e.displayName)
                        put("source", e.sourceBytes)
                        put("output", e.outputBytes)
                        put("target", e.targetBytes)
                        put("codec", e.codec)
                        put("width", e.width)
                        put("height", e.height)
                        put("ssim", e.ssim ?: JSONObject.NULL)
                        put("verdict", e.verdict)
                        put("elapsed", e.elapsedMs)
                        put("at", e.at)
                        put("skipped", e.skipped)
                        put("failed", e.failed)
                        put("failure", e.failure ?: JSONObject.NULL)
                        put("path", e.outputPath ?: JSONObject.NULL)
                    },
                )
            }
            file.writeText(array.toString())
        }
    }

    companion object {
        private const val MAX_ENTRIES = 300
    }
}
