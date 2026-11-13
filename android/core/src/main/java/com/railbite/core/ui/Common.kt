package com.railbite.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Green/red FOT-style veg marker. */
@Composable
fun VegDot(isVeg: Boolean, modifier: Modifier = Modifier) {
    val color = if (isVeg) Color(0xFF0E8A4B) else Color(0xFFC62828)
    Box(
        modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(Color.White)
            .padding(2.dp)
            .clip(CircleShape)
            .background(color),
    )
}

/** Simple pulsing placeholder blocks used while the menu/orders load. */
@Composable
fun LoadingSkeleton(rows: Int = 6, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            visible = !visible
            kotlinx.coroutines.delay(500)
        }
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0.35f,
        animationSpec = tween(500),
        label = "skeleton",
    )
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier
                        .size(64.dp)
                        .alpha(alpha)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.55f)
                            .height(16.dp)
                            .alpha(alpha)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth(0.3f)
                            .height(14.dp)
                            .alpha(alpha)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
            }
        }
    }
}

/** Empty-state card with a big emoji + message. */
@Composable
fun EmptyState(emoji: String, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(emoji, style = MaterialTheme.typography.headlineLarge)
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Small rounded pill used for statuses. */
@Composable
fun StatusChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun SectionSpacer() {
    Spacer(Modifier.height(12.dp))
}

/** Friendly emoji per menu item (no image assets, nothing copyrighted). */
object ProductEmoji {
    private val byName = listOf<Pair<String, String>>(
        "chai" to "☕", "coffee" to "☕", "cola" to "🥤",
        "buttermilk" to "🥛", "water" to "💧", "biscuit" to "🍪",
        "parle" to "🍪", "chips" to "🍟", "samosa" to "🥟",
        "puff" to "🥐", "sandwich" to "🥪", "biryani" to "🍛",
        "noodles" to "🍜", "frankie" to "🌯", "roll" to "🌯",
        "bonda" to "🍘", "cake" to "🍰", "thali" to "🍱",
        "paratha" to "🫓", "omelette" to "🍳", "juice" to "🧃",
    )

    private val byCategory = mapOf(
        "BEVERAGES" to "☕",
        "WATER" to "💧",
        "SNACKS" to "🍪",
        "MEALS" to "🍱",
        "BREAKFAST" to "🥐",
    )

    fun forItem(name: String, category: String): String {
        val n = name.lowercase()
        for ((needle, emoji) in byName) {
            if (n.contains(needle)) return emoji
        }
        return byCategory[category.uppercase()] ?: "🍽️"
    }
}
