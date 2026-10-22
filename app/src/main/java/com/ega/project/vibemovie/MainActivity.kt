package com.ega.project.vibemovie

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.ega.project.vibemovie.data.model.ThemeMode
import com.ega.project.vibemovie.data.model.VideoItem
import com.ega.project.vibemovie.ui.screens.GalleryScreen
import com.ega.project.vibemovie.ui.screens.PlayerScreen
import com.ega.project.vibemovie.ui.screens.SettingsScreen
import com.ega.project.vibemovie.ui.theme.VibeMovieTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var currentTheme by remember { mutableStateOf(ThemeMode.SYSTEM) }
            var currentScreen by remember { mutableStateOf("gallery") }
            var selectedVideo by remember { mutableStateOf<VideoItem?>(null) }

            val dummyVideos = listOf(
                VideoItem(
                    id = 1,
                    title = "Sample_4K_HDR_Movie.mkv",
                    path = "/storage/emulated/0/Movies/Sample.mkv",
                    uri = android.net.Uri.EMPTY,
                    duration = 7200000L,
                    size = 4500000000L,
                    resolution = "4K UHD",
                    isHdr = true
                )
            )

            VibeMovieTheme(themeMode = currentTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    when (currentScreen) {
                        "gallery" -> GalleryScreen(
                            videos = dummyVideos,
                            onVideoSelect = { video ->
                                selectedVideo = video
                                currentScreen = "player"
                            },
                            onOpenSettings = { currentScreen = "settings" }
                        )
                        "player" -> PlayerScreen(
                            videoTitle = selectedVideo?.title ?: "Playing Video",
                            onBack = { currentScreen = "gallery" },
                            onOpenSubtitleSync = {}
                        )
                        "settings" -> SettingsScreen(
                            currentTheme = currentTheme,
                            onThemeChange = { currentTheme = it },
                            onBack = { currentScreen = "gallery" }
                        )
                    }
                }
            }
        }
    }
}
