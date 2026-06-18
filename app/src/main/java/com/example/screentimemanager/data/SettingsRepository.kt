package com.example.screentimemanager.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

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
)

data class AppGroupPolicy(
    val name: String,
    val packageNames: Set<String>,
    val budgetMinutes: Int,
    val id: String = "",
)

enum class AppLanguage {
    Korean,
    English,
}

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

    suspend fun setAppLanguage(language: AppLanguage) {
        dataStore.edit { preferences ->
            preferences[APP_LANGUAGE] = language.name
        }
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
                message = "Kill Switch activated",
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

    private fun applySafeRecovery(preferences: MutablePreferences) {
        preferences[SAFE_MODE_ENABLED] = true
        preferences[POLICY_ENFORCEMENT_ENABLED] = false
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
        private val EMERGENCY_UNLOCK_PIN = stringPreferencesKey("emergency_unlock_pin")
        private val ADMIN_PIN = stringPreferencesKey("admin_pin")
        private val APP_LANGUAGE = stringPreferencesKey("app_language")
        private val EVENT_LOG = stringPreferencesKey("event_log")
        private val POLICY_ALERT_KEYS = stringPreferencesKey("policy_alert_keys")
        private val FOREGROUND_DETECTION_STATUS = stringPreferencesKey("foreground_detection_status")
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
        private const val EVENT_SEPARATOR = "~"
        private const val FIELD_SEPARATOR = "|"
        private const val MAX_EVENT_LOG_ENTRIES = 50
        private const val MAX_POLICY_ALERT_KEYS = 120
    }
}

fun UsagePolicySettings.normalizedAppGroups(): List<AppGroupPolicy> {
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
}

private fun String.decodePolicyField(): String {
    return replace("%5E", "^")
        .replace("%3B", ";")
        .replace("%25", "%")
}

private const val GROUP_SEPARATOR = ";"
private const val GROUP_FIELD_SEPARATOR = "^"
