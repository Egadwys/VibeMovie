package com.ega.project.vibemovie.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ega.project.vibemovie.data.model.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pengaturan VibeMovie") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Tema Antarmuka", style = MaterialTheme.typography.titleMedium)
            
            SegmentedButton(
                themeMode = currentTheme,
                onThemeSelect = onThemeChange
            )
            
            Divider()
            
            Text("Subtitle Cloud Defaults", style = MaterialTheme.typography.titleMedium)
            Text("Bahasa Terfilter: Bahasa Indonesia (id), English (en)", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun SegmentedButton(
    themeMode: ThemeMode,
    onThemeSelect: (ThemeMode) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = themeMode == ThemeMode.LIGHT,
            onClick = { onThemeSelect(ThemeMode.LIGHT) },
            label = { Text("Terang") }
        )
        FilterChip(
            selected = themeMode == ThemeMode.DARK,
            onClick = { onThemeSelect(ThemeMode.DARK) },
            label = { Text("Gelap") }
        )
        FilterChip(
            selected = themeMode == ThemeMode.SYSTEM,
            onClick = { onThemeSelect(ThemeMode.SYSTEM) },
            label = { Text("Otomatis (Sistem)") }
        )
    }
}
