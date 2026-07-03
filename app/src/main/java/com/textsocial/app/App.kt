package com.textsocial.app

import androidx.multidex.MultiDexApplication
import com.textsocial.app.di.ServiceLocator

class App : MultiDexApplication() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
