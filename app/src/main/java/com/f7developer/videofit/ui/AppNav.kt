package com.f7developer.videofit.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.f7developer.videofit.VideoFitApp
import com.f7developer.videofit.core.service.CompressionController
import com.f7developer.videofit.ui.home.HomeScreen
import com.f7developer.videofit.ui.onboarding.OnboardingScreen
import com.f7developer.videofit.ui.paywall.PaywallScreen
import com.f7developer.videofit.ui.progress.ProgressScreen
import com.f7developer.videofit.ui.result.ResultScreen
import com.f7developer.videofit.ui.settings.SettingsScreen
import com.f7developer.videofit.ui.theme.VF
import kotlinx.coroutines.launch

private object Routes {
    const val HOME = "home"
    const val PROGRESS = "progress"
    const val RESULT = "result"
    const val SETTINGS = "settings"
    const val PAYWALL = "paywall"
}

@Composable
fun AppNav(incomingUris: List<Uri> = emptyList()) {
    val app = LocalContext.current.applicationContext as VideoFitApp
    // null while DataStore is still reading: show the brand backdrop instead of
    // flashing onboarding for a returning user.
    val stored by app.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()

    val current = stored
    when {
        current == null -> Box(Modifier.fillMaxSize().background(VF.Background))

        !current.onboardingDone -> OnboardingScreen(
            onFinish = { scope.launch { app.settings.setOnboardingDone(true) } },
        )

        else -> MainNav(incomingUris = incomingUris)
    }
}

@Composable
private fun MainNav(incomingUris: List<Uri>) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenPaywall = { navController.navigate(Routes.PAYWALL) },
                onStart = {
                    navController.navigate(Routes.PROGRESS) { launchSingleTop = true }
                },
                incomingUris = incomingUris,
            )
        }
        composable(Routes.PROGRESS) {
            ProgressScreen(
                onFinished = {
                    navController.navigate(Routes.RESULT) {
                        popUpTo(Routes.HOME)
                    }
                },
                onAborted = {
                    CompressionController.reset()
                    navController.popBackStack(Routes.HOME, inclusive = false)
                },
            )
        }
        composable(Routes.RESULT) {
            ResultScreen(
                onDone = {
                    CompressionController.reset()
                    navController.popBackStack(Routes.HOME, inclusive = false)
                },
                onCompressAnother = {
                    CompressionController.reset()
                    navController.popBackStack(Routes.HOME, inclusive = false)
                },
                onOpenPaywall = { navController.navigate(Routes.PAYWALL) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenPaywall = { navController.navigate(Routes.PAYWALL) },
            )
        }
        composable(Routes.PAYWALL) {
            PaywallScreen(onBack = { navController.popBackStack() })
        }
    }
}
