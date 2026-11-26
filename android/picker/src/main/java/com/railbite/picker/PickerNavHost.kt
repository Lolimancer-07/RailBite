package com.railbite.picker

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.railbite.picker.alert.IncomingOrderActivity
import com.railbite.picker.mesh.MeshHandoverScreen
import com.railbite.picker.ui.cash.CashSummaryScreen
import com.railbite.picker.ui.home.PickerDashboardScreen
import com.railbite.picker.ui.login.LoginScreen
import com.railbite.picker.ui.order.OrderProgressScreen
import com.railbite.picker.ui.permissions.PermissionsScreen

/** Root graph of the picker app. */
@Composable
fun PickerNavHost(graph: PickerGraph, startDestination: String) {
    val nav: NavHostController = rememberNavController()
    val context = LocalContext.current

    // "Open order X" requests (accepted alerts) -> the right screen. Mesh-accepted orders
    // route to the offline handover; cloud orders to the normal picking checklist.
    val openRoute by com.railbite.picker.OpenOrderRouter.route
    androidx.compose.runtime.LaunchedEffect(openRoute) {
        val r = openRoute ?: return@LaunchedEffect
        if (r.startsWith(PickerRoutes.MESH_PREFIX)) {
            nav.navigate(PickerRoutes.meshOrder(r.removePrefix(PickerRoutes.MESH_PREFIX))) {
                launchSingleTop = true
            }
        } else if (r.isNotBlank()) {
            nav.navigate(PickerRoutes.order(r)) { launchSingleTop = true }
        }
        com.railbite.picker.OpenOrderRouter.consume()
    }

    NavHost(navController = nav, startDestination = startDestination) {
        composable(PickerRoutes.LOGIN) {
            LoginScreen(graph = graph, onLoggedIn = {
                nav.navigate(PickerRoutes.HOME) {
                    popUpTo(PickerRoutes.LOGIN) { inclusive = true }
                }
            })
        }
        composable(PickerRoutes.HOME) {
            PickerDashboardScreen(
                graph = graph,
                onOpenPermissions = { nav.navigate(PickerRoutes.PERMISSIONS) },
                onOpenOrder = { id, openChat -> nav.navigate(PickerRoutes.order(id, openChat)) },
                onOpenCash = { nav.navigate(PickerRoutes.CASH) { launchSingleTop = true } },
                onLoggedOut = {
                    nav.navigate(PickerRoutes.LOGIN) {
                        popUpTo(PickerRoutes.HOME) { inclusive = true }
                    }
                },
                onShowAlert = { orderId ->
                    // Used by the "Test alert" / "Test Ring" button
                    context.startActivity(
                        Intent(context, IncomingOrderActivity::class.java)
                            .putExtra(IncomingOrderActivity.EXTRA_ORDER_ID, orderId)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
            )
        }
        composable(PickerRoutes.PERMISSIONS) {
            PermissionsScreen(graph = graph, onBack = { nav.popBackStack() })
        }
        composable(
            route = PickerRoutes.ORDER,
            arguments = listOf(
                navArgument("orderId") { type = NavType.StringType },
                navArgument("chat") {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) { entry ->
            val orderId = entry.arguments?.getString("orderId").orEmpty()
            val startChatOpen = entry.arguments?.getBoolean("chat") ?: false
            OrderProgressScreen(
                graph = graph,
                orderId = orderId,
                onBack = { nav.popBackStack() },
                startChatOpen = startChatOpen,
            )
        }
        composable(PickerRoutes.CASH) {
            CashSummaryScreen(graph = graph, onBack = { nav.popBackStack() })
        }
        composable(PickerRoutes.MESH_ORDER) { entry ->
            val orderId = entry.arguments?.getString("orderId").orEmpty()
            MeshHandoverScreen(graph = graph, orderId = orderId, onDone = { nav.popBackStack() })
        }
    }
}
