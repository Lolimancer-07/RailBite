package com.railbite.picker.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.railbite.core.api.RailBiteApi
import com.railbite.core.model.StaffLoginRequest
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
    val pin: String = "",
    val busy: Boolean = false,
    val error: String? = null,
)

class LoginViewModel(private val container: PickerContainer) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onStaffIdChange(value: String) {
        _state.update { it.copy(staffId = value.trim().uppercase(), error = null) }
    }

    fun onPinChange(value: String) {
        _state.update { it.copy(pin = value.filter(Char::isDigit).take(4), error = null) }
    }

    fun submit(onLoggedIn: () -> Unit) {
        val s = _state.value
        if (s.busy || s.staffId.isBlank() || s.pin.length < 4) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                // Staff IDs are canonical lowercase in the seed (c1-c4). The field
                // uppercases for display only — lowercase before the request or the
                // server's exact-match lookup 401s ("C1" ≠ "c1").
                val login = container.client.api().staffLogin(StaffLoginRequest(s.staffId.lowercase(), s.pin))
                container.settings.setStaffLogin(
                    staffId = login.staffId,
                    name = login.name,
                    token = login.token,
                    zone = login.zoneCoaches.joinToString(", "),
                )
                // Register FCM device token with server on login
                launch {
                    com.railbite.picker.fcm.FcmTokenRegistrar.registerCurrentToken(container)
                }
                // The server marks fresh logins online; snap it back in sync with the app
                // toggle (OFF) so alerts are only dispatched once the picker goes online.
                runCatching {
                    container.client.api()
                        .setStaffOnline(login.staffId, RailBiteApi.OnlineRequest(false))
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
            code == 401 || e.message?.contains("401") == true -> "Invalid staff ID or PIN"
            code == 400 || e.message?.contains("400") == true -> "Invalid credentials format"
            container.settings.serverUrl.first().isBlank() -> "Set the server address first"
            else -> "Cannot reach server — check the server address"
        }
    }
}
