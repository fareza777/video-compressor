package com.f7developer.videofit.ui.home

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.f7developer.videofit.VideoFitApp
import com.f7developer.videofit.core.model.EncodePlan
import com.f7developer.videofit.core.model.Presets
import com.f7developer.videofit.core.model.SizePreset
import com.f7developer.videofit.core.model.VideoCodec
import com.f7developer.videofit.core.model.VideoInfo
import com.f7developer.videofit.core.service.CompressionController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PickedVideo(
    val uri: Uri,
    val name: String,
    val info: VideoInfo? = null,
    val error: String? = null,
)

data class HomeUiState(
    val picks: List<PickedVideo> = emptyList(),
    val targetBytes: Long = Presets.WHATSAPP.bytes,
    val customTargetText: String = "",
    val activePresetId: String = Presets.WHATSAPP.id,
    val codec: VideoCodec = VideoCodec.H264,
    val allowDownscale: Boolean = true,
    val measureQuality: Boolean = true,
    val probing: Boolean = false,
    val started: Boolean = false,
) {
    val firstInfo: VideoInfo? get() = picks.firstOrNull { it.info != null }?.info
    val ready: Boolean get() = picks.any { it.info != null } && !probing
    val presets: List<SizePreset> get() = Presets.ALL
}

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val videoFit = app as VideoFitApp
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val stored = videoFit.settings.settings.first()
            val presetBytes = Presets.ALL.firstOrNull { it.id == stored.defaultPresetId }?.bytes
                ?: Presets.WHATSAPP.bytes
            _state.update {
                it.copy(
                    codec = stored.defaultCodec,
                    activePresetId = stored.defaultPresetId,
                    targetBytes = if (stored.defaultTargetBytes > 0) stored.defaultTargetBytes else presetBytes,
                    allowDownscale = stored.allowDownscale,
                    measureQuality = stored.measureQuality,
                )
            }
        }
    }

    fun addUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val current = _state.value
        val accepted = uris.distinctBy { it.toString() }
            .filter { uri -> current.picks.none { it.uri.toString() == uri.toString() } }
        if (accepted.isEmpty()) return
        // Keep read access after this process dies: the foreground service
        // compresses the file later, and a photo-picker grant is not permanent
        // unless we ask for it.
        accepted.forEach { uri ->
            runCatching {
                getApplication<Application>().contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        val newPicks = accepted.map { uri ->
            PickedVideo(uri = uri, name = uri.lastPathSegment?.substringAfterLast('/') ?: "Video")
        }
        _state.update { it.copy(picks = it.picks + newPicks, probing = true) }
        viewModelScope.launch {
            newPicks.forEach { pick ->
                val result = runCatching {
                    videoFit.engine.probeVideo(pick.uri, pick.name)
                }
                _state.update { state ->
                    state.copy(
                        picks = state.picks.map { existing ->
                            if (existing.uri == pick.uri) {
                                result.fold(
                                    onSuccess = { info ->
                                        existing.copy(info = info, name = info.displayName, error = null)
                                    },
                                    onFailure = { error ->
                                        existing.copy(error = error.message ?: "Could not read this video")
                                    },
                                )
                            } else {
                                existing
                            }
                        },
                    )
                }
            }
            _state.update { it.copy(probing = false) }
        }
    }

    fun removePick(uri: Uri) {
        _state.update { state -> state.copy(picks = state.picks.filterNot { it.uri == uri }) }
    }

    fun selectPreset(preset: SizePreset) {
        _state.update {
            it.copy(
                activePresetId = preset.id,
                targetBytes = preset.bytes,
                customTargetText = "",
            )
        }
        viewModelScope.launch { videoFit.settings.setPreset(preset.id) }
    }

    fun setCustomTarget(text: String) {
        val bytes = com.f7developer.videofit.core.util.Format.parseSizeToBytes(text)
        _state.update {
            it.copy(
                customTargetText = text,
                targetBytes = bytes ?: it.targetBytes,
                activePresetId = if (bytes != null) "custom" else it.activePresetId,
            )
        }
    }

    fun setTargetMegabytes(megabytes: Int) {
        val bytes = megabytes.toLong() * 1_000_000L
        _state.update {
            it.copy(
                targetBytes = bytes,
                activePresetId = "custom",
                customTargetText = megabytes.toString(),
            )
        }
    }

    fun setCodec(codec: VideoCodec) {
        _state.update { it.copy(codec = codec) }
        viewModelScope.launch { videoFit.settings.setCodec(codec) }
    }

    fun toggleDownscale() {
        _state.update { it.copy(allowDownscale = !it.allowDownscale) }
    }

    fun toggleQualityReport() {
        _state.update { it.copy(measureQuality = !it.measureQuality) }
    }

    fun planFor(info: VideoInfo, state: HomeUiState = _state.value): EncodePlan =
        videoFit.engine.planFor(
            info = info,
            targetBytes = state.targetBytes,
            codec = state.codec,
            allowDownscale = state.allowDownscale,
            maxShortSide = null,
        )

    fun startCompression(onStarted: () -> Unit) {
        val current = _state.value
        val ready = current.picks.filter { it.info != null }
        if (ready.isEmpty()) return
        CompressionController.reset()
        CompressionController.startService(
            context = getApplication(),
            uris = ready.map { it.uri.toString() },
            targetBytes = current.targetBytes,
            codec = current.codec,
            maxShortSide = null,
            measureQuality = current.measureQuality,
            allowDownscale = current.allowDownscale,
        )
        _state.update { it.copy(started = true) }
        onStarted()
    }

    fun resetStarted() {
        _state.update { it.copy(started = false) }
    }
}
