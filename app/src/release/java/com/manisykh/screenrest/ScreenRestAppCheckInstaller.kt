package com.manisykh.screenrest

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

object ScreenRestAppCheckInstaller {
    fun install(context: Context) {
        val app = FirebaseApp.initializeApp(context) ?: FirebaseApp.getApps(context).firstOrNull()
            ?: return
        FirebaseAppCheck.getInstance(app).installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance(),
        )
    }
}
