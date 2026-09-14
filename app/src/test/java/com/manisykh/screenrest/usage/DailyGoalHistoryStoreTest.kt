package com.manisykh.screenrest.usage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyGoalHistoryStoreTest {
    @Test
    fun encodeAndDecode_preservesGoalNoGoalAndMissingDayDistinction() {
        val firstDay = 1_000L
        val secondDay = 2_000L
        val missingDay = 3_000L

        val decoded = decodeDailyGoalHistory(
            encodeDailyGoalHistory(
                mapOf(
                    firstDay to 120,
                    secondDay to null,
                ),
            ),
        )

        assertEquals(120, decoded[firstDay])
        assertTrue(decoded.containsKey(secondDay))
        assertEquals(null, decoded[secondDay])
        assertFalse(decoded.containsKey(missingDay))
    }

    @Test
    fun decode_ignoresMalformedRows() {
        val decoded = decodeDailyGoalHistory(
            listOf(
                "screenrest-daily-goal-history-v1",
                "G\t1000\t90",
                "G\tbad\t120",
                "X\t2000\t30",
                "G\t3000\tinvalid",
            ).joinToString("\n"),
        )

        assertEquals(mapOf(1_000L to 90), decoded)
    }
}
