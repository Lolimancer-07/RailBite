package com.railbite.core.api

import com.railbite.core.data.RailBiteSettings
import kotlinx.coroutines.flow.first

/**
 * Lazily builds (and caches) a RailBiteApi for the server URL currently stored in settings.
 * Call sites do:  val api = client.api(); api.menu()
 */
class RailBiteClient(private val settings: RailBiteSettings) {

    suspend fun serverUrl(): String = settings.serverUrl.first()

    suspend fun api(): RailBiteApi {
        val url = ApiClient.normalizeServerUrl(settings.serverUrl.first())
        synchronized(this) {
            if (cachedUrl != url || cachedApi == null) {
                cachedApi = ApiClient.api(url)
                cachedUrl = url
            }
            return cachedApi!!
        }
    }

    private var cachedUrl: String? = null
    private var cachedApi: RailBiteApi? = null
}
