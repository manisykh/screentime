package com.manisykh.screenrest

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.manisykh.screenrest.data.AppLanguage
import com.manisykh.screenrest.data.RemoteUnlockRequest
import com.manisykh.screenrest.data.RemoteUnlockRequestStatus
import com.manisykh.screenrest.data.groupRemoteRequestsForDisplay
import com.manisykh.screenrest.ui.designsystem.*
import kotlinx.coroutines.delay

@Composable
internal fun FamilyChildRequestHistory(requests: List<RemoteUnlockRequest>, text: AppStrings) {
    val korean = text.appLanguage == AppLanguage.Korean
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            now = System.currentTimeMillis()
        }
    }
    var showAllToday by rememberSaveable { mutableStateOf(false) }
    var showRecentHistory by rememberSaveable { mutableStateOf(false) }
    val grouped = groupRemoteRequestsForDisplay(requests, now)
    val visibleRequests = grouped.pending + (if (showAllToday) grouped.today else grouped.today.take(3)) +
        (if (showRecentHistory) grouped.recentHistory else emptyList())
    FamilySectionTitle(if (korean) "보낸 요청" else "Sent requests")
    ScreenRestCard(tone = if (grouped.pending.isNotEmpty()) ScreenRestTone.Warning else ScreenRestTone.Neutral) {
        if (grouped.pending.isEmpty() && grouped.today.isEmpty() && grouped.recentHistory.isEmpty()) {
            Text(
                text = if (korean) "보낸 요청이 없습니다" else "No sent requests",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            visibleRequests.forEachIndexed { index, request ->
                val displayStatus = if (request.status == RemoteUnlockRequestStatus.Pending &&
                    request.expiresAtMillis < now
                ) RemoteUnlockRequestStatus.Expired else request.status
                ScreenRestListRow(
                    title = request.remoteRequestTitle(text),
                    supportingText = if (korean) {
                        "${formatClockTime(request.createdAtMillis)} · ${formatLimitMinutesLabel(request.requestedMinutes)} 요청"
                    } else {
                        "${formatClockTime(request.createdAtMillis)} · requested ${formatLimitMinutesLabel(request.requestedMinutes)}"
                    },
                    leading = {
                        MoreMenuIcon(
                            R.drawable.ic_family_clock,
                            if (displayStatus == RemoteUnlockRequestStatus.Pending) ScreenRestTone.Warning else ScreenRestTone.Neutral,
                        )
                    },
                    trailing = {
                        ScreenRestStatusPill(
                            label = familyRequestStatusLabel(displayStatus, korean),
                            tone = when (displayStatus) {
                                RemoteUnlockRequestStatus.Pending -> ScreenRestTone.Warning
                                RemoteUnlockRequestStatus.Approved -> ScreenRestTone.Success
                                else -> ScreenRestTone.Neutral
                            },
                        )
                    },
                )
                if (index != visibleRequests.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(ScreenRestTheme.colors.divider),
                    )
                }
            }
            if (grouped.today.size > 3) {
                TextButton(onClick = { showAllToday = !showAllToday }) {
                    Text(if (showAllToday) {
                        if (korean) "오늘 이력 접기" else "Show less"
                    } else {
                        if (korean) "오늘 이력 ${grouped.today.size}건 보기" else "View all ${grouped.today.size} today"
                    })
                }
            }
            if (grouped.recentHistory.isNotEmpty()) {
                TextButton(onClick = { showRecentHistory = !showRecentHistory }) {
                    Text(if (showRecentHistory) {
                        if (korean) "지난 이력 접기" else "Hide recent history"
                    } else {
                        if (korean) "지난 7일 이력 ${grouped.recentHistory.size}건" else "Last 7 days · ${grouped.recentHistory.size}"
                    })
                }
            }
        }
    }
}

@Composable
internal fun FamilyManagementEntry(text: AppStrings, onClick: () -> Unit) {
    val korean = text.appLanguage == AppLanguage.Korean
    FamilySectionTitle(if (korean) "가족 관리" else "Family management")
    ScreenRestCard(contentPadding = PaddingValues(vertical = ScreenRestTheme.spacing.xxs)) {
        ScreenRestListRow(
            title = if (korean) "가족 및 기기 관리" else "Family and device management",
            supportingText = if (korean) "연결 · 역할 · 프로필 · 동기화" else "Pairing · roles · profiles · sync",
            onClick = onClick,
            leading = { MoreMenuIcon(R.drawable.ic_family_manage, ScreenRestTone.Primary) },
            trailing = { MoreChevron() },
        )
    }
}

@Composable
internal fun FamilySectionTitle(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(horizontal = ScreenRestTheme.spacing.xs),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

