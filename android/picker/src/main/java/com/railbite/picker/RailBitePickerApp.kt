package com.railbite.picker

import android.app.Application
import com.railbite.core.api.RailBiteClient
import com.railbite.core.auth.AuthRepository
import com.railbite.core.auth.AuthStore
import com.railbite.core.auth.EncryptedAuthStore
import com.railbite.core.data.OfflineMenuCatalog
import com.railbite.core.data.RailBiteSettings
import com.railbite.core.mesh.MeshLedger
import com.railbite.core.mesh.TransportRouter
import com.railbite.picker.mesh.MeshGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RailBitePickerApp : Application() {

    lateinit var container: PickerContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = PickerContainer(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { OfflineMenuCatalog.seedIfNeeded(this@RailBitePickerApp, container.settings) }
        }
    }
}

/** Tiny manual service locator (no DI framework on purpose). */
class PickerContainer(val app: RailBitePickerApp) {
    val settings = RailBiteSettings(app, BuildConfig.DEFAULT_SERVER_URL)
    val authStore: AuthStore = EncryptedAuthStore(app)
    val client = RailBiteClient(settings, authStore)
    val auth = AuthRepository(client, authStore)
    val transportRouter = TransportRouter(app, settings, client = client)
    val meshLedger = MeshLedger(app)
    val meshGateway = MeshGateway(app, meshLedger, transportRouter)
    val chatMqtt = PickerChatMqtt(settings)
}
