package com.manisykh.screenrest

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.manisykh.screenrest.data.AppLanguage
import com.manisykh.screenrest.ui.designsystem.*
import com.manisykh.screenrest.ui.safety.LimitStatus
import com.manisykh.screenrest.ui.safety.PolicySummary

@Composable
internal fun FamilyLocalUsageCard(
    summary: PolicySummary,
    safeModeEnabled: Boolean,
    policyEnforcementEnabled: Boolean,
    text: AppStrings,
    onOpenRules: () -> Unit,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    val availableMinutes = (summary.totalLimitMinutes + summary.totalExtraMinutes).coerceAtLeast(0)
    val remainingMinutes = (availableMinutes - summary.totalUsedMinutes).coerceAtLeast(0)
    val progress = if (summary.totalLimitEnabled && availableMinutes > 0) {
        summary.totalUsedMinutes.toFloat().div(availableMinutes.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val statusTitle = when {
        safeModeEnabled -> if (korean) "보호 일시 중지" else "Protection paused"
        !policyEnforcementEnabled -> if (korean) "규칙 적용 꺼짐" else "Rules are off"
        summary.totalUnlockedForToday -> if (korean) "오늘만 허용" else "Allowed for today"
        summary.totalStatus == LimitStatus.Exceeded -> if (korean) "사용 시간 종료" else "Time is up"
        summary.activeScheduleSummary != null -> summary.activeScheduleSummary.name
        summary.allowOnlyModeEnabled -> if (korean) "허용앱만 사용" else "Allow-only mode"
        else -> if (korean) "자유 시간" else "Free time"
    }
    ScreenRestCard(tone = when {
        summary.totalStatus == LimitStatus.Exceeded -> ScreenRestTone.Blocked
        summary.totalStatus == LimitStatus.Warning -> ScreenRestTone.Warning
        else -> ScreenRestTone.Success
    }) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.sm),
        ) {
            MoreMenuIcon(R.drawable.ic_family_clock, ScreenRestTone.Success)
            Column(modifier = Modifier.weight(1f)) {
                Text(statusTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    text = if (summary.totalLimitEnabled) {
                        if (korean) "${formatLimitMinutesLabel(summary.totalUsedMinutes)} 사용 · ${formatLimitMinutesLabel(remainingMinutes)} 남음" else "${formatLimitMinutesLabel(summary.totalUsedMinutes)} used · ${formatLimitMinutesLabel(remainingMinutes)} left"
                    } else {
                        if (korean) "${formatLimitMinutesLabel(summary.actualTotalUsedMinutes)} 사용 · 제한 없음" else "${formatLimitMinutesLabel(summary.actualTotalUsedMinutes)} used · no daily limit"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        if (summary.totalLimitEnabled) {
            FamilyUsageProgress(
                progress = progress,
                tone = when (summary.totalStatus) {
                    LimitStatus.Normal -> ScreenRestTone.Success
                    LimitStatus.Warning -> ScreenRestTone.Warning
                    LimitStatus.Exceeded -> ScreenRestTone.Blocked
                },
            )
            Text(
                text = if (korean) "오늘 ${formatLimitMinutesLabel(availableMinutes)}" else "Today ${formatLimitMinutesLabel(availableMinutes)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(ScreenRestTheme.colors.divider),
        )
        Surface(
            shape = RoundedCornerShape(ScreenRestTheme.radii.row),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, ScreenRestTheme.colors.divider),
        ) {
            Column(modifier = Modifier.padding(horizontal = ScreenRestTheme.spacing.xs)) {
                FamilyRuleSummaryRow(
                    title = if (korean) "요일별 제한" else "Daily limit",
                    value = if (summary.totalLimitEnabled) formatLimitMinutesLabel(availableMinutes) else text.noLimit,
                    iconRes = R.drawable.ic_nav_today,
                )
                Box(modifier = Modifier.fillMaxWidth().padding(start = 62.dp)
                    .height(1.dp).background(ScreenRestTheme.colors.divider))
                FamilyRuleSummaryRow(
                    title = if (korean) "앱별 제한" else "App limits",
                    value = if (korean) "${summary.appLimitSummaries.count { it.activeToday }}개 적용" else "${summary.appLimitSummaries.count { it.activeToday }} active",
                    iconRes = R.drawable.ic_nav_rules,
                )
                Box(modifier = Modifier.fillMaxWidth().padding(start = 62.dp)
                    .height(1.dp).background(ScreenRestTheme.colors.divider))
                FamilyRuleSummaryRow(
                    title = if (korean) "실행 범위" else "App availability",
                    value = when {
                        summary.activeScheduleSummary != null -> if (korean) "스케줄 적용 중" else "Schedule active"
                        summary.allowOnlyModeEnabled -> if (korean) "허용앱만 적용 중" else "Allow-only active"
                        else -> if (korean) "일반" else "Normal"
                    },
                    iconRes = R.drawable.ic_more_protection,
                )
            }
        }
        OutlinedButton(
            onClick = onOpenRules,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(ScreenRestTheme.radii.button),
        ) {
            Text(if (korean) "내 규칙 보기" else "View my rules")
        }
    }
}

@Composable
internal fun FamilyUsageProgress(progress: Float, tone: ScreenRestTone) {
    val color = when (tone) {
        ScreenRestTone.Success -> ScreenRestTheme.colors.success
        ScreenRestTone.Warning -> ScreenRestTheme.colors.warning
        ScreenRestTone.Blocked -> ScreenRestTheme.colors.blocked
        else -> MaterialTheme.colorScheme.primary
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(ScreenRestTheme.colors.progressTrack),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(color),
        )
    }
}

@Composable
private fun FamilyRuleSummaryRow(title: String, value: String, iconRes: Int) {
    ScreenRestListRow(
        title = title,
        leading = { MoreMenuIcon(iconRes, ScreenRestTone.Primary) },
        trailing = {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}
