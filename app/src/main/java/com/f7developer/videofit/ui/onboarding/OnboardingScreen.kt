package com.f7developer.videofit.ui.onboarding

import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.f7developer.videofit.ui.components.AccentButton
import com.f7developer.videofit.ui.components.VFCard
import com.f7developer.videofit.ui.theme.Space
import com.f7developer.videofit.ui.theme.VF
import kotlinx.coroutines.launch

/**
 * Three screens, one idea each, no scrolling. Shown once on first launch and
 * re-openable from the paywall ("How it works").
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val lastPage = pagerState.currentPage == 2

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(VF.GradientTop, VF.Background, VF.Background),
                ),
            ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.lg, vertical = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrandChip()
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onFinish) {
                    Text(
                        "Skip",
                        style = MaterialTheme.typography.labelLarge,
                        color = VF.TextSecondary,
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                when (page) {
                    0 -> IntroPage(
                        visual = { SizeVisual() },
                        kicker = "TARGET SIZE",
                        title = "Pick the size.\nGet that size.",
                        body = "Type any target in MB. VideoFit hits it — WhatsApp, email and social limits included.",
                    )
                    1 -> IntroPage(
                        visual = { LadderVisual() },
                        kicker = "SMART ENCODING",
                        title = "It does\nthe math.",
                        body = "Resolution, bitrate and frame rate are chosen for your target, then encoded on the hardware codec.",
                    )
                    else -> IntroPage(
                        visual = { PrivacyVisual() },
                        kicker = "PRIVATE",
                        title = "Nothing\nleaves your phone.",
                        body = "No account, no upload, no ads. The app has no internet permission at all.",
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Space.md),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(3) { index ->
                    val active = index == pagerState.currentPage
                    Box(
                        Modifier
                            .padding(horizontal = 4.dp)
                            .size(width = if (active) 26.dp else 8.dp, height = 8.dp)
                            .clip(CircleShape)
                            .background(if (active) VF.Accent else VF.Outline),
                    )
                }
            }

            AccentButton(
                text = if (lastPage) "Start compressing" else "Continue",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.lg),
                onClick = {
                    if (lastPage) {
                        onFinish()
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
            )
            Spacer(Modifier.height(Space.lg))
        }
    }
}

@Composable
private fun BrandChip() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(8.dp))
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
            style = MaterialTheme.typography.titleMedium,
            color = VF.TextPrimary,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun IntroPage(
    visual: @Composable () -> Unit,
    kicker: String,
    title: String,
    body: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(240.dp),
            contentAlignment = Alignment.Center,
        ) {
            visual()
        }
        Spacer(Modifier.height(Space.xl))
        Text(
            kicker,
            style = MaterialTheme.typography.labelMedium,
            color = VF.Accent,
            letterSpacing = 2.sp,
        )
        Spacer(Modifier.height(Space.sm))
        Text(
            title,
            style = MaterialTheme.typography.headlineLarge,
            color = VF.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.md))
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            color = VF.TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SizeVisual() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SizeCard(value = "23.2", unit = "MB", label = "Original", highlighted = false)
        Text(
            "→",
            style = MaterialTheme.typography.headlineLarge,
            color = VF.Accent,
            modifier = Modifier.padding(horizontal = Space.md),
        )
        SizeCard(value = "1.99", unit = "MB", label = "Target", highlighted = true)
    }
}

@Composable
private fun SizeCard(value: String, unit: String, label: String, highlighted: Boolean) {
    VFCard(
        backgroundColor = if (highlighted) VF.Accent.copy(alpha = 0.12f) else VF.Surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (highlighted) VF.Accent else VF.Outline,
        ),
    ) {
        Column(
            Modifier.padding(horizontal = Space.lg, vertical = Space.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = if (highlighted) VF.AccentDim else VF.TextTertiary,
                letterSpacing = 1.2.sp,
            )
            Spacer(Modifier.height(Space.xs))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    style = MaterialTheme.typography.headlineLarge,
                    color = if (highlighted) VF.Accent else VF.TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    unit,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (highlighted) VF.Accent else VF.TextSecondary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun LadderVisual() {
    val rows = listOf(
        Triple("1080p", "4.4 Mbps", false),
        Triple("720p", "2.0 Mbps", true),
        Triple("540p", "1.1 Mbps", false),
    )
    VFCard {
        Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            rows.forEach { (res, bitrate, picked) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (picked) VF.Accent.copy(alpha = 0.14f) else VF.SurfaceElevated)
                        .padding(horizontal = Space.md, vertical = Space.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        res,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (picked) VF.Accent else VF.TextSecondary,
                        fontWeight = if (picked) FontWeight.Bold else FontWeight.Normal,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        bitrate,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (picked) VF.Accent else VF.TextTertiary,
                    )
                    if (picked) {
                        Spacer(Modifier.width(Space.sm))
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            tint = VF.Accent,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacyVisual() {
    Box(
        Modifier
            .size(180.dp)
            .clip(CircleShape)
            .background(VF.Accent.copy(alpha = 0.10f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(VF.Accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Lock,
                contentDescription = null,
                tint = VF.Accent,
                modifier = Modifier.size(56.dp),
            )
        }
    }
}
