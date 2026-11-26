package com.railbite.picker.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Stitch Flat Design color tokens for `com.railbite.picker`.
 * Strictly flat white (#FFFFFF), slate (#F4F6F8 / #0F172A), and cyan (#0891B2) — zero gradients.
 */
object RailBitePickerPalette {
    // Surfaces & Canvas
    val Canvas = Color(0xFFF4F6F8)
    val SurfaceCard = Color(0xFFFFFFFF)
    val ImageTile = Color(0xFFF1F5F9)

    // Dark Slate Contrast Anchors (Hero Cash Card, Seat Pills, Lock-Screen Alert)
    val DarkSlate = Color(0xFF0F172A)
    val DarkSlateCell = Color(0xFF1E293B)
    val DarkSlateBorder = Color(0xFF334155)

    // Primary Cyan Accents
    val CyanPrimary = Color(0xFF0891B2)
    val CyanPressed = Color(0xFF0E7490)
    val CyanTint = Color(0xFFECFEFF)
    val CyanBorder = Color(0xFFA5F3FC)
    val CyanPinDigit = Color(0xFF38BDF8)

    // Hairline Boundaries
    val BorderSubtle = Color(0xFFE2E8F0)

    // Typography Tiers
    val TextPrimary = Color(0xFF0F172A)
    val TextSecondary = Color(0xFF64748B)
    val TextMuted = Color(0xFF94A3B8)

    // Semantic Statuses
    val SemanticGreen = Color(0xFF16A34A)
    val SemanticGreenTint = Color(0xFFF0FDF4)
    val SemanticGreenBorder = Color(0xFFBBF7D0)
    val SemanticGreenInk = Color(0xFF15803D)

    val SemanticAmber = Color(0xFFD97706)
    val SemanticAmberTint = Color(0xFFFEF3C7)
    val SemanticAmberBorder = Color(0xFFFDE68A)
    val SemanticAmberInk = Color(0xFF92400E)

    val SemanticRed = Color(0xFFDC2626)
    val SemanticRedTint = Color(0xFFFEF2F2)
    val SemanticRedBorder = Color(0xFFFECACA)
}

