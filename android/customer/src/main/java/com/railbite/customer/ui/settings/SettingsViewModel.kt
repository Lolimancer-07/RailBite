package com.railbite.customer.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.railbite.customer.AppContainer
import com.railbite.core.api.ApiClient
import com.railbite.core.mesh.BlePacketCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val serverUrl: String = "",
    val trainNumber: String = "",
    val autoFilledTrainName: String? = null,
    val pnr: String = "",
    val coach: String = "",
    val berth: String = "",
    val diners: Int = 1,
    val testState: OnboardingLikeTestState = OnboardingLikeTestState.Idle,
    val saved: Boolean = false,
    val error: String? = null,
    // Bluetooth relay (offline BLE mesh)
    val meshRelayEnabled: Boolean = false,
    val meshDebugEnabled: Boolean = false,
    val meshKey: String = BlePacketCodec.RAILBITE_MESH_KEY,
    val meshPending: Int = 0,
    val meshSyncing: Boolean = false,
    val meshSyncMessage: String? = null,
) {
    val seatValid: Boolean
        get() = com.railbite.core.util.Validators.isValidTrainNumber(trainNumber) &&
            com.railbite.core.util.Validators.isValidPnr(pnr) &&
            com.railbite.core.util.Validators.isValidCoach(coach) &&
            berth.toIntOrNull()?.let { com.railbite.core.util.Validators.isValidBerth(it) } == true
}

sealed interface OnboardingLikeTestState {
    data object Idle : OnboardingLikeTestState
    data object Testing : OnboardingLikeTestState
    data object Ok : OnboardingLikeTestState
    data class Fail(val message: String) : OnboardingLikeTestState
}

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private var pnrLookupJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            val s = container.settings
            _state.update {
                it.copy(
                    serverUrl = s.serverUrlStored.first(),
                    trainNumber = s.trainNumber.first(),
                    pnr = s.pnr.first(),
                    coach = s.coach.first(),
                    berth = s.berth.first().let { b -> if (b == 0) "" else b.toString() },
                    diners = s.diners.first(),
                    meshRelayEnabled = s.meshRelayEnabled.first(),
                    meshKey = s.meshKey.first(),
                )
            }
            container.meshSender.refreshPending()
            _state.update { it.copy(meshPending = container.meshSender.pending.value) }
        }
    }

    fun setMeshRelayEnabled(value: Boolean) {
        _state.update { it.copy(meshRelayEnabled = value) }
        viewModelScope.launch {
            container.settings.setMeshRelayEnabled(value)
            container.transportRouter.refreshRadioOnly()
        }
    }

    fun setMeshDebugEnabled(value: Boolean) {
        _state.update { it.copy(meshDebugEnabled = value) }
    }

    fun setMeshKey(value: String) {
        _state.update { it.copy(meshKey = value.take(32), saved = false) }
    }

    /** Manual "sync relay orders now" — also runs automatically on Home resume. */
    fun syncMeshOrders() {
        _state.update { it.copy(meshSyncing = true, meshSyncMessage = null) }
        viewModelScope.launch {
            // runCatching: any unexpected failure surfaces as a message, never a crash.
            val n = runCatching { container.meshSender.syncPending() }.getOrDefault(0)
            container.meshSender.refreshPending()
            _state.update {
                it.copy(
                    meshSyncing = false,
                    meshPending = container.meshSender.pending.value,
                    meshSyncMessage = if (n > 0) "Sent $n relay order${if (n == 1) "" else "s"} to the cloud" else "Nothing to sync (or no internet yet)",
                )
            }
        }
    }

    fun setServerUrl(v: String) = _state.update { it.copy(serverUrl = v, testState = OnboardingLikeTestState.Idle, saved = false) }
    fun setTrainNumber(v: String) = _state.update {
        it.copy(trainNumber = v.filter(Char::isDigit).take(5), autoFilledTrainName = null, saved = false, error = null)
    }

    fun setPnr(v: String) {
        val clean = v.filter(Char::isDigit).take(10)
        _state.update { it.copy(pnr = clean, saved = false, error = null) }
        pnrLookupJob?.cancel()
        if (clean.length == 10) {
            pnrLookupJob = viewModelScope.launch {
                val entry = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    kotlinx.coroutines.withTimeoutOrNull(1_500L) {
                        runCatching { container.client.api().manifestByPnr(clean) }.getOrNull()
                    }
                }
                if (entry != null && _state.value.pnr == clean) {
                    _state.update { cur ->
                        cur.copy(
                            trainNumber = entry.trainNumber,
                            autoFilledTrainName = entry.trainName,
                            coach = cur.coach.ifBlank { entry.coach },
                            berth = cur.berth.ifBlank { entry.berth.toString() },
                        )
                    }
                }
            }
        }
    }

    fun setCoach(v: String) = _state.update { it.copy(coach = v.take(3).uppercase(), saved = false) }
    fun setBerth(v: String) = _state.update { it.copy(berth = v.filter(Char::isDigit).take(3), saved = false) }
    fun setDiners(v: Int) = _state.update { it.copy(diners = v.coerceIn(1, 8), saved = false) }

    fun testConnection() {
        val url = ApiClient.normalizeServerUrl(_state.value.serverUrl)
        if (url.isBlank()) {
            _state.update { it.copy(testState = OnboardingLikeTestState.Fail("Enter a server URL first")) }
            return
        }
        _state.update { it.copy(testState = OnboardingLikeTestState.Testing) }
        viewModelScope.launch {
            try {
                ApiClient.testConnection(url)
                _state.update { it.copy(testState = OnboardingLikeTestState.Ok) }
            } catch (_: Exception) {
                _state.update { it.copy(testState = OnboardingLikeTestState.Fail("Cannot reach server")) }
            }
        }
    }

    /** Signs out of the cloud session and clears local credentials. */
    fun logout(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching { container.auth.logout() }
            onDone()
        }
    }

    fun save(onSaved: () -> Unit = {}) {
        val s = _state.value
        viewModelScope.launch {
            try {
                container.settings.setServerUrl(ApiClient.normalizeServerUrl(s.serverUrl))
                container.settings.setMeshKey(s.meshKey)
                if (com.railbite.core.util.Validators.isValidTrainNumber(s.trainNumber)) {
                    container.settings.setTrainNumber(s.trainNumber)
                }
                if (s.seatValid) {
                    container.settings.setSeat(s.pnr, s.coach, s.berth.toInt(), s.diners, s.trainNumber)
                }
                container.mqtt.disconnect()
                container.mqtt.ensureConnected()
                container.transportRouter.probeNow()
                _state.update { it.copy(saved = true) }
                onSaved()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Could not save") }
            }
        }
    }
}
