package com.railbite.picker.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.railbite.core.api.ApiClient
import com.railbite.core.api.RailBiteApi
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
    }

    fun onStaffIdChange(value: String) {
        _state.update { it.copy(staffId = value.trim().uppercase(), error = null) }
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
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                // Staff IDs are canonical lowercase in the seed (c1-c4). The field
                // uppercases for display; AuthRepository lowercases before the request
                // so the server's exact-match lookup hits.
                val login = container.auth.staffLogin(s.staffId, s.password)
                container.settings.setStaffLogin(
                    staffId = login.staffId,
                    name = login.name,
                    zone = login.zoneCoaches.joinToString(", "),
                )
                // Register FCM device token with server on login
                launch {
                    com.railbite.picker.fcm.FcmTokenRegistrar.registerCurrentToken(container)
                }
                // The server marks fresh logins online; snap it back in sync with the app
                // toggle (OFF) so alerts are only dispatched once the picker goes online.
                runCatching {
                    container.client.api().setStaffOnline(login.staffId, RailBiteApi.OnlineRequest(false))
                }
                container.settings.setOnline(false)
                _state.update { it.copy(busy = false) }
                onLoggedIn()
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = friendly(e)) }
            }
        }
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
