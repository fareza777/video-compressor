package com.f7developer.videofit.core.util

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import java.util.Locale

/** Byte/duration formatting shared by every screen, matching the reference output style. */
object Format {

    fun bytes(value: Long?): String {
        if (value == null) return "-"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = value.toDouble()
        var unit = 0
        while (size >= 1000 && unit < units.lastIndex) {
            size /= 1000
            unit++
        }
        val digits = when {
            size >= 100 -> 0
            size >= 10 -> 1
            else -> 2
        }
        return String.format(Locale.US, "%.${digits}f %s", size, units[unit])
    }

    fun megabytes(value: Long?): String {
        if (value == null) return "-"
        return String.format(Locale.US, "%.1f MB", value / 1_000_000.0)
    }

    fun duration(seconds: Double): String {
        if (!seconds.isFinite() || seconds <= 0) return "-"
        val total = seconds.roundToLong()
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return when {
            h > 0 -> String.format(Locale.US, "%dh %02dm", h, m)
            m > 0 -> String.format(Locale.US, "%dm %02ds", m, s)
            else -> "${s}s"
        }
    }

    fun durationFromMillis(millis: Long): String = duration(millis / 1000.0)

    fun clock(seconds: Double): String {
        val total = seconds.coerceAtLeast(0.0).roundToInt()
        return String.format(Locale.US, "%02d:%02d", total / 60, total % 60)
    }

    fun bitrate(kbps: Int): String =
        if (kbps >= 1000) String.format(Locale.US, "%.2f Mbps", kbps / 1000.0)
        else "$kbps kbps"

    fun ratio(from: Long, to: Long): String {
        if (from <= 0 || to <= 0) return "-"
        val value = from.toDouble() / to
        return if (value >= 10) "${value.roundToInt()}x" else String.format(Locale.US, "%.1f", value)
    }

    fun percentSaving(from: Long, to: Long): Int {
        if (from <= 0) return 0
        return (((from - to).toDouble() / from) * 100).roundToInt().coerceIn(0, 99)
    }

    fun signedPercentDelta(actual: Long, target: Long): String {
        if (target <= 0) return "-"
        val delta = (actual - target).toDouble() / target * 100
        val rounded = (abs(delta) * 10).roundToInt() / 10.0
        val sign = if (delta > 0) "+" else if (delta < 0) "-" else ""
        return String.format(Locale.US, "%s%.1f%%", sign, rounded)
    }

    /** Accepts "20", "20mb", "1.5 gb", "700k". A bare number means MB. */
    fun parseSizeToBytes(input: String): Long? {
        val cleaned = input.trim().lowercase(Locale.US).replace(" ", "")
        if (cleaned.isEmpty()) return null
        val match = Regex("^([0-9]*\\.?[0-9]+)(b|kb|k|mb|m|gb|g)?$").find(cleaned) ?: return null
        val value = match.groupValues[1].toDoubleOrNull() ?: return null
        val scale = when (match.groupValues[2]) {
            "b" -> 1.0
            "k", "kb" -> 1_000.0
            "", "m", "mb" -> 1_000_000.0
            "g", "gb" -> 1_000_000_000.0
            else -> 1_000_000.0
        }
        return (value * scale).roundToLong().coerceAtLeast(1)
    }
}
