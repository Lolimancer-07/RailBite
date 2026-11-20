package com.railbite.customer.ui.login

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthMissingActivityForRecaptchaException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.android.gms.tasks.Task
import com.railbite.core.auth.AuthSession
import com.railbite.customer.AppContainer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.util.concurrent.TimeUnit

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
    val devMode: Boolean = false,
) {
    val phoneValid: Boolean get() = phone.filter(Char::isDigit).length == 10
    val codeValid: Boolean get() = otp.filter(Char::isDigit).length == 6
}

/**
 * Customer sign-in: Firebase Phone Auth (SMS code) or local demo sign-in when
 * this build has no Firebase configuration. On success the session lands in the
 * shared [com.railbite.core.auth.AuthStore], which the root observes to open.
 */
class CustomerLoginViewModel(
    private val container: AppContainer,
    private val activity: Activity?,
    private val onLoggedIn: () -> Unit,
) : ViewModel() {

    private val _state = MutableStateFlow<CustomerLoginUiState>(
        CustomerLoginUiState(devMode = FirebaseApp.getApps(container.app.applicationContext).isEmpty()),
    )
    val state: StateFlow<CustomerLoginUiState> = _state.asStateFlow()

    private val _pendingVerificationId = MutableStateFlow("")

    private val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            viewModelScope.launch {
                runCatching { completeWithCredential(credential) }
                    .onFailure { e -> _state.update { it.copy(busy = false, error = friendly(e)) } }
            }
        }

        override fun onVerificationFailed(exception: FirebaseException) {
            _state.update {
                it.copy(
                    busy = false,
                    error = friendly(exception),
                    stage = CustomerLoginStage.PHONE,
                    info = if (exception is FirebaseAuthMissingActivityForRecaptchaException) {
                        "Phone verification could not start on this build. Use the demo sign-in instead."
                    } else null,
                )
            }
        }

        override fun onCodeSent(verificationId: String, forceResendingToken: PhoneAuthProvider.ForceResendingToken) {
            _pendingVerificationId.value = verificationId
            _state.update { it.copy(stage = CustomerLoginStage.OTP, busy = false, error = null) }
        }

        override fun onCodeAutoRetrievalTimeOut(verificationId: String) {
            // No-op: surfaced through the error/info channels if it matters later.
        }
    }

    fun onPhoneChange(value: String) {
        _state.update { it.copy(phone = value.filter(Char::isDigit).take(10), error = null, stage = CustomerLoginStage.PHONE) }
    }

    fun onOtpChange(value: String) {
        _state.update { it.copy(otp = value.filter(Char::isDigit).take(6)) }
    }

    fun sendOtp() {
        val s = _state.value
        if (s.busy) return
        if (!s.phoneValid) {
            _state.update { it.copy(error = "Enter a 10-digit mobile number to receive the SMS code") }
            return
        }
        if (s.devMode) {
            viewModelScope.launch { loginViaDev() }
            return
        }
        val ctx = activity ?: run {
            _state.update { it.copy(busy = false, error = "Phone verification is unavailable in this context — use the demo sign-in") }
            return
        }
        _state.update { it.copy(busy = true, error = null, stage = CustomerLoginStage.OTP) }
        val options = PhoneAuthOptions.newBuilder(FirebaseAuth.getInstance())
            .setPhoneNumber("+91${s.phone}")
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(ctx)
            .setCallbacks(callbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    fun verifyCode() {
        val s = _state.value
        if (s.busy || !s.codeValid) return
        val verificationId = _pendingVerificationId.value
        if (verificationId.isBlank()) {
            _state.update { it.copy(busy = false, error = "Verification was not started — press Send OTP again") }
            return
        }
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            runCatching { completeWithCode(verificationId, s.otp) }
                .onFailure { e -> _state.update { it.copy(busy = false, error = friendly(e)) } }
        }
    }

    private suspend fun completeWithCode(verificationId: String, code: String) {
        completeWithCredential(PhoneAuthProvider.getCredential(verificationId, code))
    }

    private suspend fun completeWithCredential(credential: PhoneAuthCredential) {
        val authResult: AuthResult = awaitTask(FirebaseAuth.getInstance().signInWithCredential(credential))
        val user = authResult.user ?: throw IllegalStateException("Firebase sign-in returned no user")
        val idToken: String = awaitTask(user.getIdToken(/* forceRefresh */ true)).token ?: ""
        val session: AuthSession = container.auth.firebaseLogin(idToken, user.displayName)
        _state.update { it.copy(busy = false, error = null, stage = CustomerLoginStage.OTP, otp = "") }
        onLoggedIn()
    }

    private suspend fun loginViaDev() {
        val phone = _state.value.phone
        runCatching { container.auth.devLogin(phone) }
            .onSuccess { onLoggedIn() }
            .onFailure { e -> _state.update { it.copy(busy = false, error = devError(e)) } }
    }

    private fun devError(e: Throwable): String = when (e) {
        is HttpException -> runCatching {
            e.response()?.errorBody()?.string()?.let { raw ->
                Regex("\"error\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.groupValues?.get(1) ?: "Demo sign-in could not be completed"
            } ?: "Demo sign-in could not be completed"
        }.getOrDefault("Demo sign-in could not be completed")
        else -> e.message ?: "Could not sign in"
    }

    private fun friendly(e: Throwable): String {
        if (e is FirebaseAuthInvalidCredentialsException) {
            return "That verification code is invalid. Check the SMS and try again."
        }
        val message = (e.message ?: "Phone verification failed").trim()
        return when {
            message.contains("reCAPTCHA", ignoreCase = true) || message.contains("Recaptcha", ignoreCase = true) ->
                "Phone verification needs a SHA-1/SHA-256 fingerprint registered in Firebase for this build. Use the demo sign-in for now."
            message.contains("not_enabled", ignoreCase = true) ->
                "Phone Auth is not enabled for this Firebase project. Enable it in the Firebase console and retry."
            message.contains("api_key_not_valid", ignoreCase = true) || message.contains("invalid-api-key", ignoreCase = true) ->
                "This build has no valid Firebase configuration. Use the demo sign-in for now."
            else -> message
        }
    }

    private suspend fun <T> awaitTask(task: Task<T>): T {
        val deferred = CompletableDeferred<T>()
        task.addOnCompleteListener { t ->
            if (t.isSuccessful) {
                deferred.complete(t.result)
            } else {
                deferred.completeExceptionally(t.exception ?: RuntimeException("Task failed"))
            }
        }
        return deferred.await()
    }

    override fun onCleared() {
        super.onCleared()
    }
}
