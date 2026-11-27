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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.railbite.core.mesh.BlePacketCodec
import com.railbite.core.mesh.TransportMode
import com.railbite.core.ui.EmptyState
import com.railbite.core.ui.components.ErrorBar
import com.railbite.core.util.Money
import com.railbite.customer.ui.components.CartItemCard
import com.railbite.customer.ui.components.LocalBleActionRequester
import com.railbite.customer.ui.components.TransportIndicatorPill
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
    val transportStatus by vm.transportStatus.collectAsState()
    val requestBleAction = LocalBleActionRequester.current

    // Wireframe & Stitch `21ec2fa3` locks payment mode to Cash on Delivery (COD) for offline-ready berth handover.
    LaunchedEffect(Unit) {
        if (state.paymentMode != "COD") vm.setPaymentMode("COD")
    }

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
                            text = "Cart & Checkout",
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
                                    text = if (s.trainNumber.isNotBlank()) {
                                        "${s.trainNumber} • ${s.coach}-${s.berth}"
                                    } else {
                                        "${s.coach} • Seat ${s.berth}"
                                    },
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

                            if (state.needsBleAction && !transportStatus.lanReachable) {
                                OutlinedButton(
                                    onClick = {
                                        vm.clearError()
                                        requestBleAction()
                                    },
                                    shape = RoundedCornerShape(RailBiteRadii.Button),
                                    border = BorderStroke(1.dp, RailBitePalette.CyanPrimary),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RailBitePalette.CyanPressed),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Enable Bluetooth / Grant Permissions")
                                }
                            }

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
                                        Text("Broadcasting BLE Mesh…", style = MaterialTheme.typography.titleSmall)
                                    }
                                } else {
                                    Text(
                                        text = if (transportStatus.lanReachable) {
                                            "Place Order (COD)"
                                        } else {
                                            "Broadcast via BLE Mesh (COD)"
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
                // Transport mode pill + offline >4-item BLE limit notice
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        TransportIndicatorPill(
                            status = transportStatus,
                            onClick = if (transportStatus.mode == TransportMode.BLE_DISABLED) {
                                requestBleAction
                            } else {
                                null
                            },
                        )
                        if (!transportStatus.lanReachable) {
                            Text(
                                text = "Max ${BlePacketCodec.MAX_ORDER_ITEMS} dishes offline",
                                style = MaterialTheme.typography.labelSmall,
                                color = RailBitePalette.TextSecondary,
                            )
                        }
                    }
                }

                if (!transportStatus.lanReachable && lines.size > BlePacketCodec.MAX_ORDER_ITEMS) {
                    item {
                        Card(
                            shape = RoundedCornerShape(RailBiteRadii.Button),
                            colors = CardDefaults.cardColors(containerColor = RailBitePalette.SemanticAmberTint),
                            border = BorderStroke(1.dp, RailBitePalette.SemanticAmberBorder),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(12.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = RailBitePalette.SemanticAmber,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = "Offline BLE Mesh fits up to ${BlePacketCodec.MAX_ORDER_ITEMS} distinct items per 23-byte order (${lines.size} selected). Remove extra items or place in 2 separate orders.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = RailBitePalette.SemanticAmber,
                                )
                            }
                        }
                    }
                }

                // Delivering destination banner
                item {
                    SeatVerificationCard(
                        pnr = seat?.pnr,
                        coach = seat?.coach,
                        berth = seat?.berth,
                        diners = seat?.diners ?: 1,
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Order Items ($itemCount)",
                            style = MaterialTheme.typography.titleMedium,
                            color = RailBitePalette.TextPrimary,
                        )
                        Text(
                            text = "Coach Door Handover",
                            style = MaterialTheme.typography.bodySmall,
                            color = RailBitePalette.TextSecondary,
                        )
                    }
                }

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

                // Bill Summary Card
                item {
                    BillBreakdownCard(
                        itemTotal = itemTotal,
                        deliveryFee = deliveryFee,
                        grandTotal = grandTotal,
                    )
                }

                // Locked Cash on Delivery (COD) Card per Stitch `21ec2fa3`
                item {
                    LockedCodPaymentCard(
                        grandTotal = grandTotal,
                        berth = seat?.berth,
                    )
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
                    text = "Delivering to ${if (!coach.isNullOrBlank() && berth != null && berth > 0) "Coach $coach • Seat $berth" else "Your Seat"}",
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
            Text(
                text = "Diners: $diners • Direct berth delivery by Pantry runner",
                style = MaterialTheme.typography.bodySmall,
                color = RailBitePalette.TextSecondary,
            )
        }
    }
}

@Composable
private fun BillBreakdownCard(itemTotal: Int, deliveryFee: Int, grandTotal: Int) {
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
                Text("Bill Breakdown", style = MaterialTheme.typography.titleSmall, color = RailBitePalette.TextPrimary)
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
            SummaryLine("Delivery Fee to Berth", if (deliveryFee == 0) "FREE" else Money.rupees(deliveryFee), greenValue = deliveryFee == 0)

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
                        text = "Official Govt IRCTC MRP Verified • Zero Markup",
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
                    Text("Total to Pay", style = MaterialTheme.typography.titleMedium, color = RailBitePalette.TextPrimary)
                    Text("Inclusive of all local taxes", style = MaterialTheme.typography.bodySmall, color = RailBitePalette.TextSecondary)
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
private fun LockedCodPaymentCard(grandTotal: Int, berth: Int?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Payment Method",
                style = MaterialTheme.typography.titleMedium,
                color = RailBitePalette.TextPrimary,
            )
            Surface(
                shape = RoundedCornerShape(RailBiteRadii.Pill),
                color = RailBitePalette.SemanticGreenTint,
                border = BorderStroke(1.dp, RailBitePalette.SemanticGreenBorder),
            ) {
                Text(
                    text = "Offline Ready",
                    style = MaterialTheme.typography.labelSmall,
                    color = RailBitePalette.SemanticGreen,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }

        Card(
            shape = RoundedCornerShape(RailBiteRadii.Card),
            colors = CardDefaults.cardColors(containerColor = RailBitePalette.CyanTint),
            border = RailBiteSelectedBorder,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(14.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = RailBitePalette.CyanPrimary,
                    contentColor = Color.White,
                    modifier = Modifier.size(22.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Check, contentDescription = "Locked", modifier = Modifier.size(14.dp))
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Payments,
                            contentDescription = null,
                            tint = RailBitePalette.CyanPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Cash on Delivery (COD)",
                            style = MaterialTheme.typography.titleSmall,
                            color = RailBitePalette.TextPrimary,
                        )
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "Locked",
                            tint = RailBitePalette.TextSecondary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    val seatPhrase = if (berth != null && berth > 0) "Seat $berth" else "your berth"
                    Text(
                        text = "Pay exact ${Money.rupees(grandTotal)} cash to the pantry runner at $seatPhrase upon delivery. Works 100% offline without mobile network or UPI delay.",
                        style = MaterialTheme.typography.bodySmall,
                        color = RailBitePalette.TextSecondary,
                    )
                }
            }
        }
    }
}
