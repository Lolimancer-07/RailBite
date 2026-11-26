package com.railbite.picker.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val PickerLightColorScheme = lightColorScheme(
    primary = RailBitePickerPalette.CyanPrimary,
    onPrimary = Color.White,
    primaryContainer = RailBitePickerPalette.CyanTint,
    onPrimaryContainer = RailBitePickerPalette.CyanPressed,
    secondary = RailBitePickerPalette.DarkSlate,
    onSecondary = Color.White,
    secondaryContainer = RailBitePickerPalette.ImageTile,
    onSecondaryContainer = RailBitePickerPalette.TextPrimary,
    tertiary = RailBitePickerPalette.SemanticGreen,
    onTertiary = Color.White,
    tertiaryContainer = RailBitePickerPalette.SemanticGreenTint,
    onTertiaryContainer = RailBitePickerPalette.SemanticGreenInk,
    background = RailBitePickerPalette.Canvas,
    onBackground = RailBitePickerPalette.TextPrimary,
    surface = RailBitePickerPalette.SurfaceCard,
    onSurface = RailBitePickerPalette.TextPrimary,
    surfaceVariant = RailBitePickerPalette.ImageTile,
    onSurfaceVariant = RailBitePickerPalette.TextSecondary,
    outline = RailBitePickerPalette.BorderSubtle,
    outlineVariant = RailBitePickerPalette.BorderSubtle,
    error = RailBitePickerPalette.SemanticRed,
    onError = Color.White,
    errorContainer = RailBitePickerPalette.SemanticRedTint,
    onErrorContainer = RailBitePickerPalette.SemanticRed,
)

private val PickerDarkColorScheme = darkColorScheme(
    primary = RailBitePickerPalette.CyanPrimary,
    onPrimary = Color.White,
    primaryContainer = RailBitePickerPalette.DarkSlateCell,
    onPrimaryContainer = RailBitePickerPalette.CyanPinDigit,
    secondary = RailBitePickerPalette.CyanPinDigit,
    onSecondary = RailBitePickerPalette.DarkSlate,
    tertiary = RailBitePickerPalette.SemanticGreen,
    onTertiary = Color.White,
    background = RailBitePickerPalette.DarkSlate,
    onBackground = Color.White,
    surface = RailBitePickerPalette.DarkSlateCell,
    onSurface = Color.White,
    surfaceVariant = RailBitePickerPalette.DarkSlateCell,
    onSurfaceVariant = RailBitePickerPalette.TextMuted,
    outline = RailBitePickerPalette.DarkSlateBorder,
    outlineVariant = RailBitePickerPalette.DarkSlateBorder,
    error = RailBitePickerPalette.SemanticRed,
    onError = Color.White,
    errorContainer = Color(0xFF450A0A),
    onErrorContainer = RailBitePickerPalette.SemanticRedBorder,
)

/** Standard flat 1.dp `#E2E8F0` border replacing elevation drop shadows. */
val PickerCardBorder: BorderStroke
    get() = BorderStroke(1.dp, RailBitePickerPalette.BorderSubtle)

/** Selected / active cyan 1.5.dp flat border. */
val PickerSelectedBorder: BorderStroke
    get() = BorderStroke(1.5.dp, RailBitePickerPalette.CyanPrimary)

/**
 * Stitch Picker Material 3 Theme.
 * Light ops surfaces use strictly flat `#FFFFFF` / `#F4F6F8` / `#0F172A` / `#0891B2`;
 * [darkTheme] = true is reserved for the full-screen `#0F172A` lock-screen alert takeover.
 */
@Composable
fun RailBitePickerTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) PickerDarkColorScheme else PickerLightColorScheme,
        typography = railBitePickerTypography(),
        shapes = RailBitePickerShapes,
        content = content,
    )
}

