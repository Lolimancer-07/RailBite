package com.railbite.core.auth

import com.railbite.core.api.RailBiteClient
import com.railbite.core.model.DevLoginRequest
import com.railbite.core.model.FirebaseLoginRequest
import com.railbite.core.model.LogoutRequest
import com.railbite.core.model.StaffLoginRequest
import com.railbite.core.model.StaffLoginResponse
import kotlinx.coroutines.flow.StateFlow

/**
 * Owns the login exchanges and persists the resulting session in [AuthStore].
 *
 * Customer: Firebase Phone Auth ID token → POST /auth/firebase.
 * Staff: staff ID + bcrypt password → POST /staff/login.
 */
class AuthRepository(
    private val client: RailBiteClient,
    private val store: AuthStore,
) {
    val session: StateFlow<AuthSession?> = store.session

    /** Returns the server response so the picker can use the staff profile fields. */
    suspend fun staffLogin(staffId: String, password: String): StaffLoginResponse {
        val result = client.api().staffLogin(
            StaffLoginRequest(staffId = staffId.trim().lowercase(), password = password),
        )
        val accessToken = result.accessToken?.takeIf { it.isNotBlank() } ?: result.token
        val refreshToken = result.refreshToken
        store.save(
            AuthSession(
                accessToken = accessToken,
                refreshToken = refreshToken.orEmpty(),
                expiresAt = result.expiresAt,
                user = AuthUser(
                    id = result.staffId,
                    name = result.name,
                    role = mapStaffRole(result.role),
                    staffId = result.staffId,
                    zoneCoaches = result.zoneCoaches,
                ),
            ),
        )
        return result
    }

    suspend fun firebaseLogin(idToken: String, name: String? = null): AuthSession {
        val result = client.api().verifyFirebase(FirebaseLoginRequest(idToken = idToken, name = name))
        return result.toSession().also(store::save)
    }

    /** Local demo login — the server only allows it when AUTH_DEV_MODE is enabled. */
    suspend fun devLogin(phone: String, name: String? = null): AuthSession {
        val result = client.api().devLogin(DevLoginRequest(phone = phone, name = name))
        return result.toSession().also(store::save)
    }

    /** Revokes the session server-side (best effort) and clears it locally. */
    suspend fun logout() {
        val refreshToken = store.refreshToken()
        if (!refreshToken.isNullOrBlank()) {
            runCatching { client.api().logout(LogoutRequest(refreshToken)) }
        }
        store.clear()
    }

    fun clearLocal() = store.clear()

    private fun mapStaffRole(role: String): String = when (role.uppercase()) {
        "CAPTAIN" -> "picker"
        "COOK" -> "kitchen"
        "ADMIN" -> "admin"
        else -> role.lowercase()
    }
}
