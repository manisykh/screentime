package com.manisykh.screenrest.safety

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.provider.Settings

data class OverlayPermissionState(
    val settingsAllowed: Boolean,
    val appOpsMode: Int?,
    val appOpsAllowed: Boolean,
    val canAttemptOverlay: Boolean,
) {
    val summary: String
        get() = "settings=$settingsAllowed appOps=${appOpsModeLabel()} attempt=$canAttemptOverlay"

    private fun appOpsModeLabel(): String {
        return when (appOpsMode) {
            AppOpsManager.MODE_ALLOWED -> "allowed"
            AppOpsManager.MODE_IGNORED -> "ignored"
            AppOpsManager.MODE_ERRORED -> "errored"
            AppOpsManager.MODE_DEFAULT -> "default"
            null -> "unknown"
            else -> appOpsMode.toString()
        }
    }
}

object OverlayPermissionChecker {
    fun state(context: Context): OverlayPermissionState {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return OverlayPermissionState(
                settingsAllowed = true,
                appOpsMode = AppOpsManager.MODE_ALLOWED,
                appOpsAllowed = true,
                canAttemptOverlay = true,
            )
        }

        val appContext = context.applicationContext
        val settingsAllowed = runCatching {
            Settings.canDrawOverlays(appContext)
        }.getOrDefault(false)
        val appOpsMode = runCatching {
            val appOpsManager = appContext.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOpsManager.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
                    Process.myUid(),
                    appContext.packageName,
                )
            } else {
                @Suppress("DEPRECATION")
                appOpsManager.checkOpNoThrow(
                    AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
                    Process.myUid(),
                    appContext.packageName,
                )
            }
        }.getOrNull()
        val appOpsAllowed = appOpsMode == AppOpsManager.MODE_ALLOWED ||
            (appOpsMode == AppOpsManager.MODE_DEFAULT && settingsAllowed)

        return OverlayPermissionState(
            settingsAllowed = settingsAllowed,
            appOpsMode = appOpsMode,
            appOpsAllowed = appOpsAllowed,
            canAttemptOverlay = settingsAllowed || appOpsAllowed,
        )
    }
}
