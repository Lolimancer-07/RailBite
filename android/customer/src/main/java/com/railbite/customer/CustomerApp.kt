package com.railbite.customer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.railbite.customer.ui.cart.CartScreen
import com.railbite.customer.ui.home.HomeScreen
import com.railbite.customer.ui.login.CustomerLoginScreen
import com.railbite.customer.ui.onboarding.OnboardingScreen
import com.railbite.customer.ui.orders.OrdersScreen
import com.railbite.customer.ui.report.ReportProblemScreen
import com.railbite.customer.ui.search.SearchScreen
import com.railbite.customer.ui.settings.SettingsScreen
import com.railbite.customer.ui.tracking.TrackingScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object Routes {
    const val ONBOARDING = "onboarding"
    const val LOGIN = "login"
    const val HOME = "home"
    const val SEARCH = "search"
    const val CART = "cart"
    const val ORDERS = "orders"
    const val SETTINGS = "settings"
    const val TRACKING = "tracking/{orderId}"
    const val REPORT = "report/{orderId}"

    fun tracking(orderId: String) = "tracking/$orderId"
    fun report(orderId: String) = "report/$orderId"
}

/**
 * Root navigation graph: auth -> onboarding -> wireframe home -> cart / orders / tracking / settings.
 */
@Composable
fun CustomerNavHost(graph: CustomerGraph, startDestination: String) {
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()

    val session by graph.authStore.session.collectAsState()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    // Session lost (refresh rejected / signed out) -> drop back to login.
    LaunchedEffect(session, currentRoute) {
        if (session == null && currentRoute != null && currentRoute != Routes.LOGIN) {
            nav.navigate(Routes.LOGIN) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(Unit) {
        graph.mqtt.ensureConnected()
    }

    NavHost(navController = nav, startDestination = startDestination) {
        composable(Routes.LOGIN) {
            CustomerLoginScreen(
                graph = graph,
                onLoggedIn = {
                    scope.launch {
                        val destination = if (graph.settings.onboardingDone.first()) Routes.HOME else Routes.ONBOARDING
                        nav.navigate(destination) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    }
                },
            )
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                graph = graph,
                onDone = {
                    nav.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                graph = graph,
                onOpenCart = { nav.navigate(Routes.CART) },
                onOpenOrders = { nav.navigate(Routes.ORDERS) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                onOpenOrder = { id -> nav.navigate(Routes.tracking(id)) },
                onOpenSearch = { nav.navigate(Routes.SEARCH) { launchSingleTop = true } },
            )
        }
        composable(Routes.SEARCH) {
            SearchScreen(
                graph = graph,
                onBack = { nav.popBackStack() },
                onOpenCart = { nav.navigate(Routes.CART) },
            )
        }
        composable(Routes.ORDERS) {
            OrdersScreen(
                graph = graph,
                onBack = { nav.popBackStack() },
                onOpenOrder = { id -> nav.navigate(Routes.tracking(id)) },
            )
        }
        composable(Routes.CART) {
            CartScreen(
                graph = graph,
                onPlaced = { orderId ->
                    nav.navigate(Routes.tracking(orderId)) {
                        popUpTo(Routes.HOME)
                    }
                },
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.TRACKING) { entry ->
            val orderId = entry.arguments?.getString("orderId").orEmpty()
            TrackingScreen(
                graph = graph,
                orderId = orderId,
                onBack = { nav.popBackStack() },
                onReport = { nav.navigate(Routes.report(orderId)) },
            )
        }
        composable(Routes.REPORT) { entry ->
            val orderId = entry.arguments?.getString("orderId").orEmpty()
            ReportProblemScreen(graph = graph, orderId = orderId, onDone = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(graph = graph, onBack = { nav.popBackStack() })
        }
    }
}
