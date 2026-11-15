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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.railbite.core.mesh.MeshOrderPayload
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
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text("Bluetooth relay order", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        Text(
            "Placed offline — completes locally and syncs to the cloud when internet returns.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (order == null) {
            Text("Order details not found in the relay ledger.", color = MaterialTheme.colorScheme.error)
            Button(onClick = onDone) { Text("Back") }
            return@Column
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Coach ${order.coach} · Seat ${order.berth}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("PNR ${order.pnr} · ${order.diners} diner${if (order.diners == 1) "" else "s"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                order.items.forEach { item ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${item.qty} × ${item.name}")
                        Text(Money.rupees(item.unitPricePaise * item.qty))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Cash on delivery", fontWeight = FontWeight.SemiBold)
                    Text(Money.rupees(order.totalPaise), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
                }
            }
        }

        if (!done) {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.filter(Char::isDigit).take(4) },
                label = { Text("Passenger's 4-digit code") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    if (code.length != 4) {
                        message = "Enter the 4-digit code shown in the passenger's app"
                        return@Button
                    }
                    if (code != order.deliveryCode) {
                        message = "Wrong code — ask the passenger to show it again"
                        return@Button
                    }
                    scope.launch {
                        container.meshLedger.markState(order.orderId, "DELIVERED", deliveryCode = code)
                        container.settings.setPickerOrderId("")
                        done = true
                        message = "Handover complete — will sync to the cloud automatically"
                    }
                },
                enabled = code.length == 4,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Mark delivered") }
        } else {
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        }

        message?.let {
            Text(
                it,
                color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
