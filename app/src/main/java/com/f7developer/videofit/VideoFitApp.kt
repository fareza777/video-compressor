package com.f7developer.videofit

import android.app.Application
import android.content.Context
import com.f7developer.videofit.core.ads.AdsManager
import com.f7developer.videofit.core.billing.BillingManager
import com.f7developer.videofit.core.data.HistoryRepository
import com.f7developer.videofit.core.data.LibraryRepository
import com.f7developer.videofit.core.data.SettingsRepository
import com.f7developer.videofit.core.engine.CompressionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Application entry point and tiny service locator (no DI framework needed). */
class VideoFitApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var settings: SettingsRepository
        private set
    lateinit var engine: CompressionEngine
        private set
    lateinit var billing: BillingManager
        private set
    lateinit var history: HistoryRepository
        private set
    lateinit var library: LibraryRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this)
        history = HistoryRepository(this)
        library = LibraryRepository(this)
        engine = CompressionEngine(this)
        billing = BillingManager(this, settings, appScope)
        AdsManager.init(this)
        AdsManager.preloadInterstitial(this)
    }
}

val Context.videoFit: VideoFitApp
    get() = applicationContext as VideoFitApp
