package com.railbite.customer.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.railbite.customer.AppContainer
import com.railbite.core.api.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val serverUrl: String = "",
    val pnr: String = "",
    val coach: String = "",
    val berth: String = "",
    val diners: Int = 1,
    val testState: OnboardingLikeTestState = OnboardingLikeTestState.Idle,
    val saved: Boolean = false,
    val error: String? = null,
    // Bluetooth relay (offline feature)
    val meshRelayEnabled: Boolean = false,
    val meshPending: Int = 0,
    val meshSyncing: Boolean = false,
    val meshSyncMessage: String? = null,
) {
    val seatValid: Boolean
        get() = com.railbite.core.util.Validators.isValidPnr(pnr) &&
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

    init {
        viewModelScope.launch {
            val s = container.settings
            _state.update {
                it.copy(
                    serverUrl = s.serverUrlStored.first(),
                    pnr = s.pnr.first(),
                    coach = s.coach.first(),
                    berth = s.berth.first().let { b -> if (b == 0) "" else b.toString() },
                    diners = s.diners.first(),
                    meshRelayEnabled = s.meshRelayEnabled.first(),
                )
            }
            container.meshSender.refreshPending()
            _state.update { it.copy(meshPending = container.meshSender.pending.value) }
        }
    }

    fun setMeshRelayEnabled(value: Boolean) {
        _state.update { it.copy(meshRelayEnabled = value) }
        viewModelScope.launch { container.settings.setMeshRelayEnabled(value) }
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
    fun setPnr(v: String) = _state.update { it.copy(pnr = v.take(10), saved = false) }
    fun setCoach(v: String) = _state.update { it.copy(coach = v.take(3).uppercase(), saved = false) }
    fun setBerth(v: String) = _state.update { it.copy(berth = v.take(3), saved = false) }
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

    fun save(onSaved: () -> Unit = {}) {
        val s = _state.value
        viewModelScope.launch {
            try {
                container.settings.setServerUrl(ApiClient.normalizeServerUrl(s.serverUrl))
                if (s.seatValid) {
                    container.settings.setSeat(s.pnr, s.coach, s.berth.toInt(), s.diners)
                }
                container.mqtt.disconnect()
                container.mqtt.ensureConnected()
                _state.update { it.copy(saved = true) }
                onSaved()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Could not save") }
            }
        }
    }
}
