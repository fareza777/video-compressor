package com.f7developer.videofit

import android.app.Application
import android.content.Context
import com.f7developer.videofit.core.data.ProRepository
import com.f7developer.videofit.core.data.SettingsRepository
import com.f7developer.videofit.core.engine.CompressionEngine

/** Application entry point and tiny service locator (no DI framework needed). */
class VideoFitApp : Application() {

    lateinit var settings: SettingsRepository
        private set
    lateinit var pro: ProRepository
        private set
    lateinit var engine: CompressionEngine
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this)
        pro = ProRepository(settings)
        engine = CompressionEngine(this)
    }
}

val Context.videoFit: VideoFitApp
    get() = applicationContext as VideoFitApp
