package com.prismml.grove.ui.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF9CA8FF),
    secondary = Color(0xFF7AD8FF),
    tertiary = Color(0xFFC29BFF),
    background = Color(0xFF060916),
    surface = Color(0xFF10162A),
    surfaceVariant = Color(0xFF243052),
    onSurface = Color(0xFFF3F6FF),
    onPrimary = Color(0xFF0A1020),
    outline = Color(0xFF6170A9),
    error = Color(0xFFFF8CA4),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF4B57E8),
    secondary = Color(0xFF137DB5),
    tertiary = Color(0xFF7454E2),
    background = Color(0xFFF5F7FF),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFDDE5FF),
    onSurface = Color(0xFF10162A),
    onPrimary = Color.White,
    outline = Color(0xFF7B89B6),
    error = Color(0xFFD5345F),
)

private val PrismTypography = Typography(
    headlineLarge = TextStyle(
        fontSize = 32.sp,
        lineHeight = 38.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    headlineMedium = TextStyle(
        fontSize = 26.sp,
        lineHeight = 31.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleLarge = TextStyle(
        fontSize = 20.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Medium,
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 23.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
    ),
    labelLarge = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold,
    ),
)

@Composable
fun PrismGroveTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        typography = PrismTypography,
        content = content,
    )
}
