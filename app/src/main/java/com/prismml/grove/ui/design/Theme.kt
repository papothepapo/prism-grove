package com.prismml.grove.ui.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF95A7FF),
    secondary = Color(0xFF6EDBFF),
    tertiary = Color(0xFFB091FF),
    background = Color(0xFF08101F),
    surface = Color(0xFF111B34),
    surfaceVariant = Color(0xFF192548),
    onSurface = Color(0xFFE7ECFF),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF4D4EF7),
    secondary = Color(0xFF0A8BCE),
    tertiary = Color(0xFF7859FF),
    background = Color(0xFFF6F8FF),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE8EDFF),
    onSurface = Color(0xFF111B34),
)

@Composable
fun PrismGroveTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        content = content,
    )
}
