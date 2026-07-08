package com.textsocial.app.util

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.textsocial.app.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object PushNotificationManager {

    private const val TAG = "PushNotificationManager"
    fun registerCurrentDeviceToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Failed to fetch FCM token", task.exception)
                return@addOnCompleteListener
            }
            val token = task.result ?: return@addOnCompleteListener
            CoroutineScope(Dispatchers.IO).launch {
                val result = ServiceLocator.userRepository.registerDeviceToken(token)
                if (result.isFailure) {
                    Log.w(TAG, "Failed to register device token with backend", result.exceptionOrNull())
                }
            }
        }
    }

    fun unregisterCurrentDeviceToken(onDone: () -> Unit = {}) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            val token = task.result
            CoroutineScope(Dispatchers.IO).launch {
                if (task.isSuccessful && token != null) {
                    ServiceLocator.userRepository.unregisterDeviceToken(token)
                }
                try {
                    FirebaseMessaging.getInstance().deleteToken()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to delete local FCM token", e)
                }
                onDone()
            }
        }
    }
}
