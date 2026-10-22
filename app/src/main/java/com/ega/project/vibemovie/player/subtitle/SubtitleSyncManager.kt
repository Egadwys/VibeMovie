package com.ega.project.vibemovie.data.subtitle

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SubtitleSyncManager {

    private val _delayMs = MutableStateFlow(0L)
    val delayMs: StateFlow<Long> = _delayMs.asStateFlow()

    private val _speedMultiplier = MutableStateFlow(1.0f)
    val speedMultiplier: StateFlow<Float> = _speedMultiplier.asStateFlow()

    fun adjustDelay(stepMs: Long) {
        val current = _delayMs.value
        val updated = (current + stepMs).coerceIn(-30000L, 30000L)
        _delayMs.value = updated
    }

    fun setSpeed(speed: Float) {
        _speedMultiplier.value = speed.coerceIn(0.5f, 2.0f)
    }

    fun reset() {
        _delayMs.value = 0L
        _speedMultiplier.value = 1.0f
    }

    fun calculateRenderTime(originalMs: Long): Long {
        return ((originalMs * _speedMultiplier.value) + _delayMs.value).toLong()
    }
}
