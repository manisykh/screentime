package com.example.screentimemanager.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import java.util.Calendar
import java.util.Locale

data class AppUsageInfo(
    val appName: String,
    val packageName: String,
    val totalTimeMillis: Long,
)

class UsageStatsRepository(
    private val context: Context,
) {
    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val packageManager = context.packageManager

    fun hasUsageAccess(): Boolean {
        val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOpsManager.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        return when (mode) {
            AppOpsManager.MODE_ALLOWED -> true
            AppOpsManager.MODE_IGNORED,
            AppOpsManager.MODE_ERRORED -> false
            AppOpsManager.MODE_DEFAULT -> canQueryUsageStats()
            else -> false
        }
    }

    private fun canQueryUsageStats(): Boolean {
        return try {
            val endTime = System.currentTimeMillis()
            val startTime = endTime - USAGE_ACCESS_PROBE_WINDOW_MILLIS
            usageStatsManager
                .queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
                .isNotEmpty()
        } catch (_: RuntimeException) {
            false
        }
    }

    fun getTodayUsage(maxItems: Int = 10, skipAccessCheck: Boolean = false): List<AppUsageInfo> {
        if (!skipAccessCheck && !hasUsageAccess()) {
            return emptyList()
        }

        return try {
            val startTime = localDayStartMillis()
            val endTime = System.currentTimeMillis()
            val eventUsageByPackage = runCatching {
                getEventForegroundUsage(startTime, endTime)
            }.getOrDefault(emptyMap())
            val statsUsageByPackage = runCatching {
                getStatsForegroundUsage(startTime, endTime)
            }.getOrDefault(emptyMap())
            val dailyUsageByPackage = runCatching {
                getDailyForegroundUsage(startTime, endTime)
            }.getOrDefault(emptyMap())
            val launchablePackages = runCatching {
                getLaunchablePackages()
            }.getOrDefault(emptySet())

            (eventUsageByPackage.keys + statsUsageByPackage.keys + dailyUsageByPackage.keys)
                .associateWith { packageName ->
                    maxOf(
                        eventUsageByPackage[packageName] ?: 0L,
                        statsUsageByPackage[packageName] ?: 0L,
                        dailyUsageByPackage[packageName] ?: 0L,
                    )
                }
                .filter { (packageName, totalTimeMillis) ->
                    totalTimeMillis >= MIN_VISIBLE_USAGE_MILLIS &&
                        (launchablePackages.isEmpty() || packageName in launchablePackages) &&
                        isVisibleUsageApp(packageName)
                }
                .map { (packageName, totalTimeMillis) ->
                    AppUsageInfo(
                        appName = getAppName(packageName),
                        packageName = packageName,
                        totalTimeMillis = totalTimeMillis,
                    )
                }
                .sortedByDescending { appUsage -> appUsage.totalTimeMillis }
                .take(maxItems)
        } catch (_: RuntimeException) {
            emptyList()
        }
    }

    fun getTodayUsageMillisByPackage(skipAccessCheck: Boolean = false): Map<String, Long> {
        if (!skipAccessCheck && !hasUsageAccess()) {
            return emptyMap()
        }

        return try {
            val startTime = localDayStartMillis()
            val endTime = System.currentTimeMillis()
            val eventUsageByPackage = runCatching {
                getEventForegroundUsage(startTime, endTime)
            }.getOrDefault(emptyMap())
            val statsUsageByPackage = runCatching {
                getStatsForegroundUsage(startTime, endTime)
            }.getOrDefault(emptyMap())
            val dailyUsageByPackage = runCatching {
                getDailyForegroundUsage(startTime, endTime)
            }.getOrDefault(emptyMap())
            val launchablePackages = runCatching {
                getLaunchablePackages()
            }.getOrDefault(emptySet())

            (eventUsageByPackage.keys + statsUsageByPackage.keys + dailyUsageByPackage.keys)
                .associateWith { packageName ->
                    maxOf(
                        eventUsageByPackage[packageName] ?: 0L,
                        statsUsageByPackage[packageName] ?: 0L,
                        dailyUsageByPackage[packageName] ?: 0L,
                    )
                }
                .filter { (packageName, totalTimeMillis) ->
                    totalTimeMillis >= MIN_VISIBLE_USAGE_MILLIS &&
                        (launchablePackages.isEmpty() || packageName in launchablePackages) &&
                        isVisibleUsageApp(packageName)
                }
        } catch (_: RuntimeException) {
            emptyMap()
        }
    }

    fun getAppLabel(packageName: String): String {
        return getAppName(packageName)
    }

    fun getCurrentForegroundPackageName(
        lookbackMillis: Long = CURRENT_FOREGROUND_LOOKBACK_MILLIS,
    ): String? {
        if (!hasUsageAccess()) {
            return null
        }

        return try {
            val endTime = System.currentTimeMillis()
            val startTime = maxOf(endTime - lookbackMillis, localDayStartMillis())
            val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
            val event = UsageEvents.Event()
            var foregroundPackageName: String? = null

            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)
                val eventPackageName = event.packageName.orEmpty()
                when {
                    isForegroundEvent(event.eventType) -> {
                        foregroundPackageName = if (eventPackageName.isNotBlank() && isVisibleUsageApp(eventPackageName)) {
                            eventPackageName
                        } else {
                            null
                        }
                    }

                    isBackgroundEvent(event.eventType) && foregroundPackageName == eventPackageName -> {
                        foregroundPackageName = null
                    }
                }
            }

            foregroundPackageName
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun getEventForegroundUsage(startTime: Long, endTime: Long): Map<String, Long> {
        val usageByPackage = mutableMapOf<String, Long>()
        var foregroundPackageName: String? = null
        var foregroundStartedAt = 0L
        val event = UsageEvents.Event()
        val usageEvents = usageStatsManager.queryEvents(startTime, endTime)

        fun closeForegroundSession(closedAt: Long) {
            val packageName = foregroundPackageName ?: return
            val startedAt = foregroundStartedAt.coerceAtLeast(startTime)
            val endedAt = closedAt.coerceIn(startTime, endTime)
            if (endedAt > startedAt) {
                usageByPackage[packageName] =
                    (usageByPackage[packageName] ?: 0L) + endedAt - startedAt
            }
            foregroundPackageName = null
            foregroundStartedAt = 0L
        }

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            when {
                isForegroundEvent(event.eventType) -> {
                    if (foregroundPackageName != event.packageName) {
                        closeForegroundSession(event.timeStamp)
                        foregroundPackageName = event.packageName
                        foregroundStartedAt = event.timeStamp
                    } else if (foregroundStartedAt <= 0L) {
                        foregroundStartedAt = event.timeStamp
                    }
                }

                isBackgroundEvent(event.eventType) && foregroundPackageName == event.packageName -> {
                    closeForegroundSession(event.timeStamp)
                }
            }
        }

        closeForegroundSession(endTime)
        return usageByPackage
    }

    private fun getStatsForegroundUsage(startTime: Long, endTime: Long): Map<String, Long> {
        return usageStatsManager
            .queryAndAggregateUsageStats(startTime, endTime)
            .mapValues { (_, usageStats) -> usageStats.totalTimeInForeground }
            .filterValues { totalTimeMillis -> totalTimeMillis > 0L }
    }

    private fun getDailyForegroundUsage(startTime: Long, endTime: Long): Map<String, Long> {
        return usageStatsManager
            .queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
            .groupBy { usageStats -> usageStats.packageName }
            .mapValues { (_, usageStats) -> usageStats.sumOf { stat -> stat.totalTimeInForeground } }
            .filterValues { totalTimeMillis -> totalTimeMillis > 0L }
    }

    @Suppress("DEPRECATION")
    private fun isForegroundEvent(eventType: Int): Boolean {
        return eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
            eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
    }

    @Suppress("DEPRECATION")
    private fun isBackgroundEvent(eventType: Int): Boolean {
        return eventType == UsageEvents.Event.ACTIVITY_PAUSED ||
            eventType == UsageEvents.Event.ACTIVITY_STOPPED ||
            eventType == UsageEvents.Event.MOVE_TO_BACKGROUND
    }

    private fun localDayStartMillis(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun isVisibleUsageApp(packageName: String): Boolean {
        return packageName != context.packageName && !AppVisibility.isHiddenPackage(packageName)
    }

    private fun getLaunchablePackages(): Set<String> {
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
            .mapNotNull { resolveInfo -> resolveInfo.activityInfo?.packageName }
            .filterNot { packageName -> AppVisibility.isHiddenPackage(packageName) }
            .toSet()
    }

    private fun getAppName(packageName: String): String {
        return try {
            val applicationInfo = getApplicationInfo(packageName)
                ?: return packageName.toReadableFallbackName()
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName.toReadableFallbackName()
        }
    }

    private fun getApplicationInfo(packageName: String): ApplicationInfo? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(packageName, 0)
            }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun String.toReadableFallbackName(): String {
        val rawName = substringAfterLast('.')
            .replace('_', ' ')
            .replace('-', ' ')

        return rawName
            .split(' ')
            .filter { word -> word.isNotBlank() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { character ->
                    if (character.isLowerCase()) {
                        character.titlecase(Locale.getDefault())
                    } else {
                        character.toString()
                    }
                }
            }
            .ifBlank { this }
    }

    companion object {
        private const val MIN_VISIBLE_USAGE_MILLIS = 10_000L
        private const val USAGE_ACCESS_PROBE_WINDOW_MILLIS = 7L * 24L * 60L * 60L * 1000L
        private const val CURRENT_FOREGROUND_LOOKBACK_MILLIS = 12L * 60L * 60L * 1000L
    }
}
