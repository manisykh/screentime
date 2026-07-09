package com.manisykh.screenrest.usage

import android.os.SystemClock

object ForegroundAppTracker {
    @Volatile
    private var packageName: String? = null

    @Volatile
    private var updatedAtElapsedMillis: Long = 0L

    @Volatile
    private var updatedAtWallClockMillis: Long = 0L

    fun update(packageName: String) {
        if (packageName.isBlank()) {
            clear()
            return
        }
        this.packageName = packageName
        updatedAtElapsedMillis = SystemClock.elapsedRealtime()
        updatedAtWallClockMillis = System.currentTimeMillis()
    }

    fun clear() {
        packageName = null
        updatedAtElapsedMillis = SystemClock.elapsedRealtime()
        updatedAtWallClockMillis = System.currentTimeMillis()
    }

    fun snapshot(maxAgeMillis: Long): Snapshot? {
        val elapsedAgeMillis = SystemClock.elapsedRealtime() - updatedAtElapsedMillis
        if (updatedAtElapsedMillis <= 0L || elapsedAgeMillis > maxAgeMillis) {
            return null
        }
        return Snapshot(
            packageName = packageName,
            updatedAtWallClockMillis = updatedAtWallClockMillis,
        )
    }

    data class Snapshot(
        val packageName: String?,
        val updatedAtWallClockMillis: Long,
    )
}
