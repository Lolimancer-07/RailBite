package com.railbite.picker.fcm

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.railbite.core.api.RailBiteApi
import com.railbite.picker.PickerContainer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object FcmTokenRegistrar {
    private const val TAG = "FcmTokenRegistrar"

    suspend fun registerCurrentToken(container: PickerContainer, directToken: String? = null) {
        try {
            val serverUrl = container.settings.serverUrl.first()
            val staffId = container.settings.staffId.first()
            if (serverUrl.isBlank() || staffId.isBlank()) {
                Log.d(TAG, "Skipping FCM token registration: serverUrl or staffId not set yet")
                return
            }

            val token = directToken ?: fetchToken()
            if (token.isNullOrBlank()) {
                Log.d(TAG, "No FCM token available to register")
                return
            }

            Log.i(TAG, "Registering FCM device token with server for staff $staffId...")
            container.client.api().registerDeviceToken(
                RailBiteApi.DeviceTokenRequest(staffId = staffId, deviceToken = token),
            )
            Log.i(TAG, "FCM device token registered with server successfully")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register FCM device token: ${e.message}")
        }
    }

    private suspend fun fetchToken(): String? = suspendCancellableCoroutine { cont ->
        runCatching {
            FirebaseMessaging.getInstance().token
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        cont.resume(task.result)
                    } else {
                        Log.w(TAG, "Failed to fetch FCM token: ${task.exception?.message}")
                        cont.resume(null)
                    }
                }
        }.onFailure {
            Log.w(TAG, "FirebaseMessaging not initialized or missing google-services.json: ${it.message}")
            cont.resume(null)
        }
    }
}

