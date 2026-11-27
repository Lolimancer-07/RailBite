package com.railbite.picker.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.railbite.core.api.ApiClient
import com.railbite.core.api.RailBiteApi
import com.railbite.core.auth.AuthSession
import com.railbite.core.auth.AuthUser
import com.railbite.core.auth.DemoAuth
import com.railbite.core.util.Validators
import com.railbite.picker.PickerContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

data class LoginUiState(
    val staffId: String = "",
    val password: String = "",
    val trainNumber: String = "12345",
    val trainNumberEdited: Boolean = false,
    val useFullPassword: Boolean = false,
    val serverUrl: String = "",
    val serverUrlStored: String = "",
    val mqttWsUrl: String = "",
    val serverReachable: Boolean? = null,
    val healthTripId: String = "",
    val healthLatencyMs: Long? = null,
    val busy: Boolean = false,
    val error: String? = null,
)

class LoginViewModel(private val container: PickerContainer) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.settings.serverUrl.collect { url ->
                _state.update { it.copy(serverUrl = url) }
                pingServer(url)
            }
        }
        viewModelScope.launch {
            container.settings.serverUrlStored.collect { stored ->
                _state.update { it.copy(serverUrlStored = stored) }
            }
        }
        viewModelScope.launch {
            container.settings.mqttWsUrl.collect { wsUrl ->
                _state.update { it.copy(mqttWsUrl = wsUrl) }
            }
        }
        viewModelScope.launch {
            val savedTrain = container.settings.activeTrainNumber.first()
            if (Validators.isValidTrainNumber(savedTrain)) {
                _state.update { it.copy(trainNumber = savedTrain) }
            }
        }
    }

    fun onStaffIdChange(value: String) {
        val clean = value.trim().uppercase()
        _state.update { cur ->
            val suggestedTrain = when {
                cur.trainNumberEdited -> cur.trainNumber
                clean == "C3" || clean == "C4" -> "12423"
                clean == "C1" || clean == "C2" -> "12345"
                else -> cur.trainNumber
            }
            cur.copy(staffId = clean, trainNumber = suggestedTrain, error = null)
        }
    }

    fun onTrainNumberChange(value: String) {
        val clean = Validators.normalizeTrainNumber(value)
            .filter { it.isLetterOrDigit() }
            .take(10)
        _state.update { it.copy(trainNumber = clean, trainNumberEdited = true, error = null) }
    }

    fun onPasswordChange(value: String) {
        val next = if (_state.value.useFullPassword) {
            value
        } else {
            value.filter { it.isDigit() }.take(4)
        }
        _state.update { it.copy(password = next, error = null) }
    }

    fun onTogglePasswordMode(useFull: Boolean) {
        _state.update { current ->
            val trimmed = if (useFull) {
                current.password
            } else {
                current.password.filter { it.isDigit() }.take(4)
            }
            current.copy(useFullPassword = useFull, password = trimmed, error = null)
        }
    }

    fun pingServer(targetUrl: String = _state.value.serverUrl) {
        val clean = targetUrl.trim()
        if (clean.isBlank()) {
            _state.update {
                it.copy(serverReachable = false, healthTripId = "", healthLatencyMs = null)
            }
            return
        }
        viewModelScope.launch {
            val started = System.currentTimeMillis()
            val result = runCatching { ApiClient.testConnection(clean) }
            val rtt = (System.currentTimeMillis() - started).coerceAtLeast(1)
            result.fold(
                onSuccess = { health ->
                    _state.update {
                        it.copy(
                            serverReachable = health.ok,
                            healthTripId = health.tripId,
                            healthLatencyMs = rtt,
                        )
                    }
                },
                onFailure = {
                    _state.update {
                        it.copy(
                            serverReachable = false,
                            healthTripId = "",
                            healthLatencyMs = null,
                        )
                    }
                },
            )
        }
    }

    fun updateServerConfig(rawServerUrl: String, rawMqttWsUrl: String) {
        val normalized = ApiClient.normalizeServerUrl(rawServerUrl)
        viewModelScope.launch {
            container.settings.setServerUrl(normalized)
            container.settings.setMqttWsUrl(rawMqttWsUrl.trim())
            val effective = container.settings.serverUrl.first()
            _state.update {
                it.copy(
                    serverUrl = effective,
                    serverUrlStored = normalized,
                    mqttWsUrl = rawMqttWsUrl.trim(),
                    serverReachable = null,
                    error = null,
                )
            }
            pingServer(effective)
        }
    }

    fun submit(onLoggedIn: () -> Unit) {
        val s = _state.value
        if (s.busy || s.staffId.isBlank() || s.password.length < 4) return
        val selectedTrain = Validators.normalizeTrainNumber(s.trainNumber)
        if (!Validators.isValidTrainNumber(selectedTrain)) {
            _state.update { it.copy(error = "Enter a valid train number (1–10 chars, e.g. 12345 or 777)") }
            return
        }
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val lanReachable = container.transportRouter.checkLanReachable()
            if (lanReachable) {
                try {
                    // Staff IDs are canonical lowercase in the seed (c1-c4).
                    val login = container.auth.staffLogin(s.staffId, s.password, selectedTrain)
                    val activeTrain = selectedTrain.ifBlank { login.trainNumber }
                    container.settings.setStaffLogin(
                        staffId = login.staffId,
                        name = login.name,
                        zone = login.zoneCoaches.joinToString(", "),
                        trainNumber = activeTrain,
                    )
                    launch {
                        com.railbite.picker.fcm.FcmTokenRegistrar.registerCurrentToken(container)
                    }
                    runCatching {
                        container.client.api().setPickerStatus(
                            login.staffId,
                            RailBiteApi.PickerStatusRequest(online = false, trainNumber = activeTrain),
                        )
                    }.onFailure {
                        runCatching {
                            container.client.api().setStaffOnline(
                                login.staffId,
                                RailBiteApi.OnlineRequest(false, activeTrain),
                            )
                        }
                    }
                    container.settings.setOnline(false)
                    _state.update { it.copy(busy = false) }
                    onLoggedIn()
                    return@launch
                } catch (e: HttpException) {
                    if (e.code() == 400 || e.code() == 401) {
                        _state.update { it.copy(busy = false, error = friendly(e)) }
                        return@launch
                    }
                    // Other errors fall back to offline shift roster check if applicable
                } catch (_: Exception) {
                    // Network dropped mid-login -> try offline crew sign-in
                }
            }

            // Offline crew shift sign-in (Airplane Mode / zero Wi-Fi)
            val normalizedId = s.staffId.trim().lowercase()
            val secret = s.password.trim()
            val profile = OFFLINE_CREW_ROSTER[normalizedId]
            val validSecret = secret == DemoAuth.DEMO_STAFF_PIN || secret == "railbite123"
            if (profile != null && validSecret) {
                container.authStore.save(
                    AuthSession(
                        accessToken = DemoAuth.ACCESS_TOKEN,
                        refreshToken = DemoAuth.REFRESH_TOKEN,
                        user = AuthUser(
                            id = normalizedId,
                            name = profile.first,
                            role = "picker",
                            staffId = normalizedId,
                            zoneCoaches = profile.second,
                        ),
                    ),
                )
                container.settings.setStaffLogin(
                    staffId = normalizedId,
                    name = profile.first,
                    zone = profile.second.joinToString(", "),
                    trainNumber = selectedTrain,
                )
                container.settings.setOnline(false)
                _state.update { it.copy(busy = false) }
                onLoggedIn()
            } else {
                _state.update {
                    it.copy(
                        busy = false,
                        error = if (profile == null) {
                            "Offline shift requires c1–c4 (PIN 1234 or password railbite123)"
                        } else {
                            "Invalid offline PIN/password (expected 1234 or railbite123)"
                        },
                    )
                }
            }
        }
    }

    private companion object {
        val OFFLINE_CREW_ROSTER = mapOf(
            "c1" to ("Ramesh Kumar" to listOf("B1", "B2")),
            "c2" to ("Suresh Sharma" to listOf("B3", "B4")),
            "c3" to ("Amit Verma" to listOf("B1", "B2", "S1", "S2")),
            "c4" to ("Deepak Patel" to listOf("B3", "B4", "S3", "S4")),
        )
    }

    private suspend fun friendly(e: Exception): String {
        val code = (e as? HttpException)?.code()
        return when {
            code == 401 || e.message?.contains("401") == true -> "Invalid staff ID or PIN/password"
            code == 400 || e.message?.contains("400") == true -> "Invalid credentials format"
            container.settings.serverUrl.first().isBlank() -> "Set the server address first"
            else -> "Cannot reach server — check Wi-Fi & server IP"
        }
    }
}
