package com.f7developer.videofit.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.f7developer.videofit.BuildConfig
import com.f7developer.videofit.VideoFitApp
import com.f7developer.videofit.core.data.Settings
import com.f7developer.videofit.core.data.ThemeMode
import com.f7developer.videofit.core.model.VideoCodec
import com.f7developer.videofit.ui.components.LabeledSwitch
import com.f7developer.videofit.ui.components.Pill
import com.f7developer.videofit.ui.components.SectionLabel
import com.f7developer.videofit.ui.components.TopBar
import com.f7developer.videofit.ui.components.VFCard
import com.f7developer.videofit.ui.theme.Space
import com.f7developer.videofit.ui.theme.VF
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenRemoveAds: () -> Unit,
    onReplayIntro: () -> Unit = {},
) {
    val app = LocalContext.current.applicationContext as VideoFitApp
    val settings by app.settings.settings.collectAsStateWithLifecycle(initialValue = Settings())
    val scope = rememberCoroutineScope()
    val adFree = settings.adFreeUntilMs > System.currentTimeMillis()

    Box(
        Modifier
            .fillMaxSize()
            .background(VF.Background),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            TopBar(title = "Settings", onBack = onBack)
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.lg),
            ) {
                Spacer(Modifier.height(Space.md))

                // ---- Appearance -------------------------------------------------
                SectionLabel(text = "Appearance")
                Spacer(Modifier.height(Space.sm))
                VFCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Space.lg)) {
                        Text(
                            "Theme",
                            style = MaterialTheme.typography.titleMedium,
                            color = VF.TextPrimary,
                        )
                        Spacer(Modifier.height(Space.sm))
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                            ThemeMode.entries.forEach { mode ->
                                Pill(
                                    text = when (mode) {
                                        ThemeMode.LIGHT -> "Light"
                                        ThemeMode.DARK -> "Dark"
                                        ThemeMode.SYSTEM -> "System"
                                    },
                                    selected = settings.themeMode == mode,
                                    onClick = { scope.launch { app.settings.setThemeMode(mode) } },
                                )
                            }
                        }
                        Spacer(Modifier.height(Space.sm))
                        Text(
                            "Light is the default. System follows your phone's dark mode.",
                            style = MaterialTheme.typography.bodySmall,
                            color = VF.TextTertiary,
                        )
                    }
                }

                // ---- Compression ------------------------------------------------
                Spacer(Modifier.height(Space.xl))
                SectionLabel(text = "Compression defaults")
                Spacer(Modifier.height(Space.sm))
                VFCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Space.lg)) {
                        Text(
                            "Preferred codec",
                            style = MaterialTheme.typography.titleMedium,
                            color = VF.TextPrimary,
                        )
                        Spacer(Modifier.height(Space.sm))
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                            Pill(
                                text = "H.264",
                                selected = settings.defaultCodec == VideoCodec.H264,
                                onClick = { scope.launch { app.settings.setCodec(VideoCodec.H264) } },
                            )
                            Pill(
                                text = "H.265",
                                selected = settings.defaultCodec == VideoCodec.HEVC,
                                onClick = { scope.launch { app.settings.setCodec(VideoCodec.HEVC) } },
                            )
                        }
                        Spacer(Modifier.height(Space.md))
                        Text(
                            "Default target",
                            style = MaterialTheme.typography.titleMedium,
                            color = VF.TextPrimary,
                        )
                        Spacer(Modifier.height(Space.sm))
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                            listOf(8, 16, 25, 50).forEach { mb ->
                                val bytes = mb * 1_000_000L
                                Pill(
                                    text = "$mb MB",
                                    selected = settings.defaultTargetBytes == bytes,
                                    onClick = {
                                        scope.launch {
                                            app.settings.setDefaultTargetBytes(
                                                if (settings.defaultTargetBytes == bytes) 0L else bytes,
                                            )
                                        }
                                    },
                                )
                            }
                        }
                        Spacer(Modifier.height(Space.lg))
                        LabeledSwitch(
                            title = "Downscale to fit",
                            subtitle = "Lower resolution keeps more detail at small targets",
                            checked = settings.allowDownscale,
                            onCheckedChange = { scope.launch { app.settings.setAllowDownscale(it) } },
                        )
                        Spacer(Modifier.height(Space.md))
                        LabeledSwitch(
                            title = "Quality report",
                            subtitle = "Measure SSIM against the source after encoding",
                            checked = settings.measureQuality,
                            onCheckedChange = { scope.launch { app.settings.setMeasureQuality(it) } },
                        )
                    }
                }

                // ---- Ads --------------------------------------------------------
                Spacer(Modifier.height(Space.xl))
                SectionLabel(text = "Ads")
                Spacer(Modifier.height(Space.sm))
                VFCard(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenRemoveAds),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(Space.lg),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                when {
                                    settings.adsRemoved -> "Ads removed"
                                    adFree -> "Ad-free active"
                                    else -> "Remove ads"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = VF.TextPrimary,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                when {
                                    settings.adsRemoved -> "One-time purchase, restored with your Play account."
                                    adFree -> "Until " + SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())
                                        .format(Date(settings.adFreeUntilMs))
                                    else -> "One-time \$4.99 · no subscription"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = VF.TextTertiary,
                            )
                        }
                        Text(
                            when {
                                settings.adsRemoved -> "Active"
                                adFree -> "24 h"
                                else -> "Open"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = VF.Accent,
                        )
                    }
                }

                // ---- About ------------------------------------------------------
                Spacer(Modifier.height(Space.xl))
                SectionLabel(text = "About")
                Spacer(Modifier.height(Space.sm))
                VFCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Space.lg)) {
                        AboutRow("App", "Video Compressor: VideoFit")
                        AboutRow("Developer", "F7 Developer")
                        AboutRow("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                        AboutRow("Compressions", settings.compressionCount.toString())
                        Spacer(Modifier.height(Space.sm))
                        Text(
                            "Show intro again",
                            style = MaterialTheme.typography.labelLarge,
                            color = VF.Accent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onReplayIntro)
                                .padding(vertical = Space.sm),
                        )
                        Text(
                            "Reset compression defaults",
                            style = MaterialTheme.typography.labelLarge,
                            color = VF.TextSecondary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { scope.launch { app.settings.resetCompressionDefaults() } }
                                .padding(vertical = Space.sm),
                        )
                        Spacer(Modifier.height(Space.sm))
                        Text(
                            "Compression runs on your phone. The ad SDK needs the network; your videos, file names and pixels are never uploaded.",
                            style = MaterialTheme.typography.bodySmall,
                            color = VF.TextSecondary,
                        )
                        Spacer(Modifier.height(Space.md))
                        Text(
                            "Compression planning and target-size logic adapted from the MIT-licensed halfsize project (c) 2026 YKLovePython. Encoding uses Android MediaCodec. H.264/H.265 patent licensing is handled by the device vendor's codec implementation.",
                            style = MaterialTheme.typography.bodySmall,
                            color = VF.TextTertiary,
                        )
                    }
                }
                Spacer(Modifier.height(Space.xxl))
            }
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = VF.TextTertiary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = VF.TextPrimary)
    }
}
