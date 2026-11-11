package com.f7developer.videofit

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.f7developer.videofit.ui.AppNav
import com.f7developer.videofit.ui.theme.VideoFitTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Transparent bars: the app draws its own gradient behind them, so the
        // default edge-to-edge scrim would leave a visible band under the status bar.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val incoming = extractVideoUris(intent)
        setContent {
            VideoFitTheme {
                AppNav(incomingUris = incoming)
            }
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
