package com.railbite.picker.ui.components

import androidx.annotation.DrawableRes
import com.railbite.picker.R

/**
 * Offline food category drawables and pantry rack tags for Picker Ops cards.
 * Guarantees crisp thumbnails over offline pantry Wi-Fi with zero broken network images.
 */
object PickerFoodAssets {

    data class RackMeta(
        val groupKey: String,
        val groupTitle: String,
        val rackZone: String,
        val storageTag: String,
    )

    @DrawableRes
    fun drawableFor(name: String, category: String = ""): Int {
        val key = "${name.lowercase()} ${category.lowercase()}"
        return when {
            "chai" in key || "tea" in key -> R.drawable.food_chai
            "coffee" in key -> R.drawable.food_coffee
            "cola" in key || "buttermilk" in key || "chaas" in key || "lassi" in key || "juice" in key -> R.drawable.food_cola
            "water" in key || "rail neer" in key -> R.drawable.food_water
            "parle" in key || "biscuit" in key || "cake" in key || "cookie" in key -> R.drawable.food_biscuits
            "samosa" in key || "puff" in key || "bonda" in key || "pakora" in key -> R.drawable.food_samosa
            "chips" in key || "namkeen" in key || "snack" in key -> R.drawable.food_snacks
            "sandwich" in key || "toast" in key -> R.drawable.food_sandwich
            "biryani" in key || "thali" in key || "rice" in key || "pulao" in key -> R.drawable.food_biryani
            "noodle" in key || "maggi" in key -> R.drawable.food_noodles
            "frankie" in key || "roll" in key || "wrap" in key -> R.drawable.food_frankie
            "poha" in key || "upma" in key || "idli" in key || "vada" in key || "breakfast" in key -> R.drawable.food_breakfast
            "beverage" in key || "drink" in key -> R.drawable.food_chai
            "meal" in key -> R.drawable.food_biryani
            else -> R.drawable.food_snacks
        }
    }

    fun rackMetaFor(name: String, category: String?): RackMeta {
        val upperCat = category.orEmpty().trim().uppercase()
        val lowerName = name.lowercase()
        val resolvedCat = when {
            upperCat.isNotEmpty() -> upperCat
            "chai" in lowerName || "coffee" in lowerName || "cola" in lowerName || "buttermilk" in lowerName -> "BEVERAGES"
            "water" in lowerName || "rail neer" in lowerName -> "WATER"
            "biryani" in lowerName || "sandwich" in lowerName || "noodle" in lowerName || "frankie" in lowerName -> "MEALS"
            "bonda" in lowerName || "cake" in lowerName -> "BREAKFAST"
            else -> "SNACKS"
        }
        return when (resolvedCat) {
            "BEVERAGES" -> RackMeta(
                groupKey = "BEVERAGES",
                groupTitle = "HOT FLASK & BEVERAGES",
                rackZone = "Zone P-1",
                storageTag = if ("cola" in lowerName || "buttermilk" in lowerName) "Chiller Unit A" else "Flask Unit 1",
            )
            "WATER" -> RackMeta(
                groupKey = "WATER",
                groupTitle = "CHILLER & WATER",
                rackZone = "Chiller C-1",
                storageTag = "Chiller Unit A",
            )
            "MEALS" -> RackMeta(
                groupKey = "MEALS",
                groupTitle = "PACKED MEALS",
                rackZone = "Hot Case B-2",
                storageTag = "Rack B2 • Shelf 1",
            )
            "BREAKFAST" -> RackMeta(
                groupKey = "BREAKFAST",
                groupTitle = "BREAKFAST & BAKERY",
                rackZone = "Warm Tray A-2",
                storageTag = "Hot Case 1",
            )
            else -> RackMeta(
                groupKey = "SNACKS",
                groupTitle = "PACKED SNACKS",
                rackZone = "Dry Rack S-1",
                storageTag = if ("samosa" in lowerName || "puff" in lowerName) "Hot Case 2" else "Dry Rack S1",
            )
        }
    }
}

