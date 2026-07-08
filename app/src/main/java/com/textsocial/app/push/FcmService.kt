package com.textsocial.app.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.textsocial.app.di.ServiceLocator
import com.textsocial.app.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FcmService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
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
