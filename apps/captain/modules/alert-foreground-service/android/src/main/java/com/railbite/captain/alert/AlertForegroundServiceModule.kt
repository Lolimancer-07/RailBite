package com.railbite.captain.alert

import android.content.Context
import android.content.Intent
import android.os.Build
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class AlertForegroundServiceModule : Module() {
    override fun definition() = ModuleDefinition {
        Name("AlertForegroundService")

        Function("startAlert") { orderId: String, coach: String, berth: String, total: String ->
            val context: Context = appContext.reactContext ?: return@Function false
            val intent = Intent(context, AlertForegroundService::class.java).apply {
                action = AlertForegroundService.ACTION_START
                putExtra("orderId", orderId)
                putExtra("coach", coach)
                putExtra("berth", berth)
                putExtra("total", total)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            return@Function true
        }

        Function("stopAlert") {
            val context: Context = appContext.reactContext ?: return@Function false
            val intent = Intent(context, AlertForegroundService::class.java).apply {
                action = AlertForegroundService.ACTION_STOP
            }
            context.startService(intent)
            return@Function true
        }
    }
}
