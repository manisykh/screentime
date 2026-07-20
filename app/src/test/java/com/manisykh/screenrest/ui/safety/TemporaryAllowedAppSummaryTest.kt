package com.manisykh.screenrest.ui.safety

import com.manisykh.screenrest.data.TemporaryPackageAllowance
import com.manisykh.screenrest.data.TemporaryUnlockState
import org.junit.Assert.assertEquals
import org.junit.Test

class TemporaryAllowedAppSummaryTest {
    @Test
    fun activeAllowances_includeTimedAndTodayOnly_butExcludeExpired() {
        val nowMillis = 1_000_000L
        val summaries = buildTemporaryAllowedAppSummaries(
            temporaryUnlockState = TemporaryUnlockState(
                packageAllowances = mapOf(
                    "app.timed" to TemporaryPackageAllowance(
                        temporaryAllowedUntilMillis = nowMillis + 4 * 60_000L + 1L,
                    ),
                    "app.today" to TemporaryPackageAllowance(
                        unlockedForToday = true,
                    ),
                    "app.expired" to TemporaryPackageAllowance(
                        temporaryAllowedUntilMillis = nowMillis - 1L,
                    ),
                ),
            ),
            appNameByPackage = mapOf(
                "app.timed" to "Timed App",
                "app.today" to "Today App",
            ),
            nowMillis = nowMillis,
        )

        assertEquals(listOf("app.today", "app.timed"), summaries.map { it.packageName })
        assertEquals(true, summaries.first().unlockedForToday)
        assertEquals(5, summaries.last().temporaryRemainingMinutes)
    }
}
