package com.ega.project.vibemovie.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun PlayerScreen(
    videoTitle: String,
    onBack: () -> Unit,
    onOpenSubtitleSync: () -> Unit
) {
    var isLocked by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (!isLocked) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(text = videoTitle, color = Color.White)
                IconButton(onClick = onOpenSubtitleSync) {
                    Icon(Icons.Default.Subtitles, contentDescription = "Subtitle Sync", tint = Color.White)
                }
            }

            IconButton(
                onClick = { isLocked = true },
                modifier = Modifier.align(Alignment.CenterStart).padding(16.dp)
            ) {
                Icon(Icons.Default.LockOpen, contentDescription = "Lock Screen", tint = Color.White)
            }
        } else {
            IconButton(
                onClick = { isLocked = false },
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
            ) {
                Icon(Icons.Default.Lock, contentDescription = "Unlock Screen", tint = Color.White.copy(alpha = 0.6f))
            }
        }
    }
}
