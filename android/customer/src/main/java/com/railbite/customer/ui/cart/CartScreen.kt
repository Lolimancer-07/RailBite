package com.railbite.customer.ui.cart

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.railbite.core.ui.EmptyState
import com.railbite.core.ui.components.ErrorBar
import com.railbite.core.util.Money
import com.railbite.customer.ui.components.CartItemCard
import com.railbite.customer.ui.theme.RailBiteCardBorder
import com.railbite.customer.ui.theme.RailBiteMetrics
import com.railbite.customer.ui.theme.RailBiteNumericType
import com.railbite.customer.ui.theme.RailBitePalette
import com.railbite.customer.ui.theme.RailBiteRadii
import com.railbite.customer.ui.theme.RailBiteSelectedBorder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartScreen(
    graph: com.railbite.customer.CustomerGraph,
    onPlaced: (String) -> Unit,
    onBack: () -> Unit,
) {
    val vm = viewModel(key = "cart") { CartViewModel(graph.container) }
    val state by vm.state.collectAsState()
    val lines by vm.lines.collectAsState()
    val seat by vm.seat.collectAsState()

    val itemCount = lines.values.sumOf { it.qty }
    val itemTotal = lines.values.sumOf { it.qty * it.item.mrpPaise }
    val deliveryFee = 0
    val grandTotal = itemTotal + deliveryFee

    Scaffold(
        containerColor = RailBitePalette.Canvas,
        topBar = {
            Column(Modifier.background(RailBitePalette.SurfaceCard)) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = RailBitePalette.SurfaceCard,
                        titleContentColor = RailBitePalette.TextPrimary,
                    ),
                    title = {
                        Text(
                            text = "Your Cart",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = RailBitePalette.TextPrimary,
                            )
                        }
                    },
                    actions = {
                        seat?.let { s ->
                            Surface(
                                shape = RoundedCornerShape(RailBiteRadii.Pill),
                                color = RailBitePalette.CyanTint,
                                border = BorderStroke(1.dp, RailBitePalette.CyanBorder),
                                modifier = Modifier.padding(end = 12.dp),
                            ) {
                                Text(
                                    text = "${s.coach} • ${s.berth}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = RailBitePalette.CyanPressed,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                )
                            }
                        }
                    },
                )
                HorizontalDivider(thickness = 1.dp, color = RailBitePalette.BorderSubtle)
            }
        },
        bottomBar = {
            if (itemCount > 0) {
                Surface(
                    color = RailBitePalette.SurfaceCard,
                    shadowElevation = 0.dp,
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding(),
                    ) {
                        HorizontalDivider(thickness = 1.dp, color = RailBitePalette.BorderSubtle)
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = RailBiteMetrics.ScreenPadding, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            state.error?.let { ErrorBar(it) }

                            Button(
                                onClick = { vm.placeOrder(onPlaced) },
                                enabled = !state.placing,
                                shape = RoundedCornerShape(RailBiteRadii.Button),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = RailBitePalette.CyanPrimary,
                                    contentColor = Color.White,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(RailBiteMetrics.PrimaryButtonHeight),
                            ) {
                                if (state.placing) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    if (state.sendingRelay) {
                                        Spacer(Modifier.size(8.dp))
                                        Text("Sending over Bluetooth…", style = MaterialTheme.typography.titleSmall)
                                    }
                                } else {
                                    Text(
                                        text = if (state.paymentMode == "UPI_QUEUED") {
                                            "Place Order (UPI)"
                                        } else {
                                            "Place Order (COD)"
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        text = Money.rupees(grandTotal),
                                        style = RailBiteNumericType.PriceMedium,
                                        color = Color.White,
                                    )
                                    Spacer(Modifier.size(6.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        if (itemCount == 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    title = "Your cart is empty",
                    subtitle = "Add some hot chai or pantry snacks from the storefront",
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(
                    horizontal = RailBiteMetrics.ScreenPadding,
                    vertical = 14.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(lines.values.sortedBy { it.item.name }, key = { it.item.id }) { line ->
                    CartItemCard(
                        name = line.item.name,
                        category = line.item.category,
                        isVeg = line.item.isVeg,
                        unitPricePaise = line.item.mrpPaise,
                        qty = line.qty,
                        maxStock = line.item.stock,
                        onAdd = { vm.add(line.item.id) },
                        onDecrement = { vm.decrement(line.item.id) },
                    )
                }

                // Seat Verification Card
                item {
                    SeatVerificationCard(
                        pnr = seat?.pnr,
                        coach = seat?.coach,
                        berth = seat?.berth,
                        diners = seat?.diners ?: 1,
                    )
                }

                // Bill Summary Card
                item {
                    BillSummaryCard(
                        itemTotal = itemTotal,
                        deliveryFee = deliveryFee,
                        grandTotal = grandTotal,
                    )
                }

                // Payment Method Cards (COD / UPI_QUEUED)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Payment Method",
                            style = MaterialTheme.typography.titleMedium,
                            color = RailBitePalette.TextPrimary,
                        )
                        PaymentModeCard(
                            title = "Cash on Delivery (COD)",
                            subtitle = "Pay exact ${Money.rupees(grandTotal)} cash to pantry runner at ${seat?.chip ?: "your seat"}. Works offline via Bluetooth relay.",
                            badge = "Recommended",
                            icon = Icons.Filled.Payments,
                            selected = state.paymentMode == "COD",
                            onClick = { vm.setPaymentMode("COD") },
                        )
                        PaymentModeCard(
                            title = "UPI (Pay in App)",
                            subtitle = "Launch GPay, PhonePe, or Paytm directly from live order tracking.",
                            badge = null,
                            icon = Icons.Filled.QrCode2,
                            selected = state.paymentMode == "UPI_QUEUED",
                            onClick = { vm.setPaymentMode("UPI_QUEUED") },
                        )
                    }
                }

                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
private fun SeatVerificationCard(
    pnr: String?,
    coach: String?,
    berth: Int?,
    diners: Int,
) {
    Card(
        shape = RoundedCornerShape(RailBiteRadii.Card),
        colors = CardDefaults.cardColors(containerColor = RailBitePalette.SurfaceCard),
        border = RailBiteCardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Filled.EventSeat,
                    contentDescription = null,
                    tint = RailBitePalette.CyanPrimary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "Seat Verification",
                    style = MaterialTheme.typography.titleSmall,
                    color = RailBitePalette.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (!pnr.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(RailBiteRadii.Control),
                        color = RailBitePalette.ImageTile,
                        border = RailBiteCardBorder,
                    ) {
                        Text(
                            text = "PNR $pnr",
                            style = RailBiteNumericType.MetaMono,
                            color = RailBitePalette.TextPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(RailBiteRadii.Button),
                color = RailBitePalette.Canvas,
                border = RailBiteCardBorder,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp, horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    SeatColumn(label = "COACH", value = coach ?: "—")
                    SeatColumn(label = "BERTH", value = berth?.toString() ?: "—")
                    SeatColumn(label = "DINERS", value = "$diners")
                }
            }
        }
    }
}

@Composable
private fun SeatColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = RailBitePalette.TextSecondary)
        Text(value, style = MaterialTheme.typography.titleSmall, color = RailBitePalette.CyanPressed)
    }
}

@Composable
private fun BillSummaryCard(itemTotal: Int, deliveryFee: Int, grandTotal: Int) {
    Card(
        shape = RoundedCornerShape(RailBiteRadii.Card),
        colors = CardDefaults.cardColors(containerColor = RailBitePalette.SurfaceCard),
        border = RailBiteCardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Bill Summary", style = MaterialTheme.typography.titleSmall, color = RailBitePalette.TextPrimary)
                Surface(
                    shape = RoundedCornerShape(RailBiteRadii.Pill),
                    color = RailBitePalette.Canvas,
                    border = RailBiteCardBorder,
                ) {
                    Text(
                        text = "IRCTC MRP",
                        style = MaterialTheme.typography.labelSmall,
                        color = RailBitePalette.SemanticGreen,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }

            SummaryLine("Item Total", Money.rupees(itemTotal))
            SummaryLine("Delivery to Berth", if (deliveryFee == 0) "FREE" else Money.rupees(deliveryFee), greenValue = deliveryFee == 0)
            SummaryLine("Platform & Packaging Fee", "₹0 Included")

            Surface(
                shape = RoundedCornerShape(RailBiteRadii.Button),
                color = RailBitePalette.SemanticGreenTint,
                border = BorderStroke(1.dp, RailBitePalette.SemanticGreenBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Verified,
                        contentDescription = null,
                        tint = RailBitePalette.SemanticGreen,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Official IRCTC MRP Verified • Zero Markup",
                        style = MaterialTheme.typography.labelSmall,
                        color = RailBitePalette.SemanticGreen,
                    )
                }
            }

            HorizontalDivider(thickness = 1.dp, color = RailBitePalette.BorderSubtle)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("To Pay", style = MaterialTheme.typography.titleMedium, color = RailBitePalette.TextPrimary)
                    Text("Inclusive of all pantry charges", style = MaterialTheme.typography.bodySmall, color = RailBitePalette.TextSecondary)
                }
                Text(
                    text = Money.rupees(grandTotal),
                    style = RailBiteNumericType.HeroTotal,
                    color = RailBitePalette.TextPrimary,
                )
            }
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String, greenValue: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = RailBitePalette.TextSecondary)
        Text(
            text = value,
            style = RailBiteNumericType.PriceMedium,
            color = if (greenValue) RailBitePalette.SemanticGreen else RailBitePalette.TextPrimary,
        )
    }
}

@Composable
private fun PaymentModeCard(
    title: String,
    subtitle: String,
    badge: String?,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(RailBiteRadii.Card),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) RailBitePalette.CyanTint else RailBitePalette.SurfaceCard,
        ),
        border = if (selected) RailBiteSelectedBorder else RailBiteCardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(14.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(RailBiteRadii.Control),
                color = if (selected) RailBitePalette.CyanPrimary else RailBitePalette.ImageTile,
                contentColor = if (selected) Color.White else RailBitePalette.TextSecondary,
                modifier = Modifier.size(38.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = RailBitePalette.TextPrimary,
                    )
                    if (badge != null) {
                        Surface(
                            shape = RoundedCornerShape(RailBiteRadii.Pill),
                            color = RailBitePalette.SemanticGreenTint,
                            border = BorderStroke(1.dp, RailBitePalette.SemanticGreenBorder),
                        ) {
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelSmall,
                                color = RailBitePalette.SemanticGreen,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = RailBitePalette.TextSecondary,
                )
            }

            Surface(
                shape = CircleShape,
                color = if (selected) RailBitePalette.CyanPrimary else Color.Transparent,
                border = BorderStroke(
                    width = 1.5.dp,
                    color = if (selected) RailBitePalette.CyanPrimary else RailBitePalette.BorderSubtle,
                ),
                modifier = Modifier.size(22.dp),
            ) {
                if (selected) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}
