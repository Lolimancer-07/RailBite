package com.railbite.customer.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.railbite.customer.AppContainer
import com.railbite.core.api.ApiClient
import com.railbite.core.util.Validators
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val step: Int = 0, // 0 = server url, 1 = seat
    val serverUrl: String,
    val testState: TestState = TestState.Idle,
    val pnr: String = "",
    val coach: String = "",
    val berth: String = "",
    val diners: Int = 1,
    val error: String? = null,
) {
    val seatValid: Boolean
        get() = Validators.isValidPnr(pnr) &&
            Validators.isValidCoach(coach) &&
            berth.toIntOrNull()?.let { Validators.isValidBerth(it) } == true

    sealed interface TestState {
        data object Idle : TestState
        data object Testing : TestState
        data object Ok : TestState
        data class Fail(val message: String) : TestState
    }
}

class OnboardingViewModel(private val container: AppContainer, defaultServerUrl: String) :
    ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState(serverUrl = defaultServerUrl))
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun setServerUrl(value: String) = _state.update { it.copy(serverUrl = value, testState = OnboardingUiState.TestState.Idle) }
    fun setPnr(value: String) = _state.update { it.copy(pnr = value.take(10)) }
    fun setCoach(value: String) = _state.update { it.copy(coach = value.take(3).uppercase()) }
    fun setBerth(value: String) = _state.update { it.copy(berth = value.take(3)) }
    fun setDiners(value: Int) = _state.update { it.copy(diners = value.coerceIn(1, 8)) }

    fun testConnection() {
        val url = ApiClient.normalizeServerUrl(_state.value.serverUrl)
        if (url.isBlank()) {
            _state.update { it.copy(testState = OnboardingUiState.TestState.Fail("Enter a server URL first")) }
            return
        }
        _state.update { it.copy(testState = OnboardingUiState.TestState.Testing) }
        viewModelScope.launch {
            try {
                ApiClient.testConnection(url)
                _state.update { it.copy(testState = OnboardingUiState.TestState.Ok) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(testState = OnboardingUiState.TestState.Fail("Cannot reach server — check the URL and Wi-Fi"))
                }
            }
        }
    }

    fun nextStep() {
        if (_state.value.testState is OnboardingUiState.TestState.Ok) {
            _state.update { it.copy(step = 1, error = null) }
        }
    }

    fun backToServer() = _state.update { it.copy(step = 0) }

    fun complete(onDone: () -> Unit) {
        val s = _state.value
        if (!s.seatValid) {
            _state.update { it.copy(error = "Enter a valid 10-digit PNR, coach and berth") }
            return
        }
        viewModelScope.launch {
            try {
                container.settings.setServerUrl(ApiClient.normalizeServerUrl(s.serverUrl))
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
