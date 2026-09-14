package com.manisykh.screenrest.data

import java.time.Instant
import java.time.ZoneId

/** UI-only grouping. Firestore retention and approval state are not changed. */
data class RemoteRequestDisplayGroups(
    val pending: List<RemoteUnlockRequest>,
    val today: List<RemoteUnlockRequest>,
    val recentHistory: List<RemoteUnlockRequest>,
)

fun groupRemoteRequestsForDisplay(
    requests: List<RemoteUnlockRequest>,
    nowMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): RemoteRequestDisplayGroups {
    val todayStartMillis = Instant.ofEpochMilli(nowMillis)
        .atZone(zoneId).toLocalDate().atStartOfDay(zoneId).toInstant().toEpochMilli()
    val historyStartMillis = nowMillis - 7L * 24L * 60L * 60_000L
    val sorted = requests.sortedByDescending { request -> request.createdAtMillis }
    val pending = sorted.filter { request ->
        request.status == RemoteUnlockRequestStatus.Pending && request.expiresAtMillis >= nowMillis
    }
    val today = sorted.filter { request ->
        request !in pending && request.createdAtMillis >= todayStartMillis
    }
    val recentHistory = sorted.filter { request ->
        request !in pending && request.createdAtMillis < todayStartMillis &&
            request.createdAtMillis >= historyStartMillis
    }
    return RemoteRequestDisplayGroups(pending, today, recentHistory)
}
