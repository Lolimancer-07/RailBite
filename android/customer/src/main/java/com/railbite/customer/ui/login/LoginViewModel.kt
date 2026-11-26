package com.railbite.customer.ui.login

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
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
    /**
     * Code generated on the device. Only set while the app is in demo mode —
     * empty means a real SMS was requested through Firebase Phone Auth.
     */
    val demoOtp: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val info: String? = null,
) {
    val phoneValid: Boolean get() = phone.length == 10 && phone.first() in '6'..'9'
    val codeValid: Boolean get() = otp.length == 6

    /** True when no SMS was sent and the code above is displayed on screen. */
    val demoMode: Boolean get() = demoOtp.isNotBlank()

    /** True while waiting on a real Firebase SMS challenge. */
    val smsMode: Boolean get() = stage == CustomerLoginStage.OTP && demoOtp.isBlank()
}

/**
 * Customer sign-in.
 *
 * Primary path: real Firebase Phone Auth — the user enters a real 10-digit
 * Indian mobile number, Firebase sends the SMS, and the resulting ID token is
 * exchanged for a RailBite session at POST /auth/otp/verify.
 *
 * Fallback path: when Firebase is not configured (no google-services.json, no
 * Play Services, reCAPTCHA rejected, quota exhausted…) the app switches to a
 * demo code generated on-device and shown on screen, so sign-in still works.
 * If the server has dev-login disabled too, a local demo session is stored.
 */
class CustomerLoginViewModel(
    private val container: AppContainer,
    private val onLoggedIn: () -> Unit,
) : ViewModel() {

    private val _state = MutableStateFlow(CustomerLoginUiState())
    val state: StateFlow<CustomerLoginUiState> = _state.asStateFlow()

    /** Set when Firebase sent an SMS; null means the screen is in demo mode. */
    private var verificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null

    private val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            // Instant verification / SMS auto-retrieval: sign in without user input.
            completeFirebaseSignIn(credential)
        }

        override fun onVerificationFailed(e: FirebaseException) {
            startDemoMode(
                "SMS could not be sent (${e.message ?: "verification failed"}). " +
                    "Use the demo code below instead.",
            )
        }

        override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
            verificationId = id
            resendToken = token
            _state.update {
                it.copy(
                    stage = CustomerLoginStage.OTP,
                    otp = "",
                    demoOtp = "",
                    busy = false,
                    error = null,
                    info = "OTP sent by SMS to +91 ${formatPhone(it.phone)}.",
                )
            }
        }
    }

    fun onPhoneChange(value: String) {
        _state.update {
            it.copy(
                phone = value.filter(Char::isDigit).take(10),
                error = null,
            )
        }
    }

    fun onOtpChange(value: String) {
        _state.update {
            it.copy(
                otp = value.filter(Char::isDigit).take(6),
                error = null,
            )
        }
    }

    /**
     * Requests a real SMS through Firebase. [activity] is required for the
     * reCAPTCHA fallback Firebase uses on devices where Play Integrity fails.
     * A null activity (or missing Firebase config) goes straight to demo mode.
     */
    fun sendOtp(activity: Activity?) {
        val s = _state.value
        if (s.busy) return

        validatePhone(s.phone)?.let { message ->
            _state.update { it.copy(error = message) }
            return
        }

        val auth = firebaseAuth()
        if (auth == null || activity == null) {
            startDemoMode(
                "Firebase Phone Auth is not available on this build — " +
                    "using the on-screen demo code.",
            )
            return
        }

        _state.update { it.copy(busy = true, error = null, info = "Requesting SMS…") }
        val builder = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber("+91${s.phone}")
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
        resendToken?.let(builder::setForceResendingToken)

        runCatching { PhoneAuthProvider.verifyPhoneNumber(builder.build()) }
            .onFailure {
                startDemoMode(
                    "Could not start SMS verification (${it.message}). " +
                        "Use the demo code below.",
                )
            }
    }

    fun resendOtp(activity: Activity?) {
        if (_state.value.busy) return
        if (verificationId == null) {
            _state.update {
                it.copy(
                    otp = "",
                    demoOtp = newCode(),
                    error = null,
                    info = "A new demo code was generated.",
                )
            }
        } else {
            sendOtp(activity)
        }
    }

    fun changeNumber() {
        verificationId = null
        resendToken = null
        _state.update {
            it.copy(
                stage = CustomerLoginStage.PHONE,
                otp = "",
                demoOtp = "",
                error = null,
                info = null,
            )
        }
    }

    fun autofillOtp() {
        _state.update { it.copy(otp = it.demoOtp, error = null) }
    }

    /** Escape hatch when SMS never arrives: switch to the on-screen code. */
    fun useDemoCode() {
        if (_state.value.stage != CustomerLoginStage.OTP) return
        startDemoMode("Demo code generated on this device — no SMS is sent.")
    }

    fun verifyCode() {
        val s = _state.value
        if (s.busy) return
        if (!s.codeValid) {
            _state.update { it.copy(error = "Enter the 6-digit OTP") }
            return
        }

        val id = verificationId
        if (id != null) {
            _state.update { it.copy(busy = true, error = null, info = null) }
            viewModelScope.launch {
                try {
                    val auth = firebaseAuth() ?: error("Firebase is not configured")
                    val credential = PhoneAuthProvider.getCredential(id, s.otp)
                    signInWithFirebase(auth, credential)
                } catch (e: Exception) {
                    _state.update {
                        it.copy(busy = false, otp = "", error = friendlyError(e))
                    }
                }
            }
            return
        }

        if (s.otp != s.demoOtp) {
            _state.update {
                it.copy(
                    otp = "",
                    error = "Incorrect OTP. Check the demo code and try again.",
                    info = null,
                )
            }
            return
        }

        _state.update { it.copy(busy = true, error = null, info = null) }
        viewModelScope.launch { demoSignIn(s.phone) }
    }

    private fun completeFirebaseSignIn(credential: PhoneAuthCredential) {
        _state.update { it.copy(busy = true, error = null, info = null) }
        viewModelScope.launch {
            try {
                val auth = firebaseAuth() ?: error("Firebase is not configured")
                signInWithFirebase(auth, credential)
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, otp = "", error = friendlyError(e))
                }
            }
        }
    }

    /** Signs into Firebase, exchanges the ID token with the server, persists the session. */
    private suspend fun signInWithFirebase(
        auth: FirebaseAuth,
        credential: PhoneAuthCredential,
    ) {
        val result = auth.signInWithCredential(credential).await()
        val user = result.user ?: error("Firebase returned no user")
        val idToken = user.getIdToken(false).await().token
            ?: error("Firebase returned no ID token")
        container.auth.firebaseLogin(idToken, user.displayName)
        _state.update { it.copy(busy = false) }
        onLoggedIn()
    }

    /**
     * Demo sign-in: try the server's dev-login (real JWT) first; if the
     * deployment has it disabled, keep a local demo session so the app works.
     */
    private suspend fun demoSignIn(phoneDigits: String) {
        val phone = "+91$phoneDigits"

        val serverSession = runCatching {
            withTimeout(12_000) { container.auth.devLogin(phone) }
        }.getOrNull()

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

    private fun startDemoMode(reason: String) {
        verificationId = null
        resendToken = null
        _state.update {
            it.copy(
                stage = CustomerLoginStage.OTP,
                otp = "",
                demoOtp = newCode(),
                busy = false,
                error = null,
                info = reason,
            )
        }
    }

    private fun validatePhone(phone: String): String? = when {
        phone.length != 10 -> "Enter your 10-digit mobile number"
        phone.first() !in '6'..'9' -> "Indian mobile numbers start with 6, 7, 8 or 9"
        else -> null
    }

    /**
     * The Firebase app is normally auto-initialised from google-services.json.
     * Builds without that file (or devices without Play Services) return null
     * and the login falls back to the demo code.
     */
    private fun firebaseAuth(): FirebaseAuth? = runCatching {
        val context = container.app
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
        FirebaseApp.getApps(context).firstOrNull()?.let { FirebaseAuth.getInstance(it) }
    }.getOrNull()

    private fun friendlyError(e: Throwable): String = when (e) {
        is FirebaseAuthInvalidCredentialsException ->
            "That code is incorrect or expired. Resend the OTP and try again."
        else -> e.message?.takeIf { it.isNotBlank() }
            ?: "Could not verify the phone number"
    }

    private fun newCode(): String = Random.nextInt(1_000_000).toString().padStart(6, '0')
}

internal fun formatPhone(digits: String): String =
    if (digits.length <= 5) digits else "${digits.take(5)} ${digits.drop(5)}"
