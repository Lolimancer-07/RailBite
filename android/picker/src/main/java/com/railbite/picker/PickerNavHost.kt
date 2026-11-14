package com.railbite.picker

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.railbite.picker.alert.AlertActivity
import com.railbite.picker.ui.cash.CashSummaryScreen
import com.railbite.picker.ui.home.HomeScreen
import com.railbite.picker.ui.login.LoginScreen
import com.railbite.picker.ui.order.OrderProgressScreen
import com.railbite.picker.ui.permissions.PermissionsScreen

/** Root graph of the picker app. */
@Composable
fun PickerNavHost(graph: PickerGraph, startDestination: String) {
    val nav: NavHostController = rememberNavController()
    val context = LocalContext.current

    NavHost(navController = nav, startDestination = startDestination) {
        composable(PickerRoutes.LOGIN) {
            LoginScreen(graph = graph, onLoggedIn = {
                nav.navigate(PickerRoutes.HOME) {
                    popUpTo(PickerRoutes.LOGIN) { inclusive = true }
                }
            })
        }
        composable(PickerRoutes.HOME) {
            HomeScreen(
                graph = graph,
                onOpenPermissions = { nav.navigate(PickerRoutes.PERMISSIONS) },
                onOpenOrder = { id -> nav.navigate(PickerRoutes.order(id)) },
                onOpenCash = { nav.navigate(PickerRoutes.CASH) },
                onLoggedOut = {
                    nav.navigate(PickerRoutes.LOGIN) {
                        popUpTo(PickerRoutes.HOME) { inclusive = true }
                    }
                },
                onShowAlert = { orderId ->
                    // Used by the "Test alert" button
                    context.startActivity(
                        Intent(context, AlertActivity::class.java)
                            .putExtra(AlertActivity.EXTRA_ORDER_ID, orderId)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
            )
        }
        composable(PickerRoutes.PERMISSIONS) {
            PermissionsScreen(graph = graph, onBack = { nav.popBackStack() })
        }
        composable(PickerRoutes.ORDER) { entry ->
            val orderId = entry.arguments?.getString("orderId").orEmpty()
            OrderProgressScreen(graph = graph, orderId = orderId, onBack = { nav.popBackStack() })
        }
        composable(PickerRoutes.CASH) {
            CashSummaryScreen(graph = graph, onBack = { nav.popBackStack() })
        }
    }
}
