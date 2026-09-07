package com.manisykh.screenrest

import android.app.Application
import com.manisykh.screenrest.notification.PushTokenRegistrationCoordinator

class ScreenRestApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ScreenRestAppCheckInstaller.install(this)
        ReleaseTelemetryCoordinator.start(this)
        PushTokenRegistrationCoordinator.start(this)
    }
}
