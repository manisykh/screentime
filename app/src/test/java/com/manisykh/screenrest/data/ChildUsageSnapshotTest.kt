package com.manisykh.screenrest.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChildUsageSnapshotTest {
    private fun snapshot(
        capturedAtMillis: Long = 1_000_000L,
        todayUsedMillis: Long = 42 * 60_000L,
        dailyCountedUsageMillis: Long = 38 * 60_000L,
        effectiveDailyLimitMinutes: Int? = 120,
        dailyUnlockedForToday: Boolean = false,
        usageAccessReady: Boolean = true,
        protectionPaused: Boolean = false,
    ) = ChildUsageSnapshot(
        childDeviceId = "child-device",
        dateKey = "2026-09-13",
        capturedAtMillis = capturedAtMillis,
        todayUsedMillis = todayUsedMillis,
        dailyCountedUsageMillis = dailyCountedUsageMillis,
        effectiveDailyLimitMinutes = effectiveDailyLimitMinutes,
        dailyUnlockedForToday = dailyUnlockedForToday,
        usageAccessReady = usageAccessReady,
        protectionPaused = protectionPaused,
    )

    @Test
    fun remainingTime_usesPolicyCountedUsageNotAllVisibleUsage() {
        assertEquals(82, snapshot().remainingDailyMinutes())
    }

    @Test
    fun explicitZeroLimit_hasZeroRemainingTime() {
        assertEquals(0, snapshot(effectiveDailyLimitMinutes = 0).remainingDailyMinutes())
    }

    @Test
    fun unknownOrInactiveLimit_neverClaimsRemainingTime() {
        assertNull(snapshot(effectiveDailyLimitMinutes = null).remainingDailyMinutes())
        assertNull(snapshot(dailyUnlockedForToday = true).remainingDailyMinutes())
        assertNull(snapshot(protectionPaused = true).remainingDailyMinutes())
        assertNull(snapshot(usageAccessReady = false).remainingDailyMinutes())
    }

    @Test
    fun delayedStatus_changesAtThresholdAndDetectsClockSkew() {
        val value = snapshot()
        assertFalse(value.isDelayed(1_000_000L + 75 * 60_000L))
        assertTrue(value.isDelayed(1_000_000L + 75 * 60_000L + 1L))
        assertTrue(value.isDelayed(1_000_000L - 5 * 60_000L - 1L))
    }

    @Test
    fun appUsageSharingIsOptInAndLegacySnapshotsStayPrivate() {
        val legacy = snapshot()
        assertFalse(legacy.appUsageSharingEnabled)
        assertTrue(legacy.topApps.isEmpty())
        val shared = legacy.copy(
            appUsageSharingEnabled = true,
            topApps = listOf(ChildTopAppUsage("YouTube", 30 * 60_000L)),
        )
        assertEquals("YouTube", shared.topApps.single().appName)
    }

    @Test
    fun refreshRequestSeparatesWaitingDelayedCompletedAndExpired() {
        val request = ChildUsageRefreshRequest(
            requestId = "request-1",
            childDeviceId = "child-device",
            parentUid = "parent",
            requestedAtMillis = 1_000_000L,
            expiresAtMillis = 1_000_000L + 30 * 60_000L,
        )
        assertTrue(request.isPending(1_000_000L + 60_000L))
        assertFalse(request.canRequestAgain(1_000_000L + 59_999L))
        assertTrue(request.canRequestAgain(1_000_000L + 60_000L))
        assertFalse(request.isDelayed(1_000_000L + 60_000L))
        assertTrue(request.isDelayed(1_000_000L + 2 * 60_000L))
        assertFalse(request.copy(completedAtMillis = 1_000_000L + 3 * 60_000L)
            .isPending(1_000_000L + 4 * 60_000L))
        assertFalse(request.isPending(request.expiresAtMillis))
    }
}
