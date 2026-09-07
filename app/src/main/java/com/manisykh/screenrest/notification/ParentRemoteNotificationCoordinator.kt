package com.manisykh.screenrest.notification

import android.content.Context
import com.manisykh.screenrest.data.AppLanguage
import com.manisykh.screenrest.data.ParentDeviceRole
import com.manisykh.screenrest.data.EventLogType
import com.manisykh.screenrest.data.RemoteUnlockRequest
import com.manisykh.screenrest.data.RemoteUnlockRequestStatus
import com.manisykh.screenrest.data.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ParentRemoteNotificationSyncResult(
    val synchronized: Boolean,
    val deliveredCount: Int = 0,
    val retryable: Boolean = false,
    val diagnostic: String = "",
)

class ParentRemoteNotificationCoordinator(
    context: Context,
    private val repository: SettingsRepository,
) {
    private val notificationHelper = UsageNotificationHelper(context.applicationContext)

    suspend fun synchronizeAndNotify(): ParentRemoteNotificationSyncResult = processMutex.withLock {
        val beforeState = repository.parentManagementState.first()
        val beforeNotificationState = repository.parentNotificationState.first()
        val hasSyncTarget = beforeState.childDeviceId.isNotBlank() ||
            beforeState.linkedChildDevices.any { child -> child.childDeviceId.isNotBlank() }
        if (!beforeState.paired || !hasSyncTarget) {
            beforeNotificationState.activePendingRequestIds.forEach { requestId ->
                notificationHelper.cancelParentRequestAlert(requestId)
            }
            repository.updateActiveParentRequestNotificationIds(emptySet())
            return@withLock ParentRemoteNotificationSyncResult(synchronized = true)
        }

        var syncFailureDiagnostic = ""
        val syncSucceeded = try {
            repository.syncParentDevice()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            syncFailureDiagnostic = "parent remote sync failed: ${error.javaClass.simpleName}"
            runCatching {
                repository.addEvent(EventLogType.Warning, syncFailureDiagnostic)
            }
            false
        }
        val parentState = repository.parentManagementState.first()
        val notificationState = repository.parentNotificationState.first()
        val now = System.currentTimeMillis()
        val activePendingRequestIds = parentState.remoteUnlockRequests
            .filter { request ->
                parentState.deviceRole == ParentDeviceRole.Parent &&
                    request.status == RemoteUnlockRequestStatus.Pending &&
                    request.expiresAtMillis >= now
            }
            .map { request -> request.id }
            .filter { requestId -> requestId.isNotBlank() }
            .toSet()

        (notificationState.activePendingRequestIds - activePendingRequestIds).forEach { requestId ->
            notificationHelper.cancelParentRequestAlert(requestId)
        }
        repository.updateActiveParentRequestNotificationIds(activePendingRequestIds)

        val notifications = detectParentRemoteNotifications(
            state = parentState,
            handledEventTokens = notificationState.handledEventTokens,
            nowMillis = now,
        )
        if (notifications.isEmpty()) {
            return@withLock ParentRemoteNotificationSyncResult(
                synchronized = syncSucceeded,
                retryable = !syncSucceeded,
                diagnostic = if (syncSucceeded) {
                    ""
                } else {
                    syncFailureDiagnostic.ifBlank { "parent remote sync failed" }
                },
            )
        }

        val korean = repository.appLanguage.first() == AppLanguage.Korean
        var deliveredCount = 0
        var deliveryRetryable = false
        var lastDiagnostic = ""
        notifications.forEach { notification ->
            val delivery = when (notification.type) {
                ParentRemoteNotificationType.NewPendingRequest ->
                    notificationHelper.showParentRequestAlert(
                        requestId = notification.request.id,
                        title = "ScreenRest",
                        message = notification.message(korean),
                    )
                ParentRemoteNotificationType.RequestApproved,
                ParentRemoteNotificationType.RequestRejected ->
                    notificationHelper.showRemoteDecisionAlert(
                        requestId = notification.request.id,
                        title = "ScreenRest",
                        message = notification.message(korean),
                    )
            }
            repository.recordParentNotificationOutcome(
                eventToken = notification.eventToken,
                delivered = delivery.delivered,
                error = delivery.diagnostic,
                activePendingRequestIds = activePendingRequestIds,
            )
            if (delivery.delivered) {
                deliveredCount += 1
            } else {
                lastDiagnostic = delivery.diagnostic
                deliveryRetryable = deliveryRetryable ||
                    delivery == NotificationDeliveryResult.Failed
            }
        }

        ParentRemoteNotificationSyncResult(
            synchronized = syncSucceeded,
            deliveredCount = deliveredCount,
            retryable = !syncSucceeded || deliveryRetryable,
            diagnostic = lastDiagnostic.ifBlank {
                if (syncSucceeded) {
                    ""
                } else {
                    syncFailureDiagnostic.ifBlank { "parent remote sync failed" }
                }
            },
        )
    }

    fun notificationReadiness(): NotificationDeliveryResult {
        return notificationHelper.parentRequestNotificationReadiness()
    }

    private fun ParentRemoteNotification.message(korean: Boolean): String {
        val target = request.notificationTarget()
        return when (type) {
            ParentRemoteNotificationType.NewPendingRequest -> if (korean) {
                "자녀 기기에서 사용 시간 요청을 보냈습니다: $target"
            } else {
                "A child device requested more time: $target"
            }

            ParentRemoteNotificationType.RequestApproved -> if (korean) {
                "부모가 사용 시간 요청을 승인했습니다: $target"
            } else {
                "The parent approved the time request: $target"
            }

            ParentRemoteNotificationType.RequestRejected -> if (korean) {
                "부모가 사용 시간 요청을 거절했습니다: $target"
            } else {
                "The parent rejected the time request: $target"
            }
        }
    }

    private fun RemoteUnlockRequest.notificationTarget(): String {
        return targetAppName
            .ifBlank { targetGroupName }
            .ifBlank { targetPackageName }
            .ifBlank { "ScreenRest" }
    }

    companion object {
        private val processMutex = Mutex()
    }
}
