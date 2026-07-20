package com.manisykh.screenrest.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.manisykh.screenrest.safety.SafetyGate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

private const val MAX_TEMPORARY_EXTRA_MINUTES = 720

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
            notificationAccessReady &&
            exactAlarmReady &&
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
    val appGroupName: String = "SNS",
    val appGroupPackages: String = "com.google.android.youtube",
    val appGroupBudgetMinutes: Int = 60,
    val appGroups: String = "",
    val appLimitRules: String = "",
    val scheduleBlockingEnabled: Boolean = false,
    val scheduleStartMinutes: Int = 22 * 60,
    val scheduleEndMinutes: Int = 7 * 60,
    val scheduleDays: String = "1,2,3,4,5,6,7",
    val scheduleTemplates: String = "",
    val activeScheduleTemplateId: String = "",
    val allowOnlyModeEnabled: Boolean = false,
)

data class TemporaryPackageAllowance(
    val extraMinutes: Int = 0,
    val unlockedForToday: Boolean = false,
    val temporaryAllowedUntilMillis: Long = 0L,
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
)

data class ScheduleTemplatePolicy(
    val id: String = "",
    val name: String,
    val startMinutes: Int,
    val endMinutes: Int,
    val days: Set<Int>,
    val allowedPackageNames: Set<String> = emptySet(),
)

enum class AppLanguage {
    Korean,
    English,
}

private const val APP_LANGUAGE_KOREAN = "Korean"
private const val APP_LANGUAGE_ENGLISH = "English"

data class PolicySectionExpansionSettings(
    val dailyPolicyExpanded: Boolean = true,
    val appGroupsExpanded: Boolean = true,
    val appLimitsExpanded: Boolean = true,
    val scheduleBlockingExpanded: Boolean = true,
    val allowOnlyModeExpanded: Boolean = true,
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
    fun observeParentRemoteChanges(childDeviceIds: Set<String>) =
        parentRemoteSyncDataSource.observeChanges(childDeviceIds)

    private val preferences: Flow<Preferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
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

    val permissionSetupCompletedOnce: Flow<Boolean> = preferences
        .map { preferences ->
            preferences[PERMISSION_SETUP_COMPLETED_ONCE] ?: false
        }

    val policySectionExpansionSettings: Flow<PolicySectionExpansionSettings> = preferences
        .map { preferences ->
            PolicySectionExpansionSettings(
                dailyPolicyExpanded = preferences[DAILY_POLICY_EXPANDED] ?: true,
                appGroupsExpanded = preferences[APP_GROUPS_EXPANDED] ?: true,
                appLimitsExpanded = preferences[APP_LIMITS_EXPANDED] ?: true,
                scheduleBlockingExpanded = preferences[SCHEDULE_BLOCKING_EXPANDED] ?: true,
                allowOnlyModeExpanded = preferences[ALLOW_ONLY_MODE_EXPANDED] ?: true,
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
                appGroupName = preferences[APP_GROUP_NAME] ?: "SNS",
                appGroupPackages = preferences[APP_GROUP_PACKAGES] ?: "com.google.android.youtube",
                appGroupBudgetMinutes = preferences[APP_GROUP_BUDGET_MINUTES] ?: 60,
                appGroups = preferences[APP_GROUPS].orEmpty(),
                appLimitRules = preferences[APP_LIMIT_RULES] ?: "",
                scheduleBlockingEnabled = preferences[SCHEDULE_BLOCKING_ENABLED] ?: false,
                scheduleStartMinutes = preferences[SCHEDULE_START_MINUTES] ?: 22 * 60,
                scheduleEndMinutes = preferences[SCHEDULE_END_MINUTES] ?: 7 * 60,
                scheduleDays = preferences[SCHEDULE_DAYS] ?: "1,2,3,4,5,6,7",
                scheduleTemplates = preferences[SCHEDULE_TEMPLATES].orEmpty(),
                activeScheduleTemplateId = preferences[ACTIVE_SCHEDULE_TEMPLATE_ID].orEmpty(),
                allowOnlyModeEnabled = preferences[ALLOW_ONLY_MODE_ENABLED] ?: false,
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

    val allowedAppPackages: Flow<Set<String>> = preferences
        .map { preferences ->
            preferences[ALLOWED_APP_PACKAGES].orEmpty().toPackageSet()
                .filterNot { packageName -> packageName in SafetyGate.neverBlockPackages }
                .toSet()
        }

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
        dataStore.edit { preferences ->
            val cleanPackageNames = packageNames
                .map { packageName -> packageName.trim() }
                .filter { packageName -> packageName.isNotBlank() && packageName !in SafetyGate.neverBlockPackages }
                .toSet()
            preferences[ALLOWED_APP_PACKAGES] = cleanPackageNames.sorted().joinToString(",")
            appendEvent(
                preferences = preferences,
                type = EventLogType.Safety,
                message = "Allowed apps updated: ${cleanPackageNames.size}",
            )
        }
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

    suspend fun generateChildPairingCode(adminPin: String): Boolean {
        var generated = false
        var generatedCode = ""
        var generatedChildDeviceId = ""
        var generatedChildDeviceName = ""
        dataStore.edit { preferences ->
            if (!isAdminPinValid(preferences, adminPin.trim())) {
                appendEvent(preferences, EventLogType.Warning, "Pairing code generation failed: invalid admin PIN")
                return@edit
            }
            val current = preferences[PARENT_MANAGEMENT_STATE].orEmpty().toParentManagementState()
            val childDeviceId = current.childDeviceId.ifBlank { UUID.randomUUID().toString() }
            val childDisplayName = current.localProfileName
                .ifBlank { current.childDeviceName }
                .ifBlank { "Child device" }
            val pairingCode = createPairingCode()
            preferences[PARENT_MANAGEMENT_STATE] = current.copy(
                deviceRole = ParentDeviceRole.Child,
                paired = true,
                childDeviceId = childDeviceId,
                childDeviceName = childDisplayName,
                localProfileName = childDisplayName,
                pairingCode = pairingCode,
                lastSyncMillis = System.currentTimeMillis(),
            ).toParentManagementStateEncoded()
            appendEvent(preferences, EventLogType.Safety, "Child pairing code generated")
            generatedCode = pairingCode
            generatedChildDeviceId = childDeviceId
            generatedChildDeviceName = childDisplayName
            generated = true
        }
        if (!generated) {
            return false
        }
        val remoteResult = parentRemoteSyncDataSource.publishPairingCode(
            pairingCode = generatedCode,
            childDeviceId = generatedChildDeviceId,
            childDeviceName = generatedChildDeviceName,
        )
        recordParentRemoteSyncResult(
                action = "publish pairing code",
                result = remoteResult,
        )
        if (remoteResult != ParentRemoteSyncResult.Success) {
            dataStore.edit { preferences ->
                val current = preferences[PARENT_MANAGEMENT_STATE].orEmpty().toParentManagementState()
                if (current.pairingCode == generatedCode) {
                    preferences[PARENT_MANAGEMENT_STATE] = current.copy(
                        paired = current.linkedParentDevices.isNotEmpty(),
                        pairingCode = "",
                    ).toParentManagementStateEncoded()
                }
                appendEvent(
                    preferences,
                    EventLogType.Warning,
                    "Pairing code generation rolled back: cloud publish failed",
                )
            }
            return false
        }
        return true
    }

    suspend fun registerChildPairingCode(pairingCode: String, childDeviceName: String, adminPin: String): Boolean {
        val cleanCode = pairingCode.normalizedPairingCode()
        if (cleanCode.isBlank()) {
            return false
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
            return false
        }
        val localParentDisplayName = parentManagementState.first().localProfileName
            .ifBlank { parentManagementState.first().parentAccountId }
            .ifBlank { "Parent device" }
        val pairingRecord = parentRemoteSyncDataSource.resolvePairingCode(
            pairingCode = cleanCode,
            parentDisplayName = localParentDisplayName,
        )
        if (pairingRecord == null) {
            dataStore.edit { preferences ->
                appendEvent(preferences, EventLogType.Warning, "Child device register failed: invalid or expired pairing code")
            }
            return false
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
        return registered
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

    suspend fun syncParentDevice() {
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
                parentRemoteSyncDataSource.fetchChildRequests(
                    parentAccountId = currentState.parentAccountId,
                    childDeviceId = currentState.childDeviceId,
                ).also {
                    remoteSyncAttempted = true
                    remoteSyncSucceeded = remoteSyncSucceeded && parentRemoteSyncDataSource.syncState.value.connected
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
            parentRemoteSyncDataSource.fetchChildCommands(currentState.childDeviceId).also {
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
                remoteUnlockRequests = current.remoteUnlockRequests.map { request ->
                    request.expireIfNeeded(System.currentTimeMillis())
                },
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
                val mergedRequests = (fetchedRequests + nextState.remoteUnlockRequests)
                    .distinctBy { request -> request.id }
                    .map { request -> request.expireIfNeeded(nextState.lastSyncMillis) }
                    .sortedByDescending { request -> request.createdAtMillis }
                    .take(MAX_REMOTE_UNLOCK_REQUESTS)
                nextState = nextState.copy(remoteUnlockRequests = mergedRequests)
            }
            var temporaryState = preferences[TEMPORARY_UNLOCKS].orEmpty()
                .toTemporaryUnlockState()
                .forToday()
            val existingCommandIds = nextState.remoteCommands.map { command -> command.id }.toSet()
            val newCommands = fetchedCommands
                .filter { command -> command.status == RemoteParentCommandStatus.Applied }
                .filter { command -> command.id !in existingCommandIds }
                .sortedBy { command -> command.timestampMillis }
            newCommands.forEach { command ->
                temporaryState = temporaryState.applyRemoteCommand(command)
                appendEvent(preferences, EventLogType.Info, "Remote command applied from cloud: ${command.message}")
            }
            if (newCommands.isNotEmpty()) {
                preferences[TEMPORARY_UNLOCKS] = temporaryState.toTemporaryUnlocksEncoded()
                nextState = nextState.copy(
                    remoteCommands = (newCommands.asReversed() + nextState.remoteCommands)
                        .distinctBy { command -> command.id }
                        .take(MAX_REMOTE_PARENT_COMMANDS),
                )
            }
            preferences[PARENT_MANAGEMENT_STATE] = nextState.toParentManagementStateEncoded()
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
    ): Boolean {
        if (requestedMinutes <= 0) {
            return false
        }
        var created = false
        var createdRequest: RemoteUnlockRequest? = null
        var reusedExistingRequest = false
        dataStore.edit { preferences ->
            val parentState = preferences[PARENT_MANAGEMENT_STATE].orEmpty().toParentManagementState()
            val now = System.currentTimeMillis()
            if (!parentState.paired) {
                appendEvent(preferences, EventLogType.Warning, "Remote unlock request failed: parent account not paired")
                return@edit
            }
            val cleanedRequests = parentState.remoteUnlockRequests
                .map { request -> request.expireIfNeeded(now) }
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
                val refreshedRequest = reusableRequest.copy(
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
        val request = createdRequest ?: return false
        val remoteResult = parentRemoteSyncDataSource.publishUnlockRequest(request)
        recordParentRemoteSyncResult(
            action = "publish unlock request",
            result = remoteResult,
        )
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
            return false
        }
        return created
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
        var saved = false
        dataStore.edit { preferences ->
            val savedPin = preferences[ADMIN_PIN] ?: DEFAULT_ADMIN_PIN
            if (adminPin != savedPin) {
                return@edit
            }
            preferences[WEEKDAY_LIMIT_MINUTES] = settings.weekdayLimitMinutes.coerceAtLeast(0)
            preferences[WEEKEND_LIMIT_MINUTES] = settings.weekendLimitMinutes.coerceAtLeast(0)
            preferences[MONDAY_LIMIT_MINUTES] = settings.mondayLimitMinutes.coerceAtLeast(0)
            preferences[TUESDAY_LIMIT_MINUTES] = settings.tuesdayLimitMinutes.coerceAtLeast(0)
            preferences[WEDNESDAY_LIMIT_MINUTES] = settings.wednesdayLimitMinutes.coerceAtLeast(0)
            preferences[THURSDAY_LIMIT_MINUTES] = settings.thursdayLimitMinutes.coerceAtLeast(0)
            preferences[FRIDAY_LIMIT_MINUTES] = settings.fridayLimitMinutes.coerceAtLeast(0)
            preferences[SATURDAY_LIMIT_MINUTES] = settings.saturdayLimitMinutes.coerceAtLeast(0)
            preferences[SUNDAY_LIMIT_MINUTES] = settings.sundayLimitMinutes.coerceAtLeast(0)
            preferences[APP_GROUP_NAME] = settings.appGroupName.trim().ifBlank { "Group" }
            preferences[APP_GROUP_PACKAGES] = settings.appGroupPackages.trim()
            preferences[APP_GROUP_BUDGET_MINUTES] = settings.appGroupBudgetMinutes.coerceAtLeast(0)
            preferences[APP_GROUPS] = settings.normalizedAppGroups().toAppGroupsEncoded()
            preferences[APP_LIMIT_RULES] = settings.appLimitRules.trim()
            preferences[SCHEDULE_BLOCKING_ENABLED] = settings.scheduleBlockingEnabled
            preferences[SCHEDULE_START_MINUTES] = settings.scheduleStartMinutes.coerceIn(0, MINUTES_PER_DAY - 1)
            preferences[SCHEDULE_END_MINUTES] = settings.scheduleEndMinutes.coerceIn(0, MINUTES_PER_DAY - 1)
            preferences[SCHEDULE_DAYS] = settings.scheduleDaySet().toScheduleDaysEncoded()
            preferences[SCHEDULE_TEMPLATES] = settings.normalizedScheduleTemplates().toScheduleTemplatesEncoded()
            preferences[ACTIVE_SCHEDULE_TEMPLATE_ID] = settings.activeScheduleTemplateId.trim()
            preferences[ALLOW_ONLY_MODE_ENABLED] = settings.allowOnlyModeEnabled
            appendEvent(
                preferences = preferences,
                type = EventLogType.Info,
                message = "Policy saved",
            )
            saved = true
        }
        return saved
    }

    suspend fun updateAdminPin(currentPin: String, newPin: String): Boolean {
        return updatePin(
            key = ADMIN_PIN,
            currentPin = currentPin,
            newPin = newPin,
            defaultPin = DEFAULT_ADMIN_PIN,
            successMessage = "Admin PIN changed",
        )
    }

    suspend fun updateEmergencyPin(currentPin: String, newPin: String): Boolean {
        return updatePin(
            key = EMERGENCY_UNLOCK_PIN,
            currentPin = currentPin,
            newPin = newPin,
            defaultPin = DEFAULT_EMERGENCY_UNLOCK_PIN,
            successMessage = "Emergency PIN changed",
        )
    }

    suspend fun activateKillSwitch() {
        dataStore.edit { preferences ->
            applySafeRecovery(preferences)
            appendEvent(
                preferences = preferences,
                type = EventLogType.Safety,
                message = "Kill Switch activated: Safe Mode ON, policy OFF, temporary allowances cleared",
            )
        }
    }

    suspend fun markAppStartedAndRecoverIfNeeded(): Boolean {
        var recovered = false
        dataStore.edit { preferences ->
            val previousRunClean = preferences[LAST_RUN_CLEAN] ?: true
            if (!previousRunClean) {
                applySafeRecovery(preferences)
                appendEvent(
                    preferences = preferences,
                    type = EventLogType.Safety,
                    message = "Auto Recovery enabled Safe Mode",
                )
                recovered = true
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

    suspend fun emergencyUnlock(pin: String): Boolean {
        var unlocked = false
        dataStore.edit { preferences ->
            val savedPin = preferences[EMERGENCY_UNLOCK_PIN] ?: DEFAULT_EMERGENCY_UNLOCK_PIN
            unlocked = pin == savedPin
            if (unlocked) {
                applySafeRecovery(preferences)
                appendEvent(
                    preferences = preferences,
                    type = EventLogType.Safety,
                    message = "Emergency Unlock succeeded",
                )
            } else {
                appendEvent(
                    preferences = preferences,
                    type = EventLogType.Warning,
                    message = "Emergency Unlock failed",
                )
            }
        }
        return unlocked
    }

    suspend fun verifyAdminPin(adminPin: String): Boolean {
        return isAdminPinValid(preferences.first(), adminPin.trim())
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
        key: Preferences.Key<String>,
        currentPin: String,
        newPin: String,
        defaultPin: String,
        successMessage: String,
    ): Boolean {
        val cleanNewPin = newPin.trim()
        if (cleanNewPin.length < 4) {
            return false
        }

        var updated = false
        dataStore.edit { preferences ->
            val savedPin = preferences[key] ?: defaultPin
            if (currentPin == savedPin) {
                preferences[key] = cleanNewPin
                appendEvent(preferences, EventLogType.Safety, successMessage)
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

    private fun isAdminPinValid(preferences: Preferences, adminPin: String): Boolean {
        val savedPin = preferences[ADMIN_PIN] ?: DEFAULT_ADMIN_PIN
        return adminPin == savedPin
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

    companion object {
        const val DEFAULT_EMERGENCY_UNLOCK_PIN = "0000"
        const val DEFAULT_ADMIN_PIN = "0000"

        private val SAFE_MODE_ENABLED = booleanPreferencesKey("safe_mode_enabled")
        private val POLICY_ENFORCEMENT_ENABLED = booleanPreferencesKey("policy_enforcement_enabled")
        private val WARNING_NOTIFICATIONS_ENABLED = booleanPreferencesKey("warning_notifications_enabled")
        private val LIMIT_NOTIFICATIONS_ENABLED = booleanPreferencesKey("limit_notifications_enabled")
        private val PERMISSION_SETUP_COMPLETED_ONCE = booleanPreferencesKey("permission_setup_completed_once")
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
        private val APP_LANGUAGE = stringPreferencesKey("app_language")
        private val EVENT_LOG = stringPreferencesKey("event_log")
        private val POLICY_ALERT_KEYS = stringPreferencesKey("policy_alert_keys")
        private val FOREGROUND_DETECTION_STATUS = stringPreferencesKey("foreground_detection_status")
        private val USAGE_MONITOR_STATUS = stringPreferencesKey("usage_monitor_status")
        private val SYSTEM_HEALTH_STATUS = stringPreferencesKey("system_health_status")
        private val CACHED_TODAY_USAGE = stringPreferencesKey("cached_today_usage")
        private val TEMPORARY_UNLOCKS = stringPreferencesKey("temporary_unlocks")
        private val ALLOWED_APP_PACKAGES = stringPreferencesKey("allowed_app_packages")
        private val PARENT_MANAGEMENT_STATE = stringPreferencesKey("parent_management_state")
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
        private val APP_GROUP_NAME = stringPreferencesKey("app_group_name")
        private val APP_GROUP_PACKAGES = stringPreferencesKey("app_group_packages")
        private val APP_GROUP_BUDGET_MINUTES = intPreferencesKey("app_group_budget_minutes")
        private val APP_GROUPS = stringPreferencesKey("app_groups")
        private val APP_LIMIT_RULES = stringPreferencesKey("app_limit_rules")
        private val SCHEDULE_BLOCKING_ENABLED = booleanPreferencesKey("schedule_blocking_enabled")
        private val SCHEDULE_START_MINUTES = intPreferencesKey("schedule_start_minutes")
        private val SCHEDULE_END_MINUTES = intPreferencesKey("schedule_end_minutes")
        private val SCHEDULE_DAYS = stringPreferencesKey("schedule_days")
        private val SCHEDULE_TEMPLATES = stringPreferencesKey("schedule_templates")
        private val ACTIVE_SCHEDULE_TEMPLATE_ID = stringPreferencesKey("active_schedule_template_id")
        private val ALLOW_ONLY_MODE_ENABLED = booleanPreferencesKey("allow_only_mode_enabled")
        private const val EVENT_SEPARATOR = "~"
        private const val FIELD_SEPARATOR = "|"
        private const val MAX_EVENT_LOG_ENTRIES = 50
        private const val MAX_POLICY_ALERT_KEYS = 120
        private const val MAX_REMOTE_PARENT_COMMANDS = 30
        private const val MAX_REMOTE_UNLOCK_REQUESTS = 30
        private const val REMOTE_UNLOCK_REQUEST_TTL_MILLIS = 10L * 60L * 1000L
        private const val MAX_CACHED_TODAY_USAGE_ENTRIES = 50
        private const val MINUTES_PER_DAY = 24 * 60
    }
}

fun currentTemporaryUnlockDateKey(): String {
    return LocalDate.now().toString()
}

fun UsagePolicySettings.scheduleDaySet(): Set<Int> {
    val parsedDays = scheduleDays
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
    return scheduleTemplates.toScheduleTemplatePolicies()
        .distinctBy { template -> template.id.ifBlank { template.name } }
        .take(MAX_SCHEDULE_TEMPLATES)
}

fun String.toScheduleTemplatePolicies(): List<ScheduleTemplatePolicy> {
    return split(GROUP_SEPARATOR)
        .mapNotNull { encodedTemplate ->
            val parts = encodedTemplate.split(GROUP_FIELD_SEPARATOR)
            if (parts.size !in 5..6) {
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
                ScheduleTemplatePolicy(
                    id = id,
                    name = name.ifBlank { "Schedule" },
                    startMinutes = startMinutes,
                    endMinutes = endMinutes,
                    days = days,
                    allowedPackageNames = allowedPackageNames,
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
            ).joinToString(GROUP_FIELD_SEPARATOR)
        }
        .joinToString(GROUP_SEPARATOR)
}

fun UsagePolicySettings.activeScheduleTemplate(now: LocalDateTime = LocalDateTime.now()): ScheduleTemplatePolicy? {
    val templates = normalizedScheduleTemplates()
    val activeNow = templates.firstOrNull { template -> template.isActiveAt(now) }
    if (activeNow != null) {
        return activeNow
    }
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
    }
}

fun UsagePolicySettings.activeScheduleAllowedPackages(): Set<String> {
    if (!scheduleBlockingEnabled || !isScheduleBlockingNow()) {
        return emptySet()
    }
    return activeScheduleTemplate()
        ?.allowedPackageNames
        ?.filter { packageName -> packageName.isNotBlank() }
        ?.toSet()
        .orEmpty()
}

fun UsagePolicySettings.isScheduleBlockingNow(now: LocalDateTime = LocalDateTime.now()): Boolean {
    if (!scheduleBlockingEnabled) {
        return false
    }
    return normalizedScheduleTemplates().any { template -> template.isActiveAt(now) }
}

private fun ScheduleTemplatePolicy.isActiveAt(now: LocalDateTime): Boolean {
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

fun UsagePolicySettings.normalizedAppGroups(): List<AppGroupPolicy> {
    if (appGroups == EMPTY_APP_GROUPS_ENCODED) {
        return emptyList()
    }
    val parsedGroups = appGroups.toAppGroupPolicies()
    if (parsedGroups.isNotEmpty()) {
        return parsedGroups
    }
    return listOf(
        AppGroupPolicy(
            name = appGroupName.ifBlank { "Group" },
            packageNames = appGroupPackages.toPackageSet(),
            budgetMinutes = appGroupBudgetMinutes.coerceAtLeast(0),
            id = "legacy-primary",
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
                    val budgetMinutes = parts[1].toIntOrNull()?.coerceAtLeast(0) ?: 0
                    val packageNames = parts[2].decodePolicyField().toPackageSet()
                    AppGroupPolicy(name, packageNames, budgetMinutes)
                }

                4 -> {
                    val id = parts[0].decodePolicyField()
                    val name = parts[1].decodePolicyField()
                    val budgetMinutes = parts[2].toIntOrNull()?.coerceAtLeast(0) ?: 0
                    val packageNames = parts[3].decodePolicyField().toPackageSet()
                    AppGroupPolicy(name, packageNames, budgetMinutes, id)
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
                group.budgetMinutes.coerceAtLeast(0).toString(),
                group.packageNames.sorted().joinToString(",").encodePolicyField(),
            ).joinToString(GROUP_FIELD_SEPARATOR)
        }.joinToString(GROUP_SEPARATOR)
}

private fun String.toPackageSet(): Set<String> {
    return split(',', '\n')
        .map { packageName -> packageName.trim() }
        .filter { packageName -> packageName.isNotBlank() }
        .toSet()
}

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

private fun TemporaryUnlockState.toTemporaryUnlocksEncoded(): String {
    val packageText = packageAllowances
        .toSortedMap()
        .map { (packageName, allowance) ->
            listOf(
                packageName.encodePolicyField(),
                allowance.extraMinutes.coerceAtLeast(0).toString(),
                allowance.unlockedForToday.toString(),
                allowance.temporaryAllowedUntilMillis.coerceAtLeast(0L).toString(),
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
            if (packageParts.size !in 3..4) {
                null
            } else {
                val packageName = packageParts[0].decodePolicyField()
                val extraMinutes = packageParts[1].toIntOrNull()?.coerceAtLeast(0) ?: 0
                val unlockedForToday = packageParts[2].toBooleanStrictOrNull() ?: false
                val temporaryAllowedUntilMillis = packageParts.getOrNull(3)
                    ?.toLongOrNull()
                    ?.coerceAtLeast(0L)
                    ?: 0L
                packageName.takeIf { name -> name.isNotBlank() }?.let { name ->
                    name to TemporaryPackageAllowance(
                        extraMinutes = extraMinutes,
                        unlockedForToday = unlockedForToday,
                        temporaryAllowedUntilMillis = temporaryAllowedUntilMillis,
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

private const val GROUP_SEPARATOR = ";"
private const val GROUP_FIELD_SEPARATOR = "^"
private const val EMPTY_APP_GROUPS_ENCODED = "__empty__"
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
