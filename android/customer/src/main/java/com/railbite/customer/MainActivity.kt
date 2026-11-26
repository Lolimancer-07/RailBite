package com.railbite.customer

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.railbite.customer.ui.theme.RailBiteCustomerTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** Outbox sync runs here: app open + every time connectivity returns. */
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var netCallback: ConnectivityManager.NetworkCallback? = null

    /** Bluetooth permissions gate the offline relay — ask once at first open. */
    private val btPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { /* grants persist; placement re-checks */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as RailBiteCustomerApp).container

        // Relay orders placed offline flow to the cloud on their own: sync at app
        // open and again whenever connectivity returns.
        syncScope.launch { runCatching { container.meshSender.syncPending() } }
        // Mesh duty cycle: re-broadcast pending orders until the pantry confirms and
        // listen for its STATUS announcements (accepted / delivered) — runs for the
        // app's lifetime and idles itself when nothing is in flight.
        container.meshSender.startLoop()
        registerConnectivitySync()

        // The Bluetooth relay is on by default — make sure the runtime permissions
        // (Nearby devices on Android 12+) are granted before the first offline order.
        lifecycleScope.launch {
            val relayOn = runCatching { container.settings.meshRelayEnabled.first() }.getOrDefault(false)
            if (relayOn) {
                val missing = container.meshTransport.requiredPermissions()
                if (missing.isNotEmpty()) btPermissions.launch(missing.toTypedArray())
            }
        }

        setContent {
            RailBiteCustomerTheme {
                CustomerRoot(container)
            }
        }
    }

    private fun registerConnectivitySync() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val container = (application as? RailBiteCustomerApp)?.container ?: return
                syncScope.launch { runCatching { container.meshSender.syncPending() } }
            }
        }
        runCatching {
            cm.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(),
                callback,
            )
            netCallback = callback
        }
    }

    override fun onDestroy() {
        netCallback?.let { cb ->
            (getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)?.let { cm ->
                runCatching { cm.unregisterNetworkCallback(cb) }
            }
        }
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
