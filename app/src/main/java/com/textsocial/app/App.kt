package com.textsocial.app

import android.app.Application
import com.textsocial.app.di.ServiceLocator

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}