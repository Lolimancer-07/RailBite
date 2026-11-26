package com.railbite.customer.ui.components

import androidx.annotation.DrawableRes
import com.railbite.core.model.MenuItem
import com.railbite.customer.R

/** Offline food imagery & pantry metadata mapped deterministically by item name / category. */
object FoodAssets {

    data class ItemMeta(
        @DrawableRes val imageRes: Int,
        val subtitle: String,
        val packTag: String?,
        val highlights: List<String>,
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

    fun metaFor(item: MenuItem): ItemMeta {
        val lower = item.name.lowercase()
        val imageRes = drawableFor(item.name, item.category)
        val subtitle = when {
            "chai" in lower -> "Hot spiced tea in sealed cup"
            "coffee" in lower -> "Hot brewed pantry coffee"
            "cola" in lower -> "Chilled canned beverage"
            "buttermilk" in lower -> "Spiced chilled chaas pack"
            "rail neer" in lower -> "IRCTC packaged drinking water"
            "water" in lower -> "Sealed chilled bottle"
            "parle" in lower -> "Sealed crisp glucose pack"
            "chips" in lower -> "Crispy salted potato chips"
            "samosa" in lower -> "2 crisp samosas with chutney"
            "puff" in lower -> "Fresh baked flaky pastry"
            "bonda" in lower -> "2 warm spiced potato bondas"
            "sandwich" in lower -> "Grilled chutney veg sandwich"
            "biryani" in lower -> "Aromatic rice pack with raita"
            "noodle" in lower -> "Instant hot noodles + fork"
            "frankie" in lower -> "Warm spiced roll wrap"
            "cake" in lower -> "Sealed soft bakery slice"
            else -> "${item.category} • Pantry fresh"
        }
        val packTag = when {
            "1l" in lower -> "1 Litre"
            "500ml" in lower -> "500 ml"
            "2 pcs" in lower -> "2 Pcs"
            "chai" in lower || "coffee" in lower -> "Hot Cup"
            "can" in lower || "cola" in lower -> "Chilled"
            "biryani" in lower -> "Hot Pack"
            else -> if (item.isVeg) "Pure Veg" else "Non-Veg"
        }
        val highlights = buildList {
            add("Delivered straight to your seat in ~${item.prepMinutes.coerceAtLeast(5)}–${(item.prepMinutes + 5).coerceAtLeast(10)} mins")
            add("Official IRCTC fixed MRP • Zero platform markup")
            if (item.isVeg) add("Prepared & sealed in FSSAI certified pantry car")
            else add("Hygienically packed • Freshly warmed for berth handover")
        }
        return ItemMeta(
            imageRes = imageRes,
            subtitle = subtitle,
            packTag = packTag,
            highlights = highlights,
        )
    }
}
