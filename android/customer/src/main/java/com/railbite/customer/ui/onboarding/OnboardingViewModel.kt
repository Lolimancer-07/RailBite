package com.railbite.customer.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.railbite.customer.AppContainer
import com.railbite.core.api.ApiClient
import com.railbite.core.util.Validators
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class OnboardingUiState(
    val trainNumber: String = "",
    val pnr: String = "",
    val coach: String = "",
    val berth: String = "",
    val diners: Int = 1,
    val autoFilledTrainName: String? = null,
    val connection: Connection = Connection.Testing,
    val error: String? = null,
) {
    val seatValid: Boolean
        get() = Validators.isValidTrainNumber(trainNumber) &&
            Validators.isValidPnr(pnr) &&
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
    private var pnrLookupJob: Job? = null

    init {
        testConnection()
        viewModelScope.launch {
            val savedTrain = container.settings.trainNumber.first()
            val savedPnr = container.settings.pnr.first()
            val savedCoach = container.settings.coach.first()
            val savedBerth = container.settings.berth.first()
            if (savedTrain.isNotBlank() || savedPnr.isNotBlank()) {
                _state.update {
                    it.copy(
                        trainNumber = savedTrain,
                        pnr = savedPnr,
                        coach = savedCoach,
                        berth = if (savedBerth > 0) savedBerth.toString() else "",
                    )
                }
            }
        }
    }

    fun setTrainNumber(value: String) = _state.update {
        it.copy(
            trainNumber = value.filter(Char::isDigit).take(5),
            autoFilledTrainName = null,
            error = null,
        )
    }

    fun setPnr(value: String) {
        val clean = value.filter(Char::isDigit).take(10)
        _state.update { it.copy(pnr = clean, error = null) }
        pnrLookupJob?.cancel()
        if (clean.length == 10) {
            pnrLookupJob = viewModelScope.launch {
                val entry = withContext(Dispatchers.IO) {
                    withTimeoutOrNull(1_500L) {
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

    fun setCoach(value: String) = _state.update { it.copy(coach = value.take(3).uppercase(), error = null) }
    fun setBerth(value: String) = _state.update { it.copy(berth = value.filter(Char::isDigit).take(3), error = null) }
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
            _state.update {
                it.copy(error = "Enter a valid 5-digit train number, 10-digit PNR, coach and berth")
            }
            return
        }
        viewModelScope.launch {
            try {
                container.settings.setSeat(
                    pnr = s.pnr,
                    coach = s.coach,
                    berth = s.berth.toInt(),
                    diners = s.diners,
                    trainNumber = s.trainNumber,
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
