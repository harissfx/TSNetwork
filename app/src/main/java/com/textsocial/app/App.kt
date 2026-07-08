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

        // Covers the "already logged in, just relaunching the app" case. A push token can
        // rotate at any time, so re-registering on every cold start keeps the backend's
        // copy fresh rather than relying solely on onNewToken/post-login registration.
        if (ServiceLocator.encryptedPreferencesManager.getUserId() != null) {
            PushNotificationManager.registerCurrentDeviceToken()
        }
    }
}