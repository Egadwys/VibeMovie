package com.ega.project.vibemovie.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SubtitleSyncDialog(
    delayMs: Long,
    speedMultiplier: Float,
    onAdjustDelay: (Long) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pengaturan Sinkronisasi Subtitle", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Subtitle Delay (Presisi 50 ms):", fontSize = 14.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = { onAdjustDelay(-50L) }) { Text("-50 ms") }
                    Text("${delayMs} ms", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Button(onClick = { onAdjustDelay(50L) }) { Text("+50 ms") }
                }

                Divider()

                Text("Kecepatan Subtitle (Speed Scaling):", fontSize = 14.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("0.5x", fontSize = 12.sp)
                    Slider(
                        value = speedMultiplier,
                        onValueChange = { onSetSpeed(it) },
                        valueRange = 0.5f..2.0f,
                        steps = 15,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                    Text("2.0x", fontSize = 12.sp)
                }
                Text("Kecepatan Saat Ini: ${String.format("%.2fx", speedMultiplier)}", fontWeight = FontWeight.SemiBold)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Selesai") }
        },
        dismissButton = {
            OutlinedButton(onClick = onReset) { Text("Reset (0 ms, 1.0x)") }
        }
    )
}
