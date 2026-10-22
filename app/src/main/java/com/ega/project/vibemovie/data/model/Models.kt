package com.ega.project.vibemovie.data.model

import android.net.Uri

data class VideoItem(
    val id: Long,
    val title: String,
    val path: String,
    val uri: Uri,
    val duration: Long,
    val size: Long,
    val resolution: String = "1080p",
    val isHdr: Boolean = false,
    val codec: String = "H.265/HEVC"
)

data class SubtitleItem(
    val id: String,
    val language: String,
    val languageName: String,
    val sourceName: String,
    val fileUri: Uri? = null,
    val isCloud: Boolean = false
)

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}

data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val volume: Float = 1.0f,
    val brightness: Float = 0.8f,
    val isLocked: Boolean = false,
    val subtitleDelayMs: Long = 0L,
    val subtitleSpeed: Float = 1.0f
)
