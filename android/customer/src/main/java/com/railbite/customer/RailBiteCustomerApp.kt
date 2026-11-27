package com.railbite.customer

import android.app.Application
import com.railbite.core.api.RailBiteClient
import com.railbite.core.auth.AuthRepository
import com.railbite.core.auth.AuthStore
import com.railbite.core.auth.EncryptedAuthStore
import com.railbite.core.data.OfflineMenuCatalog
import com.railbite.core.data.RailBiteSettings
import com.railbite.core.mesh.BleMeshTransport
import com.railbite.core.mesh.TransportRouter
import com.railbite.customer.mesh.MeshOrderSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RailBiteCustomerApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Manual service locator owning settings, API, MQTT, transport router, and BLE mesh coordinator. */
class AppContainer(val app: Application) {
    private val bootScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settings = RailBiteSettings(app, BuildConfig.DEFAULT_SERVER_URL)
    val authStore: AuthStore = EncryptedAuthStore(app)
    val client = RailBiteClient(settings, authStore)
    val auth = AuthRepository(client, authStore)
    val cart = CartStore()
    val mqtt = CustomerMqtt(settings)
    val transportRouter = TransportRouter(app, settings)
    val meshTransport = BleMeshTransport(app)
    val meshSender = MeshOrderSender(this)

    init {
        bootScope.launch {
            runCatching { OfflineMenuCatalog.seedIfNeeded(app, settings) }
        }
    }
}
