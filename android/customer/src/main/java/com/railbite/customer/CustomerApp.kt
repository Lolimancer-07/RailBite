package com.railbite.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
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
import com.railbite.customer.ui.settings.SettingsScreen
import com.railbite.customer.ui.theme.RailBitePalette
import com.railbite.customer.ui.tracking.TrackingScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object Routes {
    const val ONBOARDING = "onboarding"
    const val LOGIN = "login"
    const val HOME = "home"
    const val CART = "cart"
    const val ORDERS = "orders"
    const val SETTINGS = "settings"
    const val TRACKING = "tracking/{orderId}"
    const val REPORT = "report/{orderId}"

    fun tracking(orderId: String) = "tracking/$orderId"
    fun report(orderId: String) = "report/$orderId"
}

/**
 * Root graph of the customer app: auth -> onboarding -> home, or home -> tabs.
 * Starts on login whenever no session is stored, otherwise onboarding/home.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomerNavHost(graph: CustomerGraph, startDestination: String) {
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()

    val session by graph.authStore.session.collectAsState()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    // Session lost (refresh rejected / signed out in Settings) -> drop back to login.
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
            TabScaffold(
                nav = nav,
                graph = graph,
            ) { padding ->
                Box(
                    Modifier
                        .padding(padding)
                        .consumeWindowInsets(padding),
                ) {
                    HomeScreen(
                        graph = graph,
                        onOpenCart = { nav.navigate(Routes.CART) },
                        onEditSeat = { nav.navigate(Routes.SETTINGS) },
                        onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                        onOpenOrder = { id -> nav.navigate(Routes.tracking(id)) },
                    )
                }
            }
        }
        composable(Routes.ORDERS) {
            TabScaffold(nav = nav, graph = graph) { padding ->
                Box(
                    Modifier
                        .padding(padding)
                        .consumeWindowInsets(padding),
                ) {
                    OrdersScreen(
                        graph = graph,
                        onOpenOrder = { id -> nav.navigate(Routes.tracking(id)) },
                    )
                }
            }
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

@Composable
private fun TabScaffold(
    nav: NavHostController,
    graph: CustomerGraph,
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
) {
    val cartCount by graph.cart.lines.collectAsState()
    val count = cartCount.values.sumOf { it.qty }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    Scaffold(
        containerColor = RailBitePalette.Canvas,
        bottomBar = {
            Column(Modifier.background(RailBitePalette.SurfaceCard)) {
                HorizontalDivider(thickness = 1.dp, color = RailBitePalette.BorderSubtle)
                NavigationBar(
                    containerColor = RailBitePalette.SurfaceCard,
                    tonalElevation = 0.dp,
                ) {
                    BottomTab.entries.forEach { tab ->
                        val selected = route == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = RailBitePalette.CyanPrimary,
                                selectedTextColor = RailBitePalette.CyanPrimary,
                                indicatorColor = RailBitePalette.CyanTint,
                                unselectedIconColor = RailBitePalette.TextSecondary,
                                unselectedTextColor = RailBitePalette.TextSecondary,
                            ),
                            icon = {
                                if (tab == BottomTab.HOME) {
                                    BadgedBox(
                                        badge = {
                                            if (count > 0) {
                                                Badge(
                                                    containerColor = RailBitePalette.DarkSlate,
                                                    contentColor = Color.White,
                                                ) { Text("$count") }
                                            }
                                        },
                                    ) {
                                        Icon(
                                            if (selected) Icons.Filled.Home else Icons.Outlined.Home,
                                            contentDescription = tab.label,
                                        )
                                    }
                                } else {
                                    Icon(
                                        if (selected) Icons.AutoMirrored.Filled.ReceiptLong else Icons.AutoMirrored.Outlined.ReceiptLong,
                                        contentDescription = tab.label,
                                    )
                                }
                            },
                            label = {
                                Text(
                                    tab.label,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        content(padding)
    }
}

enum class BottomTab(val route: String, val label: String) {
    HOME(Routes.HOME, "Home"),
    ORDERS(Routes.ORDERS, "Orders"),
}
