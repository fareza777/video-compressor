package com.f7developer.videofit.core.quality

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow

/**
 * Cheap, honest quality check.
 *
 * The reference implementation samples three points and compares SSIM, plus
 * VMAF at the midpoint. VMAF needs Netflix's libvmaf (a large native library
 * with its own model files), which is not worth the APK weight on mobile, so
 * VideoFit measures SSIM on downscaled luma and reports PSNR alongside it.
 * The verdict thresholds are the ones the reference uses for SSIM.
 */
class QualityAnalyzer(private val context: Context) {

    data class Sample(
        val ssim: Double?,
        val psnr: Double?,
        val compared: Int,
    ) {
        val verdict: Verdict
            get() {
                val value = ssim ?: return Verdict.UNKNOWN
                return when {
                    value >= 0.98 -> Verdict.INDISTINGUISHABLE
                    value >= 0.95 -> Verdict.SAME
                    value >= 0.90 -> Verdict.SLIGHT
                    value >= 0.80 -> Verdict.VISIBLE
                    else -> Verdict.DEGRADED
                }
            }
    }

    enum class Verdict(val label: String) {
        INDISTINGUISHABLE("Indistinguishable"),
        SAME("Looks the same"),
        SLIGHT("Slight softening"),
        VISIBLE("Visible loss"),
        DEGRADED("Clearly degraded"),
        UNKNOWN("Not measured"),
    }

    fun measure(source: Uri, output: Uri, durationMs: Long): Sample {
        if (durationMs < 500) return Sample(null, null, 0)
        val sourceRetriever = MediaMetadataRetriever()
        val outputRetriever = MediaMetadataRetriever()
        val ssims = mutableListOf<Double>()
        val psnrs = mutableListOf<Double>()
        try {
            setDataSourceFor(sourceRetriever, source)
            setDataSourceFor(outputRetriever, output)
            val points = listOf(0.25, 0.5, 0.75).map { (durationMs * it).toLong() }
            for (atMs in points) {
                val before = frameAt(sourceRetriever, atMs) ?: continue
                val after = frameAt(outputRetriever, atMs) ?: continue
                val size = compareSize(before, after)
                val a = Bitmap.createScaledBitmap(before, size.first, size.second, true)
                val c = Bitmap.createScaledBitmap(after, size.first, size.second, true)
                // SSIM needs the same geometry; both are stretched to one grid.
                val lumaA = luma(a)
                val lumaB = luma(c)
                ssims += ssim(lumaA, lumaB, size.first, size.second)
                psnr(lumaA, lumaB)?.let { psnrs += it }
                a.recycle(); c.recycle()
                before.recycle(); after.recycle()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "quality measurement failed", t)
        } finally {
            runCatching { sourceRetriever.release() }
            runCatching { outputRetriever.release() }
        }
        return Sample(
            ssim = ssims.takeIf { it.isNotEmpty() }?.average(),
            psnr = psnrs.takeIf { it.isNotEmpty() }?.average(),
            compared = ssims.size,
        )
    }

    /**
     * MediaMetadataRetriever.setDataSource(String) only understands file paths,
     * so a content:// URI from the picker has to go through the Context overload.
     */
    private fun setDataSourceFor(retriever: MediaMetadataRetriever, uri: Uri) {
        if (uri.scheme == "content") {
            retriever.setDataSource(context, uri)
        } else {
            retriever.setDataSource(uri.path ?: uri.toString())
        }
    }

    private fun frameAt(retriever: MediaMetadataRetriever, atMs: Long): Bitmap? =
        runCatching {
            // CLOSEST (not CLOSEST_SYNC): two encodes have different keyframe
            // placement, and comparing different frames reports false quality loss.
            retriever.getFrameAtTime(atMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
        }.getOrNull()

    private fun compareSize(a: Bitmap, b: Bitmap): Pair<Int, Int> {
        val width = minOf(a.width, b.width, 320).coerceAtLeast(64)
        val aspect = a.height.toFloat() / a.width.coerceAtLeast(1)
        val height = (width * aspect).toInt().coerceAtLeast(64)
        return width to height
    }

    private fun luma(bitmap: Bitmap): IntArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            pixels[i] = ((r * 299 + g * 587 + b * 114) / 1000)
        }
        return pixels
    }

    private fun ssim(a: IntArray, b: IntArray, width: Int, height: Int): Double {
        val window = 8
        val c1 = (0.01 * 255).pow(2)
        val c2 = (0.03 * 255).pow(2)
        var total = 0.0
        var windows = 0
        var y = 0
        while (y + window <= height) {
            var x = 0
            while (x + window <= width) {
                var sumA = 0.0
                var sumB = 0.0
                var sumAA = 0.0
                var sumBB = 0.0
                var sumAB = 0.0
                for (j in 0 until window) {
                    val row = (y + j) * width + x
                    for (i in 0 until window) {
                        val va = a[row + i].toDouble()
                        val vb = b[row + i].toDouble()
                        sumA += va; sumB += vb
                        sumAA += va * va; sumBB += vb * vb; sumAB += va * vb
                    }
                }
                val n = (window * window).toDouble()
                val meanA = sumA / n
                val meanB = sumB / n
                val varA = sumAA / n - meanA * meanA
                val varB = sumBB / n - meanB * meanB
                val cov = sumAB / n - meanA * meanB
                val numerator = (2 * meanA * meanB + c1) * (2 * cov + c2)
                val denominator = (meanA * meanA + meanB * meanB + c1) * (varA + varB + c2)
                total += numerator / denominator
                windows++
                x += window
            }
            y += window
        }
        return if (windows == 0) 0.0 else total / windows
    }

    private fun psnr(a: IntArray, b: IntArray): Double? {
        if (a.isEmpty() || a.size != b.size) return null
        var sum = 0.0
        for (i in a.indices) {
            val d = (a[i] - b[i]).toDouble()
            sum += d * d
        }
        val mse = sum / a.size
        if (mse <= 0.0) return 100.0
        return 10.0 * log10(255.0 * 255.0 / mse)
    }

    companion object {
        private const val TAG = "QualityAnalyzer"
    }
}
