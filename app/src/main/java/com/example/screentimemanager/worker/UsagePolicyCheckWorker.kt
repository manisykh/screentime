package com.example.screentimemanager.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.screentimemanager.data.EventLogType
import com.example.screentimemanager.data.SettingsRepository
import com.example.screentimemanager.data.normalizedAppGroups
import com.example.screentimemanager.data.settingsDataStore
import com.example.screentimemanager.notification.UsageNotificationHelper
import com.example.screentimemanager.ui.safety.appLimitMap
import com.example.screentimemanager.ui.safety.todayLimitMinutes
import com.example.screentimemanager.usage.UsageStatsRepository
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
        val usage = usageRepository.getTodayUsage(maxItems = 500)
        val usageByPackage = usage.associateBy { appUsage -> appUsage.packageName }
        val totalUsedMinutes = usage.sumOf { appUsage -> appUsage.totalTimeMillis }.toMinutesCeil()

        logIfNeeded(
            alertId = "total",
            usedMinutes = totalUsedMinutes,
            limitMinutes = settings.todayLimitMinutes(),
            warningMessage = "Total usage reached 80%",
            exceededMessage = "Total usage exceeded",
            settingsRepository = settingsRepository,
            notificationHelper = notificationHelper,
            sendNotifications = sendNotifications,
            warningNotificationsEnabled = warningNotificationsEnabled,
            limitNotificationsEnabled = limitNotificationsEnabled,
        )

        settings.normalizedAppGroups().forEachIndexed { index, group ->
            val groupUsedMinutes = group.packageNames
                .sumOf { packageName -> usageByPackage[packageName]?.totalTimeMillis ?: 0L }
                .toMinutesCeil()
            logIfNeeded(
                alertId = "group:$index:${group.name.hashCode()}",
                usedMinutes = groupUsedMinutes,
                limitMinutes = group.budgetMinutes,
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
            val appUsage = usageByPackage[packageName]
            val usedMinutes = (appUsage?.totalTimeMillis ?: 0L).toMinutesCeil()
            val appName = appUsage?.appName ?: packageName
            logIfNeeded(
                alertId = "app:$packageName",
                usedMinutes = usedMinutes,
                limitMinutes = limitMinutes,
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

        val message = "${alert.message} ($usedMinutes/$limitMinutes min)"
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
            notificationHelper.showPolicyAlert("Screen Time Manager", message)
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
