package com.railbite.picker

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import android.os.StrictMode
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.railbite.core.mesh.BleMeshService
import com.railbite.core.mesh.BlePermissions
import com.railbite.core.mesh.TransportMode
import com.railbite.picker.BuildConfig
import com.railbite.picker.alert.AlertBus
import com.railbite.picker.alert.AlertForegroundService
import com.railbite.picker.alert.Notifications
import com.railbite.picker.ui.components.LocalPickerBleActionRequester
import com.railbite.picker.ui.theme.RailBitePickerTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_ORDER_ID = "openOrderId"
    }

    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val container = (application as? RailBitePickerApp)?.container ?: return@registerForActivityResult
        container.transportRouter.refreshRadioOnly()
        syncScope.launch { ensureMeshStartedIfReady(container) }
    }

    private val btPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        val container = (application as? RailBitePickerApp)?.container ?: return@registerForActivityResult
        container.transportRouter.refreshRadioOnly()
        if (BlePermissions.requiredPermissions(this).isEmpty()) {
            if (!BlePermissions.isBluetoothEnabled(this)) {
                promptEnableBluetooth()
            } else {
                syncScope.launch { ensureMeshStartedIfReady(container) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Log-only thread/vm policy while debugging hangs (e.g. Main-thread BLE scan / MQTT).
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build())
        }
        val container = (application as RailBitePickerApp).container
        Notifications.createChannels(this)
        container.transportRouter.startMonitoring()

        // Automatically sync offline BLE orders & cash entries when LAN returns
        syncScope.launch {
            container.transportRouter.status.collect { st ->
                if (st.mode == TransportMode.LAN_CONNECTED) {
                    val staffId = container.settings.staffId.first()
                    if (staffId.isNotBlank()) {
                        runCatching { container.meshGateway.replayPending() }
                    }
                }
            }
        }

        lifecycleScope.launch {
            val missing = BlePermissions.requiredPermissions(this@MainActivity)
            if (missing.isNotEmpty()) {
                btPermissions.launch(missing.toTypedArray())
            } else {
                ensureMeshStartedIfReady(container)
            }
        }

        val openOrderId = intent?.getStringExtra(EXTRA_OPEN_ORDER_ID)
        setContent {
            RailBitePickerTheme {
                CompositionLocalProvider(
                    LocalPickerBleActionRequester provides { requestBleEnableOrPermissions() },
                ) {
                    PickerRoot(container, initialOpenOrderId = openOrderId)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val container = (application as? RailBitePickerApp)?.container ?: return
        container.transportRouter.refreshRadioOnly()
        syncScope.launch {
            container.transportRouter.probeNow()
            ensureMeshStartedIfReady(container)
        }
    }

    override fun onDestroy() {
        syncScope.cancel()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_OPEN_ORDER_ID)?.let {
            OpenOrderRouter.post(it)
        }
    }

    fun requestBleEnableOrPermissions() {
        val missing = BlePermissions.requiredPermissions(this)
        if (missing.isNotEmpty()) {
            btPermissions.launch(missing.toTypedArray())
        } else if (!BlePermissions.isBluetoothEnabled(this)) {
            promptEnableBluetooth()
        } else {
            val container = (application as? RailBitePickerApp)?.container ?: return
            syncScope.launch { ensureMeshStartedIfReady(container) }
        }
    }

    private fun promptEnableBluetooth() {
        if (BlePermissions.requiredPermissions(this).isNotEmpty()) return
        runCatching {
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
    }

    private suspend fun ensureMeshStartedIfReady(container: PickerContainer) {
        if (!BlePermissions.isReadyForMesh(this)) return
        val online = runCatching { container.settings.online.first() }.getOrDefault(false)
        val staffId = runCatching { container.settings.staffId.first() }.getOrDefault("")
        if (staffId.isNotBlank() && online) {
            BleMeshService.start(this, lowLatency = true)
            container.meshGateway.start(dispatchAlerts = true)
        } else if (runCatching { container.settings.meshRelayEnabled.first() }.getOrDefault(true)) {
            BleMeshService.start(this, lowLatency = false)
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

    LaunchedEffect(Unit) {
        val staffId = container.settings.staffId.first()
        if (staffId.isNotBlank()) {
            ContextCompat.startForegroundService(
                container.app,
                Intent(container.app, AlertForegroundService::class.java)
                    .setAction(AlertForegroundService.ACTION_START),
            )
        }
        if (initialOpenOrderId != null) OpenOrderRouter.post(initialOpenOrderId)
    }

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
