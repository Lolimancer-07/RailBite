package com.railbite.customer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.railbite.core.api.RailBiteClient
import com.railbite.core.auth.AuthRepository
import com.railbite.core.auth.AuthStore
import com.railbite.core.data.RailBiteSettings
import com.railbite.core.mesh.TransportRouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Shared graph of the customer app: settings + api client + auth + cart + mqtt + transport router + start route. */
class CustomerGraph(
    val container: AppContainer,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    val settings: RailBiteSettings = container.settings
    val api: RailBiteClient = container.client
    val authStore: AuthStore = container.authStore
    val auth: AuthRepository = container.auth
    val cart: CartStore = container.cart
    val mqtt: CustomerMqtt = container.mqtt
    val transport: TransportRouter = container.transportRouter

    /** null = still resolving; "login" | "onboarding" | "home" */
    private val _startDestination = MutableStateFlow<String?>(null)
    val startDestination: StateFlow<String?> = _startDestination.asStateFlow()

    init {
        scope.launch {
            val done = runCatching {
                withTimeout(2_500) { settings.onboardingDone.first() }
            }.getOrDefault(false)
            val loggedIn = container.authStore.session.value != null
            _startDestination.value = when {
                !loggedIn -> Routes.LOGIN
                done -> Routes.HOME
                else -> Routes.ONBOARDING
            }
        }
    }

    /** Active order id persisted so a fresh app open resumes tracking. */
    val activeOrderId = settings.activeOrderId
}

@Composable
fun rememberGraph(container: AppContainer): CustomerGraph =
    remember { CustomerGraph(container) }
