package com.manisykh.screenrest

import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.manisykh.screenrest.data.AppLanguage
import com.manisykh.screenrest.data.ParentAccountAuthState
import com.manisykh.screenrest.data.ParentDeviceRole
import com.manisykh.screenrest.data.ParentManagementState
import com.manisykh.screenrest.data.RemoteUnlockRequestStatus
import com.manisykh.screenrest.ui.designsystem.*
import com.manisykh.screenrest.ui.safety.LimitStatus
import com.manisykh.screenrest.ui.safety.PolicySummary
import com.manisykh.screenrest.ui.safety.SafeModeUiState
import com.manisykh.screenrest.usage.AppUsageInfo

@Composable
fun OverviewContent(
    uiState: SafeModeUiState,
    parentAccountAuthState: ParentAccountAuthState,
    permissionSetupRequired: Boolean,
    text: AppStrings,
    isExpanded: Boolean,
    onRefreshUsageStats: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenFamily: () -> Unit,
    onOpenStatistics: () -> Unit,
) {
    val profileName = screenProfileName(
        parentState = uiState.parentManagementState,
        authState = parentAccountAuthState,
    )
    val presentation = todayStatusPresentation(
        uiState = uiState,
        permissionSetupRequired = permissionSetupRequired,
        text = text,
    )
    Column(verticalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.lg)) {
        ScreenRestBrandHeader(
            profileName = profileName,
            text = text,
            onProfileClick = onOpenFamily,
        )
        Column(verticalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.xs)) {
            Text(
                text = presentation.title,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = presentation.tone.contentColor(),
            )
            Text(
                text = presentation.subtitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AdaptiveTwoPane(
            isExpanded = isExpanded,
            leftContent = {
                TodayUsageSummaryCard(
                    uiState = uiState,
                    text = text,
                    onRefreshUsageStats = onRefreshUsageStats,
                )
                TodayActiveRulesCard(
                    summary = uiState.policySummary,
                    rulesActive = !permissionSetupRequired &&
                        !uiState.safeModeEnabled &&
                        uiState.policyEnforcementEnabled,
                    text = text,
                    onOpenRules = onOpenRules,
                )
                TodayFamilyRequestCard(
                    parentState = uiState.parentManagementState,
                    text = text,
                    onOpenFamily = onOpenFamily,
                )
            },
            rightContent = {
                TodayTopAppsCard(
                    hasUsageAccess = uiState.hasUsageAccess,
                    usageAccessChecking = uiState.usageAccessChecking,
                    lastUpdatedAtMillis = uiState.usageLastUpdatedAtMillis,
                    todayUsage = uiState.todayUsage,
                    policySummary = uiState.policySummary,
                    text = text,
                    onRefreshUsageStats = onRefreshUsageStats,
                    onOpenStatistics = onOpenStatistics,
                )
            },
        )
    }
}

private data class TodayStatusPresentation(
    val title: String,
    val subtitle: String,
    val tone: ScreenRestTone,
)

private fun todayStatusPresentation(
    uiState: SafeModeUiState,
    permissionSetupRequired: Boolean,
    text: AppStrings,
): TodayStatusPresentation {
    val korean = text.appLanguage == AppLanguage.Korean
    val summary = uiState.policySummary
    return when {
        permissionSetupRequired -> TodayStatusPresentation(
            title = if (korean) "보호 상태 확인 필요" else "Protection needs attention",
            subtitle = if (korean) "권한 또는 차단 준비 상태를 확인하세요" else "Review permissions or blocking readiness",
            tone = ScreenRestTone.Blocked,
        )
        uiState.safeModeEnabled -> TodayStatusPresentation(
            title = if (korean) "보호 일시 중지" else "Protection paused",
            subtitle = if (korean) "규칙은 보존되지만 현재 차단하지 않습니다" else "Rules are preserved but blocking is paused",
            tone = ScreenRestTone.Warning,
        )
        !uiState.policyEnforcementEnabled -> TodayStatusPresentation(
            title = if (korean) "규칙 적용 꺼짐" else "Rules are off",
            subtitle = if (korean) "설정은 보존되어 있으며 현재 적용되지 않습니다" else "Settings are preserved but not currently applied",
            tone = ScreenRestTone.Warning,
        )
        summary.totalStatus == LimitStatus.Exceeded -> TodayStatusPresentation(
            title = if (korean) "오늘 사용 시간이 끝났어요" else "Today's time is up",
            subtitle = if (korean) "추가 허용 전까지 시간 제한 앱이 차단됩니다" else "Time-limited apps stay blocked until more time is allowed",
            tone = ScreenRestTone.Blocked,
        )
        summary.activeScheduleSummary != null -> TodayStatusPresentation(
            title = if (korean) "${summary.activeScheduleSummary.name} 적용 중" else "${summary.activeScheduleSummary.name} is active",
            subtitle = if (korean) "허용된 앱만 사용할 수 있습니다" else "Only allowed apps can be used",
            tone = ScreenRestTone.Schedule,
        )
        summary.allowOnlyModeEnabled -> TodayStatusPresentation(
            title = if (korean) "허용된 앱만 사용 중" else "Allow-only mode",
            subtitle = if (korean) "허용 앱 ${summary.allowOnlyAllowedAppCount}개를 사용할 수 있습니다" else "${summary.allowOnlyAllowedAppCount} apps are available",
            tone = ScreenRestTone.Success,
        )
        summary.warningCount > 0 || summary.totalStatus == LimitStatus.Warning -> TodayStatusPresentation(
            title = if (korean) "사용 시간이 얼마 남지 않았어요" else "Time is running low",
            subtitle = if (korean) "가장 먼저 끝나는 제한을 확인하세요" else "Review the limit that will end first",
            tone = ScreenRestTone.Warning,
        )
        else -> TodayStatusPresentation(
            title = if (korean) "현재 자유 시간" else "Free time now",
            subtitle = if (korean) "적용 중인 차단 없음" else "No active block",
            tone = ScreenRestTone.Primary,
        )
    }
}

internal fun ScreenRestTone.contentColor(): Color {
    return when (this) {
        ScreenRestTone.Success -> ScreenRestPalette.Teal
        ScreenRestTone.Warning -> ScreenRestPalette.Amber
        ScreenRestTone.Blocked -> ScreenRestPalette.Coral
        ScreenRestTone.Schedule -> ScreenRestPalette.Indigo
        ScreenRestTone.Primary -> ScreenRestPalette.Cobalt
        ScreenRestTone.Neutral -> ScreenRestPalette.Navy
    }
}

internal fun screenProfileName(
    parentState: ParentManagementState,
    authState: ParentAccountAuthState,
): String {
    return parentState.localProfileName
        .ifBlank { authState.displayName }
        .ifBlank { parentState.childDeviceName }
        .trim()
}

@Composable
private fun ScreenRestBrandHeader(
    profileName: String,
    text: AppStrings,
    onProfileClick: () -> Unit,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.sm),
        ) {
            Image(
                painter = painterResource(R.mipmap.app_icon_round),
                contentDescription = if (korean) "폰 쉼 앱 아이콘" else "ScreenRest app icon",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape),
            )
            Text(
                text = if (korean) "폰 쉼" else "ScreenRest",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = ScreenRestPalette.Navy,
            )
        }
        if (profileName.isNotBlank()) {
            Surface(
                onClick = onProfileClick,
                shape = RoundedCornerShape(ScreenRestTheme.radii.button),
                color = ScreenRestPalette.CobaltSoft,
                border = BorderStroke(1.dp, ScreenRestPalette.Cobalt.copy(alpha = 0.18f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.xs),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_more_account),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = ScreenRestPalette.Cobalt,
                    )
                    Text(
                        text = profileName,
                        style = MaterialTheme.typography.labelLarge,
                        color = ScreenRestPalette.Navy,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_right),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayUsageSummaryCard(
    uiState: SafeModeUiState,
    text: AppStrings,
    onRefreshUsageStats: () -> Unit,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    val summary = uiState.policySummary
    val availableMinutes = (summary.totalLimitMinutes + summary.totalExtraMinutes).coerceAtLeast(0)
    val remainingMinutes = (availableMinutes - summary.totalUsedMinutes).coerceAtLeast(0)
    val progress = if (summary.totalLimitEnabled && availableMinutes > 0) {
        summary.totalUsedMinutes.toFloat().div(availableMinutes).coerceIn(0f, 1f)
    } else {
        0f
    }
    ScreenRestCard(
        tone = when (summary.totalStatus) {
            LimitStatus.Normal -> ScreenRestTone.Success
            LimitStatus.Warning -> ScreenRestTone.Warning
            LimitStatus.Exceeded -> ScreenRestTone.Blocked
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.md),
        ) {
            MoreMenuIcon(R.drawable.ic_family_clock, ScreenRestTone.Success)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (summary.totalLimitEnabled) {
                        if (korean) {
                            "${formatLimitMinutesLabel(summary.actualTotalUsedMinutes)} 사용 · ${formatLimitMinutesLabel(remainingMinutes)} 남음"
                        } else {
                            "${formatLimitMinutesLabel(summary.actualTotalUsedMinutes)} used · ${formatLimitMinutesLabel(remainingMinutes)} left"
                        }
                    } else {
                        if (korean) "${formatLimitMinutesLabel(summary.actualTotalUsedMinutes)} 사용" else "${formatLimitMinutesLabel(summary.actualTotalUsedMinutes)} used"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (summary.totalLimitEnabled) {
                        if (korean) "오늘 ${formatLimitMinutesLabel(availableMinutes)}" else "Today ${formatLimitMinutesLabel(availableMinutes)}"
                    } else {
                        text.noLimit
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRefreshUsageStats, enabled = uiState.hasUsageAccess) {
                Icon(
                    painter = painterResource(R.drawable.ic_family_sync),
                    contentDescription = if (korean) "사용량 새로고침" else "Refresh usage",
                    tint = ScreenRestPalette.Teal,
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
        }
        if (summary.actualTotalUsedMinutes != summary.totalUsedMinutes) {
            Text(
                text = if (korean) {
                    "제한에 계산된 사용량 ${formatLimitMinutesLabel(summary.totalUsedMinutes)}"
                } else {
                    "${formatLimitMinutesLabel(summary.totalUsedMinutes)} counted toward limits"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = if (uiState.usageAccessChecking) text.updating else usageLastUpdatedLabel(uiState.usageLastUpdatedAtMillis, text),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private data class TodayRuleItem(
    val title: String,
    val value: String,
    val iconRes: Int,
    val tone: ScreenRestTone,
)

private fun todayRuleItems(summary: PolicySummary, text: AppStrings): List<TodayRuleItem> {
    val korean = text.appLanguage == AppLanguage.Korean
    return buildList {
        if (summary.dailyPolicyEnabled && summary.totalLimitEnabled) {
            add(
                TodayRuleItem(
                    title = if (korean) "요일별 제한" else "Daily limit",
                    value = formatLimitMinutesLabel((summary.totalLimitMinutes + summary.totalExtraMinutes).coerceAtLeast(0)),
                    iconRes = R.drawable.ic_nav_today,
                    tone = ScreenRestTone.Primary,
                ),
            )
        }
        val activeGroups = summary.groupSummaries.count { it.limitConfigured && it.activeToday }
        if (activeGroups > 0) {
            add(
                TodayRuleItem(
                    title = if (korean) "앱 그룹" else "App groups",
                    value = if (korean) "${activeGroups}개 적용" else "$activeGroups active",
                    iconRes = R.drawable.ic_nav_rules,
                    tone = ScreenRestTone.Warning,
                ),
            )
        }
        val activeAppLimits = summary.appLimitSummaries.count { it.activeToday && !it.excludedFromRestrictions }
        if (activeAppLimits > 0) {
            add(
                TodayRuleItem(
                    title = if (korean) "앱별 제한" else "App limits",
                    value = if (korean) "${activeAppLimits}개 적용" else "$activeAppLimits active",
                    iconRes = R.drawable.ic_family_device,
                    tone = ScreenRestTone.Primary,
                ),
            )
        }
        summary.activeScheduleSummary?.let { schedule ->
            add(
                TodayRuleItem(
                    title = schedule.name.ifBlank { if (korean) "스케줄" else "Schedule" },
                    value = if (korean) "허용 앱 ${schedule.allowedAppCount}개" else "${schedule.allowedAppCount} allowed apps",
                    iconRes = R.drawable.ic_family_clock,
                    tone = ScreenRestTone.Schedule,
                ),
            )
        }
        if (summary.activeScheduleSummary == null && summary.allowOnlyModeEnabled) {
            add(
                TodayRuleItem(
                    title = if (korean) "허용앱만" else "Allow-only",
                    value = if (korean) "허용 앱 ${summary.allowOnlyAllowedAppCount}개" else "${summary.allowOnlyAllowedAppCount} allowed apps",
                    iconRes = R.drawable.ic_more_protection,
                    tone = ScreenRestTone.Success,
                ),
            )
        }
    }
}

@Composable
private fun TodayActiveRulesCard(
    summary: PolicySummary,
    rulesActive: Boolean,
    text: AppStrings,
    onOpenRules: () -> Unit,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    val items = if (rulesActive) todayRuleItems(summary, text) else emptyList()
    Column(verticalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.sm)) {
        FamilySectionTitle(if (korean) "현재 적용 중" else "Active now")
        ScreenRestCard(contentPadding = PaddingValues(vertical = ScreenRestTheme.spacing.xs)) {
            if (items.isEmpty()) {
                ScreenRestListRow(
                    title = if (rulesActive) {
                        if (korean) "적용 중인 규칙 없음" else "No active rules"
                    } else {
                        if (korean) "현재 규칙이 적용되지 않음" else "Rules are not currently applied"
                    },
                    supportingText = if (rulesActive) {
                        if (korean) "필요할 때 규칙을 설정할 수 있습니다" else "Set a rule whenever you need one"
                    } else {
                        if (korean) "보호 상태를 확인하세요" else "Review protection status"
                    },
                    onClick = onOpenRules,
                    leading = { MoreMenuIcon(R.drawable.ic_nav_rules, ScreenRestTone.Neutral) },
                    trailing = { MoreChevron() },
                )
            } else {
                items.forEachIndexed { index, item ->
                    ScreenRestListRow(
                        title = item.title,
                        onClick = onOpenRules,
                        leading = { MoreMenuIcon(item.iconRes, item.tone) },
                        trailing = {
                            Text(
                                text = item.value,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                            MoreChevron()
                        },
                    )
                    if (index != items.lastIndex) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(ScreenRestTheme.colors.divider),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayFamilyRequestCard(
    parentState: ParentManagementState,
    text: AppStrings,
    onOpenFamily: () -> Unit,
) {
    val connected = parentState.paired || parentState.linkedChildDevices.isNotEmpty() || parentState.linkedParentDevices.isNotEmpty()
    if (!connected) return
    val korean = text.appLanguage == AppLanguage.Korean
    val request = when (parentState.deviceRole) {
        ParentDeviceRole.Parent -> parentState.remoteUnlockRequests
            .filter { it.status == RemoteUnlockRequestStatus.Pending }
            .maxByOrNull { it.createdAtMillis }
        ParentDeviceRole.Child -> parentState.remoteUnlockRequests.maxByOrNull { it.createdAtMillis }
    } ?: return
    val title = when (parentState.deviceRole) {
        ParentDeviceRole.Parent -> if (korean) {
            "${request.childDeviceName.ifBlank { request.targetAppName }}의 요청"
        } else {
            "Request from ${request.childDeviceName.ifBlank { request.targetAppName }}"
        }
        ParentDeviceRole.Child -> if (korean) "보낸 요청 ${familyRequestStatusLabel(request.status, true)}" else "Sent request ${familyRequestStatusLabel(request.status, false)}"
    }
    val detail = if (korean) {
        "${request.targetAppName.ifBlank { request.remoteRequestTitle(text) }} · ${formatLimitMinutesLabel(request.requestedMinutes)} 요청"
    } else {
        "${request.targetAppName.ifBlank { request.remoteRequestTitle(text) }} · ${formatLimitMinutesLabel(request.requestedMinutes)} requested"
    }
    ScreenRestCard(tone = ScreenRestTone.Warning) {
        ScreenRestListRow(
            title = title,
            supportingText = detail,
            onClick = onOpenFamily,
            leading = { MoreMenuIcon(R.drawable.ic_family_clock, ScreenRestTone.Warning) },
            trailing = {
                Text(
                    text = if (korean) "요청 보기" else "View",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = ScreenRestPalette.Amber,
                )
                MoreChevron()
            },
        )
    }
}

@Composable
private fun TodayTopAppsCard(
    hasUsageAccess: Boolean,
    usageAccessChecking: Boolean,
    lastUpdatedAtMillis: Long,
    todayUsage: List<AppUsageInfo>,
    policySummary: PolicySummary,
    text: AppStrings,
    onRefreshUsageStats: () -> Unit,
    onOpenStatistics: () -> Unit,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    Column(verticalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FamilySectionTitle(if (korean) "오늘 많이 사용한 앱" else "Most used today")
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRefreshUsageStats, enabled = hasUsageAccess) {
                Icon(
                    painter = painterResource(R.drawable.ic_family_sync),
                    contentDescription = if (korean) "새로고침" else "Refresh",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        ScreenRestCard(contentPadding = PaddingValues(horizontal = ScreenRestTheme.spacing.md, vertical = ScreenRestTheme.spacing.xs)) {
            when {
                usageAccessChecking && hasUsageAccess -> Text(
                    text = text.updating,
                    modifier = Modifier.padding(vertical = ScreenRestTheme.spacing.lg),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                !hasUsageAccess -> Text(
                    text = text.usageAccessRequired,
                    modifier = Modifier.padding(vertical = ScreenRestTheme.spacing.md),
                    style = MaterialTheme.typography.bodyMedium,
                )
                todayUsage.isEmpty() -> Text(
                    text = text.noUsageRecorded,
                    modifier = Modifier.padding(vertical = ScreenRestTheme.spacing.md),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> todayUsage.take(5).forEachIndexed { index, appUsage ->
                    ScreenRestListRow(
                        title = appUsage.appName,
                        supportingText = if (index == 0) {
                            if (korean) "오늘 가장 많이 사용" else "Most used today"
                        } else {
                            null
                        },
                        leading = {
                            AppIcon(
                                packageName = appUsage.packageName,
                                contentDescription = appUsage.appName,
                                size = 40.dp,
                            )
                        },
                        trailing = {
                            Text(
                                text = formatDuration(appUsage.totalTimeMillis),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = policySummary.statusForPackage(appUsage.packageName).semanticColor(),
                            )
                        },
                    )
                    if (index != todayUsage.take(5).lastIndex) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(ScreenRestTheme.colors.divider),
                        )
                    }
                }
            }
            if (todayUsage.size > 5) {
                TextButton(
                    onClick = onOpenStatistics,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = if (korean) "전체 앱은 통계에서 보기" else "View all apps in Statistics",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Start,
                    )
                    MoreChevron()
                }
            }
            Text(
                text = if (usageAccessChecking) text.updating else usageLastUpdatedLabel(lastUpdatedAtMillis, text),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
