package com.railbite.customer.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val CustomerLightColorScheme = lightColorScheme(
    primary = RailBitePalette.CyanPrimary,
    onPrimary = Color.White,
    primaryContainer = RailBitePalette.CyanTint,
    onPrimaryContainer = RailBitePalette.CyanPressed,
    secondary = RailBitePalette.DarkSlate,
    onSecondary = Color.White,
    secondaryContainer = RailBitePalette.ImageTile,
    onSecondaryContainer = RailBitePalette.TextPrimary,
    tertiary = RailBitePalette.SemanticGreen,
    onTertiary = Color.White,
    tertiaryContainer = RailBitePalette.SemanticGreenTint,
    onTertiaryContainer = RailBitePalette.SemanticGreen,
    background = RailBitePalette.Canvas,
    onBackground = RailBitePalette.TextPrimary,
    surface = RailBitePalette.SurfaceCard,
    onSurface = RailBitePalette.TextPrimary,
    surfaceVariant = RailBitePalette.ImageTile,
    onSurfaceVariant = RailBitePalette.TextSecondary,
    outline = RailBitePalette.BorderSubtle,
    outlineVariant = RailBitePalette.BorderSubtle,
    error = RailBitePalette.SemanticRed,
    onError = Color.White,
    errorContainer = RailBitePalette.SemanticRedTint,
    onErrorContainer = RailBitePalette.SemanticRed,
)

/** Standard flat 1.dp subtle border replacing Material elevation shadows. */
val RailBiteCardBorder: BorderStroke
    get() = BorderStroke(1.dp, RailBitePalette.BorderSubtle)

/** Selected/active flat 1.dp cyan border. */
val RailBiteSelectedBorder: BorderStroke
    get() = BorderStroke(1.5.dp, RailBitePalette.CyanPrimary)

/** Flat daylight-first theme for `com.railbite.customer`. Zero gradients or heavy shadows. */
@Composable
fun RailBiteCustomerTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = CustomerLightColorScheme,
        typography = railBiteCustomerTypography(),
        shapes = RailBiteCustomerShapes,
        content = content,
    )
}

