package com.platform.android

import android.app.Application
import com.platform.android.data.AppContainer

class PlatformApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
