package com.f7developer.videofit.ui.paywall

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.f7developer.videofit.VideoFitApp
import com.f7developer.videofit.core.data.ProRepository
import com.f7developer.videofit.ui.components.AccentButton
import com.f7developer.videofit.ui.components.GhostButton
import com.f7developer.videofit.ui.components.TopBar
import com.f7developer.videofit.ui.components.VFCard
import com.f7developer.videofit.ui.theme.Space
import com.f7developer.videofit.ui.theme.VF
import kotlinx.coroutines.launch

@Composable
fun PaywallScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as VideoFitApp
    val settings by app.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    val isPro = settings?.proUnlocked == true

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
                .navigationBarsPadding(),
        ) {
            TopBar(title = "", onBack = onBack)
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.xl),
            ) {
                Text(
                    "VideoFit",
                    style = MaterialTheme.typography.labelMedium,
                    color = VF.Accent,
                    letterSpacing = 2.sp,
                )
                Text(
                    "Pro",
                    style = MaterialTheme.typography.displayLarge,
                    color = VF.TextPrimary,
                )
                Spacer(Modifier.height(Space.sm))
                Text(
                    "Smaller files, bigger detail. One purchase, forever.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = VF.TextSecondary,
                )
                Spacer(Modifier.height(Space.xl))

                VFCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Space.lg)) {
                        Benefit("H.265 encoding", "About 30% smaller than H.264 at the same quality")
                        Benefit("4K output", "Keep full resolution on large targets")
                        Benefit("Unlimited batch", "Queue as many videos as you like")
                        Benefit("Quality report", "SSIM comparison against the original")
                        Benefit("Priority presets", "Faster settings for every social app")
                    }
                }

                Spacer(Modifier.height(Space.lg))
                VFCard(Modifier.fillMaxWidth(), backgroundColor = VF.SurfaceElevated) {
                    Column(Modifier.padding(Space.lg), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "One-time purchase",
                            style = MaterialTheme.typography.labelMedium,
                            color = VF.TextTertiary,
                        )
                        Spacer(Modifier.height(Space.xs))
                        Text(
                            "Unlock Pro",
                            style = MaterialTheme.typography.headlineMedium,
                            color = VF.TextPrimary,
                        )
                        Spacer(Modifier.height(Space.md))
                        AccentButton(
                            text = if (isPro) "Pro is active" else "Unlock Pro",
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isPro,
                            showArrow = !isPro,
                            onClick = {
                                if (ProRepository.debugUnlockAvailable()) {
                                    scope.launch {
                                        app.pro.unlockPro()
                                        message = "Pro unlocked on this device (debug build)."
                                    }
                                } else {
                                    message = "Purchases open with the Google Play release."
                                }
                            },
                        )
                        if (message != null) {
                            Spacer(Modifier.height(Space.sm))
                            Text(
                                message!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = VF.Success,
                                textAlign = TextAlign.Center,
                            )
                        }
                        if (isPro && ProRepository.debugUnlockAvailable()) {
                            Spacer(Modifier.height(Space.sm))
                            GhostButton(
                                text = "Turn Pro off (testing)",
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    scope.launch {
                                        app.pro.lockPro()
                                        message = null
                                    }
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(Space.lg))
                Text(
                    "Free plan stays generous: unlimited compressions, any target size, full H.264 quality.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VF.TextTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Space.xxl))
            }
        }
    }
}

@Composable
private fun Benefit(title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = Space.sm)) {
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(VF.Accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = VF.Accent,
                modifier = Modifier.size(14.dp),
            )
        }
        Spacer(Modifier.width(Space.md))
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = VF.TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = VF.TextTertiary)
        }
    }
}
