package com.manisykh.screenrest.worker

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.manisykh.screenrest.blocking.UsageMonitorForegroundService
import com.manisykh.screenrest.formatLimitMinutesLabel
import com.manisykh.screenrest.data.EventLogType
import com.manisykh.screenrest.data.SettingsRepository
import com.manisykh.screenrest.data.SystemHealthStatus
import com.manisykh.screenrest.data.normalizedAppGroups
import com.manisykh.screenrest.data.settingsDataStore
import com.manisykh.screenrest.notification.UsageNotificationHelper
import com.manisykh.screenrest.ui.safety.appLimitMap
import com.manisykh.screenrest.ui.safety.todayLimitMinutes
import com.manisykh.screenrest.usage.UsageStatsRepository
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class UsagePolicyCheckWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        UsagePolicyAlertRunner.evaluate(applicationContext, sendNotifications = true)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "usage_policy_check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UsagePolicyCheckWorker>(
                15,
                TimeUnit.MINUTES,
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}

class SystemHealthCheckWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val appContext = applicationContext
        val settingsRepository = SettingsRepository(appContext.settingsDataStore)
        val usageRepository = UsageStatsRepository(appContext)
        val notificationHelper = UsageNotificationHelper(appContext)
        val safeModeEnabled = settingsRepository.safeModeEnabled.first()
        val policyEnforcementEnabled = settingsRepository.policyEnforcementEnabled.first()
        val monitorStatus = settingsRepository.usageMonitorStatus.first()
        val now = System.currentTimeMillis()
        val serviceExpected = !safeModeEnabled && policyEnforcementEnabled
        val foregroundServiceFresh = monitorStatus.lastTickMillis > 0L &&
            now - monitorStatus.lastTickMillis <= HEALTH_MONITOR_FRESH_MILLIS
        val usageAccessReady = usageRepository.hasUsageAccess()
        val overlayPermissionReady = Settings.canDrawOverlays(appContext)
        val notificationPermissionReady = notificationHelper.canPostNotifications()
        val notificationAccessReady = hasNotificationListenerAccess(appContext)
        val exactAlarmReady = canScheduleExactAlarms(appContext)
        val status = SystemHealthStatus(
            lastCheckedMillis = now,
            usageAccessReady = usageAccessReady,
            overlayPermissionReady = overlayPermissionReady,
            notificationPermissionReady = notificationPermissionReady,
            notificationAccessReady = notificationAccessReady,
            exactAlarmReady = exactAlarmReady,
            foregroundServiceExpected = serviceExpected,
            foregroundServiceRunning = monitorStatus.running,
            foregroundServiceFresh = foregroundServiceFresh,
            lastIssue = buildIssueSummary(
                serviceExpected = serviceExpected,
                usageAccessReady = usageAccessReady,
                overlayPermissionReady = overlayPermissionReady,
                notificationPermissionReady = notificationPermissionReady,
                notificationAccessReady = notificationAccessReady,
                exactAlarmReady = exactAlarmReady,
                foregroundServiceRunning = monitorStatus.running,
                foregroundServiceFresh = foregroundServiceFresh,
            ),
        )
        settingsRepository.updateSystemHealthStatus(status)
        if (serviceExpected && status.usageAccessReady && (!monitorStatus.running || !foregroundServiceFresh)) {
            UsageMonitorRecoveryWorker.schedule(
                context = appContext,
                forceRestart = true,
                reason = "health check",
            )
        }
        return Result.success()
    }

    companion object {
        private const val UNIQUE_PERIODIC_WORK_NAME = "system_health_check_periodic"
        private const val UNIQUE_ONE_SHOT_WORK_NAME = "system_health_check_now"
        private const val HEALTH_MONITOR_FRESH_MILLIS = 90_000L

        fun scheduleNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SystemHealthCheckWorker>()
                .setInitialDelay(1, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_ONE_SHOT_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<SystemHealthCheckWorker>(
                15,
                TimeUnit.MINUTES,
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        private fun hasNotificationListenerAccess(context: Context): Boolean {
            val enabledListeners = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners",
            ).orEmpty()
            return enabledListeners.contains(context.packageName, ignoreCase = true)
        }

        private fun canScheduleExactAlarms(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                return true
            }
            return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        }

        private fun buildIssueSummary(
            serviceExpected: Boolean,
            usageAccessReady: Boolean,
            overlayPermissionReady: Boolean,
            notificationPermissionReady: Boolean,
            notificationAccessReady: Boolean,
            exactAlarmReady: Boolean,
            foregroundServiceRunning: Boolean,
            foregroundServiceFresh: Boolean,
        ): String {
            return listOfNotNull(
                "usage access".takeUnless { usageAccessReady },
                "overlay".takeUnless { overlayPermissionReady },
                "notification permission".takeUnless { notificationPermissionReady },
                "notification access".takeUnless { notificationAccessReady },
                "exact alarm".takeUnless { exactAlarmReady },
                "monitor stopped".takeIf { serviceExpected && !foregroundServiceRunning },
                "monitor stale".takeIf { serviceExpected && foregroundServiceRunning && !foregroundServiceFresh },
            ).joinToString(", ")
        }
    }
}

class DailyRolloverWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val appContext = applicationContext
        val settingsRepository = SettingsRepository(appContext.settingsDataStore)
        settingsRepository.recordDailyRollover()
        UsagePolicyCheckWorker.schedule(appContext)
        SystemHealthCheckWorker.scheduleNow(appContext)
        SystemHealthCheckWorker.schedulePeriodic(appContext)
        UsageMonitorRecoveryWorker.schedule(
            context = appContext,
            forceRestart = true,
            reason = "daily rollover",
        )
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "daily_rollover"

        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<DailyRolloverWorker>()
                .setInitialDelay(1, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}

class UsageMonitorRecoveryWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val appContext = applicationContext
        val settingsRepository = SettingsRepository(appContext.settingsDataStore)
        val usageRepository = UsageStatsRepository(appContext)
        val safeModeEnabled = settingsRepository.safeModeEnabled.first()
        val policyEnforcementEnabled = settingsRepository.policyEnforcementEnabled.first()
        if (safeModeEnabled || !policyEnforcementEnabled || !usageRepository.hasUsageAccess()) {
            return Result.success()
        }
        val forceRestart = inputData.getBoolean(KEY_FORCE_RESTART, false)
        val requestedReason = inputData.getString(KEY_RECOVERY_REASON).orEmpty().ifBlank { "watchdog" }
        val monitorStatus = settingsRepository.usageMonitorStatus.first()
        val now = System.currentTimeMillis()
        val stale = monitorStatus.lastTickMillis <= 0L ||
            now - monitorStatus.lastTickMillis > MONITOR_STALE_MILLIS
        val shouldRestart = forceRestart || !monitorStatus.running || stale
        if (!shouldRestart) {
            return Result.success()
        }

        UsageMonitorForegroundService.start(appContext)
        UsageMonitorForegroundService.scheduleExactRecoveryAlarm(
            context = appContext,
            reason = "watchdog follow-up",
        )
        val reason = when {
            forceRestart -> requestedReason
            !monitorStatus.running -> "watchdog: monitor marked stopped"
            stale -> "watchdog: monitor stale ${(now - monitorStatus.lastTickMillis).coerceAtLeast(0L) / 1_000L}s"
            else -> requestedReason
        }
        settingsRepository.recordUsageMonitorRecovery(reason)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "usage_monitor_recovery"
        private const val UNIQUE_PERIODIC_WORK_NAME = "usage_monitor_recovery_periodic"
        private const val KEY_FORCE_RESTART = "force_restart"
        private const val KEY_RECOVERY_REASON = "recovery_reason"
        private const val MONITOR_STALE_MILLIS = 90_000L

        fun schedule(
            context: Context,
            forceRestart: Boolean = false,
            reason: String = "one-shot recovery",
        ) {
            val request = OneTimeWorkRequestBuilder<UsageMonitorRecoveryWorker>()
                .setInitialDelay(2, TimeUnit.SECONDS)
                .setInputData(
                    workDataOf(
                        KEY_FORCE_RESTART to forceRestart,
                        KEY_RECOVERY_REASON to reason,
                    ),
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<UsageMonitorRecoveryWorker>(
                15,
                TimeUnit.MINUTES,
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}

object UsagePolicyAlertRunner {
    suspend fun evaluate(context: Context, sendNotifications: Boolean) {
        val appContext = context.applicationContext
        val settingsRepository = SettingsRepository(appContext.settingsDataStore)
        val usageRepository = UsageStatsRepository(appContext)
        val notificationHelper = UsageNotificationHelper(appContext)
        val safeModeEnabled = settingsRepository.safeModeEnabled.first()
        val policyEnforcementEnabled = settingsRepository.policyEnforcementEnabled.first()
        if (safeModeEnabled || !policyEnforcementEnabled || !usageRepository.hasUsageAccess()) {
            return
        }
        val warningNotificationsEnabled = settingsRepository.warningNotificationsEnabled.first()
        val limitNotificationsEnabled = settingsRepository.limitNotificationsEnabled.first()

        val settings = settingsRepository.usagePolicySettings.first()
        val temporaryUnlockState = settingsRepository.temporaryUnlockState.first().forToday()
        val usage = usageRepository.getTodayUsage(maxItems = 500)
        val usageByPackage = usage.associateBy { appUsage -> appUsage.packageName }
        val totalUsedMinutes = usage.sumOf { appUsage -> appUsage.totalTimeMillis }.toMinutesCeil()

        val totalLimitMinutes = settings.todayLimitMinutes()
        if (!temporaryUnlockState.totalUnlockedForToday && totalLimitMinutes > 0) {
            logIfNeeded(
                alertId = "total",
                usedMinutes = totalUsedMinutes,
                limitMinutes = totalLimitMinutes + temporaryUnlockState.totalExtraMinutes,
                warningMessage = "Total usage reached 80%",
                exceededMessage = "Total usage exceeded",
                settingsRepository = settingsRepository,
                notificationHelper = notificationHelper,
                sendNotifications = sendNotifications,
                warningNotificationsEnabled = warningNotificationsEnabled,
                limitNotificationsEnabled = limitNotificationsEnabled,
            )
        }

        settings.normalizedAppGroups().forEachIndexed { index, group ->
            if (group.budgetMinutes <= 0) {
                return@forEachIndexed
            }
            val groupUsedMinutes = group.packageNames
                .sumOf { packageName -> usageByPackage[packageName]?.totalTimeMillis ?: 0L }
                .toMinutesCeil()
            val groupExtraMinutes = group.packageNames
                .sumOf { packageName -> temporaryUnlockState.packageAllowances[packageName]?.extraMinutes ?: 0 }
            logIfNeeded(
                alertId = "group:$index:${group.name.hashCode()}",
                usedMinutes = groupUsedMinutes,
                limitMinutes = group.budgetMinutes + groupExtraMinutes,
                warningMessage = "${group.name} group reached 80%",
                exceededMessage = "${group.name} group exceeded",
                settingsRepository = settingsRepository,
                notificationHelper = notificationHelper,
                sendNotifications = sendNotifications,
                warningNotificationsEnabled = warningNotificationsEnabled,
                limitNotificationsEnabled = limitNotificationsEnabled,
            )
        }

        settings.appLimitMap().forEach { (packageName, limitMinutes) ->
            if (limitMinutes <= 0) {
                return@forEach
            }
            val appAllowance = temporaryUnlockState.packageAllowances[packageName]
            if (appAllowance?.unlockedForToday == true) {
                return@forEach
            }
            val appUsage = usageByPackage[packageName]
            val usedMinutes = (appUsage?.totalTimeMillis ?: 0L).toMinutesCeil()
            val appName = appUsage?.appName ?: packageName
            logIfNeeded(
                alertId = "app:$packageName",
                usedMinutes = usedMinutes,
                limitMinutes = limitMinutes + (appAllowance?.extraMinutes ?: 0),
                warningMessage = "$appName reached 80%",
                exceededMessage = "$appName exceeded",
                settingsRepository = settingsRepository,
                notificationHelper = notificationHelper,
                sendNotifications = sendNotifications,
                warningNotificationsEnabled = warningNotificationsEnabled,
                limitNotificationsEnabled = limitNotificationsEnabled,
            )
        }
    }

    private suspend fun logIfNeeded(
        alertId: String,
        usedMinutes: Int,
        limitMinutes: Int,
        warningMessage: String,
        exceededMessage: String,
        settingsRepository: SettingsRepository,
        notificationHelper: UsageNotificationHelper,
        sendNotifications: Boolean,
        warningNotificationsEnabled: Boolean,
        limitNotificationsEnabled: Boolean,
    ) {
        if (limitMinutes <= 0) {
            return
        }

        val alert = when {
            usedMinutes >= limitMinutes -> {
                PolicyAlert(EventLogType.Exceeded, "exceeded", exceededMessage)
            }

            usedMinutes * 100 >= limitMinutes * 80 -> {
                PolicyAlert(EventLogType.Warning, "warning", warningMessage)
            }

            else -> null
        } ?: return

        val message = "${alert.message} (${formatLimitMinutesLabel(usedMinutes)} / ${formatLimitMinutesLabel(limitMinutes)})"
        val recorded = settingsRepository.recordPolicyAlertOnce(
            alertKey = "${todayKey()}:${alert.level}:$alertId",
            type = alert.type,
            message = message,
        )
        val notificationEnabledForAlert = when (alert.type) {
            EventLogType.Warning -> warningNotificationsEnabled
            EventLogType.Exceeded -> limitNotificationsEnabled
            EventLogType.Info,
            EventLogType.Safety -> false
        }
        if (recorded && sendNotifications && notificationEnabledForAlert) {
            notificationHelper.showPolicyAlert("ScreenRest", message)
        }
    }

    private fun todayKey(): String {
        return SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
    }

    private fun Long.toMinutesCeil(): Int {
        if (this <= 0L) {
            return 0
        }
        return ((this + 59_999L) / 60_000L).toInt()
    }
}

private data class PolicyAlert(
    val type: EventLogType,
    val level: String,
    val message: String,
)
