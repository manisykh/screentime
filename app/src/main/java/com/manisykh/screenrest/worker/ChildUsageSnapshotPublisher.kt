package com.manisykh.screenrest.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.manisykh.screenrest.data.ChildUsageSnapshot
import com.manisykh.screenrest.data.ChildTopAppUsage
import com.manisykh.screenrest.data.ParentDeviceRole
import com.manisykh.screenrest.data.ParentRemoteSyncDataSourceFactory
import com.manisykh.screenrest.data.ParentRemoteSyncResult
import com.manisykh.screenrest.data.SettingsRepository
import com.manisykh.screenrest.data.settingsDataStore
import com.manisykh.screenrest.safety.SafetyGate
import com.manisykh.screenrest.ui.safety.todayLimitMinutesOrNull
import com.manisykh.screenrest.usage.UsageStatsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Publishes one privacy-limited, replaceable document per paired child. */
object ChildUsageSnapshotPublisher {
    private const val MIN_INTERVAL_MILLIS = 15 * 60_000L
    private const val UNCHANGED_HEARTBEAT_MILLIS = 60 * 60_000L
    private const val PREFS = "child_usage_snapshot_sync"
    private const val LAST_PUBLISHED_AT = "last_published_at"
    private const val LAST_FINGERPRINT = "last_fingerprint"
    private const val LAST_REFRESH_REQUEST_ID = "last_refresh_request_id"
    private val refreshMutex = Mutex()

    /** A parent request is processed at most once; an acknowledgement can be retried safely. */
    suspend fun respondToRefreshIfPending(context: Context): ParentRemoteSyncResult? =
        refreshMutex.withLock {
            val appContext = context.applicationContext
            val repository = SettingsRepository(
                appContext.settingsDataStore,
                ParentRemoteSyncDataSourceFactory.create(appContext),
            )
            if (!repository.monitoringDisclosureAccepted.first()) return@withLock null
            val parentState = repository.parentManagementState.first()
            if (!parentState.paired || parentState.deviceRole != ParentDeviceRole.Child ||
                parentState.childDeviceId.isBlank()
            ) return@withLock null
            val requestResult = repository.fetchChildUsageRefresh(parentState.childDeviceId)
            if (requestResult.isFailure) {
                val error = requestResult.exceptionOrNull()
                val retryable = error is FirebaseNetworkException || error is java.io.IOException ||
                    (error is FirebaseFirestoreException && error.code in setOf(
                        FirebaseFirestoreException.Code.UNAVAILABLE,
                        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
                        FirebaseFirestoreException.Code.ABORTED,
                        FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED,
                    ))
                return@withLock ParentRemoteSyncResult.Failed(
                    "Could not fetch child usage refresh request",
                    retryable = retryable,
                )
            }
            val request = requestResult.getOrNull() ?: return@withLock null
            if (!request.isPending(System.currentTimeMillis())) return@withLock null
            val preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (preferences.getString(LAST_REFRESH_REQUEST_ID, null) != request.requestId) {
                val published = publishIfDue(appContext, force = true)
                if (published != ParentRemoteSyncResult.Success) return@withLock published
                preferences.edit().putString(LAST_REFRESH_REQUEST_ID, request.requestId).apply()
            }
            repository.acknowledgeChildUsageRefresh(parentState.childDeviceId, request.requestId)
        }

    suspend fun publishIfDue(context: Context, force: Boolean = false): ParentRemoteSyncResult? {
        val appContext = context.applicationContext
        val repository = SettingsRepository(
            appContext.settingsDataStore,
            ParentRemoteSyncDataSourceFactory.create(appContext),
        )
        if (!repository.monitoringDisclosureAccepted.first()) return null
        val parentState = repository.parentManagementState.first()
        if (!parentState.paired || parentState.deviceRole != ParentDeviceRole.Child ||
            parentState.childDeviceId.isBlank()
        ) return null

        val now = System.currentTimeMillis()
        val preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastPublishedAt = preferences.getLong(LAST_PUBLISHED_AT, 0L)
        if (!force && now >= lastPublishedAt &&
            now - lastPublishedAt < MIN_INTERVAL_MILLIS
        ) return null

        val usageRepository = UsageStatsRepository(appContext)
        val usageAccessReady = usageRepository.hasUsageAccess()
        val usageByPackage = if (usageAccessReady) {
            usageRepository.getTodayUsageMillisByPackage(skipAccessCheck = true)
        } else {
            emptyMap()
        }
        val todayUsedMillis = usageByPackage.values.sum().coerceIn(0L, 86_400_000L)
        val appUsageSharingEnabled = repository.childTopAppsSharingEnabled.first()
        val topApps = if (usageAccessReady && appUsageSharingEnabled) {
            usageRepository.topAppsFromUsageSnapshot(usageByPackage).map { app ->
                ChildTopAppUsage(
                    appName = app.appName.trim().take(80),
                    usedMillis = app.totalTimeMillis.coerceIn(0L, todayUsedMillis),
                )
            }.filter { app -> app.appName.isNotEmpty() && app.usedMillis > 0L }
        } else {
            emptyList()
        }
        val excludedPackages = SafetyGate.neverBlockPackages +
            SafetyGate.expandedUserAllowedPackages(repository.allRestrictionsExemptPackages.first())
        val dailyCountedUsageMillis = usageByPackage
            .filterKeys { packageName -> packageName !in excludedPackages }
            .values.sum().coerceIn(0L, todayUsedMillis)
        val baseLimitMinutes = repository.usagePolicySettings.first().todayLimitMinutesOrNull()
        val temporaryUnlocks = repository.temporaryUnlockState.first()
        val snapshot = ChildUsageSnapshot(
            childDeviceId = parentState.childDeviceId,
            dateKey = LocalDate.now().toString(),
            capturedAtMillis = now,
            todayUsedMillis = todayUsedMillis,
            dailyCountedUsageMillis = dailyCountedUsageMillis,
            effectiveDailyLimitMinutes = baseLimitMinutes?.let { limit ->
                (limit + temporaryUnlocks.totalExtraMinutes).coerceIn(0, 2_880)
            },
            dailyUnlockedForToday = temporaryUnlocks.totalUnlockedForToday,
            usageAccessReady = usageAccessReady,
            protectionPaused = repository.safeModeEnabled.first() ||
                !repository.policyEnforcementEnabled.first(),
            appUsageSharingEnabled = appUsageSharingEnabled,
            topApps = topApps,
        )
        val fingerprint = snapshot.copy(capturedAtMillis = 0L).toString()
        if (!force && fingerprint == preferences.getString(LAST_FINGERPRINT, null) &&
            now >= lastPublishedAt && now - lastPublishedAt < UNCHANGED_HEARTBEAT_MILLIS
        ) return null
        val result = repository.publishChildUsageSnapshot(snapshot)
        if (result == ParentRemoteSyncResult.Success) {
            preferences.edit()
                .putLong(LAST_PUBLISHED_AT, now)
                .putString(LAST_FINGERPRINT, fingerprint)
                .apply()
        }
        return result
    }
}

class ChildUsageRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result = when (
        val result = ChildUsageSnapshotPublisher.respondToRefreshIfPending(applicationContext)
    ) {
        is ParentRemoteSyncResult.Failed -> if (result.retryable) Result.retry() else Result.success()
        else -> Result.success()
    }

    companion object {
        fun scheduleImmediate(context: Context) {
            val request = OneTimeWorkRequestBuilder<ChildUsageRefreshWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                "child_usage_refresh",
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
