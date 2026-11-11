package com.f7developer.videofit.core.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.f7developer.videofit.core.model.VideoCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "videofit_settings")

data class Settings(
    val defaultCodec: VideoCodec = VideoCodec.H264,
    val defaultPresetId: String = "whatsapp",
    val measureQuality: Boolean = true,
    val keepOriginal: Boolean = true,
    val proUnlocked: Boolean = false,
    val compressionCount: Int = 0,
    val onboardingDone: Boolean = false,
)

/** Small, local-only settings store. No account, no sync, no analytics. */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val codec = stringPreferencesKey("default_codec")
        val preset = stringPreferencesKey("default_preset")
        val quality = booleanPreferencesKey("measure_quality")
        val keepOriginal = booleanPreferencesKey("keep_original")
        val pro = booleanPreferencesKey("pro_unlocked")
        val count = stringPreferencesKey("compression_count")
        val onboarding = booleanPreferencesKey("onboarding_done")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            defaultCodec = runCatching {
                VideoCodec.valueOf(prefs[Keys.codec] ?: VideoCodec.H264.name)
            }.getOrDefault(VideoCodec.H264),
            defaultPresetId = prefs[Keys.preset] ?: "whatsapp",
            measureQuality = prefs[Keys.quality] ?: true,
            keepOriginal = prefs[Keys.keepOriginal] ?: true,
            proUnlocked = prefs[Keys.pro] ?: false,
            compressionCount = prefs[Keys.count]?.toIntOrNull() ?: 0,
            onboardingDone = prefs[Keys.onboarding] ?: false,
        )
    }

    suspend fun setCodec(codec: VideoCodec) {
        context.dataStore.edit { it[Keys.codec] = codec.name }
    }

    suspend fun setPreset(presetId: String) {
        context.dataStore.edit { it[Keys.preset] = presetId }
    }

    suspend fun setMeasureQuality(enabled: Boolean) {
        context.dataStore.edit { it[Keys.quality] = enabled }
    }

    suspend fun setKeepOriginal(enabled: Boolean) {
        context.dataStore.edit { it[Keys.keepOriginal] = enabled }
    }

    suspend fun setProUnlocked(unlocked: Boolean) {
        context.dataStore.edit { it[Keys.pro] = unlocked }
    }

    suspend fun setOnboardingDone(done: Boolean) {
        context.dataStore.edit { it[Keys.onboarding] = done }
    }

    suspend fun recordCompression() {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.count]?.toIntOrNull() ?: 0
            prefs[Keys.count] = (current + 1).toString()
        }
    }
}
