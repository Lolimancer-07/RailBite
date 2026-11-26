package com.railbite.core.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Where the app keeps its session. Reads are synchronous so OkHttp can use it. */
interface AuthStore {
    val session: StateFlow<AuthSession?>

    fun accessToken(): String?
    fun refreshToken(): String?

    /** Replaces the persisted session and notifies observers. */
    fun save(session: AuthSession)

    /** Signs out locally (used by logout and when a refresh token is rejected). */
    fun clear()
}

/**
 * Tokens live encrypted at rest: AES-256/GCM with a key held by the Android
 * Keystore (never exported), ciphertext in private SharedPreferences. Plain
 * DataStore is deliberately not used for credentials.
 */
class EncryptedAuthStore(context: Context) : AuthStore {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val _session = MutableStateFlow(load())
    override val session: StateFlow<AuthSession?> = _session.asStateFlow()

    override fun accessToken(): String? = _session.value?.accessToken

    override fun refreshToken(): String? = _session.value?.refreshToken

    override fun save(session: AuthSession) {
        synchronized(lock) {
            val encoded = json.encodeToString(AuthSession.serializer(), session)
            val sealed = encrypt(encoded)
            prefs.edit().putString(KEY_SESSION, sealed).apply()
            _session.value = session
        }
    }

    override fun clear() {
        synchronized(lock) {
            prefs.edit().remove(KEY_SESSION).apply()
            _session.value = null
        }
    }

    private fun load(): AuthSession? {
        // One-time migration: builds before the real phone/OTP gate saved a demo
        // session under "session" and jumped straight to onboarding. Drop that
        // legacy blob so the login screen is shown once after upgrading; sessions
        // saved from here on live under "session_v2" and persist normally.
        prefs.edit().remove(LEGACY_KEY_SESSION).apply()
        val raw = prefs.getString(KEY_SESSION, null) ?: return null
        return runCatching {
            val decoded = decrypt(raw)
            json.decodeFromString(AuthSession.serializer(), decoded)
        }.getOrElse {
            // Key invalidated (factory reset / lock-screen change) or corrupt data:
            // drop the session rather than crash on launch.
            prefs.edit().remove(KEY_SESSION).apply()
            null
        }
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(sealed: String): String {
        val bytes = Base64.decode(sealed, Base64.NO_WRAP)
        require(bytes.size > IV_BYTES) { "Corrupt session blob" }
        val iv = bytes.copyOfRange(0, IV_BYTES)
        val ciphertext = bytes.copyOfRange(IV_BYTES, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFS_NAME = "railbite_auth"
        const val KEY_SESSION = "session_v2"
        const val LEGACY_KEY_SESSION = "session"
        const val KEY_ALIAS = "railbite_auth_key"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        val lock = Any()
    }
}
