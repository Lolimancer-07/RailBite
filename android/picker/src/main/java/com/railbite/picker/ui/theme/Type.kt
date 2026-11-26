package com.railbite.picker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.railbite.core.ui.theme.InterFamily

/**
 * Tabular numeral (`fontFeatureSettings = "tnum"`) styles for ₹ prices,
 * SLA countdowns, 4-digit customer handover PINs, and order tags.
 */
object PickerNumericType {
    val AlertHeroCoach = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        fontFeatureSettings = "tnum",
    )

    val PinDigit = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 28.sp,
        fontFeatureSettings = "tnum",
    )

    val KpiHero = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 26.sp,
        fontFeatureSettings = "tnum",
    )

    val HeroAmount = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 28.sp,
        fontFeatureSettings = "tnum",
    )

    val PriceLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontFeatureSettings = "tnum",
    )

    val PriceMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontFeatureSettings = "tnum",
    )

    val BadgeMono = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        fontFeatureSettings = "tnum",
    )

    val MetaMono = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontFeatureSettings = "tnum",
    )
}

private fun sansStyle(
    size: Int,
    weight: FontWeight,
    lineHeight: Int,
    tabular: Boolean = true,
) = TextStyle(
    fontFamily = InterFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontFeatureSettings = if (tabular) "tnum" else null,
)

fun railBitePickerTypography(): Typography = Typography(
    displaySmall = sansStyle(28, FontWeight.Bold, 34),
    headlineLarge = sansStyle(26, FontWeight.Bold, 32),
    headlineMedium = sansStyle(22, FontWeight.Bold, 28),
    headlineSmall = sansStyle(18, FontWeight.SemiBold, 24),
    titleLarge = sansStyle(20, FontWeight.Bold, 26),
    titleMedium = sansStyle(16, FontWeight.SemiBold, 22),
    titleSmall = sansStyle(14, FontWeight.SemiBold, 20),
    bodyLarge = sansStyle(16, FontWeight.Normal, 24),
    bodyMedium = sansStyle(14, FontWeight.Normal, 20),
    bodySmall = sansStyle(12, FontWeight.Normal, 16),
    labelLarge = sansStyle(14, FontWeight.SemiBold, 18),
    labelMedium = sansStyle(12, FontWeight.SemiBold, 16),
    labelSmall = sansStyle(11, FontWeight.SemiBold, 14),
)

