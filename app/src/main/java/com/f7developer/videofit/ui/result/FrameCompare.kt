package com.f7developer.videofit.ui.result

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.f7developer.videofit.ui.theme.Space
import com.f7developer.videofit.ui.theme.VF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The same frame from the source and the output, with a draggable divider.
 * A still comparison is the honest way to show what compression cost.
 */
@Composable
fun FrameCompare(
    source: Uri,
    output: Uri,
    durationMs: Long,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var before by remember(source) { mutableStateOf<ImageBitmap?>(null) }
    var after by remember(output) { mutableStateOf<ImageBitmap?>(null) }
    var fraction by remember { mutableFloatStateOf(0.5f) }
    var aspect by remember { mutableFloatStateOf(16f / 10f) }

    LaunchedEffect(source, output, durationMs) {
        withContext(Dispatchers.IO) {
            // Near the first keyframe: every provider can return this frame
            // exactly, so both sides really show the same moment.
            val at = minOf(1_000L, durationMs / 10).coerceAtLeast(0L)
            before = frame(context, source, at)?.asImageBitmap()
            after = frame(context, output, at)?.asImageBitmap()
        }
        before?.let { if (it.height > 0) aspect = it.width.toFloat() / it.height }
    }

    val left = before
    val right = after

    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(aspect.coerceIn(0.5f, 2.2f))
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black),
    ) {
        if (left == null || right == null) {
            Text(
                "Preparing comparison…",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            fraction = (change.position.x / size.width).coerceIn(0.04f, 0.96f)
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            fraction = (offset.x / size.width).coerceIn(0.04f, 0.96f)
                        }
                    },
            ) {
                val dst = IntSize(size.width.toInt(), size.height.toInt())
                drawImage(
                    image = right,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(right.width, right.height),
                    dstOffset = IntOffset.Zero,
                    dstSize = dst,
                )
                clipRect(right = size.width * fraction) {
                    drawImage(
                        image = left,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(left.width, left.height),
                        dstOffset = IntOffset.Zero,
                        dstSize = dst,
                    )
                }
                drawLine(
                    color = Color.White,
                    start = Offset(size.width * fraction, 0f),
                    end = Offset(size.width * fraction, size.height),
                    strokeWidth = 2.5.dp.toPx(),
                )
                drawCircle(
                    color = Color.White,
                    radius = 7.dp.toPx(),
                    center = Offset(size.width * fraction, size.height / 2f),
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(Space.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                CompareChip("BEFORE")
                CompareChip("AFTER", accent = true)
            }
        }
    }
}

@Composable
private fun CompareChip(text: String, accent: Boolean = false) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = if (accent) VF.Accent else Color.White,
            letterSpacing = 1.2.sp,
        )
    }
}

private fun frame(
    context: android.content.Context,
    uri: Uri,
    atMs: Long,
): Bitmap? = runCatching {
    val retriever = MediaMetadataRetriever()
    try {
        if (uri.scheme == "file") {
            retriever.setDataSource(uri.path)
        } else {
            retriever.setDataSource(context, uri)
        }
        retriever.getFrameAtTime(atMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
    } finally {
        runCatching { retriever.release() }
    }
}.getOrNull()
