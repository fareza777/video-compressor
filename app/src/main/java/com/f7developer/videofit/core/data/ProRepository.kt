package com.f7developer.videofit.core.data

import android.content.Context
import com.f7developer.videofit.BuildConfig

/**
 * Freemium gates.
 *
 * Free plan: unlimited compressions, custom targets, full H.264 quality.
 * Pro plan: H.265, output above 1080p, unlimited batch size, quality report.
 *
 * Purchases are not wired to a billing SDK yet; [unlockPro] is the single entry
 * point a Play Billing purchase/acknowledgement should call (see
 * docs/PLAY_RELEASE.md). No server or paid API is involved.
 */
class ProRepository(private val settings: SettingsRepository) {

    val freeTierLimit = Limits(
        maxShortSide = 1080,
        maxBatch = 3,
        allowHevc = false,
        allowQualityReport = false,
    )

    val proTier = Limits(
        maxShortSide = null,
        maxBatch = Int.MAX_VALUE,
        allowHevc = true,
        allowQualityReport = true,
    )

    fun limits(settings: Settings): Limits = if (settings.proUnlocked) proTier else freeTierLimit

    fun isPro(settings: Settings): Boolean = settings.proUnlocked

    /**
     * Local unlock. In a release build this is only reachable from a verified
     * Play Billing purchase; the debug build allows it so the Pro UI can be
     * tested on a device without a published listing.
     */
    suspend fun unlockPro() {
        settings.setProUnlocked(true)
    }

    suspend fun lockPro() {
        settings.setProUnlocked(false)
    }

    data class Limits(
        val maxShortSide: Int?,
        val maxBatch: Int,
        val allowHevc: Boolean,
        val allowQualityReport: Boolean,
    )

    companion object {
        fun debugUnlockAvailable(): Boolean = BuildConfig.ALLOW_DEV_UNLOCK
    }
}
