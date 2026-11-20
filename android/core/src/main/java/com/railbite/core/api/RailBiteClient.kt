package com.railbite.core.api

import com.railbite.core.auth.AuthInterceptor
import com.railbite.core.auth.AuthStore
import com.railbite.core.data.RailBiteSettings
import kotlinx.coroutines.flow.first

/**
 * Lazily builds (and caches) a RailBiteApi for the server URL currently stored in settings.
 * Call sites do:  val api = client.api(); api.menu()
 *
 * Every call carries the stored bearer token; a 401 transparently rotates the
 * session through the refresh token (see [AuthInterceptor]).
 */
class RailBiteClient(
    private val settings: RailBiteSettings,
    private val authStore: AuthStore,
) {
    private val refreshClient = ApiClient.okHttp()
    private val httpClient = ApiClient.okHttp(AuthInterceptor(authStore, refreshClient))

    suspend fun serverUrl(): String = settings.serverUrl.first()

    suspend fun api(): RailBiteApi {
        val url = ApiClient.normalizeServerUrl(settings.serverUrl.first())
        synchronized(this) {
            if (cachedUrl != url || cachedApi == null) {
                cachedApi = ApiClient.api(url, httpClient)
                cachedUrl = url
            }
            return cachedApi!!
        }
    }

    private var cachedUrl: String? = null
    private var cachedApi: RailBiteApi? = null
}
