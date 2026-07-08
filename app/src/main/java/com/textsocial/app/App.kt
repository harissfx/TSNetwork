package com.textsocial.app

import android.app.Application
import com.textsocial.app.di.ServiceLocator
import com.textsocial.app.util.NotificationHelper
import com.textsocial.app.util.PushNotificationManager

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        NotificationHelper.createNotificationChannels(this)
        if (ServiceLocator.encryptedPreferencesManager.getUserId() != null) {
            PushNotificationManager.registerCurrentDeviceToken()
        }
    }
}