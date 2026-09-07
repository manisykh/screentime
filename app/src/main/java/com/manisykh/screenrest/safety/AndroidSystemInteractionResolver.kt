package com.manisykh.screenrest.safety

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import android.telecom.TelecomManager
import android.printservice.PrintService
import java.util.concurrent.ConcurrentHashMap

/**
 * Discovers Android-level default apps and transient system surfaces without relying on one OEM.
 *
 * Android does not expose a universal parent/companion-package relationship. We therefore use
 * stable platform roles for Phone/Messages and only grant a transient hand-off to packages that
 * are system components without a launcher entry. Ordinary third-party apps are never promoted by
 * this heuristic.
 */
class AndroidSystemInteractionResolver(context: Context) {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager
    private val transientSystemSurfaceCache = ConcurrentHashMap<String, Boolean>()

    fun refreshDetectedRelationships() {
        val defaultDialer = runCatching {
            appContext.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
        }.getOrNull()
        val defaultSms = runCatching {
            Telephony.Sms.getDefaultSmsPackage(appContext)
        }.getOrNull()
        SafetyGate.registerDetectedDefaultApps(
            phonePackageName = defaultDialer,
            messagingPackageName = defaultSms,
        )

        val systemPrintPackages = queryPrintServicePackages()
            .filter(::isSystemPackageWithoutLauncher)
            .toSet()
        SafetyGate.registerDetectedSystemInteractionPackages(systemPrintPackages)
    }

    fun isProtectedSystemTransition(
        targetPackageName: String,
        sourcePackageName: String?,
    ): Boolean {
        val target = targetPackageName.trim()
        if (SafetyGate.isSystemInteractionPackage(target)) return true
        val source = sourcePackageName.orEmpty().trim()
        if (target.isBlank() || source.isBlank() || target == source) return false
        val protected = transientSystemSurfaceCache.getOrPut(target) {
            isSystemPackageWithoutLauncher(target)
        }
        if (protected) {
            SafetyGate.registerDetectedSystemInteractionPackages(listOf(target))
        }
        return protected
    }

    private fun queryPrintServicePackages(): List<String> {
        val intent = Intent(PrintService.SERVICE_INTERFACE)
        val services = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentServices(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentServices(intent, PackageManager.MATCH_ALL)
        }
        return services.mapNotNull { info -> info.serviceInfo?.packageName }.distinct()
    }

    private fun isSystemPackageWithoutLauncher(packageName: String): Boolean {
        val applicationInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(0L),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(packageName, 0)
            }
        }.getOrNull() ?: return false
        val systemFlags = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
        if (applicationInfo.flags and systemFlags == 0) return false
        return !hasLauncherActivity(packageName)
    }

    private fun hasLauncherActivity(packageName: String): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setPackage(packageName)
        }
        val activities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        return activities.isNotEmpty()
    }
}
