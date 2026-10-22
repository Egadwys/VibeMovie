package com.ega.project.vibemovie.player.gestures

enum class GestureType {
    NONE,
    BRIGHTNESS,
    VOLUME,
    SEEK
}

data class GestureState(
    val type: GestureType = GestureType.NONE,
    val value: Float = 0f,
    val showHud: Boolean = false
)
