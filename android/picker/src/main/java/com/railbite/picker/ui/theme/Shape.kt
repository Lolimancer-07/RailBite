package com.railbite.picker.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Corner radii aligned to Stitch Picker Ops specifications (16.dp cards, 24.dp sheets). */
object PickerRadii {
    val Control = 8.dp
    val Button = 12.dp
    val ChipBox = 14.dp
    val Card = 16.dp
    val ImageTile = 18.dp
    val BottomSheet = 24.dp
    val Pill = 999.dp
}

/** Shared spacing & touch-ergonomics constants for single-handed train ops. */
object PickerMetrics {
    val ScreenPadding = 16.dp
    val CardPadding = 12.dp
    val CardGap = 12.dp
    val SectionGap = 20.dp
    val TouchTargetMin = 44.dp
    val ChecklistCheckbox = 48.dp
    val PrimaryButtonHeight = 56.dp
}

val RailBitePickerShapes = Shapes(
    extraSmall = RoundedCornerShape(PickerRadii.Control),
    small = RoundedCornerShape(PickerRadii.Button),
    medium = RoundedCornerShape(PickerRadii.Card),
    large = RoundedCornerShape(PickerRadii.ImageTile),
    extraLarge = RoundedCornerShape(PickerRadii.BottomSheet),
)

val PickerBottomSheetShape = RoundedCornerShape(
    topStart = PickerRadii.BottomSheet,
    topEnd = PickerRadii.BottomSheet,
)

