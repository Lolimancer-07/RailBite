package com.railbite.customer.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Corner radii aligned to Stitch Design #2 specifications. */
object RailBiteRadii {
    val Control = 8.dp
    val Button = 12.dp
    val Card = 16.dp
    val ImageTile = 18.dp
    val BottomSheet = 24.dp
    val Pill = 999.dp
}

/** Shared spacing & touch-ergonomics constants (16 dp screen padding, ≥44 dp touch targets). */
object RailBiteMetrics {
    val ScreenPadding = 16.dp
    val CardPadding = 12.dp
    val CardGap = 12.dp
    val SectionGap = 20.dp
    val TouchTargetMin = 44.dp
    val PrimaryButtonHeight = 52.dp
}

val RailBiteCustomerShapes = Shapes(
    extraSmall = RoundedCornerShape(RailBiteRadii.Control),
    small = RoundedCornerShape(RailBiteRadii.Button),
    medium = RoundedCornerShape(RailBiteRadii.Card),
    large = RoundedCornerShape(RailBiteRadii.ImageTile),
    extraLarge = RoundedCornerShape(RailBiteRadii.BottomSheet),
)

val RailBiteBottomSheetShape = RoundedCornerShape(
    topStart = RailBiteRadii.BottomSheet,
    topEnd = RailBiteRadii.BottomSheet,
)

