package com.manisykh.screenrest.data

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteRequestDisplayPolicyTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val todayStart = LocalDate.of(2026, 9, 14).atStartOfDay(zone).toInstant().toEpochMilli()
    private val now = todayStart + 12 * 60 * 60_000L

    private fun request(
        id: String,
        createdAt: Long,
        status: RemoteUnlockRequestStatus = RemoteUnlockRequestStatus.Pending,
        expiresAt: Long = now + 60_000L,
    ) = RemoteUnlockRequest(
        id = id,
        childDeviceId = "child",
        childDeviceName = "Child",
        createdAtMillis = createdAt,
        expiresAtMillis = expiresAt,
        blockReason = RemoteRequestBlockReason.AppLimit,
        status = status,
    )

    @Test
    fun pendingRequestsStayVisibleAcrossMidnightAndTodayIsSeparate() {
        val groups = groupRemoteRequestsForDisplay(
            listOf(
                request("pending-yesterday", todayStart - 60_000L),
                request("approved-today", todayStart + 2_000L, RemoteUnlockRequestStatus.Approved),
                request("approved-yesterday", todayStart - 2 * 60_000L, RemoteUnlockRequestStatus.Approved),
            ),
            now,
            zone,
        )
        assertEquals(listOf("pending-yesterday"), groups.pending.map { it.id })
        assertEquals(listOf("approved-today"), groups.today.map { it.id })
        assertEquals(listOf("approved-yesterday"), groups.recentHistory.map { it.id })
    }

    @Test
    fun expiredRequestsCannotRemainPendingAndOldHistoryIsHidden() {
        val groups = groupRemoteRequestsForDisplay(
            listOf(
                request("expired-today", todayStart + 1_000L, expiresAt = now - 1L),
                request("expired-yesterday", todayStart - 60_000L, expiresAt = now - 1L),
                request("older-than-retention", now - 8L * 24 * 60 * 60_000L,
                    RemoteUnlockRequestStatus.Rejected),
            ),
            now,
            zone,
        )
        assertEquals(emptyList<String>(), groups.pending.map { it.id })
        assertEquals(listOf("expired-today"), groups.today.map { it.id })
        assertEquals(listOf("expired-yesterday"), groups.recentHistory.map { it.id })
    }
}
