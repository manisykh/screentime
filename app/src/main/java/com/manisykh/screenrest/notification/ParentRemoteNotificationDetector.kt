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
)

internal fun detectParentRemoteNotifications(
    before: ParentManagementState,
    after: ParentManagementState,
    nowMillis: Long,
    freshnessWindowMillis: Long = 15L * 60L * 1_000L,
): List<ParentRemoteNotification> {
    val beforeById = before.remoteUnlockRequests.associateBy { request -> request.id }
    return after.remoteUnlockRequests.mapNotNull { request ->
        if (
            request.id.isBlank() ||
            request.createdAtMillis < nowMillis - freshnessWindowMillis ||
            request.expiresAtMillis < nowMillis - REQUEST_EXPIRY_NOTIFICATION_GRACE_MILLIS
        ) {
            return@mapNotNull null
        }
        val previousStatus = beforeById[request.id]?.status
        val type = when {
            after.deviceRole == ParentDeviceRole.Parent &&
                request.status == RemoteUnlockRequestStatus.Pending &&
                request.expiresAtMillis >= nowMillis &&
                previousStatus == null -> ParentRemoteNotificationType.NewPendingRequest

            after.deviceRole == ParentDeviceRole.Child &&
                request.status == RemoteUnlockRequestStatus.Approved &&
                previousStatus != RemoteUnlockRequestStatus.Approved ->
                ParentRemoteNotificationType.RequestApproved

            after.deviceRole == ParentDeviceRole.Child &&
                request.status == RemoteUnlockRequestStatus.Rejected &&
                previousStatus != RemoteUnlockRequestStatus.Rejected ->
                ParentRemoteNotificationType.RequestRejected

            else -> null
        } ?: return@mapNotNull null
        ParentRemoteNotification(type = type, request = request)
    }
}

private const val REQUEST_EXPIRY_NOTIFICATION_GRACE_MILLIS = 60_000L
