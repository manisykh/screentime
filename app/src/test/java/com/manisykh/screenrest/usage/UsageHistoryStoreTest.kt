package com.manisykh.screenrest.usage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageHistoryStoreTest {
    @Test
    fun merge_keepsLargestCumulativeValueAndRecordedEmptyDay() {
        val merged = mergeUsageHistory(
            existing = mapOf(
                1_000L to mapOf("app.one" to 5_000L),
            ),
            updates = mapOf(
                1_000L to mapOf("app.one" to 3_000L, "app.two" to 2_000L),
                2_000L to emptyMap(),
            ),
            maxDays = 31,
        )

        assertEquals(5_000L, merged.getValue(1_000L).getValue("app.one"))
        assertEquals(2_000L, merged.getValue(1_000L).getValue("app.two"))
        assertTrue(merged.containsKey(2_000L))
        assertTrue(merged.getValue(2_000L).isEmpty())
    }

    @Test
    fun merge_prunesOldestDaysBeyondRetention() {
        val updates = (1L..35L).associateWith { day -> mapOf("app" to day) }

        val merged = mergeUsageHistory(
            existing = emptyMap(),
            updates = updates,
            maxDays = 31,
        )

        assertEquals(31, merged.size)
        assertFalse(merged.containsKey(4L))
        assertTrue(merged.containsKey(5L))
        assertTrue(merged.containsKey(35L))
    }

    @Test
    fun codec_roundTripsUsageAndEmptyRecordedDays() {
        val original = mapOf(
            1_000L to mapOf("app.one" to 10_000L),
            2_000L to emptyMap(),
        )

        val decoded = decodeUsageHistory(encodeUsageHistory(original))

        assertEquals(original, decoded)
    }

    @Test
    fun codec_ignoresInvalidOrCorruptRows() {
        val decoded = decodeUsageHistory(
            listOf(
                "screenrest-usage-history-v1",
                "D\t1000",
                "U\t1000\tapp.valid\t5000",
                "U\t1000\tapp.invalid\t-1",
                "U\tbroken\tapp.other\t2000",
            ).joinToString("\n"),
        )

        assertEquals(mapOf(1_000L to mapOf("app.valid" to 5_000L)), decoded)
    }
}
