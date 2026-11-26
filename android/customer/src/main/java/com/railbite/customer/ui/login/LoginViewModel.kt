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
import com.railbite.customer.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    val busy: Boolean = false,
    val error: String? = null,
    val info: String? = null,
) {
    val phoneValid: Boolean get() = phone.length == 10 && phone.first() in '6'..'9'
    val codeValid: Boolean get() = otp.length == 6
}

/**
 * Customer sign-in — real Firebase Phone Auth SMS OTP.
 *
 * The user enters a real 10-digit Indian mobile number; Firebase sends a real
 * SMS; the OTP is verified with the returned credential and the Firebase ID
 * token is exchanged for a RailBite session at POST /auth/otp/verify.
 *
 * There is no demo OTP path any more — the real SMS must come through Firebase.
 * (If Firebase is unavailable the screen surfaces an error; no local bypass.)
 */
class CustomerLoginViewModel(
    private val container: AppContainer,
    private val onLoggedIn: () -> Unit,
) : ViewModel() {

    private val _state = MutableStateFlow(CustomerLoginUiState())
    val state: StateFlow<CustomerLoginUiState> = _state.asStateFlow()

    private var verificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null

    private val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            completeFirebaseSignIn(credential)
        }

        override fun onVerificationFailed(e: FirebaseException) {
            _state.update {
                it.copy(busy = false, error = "SMS could not be sent (${e.message ?: "verification failed"}).")
            }
        }

        override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
            verificationId = id
            resendToken = token
            _state.update {
                it.copy(
                    stage = CustomerLoginStage.OTP,
                    otp = "",
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

    /** Requests a real SMS through Firebase Phone Auth. */
    fun sendOtp(activity: Activity?) {
        val s = _state.value
        if (s.busy) return

        val validation = when {
            s.phone.length != 10 -> "Enter your 10-digit mobile number"
            s.phone.first() !in '6'..'9' -> "Indian mobile numbers start with 6, 7, 8 or 9"
            else -> null
        }
        if (validation != null) {
            _state.update { it.copy(error = validation) }
            return
        }

        val auth = firebaseAuth()
        if (auth == null || activity == null) {
            _state.update {
                it.copy(busy = false, error = "Firebase Phone Auth is not available on this build — cannot send SMS.")
            }
            return
        }

        _state.update { it.copy(busy = true, error = null, info = "Requesting SMS…") }
        val builder = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber("+91${s.phone}")
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
        resendToken?.let(builder::setForceResendingToken)

        runCatching { PhoneAuthProvider.verifyPhoneNumber(builder.build()) }.onFailure { e ->
            _state.update {
                it.copy(busy = false, error = "Could not start SMS verification (${e.message}).")
            }
        }
    }

    fun resendOtp(activity: Activity?) {
        if (_state.value.busy) return
        if (verificationId == null) {
            _state.update { it.copy(error = "Send the OTP first") }
            return
        }
        sendOtp(activity)
    }

    fun changeNumber() {
        verificationId = null
        resendToken = null
        _state.update {
            it.copy(
                stage = CustomerLoginStage.PHONE,
                otp = "",
                error = null,
                info = null,
            )
        }
    }

    fun verifyCode() {
        val s = _state.value
        if (s.busy) return
        if (s.stage != CustomerLoginStage.OTP) {
            _state.update { it.copy(error = "Send the OTP first") }
            return
        }
        if (!s.codeValid) {
            _state.update { it.copy(error = "Enter the 6-digit OTP") }
            return
        }
        val id = verificationId
        if (id == null) {
            _state.update { it.copy(error = "Send the OTP first") }
            return
        }

        _state.update { it.copy(busy = true, error = null, info = null) }
        viewModelScope.launch {
            try {
                val auth = firebaseAuth() ?: error("Firebase is not configured")
                val credential = PhoneAuthProvider.getCredential(id, s.otp)
                signInWithFirebase(auth, credential)
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, otp = "", error = friendlyError(e)) }
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
        val idToken = user.getIdToken(false).await().token ?: error("Firebase returned no ID token")
        container.auth.firebaseLogin(idToken, user.displayName)
        _state.update { it.copy(busy = false) }
        onLoggedIn()
    }

    private fun completeFirebaseSignIn(credential: PhoneAuthCredential) {
        _state.update { it.copy(busy = true, error = null, info = null) }
        viewModelScope.launch {
            try {
                val auth = firebaseAuth() ?: error("Firebase is not configured")
                signInWithFirebase(auth, credential)
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, otp = "", error = friendlyError(e)) }
            }
        }
    }

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

    private fun formatPhone(digits: String): String =
        if (digits.length <= 5) digits else "${digits.take(5)} ${digits.drop(5)}"
}
