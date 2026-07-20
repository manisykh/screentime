package com.manisykh.screenrest.notification

import com.manisykh.screenrest.data.ParentDeviceRole
import com.manisykh.screenrest.data.ParentManagementState
import com.manisykh.screenrest.data.RemoteRequestBlockReason
import com.manisykh.screenrest.data.RemoteUnlockRequest
import com.manisykh.screenrest.data.RemoteUnlockRequestStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParentRemoteNotificationDetectorTest {
    private val now = 1_000_000L

    @Test
    fun parent_notifiesOnlyForNewPendingRequest() {
        val request = request(status = RemoteUnlockRequestStatus.Pending)
        val notifications = detectParentRemoteNotifications(
            before = ParentManagementState(deviceRole = ParentDeviceRole.Parent),
            after = ParentManagementState(
                deviceRole = ParentDeviceRole.Parent,
                remoteUnlockRequests = listOf(request),
            ),
            nowMillis = now,
        )

        assertEquals(listOf(ParentRemoteNotificationType.NewPendingRequest), notifications.map { it.type })
    }

    @Test
    fun repeatedServerSnapshot_doesNotNotifyAgain() {
        val request = request(status = RemoteUnlockRequestStatus.Pending)
        val state = ParentManagementState(
            deviceRole = ParentDeviceRole.Parent,
            remoteUnlockRequests = listOf(request),
        )

        assertTrue(detectParentRemoteNotifications(state, state, now).isEmpty())
    }

    @Test
    fun child_notifiesWhenPendingRequestBecomesApproved() {
        val pending = request(status = RemoteUnlockRequestStatus.Pending)
        val approved = pending.copy(status = RemoteUnlockRequestStatus.Approved)
        val notifications = detectParentRemoteNotifications(
            before = ParentManagementState(
                deviceRole = ParentDeviceRole.Child,
                remoteUnlockRequests = listOf(pending),
            ),
            after = ParentManagementState(
                deviceRole = ParentDeviceRole.Child,
                remoteUnlockRequests = listOf(approved),
            ),
            nowMillis = now,
        )

        assertEquals(listOf(ParentRemoteNotificationType.RequestApproved), notifications.map { it.type })
    }

    @Test
    fun oldInitialSnapshot_isNotReplayedAsNotification() {
        val oldRequest = request(status = RemoteUnlockRequestStatus.Pending).copy(
            createdAtMillis = now - 16L * 60L * 1_000L,
            expiresAtMillis = now + 60_000L,
        )
        val notifications = detectParentRemoteNotifications(
            before = ParentManagementState(deviceRole = ParentDeviceRole.Parent),
            after = ParentManagementState(
                deviceRole = ParentDeviceRole.Parent,
                remoteUnlockRequests = listOf(oldRequest),
            ),
            nowMillis = now,
        )

        assertTrue(notifications.isEmpty())
    }

    private fun request(status: RemoteUnlockRequestStatus) = RemoteUnlockRequest(
        id = "request-1",
        childDeviceId = "child-1",
        childDeviceName = "Child",
        createdAtMillis = now - 1_000L,
        expiresAtMillis = now + 10L * 60L * 1_000L,
        blockReason = RemoteRequestBlockReason.AppLimit,
        targetPackageName = "example.app",
        targetAppName = "Example",
        status = status,
    )
}
