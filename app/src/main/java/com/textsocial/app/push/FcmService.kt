package com.textsocial.app.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.textsocial.app.di.ServiceLocator
import com.textsocial.app.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Entry point for Firebase Cloud Messaging. This is what lets the app show notifications
 * (new like/comment/follow, new DM) even when it's closed or in the background.
 *
 * The backend sends *data-only* pushes (no "notification" payload block) so this
 * `onMessageReceived` callback always runs -- including while the app is killed on most
 * OEMs -- and we build the tray notification ourselves via [NotificationHelper], with a
 * tap action that deep-links into the right screen.
 */
class FcmService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Only worth registering if someone is actually logged in; if not, this will be
        // (re)registered right after the next successful login (see PushNotificationManager).
        if (ServiceLocator.encryptedPreferencesManager.getUserId() == null) return
        CoroutineScope(Dispatchers.IO).launch {
            ServiceLocator.userRepository.registerDeviceToken(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        if (message.data.isEmpty()) return
        NotificationHelper.showFromPushData(applicationContext, message.data)
    }
}
