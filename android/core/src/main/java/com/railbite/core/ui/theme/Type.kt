package com.railbite.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.railbite.core.R

/**
 * Inter (400/500/600/700) served from a single variable font file.
 * Only the seven sanctioned sizes exist: 12, 14, 15, 17, 24, 34, 44.
 * Every Material3 text role maps onto one of those sizes.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val InterFamily = FontFamily(
    Font(
        resId = R.font.inter_var,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        resId = R.font.inter_var,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        resId = R.font.inter_var,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
    Font(
        resId = R.font.inter_var,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
)

private fun style(size: Int, weight: FontWeight, lineHeight: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = InterFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
)

/**
 * Mapping (sizes in dp-pt): 12 / 14 / 15 / 17 / 24 / 34 / 44.
 * display + headline → 700 weight, titles → 600, labels → 500, body → 400.
 */
fun railBiteTypography() = Typography(
    // 44 — hero numerals (delivery code, splash headline)
    displayLarge = style(44, FontWeight.Bold, 48),
    displayMedium = style(44, FontWeight.Bold, 48),
    // 34 — page headlines
    displaySmall = style(34, FontWeight.Bold, 40),
    headlineLarge = style(34, FontWeight.Bold, 40),
    headlineMedium = style(34, FontWeight.Bold, 40),
    // 24 — section headlines / delivery code compact
    headlineSmall = style(24, FontWeight.Bold, 30),
    titleLarge = style(24, FontWeight.SemiBold, 30),
    // 17 — card titles / large body
    titleMedium = style(17, FontWeight.SemiBold, 22),
    bodyLarge = style(17, FontWeight.Normal, 24),
    // 15 — body / list rows
    titleSmall = style(15, FontWeight.SemiBold, 20),
    bodyMedium = style(15, FontWeight.Normal, 21),
    labelLarge = style(15, FontWeight.Medium, 20),
    // 14 — secondary body / input text
    bodySmall = style(14, FontWeight.Normal, 19),
    labelMedium = style(14, FontWeight.Medium, 18),
    // 12 — captions / metadata
    labelSmall = style(12, FontWeight.Medium, 16),
)
