package com.f7developer.videofit.core.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.f7developer.videofit.core.model.VideoCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "videofit_settings")

enum class ThemeMode { LIGHT, DARK, SYSTEM }

data class Settings(
    val defaultCodec: VideoCodec = VideoCodec.H264,
    val defaultPresetId: String = "whatsapp",
    val measureQuality: Boolean = true,
    val keepOriginal: Boolean = true,
    val compressionCount: Int = 0,
    val onboardingDone: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    /** 0 = follow [defaultPresetId]; otherwise an exact byte target. */
    val defaultTargetBytes: Long = 0L,
    val allowDownscale: Boolean = true,
    val adsRemoved: Boolean = false,
    /** Epoch millis until which a rewarded ad keeps banners hidden. */
    val adFreeUntilMs: Long = 0L,
) {
    fun adsHidden(nowMs: Long = System.currentTimeMillis()): Boolean =
        adsRemoved || adFreeUntilMs > nowMs
}

/** Small, local-only settings store. No account, no sync, no analytics. */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val codec = stringPreferencesKey("default_codec")
        val preset = stringPreferencesKey("default_preset")
        val quality = booleanPreferencesKey("measure_quality")
        val keepOriginal = booleanPreferencesKey("keep_original")
        val count = stringPreferencesKey("compression_count")
        val onboarding = booleanPreferencesKey("onboarding_done")
        val theme = stringPreferencesKey("theme_mode")
        val target = longPreferencesKey("default_target_bytes")
        val downscale = booleanPreferencesKey("allow_downscale")
        val adsRemoved = booleanPreferencesKey("ads_removed")
        val adFreeUntil = longPreferencesKey("ad_free_until")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            defaultCodec = runCatching {
                VideoCodec.valueOf(prefs[Keys.codec] ?: VideoCodec.H264.name)
            }.getOrDefault(VideoCodec.H264),
            defaultPresetId = prefs[Keys.preset] ?: "whatsapp",
            measureQuality = prefs[Keys.quality] ?: true,
            keepOriginal = prefs[Keys.keepOriginal] ?: true,
            compressionCount = prefs[Keys.count]?.toIntOrNull() ?: 0,
            onboardingDone = prefs[Keys.onboarding] ?: false,
            themeMode = runCatching {
                ThemeMode.valueOf(prefs[Keys.theme] ?: ThemeMode.LIGHT.name)
            }.getOrDefault(ThemeMode.LIGHT),
            defaultTargetBytes = prefs[Keys.target] ?: 0L,
            allowDownscale = prefs[Keys.downscale] ?: true,
            adsRemoved = prefs[Keys.adsRemoved] ?: false,
            adFreeUntilMs = prefs[Keys.adFreeUntil] ?: 0L,
        )
    }

    suspend fun setCodec(codec: VideoCodec) {
        context.dataStore.edit { it[Keys.codec] = codec.name }
    }

    suspend fun setPreset(presetId: String) {
        context.dataStore.edit {
            it[Keys.preset] = presetId
            it.remove(Keys.target)
        }
    }

    suspend fun setDefaultTargetBytes(bytes: Long) {
        context.dataStore.edit { it[Keys.target] = bytes }
    }

    suspend fun setMeasureQuality(enabled: Boolean) {
        context.dataStore.edit { it[Keys.quality] = enabled }
    }

    suspend fun setKeepOriginal(enabled: Boolean) {
        context.dataStore.edit { it[Keys.keepOriginal] = enabled }
    }

    suspend fun setAllowDownscale(enabled: Boolean) {
        context.dataStore.edit { it[Keys.downscale] = enabled }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.theme] = mode.name }
    }

    suspend fun setOnboardingDone(done: Boolean) {
        context.dataStore.edit { it[Keys.onboarding] = done }
    }

    suspend fun setAdsRemoved(removed: Boolean) {
        context.dataStore.edit { it[Keys.adsRemoved] = removed }
    }

    suspend fun grantAdFreeUntil(epochMs: Long) {
        context.dataStore.edit { it[Keys.adFreeUntil] = epochMs }
    }

    suspend fun clearAdFree() {
        context.dataStore.edit { it.remove(Keys.adFreeUntil) }
    }

    /** Restores compression defaults without touching the theme or entitlement. */
    suspend fun resetCompressionDefaults() {
        context.dataStore.edit {
            it.remove(Keys.codec)
            it.remove(Keys.preset)
            it.remove(Keys.quality)
            it.remove(Keys.keepOriginal)
            it.remove(Keys.target)
            it.remove(Keys.downscale)
        }
    }

    suspend fun recordCompression() {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.count]?.toIntOrNull() ?: 0
            prefs[Keys.count] = (current + 1).toString()
        }
    }
}
