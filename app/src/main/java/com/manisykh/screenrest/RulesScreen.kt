package com.manisykh.screenrest

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.manisykh.screenrest.data.AppLanguage
import com.manisykh.screenrest.data.ParentAccountAuthState
import com.manisykh.screenrest.ui.designsystem.*
import com.manisykh.screenrest.ui.safety.LimitStatus
import com.manisykh.screenrest.ui.safety.SafeModeUiState

@Composable
internal fun RulesContent(
    uiState: SafeModeUiState,
    parentAccountAuthState: ParentAccountAuthState,
    text: AppStrings,
    onOpenFamily: () -> Unit,
    editorContent: @Composable ColumnScope.() -> Unit,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    val profileName = screenProfileName(
        parentState = uiState.parentManagementState,
        authState = parentAccountAuthState,
    )
    Column(verticalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.lg)) {
        ScreenRestPageHeader(
            title = if (korean) "사용 규칙" else "Usage rules",
            trailing = {
                if (profileName.isNotBlank()) {
                    CompactProfilePill(
                        profileName = profileName,
                        onClick = onOpenFamily,
                    )
                }
            },
        )
        RulesAppliedResultCard(
            uiState = uiState,
            text = text,
        )
        if (uiState.policyDraftHasChanges) {
            ScreenRestCard(tone = ScreenRestTone.Blocked) {
                ScreenRestSectionHeader(
                    title = if (korean) "저장 전 변경사항" else "Unsaved changes",
                    supportingText = if (korean) {
                        "아래 저장 버튼을 눌러야 변경한 규칙이 적용됩니다"
                    } else {
                        "Use the save button below to apply these rule changes"
                    },
                    action = {
                        ScreenRestStatusPill(
                            label = if (korean) "저장 필요" else "Save required",
                            tone = ScreenRestTone.Blocked,
                        )
                    },
                )
            }
        }
        editorContent()
        Text(
            text = if (korean) {
                "스케줄은 선택 사항이며, 시간 제한은 함께 적용됩니다."
            } else {
                "Schedules are optional; active time limits work together."
            },
            modifier = Modifier.padding(horizontal = ScreenRestTheme.spacing.xs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun CompactProfilePill(
    profileName: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(ScreenRestTheme.radii.button),
        color = ScreenRestPalette.CobaltSoft,
        border = BorderStroke(1.dp, ScreenRestPalette.Cobalt.copy(alpha = 0.18f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.xs),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_more_account),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = ScreenRestPalette.Cobalt,
            )
            Text(
                text = profileName,
                style = MaterialTheme.typography.labelLarge,
                color = ScreenRestPalette.Navy,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RulesAppliedResultCard(
    uiState: SafeModeUiState,
    text: AppStrings,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    val summary = uiState.policySummary
    val currentMode = when {
        uiState.safeModeEnabled -> if (korean) "보호 일시 중지" else "Protection paused"
        !uiState.policyEnforcementEnabled -> if (korean) "규칙 적용 꺼짐" else "Rules are off"
        summary.activeScheduleSummary != null -> summary.activeScheduleSummary.name
            .ifBlank { if (korean) "스케줄 적용" else "Schedule active" }
        summary.allowOnlyModeEnabled -> if (korean) "허용앱만" else "Allow-only"
        else -> if (korean) "일반 사용" else "Normal use"
    }
    val activeTimeLimitCount =
        (if (summary.dailyPolicyEnabled && summary.totalLimitEnabled) 1 else 0) +
            summary.groupSummaries.count { it.limitConfigured && it.activeToday } +
            summary.appLimitSummaries.count { it.activeToday && !it.excludedFromRestrictions }
    val earliest = if (!uiState.safeModeEnabled && uiState.policyEnforcementEnabled) {
        summary.effectiveAppSummaries
            .asSequence()
            .filter { it.remainingMinutes != null && it.status != LimitStatus.Exceeded }
            .minByOrNull { it.remainingMinutes ?: Int.MAX_VALUE }
    } else {
        null
    }
    val tone = when {
        uiState.safeModeEnabled || !uiState.policyEnforcementEnabled -> ScreenRestTone.Warning
        summary.activeScheduleSummary != null -> ScreenRestTone.Schedule
        summary.allowOnlyModeEnabled -> ScreenRestTone.Success
        summary.exceededCount > 0 -> ScreenRestTone.Blocked
        else -> ScreenRestTone.Success
    }
    ScreenRestCard(tone = tone) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.md),
        ) {
            MoreMenuIcon(R.drawable.ic_family_clock, tone)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.xxs),
            ) {
                Text(
                    text = if (korean) "현재 적용 결과" else "Current result",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = currentMode,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = tone.contentColor(),
                )
                Text(
                    text = when {
                        (uiState.safeModeEnabled || !uiState.policyEnforcementEnabled) && activeTimeLimitCount > 0 ->
                            if (korean) "시간 제한 ${activeTimeLimitCount}개 설정됨 · 현재 미적용" else "$activeTimeLimitCount time limits configured · currently inactive"
                        activeTimeLimitCount > 0 ->
                            if (korean) "시간 제한 ${activeTimeLimitCount}개 적용 중" else "$activeTimeLimitCount time limits active"
                        else -> if (korean) "적용 중인 시간 제한 없음" else "No active time limits"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        earliest?.let { app ->
            Surface(
                shape = RoundedCornerShape(ScreenRestTheme.radii.button),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.sm),
                ) {
                    AppIcon(
                        packageName = app.packageName,
                        contentDescription = app.appName,
                        size = 30.dp,
                    )
                    Text(
                        text = if (korean) {
                            "가장 먼저 끝나는 제한 · ${app.appName} ${formatLimitMinutesLabel(app.remainingMinutes ?: 0)} 남음"
                        } else {
                            "First limit · ${app.appName} ${formatLimitMinutesLabel(app.remainingMinutes ?: 0)} left"
                        },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
