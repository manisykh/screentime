package com.manisykh.screenrest.usage

import android.content.Context
import android.os.SystemClock
import android.util.AtomicFile
import java.io.File

class UsageHistoryStore(context: Context) {
    private val atomicFile = AtomicFile(File(context.filesDir, FILE_NAME))

    fun mergeDays(
        usageByDay: Map<Long, Map<String, Long>>,
        forceWrite: Boolean = false,
    ): Map<Long, Map<String, Long>> = synchronized(sharedLock) {
        ensureLoaded()
        val merged = mergeUsageHistory(
            existing = cachedUsageByDay,
            updates = usageByDay,
            maxDays = MAX_HISTORY_DAYS,
        )
        if (merged != cachedUsageByDay) {
            cachedUsageByDay = merged
            dirty = true
        }
        writeIfDue(force = forceWrite)
        cachedUsageByDay
    }

    fun snapshot(dayStarts: Collection<Long>): Map<Long, Map<String, Long>> = synchronized(sharedLock) {
        ensureLoaded()
        dayStarts
            .distinct()
            .associateWith { dayStartMillis ->
                cachedUsageByDay[dayStartMillis].orEmpty()
            }
            .filterKeys { dayStartMillis -> cachedUsageByDay.containsKey(dayStartMillis) }
    }

    fun flush() = synchronized(sharedLock) {
        ensureLoaded()
        writeIfDue(force = true)
    }

    private fun ensureLoaded() {
        if (loaded) {
            return
        }
        loaded = true
        val content = try {
            atomicFile.openRead().bufferedReader().use { reader -> reader.readText() }
        } catch (_: Exception) {
            ""
        }
        cachedUsageByDay = decodeUsageHistory(content)
    }

    private fun writeIfDue(force: Boolean) {
        if (!dirty) {
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastWriteElapsedMillis < WRITE_THROTTLE_MILLIS) {
            return
        }
        try {
            val stream = atomicFile.startWrite()
            try {
                stream.write(encodeUsageHistory(cachedUsageByDay).toByteArray(Charsets.UTF_8))
                atomicFile.finishWrite(stream)
                dirty = false
                lastWriteElapsedMillis = now
            } catch (exception: Exception) {
                atomicFile.failWrite(stream)
                throw exception
            }
        } catch (_: Exception) {
            // History improves reporting but must never interrupt usage enforcement.
        }
    }

    companion object {
        private const val FILE_NAME = "usage_history_v1.txt"
        private const val MAX_HISTORY_DAYS = 31
        private const val WRITE_THROTTLE_MILLIS = 1_500L
        private val sharedLock = Any()
        private var cachedUsageByDay: Map<Long, Map<String, Long>> = emptyMap()
        private var loaded = false
        private var dirty = false
        private var lastWriteElapsedMillis = 0L
    }
}

internal fun mergeUsageHistory(
    existing: Map<Long, Map<String, Long>>,
    updates: Map<Long, Map<String, Long>>,
    maxDays: Int,
): Map<Long, Map<String, Long>> {
    val merged = existing
        .filterKeys { dayStartMillis -> dayStartMillis > 0L }
        .mapValues { (_, usageByPackage) -> sanitizeHistoryDay(usageByPackage).toMutableMap() }
        .toMutableMap()

    updates.forEach { (dayStartMillis, usageByPackage) ->
        if (dayStartMillis <= 0L) {
            return@forEach
        }
        val target = merged.getOrPut(dayStartMillis) { mutableMapOf() }
        sanitizeHistoryDay(usageByPackage).forEach { (packageName, usageMillis) ->
            target[packageName] = maxOf(target[packageName] ?: 0L, usageMillis)
        }
    }

    val retainedDays = merged.keys.sortedDescending().take(maxDays.coerceAtLeast(1)).toSet()
    return merged
        .filterKeys { dayStartMillis -> dayStartMillis in retainedDays }
        .toSortedMap()
        .mapValues { (_, usageByPackage) -> usageByPackage.toSortedMap() }
}

internal fun encodeUsageHistory(history: Map<Long, Map<String, Long>>): String {
    return buildString {
        append(HISTORY_HEADER)
        append('\n')
        history.toSortedMap().forEach { (dayStartMillis, usageByPackage) ->
            if (dayStartMillis <= 0L) {
                return@forEach
            }
            append("D\t")
            append(dayStartMillis)
            append('\n')
            sanitizeHistoryDay(usageByPackage).toSortedMap().forEach { (packageName, usageMillis) ->
                append("U\t")
                append(dayStartMillis)
                append('\t')
                append(packageName)
                append('\t')
                append(usageMillis)
                append('\n')
            }
        }
    }
}

internal fun decodeUsageHistory(content: String): Map<Long, Map<String, Long>> {
    if (content.lineSequence().firstOrNull() != HISTORY_HEADER) {
        return emptyMap()
    }
    val result = mutableMapOf<Long, MutableMap<String, Long>>()
    content.lineSequence().drop(1).forEach { line ->
        val parts = line.split('\t')
        when (parts.firstOrNull()) {
            "D" -> {
                val dayStartMillis = parts.getOrNull(1)?.toLongOrNull() ?: return@forEach
                if (dayStartMillis > 0L) {
                    result.getOrPut(dayStartMillis) { mutableMapOf() }
                }
            }

            "U" -> {
                val dayStartMillis = parts.getOrNull(1)?.toLongOrNull() ?: return@forEach
                val packageName = parts.getOrNull(2).orEmpty()
                val usageMillis = parts.getOrNull(3)?.toLongOrNull() ?: return@forEach
                if (dayStartMillis > 0L && isValidHistoryPackage(packageName) && usageMillis > 0L) {
                    val usageByPackage = result.getOrPut(dayStartMillis) { mutableMapOf() }
                    usageByPackage[packageName] = maxOf(usageByPackage[packageName] ?: 0L, usageMillis)
                }
            }
        }
    }
    return result
        .toSortedMap()
        .mapValues { (_, usageByPackage) -> usageByPackage.toSortedMap() }
}

private fun sanitizeHistoryDay(usageByPackage: Map<String, Long>): Map<String, Long> {
    return usageByPackage.filter { (packageName, usageMillis) ->
        isValidHistoryPackage(packageName) && usageMillis > 0L
    }
}

private fun isValidHistoryPackage(packageName: String): Boolean {
    return packageName.isNotBlank() && '\t' !in packageName && '\n' !in packageName && '\r' !in packageName
}

private const val HISTORY_HEADER = "screenrest-usage-history-v1"
