package com.manisykh.screenrest.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.manisykh.screenrest.safety.SafetyGate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

const val MAX_TEMPORARY_EXTRA_MINUTES = 12 * 60

private fun ImmediateBlockState.toStorageValue(): String = listOf(
    requestId, childDeviceId, requestedAtMillis.toString(),
    (expiresAtMillis ?: 0L).toString(), revokedAtMillis.toString(),
    parentUid, appliedAtMillis.toString(), releasedAtMillis.toString(),
).joinToString("|")

private fun String.toImmediateBlockState(): ImmediateBlockState {
    val parts = split('|')
    if (parts.size != 8) return ImmediateBlockState()
    val requested = parts[2].toLongOrNull() ?: return ImmediateBlockState()
    val expires = parts[3].toLongOrNull() ?: return ImmediateBlockState()
    if (parts[0].isBlank() || parts[1].isBlank() || parts[5].isBlank() ||
        requested <= 0L || expires <= requested || expires - requested > 86_400_000L
    ) return ImmediateBlockState()
    return ImmediateBlockState(
        requestId = parts[0], childDeviceId = parts[1], requestedAtMillis = requested,
        expiresAtMillis = expires, revokedAtMillis = parts[4].toLongOrNull() ?: 0L,
        parentUid = parts[5], appliedAtMillis = parts[6].toLongOrNull() ?: 0L,
        releasedAtMillis = parts[7].toLongOrNull() ?: 0L,
    )
}
private const val MAX_PARENT_NOTIFICATION_EVENT_TOKENS = 240
private const val MAX_PARENT_NOTIFICATION_ERROR_LENGTH = 180
private const val HARDSHIP_CONFIGURATION_REFLECTION_PREFIX = "Config:"

fun hardshipConfigurationWaitMillis(level: HardshipLevel): Long = when (level) {
    HardshipLevel.Level1 -> 2L * 60L * 1000L
    HardshipLevel.Level2 -> 30L * 60L * 1000L
    else -> 0L
}

data class EventLogEntry(
    val timestampMillis: Long,
    val type: EventLogType,
    val message: String,
)

data class ForegroundDetectionStatus(
    val timestampMillis: Long,
    val appName: String,
    val packageName: String,
    val decision: String,
)

data class UsageMonitorStatus(
    val running: Boolean = false,
    val lastTickMillis: Long = 0L,
    val lastForegroundAppName: String = "",
    val lastForegroundPackageName: String = "",
    val lastDecision: String = "",
    val lastUsedMillis: Long = 0L,
    val lastLimitMillis: Long = 0L,
    val lastLimitedTarget: Boolean = false,
    val lastBlockReason: String = "",
    val lastOverlayAttached: Boolean = false,
    val lastBlockAttemptMillis: Long = 0L,
    val blockRetryCount: Int = 0,
    val lastRecoveryMillis: Long = 0L,
    val lastRecoveryReason: String = "",
    val lastStopReason: String = "",
)

data class SystemHealthStatus(
    val lastCheckedMillis: Long = 0L,
    val usageAccessReady: Boolean = false,
    val overlayPermissionReady: Boolean = false,
    val notificationPermissionReady: Boolean = false,
    val notificationAccessReady: Boolean = false,
    val exactAlarmReady: Boolean = false,
    val foregroundServiceExpected: Boolean = false,
    val foregroundServiceRunning: Boolean = false,
    val foregroundServiceFresh: Boolean = false,
    val lastIssue: String = "",
) {
    val allReady: Boolean
        get() = usageAccessReady &&
            overlayPermissionReady &&
            notificationPermissionReady &&
            (!foregroundServiceExpected || (foregroundServiceRunning && foregroundServiceFresh))
}

data class CachedTodayUsageSnapshot(
    val dateKey: String,
    val savedAtMillis: Long,
    val apps: List<CachedTodayUsageEntry>,
)

data class CachedTodayUsageEntry(
    val appName: String,
    val packageName: String,
    val totalTimeMillis: Long,
)

enum class EventLogType {
    Info,
    Warning,
    Exceeded,
    Safety,
}

private const val EVENT_LOG_TYPE_INFO = "Info"
private const val EVENT_LOG_TYPE_WARNING = "Warning"
private const val EVENT_LOG_TYPE_EXCEEDED = "Exceeded"
private const val EVENT_LOG_TYPE_SAFETY = "Safety"

private fun EventLogType.toStorageValue(): String {
    return when (this) {
        EventLogType.Info -> EVENT_LOG_TYPE_INFO
        EventLogType.Warning -> EVENT_LOG_TYPE_WARNING
        EventLogType.Exceeded -> EVENT_LOG_TYPE_EXCEEDED
        EventLogType.Safety -> EVENT_LOG_TYPE_SAFETY
    }
}

private fun String.toEventLogTypeOrNull(): EventLogType? {
    return when (this) {
        EVENT_LOG_TYPE_INFO -> EventLogType.Info
        EVENT_LOG_TYPE_WARNING -> EventLogType.Warning
        EVENT_LOG_TYPE_EXCEEDED -> EventLogType.Exceeded
        EVENT_LOG_TYPE_SAFETY -> EventLogType.Safety
        else -> null
    }
}

data class UsagePolicySettings(
    val weekdayLimitMinutes: Int = 120,
    val weekendLimitMinutes: Int = 240,
    val mondayLimitMinutes: Int = 120,
    val tuesdayLimitMinutes: Int = 120,
    val wednesdayLimitMinutes: Int = 120,
    val thursdayLimitMinutes: Int = 120,
    val fridayLimitMinutes: Int = 120,
    val saturdayLimitMinutes: Int = 240,
    val sundayLimitMinutes: Int = 240,
    val dailyPolicyEnabled: Boolean = true,
    val appGroupName: String = "SNS",
    val appGroupPackages: String = "com.google.android.youtube",
    val appGroupBudgetMinutes: Int = 60,
    val appGroups: String = "",
    val appLimitRules: String = "",
    val appLimitActiveDays: String = "",
    val appLimitHardshipLevels: String = "",
    val scheduleBlockingEnabled: Boolean = false,
    val scheduleStartMinutes: Int = 22 * 60,
    val scheduleEndMinutes: Int = 7 * 60,
    val scheduleDays: String = "1,2,3,4,5,6,7",
    val scheduleTemplates: String = "",
    val activeScheduleTemplateId: String = "",
    val allowOnlyModeEnabled: Boolean = false,
    val dailyHardshipLevel: HardshipLevel = HardshipLevel.Off,
    val appGroupsHardshipLevel: HardshipLevel = HardshipLevel.Off,
    val appLimitsHardshipLevel: HardshipLevel = HardshipLevel.Off,
    val scheduleHardshipLevel: HardshipLevel = HardshipLevel.Off,
    val allowOnlyHardshipLevel: HardshipLevel = HardshipLevel.Off,
    val allowOnlyHardshipEndAtMillis: Long = 0L,
)

enum class HardshipLevel(val storageValue: Int) {
    Off(0),
    Level1(1),
    Level2(2),
    Level3(3),
    ;

    companion object {
        fun fromStorageValue(value: Int?): HardshipLevel {
            return entries.firstOrNull { level -> level.storageValue == value } ?: Off
        }
    }
}

enum class HardshipPolicyType {
    DailyLimit,
    AppGroups,
    AppLimits,
    Schedule,
    AllowOnly,
}

data class HardshipPolicyKey(
    val policyType: HardshipPolicyType,
    val targetId: String = "",
) {
    val storageKey: String
        get() = "${policyType.name}:${targetId}"

    companion object {
        fun fromStorageKey(value: String): HardshipPolicyKey? {
            val separatorIndex = value.indexOf(':')
            val typeName = if (separatorIndex >= 0) value.substring(0, separatorIndex) else value
            val targetId = if (separatorIndex >= 0) value.substring(separatorIndex + 1) else ""
            val policyType = HardshipPolicyType.entries.firstOrNull { type -> type.name == typeName }
                ?: return null
            return HardshipPolicyKey(policyType = policyType, targetId = targetId)
        }
    }
}

fun dailyHardshipKey() = HardshipPolicyKey(HardshipPolicyType.DailyLimit)
fun appGroupHardshipKey(groupId: String) = HardshipPolicyKey(HardshipPolicyType.AppGroups, groupId)
fun appLimitHardshipKey(packageName: String) = HardshipPolicyKey(HardshipPolicyType.AppLimits, packageName)
fun scheduleHardshipKey(scheduleId: String) = HardshipPolicyKey(HardshipPolicyType.Schedule, scheduleId)
fun allowOnlyHardshipKey() = HardshipPolicyKey(HardshipPolicyType.AllowOnly)

fun UsagePolicySettings.hardshipLevelFor(policyType: HardshipPolicyType): HardshipLevel {
    return when (policyType) {
        HardshipPolicyType.DailyLimit -> dailyHardshipLevel
        HardshipPolicyType.AppGroups -> normalizedAppGroups().maxAppGroupHardshipLevel(appGroupsHardshipLevel)
        HardshipPolicyType.AppLimits -> appLimitHardshipLevelMap().values.maxHardshipLevel(appLimitsHardshipLevel)
        HardshipPolicyType.Schedule -> normalizedScheduleTemplates().maxScheduleHardshipLevel(scheduleHardshipLevel)
        HardshipPolicyType.AllowOnly -> allowOnlyHardshipLevel
    }
}

fun UsagePolicySettings.hardshipLevelFor(key: HardshipPolicyKey): HardshipLevel {
    return when (key.policyType) {
        HardshipPolicyType.DailyLimit -> dailyHardshipLevel
        HardshipPolicyType.AppGroups -> normalizedAppGroups()
            .firstOrNull { group -> group.id == key.targetId }
            ?.hardshipLevel
            ?: HardshipLevel.Off
        HardshipPolicyType.AppLimits -> appLimitHardshipLevelMap()[key.targetId] ?: HardshipLevel.Off
        HardshipPolicyType.Schedule -> normalizedScheduleTemplates()
            .firstOrNull { schedule -> schedule.id == key.targetId }
            ?.hardshipLevel
            ?: HardshipLevel.Off
        HardshipPolicyType.AllowOnly -> allowOnlyHardshipLevel
    }
}

fun UsagePolicySettings.withHardshipLevel(
    policyType: HardshipPolicyType,
    level: HardshipLevel,
): UsagePolicySettings {
    return when (policyType) {
        HardshipPolicyType.DailyLimit -> copy(
            dailyPolicyEnabled = dailyPolicyEnabled || level != HardshipLevel.Off,
            dailyHardshipLevel = level,
        )
        HardshipPolicyType.AppGroups -> copy(appGroupsHardshipLevel = level)
        HardshipPolicyType.AppLimits -> copy(appLimitsHardshipLevel = level)
        HardshipPolicyType.Schedule -> copy(
            scheduleBlockingEnabled = scheduleBlockingEnabled || level != HardshipLevel.Off,
            scheduleHardshipLevel = level,
            scheduleTemplates = normalizedScheduleTemplates().map { schedule ->
                schedule.withHardshipLevelForNextOccurrence(level)
            }.toScheduleTemplatesEncoded(),
        )
        HardshipPolicyType.AllowOnly -> copy(
            allowOnlyModeEnabled = allowOnlyModeEnabled || level != HardshipLevel.Off,
            allowOnlyHardshipLevel = level,
            allowOnlyHardshipEndAtMillis = if (level == HardshipLevel.Level3) {
                nextLocalMidnightMillis()
            } else {
                0L
            },
        )
    }
}

fun UsagePolicySettings.withHardshipLevel(
    policyKey: HardshipPolicyKey,
    level: HardshipLevel,
): UsagePolicySettings {
    return when (policyKey.policyType) {
        HardshipPolicyType.DailyLimit -> copy(
            dailyPolicyEnabled = dailyPolicyEnabled || level != HardshipLevel.Off,
            dailyHardshipLevel = level,
        )
        HardshipPolicyType.AppGroups -> copy(
            appGroups = normalizedAppGroups().map { group ->
                if (group.id == policyKey.targetId) {
                    group.copy(
                        hardshipLevel = level,
                        enabled = group.enabled || level != HardshipLevel.Off,
                    )
                } else {
                    group
                }
            }.toAppGroupsEncoded(),
        )
        HardshipPolicyType.AppLimits -> copy(
            appLimitHardshipLevels = (appLimitHardshipLevelMap() + (policyKey.targetId to level))
                .toAppLimitHardshipLevelsEncoded(),
        )
        HardshipPolicyType.Schedule -> copy(
            scheduleBlockingEnabled = scheduleBlockingEnabled || level != HardshipLevel.Off,
            scheduleTemplates = normalizedScheduleTemplates().map { schedule ->
                if (schedule.id == policyKey.targetId) {
                    schedule.withHardshipLevelForNextOccurrence(level).copy(
                        enabled = schedule.enabled || level != HardshipLevel.Off,
                    )
                } else {
                    schedule
                }
            }.toScheduleTemplatesEncoded(),
        )
        HardshipPolicyType.AllowOnly -> copy(
            allowOnlyModeEnabled = allowOnlyModeEnabled || level != HardshipLevel.Off,
            allowOnlyHardshipLevel = level,
            allowOnlyHardshipEndAtMillis = if (level == HardshipLevel.Level3) {
                nextLocalMidnightMillis()
            } else {
                0L
            },
        )
    }
}

fun UsagePolicySettings.level3HardshipPolicyKeys(): Set<HardshipPolicyKey> = buildSet {
    if (dailyHardshipLevel == HardshipLevel.Level3) add(dailyHardshipKey())
    normalizedAppGroups()
        .filter { group -> group.hardshipLevel == HardshipLevel.Level3 }
        .forEach { group -> add(appGroupHardshipKey(group.id)) }
    appLimitHardshipLevelMap()
        .filterValues { level -> level == HardshipLevel.Level3 }
        .keys
        .forEach { packageName -> add(appLimitHardshipKey(packageName)) }
    normalizedScheduleTemplates()
        .filter { schedule -> schedule.hardshipLevel == HardshipLevel.Level3 }
        .forEach { schedule -> add(scheduleHardshipKey(schedule.id)) }
    if (allowOnlyHardshipLevel == HardshipLevel.Level3) add(allowOnlyHardshipKey())
}

fun UsagePolicySettings.configuredHardshipPolicyKeys(): Set<HardshipPolicyKey> = buildSet {
    if (dailyHardshipLevel != HardshipLevel.Off) add(dailyHardshipKey())
    normalizedAppGroups()
        .filter { group -> group.hardshipLevel != HardshipLevel.Off }
        .forEach { group -> add(appGroupHardshipKey(group.id)) }
    appLimitHardshipLevelMap()
        .filterValues { level -> level != HardshipLevel.Off }
        .keys
        .forEach { packageName -> add(appLimitHardshipKey(packageName)) }
    normalizedScheduleTemplates()
        .filter { schedule -> schedule.hardshipLevel != HardshipLevel.Off }
        .forEach { schedule -> add(scheduleHardshipKey(schedule.id)) }
    if (allowOnlyHardshipLevel != HardshipLevel.Off) add(allowOnlyHardshipKey())
}

fun UsagePolicySettings.hasDisabledPolicyWithHardship(): Boolean {
    return (!dailyPolicyEnabled && dailyHardshipLevel != HardshipLevel.Off) ||
        normalizedAppGroups().any { group ->
            !group.enabled && group.hardshipLevel != HardshipLevel.Off
        } ||
        (!scheduleBlockingEnabled && normalizedScheduleTemplates().any { schedule ->
            schedule.hardshipLevel != HardshipLevel.Off
        }) ||
        normalizedScheduleTemplates().any { schedule ->
            !schedule.enabled && schedule.hardshipLevel != HardshipLevel.Off
        } ||
        (!allowOnlyModeEnabled && allowOnlyHardshipLevel != HardshipLevel.Off)
}

private fun hardshipWeakeningKeys(
    current: UsagePolicySettings,
    requested: UsagePolicySettings,
): Set<HardshipPolicyKey> = current.configuredHardshipPolicyKeys()
    .filterTo(mutableSetOf()) { policyKey ->
        val currentLevel = current.hardshipLevelFor(policyKey)
        if (currentLevel !in setOf(HardshipLevel.Level1, HardshipLevel.Level2)) {
            return@filterTo false
        }
        val requestedLevel = requested.hardshipLevelFor(policyKey)
        if (requestedLevel.storageValue < currentLevel.storageValue) {
            return@filterTo true
        }
        when (policyKey.policyType) {
            HardshipPolicyType.DailyLimit -> {
                val currentLimits = listOf(
                    current.mondayLimitMinutes,
                    current.tuesdayLimitMinutes,
                    current.wednesdayLimitMinutes,
                    current.thursdayLimitMinutes,
                    current.fridayLimitMinutes,
                    current.saturdayLimitMinutes,
                    current.sundayLimitMinutes,
                ).map(::decodeOptionalLimitMinutes)
                val requestedLimits = listOf(
                    requested.mondayLimitMinutes,
                    requested.tuesdayLimitMinutes,
                    requested.wednesdayLimitMinutes,
                    requested.thursdayLimitMinutes,
                    requested.fridayLimitMinutes,
                    requested.saturdayLimitMinutes,
                    requested.sundayLimitMinutes,
                ).map(::decodeOptionalLimitMinutes)
                (current.dailyPolicyEnabled && !requested.dailyPolicyEnabled) ||
                    currentLimits.zip(requestedLimits).any { (before, after) ->
                        optionalLimitIsWeaker(before, after)
                    }
            }

            HardshipPolicyType.AppGroups -> {
                val before = current.normalizedAppGroups().firstOrNull { group -> group.id == policyKey.targetId }
                val after = requested.normalizedAppGroups().firstOrNull { group -> group.id == policyKey.targetId }
                before != null && (
                    after == null ||
                        (before.enabled && !after.enabled) ||
                        optionalLimitIsWeaker(before.limitMinutesOrNull(), after.limitMinutesOrNull()) ||
                        (before.packageNames - after.packageNames).isNotEmpty() ||
                        (before.activeDays.normalizedPolicyDays() - after.activeDays.normalizedPolicyDays()).isNotEmpty()
                    )
            }

            HardshipPolicyType.AppLimits -> {
                val beforeLimit = current.appLimitMinutesFor(policyKey.targetId)
                val afterLimit = requested.appLimitMinutesFor(policyKey.targetId)
                val beforeDays = current.appLimitActiveDayMap()[policyKey.targetId]?.normalizedPolicyDays() ?: (1..7).toSet()
                val afterDays = requested.appLimitActiveDayMap()[policyKey.targetId]?.normalizedPolicyDays() ?: (1..7).toSet()
                optionalLimitIsWeaker(beforeLimit, afterLimit) || (beforeDays - afterDays).isNotEmpty()
            }

            HardshipPolicyType.Schedule -> {
                val before = current.normalizedScheduleTemplates().firstOrNull { schedule -> schedule.id == policyKey.targetId }
                val after = requested.normalizedScheduleTemplates().firstOrNull { schedule -> schedule.id == policyKey.targetId }
                before != null && (
                    after == null ||
                        (before.enabled && !after.enabled) ||
                        (current.scheduleBlockingEnabled && !requested.scheduleBlockingEnabled) ||
                        (after.allowedPackageNames - before.allowedPackageNames).isNotEmpty() ||
                        (before.days.normalizedPolicyDays() - after.days.normalizedPolicyDays()).isNotEmpty() ||
                        after.startMinutes != before.startMinutes ||
                        after.endMinutes != before.endMinutes
                    )
            }

            HardshipPolicyType.AllowOnly ->
                current.allowOnlyModeEnabled && !requested.allowOnlyModeEnabled
        }
    }

private fun optionalLimitIsWeaker(before: Int?, after: Int?): Boolean =
    before != null && (after == null || after > before)

fun nextLocalMidnightMillis(): Long {
    return LocalDate.now()
        .plusDays(1)
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
}

private fun currentLocalDayStartMillis(): Long {
    return LocalDate.now()
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
}

data class TemporaryPackageAllowance(
    val extraMinutes: Int = 0,
    val unlockedForToday: Boolean = false,
    val temporaryAllowedUntilMillis: Long = 0L,
    val hardshipAllowanceUntilMillis: Long = 0L,
)

fun TemporaryPackageAllowance.isTemporarilyAllowed(nowMillis: Long = System.currentTimeMillis()): Boolean {
    return temporaryAllowedUntilMillis > nowMillis
}

fun TemporaryPackageAllowance.temporaryRemainingMinutes(nowMillis: Long = System.currentTimeMillis()): Int {
    if (!isTemporarilyAllowed(nowMillis)) {
        return 0
    }
    return ((temporaryAllowedUntilMillis - nowMillis + 59_999L) / 60_000L).toInt().coerceAtLeast(0)
}

fun TemporaryPackageAllowance.withExtraTime(extraMinutes: Int, nowMillis: Long = System.currentTimeMillis()): TemporaryPackageAllowance {
    val safeExtraMinutes = extraMinutes.coerceAtLeast(1)
    val extraUntilMillis = nowMillis + safeExtraMinutes * 60_000L
    return copy(
        extraMinutes = (this.extraMinutes + safeExtraMinutes).coerceAtMost(MAX_TEMPORARY_EXTRA_MINUTES),
        temporaryAllowedUntilMillis = maxOf(temporaryAllowedUntilMillis, extraUntilMillis),
    )
}

data class TemporaryUnlockState(
    val dateKey: String = "",
    val totalExtraMinutes: Int = 0,
    val totalUnlockedForToday: Boolean = false,
    val packageAllowances: Map<String, TemporaryPackageAllowance> = emptyMap(),
) {
    fun forToday(todayKey: String = currentTemporaryUnlockDateKey()): TemporaryUnlockState {
        return if (dateKey == todayKey) {
            this
        } else {
            TemporaryUnlockState(dateKey = todayKey)
        }
    }
}

data class HardshipReflectionEntry(
    val readyAtMillis: Long = 0L,
    val useCount: Int = 0,
)

data class EmergencyPassGrant(
    val packageName: String,
    val policyKey: HardshipPolicyKey,
    val grantedAtMillis: Long,
    val expiresAtMillis: Long,
)

data class HardshipRuntimeState(
    val dateKey: String = "",
    val reflections: Map<String, HardshipReflectionEntry> = emptyMap(),
    val bypassedPolicies: Set<HardshipPolicyType> = emptySet(),
    val activePolicies: Set<HardshipPolicyType> = emptySet(),
    val bypassedPolicyKeys: Set<HardshipPolicyKey> = emptySet(),
    val activePolicyKeys: Set<HardshipPolicyKey> = emptySet(),
    val emergencyPassGrants: List<EmergencyPassGrant> = emptyList(),
    val lastEmergencyPassUsedAtMillis: Long = 0L,
) {
    fun forToday(
        todayKey: String = currentTemporaryUnlockDateKey(),
        nowMillis: Long = System.currentTimeMillis(),
    ): HardshipRuntimeState {
        val activeGrants = emergencyPassGrants.filter { grant -> grant.expiresAtMillis > nowMillis }
        if (dateKey == todayKey) return copy(emergencyPassGrants = activeGrants)

        // A schedule may legitimately cross midnight. Its runtime lock must survive the
        // calendar rollover and is cleared when that schedule occurrence actually ends.
        val scheduleActiveKeys = activePolicyKeys.filterTo(mutableSetOf()) { key ->
            key.policyType == HardshipPolicyType.Schedule
        }
        val scheduleBypassedKeys = bypassedPolicyKeys.filterTo(mutableSetOf()) { key ->
            key.policyType == HardshipPolicyType.Schedule
        }
        val scheduleReflectionPrefixes = (scheduleActiveKeys + scheduleBypassedKeys)
            .map { key -> "${key.storageKey}:" }
        return copy(
            dateKey = todayKey,
            reflections = reflections.filterKeys { reflectionKey ->
                reflectionKey.startsWith(HARDSHIP_CONFIGURATION_REFLECTION_PREFIX) ||
                    scheduleReflectionPrefixes.any(reflectionKey::startsWith)
            },
            bypassedPolicies = bypassedPolicies.filterTo(mutableSetOf()) { policyType ->
                policyType == HardshipPolicyType.Schedule
            },
            activePolicies = buildSet {
                if (
                    HardshipPolicyType.Schedule in activePolicies ||
                    scheduleActiveKeys.isNotEmpty()
                ) add(HardshipPolicyType.Schedule)
            },
            bypassedPolicyKeys = scheduleBypassedKeys,
            activePolicyKeys = scheduleActiveKeys,
            emergencyPassGrants = activeGrants,
        )
    }

    fun bypassedKeysForPackage(
        packageName: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): Set<HardshipPolicyKey> {
        return bypassedPolicyKeys + emergencyPassGrants
            .filter { grant -> grant.packageName == packageName && grant.expiresAtMillis > nowMillis }
            .map { grant -> grant.policyKey }
    }

    fun emergencyPassNextAvailableAtMillis(cooldownMillis: Long = 7L * 24L * 60L * 60L * 1000L): Long {
        return lastEmergencyPassUsedAtMillis.takeIf { usedAt -> usedAt > 0L }
            ?.plus(cooldownMillis)
            ?: 0L
    }
}

fun HardshipRuntimeState.levelOneReflectionEntry(
    policyKey: HardshipPolicyKey,
    packageName: String,
): HardshipReflectionEntry? = reflections[hardshipReflectionKey(policyKey, packageName)]

fun HardshipRuntimeState.configurationReflectionReadyAtMillis(policyKey: HardshipPolicyKey): Long =
    reflections[hardshipConfigurationReflectionKey(policyKey)]?.readyAtMillis ?: 0L

enum class HardshipLevelOneGrantResult {
    Granted,
    WaitingStarted,
    Waiting,
    DailyLimitReached,
    NotAvailable,
}

enum class HardshipLevelTwoUnlockResult {
    Unlocked,
    WaitingStarted,
    Waiting,
    InvalidPin,
    NotAvailable,
}

enum class PolicyConfigurationSaveResult {
    Saved,
    InvalidAdminPin,
    StructuralConflict,
    HardshipLocked,
    HardshipReflectionRequired,
    HardshipReflectionWaiting,
}

enum class EmergencyPassUseResult {
    Used,
    InvalidPin,
    CooldownActive,
    NotAvailable,
}

data class ParentManagementState(
    val paired: Boolean = false,
    val parentAccountId: String = "",
    val childDeviceId: String = "",
    val childDeviceName: String = "",
    val localProfileName: String = "",
    val lastSyncMillis: Long = 0L,
    val remoteCommands: List<RemoteParentCommand> = emptyList(),
    val remoteUnlockRequests: List<RemoteUnlockRequest> = emptyList(),
    val deviceRole: ParentDeviceRole = ParentDeviceRole.Child,
    val pairingCode: String = "",
    val linkedChildPairingCodes: Set<String> = emptySet(),
    val linkedChildDevices: List<LinkedChildDevice> = emptyList(),
    val linkedParentDevices: List<LinkedParentDevice> = emptyList(),
)

data class ParentNotificationState(
    val handledEventTokens: Set<String> = emptySet(),
    val activePendingRequestIds: Set<String> = emptySet(),
    val lastAttemptMillis: Long = 0L,
    val lastSuccessMillis: Long = 0L,
    val lastError: String = "",
)

fun ParentManagementState.canRequestParentApproval(): Boolean {
    return deviceRole == ParentDeviceRole.Child &&
        paired &&
        (parentAccountId.isNotBlank() || linkedParentDevices.isNotEmpty())
}

enum class ParentDeviceRole {
    Child,
    Parent,
}

private const val PARENT_DEVICE_ROLE_CHILD = "Child"
private const val PARENT_DEVICE_ROLE_PARENT = "Parent"

private fun ParentDeviceRole.toStorageValue(): String {
    return when (this) {
        ParentDeviceRole.Parent -> PARENT_DEVICE_ROLE_PARENT
        ParentDeviceRole.Child -> PARENT_DEVICE_ROLE_CHILD
    }
}

private fun String.toParentDeviceRole(): ParentDeviceRole {
    return when (this) {
        PARENT_DEVICE_ROLE_PARENT -> ParentDeviceRole.Parent
        else -> ParentDeviceRole.Child
    }
}

data class LinkedChildDevice(
    val childDeviceId: String,
    val childDeviceName: String,
    val pairingCode: String = "",
    val linkedAtMillis: Long = 0L,
)

data class LinkedParentDevice(
    val parentUid: String,
    val parentDisplayName: String,
    val linkedAtMillis: Long = 0L,
)

data class RemoteParentCommand(
    val id: String,
    val timestampMillis: Long,
    val type: RemoteParentCommandType,
    val targetPackageName: String = "",
    val targetAppName: String = "",
    val minutes: Int = 0,
    val status: RemoteParentCommandStatus = RemoteParentCommandStatus.Pending,
    val message: String = "",
)

enum class RemoteParentCommandType {
    AddAppTime,
    UnlockAppToday,
    AddTotalTime,
    UnlockTotalToday,
}

private const val REMOTE_PARENT_COMMAND_TYPE_ADD_APP_TIME = "AddAppTime"
private const val REMOTE_PARENT_COMMAND_TYPE_UNLOCK_APP_TODAY = "UnlockAppToday"
private const val REMOTE_PARENT_COMMAND_TYPE_ADD_TOTAL_TIME = "AddTotalTime"
private const val REMOTE_PARENT_COMMAND_TYPE_UNLOCK_TOTAL_TODAY = "UnlockTotalToday"

private fun RemoteParentCommandType.toStorageValue(): String {
    return when (this) {
        RemoteParentCommandType.AddAppTime -> REMOTE_PARENT_COMMAND_TYPE_ADD_APP_TIME
        RemoteParentCommandType.UnlockAppToday -> REMOTE_PARENT_COMMAND_TYPE_UNLOCK_APP_TODAY
        RemoteParentCommandType.AddTotalTime -> REMOTE_PARENT_COMMAND_TYPE_ADD_TOTAL_TIME
        RemoteParentCommandType.UnlockTotalToday -> REMOTE_PARENT_COMMAND_TYPE_UNLOCK_TOTAL_TODAY
    }
}

private fun String.toRemoteParentCommandTypeOrNull(): RemoteParentCommandType? {
    return when (this) {
        REMOTE_PARENT_COMMAND_TYPE_ADD_APP_TIME -> RemoteParentCommandType.AddAppTime
        REMOTE_PARENT_COMMAND_TYPE_UNLOCK_APP_TODAY -> RemoteParentCommandType.UnlockAppToday
        REMOTE_PARENT_COMMAND_TYPE_ADD_TOTAL_TIME -> RemoteParentCommandType.AddTotalTime
        REMOTE_PARENT_COMMAND_TYPE_UNLOCK_TOTAL_TODAY -> RemoteParentCommandType.UnlockTotalToday
        else -> null
    }
}

enum class RemoteParentCommandStatus {
    Pending,
    Applied,
    Failed,
}

private const val REMOTE_PARENT_COMMAND_STATUS_PENDING = "Pending"
private const val REMOTE_PARENT_COMMAND_STATUS_APPLIED = "Applied"
private const val REMOTE_PARENT_COMMAND_STATUS_FAILED = "Failed"

private fun RemoteParentCommandStatus.toStorageValue(): String {
    return when (this) {
        RemoteParentCommandStatus.Pending -> REMOTE_PARENT_COMMAND_STATUS_PENDING
        RemoteParentCommandStatus.Applied -> REMOTE_PARENT_COMMAND_STATUS_APPLIED
        RemoteParentCommandStatus.Failed -> REMOTE_PARENT_COMMAND_STATUS_FAILED
    }
}

private fun String.toRemoteParentCommandStatusOrNull(): RemoteParentCommandStatus? {
    return when (this) {
        REMOTE_PARENT_COMMAND_STATUS_PENDING -> RemoteParentCommandStatus.Pending
        REMOTE_PARENT_COMMAND_STATUS_APPLIED -> RemoteParentCommandStatus.Applied
        REMOTE_PARENT_COMMAND_STATUS_FAILED -> RemoteParentCommandStatus.Failed
        else -> null
    }
}

data class RemoteUnlockRequest(
    val id: String,
    val childDeviceId: String,
    val childDeviceName: String,
    val createdAtMillis: Long,
    val expiresAtMillis: Long,
    val blockReason: RemoteRequestBlockReason,
    val targetPackageName: String = "",
    val targetAppName: String = "",
    val targetGroupName: String = "",
    val scheduleName: String = "",
    val usedMillis: Long = 0L,
    val limitMillis: Long? = null,
    val alreadyGrantedExtraMinutes: Int = 0,
    val unlockedForToday: Boolean = false,
    val requestedMinutes: Int = 0,
    val childMessage: String = "",
    val status: RemoteUnlockRequestStatus = RemoteUnlockRequestStatus.Pending,
)

enum class RemoteRequestBlockReason {
    DailyLimit,
    AppGroupLimit,
    AppLimit,
    ScheduleBlock,
    AllowOnlyMode,
}

private const val REMOTE_REQUEST_BLOCK_REASON_DAILY_LIMIT = "DailyLimit"
private const val REMOTE_REQUEST_BLOCK_REASON_APP_GROUP_LIMIT = "AppGroupLimit"
private const val REMOTE_REQUEST_BLOCK_REASON_APP_LIMIT = "AppLimit"
private const val REMOTE_REQUEST_BLOCK_REASON_SCHEDULE_BLOCK = "ScheduleBlock"
private const val REMOTE_REQUEST_BLOCK_REASON_ALLOW_ONLY_MODE = "AllowOnlyMode"

private fun RemoteRequestBlockReason.toStorageValue(): String {
    return when (this) {
        RemoteRequestBlockReason.DailyLimit -> REMOTE_REQUEST_BLOCK_REASON_DAILY_LIMIT
        RemoteRequestBlockReason.AppGroupLimit -> REMOTE_REQUEST_BLOCK_REASON_APP_GROUP_LIMIT
        RemoteRequestBlockReason.AppLimit -> REMOTE_REQUEST_BLOCK_REASON_APP_LIMIT
        RemoteRequestBlockReason.ScheduleBlock -> REMOTE_REQUEST_BLOCK_REASON_SCHEDULE_BLOCK
        RemoteRequestBlockReason.AllowOnlyMode -> REMOTE_REQUEST_BLOCK_REASON_ALLOW_ONLY_MODE
    }
}

private fun String.toRemoteRequestBlockReasonOrNull(): RemoteRequestBlockReason? {
    return when (this) {
        REMOTE_REQUEST_BLOCK_REASON_DAILY_LIMIT -> RemoteRequestBlockReason.DailyLimit
        REMOTE_REQUEST_BLOCK_REASON_APP_GROUP_LIMIT -> RemoteRequestBlockReason.AppGroupLimit
        REMOTE_REQUEST_BLOCK_REASON_APP_LIMIT -> RemoteRequestBlockReason.AppLimit
        REMOTE_REQUEST_BLOCK_REASON_SCHEDULE_BLOCK -> RemoteRequestBlockReason.ScheduleBlock
        REMOTE_REQUEST_BLOCK_REASON_ALLOW_ONLY_MODE -> RemoteRequestBlockReason.AllowOnlyMode
        else -> null
    }
}

enum class RemoteUnlockRequestStatus {
    Pending,
    Approved,
    Rejected,
    Expired,
    Failed,
}

sealed class RemoteUnlockRequestSubmitResult {
    data class Sent(val requestId: String) : RemoteUnlockRequestSubmitResult()
    data class Retrying(val requestId: String) : RemoteUnlockRequestSubmitResult()
    object NotPaired : RemoteUnlockRequestSubmitResult()
    data class Failed(val reason: String = "") : RemoteUnlockRequestSubmitResult()
}

private const val REMOTE_UNLOCK_REQUEST_STATUS_PENDING = "Pending"
private const val REMOTE_UNLOCK_REQUEST_STATUS_APPROVED = "Approved"
private const val REMOTE_UNLOCK_REQUEST_STATUS_REJECTED = "Rejected"
private const val REMOTE_UNLOCK_REQUEST_STATUS_EXPIRED = "Expired"
private const val REMOTE_UNLOCK_REQUEST_STATUS_FAILED = "Failed"

private fun RemoteUnlockRequestStatus.toStorageValue(): String {
    return when (this) {
        RemoteUnlockRequestStatus.Pending -> REMOTE_UNLOCK_REQUEST_STATUS_PENDING
        RemoteUnlockRequestStatus.Approved -> REMOTE_UNLOCK_REQUEST_STATUS_APPROVED
        RemoteUnlockRequestStatus.Rejected -> REMOTE_UNLOCK_REQUEST_STATUS_REJECTED
        RemoteUnlockRequestStatus.Expired -> REMOTE_UNLOCK_REQUEST_STATUS_EXPIRED
        RemoteUnlockRequestStatus.Failed -> REMOTE_UNLOCK_REQUEST_STATUS_FAILED
    }
}

private fun String.toRemoteUnlockRequestStatusOrNull(): RemoteUnlockRequestStatus? {
    return when (this) {
        REMOTE_UNLOCK_REQUEST_STATUS_PENDING -> RemoteUnlockRequestStatus.Pending
        REMOTE_UNLOCK_REQUEST_STATUS_APPROVED -> RemoteUnlockRequestStatus.Approved
        REMOTE_UNLOCK_REQUEST_STATUS_REJECTED -> RemoteUnlockRequestStatus.Rejected
        REMOTE_UNLOCK_REQUEST_STATUS_EXPIRED -> RemoteUnlockRequestStatus.Expired
        REMOTE_UNLOCK_REQUEST_STATUS_FAILED -> RemoteUnlockRequestStatus.Failed
        else -> null
    }
}

data class AppGroupPolicy(
    val name: String,
    val packageNames: Set<String>,
    val budgetMinutes: Int,
    val id: String = "",
    val hardshipLevel: HardshipLevel = HardshipLevel.Off,
    val activeDays: Set<Int> = (1..7).toSet(),
    val enabled: Boolean = true,
)

fun Set<Int>.normalizedPolicyDays(): Set<Int> =
    filter { day -> day in 1..7 }.toSet().ifEmpty { (1..7).toSet() }

fun AppGroupPolicy.appliesOn(dayOfWeek: Int): Boolean =
    enabled && dayOfWeek in activeDays.normalizedPolicyDays()

fun currentPolicyDayOfWeek(): Int = LocalDate.now().dayOfWeek.value

/**
 * Legacy persisted value 0 meant "no limit". A newly selected 00:00 limit is
 * therefore stored as -1 so existing users are never migrated into an immediate
 * block. Public helpers expose that value as an ordinary zero-minute limit.
 */
const val EXPLICIT_ZERO_LIMIT_STORAGE_MINUTES = -1

fun decodeOptionalLimitMinutes(storedMinutes: Int): Int? = when {
    storedMinutes == EXPLICIT_ZERO_LIMIT_STORAGE_MINUTES -> 0
    storedMinutes > 0 -> storedMinutes
    else -> null
}

fun encodeOptionalLimitMinutes(limitMinutes: Int?): Int = when {
    limitMinutes == null -> 0
    limitMinutes <= 0 -> EXPLICIT_ZERO_LIMIT_STORAGE_MINUTES
    else -> limitMinutes
}

fun AppGroupPolicy.limitMinutesOrNull(): Int? = decodeOptionalLimitMinutes(budgetMinutes)

data class ScheduleTemplatePolicy(
    val id: String = "",
    val name: String,
    val startMinutes: Int,
    val endMinutes: Int,
    val days: Set<Int>,
    val allowedPackageNames: Set<String> = emptySet(),
    val hardshipLevel: HardshipLevel = HardshipLevel.Off,
    val hardshipEndAtMillis: Long = 0L,
    val enabled: Boolean = true,
)

data class ScheduleHardshipStarted(
    val scheduleId: String,
    val scheduleName: String,
    val level: HardshipLevel,
    val endsAtMillis: Long,
)

data class HardshipLifecycleResult(
    val startedSchedules: List<ScheduleHardshipStarted> = emptyList(),
    val endedScheduleIds: Set<String> = emptySet(),
    val dailyHardshipCleared: Boolean = false,
)

enum class AppLanguage {
    Korean,
    English,
}

private const val APP_LANGUAGE_KOREAN = "Korean"
private const val APP_LANGUAGE_ENGLISH = "English"

data class PolicySectionExpansionSettings(
    val dailyPolicyExpanded: Boolean = false,
    val appGroupsExpanded: Boolean = false,
    val appLimitsExpanded: Boolean = false,
    val scheduleBlockingExpanded: Boolean = false,
    val allowOnlyModeExpanded: Boolean = false,
    val settingsLanguageExpanded: Boolean = true,
    val settingsNotificationExpanded: Boolean = true,
    val settingsPinExpanded: Boolean = true,
    val settingsParentManagementExpanded: Boolean = true,
    val settingsEventLogExpanded: Boolean = false,
)

class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val parentRemoteSyncDataSource: ParentRemoteSyncDataSource = LocalOnlyParentRemoteSyncDataSource,
) {
    fun observeParentRemoteChanges(
        childDeviceIds: Set<String>,
        deviceRole: ParentDeviceRole,
    ) = parentRemoteSyncDataSource.observeChanges(childDeviceIds, deviceRole)

    suspend fun publishChildUsageSnapshot(snapshot: ChildUsageSnapshot): ParentRemoteSyncResult =
        parentRemoteSyncDataSource.publishChildUsageSnapshot(snapshot)

    suspend fun fetchChildUsageSnapshot(childDeviceId: String): ChildUsageSnapshot? =
        parentRemoteSyncDataSource.fetchChildUsageSnapshot(childDeviceId)

    suspend fun requestChildUsageRefresh(childDeviceId: String): Result<ChildUsageRefreshRequest> =
        parentRemoteSyncDataSource.requestChildUsageRefresh(childDeviceId)

    suspend fun fetchChildUsageRefresh(childDeviceId: String): Result<ChildUsageRefreshRequest?> =
        parentRemoteSyncDataSource.fetchChildUsageRefresh(childDeviceId)

    suspend fun acknowledgeChildUsageRefresh(
        childDeviceId: String,
        requestId: String,
    ): ParentRemoteSyncResult =
        parentRemoteSyncDataSource.acknowledgeChildUsageRefresh(childDeviceId, requestId)

    suspend fun issueImmediateBlock(childDeviceId: String, durationMinutes: Int): ParentRemoteSyncResult {
        val result = parentRemoteSyncDataSource.issueImmediateBlock(childDeviceId, durationMinutes)
        if (result == ParentRemoteSyncResult.Success) {
            addEvent(EventLogType.Safety, "Parent immediate block requested: child=$childDeviceId duration=${durationMinutes}m")
        }
        return result
    }

    suspend fun revokeImmediateBlock(childDeviceId: String, requestId: String): ParentRemoteSyncResult {
        val result = parentRemoteSyncDataSource.revokeImmediateBlock(childDeviceId, requestId)
        if (result == ParentRemoteSyncResult.Success) {
            addEvent(EventLogType.Safety, "Parent immediate block stop requested: child=$childDeviceId")
        }
        return result
    }

    suspend fun fetchImmediateBlock(childDeviceId: String): Result<ImmediateBlockState?> =
        parentRemoteSyncDataSource.fetchImmediateBlock(childDeviceId)

    /** Server-only fetch: an offline child keeps its last verified order until its local expiry. */
    suspend fun syncImmediateBlock(canAcknowledge: Boolean = false): Boolean {
        val parentState = parentManagementState.first()
        if (!parentState.paired || parentState.deviceRole != ParentDeviceRole.Child ||
            parentState.childDeviceId.isBlank()
        ) {
            dataStore.edit { stored -> stored.remove(IMMEDIATE_BLOCK_STATE) }
            return true
        }
        val fetched = parentRemoteSyncDataSource.fetchImmediateBlock(parentState.childDeviceId)
        if (fetched.isFailure) return false
        val remote = fetched.getOrNull()
        val linkedParentIds = parentState.linkedParentDevices.map { it.parentUid }.toSet()
        val valid = remote?.takeIf { order ->
            order.parentUid in linkedParentIds && order.isActiveAt(System.currentTimeMillis())
        }
        dataStore.edit { stored ->
            if (valid == null) stored.remove(IMMEDIATE_BLOCK_STATE)
            else stored[IMMEDIATE_BLOCK_STATE] = valid.toStorageValue()
        }
        if (remote != null && remote.revokedAtMillis > 0L &&
            remote.releasedAtMillis == 0L && remote.parentUid in linkedParentIds
        ) {
            parentRemoteSyncDataSource.acknowledgeImmediateBlockRelease(
                parentState.childDeviceId, remote.requestId,
            )
        }
        if (valid != null && canAcknowledge && valid.appliedAtMillis == 0L &&
            !safeModeEnabled.first() && policyEnforcementEnabled.first()
        ) {
            parentRemoteSyncDataSource.acknowledgeImmediateBlock(
                parentState.childDeviceId, valid.requestId,
            )
        }
        return true
    }

    private val preferences: Flow<Preferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }

    val immediateBlockState: Flow<ImmediateBlockState> = preferences.map { stored ->
        stored[IMMEDIATE_BLOCK_STATE].orEmpty().toImmediateBlockState()
    }

    val safeModeEnabled: Flow<Boolean> = preferences
        .map { preferences ->
            preferences[SAFE_MODE_ENABLED] ?: true
        }

    val policyEnforcementEnabled: Flow<Boolean> = preferences
        .map { preferences ->
            preferences[POLICY_ENFORCEMENT_ENABLED] ?: false
        }

    val warningNotificationsEnabled: Flow<Boolean> = preferences
        .map { preferences ->
            preferences[WARNING_NOTIFICATIONS_ENABLED] ?: true
        }

    val limitNotificationsEnabled: Flow<Boolean> = preferences
        .map { preferences ->
            preferences[LIMIT_NOTIFICATIONS_ENABLED] ?: true
        }

    val parentNotificationState: Flow<ParentNotificationState> = preferences
        .map { preferences ->
            preferences[PARENT_NOTIFICATION_STATE].orEmpty().toParentNotificationState()
        }

    val permissionSetupCompletedOnce: Flow<Boolean> = preferences
        .map { preferences ->
            preferences[PERMISSION_SETUP_COMPLETED_ONCE] ?: false
        }

    val monitoringDisclosureAccepted: Flow<Boolean> = preferences
        .map { preferences ->
            (preferences[MONITORING_DISCLOSURE_ACCEPTED_VERSION] ?: 0) >=
                MONITORING_DISCLOSURE_CURRENT_VERSION
        }

    /** Separate opt-in for sending app names and per-app durations to linked parents. */
    val childTopAppsSharingEnabled: Flow<Boolean> = preferences
        .map { preferences -> preferences[CHILD_TOP_APPS_SHARING_ENABLED] ?: false }

    val securityPinsConfigured: Flow<Boolean> = preferences
        .map { preferences ->
            preferences.hasConfiguredPin(ADMIN_PIN_CREDENTIAL, ADMIN_PIN)
        }

    val policySectionExpansionSettings: Flow<PolicySectionExpansionSettings> = preferences
        .map { preferences ->
            PolicySectionExpansionSettings(
                dailyPolicyExpanded = preferences[DAILY_POLICY_EXPANDED] ?: false,
                appGroupsExpanded = preferences[APP_GROUPS_EXPANDED] ?: false,
                appLimitsExpanded = preferences[APP_LIMITS_EXPANDED] ?: false,
                scheduleBlockingExpanded = preferences[SCHEDULE_BLOCKING_EXPANDED] ?: false,
                allowOnlyModeExpanded = preferences[ALLOW_ONLY_MODE_EXPANDED] ?: false,
                settingsLanguageExpanded = preferences[SETTINGS_LANGUAGE_EXPANDED] ?: true,
                settingsNotificationExpanded = preferences[SETTINGS_NOTIFICATION_EXPANDED] ?: true,
                settingsPinExpanded = preferences[SETTINGS_PIN_EXPANDED] ?: true,
                settingsParentManagementExpanded = preferences[SETTINGS_PARENT_MANAGEMENT_EXPANDED] ?: true,
                settingsEventLogExpanded = preferences[SETTINGS_EVENT_LOG_EXPANDED] ?: false,
            )
        }

    val appLanguage: Flow<AppLanguage> = preferences
        .map { preferences ->
            val savedLanguage = preferences[APP_LANGUAGE].orEmpty()
            when (savedLanguage) {
                APP_LANGUAGE_ENGLISH -> AppLanguage.English
                else -> AppLanguage.Korean
            }
        }

    val usagePolicySettings: Flow<UsagePolicySettings> = preferences
        .map { preferences ->
            val allowOnlyHardshipLevel = HardshipLevel.fromStorageValue(preferences[ALLOW_ONLY_HARDSHIP_LEVEL])
            val allowOnlyHardshipEndAtMillis = preferences[ALLOW_ONLY_HARDSHIP_END_AT_MILLIS] ?: 0L
            val allowOnlyModeEnabled = preferences[ALLOW_ONLY_MODE_ENABLED] ?: false
            UsagePolicySettings(
                weekdayLimitMinutes = preferences[WEEKDAY_LIMIT_MINUTES] ?: 120,
                weekendLimitMinutes = preferences[WEEKEND_LIMIT_MINUTES] ?: 240,
                mondayLimitMinutes = preferences[MONDAY_LIMIT_MINUTES] ?: 120,
                tuesdayLimitMinutes = preferences[TUESDAY_LIMIT_MINUTES] ?: 120,
                wednesdayLimitMinutes = preferences[WEDNESDAY_LIMIT_MINUTES] ?: 120,
                thursdayLimitMinutes = preferences[THURSDAY_LIMIT_MINUTES] ?: 120,
                fridayLimitMinutes = preferences[FRIDAY_LIMIT_MINUTES] ?: 120,
                saturdayLimitMinutes = preferences[SATURDAY_LIMIT_MINUTES] ?: 240,
                sundayLimitMinutes = preferences[SUNDAY_LIMIT_MINUTES] ?: 240,
                dailyPolicyEnabled = preferences.dailyPolicyEnabledOrMigrated(),
                appGroupName = preferences[APP_GROUP_NAME] ?: "SNS",
                appGroupPackages = preferences[APP_GROUP_PACKAGES] ?: "com.google.android.youtube",
                appGroupBudgetMinutes = preferences[APP_GROUP_BUDGET_MINUTES] ?: 60,
                appGroups = preferences[APP_GROUPS].orEmpty(),
                appLimitRules = preferences[APP_LIMIT_RULES] ?: "",
                appLimitActiveDays = preferences[APP_LIMIT_ACTIVE_DAYS].orEmpty(),
                appLimitHardshipLevels = preferences[APP_LIMIT_HARDSHIP_LEVELS].orEmpty(),
                scheduleBlockingEnabled = preferences[SCHEDULE_BLOCKING_ENABLED] ?: false,
                scheduleStartMinutes = preferences[SCHEDULE_START_MINUTES] ?: 22 * 60,
                scheduleEndMinutes = preferences[SCHEDULE_END_MINUTES] ?: 7 * 60,
                scheduleDays = preferences[SCHEDULE_DAYS] ?: "1,2,3,4,5,6,7",
                scheduleTemplates = preferences[SCHEDULE_TEMPLATES].orEmpty(),
                activeScheduleTemplateId = preferences[ACTIVE_SCHEDULE_TEMPLATE_ID].orEmpty(),
                allowOnlyModeEnabled = allowOnlyModeEnabled && !(
                    allowOnlyHardshipLevel == HardshipLevel.Level3 &&
                        allowOnlyHardshipEndAtMillis > 0L &&
                        System.currentTimeMillis() >= allowOnlyHardshipEndAtMillis
                    ),
                dailyHardshipLevel = HardshipLevel.fromStorageValue(preferences[DAILY_HARDSHIP_LEVEL]),
                appGroupsHardshipLevel = HardshipLevel.fromStorageValue(preferences[APP_GROUPS_HARDSHIP_LEVEL]),
                appLimitsHardshipLevel = HardshipLevel.fromStorageValue(preferences[APP_LIMITS_HARDSHIP_LEVEL]),
                scheduleHardshipLevel = HardshipLevel.fromStorageValue(preferences[SCHEDULE_HARDSHIP_LEVEL]),
                allowOnlyHardshipLevel = allowOnlyHardshipLevel,
                allowOnlyHardshipEndAtMillis = allowOnlyHardshipEndAtMillis,
            )
        }

    val eventLog: Flow<List<EventLogEntry>> = preferences
        .map { preferences ->
            preferences[EVENT_LOG]
                ?.split(EVENT_SEPARATOR)
                ?.mapNotNull { encodedEntry -> encodedEntry.toEventLogEntryOrNull() }
                .orEmpty()
        }

    val foregroundDetectionStatus: Flow<ForegroundDetectionStatus?> = preferences
        .map { preferences ->
            preferences[FOREGROUND_DETECTION_STATUS]?.toForegroundDetectionStatusOrNull()
        }

    val usageMonitorStatus: Flow<UsageMonitorStatus> = preferences
        .map { preferences ->
            preferences[USAGE_MONITOR_STATUS]?.toUsageMonitorStatusOrNull() ?: UsageMonitorStatus()
        }

    val systemHealthStatus: Flow<SystemHealthStatus> = preferences
        .map { preferences ->
            preferences[SYSTEM_HEALTH_STATUS]?.toSystemHealthStatusOrNull() ?: SystemHealthStatus()
        }

    val temporaryUnlockState: Flow<TemporaryUnlockState> = preferences
        .map { preferences ->
            preferences[TEMPORARY_UNLOCKS].orEmpty()
                .toTemporaryUnlockState()
                .forToday()
        }

    val hardshipRuntimeState: Flow<HardshipRuntimeState> = preferences
        .map { preferences ->
            preferences[HARDSHIP_RUNTIME_STATE].orEmpty()
                .toHardshipRuntimeState(
                    lastEmergencyPassUsedAtMillis = preferences[HARDSHIP_LAST_EMERGENCY_PASS_AT] ?: 0L,
                )
                .forToday()
        }

    /**
     * Apps that may enter while the manual allow-only mode is active.
     *
     * Before policy-app-list version 1 the single allowed_app_packages value was
     * also treated as a global exemption. Reading the legacy value here keeps the
     * existing allow-only selection intact until the first atomic policy save.
     */
    val allowOnlyAllowedAppPackages: Flow<Set<String>> = preferences
        .map { savedPreferences ->
            savedPreferences.policyPackageSet(
                currentKey = ALLOW_ONLY_ALLOWED_APP_PACKAGES,
                legacyKey = ALLOWED_APP_PACKAGES,
            )
        }

    /**
     * User-selected apps that bypass every enforcement policy. Usage is still
     * collected for statistics; only policy enforcement is bypassed.
     */
    val allRestrictionsExemptPackages: Flow<Set<String>> = preferences
        .map { savedPreferences ->
            savedPreferences.policyPackageSet(
                currentKey = ALL_RESTRICTIONS_EXEMPT_PACKAGES,
                legacyKey = ALLOWED_APP_PACKAGES,
            )
        }

    /**
     * Compatibility alias for call sites that have not yet been migrated.
     * New code must use allowOnlyAllowedAppPackages or
     * allRestrictionsExemptPackages explicitly.
     */
    val allowedAppPackages: Flow<Set<String>> = allowOnlyAllowedAppPackages

    val parentManagementState: Flow<ParentManagementState> = preferences
        .map { preferences ->
            preferences[PARENT_MANAGEMENT_STATE].orEmpty().toParentManagementState()
        }

    val cachedTodayUsage: Flow<CachedTodayUsageSnapshot?> = preferences
        .map { preferences ->
            preferences[CACHED_TODAY_USAGE]?.toCachedTodayUsageSnapshotOrNull()
        }

    suspend fun setSafeModeEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SAFE_MODE_ENABLED] = enabled
        }
    }

    suspend fun setPolicyEnforcementEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[POLICY_ENFORCEMENT_ENABLED] = enabled
            appendEvent(
                preferences = preferences,
                type = EventLogType.Safety,
                message = if (enabled) {
                    "Policy enforcement enabled"
                } else {
                    "Policy enforcement disabled"
                },
            )
        }
    }

    suspend fun setWarningNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[WARNING_NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun setLimitNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[LIMIT_NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun setPermissionSetupCompletedOnce(completed: Boolean) {
        dataStore.edit { preferences ->
            preferences[PERMISSION_SETUP_COMPLETED_ONCE] = completed
        }
    }

    suspend fun acceptMonitoringDisclosure() {
        dataStore.edit { preferences ->
            preferences[MONITORING_DISCLOSURE_ACCEPTED_VERSION] =
                MONITORING_DISCLOSURE_CURRENT_VERSION
            appendEvent(
                preferences = preferences,
                type = EventLogType.Safety,
                message = "Monitoring disclosure accepted",
            )
        }
    }

    suspend fun setChildTopAppsSharingEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[CHILD_TOP_APPS_SHARING_ENABLED] = enabled
        }
    }

    suspend fun configureInitialAdminPin(adminPin: String): Boolean {
        val cleanAdminPin = adminPin.trim()
        if (!cleanAdminPin.isValidSecurityPin()) {
            return false
        }
        var configured = false
        dataStore.edit { preferences ->
            val alreadyConfigured = preferences.hasConfiguredPin(ADMIN_PIN_CREDENTIAL, ADMIN_PIN)
            if (alreadyConfigured) {
                return@edit
            }
            preferences[ADMIN_PIN_CREDENTIAL] = createPinCredential(cleanAdminPin)
            preferences.remove(ADMIN_PIN)
            preferences.remove(EMERGENCY_PIN_CREDENTIAL)
            preferences.remove(EMERGENCY_UNLOCK_PIN)
            preferences.remove(EMERGENCY_PIN_FAILURE_COUNT)
            preferences.remove(EMERGENCY_PIN_LOCK_UNTIL)
            preferences[PIN_SCHEMA_VERSION] = PIN_SCHEMA_VERSION_CURRENT
            clearAdminPinFailures(preferences)
            appendEvent(preferences, EventLogType.Safety, "Admin PIN configured")
            configured = true
        }
        return configured
    }

    suspend fun setDailyPolicyExpanded(expanded: Boolean) {
        dataStore.edit { preferences ->
            preferences[DAILY_POLICY_EXPANDED] = expanded
        }
    }

    suspend fun setAppGroupsExpanded(expanded: Boolean) {
        dataStore.edit { preferences ->
            preferences[APP_GROUPS_EXPANDED] = expanded
        }
    }

    suspend fun setAppLimitsExpanded(expanded: Boolean) {
        dataStore.edit { preferences ->
            preferences[APP_LIMITS_EXPANDED] = expanded
        }
    }

    suspend fun setScheduleBlockingExpanded(expanded: Boolean) {
        dataStore.edit { preferences ->
            preferences[SCHEDULE_BLOCKING_EXPANDED] = expanded
        }
    }

    suspend fun setAllowOnlyModeExpanded(expanded: Boolean) {
        dataStore.edit { preferences ->
            preferences[ALLOW_ONLY_MODE_EXPANDED] = expanded
        }
    }

    suspend fun setSettingsLanguageExpanded(expanded: Boolean) {
        dataStore.edit { preferences ->
            preferences[SETTINGS_LANGUAGE_EXPANDED] = expanded
        }
    }

    suspend fun setSettingsNotificationExpanded(expanded: Boolean) {
        dataStore.edit { preferences ->
            preferences[SETTINGS_NOTIFICATION_EXPANDED] = expanded
        }
    }

    suspend fun setSettingsPinExpanded(expanded: Boolean) {
        dataStore.edit { preferences ->
            preferences[SETTINGS_PIN_EXPANDED] = expanded
        }
    }

    suspend fun setSettingsParentManagementExpanded(expanded: Boolean) {
        dataStore.edit { preferences ->
            preferences[SETTINGS_PARENT_MANAGEMENT_EXPANDED] = expanded
        }
    }

    suspend fun setSettingsEventLogExpanded(expanded: Boolean) {
        dataStore.edit { preferences ->
            preferences[SETTINGS_EVENT_LOG_EXPANDED] = expanded
        }
    }

    suspend fun setAppLanguage(language: AppLanguage) {
        dataStore.edit { preferences ->
            preferences[APP_LANGUAGE] = when (language) {
                AppLanguage.English -> APP_LANGUAGE_ENGLISH
                AppLanguage.Korean -> APP_LANGUAGE_KOREAN
            }
        }
    }

    suspend fun setAllowedAppPackages(packageNames: Set<String>) {
        setAllowOnlyAllowedAppPackages(packageNames)
    }

    suspend fun setAllowOnlyAllowedAppPackages(packageNames: Set<String>) {
        dataStore.edit { preferences ->
            migrateLegacyPolicyPackageListsIfNeeded(preferences)
            val runtimeState = preferences[HARDSHIP_RUNTIME_STATE].orEmpty()
                .toHardshipRuntimeState(preferences[HARDSHIP_LAST_EMERGENCY_PASS_AT] ?: 0L)
                .forToday()
            val currentSettings = storedUsagePolicySettings(preferences)
            val level3ActiveKeys = activeHardshipKeys(preferences, runtimeState)
                .filter { key -> currentSettings.hardshipLevelFor(key) == HardshipLevel.Level3 }
                .toSet()
            val currentPackages = preferences.policyPackageSet(
                currentKey = ALLOW_ONLY_ALLOWED_APP_PACKAGES,
                legacyKey = ALLOWED_APP_PACKAGES,
            )
            val unrestrictedPackages = SafetyGate.expandedUserAllowedPackages(
                preferences.policyPackageSet(
                    currentKey = ALL_RESTRICTIONS_EXEMPT_PACKAGES,
                    legacyKey = ALLOWED_APP_PACKAGES,
                ),
            )
            val cleanPackageNames =
                packageNames.cleanUserPolicyPackageSet() - unrestrictedPackages
            if (
                allowOnlyHardshipKey() in level3ActiveKeys &&
                (cleanPackageNames - currentPackages).isNotEmpty()
            ) {
                appendEvent(
                    preferences,
                    EventLogType.Warning,
                    "Allowed apps change blocked by active hardship level 3",
                )
                return@edit
            }
            preferences[ALLOW_ONLY_ALLOWED_APP_PACKAGES] = cleanPackageNames.toPackageString()
            appendEvent(
                preferences = preferences,
                type = EventLogType.Safety,
                message = "Allow-only apps updated: ${cleanPackageNames.size}",
            )
        }
    }

    suspend fun setAllRestrictionsExemptPackages(
        packageNames: Set<String>,
        adminPin: String,
    ): Boolean {
        var updated = false
        dataStore.edit { preferences ->
            if (!isAdminPinValid(preferences, adminPin.trim())) {
                appendEvent(preferences, EventLogType.Warning, "All-policy exemption change failed: invalid admin PIN")
                return@edit
            }
            migrateLegacyPolicyPackageListsIfNeeded(preferences)
            val runtimeState = preferences[HARDSHIP_RUNTIME_STATE].orEmpty()
                .toHardshipRuntimeState(preferences[HARDSHIP_LAST_EMERGENCY_PASS_AT] ?: 0L)
                .forToday()
            val currentSettings = storedUsagePolicySettings(preferences)
            val activeKeys = activeHardshipKeys(preferences, runtimeState)
                .filter { key -> currentSettings.hardshipLevelFor(key) == HardshipLevel.Level3 }
                .toSet()
            val currentPackages = preferences.policyPackageSet(
                currentKey = ALL_RESTRICTIONS_EXEMPT_PACKAGES,
                legacyKey = ALLOWED_APP_PACKAGES,
            )
            val cleanPackageNames = packageNames.cleanUserPolicyPackageSet()
            if (activeKeys.isNotEmpty() && (cleanPackageNames - currentPackages).isNotEmpty()) {
                appendEvent(
                    preferences,
                    EventLogType.Warning,
                    "All-policy exemption addition blocked by active hardship level 3",
                )
                return@edit
            }
            preferences[ALL_RESTRICTIONS_EXEMPT_PACKAGES] = cleanPackageNames.toPackageString()
            val unrestrictedPackages =
                SafetyGate.expandedUserAllowedPackages(cleanPackageNames)
            val cleanAllowOnlyPackages = preferences.policyPackageSet(
                currentKey = ALLOW_ONLY_ALLOWED_APP_PACKAGES,
                legacyKey = ALLOWED_APP_PACKAGES,
            ) - unrestrictedPackages
            preferences[ALLOW_ONLY_ALLOWED_APP_PACKAGES] =
                cleanAllowOnlyPackages.toPackageString()
            appendEvent(
                preferences,
                EventLogType.Safety,
                "All-policy exemptions updated: ${cleanPackageNames.size}",
            )
            updated = true
        }
        return updated
    }

    suspend fun pairParentAccount(parentAccountId: String, childDeviceName: String, adminPin: String): Boolean {
        val cleanParentId = parentAccountId.trim()
        val cleanDeviceName = childDeviceName.trim().ifBlank { "Child device" }
        if (cleanParentId.isBlank()) {
            return false
        }

        var paired = false
        dataStore.edit { preferences ->
            if (!isAdminPinValid(preferences, adminPin.trim())) {
                appendEvent(preferences, EventLogType.Warning, "Parent pairing failed: invalid admin PIN")
                return@edit
            }
            val current = preferences[PARENT_MANAGEMENT_STATE].orEmpty().toParentManagementState()
            val nextState = current.copy(
                paired = true,
                parentAccountId = cleanParentId,
                localProfileName = current.localProfileName.ifBlank { cleanParentId },
                childDeviceId = current.childDeviceId.ifBlank { UUID.randomUUID().toString() },
                childDeviceName = cleanDeviceName,
                lastSyncMillis = System.currentTimeMillis(),
            )
            preferences[PARENT_MANAGEMENT_STATE] = nextState.toParentManagementStateEncoded()
            appendEvent(preferences, EventLogType.Safety, "Parent account paired: $cleanParentId")
            paired = true
        }
        return paired
    }

    suspend fun setParentDeviceRole(role: ParentDeviceRole, adminPin: String): Boolean {
        var updated = false
        dataStore.edit { preferences ->
            if (!isAdminPinValid(preferences, adminPin.trim())) {
                appendEvent(preferences, EventLogType.Warning, "Parent management role change failed: invalid admin PIN")
                return@edit
            }
            val current = preferences[PARENT_MANAGEMENT_STATE].orEmpty().toParentManagementState()
            preferences[PARENT_MANAGEMENT_STATE] = current.copy(
                deviceRole = role,
                lastSyncMillis = System.currentTimeMillis(),
            ).toParentManagementStateEncoded()
            appendEvent(preferences, EventLogType.Info, "Parent management role changed: ${role.toStorageValue()}")
            updated = true
        }
        return updated
    }

    suspend fun recoverParentAccountLinks(
        parentUid: String,
        parentDisplayName: String,
    ): Boolean {
        val cleanUid = parentUid.trim()
        if (cleanUid.isBlank()) {
            return false
        }
        val recoveredChildren = parentRemoteSyncDataSource
            .fetchLinkedChildDevicesForCurrentParent()
        if (!parentRemoteSyncDataSource.syncState.value.connected) {
            addEvent(EventLogType.Warning, "Parent account recovery failed: cloud unavailable")
            return false
        }
        val cleanDisplayName = parentDisplayName.trim().ifBlank { "Parent device" }
        dataStore.edit { preferences ->
            val current = preferences[PARENT_MANAGEMENT_STATE].orEmpty().toParentManagementState()
            val primaryChild = recoveredChildren.firstOrNull()
            preferences[PARENT_AUTH_UID] = cleanUid
            preferences[PARENT_MANAGEMENT_STATE] = current.copy(
                deviceRole = ParentDeviceRole.Parent,
                paired = recoveredChildren.isNotEmpty(),
                parentAccountId = cleanDisplayName,
                localProfileName = cleanDisplayName,
                childDeviceId = primaryChild?.childDeviceId.orEmpty(),
                childDeviceName = primaryChild?.childDeviceName.orEmpty(),
                remoteCommands = emptyList(),
                remoteUnlockRequests = emptyList(),
                linkedChildPairingCodes = emptySet(),
                linkedChildDevices = recoveredChildren,
                linkedParentDevices = emptyList(),
                lastSyncMillis = System.currentTimeMillis(),
            ).toParentManagementStateEncoded()
            preferences[PARENT_NOTIFICATION_STATE] = ParentNotificationState()
                .toParentNotificationStateEncoded()
            appendEvent(
                preferences,
                EventLogType.Safety,
                "Parent Google account restored: ${recoveredChildren.size} child devices",
            )
        }
        return true
    }

    suspend fun deleteCurrentAccountAndCloudData(adminPin: String): Boolean {
        var pinValid = false
        dataStore.edit { preferences ->
            if (!isAdminPinValid(preferences, adminPin.trim())) {
                appendEvent(
                    preferences,
                    EventLogType.Warning,
                    "Account deletion failed: invalid admin PIN",
                )
                return@edit
            }
            pinValid = true
        }
        if (!pinValid) {
            return false
        }
        val result = parentRemoteSyncDataSource.deleteCurrentUserCloudData()
        recordParentRemoteSyncResult("delete account and cloud data", result)
        if (result != ParentRemoteSyncResult.Success) {
            return false
        }
        dataStore.edit { preferences ->
            val current = preferences[PARENT_MANAGEMENT_STATE].orEmpty().toParentManagementState()
            preferences.remove(PARENT_AUTH_UID)
            preferences[PARENT_MANAGEMENT_STATE] = ParentManagementState(
                deviceRole = current.deviceRole,
                localProfileName = current.localProfileName,
                childDeviceName = if (current.deviceRole == ParentDeviceRole.Child) {
                    current.childDeviceName
                } else {
                    ""
                },
                lastSyncMillis = System.currentTimeMillis(),
            ).toParentManagementStateEncoded()
            preferences[PARENT_NOTIFICATION_STATE] = ParentNotificationState()
                .toParentNotificationStateEncoded()
            appendEvent(preferences, EventLogType.Safety, "Account and cloud data deleted")
        }
        return true
    }

    suspend fun setParentProfileName(profileName: String): Boolean {
        val cleanName = profileName.trim()
        if (cleanName.isBlank()) {
            return false
        }
        var updated = false
        var updatedState: ParentManagementState? = null
        dataStore.edit { preferences ->
            val current = preferences[PARENT_MANAGEMENT_STATE].orEmpty().toParentManagementState()
            val nextState = current.copy(
                localProfileName = cleanName,
                parentAccountId = if (current.deviceRole == ParentDeviceRole.Parent) {
                    cleanName
                } else {
                    current.parentAccountId
                },
                childDeviceName = if (current.deviceRole == ParentDeviceRole.Child) {
                    cleanName
                } else {
                    current.childDeviceName
                },
                lastSyncMillis = System.currentTimeMillis(),
            )
            preferences[PARENT_MANAGEMENT_STATE] = nextState.toParentManagementStateEncoded()
            appendEvent(preferences, EventLogType.Info, "Parent profile name updated")
            updatedState = nextState
            updated = true
        }
        updatedState?.let { state ->
            val result = if (state.deviceRole == ParentDeviceRole.Child) {
                parentRemoteSyncDataSource.updateChildProfile(
                    childDeviceId = state.childDeviceId,
                    childDeviceName = state.childDeviceName.ifBlank { cleanName },
                    pairingCode = state.pairingCode,
                )
            } else {
                parentRemoteSyncDataSource.updateParentProfile(
                    childDeviceIds = state.syncChildDevices().map { child -> child.childDeviceId },
                    parentDisplayName = cleanName,
                )
            }
            recordParentRemoteSyncResult(
                action = "update profile name",
                result = result,
            )
        }
        return updated
    }

    suspend fun generateChildPairingCode(adminPin: String): PairingOperationResult {
        var adminPinValid = false
        dataStore.edit { preferences ->
            if (!isAdminPinValid(preferences, adminPin.trim())) {
                appendEvent(preferences, EventLogType.Warning, "Pairing code generation failed: invalid admin PIN")
                return@edit
            }
            adminPinValid = true
        }
        if (!adminPinValid) {
            return PairingOperationResult.failed(PairingOperationFailure.InvalidAdminPin)
        }

        val current = parentManagementState.first()
        var generatedCode = createPairingCode()
        var generatedChildDeviceId = current.childDeviceId.ifBlank { UUID.randomUUID().toString() }
        val generatedChildDeviceName = current.localProfileName
            .ifBlank { current.childDeviceName }
            .ifBlank { "Child device" }
        var remoteResult = parentRemoteSyncDataSource.publishPairingCode(
            pairingCode = generatedCode,
            childDeviceId = generatedChildDeviceId,
            childDeviceName = generatedChildDeviceName,
            previousPairingCode = current.pairingCode,
        )
        recordParentRemoteSyncResult(
            action = "publish pairing code",
            result = remoteResult,
        )
        if (
            remoteResult is ParentRemoteSyncResult.Failed &&
            remoteResult.kind == ParentRemoteFailureKind.PermissionDenied &&
            current.linkedParentDevices.isEmpty()
        ) {
            // Anonymous Firebase identity can change after app data/auth recovery while the
            // locally stored child id still points to a document owned by the old UID.
            // With no known parent link to preserve, rotate both ids and retry once.
            generatedCode = createPairingCode()
            generatedChildDeviceId = UUID.randomUUID().toString()
            remoteResult = parentRemoteSyncDataSource.publishPairingCode(
                pairingCode = generatedCode,
                childDeviceId = generatedChildDeviceId,
                childDeviceName = generatedChildDeviceName,
                previousPairingCode = current.pairingCode,
            )
            recordParentRemoteSyncResult(
                action = "publish pairing code with recovered child identity",
                result = remoteResult,
            )
        }
        if (remoteResult != ParentRemoteSyncResult.Success) {
            dataStore.edit { preferences ->
                appendEvent(
                    preferences,
                    EventLogType.Warning,
                    "Pairing code cloud publish failed; local code was not activated",
                )
            }
            return PairingOperationResult.failed(remoteResult.toPairingOperationFailure())
        }

        dataStore.edit { preferences ->
            val latest = preferences[PARENT_MANAGEMENT_STATE].orEmpty().toParentManagementState()
            preferences[PARENT_MANAGEMENT_STATE] = latest.copy(
                deviceRole = ParentDeviceRole.Child,
                paired = true,
                childDeviceId = generatedChildDeviceId,
                childDeviceName = generatedChildDeviceName,
                localProfileName = generatedChildDeviceName,
                pairingCode = generatedCode,
                lastSyncMillis = System.currentTimeMillis(),
            ).toParentManagementStateEncoded()
            appendEvent(preferences, EventLogType.Safety, "Child pairing code generated and published")
        }
        return PairingOperationResult.Success
    }

    suspend fun registerChildPairingCode(
        pairingCode: String,
        childDeviceName: String,
        adminPin: String,
    ): PairingOperationResult {
        val cleanCode = pairingCode.normalizedPairingCode()
        if (!cleanCode.isValidPairingCodeFormat()) {
            return PairingOperationResult.failed(PairingOperationFailure.InvalidCode)
        }
        var adminPinValid = false
        dataStore.edit { preferences ->
            if (!isAdminPinValid(preferences, adminPin.trim())) {
                appendEvent(preferences, EventLogType.Warning, "Child device register failed: invalid admin PIN")
                return@edit
            }
            adminPinValid = true
        }
        if (!adminPinValid) {
            return PairingOperationResult.failed(PairingOperationFailure.InvalidAdminPin)
        }
        val currentParentState = parentManagementState.first()
        val localParentDisplayName = currentParentState.localProfileName
            .ifBlank { currentParentState.parentAccountId }
            .ifBlank { "Parent device" }
        val pairingResolution = parentRemoteSyncDataSource.resolvePairingCodeDetailed(
            pairingCode = cleanCode,
            parentDisplayName = localParentDisplayName,
        )
        val pairingRecord = when (pairingResolution) {
            is ParentRemotePairingResolution.Success -> pairingResolution.record
            ParentRemotePairingResolution.NotFound -> {
                dataStore.edit { preferences ->
                    appendEvent(preferences, EventLogType.Warning, "Child device register failed: pairing code not found")
                }
                return PairingOperationResult.failed(PairingOperationFailure.InvalidCode)
            }
            ParentRemotePairingResolution.Expired -> {
                dataStore.edit { preferences ->
                    appendEvent(preferences, EventLogType.Warning, "Child device register failed: expired pairing code")
                }
                return PairingOperationResult.failed(PairingOperationFailure.ExpiredCode)
            }
            ParentRemotePairingResolution.AlreadyUsed -> {
                dataStore.edit { preferences ->
                    appendEvent(preferences, EventLogType.Warning, "Child device register failed: pairing code already used")
                }
                return PairingOperationResult.failed(PairingOperationFailure.AlreadyUsedCode)
            }
            ParentRemotePairingResolution.CloudUnavailable -> {
                return PairingOperationResult.failed(PairingOperationFailure.CloudUnavailable)
            }
            is ParentRemotePairingResolution.Failed -> {
                dataStore.edit { preferences ->
                    appendEvent(
                        preferences,
                        EventLogType.Warning,
                        "Child device register failed: ${pairingResolution.reason}",
                    )
                }
                return PairingOperationResult.failed(pairingResolution.toPairingOperationFailure())
            }
        }
        if (pairingRecord.childDeviceId.isBlank()) {
            dataStore.edit { preferences ->
                appendEvent(preferences, EventLogType.Warning, "Child device register failed: pairing record missing child id")
            }
            return PairingOperationResult.failed(PairingOperationFailure.InvalidCode)
        }
        var registered = false
        dataStore.edit { preferences ->
            val current = preferences[PARENT_MANAGEMENT_STATE].orEmpty().toParentManagementState()
            val resolvedChildDeviceId = pairingRecord.childDeviceId
            val resolvedChildDeviceName = pairingRecord.childDeviceName
                .ifBlank { childDeviceName.trim() }
                .ifBlank { current.childDeviceName.ifBlank { "Child device" } }
            val linkedChild = LinkedChildDevice(
                childDeviceId = resolvedChildDeviceId,
                childDeviceName = resolvedChildDeviceName,
                pairingCode = cleanCode,
                linkedAtMillis = System.currentTimeMillis(),
            )
            preferences[PARENT_MANAGEMENT_STATE] = current.copy(
                deviceRole = ParentDeviceRole.Parent,
                paired = true,
                parentAccountId = current.parentAccountId.ifBlank { localParentDisplayName },
                localProfileName = current.localProfileName.ifBlank { localParentDisplayName },
                childDeviceId = resolvedChildDeviceId,
                childDeviceName = resolvedChildDeviceName,
                linkedChildPairingCodes = current.linkedChildPairingCodes + cleanCode,
                linkedChildDevices = (current.linkedChildDevices + linkedChild)
                    .distinctBy { child -> child.childDeviceId },
                lastSyncMillis = System.currentTimeMillis(),
            ).toParentManagementStateEncoded()
            appendEvent(preferences, EventLogType.Safety, "Child device registered: $cleanCode")
            registered = true
        }
        if (registered) {
            recordParentRemoteSyncResult(
                action = "confirm parent profile after child registration",
                result = parentRemoteSyncDataSource.updateParentProfile(
                    childDeviceIds = listOf(pairingRecord.childDeviceId),
                    parentDisplayName = localParentDisplayName,
                ),
            )
        }
        return if (registered) {
            PairingOperationResult.Success
        } else {
            PairingOperationResult.failed(PairingOperationFailure.Unknown)
        }
    }

    suspend fun unlinkParentAccount(adminPin: String): Boolean {
        var unlinked = false
        var childDeviceIdsForRemote = emptyList<String>()
        var parentAccountIdForRemote = ""
        dataStore.edit { preferences ->
            if (!isAdminPinValid(preferences, adminPin.trim())) {
                appendEvent(preferences, EventLogType.Warning, "Parent unlink failed: invalid admin PIN")
                return@edit
            }
            val current = preferences[PARENT_MANAGEMENT_STATE].orEmpty().toParentManagementState()
            childDeviceIdsForRemote = current.linkedChildDevices
                .map { child -> child.childDeviceId }
                .plus(current.childDeviceId)
                .filter { childDeviceId -> childDeviceId.isNotBlank() }
                .distinct()
            parentAccountIdForRemote = current.parentAccountId
            preferences[PARENT_MANAGEMENT_STATE] = current.copy(
                paired = false,
                parentAccountId = "",
                linkedChildPairingCodes = emptySet(),
                linkedChildDevices = emptyList(),
                lastSyncMillis = System.currentTimeMillis(),
            ).toParentManagementStateEncoded()
            appendEvent(preferences, EventLogType.Safety, "Parent account unlinked")
            unlinked = true
        }
        if (unlinked) {
            childDeviceIdsForRemote.forEach { childDeviceId ->
                recordParentRemoteSyncResult(
                    action = "unlink child",
                    result = parentRemoteSyncDataSource.unlinkChild(
                        childDeviceId = childDeviceId,
                        parentAccountId = parentAccountIdForRemote,
                    ),
                )
            }
        }
        return unlinked
    }

    suspend fun unlinkLinkedChildDevice(childDeviceId: String, adminPin: String): Boolean {
        val cleanChildDeviceId = childDeviceId.trim()
        if (cleanChildDeviceId.isBlank()) {
            return false
        }
        var unlinked = false
        var parentAccountIdForRemote = ""
        dataStore.edit { preferences ->
            if (!isAdminPinValid(preferences, adminPin.trim())) {
                appendEvent(preferences, EventLogType.Warning, "Linked child unlink failed: invalid admin PIN")
                return@edit
            }
            val current = preferences[PARENT_MANAGEMENT_STATE].orEmpty().toParentManagementState()
            parentAccountIdForRemote = current.parentAccountId
            val remainingChildren = current.linkedChildDevices
                .filterNot { child -> child.childDeviceId == cleanChildDeviceId }
            val nextPrimaryChild = remainingChildren.firstOrNull()
            preferences[PARENT_MANAGEMENT_STATE] = current.copy(
                paired = remainingChildren.isNotEmpty() || current.deviceRole == ParentDeviceRole.Child,
                childDeviceId = if (current.childDeviceId == cleanChildDeviceId) {
                    nextPrimaryChild?.childDeviceId.orEmpty()
                } else {
                    current.childDeviceId
                },
                childDeviceName = if (current.childDeviceId == cleanChildDeviceId) {
                    nextPrimaryChild?.childDeviceName.orEmpty()
                } else {
                    current.childDeviceName
                },
                linkedChildDevices = remainingChildren,
                linkedChildPairingCodes = current.linkedChildPairingCodes.filterNot { code ->
                    current.linkedChildDevices.any { child ->
                        child.childDeviceId == cleanChildDeviceId && child.pairingCode == code
                    }
                }.toSet(),
                lastSyncMillis = System.currentTimeMillis(),
            ).toParentManagementStateEncoded()
            appendEvent(preferences, EventLogType.Safety, "Linked child device unlinked")
            unlinked = true
        }
        if (unlinked) {
            recordParentRemoteSyncResult(
                action = "unlink selected child",
                result = parentRemoteSyncDataSource.unlinkChild(
                    childDeviceId = cleanChildDeviceId,
                    parentAccountId = parentAccountIdForRemote,
                ),
            )
        }
        return unlinked
    }

    suspend fun unlinkLinkedParentDevice(parentUid: String, adminPin: String): Boolean {
        val cleanParentUid = parentUid.trim()
        if (cleanParentUid.isBlank()) {
            return false
        }
        var unlinked = false
        var childDeviceIdForRemote = ""
        dataStore.edit { preferences ->
            if (!isAdminPinValid(preferences, adminPin.trim())) {
                appendEvent(preferences, EventLogType.Warning, "Linked parent unlink failed: invalid admin PIN")
                return@edit
            }
            val current = preferences[PARENT_MANAGEMENT_STATE].orEmpty().toParentManagementState()
            childDeviceIdForRemote = current.childDeviceId
            val remainingParents = current.linkedParentDevices
                .filterNot { parent -> parent.parentUid == cleanParentUid }
            preferences[PARENT_MANAGEMENT_STATE] = current.copy(
                paired = remainingParents.isNotEmpty() || current.deviceRole == ParentDeviceRole.Parent,
                linkedParentDevices = remainingParents,
                lastSyncMillis = System.currentTimeMillis(),
            ).toParentManagementStateEncoded()
            appendEvent(preferences, EventLogType.Safety, "Linked parent device unlinked")
            unlinked = true
        }
        if (unlinked) {
            recordParentRemoteSyncResult(
                action = "unlink selected parent",
                result = parentRemoteSyncDataSource.unlinkParentFromChild(
                    childDeviceId = childDeviceIdForRemote,
                    parentUid = cleanParentUid,
                ),
            )
        }
        return unlinked
    }

    suspend fun syncParentDevice(): Boolean {
        val currentState = parentManagementState.first()
        val syncChildDevices = currentState.syncChildDevices()
        var remoteSyncAttempted = false
        var remoteSyncSucceeded = parentRemoteSyncDataSource.syncState.value.mode == ParentRemoteSyncMode.Cloud
        val fetchedLinkedChildDevices = if (
            currentState.deviceRole == ParentDeviceRole.Parent &&
            syncChildDevices.isNotEmpty()
        ) {
            parentRemoteSyncDataSource.fetchLinkedChildDevices(
                syncChildDevices.map { child -> child.childDeviceId },
            ).also {
                remoteSyncAttempted = true
                remoteSyncSucceeded = remoteSyncSucceeded && parentRemoteSyncDataSource.syncState.value.connected
            }
        } else {
            emptyList()
        }
        val fetchedRequests = when {
            currentState.deviceRole == ParentDeviceRole.Parent -> {
                syncChildDevices.flatMap { child ->
                    parentRemoteSyncDataSource.fetchChildRequests(
                        parentAccountId = currentState.parentAccountId,
                        childDeviceId = child.childDeviceId,
                    ).also {
                        remoteSyncAttempted = true
                        remoteSyncSucceeded = remoteSyncSucceeded && parentRemoteSyncDataSource.syncState.value.connected
                    }
                }
            }

            currentState.childDeviceId.isNotBlank() -> {
                val now = System.currentTimeMillis()
                currentState.remoteUnlockRequests
                    .filter { request ->
                        request.status == RemoteUnlockRequestStatus.Pending &&
                            request.expiresAtMillis >= now &&
                            request.id.isNotBlank()
                    }
                    .mapNotNull { request ->
                        parentRemoteSyncDataSource.fetchChildRequest(
                            childDeviceId = currentState.childDeviceId,
                            requestId = request.id,
                        ).also {
                            remoteSyncAttempted = true
                            remoteSyncSucceeded = remoteSyncSucceeded &&
                                parentRemoteSyncDataSource.syncState.value.connected
                        }
                    }
                }

            else -> emptyList()
        }
        val fetchedLinkedParents = if (
            currentState.deviceRole == ParentDeviceRole.Child &&
            currentState.childDeviceId.isNotBlank()
        ) {
            parentRemoteSyncDataSource.fetchLinkedParents(currentState.childDeviceId).also {
                remoteSyncAttempted = true
                remoteSyncSucceeded = remoteSyncSucceeded && parentRemoteSyncDataSource.syncState.value.connected
            }
        } else {
            emptyList()
        }
        val fetchedCommands = if (
            currentState.deviceRole == ParentDeviceRole.Child &&
            currentState.childDeviceId.isNotBlank()
        ) {
            val lastCommandTimestamp = currentState.remoteCommands
                .maxOfOrNull { command -> command.timestampMillis }
                ?.coerceAtLeast(0L)
                ?: 0L
            val commandCursorMillis = maxOf(
                lastCommandTimestamp,
                currentLocalDayStartMillis() - 1L,
            )
            parentRemoteSyncDataSource.fetchChildCommands(
                childDeviceId = currentState.childDeviceId,
                afterTimestampMillis = commandCursorMillis,
            ).also {
                remoteSyncAttempted = true
                remoteSyncSucceeded = remoteSyncSucceeded && parentRemoteSyncDataSource.syncState.value.connected
            }
        } else {
            emptyList()
        }
        dataStore.edit { preferences ->
            val current = preferences[PARENT_MANAGEMENT_STATE].orEmpty().toParentManagementState()
            var nextState = current.copy(
                childDeviceId = if (current.deviceRole == ParentDeviceRole.Child) {
                    current.childDeviceId.ifBlank { UUID.randomUUID().toString() }
                } else {
                    current.childDeviceId
                },
                linkedChildDevices = fetchedLinkedChildDevices.mergeWithLocalLinkedChildren(
                    current.linkedChildDevices.ifEmpty {
                        if (current.deviceRole == ParentDeviceRole.Parent && current.childDeviceId.isNotBlank()) {
                            listOf(
                                LinkedChildDevice(
                                    childDeviceId = current.childDeviceId,
                                    childDeviceName = current.childDeviceName.ifBlank { "Child device" },
                                    linkedAtMillis = current.lastSyncMillis,
                                ),
                            )
                        } else {
                            emptyList()
                        }
                    },
                ),
                linkedParentDevices = fetchedLinkedParents.ifEmpty { current.linkedParentDevices },
                lastSyncMillis = if (remoteSyncAttempted && remoteSyncSucceeded) {
                    System.currentTimeMillis()
                } else {
                    current.lastSyncMillis
                },
                remoteUnlockRequests = current.remoteUnlockRequests
                    .withOnlyLatestPendingRequestPerTarget(System.currentTimeMillis()),
            )
            if (current.deviceRole == ParentDeviceRole.Parent && fetchedLinkedChildDevices.isNotEmpty()) {
                val primaryChild = nextState.linkedChildDevices.firstOrNull { child ->
                    child.childDeviceId == nextState.childDeviceId
                } ?: nextState.linkedChildDevices.firstOrNull()
                if (primaryChild != null) {
                    nextState = nextState.copy(
                        childDeviceId = primaryChild.childDeviceId,
                        childDeviceName = primaryChild.childDeviceName,
                    )
                }
            }
            if (fetchedRequests.isNotEmpty()) {
                val appliedCommandIds = (fetchedCommands + nextState.remoteCommands)
                    .filter { command -> command.status == RemoteParentCommandStatus.Applied }
                    .map { command -> command.id }
                    .toSet()
                val requestsReadyToMerge = if (current.deviceRole == ParentDeviceRole.Child) {
                    fetchedRequests.filter { request ->
                        request.status != RemoteUnlockRequestStatus.Approved ||
                            request.id in appliedCommandIds
                    }
                } else {
                    fetchedRequests
                }
                val mergedRequests = (requestsReadyToMerge + nextState.remoteUnlockRequests)
                    .distinctBy { request -> request.id }
                    .withOnlyLatestPendingRequestPerTarget(System.currentTimeMillis())
                    .take(MAX_REMOTE_UNLOCK_REQUESTS)
                nextState = nextState.copy(remoteUnlockRequests = mergedRequests)
            }
            var temporaryState = preferences[TEMPORARY_UNLOCKS].orEmpty()
                .toTemporaryUnlockState()
                .forToday()
            val existingCommandIds = nextState.remoteCommands.map { command -> command.id }.toSet()
            val currentDayStartMillis = currentLocalDayStartMillis()
            val newCommands = fetchedCommands
                .filter { command -> command.status == RemoteParentCommandStatus.Applied }
                .filter { command -> command.id !in existingCommandIds }
                .filter { command -> command.timestampMillis >= currentDayStartMillis }
                .sortedBy { command -> command.timestampMillis }
            val activeHardshipPolicies = preferences[HARDSHIP_RUNTIME_STATE].orEmpty()
                .toHardshipRuntimeState(preferences[HARDSHIP_LAST_EMERGENCY_PASS_AT] ?: 0L)
                .forToday()
                .activePolicies
            val (blockedCommands, allowedCommands) = newCommands.partition { command ->
                when (command.type) {
                    RemoteParentCommandType.AddTotalTime,
                    RemoteParentCommandType.UnlockTotalToday ->
                        HardshipPolicyType.DailyLimit in activeHardshipPolicies

                    RemoteParentCommandType.AddAppTime,
                    RemoteParentCommandType.UnlockAppToday ->
                        activeHardshipPolicies.any { policyType ->
                            policyType != HardshipPolicyType.DailyLimit
                        }
                }
            }
            allowedCommands.forEach { command ->
                temporaryState = temporaryState.applyRemoteCommand(command)
                appendEvent(preferences, EventLogType.Info, "Remote command applied from cloud: ${command.message}")
            }
            blockedCommands.forEach { command ->
                appendEvent(
                    preferences,
                    EventLogType.Warning,
                    "Remote command rejected by active hardship level 3: ${command.message}",
                )
            }
            if (newCommands.isNotEmpty()) {
                if (allowedCommands.isNotEmpty()) {
                    preferences[TEMPORARY_UNLOCKS] = temporaryState.toTemporaryUnlocksEncoded()
                }
                val recordedCommands = newCommands.map { command ->
                    if (command in blockedCommands) {
                        command.copy(
                            status = RemoteParentCommandStatus.Failed,
                            message = "${command.message} (blocked by hardship level 3)",
                        )
                    } else {
                        command
                    }
                }
                nextState = nextState.copy(
                    remoteCommands = (recordedCommands.asReversed() + nextState.remoteCommands)
                        .distinctBy { command -> command.id }
                        .take(MAX_REMOTE_PARENT_COMMANDS),
                )
            }
            preferences[PARENT_MANAGEMENT_STATE] = nextState.toParentManagementStateEncoded()
        }
        if (remoteSyncAttempted && remoteSyncSucceeded) {
            cleanupRemoteDataIfDue(
                deviceRole = currentState.deviceRole,
                childDeviceId = currentState.childDeviceId,
            )
        }
        return !remoteSyncAttempted || remoteSyncSucceeded
    }

    suspend fun synchronizePushToken(
        registrationId: String,
        token: String,
        previouslyRegisteredTargets: Set<RemotePushTokenTarget>,
    ): Set<RemotePushTokenTarget>? {
        val state = parentManagementState.first()
        val desiredTargets = when {
            !state.paired -> emptySet()
            state.deviceRole == ParentDeviceRole.Child && state.childDeviceId.isNotBlank() -> setOf(
                RemotePushTokenTarget(
                    childDeviceId = state.childDeviceId,
                    role = RemotePushTokenRole.Child,
                ),
            )
            state.deviceRole == ParentDeviceRole.Parent -> state.syncChildDevices()
                .mapNotNullTo(linkedSetOf()) { child ->
                    child.childDeviceId.trim()
                        .takeIf { childDeviceId -> childDeviceId.isNotBlank() }
                        ?.let { childDeviceId ->
                            RemotePushTokenTarget(
                                childDeviceId = childDeviceId,
                                role = RemotePushTokenRole.Parent,
                            )
                        }
                }
            else -> emptySet()
        }
        val result = parentRemoteSyncDataSource.synchronizePushToken(
            registrationId = registrationId,
            token = token,
            desiredTargets = desiredTargets,
            obsoleteTargets = previouslyRegisteredTargets - desiredTargets,
        )
        return if (result == ParentRemoteSyncResult.Success) desiredTargets else null
    }

    private suspend fun cleanupRemoteDataIfDue(
        deviceRole: ParentDeviceRole,
        childDeviceId: String,
    ) {
        if (deviceRole != ParentDeviceRole.Child || childDeviceId.isBlank()) {
            return
        }
        val now = System.currentTimeMillis()
        var shouldCleanup = false
        dataStore.edit { preferences ->
            val lastCleanupMillis = preferences[LAST_REMOTE_CLEANUP_MILLIS] ?: 0L
            if (now - lastCleanupMillis >= REMOTE_CLEANUP_INTERVAL_MILLIS) {
                preferences[LAST_REMOTE_CLEANUP_MILLIS] = now
                shouldCleanup = true
            }
        }
        if (!shouldCleanup) {
            return
        }
        val result = parentRemoteSyncDataSource.cleanupExpiredRemoteData(
            childDeviceId = childDeviceId,
            olderThanMillis = now - REMOTE_DOCUMENT_RETENTION_MILLIS,
        )
        if (result is ParentRemoteSyncResult.Failed && !result.retryable) {
            addEvent(
                type = EventLogType.Warning,
                message = "Remote history cleanup skipped: ${result.reason}",
            )
        }
    }

    suspend fun syncRemoteUnlockRequest(requestId: String): Boolean {
        val cleanRequestId = requestId.trim()
        if (cleanRequestId.isBlank()) {
            return false
        }
        val currentState = parentManagementState.first()
        if (currentState.deviceRole != ParentDeviceRole.Child || currentState.childDeviceId.isBlank()) {
            return false
        }
        val remoteRequest = parentRemoteSyncDataSource.fetchChildRequest(
            childDeviceId = currentState.childDeviceId,
            requestId = cleanRequestId,
        )
        val remoteCommand = parentRemoteSyncDataSource.fetchChildCommand(
            childDeviceId = currentState.childDeviceId,
            commandId = cleanRequestId,
        )
        val resolvedRemoteRequest = remoteRequest?.takeIf { request ->
            request.status != RemoteUnlockRequestStatus.Approved ||
                remoteCommand?.status == RemoteParentCommandStatus.Applied ||
                currentState.remoteCommands.any { command ->
                    command.id == cleanRequestId &&
                        command.status == RemoteParentCommandStatus.Applied
                }
        }
        val syncSucceeded = parentRemoteSyncDataSource.syncState.value.connected
        if (resolvedRemoteRequest == null && remoteCommand == null) {
            return syncSucceeded
        }
        dataStore.edit { preferences ->
            val current = preferences[PARENT_MANAGEMENT_STATE].orEmpty().toParentManagementState()
            var nextState = current
            if (resolvedRemoteRequest != null) {
                nextState = nextState.copy(
                    remoteUnlockRequests = (listOf(resolvedRemoteRequest) + nextState.remoteUnlockRequests)
                        .distinctBy { request -> request.id }
                        .withOnlyLatestPendingRequestPerTarget(System.currentTimeMillis())
                        .take(MAX_REMOTE_UNLOCK_REQUESTS),
                )
            }
            if (
                remoteCommand != null &&
                remoteCommand.status == RemoteParentCommandStatus.Applied &&
                nextState.remoteCommands.none { command -> command.id == remoteCommand.id }
            ) {
                var temporaryState = preferences[TEMPORARY_UNLOCKS].orEmpty()
                    .toTemporaryUnlockState()
                    .forToday()
                val activeHardshipPolicies = preferences[HARDSHIP_RUNTIME_STATE].orEmpty()
                    .toHardshipRuntimeState(preferences[HARDSHIP_LAST_EMERGENCY_PASS_AT] ?: 0L)
                    .forToday()
                    .activePolicies
                val blocked = remoteCommand.isBlockedByActiveHardship(activeHardshipPolicies)
                val recordedCommand = if (blocked) {
                    appendEvent(
                        preferences,
                        EventLogType.Warning,
                        "Remote command rejected by active hardship level 3: ${remoteCommand.message}",
                    )
                    remoteCommand.copy(
                        status = RemoteParentCommandStatus.Failed,
                        message = "${remoteCommand.message} (blocked by hardship level 3)",
                    )
                } else {
                    temporaryState = temporaryState.applyRemoteCommand(remoteCommand)
                    preferences[TEMPORARY_UNLOCKS] = temporaryState.toTemporaryUnlocksEncoded()
                    appendEvent(
                        preferences,
                        EventLogType.Info,
                        "Remote command applied from cloud: ${remoteCommand.message}",
                    )
                    remoteCommand
                }
                nextState = nextState.copy(
                    remoteCommands = (listOf(recordedCommand) + nextState.remoteCommands)
                        .distinctBy { command -> command.id }
                        .take(MAX_REMOTE_PARENT_COMMANDS),
                )
            }
            preferences[PARENT_MANAGEMENT_STATE] = nextState.copy(
                lastSyncMillis = if (syncSucceeded) System.currentTimeMillis() else nextState.lastSyncMillis,
            ).toParentManagementStateEncoded()
        }
        return syncSucceeded
    }

    suspend fun syncNewRemoteCommands(): Boolean {
        val currentState = parentManagementState.first()
        if (currentState.deviceRole != ParentDeviceRole.Child || currentState.childDeviceId.isBlank()) {
            return false
        }
        val lastCommandTimestamp = currentState.remoteCommands
            .maxOfOrNull { command -> command.timestampMillis }
            ?.coerceAtLeast(0L)
            ?: 0L
        val commandCursorMillis = maxOf(
            lastCommandTimestamp,
            currentLocalDayStartMillis() - 1L,
        )
        val fetchedCommands = parentRemoteSyncDataSource.fetchChildCommands(
            childDeviceId = currentState.childDeviceId,
            afterTimestampMillis = commandCursorMillis,
        )
        val syncSucceeded = parentRemoteSyncDataSource.syncState.value.connected
        if (fetchedCommands.isEmpty()) {
            return syncSucceeded
        }
        dataStore.edit { preferences ->
            val current = preferences[PARENT_MANAGEMENT_STATE].orEmpty().toParentManagementState()
            val existingCommandIds = current.remoteCommands.map { command -> command.id }.toSet()
            val currentDayStartMillis = currentLocalDayStartMillis()
            val newCommands = fetchedCommands
                .filter { command -> command.id !in existingCommandIds }
                .filter { command -> command.timestampMillis >= currentDayStartMillis }
                .sortedBy { command -> command.timestampMillis }
            if (newCommands.isEmpty()) {
                return@edit
            }
            var temporaryState = preferences[TEMPORARY_UNLOCKS].orEmpty()
                .toTemporaryUnlockState()
                .forToday()
            val activeHardshipPolicies = preferences[HARDSHIP_RUNTIME_STATE].orEmpty()
                .toHardshipRuntimeState(preferences[HARDSHIP_LAST_EMERGENCY_PASS_AT] ?: 0L)
                .forToday()
                .activePolicies
            val recordedCommands = newCommands.map { command ->
                when {
                    command.status != RemoteParentCommandStatus.Applied -> command
                    command.isBlockedByActiveHardship(activeHardshipPolicies) -> {
                        appendEvent(
                            preferences,
                            EventLogType.Warning,
                            "Remote command rejected by active hardship level 3: ${command.message}",
                        )
                        command.copy(
                            status = RemoteParentCommandStatus.Failed,
                            message = "${command.message} (blocked by hardship level 3)",
                        )
                    }

                    else -> {
                        temporaryState = temporaryState.applyRemoteCommand(command)
                        appendEvent(
                            preferences,
                            EventLogType.Info,
                            "Remote command applied from cloud: ${command.message}",
                        )
                        command
                    }
                }
            }
            if (recordedCommands.any { command -> command.status == RemoteParentCommandStatus.Applied }) {
                preferences[TEMPORARY_UNLOCKS] = temporaryState.toTemporaryUnlocksEncoded()
            }
            val statusesByRequestId = newCommands.mapNotNull { command ->
                val status = when (command.status) {
                    RemoteParentCommandStatus.Applied -> RemoteUnlockRequestStatus.Approved
                    RemoteParentCommandStatus.Failed -> RemoteUnlockRequestStatus.Rejected
                    RemoteParentCommandStatus.Pending -> null
                } ?: return@mapNotNull null
                command.id to status
            }.toMap()
            preferences[PARENT_MANAGEMENT_STATE] = current.copy(
                lastSyncMillis = if (syncSucceeded) System.currentTimeMillis() else current.lastSyncMillis,
                remoteCommands = (recordedCommands.asReversed() + current.remoteCommands)
                    .distinctBy { command -> command.id }
                    .take(MAX_REMOTE_PARENT_COMMANDS),
                remoteUnlockRequests = current.remoteUnlockRequests.map { request ->
                    statusesByRequestId[request.id]?.let { status -> request.copy(status = status) } ?: request
                },
            ).toParentManagementStateEncoded()
        }
        return syncSucceeded
    }

    suspend fun recordParentNotificationOutcome(
        eventToken: String?,
        delivered: Boolean,
        error: String,
        activePendingRequestIds: Set<String>,
    ) {
        val now = System.currentTimeMillis()
        dataStore.edit { preferences ->
            val current = preferences[PARENT_NOTIFICATION_STATE].orEmpty().toParentNotificationState()
            val handledTokens = if (delivered && !eventToken.isNullOrBlank()) {
                (current.handledEventTokens + eventToken)
                    .toList()
                    .takeLast(MAX_PARENT_NOTIFICATION_EVENT_TOKENS)
                    .toSet()
            } else {
                current.handledEventTokens
            }
            val cleanError = error.trim().take(MAX_PARENT_NOTIFICATION_ERROR_LENGTH)
            preferences[PARENT_NOTIFICATION_STATE] = current.copy(
                handledEventTokens = handledTokens,
                activePendingRequestIds = activePendingRequestIds.filter { id -> id.isNotBlank() }.toSet(),
                lastAttemptMillis = now,
                lastSuccessMillis = if (delivered) now else current.lastSuccessMillis,
                lastError = if (delivered) "" else cleanError,
            ).toParentNotificationStateEncoded()
            if (!delivered && cleanError.isNotBlank() && cleanError != current.lastError) {
                appendEvent(
                    preferences,
                    EventLogType.Warning,
                    "Parent request notification unavailable: $cleanError",
                )
            }
        }
    }

    suspend fun updateActiveParentRequestNotificationIds(requestIds: Set<String>) {
        dataStore.edit { preferences ->
            val current = preferences[PARENT_NOTIFICATION_STATE].orEmpty().toParentNotificationState()
            val cleanIds = requestIds.filter { id -> id.isNotBlank() }.toSet()
            if (current.activePendingRequestIds != cleanIds) {
                preferences[PARENT_NOTIFICATION_STATE] = current.copy(
                    activePendingRequestIds = cleanIds,
                ).toParentNotificationStateEncoded()
            }
        }
    }

    suspend fun clearRemoteParentCommands(adminPin: String): Boolean {
        var cleared = false
        dataStore.edit { preferences ->
            if (!isAdminPinValid(preferences, adminPin.trim())) {
                appendEvent(preferences, EventLogType.Warning, "Remote command history clear failed: invalid admin PIN")
                return@edit
            }
            val current = preferences[PARENT_MANAGEMENT_STATE].orEmpty().toParentManagementState()
            preferences[PARENT_MANAGEMENT_STATE] = current.copy(
                remoteCommands = emptyList(),
                lastSyncMillis = System.currentTimeMillis(),
            ).toParentManagementStateEncoded()
            appendEvent(preferences, EventLogType.Info, "Remote command history cleared")
            cleared = true
        }
        return cleared
    }

    suspend fun createRemoteUnlockRequest(
        blockReason: RemoteRequestBlockReason,
        targetPackageName: String,
        targetAppName: String,
        targetGroupName: String,
        scheduleName: String,
        usedMillis: Long,
        limitMillis: Long?,
        alreadyGrantedExtraMinutes: Int,
        unlockedForToday: Boolean,
        requestedMinutes: Int,
        childMessage: String = "",
    ): RemoteUnlockRequestSubmitResult {
        if (requestedMinutes <= 0) {
            return RemoteUnlockRequestSubmitResult.Failed("Requested minutes must be positive")
        }
        var created = false
        var createdRequest: RemoteUnlockRequest? = null
        var reusedExistingRequest = false
        var suppressedByCooldown = false
        var parentNotPaired = false
        dataStore.edit { preferences ->
            val parentState = preferences[PARENT_MANAGEMENT_STATE].orEmpty().toParentManagementState()
            val now = System.currentTimeMillis()
            if (!parentState.canRequestParentApproval()) {
                appendEvent(preferences, EventLogType.Warning, "Remote unlock request failed: parent account not paired")
                parentNotPaired = true
                return@edit
            }
            val cleanedRequests = parentState.remoteUnlockRequests
                .withOnlyLatestPendingRequestPerTarget(now)
            val reusableRequest = cleanedRequests.firstOrNull { request ->
                request.status == RemoteUnlockRequestStatus.Pending &&
                    request.expiresAtMillis >= now &&
                    request.isSameRemoteRequestTarget(
                        blockReason = blockReason,
                        targetPackageName = targetPackageName,
                        targetGroupName = targetGroupName,
                        scheduleName = scheduleName,
                    )
            }
            if (reusableRequest != null) {
                if (now - reusableRequest.createdAtMillis < REMOTE_UNLOCK_REQUEST_COOLDOWN_MILLIS) {
                    preferences[PARENT_MANAGEMENT_STATE] = parentState.copy(
                        childDeviceId = parentState.childDeviceId.ifBlank { reusableRequest.childDeviceId },
                        remoteUnlockRequests = cleanedRequests.take(MAX_REMOTE_UNLOCK_REQUESTS),
                    ).toParentManagementStateEncoded()
                    appendEvent(
                        preferences,
                        EventLogType.Info,
                        "Remote unlock request suppressed by cooldown: ${reusableRequest.id}",
                    )
                    created = true
                    createdRequest = reusableRequest
                    reusedExistingRequest = true
                    suppressedByCooldown = true
                    return@edit
                }
                val refreshedRequest = reusableRequest.copy(
                    createdAtMillis = now,
                    expiresAtMillis = now + REMOTE_UNLOCK_REQUEST_TTL_MILLIS,
                    usedMillis = usedMillis.coerceAtLeast(0L),
                    limitMillis = limitMillis?.coerceAtLeast(0L),
                    alreadyGrantedExtraMinutes = alreadyGrantedExtraMinutes.coerceAtLeast(0),
                    unlockedForToday = unlockedForToday,
                    requestedMinutes = requestedMinutes.coerceAtLeast(0),
                    childMessage = childMessage.trim(),
                )
                preferences[PARENT_MANAGEMENT_STATE] = parentState.copy(
                    childDeviceId = parentState.childDeviceId.ifBlank { refreshedRequest.childDeviceId },
                    lastSyncMillis = now,
                    remoteUnlockRequests = (listOf(refreshedRequest) + cleanedRequests.filterNot { request ->
                        request.id == refreshedRequest.id
                    })
                        .sortedByDescending { request -> request.createdAtMillis }
                        .take(MAX_REMOTE_UNLOCK_REQUESTS),
                ).toParentManagementStateEncoded()
                appendEvent(
                    preferences,
                    EventLogType.Info,
                    "Remote unlock request reused: ${blockReason.toStorageValue()} ${targetAppName.ifBlank { targetGroupName.ifBlank { targetPackageName } }}",
                )
                created = true
                createdRequest = refreshedRequest
                reusedExistingRequest = true
                return@edit
            }
            val request = RemoteUnlockRequest(
                id = UUID.randomUUID().toString(),
                childDeviceId = parentState.childDeviceId.ifBlank { UUID.randomUUID().toString() },
                childDeviceName = parentState.childDeviceName.ifBlank { "Child device" },
                createdAtMillis = now,
                expiresAtMillis = now + REMOTE_UNLOCK_REQUEST_TTL_MILLIS,
                blockReason = blockReason,
                targetPackageName = targetPackageName,
                targetAppName = targetAppName,
                targetGroupName = targetGroupName,
                scheduleName = scheduleName,
                usedMillis = usedMillis.coerceAtLeast(0L),
                limitMillis = limitMillis?.coerceAtLeast(0L),
                alreadyGrantedExtraMinutes = alreadyGrantedExtraMinutes.coerceAtLeast(0),
                unlockedForToday = unlockedForToday,
                requestedMinutes = requestedMinutes.coerceAtLeast(0),
                childMessage = childMessage.trim(),
                status = RemoteUnlockRequestStatus.Pending,
            )
            preferences[PARENT_MANAGEMENT_STATE] = parentState.copy(
                childDeviceId = parentState.childDeviceId.ifBlank { request.childDeviceId },
                lastSyncMillis = now,
                remoteUnlockRequests = (listOf(request) + cleanedRequests)
                    .take(MAX_REMOTE_UNLOCK_REQUESTS),
            ).toParentManagementStateEncoded()
            appendEvent(
                preferences,
                EventLogType.Safety,
                "Remote unlock requested: ${blockReason.toStorageValue()} ${targetAppName.ifBlank { targetGroupName.ifBlank { targetPackageName } }} +${requestedMinutes.toTimeLabel()}",
            )
            created = true
            createdRequest = request
        }
        val request = createdRequest ?: return if (parentNotPaired) {
            RemoteUnlockRequestSubmitResult.NotPaired
        } else {
            RemoteUnlockRequestSubmitResult.Failed("Unable to create a local request")
        }
        if (suppressedByCooldown) {
            return RemoteUnlockRequestSubmitResult.Sent(request.id)
        }
        val remoteResult = publishUnlockRequestWithTimeout(request)
        recordParentRemoteSyncResult(
            action = "publish unlock request",
            result = remoteResult,
        )
        if (remoteResult is ParentRemoteSyncResult.Failed && remoteResult.retryable) {
            addEvent(
                type = EventLogType.Info,
                message = "Remote unlock request queued for retry: ${request.id}",
            )
            return RemoteUnlockRequestSubmitResult.Retrying(request.id)
        }
        if (remoteResult != ParentRemoteSyncResult.Success) {
            if (!reusedExistingRequest) {
                dataStore.edit { preferences ->
                    val current = preferences[PARENT_MANAGEMENT_STATE].orEmpty().toParentManagementState()
                    preferences[PARENT_MANAGEMENT_STATE] = current.copy(
                        remoteUnlockRequests = current.remoteUnlockRequests.filterNot { item ->
                            item.id == request.id
                        },
                    ).toParentManagementStateEncoded()
                }
            }
            val reason = (remoteResult as? ParentRemoteSyncResult.Failed)?.reason.orEmpty()
            return RemoteUnlockRequestSubmitResult.Failed(reason)
        }
        return if (created) {
            RemoteUnlockRequestSubmitResult.Sent(request.id)
        } else {
            RemoteUnlockRequestSubmitResult.Failed("Unable to create request")
        }
    }

    suspend fun retryRemoteUnlockRequest(requestId: String): RemoteUnlockRequestSubmitResult {
        val cleanRequestId = requestId.trim()
        if (cleanRequestId.isBlank()) {
            return RemoteUnlockRequestSubmitResult.Failed("Request id is blank")
        }
        val request = parentManagementState.first()
            .remoteUnlockRequests
            .firstOrNull { item -> item.id == cleanRequestId }
            ?: return RemoteUnlockRequestSubmitResult.Failed("Request not found")
        if (request.expiresAtMillis < System.currentTimeMillis()) {
            return RemoteUnlockRequestSubmitResult.Failed("Request expired")
        }
        return when (val result = publishUnlockRequestWithTimeout(request)) {
            ParentRemoteSyncResult.Success -> {
                addEvent(EventLogType.Info, "Remote unlock request sent after retry: ${request.id}")
                RemoteUnlockRequestSubmitResult.Sent(request.id)
            }
            ParentRemoteSyncResult.LocalOnly ->
                RemoteUnlockRequestSubmitResult.Failed("Cloud configuration missing")
            is ParentRemoteSyncResult.Failed -> if (result.retryable) {
                RemoteUnlockRequestSubmitResult.Retrying(request.id)
            } else {
                addEvent(EventLogType.Warning, "Remote unlock request retry stopped: ${result.reason}")
                RemoteUnlockRequestSubmitResult.Failed(result.reason)
            }
        }
    }

    private suspend fun publishUnlockRequestWithTimeout(
        request: RemoteUnlockRequest,
    ): ParentRemoteSyncResult {
        return withTimeoutOrNull(REMOTE_UNLOCK_REQUEST_PUBLISH_TIMEOUT_MILLIS) {
            parentRemoteSyncDataSource.publishUnlockRequest(request)
        } ?: ParentRemoteSyncResult.Failed(
            reason = "Unlock request publish timed out",
            retryable = true,
        )
    }

    suspend fun approveRemoteUnlockRequest(requestId: String, extraMinutes: Int, unlockForToday: Boolean): Boolean {
        if (requestId.isBlank()) {
            return false
        }
        var approved = false
        var approvedRequest: RemoteUnlockRequest? = null
        var approvedDecision: ParentRemoteUnlockDecision? = null
        var approvedCommandId = ""
        var previousTemporaryUnlocksEncoded = ""
        dataStore.edit { preferences ->
            val parentState = preferences[PARENT_MANAGEMENT_STATE].orEmpty().toParentManagementState()
            val now = System.currentTimeMillis()
            val request = parentState.remoteUnlockRequests.firstOrNull { request -> request.id == requestId }
            if (request == null) {
                appendEvent(preferences, EventLogType.Warning, "Remote request approve failed: request not found")
                return@edit
            }
            if (request.status != RemoteUnlockRequestStatus.Pending || request.expiresAtMillis < now) {
                val expiredStatus = if (request.expiresAtMillis < now) {
                    RemoteUnlockRequestStatus.Expired
                } else {
                    request.status
                }
                preferences[PARENT_MANAGEMENT_STATE] = parentState.copy(
                    lastSyncMillis = now,
                    remoteUnlockRequests = parentState.remoteUnlockRequests.map { item ->
                        if (item.id == requestId) item.copy(status = expiredStatus) else item
                    },
                ).toParentManagementStateEncoded()
                appendEvent(preferences, EventLogType.Warning, "Remote request approve failed: ${expiredStatus.toStorageValue()}")
                return@edit
            }

            val currentTemporaryState = preferences[TEMPORARY_UNLOCKS].orEmpty()
                .toTemporaryUnlockState()
                .forToday()
            previousTemporaryUnlocksEncoded = preferences[TEMPORARY_UNLOCKS].orEmpty()
            val targetIsDaily = request.blockReason == RemoteRequestBlockReason.DailyLimit
            val nextTemporaryState = if (unlockForToday) {
                if (targetIsDaily) {
                    currentTemporaryState.copy(totalUnlockedForToday = true)
                } else {
                    val currentAllowance =
                        currentTemporaryState.packageAllowances[request.targetPackageName] ?: TemporaryPackageAllowance()
                    currentTemporaryState.copy(
                        packageAllowances = currentTemporaryState.packageAllowances + (
                            request.targetPackageName to currentAllowance.copy(unlockedForToday = true)
                        ),
                    )
                }
            } else {
                val safeExtraMinutes = extraMinutes.coerceAtLeast(1)
                if (targetIsDaily) {
                    currentTemporaryState.copy(
                        totalExtraMinutes = (currentTemporaryState.totalExtraMinutes + safeExtraMinutes)
                            .coerceAtMost(MAX_TEMPORARY_EXTRA_MINUTES),
                    )
                } else {
                    if (request.targetPackageName.isBlank()) {
                        appendEvent(preferences, EventLogType.Warning, "Remote request approve failed: target package missing")
                        return@edit
                    }
                    val currentAllowance =
                        currentTemporaryState.packageAllowances[request.targetPackageName] ?: TemporaryPackageAllowance()
                    currentTemporaryState.copy(
                        packageAllowances = currentTemporaryState.packageAllowances + (
                            request.targetPackageName to currentAllowance.withExtraTime(
                                extraMinutes = safeExtraMinutes,
                                nowMillis = now,
                            )
                        ),
                    )
                }
            }
            preferences[TEMPORARY_UNLOCKS] = nextTemporaryState.toTemporaryUnlocksEncoded()

            val commandType = when {
                unlockForToday && targetIsDaily -> RemoteParentCommandType.UnlockTotalToday
                unlockForToday -> RemoteParentCommandType.UnlockAppToday
                targetIsDaily -> RemoteParentCommandType.AddTotalTime
                else -> RemoteParentCommandType.AddAppTime
            }
            val commandMinutes = if (unlockForToday) 0 else extraMinutes.coerceAtLeast(1)
            val commandTarget = request.targetAppName.ifBlank {
                request.targetGroupName.ifBlank { request.targetPackageName }
            }
            val message = if (unlockForToday) {
                "Remote parent approved unlock for today: ${request.blockReason.toStorageValue()} $commandTarget"
            } else {
                "Remote parent approved ${commandMinutes.toTimeLabel()}: ${request.blockReason.toStorageValue()} $commandTarget"
            }
            val command = RemoteParentCommand(
                id = UUID.randomUUID().toString(),
                timestampMillis = now,
                type = commandType,
                targetPackageName = request.targetPackageName,
                targetAppName = commandTarget,
                minutes = commandMinutes,
                status = RemoteParentCommandStatus.Applied,
                message = message,
            )
            approvedCommandId = command.id
            preferences[PARENT_MANAGEMENT_STATE] = parentState.copy(
                lastSyncMillis = now,
                remoteCommands = (listOf(command) + parentState.remoteCommands).take(MAX_REMOTE_PARENT_COMMANDS),
                remoteUnlockRequests = parentState.remoteUnlockRequests.map { item ->
                    if (item.id == requestId) item.copy(status = RemoteUnlockRequestStatus.Approved) else item
                }.take(MAX_REMOTE_UNLOCK_REQUESTS),
            ).toParentManagementStateEncoded()
            appendEvent(preferences, EventLogType.Info, message)
            approved = true
            approvedRequest = request
            approvedDecision = ParentRemoteUnlockDecision(
                requestId = requestId,
                approved = true,
                extraMinutes = commandMinutes,
                unlockForToday = unlockForToday,
                decidedAtMillis = now,
            )
        }
        val request = approvedRequest
        val decision = approvedDecision
        if (request != null && decision != null) {
            val remoteResult = parentRemoteSyncDataSource.publishUnlockDecision(request, decision)
            recordParentRemoteSyncResult(
                action = "publish unlock approval",
                result = remoteResult,
            )
            if (remoteResult != ParentRemoteSyncResult.Success) {
                dataStore.edit { preferences ->
                    val current = preferences[PARENT_MANAGEMENT_STATE].orEmpty().toParentManagementState()
                    preferences[TEMPORARY_UNLOCKS] = previousTemporaryUnlocksEncoded
                    preferences[PARENT_MANAGEMENT_STATE] = current.copy(
                        remoteCommands = current.remoteCommands.filterNot { command ->
                            command.id == approvedCommandId
                        },
                        remoteUnlockRequests = current.remoteUnlockRequests.map { item ->
                            if (item.id == requestId && item.status == RemoteUnlockRequestStatus.Approved) {
                                item.copy(status = RemoteUnlockRequestStatus.Pending)
                            } else {
                                item
                            }
                        },
                    ).toParentManagementStateEncoded()
                    appendEvent(
                        preferences,
                        EventLogType.Warning,
                        "Remote unlock approval rolled back: cloud publish failed",
                    )
                }
                return false
            }
        }
        return approved
    }

    suspend fun rejectRemoteUnlockRequest(requestId: String): Boolean {
        if (requestId.isBlank()) {
            return false
        }
        var rejected = false
        var rejectedRequest: RemoteUnlockRequest? = null
        var rejectedDecision: ParentRemoteUnlockDecision? = null
        dataStore.edit { preferences ->
            val parentState = preferences[PARENT_MANAGEMENT_STATE].orEmpty().toParentManagementState()
            val now = System.currentTimeMillis()
            val request = parentState.remoteUnlockRequests.firstOrNull { request -> request.id == requestId }
            if (request == null) {
                return@edit
            }
            preferences[PARENT_MANAGEMENT_STATE] = parentState.copy(
                lastSyncMillis = now,
                remoteUnlockRequests = parentState.remoteUnlockRequests.map { request ->
                    if (request.id == requestId) request.copy(status = RemoteUnlockRequestStatus.Rejected) else request
                }.take(MAX_REMOTE_UNLOCK_REQUESTS),
            ).toParentManagementStateEncoded()
            appendEvent(preferences, EventLogType.Info, "Remote unlock request rejected")
            rejected = true
            rejectedRequest = request
            rejectedDecision = ParentRemoteUnlockDecision(
                requestId = requestId,
                approved = false,
                decidedAtMillis = now,
            )
        }
        val request = rejectedRequest
        val decision = rejectedDecision
        if (request != null && decision != null) {
            val remoteResult = parentRemoteSyncDataSource.publishUnlockDecision(request, decision)
            recordParentRemoteSyncResult(
                action = "publish unlock rejection",
                result = remoteResult,
            )
            if (remoteResult != ParentRemoteSyncResult.Success) {
                dataStore.edit { preferences ->
                    val current = preferences[PARENT_MANAGEMENT_STATE].orEmpty().toParentManagementState()
                    preferences[PARENT_MANAGEMENT_STATE] = current.copy(
                        remoteUnlockRequests = current.remoteUnlockRequests.map { item ->
                            if (item.id == requestId && item.status == RemoteUnlockRequestStatus.Rejected) {
                                item.copy(status = RemoteUnlockRequestStatus.Pending)
                            } else {
                                item
                            }
                        },
                    ).toParentManagementStateEncoded()
                    appendEvent(
                        preferences,
                        EventLogType.Warning,
                        "Remote unlock rejection rolled back: cloud publish failed",
                    )
                }
                return false
            }
        }
        return rejected
    }

    suspend fun saveUsagePolicySettings(settings: UsagePolicySettings, adminPin: String): Boolean {
        return saveUsagePolicyConfiguration(
            settings = settings,
            allowOnlyPackages = allowOnlyAllowedAppPackages.first(),
            allRestrictionsExemptPackages = allRestrictionsExemptPackages.first(),
            adminPin = adminPin,
        )
    }

    suspend fun saveUsagePolicyConfiguration(
        settings: UsagePolicySettings,
        allowOnlyPackages: Set<String>,
        allRestrictionsExemptPackages: Set<String>,
        adminPin: String,
    ): Boolean {
        return saveUsagePolicyConfigurationResult(
            settings = settings,
            allowOnlyPackages = allowOnlyPackages,
            allRestrictionsExemptPackages = allRestrictionsExemptPackages,
            adminPin = adminPin,
        ) == PolicyConfigurationSaveResult.Saved
    }

    suspend fun saveUsagePolicyConfigurationResult(
        settings: UsagePolicySettings,
        allowOnlyPackages: Set<String>,
        allRestrictionsExemptPackages: Set<String>,
        adminPin: String,
    ): PolicyConfigurationSaveResult {
        var result = PolicyConfigurationSaveResult.InvalidAdminPin
        dataStore.edit { preferences ->
            if (!isAdminPinValid(preferences, adminPin.trim())) {
                return@edit
            }
            if (settings.hasStructuralPolicyConflict()) {
                appendEvent(
                    preferences,
                    EventLogType.Warning,
                    "Policy save blocked: duplicate app groups or overlapping schedules",
                )
                result = PolicyConfigurationSaveResult.StructuralConflict
                return@edit
            }
            val runtimeState = preferences[HARDSHIP_RUNTIME_STATE].orEmpty()
                .toHardshipRuntimeState(preferences[HARDSHIP_LAST_EMERGENCY_PASS_AT] ?: 0L)
                .forToday()
            val currentSettings = storedUsagePolicySettings(preferences)
            val currentAllowOnlyPackages = preferences.policyPackageSet(
                currentKey = ALLOW_ONLY_ALLOWED_APP_PACKAGES,
                legacyKey = ALLOWED_APP_PACKAGES,
            )
            val currentExemptPackages = preferences.policyPackageSet(
                currentKey = ALL_RESTRICTIONS_EXEMPT_PACKAGES,
                legacyKey = ALLOWED_APP_PACKAGES,
            )
            val cleanExemptPackages = allRestrictionsExemptPackages.cleanUserPolicyPackageSet()
            val effectiveExemptPackages =
                SafetyGate.expandedUserAllowedPackages(cleanExemptPackages)
            val cleanAllowOnlyPackages =
                allowOnlyPackages.cleanUserPolicyPackageSet() - effectiveExemptPackages
            val weakeningKeys = hardshipWeakeningKeys(currentSettings, settings).toMutableSet()
            if (
                currentSettings.allowOnlyHardshipLevel in setOf(HardshipLevel.Level1, HardshipLevel.Level2) &&
                (cleanAllowOnlyPackages - currentAllowOnlyPackages).isNotEmpty()
            ) {
                weakeningKeys += allowOnlyHardshipKey()
            }
            if ((cleanExemptPackages - currentExemptPackages).isNotEmpty()) {
                weakeningKeys += currentSettings.configuredHardshipPolicyKeys().filter { key ->
                    currentSettings.hardshipLevelFor(key) in
                        setOf(HardshipLevel.Level1, HardshipLevel.Level2)
                }
            }
            if (weakeningKeys.isNotEmpty()) {
                val now = System.currentTimeMillis()
                val reflectionEntries = weakeningKeys.associateWith { key ->
                    runtimeState.reflections[hardshipConfigurationReflectionKey(key)]
                }
                if (reflectionEntries.any { (_, entry) -> entry == null || entry.readyAtMillis <= 0L }) {
                    appendEvent(
                        preferences,
                        EventLogType.Warning,
                        "Policy save blocked: hardship reflection has not started",
                    )
                    result = PolicyConfigurationSaveResult.HardshipReflectionRequired
                    return@edit
                }
                if (reflectionEntries.any { (_, entry) -> now < requireNotNull(entry).readyAtMillis }) {
                    appendEvent(
                        preferences,
                        EventLogType.Warning,
                        "Policy save blocked: hardship reflection is still active",
                    )
                    result = PolicyConfigurationSaveResult.HardshipReflectionWaiting
                    return@edit
                }
            }
            val protectedHardshipKeys = activeHardshipKeys(preferences, runtimeState)
                .filter { key -> currentSettings.hardshipLevelFor(key) == HardshipLevel.Level3 }
                .toSet()
            if (hasProtectedHardshipPolicyChange(preferences, settings, protectedHardshipKeys)) {
                appendEvent(
                    preferences,
                    EventLogType.Warning,
                    "Policy change blocked by active hardship level 3",
                )
                result = PolicyConfigurationSaveResult.HardshipLocked
                return@edit
            }
            if (settings.hasDisabledPolicyWithHardship()) {
                appendEvent(
                    preferences,
                    EventLogType.Warning,
                    "Policy save blocked: hardship requires its base policy to remain enabled",
                )
                result = PolicyConfigurationSaveResult.HardshipLocked
                return@edit
            }
            val cleanScheduleTemplates = settings.normalizedScheduleTemplates().map { schedule ->
                schedule.copy(
                    allowedPackageNames = schedule.allowedPackageNames.cleanUserPolicyPackageSet() -
                        effectiveExemptPackages,
                )
            }
            val hasEnabledSchedules = cleanScheduleTemplates.any { schedule -> schedule.enabled }
            if (
                allowOnlyHardshipKey() in protectedHardshipKeys &&
                (cleanAllowOnlyPackages - currentAllowOnlyPackages).isNotEmpty()
            ) {
                appendEvent(
                    preferences,
                    EventLogType.Warning,
                    "Allow-only app addition blocked by active hardship level 3",
                )
                result = PolicyConfigurationSaveResult.HardshipLocked
                return@edit
            }
            if (
                protectedHardshipKeys.isNotEmpty() &&
                (cleanExemptPackages - currentExemptPackages).isNotEmpty()
            ) {
                appendEvent(
                    preferences,
                    EventLogType.Warning,
                    "All-policy exemption addition blocked by active hardship level 3",
                )
                result = PolicyConfigurationSaveResult.HardshipLocked
                return@edit
            }
            preferences[WEEKDAY_LIMIT_MINUTES] = settings.weekdayLimitMinutes.coerceAtLeast(0)
            preferences[WEEKEND_LIMIT_MINUTES] = settings.weekendLimitMinutes.coerceAtLeast(0)
            preferences[MONDAY_LIMIT_MINUTES] = settings.mondayLimitMinutes.coerceAtLeast(EXPLICIT_ZERO_LIMIT_STORAGE_MINUTES)
            preferences[TUESDAY_LIMIT_MINUTES] = settings.tuesdayLimitMinutes.coerceAtLeast(EXPLICIT_ZERO_LIMIT_STORAGE_MINUTES)
            preferences[WEDNESDAY_LIMIT_MINUTES] = settings.wednesdayLimitMinutes.coerceAtLeast(EXPLICIT_ZERO_LIMIT_STORAGE_MINUTES)
            preferences[THURSDAY_LIMIT_MINUTES] = settings.thursdayLimitMinutes.coerceAtLeast(EXPLICIT_ZERO_LIMIT_STORAGE_MINUTES)
            preferences[FRIDAY_LIMIT_MINUTES] = settings.fridayLimitMinutes.coerceAtLeast(EXPLICIT_ZERO_LIMIT_STORAGE_MINUTES)
            preferences[SATURDAY_LIMIT_MINUTES] = settings.saturdayLimitMinutes.coerceAtLeast(EXPLICIT_ZERO_LIMIT_STORAGE_MINUTES)
            preferences[SUNDAY_LIMIT_MINUTES] = settings.sundayLimitMinutes.coerceAtLeast(EXPLICIT_ZERO_LIMIT_STORAGE_MINUTES)
            preferences[DAILY_POLICY_ENABLED] = settings.dailyPolicyEnabled
            preferences[APP_GROUP_NAME] = settings.appGroupName.trim().ifBlank { "Group" }
            preferences[APP_GROUP_PACKAGES] = settings.appGroupPackages.trim()
            preferences[APP_GROUP_BUDGET_MINUTES] = settings.appGroupBudgetMinutes
                .coerceAtLeast(EXPLICIT_ZERO_LIMIT_STORAGE_MINUTES)
            preferences[APP_GROUPS] = settings.normalizedAppGroups().toAppGroupsEncoded()
            preferences[APP_LIMIT_RULES] = settings.appLimitRules.trim()
            preferences[APP_LIMIT_ACTIVE_DAYS] = settings.appLimitActiveDayMap()
                .toAppLimitActiveDaysEncoded()
            preferences[APP_LIMIT_HARDSHIP_LEVELS] = settings.appLimitHardshipLevelMap()
                .toAppLimitHardshipLevelsEncoded()
            // Keep the legacy section flag synchronized for older app versions,
            // while the current policy is controlled by each schedule item.
            preferences[SCHEDULE_BLOCKING_ENABLED] = hasEnabledSchedules
            preferences[SCHEDULE_START_MINUTES] = settings.scheduleStartMinutes.coerceIn(0, MINUTES_PER_DAY - 1)
            preferences[SCHEDULE_END_MINUTES] = settings.scheduleEndMinutes.coerceIn(0, MINUTES_PER_DAY - 1)
            preferences[SCHEDULE_DAYS] = settings.scheduleDaySet().toScheduleDaysEncoded()
            preferences[SCHEDULE_TEMPLATES] = cleanScheduleTemplates.toScheduleTemplatesEncoded()
            preferences[ACTIVE_SCHEDULE_TEMPLATE_ID] = settings.activeScheduleTemplateId.trim()
            preferences[ALLOW_ONLY_MODE_ENABLED] = settings.allowOnlyModeEnabled
            preferences[DAILY_HARDSHIP_LEVEL] = settings.dailyHardshipLevel.storageValue
            preferences[APP_GROUPS_HARDSHIP_LEVEL] = settings.appGroupsHardshipLevel.storageValue
            preferences[APP_LIMITS_HARDSHIP_LEVEL] = settings.appLimitsHardshipLevel.storageValue
            preferences[SCHEDULE_HARDSHIP_LEVEL] = settings.scheduleHardshipLevel.storageValue
            preferences[ALLOW_ONLY_HARDSHIP_LEVEL] = settings.allowOnlyHardshipLevel.storageValue
            preferences[ALLOW_ONLY_HARDSHIP_END_AT_MILLIS] = settings.allowOnlyHardshipEndAtMillis
            preferences[ALLOW_ONLY_ALLOWED_APP_PACKAGES] = cleanAllowOnlyPackages.toPackageString()
            preferences[ALL_RESTRICTIONS_EXEMPT_PACKAGES] = cleanExemptPackages.toPackageString()
            preferences[POLICY_APP_LISTS_VERSION] = POLICY_APP_LISTS_VERSION_CURRENT
            // Schedule hardship becomes active only when its schedule window starts.
            // Other level-3 policies begin immediately when the configuration is saved.
            val newlyStartedLevel3Keys = (settings.level3HardshipPolicyKeys() -
                currentSettings.level3HardshipPolicyKeys()).filterTo(mutableSetOf()) { key ->
                key.policyType != HardshipPolicyType.Schedule
            }
            val runtimeStateAfterReflection = runtimeState.copy(
                reflections = runtimeState.reflections - weakeningKeys.map(::hardshipConfigurationReflectionKey).toSet(),
            )
            if (newlyStartedLevel3Keys.isNotEmpty()) {
                preferences[HARDSHIP_RUNTIME_STATE] = runtimeStateAfterReflection.copy(
                    bypassedPolicyKeys = runtimeState.bypassedPolicyKeys - newlyStartedLevel3Keys,
                    bypassedPolicies = runtimeState.bypassedPolicies -
                        newlyStartedLevel3Keys.map { key -> key.policyType }.toSet(),
                    activePolicyKeys = runtimeState.activePolicyKeys + newlyStartedLevel3Keys,
                    activePolicies = runtimeState.activePolicies +
                        newlyStartedLevel3Keys.map { key -> key.policyType },
                    emergencyPassGrants = runtimeState.emergencyPassGrants.filterNot { grant ->
                        grant.policyKey in newlyStartedLevel3Keys
                    },
                ).toHardshipRuntimeStateEncoded()
                appendEvent(
                    preferences,
                    EventLogType.Safety,
                    "Hardship level 3 started: ${newlyStartedLevel3Keys.joinToString { key -> key.storageKey }}",
                )
            } else {
                // Persist today's date even for level 1/2 so their next-day expiry is
                // deterministic even when the policy has not yet produced a block.
                preferences[HARDSHIP_RUNTIME_STATE] = runtimeStateAfterReflection.toHardshipRuntimeStateEncoded()
            }
            appendEvent(
                preferences = preferences,
                type = EventLogType.Info,
                message = "Policy saved: allow-only=${cleanAllowOnlyPackages.size}, exemptions=${cleanExemptPackages.size}",
            )
            result = PolicyConfigurationSaveResult.Saved
        }
        return result
    }

    suspend fun isPolicyChangeBlockedByHardship(
        settings: UsagePolicySettings,
        allowedPackages: Set<String>,
        allRestrictionsExemptPackages: Set<String> = emptySet(),
    ): Boolean {
        val currentPreferences = preferences.first()
        val runtimeState = currentPreferences[HARDSHIP_RUNTIME_STATE].orEmpty()
            .toHardshipRuntimeState(currentPreferences[HARDSHIP_LAST_EMERGENCY_PASS_AT] ?: 0L)
            .forToday()
        val currentSettings = storedUsagePolicySettings(currentPreferences)
        val activeKeys = activeHardshipKeys(currentPreferences, runtimeState)
            .filter { key -> currentSettings.hardshipLevelFor(key) == HardshipLevel.Level3 }
            .toSet()
        if (hasProtectedHardshipPolicyChange(currentPreferences, settings, activeKeys)) {
            return true
        }
        if (allowOnlyHardshipKey() in activeKeys) {
            val currentAllowedPackages = currentPreferences.policyPackageSet(
                currentKey = ALLOW_ONLY_ALLOWED_APP_PACKAGES,
                legacyKey = ALLOWED_APP_PACKAGES,
            )
            val requestedAllowedPackages = allowedPackages.cleanUserPolicyPackageSet()
            if ((requestedAllowedPackages - currentAllowedPackages).isNotEmpty()) return true
        }
        if (activeKeys.isNotEmpty()) {
            val currentExemptPackages = currentPreferences.policyPackageSet(
                currentKey = ALL_RESTRICTIONS_EXEMPT_PACKAGES,
                legacyKey = ALLOWED_APP_PACKAGES,
            )
            val requestedExemptPackages = allRestrictionsExemptPackages.cleanUserPolicyPackageSet()
            if ((requestedExemptPackages - currentExemptPackages).isNotEmpty()) return true
        }
        return false
    }

    private fun hasProtectedHardshipPolicyChange(
        preferences: Preferences,
        settings: UsagePolicySettings,
        activeKeys: Set<HardshipPolicyKey>,
    ): Boolean {
        val current = storedUsagePolicySettings(preferences)
        return activeKeys.any { policyKey ->
            if (current.hardshipLevelFor(policyKey) != HardshipLevel.Level3) {
                return@any false
            }
            when (policyKey.policyType) {
                HardshipPolicyType.DailyLimit ->
                    settings.dailyHardshipLevel != HardshipLevel.Level3 ||
                        settings.dailyPolicyEnabled != current.dailyPolicyEnabled ||
                        settings.mondayLimitMinutes != current.mondayLimitMinutes ||
                        settings.tuesdayLimitMinutes != current.tuesdayLimitMinutes ||
                        settings.wednesdayLimitMinutes != current.wednesdayLimitMinutes ||
                        settings.thursdayLimitMinutes != current.thursdayLimitMinutes ||
                        settings.fridayLimitMinutes != current.fridayLimitMinutes ||
                        settings.saturdayLimitMinutes != current.saturdayLimitMinutes ||
                        settings.sundayLimitMinutes != current.sundayLimitMinutes

                HardshipPolicyType.AppGroups -> {
                    val savedGroup = current.normalizedAppGroups().firstOrNull { it.id == policyKey.targetId }
                    val editedGroup = settings.normalizedAppGroups().firstOrNull { it.id == policyKey.targetId }
                    savedGroup == null || editedGroup != savedGroup
                }

                HardshipPolicyType.AppLimits -> {
                    val savedLimit = current.appLimitMinutesFor(policyKey.targetId)
                    val editedLimit = settings.appLimitMinutesFor(policyKey.targetId)
                    val savedDays = current.appLimitActiveDayMap()[policyKey.targetId]
                    val editedDays = settings.appLimitActiveDayMap()[policyKey.targetId]
                    savedLimit == null || editedLimit != savedLimit ||
                        editedDays != savedDays ||
                        settings.hardshipLevelFor(policyKey) != HardshipLevel.Level3
                }

                HardshipPolicyType.Schedule -> {
                    val savedSchedule = current.normalizedScheduleTemplates()
                        .firstOrNull { it.id == policyKey.targetId }
                    val editedSchedule = settings.normalizedScheduleTemplates()
                        .firstOrNull { it.id == policyKey.targetId }
                    savedSchedule == null || editedSchedule != savedSchedule ||
                        settings.scheduleBlockingEnabled != current.scheduleBlockingEnabled
                }

                HardshipPolicyType.AllowOnly ->
                    settings.allowOnlyHardshipLevel != HardshipLevel.Level3 ||
                        settings.allowOnlyModeEnabled != current.allowOnlyModeEnabled ||
                        settings.allowOnlyHardshipEndAtMillis != current.allowOnlyHardshipEndAtMillis
            }
        }
    }

    private fun activeHardshipKeys(
        preferences: Preferences,
        runtimeState: HardshipRuntimeState,
    ): Set<HardshipPolicyKey> {
        val current = storedUsagePolicySettings(preferences)
        val keyedTypes = runtimeState.activePolicyKeys.map { key -> key.policyType }.toSet()
        val migratedLegacyKeys = (runtimeState.activePolicies - keyedTypes).flatMap { policyType ->
            when (policyType) {
                HardshipPolicyType.DailyLimit -> listOf(dailyHardshipKey())
                HardshipPolicyType.AppGroups -> current.normalizedAppGroups()
                    .filter { group -> group.hardshipLevel == HardshipLevel.Level3 }
                    .map { group -> appGroupHardshipKey(group.id) }
                HardshipPolicyType.AppLimits -> current.appLimitHardshipLevelMap()
                    .filterValues { level -> level == HardshipLevel.Level3 }
                    .keys.map(::appLimitHardshipKey)
                HardshipPolicyType.Schedule -> current.normalizedScheduleTemplates()
                    .filter { schedule -> schedule.hardshipLevel == HardshipLevel.Level3 }
                    .map { schedule -> scheduleHardshipKey(schedule.id) }
                HardshipPolicyType.AllowOnly -> listOf(allowOnlyHardshipKey())
            }
        }.toSet()
        return runtimeState.activePolicyKeys + migratedLegacyKeys
    }

    private fun storedUsagePolicySettings(preferences: Preferences): UsagePolicySettings {
        return UsagePolicySettings(
            mondayLimitMinutes = preferences[MONDAY_LIMIT_MINUTES] ?: 120,
            tuesdayLimitMinutes = preferences[TUESDAY_LIMIT_MINUTES] ?: 120,
            wednesdayLimitMinutes = preferences[WEDNESDAY_LIMIT_MINUTES] ?: 120,
            thursdayLimitMinutes = preferences[THURSDAY_LIMIT_MINUTES] ?: 120,
            fridayLimitMinutes = preferences[FRIDAY_LIMIT_MINUTES] ?: 120,
            saturdayLimitMinutes = preferences[SATURDAY_LIMIT_MINUTES] ?: 240,
            sundayLimitMinutes = preferences[SUNDAY_LIMIT_MINUTES] ?: 240,
            dailyPolicyEnabled = preferences.dailyPolicyEnabledOrMigrated(),
            appGroups = preferences[APP_GROUPS].orEmpty(),
            appLimitRules = preferences[APP_LIMIT_RULES].orEmpty(),
            appLimitActiveDays = preferences[APP_LIMIT_ACTIVE_DAYS].orEmpty(),
            appLimitHardshipLevels = preferences[APP_LIMIT_HARDSHIP_LEVELS].orEmpty(),
            scheduleBlockingEnabled = preferences[SCHEDULE_BLOCKING_ENABLED] ?: false,
            scheduleTemplates = preferences[SCHEDULE_TEMPLATES].orEmpty(),
            allowOnlyModeEnabled = preferences[ALLOW_ONLY_MODE_ENABLED] ?: false,
            dailyHardshipLevel = HardshipLevel.fromStorageValue(preferences[DAILY_HARDSHIP_LEVEL]),
            appGroupsHardshipLevel = HardshipLevel.fromStorageValue(preferences[APP_GROUPS_HARDSHIP_LEVEL]),
            appLimitsHardshipLevel = HardshipLevel.fromStorageValue(preferences[APP_LIMITS_HARDSHIP_LEVEL]),
            scheduleHardshipLevel = HardshipLevel.fromStorageValue(preferences[SCHEDULE_HARDSHIP_LEVEL]),
            allowOnlyHardshipLevel = HardshipLevel.fromStorageValue(preferences[ALLOW_ONLY_HARDSHIP_LEVEL]),
            allowOnlyHardshipEndAtMillis = preferences[ALLOW_ONLY_HARDSHIP_END_AT_MILLIS] ?: 0L,
        )
    }

    private fun Preferences.dailyPolicyEnabledOrMigrated(): Boolean {
        return this[DAILY_POLICY_ENABLED] ?: listOf(
            this[MONDAY_LIMIT_MINUTES] ?: 120,
            this[TUESDAY_LIMIT_MINUTES] ?: 120,
            this[WEDNESDAY_LIMIT_MINUTES] ?: 120,
            this[THURSDAY_LIMIT_MINUTES] ?: 120,
            this[FRIDAY_LIMIT_MINUTES] ?: 120,
            this[SATURDAY_LIMIT_MINUTES] ?: 240,
            this[SUNDAY_LIMIT_MINUTES] ?: 240,
        ).any { minutes -> minutes != 0 }
    }

    suspend fun updateAdminPin(currentPin: String, newPin: String): Boolean {
        return updatePin(
            currentPin = currentPin,
            newPin = newPin,
        )
    }

    suspend fun activateKillSwitch(adminPin: String): Boolean {
        var activated = false
        dataStore.edit { preferences ->
            if (!isAdminPinValid(preferences, adminPin.trim())) {
                appendEvent(preferences, EventLogType.Warning, "Kill Switch failed: invalid Admin PIN")
                return@edit
            }
            val runtimeState = preferences[HARDSHIP_RUNTIME_STATE].orEmpty()
                .toHardshipRuntimeState(preferences[HARDSHIP_LAST_EMERGENCY_PASS_AT] ?: 0L)
                .forToday()
            val currentSettings = storedUsagePolicySettings(preferences)
            val level3Active = activeHardshipKeys(preferences, runtimeState).any { key ->
                currentSettings.hardshipLevelFor(key) == HardshipLevel.Level3
            }
            if (level3Active) {
                appendEvent(
                    preferences,
                    EventLogType.Warning,
                    "Kill Switch blocked by active hardship level 3; use Emergency Pass",
                )
                return@edit
            }
            applySafeRecovery(preferences)
            appendEvent(
                preferences = preferences,
                type = EventLogType.Safety,
                message = "Kill Switch activated: Safe Mode ON, policy OFF, temporary allowances cleared",
            )
            activated = true
        }
        return activated
    }

    suspend fun markAppStartedAndRecoverIfNeeded(): Boolean {
        var recovered = false
        dataStore.edit { preferences ->
            if ((preferences[PIN_SCHEMA_VERSION] ?: 0) < PIN_SCHEMA_VERSION_CURRENT) {
                preferences.remove(EMERGENCY_PIN_CREDENTIAL)
                preferences.remove(EMERGENCY_UNLOCK_PIN)
                preferences.remove(EMERGENCY_PIN_FAILURE_COUNT)
                preferences.remove(EMERGENCY_PIN_LOCK_UNTIL)
                preferences[PIN_SCHEMA_VERSION] = PIN_SCHEMA_VERSION_CURRENT
                appendEvent(
                    preferences,
                    EventLogType.Safety,
                    "Emergency PIN retired; Admin PIN preserved",
                )
            }
            if ((preferences[HARDSHIP_LEVEL3_LOCK_VERSION] ?: 0) < HARDSHIP_LEVEL3_LOCK_VERSION_CURRENT) {
                val currentSettings = storedUsagePolicySettings(preferences)
                val configuredLevel3Keys = currentSettings.level3HardshipPolicyKeys()
                    .filterTo(mutableSetOf()) { key ->
                        key.policyType != HardshipPolicyType.Schedule
                    }
                if (configuredLevel3Keys.isNotEmpty()) {
                    val runtimeState = preferences[HARDSHIP_RUNTIME_STATE].orEmpty()
                        .toHardshipRuntimeState(preferences[HARDSHIP_LAST_EMERGENCY_PASS_AT] ?: 0L)
                        .forToday()
                    preferences[HARDSHIP_RUNTIME_STATE] = runtimeState.copy(
                        bypassedPolicyKeys = runtimeState.bypassedPolicyKeys - configuredLevel3Keys,
                        bypassedPolicies = runtimeState.bypassedPolicies -
                            configuredLevel3Keys.map { key -> key.policyType }.toSet(),
                        activePolicyKeys = runtimeState.activePolicyKeys + configuredLevel3Keys,
                        activePolicies = runtimeState.activePolicies +
                            configuredLevel3Keys.map { key -> key.policyType },
                        emergencyPassGrants = runtimeState.emergencyPassGrants.filterNot { grant ->
                            grant.policyKey in configuredLevel3Keys
                        },
                    ).toHardshipRuntimeStateEncoded()
                    if (dailyHardshipKey() in configuredLevel3Keys) {
                        preferences[DAILY_POLICY_ENABLED] = true
                    }
                    if (allowOnlyHardshipKey() in configuredLevel3Keys) {
                        preferences[ALLOW_ONLY_MODE_ENABLED] = true
                        preferences[ALLOW_ONLY_HARDSHIP_END_AT_MILLIS] = nextLocalMidnightMillis()
                    }
                    appendEvent(
                        preferences,
                        EventLogType.Safety,
                        "Existing hardship level 3 lock restored",
                    )
                }
                preferences[HARDSHIP_LEVEL3_LOCK_VERSION] = HARDSHIP_LEVEL3_LOCK_VERSION_CURRENT
            }
            val previousRunClean = preferences[LAST_RUN_CLEAN] ?: true
            if (!previousRunClean) {
                val runtimeState = preferences[HARDSHIP_RUNTIME_STATE].orEmpty()
                    .toHardshipRuntimeState(preferences[HARDSHIP_LAST_EMERGENCY_PASS_AT] ?: 0L)
                    .forToday()
                val currentSettings = storedUsagePolicySettings(preferences)
                val level3Active = activeHardshipKeys(preferences, runtimeState).any { key ->
                    currentSettings.hardshipLevelFor(key) == HardshipLevel.Level3
                }
                if (level3Active) {
                    preferences[SAFE_MODE_ENABLED] = false
                    preferences[POLICY_ENFORCEMENT_ENABLED] = true
                    appendEvent(
                        preferences = preferences,
                        type = EventLogType.Safety,
                        message = "Auto Recovery preserved active hardship level 3",
                    )
                } else {
                    applySafeRecovery(preferences)
                    appendEvent(
                        preferences = preferences,
                        type = EventLogType.Safety,
                        message = "Auto Recovery enabled Safe Mode",
                    )
                    recovered = true
                }
            }
            preferences[LAST_RUN_CLEAN] = false
        }
        return recovered
    }

    suspend fun markAppStoppedCleanly() {
        dataStore.edit { preferences ->
            preferences[LAST_RUN_CLEAN] = true
        }
    }

    suspend fun safeRecovery(adminPin: String): Boolean {
        var unlocked = false
        dataStore.edit { preferences ->
            val runtimeState = preferences[HARDSHIP_RUNTIME_STATE].orEmpty()
                .toHardshipRuntimeState(preferences[HARDSHIP_LAST_EMERGENCY_PASS_AT] ?: 0L)
                .forToday()
            val currentSettings = storedUsagePolicySettings(preferences)
            val level3Active = activeHardshipKeys(preferences, runtimeState).any { key ->
                currentSettings.hardshipLevelFor(key) == HardshipLevel.Level3
            }
            if (level3Active) {
                appendEvent(
                    preferences = preferences,
                    type = EventLogType.Warning,
                    message = "Safe Recovery blocked by active hardship level 3; use Emergency Pass",
                )
                return@edit
            }
            unlocked = isAdminPinValid(preferences, adminPin.trim())
            if (unlocked) {
                applySafeRecovery(preferences)
                appendEvent(
                    preferences = preferences,
                    type = EventLogType.Safety,
                    message = "Safe Recovery succeeded",
                )
            } else {
                appendEvent(
                    preferences = preferences,
                    type = EventLogType.Warning,
                    message = "Safe Recovery failed: invalid Admin PIN",
                )
            }
        }
        return unlocked
    }

    suspend fun verifyAdminPin(adminPin: String): Boolean {
        var valid = false
        dataStore.edit { preferences ->
            valid = isAdminPinValid(preferences, adminPin.trim())
        }
        return valid
    }

    suspend fun hasActiveLevel3Hardship(): Boolean {
        val currentPreferences = preferences.first()
        val runtimeState = currentPreferences[HARDSHIP_RUNTIME_STATE].orEmpty()
            .toHardshipRuntimeState(currentPreferences[HARDSHIP_LAST_EMERGENCY_PASS_AT] ?: 0L)
            .forToday()
        val currentSettings = storedUsagePolicySettings(currentPreferences)
        return activeHardshipKeys(currentPreferences, runtimeState).any { key ->
            currentSettings.hardshipLevelFor(key) == HardshipLevel.Level3
        }
    }

    /**
     * Reconciles the persisted hardship configuration with its real lifetime.
     *
     * Daily/group/app/allow-only hardship lasts for the local day on which it was
     * configured. Schedule hardship is one occurrence only and lasts until that
     * occurrence's end, including schedules that cross midnight.
     */
    suspend fun reconcileHardshipLifecycle(
        nowMillis: Long = System.currentTimeMillis(),
    ): HardshipLifecycleResult {
        var result = HardshipLifecycleResult()
        dataStore.edit { preferences ->
            val todayKey = currentTemporaryUnlockDateKey()
            val rawRuntimeState = preferences[HARDSHIP_RUNTIME_STATE].orEmpty()
                .toHardshipRuntimeState(preferences[HARDSHIP_LAST_EMERGENCY_PASS_AT] ?: 0L)
            val dayChanged = rawRuntimeState.dateKey.isNotBlank() &&
                rawRuntimeState.dateKey != todayKey

            if (dayChanged) {
                val beforeRollover = storedUsagePolicySettings(preferences)
                val hadDailyHardship = beforeRollover.dailyHardshipLevel != HardshipLevel.Off ||
                    beforeRollover.normalizedAppGroups().any { group ->
                        group.hardshipLevel != HardshipLevel.Off
                    } ||
                    beforeRollover.appLimitHardshipLevelMap().values.any { level ->
                        level != HardshipLevel.Off
                    } ||
                    beforeRollover.allowOnlyHardshipLevel != HardshipLevel.Off
                preferences[DAILY_HARDSHIP_LEVEL] = HardshipLevel.Off.storageValue
                preferences[APP_GROUPS] = beforeRollover.normalizedAppGroups()
                    .map { group -> group.copy(hardshipLevel = HardshipLevel.Off) }
                    .toAppGroupsEncoded()
                preferences[APP_GROUPS_HARDSHIP_LEVEL] = HardshipLevel.Off.storageValue
                preferences[APP_LIMIT_HARDSHIP_LEVELS] = ""
                preferences[APP_LIMITS_HARDSHIP_LEVEL] = HardshipLevel.Off.storageValue
                preferences[ALLOW_ONLY_HARDSHIP_LEVEL] = HardshipLevel.Off.storageValue
                preferences[ALLOW_ONLY_HARDSHIP_END_AT_MILLIS] = 0L
                result = result.copy(dailyHardshipCleared = hadDailyHardship)
                if (hadDailyHardship) {
                    appendEvent(
                        preferences,
                        EventLogType.Info,
                        "Non-schedule hardship ended at daily rollover: $todayKey",
                    )
                }
            }

            var runtimeState = rawRuntimeState.forToday(todayKey, nowMillis)
            val now = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(nowMillis),
                ZoneId.systemDefault(),
            )
            var settings = storedUsagePolicySettings(preferences)
            val disabledHardshipGroups = settings.normalizedAppGroups().filter { group ->
                !group.enabled && group.hardshipLevel != HardshipLevel.Off
            }
            if (disabledHardshipGroups.isNotEmpty()) {
                preferences[APP_GROUPS] = settings.normalizedAppGroups()
                    .map { group ->
                        if (group in disabledHardshipGroups) group.copy(enabled = true) else group
                    }
                    .toAppGroupsEncoded()
                settings = storedUsagePolicySettings(preferences)
                appendEvent(
                    preferences,
                    EventLogType.Safety,
                    "App group restored because configured hardship is still active",
                )
            }
            val disabledHardshipSchedules = settings.normalizedScheduleTemplates().filter { schedule ->
                !schedule.enabled && schedule.hardshipLevel != HardshipLevel.Off
            }
            val restoredDisabledSchedule = !settings.scheduleBlockingEnabled || disabledHardshipSchedules.isNotEmpty()
            if (
                restoredDisabledSchedule &&
                settings.normalizedScheduleTemplates().any { schedule ->
                    schedule.hardshipLevel != HardshipLevel.Off
                }
            ) {
                val repairedSchedules = settings.normalizedScheduleTemplates().map { schedule ->
                    if (schedule.hardshipLevel != HardshipLevel.Off) schedule.copy(enabled = true) else schedule
                }
                preferences[SCHEDULE_TEMPLATES] = repairedSchedules.toScheduleTemplatesEncoded()
                preferences[SCHEDULE_BLOCKING_ENABLED] = true
                settings = storedUsagePolicySettings(preferences)
                appendEvent(
                    preferences,
                    EventLogType.Safety,
                    "Schedule restored because configured hardship is still active",
                )
            }
            val started = mutableListOf<ScheduleHardshipStarted>()
            val endedIds = mutableSetOf<String>()
            val migratedRuntimeActiveKeys = activeHardshipKeys(preferences, runtimeState)
            val migratedLegacyScheduleLock =
                HardshipPolicyType.Schedule in runtimeState.activePolicies &&
                    runtimeState.activePolicyKeys.none { key ->
                        key.policyType == HardshipPolicyType.Schedule
                    }

            val nextSchedules = settings.normalizedScheduleTemplates().map { schedule ->
                if (schedule.hardshipLevel == HardshipLevel.Off) {
                    schedule.copy(hardshipEndAtMillis = 0L)
                } else {
                    val key = scheduleHardshipKey(schedule.id)
                    val activeNow = settings.scheduleBlockingEnabled && schedule.isActiveAt(now)
                    val wasActive = key in migratedRuntimeActiveKeys ||
                        (migratedLegacyScheduleLock && schedule.hardshipLevel == HardshipLevel.Level3)
                    val endAtMillis = schedule.hardshipEndAtMillis.takeIf { endAt -> endAt > 0L }
                        ?: when {
                            activeNow -> schedule.currentOccurrenceEndMillis(now)
                            wasActive -> nowMillis
                            else -> schedule.nextOccurrenceEndMillis(now)
                        }
                    val expired = endAtMillis <= nowMillis || (wasActive && !activeNow)
                    when {
                        expired -> {
                            endedIds += schedule.id
                            schedule.copy(
                                hardshipLevel = HardshipLevel.Off,
                                hardshipEndAtMillis = 0L,
                            )
                        }
                        activeNow -> {
                            if (
                                key !in runtimeState.activePolicyKeys &&
                                key !in runtimeState.bypassedPolicyKeys
                            ) {
                                started += ScheduleHardshipStarted(
                                    scheduleId = schedule.id,
                                    scheduleName = schedule.name,
                                    level = schedule.hardshipLevel,
                                    endsAtMillis = endAtMillis,
                                )
                            }
                            schedule.copy(hardshipEndAtMillis = endAtMillis)
                        }
                        else -> schedule.copy(hardshipEndAtMillis = endAtMillis)
                    }
                }
            }

            val currentScheduleKeys = nextSchedules
                .filter { schedule ->
                    schedule.hardshipLevel != HardshipLevel.Off &&
                        settings.scheduleBlockingEnabled &&
                        schedule.isActiveAt(now)
                }
                .mapTo(mutableSetOf()) { schedule -> scheduleHardshipKey(schedule.id) }
            val currentActiveScheduleKeys = currentScheduleKeys - runtimeState.bypassedPolicyKeys
            val endedScheduleKeys = endedIds.mapTo(mutableSetOf(), ::scheduleHardshipKey)
            val retainedNonScheduleActiveKeys = migratedRuntimeActiveKeys.filterTo(mutableSetOf()) { key ->
                key.policyType != HardshipPolicyType.Schedule &&
                    settings.hardshipLevelFor(key) != HardshipLevel.Off
            }
            val nextActiveKeys = retainedNonScheduleActiveKeys + currentActiveScheduleKeys
            val retainedBypassedKeys = runtimeState.bypassedPolicyKeys.filterTo(mutableSetOf()) { key ->
                key.policyType != HardshipPolicyType.Schedule || key in currentScheduleKeys
            }
            val invalidScheduleKeys = (
                runtimeState.activePolicyKeys + runtimeState.bypassedPolicyKeys + endedScheduleKeys
                ).filterTo(mutableSetOf()) { key ->
                key.policyType == HardshipPolicyType.Schedule && key !in currentScheduleKeys
            }
            runtimeState = runtimeState.copy(
                activePolicyKeys = nextActiveKeys,
                activePolicies = nextActiveKeys.mapTo(mutableSetOf()) { key -> key.policyType },
                bypassedPolicyKeys = retainedBypassedKeys,
                bypassedPolicies = runtimeState.bypassedPolicies.filterTo(mutableSetOf()) { policyType ->
                    policyType != HardshipPolicyType.Schedule ||
                        retainedBypassedKeys.any { key -> key.policyType == HardshipPolicyType.Schedule }
                },
                reflections = runtimeState.reflections.filterKeys { reflectionKey ->
                    invalidScheduleKeys.none { key -> reflectionKey.contains(key.storageKey) }
                },
                emergencyPassGrants = runtimeState.emergencyPassGrants.filterNot { grant ->
                    grant.policyKey in invalidScheduleKeys
                },
            )
            preferences[SCHEDULE_TEMPLATES] = nextSchedules.toScheduleTemplatesEncoded()
            if (
                restoredDisabledSchedule &&
                nextSchedules.none { schedule -> schedule.hardshipLevel != HardshipLevel.Off }
            ) {
                preferences[SCHEDULE_BLOCKING_ENABLED] = false
            }
            preferences[SCHEDULE_HARDSHIP_LEVEL] = nextSchedules
                .maxByOrNull { schedule -> schedule.hardshipLevel.storageValue }
                ?.hardshipLevel
                ?.storageValue
                ?: HardshipLevel.Off.storageValue
            preferences[HARDSHIP_RUNTIME_STATE] = runtimeState.toHardshipRuntimeStateEncoded()

            started.forEach { schedule ->
                appendEvent(
                    preferences,
                    EventLogType.Safety,
                    "Schedule hardship started: ${schedule.scheduleName} level ${schedule.level.storageValue}",
                )
            }
            endedIds.forEach { scheduleId ->
                appendEvent(
                    preferences,
                    EventLogType.Info,
                    "Schedule hardship ended with schedule: $scheduleId",
                )
            }
            result = result.copy(
                startedSchedules = started,
                endedScheduleIds = endedIds,
            )
        }
        return result
    }

    suspend fun markHardshipPolicyTriggered(policyKey: HardshipPolicyKey) {
        dataStore.edit { preferences ->
            val runtimeState = preferences[HARDSHIP_RUNTIME_STATE].orEmpty()
                .toHardshipRuntimeState(preferences[HARDSHIP_LAST_EMERGENCY_PASS_AT] ?: 0L)
                .forToday()
            if (policyKey in runtimeState.bypassedPolicyKeys || policyKey in runtimeState.activePolicyKeys) {
                return@edit
            }
            preferences[HARDSHIP_RUNTIME_STATE] = runtimeState.copy(
                activePolicyKeys = runtimeState.activePolicyKeys + policyKey,
                activePolicies = runtimeState.activePolicies + policyKey.policyType,
            ).toHardshipRuntimeStateEncoded()
            appendEvent(preferences, EventLogType.Info, "Hardship policy triggered: ${policyKey.storageKey}")
        }
    }

    suspend fun clearHardshipPolicyTriggered(policyKey: HardshipPolicyKey) {
        dataStore.edit { preferences ->
            val runtimeState = preferences[HARDSHIP_RUNTIME_STATE].orEmpty()
                .toHardshipRuntimeState(preferences[HARDSHIP_LAST_EMERGENCY_PASS_AT] ?: 0L)
                .forToday()
            if (
                policyKey !in runtimeState.activePolicyKeys &&
                policyKey.policyType !in runtimeState.activePolicies
            ) return@edit
            val nextKeys = runtimeState.activePolicyKeys - policyKey
            val nextPolicyTypes = runtimeState.activePolicies.toMutableSet().apply {
                if (nextKeys.none { key -> key.policyType == policyKey.policyType }) {
                    remove(policyKey.policyType)
                }
                addAll(nextKeys.map { key -> key.policyType })
            }.toSet()
            preferences[HARDSHIP_RUNTIME_STATE] = runtimeState.copy(
                activePolicyKeys = nextKeys,
                activePolicies = nextPolicyTypes,
            ).toHardshipRuntimeStateEncoded()
            appendEvent(preferences, EventLogType.Info, "Hardship policy ended naturally: ${policyKey.storageKey}")
        }
    }

    suspend fun startHardshipConfigurationReflection(policyKey: HardshipPolicyKey): Boolean {
        var startedOrAlreadyActive = false
        dataStore.edit { preferences ->
            val currentSettings = storedUsagePolicySettings(preferences)
            val currentLevel = currentSettings.hardshipLevelFor(policyKey)
            val waitMillis = hardshipConfigurationWaitMillis(currentLevel)
            if (waitMillis <= 0L) return@edit
            val now = System.currentTimeMillis()
            val runtimeState = preferences[HARDSHIP_RUNTIME_STATE].orEmpty()
                .toHardshipRuntimeState(preferences[HARDSHIP_LAST_EMERGENCY_PASS_AT] ?: 0L)
                .forToday()
            val reflectionKey = hardshipConfigurationReflectionKey(policyKey)
            val existing = runtimeState.reflections[reflectionKey]
            if (existing == null || existing.readyAtMillis <= 0L) {
                preferences[HARDSHIP_RUNTIME_STATE] = runtimeState.copy(
                    reflections = runtimeState.reflections + (
                        reflectionKey to HardshipReflectionEntry(readyAtMillis = now + waitMillis)
                    ),
                ).toHardshipRuntimeStateEncoded()
                appendEvent(
                    preferences,
                    EventLogType.Info,
                    "Hardship configuration reflection started: ${policyKey.storageKey}",
                )
            }
            startedOrAlreadyActive = true
        }
        return startedOrAlreadyActive
    }

    suspend fun requestLevelOneAllowance(
        policyKey: HardshipPolicyKey,
        packageName: String,
        appName: String,
    ): HardshipLevelOneGrantResult {
        var result = HardshipLevelOneGrantResult.NotAvailable
        dataStore.edit { preferences ->
            if (preferences.hardshipLevelFor(policyKey) != HardshipLevel.Level1) {
                return@edit
            }
            if (policyKey.policyType != HardshipPolicyType.DailyLimit && packageName.isBlank()) {
                return@edit
            }
            val now = System.currentTimeMillis()
            val runtimeState = preferences[HARDSHIP_RUNTIME_STATE].orEmpty()
                .toHardshipRuntimeState(preferences[HARDSHIP_LAST_EMERGENCY_PASS_AT] ?: 0L)
                .forToday()
            val reflectionKey = hardshipReflectionKey(policyKey, packageName)
            val currentEntry = runtimeState.reflections[reflectionKey] ?: HardshipReflectionEntry()
            when {
                currentEntry.useCount >= HARDSHIP_LEVEL_ONE_DAILY_USE_LIMIT -> {
                    result = HardshipLevelOneGrantResult.DailyLimitReached
                }
                currentEntry.readyAtMillis <= 0L -> {
                    val nextState = runtimeState.copy(
                        reflections = runtimeState.reflections + (
                            reflectionKey to currentEntry.copy(readyAtMillis = now + HARDSHIP_LEVEL_ONE_WAIT_MILLIS)
                        ),
                    )
                    preferences[HARDSHIP_RUNTIME_STATE] = nextState.toHardshipRuntimeStateEncoded()
                    appendEvent(
                        preferences,
                        EventLogType.Info,
                        "Hardship level 1 reflection started: ${policyKey.storageKey} ${appName.ifBlank { packageName }}",
                    )
                    result = HardshipLevelOneGrantResult.WaitingStarted
                }
                now < currentEntry.readyAtMillis -> {
                    result = HardshipLevelOneGrantResult.Waiting
                }
                else -> {
                    val temporaryState = preferences[TEMPORARY_UNLOCKS].orEmpty()
                        .toTemporaryUnlockState()
                        .forToday()
                    val nextTemporaryState = if (policyKey.policyType == HardshipPolicyType.DailyLimit) {
                        temporaryState.copy(
                            totalExtraMinutes = (temporaryState.totalExtraMinutes + HARDSHIP_LEVEL_ONE_ALLOWANCE_MINUTES)
                                .coerceAtMost(MAX_TEMPORARY_EXTRA_MINUTES),
                        )
                    } else {
                        val currentAllowance = temporaryState.packageAllowances[packageName]
                            ?: TemporaryPackageAllowance()
                        temporaryState.copy(
                            packageAllowances = temporaryState.packageAllowances + (
                                packageName to currentAllowance
                                    .withExtraTime(
                                        extraMinutes = HARDSHIP_LEVEL_ONE_ALLOWANCE_MINUTES,
                                        nowMillis = now,
                                    )
                                    .copy(
                                        hardshipAllowanceUntilMillis = maxOf(
                                            currentAllowance.hardshipAllowanceUntilMillis,
                                            now + HARDSHIP_LEVEL_ONE_ALLOWANCE_MINUTES * 60_000L,
                                        ),
                                    )
                            ),
                        )
                    }
                    preferences[TEMPORARY_UNLOCKS] = nextTemporaryState.toTemporaryUnlocksEncoded()
                    preferences[HARDSHIP_RUNTIME_STATE] = runtimeState.copy(
                        reflections = runtimeState.reflections + (
                            reflectionKey to HardshipReflectionEntry(
                                readyAtMillis = 0L,
                                useCount = currentEntry.useCount + 1,
                            )
                        ),
                    ).toHardshipRuntimeStateEncoded()
                    appendEvent(
                        preferences,
                        EventLogType.Info,
                        "Hardship level 1 granted 5m: ${policyKey.storageKey} ${appName.ifBlank { packageName }}",
                    )
                    result = HardshipLevelOneGrantResult.Granted
                }
            }
        }
        return result
    }

    suspend fun useHardshipEmergencyPass(
        adminPin: String,
        packageName: String,
        blockingPolicyKeys: Set<HardshipPolicyKey>,
    ): EmergencyPassUseResult {
        var result = EmergencyPassUseResult.NotAvailable
        dataStore.edit { preferences ->
            val currentSettings = storedUsagePolicySettings(preferences)
            val cleanPolicyKeys = blockingPolicyKeys.filter { key ->
                currentSettings.containsPolicyKey(key)
            }.toSet()
            if (
                packageName.isBlank() ||
                cleanPolicyKeys.none { key -> currentSettings.hardshipLevelFor(key) == HardshipLevel.Level3 }
            ) {
                return@edit
            }
            val now = System.currentTimeMillis()
            val lastUsedAt = preferences[HARDSHIP_LAST_EMERGENCY_PASS_AT] ?: 0L
            if (lastUsedAt > 0L && now - lastUsedAt < HARDSHIP_EMERGENCY_PASS_COOLDOWN_MILLIS) {
                result = EmergencyPassUseResult.CooldownActive
                return@edit
            }
            if (!isAdminPinValid(preferences, adminPin.trim())) {
                result = EmergencyPassUseResult.InvalidPin
                appendEvent(preferences, EventLogType.Warning, "Hardship Emergency Pass failed: invalid Admin PIN")
                return@edit
            }
            val runtimeState = preferences[HARDSHIP_RUNTIME_STATE].orEmpty()
                .toHardshipRuntimeState(lastUsedAt)
                .forToday()
            val nextGrants = (
                runtimeState.emergencyPassGrants.filterNot { grant ->
                    grant.packageName == packageName && grant.policyKey in cleanPolicyKeys
                } + cleanPolicyKeys.map { policyKey ->
                    EmergencyPassGrant(
                        packageName = packageName,
                        policyKey = policyKey,
                        grantedAtMillis = now,
                        expiresAtMillis = currentSettings.emergencyPassExpiryFor(policyKey, now),
                    )
                }
            ).filter { grant -> grant.expiresAtMillis > now }
            preferences[HARDSHIP_RUNTIME_STATE] = runtimeState.copy(
                emergencyPassGrants = nextGrants,
                lastEmergencyPassUsedAtMillis = now,
            ).toHardshipRuntimeStateEncoded()
            preferences[HARDSHIP_LAST_EMERGENCY_PASS_AT] = now
            val latestExpiryAt = nextGrants
                .filter { grant ->
                    grant.packageName == packageName && grant.policyKey in cleanPolicyKeys
                }
                .maxOfOrNull { grant -> grant.expiresAtMillis }
                ?: now
            appendEvent(
                preferences,
                EventLogType.Safety,
                "Hardship Emergency Pass used for $packageName: " +
                    "${cleanPolicyKeys.joinToString { key -> key.storageKey }}; " +
                    "expiresAt=$latestExpiryAt; " +
                    "nextAvailableAt=${now + HARDSHIP_EMERGENCY_PASS_COOLDOWN_MILLIS}",
            )
            result = EmergencyPassUseResult.Used
        }
        return result
    }

    /**
     * Confirms that a caller-provided block key still belongs to the currently
     * saved policy. Emergency Pass may need to bypass an ordinary or lower-level
     * policy that blocks the same app, but it must never create a grant for a
     * deleted or fabricated policy key.
     */
    private fun UsagePolicySettings.containsPolicyKey(key: HardshipPolicyKey): Boolean {
        return when (key.policyType) {
            HardshipPolicyType.DailyLimit -> dailyPolicyEnabled
            HardshipPolicyType.AppGroups -> normalizedAppGroups().any { group ->
                group.id == key.targetId && group.enabled
            }
            HardshipPolicyType.AppLimits -> appLimitMinutesFor(key.targetId) != null
            HardshipPolicyType.Schedule -> scheduleBlockingEnabled &&
                normalizedScheduleTemplates().any { schedule ->
                    schedule.id == key.targetId && schedule.enabled
                }
            HardshipPolicyType.AllowOnly -> allowOnlyModeEnabled
        }
    }

    suspend fun requestLevelTwoPolicyUnlock(
        policyKey: HardshipPolicyKey,
        adminPin: String,
    ): HardshipLevelTwoUnlockResult {
        var result = HardshipLevelTwoUnlockResult.NotAvailable
        dataStore.edit { preferences ->
            if (preferences.hardshipLevelFor(policyKey) != HardshipLevel.Level2) {
                return@edit
            }
            val now = System.currentTimeMillis()
            val runtimeState = preferences[HARDSHIP_RUNTIME_STATE].orEmpty()
                .toHardshipRuntimeState(preferences[HARDSHIP_LAST_EMERGENCY_PASS_AT] ?: 0L)
                .forToday()
            val reflectionKey = "Level2:${policyKey.storageKey}"
            val entry = runtimeState.reflections[reflectionKey] ?: HardshipReflectionEntry()
            when {
                entry.readyAtMillis <= 0L -> {
                    preferences[HARDSHIP_RUNTIME_STATE] = runtimeState.copy(
                        reflections = runtimeState.reflections + (
                            reflectionKey to entry.copy(readyAtMillis = now + HARDSHIP_LEVEL_TWO_WAIT_MILLIS)
                        ),
                    ).toHardshipRuntimeStateEncoded()
                    appendEvent(preferences, EventLogType.Info, "Hardship level 2 reflection started: ${policyKey.storageKey}")
                    result = HardshipLevelTwoUnlockResult.WaitingStarted
                }
                now < entry.readyAtMillis -> result = HardshipLevelTwoUnlockResult.Waiting
                !isAdminPinValid(preferences, adminPin.trim()) -> result = HardshipLevelTwoUnlockResult.InvalidPin
                else -> {
                    val nextActiveKeys = runtimeState.activePolicyKeys - policyKey
                    val nextActivePolicies = runtimeState.activePolicies.toMutableSet().apply {
                        if (nextActiveKeys.none { key -> key.policyType == policyKey.policyType }) {
                            remove(policyKey.policyType)
                        }
                        addAll(nextActiveKeys.map { key -> key.policyType })
                    }.toSet()
                    preferences[HARDSHIP_RUNTIME_STATE] = runtimeState.copy(
                        bypassedPolicyKeys = runtimeState.bypassedPolicyKeys + policyKey,
                        activePolicyKeys = nextActiveKeys,
                        activePolicies = nextActivePolicies,
                        reflections = runtimeState.reflections - reflectionKey,
                    ).toHardshipRuntimeStateEncoded()
                    appendEvent(preferences, EventLogType.Info, "Hardship level 2 ended after reflection: ${policyKey.storageKey}")
                    result = HardshipLevelTwoUnlockResult.Unlocked
                }
            }
        }
        return result
    }

    suspend fun addTemporaryAppTime(
        packageName: String,
        appName: String,
        extraMinutes: Int,
        adminPin: String,
    ): Boolean {
        if (packageName.isBlank() || extraMinutes <= 0) {
            return false
        }
        return updateTemporaryUnlocks(adminPin) { current ->
            val currentAllowance = current.packageAllowances[packageName] ?: TemporaryPackageAllowance()
            val nextAllowance = currentAllowance.withExtraTime(extraMinutes)
            current.copy(
                packageAllowances = current.packageAllowances + (packageName to nextAllowance),
            ) to "Parent added ${extraMinutes.toTimeLabel()} for ${appName.ifBlank { packageName }} (today extra ${nextAllowance.extraMinutes.toTimeLabel()})"
        }
    }

    suspend fun unlockAppForToday(
        packageName: String,
        appName: String,
        adminPin: String,
    ): Boolean {
        if (packageName.isBlank()) {
            return false
        }
        return updateTemporaryUnlocks(adminPin) { current ->
            val currentAllowance = current.packageAllowances[packageName] ?: TemporaryPackageAllowance()
            current.copy(
                packageAllowances = current.packageAllowances + (
                    packageName to currentAllowance.copy(unlockedForToday = true)
                ),
            ) to "Parent unlocked ${appName.ifBlank { packageName }} for today"
        }
    }

    suspend fun addTemporaryTotalTime(extraMinutes: Int, adminPin: String): Boolean {
        if (extraMinutes <= 0) {
            return false
        }
        return updateTemporaryUnlocks(adminPin) { current ->
            current.copy(
                totalExtraMinutes = (current.totalExtraMinutes + extraMinutes).coerceAtMost(MAX_TEMPORARY_EXTRA_MINUTES),
            ) to "Parent added ${extraMinutes.toTimeLabel()} to daily limit (today extra ${current.totalExtraMinutes.plus(extraMinutes).coerceAtMost(MAX_TEMPORARY_EXTRA_MINUTES).toTimeLabel()})"
        }
    }

    suspend fun unlockTotalForToday(adminPin: String): Boolean {
        return updateTemporaryUnlocks(adminPin) { current ->
            current.copy(totalUnlockedForToday = true) to "Parent unlocked total limit for today"
        }
    }

    suspend fun applyRemoteAppExtraTime(packageName: String, appName: String, extraMinutes: Int): Boolean {
        if (packageName.isBlank() || extraMinutes <= 0) {
            return false
        }
        return applyRemoteParentCommand(
            type = RemoteParentCommandType.AddAppTime,
            targetPackageName = packageName,
            targetAppName = appName,
            minutes = extraMinutes,
        ) { current ->
            val currentAllowance = current.packageAllowances[packageName] ?: TemporaryPackageAllowance()
            val nextAllowance = currentAllowance.withExtraTime(extraMinutes)
            current.copy(packageAllowances = current.packageAllowances + (packageName to nextAllowance))
        }
    }

    suspend fun applyRemoteAppUnlockToday(packageName: String, appName: String): Boolean {
        if (packageName.isBlank()) {
            return false
        }
        return applyRemoteParentCommand(
            type = RemoteParentCommandType.UnlockAppToday,
            targetPackageName = packageName,
            targetAppName = appName,
        ) { current ->
            val currentAllowance = current.packageAllowances[packageName] ?: TemporaryPackageAllowance()
            current.copy(
                packageAllowances = current.packageAllowances + (
                    packageName to currentAllowance.copy(unlockedForToday = true)
                ),
            )
        }
    }

    suspend fun applyRemoteTotalExtraTime(extraMinutes: Int): Boolean {
        if (extraMinutes <= 0) {
            return false
        }
        return applyRemoteParentCommand(
            type = RemoteParentCommandType.AddTotalTime,
            minutes = extraMinutes,
        ) { current ->
            current.copy(
                totalExtraMinutes = (current.totalExtraMinutes + extraMinutes).coerceAtMost(MAX_TEMPORARY_EXTRA_MINUTES),
            )
        }
    }

    suspend fun applyRemoteTotalUnlockToday(): Boolean {
        return applyRemoteParentCommand(
            type = RemoteParentCommandType.UnlockTotalToday,
        ) { current ->
            current.copy(totalUnlockedForToday = true)
        }
    }

    suspend fun addEvent(type: EventLogType, message: String) {
        dataStore.edit { preferences ->
            appendEvent(preferences, type, message)
        }
    }

    suspend fun recordPolicyAlertOnce(
        alertKey: String,
        type: EventLogType,
        message: String,
    ): Boolean {
        var recorded = false
        dataStore.edit { preferences ->
            val safeAlertKey = alertKey.encodeForEventLog()
            val currentKeys = preferences[POLICY_ALERT_KEYS]
                ?.split(EVENT_SEPARATOR)
                ?.filter { encodedKey -> encodedKey.isNotBlank() }
                .orEmpty()

            if (safeAlertKey !in currentKeys) {
                preferences[POLICY_ALERT_KEYS] = (listOf(safeAlertKey) + currentKeys)
                    .take(MAX_POLICY_ALERT_KEYS)
                    .joinToString(EVENT_SEPARATOR)
                appendEvent(preferences, type, message)
                recorded = true
            }
        }
        return recorded
    }

    suspend fun updateForegroundDetectionStatus(
        appName: String,
        packageName: String,
        decision: String,
    ) {
        dataStore.edit { preferences ->
            preferences[FOREGROUND_DETECTION_STATUS] = listOf(
                System.currentTimeMillis().toString(),
                appName.encodeForEventLog(),
                packageName.encodeForEventLog(),
                decision.encodeForEventLog(),
            ).joinToString(FIELD_SEPARATOR)
        }
    }

    suspend fun updateUsageMonitorStatus(
        running: Boolean,
        appName: String = "",
        packageName: String = "",
        decision: String = "",
        usedMillis: Long = 0L,
        limitMillis: Long? = null,
        limitedTarget: Boolean? = null,
        blockReason: String? = null,
        overlayAttached: Boolean? = null,
        blockAttemptMillis: Long? = null,
        blockRetryCount: Int? = null,
    ) {
        dataStore.edit { preferences ->
            val previousStatus = preferences[USAGE_MONITOR_STATUS]?.toUsageMonitorStatusOrNull()
                ?: UsageMonitorStatus()
            preferences[USAGE_MONITOR_STATUS] = previousStatus.copy(
                running = running,
                lastTickMillis = System.currentTimeMillis(),
                lastForegroundAppName = appName,
                lastForegroundPackageName = packageName,
                lastDecision = decision,
                lastUsedMillis = usedMillis.coerceAtLeast(0L),
                lastLimitMillis = (limitMillis ?: 0L).coerceAtLeast(0L),
                lastLimitedTarget = limitedTarget ?: previousStatus.lastLimitedTarget,
                lastBlockReason = blockReason ?: previousStatus.lastBlockReason,
                lastOverlayAttached = overlayAttached ?: previousStatus.lastOverlayAttached,
                lastBlockAttemptMillis = blockAttemptMillis ?: previousStatus.lastBlockAttemptMillis,
                blockRetryCount = blockRetryCount ?: previousStatus.blockRetryCount,
                lastStopReason = if (running) "" else previousStatus.lastStopReason,
            ).toUsageMonitorStatusEncoded()
        }
    }

    suspend fun markUsageMonitorStopped(reason: String) {
        dataStore.edit { preferences ->
            val previousStatus = preferences[USAGE_MONITOR_STATUS]?.toUsageMonitorStatusOrNull()
                ?: UsageMonitorStatus()
            preferences[USAGE_MONITOR_STATUS] = previousStatus.copy(
                running = false,
                lastStopReason = reason,
            ).toUsageMonitorStatusEncoded()
        }
    }

    suspend fun recordUsageMonitorRecovery(reason: String) {
        dataStore.edit { preferences ->
            val previousStatus = preferences[USAGE_MONITOR_STATUS]?.toUsageMonitorStatusOrNull()
                ?: UsageMonitorStatus()
            preferences[USAGE_MONITOR_STATUS] = previousStatus.copy(
                running = true,
                lastRecoveryMillis = System.currentTimeMillis(),
                lastRecoveryReason = reason,
                lastStopReason = "",
            ).toUsageMonitorStatusEncoded()
            appendEvent(
                preferences = preferences,
                type = EventLogType.Safety,
                message = "Usage monitor recovery: $reason",
            )
        }
    }

    suspend fun updateSystemHealthStatus(status: SystemHealthStatus) {
        dataStore.edit { preferences ->
            val previous = preferences[SYSTEM_HEALTH_STATUS]?.toSystemHealthStatusOrNull()
            preferences[SYSTEM_HEALTH_STATUS] = status.toSystemHealthStatusEncoded()
            if (previous != null && previous.allReady && !status.allReady) {
                appendEvent(
                    preferences = preferences,
                    type = EventLogType.Warning,
                    message = "System health issue: ${status.lastIssue.ifBlank { "permission or service not ready" }}",
                )
            }
        }
    }

    suspend fun saveTodayUsageCache(dateKey: String, apps: List<CachedTodayUsageEntry>) {
        dataStore.edit { preferences ->
            preferences[CACHED_TODAY_USAGE] = CachedTodayUsageSnapshot(
                dateKey = dateKey,
                savedAtMillis = System.currentTimeMillis(),
                apps = apps
                    .filter { app -> app.packageName.isNotBlank() && app.totalTimeMillis >= 0L }
                    .take(MAX_CACHED_TODAY_USAGE_ENTRIES),
            ).toCachedTodayUsageSnapshotEncoded()
        }
    }

    suspend fun recordDailyRollover(dateKey: String = currentTemporaryUnlockDateKey()) {
        reconcileHardshipLifecycle()
        dataStore.edit { preferences ->
            val currentTemporaryState = preferences[TEMPORARY_UNLOCKS].orEmpty()
                .toTemporaryUnlockState()
            if (currentTemporaryState.dateKey != dateKey) {
                preferences[TEMPORARY_UNLOCKS] = TemporaryUnlockState(dateKey = dateKey)
                    .toTemporaryUnlocksEncoded()
            }
            preferences[USAGE_MONITOR_STATUS] = UsageMonitorStatus(
                running = preferences[USAGE_MONITOR_STATUS]
                    ?.toUsageMonitorStatusOrNull()
                    ?.running
                    ?: false,
                lastTickMillis = System.currentTimeMillis(),
            ).toUsageMonitorStatusEncoded()
            appendEvent(
                preferences = preferences,
                type = EventLogType.Safety,
                message = "Daily rollover completed: $dateKey",
            )
        }
    }

    suspend fun clearEventLog() {
        dataStore.edit { preferences ->
            preferences[EVENT_LOG] = ""
            preferences[POLICY_ALERT_KEYS] = ""
        }
    }

    private suspend fun updatePin(
        currentPin: String,
        newPin: String,
    ): Boolean {
        val cleanNewPin = newPin.trim()
        if (!cleanNewPin.isValidSecurityPin()) {
            return false
        }

        var updated = false
        dataStore.edit { preferences ->
            val currentValid = isAdminPinValid(preferences, currentPin.trim())
            if (currentValid) {
                preferences[ADMIN_PIN_CREDENTIAL] = createPinCredential(cleanNewPin)
                preferences.remove(ADMIN_PIN)
                clearAdminPinFailures(preferences)
                appendEvent(preferences, EventLogType.Safety, "Admin PIN changed")
                updated = true
            }
        }
        return updated
    }

    private suspend fun updateTemporaryUnlocks(
        adminPin: String,
        transform: (TemporaryUnlockState) -> Pair<TemporaryUnlockState, String>,
    ): Boolean {
        var updated = false
        dataStore.edit { preferences ->
            if (!isAdminPinValid(preferences, adminPin)) {
                appendEvent(
                    preferences = preferences,
                    type = EventLogType.Warning,
                    message = "Parent override failed: invalid admin PIN",
                )
                return@edit
            }

            val current = preferences[TEMPORARY_UNLOCKS].orEmpty()
                .toTemporaryUnlockState()
                .forToday()
            val (nextState, message) = transform(current)
            preferences[TEMPORARY_UNLOCKS] = nextState.toTemporaryUnlocksEncoded()
            appendEvent(
                preferences = preferences,
                type = EventLogType.Info,
                message = message,
            )
            updated = true
        }
        return updated
    }

    private suspend fun applyRemoteParentCommand(
        type: RemoteParentCommandType,
        targetPackageName: String = "",
        targetAppName: String = "",
        minutes: Int = 0,
        transform: (TemporaryUnlockState) -> TemporaryUnlockState,
    ): Boolean {
        var applied = false
        dataStore.edit { preferences ->
            val parentState = preferences[PARENT_MANAGEMENT_STATE].orEmpty().toParentManagementState()
            val now = System.currentTimeMillis()
            if (!parentState.paired) {
                val failedCommand = RemoteParentCommand(
                    id = UUID.randomUUID().toString(),
                    timestampMillis = now,
                    type = type,
                    targetPackageName = targetPackageName,
                    targetAppName = targetAppName,
                    minutes = minutes,
                    status = RemoteParentCommandStatus.Failed,
                    message = "No parent account paired",
                )
                preferences[PARENT_MANAGEMENT_STATE] = parentState.copy(
                    lastSyncMillis = now,
                    remoteCommands = (listOf(failedCommand) + parentState.remoteCommands).take(MAX_REMOTE_PARENT_COMMANDS),
                ).toParentManagementStateEncoded()
                appendEvent(preferences, EventLogType.Warning, "Remote parent command failed: parent account not paired")
                return@edit
            }

            val currentTemporaryState = preferences[TEMPORARY_UNLOCKS].orEmpty()
                .toTemporaryUnlockState()
                .forToday()
            val nextTemporaryState = transform(currentTemporaryState)
            preferences[TEMPORARY_UNLOCKS] = nextTemporaryState.toTemporaryUnlocksEncoded()

            val message = type.toRemoteCommandMessage(
                appName = targetAppName.ifBlank { targetPackageName },
                minutes = minutes,
            )
            val command = RemoteParentCommand(
                id = UUID.randomUUID().toString(),
                timestampMillis = now,
                type = type,
                targetPackageName = targetPackageName,
                targetAppName = targetAppName,
                minutes = minutes,
                status = RemoteParentCommandStatus.Applied,
                message = message,
            )
            preferences[PARENT_MANAGEMENT_STATE] = parentState.copy(
                lastSyncMillis = now,
                remoteCommands = (listOf(command) + parentState.remoteCommands).take(MAX_REMOTE_PARENT_COMMANDS),
            ).toParentManagementStateEncoded()
            appendEvent(preferences, EventLogType.Info, message)
            applied = true
        }
        return applied
    }

    private suspend fun recordParentRemoteSyncResult(
        action: String,
        result: ParentRemoteSyncResult,
    ) {
        when (result) {
            ParentRemoteSyncResult.Success -> Unit
            ParentRemoteSyncResult.LocalOnly -> addEvent(
                type = EventLogType.Warning,
                message = "Parent remote sync unavailable: $action - cloud configuration missing",
            )
            is ParentRemoteSyncResult.Failed -> addEvent(
                type = EventLogType.Warning,
                message = "Parent remote sync failed: $action - ${result.reason}",
            )
        }
    }

    private fun Preferences.hasConfiguredPin(
        credentialKey: Preferences.Key<String>,
        legacyKey: Preferences.Key<String>,
    ): Boolean = !this[credentialKey].isNullOrBlank() || !this[legacyKey].isNullOrBlank()

    private fun String.isValidSecurityPin(): Boolean =
        length in 4..8 && all { character -> character.isDigit() }

    private fun createPinCredential(pin: String): String {
        val salt = ByteArray(PIN_SALT_BYTES).also(SecureRandom()::nextBytes)
        val spec = PBEKeySpec(pin.toCharArray(), salt, PIN_HASH_ITERATIONS, PIN_HASH_BITS)
        val hash = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
        val encoder = Base64.getUrlEncoder().withoutPadding()
        return listOf(
            PIN_CREDENTIAL_VERSION,
            PIN_HASH_ITERATIONS.toString(),
            encoder.encodeToString(salt),
            encoder.encodeToString(hash),
        ).joinToString(":")
    }

    private fun pinMatchesCredential(pin: String, credential: String): Boolean {
        val pieces = credential.split(':')
        if (pieces.size != 4 || pieces[0] != PIN_CREDENTIAL_VERSION) {
            return false
        }
        return runCatching {
            val iterations = pieces[1].toInt()
            if (iterations < PIN_HASH_ITERATIONS) {
                return@runCatching false
            }
            val decoder = Base64.getUrlDecoder()
            val salt = decoder.decode(pieces[2])
            val expectedHash = decoder.decode(pieces[3])
            val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, expectedHash.size * 8)
            val actualHash = try {
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            } finally {
                spec.clearPassword()
            }
            MessageDigest.isEqual(expectedHash, actualHash)
        }.getOrDefault(false)
    }

    private fun isAdminPinValid(preferences: MutablePreferences, adminPin: String): Boolean {
        val now = System.currentTimeMillis()
        if ((preferences[ADMIN_PIN_LOCK_UNTIL] ?: 0L) > now) {
            return false
        }
        val credential = preferences[ADMIN_PIN_CREDENTIAL]
        val legacyPin = preferences[ADMIN_PIN]
        val valid = when {
            !credential.isNullOrBlank() -> pinMatchesCredential(adminPin, credential)
            !legacyPin.isNullOrBlank() -> MessageDigest.isEqual(
                adminPin.toByteArray(Charsets.UTF_8),
                legacyPin.toByteArray(Charsets.UTF_8),
            )
            else -> false
        }
        if (valid) {
            if (credential.isNullOrBlank()) {
                preferences[ADMIN_PIN_CREDENTIAL] = createPinCredential(adminPin)
                preferences.remove(ADMIN_PIN)
            }
            clearAdminPinFailures(preferences)
        } else {
            recordAdminPinFailure(preferences, now)
        }
        return valid
    }

    private fun recordAdminPinFailure(
        preferences: MutablePreferences,
        nowMillis: Long,
    ) {
        val failures = (preferences[ADMIN_PIN_FAILURE_COUNT] ?: 0) + 1
        if (failures >= PIN_FAILURES_BEFORE_LOCK) {
            preferences[ADMIN_PIN_FAILURE_COUNT] = 0
            preferences[ADMIN_PIN_LOCK_UNTIL] = nowMillis + PIN_LOCK_MILLIS
            appendEvent(
                preferences,
                EventLogType.Warning,
                "Admin PIN temporarily locked",
            )
        } else {
            preferences[ADMIN_PIN_FAILURE_COUNT] = failures
        }
    }

    private fun clearAdminPinFailures(preferences: MutablePreferences) {
        preferences[ADMIN_PIN_FAILURE_COUNT] = 0
        preferences[ADMIN_PIN_LOCK_UNTIL] = 0L
    }

    private fun applySafeRecovery(preferences: MutablePreferences) {
        preferences[SAFE_MODE_ENABLED] = true
        preferences[POLICY_ENFORCEMENT_ENABLED] = false
        preferences[TEMPORARY_UNLOCKS] = ""
        preferences[FOREGROUND_DETECTION_STATUS] = ""
        preferences[USAGE_MONITOR_STATUS] = UsageMonitorStatus(
            running = false,
            lastStopReason = "safe recovery",
        ).toUsageMonitorStatusEncoded()
        preferences[POLICY_ALERT_KEYS] = ""
    }

    private fun appendEvent(
        preferences: MutablePreferences,
        type: EventLogType,
        message: String,
    ) {
        val currentEntries = preferences[EVENT_LOG]
            ?.split(EVENT_SEPARATOR)
            ?.filter { encodedEntry -> encodedEntry.isNotBlank() }
            .orEmpty()

        val encodedEntry = listOf(
            System.currentTimeMillis().toString(),
            type.toStorageValue(),
            message.encodeForEventLog(),
        ).joinToString(FIELD_SEPARATOR)

        preferences[EVENT_LOG] = (listOf(encodedEntry) + currentEntries)
            .take(MAX_EVENT_LOG_ENTRIES)
            .joinToString(EVENT_SEPARATOR)
    }

    private fun String.toEventLogEntryOrNull(): EventLogEntry? {
        val parts = split(FIELD_SEPARATOR)
        if (parts.size != 3) {
            return null
        }
        val timestampMillis = parts[0].toLongOrNull() ?: return null
        val type = parts[1].toEventLogTypeOrNull() ?: return null
        return EventLogEntry(
            timestampMillis = timestampMillis,
            type = type,
            message = parts[2].decodeFromEventLog(),
        )
    }

    private fun String.toForegroundDetectionStatusOrNull(): ForegroundDetectionStatus? {
        val parts = split(FIELD_SEPARATOR)
        if (parts.size != 4) {
            return null
        }
        return ForegroundDetectionStatus(
            timestampMillis = parts[0].toLongOrNull() ?: return null,
            appName = parts[1].decodeFromEventLog(),
            packageName = parts[2].decodeFromEventLog(),
            decision = parts[3].decodeFromEventLog(),
        )
    }

    private fun String.toUsageMonitorStatusOrNull(): UsageMonitorStatus? {
        val parts = split(FIELD_SEPARATOR)
        if (parts.size !in setOf(8, 10, 15)) {
            return null
        }
        val running = when (parts[0]) {
            "true" -> true
            "false" -> false
            else -> return null
        }
        return UsageMonitorStatus(
            running = running,
            lastTickMillis = parts[1].toLongOrNull() ?: 0L,
            lastForegroundAppName = parts[2].decodeFromEventLog(),
            lastForegroundPackageName = parts[3].decodeFromEventLog(),
            lastDecision = parts[4].decodeFromEventLog(),
            lastUsedMillis = if (parts.size >= 10) parts[5].toLongOrNull() ?: 0L else 0L,
            lastLimitMillis = if (parts.size >= 10) parts[6].toLongOrNull() ?: 0L else 0L,
            lastLimitedTarget = if (parts.size >= 15) parts[7].toBooleanStrictOrNull() ?: false else false,
            lastBlockReason = if (parts.size >= 15) parts[8].decodeFromEventLog() else "",
            lastOverlayAttached = if (parts.size >= 15) parts[9].toBooleanStrictOrNull() ?: false else false,
            lastBlockAttemptMillis = if (parts.size >= 15) parts[10].toLongOrNull() ?: 0L else 0L,
            blockRetryCount = if (parts.size >= 15) parts[11].toIntOrNull()?.coerceAtLeast(0) ?: 0 else 0,
            lastRecoveryMillis = parts[if (parts.size >= 15) 12 else if (parts.size >= 10) 7 else 5].toLongOrNull() ?: 0L,
            lastRecoveryReason = parts[if (parts.size >= 15) 13 else if (parts.size >= 10) 8 else 6].decodeFromEventLog(),
            lastStopReason = parts[if (parts.size >= 15) 14 else if (parts.size >= 10) 9 else 7].decodeFromEventLog(),
        )
    }

    private fun UsageMonitorStatus.toUsageMonitorStatusEncoded(): String {
        return listOf(
            running.toString(),
            lastTickMillis.toString(),
            lastForegroundAppName.encodeForEventLog(),
            lastForegroundPackageName.encodeForEventLog(),
            lastDecision.encodeForEventLog(),
            lastUsedMillis.coerceAtLeast(0L).toString(),
            lastLimitMillis.coerceAtLeast(0L).toString(),
            lastLimitedTarget.toString(),
            lastBlockReason.encodeForEventLog(),
            lastOverlayAttached.toString(),
            lastBlockAttemptMillis.coerceAtLeast(0L).toString(),
            blockRetryCount.coerceAtLeast(0).toString(),
            lastRecoveryMillis.toString(),
            lastRecoveryReason.encodeForEventLog(),
            lastStopReason.encodeForEventLog(),
        ).joinToString(FIELD_SEPARATOR)
    }

    private fun String.toSystemHealthStatusOrNull(): SystemHealthStatus? {
        val parts = split(FIELD_SEPARATOR)
        if (parts.size != 10) {
            return null
        }
        return SystemHealthStatus(
            lastCheckedMillis = parts[0].toLongOrNull() ?: 0L,
            usageAccessReady = parts[1].toBooleanStrictOrNull() ?: false,
            overlayPermissionReady = parts[2].toBooleanStrictOrNull() ?: false,
            notificationPermissionReady = parts[3].toBooleanStrictOrNull() ?: false,
            notificationAccessReady = parts[4].toBooleanStrictOrNull() ?: false,
            exactAlarmReady = parts[5].toBooleanStrictOrNull() ?: false,
            foregroundServiceExpected = parts[6].toBooleanStrictOrNull() ?: false,
            foregroundServiceRunning = parts[7].toBooleanStrictOrNull() ?: false,
            foregroundServiceFresh = parts[8].toBooleanStrictOrNull() ?: false,
            lastIssue = parts[9].decodeFromEventLog(),
        )
    }

    private fun SystemHealthStatus.toSystemHealthStatusEncoded(): String {
        return listOf(
            lastCheckedMillis.toString(),
            usageAccessReady.toString(),
            overlayPermissionReady.toString(),
            notificationPermissionReady.toString(),
            notificationAccessReady.toString(),
            exactAlarmReady.toString(),
            foregroundServiceExpected.toString(),
            foregroundServiceRunning.toString(),
            foregroundServiceFresh.toString(),
            lastIssue.encodeForEventLog(),
        ).joinToString(FIELD_SEPARATOR)
    }

    private fun String.toCachedTodayUsageSnapshotOrNull(): CachedTodayUsageSnapshot? {
        val parts = split(FIELD_SEPARATOR, limit = 3)
        if (parts.size != 3) {
            return null
        }
        val decodedEntries = parts[2].decodeFromEventLog()
        return CachedTodayUsageSnapshot(
            dateKey = parts[0],
            savedAtMillis = parts[1].toLongOrNull() ?: 0L,
            apps = decodedEntries
                .split(EVENT_SEPARATOR)
                .mapNotNull { encodedEntry -> encodedEntry.toCachedTodayUsageEntryOrNull() },
        )
    }

    private fun CachedTodayUsageSnapshot.toCachedTodayUsageSnapshotEncoded(): String {
        val encodedEntries = apps
            .take(MAX_CACHED_TODAY_USAGE_ENTRIES)
            .joinToString(EVENT_SEPARATOR) { entry -> entry.toCachedTodayUsageEntryEncoded() }
        return listOf(
            dateKey,
            savedAtMillis.toString(),
            encodedEntries.encodeForEventLog(),
        ).joinToString(FIELD_SEPARATOR)
    }

    private fun String.toCachedTodayUsageEntryOrNull(): CachedTodayUsageEntry? {
        val parts = split(FIELD_SEPARATOR)
        if (parts.size != 3) {
            return null
        }
        return CachedTodayUsageEntry(
            appName = parts[0].decodeFromEventLog(),
            packageName = parts[1].decodeFromEventLog(),
            totalTimeMillis = parts[2].toLongOrNull() ?: return null,
        )
    }

    private fun CachedTodayUsageEntry.toCachedTodayUsageEntryEncoded(): String {
        return listOf(
            appName.encodeForEventLog(),
            packageName.encodeForEventLog(),
            totalTimeMillis.coerceAtLeast(0L).toString(),
        ).joinToString(FIELD_SEPARATOR)
    }

    private fun String.encodeForEventLog(): String {
        return replace("%", "%25")
            .replace("|", "%7C")
            .replace("~", "%7E")
    }

    private fun String.decodeFromEventLog(): String {
        return replace("%7E", "~")
            .replace("%7C", "|")
            .replace("%25", "%")
    }

    private fun Preferences.policyPackageSet(
        currentKey: Preferences.Key<String>,
        legacyKey: Preferences.Key<String>,
    ): Set<String> {
        val source = if ((this[POLICY_APP_LISTS_VERSION] ?: 0) >= POLICY_APP_LISTS_VERSION_CURRENT) {
            this[currentKey].orEmpty()
        } else {
            this[currentKey] ?: this[legacyKey].orEmpty()
        }
        return source.toPackageSet().cleanUserPolicyPackageSet()
    }

    private fun migrateLegacyPolicyPackageListsIfNeeded(preferences: MutablePreferences) {
        if ((preferences[POLICY_APP_LISTS_VERSION] ?: 0) >= POLICY_APP_LISTS_VERSION_CURRENT) return
        val legacyPackages = preferences[ALLOWED_APP_PACKAGES].orEmpty()
            .toPackageSet()
            .cleanUserPolicyPackageSet()
        if (preferences[ALLOW_ONLY_ALLOWED_APP_PACKAGES] == null) {
            preferences[ALLOW_ONLY_ALLOWED_APP_PACKAGES] = legacyPackages.toPackageString()
        }
        if (preferences[ALL_RESTRICTIONS_EXEMPT_PACKAGES] == null) {
            preferences[ALL_RESTRICTIONS_EXEMPT_PACKAGES] = legacyPackages.toPackageString()
        }
        preferences[POLICY_APP_LISTS_VERSION] = POLICY_APP_LISTS_VERSION_CURRENT
    }

    companion object {
        private const val MONITORING_DISCLOSURE_CURRENT_VERSION = 1
        private const val PIN_CREDENTIAL_VERSION = "v1"
        private const val PIN_HASH_ITERATIONS = 210_000
        private const val PIN_HASH_BITS = 256
        private const val PIN_SALT_BYTES = 16
        private const val PIN_FAILURES_BEFORE_LOCK = 5
        private const val PIN_LOCK_MILLIS = 30_000L

        private val SAFE_MODE_ENABLED = booleanPreferencesKey("safe_mode_enabled")
        private val POLICY_ENFORCEMENT_ENABLED = booleanPreferencesKey("policy_enforcement_enabled")
        private val WARNING_NOTIFICATIONS_ENABLED = booleanPreferencesKey("warning_notifications_enabled")
        private val LIMIT_NOTIFICATIONS_ENABLED = booleanPreferencesKey("limit_notifications_enabled")
        private val PERMISSION_SETUP_COMPLETED_ONCE = booleanPreferencesKey("permission_setup_completed_once")
        private val MONITORING_DISCLOSURE_ACCEPTED_VERSION =
            intPreferencesKey("monitoring_disclosure_accepted_version")
        private val CHILD_TOP_APPS_SHARING_ENABLED = booleanPreferencesKey("child_top_apps_sharing_enabled")
        private val DAILY_POLICY_EXPANDED = booleanPreferencesKey("daily_policy_expanded")
        private val APP_GROUPS_EXPANDED = booleanPreferencesKey("app_groups_expanded")
        private val APP_LIMITS_EXPANDED = booleanPreferencesKey("app_limits_expanded")
        private val SCHEDULE_BLOCKING_EXPANDED = booleanPreferencesKey("schedule_blocking_expanded")
        private val ALLOW_ONLY_MODE_EXPANDED = booleanPreferencesKey("allow_only_mode_expanded")
        private val SETTINGS_LANGUAGE_EXPANDED = booleanPreferencesKey("settings_language_expanded")
        private val SETTINGS_NOTIFICATION_EXPANDED = booleanPreferencesKey("settings_notification_expanded")
        private val SETTINGS_PIN_EXPANDED = booleanPreferencesKey("settings_pin_expanded")
        private val SETTINGS_PARENT_MANAGEMENT_EXPANDED = booleanPreferencesKey("settings_parent_management_expanded")
        private val SETTINGS_EVENT_LOG_EXPANDED = booleanPreferencesKey("settings_event_log_expanded")
        private val EMERGENCY_UNLOCK_PIN = stringPreferencesKey("emergency_unlock_pin")
        private val ADMIN_PIN = stringPreferencesKey("admin_pin")
        private val EMERGENCY_PIN_CREDENTIAL = stringPreferencesKey("emergency_pin_credential")
        private val ADMIN_PIN_CREDENTIAL = stringPreferencesKey("admin_pin_credential")
        private val EMERGENCY_PIN_FAILURE_COUNT = intPreferencesKey("emergency_pin_failure_count")
        private val ADMIN_PIN_FAILURE_COUNT = intPreferencesKey("admin_pin_failure_count")
        private val EMERGENCY_PIN_LOCK_UNTIL = longPreferencesKey("emergency_pin_lock_until")
        private val ADMIN_PIN_LOCK_UNTIL = longPreferencesKey("admin_pin_lock_until")
        private val PIN_SCHEMA_VERSION = intPreferencesKey("pin_schema_version")
        private val APP_LANGUAGE = stringPreferencesKey("app_language")
        private val EVENT_LOG = stringPreferencesKey("event_log")
        private val POLICY_ALERT_KEYS = stringPreferencesKey("policy_alert_keys")
        private val FOREGROUND_DETECTION_STATUS = stringPreferencesKey("foreground_detection_status")
        private val USAGE_MONITOR_STATUS = stringPreferencesKey("usage_monitor_status")
        private val SYSTEM_HEALTH_STATUS = stringPreferencesKey("system_health_status")
        private val CACHED_TODAY_USAGE = stringPreferencesKey("cached_today_usage")
        private val TEMPORARY_UNLOCKS = stringPreferencesKey("temporary_unlocks")
        private val ALLOWED_APP_PACKAGES = stringPreferencesKey("allowed_app_packages")
        private val ALLOW_ONLY_ALLOWED_APP_PACKAGES = stringPreferencesKey("allow_only_allowed_app_packages")
        private val ALL_RESTRICTIONS_EXEMPT_PACKAGES =
            stringPreferencesKey("all_restrictions_exempt_packages")
        private val POLICY_APP_LISTS_VERSION = intPreferencesKey("policy_app_lists_version")
        private val HARDSHIP_LEVEL3_LOCK_VERSION = intPreferencesKey("hardship_level3_lock_version")
        private val PARENT_MANAGEMENT_STATE = stringPreferencesKey("parent_management_state")
        private val IMMEDIATE_BLOCK_STATE = stringPreferencesKey("immediate_block_state")
        private val PARENT_NOTIFICATION_STATE = stringPreferencesKey("parent_notification_state")
        private val PARENT_AUTH_UID = stringPreferencesKey("parent_auth_uid")
        private val LAST_REMOTE_CLEANUP_MILLIS = longPreferencesKey("last_remote_cleanup_millis")
        private val LAST_RUN_CLEAN = booleanPreferencesKey("last_run_clean")
        private val WEEKDAY_LIMIT_MINUTES = intPreferencesKey("weekday_limit_minutes")
        private val WEEKEND_LIMIT_MINUTES = intPreferencesKey("weekend_limit_minutes")
        private val MONDAY_LIMIT_MINUTES = intPreferencesKey("monday_limit_minutes")
        private val TUESDAY_LIMIT_MINUTES = intPreferencesKey("tuesday_limit_minutes")
        private val WEDNESDAY_LIMIT_MINUTES = intPreferencesKey("wednesday_limit_minutes")
        private val THURSDAY_LIMIT_MINUTES = intPreferencesKey("thursday_limit_minutes")
        private val FRIDAY_LIMIT_MINUTES = intPreferencesKey("friday_limit_minutes")
        private val SATURDAY_LIMIT_MINUTES = intPreferencesKey("saturday_limit_minutes")
        private val SUNDAY_LIMIT_MINUTES = intPreferencesKey("sunday_limit_minutes")
        private val DAILY_POLICY_ENABLED = booleanPreferencesKey("daily_policy_enabled")
        private val APP_GROUP_NAME = stringPreferencesKey("app_group_name")
        private val APP_GROUP_PACKAGES = stringPreferencesKey("app_group_packages")
        private val APP_GROUP_BUDGET_MINUTES = intPreferencesKey("app_group_budget_minutes")
        private val APP_GROUPS = stringPreferencesKey("app_groups")
        private val APP_LIMIT_RULES = stringPreferencesKey("app_limit_rules")
        private val APP_LIMIT_ACTIVE_DAYS = stringPreferencesKey("app_limit_active_days_v1")
        private val APP_LIMIT_HARDSHIP_LEVELS = stringPreferencesKey("app_limit_hardship_levels_v2")
        private val SCHEDULE_BLOCKING_ENABLED = booleanPreferencesKey("schedule_blocking_enabled")
        private val SCHEDULE_START_MINUTES = intPreferencesKey("schedule_start_minutes")
        private val SCHEDULE_END_MINUTES = intPreferencesKey("schedule_end_minutes")
        private val SCHEDULE_DAYS = stringPreferencesKey("schedule_days")
        private val SCHEDULE_TEMPLATES = stringPreferencesKey("schedule_templates")
        private val ACTIVE_SCHEDULE_TEMPLATE_ID = stringPreferencesKey("active_schedule_template_id")
        private val ALLOW_ONLY_MODE_ENABLED = booleanPreferencesKey("allow_only_mode_enabled")
        private val DAILY_HARDSHIP_LEVEL = intPreferencesKey("daily_hardship_level")
        private val APP_GROUPS_HARDSHIP_LEVEL = intPreferencesKey("app_groups_hardship_level")
        private val APP_LIMITS_HARDSHIP_LEVEL = intPreferencesKey("app_limits_hardship_level")
        private val SCHEDULE_HARDSHIP_LEVEL = intPreferencesKey("schedule_hardship_level")
        private val ALLOW_ONLY_HARDSHIP_LEVEL = intPreferencesKey("allow_only_hardship_level")
        private val HARDSHIP_RUNTIME_STATE = stringPreferencesKey("hardship_runtime_state")
        private val HARDSHIP_LAST_EMERGENCY_PASS_AT = longPreferencesKey("hardship_last_emergency_pass_at")
        private val ALLOW_ONLY_HARDSHIP_END_AT_MILLIS = longPreferencesKey("allow_only_hardship_end_at_millis")
        private const val EVENT_SEPARATOR = "~"
        private const val FIELD_SEPARATOR = "|"
        private const val MAX_EVENT_LOG_ENTRIES = 50
        private const val MAX_POLICY_ALERT_KEYS = 120
        private const val MAX_REMOTE_PARENT_COMMANDS = 30
        private const val MAX_REMOTE_UNLOCK_REQUESTS = 30
        private const val REMOTE_UNLOCK_REQUEST_COOLDOWN_MILLIS = 60_000L
        private const val REMOTE_UNLOCK_REQUEST_TTL_MILLIS = 10L * 60L * 1000L
        private const val REMOTE_UNLOCK_REQUEST_PUBLISH_TIMEOUT_MILLIS = 8_000L
        private const val REMOTE_CLEANUP_INTERVAL_MILLIS = 3L * 24L * 60L * 60L * 1_000L
        private const val REMOTE_DOCUMENT_RETENTION_MILLIS = 7L * 24L * 60L * 60L * 1_000L
        private const val MAX_CACHED_TODAY_USAGE_ENTRIES = 50
        private const val MINUTES_PER_DAY = 24 * 60
        private const val HARDSHIP_LEVEL_ONE_WAIT_MILLIS = 2L * 60L * 1000L
        private const val HARDSHIP_LEVEL_ONE_ALLOWANCE_MINUTES = 5
        private const val HARDSHIP_LEVEL_ONE_DAILY_USE_LIMIT = 2
        private const val HARDSHIP_EMERGENCY_PASS_COOLDOWN_MILLIS = 7L * 24L * 60L * 60L * 1000L
        private const val HARDSHIP_LEVEL_TWO_WAIT_MILLIS = 30L * 60L * 1000L
        private const val POLICY_APP_LISTS_VERSION_CURRENT = 1
        private const val HARDSHIP_LEVEL3_LOCK_VERSION_CURRENT = 1
        private const val PIN_SCHEMA_VERSION_CURRENT = 2
    }
}

fun currentTemporaryUnlockDateKey(): String {
    return LocalDate.now().toString()
}

private fun Preferences.hardshipLevelFor(policyType: HardshipPolicyType): HardshipLevel {
    val key = when (policyType) {
        HardshipPolicyType.DailyLimit -> "daily_hardship_level"
        HardshipPolicyType.AppGroups -> "app_groups_hardship_level"
        HardshipPolicyType.AppLimits -> "app_limits_hardship_level"
        HardshipPolicyType.Schedule -> "schedule_hardship_level"
        HardshipPolicyType.AllowOnly -> "allow_only_hardship_level"
    }
    return HardshipLevel.fromStorageValue(this[intPreferencesKey(key)])
}

private fun UsagePolicySettings.appLimitMinutesFor(packageName: String): Int? {
    return appLimitRules
        .split('|')
        .mapNotNull { rule ->
            val parts = rule.split('=', limit = 2)
            val targetPackage = parts.getOrNull(0)?.trim().orEmpty()
            val storedMinutes = parts.getOrNull(1)?.trim()?.toIntOrNull()
            if (targetPackage.isBlank() || storedMinutes == null) {
                null
            } else {
                decodeOptionalLimitMinutes(storedMinutes)?.let { minutes -> targetPackage to minutes }
            }
        }
        .firstOrNull { (targetPackage, _) -> targetPackage == packageName }
        ?.second
}

private fun Preferences.hardshipLevelFor(policyKey: HardshipPolicyKey): HardshipLevel {
    val settings = UsagePolicySettings(
        appGroups = this[stringPreferencesKey("app_groups")].orEmpty(),
        appLimitRules = this[stringPreferencesKey("app_limit_rules")].orEmpty(),
        appLimitHardshipLevels = this[stringPreferencesKey("app_limit_hardship_levels_v2")].orEmpty(),
        scheduleTemplates = this[stringPreferencesKey("schedule_templates")].orEmpty(),
        dailyHardshipLevel = HardshipLevel.fromStorageValue(this[intPreferencesKey("daily_hardship_level")]),
        appGroupsHardshipLevel = HardshipLevel.fromStorageValue(this[intPreferencesKey("app_groups_hardship_level")]),
        appLimitsHardshipLevel = HardshipLevel.fromStorageValue(this[intPreferencesKey("app_limits_hardship_level")]),
        scheduleHardshipLevel = HardshipLevel.fromStorageValue(this[intPreferencesKey("schedule_hardship_level")]),
        allowOnlyHardshipLevel = HardshipLevel.fromStorageValue(this[intPreferencesKey("allow_only_hardship_level")]),
    )
    return settings.hardshipLevelFor(policyKey)
}

private fun hardshipReflectionKey(policyKey: HardshipPolicyKey, packageName: String): String {
    return "${policyKey.storageKey}:$packageName"
}

private fun hardshipConfigurationReflectionKey(policyKey: HardshipPolicyKey): String =
    "$HARDSHIP_CONFIGURATION_REFLECTION_PREFIX${policyKey.storageKey}"

private fun String.toHardshipRuntimeState(lastEmergencyPassUsedAtMillis: Long): HardshipRuntimeState {
    val parts = split('#', limit = 7)
    val dateKey = parts.getOrNull(0).orEmpty()
    val bypassedPolicies = parts.getOrNull(1)
        .orEmpty()
        .split(',')
        .mapNotNull { value ->
            HardshipPolicyType.entries.firstOrNull { policyType -> policyType.name == value }
        }
        .toSet()
    val activePolicies = parts.getOrNull(2)
        .orEmpty()
        .split(',')
        .mapNotNull { value ->
            HardshipPolicyType.entries.firstOrNull { policyType -> policyType.name == value }
        }
        .toSet()
    val reflections = parts.getOrNull(3)
        .orEmpty()
        .split(';')
        .mapNotNull { encodedEntry ->
            val entryParts = encodedEntry.split(',', limit = 3)
            val key = entryParts.getOrNull(0).orEmpty()
            val readyAtMillis = entryParts.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
            val useCount = entryParts.getOrNull(2)?.toIntOrNull() ?: return@mapNotNull null
            key.takeIf { it.isNotBlank() }?.let {
                it to HardshipReflectionEntry(
                    readyAtMillis = readyAtMillis.coerceAtLeast(0L),
                    useCount = useCount.coerceAtLeast(0),
                )
            }
        }
        .toMap()
    val bypassedPolicyKeys = parts.getOrNull(4)
        .orEmpty()
        .split(',')
        .mapNotNull { value -> HardshipPolicyKey.fromStorageKey(value) }
        .toSet()
    val activePolicyKeys = parts.getOrNull(5)
        .orEmpty()
        .split(',')
        .mapNotNull { value -> HardshipPolicyKey.fromStorageKey(value) }
        .toSet()
    val emergencyPassGrants = parts.getOrNull(6)
        .orEmpty()
        .split(';')
        .mapNotNull { encodedGrant ->
            val grantParts = encodedGrant.split(',', limit = 4)
            val packageName = grantParts.getOrNull(0).orEmpty()
            val policyKey = grantParts.getOrNull(1)?.let { value -> HardshipPolicyKey.fromStorageKey(value) }
                ?: return@mapNotNull null
            val grantedAtMillis = grantParts.getOrNull(2)?.toLongOrNull() ?: return@mapNotNull null
            val expiresAtMillis = grantParts.getOrNull(3)?.toLongOrNull() ?: return@mapNotNull null
            if (packageName.isBlank() || expiresAtMillis <= 0L) return@mapNotNull null
            EmergencyPassGrant(packageName, policyKey, grantedAtMillis, expiresAtMillis)
        }
    return HardshipRuntimeState(
        dateKey = dateKey,
        reflections = reflections,
        bypassedPolicies = bypassedPolicies,
        activePolicies = activePolicies,
        bypassedPolicyKeys = bypassedPolicyKeys,
        activePolicyKeys = activePolicyKeys,
        emergencyPassGrants = emergencyPassGrants,
        lastEmergencyPassUsedAtMillis = lastEmergencyPassUsedAtMillis.coerceAtLeast(0L),
    )
}

private fun HardshipRuntimeState.toHardshipRuntimeStateEncoded(): String {
    val encodedBypasses = bypassedPolicies
        .sortedBy { policyType -> policyType.ordinal }
        .joinToString(",") { policyType -> policyType.name }
    val encodedReflections = reflections.entries
        .sortedBy { (key, _) -> key }
        .joinToString(";") { (key, entry) ->
            listOf(
                key,
                entry.readyAtMillis.coerceAtLeast(0L).toString(),
                entry.useCount.coerceAtLeast(0).toString(),
            ).joinToString(",")
        }
    val encodedActivePolicies = activePolicies
        .sortedBy { policyType -> policyType.ordinal }
        .joinToString(",") { policyType -> policyType.name }
    val encodedBypassedPolicyKeys = bypassedPolicyKeys
        .sortedBy { key -> key.storageKey }
        .joinToString(",") { key -> key.storageKey }
    val encodedActivePolicyKeys = activePolicyKeys
        .sortedBy { key -> key.storageKey }
        .joinToString(",") { key -> key.storageKey }
    val encodedEmergencyPassGrants = emergencyPassGrants
        .sortedWith(compareBy<EmergencyPassGrant> { grant -> grant.packageName }.thenBy { grant -> grant.policyKey.storageKey })
        .joinToString(";") { grant ->
            listOf(
                grant.packageName,
                grant.policyKey.storageKey,
                grant.grantedAtMillis.coerceAtLeast(0L).toString(),
                grant.expiresAtMillis.coerceAtLeast(0L).toString(),
            ).joinToString(",")
        }
    return listOf(
        dateKey.ifBlank { currentTemporaryUnlockDateKey() },
        encodedBypasses,
        encodedActivePolicies,
        encodedReflections,
        encodedBypassedPolicyKeys,
        encodedActivePolicyKeys,
        encodedEmergencyPassGrants,
    ).joinToString("#")
}

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

private fun Iterable<HardshipLevel>.maxHardshipLevel(fallback: HardshipLevel): HardshipLevel {
    return maxByOrNull { level -> level.storageValue } ?: fallback
}

private fun List<AppGroupPolicy>.maxAppGroupHardshipLevel(fallback: HardshipLevel): HardshipLevel =
    map { group -> group.hardshipLevel }.maxHardshipLevel(fallback)

private fun List<ScheduleTemplatePolicy>.maxScheduleHardshipLevel(fallback: HardshipLevel): HardshipLevel =
    map { schedule -> schedule.hardshipLevel }.maxHardshipLevel(fallback)

private fun String.toPackageSet(): Set<String> {
    return split(',', '\n')
        .map { packageName -> packageName.trim() }
        .filter { packageName -> packageName.isNotBlank() }
        .toSet()
}

private fun Set<String>.cleanUserPolicyPackageSet(): Set<String> {
    return asSequence()
        .map { packageName -> packageName.trim() }
        .filter { packageName ->
            packageName.isNotBlank() && packageName !in SafetyGate.neverBlockPackages
        }
        .toSet()
}

private fun Set<String>.toPackageString(): String = sorted().joinToString(",")

private fun String.encodePolicyField(): String {
    return replace("%", "%25")
        .replace(";", "%3B")
        .replace("^", "%5E")
        .replace(":", "%3A")
        .replace(",", "%2C")
}

private fun String.decodePolicyField(): String {
    return replace("%2C", ",")
        .replace("%3A", ":")
        .replace("%5E", "^")
        .replace("%3B", ";")
        .replace("%25", "%")
}

private fun TemporaryUnlockState.applyRemoteCommand(command: RemoteParentCommand): TemporaryUnlockState {
    return when (command.type) {
        RemoteParentCommandType.AddAppTime -> {
            val packageName = command.targetPackageName
            if (packageName.isBlank() || command.minutes <= 0) {
                this
            } else {
                val currentAllowance = packageAllowances[packageName] ?: TemporaryPackageAllowance()
                copy(
                    packageAllowances = packageAllowances + (
                        packageName to currentAllowance.withExtraTime(
                            extraMinutes = command.minutes,
                            nowMillis = command.timestampMillis.takeIf { timestamp -> timestamp > 0L }
                                ?: System.currentTimeMillis(),
                        )
                    ),
                )
            }
        }

        RemoteParentCommandType.UnlockAppToday -> {
            val packageName = command.targetPackageName
            if (packageName.isBlank()) {
                this
            } else {
                val currentAllowance = packageAllowances[packageName] ?: TemporaryPackageAllowance()
                copy(
                    packageAllowances = packageAllowances + (
                        packageName to currentAllowance.copy(unlockedForToday = true)
                    ),
                )
            }
        }

        RemoteParentCommandType.AddTotalTime -> {
            if (command.minutes <= 0) {
                this
            } else {
                copy(totalExtraMinutes = (totalExtraMinutes + command.minutes).coerceAtMost(MAX_TEMPORARY_EXTRA_MINUTES))
            }
        }

        RemoteParentCommandType.UnlockTotalToday -> copy(totalUnlockedForToday = true)
    }
}

private fun RemoteParentCommand.isBlockedByActiveHardship(
    activeHardshipPolicies: Set<HardshipPolicyType>,
): Boolean {
    return when (type) {
        RemoteParentCommandType.AddTotalTime,
        RemoteParentCommandType.UnlockTotalToday ->
            HardshipPolicyType.DailyLimit in activeHardshipPolicies

        RemoteParentCommandType.AddAppTime,
        RemoteParentCommandType.UnlockAppToday ->
            activeHardshipPolicies.any { policyType ->
                policyType != HardshipPolicyType.DailyLimit
            }
    }
}

private fun TemporaryUnlockState.toTemporaryUnlocksEncoded(): String {
    val packageText = packageAllowances
        .toSortedMap()
        .map { (packageName, allowance) ->
            listOf(
                packageName.encodePolicyField(),
                allowance.extraMinutes.coerceAtLeast(0).toString(),
                allowance.unlockedForToday.toString(),
                allowance.temporaryAllowedUntilMillis.coerceAtLeast(0L).toString(),
                allowance.hardshipAllowanceUntilMillis.coerceAtLeast(0L).toString(),
            ).joinToString(TEMP_PACKAGE_FIELD_SEPARATOR)
        }
        .joinToString(TEMP_PACKAGE_SEPARATOR)

    return listOf(
        dateKey.encodePolicyField(),
        totalExtraMinutes.coerceAtLeast(0).toString(),
        totalUnlockedForToday.toString(),
        packageText.encodePolicyField(),
    ).joinToString(TEMP_FIELD_SEPARATOR)
}

private fun String.toTemporaryUnlockState(): TemporaryUnlockState {
    if (isBlank()) {
        return TemporaryUnlockState(dateKey = currentTemporaryUnlockDateKey())
    }

    val parts = split(TEMP_FIELD_SEPARATOR)
    if (parts.size != 4) {
        return TemporaryUnlockState(dateKey = currentTemporaryUnlockDateKey())
    }

    val packages = parts[3].decodePolicyField()
        .split(TEMP_PACKAGE_SEPARATOR)
        .mapNotNull { encodedPackage ->
            val packageParts = encodedPackage.split(TEMP_PACKAGE_FIELD_SEPARATOR)
            if (packageParts.size !in 3..5) {
                null
            } else {
                val packageName = packageParts[0].decodePolicyField()
                val extraMinutes = packageParts[1].toIntOrNull()?.coerceAtLeast(0) ?: 0
                val unlockedForToday = packageParts[2].toBooleanStrictOrNull() ?: false
                val temporaryAllowedUntilMillis = packageParts.getOrNull(3)
                    ?.toLongOrNull()
                    ?.coerceAtLeast(0L)
                    ?: 0L
                val hardshipAllowanceUntilMillis = packageParts.getOrNull(4)
                    ?.toLongOrNull()
                    ?.coerceAtLeast(0L)
                    ?: 0L
                packageName.takeIf { name -> name.isNotBlank() }?.let { name ->
                    name to TemporaryPackageAllowance(
                        extraMinutes = extraMinutes,
                        unlockedForToday = unlockedForToday,
                        temporaryAllowedUntilMillis = temporaryAllowedUntilMillis,
                        hardshipAllowanceUntilMillis = hardshipAllowanceUntilMillis,
                    )
                }
            }
        }
        .toMap()

    return TemporaryUnlockState(
        dateKey = parts[0].decodePolicyField(),
        totalExtraMinutes = parts[1].toIntOrNull()?.coerceAtLeast(0) ?: 0,
        totalUnlockedForToday = parts[2].toBooleanStrictOrNull() ?: false,
        packageAllowances = packages,
    )
}

private fun ParentManagementState.toParentManagementStateEncoded(): String {
    return listOf(
        paired.toString(),
        parentAccountId.encodePolicyField(),
        childDeviceId.encodePolicyField(),
        childDeviceName.encodePolicyField(),
        lastSyncMillis.coerceAtLeast(0L).toString(),
        remoteCommands.toRemoteParentCommandsEncoded().encodePolicyField(),
        remoteUnlockRequests.toRemoteUnlockRequestsEncoded().encodePolicyField(),
        deviceRole.toStorageValue(),
        pairingCode.encodePolicyField(),
        linkedChildPairingCodes
            .map { code -> code.normalizedPairingCode() }
            .filter { code -> code.isNotBlank() }
            .sorted()
            .joinToString(",")
            .encodePolicyField(),
        linkedChildDevices.toLinkedChildDevicesEncoded().encodePolicyField(),
        localProfileName.encodePolicyField(),
        linkedParentDevices.toLinkedParentDevicesEncoded().encodePolicyField(),
    ).joinToString(PARENT_FIELD_SEPARATOR)
}

private fun String.toParentManagementState(): ParentManagementState {
    if (isBlank()) {
        return ParentManagementState()
    }

    val parts = split(PARENT_FIELD_SEPARATOR)
    if (parts.size !in 6..13) {
        return ParentManagementState()
    }

    val legacyLinkedChildren = if (parts.getOrNull(10).isNullOrBlank() && parts[2].decodePolicyField().isNotBlank()) {
        listOf(
            LinkedChildDevice(
                childDeviceId = parts[2].decodePolicyField(),
                childDeviceName = parts[3].decodePolicyField().ifBlank { "Child device" },
                pairingCode = parts.getOrNull(9)
                    ?.decodePolicyField()
                    ?.split(",")
                    ?.firstOrNull()
                    .orEmpty(),
                linkedAtMillis = parts[4].toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
            ),
        )
    } else {
        emptyList()
    }

    return ParentManagementState(
        paired = parts[0].toBooleanStrictOrNull() ?: false,
        parentAccountId = parts[1].decodePolicyField(),
        childDeviceId = parts[2].decodePolicyField(),
        childDeviceName = parts[3].decodePolicyField(),
        localProfileName = parts.getOrNull(11)
            ?.decodePolicyField()
            .orEmpty()
            .ifBlank { parts[1].decodePolicyField().ifBlank { parts[3].decodePolicyField() } },
        lastSyncMillis = parts[4].toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
        remoteCommands = parts[5].decodePolicyField().toRemoteParentCommands(),
        remoteUnlockRequests = parts.getOrNull(6)
            ?.decodePolicyField()
            ?.toRemoteUnlockRequests()
            .orEmpty(),
        deviceRole = parts.getOrNull(7)
            ?.toParentDeviceRole()
            ?: ParentDeviceRole.Child,
        pairingCode = parts.getOrNull(8)
            ?.decodePolicyField()
            ?.normalizedPairingCode()
            .orEmpty(),
        linkedChildPairingCodes = parts.getOrNull(9)
            ?.decodePolicyField()
            ?.split(",")
            ?.map { code -> code.normalizedPairingCode() }
            ?.filter { code -> code.isNotBlank() }
            ?.toSet()
            .orEmpty(),
        linkedChildDevices = parts.getOrNull(10)
            ?.decodePolicyField()
            ?.toLinkedChildDevices()
            ?.ifEmpty { legacyLinkedChildren }
            ?: legacyLinkedChildren,
        linkedParentDevices = parts.getOrNull(12)
            ?.decodePolicyField()
            ?.toLinkedParentDevices()
            .orEmpty(),
    )
}

private fun ParentNotificationState.toParentNotificationStateEncoded(): String {
    return listOf(
        handledEventTokens
            .filter { token -> token.isNotBlank() }
            .joinToString(",")
            .encodePolicyField(),
        activePendingRequestIds
            .filter { id -> id.isNotBlank() }
            .sorted()
            .joinToString(",")
            .encodePolicyField(),
        lastAttemptMillis.coerceAtLeast(0L).toString(),
        lastSuccessMillis.coerceAtLeast(0L).toString(),
        lastError.encodePolicyField(),
    ).joinToString(PARENT_FIELD_SEPARATOR)
}

private fun String.toParentNotificationState(): ParentNotificationState {
    if (isBlank()) {
        return ParentNotificationState()
    }
    val parts = split(PARENT_FIELD_SEPARATOR)
    if (parts.size != 5) {
        return ParentNotificationState()
    }
    return ParentNotificationState(
        handledEventTokens = parts[0]
            .decodePolicyField()
            .split(",")
            .filter { token -> token.isNotBlank() }
            .takeLast(MAX_PARENT_NOTIFICATION_EVENT_TOKENS)
            .toSet(),
        activePendingRequestIds = parts[1]
            .decodePolicyField()
            .split(",")
            .filter { id -> id.isNotBlank() }
            .toSet(),
        lastAttemptMillis = parts[2].toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
        lastSuccessMillis = parts[3].toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
        lastError = parts[4].decodePolicyField(),
    )
}

private fun ParentManagementState.syncChildDevices(): List<LinkedChildDevice> {
    val legacyChild = childDeviceId.takeIf { id -> id.isNotBlank() }?.let { id ->
        LinkedChildDevice(
            childDeviceId = id,
            childDeviceName = childDeviceName.ifBlank { "Child device" },
            linkedAtMillis = lastSyncMillis,
        )
    }
    return (linkedChildDevices + listOfNotNull(legacyChild))
        .filter { child -> child.childDeviceId.isNotBlank() }
        .distinctBy { child -> child.childDeviceId }
}

private fun List<LinkedChildDevice>.mergeWithLocalLinkedChildren(
    localChildren: List<LinkedChildDevice>,
): List<LinkedChildDevice> {
    if (isEmpty()) {
        return localChildren
    }
    val remoteById = associateBy { child -> child.childDeviceId }
    val mergedLocal = localChildren.mapNotNull { localChild ->
        val remoteChild = remoteById[localChild.childDeviceId] ?: return@mapNotNull null
        localChild.copy(
            childDeviceName = remoteChild.childDeviceName.ifBlank { localChild.childDeviceName },
            linkedAtMillis = remoteChild.linkedAtMillis.takeIf { linkedAt -> linkedAt > 0L }
                ?: localChild.linkedAtMillis,
        )
    }
    val localIds = mergedLocal.map { child -> child.childDeviceId }.toSet()
    return (mergedLocal + filterNot { remoteChild -> remoteChild.childDeviceId in localIds })
        .filter { child -> child.childDeviceId.isNotBlank() }
        .distinctBy { child -> child.childDeviceId }
}

private fun List<LinkedChildDevice>.toLinkedChildDevicesEncoded(): String {
    return distinctBy { child -> child.childDeviceId }
        .filter { child -> child.childDeviceId.isNotBlank() }
        .joinToString(LINKED_CHILD_SEPARATOR) { child ->
            listOf(
                child.childDeviceId.encodePolicyField(),
                child.childDeviceName.encodePolicyField(),
                child.pairingCode.encodePolicyField(),
                child.linkedAtMillis.coerceAtLeast(0L).toString(),
            ).joinToString(LINKED_CHILD_FIELD_SEPARATOR)
        }
}

private fun String.toLinkedChildDevices(): List<LinkedChildDevice> {
    if (isBlank()) {
        return emptyList()
    }
    return split(LINKED_CHILD_SEPARATOR)
        .mapNotNull { encodedChild ->
            val parts = encodedChild.split(LINKED_CHILD_FIELD_SEPARATOR)
            if (parts.size != 4) {
                null
            } else {
                val childDeviceId = parts[0].decodePolicyField()
                childDeviceId.takeIf { id -> id.isNotBlank() }?.let {
                    LinkedChildDevice(
                        childDeviceId = childDeviceId,
                        childDeviceName = parts[1].decodePolicyField().ifBlank { "Child device" },
                        pairingCode = parts[2].decodePolicyField(),
                        linkedAtMillis = parts[3].toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
                    )
                }
            }
        }
        .distinctBy { child -> child.childDeviceId }
}

private fun List<LinkedParentDevice>.toLinkedParentDevicesEncoded(): String {
    return distinctBy { parent -> parent.parentUid }
        .filter { parent -> parent.parentUid.isNotBlank() }
        .joinToString(LINKED_CHILD_SEPARATOR) { parent ->
            listOf(
                parent.parentUid.encodePolicyField(),
                parent.parentDisplayName.encodePolicyField(),
                parent.linkedAtMillis.coerceAtLeast(0L).toString(),
            ).joinToString(LINKED_CHILD_FIELD_SEPARATOR)
        }
}

private fun String.toLinkedParentDevices(): List<LinkedParentDevice> {
    if (isBlank()) {
        return emptyList()
    }
    return split(LINKED_CHILD_SEPARATOR)
        .mapNotNull { encodedParent ->
            val parts = encodedParent.split(LINKED_CHILD_FIELD_SEPARATOR)
            if (parts.size != 3) {
                null
            } else {
                val parentUid = parts[0].decodePolicyField()
                parentUid.takeIf { id -> id.isNotBlank() }?.let {
                    LinkedParentDevice(
                        parentUid = parentUid,
                        parentDisplayName = parts[1].decodePolicyField().ifBlank { "Parent device" },
                        linkedAtMillis = parts[2].toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
                    )
                }
            }
        }
        .distinctBy { parent -> parent.parentUid }
}

private fun List<RemoteParentCommand>.toRemoteParentCommandsEncoded(): String {
    return take(MAX_REMOTE_PARENT_COMMANDS_TOP_LEVEL)
        .map { command ->
            listOf(
                command.id.encodePolicyField(),
                command.timestampMillis.coerceAtLeast(0L).toString(),
                command.type.toStorageValue(),
                command.targetPackageName.encodePolicyField(),
                command.targetAppName.encodePolicyField(),
                command.minutes.coerceAtLeast(0).toString(),
                command.status.toStorageValue(),
                command.message.encodePolicyField(),
            ).joinToString(REMOTE_COMMAND_FIELD_SEPARATOR)
        }
        .joinToString(REMOTE_COMMAND_SEPARATOR)
}

private fun String.toRemoteParentCommands(): List<RemoteParentCommand> {
    if (isBlank()) {
        return emptyList()
    }
    return split(REMOTE_COMMAND_SEPARATOR)
        .mapNotNull { encodedCommand ->
            val parts = encodedCommand.split(REMOTE_COMMAND_FIELD_SEPARATOR)
            if (parts.size != 8) {
                null
            } else {
                val type = parts[2].toRemoteParentCommandTypeOrNull()
                    ?: return@mapNotNull null
                val status = parts[6].toRemoteParentCommandStatusOrNull()
                    ?: RemoteParentCommandStatus.Pending
                RemoteParentCommand(
                    id = parts[0].decodePolicyField(),
                    timestampMillis = parts[1].toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
                    type = type,
                    targetPackageName = parts[3].decodePolicyField(),
                    targetAppName = parts[4].decodePolicyField(),
                    minutes = parts[5].toIntOrNull()?.coerceAtLeast(0) ?: 0,
                    status = status,
                    message = parts[7].decodePolicyField(),
                )
            }
        }
        .take(MAX_REMOTE_PARENT_COMMANDS_TOP_LEVEL)
}

private fun List<RemoteUnlockRequest>.toRemoteUnlockRequestsEncoded(): String {
    return take(MAX_REMOTE_UNLOCK_REQUESTS_TOP_LEVEL)
        .map { request ->
            listOf(
                request.id.encodePolicyField(),
                request.childDeviceId.encodePolicyField(),
                request.childDeviceName.encodePolicyField(),
                request.createdAtMillis.coerceAtLeast(0L).toString(),
                request.expiresAtMillis.coerceAtLeast(0L).toString(),
                request.blockReason.toStorageValue(),
                request.targetPackageName.encodePolicyField(),
                request.targetAppName.encodePolicyField(),
                request.targetGroupName.encodePolicyField(),
                request.scheduleName.encodePolicyField(),
                request.usedMillis.coerceAtLeast(0L).toString(),
                request.limitMillis?.coerceAtLeast(0L)?.toString().orEmpty(),
                request.alreadyGrantedExtraMinutes.coerceAtLeast(0).toString(),
                request.unlockedForToday.toString(),
                request.requestedMinutes.coerceAtLeast(0).toString(),
                request.childMessage.encodePolicyField(),
                request.status.toStorageValue(),
            ).joinToString(REMOTE_REQUEST_FIELD_SEPARATOR)
        }
        .joinToString(REMOTE_REQUEST_SEPARATOR)
}

private fun String.toRemoteUnlockRequests(): List<RemoteUnlockRequest> {
    if (isBlank()) {
        return emptyList()
    }
    return split(REMOTE_REQUEST_SEPARATOR)
        .mapNotNull { encodedRequest ->
            val parts = encodedRequest.split(REMOTE_REQUEST_FIELD_SEPARATOR)
            if (parts.size != 17) {
                null
            } else {
                val blockReason = parts[5].toRemoteRequestBlockReasonOrNull()
                    ?: return@mapNotNull null
                val status = parts[16].toRemoteUnlockRequestStatusOrNull()
                    ?: RemoteUnlockRequestStatus.Pending
                RemoteUnlockRequest(
                    id = parts[0].decodePolicyField(),
                    childDeviceId = parts[1].decodePolicyField(),
                    childDeviceName = parts[2].decodePolicyField(),
                    createdAtMillis = parts[3].toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
                    expiresAtMillis = parts[4].toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
                    blockReason = blockReason,
                    targetPackageName = parts[6].decodePolicyField(),
                    targetAppName = parts[7].decodePolicyField(),
                    targetGroupName = parts[8].decodePolicyField(),
                    scheduleName = parts[9].decodePolicyField(),
                    usedMillis = parts[10].toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
                    limitMillis = parts[11].toLongOrNull()?.coerceAtLeast(0L),
                    alreadyGrantedExtraMinutes = parts[12].toIntOrNull()?.coerceAtLeast(0) ?: 0,
                    unlockedForToday = parts[13].toBooleanStrictOrNull() ?: false,
                    requestedMinutes = parts[14].toIntOrNull()?.coerceAtLeast(0) ?: 0,
                    childMessage = parts[15].decodePolicyField(),
                    status = status,
                )
            }
        }
        .take(MAX_REMOTE_UNLOCK_REQUESTS_TOP_LEVEL)
}

private fun RemoteParentCommandType.toRemoteCommandMessage(appName: String, minutes: Int): String {
    return when (this) {
        RemoteParentCommandType.AddAppTime ->
            "Remote parent added ${minutes.toTimeLabel()} for ${appName.ifBlank { "app" }}"
        RemoteParentCommandType.UnlockAppToday ->
            "Remote parent unlocked ${appName.ifBlank { "app" }} for today"
        RemoteParentCommandType.AddTotalTime ->
            "Remote parent added ${minutes.toTimeLabel()} to daily limit"
        RemoteParentCommandType.UnlockTotalToday ->
            "Remote parent unlocked daily limit for today"
    }
}

private fun RemoteUnlockRequest.expireIfNeeded(nowMillis: Long): RemoteUnlockRequest {
    return if (status == RemoteUnlockRequestStatus.Pending && expiresAtMillis > 0L && expiresAtMillis < nowMillis) {
        copy(status = RemoteUnlockRequestStatus.Expired)
    } else {
        this
    }
}

private fun RemoteUnlockRequest.isSameRemoteRequestTarget(
    blockReason: RemoteRequestBlockReason,
    targetPackageName: String,
    targetGroupName: String,
    scheduleName: String,
): Boolean {
    return this.blockReason == blockReason &&
        this.targetPackageName == targetPackageName &&
        this.targetGroupName == targetGroupName &&
        this.scheduleName == scheduleName
}

internal fun List<RemoteUnlockRequest>.withOnlyLatestPendingRequestPerTarget(
    nowMillis: Long,
): List<RemoteUnlockRequest> {
    val newerRequests = mutableListOf<RemoteUnlockRequest>()
    return sortedByDescending { request -> request.createdAtMillis }
        .map { request ->
            val current = request.expireIfNeeded(nowMillis)
            val newerRequestExists = newerRequests.any { newer ->
                    newer.isSameRemoteRequestTarget(
                        blockReason = current.blockReason,
                        targetPackageName = current.targetPackageName,
                        targetGroupName = current.targetGroupName,
                        scheduleName = current.scheduleName,
                    )
                }
            newerRequests += current
            if (current.status == RemoteUnlockRequestStatus.Pending && newerRequestExists) {
                current.copy(status = RemoteUnlockRequestStatus.Expired)
            } else {
                current
            }
        }
}

private fun createPairingCode(): String {
    val raw = UUID.randomUUID().toString()
        .filter { char -> char.isLetterOrDigit() }
        .take(6)
        .uppercase()
    return "SR-$raw"
}

private fun String.normalizedPairingCode(): String {
    val raw = trim()
        .uppercase()
        .filter { char -> char.isLetterOrDigit() }
        .removePrefix("SR")
        .take(6)
    return if (raw.isBlank()) "" else "SR-$raw"
}

private fun String.isValidPairingCodeFormat(): Boolean {
    return length == 9 && startsWith("SR-") && drop(3).all { char -> char.isLetterOrDigit() }
}

private fun ParentRemoteSyncResult.toPairingOperationFailure(): PairingOperationFailure {
    return when (this) {
        ParentRemoteSyncResult.Success -> PairingOperationFailure.Unknown
        ParentRemoteSyncResult.LocalOnly -> PairingOperationFailure.CloudUnavailable
        is ParentRemoteSyncResult.Failed -> kind.toPairingOperationFailure()
    }
}

private fun ParentRemotePairingResolution.Failed.toPairingOperationFailure(): PairingOperationFailure {
    return kind.toPairingOperationFailure()
}

private fun ParentRemoteFailureKind.toPairingOperationFailure(): PairingOperationFailure {
    return when (this) {
        ParentRemoteFailureKind.Network -> PairingOperationFailure.Network
        ParentRemoteFailureKind.PermissionDenied -> PairingOperationFailure.PermissionDenied
        ParentRemoteFailureKind.Authentication -> PairingOperationFailure.Authentication
        ParentRemoteFailureKind.NotFound -> PairingOperationFailure.InvalidCode
        ParentRemoteFailureKind.Unknown -> PairingOperationFailure.Unknown
    }
}

private const val GROUP_SEPARATOR = ";"
private const val GROUP_FIELD_SEPARATOR = "^"
private const val EMPTY_APP_GROUPS_ENCODED = "__empty__"
private const val APP_LIMIT_HARDSHIP_LEVELS_V2_PREFIX = "v2|"
private const val APP_LIMIT_ACTIVE_DAYS_V1_PREFIX = "v1|"
private const val TEMP_FIELD_SEPARATOR = "^"
private const val TEMP_PACKAGE_SEPARATOR = ";"
private const val TEMP_PACKAGE_FIELD_SEPARATOR = ":"
private const val PARENT_FIELD_SEPARATOR = "^"
private const val REMOTE_COMMAND_SEPARATOR = ";"
private const val REMOTE_COMMAND_FIELD_SEPARATOR = ":"
private const val REMOTE_REQUEST_SEPARATOR = ";"
private const val REMOTE_REQUEST_FIELD_SEPARATOR = ":"
private const val LINKED_CHILD_SEPARATOR = ";"
private const val LINKED_CHILD_FIELD_SEPARATOR = ":"
private const val MAX_REMOTE_PARENT_COMMANDS_TOP_LEVEL = 30
private const val MAX_REMOTE_UNLOCK_REQUESTS_TOP_LEVEL = 30
private const val MAX_SCHEDULE_TEMPLATES = 12
private const val SCHEDULE_MINUTES_PER_DAY = 24 * 60

private fun Int.toTimeLabel(): String {
    val safeMinutes = coerceAtLeast(0)
    val hours = safeMinutes / 60
    val minutes = safeMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}
