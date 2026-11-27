package com.railbite.core.auth

import com.railbite.core.model.DevLoginRequest
import com.railbite.core.model.RefreshRequest
import com.railbite.core.model.StaffLoginRequest
import com.railbite.core.model.StaffLoginResponse
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * Adds the stored Bearer token to every API call and, on a 401, performs exactly
 * one refresh (or DemoAuth → real-JWT upgrade via `/auth/dev/login`) + retry.
 * Refresh is serialized so parallel requests that all hit 401 share a single
 * rotation (the server revokes each refresh token on use).
 *
 * When the refresh token itself is rejected the session is cleared, which the
 * app roots observe to return to the login screen.
 */
class AuthInterceptor(
    private val store: AuthStore,
    private val refreshClient: OkHttpClient,
) : Interceptor {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
    private val lock = Any()

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val path = original.url.encodedPath
        if (isAuthEndpoint(path)) return chain.proceed(original)

        val token = store.accessToken()
        val request = if (token.isNullOrBlank()) {
            original
        } else {
            original.newBuilder().header("Authorization", "Bearer $token").build()
        }

        val response = chain.proceed(request)
        if (response.code != 401 || token.isNullOrBlank()) return response

        val refreshed = synchronized(lock) {
            val current = store.accessToken()
            // Another request already rotated or upgraded the session while this one was in flight.
            if (!current.isNullOrBlank() && current != token) {
                current
            } else if (DemoAuth.isDemoToken(token)) {
                upgradeDemoSession(original)
            } else {
                refresh(original)
            }
        }

        if (refreshed.isNullOrBlank()) {
            // The demo sign-in has no server session to rotate; keep it so a
            // 401 from an auth-enforcing deployment does not bounce the user
            // back to the login screen when offline/demo-only.
            if (!DemoAuth.isDemoToken(token)) store.clear()
            return response
        }

        response.close()
        return chain.proceed(
            request.newBuilder().header("Authorization", "Bearer $refreshed").build(),
        )
    }

    /** Exchanges a local demo session for a real server JWT when `/auth/dev/login` or `/staff/login` is reachable. */
    private fun upgradeDemoSession(original: Request): String? {
        val user = store.session.value?.user ?: return null
        if (user.role == "picker" && !user.staffId.isNullOrBlank()) {
            return exchangeStaffSession(original, user.staffId)
        }
        val phone = user.phone?.takeIf { it.isNotBlank() } ?: return null
        val name = user.name?.takeUnless { it == "Demo Passenger" }
        return exchangeSession(
            original = original,
            path = "/auth/dev/login",
            serializer = DevLoginRequest.serializer(),
            payload = DevLoginRequest(phone = phone, name = name),
        )
    }

    private fun exchangeStaffSession(original: Request, staffId: String): String? {
        val body = json.encodeToString(
            StaffLoginRequest.serializer(),
            StaffLoginRequest(
                staffId = staffId.trim().lowercase(),
                password = DemoAuth.DEMO_STAFF_PIN,
                pin = DemoAuth.DEMO_STAFF_PIN,
            ),
        ).toRequestBody(JSON)
        val request = Request.Builder()
            .url(original.url.newBuilder().encodedPath("/staff/login").query(null).build())
            .post(body)
            .header("Accept", "application/json")
            .build()

        return runCatching {
            refreshClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val text = response.body?.string() ?: return@use null
                val parsed = runCatching {
                    json.decodeFromString(StaffLoginResponse.serializer(), text)
                }.getOrNull() ?: return@use null
                val access = parsed.accessToken?.takeIf { it.isNotBlank() } ?: parsed.token
                if (access.isBlank()) return@use null
                val mappedRole = when (parsed.role.uppercase()) {
                    "CAPTAIN" -> "picker"
                    "COOK" -> "kitchen"
                    "ADMIN" -> "admin"
                    else -> parsed.role.lowercase()
                }
                store.save(
                    AuthSession(
                        accessToken = access,
                        refreshToken = parsed.refreshToken.orEmpty(),
                        expiresAt = parsed.expiresAt,
                        user = AuthUser(
                            id = parsed.staffId,
                            name = parsed.name,
                            role = mappedRole,
                            staffId = parsed.staffId,
                            zoneCoaches = parsed.zoneCoaches,
                        ),
                    ),
                )
                access
            }
        }.getOrNull()
    }

    /** Returns the new access token, or null when the refresh token is no longer valid. */
    private fun refresh(original: Request): String? {
        val refreshToken = store.refreshToken() ?: return null
        return exchangeSession(
            original = original,
            path = "/auth/refresh",
            serializer = RefreshRequest.serializer(),
            payload = RefreshRequest(refreshToken),
        )
    }

    private fun <T> exchangeSession(
        original: Request,
        path: String,
        serializer: KSerializer<T>,
        payload: T,
    ): String? {
        val body = json.encodeToString(serializer, payload).toRequestBody(JSON)
        val request = Request.Builder()
            .url(original.url.newBuilder().encodedPath(path).query(null).build())
            .post(body)
            .header("Accept", "application/json")
            .build()

        return runCatching {
            refreshClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val text = response.body?.string() ?: return@use null
                val parsed = runCatching {
                    json.decodeFromString(AuthResponse.serializer(), text)
                }.getOrNull() ?: return@use null
                if (parsed.accessToken.isBlank() || parsed.refreshToken.isBlank()) return@use null
                store.save(parsed.toSession())
                parsed.accessToken
            }
        }.getOrNull()
    }

    private fun isAuthEndpoint(path: String): Boolean =
        path.startsWith("/auth/") || path.endsWith("/staff/login")

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
