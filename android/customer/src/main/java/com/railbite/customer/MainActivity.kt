package com.railbite.customer

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.railbite.core.mesh.BleMeshService
import com.railbite.core.mesh.BlePermissions
import com.railbite.core.mesh.TransportMode
import com.railbite.customer.BuildConfig
import com.railbite.customer.ui.components.LocalBleActionRequester
import com.railbite.customer.ui.theme.RailBiteCustomerTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val container = (application as? RailBiteCustomerApp)?.container ?: return@registerForActivityResult
        container.transportRouter.refreshRadioOnly()
        syncScope.launch { ensureMeshStartedIfReady(container) }
    }

    private val btPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        val container = (application as? RailBiteCustomerApp)?.container ?: return@registerForActivityResult
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
        val container = (application as RailBiteCustomerApp).container

        container.transportRouter.startMonitoring()
        container.meshSender.startLoop()

        // Automatically sync offline BLE orders when LAN becomes reachable
        syncScope.launch {
            container.transportRouter.status.collect { st ->
                if (st.mode == TransportMode.LAN_CONNECTED) {
                    runCatching { container.meshSender.syncPending() }
                }
            }
        }

        // Request BLE runtime permissions on launch and start BleMeshService once permitted & BT ON
        lifecycleScope.launch {
            val missing = BlePermissions.requiredPermissions(this@MainActivity)
            if (missing.isNotEmpty()) {
                btPermissions.launch(missing.toTypedArray())
            } else {
                ensureMeshStartedIfReady(container)
            }
        }

        setContent {
            RailBiteCustomerTheme {
                CompositionLocalProvider(
                    LocalBleActionRequester provides { requestBleEnableOrPermissions() },
                ) {
                    CustomerRoot(container)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val container = (application as? RailBiteCustomerApp)?.container ?: return
        container.transportRouter.refreshRadioOnly()
        syncScope.launch {
            container.transportRouter.probeNow()
            ensureMeshStartedIfReady(container)
        }
    }

    fun requestBleEnableOrPermissions() {
        val missing = BlePermissions.requiredPermissions(this)
        if (missing.isNotEmpty()) {
            btPermissions.launch(missing.toTypedArray())
        } else if (!BlePermissions.isBluetoothEnabled(this)) {
            promptEnableBluetooth()
        } else {
            val container = (application as? RailBiteCustomerApp)?.container ?: return
            syncScope.launch { ensureMeshStartedIfReady(container) }
        }
    }

    private fun promptEnableBluetooth() {
        // BLUETOOTH_CONNECT is required on Android 12+ before calling ACTION_REQUEST_ENABLE
        if (BlePermissions.requiredPermissions(this).isEmpty()) {
            runCatching {
                enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
        }
    }

    private suspend fun ensureMeshStartedIfReady(container: AppContainer) {
        val relayOn = runCatching { container.settings.meshRelayEnabled.first() }.getOrDefault(true)
        if (relayOn && BlePermissions.isReadyForMesh(this)) {
            val activeOrder = runCatching { container.settings.activeOrderId.first() }.getOrDefault("")
            BleMeshService.start(this, lowLatency = activeOrder.isNotBlank())
        }
    }

    override fun onDestroy() {
        syncScope.cancel()
        super.onDestroy()
    }
}

@Composable
private fun CustomerRoot(container: AppContainer) {
    val graph = rememberGraph(container)
    val start by graph.startDestination.collectAsState()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (start == null) {
            CircularProgressIndicator()
        } else {
            CustomerNavHost(graph, start!!)
        }
    }
}
