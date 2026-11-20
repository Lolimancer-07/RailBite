package com.railbite.picker.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val busy: Boolean = false,
    val error: String? = null,
)

class LoginViewModel(private val container: PickerContainer) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onStaffIdChange(value: String) {
        _state.update { it.copy(staffId = value.trim().uppercase(), error = null) }
    }

    fun onPasswordChange(value: String) {
        _state.update { it.copy(password = value, error = null) }
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
            code == 401 || e.message?.contains("401") == true -> "Invalid staff ID or password"
            code == 400 || e.message?.contains("400") == true -> "Invalid credentials format"
            container.settings.serverUrl.first().isBlank() -> "Set the server address first"
            else -> "Cannot reach server — check the server address"
        }
    }
}
