package com.railbite.customer.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.railbite.core.util.Statuses
import com.railbite.customer.ui.theme.RailBiteCardBorder
import com.railbite.customer.ui.theme.RailBitePalette
import com.railbite.customer.ui.theme.RailBiteRadii

private val TRACKING_STEPS = listOf(
    "Placed",
    "Accepted",
    "Packing",
    "On the Way",
    "Delivered",
)

/**
 * Stitch Screen 5 horizontal 5-step Live Order Timeline:
 * Placed -> Accepted -> Packing -> On the Way -> Delivered.
 * Semantic green `#16A34A` for completed steps, Cyan `#0891B2` for active step, `#E2E8F0` upcoming.
 */
@Composable
fun OrderProgressTimeline(
    status: String,
    etaMin: Int?,
    coach: String?,
    berth: Int?,
    relayed: Boolean,
    modifier: Modifier = Modifier,
) {
    val activeStep = Statuses.timelineStep(status)
    val failed = Statuses.isFailed(status)
    val delivered = status == Statuses.DELIVERED

    Card(
        shape = RoundedCornerShape(RailBiteRadii.Card),
        colors = CardDefaults.cardColors(containerColor = RailBitePalette.SurfaceCard),
        border = RailBiteCardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Top ETA / Status headline
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Surface(
                    shape = RoundedCornerShape(RailBiteRadii.Button),
                    color = when {
                        failed -> RailBitePalette.SemanticRedTint
                        delivered -> RailBitePalette.SemanticGreenTint
                        else -> RailBitePalette.CyanTint
                    },
                    border = BorderStroke(
                        1.dp,
                        when {
                            failed -> RailBitePalette.SemanticRedBorder
                            delivered -> RailBitePalette.SemanticGreenBorder
                            else -> RailBitePalette.CyanBorder
                        },
                    ),
                    modifier = Modifier.size(42.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = when {
                                failed -> RailBitePalette.SemanticRed
                                delivered -> RailBitePalette.SemanticGreen
                                else -> RailBitePalette.CyanPrimary
                            },
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            failed -> "ORDER STATUS"
                            delivered -> "BERTH HANDOVER COMPLETE"
                            relayed -> "BLUETOOTH MESH ACTIVE"
                            else -> "ESTIMATED BERTH ARRIVAL"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = RailBitePalette.TextSecondary,
                    )
                    Text(
                        text = when {
                            status == Statuses.CANCELLED -> "Order Cancelled"
                            status == Statuses.EXPIRED -> "Order Expired"
                            delivered -> "Delivered to Seat"
                            relayed -> Statuses.label(status)
                            else -> "~${etaMin ?: 10} mins away"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (failed) RailBitePalette.SemanticRed else RailBitePalette.TextPrimary,
                    )
                }

                if (!coach.isNullOrBlank() && berth != null && berth > 0) {
                    Surface(
                        shape = RoundedCornerShape(RailBiteRadii.Pill),
                        color = RailBitePalette.Canvas,
                        border = RailBiteCardBorder,
                    ) {
                        Text(
                            text = "$coach • $berth",
                            style = MaterialTheme.typography.labelMedium,
                            color = RailBitePalette.TextPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            if (!failed) {
                // 5-node horizontal progress bar
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TRACKING_STEPS.forEachIndexed { idx, label ->
                        val completed = activeStep > idx || (delivered && idx == 4)
                        val current = activeStep == idx && !delivered

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                // Connector segments behind dot
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(3.dp)
                                            .background(
                                                when {
                                                    idx == 0 -> Color.Transparent
                                                    activeStep >= idx -> RailBitePalette.SemanticGreen
                                                    else -> RailBitePalette.BorderSubtle
                                                },
                                            ),
                                    )
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(3.dp)
                                            .background(
                                                when {
                                                    idx == TRACKING_STEPS.lastIndex -> Color.Transparent
                                                    activeStep > idx -> RailBitePalette.SemanticGreen
                                                    else -> RailBitePalette.BorderSubtle
                                                },
                                            ),
                                    )
                                }

                                val circleBg = when {
                                    completed -> RailBitePalette.SemanticGreen
                                    current -> RailBitePalette.CyanPrimary
                                    else -> RailBitePalette.SurfaceCard
                                }
                                val circleBorder = when {
                                    completed -> RailBitePalette.SemanticGreen
                                    current -> RailBitePalette.CyanPrimary
                                    else -> RailBitePalette.BorderSubtle
                                }

                                Surface(
                                    shape = CircleShape,
                                    color = circleBg,
                                    border = BorderStroke(if (current) 2.dp else 1.5.dp, circleBorder),
                                    modifier = Modifier.size(30.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        when {
                                            completed -> Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            idx == 2 -> Icon(
                                                imageVector = Icons.Filled.Inventory2,
                                                contentDescription = null,
                                                tint = if (current) Color.White else RailBitePalette.TextMuted,
                                                modifier = Modifier.size(14.dp),
                                            )
                                            idx == 3 -> Icon(
                                                imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                                                contentDescription = null,
                                                tint = if (current) Color.White else RailBitePalette.TextMuted,
                                                modifier = Modifier.size(14.dp),
                                            )
                                            idx == 4 -> Icon(
                                                imageVector = Icons.Filled.Flag,
                                                contentDescription = null,
                                                tint = if (current) Color.White else RailBitePalette.TextMuted,
                                                modifier = Modifier.size(14.dp),
                                            )
                                            else -> Text(
                                                text = "${idx + 1}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (current) Color.White else RailBitePalette.TextMuted,
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(6.dp))

                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = when {
                                    completed -> RailBitePalette.SemanticGreen
                                    current -> RailBitePalette.CyanPrimary
                                    else -> RailBitePalette.TextMuted
                                },
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )

                            if (current) {
                                Spacer(Modifier.height(3.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(RailBiteRadii.Pill))
                                        .background(RailBitePalette.CyanTint)
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        text = "Now",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = RailBitePalette.CyanPrimary,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Contextual status explanation box
            Surface(
                shape = RoundedCornerShape(RailBiteRadii.Button),
                color = if (failed) RailBitePalette.SemanticRedTint else RailBitePalette.Canvas,
                border = BorderStroke(
                    1.dp,
                    if (failed) RailBitePalette.SemanticRedBorder else RailBitePalette.BorderSubtle,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = when {
                        status == Statuses.CANCELLED -> "This order has been cancelled."
                        status == Statuses.EXPIRED -> "No pantry runner accepted in time — your order expired."
                        relayed && status == Statuses.ACCEPTED -> "Runner accepted over Bluetooth — packing at Coach Pantry."
                        relayed && status == Statuses.OUT_FOR_DELIVERY -> "Runner is on the way to your seat — keep cash & PIN ready."
                        relayed && status == Statuses.DELIVERED -> "Delivered — cash handover completed offline."
                        relayed -> "Waiting for a nearby runner to accept your Bluetooth mesh order."
                        status == Statuses.PLACED || status == Statuses.ASSIGNED -> "Your order has been queued to the Pantry Car."
                        status == Statuses.ACCEPTED -> "Pantry runner accepted your order and queued preparation."
                        status == Statuses.PACKING -> "Pantry staff is freshly sealing your order at Coach Pantry."
                        status == Statuses.OUT_FOR_DELIVERY -> "Your runner is walking toward your berth now."
                        status == Statuses.DELIVERED -> "Delivered to your seat. Enjoy your meal!"
                        else -> Statuses.label(status)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (failed) RailBitePalette.SemanticRed else RailBitePalette.TextSecondary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
    }
}

