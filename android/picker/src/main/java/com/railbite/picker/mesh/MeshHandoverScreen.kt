package com.railbite.picker.mesh

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.railbite.core.mesh.MeshOrderPayload
import com.railbite.core.ui.components.ErrorBar
import com.railbite.core.ui.components.RailBiteButton
import com.railbite.core.ui.components.RailBiteSecondaryButton
import com.railbite.core.ui.components.RailBiteTextField
import com.railbite.core.ui.theme.Space
import com.railbite.core.util.Money
import kotlinx.coroutines.launch

/**
 * Offline handover for Bluetooth-relay orders: the passenger shows the 4-digit code they
 * generated when placing the order; the picker repeats it, cash is collected (total is
 * expected), and the outcome is recorded locally. The gateway replays it to the cloud
 * when internet returns — no server call happens here.
 */
@Composable
fun MeshHandoverScreen(
    graph: com.railbite.picker.PickerGraph,
    orderId: String,
    onDone: () -> Unit,
) {
    val container = graph.container
    val scope = rememberCoroutineScope()

    // Latest ledger payload for this order (the alert kept it in memory-adjacent storage).
    var payload by remember { mutableStateOf<MeshOrderPayload?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf("") }
    var done by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(orderId) {
        payload = container.meshLedger.all()
            .mapNotNull { MeshOrderPayload.decode(it.payload) }
            .firstOrNull { it.orderId == orderId }
    }

    val order = payload
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Space.screen),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        Spacer(Modifier.height(Space.xs))
        Text("Bluetooth relay order", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Placed offline — completes locally and syncs to the cloud when internet returns.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (order == null) {
            ErrorBar("Order details not found in the relay ledger.")
            RailBiteSecondaryButton(text = "Back", onClick = onDone)
            return@Column
        }

        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(
                Modifier.padding(Space.m),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                Text(
                    "Coach ${order.coach} · Seat ${order.berth}",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    "PNR ${order.pnr} · ${order.diners} diner${if (order.diners == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Space.xs))
                order.items.forEach { item ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${item.qty} × ${item.name}", style = MaterialTheme.typography.bodyMedium)
                        Text(Money.rupees(item.unitPricePaise * item.qty), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(Space.xs))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Cash on delivery", style = MaterialTheme.typography.titleSmall)
                    Text(
                        Money.rupees(order.totalPaise),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        if (!done) {
            RailBiteTextField(
                value = code,
                onValueChange = { code = it.filter(Char::isDigit).take(4) },
                label = "Passenger's 4-digit code",
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                ),
            )
            RailBiteButton(
                text = "Mark delivered",
                onClick = {
                    if (code.length != 4) {
                        message = "Enter the 4-digit code shown in the passenger's app"
                        return@RailBiteButton
                    }
                    if (code != order.deliveryCode) {
                        message = "Wrong code — ask the passenger to show it again"
                        return@RailBiteButton
                    }
                    scope.launch {
                        container.meshLedger.markState(order.orderId, "DELIVERED", deliveryCode = code)
                        container.settings.setPickerOrderId("")
                        done = true
                        message = "Handover complete — will sync to the cloud automatically"
                    }
                },
                enabled = code.length == 4,
            )
        } else {
            RailBiteButton(text = "Done", onClick = onDone)
        }

        message?.let {
            if (done) {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                ErrorBar(it)
            }
        }
    }
}
