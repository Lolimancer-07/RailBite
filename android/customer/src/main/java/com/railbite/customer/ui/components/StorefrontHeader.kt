package com.railbite.customer.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.railbite.customer.ui.onboarding.flatTextFieldColors
import com.railbite.customer.ui.theme.RailBiteCardBorder
import com.railbite.customer.ui.theme.RailBiteMetrics
import com.railbite.customer.ui.theme.RailBiteNumericType
import com.railbite.customer.ui.theme.RailBitePalette
import com.railbite.customer.ui.theme.RailBiteRadii
import kotlinx.coroutines.delay

/**
 * Wireframe Home Top Row (`acf3d694`):
 * Left: `RailBite` brand box with live server connection dot.
 * Right: compact tappable `SeatInfoBox` showing `PNR`, `Coach`, `Seat` that opens `SeatSetupBottomSheet`.
 */
@Composable
fun StorefrontTopRow(
    online: Boolean,
    trainNumber: String,
    pnr: String,
    coach: String,
    berth: Int?,
    onBrandClick: () -> Unit,
    onSeatBoxClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = RailBiteMetrics.ScreenPadding, vertical = 10.dp),
    ) {
        // Left: Brand card + live status dot
        Surface(
            onClick = onBrandClick,
            shape = RoundedCornerShape(RailBiteRadii.ChipBox),
            color = RailBitePalette.SurfaceCard,
            border = RailBiteCardBorder,
            shadowElevation = 0.dp,
            modifier = Modifier.heightIn(min = 54.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    text = "RailBite",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = RailBitePalette.CyanPrimary,
                )
                StatusDot(online = online)
            }
        }

        // Right: Compact tappable SeatInfoBox (Train + PNR + Coach + Seat)
        SeatInfoBox(
            trainNumber = trainNumber,
            pnr = pnr,
            coach = coach,
            berth = berth,
            onClick = onSeatBoxClick,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun SeatInfoBox(
    trainNumber: String,
    pnr: String,
    coach: String,
    berth: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(RailBiteRadii.ChipBox),
        color = RailBitePalette.CyanTint,
        border = BorderStroke(1.dp, RailBitePalette.CyanPrimary),
        shadowElevation = 0.dp,
        modifier = modifier.heightIn(min = 54.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (pnr.isNotBlank()) "PNR: $pnr" else "Set Train & Seat",
                    style = RailBiteNumericType.MetaMono,
                    color = RailBitePalette.DarkSlate,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Edit seat details",
                    tint = RailBitePalette.CyanPrimary,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = when {
                    trainNumber.isNotBlank() && coach.isNotBlank() && berth != null && berth > 0 ->
                        "$trainNumber • $coach-$berth"
                    coach.isNotBlank() && berth != null && berth > 0 ->
                        "Set Train • $coach-$berth"
                    else -> "Tap to select Train, Coach & Berth"
                },
                style = MaterialTheme.typography.labelSmall,
                color = RailBitePalette.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val ROTATING_SEARCH_HINTS = listOf(
    "Search \"Masala Chai\"",
    "Search \"Veg Biryani\"",
    "Search \"Samosa\"",
    "Search \"RailNeer\"",
)

/**
 * Wireframe Home Utility Row (`acf3d694`):
 * Animated clickable `HomeSearchEntryPill` (`Modifier.weight(1f)`) -> navigates to dedicated SearchScreen,
 * plus `AccountIconButton` + `CartIconButton` (with live badge count).
 */
@Composable
fun StorefrontUtilityRow(
    search: String,
    onSearchChange: (String) -> Unit,
    cartCount: Int,
    onAccountClick: () -> Unit,
    onCartClick: () -> Unit,
    onOpenSearch: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = RailBiteMetrics.ScreenPadding),
    ) {
        if (onOpenSearch != null) {
            HomeSearchEntryPill(
                onClick = onOpenSearch,
                modifier = Modifier.weight(1f),
            )
        } else {
            SearchPill(
                value = search,
                onValueChange = onSearchChange,
                modifier = Modifier.weight(1f),
            )
        }

        AccountIconButton(onClick = onAccountClick)

        CartIconButton(
            count = cartCount,
            onClick = onCartClick,
        )
    }
}

/** Clickable search trigger pill with subtle rotating placeholder hints every 3 seconds. */
@Composable
fun HomeSearchEntryPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var hintIdx by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(3_000)
            hintIdx = (hintIdx + 1) % ROTATING_SEARCH_HINTS.size
        }
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(RailBiteRadii.Pill),
        color = RailBitePalette.SurfaceCard,
        border = RailBiteCardBorder,
        shadowElevation = 0.dp,
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics { contentDescription = "Search pantry items" },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = RailBitePalette.CyanPrimary,
                modifier = Modifier.size(20.dp),
            )
            AnimatedContent(
                targetState = ROTATING_SEARCH_HINTS[hintIdx],
                transitionSpec = {
                    (fadeIn() + slideInVertically { it / 3 })
                        .togetherWith(fadeOut() + slideOutVertically { -it / 3 })
                },
                label = "home-search-hint",
                modifier = Modifier.weight(1f),
            ) { hint ->
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = RailBitePalette.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun SearchPill(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                text = "Search chai, snacks, lunch…",
                style = MaterialTheme.typography.bodyMedium,
                color = RailBitePalette.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Clear search",
                        tint = RailBitePalette.TextSecondary,
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = RailBitePalette.TextSecondary,
                )
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(RailBiteRadii.Pill),
        colors = flatTextFieldColors(),
        modifier = modifier.heightIn(min = 48.dp),
    )
}

@Composable
fun AccountIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = RailBitePalette.SurfaceCard,
        border = RailBiteCardBorder,
        shadowElevation = 0.dp,
        modifier = modifier
            .size(48.dp)
            .semantics { contentDescription = "Account, orders and settings" },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.PersonOutline,
                contentDescription = null,
                tint = RailBitePalette.DarkSlate,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
fun CartIconButton(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = RailBitePalette.SurfaceCard,
            border = RailBiteCardBorder,
            shadowElevation = 0.dp,
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = "Open cart" },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.ShoppingCart,
                    contentDescription = null,
                    tint = RailBitePalette.DarkSlate,
                    modifier = Modifier.size(21.dp),
                )
            }
        }
        if (count > 0) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
                    .size(20.dp)
                    .background(RailBitePalette.CyanPrimary, CircleShape),
            ) {
                Text(
                    text = if (count > 99) "99" else "$count",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
        }
    }
}

