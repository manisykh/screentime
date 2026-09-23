package com.manisykh.screenrest.data

import java.time.LocalDateTime
import java.time.ZoneId

fun UsagePolicySettings.scheduleDaySet(): Set<Int> {
    return scheduleDays.toPolicyDaySet()
}

fun String.toPolicyDaySet(): Set<Int> {
    val parsedDays = this
        .split(',')
        .mapNotNull { value -> value.trim().toIntOrNull() }
        .filter { day -> day in 1..7 }
        .toSet()
    return parsedDays.ifEmpty { (1..7).toSet() }
}

fun Set<Int>.toScheduleDaysEncoded(): String {
    return filter { day -> day in 1..7 }
        .distinct()
        .sorted()
        .joinToString(",")
        .ifBlank { "1,2,3,4,5,6,7" }
}

fun UsagePolicySettings.normalizedScheduleTemplates(): List<ScheduleTemplatePolicy> {
    val hasItemLevels = scheduleTemplates
        .split(GROUP_SEPARATOR)
        .filter { encoded -> encoded.isNotBlank() }
        .all { encoded -> encoded.split(GROUP_FIELD_SEPARATOR).size >= 7 }
    val hasItemEnabledState = scheduleTemplates
        .split(GROUP_SEPARATOR)
        .filter { encoded -> encoded.isNotBlank() }
        .all { encoded -> encoded.split(GROUP_FIELD_SEPARATOR).size >= 9 }
    return scheduleTemplates.toScheduleTemplatePolicies()
        .map { template ->
            template.copy(
                hardshipLevel = if (hasItemLevels) template.hardshipLevel else scheduleHardshipLevel,
                // Legacy schedules were controlled by one section switch. Carry
                // that effective state into every item exactly once when decoded.
                enabled = if (hasItemEnabledState) template.enabled else scheduleBlockingEnabled,
            )
        }
        .distinctBy { template -> template.id.ifBlank { template.name } }
        .take(MAX_SCHEDULE_TEMPLATES)
}

fun String.toScheduleTemplatePolicies(): List<ScheduleTemplatePolicy> {
    return split(GROUP_SEPARATOR)
        .mapNotNull { encodedTemplate ->
            val parts = encodedTemplate.split(GROUP_FIELD_SEPARATOR)
            if (parts.size !in 5..9) {
                null
            } else {
                val id = parts[0].decodePolicyField()
                val name = parts[1].decodePolicyField()
                val startMinutes = parts[2].toIntOrNull()?.coerceIn(0, SCHEDULE_MINUTES_PER_DAY - 1) ?: 0
                val endMinutes = parts[3].toIntOrNull()?.coerceIn(0, SCHEDULE_MINUTES_PER_DAY - 1) ?: 0
                val days = parts[4].decodePolicyField()
                    .split(',')
                    .mapNotNull { value -> value.trim().toIntOrNull() }
                    .filter { day -> day in 1..7 }
                    .toSet()
                    .ifEmpty { (1..7).toSet() }
                val allowedPackageNames = parts
                    .getOrNull(5)
                    ?.decodePolicyField()
                    ?.toPackageSet()
                    .orEmpty()
                val hardshipLevel = HardshipLevel.fromStorageValue(parts.getOrNull(6)?.toIntOrNull())
                val hardshipEndAtMillis = parts.getOrNull(7)
                    ?.toLongOrNull()
                    ?.coerceAtLeast(0L)
                    ?: 0L
                val enabled = parts.getOrNull(8)?.toBooleanStrictOrNull() ?: true
                ScheduleTemplatePolicy(
                    id = id,
                    name = name.ifBlank { "Schedule" },
                    startMinutes = startMinutes,
                    endMinutes = endMinutes,
                    days = days,
                    allowedPackageNames = allowedPackageNames,
                    hardshipLevel = hardshipLevel,
                    hardshipEndAtMillis = hardshipEndAtMillis,
                    enabled = enabled,
                )
            }
        }
}

fun List<ScheduleTemplatePolicy>.toScheduleTemplatesEncoded(): String {
    return take(MAX_SCHEDULE_TEMPLATES)
        .mapIndexed { index, template ->
            listOf(
                template.id.ifBlank { "schedule-$index" }.encodePolicyField(),
                template.name.ifBlank { "Schedule ${index + 1}" }.encodePolicyField(),
                template.startMinutes.coerceIn(0, SCHEDULE_MINUTES_PER_DAY - 1).toString(),
                template.endMinutes.coerceIn(0, SCHEDULE_MINUTES_PER_DAY - 1).toString(),
                template.days.toScheduleDaysEncoded().encodePolicyField(),
                template.allowedPackageNames
                    .filter { packageName -> packageName.isNotBlank() }
                    .sorted()
                    .joinToString(",")
                    .encodePolicyField(),
                template.hardshipLevel.storageValue.toString(),
                template.hardshipEndAtMillis.coerceAtLeast(0L).toString(),
                template.enabled.toString(),
            ).joinToString(GROUP_FIELD_SEPARATOR)
        }
        .joinToString(GROUP_SEPARATOR)
}

fun UsagePolicySettings.selectedScheduleTemplate(): ScheduleTemplatePolicy? {
    val templates = normalizedScheduleTemplates()
    val byActiveId = templates.firstOrNull { template ->
        activeScheduleTemplateId.isNotBlank() && template.id == activeScheduleTemplateId
    }
    if (byActiveId != null) {
        return byActiveId
    }
    val activeDays = scheduleDaySet()
    return templates.firstOrNull { template ->
        template.startMinutes == scheduleStartMinutes.coerceIn(0, SCHEDULE_MINUTES_PER_DAY - 1) &&
            template.endMinutes == scheduleEndMinutes.coerceIn(0, SCHEDULE_MINUTES_PER_DAY - 1) &&
            template.days == activeDays
    } ?: templates.firstOrNull()
}

fun UsagePolicySettings.activeScheduleTemplate(now: LocalDateTime = LocalDateTime.now()): ScheduleTemplatePolicy? {
    if (!scheduleBlockingEnabled) return null
    return normalizedScheduleTemplates().firstOrNull { template -> template.enabled && template.isActiveAt(now) }
}

fun UsagePolicySettings.activeScheduleTemplates(now: LocalDateTime = LocalDateTime.now()): List<ScheduleTemplatePolicy> {
    if (!scheduleBlockingEnabled) return emptyList()
    return normalizedScheduleTemplates().filter { template -> template.enabled && template.isActiveAt(now) }
}

fun UsagePolicySettings.activeScheduleAllowedPackages(): Set<String> {
    if (!scheduleBlockingEnabled || !isScheduleBlockingNow()) {
        return emptySet()
    }
    val activeTemplates = activeScheduleTemplates()
    if (activeTemplates.isEmpty()) return emptySet()
    return activeTemplates
        .map { template -> template.allowedPackageNames.filter { packageName -> packageName.isNotBlank() }.toSet() }
        .reduce { allowedByAll, next -> allowedByAll intersect next }
}

fun UsagePolicySettings.isScheduleBlockingNow(now: LocalDateTime = LocalDateTime.now()): Boolean {
    if (!scheduleBlockingEnabled) {
        return false
    }
    return normalizedScheduleTemplates().any { template -> template.enabled && template.isActiveAt(now) }
}

fun List<ScheduleTemplatePolicy>.overlappingSchedulePairs(): List<Pair<String, String>> {
    val enabledSchedules = filter { schedule -> schedule.enabled }
    if (enabledSchedules.size < 2) return emptyList()
    val occupiedMinutesBySchedule = enabledSchedules.associateWith { schedule -> schedule.occupiedWeekMinutes() }
    return enabledSchedules.indices.flatMap { firstIndex ->
        ((firstIndex + 1) until enabledSchedules.size).mapNotNull { secondIndex ->
            val first = enabledSchedules[firstIndex]
            val second = enabledSchedules[secondIndex]
            if (occupiedMinutesBySchedule.getValue(first)
                    .any(occupiedMinutesBySchedule.getValue(second)::contains)
            ) {
                (first.name.ifBlank { "Schedule ${firstIndex + 1}" }) to
                    (second.name.ifBlank { "Schedule ${secondIndex + 1}" })
            } else {
                null
            }
        }
    }
}

fun UsagePolicySettings.duplicateAppGroupPackages(): Set<String> {
    return normalizedAppGroups()
        .flatMap { group -> group.packageNames }
        .groupingBy { packageName -> packageName }
        .eachCount()
        .filterValues { count -> count > 1 }
        .keys
}

fun UsagePolicySettings.hasStructuralPolicyConflict(): Boolean {
    return duplicateAppGroupPackages().isNotEmpty() ||
        normalizedScheduleTemplates().overlappingSchedulePairs().isNotEmpty()
}

private fun ScheduleTemplatePolicy.occupiedWeekMinutes(): Set<Int> {
    val minutesPerDay = 24 * 60
    val minutesPerWeek = 7 * minutesPerDay
    val start = startMinutes.coerceIn(0, minutesPerDay - 1)
    val end = endMinutes.coerceIn(0, minutesPerDay - 1)
    // Equal times are treated as a disabled/empty schedule, matching isActiveAt.
    if (start == end) return emptySet()
    val duration = (end - start).takeIf { difference -> difference > 0 }
        ?: (minutesPerDay - start + end)
    val activeDays = days
        .filter { day -> day in 1..7 }
        .ifEmpty { (1..7).toList() }
    return activeDays
        .flatMap { day ->
            val startOfOccurrence = (day - 1) * minutesPerDay + start
            (0 until duration).map { offset -> (startOfOccurrence + offset) % minutesPerWeek }
        }
        .toSet()
}

fun ScheduleTemplatePolicy.isActiveAt(now: LocalDateTime): Boolean {
    if (!enabled) return false
    val startMinutes = startMinutes.coerceIn(0, 24 * 60 - 1)
    val endMinutes = endMinutes.coerceIn(0, 24 * 60 - 1)
    if (startMinutes == endMinutes) {
        return false
    }
    val minuteOfDay = now.hour * 60 + now.minute
    val today = now.dayOfWeek.value
    val yesterday = if (today == 1) 7 else today - 1
    val activeDays = days.filter { day -> day in 1..7 }.toSet().ifEmpty { (1..7).toSet() }
    return if (startMinutes < endMinutes) {
        today in activeDays && minuteOfDay >= startMinutes && minuteOfDay < endMinutes
    } else {
        (today in activeDays && minuteOfDay >= startMinutes) ||
            (yesterday in activeDays && minuteOfDay < endMinutes)
    }
}

fun ScheduleTemplatePolicy.currentOccurrenceEndMillis(now: LocalDateTime = LocalDateTime.now()): Long {
    if (!isActiveAt(now)) return 0L
    val safeStart = startMinutes.coerceIn(0, 24 * 60 - 1)
    val safeEnd = endMinutes.coerceIn(0, 24 * 60 - 1)
    val minuteOfDay = now.hour * 60 + now.minute
    val endDate = when {
        safeStart < safeEnd -> now.toLocalDate()
        minuteOfDay >= safeStart -> now.toLocalDate().plusDays(1)
        else -> now.toLocalDate()
    }
    return endDate
        .atTime(safeEnd / 60, safeEnd % 60)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
}

fun ScheduleTemplatePolicy.nextOccurrenceEndMillis(
    now: LocalDateTime = LocalDateTime.now(),
): Long {
    val safeStart = startMinutes.coerceIn(0, 24 * 60 - 1)
    val safeEnd = endMinutes.coerceIn(0, 24 * 60 - 1)
    if (safeStart == safeEnd) return 0L
    currentOccurrenceEndMillis(now).takeIf { endAt -> endAt > 0L }?.let { return it }

    val activeDays = days.filter { day -> day in 1..7 }.toSet().ifEmpty { (1..7).toSet() }
    for (offset in 0..7) {
        val startDate = now.toLocalDate().plusDays(offset.toLong())
        if (startDate.dayOfWeek.value !in activeDays) continue
        val startAt = startDate.atTime(safeStart / 60, safeStart % 60)
        if (!startAt.isAfter(now)) continue
        val endDate = if (safeStart < safeEnd) startDate else startDate.plusDays(1)
        return endDate
            .atTime(safeEnd / 60, safeEnd % 60)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }
    return 0L
}

fun ScheduleTemplatePolicy.nextOccurrenceStartMillis(
    now: LocalDateTime = LocalDateTime.now(),
): Long {
    val safeStart = startMinutes.coerceIn(0, 24 * 60 - 1)
    val safeEnd = endMinutes.coerceIn(0, 24 * 60 - 1)
    if (safeStart == safeEnd || isActiveAt(now)) return 0L
    val activeDays = days.filter { day -> day in 1..7 }.toSet().ifEmpty { (1..7).toSet() }
    for (offset in 0..7) {
        val startDate = now.toLocalDate().plusDays(offset.toLong())
        if (startDate.dayOfWeek.value !in activeDays) continue
        val startAt = startDate.atTime(safeStart / 60, safeStart % 60)
        if (!startAt.isAfter(now)) continue
        return startAt
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }
    return 0L
}

fun ScheduleTemplatePolicy.withHardshipLevelForNextOccurrence(
    level: HardshipLevel,
    now: LocalDateTime = LocalDateTime.now(),
): ScheduleTemplatePolicy {
    val next = copy(
        hardshipLevel = level,
        enabled = enabled || level != HardshipLevel.Off,
    )
    return next.copy(
        hardshipEndAtMillis = if (level == HardshipLevel.Off) {
            0L
        } else {
            next.nextOccurrenceEndMillis(now)
        },
    )
}

fun UsagePolicySettings.emergencyPassExpiryFor(
    policyKey: HardshipPolicyKey,
    nowMillis: Long = System.currentTimeMillis(),
): Long {
    return when (policyKey.policyType) {
        HardshipPolicyType.Schedule -> normalizedScheduleTemplates()
            .firstOrNull { schedule -> schedule.id == policyKey.targetId }
            ?.currentOccurrenceEndMillis(LocalDateTime.now())
            ?.takeIf { expiry -> expiry > nowMillis }
            ?: nextLocalMidnightMillis()
        HardshipPolicyType.AllowOnly -> allowOnlyHardshipEndAtMillis
            .takeIf { expiry -> expiry > nowMillis }
            ?: nextLocalMidnightMillis()
        else -> nextLocalMidnightMillis()
    }
}

fun UsagePolicySettings.normalizedAppGroups(): List<AppGroupPolicy> {
    if (appGroups == EMPTY_APP_GROUPS_ENCODED) {
        return emptyList()
    }
    val hasItemLevels = appGroups
        .split(GROUP_SEPARATOR)
        .filter { encoded -> encoded.isNotBlank() }
        .all { encoded -> encoded.split(GROUP_FIELD_SEPARATOR).size >= 5 }
    val parsedGroups = appGroups.toAppGroupPolicies()
    if (parsedGroups.isNotEmpty()) {
        return parsedGroups.map { group ->
            if (hasItemLevels) group else group.copy(hardshipLevel = appGroupsHardshipLevel)
        }
    }
    return listOf(
        AppGroupPolicy(
            name = appGroupName.ifBlank { "Group" },
            packageNames = appGroupPackages.toPackageSet(),
            budgetMinutes = appGroupBudgetMinutes.coerceAtLeast(EXPLICIT_ZERO_LIMIT_STORAGE_MINUTES),
            id = "legacy-primary",
            hardshipLevel = appGroupsHardshipLevel,
        ),
    )
}

fun String.toAppGroupPolicies(): List<AppGroupPolicy> {
    if (this == EMPTY_APP_GROUPS_ENCODED) {
        return emptyList()
    }
    return split(GROUP_SEPARATOR)
        .mapNotNull { encodedGroup ->
            val parts = encodedGroup.split(GROUP_FIELD_SEPARATOR)
            when (parts.size) {
                3 -> {
                    val name = parts[0].decodePolicyField()
                    val budgetMinutes = parts[1].toIntOrNull()
                        ?.coerceAtLeast(EXPLICIT_ZERO_LIMIT_STORAGE_MINUTES)
                        ?: 0
                    val packageNames = parts[2].decodePolicyField().toPackageSet()
                    AppGroupPolicy(name, packageNames, budgetMinutes)
                }

                4 -> {
                    val id = parts[0].decodePolicyField()
                    val name = parts[1].decodePolicyField()
                    val budgetMinutes = parts[2].toIntOrNull()
                        ?.coerceAtLeast(EXPLICIT_ZERO_LIMIT_STORAGE_MINUTES)
                        ?: 0
                    val packageNames = parts[3].decodePolicyField().toPackageSet()
                    AppGroupPolicy(name, packageNames, budgetMinutes, id)
                }

                5 -> {
                    val id = parts[0].decodePolicyField()
                    val name = parts[1].decodePolicyField()
                    val budgetMinutes = parts[2].toIntOrNull()
                        ?.coerceAtLeast(EXPLICIT_ZERO_LIMIT_STORAGE_MINUTES)
                        ?: 0
                    val packageNames = parts[3].decodePolicyField().toPackageSet()
                    val hardshipLevel = HardshipLevel.fromStorageValue(parts[4].toIntOrNull())
                    AppGroupPolicy(name, packageNames, budgetMinutes, id, hardshipLevel)
                }

                6 -> {
                    val id = parts[0].decodePolicyField()
                    val name = parts[1].decodePolicyField()
                    val budgetMinutes = parts[2].toIntOrNull()
                        ?.coerceAtLeast(EXPLICIT_ZERO_LIMIT_STORAGE_MINUTES)
                        ?: 0
                    val packageNames = parts[3].decodePolicyField().toPackageSet()
                    val hardshipLevel = HardshipLevel.fromStorageValue(parts[4].toIntOrNull())
                    val activeDays = parts[5]
                        .decodePolicyField()
                        .toPolicyDaySet()
                    AppGroupPolicy(name, packageNames, budgetMinutes, id, hardshipLevel, activeDays)
                }

                7 -> {
                    val id = parts[0].decodePolicyField()
                    val name = parts[1].decodePolicyField()
                    val budgetMinutes = parts[2].toIntOrNull()
                        ?.coerceAtLeast(EXPLICIT_ZERO_LIMIT_STORAGE_MINUTES)
                        ?: 0
                    val packageNames = parts[3].decodePolicyField().toPackageSet()
                    val hardshipLevel = HardshipLevel.fromStorageValue(parts[4].toIntOrNull())
                    val activeDays = parts[5]
                        .decodePolicyField()
                        .toPolicyDaySet()
                    val enabled = parts[6].toBooleanStrictOrNull() ?: true
                    AppGroupPolicy(
                        name = name,
                        packageNames = packageNames,
                        budgetMinutes = budgetMinutes,
                        id = id,
                        hardshipLevel = hardshipLevel,
                        activeDays = activeDays,
                        enabled = enabled,
                    )
                }

                else -> null
            }
        }
}

fun List<AppGroupPolicy>.toAppGroupsEncoded(): String {
    if (isEmpty()) {
        return EMPTY_APP_GROUPS_ENCODED
    }
    return mapIndexed { index, group ->
            listOf(
                group.id.ifBlank { "legacy-$index" }.encodePolicyField(),
                group.name.encodePolicyField(),
                group.budgetMinutes.coerceAtLeast(EXPLICIT_ZERO_LIMIT_STORAGE_MINUTES).toString(),
                group.packageNames.sorted().joinToString(",").encodePolicyField(),
                group.hardshipLevel.storageValue.toString(),
                group.activeDays.normalizedPolicyDays().toScheduleDaysEncoded().encodePolicyField(),
                group.enabled.toString(),
            ).joinToString(GROUP_FIELD_SEPARATOR)
        }.joinToString(GROUP_SEPARATOR)
}

fun UsagePolicySettings.appLimitHardshipLevelMap(): Map<String, HardshipLevel> {
    val cleanPackages = appLimitRules
        .split('|')
        .mapNotNull { rule -> rule.substringBefore('=').trim().takeIf(String::isNotBlank) }
        .toSet()
    if (!appLimitHardshipLevels.startsWith(APP_LIMIT_HARDSHIP_LEVELS_V2_PREFIX)) {
        return cleanPackages.associateWith { appLimitsHardshipLevel }
    }
    val parsed = appLimitHardshipLevels
        .removePrefix(APP_LIMIT_HARDSHIP_LEVELS_V2_PREFIX)
        .split(GROUP_SEPARATOR)
        .mapNotNull { encoded ->
            val parts = encoded.split(GROUP_FIELD_SEPARATOR)
            val packageName = parts.getOrNull(0)?.decodePolicyField().orEmpty()
            val level = HardshipLevel.fromStorageValue(parts.getOrNull(1)?.toIntOrNull())
            packageName.takeIf { value -> value.isNotBlank() }?.let { value -> value to level }
        }
        .toMap()
    return cleanPackages.associateWith { packageName -> parsed[packageName] ?: HardshipLevel.Off }
}

fun UsagePolicySettings.appLimitActiveDayMap(): Map<String, Set<Int>> {
    val cleanPackages = appLimitRules
        .split('|')
        .mapNotNull { rule -> rule.substringBefore('=').trim().takeIf(String::isNotBlank) }
        .toSet()
    val parsed = if (appLimitActiveDays.startsWith(APP_LIMIT_ACTIVE_DAYS_V1_PREFIX)) {
        appLimitActiveDays
            .removePrefix(APP_LIMIT_ACTIVE_DAYS_V1_PREFIX)
            .split(GROUP_SEPARATOR)
            .mapNotNull { encoded ->
                val parts = encoded.split(GROUP_FIELD_SEPARATOR)
                val packageName = parts.getOrNull(0)?.decodePolicyField().orEmpty()
                val days = parts.getOrNull(1)?.decodePolicyField()?.toPolicyDaySet()
                if (packageName.isBlank() || days == null) null else packageName to days
            }
            .toMap()
    } else {
        emptyMap()
    }
    return cleanPackages.associateWith { packageName ->
        parsed[packageName]?.normalizedPolicyDays() ?: (1..7).toSet()
    }
}

fun Map<String, Set<Int>>.toAppLimitActiveDaysEncoded(): String {
    val body = entries
        .filter { (packageName, _) -> packageName.isNotBlank() }
        .sortedBy { (packageName, _) -> packageName }
        .joinToString(GROUP_SEPARATOR) { (packageName, days) ->
            listOf(
                packageName.encodePolicyField(),
                days.normalizedPolicyDays().toScheduleDaysEncoded().encodePolicyField(),
            ).joinToString(GROUP_FIELD_SEPARATOR)
        }
    return APP_LIMIT_ACTIVE_DAYS_V1_PREFIX + body
}

fun Map<String, HardshipLevel>.toAppLimitHardshipLevelsEncoded(): String {
    val body = entries
        .filter { (packageName, _) -> packageName.isNotBlank() }
        .sortedBy { (packageName, _) -> packageName }
        .joinToString(GROUP_SEPARATOR) { (packageName, level) ->
            listOf(
                packageName.encodePolicyField(),
                level.storageValue.toString(),
            ).joinToString(GROUP_FIELD_SEPARATOR)
        }
    return APP_LIMIT_HARDSHIP_LEVELS_V2_PREFIX + body
}

internal fun Iterable<HardshipLevel>.maxHardshipLevel(fallback: HardshipLevel): HardshipLevel {
    return maxByOrNull { level -> level.storageValue } ?: fallback
}

internal fun List<AppGroupPolicy>.maxAppGroupHardshipLevel(fallback: HardshipLevel): HardshipLevel =
    map { group -> group.hardshipLevel }.maxHardshipLevel(fallback)

internal fun List<ScheduleTemplatePolicy>.maxScheduleHardshipLevel(fallback: HardshipLevel): HardshipLevel =
    map { schedule -> schedule.hardshipLevel }.maxHardshipLevel(fallback)
