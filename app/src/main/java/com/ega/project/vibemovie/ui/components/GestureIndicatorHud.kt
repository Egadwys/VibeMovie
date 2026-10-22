package com.ega.project.vibemovie.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ega.project.vibemovie.player.gestures.GestureType

@Composable
fun GestureIndicatorHud(
    type: GestureType,
    value: Float
) {
    GlassmorphicPanel(
        modifier = Modifier
            .width(160.dp)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (type == GestureType.BRIGHTNESS) Icons.Default.BrightnessMedium else Icons.Default.VolumeUp,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = "${(value * 100).toInt()}%",
                color = Color.White,
                fontSize = 16.sp
            )
            LinearProgressIndicator(
                progress = value,
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF00E5FF)
            )
        }
    }
}
