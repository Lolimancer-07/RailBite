package com.railbite.customer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.railbite.customer.ui.theme.RailBiteNumericType
import com.railbite.customer.ui.theme.RailBitePalette
import com.railbite.customer.ui.theme.RailBiteRadii

/**
 * Stitch Screen 5 High-Contrast 4-Digit Handover PIN Card:
 * `#0F172A` dark card with 4 individual `#1E293B` digit cells (`8.dp` radius, tabular monospace).
 */
@Composable
fun HandoverPinCard(
    code: String,
    subtitle: String = "Share this confirmation code with your delivery runner at your berth.",
    modifier: Modifier = Modifier,
) {
    val digits = code.trim().take(4).padEnd(4, '•').toList()

    Card(
        shape = RoundedCornerShape(RailBiteRadii.Card),
        colors = CardDefaults.cardColors(containerColor = RailBitePalette.DarkSlate),
        border = BorderStroke(1.dp, RailBitePalette.DarkSlateBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = RailBitePalette.CyanPinDigit,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = "SAFE HANDOVER PIN",
                    style = MaterialTheme.typography.labelMedium,
                    color = RailBitePalette.CyanPinDigit,
                )
            }

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                digits.forEach { ch ->
                    Surface(
                        shape = RoundedCornerShape(RailBiteRadii.Control),
                        color = RailBitePalette.DarkSlateCell,
                        border = BorderStroke(1.dp, RailBitePalette.DarkSlateBorder),
                        modifier = Modifier.size(width = 52.dp, height = 58.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = ch.toString(),
                                style = RailBiteNumericType.PinDigit,
                                color = RailBitePalette.CyanPinDigit,
                            )
                        }
                    }
                }
            }

            Text(
                text = "Protects against wrong-berth handovers",
                style = MaterialTheme.typography.labelSmall,
                color = RailBitePalette.TextMuted,
            )
        }
    }
}

