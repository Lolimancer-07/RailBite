package com.railbite.picker

import com.railbite.core.api.RailBiteClient
import com.railbite.core.data.RailBiteSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object PickerRoutes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val PERMISSIONS = "permissions"
    const val ORDER = "order/{orderId}"
    const val CASH = "cash"
    const val MESH_ORDER = "meshOrder/{orderId}"

    /** OpenOrderRouter marker for offline (Bluetooth relay) handovers. */
    const val MESH_PREFIX = "mesh:"

    fun order(orderId: String) = "order/$orderId"
    fun meshOrder(orderId: String) = "meshOrder/$orderId"
}

/** Shared graph of the picker app: settings + api client + pending-order store. */
class PickerGraph(
    val container: PickerContainer,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    val settings: RailBiteSettings = container.settings
    val api: RailBiteClient = container.client

    /** null = resolving; LOGIN | HOME */
    private val _startDestination = MutableStateFlow<String?>(null)
    val startDestination: StateFlow<String?> = _startDestination.asStateFlow()

    init {
        scope.launch {
            if (settings.serverUrl.first().isBlank() && BuildConfig.DEFAULT_SERVER_URL.isNotBlank()) {
                settings.setServerUrl(BuildConfig.DEFAULT_SERVER_URL)
            }
            val loggedIn = settings.staffToken.first().isNotBlank()
            if (loggedIn) {
                com.railbite.picker.fcm.FcmTokenRegistrar.registerCurrentToken(container)
            }
            _startDestination.value = if (loggedIn) PickerRoutes.HOME else PickerRoutes.LOGIN
        }
    }
}
