package com.railbite.core.api

import com.railbite.core.model.Health
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Builds Retrofit/OkHttp clients against a user-entered server URL
 * (e.g. http://192.168.1.45:8080). Cleartext HTTP is allowed by the app
 * networkSecurityConfig (prototype-only).
 */
object ApiClient {

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
        // Never send `"password": null` — the server validates optional fields strictly.
        explicitNulls = false
    }

    fun normalizeServerUrl(raw: String): String {
        var url = raw.trim()
        if (url.isEmpty()) return ""
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            val bareHost = url.substringBefore("/")
            val isLanOrPort = bareHost.contains(":") ||
                bareHost.startsWith("192.168.") ||
                bareHost.startsWith("10.") ||
                bareHost.startsWith("127.") ||
                Regex("^172\\.(1[6-9]|2\\d|3[01])\\.").containsMatchIn(bareHost) ||
                bareHost.equals("localhost", ignoreCase = true)
            url = if (isLanOrPort) "http://$url" else "https://$url"
        }
        while (url.endsWith("/")) url = url.dropLast(1)
        return url
    }

    /** Normalizes a custom WebSocket URL or derives `ws(s)://<host>:<port>/mqtt` from [serverUrl]. */
    fun deriveMqttWsUrl(serverUrl: String, overrideWsUrl: String = ""): String {
        val rawOverride = overrideWsUrl.trim()
        if (rawOverride.isNotEmpty()) {
            val normalized = when {
                rawOverride.startsWith("ws://", ignoreCase = true) ||
                    rawOverride.startsWith("wss://", ignoreCase = true) -> rawOverride
                rawOverride.startsWith("http://", ignoreCase = true) -> "ws://" + rawOverride.removePrefix("http://")
                rawOverride.startsWith("https://", ignoreCase = true) -> "wss://" + rawOverride.removePrefix("https://")
                else -> "ws://$rawOverride"
            }
            val uri = runCatching { java.net.URI(normalized) }.getOrNull()
            return if (uri != null && uri.path.isNullOrBlank()) {
                normalized.trimEnd('/') + "/mqtt"
            } else {
                normalized
            }
        }
        val http = normalizeServerUrl(serverUrl)
        if (http.isEmpty()) return ""
        return when {
            http.startsWith("https://") -> "wss://" + http.removePrefix("https://") + "/mqtt"
            http.startsWith("http://") -> "ws://" + http.removePrefix("http://") + "/mqtt"
            else -> ""
        }
    }

    /** Extracts the host from a server URL for the MQTT broker connection. */
    fun hostFromServerUrl(raw: String): String {
        val url = normalizeServerUrl(raw)
        if (url.isEmpty()) return ""
        return try {
            java.net.URI(url).host ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    fun okHttp(authInterceptor: okhttp3.Interceptor? = null): OkHttpClient =
        OkHttpClient.Builder()
            // Tolerate Render free-tier cold starts (~15–30s) on login/order calls;
            // reachable() keeps a dedicated 2s probe for offline Bluetooth relay fallback.
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .apply { authInterceptor?.let { addInterceptor(it) } }
            .addInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC },
            )
            .build()

    fun retrofit(serverUrl: String, client: OkHttpClient = okHttp()): Retrofit =
        Retrofit.Builder()
            .baseUrl(normalizeServerUrl(serverUrl) + "/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    fun api(serverUrl: String, client: OkHttpClient = okHttp()): RailBiteApi =
        retrofit(serverUrl, client).create(RailBiteApi::class.java)

    /** Cheap connection test: GET /health. Returns the health payload or throws. */
    suspend fun testConnection(serverUrl: String): Health = withContext(Dispatchers.IO) {
        api(serverUrl).health()
    }

    /**
     * Fast reachability probe (~2 s) used to choose between the cloud and the
     * Bluetooth relay without making the user wait out the full HTTP timeouts.
     */
    suspend fun reachable(serverUrl: String, timeoutMs: Long = 2_000L): Boolean =
        withContext(Dispatchers.IO) {
            val url = normalizeServerUrl(serverUrl)
            if (url.isEmpty()) {
                false
            } else {
                val client = OkHttpClient.Builder()
                    .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .build()
                runCatching {
                    val request = okhttp3.Request.Builder().url("$url/health").build()
                    client.newCall(request).execute().use { it.isSuccessful }
                }.getOrDefault(false)
            }
        }

    /**
     * True when the cloud already knows this order (any state). Used by the mesh
     * sink to drop duplicate alerts for dual-path orders — the passenger posted it
     * online AND burst it over Bluetooth, so the cloud alert already fired.
     * Auth is enforced on this route, so the staff bearer token is required.
     */
    suspend fun orderExists(
        serverUrl: String,
        orderId: String,
        bearerToken: String? = null,
        timeoutMs: Long = 2_000L,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val url = normalizeServerUrl(serverUrl)
            if (url.isEmpty()) {
                false
            } else {
                val client = OkHttpClient.Builder()
                    .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .build()
                runCatching {
                    val request = okhttp3.Request.Builder()
                        .url("$url/orders/$orderId")
                        .apply {
                            if (!bearerToken.isNullOrBlank()) {
                                header("Authorization", "Bearer $bearerToken")
                            }
                        }
                        .build()
                    client.newCall(request).execute().use { it.isSuccessful }
                }.getOrDefault(false)
            }
        }
}
