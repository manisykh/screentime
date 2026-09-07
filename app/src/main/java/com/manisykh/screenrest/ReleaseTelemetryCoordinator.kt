package com.manisykh.screenrest

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.manisykh.screenrest.data.SettingsRepository
import com.manisykh.screenrest.data.settingsDataStore
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

object ReleaseTelemetryCoordinator {
    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start(context: Context) {
        val appContext = context.applicationContext
        if (FirebaseApp.getApps(appContext).isEmpty() || !started.compareAndSet(false, true)) {
            return
        }
        scope.launch {
            SettingsRepository(appContext.settingsDataStore)
                .monitoringDisclosureAccepted
                .distinctUntilChanged()
                .collect { accepted ->
                    FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(accepted)
                }
        }
    }
}
