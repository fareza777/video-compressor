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
import com.f7developer.videofit.core.model.VideoCodec
import com.f7developer.videofit.ui.components.LabeledSwitch
import com.f7developer.videofit.ui.components.Pill
import com.f7developer.videofit.ui.components.SectionLabel
import com.f7developer.videofit.ui.components.TopBar
import com.f7developer.videofit.ui.components.VFCard
import com.f7developer.videofit.ui.theme.Space
import com.f7developer.videofit.ui.theme.VF
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenPaywall: () -> Unit) {
    val app = LocalContext.current.applicationContext as VideoFitApp
    val settings by app.settings.settings.collectAsStateWithLifecycle(initialValue = Settings())
    val scope = rememberCoroutineScope()

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
                SectionLabel(text = "Defaults")
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
                                onClick = {
                                    if (settings.proUnlocked) {
                                        scope.launch { app.settings.setCodec(VideoCodec.HEVC) }
                                    } else {
                                        onOpenPaywall()
                                    }
                                },
                            )
                        }
                        Spacer(Modifier.height(Space.lg))
                        LabeledSwitch(
                            title = "Quality report after encoding",
                            subtitle = "Measures SSIM against the source (Pro)",
                            checked = settings.measureQuality,
                            enabled = settings.proUnlocked,
                            onCheckedChange = { scope.launch { app.settings.setMeasureQuality(it) } },
                        )
                        Spacer(Modifier.height(Space.md))
                        LabeledSwitch(
                            title = "Keep original file",
                            subtitle = "Never modify what you picked",
                            checked = settings.keepOriginal,
                            onCheckedChange = { scope.launch { app.settings.setKeepOriginal(it) } },
                        )
                    }
                }

                Spacer(Modifier.height(Space.xl))
                SectionLabel(text = "Plan")
                Spacer(Modifier.height(Space.sm))
                VFCard(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenPaywall),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(Space.lg),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (settings.proUnlocked) "VideoFit Pro active" else "VideoFit Pro",
                                style = MaterialTheme.typography.titleMedium,
                                color = VF.TextPrimary,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                if (settings.proUnlocked) {
                                    "H.265, 4K output, unlimited batch and quality reports unlocked."
                                } else {
                                    "H.265, 4K output, unlimited batch and quality reports."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = VF.TextTertiary,
                            )
                        }
                        Text(
                            if (settings.proUnlocked) "Active" else "Upgrade",
                            style = MaterialTheme.typography.labelLarge,
                            color = VF.Accent,
                        )
                    }
                }

                Spacer(Modifier.height(Space.xl))
                SectionLabel(text = "About")
                Spacer(Modifier.height(Space.sm))
                VFCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Space.lg)) {
                        AboutRow("App", "Video Compressor: VideoFit")
                        AboutRow("Developer", "F7 Developer")
                        AboutRow("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                        AboutRow("Compressions", settings.compressionCount.toString())
                        Spacer(Modifier.height(Space.md))
                        Text(
                            "Everything runs on your phone. VideoFit has no internet permission, no account, no analytics and no ads.",
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
