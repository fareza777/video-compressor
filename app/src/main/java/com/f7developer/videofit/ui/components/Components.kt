package com.f7developer.videofit.ui.components

import android.graphics.Bitmap
import android.net.Uri
import android.util.Size
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Movie
import com.f7developer.videofit.ui.theme.Space
import com.f7developer.videofit.ui.theme.VF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val CardShape = RoundedCornerShape(22.dp)

@Composable
fun VFCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = VF.Surface,
    border: BorderStroke? = BorderStroke(1.dp, VF.Outline),
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(CardShape)
            .background(backgroundColor)
            .then(if (border != null) Modifier.border(border, CardShape) else Modifier),
    ) {
        content()
    }
}

@Composable
fun SectionLabel(text: String, trailing: String? = null, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = VF.TextTertiary,
            letterSpacing = 1.4.sp,
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelMedium,
                color = VF.TextSecondary,
            )
        }
    }
}

@Composable
fun AccentButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showArrow: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = VF.Accent,
            contentColor = VF.OnAccent,
            disabledContainerColor = VF.Outline,
            disabledContentColor = VF.TextTertiary,
        ),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
        if (showArrow) {
            Spacer(Modifier.width(Space.sm))
            Icon(Icons.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun GhostButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, VF.Outline, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = VF.TextPrimary)
    }
}

@Composable
fun Pill(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    locked: Boolean = false,
    onClick: () -> Unit,
) {
    val bg = if (selected) VF.Accent else VF.SurfaceElevated
    val fg = if (selected) VF.OnAccent else VF.TextSecondary
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .then(if (selected) Modifier else Modifier.border(1.dp, VF.Outline, RoundedCornerShape(50)))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
        if (locked) {
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Rounded.Lock,
                contentDescription = null,
                tint = if (selected) VF.OnAccent else VF.AccentDim,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

@Composable
fun StatPill(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = VF.TextTertiary,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = VF.TextPrimary)
    }
}

@Composable
fun LabeledSwitch(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = VF.TextPrimary)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = VF.TextTertiary)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = VF.OnAccent,
                checkedTrackColor = VF.Accent,
                uncheckedThumbColor = VF.TextSecondary,
                uncheckedTrackColor = VF.SurfaceElevated,
                uncheckedBorderColor = VF.Outline,
            ),
        )
    }
}

@Composable
fun TopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.lg, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = VF.TextPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(Space.xs))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = VF.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        action?.invoke()
    }
}

/** Renders a video's poster frame; cheap, cached per composition. */
@Composable
fun VideoThumbnail(
    uri: Uri,
    modifier: Modifier = Modifier,
    cornerRadius: Int = 12,
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val retriever = android.media.MediaMetadataRetriever()
                try {
                    // file:// outputs from the library are not readable through
                    // loadThumbnail, so go straight to the decoder for those.
                    if (uri.scheme == "file") {
                        retriever.setDataSource(uri.path)
                    } else {
                        retriever.setDataSource(context, uri)
                    }
                    retriever.getFrameAtTime(0, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } finally {
                    runCatching { retriever.release() }
                }
            }.getOrNull()
        }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius.dp))
            .background(VF.SurfaceElevated),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Rounded.Movie,
                contentDescription = null,
                tint = VF.TextTertiary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Int = 220,
    stroke: Int = 14,
    center: @Composable () -> Unit,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(400),
        label = "ring",
    )
    // Resolve theme colours here: DrawScope is not a composable scope.
    val accent = VF.Accent
    val success = VF.Success
    val outline = VF.Outline
    val background = VF.Background
    Box(modifier = modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(
                    Brush.sweepGradient(
                        listOf(accent, success, accent),
                    ),
                )
                .padding(stroke.dp)
                .clip(CircleShape)
                .background(background),
        )
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val strokeWidth = stroke.dp.toPx()
            drawCircle(
                color = outline,
                style = androidx.compose.ui.graphics.drawscope.Stroke(strokeWidth),
                radius = this.size.minDimension / 2 - strokeWidth / 2,
            )
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    strokeWidth,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                ),
                topLeft = androidx.compose.ui.geometry.Offset(strokeWidth / 2, strokeWidth / 2),
                size = androidx.compose.ui.geometry.Size(
                    this.size.width - strokeWidth,
                    this.size.height - strokeWidth,
                ),
            )
        }
        center()
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = VF.TextTertiary,
            textAlign = TextAlign.Center,
        )
    }
}
