package com.railbite.picker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.railbite.picker.ui.theme.RailBitePickerTheme
import com.railbite.picker.alert.AlertBus
import com.railbite.picker.alert.Notifications
import com.railbite.picker.alert.RailBiteAlertService
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_ORDER_ID = "openOrderId"
    }

    /**
     * Bluetooth permissions gate the offline relay sink — ask once at first open.
     * Field-observed (2026-11-17): without this, the mesh gateway silently never
     * starts (scan needs Nearby devices on Android 12+) and relay orders never ring.
     */
    private val btPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        val container = (application as? RailBitePickerApp)?.container ?: return@registerForActivityResult
        if (granted.values.all { it }) {
            // Permissions landed: (re)start the mesh sink immediately, no relaunch needed.
            lifecycleScope.launch { runCatching { container.meshGateway.start() } }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as RailBitePickerApp).container
        Notifications.createChannels(this)

        // Make sure the mesh sink can actually scan before the first offline order.
        lifecycleScope.launch {
            val sinkOn = runCatching { container.settings.meshSinkEnabled.first() }.getOrDefault(false)
            if (sinkOn) {
                val missing = container.meshTransport.requiredPermissions()
                if (missing.isNotEmpty()) btPermissions.launch(missing.toTypedArray())
            }
        }

        val openOrderId = intent?.getStringExtra(EXTRA_OPEN_ORDER_ID)
        setContent {
            RailBitePickerTheme {
                PickerRoot(container, initialOpenOrderId = openOrderId)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Route to the picking checklist after ACCEPT from AlertActivity
        intent.getStringExtra(EXTRA_OPEN_ORDER_ID)?.let {
            OpenOrderRouter.post(it)
        }
    }
}

/** Simple event relay for "open order X" requests from intents. */
object OpenOrderRouter {
    private val _route = mutableStateOf<String?>(null)
    val route: androidx.compose.runtime.State<String?> get() = _route

    fun post(orderId: String) {
        _route.value = orderId
    }

    fun consume() {
        _route.value = null
    }
}

@Composable
private fun PickerRoot(
    container: PickerContainer,
    initialOpenOrderId: String?,
) {
    val graph = remember { PickerGraph(container) }
    val start by graph.startDestination.collectAsState()

    // Arm the alert service on every app open when a staff login exists.
    // Field-observed (2026-11-16): app restarts left the picker silently disarmed
    // (local ONLINE flag false) while the server kept dispatching to it — the seeded
    // staff rows are online=1, so the server-side gate alone can't reflect the device.
    // Opening the picker means returning to shift; the in-app ONLINE/OFFLINE toggle
    // still controls the current session (it stops the service and marks the staff
    // offline server-side, which is what actually gates dispatch).
    LaunchedEffect(Unit) {
        val staffId = container.settings.staffId.first()
        if (staffId.isNotBlank()) {
            androidx.core.content.ContextCompat.startForegroundService(
                container.app,
                Intent(container.app, RailBiteAlertService::class.java)
                    .setAction(RailBiteAlertService.ACTION_START),
            )
        }
        if (initialOpenOrderId != null) OpenOrderRouter.post(initialOpenOrderId)
    }

    // If an alert was accepted, jump straight to the progress screen — and consume
    // the event so a stale order doesn't resurface on the next app open.
    LaunchedEffect(Unit) {
        AlertBus.acceptedOrderId.collect { id ->
            if (id != null) {
                OpenOrderRouter.post(id)
                AlertBus.clearAccepted()
            }
        }
    }

    val startValue = start
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (startValue == null) {
            CircularProgressIndicator()
        } else {
            PickerNavHost(graph, startValue)
        }
    }
}
