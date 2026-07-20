package com.manisykh.screenrest.usage

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageStatsMergeTest {
    @Test
    fun partialEvents_keepPackagesOnlySeenInAggregateSources() {
        val merged = mergeUsageSources(
            eventUsageByPackage = mapOf("app.events" to 1_000L),
            statsUsageByPackage = mapOf(
                "app.events" to 900L,
                "app.aggregate" to 2_000L,
            ),
            dailyUsageByPackage = mapOf("app.daily" to 3_000L),
        )

        assertEquals(
            mapOf(
                "app.events" to 1_000L,
                "app.aggregate" to 2_000L,
                "app.daily" to 3_000L,
            ),
            merged,
        )
    }

    @Test
    fun conflictingSources_useLargestPlausibleValue() {
        val merged = mergeUsageSources(
            eventUsageByPackage = mapOf("app.shared" to 2_000L),
            statsUsageByPackage = mapOf("app.shared" to 4_000L),
            dailyUsageByPackage = mapOf("app.shared" to 3_000L),
        )

        assertEquals(4_000L, merged["app.shared"])
    }
}
