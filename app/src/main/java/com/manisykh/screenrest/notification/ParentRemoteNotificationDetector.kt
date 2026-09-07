package com.manisykh.screenrest.notification

import com.manisykh.screenrest.data.ParentDeviceRole
import com.manisykh.screenrest.data.ParentManagementState
import com.manisykh.screenrest.data.RemoteUnlockRequest
import com.manisykh.screenrest.data.RemoteUnlockRequestStatus

internal enum class ParentRemoteNotificationType {
    NewPendingRequest,
    RequestApproved,
    RequestRejected,
}

internal data class ParentRemoteNotification(
    val type: ParentRemoteNotificationType,
    val request: RemoteUnlockRequest,
) {
    val eventToken: String
        get() = request.eventToken(type)
}

internal fun detectParentRemoteNotifications(
    state: ParentManagementState,
    handledEventTokens: Set<String>,
    nowMillis: Long,
): List<ParentRemoteNotification> {
    return state.remoteUnlockRequests.mapNotNull { request ->
        if (request.id.isBlank()) {
            return@mapNotNull null
        }
        val type = when {
            state.deviceRole == ParentDeviceRole.Parent &&
                request.status == RemoteUnlockRequestStatus.Pending &&
                request.expiresAtMillis >= nowMillis -> ParentRemoteNotificationType.NewPendingRequest

            state.deviceRole == ParentDeviceRole.Child &&
                request.status == RemoteUnlockRequestStatus.Approved &&
                request.expiresAtMillis >= nowMillis - REQUEST_EXPIRY_NOTIFICATION_GRACE_MILLIS ->
                ParentRemoteNotificationType.RequestApproved

            state.deviceRole == ParentDeviceRole.Child &&
                request.status == RemoteUnlockRequestStatus.Rejected &&
                request.expiresAtMillis >= nowMillis - REQUEST_EXPIRY_NOTIFICATION_GRACE_MILLIS ->
                ParentRemoteNotificationType.RequestRejected

            else -> null
        } ?: return@mapNotNull null
        ParentRemoteNotification(type = type, request = request)
            .takeUnless { notification -> notification.eventToken in handledEventTokens }
    }
}

internal fun detectParentRemoteNotifications(
    before: ParentManagementState,
    after: ParentManagementState,
    nowMillis: Long,
): List<ParentRemoteNotification> {
    val handledEventTokens = before.remoteUnlockRequests.mapNotNull { request ->
        val type = when (request.status) {
            RemoteUnlockRequestStatus.Pending -> ParentRemoteNotificationType.NewPendingRequest
            RemoteUnlockRequestStatus.Approved -> ParentRemoteNotificationType.RequestApproved
            RemoteUnlockRequestStatus.Rejected -> ParentRemoteNotificationType.RequestRejected
            RemoteUnlockRequestStatus.Expired,
            RemoteUnlockRequestStatus.Failed -> null
        } ?: return@mapNotNull null
        request.eventToken(type)
    }.toSet()
    return detectParentRemoteNotifications(after, handledEventTokens, nowMillis)
}

private fun RemoteUnlockRequest.eventToken(type: ParentRemoteNotificationType): String {
    return if (type == ParentRemoteNotificationType.NewPendingRequest) {
        "$id:${type.name}:$createdAtMillis"
    } else {
        "$id:${type.name}"
    }
}

private const val REQUEST_EXPIRY_NOTIFICATION_GRACE_MILLIS = 60_000L
