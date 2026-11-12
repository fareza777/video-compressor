package com.f7developer.videofit

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.f7developer.videofit.core.data.ThemeMode
import com.f7developer.videofit.ui.AppNav
import com.f7developer.videofit.ui.theme.VideoFitTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Transparent bars: the app draws its own gradient behind them, so the
        // default edge-to-edge scrim would leave a visible band under the status bar.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )
        val incoming = extractVideoUris(intent)
        val suggestedTarget = suggestedTargetMb()
        val app = application as VideoFitApp

        setContent {
            val settings by app.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            // Light is the default until the stored preference arrives.
            VideoFitTheme(mode = settings?.themeMode ?: ThemeMode.LIGHT) {
                AppNav(incomingUris = incoming, suggestedTargetMb = suggestedTarget)
            }
        }
    }

    /**
     * When a video is shared from WhatsApp/Gmail/Instagram, start with that
     * app's upload limit already selected.
     */
    private fun suggestedTargetMb(): Int? {
        val source = runCatching { referrer?.host }.getOrNull() ?: return null
        return when {
            source.contains("whatsapp", true) -> 16
            source.contains("gm", true) || source.contains("gmail", true) -> 18
            source.contains("instagram", true) -> 25
            source.contains("telegram", true) -> 20
            else -> null
        }
    }

    /** "Open with" / share-sheet entry: hand the video straight to the picker. */
    private fun extractVideoUris(intent: Intent?): List<Uri> {
        if (intent == null) return emptyList()
        val single = when (intent.action) {
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }
        if (single != null) return listOf(single)
        val multiple = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
        return multiple?.filterNotNull() ?: emptyList()
    }
}
