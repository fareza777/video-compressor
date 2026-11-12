package com.f7developer.videofit.ui.ads

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.f7developer.videofit.BuildConfig
import com.f7developer.videofit.VideoFitApp
import com.f7developer.videofit.core.ads.AdsManager
import com.f7developer.videofit.core.ads.findActivity
import com.f7developer.videofit.ui.components.AccentButton
import com.f7developer.videofit.ui.components.GhostButton
import com.f7developer.videofit.ui.components.TopBar
import com.f7developer.videofit.ui.components.VFCard
import com.f7developer.videofit.ui.theme.Space
import com.f7developer.videofit.ui.theme.VF
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RemoveAdsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as VideoFitApp
    val settings by app.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    val billing = app.billing
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        billing.start()
        AdsManager.preloadRewarded(context)
    }

    val removed = settings?.adsRemoved == true
    val adFreeUntil = settings?.adFreeUntilMs ?: 0L
    val now = System.currentTimeMillis()
    val adFree = !removed && adFreeUntil > now
    val price = billing.priceLabel ?: "$4.99"

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(VF.GradientTop, VF.Background, VF.Background))),
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
                    "AD-FREE",
                    style = MaterialTheme.typography.labelMedium,
                    color = VF.Accent,
                    letterSpacing = 2.sp,
                )
                Spacer(Modifier.height(Space.xs))
                Text(
                    "No ads.\nJust compression.",
                    style = MaterialTheme.typography.headlineLarge,
                    color = VF.TextPrimary,
                )
                Spacer(Modifier.height(Space.sm))
                Text(
                    "One payment, kept on your Play account. Restore it on any phone.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = VF.TextSecondary,
                )

                Spacer(Modifier.height(Space.xl))
                VFCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Space.lg)) {
                        Benefit("No banner", "The bottom of the home screen is yours again")
                        Benefit("No interstitials", "Nothing pops up after a compression")
                        Benefit("No expiry", "One-time purchase, tied to your Google account")
                        Benefit("Keeps development going", "No subscription, no data collection")
                    }
                }

                Spacer(Modifier.height(Space.lg))
                VFCard(Modifier.fillMaxWidth(), backgroundColor = VF.SurfaceElevated) {
                    Column(
                        Modifier.padding(Space.lg),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (removed) {
                            Text(
                                "Ads are off",
                                style = MaterialTheme.typography.headlineMedium,
                                color = VF.Accent,
                            )
                            Spacer(Modifier.height(Space.xs))
                            Text(
                                "Thanks for supporting VideoFit.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = VF.TextSecondary,
                            )
                        } else {
                            Text(
                                "One-time purchase",
                                style = MaterialTheme.typography.labelMedium,
                                color = VF.TextTertiary,
                            )
                            Spacer(Modifier.height(Space.xs))
                            Text(
                                price,
                                style = MaterialTheme.typography.displayMedium,
                                color = VF.TextPrimary,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(Space.md))
                            AccentButton(
                                text = "Remove ads · $price",
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    val activity = context.findActivity()
                                    if (activity == null) {
                                        message = "Could not open the purchase screen."
                                    } else {
                                        billing.purchase(activity)
                                        message = billing.lastMessage
                                    }
                                },
                            )
                            Spacer(Modifier.height(Space.sm))
                            GhostButton(
                                text = "Restore purchase",
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    billing.restore()
                                    message = billing.lastMessage
                                },
                            )
                            if (!billing.ready) {
                                Spacer(Modifier.height(Space.sm))
                                Text(
                                    "Google Play Billing is not available in this build. Install the Play release to buy.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VF.TextTertiary,
                                    textAlign = TextAlign.Center,
                                )
                            }
                            if (BuildConfig.ALLOW_DEV_UNLOCK) {
                                Spacer(Modifier.height(Space.sm))
                                Text(
                                    "Simulate purchase (debug)",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = VF.AccentDim,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .padding(horizontal = Space.md, vertical = Space.xs)
                                        .then(
                                            Modifier.clickableCompat {
                                                scope.launch {
                                                    app.settings.setAdsRemoved(true)
                                                    message = "Ads removed on this debug build."
                                                }
                                            },
                                        ),
                                )
                            }
                        }
                    }
                }

                if (!removed) {
                    Spacer(Modifier.height(Space.lg))
                    VFCard(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(Space.lg),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Rounded.PlayCircle,
                                contentDescription = null,
                                tint = VF.Accent,
                                modifier = Modifier.size(30.dp),
                            )
                            Spacer(Modifier.width(Space.md))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (adFree) "Ad-free is active" else "Not ready to buy?",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = VF.TextPrimary,
                                )
                                Text(
                                    if (adFree) {
                                        "Until " + SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())
                                            .format(Date(adFreeUntil))
                                    } else {
                                        "Watch one short ad for 24 hours without banners."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VF.TextTertiary,
                                )
                            }
                            Text(
                                "Watch",
                                style = MaterialTheme.typography.labelLarge,
                                color = VF.Accent,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickableCompat {
                                        AdsManager.showRewarded(
                                            context = context,
                                            onRewarded = {
                                                scope.launch {
                                                    app.settings.grantAdFreeUntil(
                                                        System.currentTimeMillis() + 24 * 60 * 60 * 1000L,
                                                    )
                                                    message = "24 hours ad-free unlocked."
                                                }
                                            },
                                            onUnavailable = {
                                                message = "The ad is still loading. Try again in a moment."
                                            },
                                        )
                                    }
                                    .padding(horizontal = Space.sm, vertical = Space.xs),
                            )
                        }
                    }
                }

                (message ?: billing.lastMessage)?.let { text ->
                    Spacer(Modifier.height(Space.md))
                    Text(
                        text,
                        style = MaterialTheme.typography.bodySmall,
                        color = VF.TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(Space.lg))
                Text(
                    "Compression stays free and unlimited either way. Ads never cover a button and never interrupt a running encode.",
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

/** Small helper so the modifier chain reads cleanly. */
private fun Modifier.clickableCompat(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)

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
