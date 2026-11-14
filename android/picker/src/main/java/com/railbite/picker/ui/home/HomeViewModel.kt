package com.railbite.picker.ui.home

import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.railbite.core.api.RailBiteApi
import com.railbite.core.model.MqttOrderAlert
import com.railbite.core.model.Order
import com.railbite.core.model.PickerStats
import com.railbite.core.util.Statuses
import com.railbite.picker.PickerContainer
import com.railbite.picker.alert.RailBiteAlertService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant

data class HomeUiState(
    val staffId: String = "",
    val staffName: String = "",
    val zone: String = "",
    val online: Boolean = false,
    val serviceRunning: Boolean = false,
    val serverUrl: String = "",
    val stats: PickerStats? = null,
    val activeOrder: Order? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

class HomeViewModel(private val container: PickerContainer) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    staffId = container.settings.staffId.first(),
                    staffName = container.settings.staffName.first(),
                    zone = container.settings.staffZone.first(),
                    serverUrl = container.settings.serverUrl.first(),
                )
            }
        }
        // The local toggle lives in settings; the service reports its own running state
        viewModelScope.launch {
            container.settings.online.collect { online ->
                _state.update { it.copy(online = online) }
            }
        }
        viewModelScope.launch {
            RailBiteAlertService.running.collect { running ->
                _state.update { it.copy(serviceRunning = running) }
            }
        }
        refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (isActive) {
                loadOnce()
                delay(5_000)
            }
        }
    }

    private suspend fun loadOnce() {
        try {
            val api = container.client.api()
            val staffId = container.settings.staffId.first()
            val stats = if (staffId.isNotBlank()) api.staffStats(staffId) else null

            var active: Order? = null
            val activeId = container.settings.pickerOrderId.first()
            if (activeId.isNotBlank()) {
                val order = runCatching { api.order(activeId, role = "picker") }.getOrNull()
                if (order != null && !Statuses.isTerminal(order.status)) {
                    active = order
                } else if (order != null) {
                    container.settings.setPickerOrderId("")
                }
            }
            _state.update {
                it.copy(stats = stats, activeOrder = active, loading = false, error = null)
            }
        } catch (_: Exception) {
            _state.update { it.copy(loading = false, error = "Cannot reach server — retrying") }
        }
    }

    /** ONLINE/OFFLINE toggle: starts/stops the alert foreground service and tells the server. */
    fun setOnline(online: Boolean) {
        val app = container.app
        if (online) {
            ContextCompat.startForegroundService(
                app,
                Intent(app, RailBiteAlertService::class.java).setAction(RailBiteAlertService.ACTION_START),
            )
        } else {
            // Plain startService: the service is already foreground; ACTION_STOP just ends it
            app.startService(
                Intent(app, RailBiteAlertService::class.java).setAction(RailBiteAlertService.ACTION_STOP),
            )
        }
        viewModelScope.launch {
            container.settings.setOnline(online)
            runCatching {
                container.client.api().setStaffOnline(
                    container.settings.staffId.first(),
                    RailBiteApi.OnlineRequest(online),
                )
            }
        }
    }

    /** Fake alert so the picker can test the loud alarm without a real passenger order. */
    fun fakeAlert(): MqttOrderAlert = MqttOrderAlert(
        orderId = "test-" + System.currentTimeMillis(),
        coach = "B3",
        berth = 42,
        items = listOf(
            MqttOrderAlert.AlertItem(name = "Veg Biryani", qty = 2, unitPricePaise = 12_000),
            MqttOrderAlert.AlertItem(name = "Masala Chai", qty = 1, unitPricePaise = 3_000),
        ),
        total = 27_000,
        track = "READY",
        attempt = 1,
        expiresAt = Instant.now().plusSeconds(60).toString(),
        pnr = "8492000001",
        diners = 2,
        verified = 1,
    )

    /** Signs out: goes offline server-side and clears the stored login. */
    fun logout(onDone: () -> Unit) {
        _state.update { it.copy(online = false) }
        container.app.startService(
            Intent(container.app, RailBiteAlertService::class.java)
                .setAction(RailBiteAlertService.ACTION_STOP),
        )
        viewModelScope.launch {
            container.settings.setOnline(false)
            runCatching {
                container.client.api().setStaffOnline(
                    container.settings.staffId.first(),
                    RailBiteApi.OnlineRequest(false),
                )
            }
            container.settings.clearStaff()
            onDone()
        }
    }
}
