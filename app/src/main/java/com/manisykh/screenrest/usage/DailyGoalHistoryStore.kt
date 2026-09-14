package com.manisykh.screenrest.usage

import android.content.Context
import android.os.SystemClock
import android.util.AtomicFile
import java.io.File

/**
 * Stores the daily total-usage goal that was actually in effect on each day.
 *
 * A present day with a null value means that the day was observed but there was
 * no active daily goal. A missing day means that older data predates goal
 * tracking and must not be judged using today's settings.
 */
class DailyGoalHistoryStore(context: Context) {
    private val atomicFile = AtomicFile(File(context.filesDir, FILE_NAME))

    fun rememberGoal(
        dayStartMillis: Long,
        goalMinutes: Int?,
        forceWrite: Boolean = false,
    ) = synchronized(sharedLock) {
        if (dayStartMillis <= 0L) return@synchronized
        ensureLoaded()
        val sanitizedGoal = goalMinutes?.coerceAtLeast(0)
        if (!cachedGoalsByDay.containsKey(dayStartMillis) || cachedGoalsByDay[dayStartMillis] != sanitizedGoal) {
            cachedGoalsByDay = (cachedGoalsByDay + (dayStartMillis to sanitizedGoal))
                .toSortedMap()
                .entries
                .sortedByDescending { entry -> entry.key }
                .take(MAX_HISTORY_DAYS)
                .associate { entry -> entry.key to entry.value }
                .toSortedMap()
            dirty = true
        }
        writeIfDue(forceWrite)
    }

    fun snapshot(dayStarts: Collection<Long>): Map<Long, Int?> = synchronized(sharedLock) {
        ensureLoaded()
        dayStarts
            .distinct()
            .filter { dayStartMillis -> cachedGoalsByDay.containsKey(dayStartMillis) }
            .associateWith { dayStartMillis -> cachedGoalsByDay[dayStartMillis] }
    }

    fun flush() = synchronized(sharedLock) {
        ensureLoaded()
        writeIfDue(force = true)
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val content = try {
            atomicFile.openRead().bufferedReader().use { reader -> reader.readText() }
        } catch (_: Exception) {
            ""
        }
        cachedGoalsByDay = decodeDailyGoalHistory(content)
    }

    private fun writeIfDue(force: Boolean) {
        if (!dirty) return
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastWriteElapsedMillis < WRITE_THROTTLE_MILLIS) return
        try {
            val stream = atomicFile.startWrite()
            try {
                stream.write(encodeDailyGoalHistory(cachedGoalsByDay).toByteArray(Charsets.UTF_8))
                atomicFile.finishWrite(stream)
                dirty = false
                lastWriteElapsedMillis = now
            } catch (exception: Exception) {
                atomicFile.failWrite(stream)
                throw exception
            }
        } catch (_: Exception) {
            // Goal history is reporting-only and must never interrupt enforcement.
        }
    }

    companion object {
        private const val FILE_NAME = "daily_goal_history_v1.txt"
        private const val MAX_HISTORY_DAYS = 31
        private const val WRITE_THROTTLE_MILLIS = 1_500L
        private val sharedLock = Any()
        private var cachedGoalsByDay: Map<Long, Int?> = emptyMap()
        private var loaded = false
        private var dirty = false
        private var lastWriteElapsedMillis = 0L
    }
}

internal fun encodeDailyGoalHistory(goalsByDay: Map<Long, Int?>): String = buildString {
    append(DAILY_GOAL_HISTORY_HEADER)
    append('\n')
    goalsByDay.toSortedMap().forEach { (dayStartMillis, goalMinutes) ->
        if (dayStartMillis <= 0L) return@forEach
        append("G\t")
        append(dayStartMillis)
        append('\t')
        append(goalMinutes?.coerceAtLeast(0) ?: NO_GOAL_VALUE)
        append('\n')
    }
}

internal fun decodeDailyGoalHistory(content: String): Map<Long, Int?> {
    if (content.lineSequence().firstOrNull() != DAILY_GOAL_HISTORY_HEADER) return emptyMap()
    return content.lineSequence()
        .drop(1)
        .mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.firstOrNull() != "G") return@mapNotNull null
            val dayStartMillis = parts.getOrNull(1)?.toLongOrNull()?.takeIf { value -> value > 0L }
                ?: return@mapNotNull null
            val rawGoal = parts.getOrNull(2)?.toIntOrNull() ?: return@mapNotNull null
            dayStartMillis to rawGoal.takeUnless { value -> value == NO_GOAL_VALUE }?.coerceAtLeast(0)
        }
        .toMap()
        .toSortedMap()
}

private const val DAILY_GOAL_HISTORY_HEADER = "screenrest-daily-goal-history-v1"
private const val NO_GOAL_VALUE = -1
