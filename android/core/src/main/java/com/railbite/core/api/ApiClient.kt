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
    }

    fun normalizeServerUrl(raw: String): String {
        var url = raw.trim()
        if (url.isEmpty()) return ""
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        while (url.endsWith("/")) url = url.dropLast(1)
        return url
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

    fun okHttp(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
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
}
