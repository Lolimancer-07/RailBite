package com.railbite.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * RailBite design tokens — the single source of truth for colours, type sizes,
 * spacing, corner radii and component metrics. Screens must never hard-code
 * colours or sizes; they read these values (usually via MaterialTheme).
 */

// ---------- Colour palette (light-first) ----------
// ink #141414, background #FFFFFF, surface #F4F4F2, divider #E6E5E0,
// muted #5F6368, accent #0B6B3A (actions only), warning #F5B942 on dark.
object Palette {
    val Ink = Color(0xFF141414)
    val Background = Color(0xFFFFFFFF)
    val Surface = Color(0xFFF4F4F2)
    val Divider = Color(0xFFE6E5E0)
    val MutedText = Color(0xFF5F6368)
    val Accent = Color(0xFF0B6B3A)
    val OnAccent = Color(0xFFFFFFFF)
    val Warning = Color(0xFFF5B942)

    // Light accent tint for checked/tonal surfaces (same hue, no new colour).
    val AccentTint = Color(0xFFE4F0E9)
    val OnAccentTint = Color(0xFF0A5530)

    // Functional error (not decoration): material error red, 4.5:1 on white.
    val Error = Color(0xFFB3261E)
    val ErrorContainer = Color(0xFFFCE9E7)
    val OnErrorContainer = Color(0xFF611B16)

    // Picker alert surface is dark regardless of system theme.
    val AlertDark = Color(0xFF141414)
    val AlertDarkSurface = Color(0xFF1C1C1B)
    val AlertDarkText = Color(0xFFECECE9)

    // Dark-mode derivatives (same hues, tuned for contrast on #141414).
    val DarkBackground = Color(0xFF141414)
    val DarkSurface = Color(0xFF1C1C1B)
    val DarkInk = Color(0xFFECECE9)
    val DarkDivider = Color(0xFF2E2E2C)
    val DarkMutedText = Color(0xFFA5A59E)
    val DarkAccent = Color(0xFF57B982)
    val DarkOnAccent = Color(0xFF06240F)
    val DarkAccentTint = Color(0xFF17402B)
    val DarkOnAccentTint = Color(0xFFB9E7CC)
}

// ---------- Type sizes: 12, 14, 15, 17, 24, 34, 44. No other sizes. ----------
object Type {
    val s12 = 12.sp
    val s14 = 14.sp
    val s15 = 15.sp
    val s17 = 17.sp
    val s24 = 24.sp
    val s34 = 34.sp
    val s44 = 44.sp
}

// ---------- Spacing: 4/8 scale (8, 12, 16, 20, 24). Screen padding 20. ----------
object Space {
    val xs = 8.dp
    val s = 12.dp
    val m = 16.dp
    val l = 20.dp
    val xl = 24.dp

    /** Horizontal screen padding. */
    val screen = 20.dp
}

// ---------- Corner radii: 8 (buttons, inputs), 10–12 (cards, tiles). ----------
object Radii {
    val control = 8.dp
    val card = 10.dp
    val tile = 12.dp
}

// ---------- Component metrics ----------
object Metrics {
    /** Every tap target is at least 44 dp. */
    val tapTarget = 44.dp

    /** Primary buttons are 56 dp tall. */
    val primaryButtonHeight = 56.dp
}
