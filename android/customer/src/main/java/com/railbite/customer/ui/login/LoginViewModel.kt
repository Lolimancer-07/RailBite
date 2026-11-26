package com.railbite.customer.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.railbite.core.auth.AuthSession
import com.railbite.core.auth.AuthUser
import com.railbite.core.auth.DemoAuth
import com.railbite.customer.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeUnit
import kotlin.random.Random

enum class CustomerLoginStage {
    PHONE,
    OTP,
}

data class CustomerLoginUiState(
    val stage: CustomerLoginStage = CustomerLoginStage.PHONE,
    val phone: String = "",
    val otp: String = "",
    val demoOtp: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val info: String? = null,
) {
    val phoneValid: Boolean get() = phone.length == 10 && phone.first() in '6'..'9'
    val codeValid: Boolean get() = otp.length == 6
}

/**
 * Customer sign-in — dummy/demo phone + OTP login page.
 *
 * The 6-digit OTP is generated on-device and shown on screen; the phone number
 * is a real 10-digit Indian mobile number. The real Firebase Phone Auth SMS
 * flow is deferred (see git history) — this build intentionally contains no
 * Firebase SMS logic.
 *
 * After a successful demo code check, the app tries the server's dev-login
 * (real JWT); if that is unavailable it keeps a local demo session so the app
 * can continue without SMS.
 */
class CustomerLoginViewModel(
    private val container: AppContainer,
    private val onLoggedIn: () -> Unit,
) : ViewModel() {

    private val _state = MutableStateFlow(CustomerLoginUiState())
    val state: StateFlow<CustomerLoginUiState> = _state.asStateFlow()

    fun onPhoneChange(value: String) {
        _state.update { it.copy(phone = value.filter(Char::isDigit).take(10), error = null) }
    }

    fun onOtpChange(value: String) {
        _state.update { it.copy(otp = value.filter(Char::isDigit).take(6), error = null) }
    }

    /** Demo mode: generate an OTP on-device and move to the OTP stage. */
    fun sendOtp() {
        val s = _state.value
        if (s.busy) return
        if (s.phone.length != 10 || s.phone.first() !in '6'..'9') {
            _state.update { it.copy(error = "Enter a valid 10-digit Indian mobile number") }
            return
        }
        _state.update { it.copy(busy = true, error = null, info = "Generating demo code…") }
        val code = Random.nextInt(1_000_000).toString().padStart(6, '0')
        _state.update {
            it.copy(
                stage = CustomerLoginStage.OTP,
                otp = "",
                demoOtp = code,
                busy = false,
                info = "Demo code generated on this device: $code",
            )
        }
    }

    fun resendOtp() {
        val s = _state.value
        if (s.busy) return
        val code = Random.nextInt(1_000_000).toString().padStart(6, '0')
        _state.update { it.copy(otp = "", demoOtp = code, busy = false, error = null, info = "A new demo code was generated.") }
    }

    fun changeNumber() {
        _state.update { it.copy(stage = CustomerLoginStage.PHONE, otp = "", demoOtp = "", error = null, info = null) }
    }

    fun verifyCode() {
        val s = _state.value
        if (s.busy) return
        if (s.stage != CustomerLoginStage.OTP) {
            _state.update { it.copy(error = "Send the OTP first") }
            return
        }
        if (s.otp.length != 6) {
            _state.update { it.copy(error = "Enter the 6-digit OTP") }
            return
        }
        if (s.otp != s.demoOtp) {
            _state.update { it.copy(otp = "", error = "Incorrect OTP. Check the demo code and try again.", info = null) }
            return
        }
        _state.update { it.copy(busy = true, error = null, info = null) }
        viewModelScope.launch { demoSignIn(s.phone) }
    }

    private suspend fun demoSignIn(phoneDigits: String) {
        val phone = "+91$phoneDigits"
        val serverSession = runCatching { withTimeout(12_000) { container.auth.devLogin(phone) } }.getOrNull()
        if (serverSession == null) {
            container.authStore.save(
                AuthSession(
                    accessToken = DemoAuth.ACCESS_TOKEN,
                    refreshToken = DemoAuth.REFRESH_TOKEN,
                    user = AuthUser(
                        id = "demo-${phoneDigits.takeLast(4)}",
                        name = "Demo Passenger",
                        phone = phone,
                        role = "customer",
                    ),
                ),
            )
        }
        _state.update { it.copy(busy = false) }
        onLoggedIn()
    }
}
