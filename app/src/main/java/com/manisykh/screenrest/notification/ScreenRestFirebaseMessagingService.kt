package com.manisykh.screenrest.notification

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.manisykh.screenrest.data.ParentRemoteSyncDataSourceFactory
import com.manisykh.screenrest.data.RemotePushTokenRole
import com.manisykh.screenrest.data.RemotePushTokenTarget
import com.manisykh.screenrest.data.SettingsRepository
import com.manisykh.screenrest.data.settingsDataStore
import com.manisykh.screenrest.worker.RemoteParentSyncWorker
import com.manisykh.screenrest.worker.ChildUsageRefreshWorker
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

class ScreenRestFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        PushTokenRegistrationWorker.schedule(applicationContext)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val type = message.data[DATA_TYPE].orEmpty()
        if (type !in SUPPORTED_MESSAGE_TYPES) {
            return
        }
        if (type == MESSAGE_TYPE_USAGE_REFRESH) {
            ChildUsageRefreshWorker.scheduleImmediate(applicationContext)
            return
        }
        val requestId = message.data[DATA_REQUEST_ID].orEmpty()
        val eventKey = message.data[DATA_EVENT_KEY]
            .orEmpty()
            .ifBlank { message.messageId.orEmpty() }
            .ifBlank { "$type:$requestId" }
        RemoteParentSyncWorker.scheduleImmediate(
            context = applicationContext,
            eventKey = eventKey,
            requestId = requestId,
            fetchRequestDirectly = type == MESSAGE_TYPE_REQUEST_RESOLVED,
        )
    }

    override fun onDeletedMessages() {
        ChildUsageRefreshWorker.scheduleImmediate(applicationContext)
        RemoteParentSyncWorker.scheduleImmediate(
            context = applicationContext,
            eventKey = "deleted-messages",
        )
    }

    companion object {
        private const val DATA_TYPE = "type"
        private const val DATA_REQUEST_ID = "requestId"
        private const val DATA_EVENT_KEY = "eventKey"
        private const val MESSAGE_TYPE_REQUEST_CREATED = "unlock_request_created"
        private const val MESSAGE_TYPE_REQUEST_RESOLVED = "unlock_request_resolved"
        private const val MESSAGE_TYPE_IMMEDIATE_BLOCK = "immediate_block_changed"
        private const val MESSAGE_TYPE_USAGE_REFRESH = "child_usage_refresh_requested"
        private val SUPPORTED_MESSAGE_TYPES = setOf(
            MESSAGE_TYPE_REQUEST_CREATED,
            MESSAGE_TYPE_REQUEST_RESOLVED,
            MESSAGE_TYPE_IMMEDIATE_BLOCK,
            MESSAGE_TYPE_USAGE_REFRESH,
        )
    }
}

object PushTokenRegistrationCoordinator {
    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start(context: Context) {
        val appContext = context.applicationContext
        if (FirebaseApp.getApps(appContext).isEmpty()) {
            return
        }
        if (!started.compareAndSet(false, true)) {
            return
        }
        scope.launch {
            val repository = SettingsRepository(
                appContext.settingsDataStore,
                ParentRemoteSyncDataSourceFactory.create(appContext),
            )
            combine(
                repository.monitoringDisclosureAccepted,
                repository.parentManagementState,
            ) { disclosureAccepted, state ->
                    buildList {
                        add(disclosureAccepted.toString())
                        add(state.paired.toString())
                        add(state.deviceRole.name)
                        add(state.childDeviceId)
                        addAll(state.linkedChildDevices.map { child -> child.childDeviceId }.sorted())
                    }.joinToString("|")
                }
                .distinctUntilChanged()
                .collect { registrationState ->
                    if (registrationState.startsWith("true|")) {
                        FirebaseMessaging.getInstance().isAutoInitEnabled = true
                        PushTokenRegistrationWorker.schedule(appContext)
                    }
                }
        }
    }
}

class PushTokenRegistrationWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        if (FirebaseApp.getApps(applicationContext).isEmpty()) {
            return Result.success()
        }
        val repository = SettingsRepository(
            applicationContext.settingsDataStore,
            ParentRemoteSyncDataSourceFactory.create(applicationContext),
        )
        if (!repository.monitoringDisclosureAccepted.first()) {
            return Result.success()
        }
        val preferences = applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val registrationId = preferences.getString(KEY_REGISTRATION_ID, null)
            ?.takeIf { value -> value.isNotBlank() }
            ?: UUID.randomUUID().toString().also { value ->
                preferences.edit().putString(KEY_REGISTRATION_ID, value).apply()
            }
        val previousTargets = preferences.getStringSet(KEY_REGISTERED_TARGETS, emptySet())
            .orEmpty()
            .mapNotNullTo(linkedSetOf()) { value -> decodeTarget(value) }
        val token = try {
            FirebaseMessaging.getInstance().token.awaitTask()
        } catch (_: Throwable) {
            return Result.retry()
        }
        val synchronizedTargets = repository.synchronizePushToken(
            registrationId = registrationId,
            token = token,
            previouslyRegisteredTargets = previousTargets,
        ) ?: return Result.retry()
        preferences.edit()
            .putStringSet(
                KEY_REGISTERED_TARGETS,
                synchronizedTargets.mapTo(linkedSetOf()) { target -> encodeTarget(target) },
            )
            .apply()
        return Result.success()
    }

    companion object {
        private const val PREFERENCES_NAME = "screenrest_push_registration"
        private const val KEY_REGISTRATION_ID = "registration_id"
        private const val KEY_REGISTERED_TARGETS = "registered_targets"
        private const val UNIQUE_IMMEDIATE_WORK_NAME = "push_token_registration_immediate"
        private const val UNIQUE_PERIODIC_WORK_NAME = "push_token_registration_periodic"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<PushTokenRegistrationWorker>()
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build(),
            )
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<PushTokenRegistrationWorker>(30, TimeUnit.DAYS)
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build(),
            )
        }

        private fun encodeTarget(target: RemotePushTokenTarget): String =
            "${target.role.storageValue}|${target.childDeviceId}"

        private fun decodeTarget(value: String): RemotePushTokenTarget? {
            val pieces = value.split('|', limit = 2)
            if (pieces.size != 2 || pieces[1].isBlank()) {
                return null
            }
            val role = RemotePushTokenRole.entries.firstOrNull { candidate ->
                candidate.storageValue == pieces[0]
            } ?: return null
            return RemotePushTokenTarget(childDeviceId = pieces[1], role = role)
        }
    }
}

private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (!continuation.isActive) {
            return@addOnCompleteListener
        }
        val error = task.exception
        if (task.isSuccessful) {
            continuation.resume(task.result)
        } else if (error != null) {
            continuation.resumeWithException(error)
        } else {
            continuation.cancel()
        }
    }
}
