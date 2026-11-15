package com.railbite.customer.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.railbite.customer.AppContainer
import com.railbite.core.api.ApiClient
import com.railbite.core.util.Validators
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val pnr: String = "",
    val coach: String = "",
    val berth: String = "",
    val diners: Int = 1,
    val connection: Connection = Connection.Testing,
    val error: String? = null,
) {
    val seatValid: Boolean
        get() = Validators.isValidPnr(pnr) &&
            Validators.isValidCoach(coach) &&
            berth.toIntOrNull()?.let { Validators.isValidBerth(it) } == true

    /** Baked-in cloud server; informational only (non-blocking). */
    sealed interface Connection {
        data object Testing : Connection
        data object Ok : Connection
        data object Fail : Connection
    }
}

class OnboardingViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    init {
        testConnection()
    }

    fun setPnr(value: String) = _state.update { it.copy(pnr = value.take(10)) }
    fun setCoach(value: String) = _state.update { it.copy(coach = value.take(3).uppercase()) }
    fun setBerth(value: String) = _state.update { it.copy(berth = value.take(3)) }
    fun setDiners(value: Int) = _state.update { it.copy(diners = value.coerceIn(1, 8)) }

    fun testConnection() {
        _state.update { it.copy(connection = OnboardingUiState.Connection.Testing) }
        viewModelScope.launch {
            try {
                ApiClient.testConnection(container.settings.serverUrl.first())
                _state.update { it.copy(connection = OnboardingUiState.Connection.Ok) }
            } catch (_: Exception) {
                _state.update { it.copy(connection = OnboardingUiState.Connection.Fail) }
            }
        }
    }

    fun complete(onDone: () -> Unit) {
        val s = _state.value
        if (!s.seatValid) {
            _state.update { it.copy(error = "Enter a valid 10-digit PNR, coach and berth") }
            return
        }
        viewModelScope.launch {
            try {
                container.settings.setSeat(
                    s.pnr,
                    s.coach,
                    s.berth.toInt(),
                    s.diners,
                )
                container.settings.setOnboardingDone()
                container.mqtt.ensureConnected()
                onDone()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Could not save") }
            }
        }
    }
}
