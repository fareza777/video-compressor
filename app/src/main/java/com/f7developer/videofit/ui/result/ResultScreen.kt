package com.f7developer.videofit.ui.result

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.f7developer.videofit.core.engine.CompressionResult
import com.f7developer.videofit.core.media.OutputStore
import com.f7developer.videofit.core.service.CompressionController
import com.f7developer.videofit.core.service.CompressionState
import com.f7developer.videofit.core.util.Format
import com.f7developer.videofit.ui.components.AccentButton
import com.f7developer.videofit.ui.components.GhostButton
import com.f7developer.videofit.ui.components.VFCard
import com.f7developer.videofit.ui.theme.Space
import com.f7developer.videofit.ui.theme.VF
import kotlinx.coroutines.launch

@Composable
fun ResultScreen(
    onDone: () -> Unit,
    onCompressAnother: () -> Unit,
    onOpenPaywall: () -> Unit,
) {
    val state by CompressionController.state.collectAsStateWithLifecycle()
    val finished = state as? CompressionState.Finished
    val results = finished?.results.orEmpty()
    val primary = results.firstOrNull()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(VF.GradientTop, VF.Background, VF.Background)),
            ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg),
        ) {
            Spacer(Modifier.height(Space.xxl))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = VF.Accent,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(Space.sm))
                Text(
                    text = if (finished?.cancelled == true) "Cancelled" else "Done",
                    style = MaterialTheme.typography.headlineMedium,
                    color = VF.TextPrimary,
                )
            }
            Spacer(Modifier.height(Space.xs))
            Text(
                text = if (results.size == 1) primary?.displayName ?: "" else "${results.size} videos compressed",
                style = MaterialTheme.typography.bodyMedium,
                color = VF.TextTertiary,
            )

            if (primary != null) {
                Spacer(Modifier.height(Space.lg))
                FrameCompare(
                    source = Uri.parse(primary.sourceUriString),
                    output = Uri.fromFile(primary.outputFile),
                    durationMs = primary.durationMs,
                )
                Spacer(Modifier.height(Space.xs))
                Text(
                    "Drag the divider to compare the same frame",
                    style = MaterialTheme.typography.labelSmall,
                    color = VF.TextTertiary,
                )

                Spacer(Modifier.height(Space.lg))
                VFCard(Modifier.fillMaxWidth(), backgroundColor = VF.SurfaceElevated) {
                    Column(Modifier.padding(Space.lg)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = Format.bytes(primary.sourceBytes),
                                style = MaterialTheme.typography.titleLarge,
                                color = VF.TextTertiary,
                            )
                            Text(
                                "  →  ",
                                style = MaterialTheme.typography.titleLarge,
                                color = VF.TextTertiary,
                            )
                            Text(
                                text = Format.bytes(primary.outputBytes),
                                style = MaterialTheme.typography.headlineMedium,
                                color = VF.Accent,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.weight(1f))
                            if (primary.savedPercent > 0) {
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(VF.Accent.copy(alpha = 0.14f))
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                ) {
                                    Text(
                                        "${primary.savedPercent}% smaller",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = VF.Accent,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(Space.lg))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            ResultStat("Resolution", primary.plan.resolutionLabel)
                            ResultStat("Bitrate", Format.bitrate(primary.plan.videoKbps))
                            ResultStat("Codec", primary.plan.codec.label)
                            ResultStat(
                                "Quality",
                                primary.ssim?.let { String.format("%.3f", it) } ?: "—",
                            )
                        }
                        Spacer(Modifier.height(Space.md))
                        Text(
                            text = if (primary.skipped) {
                                "Already under target — copied unchanged · ${primary.elapsedMs / 1000}s"
                            } else {
                                "${primary.verdict.label} · ${if (primary.hitTarget) "target hit" else "slightly over target"} · ${primary.elapsedMs / 1000}s"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (primary.skipped || primary.hitTarget) VF.Success else VF.Warning,
                        )
                    }
                }

                Spacer(Modifier.height(Space.md))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    GhostButton(
                        text = "Share",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            runCatching { shareFile(context, primary) }
                                .onFailure {
                                    Toast.makeText(context, "Could not share this file", Toast.LENGTH_SHORT).show()
                                }
                        },
                    )
                    GhostButton(
                        text = "Save to gallery",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            scope.launch {
                                val ok = OutputStore.saveToGallery(
                                    context, primary.outputFile, primary.outputFile.name,
                                )
                                Toast.makeText(
                                    context,
                                    if (ok) "Saved to Movies/VideoFit" else "Could not save to gallery",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                    )
                }

                if (results.size > 1) {
                    Spacer(Modifier.height(Space.lg))
                    results.drop(1).forEach { item ->
                        VFCard(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(Space.md),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Rounded.Download,
                                    contentDescription = null,
                                    tint = VF.AccentDim,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(Space.sm))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        item.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = VF.TextPrimary,
                                        maxLines = 1,
                                    )
                                    Text(
                                        "${Format.bytes(item.sourceBytes)} → ${Format.bytes(item.outputBytes)} · ${item.plan.resolutionLabel}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = VF.TextTertiary,
                                    )
                                }
                                Text(
                                    "${item.savedPercent}%",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = VF.Accent,
                                )
                            }
                        }
                        Spacer(Modifier.height(Space.sm))
                    }
                }
            }

            finished?.failures?.takeIf { it.isNotEmpty() }?.let { failures ->
                Spacer(Modifier.height(Space.md))
                VFCard(Modifier.fillMaxWidth(), backgroundColor = VF.Danger.copy(alpha = 0.1f)) {
                    Column(Modifier.padding(Space.md)) {
                        failures.forEach { failure ->
                            Text(
                                failure,
                                style = MaterialTheme.typography.bodySmall,
                                color = VF.Danger,
                            )
                            Spacer(Modifier.height(Space.xs))
                        }
                    }
                }
            }

            Spacer(Modifier.height(Space.xl))
            AccentButton(
                text = "Compress another",
                modifier = Modifier.fillMaxWidth(),
                onClick = onCompressAnother,
            )
            Spacer(Modifier.height(Space.sm))
            GhostButton(
                text = "Back to home",
                modifier = Modifier.fillMaxWidth(),
                onClick = onDone,
            )
            Spacer(Modifier.height(Space.xxl))
        }
    }
}

@Composable
private fun VideoBox(uri: Uri) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 10f)
            .clip(RoundedCornerShape(14.dp))
            .background(VF.Surface),
    ) {
        VideoSurface(uri = uri, modifier = Modifier.fillMaxSize())
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VideoSurface(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        },
    )
}

@Composable
private fun ResultStat(label: String, value: String) {
    Column {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = VF.TextTertiary,
            letterSpacing = 0.8.sp,
        )
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = VF.TextPrimary)
    }
}

private fun shareFile(context: Context, result: CompressionResult) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        result.outputFile,
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share video"))
}
