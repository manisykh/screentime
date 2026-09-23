package com.manisykh.screenrest.ui.safety

import com.manisykh.screenrest.data.*
import com.manisykh.screenrest.safety.SafetyGate
import java.util.Calendar

private const val POLICY_MAX_MINUTES = 720
private const val DAILY_POLICY_MAX_MINUTES = 24 * 60 - 1
private const val POLICY_MAX_SCHEDULE_MINUTES = 24 * 60 - 5

fun UsagePolicySettings.selectedPackageSet(): Set<String> {
    return normalizedAppGroups()
        .flatMap { group -> group.packageNames }
        .toSet()
}

fun UsagePolicySettings.appLimitMap(): Map<String, Int> {
    return appLimitRules
        .split('|')
        .mapNotNull { rule ->
            val parts = rule.split('=')
            if (parts.size != 2) {
                null
            } else {
                val packageName = parts[0].trim()
                val minutes = parts[1].trim().toIntOrNull()
                if (packageName.isBlank() || minutes == null || minutes < 0) {
                    null
                } else {
                    packageName to minutes.coerceIn(0, POLICY_MAX_MINUTES)
                }
            }
        }
        .toMap()
}

fun UsagePolicySettings.activeAppLimitMap(
    dayOfWeek: Int = currentPolicyDayOfWeek(),
): Map<String, Int> {
    val activeDaysByPackage = appLimitActiveDayMap()
    return appLimitMap().filterKeys { packageName ->
        dayOfWeek in (activeDaysByPackage[packageName] ?: (1..7).toSet())
    }
}

fun UsagePolicySettings.dailyLimitMinutesByDay(): List<Int> {
    return listOf(
        mondayLimitMinutes,
        tuesdayLimitMinutes,
        wednesdayLimitMinutes,
        thursdayLimitMinutes,
        fridayLimitMinutes,
        saturdayLimitMinutes,
        sundayLimitMinutes,
    ).map { minutes -> minutes.coerceIn(EXPLICIT_ZERO_LIMIT_STORAGE_MINUTES, DAILY_POLICY_MAX_MINUTES) }
}

fun UsagePolicySettings.dailyLimitMinutesByDayOrNull(): List<Int?> {
    return dailyLimitMinutesByDay().map(::decodeOptionalLimitMinutes)
}

fun UsagePolicySettings.normalizedForDraft(): UsagePolicySettings {
    val cleanAppLimits = appLimitMap()
        .filter { (packageName, minutes) -> packageName.isNotBlank() && minutes >= 0 }
        .mapValues { (_, minutes) -> minutes.coerceIn(0, POLICY_MAX_MINUTES) }
    val assignedGroupPackages = mutableSetOf<String>()
    val cleanGroups = normalizedAppGroups()
        .mapIndexed { index, group ->
            group.copy(
                name = group.name,
                packageNames = group.packageNames
                    .filter { packageName ->
                        packageName.isNotBlank() && assignedGroupPackages.add(packageName)
                    }
                    .toSet(),
                budgetMinutes = group.budgetMinutes.coerceIn(
                    EXPLICIT_ZERO_LIMIT_STORAGE_MINUTES,
                    POLICY_MAX_MINUTES,
                ),
                activeDays = group.activeDays.normalizedPolicyDays(),
                id = group.id.ifBlank { "legacy-$index" },
            )
        }
    val dailyLimits = dailyLimitMinutesByDay()
        .map { minutes -> minutes.coerceIn(EXPLICIT_ZERO_LIMIT_STORAGE_MINUTES, DAILY_POLICY_MAX_MINUTES) }
    val cleanScheduleTemplates = normalizedScheduleTemplates()
    val cleanAppLimitHardshipLevels = appLimitHardshipLevelMap()
        .filterKeys { packageName -> packageName in cleanAppLimits }
        .let { levels ->
            cleanAppLimits.keys.associateWith { packageName ->
                levels[packageName] ?: HardshipLevel.Off
            }
        }
    val cleanAppLimitActiveDays = appLimitActiveDayMap()
        .filterKeys { packageName -> packageName in cleanAppLimits }
    val cleanActiveScheduleTemplateId = activeScheduleTemplateId
        .takeIf { id -> cleanScheduleTemplates.any { template -> template.id == id } }
        .orEmpty()
    val primaryGroup = cleanGroups.firstOrNull()
    return copy(
        weekdayLimitMinutes = dailyLimits[0],
        weekendLimitMinutes = dailyLimits[5],
        mondayLimitMinutes = dailyLimits[0],
        tuesdayLimitMinutes = dailyLimits[1],
        wednesdayLimitMinutes = dailyLimits[2],
        thursdayLimitMinutes = dailyLimits[3],
        fridayLimitMinutes = dailyLimits[4],
        saturdayLimitMinutes = dailyLimits[5],
        sundayLimitMinutes = dailyLimits[6],
        appGroupName = primaryGroup?.name.orEmpty(),
        appGroupPackages = primaryGroup?.packageNames.orEmpty().sorted().joinToString(","),
        appGroupBudgetMinutes = primaryGroup?.budgetMinutes ?: 0,
        appGroups = cleanGroups.toAppGroupsEncoded(),
        appLimitRules = cleanAppLimits.toAppLimitRules(),
        appLimitActiveDays = cleanAppLimitActiveDays.toAppLimitActiveDaysEncoded(),
        appLimitHardshipLevels = cleanAppLimitHardshipLevels.toAppLimitHardshipLevelsEncoded(),
        scheduleStartMinutes = scheduleStartMinutes.coerceIn(0, POLICY_MAX_SCHEDULE_MINUTES),
        scheduleEndMinutes = scheduleEndMinutes.coerceIn(0, POLICY_MAX_SCHEDULE_MINUTES),
        scheduleDays = scheduleDaySet().toScheduleDaysEncoded(),
        scheduleTemplates = cleanScheduleTemplates.toScheduleTemplatesEncoded(),
        scheduleBlockingEnabled = cleanScheduleTemplates.any { template -> template.enabled },
        activeScheduleTemplateId = cleanActiveScheduleTemplateId,
    )
}

fun UsagePolicySettings.policyBudgetValidation(
    allRestrictionsExemptPackages: Set<String> = emptySet(),
): PolicyBudgetValidation {
    val appLimits = appLimitMap()
    val appLimitTotal = appLimits.values.sumOf { minutes -> minutes.coerceAtLeast(0) }
    val groups = normalizedAppGroups()
    val groupBudgetTotal = groups.mapNotNull { group -> group.limitMinutesOrNull() }.sum()
    val duplicateGroupPackageNames = duplicateAppGroupPackages()
    val overlappingSchedulePairs = normalizedScheduleTemplates().overlappingSchedulePairs()
    val expandedExemptPackages =
        SafetyGate.expandedUserAllowedPackages(allRestrictionsExemptPackages)
    val exemptAppLimitPackages = appLimits.keys intersect expandedExemptPackages
    val exemptGroupPackages = groups
        .flatMap { group -> group.packageNames }
        .toSet() intersect expandedExemptPackages
    return PolicyBudgetValidation(
        hasOverflow = duplicateGroupPackageNames.isNotEmpty() ||
            overlappingSchedulePairs.isNotEmpty(),
        overflowingDayIndexes = emptyList(),
        appLimitTotalMinutes = appLimitTotal,
        groupBudgetTotalMinutes = groupBudgetTotal,
        appGroupLimitConflictCount = 0,
        duplicateGroupPackageNames = duplicateGroupPackageNames,
        overlappingSchedulePairs = overlappingSchedulePairs,
        exemptAppLimitPackages = exemptAppLimitPackages,
        exemptGroupPackages = exemptGroupPackages,
    )
}

fun Map<String, Int>.toAppLimitRules(): String {
    return entries
        .filter { (_, minutes) -> minutes >= 0 }
        .sortedBy { (packageName, _) -> packageName }
        .joinToString("|") { (packageName, minutes) -> "$packageName=$minutes" }
}

fun UsagePolicySettings.todayLimitMinutes(): Int {
    return todayLimitMinutesOrNull() ?: 0
}

fun UsagePolicySettings.todayLimitMinutesOrNull(): Int? {
    if (!dailyPolicyEnabled) return null

    val rawLimit = when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> mondayLimitMinutes
        Calendar.TUESDAY -> tuesdayLimitMinutes
        Calendar.WEDNESDAY -> wednesdayLimitMinutes
        Calendar.THURSDAY -> thursdayLimitMinutes
        Calendar.FRIDAY -> fridayLimitMinutes
        Calendar.SATURDAY -> saturdayLimitMinutes
        Calendar.SUNDAY -> sundayLimitMinutes
        else -> weekdayLimitMinutes
    }
    return decodeOptionalLimitMinutes(
        rawLimit.coerceIn(EXPLICIT_ZERO_LIMIT_STORAGE_MINUTES, DAILY_POLICY_MAX_MINUTES),
    )
}

