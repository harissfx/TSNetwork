package com.textsocial.app.util

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.textsocial.app.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Registers/unregisters this device's Firebase Cloud Messaging token with our backend
 * (the `device_tokens` table), so the server knows where to send push notifications
 * for the signed-in user -- including while the app is closed or in the background.
 *
 * This is deliberately a plain object (not tied to any ViewModel/Activity lifecycle):
 * token registration needs to happen right after login, right after registration, and
 * once on every app start for an already-logged-in user, from places that don't share
 * a common parent scope.
 */
object PushNotificationManager {

    private const val TAG = "PushNotificationManager"

    /** Call after a successful login/registration, and once on startup if already logged in. */
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

    /** Call on logout so this device stops receiving pushes for the account that signed out. */
    fun unregisterCurrentDeviceToken(onDone: () -> Unit = {}) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            val token = task.result
            CoroutineScope(Dispatchers.IO).launch {
                if (task.isSuccessful && token != null) {
                    ServiceLocator.userRepository.unregisterDeviceToken(token)
                }
                // Also ask Firebase to drop the token so a stale one isn't kept around locally.
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
