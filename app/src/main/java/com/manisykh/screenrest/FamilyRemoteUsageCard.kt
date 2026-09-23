package com.manisykh.screenrest

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.manisykh.screenrest.data.AppLanguage
import com.manisykh.screenrest.data.ChildUsageRefreshRequest
import com.manisykh.screenrest.data.ChildUsageSnapshot
import com.manisykh.screenrest.ui.designsystem.*
import kotlinx.coroutines.delay

@Composable
internal fun FamilyRemoteSnapshotCard(
    snapshot: ChildUsageSnapshot?,
    refreshRequest: ChildUsageRefreshRequest?,
    text: AppStrings,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    var clockMillis by remember(snapshot?.capturedAtMillis) {
        mutableStateOf(System.currentTimeMillis())
    }
    LaunchedEffect(snapshot?.capturedAtMillis) {
        while (true) {
            delay(60_000L)
            clockMillis = System.currentTimeMillis()
        }
    }
    val limitMinutes = snapshot?.effectiveDailyLimitMinutes
    val usedMinutes = snapshot?.todayUsedMillis?.let { millis -> (millis / 60_000L).toInt() } ?: 0
    val countedMinutes = snapshot?.dailyCountedUsageMillis?.let { millis -> (millis / 60_000L).toInt() } ?: 0
    val remainingMinutes = snapshot?.remainingDailyMinutes()
    val stale = snapshot?.let { usage ->
        usage.isDelayed(clockMillis) ||
            usage.dateKey != java.time.Instant.ofEpochMilli(clockMillis)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
    } == true
    val refreshPending = refreshRequest?.isPending(clockMillis) == true
    val refreshDelayed = refreshRequest?.isDelayed(clockMillis) == true
    val refreshExpired = refreshRequest != null && refreshRequest.completedAtMillis == 0L &&
        refreshRequest.expiresAtMillis <= clockMillis &&
        clockMillis - refreshRequest.expiresAtMillis < 60 * 60_000L &&
        (snapshot == null || snapshot.capturedAtMillis < refreshRequest.requestedAtMillis - 2 * 60_000L)
    val progress = if (limitMinutes == 0) {
        1f
    } else if (limitMinutes != null && limitMinutes > 0) {
        countedMinutes.toFloat().div(limitMinutes.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    ScreenRestCard(tone = if (snapshot == null || stale || !snapshot.usageAccessReady || refreshPending) {
        ScreenRestTone.Warning
    } else {
        ScreenRestTone.Success
    }) {
        if (snapshot == null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.sm),
            ) {
                MoreMenuIcon(R.drawable.ic_family_clock, ScreenRestTone.Neutral)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (korean) "자녀 사용량 대기 중" else "Waiting for child usage",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (refreshExpired) {
                            if (korean) "새 기록 요청이 만료됐습니다. 다시 동기화해 주세요."
                            else "The refresh request expired. Try syncing again."
                        } else if (refreshPending) {
                            if (korean) "새 사용 현황을 요청했습니다. 자녀 기기 응답을 기다리는 중입니다."
                            else "Fresh usage requested. Waiting for the child device."
                        } else if (korean) {
                            "동기화를 누르거나 자녀 기기의 정기 동기화를 기다려 주세요."
                        } else {
                            "Tap sync or wait for the child's scheduled update."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            ScreenRestSectionHeader(
                title = if (stale) {
                    if (korean) "마지막 사용 기록" else "Last usage snapshot"
                } else {
                    if (korean) "오늘 사용" else "Today's usage"
                },
                supportingText = snapshot.dateKey,
                action = {
                    ScreenRestStatusPill(
                        label = if (refreshDelayed) {
                            if (korean) "연결 대기" else "Waiting for device"
                        } else if (refreshPending) {
                            if (korean) "새 기록 요청 중" else "Refreshing"
                        } else if (stale) {
                            if (korean) "업데이트 지연" else "Update delayed"
                        } else {
                            if (korean) "동기화됨" else "Synced"
                        },
                        tone = if (stale || refreshPending) ScreenRestTone.Warning else ScreenRestTone.Success,
                    )
                },
            )
            if (!snapshot.usageAccessReady) {
                Text(
                    text = if (korean) "자녀 기기의 사용 기록 권한을 확인해 주세요" else "Check usage access on the child device",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    text = if (remainingMinutes != null) {
                        if (korean) "전체 ${formatLimitMinutesLabel(usedMinutes)} · 제한 ${formatLimitMinutesLabel(remainingMinutes)} 남음" else "${formatLimitMinutesLabel(usedMinutes)} total · ${formatLimitMinutesLabel(remainingMinutes)} limit left"
                    } else {
                        if (korean) "${formatLimitMinutesLabel(usedMinutes)} 사용" else "${formatLimitMinutesLabel(usedMinutes)} used"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (remainingMinutes != null) {
                    FamilyUsageProgress(progress = progress, tone = ScreenRestTone.Warning)
                }
                if (snapshot.appUsageSharingEnabled) {
                    Text(
                        if (korean) "많이 사용한 앱" else "Most-used apps",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    if (snapshot.topApps.isEmpty()) {
                        Text(
                            if (korean) "아직 기록된 앱이 없습니다" else "No app usage recorded yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(ScreenRestTheme.radii.row),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, ScreenRestTheme.colors.divider),
                        ) {
                            Column(modifier = Modifier.padding(horizontal = ScreenRestTheme.spacing.xs)) {
                                val visibleApps = snapshot.topApps.take(5)
                                visibleApps.forEachIndexed { index, app ->
                                    ScreenRestListRow(
                                        title = app.appName,
                                        leading = {
                                            FamilyTopAppBadge(app.appName)
                                        },
                                        trailing = {
                                            Text(
                                                formatDuration(app.usedMillis),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        },
                                    )
                                    if (index != visibleApps.lastIndex) {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().padding(start = 54.dp)
                                                .height(1.dp).background(ScreenRestTheme.colors.divider),
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        if (korean) "앱별 현황은 자녀 기기에서 공유에 동의하면 표시됩니다"
                        else "App usage appears after sharing is enabled on the child device",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = when {
                    snapshot.protectionPaused -> if (korean) "자녀 기기에서 보호가 일시 중지되었습니다" else "Protection is paused on the child device"
                    snapshot.dailyUnlockedForToday -> if (korean) "오늘의 전체 시간 제한이 해제되었습니다" else "The daily limit is unlocked for today"
                    limitMinutes == null -> if (korean) "오늘의 전체 시간 제한이 없습니다" else "No overall daily limit is set"
                    else -> if (korean) "자녀 기기에서 측정한 값입니다" else "Measured on the child device"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = familySyncLabel(snapshot.capturedAtMillis, korean),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (refreshPending || refreshExpired) {
                Text(
                    text = if (refreshExpired) {
                        if (korean) "새 기록 요청이 만료됐습니다. 마지막 측정값을 표시합니다."
                        else "Refresh request expired. Showing the last measurement."
                    } else if (refreshDelayed) {
                        if (korean) "자녀 기기가 오프라인이거나 절전 중일 수 있습니다."
                        else "The child device may be offline or sleeping."
                    } else {
                        if (korean) "자녀 기기에서 새 사용량을 측정하고 있습니다."
                        else "Waiting for a new measurement from the child device."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FamilyTopAppBadge(appName: String) {
    val paletteIndex = Math.floorMod(appName.trim().lowercase().hashCode(), 5)
    val (background, foreground) = when (paletteIndex) {
        0 -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
        1 -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.secondary
        2 -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.tertiary
        3 -> ScreenRestTheme.colors.scheduleContainer to ScreenRestTheme.colors.schedule
        else -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.error
    }
    val initial = appName.trim().takeIf { it.isNotEmpty() }
        ?.let { name -> String(Character.toChars(name.codePointAt(0))).uppercase() }
        ?: "·"
    Surface(
        modifier = Modifier.size(34.dp),
        shape = RoundedCornerShape(11.dp),
        color = background,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = initial,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = foreground,
                maxLines = 1,
            )
        }
    }
}
