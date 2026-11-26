package com.railbite.core.auth

import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * Adds the stored Bearer token to every API call and, on a 401, performs exactly
 * one refresh + retry. Refresh is serialized so parallel requests that all hit
 * 401 share a single rotation (the server revokes each refresh token on use).
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
            // Another request already rotated the session while this one was in flight.
            if (!current.isNullOrBlank() && current != token) {
                current
            } else {
                refresh(original)
            }
        }

        if (refreshed.isNullOrBlank()) {
            // The demo sign-in has no server session to rotate; keep it so a
            // 401 from an auth-enforcing deployment does not bounce the user
            // back to the login screen.
            if (!DemoAuth.isDemoToken(token)) store.clear()
            return response
        }

        response.close()
        return chain.proceed(
            request.newBuilder().header("Authorization", "Bearer $refreshed").build(),
        )
    }

    /** Returns the new access token, or null when the refresh token is no longer valid. */
    private fun refresh(original: Request): String? {
        val refreshToken = store.refreshToken() ?: return null
        val body = json
            .encodeToString(RefreshTokenRequest.serializer(), RefreshTokenRequest(refreshToken))
            .toRequestBody(JSON)
        val request = Request.Builder()
            .url(original.url.newBuilder().encodedPath("/auth/refresh").query(null).build())
            .post(body)
            .header("Accept", "application/json")
            .build()

        return refreshClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val text = response.body?.string() ?: return null
            val parsed = runCatching {
                json.decodeFromString(AuthResponse.serializer(), text)
            }.getOrNull() ?: return null
            if (parsed.accessToken.isBlank() || parsed.refreshToken.isBlank()) return null
            store.save(parsed.toSession())
            parsed.accessToken
        }
    }

    private fun isAuthEndpoint(path: String): Boolean =
        path.startsWith("/auth/") || path.endsWith("/staff/login")

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

@kotlinx.serialization.Serializable
private data class RefreshTokenRequest(val refreshToken: String)
