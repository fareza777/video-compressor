package com.f7developer.videofit.core.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.f7developer.videofit.BuildConfig
import com.f7developer.videofit.VideoFitApp
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * Thin wrapper around the three AdMob formats.
 *
 * Nothing is shown when the user bought "remove ads" or is inside the 24 h
 * rewarded window, so the check lives here rather than in every screen.
 */
object AdsManager {

    private const val MIN_INTERSTITIAL_GAP_MS = 90_000L

    private var initialized = false
    private var interstitial: InterstitialAd? = null
    private var loadingInterstitial = false
    private var rewarded: RewardedAd? = null
    private var loadingRewarded = false
    private var lastInterstitialAt = 0L

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        runCatching { MobileAds.initialize(context.applicationContext) {} }
    }

    fun preloadInterstitial(context: Context) {
        if (interstitial != null || loadingInterstitial) return
        loadingInterstitial = true
        InterstitialAd.load(
            context,
            BuildConfig.ADMOB_INTERSTITIAL_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    loadingInterstitial = false
                    interstitial = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loadingInterstitial = false
                    interstitial = null
                }
            },
        )
    }

    /** Shows a full-screen ad, then always continues navigation. */
    fun maybeShowInterstitial(context: Context, onDismiss: () -> Unit) {
        val activity = context.findActivity()
        val ad = interstitial
        val now = System.currentTimeMillis()
        if (activity == null || ad == null || now - lastInterstitialAt < MIN_INTERSTITIAL_GAP_MS) {
            if (activity != null) preloadInterstitial(activity)
            onDismiss()
            return
        }
        lastInterstitialAt = now
        interstitial = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                onDismiss()
                activity.let { preloadInterstitial(it) }
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                onDismiss()
                activity.let { preloadInterstitial(it) }
            }
        }
        ad.show(activity)
    }

    fun preloadRewarded(context: Context) {
        if (rewarded != null || loadingRewarded) return
        loadingRewarded = true
        RewardedAd.load(
            context,
            BuildConfig.ADMOB_REWARDED_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    loadingRewarded = false
                    rewarded = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loadingRewarded = false
                    rewarded = null
                }
            },
        )
    }

    fun showRewarded(
        context: Context,
        onRewarded: () -> Unit,
        onUnavailable: () -> Unit,
    ) {
        val activity = context.findActivity()
        val ad = rewarded
        if (activity == null || ad == null) {
            preloadRewarded(context)
            onUnavailable()
            return
        }
        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewarded = null
                preloadRewarded(activity)
                if (earned) onRewarded()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                rewarded = null
                preloadRewarded(activity)
                onUnavailable()
            }
        }
        ad.show(activity) { earned = true }
    }
}

/**
 * Banner that lives inside the layout (never overlapping the app's own buttons)
 * and disappears for paying users.
 */
@Composable
fun BannerAdSlot(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as? VideoFitApp ?: return
    val settings by app.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val current = settings ?: return
    if (current.adsHidden()) return

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            factory = { ctx ->
                AdView(ctx).apply {
                    setAdSize(AdSize.BANNER)
                    adUnitId = BuildConfig.ADMOB_BANNER_ID
                    loadAd(AdRequest.Builder().build())
                }
            },
        )
    }
}
