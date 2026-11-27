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
import com.railbite.core.ui.theme.Radii
import com.railbite.core.ui.theme.Space

/** Green/red FOT-style veg marker (functional food-safety semantics). */
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

/** Flat pulsing placeholder blocks used while the menu/orders load. */
@Composable
fun LoadingSkeleton(rows: Int = 6, modifier: Modifier = Modifier) {
    var pulse by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            pulse = !pulse
            kotlinx.coroutines.delay(500)
        }
    }
    val visible = pulse
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0.35f,
        animationSpec = tween(500),
        label = "skeleton",
    )
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.m)) {
        repeat(rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                Box(
                    Modifier
                        .size(64.dp)
                        .alpha(alpha)
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.55f)
                            .height(16.dp)
                            .alpha(alpha)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth(0.3f)
                            .height(14.dp)
                            .alpha(alpha)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
            }
        }
    }
}

/** Empty state: flat, centred, no decoration. */
@Composable
fun EmptyState(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
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
            .clip(MaterialTheme.shapes.small)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

@Composable
fun SectionSpacer() {
    Spacer(Modifier.height(Space.s))
}

/**
 * Flat product tile placeholder until real photos exist: a surface-tinted
 * rounded square with the item's first letter. No emoji, no gradients.
 * Size it through [modifier] (e.g. Modifier.size(56.dp) or fillMaxWidth).
 */
@Composable
fun ProductThumb(name: String, modifier: Modifier = Modifier) {
    val letter: String = name.trim().uppercase().firstOrNull()?.takeIf { it.isLetter() }?.toString() ?: "•"
    Box(
        modifier
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            letter,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
