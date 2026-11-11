package com.f7developer.videofit.core.service

import android.content.Context
import com.f7developer.videofit.core.engine.CompressionResult
import com.f7developer.videofit.core.model.VideoCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/** What the UI needs to know about a running batch. */
sealed interface CompressionState {
    data object Idle : CompressionState

    data class Running(
        val index: Int,
        val total: Int,
        val name: String,
        val progress: Float,
        val overall: Float,
        val etaSeconds: Long?,
        val speedLabel: String?,
        val thermalWarning: Boolean,
    ) : CompressionState

    data class Finished(
        val results: List<CompressionResult>,
        val failures: List<String>,
        val cancelled: Boolean,
    ) : CompressionState

    data class Failed(val message: String) : CompressionState
}

/**
 * Process-wide bridge between [CompressionService] and the composables.
 * The service owns the work; the UI only reads state and writes a cancel flag.
 */
object CompressionController {

    private val _state = MutableStateFlow<CompressionState>(CompressionState.Idle)
    val state: StateFlow<CompressionState> = _state.asStateFlow()

    private val cancelFlag = AtomicBoolean(false)
    private val startTime = AtomicLongHolder()

    fun publish(state: CompressionState) {
        _state.value = state
    }

    fun reset() {
        cancelFlag.set(false)
        _state.value = CompressionState.Idle
    }

    fun cancel() {
        cancelFlag.set(true)
    }

    fun isCancelled(): Boolean = cancelFlag.get()

    fun startClock() {
        cancelFlag.set(false)
        startTime.set(System.currentTimeMillis())
    }

    fun elapsedSeconds(): Long =
        ((System.currentTimeMillis() - startTime.get()) / 1000).coerceAtLeast(0)

    fun startService(
        context: Context,
        uris: List<String>,
        targetBytes: Long,
        codec: VideoCodec,
        maxShortSide: Int?,
        measureQuality: Boolean,
        allowDownscale: Boolean,
    ) {
        CompressionService.start(
            context = context,
            uris = uris,
            targetBytes = targetBytes,
            codec = codec,
            maxShortSide = maxShortSide,
            measureQuality = measureQuality,
            allowDownscale = allowDownscale,
        )
    }

    private class AtomicLongHolder {
        @Volatile
        private var value = System.currentTimeMillis()
        fun set(newValue: Long) {
            value = newValue
        }
        fun get(): Long = value
    }
}
