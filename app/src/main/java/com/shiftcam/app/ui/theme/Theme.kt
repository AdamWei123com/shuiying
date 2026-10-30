package com.shiftcam.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F6B4A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8EAD2),
    secondary = Color(0xFF4B6357),
    surface = Color(0xFFF7FBF8),
    background = Color(0xFFF2F6F3),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8ED7B3),
    onPrimary = Color(0xFF003825),
    primaryContainer = Color(0xFF123B2A),
    secondary = Color(0xFFB3CCC0),
    surface = Color(0xFF121412),
    background = Color(0xFF0E100F),
)

@Composable
fun ShiftCamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
