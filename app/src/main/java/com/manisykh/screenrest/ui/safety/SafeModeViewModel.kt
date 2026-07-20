package com.manisykh.screenrest.ui.safety

import android.app.AlarmManager
import android.app.Application
import android.content.ComponentName
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.manisykh.screenrest.blocking.UsageMonitorForegroundService
import com.manisykh.screenrest.data.AppLanguage
import com.manisykh.screenrest.data.CachedTodayUsageEntry
import com.manisykh.screenrest.data.EventLogEntry
import com.manisykh.screenrest.data.EventLogType
import com.manisykh.screenrest.data.ForegroundDetectionStatus
import com.manisykh.screenrest.data.ParentManagementState
import com.manisykh.screenrest.data.ParentDeviceRole
import com.manisykh.screenrest.data.ParentRemoteSyncDataSourceFactory
import com.manisykh.screenrest.data.PolicySectionExpansionSettings
import com.manisykh.screenrest.data.SettingsRepository
import com.manisykh.screenrest.data.ScheduleTemplatePolicy
import com.manisykh.screenrest.data.SystemHealthStatus
import com.manisykh.screenrest.data.TemporaryUnlockState
import com.manisykh.screenrest.data.UsagePolicySettings
import com.manisykh.screenrest.data.UsageMonitorStatus
import com.manisykh.screenrest.data.activeScheduleAllowedPackages
import com.manisykh.screenrest.data.temporaryRemainingMinutes
import com.manisykh.screenrest.data.isScheduleBlockingNow
import com.manisykh.screenrest.data.normalizedAppGroups
import com.manisykh.screenrest.data.normalizedScheduleTemplates
import com.manisykh.screenrest.data.scheduleDaySet
import com.manisykh.screenrest.data.toScheduleDaysEncoded
import com.manisykh.screenrest.data.toScheduleTemplatesEncoded
import com.manisykh.screenrest.data.settingsDataStore
import com.manisykh.screenrest.data.toAppGroupsEncoded
import com.manisykh.screenrest.notification.ScreenTimeNotificationListenerService
import com.manisykh.screenrest.notification.ParentRemoteNotificationType
import com.manisykh.screenrest.notification.UsageNotificationHelper
import com.manisykh.screenrest.notification.detectParentRemoteNotifications
import com.manisykh.screenrest.safety.BlockDecision
import com.manisykh.screenrest.safety.BlockDecisionEngine
import com.manisykh.screenrest.safety.BlockDecisionResult
import com.manisykh.screenrest.safety.OverlayPermissionChecker
import com.manisykh.screenrest.safety.SafetyGate
import com.manisykh.screenrest.usage.AppCatalogRepository
import com.manisykh.screenrest.usage.AppVisibility
import com.manisykh.screenrest.usage.AppUsageInfo
import com.manisykh.screenrest.usage.DailyUsageInfo
import com.manisykh.screenrest.usage.InstalledAppInfo
import com.manisykh.screenrest.usage.UsageStatsRepository
import com.manisykh.screenrest.worker.UsagePolicyAlertRunner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Calendar

data class SafeModeUiState(
    val safeModeEnabled: Boolean = true,
    val appLanguage: AppLanguage = AppLanguage.Korean,
    val policyEnforcementEnabled: Boolean = false,
    val warningNotificationsEnabled: Boolean = true,
    val limitNotificationsEnabled: Boolean = true,
    val emergencyUnlockStatus: EmergencyUnlockStatus = EmergencyUnlockStatus.Idle,
    val autoRecoveryStatus: AutoRecoveryStatus = AutoRecoveryStatus.Idle,
    val hasUsageAccess: Boolean = false,
    val usageAccessChecking: Boolean = false,
    val statisticsRefreshing: Boolean = false,
    val usageLastUpdatedAtMillis: Long = 0L,
    val statisticsLastUpdatedAtMillis: Long = 0L,
    val todayUsage: List<AppUsageInfo> = emptyList(),
    val usageStatistics: UsageStatistics = UsageStatistics(),
    val installedApps: List<InstalledAppInfo> = emptyList(),
    val allowedAppPackages: Set<String> = emptySet(),
    val parentManagementState: ParentManagementState = ParentManagementState(),
    val usagePolicySettings: UsagePolicySettings = UsagePolicySettings(),
    val temporaryUnlockState: TemporaryUnlockState = TemporaryUnlockState(),
    val policyDraftSettings: UsagePolicySettings = UsagePolicySettings(),
    val policyDraftAllowedAppPackages: Set<String> = emptySet(),
    val policyDraftHasChanges: Boolean = false,
    val policyBudgetValidation: PolicyBudgetValidation = PolicyBudgetValidation(),
    val policySummary: PolicySummary = PolicySummary(),
    val policySaveStatus: PolicySaveStatus = PolicySaveStatus.Idle,
    val pinChangeStatus: PinChangeStatus = PinChangeStatus.Idle,
    val safeModePinStatus: SafeModePinStatus = SafeModePinStatus.Idle,
    val eventLog: List<EventLogEntry> = emptyList(),
    val foregroundDetectionStatus: ForegroundDetectionStatus? = null,
    val usageMonitorStatus: UsageMonitorStatus = UsageMonitorStatus(),
    val systemHealthStatus: SystemHealthStatus = SystemHealthStatus(),
    val permissionSetupCompletedOnce: Boolean = false,
    val blockingReadiness: BlockingReadiness = BlockingReadiness(),
    val blockDecisionResults: List<BlockDecisionResult> = emptyList(),
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

data class UsageStatistics(
    val dailyUsage: List<DailyUsageInfo> = emptyList(),
    val topApps: TopAppsUsageSet = TopAppsUsageSet(),
)

data class TopAppsUsageSet(
    val oneDay: List<AppUsageInfo> = emptyList(),
    val sevenDays: List<AppUsageInfo> = emptyList(),
    val thirtyDays: List<AppUsageInfo> = emptyList(),
)

data class PolicySummary(
    val totalUsedMinutes: Int = 0,
    val totalLimitMinutes: Int = 120,
    val totalExtraMinutes: Int = 0,
    val totalUnlockedForToday: Boolean = false,
    val totalStatus: LimitStatus = LimitStatus.Normal,
    val groupName: String = "SNS",
    val groupUsedMinutes: Int = 0,
    val groupLimitMinutes: Int = 60,
    val groupStatus: LimitStatus = LimitStatus.Normal,
    val groupSummaries: List<AppGroupSummary> = emptyList(),
    val appLimitSummaries: List<AppLimitSummary> = emptyList(),
    val scheduleSummaries: List<ScheduleSummary> = emptyList(),
    val activeScheduleSummary: ScheduleSummary? = null,
    val nextScheduleSummary: ScheduleSummary? = null,
    val allowOnlyModeEnabled: Boolean = false,
    val allowOnlyAllowedAppCount: Int = 0,
    val temporaryAllowedApps: List<TemporaryAllowedAppSummary> = emptyList(),
    val warningCount: Int = 0,
    val exceededCount: Int = 0,
)

data class ScheduleSummary(
    val id: String,
    val name: String,
    val startMinutes: Int,
    val endMinutes: Int,
    val days: Set<Int>,
    val allowedAppCount: Int,
    val activeNow: Boolean,
    val minutesUntilStart: Int? = null,
)

data class TemporaryAllowedAppSummary(
    val appName: String,
    val packageName: String,
    val unlockedForToday: Boolean,
    val temporaryRemainingMinutes: Int,
)

internal fun buildTemporaryAllowedAppSummaries(
    temporaryUnlockState: TemporaryUnlockState,
    appNameByPackage: Map<String, String>,
    nowMillis: Long = System.currentTimeMillis(),
): List<TemporaryAllowedAppSummary> {
    return temporaryUnlockState.packageAllowances.mapNotNull { (packageName, allowance) ->
        val remainingMinutes = allowance.temporaryRemainingMinutes(nowMillis)
        if (!allowance.unlockedForToday && remainingMinutes <= 0) {
            return@mapNotNull null
        }
        TemporaryAllowedAppSummary(
            appName = appNameByPackage[packageName] ?: packageName,
            packageName = packageName,
            unlockedForToday = allowance.unlockedForToday,
            temporaryRemainingMinutes = remainingMinutes,
        )
    }.sortedWith(
        compareByDescending<TemporaryAllowedAppSummary> { summary -> summary.unlockedForToday }
            .thenBy { summary -> summary.appName.lowercase() },
    )
}

data class AppGroupSummary(
    val groupName: String,
    val packageNames: Set<String>,
    val usedMinutes: Int,
    val limitMinutes: Int,
    val extraMinutes: Int = 0,
    val status: LimitStatus,
    val appUsages: List<GroupAppUsageSummary> = emptyList(),
)

data class GroupAppUsageSummary(
    val appName: String,
    val packageName: String,
    val usedMinutes: Int,
    val limitMinutes: Int? = null,
    val extraMinutes: Int = 0,
    val unlockedForToday: Boolean = false,
    val temporaryRemainingMinutes: Int = 0,
)

data class AppLimitSummary(
    val appName: String,
    val packageName: String,
    val usedMinutes: Int,
    val limitMinutes: Int,
    val extraMinutes: Int = 0,
    val unlockedForToday: Boolean = false,
    val temporaryRemainingMinutes: Int = 0,
    val status: LimitStatus,
)

data class PolicyBudgetValidation(
    val hasOverflow: Boolean = false,
    val overflowingDayIndexes: List<Int> = emptyList(),
    val appLimitTotalMinutes: Int = 0,
    val groupBudgetTotalMinutes: Int = 0,
    val appGroupLimitConflictCount: Int = 0,
)

enum class LimitStatus {
    Normal,
    Warning,
    Exceeded,
}

enum class EmergencyUnlockStatus {
    Idle,
    Unlocked,
    InvalidPin,
}

enum class AutoRecoveryStatus {
    Idle,
    RecoveredToSafeMode,
}

enum class PolicySaveStatus {
    Idle,
    Saved,
    InvalidAdminPin,
    BudgetExceeded,
}

enum class PinChangeStatus {
    Idle,
    Changed,
    TooShort,
    SameAsCurrent,
    InvalidCurrentPin,
    Failed,
}

enum class SafeModePinStatus {
    Idle,
    Accepted,
    TooShort,
    InvalidAdminPin,
}

data class BlockingReadiness(
    val safeModeAllowsBlocking: Boolean = false,
    val usageAccessReady: Boolean = false,
    val overlayPermissionReady: Boolean = false,
    val notificationPermissionReady: Boolean = false,
    val notificationAccessReady: Boolean = false,
    val exactAlarmReady: Boolean = false,
    val policyEnforcementReady: Boolean = false,
    val whitelistReady: Boolean = true,
    val emergencyUnlockReady: Boolean = true,
) {
    val readyForBlocking: Boolean
        get() = safeModeAllowsBlocking &&
            usageAccessReady &&
            overlayPermissionReady &&
            notificationPermissionReady &&
            notificationAccessReady &&
            exactAlarmReady &&
            policyEnforcementReady &&
            whitelistReady &&
            emergencyUnlockReady
}

class SafeModeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(
        application.settingsDataStore,
        ParentRemoteSyncDataSourceFactory.create(application),
    )
    private val usageStatsRepository = UsageStatsRepository(application)
    private val appCatalogRepository = AppCatalogRepository(application)
    private val notificationHelper = UsageNotificationHelper(application)
    private val emergencyUnlockStatus = MutableStateFlow(EmergencyUnlockStatus.Idle)
    private val autoRecoveryStatus = MutableStateFlow(AutoRecoveryStatus.Idle)
    private val usageState = MutableStateFlow(
        UsageState(
            hasUsageAccess = runCatching { usageStatsRepository.hasUsageAccess() }.getOrDefault(false),
            overlayPermissionReady = runCatching { canDrawOverlays() }.getOrDefault(false),
            notificationPermissionReady = runCatching { notificationHelper.canPostNotifications() }.getOrDefault(false),
            notificationAccessReady = runCatching { hasNotificationListenerAccess() }.getOrDefault(false),
            exactAlarmReady = runCatching { canScheduleExactAlarms() }.getOrDefault(false),
        ),
    )
    private val policySaveStatus = MutableStateFlow(PolicySaveStatus.Idle)
    private val policyDraftSettings = MutableStateFlow<UsagePolicySettings?>(null)
    private val policyDraftAllowedAppPackages = MutableStateFlow<Set<String>?>(null)
    private val pinChangeStatus = MutableStateFlow(PinChangeStatus.Idle)
    private val safeModePinStatus = MutableStateFlow(SafeModePinStatus.Idle)
    private var refreshUsageJob: Job? = null
    private var alertEvaluationJob: Job? = null
    private var refreshInstalledAppsJob: Job? = null
    private var parentRemoteListenerJob: Job? = null
    private var lastUsageRefreshAtMillis: Long = 0L
    private var lastInstalledAppsRefreshAtMillis: Long = 0L
    private var lastParentForegroundSyncAtMillis: Long = 0L
    private var usageRefreshGeneration: Long = 0L
    private var usageAccessDeniedCount: Int = 0

    private val notificationSettingsState = combine(
        repository.warningNotificationsEnabled,
        repository.limitNotificationsEnabled,
    ) { warningNotificationsEnabled, limitNotificationsEnabled ->
        NotificationSettingsState(
            warningNotificationsEnabled = warningNotificationsEnabled,
            limitNotificationsEnabled = limitNotificationsEnabled,
        )
    }

    private val policyAndLogState = combine(
        repository.usagePolicySettings,
        repository.eventLog,
        repository.temporaryUnlockState,
        repository.allowedAppPackages,
        repository.parentManagementState,
    ) { usagePolicySettings, eventLog, temporaryUnlockState, allowedAppPackages, parentManagementState ->
        PolicyAndLogState(
            usagePolicySettings = usagePolicySettings,
            eventLog = eventLog,
            temporaryUnlockState = temporaryUnlockState,
            allowedAppPackages = allowedAppPackages,
            parentManagementState = parentManagementState,
        )
    }

    private val persistedBaseState = combine(
        combine(
            repository.safeModeEnabled,
            repository.appLanguage,
            repository.policyEnforcementEnabled,
            notificationSettingsState,
            policyAndLogState,
        ) { safeModeEnabled, appLanguage, policyEnforcementEnabled, notificationSettings, policyAndLog ->
            PersistedState(
                safeModeEnabled = safeModeEnabled,
                appLanguage = appLanguage,
                policyEnforcementEnabled = policyEnforcementEnabled,
                warningNotificationsEnabled = notificationSettings.warningNotificationsEnabled,
                limitNotificationsEnabled = notificationSettings.limitNotificationsEnabled,
                usagePolicySettings = policyAndLog.usagePolicySettings,
                temporaryUnlockState = policyAndLog.temporaryUnlockState,
                allowedAppPackages = policyAndLog.allowedAppPackages,
                parentManagementState = policyAndLog.parentManagementState,
                eventLog = policyAndLog.eventLog,
            )
        },
        repository.policySectionExpansionSettings,
    ) { persistedState, expansionSettings ->
        persistedState.copy(policySectionExpansionSettings = expansionSettings)
    }

    private val persistedState = combine(
        persistedBaseState,
        repository.foregroundDetectionStatus,
        repository.usageMonitorStatus,
        repository.systemHealthStatus,
        repository.permissionSetupCompletedOnce,
    ) { persistedState, detectionStatus, usageMonitorStatus, systemHealthStatus, permissionSetupCompletedOnce ->
        persistedState.copy(
            foregroundDetectionStatus = detectionStatus?.takeUnless { status ->
                AppVisibility.isHiddenPackage(status.packageName) ||
                    status.packageName in SafetyGate.neverBlockPackages ||
                    status.packageName in persistedState.allowedAppPackages
            },
            usageMonitorStatus = usageMonitorStatus,
            systemHealthStatus = systemHealthStatus,
            permissionSetupCompletedOnce = permissionSetupCompletedOnce,
        )
    }

    private val transientCoreState = combine(
        emergencyUnlockStatus,
        autoRecoveryStatus,
        usageState,
        policySaveStatus,
        pinChangeStatus,
    ) { unlockStatus, recoveryStatus, usageState, saveStatus, pinStatus ->
        TransientState(
            emergencyUnlockStatus = unlockStatus,
            autoRecoveryStatus = recoveryStatus,
            usageState = usageState,
            policySaveStatus = saveStatus,
            pinChangeStatus = pinStatus,
        )
    }

    private val transientBaseState = combine(
        transientCoreState,
        safeModePinStatus,
    ) { transientState, safeModeStatus ->
        transientState.copy(safeModePinStatus = safeModeStatus)
    }

    private val transientState = combine(
        transientBaseState,
        policyDraftSettings,
        policyDraftAllowedAppPackages,
    ) { transientState, draftSettings, draftAllowedPackages ->
        transientState.copy(
            policyDraftSettings = draftSettings,
            policyDraftAllowedAppPackages = draftAllowedPackages,
        )
    }

    val uiState: StateFlow<SafeModeUiState> = combine(
        persistedState,
        transientState,
    ) { persistedState, transientState ->
        val savedSettings = persistedState.usagePolicySettings.normalizedForDraft()
        val draftSettings = (transientState.policyDraftSettings ?: persistedState.usagePolicySettings)
            .normalizedForDraft()
        val savedAllowedPackages = persistedState.allowedAppPackages
        val draftAllowedPackages = transientState.policyDraftAllowedAppPackages ?: savedAllowedPackages
        val todayUsage = transientState.usageState.todayUsage
            .withInstalledAppNames(transientState.usageState.installedApps)
            .withMonitorStatusUsage(
                monitorStatus = persistedState.usageMonitorStatus,
                installedApps = transientState.usageState.installedApps,
            )
        val topAppsSevenDays = transientState.usageState.topAppsSevenDays
            .withInstalledAppNames(transientState.usageState.installedApps)
        val topAppsThirtyDays = transientState.usageState.topAppsThirtyDays
            .withInstalledAppNames(transientState.usageState.installedApps)
        val budgetValidation = draftSettings.policyBudgetValidation()
        val policySummary = buildPolicySummary(
            settings = draftSettings,
            temporaryUnlockState = persistedState.temporaryUnlockState,
            allowedAppPackages = draftAllowedPackages,
            todayUsage = todayUsage,
            installedApps = transientState.usageState.installedApps,
        )
        SafeModeUiState(
            safeModeEnabled = persistedState.safeModeEnabled,
            appLanguage = persistedState.appLanguage,
            policyEnforcementEnabled = persistedState.policyEnforcementEnabled,
            warningNotificationsEnabled = persistedState.warningNotificationsEnabled,
            limitNotificationsEnabled = persistedState.limitNotificationsEnabled,
            emergencyUnlockStatus = transientState.emergencyUnlockStatus,
            autoRecoveryStatus = transientState.autoRecoveryStatus,
            hasUsageAccess = transientState.usageState.hasUsageAccess,
            usageAccessChecking = transientState.usageState.usageAccessChecking,
            statisticsRefreshing = transientState.usageState.statisticsRefreshing,
            usageLastUpdatedAtMillis = transientState.usageState.usageLastUpdatedAtMillis,
            statisticsLastUpdatedAtMillis = transientState.usageState.statisticsLastUpdatedAtMillis,
            todayUsage = todayUsage.take(50),
            usageStatistics = UsageStatistics(
                dailyUsage = transientState.usageState.dailyUsage,
                topApps = TopAppsUsageSet(
                    oneDay = todayUsage.take(STATISTICS_TOP_APP_LIMIT),
                    sevenDays = topAppsSevenDays.take(STATISTICS_TOP_APP_LIMIT),
                    thirtyDays = topAppsThirtyDays.take(STATISTICS_TOP_APP_LIMIT),
                ),
            ),
            installedApps = transientState.usageState.installedApps,
            allowedAppPackages = draftAllowedPackages,
            parentManagementState = persistedState.parentManagementState,
            usagePolicySettings = persistedState.usagePolicySettings,
            temporaryUnlockState = persistedState.temporaryUnlockState,
            policyDraftSettings = draftSettings,
            policyDraftAllowedAppPackages = draftAllowedPackages,
            policyDraftHasChanges = draftSettings != savedSettings || draftAllowedPackages != savedAllowedPackages,
            policyBudgetValidation = budgetValidation,
            policySummary = policySummary,
            policySaveStatus = transientState.policySaveStatus,
            pinChangeStatus = transientState.pinChangeStatus,
            safeModePinStatus = transientState.safeModePinStatus,
            eventLog = persistedState.eventLog,
            foregroundDetectionStatus = persistedState.foregroundDetectionStatus,
            usageMonitorStatus = persistedState.usageMonitorStatus,
            systemHealthStatus = persistedState.systemHealthStatus,
            permissionSetupCompletedOnce = persistedState.permissionSetupCompletedOnce,
            blockingReadiness = buildBlockingReadiness(
                safeModeEnabled = persistedState.safeModeEnabled,
                policyEnforcementEnabled = persistedState.policyEnforcementEnabled,
                hasUsageAccess = transientState.usageState.hasUsageAccess,
                overlayPermissionReady = transientState.usageState.overlayPermissionReady,
                notificationPermissionReady = transientState.usageState.notificationPermissionReady,
                notificationAccessReady = transientState.usageState.notificationAccessReady,
                exactAlarmReady = transientState.usageState.exactAlarmReady,
                allowedAppPackages = draftAllowedPackages,
            ),
            blockDecisionResults = buildBlockDecisionResults(
                safeModeEnabled = persistedState.safeModeEnabled,
                policyEnforcementEnabled = persistedState.policyEnforcementEnabled,
                settings = persistedState.usagePolicySettings,
                temporaryUnlockState = persistedState.temporaryUnlockState,
                allowedAppPackages = draftAllowedPackages,
                todayUsage = todayUsage,
                installedApps = transientState.usageState.installedApps,
                summary = policySummary,
            ),
            dailyPolicyExpanded = persistedState.policySectionExpansionSettings.dailyPolicyExpanded,
            appGroupsExpanded = persistedState.policySectionExpansionSettings.appGroupsExpanded,
            appLimitsExpanded = persistedState.policySectionExpansionSettings.appLimitsExpanded,
            scheduleBlockingExpanded = persistedState.policySectionExpansionSettings.scheduleBlockingExpanded,
            allowOnlyModeExpanded = persistedState.policySectionExpansionSettings.allowOnlyModeExpanded,
            settingsLanguageExpanded = persistedState.policySectionExpansionSettings.settingsLanguageExpanded,
            settingsNotificationExpanded = persistedState.policySectionExpansionSettings.settingsNotificationExpanded,
            settingsPinExpanded = persistedState.policySectionExpansionSettings.settingsPinExpanded,
            settingsParentManagementExpanded = persistedState.policySectionExpansionSettings.settingsParentManagementExpanded,
            settingsEventLogExpanded = persistedState.policySectionExpansionSettings.settingsEventLogExpanded,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SafeModeUiState(),
        )

    init {
        viewModelScope.launch {
            hydrateCachedTodayUsage()
            val recovered = repository.markAppStartedAndRecoverIfNeeded()
            if (recovered) {
                autoRecoveryStatus.value = AutoRecoveryStatus.RecoveredToSafeMode
            }
            refreshForForeground(force = true)
        }
        viewModelScope.launch {
            runDailyRolloverLoop()
        }
        observeParentRemoteChanges()
        viewModelScope.launch {
            combine(
                usageState,
                repository.permissionSetupCompletedOnce,
            ) { usageState, completedOnce ->
                usageState to completedOnce
            }.collect { (usageState, completedOnce) ->
                if (
                    !completedOnce &&
                    usageState.hasUsageAccess &&
                    usageState.overlayPermissionReady &&
                    usageState.notificationPermissionReady &&
                    usageState.notificationAccessReady &&
                    usageState.exactAlarmReady
                ) {
                    repository.setPermissionSetupCompletedOnce(true)
                }
            }
        }
    }

    private fun observeParentRemoteChanges() {
        viewModelScope.launch {
            repository.parentManagementState
                .map(::parentListenerChildIds)
                .distinctUntilChanged()
                .collect(::restartParentRemoteListener)
        }
    }

    private fun restartParentRemoteListener(childDeviceIds: Set<String>) {
        parentRemoteListenerJob?.cancel()
        parentRemoteListenerJob = null
        if (childDeviceIds.isEmpty()) {
            return
        }
        parentRemoteListenerJob = viewModelScope.launch(Dispatchers.IO) {
            repository.observeParentRemoteChanges(childDeviceIds)
                .filter { change -> !change.fromCache && !change.hasPendingWrites }
                .conflate()
                .retryWhen { error, attempt ->
                    Log.w("STM-ParentSync", "Firestore listener disconnected; retry=$attempt", error)
                    if (attempt == 0L) {
                        runCatching {
                            repository.addEvent(
                                EventLogType.Warning,
                                "Parent listener disconnected; automatic retry scheduled",
                            )
                        }
                    }
                    delay(parentListenerRetryDelayMillis(attempt))
                    true
                }
                .collect {
                    synchronizeParentStateFromListener()
                }
        }
    }

    private suspend fun synchronizeParentStateFromListener() {
        val before = repository.parentManagementState.first()
        var after = before
        for (attempt in 0 until PARENT_LISTENER_SYNC_RETRY_COUNT) {
            repository.syncParentDevice()
            after = repository.parentManagementState.first()
            if (
                after.lastSyncMillis > before.lastSyncMillis ||
                after.remoteUnlockRequests != before.remoteUnlockRequests ||
                after.remoteCommands != before.remoteCommands
            ) {
                break
            }
            if (attempt < PARENT_LISTENER_SYNC_RETRY_COUNT - 1) {
                delay(PARENT_LISTENER_SYNC_RETRY_DELAY_MILLIS)
            }
        }

        val notifications = detectParentRemoteNotifications(
            before = before,
            after = after,
            nowMillis = System.currentTimeMillis(),
        )
        if (notifications.isEmpty()) {
            return
        }
        val korean = repository.appLanguage.first() == AppLanguage.Korean
        notifications.forEach { notification ->
            val request = notification.request
            val target = request.targetAppName
                .ifBlank { request.targetGroupName }
                .ifBlank { request.targetPackageName }
                .ifBlank { "ScreenRest" }
            val message = when (notification.type) {
                ParentRemoteNotificationType.NewPendingRequest -> if (korean) {
                    "자녀 기기에서 사용 시간 요청을 보냈습니다: $target"
                } else {
                    "A child device requested more time: $target"
                }
                ParentRemoteNotificationType.RequestApproved -> if (korean) {
                    "부모가 사용 시간 요청을 승인했습니다: $target"
                } else {
                    "The parent approved the time request: $target"
                }
                ParentRemoteNotificationType.RequestRejected -> if (korean) {
                    "부모가 사용 시간 요청을 거절했습니다: $target"
                } else {
                    "The parent rejected the time request: $target"
                }
            }
            notificationHelper.showPolicyAlert(title = "ScreenRest", message = message)
        }
    }

    fun refreshForForeground(force: Boolean = false) {
        resetUsageIfDateChanged()
        refreshSystemPermissionStates()
        val now = SystemClock.elapsedRealtime()
        if (
            usageState.value.installedApps.isEmpty() ||
            now - lastInstalledAppsRefreshAtMillis >= INSTALLED_APPS_REFRESH_THROTTLE_MILLIS
        ) {
            lastInstalledAppsRefreshAtMillis = now
            refreshInstalledApps()
        }
        if (force || now - lastUsageRefreshAtMillis >= USAGE_REFRESH_THROTTLE_MILLIS) {
            lastUsageRefreshAtMillis = now
            refreshUsageStatsInternal(force = force, includeStatistics = false)
        }
        if (force || now - lastParentForegroundSyncAtMillis >= PARENT_SYNC_FOREGROUND_THROTTLE_MILLIS) {
            lastParentForegroundSyncAtMillis = now
            viewModelScope.launch {
                val parentState = repository.parentManagementState.first()
                val hasSyncTarget = parentState.childDeviceId.isNotBlank() ||
                    parentState.linkedChildDevices.any { child -> child.childDeviceId.isNotBlank() }
                if (parentState.paired && hasSyncTarget) {
                    repository.syncParentDevice()
                }
            }
        }
    }

    private fun refreshSystemPermissionStates() {
        usageState.value = usageState.value.copy(
            dateKey = currentUsageDateKey(),
            hasUsageAccess = runCatching {
                usageStatsRepository.hasUsageAccess()
            }.getOrDefault(usageState.value.hasUsageAccess),
            overlayPermissionReady = runCatching {
                canDrawOverlays()
            }.getOrDefault(false),
            notificationPermissionReady = runCatching {
                notificationHelper.canPostNotifications()
            }.getOrDefault(false),
            notificationAccessReady = runCatching {
                hasNotificationListenerAccess()
            }.getOrDefault(false),
            exactAlarmReady = runCatching {
                canScheduleExactAlarms()
            }.getOrDefault(false),
        )
    }

    private fun resetUsageIfDateChanged() {
        val todayKey = currentUsageDateKey()
        val currentState = usageState.value
        if (currentState.dateKey != todayKey) {
            usageAccessDeniedCount = 0
            lastUsageRefreshAtMillis = 0L
            usageState.value = currentState.copy(
                dateKey = todayKey,
                usageAccessChecking = currentState.hasUsageAccess,
                statisticsRefreshing = false,
                usageLastUpdatedAtMillis = 0L,
                statisticsLastUpdatedAtMillis = 0L,
                todayUsage = emptyList(),
                dailyUsage = emptyList(),
                topAppsSevenDays = emptyList(),
                topAppsThirtyDays = emptyList(),
            )
        }
    }

    private suspend fun runDailyRolloverLoop() {
        var lastDateKey = currentUsageDateKey()
        while (true) {
            delay(millisUntilNextLocalDay().coerceAtLeast(1_000L))
            val todayKey = currentUsageDateKey()
            if (todayKey != lastDateKey) {
                lastDateKey = todayKey
                runCatching { repository.recordDailyRollover(todayKey) }
                resetUsageIfDateChanged()
                refreshForForeground(force = true)
            }
        }
    }

    private suspend fun hydrateCachedTodayUsage() {
        val todayKey = currentUsageDateKey()
        val cachedSnapshot = repository.cachedTodayUsage.first()
        if (cachedSnapshot?.dateKey != todayKey || cachedSnapshot.apps.isEmpty()) {
            return
        }
        val cachedUsage = cachedSnapshot.apps
            .map { cachedApp ->
                AppUsageInfo(
                    appName = cachedApp.appName,
                    packageName = cachedApp.packageName,
                    totalTimeMillis = cachedApp.totalTimeMillis,
                )
            }
            .filter { appUsage -> appUsage.packageName.isNotBlank() && appUsage.totalTimeMillis > 0L }
        if (cachedUsage.isEmpty()) {
            return
        }
        usageState.value = usageState.value.copy(
            dateKey = todayKey,
            usageAccessChecking = false,
            todayUsage = cachedUsage,
        )
    }

    fun submitEmergencyPin(pin: String) {
        viewModelScope.launch {
            emergencyUnlockStatus.value = if (repository.emergencyUnlock(pin)) {
                EmergencyUnlockStatus.Unlocked
            } else {
                EmergencyUnlockStatus.InvalidPin
            }
        }
    }

    fun clearEmergencyUnlockStatus() {
        emergencyUnlockStatus.value = EmergencyUnlockStatus.Idle
    }

    fun clearPolicySaveStatus() {
        policySaveStatus.value = PolicySaveStatus.Idle
    }

    fun clearPinChangeStatus() {
        pinChangeStatus.value = PinChangeStatus.Idle
    }

    fun markAppStoppedCleanly() {
        viewModelScope.launch {
            repository.markAppStoppedCleanly()
        }
    }

    fun refreshUsageStats(force: Boolean = false) {
        refreshUsageStatsInternal(force = force, includeStatistics = false)
    }

    fun refreshStatistics(force: Boolean = true) {
        refreshUsageStatsInternal(force = force, includeStatistics = true)
    }

    private fun refreshUsageStatsInternal(force: Boolean = false, includeStatistics: Boolean = false) {
        if (refreshUsageJob?.isActive == true && !force) {
            return
        }
        if (force) {
            refreshUsageJob?.cancel()
        }
        lastUsageRefreshAtMillis = SystemClock.elapsedRealtime()
        val generation = ++usageRefreshGeneration
        refreshUsageJob = viewModelScope.launch(Dispatchers.IO) {
            fun isCurrentRefresh(): Boolean = generation == usageRefreshGeneration

            try {
                if (isCurrentRefresh()) {
                    val currentState = usageState.value
                    usageState.value = currentState.copy(
                        dateKey = currentUsageDateKey(),
                        usageAccessChecking = currentState.hasUsageAccess || currentState.todayUsage.isNotEmpty(),
                        statisticsRefreshing = includeStatistics,
                    )
                }

                val hasUsageAccess = try {
                    withTimeoutOrNull(USAGE_ACCESS_CHECK_TIMEOUT_MILLIS) {
                        usageStatsRepository.hasUsageAccess()
                    }
                } catch (exception: Exception) {
                    if (exception is CancellationException) {
                        throw exception
                    }
                    null
                }

                when (hasUsageAccess) {
                    true -> {
                        usageAccessDeniedCount = 0
                        if (!isCurrentRefresh()) return@launch
                        usageState.value = usageState.value.copy(hasUsageAccess = true)
                    }

                    false -> {
                        usageAccessDeniedCount += 1
                        if (!isCurrentRefresh()) return@launch
                        val previousState = usageState.value
                        if (usageAccessDeniedCount >= USAGE_ACCESS_DENIED_CLEAR_THRESHOLD) {
                            usageState.value = previousState.copy(
                                hasUsageAccess = false,
                                usageAccessChecking = false,
                                todayUsage = if (previousState.dateKey == currentUsageDateKey()) {
                                    previousState.todayUsage
                                } else {
                                    emptyList()
                                },
                            )
                        } else {
                            usageState.value = previousState.copy(usageAccessChecking = false)
                            scheduleUsageAccessRetry(generation)
                        }
                        return@launch
                    }

                    null -> {
                        if (isCurrentRefresh()) {
                            usageState.value = usageState.value.copy(usageAccessChecking = false)
                            scheduleUsageAccessRetry(generation)
                        }
                        return@launch
                    }
                }

                val todayUsage = queryUsageWithRetry {
                    usageStatsRepository.getTodayUsage(maxItems = 500, skipAccessCheck = true)
                }
                if (todayUsage != null) {
                    if (!isCurrentRefresh()) return@launch
                    val previousState = usageState.value
                    val todayKey = currentUsageDateKey()
                    val installedApps = previousState.installedApps
                        .ifEmpty { appCatalogRepository.getLaunchableApps() }
                    val stableTodayUsage = if (
                        todayUsage.isEmpty() &&
                        previousState.dateKey == todayKey &&
                        previousState.todayUsage.isNotEmpty()
                    ) {
                        previousState.todayUsage
                    } else {
                        todayUsage
                    }
                    usageState.value = usageState.value.copy(
                        dateKey = todayKey,
                        hasUsageAccess = true,
                        usageAccessChecking = false,
                        usageLastUpdatedAtMillis = System.currentTimeMillis(),
                        todayUsage = stableTodayUsage.withInstalledAppNames(installedApps),
                        installedApps = installedApps,
                    )
                    repository.saveTodayUsageCache(
                        dateKey = todayKey,
                        apps = stableTodayUsage
                            .withInstalledAppNames(installedApps)
                            .map { appUsage ->
                                CachedTodayUsageEntry(
                                    appName = appUsage.appName,
                                    packageName = appUsage.packageName,
                                    totalTimeMillis = appUsage.totalTimeMillis,
                                )
                            },
                    )
                    evaluatePolicyAlertsAsync()
                } else if (isCurrentRefresh()) {
                    usageState.value = usageState.value.copy(usageAccessChecking = false)
                }

                if (includeStatistics && todayUsage != null && isCurrentRefresh()) {
                    val dailyUsage = queryUsageWithRetry {
                        usageStatsRepository.getDailyUsage(
                            days = STATISTICS_DAYS,
                            skipAccessCheck = true,
                        )
                    }
                    val topAppsSevenDays = queryUsageWithRetry {
                        usageStatsRepository.getTopAppsUsage(
                            days = 7,
                            maxItems = STATISTICS_TOP_APP_LIMIT,
                            skipAccessCheck = true,
                        )
                    }
                    val topAppsThirtyDays = queryUsageWithRetry {
                        usageStatsRepository.getTopAppsUsage(
                            days = STATISTICS_DAYS,
                            maxItems = STATISTICS_TOP_APP_LIMIT,
                            skipAccessCheck = true,
                        )
                    }
                    if (!isCurrentRefresh()) return@launch
                    val previousState = usageState.value
                    val installedApps = previousState.installedApps
                    val statisticsQueryCompleted = dailyUsage != null ||
                        topAppsSevenDays != null ||
                        topAppsThirtyDays != null
                    val statisticsQueryHasData = dailyUsage?.isNotEmpty() == true ||
                        topAppsSevenDays?.isNotEmpty() == true ||
                        topAppsThirtyDays?.isNotEmpty() == true
                    val previousStatisticsHasData = previousState.dailyUsage.isNotEmpty() ||
                        previousState.topAppsSevenDays.isNotEmpty() ||
                        previousState.topAppsThirtyDays.isNotEmpty()
                    val statisticsUpdated = statisticsQueryCompleted &&
                        (statisticsQueryHasData || !previousStatisticsHasData)
                    usageState.value = previousState.copy(
                        statisticsRefreshing = false,
                        statisticsLastUpdatedAtMillis = if (statisticsUpdated) {
                            System.currentTimeMillis()
                        } else {
                            previousState.statisticsLastUpdatedAtMillis
                        },
                        dailyUsage = dailyUsage
                            ?.takeUnless { usage -> usage.isEmpty() && previousState.dailyUsage.isNotEmpty() }
                            ?: previousState.dailyUsage,
                        topAppsSevenDays = topAppsSevenDays
                            ?.takeUnless { usage -> usage.isEmpty() && previousState.topAppsSevenDays.isNotEmpty() }
                            ?.withInstalledAppNames(installedApps)
                            ?: previousState.topAppsSevenDays,
                        topAppsThirtyDays = topAppsThirtyDays
                            ?.takeUnless { usage -> usage.isEmpty() && previousState.topAppsThirtyDays.isNotEmpty() }
                            ?.withInstalledAppNames(installedApps)
                            ?: previousState.topAppsThirtyDays,
                    )
                }
            } finally {
                if (
                    isCurrentRefresh() &&
                    (usageState.value.usageAccessChecking || usageState.value.statisticsRefreshing)
                ) {
                    usageState.value = usageState.value.copy(
                        usageAccessChecking = false,
                        statisticsRefreshing = false,
                    )
                }
            }
        }
    }

    private suspend fun <T> queryUsageWithRetry(query: suspend () -> T): T? {
        repeat(USAGE_QUERY_RETRY_COUNT) { attempt ->
            val result = try {
                withTimeoutOrNull(USAGE_QUERY_TIMEOUT_MILLIS) {
                    query()
                }
            } catch (exception: Exception) {
                if (exception is CancellationException) {
                    throw exception
                }
                null
            }
            if (result != null) {
                return result
            }
            if (attempt < USAGE_QUERY_RETRY_COUNT - 1) {
                delay(USAGE_QUERY_RETRY_DELAY_MILLIS * (attempt + 1L))
            }
        }
        return null
    }

    private fun scheduleUsageAccessRetry(generation: Long) {
        viewModelScope.launch {
            delay(USAGE_ACCESS_RETRY_DELAY_MILLIS)
            if (generation == usageRefreshGeneration) {
                refreshUsageStatsInternal(force = true, includeStatistics = false)
            }
        }
    }

    private fun evaluatePolicyAlertsAsync() {
        alertEvaluationJob?.cancel()
        alertEvaluationJob = viewModelScope.launch(Dispatchers.IO) {
            UsagePolicyAlertRunner.evaluate(getApplication<Application>(), sendNotifications = true)
        }
    }

    fun refreshInstalledApps() {
        lastInstalledAppsRefreshAtMillis = SystemClock.elapsedRealtime()
        refreshInstalledAppsJob?.cancel()
        refreshInstalledAppsJob = viewModelScope.launch(Dispatchers.Default) {
            val installedApps = appCatalogRepository.getLaunchableApps()
            usageState.value = usageState.value.copy(
                installedApps = installedApps,
                todayUsage = usageState.value.todayUsage.withInstalledAppNames(installedApps),
            )
        }
    }

    fun updatePolicyDraft(settings: UsagePolicySettings) {
        policyDraftSettings.value = settings.normalizedForDraft()
        policySaveStatus.value = PolicySaveStatus.Idle
    }

    fun resetPolicyDraft() {
        policyDraftSettings.value = null
        policyDraftAllowedAppPackages.value = null
        policySaveStatus.value = PolicySaveStatus.Idle
    }

    fun savePolicyDraft(adminPin: String) {
        viewModelScope.launch {
            val draftSettings = (policyDraftSettings.value ?: uiState.value.usagePolicySettings)
                .normalizedForDraft()
            val draftAllowedPackages = policyDraftAllowedAppPackages.value ?: uiState.value.allowedAppPackages
            if (draftSettings.policyBudgetValidation().hasOverflow) {
                policySaveStatus.value = PolicySaveStatus.BudgetExceeded
                return@launch
            }

            val saved = repository.saveUsagePolicySettings(draftSettings, adminPin)
            policySaveStatus.value = if (saved) {
                repository.setAllowedAppPackages(draftAllowedPackages)
                policyDraftSettings.value = draftSettings
                policyDraftAllowedAppPackages.value = draftAllowedPackages
                viewModelScope.launch {
                    repository.usagePolicySettings.first { savedSettings ->
                        savedSettings.normalizedForDraft() == draftSettings
                    }
                    repository.allowedAppPackages.first { savedAllowedPackages ->
                        savedAllowedPackages == draftAllowedPackages
                    }
                    if (policyDraftSettings.value == draftSettings) {
                        policyDraftSettings.value = null
                    }
                    if (policyDraftAllowedAppPackages.value == draftAllowedPackages) {
                        policyDraftAllowedAppPackages.value = null
                    }
                }
                evaluatePolicyAlertsAsync()
                PolicySaveStatus.Saved
            } else {
                PolicySaveStatus.InvalidAdminPin
            }
        }
    }

    fun updateAdminPin(currentPin: String, newPin: String) {
        viewModelScope.launch {
            pinChangeStatus.value = when {
                currentPin.length < 4 || newPin.length < 4 -> PinChangeStatus.TooShort
                currentPin == newPin -> PinChangeStatus.SameAsCurrent
                repository.updateAdminPin(currentPin, newPin) -> PinChangeStatus.Changed
                else -> PinChangeStatus.InvalidCurrentPin
            }
        }
    }

    fun updateEmergencyPin(currentPin: String, newPin: String) {
        viewModelScope.launch {
            pinChangeStatus.value = when {
                currentPin.length < 4 || newPin.length < 4 -> PinChangeStatus.TooShort
                currentPin == newPin -> PinChangeStatus.SameAsCurrent
                repository.updateEmergencyPin(currentPin, newPin) -> PinChangeStatus.Changed
                else -> PinChangeStatus.InvalidCurrentPin
            }
        }
    }

    fun clearEventLog() {
        viewModelScope.launch {
            repository.clearEventLog()
        }
    }

    fun pairParentAccount(parentAccountId: String, childDeviceName: String, adminPin: String) {
        viewModelScope.launch {
            repository.pairParentAccount(parentAccountId, childDeviceName, adminPin)
        }
    }

    fun setParentDeviceRole(role: ParentDeviceRole, adminPin: String) {
        viewModelScope.launch {
            repository.setParentDeviceRole(role, adminPin)
        }
    }

    fun setParentProfileName(profileName: String) {
        viewModelScope.launch {
            if (repository.setParentProfileName(profileName)) {
                repository.syncParentDevice()
            }
        }
    }

    fun generateChildPairingCode(adminPin: String) {
        viewModelScope.launch {
            val success = repository.generateChildPairingCode(adminPin)
            showParentOperationResult(
                success = success,
                successKorean = "연결 코드가 클라우드에 등록되었습니다",
                failureKorean = "연결 코드를 등록하지 못했습니다. PIN과 네트워크를 확인해 주세요",
                successEnglish = "Pairing code registered in the cloud",
                failureEnglish = "Could not register the pairing code. Check the PIN and network",
            )
        }
    }

    fun registerChildPairingCode(pairingCode: String, childDeviceName: String, adminPin: String) {
        viewModelScope.launch {
            val success = repository.registerChildPairingCode(
                pairingCode = pairingCode,
                childDeviceName = childDeviceName,
                adminPin = adminPin,
            )
            if (success) {
                repository.syncParentDevice()
            }
            showParentOperationResult(
                success = success,
                successKorean = "자녀 기기가 연결되었습니다",
                failureKorean = "자녀 기기를 연결하지 못했습니다. 코드, PIN, 네트워크를 확인해 주세요",
                successEnglish = "Child device connected",
                failureEnglish = "Could not connect the child device. Check the code, PIN, and network",
            )
        }
    }

    fun unlinkParentAccount(adminPin: String) {
        viewModelScope.launch {
            repository.unlinkParentAccount(adminPin)
        }
    }

    fun unlinkLinkedChildDevice(childDeviceId: String, adminPin: String) {
        viewModelScope.launch {
            repository.unlinkLinkedChildDevice(childDeviceId, adminPin)
        }
    }

    fun unlinkLinkedParentDevice(parentUid: String, adminPin: String) {
        viewModelScope.launch {
            repository.unlinkLinkedParentDevice(parentUid, adminPin)
        }
    }

    fun syncParentDevice() {
        viewModelScope.launch {
            repository.syncParentDevice()
        }
    }

    fun clearRemoteParentCommands(adminPin: String) {
        viewModelScope.launch {
            repository.clearRemoteParentCommands(adminPin)
        }
    }

    fun applyRemoteAppExtraTime(packageName: String, appName: String, minutes: Int) {
        viewModelScope.launch {
            repository.applyRemoteAppExtraTime(packageName, appName, minutes)
            evaluatePolicyAlertsAsync()
        }
    }

    fun applyRemoteAppUnlockToday(packageName: String, appName: String) {
        viewModelScope.launch {
            repository.applyRemoteAppUnlockToday(packageName, appName)
            evaluatePolicyAlertsAsync()
        }
    }

    fun applyRemoteTotalExtraTime(minutes: Int) {
        viewModelScope.launch {
            repository.applyRemoteTotalExtraTime(minutes)
            evaluatePolicyAlertsAsync()
        }
    }

    fun applyRemoteTotalUnlockToday() {
        viewModelScope.launch {
            repository.applyRemoteTotalUnlockToday()
            evaluatePolicyAlertsAsync()
        }
    }

    fun approveRemoteUnlockRequest(requestId: String, minutes: Int, unlockForToday: Boolean) {
        viewModelScope.launch {
            val success = repository.approveRemoteUnlockRequest(
                requestId = requestId,
                extraMinutes = minutes,
                unlockForToday = unlockForToday,
            )
            showParentOperationResult(
                success = success,
                successKorean = "승인이 자녀 기기에 전달되었습니다",
                failureKorean = "승인을 전달하지 못했습니다. 네트워크 연결을 확인해 주세요",
                successEnglish = "Approval sent to the child device",
                failureEnglish = "Could not send the approval. Check the network connection",
            )
            evaluatePolicyAlertsAsync()
        }
    }

    fun rejectRemoteUnlockRequest(requestId: String) {
        viewModelScope.launch {
            val success = repository.rejectRemoteUnlockRequest(requestId)
            showParentOperationResult(
                success = success,
                successKorean = "거절 응답이 자녀 기기에 전달되었습니다",
                failureKorean = "거절 응답을 전달하지 못했습니다. 네트워크 연결을 확인해 주세요",
                successEnglish = "Rejection sent to the child device",
                failureEnglish = "Could not send the rejection. Check the network connection",
            )
            evaluatePolicyAlertsAsync()
        }
    }

    private suspend fun showParentOperationResult(
        success: Boolean,
        successKorean: String,
        failureKorean: String,
        successEnglish: String,
        failureEnglish: String,
    ) {
        val korean = repository.appLanguage.first() == AppLanguage.Korean
        val message = when {
            success && korean -> successKorean
            success -> successEnglish
            korean -> failureKorean
            else -> failureEnglish
        }
        Toast.makeText(getApplication<Application>(), message, Toast.LENGTH_LONG).show()
    }

    fun setSafeModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            safeModePinStatus.value = SafeModePinStatus.Idle
            debugPolicy("safeMode request enabled=$enabled")
            repository.setSafeModeEnabled(enabled)
            val application = getApplication<Application>()
            if (enabled) {
                debugPolicy("safeMode enabled; stopping monitor")
                UsageMonitorForegroundService.stop(application)
            } else if (repository.policyEnforcementEnabled.first()) {
                debugPolicy("safeMode disabled with policy on; starting monitor")
                if (!UsageMonitorForegroundService.start(application)) {
                    repository.setSafeModeEnabled(true)
                    repository.addEvent(
                        EventLogType.Warning,
                        "Safe mode restored: usage monitor service could not start",
                    )
                    Toast.makeText(
                        application,
                        "감시 서비스를 시작하지 못해 안전 모드로 복구했습니다",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    fun enableSafeModeWithAdminPin(adminPin: String) {
        viewModelScope.launch {
            val cleanPin = adminPin.trim()
            if (cleanPin.length < 4) {
                safeModePinStatus.value = SafeModePinStatus.TooShort
                return@launch
            }
            if (!repository.verifyAdminPin(cleanPin)) {
                repository.addEvent(EventLogType.Warning, "Safe Mode enable failed: invalid admin PIN")
                safeModePinStatus.value = SafeModePinStatus.InvalidAdminPin
                return@launch
            }

            repository.setSafeModeEnabled(true)
            repository.addEvent(EventLogType.Safety, "Safe Mode enabled by admin PIN")
            UsageMonitorForegroundService.stop(getApplication<Application>())
            safeModePinStatus.value = SafeModePinStatus.Accepted
        }
    }

    fun disablePolicyEnforcementWithAdminPin(adminPin: String) {
        viewModelScope.launch {
            val cleanPin = adminPin.trim()
            if (cleanPin.length < 4) {
                safeModePinStatus.value = SafeModePinStatus.TooShort
                return@launch
            }
            if (!repository.verifyAdminPin(cleanPin)) {
                repository.addEvent(EventLogType.Warning, "Policy enforcement disable failed: invalid admin PIN")
                safeModePinStatus.value = SafeModePinStatus.InvalidAdminPin
                return@launch
            }

            repository.setPolicyEnforcementEnabled(false)
            repository.addEvent(EventLogType.Safety, "Policy enforcement disabled by admin PIN")
            UsageMonitorForegroundService.stop(getApplication<Application>())
            safeModePinStatus.value = SafeModePinStatus.Accepted
        }
    }

    fun clearSafeModePinStatus() {
        safeModePinStatus.value = SafeModePinStatus.Idle
    }

    fun setPolicyEnforcementEnabled(enabled: Boolean) {
        viewModelScope.launch {
            safeModePinStatus.value = SafeModePinStatus.Idle
            refreshSystemPermissionStates()
            debugPolicy(
                "policy request enabled=$enabled usage=${usageState.value.hasUsageAccess} overlay=${usageState.value.overlayPermissionReady} notif=${usageState.value.notificationPermissionReady} notifAccess=${usageState.value.notificationAccessReady} alarm=${usageState.value.exactAlarmReady}",
            )
            if (enabled && !usageState.value.hasUsageAccess) {
                repository.addEvent(EventLogType.Safety, "Policy enforcement requires Usage Access permission")
                repository.setPolicyEnforcementEnabled(false)
                return@launch
            }
            if (enabled && !usageState.value.overlayPermissionReady) {
                repository.addEvent(EventLogType.Safety, "Policy enforcement requires display-over-other-apps permission")
                repository.setPolicyEnforcementEnabled(false)
                return@launch
            }
            if (enabled && !usageState.value.notificationPermissionReady) {
                repository.addEvent(EventLogType.Safety, "Policy enforcement requires notification permission")
                repository.setPolicyEnforcementEnabled(false)
                return@launch
            }
            if (enabled && !usageState.value.notificationAccessReady) {
                repository.addEvent(EventLogType.Safety, "Policy enforcement requires notification access permission")
                repository.setPolicyEnforcementEnabled(false)
                return@launch
            }
            if (enabled && !usageState.value.exactAlarmReady) {
                repository.addEvent(EventLogType.Safety, "Policy enforcement requires alarms and reminders permission")
                repository.setPolicyEnforcementEnabled(false)
                return@launch
            }
            repository.setPolicyEnforcementEnabled(enabled)
            val application = getApplication<Application>()
            if (enabled) {
                debugPolicy("policy enabled persisted; starting monitor service now")
                if (!UsageMonitorForegroundService.start(application)) {
                    repository.setPolicyEnforcementEnabled(false)
                    repository.addEvent(
                        EventLogType.Warning,
                        "Policy enforcement disabled: usage monitor service could not start",
                    )
                    Toast.makeText(
                        application,
                        "감시 서비스를 시작하지 못해 정책 적용을 취소했습니다",
                        Toast.LENGTH_LONG,
                    ).show()
                    return@launch
                }
                UsageMonitorForegroundService.scheduleExactRecoveryAlarm(
                    context = application,
                    reason = "policy enabled",
                    delayMillis = 10_000L,
                )
            } else {
                debugPolicy("policy disabled persisted; stopping monitor service now")
                UsageMonitorForegroundService.stop(application)
            }
        }
    }

    fun setWarningNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setWarningNotificationsEnabled(enabled)
        }
    }

    fun setLimitNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setLimitNotificationsEnabled(enabled)
        }
    }

    fun setAppLanguage(language: AppLanguage) {
        viewModelScope.launch {
            repository.setAppLanguage(language)
        }
    }

    fun setAllowedAppPackages(packageNames: Set<String>) {
        policyDraftAllowedAppPackages.value = packageNames
            .map { packageName -> packageName.trim() }
            .filter { packageName -> packageName.isNotBlank() && packageName !in SafetyGate.neverBlockPackages }
            .toSet()
        policySaveStatus.value = PolicySaveStatus.Idle
    }

    fun setDailyPolicyExpanded(expanded: Boolean) {
        viewModelScope.launch {
            repository.setDailyPolicyExpanded(expanded)
        }
    }

    fun setAppGroupsExpanded(expanded: Boolean) {
        viewModelScope.launch {
            repository.setAppGroupsExpanded(expanded)
        }
    }

    fun setAppLimitsExpanded(expanded: Boolean) {
        viewModelScope.launch {
            repository.setAppLimitsExpanded(expanded)
        }
    }

    fun setScheduleBlockingExpanded(expanded: Boolean) {
        viewModelScope.launch {
            repository.setScheduleBlockingExpanded(expanded)
        }
    }

    fun setAllowOnlyModeExpanded(expanded: Boolean) {
        viewModelScope.launch {
            repository.setAllowOnlyModeExpanded(expanded)
        }
    }

    fun setSettingsLanguageExpanded(expanded: Boolean) {
        viewModelScope.launch {
            repository.setSettingsLanguageExpanded(expanded)
        }
    }

    fun setSettingsNotificationExpanded(expanded: Boolean) {
        viewModelScope.launch {
            repository.setSettingsNotificationExpanded(expanded)
        }
    }

    fun setSettingsPinExpanded(expanded: Boolean) {
        viewModelScope.launch {
            repository.setSettingsPinExpanded(expanded)
        }
    }

    fun setSettingsParentManagementExpanded(expanded: Boolean) {
        viewModelScope.launch {
            repository.setSettingsParentManagementExpanded(expanded)
        }
    }

    fun setSettingsEventLogExpanded(expanded: Boolean) {
        viewModelScope.launch {
            repository.setSettingsEventLogExpanded(expanded)
        }
    }

    fun activateKillSwitch() {
        viewModelScope.launch {
            repository.activateKillSwitch()
            policyDraftSettings.value = null
            policySaveStatus.value = PolicySaveStatus.Idle
            emergencyUnlockStatus.value = EmergencyUnlockStatus.Unlocked
        }
    }

    private fun buildPolicySummary(
        settings: UsagePolicySettings,
        temporaryUnlockState: TemporaryUnlockState,
        allowedAppPackages: Set<String>,
        todayUsage: List<AppUsageInfo>,
        installedApps: List<InstalledAppInfo>,
    ): PolicySummary {
        val todayTemporaryUnlockState = temporaryUnlockState.forToday()
        val nowMillis = System.currentTimeMillis()
        val usageByPackage = todayUsage.associateBy { appUsage -> appUsage.packageName }
        val appNameByPackage = installedApps.associate { app -> app.packageName to app.appName }
        val appLimits = settings.appLimitMap()
        val groupSummaries = settings.normalizedAppGroups().map { group ->
            val groupExtraMinutes = group.packageNames
                .sumOf { packageName -> todayTemporaryUnlockState.packageAllowances[packageName]?.extraMinutes ?: 0 }
            val usedMinutes = group.packageNames
                .sumOf { packageName -> usageByPackage[packageName]?.totalTimeMillis ?: 0L }
                .toDisplayMinutes()
            val appUsages = group.packageNames
                .map { packageName ->
                    val usage = usageByPackage[packageName]
                    val allowance = todayTemporaryUnlockState.packageAllowances[packageName]
                    GroupAppUsageSummary(
                        appName = usage?.appName ?: appNameByPackage[packageName] ?: packageName,
                        packageName = packageName,
                        usedMinutes = (usage?.totalTimeMillis ?: 0L).toDisplayMinutes(),
                        limitMinutes = appLimits[packageName],
                        extraMinutes = allowance?.extraMinutes ?: 0,
                        unlockedForToday = allowance?.unlockedForToday == true,
                        temporaryRemainingMinutes = allowance?.temporaryRemainingMinutes(nowMillis) ?: 0,
                    )
                }
                .sortedWith(
                    compareByDescending<GroupAppUsageSummary> { appUsage -> appUsage.usedMinutes }
                        .thenBy { appUsage -> appUsage.appName.lowercase() },
                )
            AppGroupSummary(
                groupName = group.name,
                packageNames = group.packageNames,
                usedMinutes = usedMinutes,
                limitMinutes = group.budgetMinutes,
                extraMinutes = groupExtraMinutes,
                status = calculateLimitStatus(usedMinutes, group.budgetMinutes + groupExtraMinutes),
                appUsages = appUsages,
            )
        }

        val totalUsedMinutes = todayUsage.sumOf { appUsage -> appUsage.totalTimeMillis }.toDisplayMinutes()
        val totalLimitMinutes = settings.todayLimitMinutes()
        val totalExtraMinutes = todayTemporaryUnlockState.totalExtraMinutes
        val primaryGroup = groupSummaries.firstOrNull()
        val groupUsedMinutes = primaryGroup?.usedMinutes ?: 0
        val groupLimitMinutes = primaryGroup?.limitMinutes ?: 0
        val totalStatus = if (todayTemporaryUnlockState.totalUnlockedForToday || totalLimitMinutes <= 0) {
            LimitStatus.Normal
        } else {
            calculateLimitStatus(totalUsedMinutes, totalLimitMinutes + totalExtraMinutes)
        }
        val temporaryAllowedApps = buildTemporaryAllowedAppSummaries(
            temporaryUnlockState = todayTemporaryUnlockState,
            appNameByPackage = appNameByPackage,
            nowMillis = nowMillis,
        )
        val temporarilyAllowedPackages = temporaryAllowedApps
            .map { summary -> summary.packageName }
            .toSet()
        val scheduleSummaries = buildScheduleSummaries(
            settings = settings,
            allowedAppPackages = allowedAppPackages,
            temporarilyAllowedPackages = temporarilyAllowedPackages,
        )
        val activeScheduleSummary = scheduleSummaries.firstOrNull { summary -> summary.activeNow }
        val nextScheduleSummary = scheduleSummaries
            .filter { summary -> !summary.activeNow && summary.minutesUntilStart != null }
            .minByOrNull { summary -> summary.minutesUntilStart ?: Int.MAX_VALUE }
        val allowOnlyAllowedAppCount = ((allowedAppPackages + temporarilyAllowedPackages) -
            SafetyGate.neverBlockPackages).size
        val groupStatus = primaryGroup?.status ?: LimitStatus.Normal
        val appLimitSummaries = appLimits.map { (packageName, limitMinutes) ->
                val usage = usageByPackage[packageName]
                val usedMinutes = (usage?.totalTimeMillis ?: 0L).toDisplayMinutes()
                val allowance = todayTemporaryUnlockState.packageAllowances[packageName]
                val unlockedForToday = allowance?.unlockedForToday == true
                val extraMinutes = allowance?.extraMinutes ?: 0
                val temporaryRemainingMinutes = allowance?.temporaryRemainingMinutes(nowMillis) ?: 0
                AppLimitSummary(
                    appName = usage?.appName ?: appNameByPackage[packageName] ?: packageName,
                    packageName = packageName,
                    usedMinutes = usedMinutes,
                    limitMinutes = limitMinutes,
                    extraMinutes = extraMinutes,
                    unlockedForToday = unlockedForToday,
                    temporaryRemainingMinutes = temporaryRemainingMinutes,
                    status = if (unlockedForToday) {
                        LimitStatus.Normal
                    } else {
                        calculateLimitStatus(usedMinutes, limitMinutes + extraMinutes)
                    },
                )
            }.sortedBy { summary -> summary.appName.lowercase() }
        val scheduleStatuses = listOfNotNull(
            activeScheduleSummary?.let { LimitStatus.Exceeded },
            nextScheduleSummary?.takeIf { summary -> activeScheduleSummary?.id != summary.id }?.let { LimitStatus.Warning },
            settings.allowOnlyModeEnabled.takeIf { enabled -> enabled }?.let { LimitStatus.Warning },
        )
        val statuses = listOf(totalStatus) +
            groupSummaries.map { summary -> summary.status } +
            appLimitSummaries.map { summary -> summary.status } +
            scheduleStatuses

        return PolicySummary(
            totalUsedMinutes = totalUsedMinutes,
            totalLimitMinutes = totalLimitMinutes,
            totalExtraMinutes = totalExtraMinutes,
            totalUnlockedForToday = todayTemporaryUnlockState.totalUnlockedForToday,
            totalStatus = totalStatus,
            groupName = primaryGroup?.groupName ?: settings.appGroupName,
            groupUsedMinutes = groupUsedMinutes,
            groupLimitMinutes = groupLimitMinutes,
            groupStatus = groupStatus,
            groupSummaries = groupSummaries,
            appLimitSummaries = appLimitSummaries,
            scheduleSummaries = scheduleSummaries,
            activeScheduleSummary = activeScheduleSummary,
            nextScheduleSummary = nextScheduleSummary,
            allowOnlyModeEnabled = settings.allowOnlyModeEnabled,
            allowOnlyAllowedAppCount = allowOnlyAllowedAppCount,
            temporaryAllowedApps = temporaryAllowedApps,
            warningCount = statuses.count { status -> status == LimitStatus.Warning },
            exceededCount = statuses.count { status -> status == LimitStatus.Exceeded },
        )
    }

    private fun buildScheduleSummaries(
        settings: UsagePolicySettings,
        allowedAppPackages: Set<String>,
        temporarilyAllowedPackages: Set<String>,
    ): List<ScheduleSummary> {
        if (!settings.scheduleBlockingEnabled) {
            return emptyList()
        }
        val now = LocalDateTime.now()
        val globalUserAllowedPackages = (allowedAppPackages + temporarilyAllowedPackages) -
            SafetyGate.neverBlockPackages
        return settings.normalizedScheduleTemplates()
            .map { template ->
                val activeNow = template.isActiveAt(now)
                val effectiveAllowedPackages = template.allowedPackageNames + globalUserAllowedPackages
                ScheduleSummary(
                    id = template.id,
                    name = template.name,
                    startMinutes = template.startMinutes,
                    endMinutes = template.endMinutes,
                    days = template.days,
                    allowedAppCount = effectiveAllowedPackages.size,
                    activeNow = activeNow,
                    minutesUntilStart = if (activeNow) null else template.minutesUntilStart(now),
                )
            }
            .sortedWith(
                compareByDescending<ScheduleSummary> { summary -> summary.activeNow }
                    .thenBy { summary -> summary.minutesUntilStart ?: Int.MAX_VALUE }
                    .thenBy { summary -> summary.name.lowercase() },
            )
    }

    private fun List<AppUsageInfo>.withMonitorStatusUsage(
        monitorStatus: UsageMonitorStatus,
        installedApps: List<InstalledAppInfo>,
    ): List<AppUsageInfo> {
        val packageName = monitorStatus.lastForegroundPackageName
        val usedMillis = monitorStatus.lastUsedMillis
        val todayStartMillis = currentLocalDayStartMillis()
        val maxTodayUsageMillis = (System.currentTimeMillis() - todayStartMillis)
            .coerceAtLeast(0L) + MONITOR_STATUS_TODAY_TOLERANCE_MILLIS
        if (
            !monitorStatus.running ||
            packageName.isBlank() ||
            usedMillis <= 0L ||
            monitorStatus.lastTickMillis < todayStartMillis ||
            usedMillis > maxTodayUsageMillis
        ) {
            return this
        }
        val appNameByPackage = installedApps.associate { app -> app.packageName to app.appName }
        val merged = toMutableList()
        val index = merged.indexOfFirst { usage -> usage.packageName == packageName }
        if (index >= 0) {
            val current = merged[index]
            if (usedMillis > current.totalTimeMillis) {
                merged[index] = current.copy(totalTimeMillis = usedMillis)
            }
        } else {
            merged += AppUsageInfo(
                appName = monitorStatus.lastForegroundAppName
                    .ifBlank { appNameByPackage[packageName] ?: packageName },
                packageName = packageName,
                totalTimeMillis = usedMillis,
            )
        }
        return merged.sortedByDescending { usage -> usage.totalTimeMillis }
    }

    private fun calculateLimitStatus(usedMinutes: Int, limitMinutes: Int): LimitStatus {
        if (limitMinutes <= 0) {
            return LimitStatus.Normal
        }
        return when {
            usedMinutes >= limitMinutes -> LimitStatus.Exceeded
            usedMinutes * 100 >= limitMinutes * 80 -> LimitStatus.Warning
            else -> LimitStatus.Normal
        }
    }

    private fun buildBlockingReadiness(
        safeModeEnabled: Boolean,
        policyEnforcementEnabled: Boolean,
        hasUsageAccess: Boolean,
        overlayPermissionReady: Boolean,
        notificationPermissionReady: Boolean,
        notificationAccessReady: Boolean,
        exactAlarmReady: Boolean,
        allowedAppPackages: Set<String>,
    ): BlockingReadiness {
        return BlockingReadiness(
            safeModeAllowsBlocking = !safeModeEnabled,
            usageAccessReady = hasUsageAccess,
            overlayPermissionReady = overlayPermissionReady,
            notificationPermissionReady = notificationPermissionReady,
            notificationAccessReady = notificationAccessReady,
            exactAlarmReady = exactAlarmReady,
            policyEnforcementReady = policyEnforcementEnabled,
            whitelistReady = SafetyGate.neverBlockPackages.containsAll(SafetyGate.requiredNeverBlockPackages),
            emergencyUnlockReady = true,
        )
    }

    private fun canDrawOverlays(): Boolean {
        val application = getApplication<Application>()
        return OverlayPermissionChecker.state(application).canAttemptOverlay
    }

    private fun hasNotificationListenerAccess(): Boolean {
        val application = getApplication<Application>()
        val expectedComponent = ComponentName(application, ScreenTimeNotificationListenerService::class.java)
        val enabledServices = Settings.Secure.getString(
            application.contentResolver,
            ENABLED_NOTIFICATION_LISTENERS_SETTING,
        ).orEmpty()
        return enabledServices
            .split(':')
            .any { enabledService ->
                enabledService.equals(expectedComponent.flattenToString(), ignoreCase = true) ||
                    enabledService.equals(expectedComponent.flattenToShortString(), ignoreCase = true)
            }
    }

    private fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true
        }
        val application = getApplication<Application>()
        return application.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    private fun debugPolicy(message: String) {
        Log.d(POLICY_DEBUG_TAG, "Policy $message")
    }

    private fun buildBlockDecisionResults(
        safeModeEnabled: Boolean,
        policyEnforcementEnabled: Boolean,
        settings: UsagePolicySettings,
        temporaryUnlockState: TemporaryUnlockState,
        allowedAppPackages: Set<String>,
        todayUsage: List<AppUsageInfo>,
        installedApps: List<InstalledAppInfo>,
        summary: PolicySummary,
    ): List<BlockDecisionResult> {
        val usageByPackage = todayUsage.associateBy { usage -> usage.packageName }
        val groupPackages = settings.selectedPackageSet()
        val installedByPackage = installedApps.associateBy { app -> app.packageName }
        val scheduleAllowedPackages = settings.activeScheduleAllowedPackages()
        val globalBlockCandidates = if (settings.allowOnlyModeEnabled || settings.isScheduleBlockingNow()) {
            installedApps.map { app -> app.packageName }
        } else {
            emptyList()
        }
        val candidatePackages = (
            globalBlockCandidates +
                todayUsage.map { usage -> usage.packageName } +
                settings.appLimitMap().keys +
                groupPackages
            ).distinct()

        return candidatePackages
            .map { packageName ->
                val usage = usageByPackage[packageName]
                val installedApp = installedByPackage[packageName]
                val targetGroup = summary.groupSummaries.firstOrNull { groupSummary ->
                    packageName in groupSummary.packageNames
                }
                BlockDecisionEngine.evaluate(
                    packageName = packageName,
                    appName = usage?.appName ?: installedApp?.appName ?: packageName,
                    safeModeEnabled = safeModeEnabled,
                    policyEnforcementEnabled = policyEnforcementEnabled,
                    settings = settings,
                    appUsedMinutes = (usage?.totalTimeMillis ?: 0L).toDisplayMinutes(),
                    totalUsedMinutes = summary.totalUsedMinutes,
                    exceededGroupPackages = summary.groupSummaries
                        .filter { groupSummary -> groupSummary.status == LimitStatus.Exceeded }
                        .flatMap { groupSummary -> groupSummary.packageNames }
                        .toSet(),
                    targetGroupUsedMinutes = targetGroup?.usedMinutes,
                    targetGroupLimitMinutes = targetGroup?.limitMinutes,
                    temporaryUnlockState = temporaryUnlockState,
                    userAllowedPackages = allowedAppPackages,
                    scheduleAllowedPackages = scheduleAllowedPackages,
                )
            }
            .sortedWith(
                compareByDescending<BlockDecisionResult> { result -> result.decision.isWouldBlock() }
                    .thenBy { result -> result.appName.lowercase() },
            )
            .take(20)
    }

}

private const val USAGE_REFRESH_THROTTLE_MILLIS = 1_500L
private const val USAGE_ACCESS_CHECK_TIMEOUT_MILLIS = 1_500L
private const val USAGE_QUERY_TIMEOUT_MILLIS = 5_000L
private const val USAGE_QUERY_RETRY_COUNT = 3
private const val USAGE_QUERY_RETRY_DELAY_MILLIS = 350L
private const val USAGE_ACCESS_RETRY_DELAY_MILLIS = 800L
private const val USAGE_ACCESS_DENIED_CLEAR_THRESHOLD = 3
private const val INSTALLED_APPS_REFRESH_THROTTLE_MILLIS = 30_000L
private const val PARENT_SYNC_FOREGROUND_THROTTLE_MILLIS = 5_000L
private const val PARENT_LISTENER_SYNC_RETRY_COUNT = 3
private const val PARENT_LISTENER_SYNC_RETRY_DELAY_MILLIS = 2_000L
private const val PARENT_LISTENER_MAX_RECONNECT_DELAY_MILLIS = 30_000L
private const val POLICY_MAX_MINUTES = 720
private const val DAILY_POLICY_MAX_MINUTES = 24 * 60 - 1
private const val POLICY_MAX_SCHEDULE_MINUTES = 24 * 60 - 5
private const val STATISTICS_DAYS = 30
private const val STATISTICS_TOP_APP_LIMIT = 50
private const val ENABLED_NOTIFICATION_LISTENERS_SETTING = "enabled_notification_listeners"
private const val POLICY_DEBUG_TAG = "STM-Foreground"
private const val MONITOR_STATUS_TODAY_TOLERANCE_MILLIS = 2L * 60L * 1000L

private fun parentListenerRetryDelayMillis(attempt: Long): Long {
    val multiplier = 1L shl attempt.coerceAtMost(5L).toInt()
    return (1_000L * multiplier).coerceAtMost(PARENT_LISTENER_MAX_RECONNECT_DELAY_MILLIS)
}

private fun parentListenerChildIds(state: ParentManagementState): Set<String> {
    if (!state.paired) {
        return emptySet()
    }
    return (state.linkedChildDevices.map { child -> child.childDeviceId } + state.childDeviceId)
        .map { childDeviceId -> childDeviceId.trim() }
        .filter { childDeviceId -> childDeviceId.isNotBlank() }
        .toSet()
}

private fun BlockDecision.isWouldBlock(): Boolean {
    return this == BlockDecision.WouldBlockTotalLimit ||
        this == BlockDecision.WouldBlockSchedule ||
        this == BlockDecision.WouldBlockAllowOnly ||
        this == BlockDecision.WouldBlockGroupLimit ||
        this == BlockDecision.WouldBlockAppLimit
}

private fun ScheduleTemplatePolicy.isActiveAt(now: LocalDateTime): Boolean {
    val startMinutes = startMinutes.coerceIn(0, 24 * 60 - 1)
    val endMinutes = endMinutes.coerceIn(0, 24 * 60 - 1)
    if (startMinutes == endMinutes) {
        return false
    }
    val activeDays = days.filter { day -> day in 1..7 }.toSet().ifEmpty { (1..7).toSet() }
    val minuteOfDay = now.hour * 60 + now.minute
    val today = now.dayOfWeek.value
    val yesterday = if (today == 1) 7 else today - 1
    return if (startMinutes < endMinutes) {
        today in activeDays && minuteOfDay >= startMinutes && minuteOfDay < endMinutes
    } else {
        (today in activeDays && minuteOfDay >= startMinutes) ||
            (yesterday in activeDays && minuteOfDay < endMinutes)
    }
}

private fun ScheduleTemplatePolicy.minutesUntilStart(now: LocalDateTime): Int? {
    val activeDays = days.filter { day -> day in 1..7 }.toSet().ifEmpty { (1..7).toSet() }
    val startMinutes = startMinutes.coerceIn(0, 24 * 60 - 1)
    val startHour = startMinutes / 60
    val startMinute = startMinutes % 60
    return (0..7)
        .mapNotNull { dayOffset ->
            val candidateDate = now.toLocalDate().plusDays(dayOffset.toLong())
            if (candidateDate.dayOfWeek.value !in activeDays) {
                null
            } else {
                val candidateStart = candidateDate.atTime(startHour, startMinute)
                if (candidateStart.isAfter(now)) {
                    java.time.Duration.between(now, candidateStart).toMinutes().toInt().coerceAtLeast(0)
                } else {
                    null
                }
            }
        }
        .minOrNull()
}

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
                if (packageName.isBlank() || minutes == null) null else packageName to minutes
            }
        }
        .toMap()
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
    ).map { minutes -> minutes.coerceIn(0, DAILY_POLICY_MAX_MINUTES) }
}

fun UsagePolicySettings.normalizedForDraft(): UsagePolicySettings {
    val cleanAppLimits = appLimitMap()
        .filter { (packageName, minutes) -> packageName.isNotBlank() && minutes > 0 }
        .mapValues { (_, minutes) -> minutes.coerceIn(0, POLICY_MAX_MINUTES) }
    val cleanGroups = normalizedAppGroups()
        .mapIndexed { index, group ->
            group.copy(
                name = group.name,
                packageNames = group.packageNames.filter { packageName -> packageName.isNotBlank() }.toSet(),
                budgetMinutes = group.budgetMinutes.coerceIn(0, POLICY_MAX_MINUTES),
                id = group.id.ifBlank { "legacy-$index" },
            )
        }
        .map { group ->
            val assignedAppLimitTotal = group.packageNames.sumOf { packageName -> cleanAppLimits[packageName] ?: 0 }
            group.copy(budgetMinutes = group.budgetMinutes.coerceAtLeast(assignedAppLimitTotal))
        }
    val groupBudgetTotal = cleanGroups.sumOf { group -> group.budgetMinutes.coerceAtLeast(0) }
    val dailyLimits = dailyLimitMinutesByDay()
        .map { minutes ->
            if (groupBudgetTotal > 0 && minutes > 0) {
                minutes.coerceAtLeast(groupBudgetTotal).coerceAtMost(DAILY_POLICY_MAX_MINUTES)
            } else {
                minutes
            }
        }
    val cleanScheduleTemplates = normalizedScheduleTemplates()
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
        scheduleStartMinutes = scheduleStartMinutes.coerceIn(0, POLICY_MAX_SCHEDULE_MINUTES),
        scheduleEndMinutes = scheduleEndMinutes.coerceIn(0, POLICY_MAX_SCHEDULE_MINUTES),
        scheduleDays = scheduleDaySet().toScheduleDaysEncoded(),
        scheduleTemplates = cleanScheduleTemplates.toScheduleTemplatesEncoded(),
        activeScheduleTemplateId = cleanActiveScheduleTemplateId,
    )
}

fun UsagePolicySettings.policyBudgetValidation(): PolicyBudgetValidation {
    val dailyLimits = dailyLimitMinutesByDay()
    val appLimits = appLimitMap()
    val appLimitTotal = appLimits.values.sumOf { minutes -> minutes.coerceAtLeast(0) }
    val groups = normalizedAppGroups()
    val groupBudgetTotal = groups.sumOf { group -> group.budgetMinutes.coerceAtLeast(0) }
    val overflowingDays = dailyLimits.mapIndexedNotNull { index, dailyLimit ->
        when {
            dailyLimit <= 0 -> null
            appLimitTotal > dailyLimit -> index
            groupBudgetTotal > dailyLimit -> index
            else -> null
        }
    }
    val appGroupLimitConflictCount = groups.count { group ->
        group.budgetMinutes > 0 &&
            group.packageNames.sumOf { packageName -> appLimits[packageName] ?: 0 } > group.budgetMinutes
    }
    return PolicyBudgetValidation(
        hasOverflow = overflowingDays.isNotEmpty() || appGroupLimitConflictCount > 0,
        overflowingDayIndexes = overflowingDays,
        appLimitTotalMinutes = appLimitTotal,
        groupBudgetTotalMinutes = groupBudgetTotal,
        appGroupLimitConflictCount = appGroupLimitConflictCount,
    )
}

fun Map<String, Int>.toAppLimitRules(): String {
    return entries
        .filter { (_, minutes) -> minutes > 0 }
        .sortedBy { (packageName, _) -> packageName }
        .joinToString("|") { (packageName, minutes) -> "$packageName=$minutes" }
}

fun UsagePolicySettings.todayLimitMinutes(): Int {
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
    val groupBudgetTotal = normalizedAppGroups().sumOf { group -> group.budgetMinutes.coerceAtLeast(0) }
    return if (groupBudgetTotal > 0 && rawLimit > 0) {
        rawLimit.coerceAtLeast(groupBudgetTotal).coerceAtMost(DAILY_POLICY_MAX_MINUTES)
    } else {
        rawLimit.coerceIn(0, DAILY_POLICY_MAX_MINUTES)
    }
}

private fun Long.toDisplayMinutes(): Int {
    if (this <= 0L) {
        return 0
    }
    return ((this + 59_999L) / 60_000L).toInt()
}

private fun currentUsageDateKey(): String {
    return LocalDate.now().toString()
}

private fun currentLocalDayStartMillis(): Long {
    return Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun millisUntilNextLocalDay(): Long {
    val now = Calendar.getInstance()
    val nextDay = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 1)
        set(Calendar.MILLISECOND, 0)
    }
    return nextDay.timeInMillis - now.timeInMillis
}

private fun List<AppUsageInfo>.withInstalledAppNames(installedApps: List<InstalledAppInfo>): List<AppUsageInfo> {
    if (installedApps.isEmpty()) {
        return this
    }
    val installedNameByPackage = installedApps.associate { app -> app.packageName to app.appName }
    return map { usage ->
        val installedName = installedNameByPackage[usage.packageName]
        if (!installedName.isNullOrBlank() && usage.appName.looksLikePackageName()) {
            usage.copy(appName = installedName)
        } else {
            usage
        }
    }
}

private fun String.looksLikePackageName(): Boolean {
    return contains('.') || all { character -> character.isLowerCase() || character == '_' || character == '-' }
}

private data class PersistedState(
    val safeModeEnabled: Boolean = true,
    val appLanguage: AppLanguage = AppLanguage.Korean,
    val policyEnforcementEnabled: Boolean = false,
    val warningNotificationsEnabled: Boolean = true,
    val limitNotificationsEnabled: Boolean = true,
    val usagePolicySettings: UsagePolicySettings = UsagePolicySettings(),
    val temporaryUnlockState: TemporaryUnlockState = TemporaryUnlockState(),
    val allowedAppPackages: Set<String> = emptySet(),
    val parentManagementState: ParentManagementState = ParentManagementState(),
    val eventLog: List<EventLogEntry> = emptyList(),
    val foregroundDetectionStatus: ForegroundDetectionStatus? = null,
    val usageMonitorStatus: UsageMonitorStatus = UsageMonitorStatus(),
    val systemHealthStatus: SystemHealthStatus = SystemHealthStatus(),
    val permissionSetupCompletedOnce: Boolean = false,
    val policySectionExpansionSettings: PolicySectionExpansionSettings = PolicySectionExpansionSettings(),
)

private data class NotificationSettingsState(
    val warningNotificationsEnabled: Boolean = true,
    val limitNotificationsEnabled: Boolean = true,
)

private data class PolicyAndLogState(
    val usagePolicySettings: UsagePolicySettings = UsagePolicySettings(),
    val temporaryUnlockState: TemporaryUnlockState = TemporaryUnlockState(),
    val allowedAppPackages: Set<String> = emptySet(),
    val parentManagementState: ParentManagementState = ParentManagementState(),
    val eventLog: List<EventLogEntry> = emptyList(),
)

private data class UsageState(
    val dateKey: String = currentUsageDateKey(),
    val hasUsageAccess: Boolean = false,
    val overlayPermissionReady: Boolean = false,
    val notificationPermissionReady: Boolean = false,
    val notificationAccessReady: Boolean = false,
    val exactAlarmReady: Boolean = false,
    val usageAccessChecking: Boolean = false,
    val statisticsRefreshing: Boolean = false,
    val usageLastUpdatedAtMillis: Long = 0L,
    val statisticsLastUpdatedAtMillis: Long = 0L,
    val todayUsage: List<AppUsageInfo> = emptyList(),
    val dailyUsage: List<DailyUsageInfo> = emptyList(),
    val topAppsSevenDays: List<AppUsageInfo> = emptyList(),
    val topAppsThirtyDays: List<AppUsageInfo> = emptyList(),
    val installedApps: List<InstalledAppInfo> = emptyList(),
)

private data class TransientState(
    val emergencyUnlockStatus: EmergencyUnlockStatus = EmergencyUnlockStatus.Idle,
    val autoRecoveryStatus: AutoRecoveryStatus = AutoRecoveryStatus.Idle,
    val usageState: UsageState = UsageState(),
    val policySaveStatus: PolicySaveStatus = PolicySaveStatus.Idle,
    val pinChangeStatus: PinChangeStatus = PinChangeStatus.Idle,
    val safeModePinStatus: SafeModePinStatus = SafeModePinStatus.Idle,
    val policyDraftSettings: UsagePolicySettings? = null,
    val policyDraftAllowedAppPackages: Set<String>? = null,
)
