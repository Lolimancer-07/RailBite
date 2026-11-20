package com.railbite.core.auth

import kotlinx.serialization.Serializable

/** Mirrors the server's auth user object. */
@Serializable
data class AuthUser(
    val id: String,
    val name: String? = null,
    val phone: String? = null,
    val role: String = "",
    val staffId: String? = null,
    val zoneCoaches: List<String>? = null,
)

/**
 * A signed-in RailBite session. Access tokens are short-lived JWTs; the refresh
 * token is opaque and rotated by the server on every refresh.
 */
@Serializable
data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: String = "",
    val user: AuthUser,
) {
    val isStaff: Boolean
        get() = user.role == "picker" || user.role == "kitchen" || user.role == "admin"

    val isCustomer: Boolean
        get() = user.role == "customer"
}

/**
 * Exact response shape of POST /auth/firebase, /auth/dev/login and /auth/refresh.
 * `expiresAt` is an ISO-8601 instant.
 */
@Serializable
data class AuthResponse(
    val ok: Boolean = true,
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: String? = null,
    val expiresAt: String = "",
    val user: AuthUser,
) {
    fun toSession(): AuthSession = AuthSession(
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresAt = expiresAt,
        user = user,
    )
}
