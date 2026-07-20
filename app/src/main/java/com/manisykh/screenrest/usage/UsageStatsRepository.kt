package com.manisykh.screenrest.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.util.Log
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class AppUsageInfo(
    val appName: String,
    val packageName: String,
    val totalTimeMillis: Long,
)

data class DailyUsageInfo(
    val dayStartMillis: Long,
    val dayLabel: String,
    val totalTimeMillis: Long,
    val hasRecordedData: Boolean,
)

class UsageStatsRepository(
    private val context: Context,
) {
    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val packageManager = context.packageManager
    private val continuityStore = UsageContinuityStore(context.applicationContext)
    private val historyStore = UsageHistoryStore(context.applicationContext)
    private val monotonicUsageMillisByPackage = mutableMapOf<String, Long>()
    private var monotonicUsageDayStartMillis: Long = 0L
    private val appNameCache = mutableMapOf<String, String>()
    private var launchablePackagesCache: Set<String>? = null
    private var launchablePackagesCacheAtMillis: Long = 0L

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
            val currentForeground = getCurrentForegroundSnapshot()
            val eventUsageByPackage = runCatching {
                getEventForegroundUsage(
                    startTime = startTime,
                    endTime = endTime,
                    currentForegroundPackageName = currentForeground.packageName,
                    foregroundBoundaryTimeMillis = currentForeground.updatedAtWallClockMillis,
                )
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

            val maxAllowedUsageMillis = maxPossibleTodayUsageMillis(startTime, endTime)
            val sanitizedEventUsageByPackage = sanitizeTodayUsageMap(
                source = "events",
                usageByPackage = eventUsageByPackage,
                startTime = startTime,
                endTime = endTime,
            )
            val sanitizedStatsUsageByPackage = sanitizeTodayUsageMap(
                source = "aggregate",
                usageByPackage = statsUsageByPackage,
                startTime = startTime,
                endTime = endTime,
            )
            val sanitizedDailyUsageByPackage = sanitizeTodayUsageMap(
                source = "daily",
                usageByPackage = dailyUsageByPackage,
                startTime = startTime,
                endTime = endTime,
            )
            auditTodayUsageSources(
                startTime = startTime,
                endTime = endTime,
                eventUsageByPackage = eventUsageByPackage,
                statsUsageByPackage = statsUsageByPackage,
                dailyUsageByPackage = dailyUsageByPackage,
            )

            val stableUsageByPackage = applyMonotonicUsage(
                dayStartMillis = startTime,
                usageByPackage = mergeUsageByPackage(
                    eventUsageByPackage = sanitizedEventUsageByPackage,
                    statsUsageByPackage = sanitizedStatsUsageByPackage,
                    dailyUsageByPackage = sanitizedDailyUsageByPackage,
                ),
                maxAllowedUsageMillis = maxAllowedUsageMillis,
            )
                .filter { (packageName, totalTimeMillis) ->
                    totalTimeMillis > 0L &&
                        (launchablePackages.isEmpty() || packageName in launchablePackages) &&
                        isVisibleUsageApp(packageName)
                }
            historyStore.mergeDays(mapOf(startTime to stableUsageByPackage))

            stableUsageByPackage
                .filter { (packageName, totalTimeMillis) ->
                    totalTimeMillis >= MIN_VISIBLE_USAGE_MILLIS
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
            todayContinuitySnapshot()
                .toVisibleAppUsageInfo(maxItems = maxItems)
        }
    }

    fun getTodayUsageMillisByPackage(skipAccessCheck: Boolean = false): Map<String, Long> {
        if (!skipAccessCheck && !hasUsageAccess()) {
            return todayContinuitySnapshot()
        }

        return try {
            val startTime = localDayStartMillis()
            val endTime = System.currentTimeMillis()
            val currentForeground = getCurrentForegroundSnapshot()
            val eventUsageByPackage = runCatching {
                getEventForegroundUsage(
                    startTime = startTime,
                    endTime = endTime,
                    currentForegroundPackageName = currentForeground.packageName,
                    foregroundBoundaryTimeMillis = currentForeground.updatedAtWallClockMillis,
                )
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
            val maxAllowedUsageMillis = maxPossibleTodayUsageMillis(startTime, endTime)
            val sanitizedEventUsageByPackage = sanitizeTodayUsageMap(
                source = "events",
                usageByPackage = eventUsageByPackage,
                startTime = startTime,
                endTime = endTime,
            )
            val sanitizedStatsUsageByPackage = sanitizeTodayUsageMap(
                source = "aggregate",
                usageByPackage = statsUsageByPackage,
                startTime = startTime,
                endTime = endTime,
            )
            val sanitizedDailyUsageByPackage = sanitizeTodayUsageMap(
                source = "daily",
                usageByPackage = dailyUsageByPackage,
                startTime = startTime,
                endTime = endTime,
            )
            auditTodayUsageSources(
                startTime = startTime,
                endTime = endTime,
                eventUsageByPackage = eventUsageByPackage,
                statsUsageByPackage = statsUsageByPackage,
                dailyUsageByPackage = dailyUsageByPackage,
            )

            val stableUsageByPackage = applyMonotonicUsage(
                dayStartMillis = startTime,
                usageByPackage = mergeUsageByPackage(
                    eventUsageByPackage = sanitizedEventUsageByPackage,
                    statsUsageByPackage = sanitizedStatsUsageByPackage,
                    dailyUsageByPackage = sanitizedDailyUsageByPackage,
                ),
                maxAllowedUsageMillis = maxAllowedUsageMillis,
            )
                .filter { (packageName, totalTimeMillis) ->
                    totalTimeMillis > 0L &&
                        (launchablePackages.isEmpty() || packageName in launchablePackages) &&
                        isVisibleUsageApp(packageName)
                }
            historyStore.mergeDays(mapOf(startTime to stableUsageByPackage))
            stableUsageByPackage
        } catch (_: RuntimeException) {
            todayContinuitySnapshot()
        }
    }

    fun rememberTodayUsageMillis(
        packageName: String,
        usageMillis: Long,
        forceWrite: Boolean = false,
    ): Long {
        val todayStartMillis = localDayStartMillis()
        val rememberedUsageMillis = continuityStore.rememberUsage(
            dayStartMillis = todayStartMillis,
            packageName = packageName,
            usageMillis = usageMillis,
            forceWrite = forceWrite,
            maxAllowedUsageMillis = maxPossibleTodayUsageMillis(),
        )
        historyStore.mergeDays(
            usageByDay = mapOf(
                todayStartMillis to continuityStore.snapshot(
                    dayStartMillis = todayStartMillis,
                    maxAllowedUsageMillis = maxPossibleTodayUsageMillis(),
                ),
            ),
            forceWrite = forceWrite,
        )
        return rememberedUsageMillis
    }

    fun flushUsageContinuity() {
        continuityStore.flush()
        historyStore.flush()
    }

    fun getDailyUsage(days: Int = 7, skipAccessCheck: Boolean = false): List<DailyUsageInfo> {
        if (!skipAccessCheck && !hasUsageAccess()) {
            return emptyList()
        }

        return try {
            val safeDays = days.coerceIn(1, 31)
            val todayStartMillis = localDayStartMillis()
            val dayStarts = (safeDays - 1 downTo 0).map { offset ->
                Calendar.getInstance().apply {
                    timeInMillis = todayStartMillis
                    add(Calendar.DAY_OF_YEAR, -offset)
                }.timeInMillis
            }
            val launchablePackages = runCatching {
                getLaunchablePackages()
            }.getOrDefault(emptySet())
            val usageHistory = refreshUsageHistory(
                dayStarts = dayStarts,
                launchablePackages = launchablePackages,
            )

            dayStarts.map { dayStartMillis ->
                val usageByPackage = usageHistory[dayStartMillis]
                DailyUsageInfo(
                    dayStartMillis = dayStartMillis,
                    dayLabel = dayLabel(dayStartMillis),
                    totalTimeMillis = usageByPackage.orEmpty().values.sum(),
                    hasRecordedData = usageByPackage != null,
                )
            }
        } catch (_: RuntimeException) {
            emptyList()
        }
    }

    fun getTopAppsUsage(
        days: Int = 1,
        maxItems: Int = 10,
        skipAccessCheck: Boolean = false,
    ): List<AppUsageInfo> {
        if (!skipAccessCheck && !hasUsageAccess()) {
            return emptyList()
        }

        val safeDays = days.coerceIn(1, 31)
        if (safeDays <= 1) {
            return getTodayUsage(maxItems = maxItems, skipAccessCheck = true)
        }

        return try {
            val todayStartMillis = localDayStartMillis()
            val dayStarts = (safeDays - 1 downTo 0).map { offset ->
                Calendar.getInstance().apply {
                    timeInMillis = todayStartMillis
                    add(Calendar.DAY_OF_YEAR, -offset)
                }.timeInMillis
            }
            val launchablePackages = runCatching {
                getLaunchablePackages()
            }.getOrDefault(emptySet())
            val usageByPackage = mutableMapOf<String, Long>()

            refreshUsageHistory(
                dayStarts = dayStarts,
                launchablePackages = launchablePackages,
            ).values.forEach { dailyUsageByPackage ->
                dailyUsageByPackage.forEach { (packageName, totalTimeMillis) ->
                    usageByPackage[packageName] =
                        (usageByPackage[packageName] ?: 0L) + totalTimeMillis
                }
            }

            usageByPackage
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

    private fun refreshUsageHistory(
        dayStarts: List<Long>,
        launchablePackages: Set<String>,
    ): Map<Long, Map<String, Long>> {
        if (dayStarts.isEmpty()) {
            return emptyMap()
        }
        val requestedDays = dayStarts.toSet()
        val updates = mutableMapOf<Long, Map<String, Long>>()
        var systemDailyQuerySucceeded = false
        try {
            val systemUsageByDay = querySystemDailyUsage(
                startTime = dayStarts.first(),
                endTime = System.currentTimeMillis(),
                requestedDays = requestedDays,
                launchablePackages = launchablePackages,
            )
            systemDailyQuerySucceeded = true
            updates.putAll(systemUsageByDay)
        } catch (_: RuntimeException) {
            // Previously recorded days remain available when the system query is temporarily unavailable.
        }

        if (systemDailyQuerySucceeded) {
            dayStarts
                .takeLast(minOf(dayStarts.size, SYSTEM_DAILY_RETENTION_DAYS))
                .forEach { dayStartMillis ->
                    updates.putIfAbsent(dayStartMillis, emptyMap())
                }
        }

        val todayStartMillis = dayStarts.last()
        val todayUsageByPackage = getTodayUsageMillisByPackage(skipAccessCheck = true)
        updates[todayStartMillis] = mergeUsageByMaximum(
            updates[todayStartMillis].orEmpty(),
            todayUsageByPackage,
        )

        historyStore.mergeDays(
            usageByDay = updates,
            forceWrite = true,
        )
        return historyStore.snapshot(dayStarts)
    }

    private fun querySystemDailyUsage(
        startTime: Long,
        endTime: Long,
        requestedDays: Set<Long>,
        launchablePackages: Set<String>,
    ): Map<Long, Map<String, Long>> {
        val usageByDay = mutableMapOf<Long, MutableMap<String, Long>>()
        usageStatsManager
            .queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
            .orEmpty()
            .forEach { usageStats ->
                val packageName = usageStats.packageName
                val totalTimeMillis = usageStats.totalTimeInForeground
                if (
                    totalTimeMillis <= 0L ||
                    totalTimeMillis > MAX_PLAUSIBLE_DAILY_USAGE_MILLIS ||
                    (launchablePackages.isNotEmpty() && packageName !in launchablePackages) ||
                    !isVisibleUsageApp(packageName)
                ) {
                    return@forEach
                }
                val timestamp = when {
                    usageStats.lastTimeUsed > 0L -> usageStats.lastTimeUsed
                    usageStats.lastTimeStamp > 0L -> usageStats.lastTimeStamp
                    else -> usageStats.firstTimeStamp
                }
                val dayStartMillis = dayStartMillisFor(timestamp)
                if (dayStartMillis !in requestedDays) {
                    return@forEach
                }
                val dailyUsageByPackage = usageByDay.getOrPut(dayStartMillis) { mutableMapOf() }
                dailyUsageByPackage[packageName] =
                    (dailyUsageByPackage[packageName] ?: 0L) + totalTimeMillis
            }
        return usageByDay
    }

    fun getAppLabel(packageName: String): String {
        return getAppName(packageName)
    }

    fun getCurrentForegroundPackageName(
        lookbackMillis: Long = CURRENT_FOREGROUND_LOOKBACK_MILLIS,
    ): String? {
        ForegroundAppTracker.snapshot(CURRENT_FOREGROUND_TRACKER_MAX_AGE_MILLIS)?.let { snapshot ->
            return snapshot.packageName
        }

        val usageEventsState = getUsageEventsCurrentForegroundState(lookbackMillis)
        usageEventsState.packageName?.let { packageName -> return packageName }
        if (usageEventsState.hasRelevantTerminalEvent) {
            return null
        }
        return getRecentUsageStatsForegroundSnapshot()?.packageName
    }

    private fun getCurrentForegroundSnapshot(): CurrentForegroundSnapshot {
        ForegroundAppTracker.snapshot(CURRENT_FOREGROUND_TRACKER_MAX_AGE_MILLIS)?.let { snapshot ->
            return CurrentForegroundSnapshot(
                packageName = snapshot.packageName,
                updatedAtWallClockMillis = snapshot.updatedAtWallClockMillis,
            )
        }

        val usageEventsState = getUsageEventsCurrentForegroundState()
        if (usageEventsState.packageName != null) {
            return CurrentForegroundSnapshot(
                packageName = usageEventsState.packageName,
                updatedAtWallClockMillis = null,
            )
        }
        if (usageEventsState.hasRelevantTerminalEvent) {
            return CurrentForegroundSnapshot(packageName = null, updatedAtWallClockMillis = null)
        }

        return getRecentUsageStatsForegroundSnapshot()
            ?: CurrentForegroundSnapshot(packageName = null, updatedAtWallClockMillis = null)
    }

    private fun getUsageEventsCurrentForegroundPackageName(
        lookbackMillis: Long = CURRENT_FOREGROUND_LOOKBACK_MILLIS,
    ): String? {
        return getUsageEventsCurrentForegroundState(lookbackMillis).packageName
    }

    private fun getUsageEventsCurrentForegroundState(
        lookbackMillis: Long = CURRENT_FOREGROUND_LOOKBACK_MILLIS,
    ): UsageEventsForegroundState {
        if (!hasUsageAccess()) {
            return UsageEventsForegroundState()
        }

        return try {
            val endTime = System.currentTimeMillis()
            val startTime = maxOf(endTime - lookbackMillis, localDayStartMillis())
            val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
            val event = UsageEvents.Event()
            var foregroundPackageName: String? = null
            var hasRelevantTerminalEvent = false

            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)
                val eventPackageName = event.packageName.orEmpty()
                when {
                    isSessionClearEvent(event.eventType) -> {
                        foregroundPackageName = null
                        hasRelevantTerminalEvent = true
                    }

                    isForegroundEvent(event.eventType) -> {
                        when {
                            eventPackageName.isBlank() -> Unit
                            eventPackageName == context.packageName -> {
                                foregroundPackageName = null
                                hasRelevantTerminalEvent = true
                            }
                            AppVisibility.clearsForegroundSession(eventPackageName) -> {
                                foregroundPackageName = null
                                hasRelevantTerminalEvent = true
                            }
                            AppVisibility.isHiddenPackage(eventPackageName) -> Unit
                            else -> {
                                foregroundPackageName = eventPackageName
                                hasRelevantTerminalEvent = false
                            }
                        }
                    }

                    isBackgroundEvent(event.eventType) && eventPackageName.isNotBlank() -> {
                        if (foregroundPackageName == eventPackageName || foregroundPackageName == null) {
                            foregroundPackageName = null
                            hasRelevantTerminalEvent = true
                        }
                    }
                }
            }

            UsageEventsForegroundState(
                packageName = foregroundPackageName,
                hasRelevantTerminalEvent = hasRelevantTerminalEvent,
            )
        } catch (_: RuntimeException) {
            UsageEventsForegroundState()
        }
    }

    private fun getRecentUsageStatsForegroundSnapshot(): CurrentForegroundSnapshot? {
        if (!hasUsageAccess()) {
            return null
        }

        return try {
            val endTime = System.currentTimeMillis()
            val startTime = maxOf(endTime - RECENT_FOREGROUND_STATS_LOOKBACK_MILLIS, localDayStartMillis())
            val launchablePackages = runCatching {
                getLaunchablePackages()
            }.getOrDefault(emptySet())
            usageStatsManager
                .queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
                .asSequence()
                .filter { usageStats ->
                    val packageName = usageStats.packageName
                    val lastTimeUsed = usageStats.lastTimeUsed
                    packageName.isNotBlank() &&
                        packageName != context.packageName &&
                        isVisibleUsageApp(packageName) &&
                        (launchablePackages.isEmpty() || packageName in launchablePackages) &&
                        lastTimeUsed > 0L &&
                        endTime - lastTimeUsed <= RECENT_FOREGROUND_STATS_MAX_AGE_MILLIS
                }
                .maxByOrNull { usageStats -> usageStats.lastTimeUsed }
                ?.let { usageStats ->
                    CurrentForegroundSnapshot(
                        packageName = usageStats.packageName,
                        updatedAtWallClockMillis = usageStats.lastTimeUsed,
                    )
                }
        } catch (_: RuntimeException) {
            null
        }
    }

    fun getRawCurrentForegroundPackageName(
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
                    isSessionClearEvent(event.eventType) -> {
                        foregroundPackageName = null
                    }

                    isForegroundEvent(event.eventType) && eventPackageName.isNotBlank() -> {
                        foregroundPackageName = eventPackageName
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

    private fun getEventForegroundUsage(
        startTime: Long,
        endTime: Long,
        currentForegroundPackageName: String?,
        foregroundBoundaryTimeMillis: Long?,
    ): Map<String, Long> {
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
                isSessionClearEvent(event.eventType) -> {
                    closeForegroundSession(event.timeStamp)
                }

                isForegroundEvent(event.eventType) -> {
                    val eventPackageName = event.packageName.orEmpty()
                    when {
                        eventPackageName.isBlank() -> Unit
                        eventPackageName == context.packageName ||
                            AppVisibility.clearsForegroundSession(eventPackageName) -> {
                            closeForegroundSession(event.timeStamp)
                        }
                        AppVisibility.isHiddenPackage(eventPackageName) -> Unit
                        foregroundPackageName != eventPackageName -> {
                            closeForegroundSession(event.timeStamp)
                            foregroundPackageName = eventPackageName
                            foregroundStartedAt = event.timeStamp
                        }
                        foregroundStartedAt <= 0L -> {
                            foregroundStartedAt = event.timeStamp
                        }
                    }
                }

                isBackgroundEvent(event.eventType) && foregroundPackageName == event.packageName -> {
                    closeForegroundSession(event.timeStamp)
                }
            }
        }

        when {
            foregroundPackageName == currentForegroundPackageName -> {
                closeForegroundSession(endTime)
            }
            foregroundBoundaryTimeMillis != null && foregroundStartedAt > 0L -> {
                closeForegroundSession(foregroundBoundaryTimeMillis)
            }
        }
        return usageByPackage
    }

    private fun getStatsForegroundUsage(startTime: Long, endTime: Long): Map<String, Long> {
        return usageStatsManager
            .queryAndAggregateUsageStats(startTime, endTime)
            .filter { (packageName, usageStats) ->
                usageStats.totalTimeInForeground > 0L &&
                    usageStatsHasTodaySignal(
                        source = "aggregate",
                        packageName = packageName,
                        usageStats = usageStats,
                        startTime = startTime,
                        endTime = endTime,
                    )
            }
            .mapValues { (_, usageStats) -> usageStats.totalTimeInForeground }
    }

    private fun mergeUsageByPackage(
        eventUsageByPackage: Map<String, Long>,
        statsUsageByPackage: Map<String, Long>,
        dailyUsageByPackage: Map<String, Long>,
    ): Map<String, Long> {
        return mergeUsageSources(
            eventUsageByPackage = eventUsageByPackage,
            statsUsageByPackage = statsUsageByPackage,
            dailyUsageByPackage = dailyUsageByPackage,
        )
    }

    @Synchronized
    private fun applyMonotonicUsage(
        dayStartMillis: Long,
        usageByPackage: Map<String, Long>,
        maxAllowedUsageMillis: Long,
    ): Map<String, Long> {
        if (monotonicUsageDayStartMillis != dayStartMillis) {
            monotonicUsageDayStartMillis = dayStartMillis
            monotonicUsageMillisByPackage.clear()
        }

        monotonicUsageMillisByPackage
            .filterValues { totalTimeMillis -> totalTimeMillis > maxAllowedUsageMillis }
            .keys
            .forEach { packageName ->
                Log.w(
                    USAGE_AUDIT_TAG,
                    "prune impossible monotonic package=$packageName usage=${monotonicUsageMillisByPackage[packageName] ?: 0L} max=$maxAllowedUsageMillis",
                )
                monotonicUsageMillisByPackage.remove(packageName)
            }

        val sanitizedUsageByPackage = sanitizeTodayUsageMap(
            source = "merged",
            usageByPackage = usageByPackage,
            startTime = dayStartMillis,
            endTime = System.currentTimeMillis(),
        )
        val observedPackageNames = sanitizedUsageByPackage.keys
        monotonicUsageMillisByPackage
            .keys
            .filter { packageName -> packageName !in observedPackageNames }
            .forEach { packageName ->
                Log.w(
                    USAGE_AUDIT_TAG,
                    "prune unobserved monotonic package=$packageName usage=${monotonicUsageMillisByPackage[packageName] ?: 0L}",
                )
                monotonicUsageMillisByPackage.remove(packageName)
            }

        sanitizedUsageByPackage.forEach { (packageName, totalTimeMillis) ->
            monotonicUsageMillisByPackage[packageName] =
                maxOf(monotonicUsageMillisByPackage[packageName] ?: 0L, totalTimeMillis)
        }

        val processStableUsageByPackage = (sanitizedUsageByPackage.keys + monotonicUsageMillisByPackage.keys)
            .associateWith { packageName ->
                maxOf(
                    sanitizedUsageByPackage[packageName] ?: 0L,
                    monotonicUsageMillisByPackage[packageName] ?: 0L,
                )
            }
        return continuityStore.mergeRawUsage(
            dayStartMillis = dayStartMillis,
            rawUsageMillisByPackage = processStableUsageByPackage,
            maxAllowedUsageMillis = maxAllowedUsageMillis,
            replaceMissingPackages = true,
        )
    }

    private fun getDailyForegroundUsage(startTime: Long, endTime: Long): Map<String, Long> {
        return usageStatsManager
            .queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
            .filter { usageStats ->
                usageStats.totalTimeInForeground > 0L &&
                    usageStatsHasTodaySignal(
                        source = "daily",
                        packageName = usageStats.packageName,
                        usageStats = usageStats,
                        startTime = startTime,
                        endTime = endTime,
                    )
            }
            .groupBy { usageStats -> usageStats.packageName }
            .mapValues { (_, usageStats) -> usageStats.sumOf { stat -> stat.totalTimeInForeground } }
    }

    private fun usageStatsHasTodaySignal(
        source: String,
        packageName: String,
        usageStats: UsageStats,
        startTime: Long,
        endTime: Long,
    ): Boolean {
        val lastTimeUsed = usageStats.lastTimeUsed
        val hasTodaySignal = lastTimeUsed in startTime..endTime
        if (!hasTodaySignal && usageStats.totalTimeInForeground > 0L) {
            Log.w(
                USAGE_AUDIT_TAG,
                "drop stale today usage source=$source package=$packageName app=${getAppName(packageName)} total=${usageStats.totalTimeInForeground} lastTimeUsed=$lastTimeUsed start=$startTime end=$endTime",
            )
        }
        return hasTodaySignal
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

    private fun isSessionClearEvent(eventType: Int): Boolean {
        return eventType == UsageEvents.Event.SCREEN_NON_INTERACTIVE ||
            eventType == UsageEvents.Event.KEYGUARD_SHOWN
    }

    private fun localDayStartMillis(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun dayStartMillisFor(timestampMillis: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timestampMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun dayLabel(dayStartMillis: Long): String {
        return java.text.SimpleDateFormat("E", Locale.getDefault()).format(Date(dayStartMillis))
    }

    private fun isVisibleUsageApp(packageName: String): Boolean {
        return packageName != context.packageName && !AppVisibility.isHiddenPackage(packageName)
    }

    private fun getLaunchablePackages(): Set<String> {
        val now = SystemClock.elapsedRealtime()
        launchablePackagesCache?.let { cachedPackages ->
            if (now - launchablePackagesCacheAtMillis < LAUNCHABLE_PACKAGES_CACHE_MILLIS) {
                return cachedPackages
            }
        }

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
            .also { packages ->
                launchablePackagesCache = packages
                launchablePackagesCacheAtMillis = now
            }
    }

    private fun getAppName(packageName: String): String {
        appNameCache[packageName]?.let { appName ->
            return appName
        }
        val appName = try {
            val applicationInfo = getApplicationInfo(packageName)
            if (applicationInfo != null) {
                packageManager.getApplicationLabel(applicationInfo).toString()
            } else {
                packageName.toReadableFallbackName()
            }
        } catch (_: PackageManager.NameNotFoundException) {
            packageName.toReadableFallbackName()
        }
        appNameCache[packageName] = appName
        return appName
    }

    private fun todayContinuitySnapshot(): Map<String, Long> {
        val startTime = localDayStartMillis()
        val endTime = System.currentTimeMillis()
        val rawSnapshot = continuityStore.snapshot(dayStartMillis = startTime)
        auditContinuitySnapshot(
            startTime = startTime,
            endTime = endTime,
            usageByPackage = rawSnapshot,
        )
        return continuityStore
            .snapshot(
                dayStartMillis = startTime,
                maxAllowedUsageMillis = maxPossibleTodayUsageMillis(startTime, endTime),
            )
    }

    private fun maxPossibleTodayUsageMillis(
        startTime: Long = localDayStartMillis(),
        endTime: Long = System.currentTimeMillis(),
    ): Long {
        return (endTime - startTime).coerceAtLeast(0L) + IMPOSSIBLE_USAGE_TOLERANCE_MILLIS
    }

    private fun sanitizeTodayUsageMap(
        source: String,
        usageByPackage: Map<String, Long>,
        startTime: Long,
        endTime: Long,
    ): Map<String, Long> {
        if (usageByPackage.isEmpty()) {
            return usageByPackage
        }
        val maxAllowedUsageMillis = maxPossibleTodayUsageMillis(startTime, endTime)
        return usageByPackage.filter { (packageName, totalTimeMillis) ->
            val possible = totalTimeMillis <= maxAllowedUsageMillis
            if (!possible) {
                Log.w(
                    USAGE_AUDIT_TAG,
                    "drop impossible today usage source=$source package=$packageName app=${getAppName(packageName)} usage=$totalTimeMillis max=$maxAllowedUsageMillis elapsedToday=${(endTime - startTime).coerceAtLeast(0L)} start=$startTime end=$endTime",
                )
            }
            possible
        }
    }

    private fun auditTodayUsageSources(
        startTime: Long,
        endTime: Long,
        eventUsageByPackage: Map<String, Long>,
        statsUsageByPackage: Map<String, Long>,
        dailyUsageByPackage: Map<String, Long>,
    ) {
        val maxAllowedUsageMillis = maxPossibleTodayUsageMillis(startTime, endTime)
        val suspiciousPackages = (
            eventUsageByPackage.filterValues { usageMillis -> usageMillis > maxAllowedUsageMillis }.keys +
                statsUsageByPackage.filterValues { usageMillis -> usageMillis > maxAllowedUsageMillis }.keys +
                dailyUsageByPackage.filterValues { usageMillis -> usageMillis > maxAllowedUsageMillis }.keys
            ).toSortedSet()

        suspiciousPackages.forEach { packageName ->
            Log.w(
                USAGE_AUDIT_TAG,
                "source comparison package=$packageName app=${getAppName(packageName)} events=${eventUsageByPackage[packageName] ?: 0L} aggregate=${statsUsageByPackage[packageName] ?: 0L} daily=${dailyUsageByPackage[packageName] ?: 0L} max=$maxAllowedUsageMillis elapsedToday=${(endTime - startTime).coerceAtLeast(0L)} start=$startTime end=$endTime",
            )
        }
    }

    private fun auditContinuitySnapshot(
        startTime: Long,
        endTime: Long,
        usageByPackage: Map<String, Long>,
    ) {
        val maxAllowedUsageMillis = maxPossibleTodayUsageMillis(startTime, endTime)
        usageByPackage
            .filterValues { usageMillis -> usageMillis > maxAllowedUsageMillis }
            .forEach { (packageName, usageMillis) ->
                Log.w(
                    USAGE_AUDIT_TAG,
                    "continuity still impossible package=$packageName app=${getAppName(packageName)} usage=$usageMillis max=$maxAllowedUsageMillis elapsedToday=${(endTime - startTime).coerceAtLeast(0L)}",
                )
            }
    }

    private fun Map<String, Long>.toVisibleAppUsageInfo(maxItems: Int): List<AppUsageInfo> {
        val launchablePackages = runCatching {
            getLaunchablePackages()
        }.getOrDefault(emptySet())
        return filter { (packageName, totalTimeMillis) ->
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
            .sortedByDescending { usage -> usage.totalTimeMillis }
            .take(maxItems)
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

    private data class CurrentForegroundSnapshot(
        val packageName: String?,
        val updatedAtWallClockMillis: Long?,
    )

    private data class UsageEventsForegroundState(
        val packageName: String? = null,
        val hasRelevantTerminalEvent: Boolean = false,
    )

    companion object {
        private const val MIN_VISIBLE_USAGE_MILLIS = 10_000L
        private const val SYSTEM_DAILY_RETENTION_DAYS = 10
        private const val MAX_PLAUSIBLE_DAILY_USAGE_MILLIS = 26L * 60L * 60L * 1000L
        private const val USAGE_ACCESS_PROBE_WINDOW_MILLIS = 7L * 24L * 60L * 60L * 1000L
        private const val CURRENT_FOREGROUND_LOOKBACK_MILLIS = 30_000L
        private const val CURRENT_FOREGROUND_TRACKER_MAX_AGE_MILLIS = 2_500L
        private const val RECENT_FOREGROUND_STATS_LOOKBACK_MILLIS = 30_000L
        private const val RECENT_FOREGROUND_STATS_MAX_AGE_MILLIS = 15_000L
        private const val LAUNCHABLE_PACKAGES_CACHE_MILLIS = 30_000L
        private const val IMPOSSIBLE_USAGE_TOLERANCE_MILLIS = 2L * 60L * 1000L
        private const val USAGE_AUDIT_TAG = "STM-UsageAudit"
    }
}

private fun mergeUsageByMaximum(
    first: Map<String, Long>,
    second: Map<String, Long>,
): Map<String, Long> {
    return (first.keys + second.keys)
        .associateWith { packageName ->
            maxOf(first[packageName] ?: 0L, second[packageName] ?: 0L)
        }
        .filterValues { usageMillis -> usageMillis > 0L }
}

internal fun mergeUsageSources(
    eventUsageByPackage: Map<String, Long>,
    statsUsageByPackage: Map<String, Long>,
    dailyUsageByPackage: Map<String, Long>,
): Map<String, Long> {
    return (eventUsageByPackage.keys + statsUsageByPackage.keys + dailyUsageByPackage.keys)
        .associateWith { packageName ->
            maxOf(
                eventUsageByPackage[packageName] ?: 0L,
                statsUsageByPackage[packageName] ?: 0L,
                dailyUsageByPackage[packageName] ?: 0L,
            )
        }
        .filterValues { usageMillis -> usageMillis > 0L }
}
