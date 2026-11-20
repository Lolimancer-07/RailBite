package com.railbite.customer

import android.app.Application
import com.railbite.core.api.RailBiteClient
import com.railbite.core.auth.AuthRepository
import com.railbite.core.auth.EncryptedAuthStore
import com.railbite.core.auth.AuthStore
import com.railbite.core.data.RailBiteSettings
import com.railbite.core.mesh.BleMeshTransport
import com.railbite.customer.mesh.MeshOrderSender

class RailBiteCustomerApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Tiny manual service locator (no DI framework on purpose). */
class AppContainer(val app: Application) {
    val settings = RailBiteSettings(app, BuildConfig.DEFAULT_SERVER_URL)
    val authStore: AuthStore = EncryptedAuthStore(app)
    val client = RailBiteClient(settings, authStore)
    val auth = AuthRepository(client, authStore)
    val cart = CartStore()
    val mqtt = CustomerMqtt(settings)
    val meshTransport = BleMeshTransport(app)
    val meshSender = MeshOrderSender(this)
}
