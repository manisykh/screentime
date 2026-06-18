package com.example.screentimemanager.usage

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

data class InstalledAppInfo(
    val appName: String,
    val packageName: String,
)

class AppCatalogRepository(
    private val context: Context,
) {
    private val packageManager = context.packageManager

    fun getLaunchableApps(): List<InstalledAppInfo> {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val activities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }

        return activities
            .mapNotNull { resolveInfo ->
                val packageName = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
                if (packageName == context.packageName || AppVisibility.isHiddenPackage(packageName)) {
                    return@mapNotNull null
                }

                InstalledAppInfo(
                    appName = resolveInfo.loadLabel(packageManager).toString(),
                    packageName = packageName,
                )
            }
            .distinctBy { app -> app.packageName }
            .sortedBy { app -> app.appName.lowercase() }
    }
}
