package com.manisykh.screenrest.blocking

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.manisykh.screenrest.worker.DailyRolloverWorker
import com.manisykh.screenrest.worker.RemoteParentSyncWorker
import com.manisykh.screenrest.worker.SystemHealthCheckWorker
import com.manisykh.screenrest.worker.UsageMonitorRecoveryWorker
import com.manisykh.screenrest.worker.UsagePolicyCheckWorker
import java.util.Calendar

class BootRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            ACTION_DAILY_ROLLOVER_ALARM,
            UsageMonitorForegroundService.ACTION_EXACT_RECOVERY_ALARM -> {
                val appContext = context.applicationContext
                val reason = intent.getStringExtra(UsageMonitorForegroundService.EXTRA_RECOVERY_REASON)
                    .orEmpty()
                    .ifBlank { intent.action.orEmpty().ifBlank { "boot/package recovery" } }
                UsagePolicyCheckWorker.schedule(appContext)
                SystemHealthCheckWorker.scheduleNow(appContext)
                SystemHealthCheckWorker.schedulePeriodic(appContext)
                RemoteParentSyncWorker.schedule(appContext)
                if (
                    intent.action == Intent.ACTION_DATE_CHANGED ||
                    intent.action == Intent.ACTION_TIME_CHANGED ||
                    intent.action == Intent.ACTION_TIMEZONE_CHANGED ||
                    intent.action == ACTION_DAILY_ROLLOVER_ALARM
                ) {
                    DailyRolloverWorker.schedule(appContext)
                }
                scheduleDailyRolloverAlarm(appContext)
                UsageMonitorRecoveryWorker.schedulePeriodic(appContext)
                UsageMonitorRecoveryWorker.schedule(
                    context = appContext,
                    forceRestart = true,
                    reason = reason,
                )
            }
        }
    }

    companion object {
        const val ACTION_DAILY_ROLLOVER_ALARM = "com.manisykh.screenrest.action.DAILY_ROLLOVER"

        fun scheduleDailyRolloverAlarm(context: Context) {
            val appContext = context.applicationContext
            val alarmManager = appContext.getSystemService(AlarmManager::class.java)
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !alarmManager.canScheduleExactAlarms()
            ) {
                return
            }
            val triggerAtMillis = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 3)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val pendingIntent = PendingIntent.getBroadcast(
                appContext,
                2001,
                Intent(appContext, BootRecoveryReceiver::class.java).apply {
                    action = ACTION_DAILY_ROLLOVER_ALARM
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            runCatching {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent,
                )
            }
        }
    }
}
