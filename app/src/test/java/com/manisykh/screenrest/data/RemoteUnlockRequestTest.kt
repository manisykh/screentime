package com.manisykh.screenrest.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteUnlockRequestTest {
    @Test
    fun parentApproval_isVisibleOnlyOnLinkedChildDevice() {
        val linkedChild = ParentManagementState(
            paired = true,
            parentAccountId = "parent",
            deviceRole = ParentDeviceRole.Child,
        )
        val parentDevice = linkedChild.copy(deviceRole = ParentDeviceRole.Parent)
        val unlinkedChild = linkedChild.copy(paired = false, parentAccountId = "")

        assertEquals(true, linkedChild.canRequestParentApproval())
        assertEquals(false, parentDevice.canRequestParentApproval())
        assertEquals(false, unlinkedChild.canRequestParentApproval())
    }

    @Test
    fun sameTarget_keepsOnlyNewestPendingRequestActive() {
        val older = request(id = "older", createdAtMillis = 1_000L)
        val newer = request(id = "newer", createdAtMillis = 2_000L)

        val normalized = listOf(older, newer)
            .withOnlyLatestPendingRequestPerTarget(nowMillis = 2_500L)

        assertEquals(RemoteUnlockRequestStatus.Pending, normalized.first { it.id == "newer" }.status)
        assertEquals(RemoteUnlockRequestStatus.Expired, normalized.first { it.id == "older" }.status)
    }

    @Test
    fun differentBlockTargets_remainPending() {
        val daily = request(id = "daily", createdAtMillis = 1_000L)
        val app = request(
            id = "app",
            createdAtMillis = 2_000L,
            blockReason = RemoteRequestBlockReason.AppLimit,
            targetPackageName = "example.app",
        )

        val normalized = listOf(daily, app)
            .withOnlyLatestPendingRequestPerTarget(nowMillis = 2_500L)

        assertEquals(2, normalized.count { it.status == RemoteUnlockRequestStatus.Pending })
    }

    @Test
    fun newerCompletedRequest_expiresOlderDuplicatePendingRequest() {
        val older = request(id = "older", createdAtMillis = 1_000L)
        val newer = request(id = "newer", createdAtMillis = 2_000L)
            .copy(status = RemoteUnlockRequestStatus.Approved)

        val normalized = listOf(older, newer)
            .withOnlyLatestPendingRequestPerTarget(nowMillis = 2_500L)

        assertEquals(RemoteUnlockRequestStatus.Approved, normalized.first { it.id == "newer" }.status)
        assertEquals(RemoteUnlockRequestStatus.Expired, normalized.first { it.id == "older" }.status)
    }

    private fun request(
        id: String,
        createdAtMillis: Long,
        blockReason: RemoteRequestBlockReason = RemoteRequestBlockReason.DailyLimit,
        targetPackageName: String = "",
    ) = RemoteUnlockRequest(
        id = id,
        childDeviceId = "child",
        childDeviceName = "Child",
        createdAtMillis = createdAtMillis,
        expiresAtMillis = 20_000L,
        blockReason = blockReason,
        targetPackageName = targetPackageName,
    )
}
