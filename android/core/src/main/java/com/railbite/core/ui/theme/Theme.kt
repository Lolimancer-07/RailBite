package com.railbite.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Palette.Accent,
    onPrimary = Palette.OnAccent,
    primaryContainer = Palette.AccentTint,
    onPrimaryContainer = Palette.OnAccentTint,
    // One accent hue: secondary mirrors the accent so M3 components never
    // introduce a second action colour.
    secondary = Palette.Accent,
    onSecondary = Palette.OnAccent,
    secondaryContainer = Palette.AccentTint,
    onSecondaryContainer = Palette.OnAccentTint,
    tertiary = Palette.Warning,
    onTertiary = Color(0xFF231A00),
    tertiaryContainer = Color(0xFFFFF0D2),
    onTertiaryContainer = Color(0xFF4A3300),
    background = Palette.Background,
    onBackground = Palette.Ink,
    surface = Palette.Surface,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.Surface,
    onSurfaceVariant = Palette.MutedText,
    outline = Palette.Divider,
    outlineVariant = Palette.Divider,
    error = Palette.Error,
    onError = Color.White,
    errorContainer = Palette.ErrorContainer,
    onErrorContainer = Palette.OnErrorContainer,
)

private val DarkColors = darkColorScheme(
    primary = Palette.DarkAccent,
    onPrimary = Palette.DarkOnAccent,
    primaryContainer = Palette.DarkAccentTint,
    onPrimaryContainer = Palette.DarkOnAccentTint,
    secondary = Palette.DarkAccent,
    onSecondary = Palette.DarkOnAccent,
    secondaryContainer = Color(0xFF1C4A5F),
    onSecondaryContainer = Color(0xFFBFE3F2),
    tertiary = Palette.Warning,
    onTertiary = Color(0xFF231A00),
    tertiaryContainer = Color(0xFF4A3A10),
    onTertiaryContainer = Color(0xFFF5E3B8),
    background = Palette.DarkBackground,
    onBackground = Palette.DarkInk,
    surface = Palette.DarkSurface,
    onSurface = Palette.DarkInk,
    surfaceVariant = Palette.DarkSurface,
    onSurfaceVariant = Palette.DarkMutedText,
    outline = Palette.DarkDivider,
    outlineVariant = Palette.DarkDivider,
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)

/** Radii: 8 dp controls (buttons, inputs), 10 dp cards, 12 dp tiles. */
private val RailBiteShapes = Shapes(
    extraSmall = RoundedCornerShape(Radii.control),
    small = RoundedCornerShape(Radii.control),
    medium = RoundedCornerShape(Radii.card),
    large = RoundedCornerShape(Radii.tile),
    extraLarge = RoundedCornerShape(Radii.tile),
)

/** Light-first theme (used in daylight on train platforms); respects system dark. */
@Composable
fun RailBiteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = railBiteTypography(),
        shapes = RailBiteShapes,
        content = content,
    )
}
