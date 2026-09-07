package com.manisykh.screenrest.usage

import android.content.Context
import android.os.SystemClock
import android.util.AtomicFile
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UsageContinuityStore(context: Context) {
    private val atomicFile = AtomicFile(File(context.filesDir, FILE_NAME))

    fun mergeRawUsage(
        dayStartMillis: Long,
        rawUsageMillisByPackage: Map<String, Long>,
        maxAllowedUsageMillis: Long = Long.MAX_VALUE,
        replaceMissingPackages: Boolean = false,
    ): Map<String, Long> = synchronized(sharedLock) {
        val dayKey = dayKey(dayStartMillis)
        ensureLoaded(dayKey)
        var changed = pruneImpossibleUsage(maxAllowedUsageMillis)
        if (replaceMissingPackages) {
            val retainedPackages = rawUsageMillisByPackage
                .filter { (packageName, usageMillis) ->
                    packageName.isNotBlank() &&
                        usageMillis > 0L &&
                        usageMillis <= maxAllowedUsageMillis
                }
                .keys
            val removedPackages = cachedUsageMillisByPackage.keys - retainedPackages
            if (removedPackages.isNotEmpty()) {
                removedPackages.forEach { packageName ->
                    cachedUsageMillisByPackage.remove(packageName)
                }
                changed = true
            }
        }
        rawUsageMillisByPackage.forEach { (packageName, usageMillis) ->
            if (packageName.isBlank() || usageMillis <= 0L) {
                return@forEach
            }
            if (usageMillis > maxAllowedUsageMillis) {
                return@forEach
            }
            val current = cachedUsageMillisByPackage[packageName] ?: 0L
            if (usageMillis > current) {
                cachedUsageMillisByPackage[packageName] = usageMillis
                changed = true
            }
        }
        if (changed) {
            dirty = true
            writeIfDue(force = false)
        }
        cachedUsageMillisByPackage.toMap()
    }

    fun replaceRawUsage(
        dayStartMillis: Long,
        rawUsageMillisByPackage: Map<String, Long>,
        maxAllowedUsageMillis: Long = Long.MAX_VALUE,
    ): Map<String, Long> = synchronized(sharedLock) {
        val dayKey = dayKey(dayStartMillis)
        ensureLoaded(dayKey)
        val replacement = rawUsageMillisByPackage
            .filter { (packageName, usageMillis) ->
                packageName.isNotBlank() &&
                    usageMillis > 0L &&
                    usageMillis <= maxAllowedUsageMillis
            }
            .toMutableMap()
        if (replacement != cachedUsageMillisByPackage) {
            cachedUsageMillisByPackage = replacement
            dirty = true
            writeIfDue(force = false)
        }
        cachedUsageMillisByPackage.toMap()
    }

    fun rememberUsage(
        dayStartMillis: Long,
        packageName: String,
        usageMillis: Long,
        forceWrite: Boolean = false,
        maxAllowedUsageMillis: Long = Long.MAX_VALUE,
    ): Long = synchronized(sharedLock) {
        if (packageName.isBlank() || usageMillis <= 0L) {
            return@synchronized 0L
        }
        val dayKey = dayKey(dayStartMillis)
        ensureLoaded(dayKey)
        val pruned = pruneImpossibleUsage(maxAllowedUsageMillis)
        if (usageMillis > maxAllowedUsageMillis) {
            if (pruned) {
                dirty = true
                writeIfDue(force = forceWrite)
            }
            return@synchronized cachedUsageMillisByPackage[packageName] ?: 0L
        }
        val stableUsageMillis = maxOf(cachedUsageMillisByPackage[packageName] ?: 0L, usageMillis)
        if (stableUsageMillis != cachedUsageMillisByPackage[packageName]) {
            cachedUsageMillisByPackage[packageName] = stableUsageMillis
            dirty = true
            writeIfDue(force = forceWrite)
        } else if (forceWrite) {
            writeIfDue(force = true)
        }
        stableUsageMillis
    }

    fun snapshot(
        dayStartMillis: Long,
        maxAllowedUsageMillis: Long = Long.MAX_VALUE,
    ): Map<String, Long> = synchronized(sharedLock) {
        ensureLoaded(dayKey(dayStartMillis))
        if (pruneImpossibleUsage(maxAllowedUsageMillis)) {
            dirty = true
            writeIfDue(force = true)
        }
        cachedUsageMillisByPackage.toMap()
    }

    fun flush() = synchronized(sharedLock) {
        writeIfDue(force = true)
    }

    private fun ensureLoaded(dayKey: String) {
        if (!loaded) {
            loadFromDisk()
        }
        if (cachedDayKey != dayKey) {
            cachedDayKey = dayKey
            cachedUsageMillisByPackage = mutableMapOf()
            dirty = true
            writeIfDue(force = true)
        }
    }

    private fun loadFromDisk() {
        loaded = true
        val content = try {
            atomicFile.openRead().bufferedReader().use { reader -> reader.readText() }
        } catch (_: Exception) {
            ""
        }
        val lines = content.lineSequence().toList()
        cachedDayKey = lines.firstOrNull().orEmpty()
        cachedUsageMillisByPackage = lines
            .drop(1)
            .mapNotNull { line ->
                val packageName = line.substringBefore('=', missingDelimiterValue = "").trim()
                val usageMillis = line.substringAfter('=', missingDelimiterValue = "").toLongOrNull()
                if (packageName.isBlank() || usageMillis == null || usageMillis <= 0L) {
                    null
                } else {
                    packageName to usageMillis
                }
            }
            .toMap()
            .toMutableMap()
    }

    private fun pruneImpossibleUsage(maxAllowedUsageMillis: Long): Boolean {
        if (maxAllowedUsageMillis == Long.MAX_VALUE) {
            return false
        }
        val impossiblePackages = cachedUsageMillisByPackage
            .filterValues { usageMillis -> usageMillis > maxAllowedUsageMillis }
            .keys
        if (impossiblePackages.isEmpty()) {
            return false
        }
        impossiblePackages.forEach { packageName ->
            cachedUsageMillisByPackage.remove(packageName)
        }
        return true
    }

    private fun writeIfDue(force: Boolean) {
        if (!dirty) {
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastWriteElapsedMillis < WRITE_THROTTLE_MILLIS) {
            return
        }
        val content = buildString {
            append(cachedDayKey)
            append('\n')
            cachedUsageMillisByPackage
                .toSortedMap()
                .forEach { (packageName, usageMillis) ->
                    append(packageName)
                    append('=')
                    append(usageMillis.coerceAtLeast(0L))
                    append('\n')
                }
        }
        try {
            val stream = atomicFile.startWrite()
            try {
                stream.write(content.toByteArray(Charsets.UTF_8))
                atomicFile.finishWrite(stream)
                dirty = false
                lastWriteElapsedMillis = now
            } catch (exception: Exception) {
                atomicFile.failWrite(stream)
                throw exception
            }
        } catch (_: Exception) {
            // Usage continuity is a safety improvement. Querying must not fail
            // just because the private cache file could not be written.
        }
    }

    private fun dayKey(dayStartMillis: Long): String {
        return SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(dayStartMillis))
    }

    companion object {
        private const val FILE_NAME = "usage_continuity.txt"
        private const val WRITE_THROTTLE_MILLIS = 1_500L
        private val sharedLock = Any()
        private var cachedDayKey: String = ""
        private var cachedUsageMillisByPackage: MutableMap<String, Long> = mutableMapOf()
        private var loaded = false
        private var dirty = false
        private var lastWriteElapsedMillis: Long = 0L
    }
}
