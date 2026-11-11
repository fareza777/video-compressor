package com.f7developer.videofit.ui.progress

import android.app.Activity
import android.view.WindowManager
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.f7developer.videofit.core.service.CompressionController
import com.f7developer.videofit.core.service.CompressionState
import com.f7developer.videofit.ui.components.GhostButton
import com.f7developer.videofit.ui.components.ProgressRing
import com.f7developer.videofit.ui.components.StatPill
import com.f7developer.videofit.ui.components.VFCard
import com.f7developer.videofit.ui.theme.Space
import com.f7developer.videofit.ui.theme.VF
import kotlinx.coroutines.delay

@Composable
fun ProgressScreen(
    onFinished: () -> Unit,
    onAborted: () -> Unit,
) {
    val state by CompressionController.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Keep the panel awake while the encoder runs; the service owns the CPU lock.
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    LaunchedEffect(state) {
        if (state is CompressionState.Finished) onFinished()
    }

    // The service publishes its first Running state a moment after start. If it
    // never arrives (start refused, permission problem) leave 0% behind instead
    // of spinning forever.
    LaunchedEffect(Unit) {
        delay(10_000)
        val current = CompressionController.state.value
        if (current is CompressionState.Idle) onAborted()
    }

    val running = state as? CompressionState.Running

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
                .navigationBarsPadding()
                .padding(horizontal = Space.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(Space.huge))
            Text(
                text = if (running != null && running.progress < 0.02f) "Analysing" else "Compressing",
                style = MaterialTheme.typography.headlineMedium,
                color = VF.TextPrimary,
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                text = running?.name ?: "Preparing…",
                style = MaterialTheme.typography.bodyMedium,
                color = VF.TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(Space.xxl))
            ProgressRing(
                progress = running?.progress ?: 0f,
                size = 248,
                stroke = 16,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${((running?.progress ?: 0f) * 100).toInt()}%",
                        style = MaterialTheme.typography.displayMedium,
                        color = VF.TextPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = running?.speedLabel ?: "",
                        style = MaterialTheme.typography.labelMedium,
                        color = VF.Accent,
                    )
                }
            }

            Spacer(Modifier.height(Space.xxl))
            VFCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(Space.lg),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    StatPill(
                        label = "File",
                        value = "${(running?.index ?: 0) + 1} of ${running?.total ?: 1}",
                    )
                    StatPill(
                        label = "Progress",
                        value = "${((running?.overall ?: 0f) * 100).toInt()}%",
                    )
                    StatPill(
                        label = "Remaining",
                        value = running?.etaSeconds?.let { if (it > 60) "${it / 60} min" else "${it}s" } ?: "—",
                    )
                }
            }

            if (running?.thermalWarning == true) {
                Spacer(Modifier.height(Space.md))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(VF.Warning.copy(alpha = 0.12f))
                        .padding(Space.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Thermostat,
                        contentDescription = null,
                        tint = VF.Warning,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.size(Space.sm))
                    Text(
                        "Your phone is warm. Encoding may slow down to stay cool.",
                        style = MaterialTheme.typography.bodySmall,
                        color = VF.Warning,
                    )
                }
            }

            Spacer(Modifier.height(Space.lg))
            Text(
                text = "You can leave the app — compression keeps running in the background.",
                style = MaterialTheme.typography.bodySmall,
                color = VF.TextTertiary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.weight(1f))
            GhostButton(
                text = "Cancel",
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    CompressionController.cancel()
                    onAborted()
                },
            )
            Spacer(Modifier.height(Space.lg))
        }
    }
}
