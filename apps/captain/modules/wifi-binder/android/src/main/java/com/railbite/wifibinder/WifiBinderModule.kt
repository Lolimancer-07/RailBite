package com.railbite.wifibinder

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import androidx.annotation.RequiresApi
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class WifiBinderModule : Module() {
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var boundNetwork: Network? = null

    override fun definition() = ModuleDefinition {
        Name("WifiBinder")

        Events("onPantryConnected", "onPantryLost")

        Function("connectToPantry") { ssid: String, passphrase: String? ->
            val context: Context = appContext.reactContext ?: return@Function false
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return@Function false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val specifierBuilder = WifiNetworkSpecifier.Builder()
                    .setSsid(ssid)

                if (!passphrase.isNullOrEmpty()) {
                    specifierBuilder.setWpa2Passphrase(passphrase)
                }

                val networkSpecifier = specifierBuilder.build()
                val request = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) // Crucial: allow no-internet pantry network
                    .setNetworkSpecifier(networkSpecifier)
                    .build()

                // Unregister previous if any
                networkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }

                networkCallback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        super.onAvailable(network)
                        boundNetwork = network
                        // Bind all process sockets to this pantry network
                        connectivityManager.bindProcessToNetwork(network)
                        this@WifiBinderModule.sendEvent("onPantryConnected", mapOf("ssid" to ssid))
                    }

                    override fun onLost(network: Network) {
                        super.onLost(network)
                        if (boundNetwork == network) {
                            boundNetwork = null
                            connectivityManager.bindProcessToNetwork(null)
                        }
                        this@WifiBinderModule.sendEvent("onPantryLost", mapOf("ssid" to ssid))
                    }
                }

                connectivityManager.requestNetwork(request, networkCallback!!)
                return@Function true
            }

            return@Function false
        }

        Function("unbind") {
            val context: Context = appContext.reactContext ?: return@Function false
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return@Function false

            networkCallback?.let {
                try {
                    connectivityManager.unregisterNetworkCallback(it)
                } catch (_: Exception) {}
                networkCallback = null
            }

            boundNetwork = null
            connectivityManager.bindProcessToNetwork(null)
            return@Function true
        }

        Function("isBound") {
            return@Function boundNetwork != null
        }
    }
}
