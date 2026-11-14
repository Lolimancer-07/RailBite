package com.railbite.picker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.railbite.core.ui.theme.RailBiteTheme
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as RailBitePickerApp).container
        Notifications.createChannels(this)

        val openOrderId = intent?.getStringExtra(EXTRA_OPEN_ORDER_ID)
        setContent {
            RailBiteTheme {
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

    // Restart the foreground service if the picker was ONLINE when the app closed
    LaunchedEffect(Unit) {
        val online = container.settings.online.first()
        if (online) {
            androidx.core.content.ContextCompat.startForegroundService(
                container.app,
                Intent(container.app, RailBiteAlertService::class.java)
                    .setAction(RailBiteAlertService.ACTION_START),
            )
        }
        if (initialOpenOrderId != null) OpenOrderRouter.post(initialOpenOrderId)
    }

    // If an alert was accepted, jump straight to the progress screen
    LaunchedEffect(Unit) {
        AlertBus.acceptedOrderId.collect { id ->
            if (id != null) {
                OpenOrderRouter.post(id)
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
