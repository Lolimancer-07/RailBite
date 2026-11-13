package com.railbite.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// RailBite palette — fresh green + amber accent, light and bright.
val Green600 = Color(0xFF0E8A4B)
val Green700 = Color(0xFF0B6E3C)
val Green50 = Color(0xFFE7F6EC)
val Amber = Color(0xFFF5A623)
val Ink = Color(0xFF1C2A21)
val CloudBg = Color(0xFFF6F8F7)
val AlertRed = Color(0xFFD32F2F)
val AlertOrange = Color(0xFFFF6D00)
val Muted = Color(0xFF6B7A70)

private val LightColors = lightColorScheme(
    primary = Green600,
    onPrimary = Color.White,
    primaryContainer = Green50,
    onPrimaryContainer = Green700,
    secondary = Color(0xFF1A6B8F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FA),
    onSecondaryContainer = Color(0xFF114E69),
    tertiary = Amber,
    onTertiary = Color(0xFF3D2A00),
    tertiaryContainer = Color(0xFFFFF0D2),
    onTertiaryContainer = Color(0xFF4A3300),
    background = CloudBg,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEDF1EE),
    onSurfaceVariant = Muted,
    outline = Color(0xFFD6DCD8),
    error = AlertRed,
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6FD39A),
    onPrimary = Color(0xFF00391D),
    primaryContainer = Green700,
    onPrimaryContainer = Color(0xFFC8F0D5),
    background = Color(0xFF111613),
    onBackground = Color(0xFFE2E7E3),
    surface = Color(0xFF191F1B),
    onSurface = Color(0xFFE2E7E3),
)

/** Light-first theme (the demo is used in daylight on train platforms); respects system dark. */
@Composable
fun RailBiteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = railBiteTypography(),
        content = content,
    )
}
