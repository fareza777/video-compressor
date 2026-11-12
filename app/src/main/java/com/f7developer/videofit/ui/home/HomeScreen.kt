package com.f7developer.videofit.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.f7developer.videofit.core.ads.BannerAdSlot
import com.f7developer.videofit.core.model.EncodePlan
import com.f7developer.videofit.core.model.VideoCodec
import com.f7developer.videofit.core.util.Format
import com.f7developer.videofit.ui.components.AccentButton
import com.f7developer.videofit.ui.components.LabeledSwitch
import com.f7developer.videofit.ui.components.Pill
import com.f7developer.videofit.ui.components.VFCard
import com.f7developer.videofit.ui.components.VideoThumbnail
import com.f7developer.videofit.ui.theme.Space
import com.f7developer.videofit.ui.theme.VF
import java.util.Locale

/**
 * One screen, no hunting: pick, choose a size, compress. Everything is
 * unlocked. The banner sits below the primary button inside the layout, so it
 * can never cover the app's own controls or the system navigation bar.
 */
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenHistory: () -> Unit,
    onStart: () -> Unit,
    incomingUris: List<android.net.Uri> = emptyList(),
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var advanced by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20),
    ) { uris -> viewModel.addUris(uris) }

    LaunchedEffect(incomingUris) {
        if (incomingUris.isNotEmpty()) viewModel.addUris(incomingUris)
    }

    val plan = state.firstInfo?.let { viewModel.planFor(it, state) }
    val launchPicker = {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
    }
    val count = state.picks.count { it.info != null }

    Column(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to VF.GradientTop,
                    0.4f to VF.Background,
                    1f to VF.Background,
                ),
            )
            .statusBarsPadding(),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg),
        ) {
            HeaderRow(
                onOpenSettings = onOpenSettings,
                onOpenLibrary = onOpenLibrary,
                onOpenHistory = onOpenHistory,
            )
            Spacer(Modifier.height(Space.sm))

            Text(
                text = "Any video.",
                style = MaterialTheme.typography.displayLarge,
                color = VF.TextPrimary,
            )
            Text(
                text = "Any size.",
                style = MaterialTheme.typography.displayLarge,
                color = VF.Accent,
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                text = "Compress to any target size — offline.",
                style = MaterialTheme.typography.bodyMedium,
                color = VF.TextSecondary,
            )

            Spacer(Modifier.height(Space.lg))
            TargetCard(state = state, viewModel = viewModel)

            Spacer(Modifier.height(Space.md))
            if (state.picks.isEmpty()) {
                AddRow(onClick = launchPicker)
            } else {
                SourceList(
                    state = state,
                    onRemove = viewModel::removePick,
                    onAddMore = launchPicker,
                )
            }

            Spacer(Modifier.height(Space.md))
            EncodeRow(
                state = state,
                advanced = advanced,
                onToggleAdvanced = { advanced = !advanced },
                onCodec = viewModel::setCodec,
                onToggleDownscale = viewModel::toggleDownscale,
                onToggleQuality = viewModel::toggleQualityReport,
            )

            if (plan != null) {
                Spacer(Modifier.height(Space.md))
                PlanStrip(plan = plan)
            }
            Spacer(Modifier.height(Space.lg))
        }

        // Bottom block: CTA first, banner underneath. Both are in the layout,
        // so neither overlaps content and the banner clears the nav bar.
        Column(
            Modifier
                .fillMaxWidth()
                .background(VF.Background)
                .navigationBarsPadding(),
        ) {
            AccentButton(
                text = when (count) {
                    0 -> "Select videos"
                    1 -> "Compress video"
                    else -> "Compress $count videos"
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.lg, vertical = Space.sm),
                enabled = state.picks.isEmpty() || state.ready,
                onClick = { if (count == 0) launchPicker() else viewModel.startCompression(onStart) },
            )
            BannerAdSlot()
        }
    }
}

@Composable
private fun HeaderRow(
    onOpenSettings: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(VF.Accent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "V",
                style = MaterialTheme.typography.labelLarge,
                color = VF.OnAccent,
                fontWeight = FontWeight.Black,
            )
        }
        Spacer(Modifier.width(Space.sm))
        Text(
            "VideoFit",
            style = MaterialTheme.typography.titleLarge,
            color = VF.TextPrimary,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onOpenLibrary) {
            Icon(
                Icons.Rounded.PhotoLibrary,
                contentDescription = "Library",
                tint = VF.TextSecondary,
            )
        }
        IconButton(onClick = onOpenHistory) {
            Icon(
                Icons.Rounded.History,
                contentDescription = "History",
                tint = VF.TextSecondary,
            )
        }
        IconButton(onClick = onOpenSettings) {
            Icon(
                Icons.Rounded.Settings,
                contentDescription = "Settings",
                tint = VF.TextSecondary,
            )
        }
    }
}

@Composable
private fun TargetCard(state: HomeUiState, viewModel: HomeViewModel) {
    var sliderValue by remember(state.targetBytes) {
        mutableIntStateOf((state.targetBytes / 1_000_000L).toInt().coerceIn(1, 200))
    }
    val megabytes = state.targetBytes / 1_000_000.0
    val sizeText = if (megabytes % 1.0 == 0.0) {
        megabytes.toInt().toString()
    } else {
        String.format(Locale.US, "%.1f", megabytes)
    }

    VFCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = Space.lg, vertical = Space.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = sizeText,
                    style = MaterialTheme.typography.displayMedium,
                    color = VF.Accent,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    "MB",
                    style = MaterialTheme.typography.titleLarge,
                    color = VF.Accent.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 10.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "TARGET SIZE",
                    style = MaterialTheme.typography.labelSmall,
                    color = VF.TextTertiary,
                    letterSpacing = 1.4.sp,
                )
            }

            PresetChips(
                state = state,
                onPreset = viewModel::selectPreset,
                onCustom = { viewModel.setTargetMegabytes(sliderValue) },
            )

            Slider(
                value = sliderValue.toFloat(),
                onValueChange = {
                    sliderValue = it.toInt()
                    viewModel.setTargetMegabytes(sliderValue)
                },
                valueRange = 1f..200f,
                colors = SliderDefaults.colors(
                    thumbColor = VF.Accent,
                    activeTrackColor = VF.Accent,
                    inactiveTrackColor = VF.Outline,
                ),
            )

            OutlinedTextField(
                value = state.customTargetText,
                onValueChange = viewModel::setCustomTarget,
                placeholder = {
                    Text("Or type an exact size, e.g. 8.5", style = MaterialTheme.typography.bodyMedium)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VF.Accent,
                    unfocusedBorderColor = VF.Outline,
                    focusedTextColor = VF.TextPrimary,
                    unfocusedTextColor = VF.TextPrimary,
                    cursorColor = VF.Accent,
                    focusedPlaceholderColor = VF.TextTertiary,
                    unfocusedPlaceholderColor = VF.TextTertiary,
                ),
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PresetChips(
    state: HomeUiState,
    onPreset: (com.f7developer.videofit.core.model.SizePreset) -> Unit,
    onCustom: () -> Unit,
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Space.xs),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        state.presets.forEach { preset ->
            Pill(
                text = preset.label,
                selected = state.activePresetId == preset.id,
                onClick = { onPreset(preset) },
            )
        }
        Pill(
            text = "Custom",
            selected = state.activePresetId == "custom",
            onClick = onCustom,
        )
    }
}

@Composable
private fun AddRow(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(VF.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(VF.Accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = VF.Accent)
        }
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text("Add videos", style = MaterialTheme.typography.titleMedium, color = VF.TextPrimary)
            Text(
                "MP4, MOV, MKV · up to 4K",
                style = MaterialTheme.typography.bodySmall,
                color = VF.TextTertiary,
            )
        }
        Text("Browse", style = MaterialTheme.typography.labelLarge, color = VF.Accent)
    }
}

@Composable
private fun SourceList(
    state: HomeUiState,
    onRemove: (android.net.Uri) -> Unit,
    onAddMore: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        state.picks.forEach { pick ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(VF.Surface)
                    .padding(Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VideoThumbnail(
                    uri = pick.uri,
                    modifier = Modifier.size(width = 64.dp, height = 44.dp),
                )
                Spacer(Modifier.width(Space.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = pick.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = VF.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val info = pick.info
                    Text(
                        text = when {
                            pick.error != null -> pick.error.orEmpty()
                            info == null -> "Reading…"
                            else -> "${Format.bytes(info.sizeBytes)} · ${info.width}×${info.height} · ${Format.durationFromMillis(info.durationMs)}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (pick.error != null) VF.Danger else VF.TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { onRemove(pick.uri) }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Remove",
                        tint = VF.TextTertiary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onAddMore)
                .padding(vertical = Space.sm),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = VF.Accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Space.xs))
            Text("Add another video", style = MaterialTheme.typography.labelLarge, color = VF.Accent)
        }
    }
}

@Composable
private fun EncodeRow(
    state: HomeUiState,
    advanced: Boolean,
    onToggleAdvanced: () -> Unit,
    onCodec: (VideoCodec) -> Unit,
    onToggleDownscale: () -> Unit,
    onToggleQuality: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
            ) {
                Pill(
                    text = "H.264",
                    selected = state.codec == VideoCodec.H264,
                    onClick = { onCodec(VideoCodec.H264) },
                )
                Pill(
                    text = "H.265 · smaller",
                    selected = state.codec == VideoCodec.HEVC,
                    onClick = { onCodec(VideoCodec.HEVC) },
                )
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onToggleAdvanced)
                    .padding(horizontal = Space.sm, vertical = Space.sm),
            ) {
                Icon(
                    Icons.Rounded.Tune,
                    contentDescription = "Advanced",
                    tint = if (advanced) VF.Accent else VF.TextTertiary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = advanced,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            VFCard(
                Modifier
                    .fillMaxWidth()
                    .padding(top = Space.sm),
                backgroundColor = VF.SurfaceElevated,
                border = BorderStroke(1.dp, VF.Outline),
            ) {
                Column(Modifier.padding(Space.lg)) {
                    LabeledSwitch(
                        title = "Downscale to fit",
                        subtitle = "Keeps more detail at small targets",
                        checked = state.allowDownscale,
                        onCheckedChange = { onToggleDownscale() },
                    )
                    Spacer(Modifier.height(Space.md))
                    LabeledSwitch(
                        title = "Quality report",
                        subtitle = "SSIM check after encoding",
                        checked = state.measureQuality,
                        onCheckedChange = { onToggleQuality() },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanStrip(plan: EncodePlan) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(VF.SurfaceElevated)
            .padding(horizontal = Space.md, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = plan.resolutionLabel,
            style = MaterialTheme.typography.titleMedium,
            color = VF.Accent,
            fontWeight = FontWeight.Bold,
        )
        Dot()
        Text(
            "≈ ${Format.megabytes(plan.estimatedBytes)}",
            style = MaterialTheme.typography.bodyMedium,
            color = VF.TextSecondary,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = plan.codec.label,
            style = MaterialTheme.typography.labelMedium,
            color = VF.TextTertiary,
        )
        if (plan.warnings.isNotEmpty()) {
            Spacer(Modifier.width(Space.sm))
            Text("!", style = MaterialTheme.typography.labelLarge, color = VF.Warning)
        }
    }
}

@Composable
private fun Dot() {
    Text(
        " · ",
        style = MaterialTheme.typography.bodyMedium,
        color = VF.TextTertiary,
    )
}
