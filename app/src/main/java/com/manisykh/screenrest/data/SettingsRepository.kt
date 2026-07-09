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
)

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
    val lastSyncMillis: Long = 0L,
    val remoteCommands: List<RemoteParentCommand> = emptyList(),
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

enum class RemoteParentCommandStatus {
    Pending,
    Applied,
    Failed,
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

data class PolicySectionExpansionSettings(
    val dailyPolicyExpanded: Boolean = true,
    val appGroupsExpanded: Boolean = true,
    val appLimitsExpanded: Boolean = true,
    val scheduleBlockingExpanded: Boolean = true,
    val allowOnlyModeExpanded: Boolean = true,
)

class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
) {
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
            )
        }

    val appLanguage: Flow<AppLanguage> = preferences
        .map { preferences ->
            when (preferences[APP_LANGUAGE]) {
                AppLanguage.English.name -> AppLanguage.English
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

    suspend fun setAppLanguage(language: AppLanguage) {
        dataStore.edit { preferences ->
            preferences[APP_LANGUAGE] = language.name
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

    suspend fun unlinkParentAccount(adminPin: String): Boolean {
        var unlinked = false
        dataStore.edit { preferences ->
            if (!isAdminPinValid(preferences, adminPin.trim())) {
                appendEvent(preferences, EventLogType.Warning, "Parent unlink failed: invalid admin PIN")
                return@edit
            }
            val current = preferences[PARENT_MANAGEMENT_STATE].orEmpty().toParentManagementState()
            preferences[PARENT_MANAGEMENT_STATE] = current.copy(
                paired = false,
                parentAccountId = "",
                lastSyncMillis = System.currentTimeMillis(),
            ).toParentManagementStateEncoded()
            appendEvent(preferences, EventLogType.Safety, "Parent account unlinked")
            unlinked = true
        }
        return unlinked
    }

    suspend fun syncParentDevice() {
        dataStore.edit { preferences ->
            val current = preferences[PARENT_MANAGEMENT_STATE].orEmpty().toParentManagementState()
            preferences[PARENT_MANAGEMENT_STATE] = current.copy(
                childDeviceId = current.childDeviceId.ifBlank { UUID.randomUUID().toString() },
                lastSyncMillis = System.currentTimeMillis(),
            ).toParentManagementStateEncoded()
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
            val nextAllowance = currentAllowance.copy(
                extraMinutes = (currentAllowance.extraMinutes + extraMinutes).coerceAtMost(MAX_TEMPORARY_EXTRA_MINUTES),
            )
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
            val nextAllowance = currentAllowance.copy(
                extraMinutes = (currentAllowance.extraMinutes + extraMinutes).coerceAtMost(MAX_TEMPORARY_EXTRA_MINUTES),
            )
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
            type.name,
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
        val type = EventLogType.entries.firstOrNull { type -> type.name == parts[1] } ?: return null
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
        private const val MAX_TEMPORARY_EXTRA_MINUTES = 720
        private const val MAX_REMOTE_PARENT_COMMANDS = 30
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
}

private fun String.decodePolicyField(): String {
    return replace("%3A", ":")
        .replace("%5E", "^")
        .replace("%3B", ";")
        .replace("%25", "%")
}

private fun TemporaryUnlockState.toTemporaryUnlocksEncoded(): String {
    val packageText = packageAllowances
        .toSortedMap()
        .map { (packageName, allowance) ->
            listOf(
                packageName.encodePolicyField(),
                allowance.extraMinutes.coerceAtLeast(0).toString(),
                allowance.unlockedForToday.toString(),
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
            if (packageParts.size != 3) {
                null
            } else {
                val packageName = packageParts[0].decodePolicyField()
                val extraMinutes = packageParts[1].toIntOrNull()?.coerceAtLeast(0) ?: 0
                val unlockedForToday = packageParts[2].toBooleanStrictOrNull() ?: false
                packageName.takeIf { name -> name.isNotBlank() }?.let { name ->
                    name to TemporaryPackageAllowance(extraMinutes, unlockedForToday)
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
    ).joinToString(PARENT_FIELD_SEPARATOR)
}

private fun String.toParentManagementState(): ParentManagementState {
    if (isBlank()) {
        return ParentManagementState()
    }

    val parts = split(PARENT_FIELD_SEPARATOR)
    if (parts.size != 6) {
        return ParentManagementState()
    }

    return ParentManagementState(
        paired = parts[0].toBooleanStrictOrNull() ?: false,
        parentAccountId = parts[1].decodePolicyField(),
        childDeviceId = parts[2].decodePolicyField(),
        childDeviceName = parts[3].decodePolicyField(),
        lastSyncMillis = parts[4].toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
        remoteCommands = parts[5].decodePolicyField().toRemoteParentCommands(),
    )
}

private fun List<RemoteParentCommand>.toRemoteParentCommandsEncoded(): String {
    return take(MAX_REMOTE_PARENT_COMMANDS_TOP_LEVEL)
        .map { command ->
            listOf(
                command.id.encodePolicyField(),
                command.timestampMillis.coerceAtLeast(0L).toString(),
                command.type.name,
                command.targetPackageName.encodePolicyField(),
                command.targetAppName.encodePolicyField(),
                command.minutes.coerceAtLeast(0).toString(),
                command.status.name,
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
                val type = RemoteParentCommandType.entries.firstOrNull { type -> type.name == parts[2] }
                    ?: return@mapNotNull null
                val status = RemoteParentCommandStatus.entries.firstOrNull { status -> status.name == parts[6] }
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

private const val GROUP_SEPARATOR = ";"
private const val GROUP_FIELD_SEPARATOR = "^"
private const val EMPTY_APP_GROUPS_ENCODED = "__empty__"
private const val TEMP_FIELD_SEPARATOR = "^"
private const val TEMP_PACKAGE_SEPARATOR = ";"
private const val TEMP_PACKAGE_FIELD_SEPARATOR = ":"
private const val PARENT_FIELD_SEPARATOR = "^"
private const val REMOTE_COMMAND_SEPARATOR = ";"
private const val REMOTE_COMMAND_FIELD_SEPARATOR = ":"
private const val MAX_REMOTE_PARENT_COMMANDS_TOP_LEVEL = 30
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
