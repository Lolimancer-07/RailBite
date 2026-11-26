package com.railbite.customer.ui.tracking

import android.content.ContentValues
import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.railbite.core.ui.LoadingSkeleton
import com.railbite.core.util.Money
import com.railbite.core.util.Statuses
import com.railbite.core.util.Upi
import com.railbite.customer.ui.components.CashConfirmInlineCard
import com.railbite.customer.ui.components.CashConfirmSheet
import com.railbite.customer.ui.components.FoodAssets
import com.railbite.customer.ui.components.HandoverPinCard
import com.railbite.customer.ui.components.OrderProgressTimeline
import com.railbite.customer.ui.components.StatusDot
import com.railbite.customer.ui.theme.RailBiteCardBorder
import com.railbite.customer.ui.theme.RailBiteMetrics
import com.railbite.customer.ui.theme.RailBiteNumericType
import com.railbite.customer.ui.theme.RailBitePalette
import com.railbite.customer.ui.theme.RailBiteRadii
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackingScreen(
    graph: com.railbite.customer.CustomerGraph,
    orderId: String,
    onBack: () -> Unit,
    onReport: () -> Unit,
) {
    val vm = viewModel(key = "tracking-$orderId") { TrackingViewModel(graph.container, orderId) }
    val state by vm.state.collectAsState()
    val mqttStatuses by vm.mqttStatuses(orderId).collectAsState()

    LaunchedEffect(mqttStatuses) {
        vm.refreshMerged(mqttStatuses)
    }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            vm.clearError()
        }
    }

    var showCashSheet by remember { mutableStateOf(false) }
    LaunchedEffect(state.delivered, state.cashConfirmed, state.relayed) {
        if (state.delivered && !state.cashConfirmed && !state.relayed) {
            showCashSheet = true
        }
    }

    Scaffold(
        containerColor = RailBitePalette.Canvas,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column(Modifier.background(RailBitePalette.SurfaceCard)) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = RailBitePalette.SurfaceCard,
                        titleContentColor = RailBitePalette.TextPrimary,
                    ),
                    title = {
                        Text(
                            text = "Live Order Tracking",
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
                        Surface(
                            shape = RoundedCornerShape(RailBiteRadii.Pill),
                            color = RailBitePalette.CyanTint,
                            border = BorderStroke(1.dp, RailBitePalette.CyanBorder),
                            modifier = Modifier.padding(end = 12.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            ) {
                                StatusDot(online = !state.failed && !state.relayed)
                                Text(
                                    text = "#${orderId.take(8).uppercase()}",
                                    style = RailBiteNumericType.MetaMono,
                                    color = RailBitePalette.CyanPressed,
                                )
                            }
                        }
                    },
                )
                HorizontalDivider(thickness = 1.dp, color = RailBitePalette.BorderSubtle)
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = RailBiteMetrics.ScreenPadding, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.loading) {
                LoadingSkeleton(rows = 4)
            } else {
                val order = state.order
                val code = order?.deliveryCode

                if (state.relayed) {
                    RelayBanner()
                }

                // Stitch `5ea7ade2` 5-step Meal Journey Timeline
                OrderProgressTimeline(
                    status = state.status,
                    etaMin = state.etaMin,
                    coach = order?.coach,
                    berth = order?.berth,
                    relayed = state.relayed,
                )

                // Berth Delivery PIN (`HandoverPinCard`)
                if (state.relayed && state.relayCode != null) {
                    HandoverPinCard(
                        code = state.relayCode!!,
                        subtitle = relayHint,
                    )
                } else if (
                    code != null &&
                    (state.status == Statuses.PACKING || state.status == Statuses.OUT_FOR_DELIVERY)
                ) {
                    HandoverPinCard(
                        code = code,
                        subtitle = if (state.status == Statuses.PACKING) {
                            "Order is packing — share this handover PIN when the runner reaches Berth ${order.berth}."
                        } else {
                            "Share this handover PIN with your delivery runner at Berth ${order.berth}."
                        },
                    )
                }

                // Assigned Runner card
                state.captainName?.let { name ->
                    RunnerInfoCard(name = pickerDisplayName(name))
                }

                // UPI_QUEUED fallback card
                if (order?.paymentMode == "UPI_QUEUED" && state.status != Statuses.DELIVERED && !state.failed) {
                    UpiPayCard(
                        orderId = orderId,
                        totalPaise = order.totalPaise,
                        onNoUpiApp = {
                            scope.launch {
                                snackbar.showSnackbar("No UPI app found — pay via any UPI app to ${Upi.VPA}")
                            }
                        },
                    )
                }

                // Cash Handover Verification Inline Card (companion to CashConfirmSheet)
                if (state.delivered && !state.relayed) {
                    CashConfirmInlineCard(
                        totalPaise = order?.totalPaise ?: 0,
                        isUpi = order?.paymentMode == "UPI_QUEUED",
                        confirmed = state.cashConfirmed,
                        confirming = state.cashConfirming,
                        captainName = state.captainName,
                        onConfirmPaid = vm::confirmCash,
                        onReportIssue = onReport,
                    )
                }

                if (state.status == Statuses.DELIVERED) {
                    BillCard(
                        order = state.order,
                        onDownloadPdf = { deliveredOrder ->
                            scope.launch {
                                val uri = withContext(Dispatchers.IO) { generateBillPdf(context, deliveredOrder) }
                                if (uri != null) {
                                    snackbar.showSnackbar("Bill saved to your downloads")
                                }
                            }
                        },
                    )
                } else if (state.relayed) {
                    RelayItemsCard(state.relayItems, state.relayTotalPaise)
                } else {
                    ItemsCard(state.order)
                }

                if ((state.status == Statuses.PLACED || state.status == Statuses.ASSIGNED) && !state.relayed) {
                    OutlinedButton(
                        onClick = vm::cancelOrder,
                        shape = RoundedCornerShape(RailBiteRadii.Button),
                        border = BorderStroke(1.dp, RailBitePalette.BorderSubtle),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RailBitePalette.TextPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = RailBiteMetrics.TouchTargetMin),
                    ) {
                        Text("Cancel Order", style = MaterialTheme.typography.labelLarge)
                    }
                }

                // Stitch `5ea7ade2` bottom Report Issue card
                Surface(
                    onClick = onReport,
                    shape = RoundedCornerShape(RailBiteRadii.Card),
                    color = RailBitePalette.SurfaceCard,
                    border = RailBiteCardBorder,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = RailBiteMetrics.TouchTargetMin),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ReportProblem,
                            contentDescription = null,
                            tint = RailBitePalette.SemanticRed,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Train diverted or berth changed?",
                            style = MaterialTheme.typography.bodySmall,
                            color = RailBitePalette.TextSecondary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "Report Issue",
                            style = MaterialTheme.typography.labelLarge,
                            color = RailBitePalette.CyanPrimary,
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = RailBitePalette.CyanPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
            }
        }
    }

    if (showCashSheet && state.delivered && !state.cashConfirmed && !state.relayed) {
        CashConfirmSheet(
            totalPaise = state.order?.totalPaise ?: 0,
            isUpi = state.order?.paymentMode == "UPI_QUEUED",
            captainName = state.captainName,
            confirming = state.cashConfirming,
            onConfirmPaid = {
                showCashSheet = false
                vm.confirmCash()
            },
            onReportMismatch = onReport,
            onDismiss = { showCashSheet = false },
        )
    }
}

private val relayHint = "Show this code to the pantry runner when the food reaches your seat."

@Composable
private fun RelayBanner() {
    Surface(
        shape = RoundedCornerShape(RailBiteRadii.Card),
        color = RailBitePalette.CyanTint,
        border = BorderStroke(1.dp, RailBitePalette.CyanBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.BluetoothConnected,
                contentDescription = null,
                tint = RailBitePalette.CyanPrimary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = "Sent over Bluetooth mesh. Pantry updates travel directly phone-to-phone without internet.",
                style = MaterialTheme.typography.bodySmall,
                color = RailBitePalette.CyanPressed,
            )
        }
    }
}

@Composable
private fun RunnerInfoCard(name: String) {
    Card(
        shape = RoundedCornerShape(RailBiteRadii.Card),
        colors = CardDefaults.cardColors(containerColor = RailBitePalette.SurfaceCard),
        border = RailBiteCardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(14.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = RailBitePalette.ImageTile,
                border = RailBiteCardBorder,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = RailBitePalette.TextSecondary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleSmall,
                        color = RailBitePalette.TextPrimary,
                    )
                    Icon(
                        imageVector = Icons.Filled.Verified,
                        contentDescription = null,
                        tint = RailBitePalette.SemanticGreen,
                        modifier = Modifier.size(15.dp),
                    )
                }
                Text(
                    text = "Verified Pantry Runner",
                    style = MaterialTheme.typography.bodySmall,
                    color = RailBitePalette.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun UpiPayCard(orderId: String, totalPaise: Int, onNoUpiApp: () -> Unit) {
    val context = LocalContext.current
    Card(
        shape = RoundedCornerShape(RailBiteRadii.Card),
        colors = CardDefaults.cardColors(containerColor = RailBitePalette.SurfaceCard),
        border = BorderStroke(1.dp, RailBitePalette.CyanBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.QrCode2,
                    contentDescription = null,
                    tint = RailBitePalette.CyanPrimary,
                    modifier = Modifier.size(20.dp),
                )
                Text("Pay via UPI", style = MaterialTheme.typography.titleSmall, color = RailBitePalette.TextPrimary)
            }
            Text(
                text = "Queued for digital payment — pay ${Money.rupees(totalPaise)} using any installed UPI app, then confirm after delivery.",
                style = MaterialTheme.typography.bodySmall,
                color = RailBitePalette.TextSecondary,
            )
            Button(
                onClick = { if (!Upi.launch(context, orderId, totalPaise)) onNoUpiApp() },
                shape = RoundedCornerShape(RailBiteRadii.Button),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RailBitePalette.CyanPrimary,
                    contentColor = Color.White,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(RailBiteMetrics.PrimaryButtonHeight),
            ) {
                Text(
                    text = "Pay ${Money.rupees(totalPaise)} via UPI",
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    }
}

private fun pickerDisplayName(name: String): String =
    name.substringBefore(" (Picker").trim()

@Composable
private fun RelayItemsCard(
    items: List<com.railbite.core.mesh.MeshOrderPayload.Item>,
    totalPaise: Int,
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
            Text("Order Summary", style = MaterialTheme.typography.titleSmall, color = RailBitePalette.TextPrimary)
            items.forEach { item ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(RailBiteRadii.Button))
                            .background(RailBitePalette.ImageTile),
                    ) {
                        Image(
                            painter = painterResource(id = FoodAssets.drawableFor(item.name)),
                            contentDescription = item.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Text(
                        text = "${item.qty}× ${item.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RailBitePalette.TextPrimary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = Money.rupees(item.unitPricePaise * item.qty),
                        style = RailBiteNumericType.PriceMedium,
                        color = RailBitePalette.TextPrimary,
                    )
                }
            }
            HorizontalDivider(thickness = 1.dp, color = RailBitePalette.BorderSubtle)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Amount Payable (COD)", style = MaterialTheme.typography.titleSmall, color = RailBitePalette.TextPrimary)
                Text(
                    text = Money.rupees(totalPaise),
                    style = RailBiteNumericType.PriceLarge,
                    color = RailBitePalette.CyanPrimary,
                )
            }
        }
    }
}

@Composable
private fun ItemsCard(order: com.railbite.core.model.Order?) {
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
            val items = order?.items.orEmpty()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Order Summary", style = MaterialTheme.typography.titleSmall, color = RailBitePalette.TextPrimary)
                if (items.isNotEmpty()) {
                    Text(
                        text = "${items.sumOf { it.qty }} items",
                        style = MaterialTheme.typography.bodySmall,
                        color = RailBitePalette.TextSecondary,
                    )
                }
            }
            items.forEach { item ->
                val itemName = item.name ?: item.itemId.take(8)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(RailBiteRadii.Button))
                            .background(RailBitePalette.ImageTile),
                    ) {
                        Image(
                            painter = painterResource(id = FoodAssets.drawableFor(itemName)),
                            contentDescription = itemName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Text(
                        text = "${item.qty}× $itemName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RailBitePalette.TextPrimary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = Money.rupees(item.unitPricePaise * item.qty),
                        style = RailBiteNumericType.PriceMedium,
                        color = RailBitePalette.TextPrimary,
                    )
                }
            }
            if (items.isEmpty()) {
                Text(
                    text = "Items load when the server is reachable.",
                    style = MaterialTheme.typography.bodySmall,
                    color = RailBitePalette.TextSecondary,
                )
            }
            HorizontalDivider(thickness = 1.dp, color = RailBitePalette.BorderSubtle)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Amount Payable", style = MaterialTheme.typography.titleSmall, color = RailBitePalette.TextPrimary)
                    Text(
                        text = if (order?.paymentMode == "UPI_QUEUED") "Digital / UPI Queued" else "Cash on Delivery",
                        style = MaterialTheme.typography.bodySmall,
                        color = RailBitePalette.TextSecondary,
                    )
                }
                Text(
                    text = Money.rupees(order?.totalPaise ?: 0),
                    style = RailBiteNumericType.HeroTotal,
                    color = RailBitePalette.CyanPrimary,
                )
            }
        }
    }
}

@Composable
private fun BillCard(
    order: com.railbite.core.model.Order?,
    onDownloadPdf: (com.railbite.core.model.Order?) -> Unit = {},
) {
    Card(
        shape = RoundedCornerShape(RailBiteRadii.Card),
        colors = CardDefaults.cardColors(containerColor = RailBitePalette.SurfaceCard),
        border = RailBiteCardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Receipt & Bill", style = MaterialTheme.typography.titleMedium, color = RailBitePalette.TextPrimary)
                Text(
                    text = "#${order?.id?.take(8)?.uppercase() ?: ""}",
                    style = RailBiteNumericType.MetaMono,
                    color = RailBitePalette.TextSecondary,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "PNR ${order?.pnr ?: "—"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = RailBitePalette.TextSecondary,
                )
                Text(
                    text = "Seat ${order?.coach ?: "—"} • Berth ${order?.berth ?: "—"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = RailBitePalette.TextSecondary,
                )
            }
            order?.let {
                Text(
                    text = "Placed ${formatBillTime(it.createdAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = RailBitePalette.TextSecondary,
                )
            }

            HorizontalDivider(thickness = 1.dp, color = RailBitePalette.BorderSubtle)

            val billItems = order?.items.orEmpty()
            if (billItems.isEmpty()) {
                Text(
                    text = "Items load when the server is reachable.",
                    style = MaterialTheme.typography.bodySmall,
                    color = RailBitePalette.TextSecondary,
                )
            }
            billItems.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${item.qty} × ${item.name ?: item.itemId.take(8)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RailBitePalette.TextPrimary,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                    )
                    Text(
                        text = Money.rupees(item.unitPricePaise * item.qty),
                        style = RailBiteNumericType.PriceMedium,
                        color = RailBitePalette.TextPrimary,
                    )
                }
            }

            HorizontalDivider(thickness = 1.dp, color = RailBitePalette.BorderSubtle)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Total (${if (order?.paymentMode == "UPI_QUEUED") "UPI" else "COD"})", style = MaterialTheme.typography.titleSmall, color = RailBitePalette.TextPrimary)
                Text(
                    text = Money.rupees(order?.totalPaise ?: 0),
                    style = RailBiteNumericType.PriceLarge,
                    color = RailBitePalette.CyanPrimary,
                )
            }

            Spacer(Modifier.height(4.dp))

            OutlinedButton(
                onClick = { order?.let(onDownloadPdf) },
                shape = RoundedCornerShape(RailBiteRadii.Button),
                border = RailBiteCardBorder,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = RailBitePalette.TextPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = RailBiteMetrics.TouchTargetMin),
            ) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text("Download PDF Bill", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

private suspend fun generateBillPdf(context: Context, order: com.railbite.core.model.Order?): Uri? =
    withContext(Dispatchers.IO) {
        runCatching {
            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas
            val pageHeight = 842
            val titlePaint = Paint().apply { textSize = 18f }
            val bodyPaint = Paint().apply { textSize = 12f }

            canvas.save()
            var y = pageHeight - 60f
            canvas.drawText("RailBite Bill", 40f, y, titlePaint)
            y -= 40
            order?.let {
                canvas.drawText("Order #${it.id.take(8)}", 40f, y, bodyPaint)
                y -= 20
                canvas.drawText("PNR ${it.pnr}", 40f, y, bodyPaint)
                y -= 20
                canvas.drawText("Seat ${it.coach} · Berth ${it.berth}", 40f, y, bodyPaint)
                y -= 20
                canvas.drawText("Placed ${formatBillTime(it.createdAt)}", 40f, y, bodyPaint)
                y -= 30
                it.items.orEmpty().forEach { item ->
                    canvas.drawText(
                        "${item.qty} × ${item.name ?: item.itemId.take(8)} — ${Money.rupees(item.unitPricePaise * item.qty)}",
                        40f,
                        y,
                        bodyPaint,
                    )
                    y -= 18
                }
                y -= 10
                canvas.drawText("Total: ${Money.rupees(it.totalPaise)}", 40f, y, titlePaint)
                y -= 30
                canvas.drawText("Payment: ${if (it.paymentMode == "UPI_QUEUED") "UPI" else "COD"}", 40f, y, bodyPaint)
            }
            canvas.restore()
            document.finishPage(page)

            val out = java.io.ByteArrayOutputStream()
            document.writeTo(out)
            document.close()
            val bytes = out.toByteArray()

            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "railbite-bill-${order?.id?.take(8) ?: "order"}.pdf")
                    put(MediaStore.Images.Media.MIME_TYPE, "application/pdf")
                },
            ) ?: return@withContext null
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return@withContext null
            uri
        }.getOrNull()
    }

private fun formatBillTime(iso: String): String = runCatching {
    java.time.Instant.parse(iso)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"))
}.getOrDefault(iso)
