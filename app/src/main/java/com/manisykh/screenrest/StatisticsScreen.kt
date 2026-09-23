package com.manisykh.screenrest

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manisykh.screenrest.data.AppLanguage
import com.manisykh.screenrest.data.ParentAccountAuthState
import com.manisykh.screenrest.ui.designsystem.*
import com.manisykh.screenrest.ui.safety.AppGroupSummary
import com.manisykh.screenrest.ui.safety.LimitStatus
import com.manisykh.screenrest.ui.safety.PolicySummary
import com.manisykh.screenrest.ui.safety.SafeModeUiState
import com.manisykh.screenrest.usage.AppUsageInfo
import com.manisykh.screenrest.usage.DailyUsageInfo
import com.manisykh.screenrest.ui.theme.AppOver
import com.manisykh.screenrest.ui.theme.AppSafe
import com.manisykh.screenrest.ui.theme.AppWarn
import kotlin.math.abs

@Composable
fun StatisticsContent(
    uiState: SafeModeUiState,
    parentAccountAuthState: ParentAccountAuthState,
    text: AppStrings,
    isExpanded: Boolean,
    onOpenFamily: () -> Unit,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    var selectedRange by rememberSaveable { mutableStateOf(StatisticsDisplayRange.SevenDays) }
    val dayCount = selectedRange.dayCount
    val selectedDailyUsage = uiState.usageStatistics.dailyUsage.takeLast(dayCount)
    val selectedTopApps = when (selectedRange) {
        StatisticsDisplayRange.SevenDays -> uiState.usageStatistics.topApps.sevenDays
        StatisticsDisplayRange.ThirtyDays -> uiState.usageStatistics.topApps.thirtyDays
    }
    val profileName = screenProfileName(
        parentState = uiState.parentManagementState,
        authState = parentAccountAuthState,
    )

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = if (isExpanded) Modifier.fillMaxWidth(0.82f) else Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.lg),
        ) {
            ScreenRestPageHeader(
                title = if (korean) "사용 흐름" else "Usage trends",
                trailing = {
                    if (profileName.isNotBlank()) {
                        CompactProfilePill(
                            profileName = profileName,
                            onClick = onOpenFamily,
                        )
                    }
                },
            )
            StatisticsRangeSelector(
                selectedRange = selectedRange,
                onRangeSelected = { selectedRange = it },
                korean = korean,
            )
            StatisticsFlowCard(
                dailyUsage = selectedDailyUsage,
                previousDailyUsage = uiState.usageStatistics.dailyUsage
                    .dropLast(dayCount)
                    .takeLast(dayCount),
                selectedRange = selectedRange,
                text = text,
            )
            StatisticsTopAppsCard(
                topApps = selectedTopApps,
                policySummary = uiState.policySummary,
                text = text,
            )
            GroupStatsCard(
                groupSummaries = uiState.policySummary.groupSummaries,
                text = text,
            )
            Text(
                text = if (uiState.statisticsRefreshing) {
                    text.updating
                } else {
                    usageLastUpdatedLabel(uiState.statisticsLastUpdatedAtMillis, text)
                },
                modifier = Modifier.padding(horizontal = ScreenRestTheme.spacing.xs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private enum class StatisticsDisplayRange(val dayCount: Int) {
    SevenDays(7),
    ThirtyDays(30),
}

@Composable
private fun StatisticsRangeSelector(
    selectedRange: StatisticsDisplayRange,
    onRangeSelected: (StatisticsDisplayRange) -> Unit,
    korean: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(ScreenRestTheme.radii.button),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(modifier = Modifier.padding(4.dp)) {
            StatisticsDisplayRange.entries.forEach { range ->
                val selected = range == selectedRange
                Surface(
                    onClick = { onRangeSelected(range) },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(ScreenRestTheme.radii.button - 4.dp),
                    color = if (selected) ScreenRestPalette.Cobalt else Color.Transparent,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = when (range) {
                                StatisticsDisplayRange.SevenDays -> if (korean) "7일" else "7 days"
                                StatisticsDisplayRange.ThirtyDays -> if (korean) "30일" else "30 days"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatisticsFlowCard(
    dailyUsage: List<DailyUsageInfo>,
    previousDailyUsage: List<DailyUsageInfo>,
    selectedRange: StatisticsDisplayRange,
    text: AppStrings,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    val recordedDailyUsage = dailyUsage.filter { usage -> usage.hasRecordedData }
    val averageMillis = if (recordedDailyUsage.isNotEmpty()) {
        recordedDailyUsage.sumOf { usage -> usage.totalTimeMillis } / recordedDailyUsage.size
    } else {
        0L
    }
    val previousRecordedUsage = previousDailyUsage.filter { usage -> usage.hasRecordedData }
    val previousAverageMillis = previousRecordedUsage
        .takeIf { values -> values.isNotEmpty() }
        ?.let { values -> values.sumOf { usage -> usage.totalTimeMillis } / values.size }
    val averageDifferenceMillis = previousAverageMillis?.let { previous -> averageMillis - previous }
    val comparisonColor = when {
        averageDifferenceMillis == null -> MaterialTheme.colorScheme.onSurfaceVariant
        averageDifferenceMillis <= 0L -> ScreenRestPalette.Teal
        else -> ScreenRestPalette.Coral
    }
    val comparisonLabel = when {
        averageDifferenceMillis == null -> if (korean) {
            "비교할 이전 기록이 아직 없어요"
        } else {
            "No earlier period to compare yet"
        }
        averageDifferenceMillis == 0L -> if (korean) "이전 기간과 같아요" else "Same as the previous period"
        averageDifferenceMillis < 0L -> if (korean) {
            "이전 기간보다 ${formatDuration(abs(averageDifferenceMillis))} 줄었어요"
        } else {
            "${formatDuration(abs(averageDifferenceMillis))} less than the previous period"
        }
        else -> if (korean) {
            "이전 기간보다 ${formatDuration(averageDifferenceMillis)} 늘었어요"
        } else {
            "${formatDuration(averageDifferenceMillis)} more than the previous period"
        }
    }

    ScreenRestCard(contentPadding = PaddingValues(ScreenRestTheme.spacing.lg)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.xs),
        ) {
            Text(
                text = when (selectedRange) {
                    StatisticsDisplayRange.SevenDays -> if (korean) "최근 7일 평균" else "7-day average"
                    StatisticsDisplayRange.ThirtyDays -> if (korean) "최근 30일 평균" else "30-day average"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (recordedDailyUsage.isEmpty()) "—" else formatDuration(averageMillis),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = ScreenRestPalette.Navy,
            )
            Text(
                text = comparisonLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = comparisonColor,
            )
        }
        StatisticsDailyTrendChart(
            dailyUsage = dailyUsage,
            text = text,
        )
        StatisticsGoalSummary(
            dailyUsage = dailyUsage,
            text = text,
        )
    }
}

@Composable
private fun StatisticsDailyTrendChart(
    dailyUsage: List<DailyUsageInfo>,
    text: AppStrings,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    val hasDailyUsageData = dailyUsage.any { usage -> usage.hasRecordedData }
    val maxUsageMillis = dailyUsage.maxOfOrNull { usage ->
        maxOf(usage.totalTimeMillis, (usage.dailyGoalMinutes ?: 0) * 60_000L)
    }?.coerceAtLeast(1L) ?: 1L
    val listState = rememberLazyListState()

    LaunchedEffect(dailyUsage.size, hasDailyUsageData) {
        if (hasDailyUsageData && dailyUsage.size > 7) {
            listState.scrollToItem((dailyUsage.lastIndex - 6).coerceAtLeast(0))
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (korean) "일별 사용" else "Daily use",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = ScreenRestPalette.Navy,
            )
            if (dailyUsage.size > 7) {
                Text(
                    text = if (korean) "좌우로 움직여 보세요" else "Swipe to see more",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!hasDailyUsageData) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text.noStats, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
            ) {
                val spacing = 6.dp
                val itemWidth = if (dailyUsage.size <= 7) {
                    ((maxWidth - spacing * (dailyUsage.size - 1).coerceAtLeast(0)) /
                        dailyUsage.size.coerceAtLeast(1)).coerceAtLeast(38.dp)
                } else {
                    46.dp
                }
                LazyRow(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    items(dailyUsage, key = { usage -> usage.dayStartMillis }) { usage ->
                        StatisticsDailyUsageBar(
                            usage = usage,
                            maxUsageMillis = maxUsageMillis,
                            itemWidth = itemWidth,
                        )
                    }
                }
            }
        }
        StatisticsDailyTrendLegend(korean = korean)
    }
}

@Composable
private fun StatisticsDailyTrendLegend(korean: Boolean) {
    val noGoalColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatisticsDailyTrendLegendItem(
            color = ScreenRestPalette.Teal,
            label = if (korean) "목표 이내" else "Within goal",
        )
        StatisticsDailyTrendLegendItem(
            color = ScreenRestPalette.Amber,
            label = if (korean) "목표 임박" else "Near goal",
        )
        StatisticsDailyTrendLegendItem(
            color = ScreenRestPalette.Coral,
            label = if (korean) "목표 초과" else "Over goal",
        )
        StatisticsDailyTrendLegendItem(
            color = noGoalColor,
            label = if (korean) "목표·기록 없음" else "No goal/data",
        )
    }
}

@Composable
private fun StatisticsDailyTrendLegendItem(
    color: Color,
    label: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun StatisticsDailyUsageBar(
    usage: DailyUsageInfo,
    maxUsageMillis: Long,
    itemWidth: androidx.compose.ui.unit.Dp,
) {
    val fraction = if (maxUsageMillis <= 0L) 0f else {
        (usage.totalTimeMillis.toFloat() / maxUsageMillis.toFloat()).coerceIn(0f, 1f)
    }
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 450),
        label = "statisticsDailyUsageBar",
    )
    val today = isToday(usage.dayStartMillis)
    val goalMillis = usage.dailyGoalMinutes?.times(60_000L)
    val goalFraction = if (goalMillis == null || goalMillis <= 0L) null else {
        usage.totalTimeMillis.toFloat() / goalMillis.toFloat()
    }
    val noGoalColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    val barColor = when {
        !usage.hasRecordedData -> noGoalColor
        !usage.hasRecordedGoal || goalMillis == null -> noGoalColor
        usage.totalTimeMillis > goalMillis -> ScreenRestPalette.Coral
        goalFraction != null && goalFraction >= 0.85f -> ScreenRestPalette.Amber
        else -> ScreenRestPalette.Teal
    }
    val labelColor = if (today) ScreenRestPalette.Cobalt else MaterialTheme.colorScheme.onSurfaceVariant
    val barHeight = (126.dp * animatedFraction).coerceAtLeast(if (usage.hasRecordedData) 7.dp else 3.dp)

    Column(
        modifier = Modifier.width(itemWidth),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(
            text = if (usage.hasRecordedData) {
                formatDuration(usage.totalTimeMillis).replace(" ", "\n")
            } else {
                "—"
            },
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = labelColor,
            maxLines = 2,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .width(24.dp)
                .height(132.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight)
                    .clip(RoundedCornerShape(50))
                    .background(barColor),
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = formatStatsWeekdayLabel(usage.dayStartMillis),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = labelColor,
            maxLines = 1,
        )
        Text(
            text = formatStatsDateLabel(usage.dayStartMillis),
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun StatisticsGoalSummary(
    dailyUsage: List<DailyUsageInfo>,
    text: AppStrings,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    val goalDays = dailyUsage.filter { usage ->
        usage.hasRecordedData && usage.hasRecordedGoal && usage.dailyGoalMinutes != null
    }
    if (goalDays.isEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(ScreenRestTheme.radii.button))
                .background(ScreenRestPalette.CobaltSoft.copy(alpha = 0.48f))
                .padding(ScreenRestTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.sm),
        ) {
            MoreMenuIcon(R.drawable.ic_nav_statistics, ScreenRestTone.Primary)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (korean) "날짜별 목표 기록을 시작했어요" else "Daily goal tracking has started",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = ScreenRestPalette.Navy,
                )
                Text(
                    text = if (korean) {
                        "기록된 목표가 있는 날부터 달성 결과를 보여드립니다."
                    } else {
                        "Results appear only for days with a recorded goal."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        val withinGoalCount = goalDays.count { usage ->
            usage.totalTimeMillis <= (usage.dailyGoalMinutes ?: 0) * 60_000L
        }
        val exceededGoalCount = goalDays.size - withinGoalCount
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(ScreenRestTheme.radii.button))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f))
                .padding(vertical = ScreenRestTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatisticsGoalMetric(
                label = if (korean) "목표 안에서 사용" else "Within goal",
                value = if (korean) "${withinGoalCount}일" else "$withinGoalCount days",
                tone = ScreenRestTone.Success,
                iconRes = R.drawable.ic_family_clock,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(54.dp)
                    .background(ScreenRestTheme.colors.divider),
            )
            StatisticsGoalMetric(
                label = if (korean) "목표 초과" else "Over goal",
                value = if (korean) "${exceededGoalCount}일" else "$exceededGoalCount days",
                tone = ScreenRestTone.Blocked,
                iconRes = R.drawable.ic_family_block,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = if (korean) {
                "목표 기록 ${goalDays.size}일 기준"
            } else {
                "Based on ${goalDays.size} days with recorded goals"
            },
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun StatisticsGoalMetric(
    label: String,
    value: String,
    tone: ScreenRestTone,
    iconRes: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = ScreenRestTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.sm),
    ) {
        MoreMenuIcon(iconRes = iconRes, tone = tone)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = tone.contentColor(),
            )
        }
    }
}

@Composable
private fun StatisticsTopAppsCard(
    topApps: List<AppUsageInfo>,
    policySummary: PolicySummary,
    text: AppStrings,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    val topUsageMillis = topApps.firstOrNull()?.totalTimeMillis?.coerceAtLeast(1L) ?: 1L

    ScreenRestCard {
        ScreenRestSectionHeader(
            title = if (korean) "많이 사용한 앱" else "Most used apps",
            supportingText = if (korean) "아래 목록을 스크롤해 더 볼 수 있습니다." else "Scroll the list to see more.",
        )
        if (topApps.isEmpty()) {
            Text(text.noStats, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            ContainedLazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 430.dp),
                resetKey = topApps.map { usage -> usage.packageName },
            ) {
                items(topApps, key = { appUsage -> appUsage.packageName }) { appUsage ->
                    UsageListRow(
                        appUsage = appUsage,
                        topUsageMillis = topUsageMillis,
                        status = policySummary.statusForPackage(appUsage.packageName),
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupStatsCard(
    groupSummaries: List<AppGroupSummary>,
    text: AppStrings,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    var expanded by rememberSaveable { mutableStateOf(false) }
    ScreenRestCard {
        ScreenRestSectionHeader(
            title = text.groupStats,
            supportingText = if (groupSummaries.isEmpty()) {
                text.noStats
            } else if (korean) {
                "${groupSummaries.size}개 그룹"
            } else {
                "${groupSummaries.size} groups"
            },
            action = if (groupSummaries.isEmpty()) null else {
                {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(
                            text = if (expanded) {
                                if (korean) "접기" else "Collapse"
                            } else {
                                if (korean) "보기" else "View"
                            },
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            },
        )
        if (groupSummaries.isNotEmpty() && expanded) {
            groupSummaries.forEach { groupSummary ->
                ProgressLine(
                    label = groupSummary.groupName.ifBlank { text.groupName },
                    usedMinutes = groupSummary.usedMinutes,
                    limitMinutes = groupSummary.limitMinutes,
                    status = groupSummary.status,
                    text = text,
                    extraMinutes = groupSummary.extraMinutes,
                    limitEnabled = groupSummary.limitEnabled,
                    limitTextOverride = if (groupSummary.activeToday) null else text.todayNotAppliedLabel(),
                )
                val topGroupApps = groupSummary.appUsages
                    .filter { appUsage -> appUsage.usedMinutes > 0 }
                    .sortedByDescending { appUsage -> appUsage.usedMinutes }
                    .take(3)
                if (topGroupApps.isNotEmpty()) {
                    topGroupApps.forEach { appUsage ->
                        AppRow(
                            appName = appUsage.appName,
                            packageName = appUsage.packageName,
                            supportingText = appUsage.limitMinutes?.let { limitMinutes ->
                                formatLimitWithAllowance(
                                    limitMinutes,
                                    appUsage.extraMinutes,
                                    appUsage.unlockedForToday,
                                    text,
                                )
                            }.orEmpty(),
                            trailingContent = {
                                LimitTimeChip(formatLimitMinutesLabel(appUsage.usedMinutes))
                            },
                        )
                    }
                } else {
                    Text(
                        text.noGroupAppStats,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun AdaptiveTwoPane(
    isExpanded: Boolean,
    leftContent: @Composable ColumnScope.() -> Unit,
    rightContent: @Composable ColumnScope.() -> Unit,
) {
    if (isExpanded) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = leftContent,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = rightContent,
            )
        }
    } else {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            leftContent()
            rightContent()
        }
    }
}

@Composable
fun StatusCard(uiState: SafeModeUiState, text: AppStrings) {
    val summary = uiState.policySummary
    val hasTotalLimit = summary.totalLimitEnabled && !summary.totalUnlockedForToday
    val effectiveTotalLimitMinutes = (summary.totalLimitMinutes + summary.totalExtraMinutes)
        .coerceAtLeast(summary.totalLimitMinutes)
    val overMinutes = if (!hasTotalLimit) {
        0
    } else {
        (summary.totalUsedMinutes - effectiveTotalLimitMinutes).coerceAtLeast(0)
    }
    val headline = if (uiState.safeModeEnabled) text.safeModeOn else text.safeModeOff
    SimpleCard {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text.todayStatus, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    headline,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            StatusBadge(
                label = if (uiState.safeModeEnabled) "SAFE" else "LIVE",
                status = if (uiState.safeModeEnabled) LimitStatus.Normal else LimitStatus.Warning,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            UsageProgressRing(
                usedMinutes = summary.totalUsedMinutes,
                limitMinutes = if (hasTotalLimit) {
                    effectiveTotalLimitMinutes
                } else {
                    0
                },
                status = summary.totalStatus,
            )
            Spacer(modifier = Modifier.width(24.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (text.appLanguage == AppLanguage.Korean) {
                        "제한 적용 사용량"
                    } else {
                        "Usage counted toward limits"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        formatLimitMinutesLabel(summary.totalUsedMinutes),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        " / ${if (summary.totalLimitEnabled) {
                            formatLimitWithAllowance(summary.totalLimitMinutes, summary.totalExtraMinutes, summary.totalUnlockedForToday, text)
                        } else {
                            text.noLimit
                        }}",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    when {
                        !summary.dailyPolicyEnabled -> text.scheduleInactiveNow
                        overMinutes > 0 -> "+${formatLimitMinutesLabel(overMinutes)} over limit"
                        else -> text.limitStatus(summary.totalStatus)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (overMinutes > 0) AppOver else summary.totalStatus.semanticColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (summary.actualTotalUsedMinutes != summary.totalUsedMinutes) {
                    val excludedMinutes =
                        (summary.actualTotalUsedMinutes - summary.totalUsedMinutes).coerceAtLeast(0)
                    Text(
                        if (text.appLanguage == AppLanguage.Korean) {
                            "오늘 전체 사용 ${formatLimitMinutesLabel(summary.actualTotalUsedMinutes)} · 제한 미적용 ${formatLimitMinutesLabel(excludedMinutes)}"
                        } else {
                            "All usage today ${formatLimitMinutesLabel(summary.actualTotalUsedMinutes)} · excluded ${formatLimitMinutesLabel(excludedMinutes)}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DotMetric(AppWarn, "${summary.warningCount} Warning")
                    DotMetric(AppOver, "${summary.exceededCount} Exceeded")
                }
            }
        }
    }
}

@Composable
fun UsageProgressRing(
    usedMinutes: Int,
    limitMinutes: Int,
    status: LimitStatus,
) {
    val rawProgress = if (limitMinutes <= 0) {
        if (status == LimitStatus.Exceeded) 1f else 0f
    } else {
        usedMinutes.toFloat() / limitMinutes.toFloat()
    }
    val animatedProgress by animateFloatAsState(
        targetValue = rawProgress.coerceIn(0f, 1.5f),
        animationSpec = tween(durationMillis = 600),
        label = "usage-ring",
    )
    Box(modifier = Modifier.size(92.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(92.dp)) {
            val strokeWidth = 8.dp.toPx()
            val arcSize = size.minDimension - strokeWidth
            val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
            drawArc(
                color = Color(0xFFE5E7EB),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = Size(arcSize, arcSize),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            drawArc(
                color = status.semanticColor(),
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress.coerceAtMost(1f),
                useCenter = false,
                topLeft = topLeft,
                size = Size(arcSize, arcSize),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("USED", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${(rawProgress * 100).toInt()}%",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
fun DotMetric(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Surface(modifier = Modifier.size(9.dp), shape = CircleShape, color = color) {}
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun StatusBadge(label: String, status: LimitStatus) {
    val color = when (status) {
        LimitStatus.Normal -> AppSafe.copy(alpha = 0.18f)
        LimitStatus.Warning -> AppWarn.copy(alpha = 0.18f)
        LimitStatus.Exceeded -> AppOver.copy(alpha = 0.18f)
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color,
        border = BorderStroke(1.dp, status.semanticColor().copy(alpha = 0.22f)),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = status.semanticColor(),
        )
    }
}

@Composable
fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.64f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.64f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SectionTitle(label: String, modifier: Modifier = Modifier) {
    Text(
        text = label,
        modifier = modifier,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
fun ProgressLine(
    label: String,
    usedMinutes: Int,
    limitMinutes: Int,
    status: LimitStatus,
    text: AppStrings,
    extraMinutes: Int = 0,
    unlockedForToday: Boolean = false,
    limitEnabled: Boolean = true,
    limitTextOverride: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "${formatLimitMinutesLabel(usedMinutes)} / ${limitTextOverride ?: if (limitEnabled) {
                    formatLimitWithAllowance(limitMinutes, extraMinutes, unlockedForToday, text)
                } else {
                    text.noLimit
                }}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (limitEnabled) {
            ProgressOnlyBar(
                usedMinutes = usedMinutes,
                limitMinutes = limitMinutes,
                extraMinutes = extraMinutes,
                unlockedForToday = unlockedForToday,
                status = status,
            )
        }
    }
}

fun formatLimitWithAllowance(
    limitMinutes: Int,
    extraMinutes: Int,
    unlockedForToday: Boolean,
    text: AppStrings,
): String {
    return when {
        unlockedForToday -> text.unlockedToday
        limitMinutes <= 0 -> text.zeroMinuteBlockLabel()
        extraMinutes > 0 -> "${formatLimitMinutesLabel(limitMinutes)}+${formatLimitMinutesLabel(extraMinutes)}"
        else -> formatLimitMinutesLabel(limitMinutes)
    }
}

fun formatLimitWithTemporaryAllowance(
    limitMinutes: Int,
    extraMinutes: Int,
    unlockedForToday: Boolean,
    temporaryRemainingMinutes: Int,
    text: AppStrings,
): String {
    return when {
        unlockedForToday -> text.unlockedToday
        temporaryRemainingMinutes > 0 ->
            "${formatLimitMinutesLabel(temporaryRemainingMinutes)} ${text.temporaryAllowances}"
        else -> formatLimitWithAllowance(limitMinutes, extraMinutes, false, text)
    }
}

private val GaugeTrackColor = Color(0xFFE6EAF2)

@Composable
fun GaugeBar(
    fraction: Float,
    status: LimitStatus,
    height: Int,
    modifier: Modifier = Modifier,
) {
    val safeFraction = if (fraction.isFinite()) fraction.coerceIn(0f, 1f) else 0f
    val barShape = RoundedCornerShape(999.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(barShape)
            .background(GaugeTrackColor),
    ) {
        if (safeFraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(safeFraction)
                    .clip(barShape)
                    .background(status.semanticColor()),
            )
        }
    }
}

fun LimitStatus.semanticColor(): Color {
    return when (this) {
        LimitStatus.Normal -> AppSafe
        LimitStatus.Warning -> AppWarn
        LimitStatus.Exceeded -> AppOver
    }
}
