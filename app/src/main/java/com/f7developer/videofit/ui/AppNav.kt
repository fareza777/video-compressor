package com.f7developer.videofit.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.f7developer.videofit.VideoFitApp
import com.f7developer.videofit.core.ads.AdsManager
import com.f7developer.videofit.core.service.CompressionController
import com.f7developer.videofit.ui.ads.RemoveAdsScreen
import com.f7developer.videofit.ui.history.HistoryScreen
import com.f7developer.videofit.ui.home.HomeScreen
import com.f7developer.videofit.ui.library.LibraryScreen
import com.f7developer.videofit.ui.onboarding.OnboardingScreen
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
    const val REMOVE_ADS = "remove_ads"
    const val LIBRARY = "library"
    const val HISTORY = "history"
}

@Composable
fun AppNav(
    incomingUris: List<Uri> = emptyList(),
    suggestedTargetMb: Int? = null,
) {
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

        else -> MainNav(
            incomingUris = incomingUris,
            suggestedTargetMb = suggestedTargetMb,
            onReplayIntro = { scope.launch { app.settings.setOnboardingDone(false) } },
            libraryRepository = app.library,
            historyRepository = app.history,
        )
    }
}

@Composable
private fun MainNav(
    incomingUris: List<Uri>,
    suggestedTargetMb: Int?,
    onReplayIntro: () -> Unit,
    libraryRepository: com.f7developer.videofit.core.data.LibraryRepository,
    historyRepository: com.f7developer.videofit.core.data.HistoryRepository,
) {
    val navController = rememberNavController()
    val context = LocalContext.current

    LaunchedEffect(Unit) { AdsManager.preloadInterstitial(context) }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenLibrary = { navController.navigate(Routes.LIBRARY) { launchSingleTop = true } },
                onOpenHistory = { navController.navigate(Routes.HISTORY) { launchSingleTop = true } },
                onStart = {
                    navController.navigate(Routes.PROGRESS) { launchSingleTop = true }
                },
                incomingUris = incomingUris,
                suggestedTargetMb = suggestedTargetMb,
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
            val leave: () -> Unit = {
                AdsManager.maybeShowInterstitial(context) {
                    CompressionController.reset()
                    navController.popBackStack(Routes.HOME, inclusive = false)
                }
            }
            ResultScreen(
                onDone = leave,
                onCompressAnother = leave,
                onOpenPaywall = { navController.navigate(Routes.REMOVE_ADS) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenRemoveAds = { navController.navigate(Routes.REMOVE_ADS) },
                onReplayIntro = onReplayIntro,
            )
        }
        composable(Routes.REMOVE_ADS) {
            RemoveAdsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.LIBRARY) {
            LibraryScreen(
                onBack = { navController.popBackStack() },
                repository = libraryRepository,
            )
        }
        composable(Routes.HISTORY) {
            HistoryScreen(
                onBack = { navController.popBackStack() },
                repository = historyRepository,
            )
        }
    }
}
