package com.example.screentimemanager.ui.safety

import android.app.Application
import android.os.SystemClock
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.screentimemanager.data.AppLanguage
import com.example.screentimemanager.data.EventLogEntry
import com.example.screentimemanager.data.ForegroundDetectionStatus
import com.example.screentimemanager.data.SettingsRepository
import com.example.screentimemanager.data.UsagePolicySettings
import com.example.screentimemanager.data.normalizedAppGroups
import com.example.screentimemanager.data.settingsDataStore
import com.example.screentimemanager.data.toAppGroupsEncoded
import com.example.screentimemanager.notification.UsageNotificationHelper
import com.example.screentimemanager.safety.BlockDecision
import com.example.screentimemanager.safety.BlockDecisionEngine
import com.example.screentimemanager.safety.BlockDecisionResult
import com.example.screentimemanager.safety.SafetyGate
import com.example.screentimemanager.usage.AppCatalogRepository
import com.example.screentimemanager.usage.AppVisibility
import com.example.screentimemanager.usage.AppUsageInfo
import com.example.screentimemanager.usage.InstalledAppInfo
import com.example.screentimemanager.usage.UsageStatsRepository
import com.example.screentimemanager.worker.UsagePolicyAlertRunner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
    val todayUsage: List<AppUsageInfo> = emptyList(),
    val installedApps: List<InstalledAppInfo> = emptyList(),
    val usagePolicySettings: UsagePolicySettings = UsagePolicySettings(),
    val policyDraftSettings: UsagePolicySettings = UsagePolicySettings(),
    val policyDraftHasChanges: Boolean = false,
    val policyBudgetValidation: PolicyBudgetValidation = PolicyBudgetValidation(),
    val policySummary: PolicySummary = PolicySummary(),
    val policySaveStatus: PolicySaveStatus = PolicySaveStatus.Idle,
    val pinChangeStatus: PinChangeStatus = PinChangeStatus.Idle,
    val eventLog: List<EventLogEntry> = emptyList(),
    val foregroundDetectionStatus: ForegroundDetectionStatus? = null,
    val blockingReadiness: BlockingReadiness = BlockingReadiness(),
    val blockDecisionResults: List<BlockDecisionResult> = emptyList(),
)

data class PolicySummary(
    val totalUsedMinutes: Int = 0,
    val totalLimitMinutes: Int = 120,
    val totalStatus: LimitStatus = LimitStatus.Normal,
    val groupName: String = "SNS",
    val groupUsedMinutes: Int = 0,
    val groupLimitMinutes: Int = 60,
    val groupStatus: LimitStatus = LimitStatus.Normal,
    val groupSummaries: List<AppGroupSummary> = emptyList(),
    val appLimitSummaries: List<AppLimitSummary> = emptyList(),
    val warningCount: Int = 0,
    val exceededCount: Int = 0,
)

data class AppGroupSummary(
    val groupName: String,
    val packageNames: Set<String>,
    val usedMinutes: Int,
    val limitMinutes: Int,
    val status: LimitStatus,
    val appUsages: List<GroupAppUsageSummary> = emptyList(),
)

data class GroupAppUsageSummary(
    val appName: String,
    val packageName: String,
    val usedMinutes: Int,
    val limitMinutes: Int? = null,
)

data class AppLimitSummary(
    val appName: String,
    val packageName: String,
    val usedMinutes: Int,
    val limitMinutes: Int,
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

data class BlockingReadiness(
    val accessibilityServiceEnabled: Boolean = false,
    val safeModeAllowsBlocking: Boolean = false,
    val usageAccessReady: Boolean = false,
    val notificationPermissionReady: Boolean = false,
    val policyEnforcementReady: Boolean = false,
    val whitelistReady: Boolean = true,
    val emergencyUnlockReady: Boolean = true,
    val killSwitchReady: Boolean = true,
) {
    val readyForBlocking: Boolean
        get() = accessibilityServiceEnabled &&
            safeModeAllowsBlocking &&
            usageAccessReady &&
            policyEnforcementReady &&
            whitelistReady &&
            emergencyUnlockReady &&
            killSwitchReady
}

class SafeModeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application.settingsDataStore)
    private val usageStatsRepository = UsageStatsRepository(application)
    private val appCatalogRepository = AppCatalogRepository(application)
    private val notificationHelper = UsageNotificationHelper(application)
    private val emergencyUnlockStatus = MutableStateFlow(EmergencyUnlockStatus.Idle)
    private val autoRecoveryStatus = MutableStateFlow(AutoRecoveryStatus.Idle)
    private val usageState = MutableStateFlow(
        UsageState(
            hasUsageAccess = runCatching { usageStatsRepository.hasUsageAccess() }.getOrDefault(false),
            notificationPermissionReady = runCatching { notificationHelper.canPostNotifications() }.getOrDefault(false),
        ),
    )
    private val policySaveStatus = MutableStateFlow(PolicySaveStatus.Idle)
    private val policyDraftSettings = MutableStateFlow<UsagePolicySettings?>(null)
    private val pinChangeStatus = MutableStateFlow(PinChangeStatus.Idle)
    private var refreshUsageJob: Job? = null
    private var alertEvaluationJob: Job? = null
    private var refreshInstalledAppsJob: Job? = null
    private var lastUsageRefreshAtMillis: Long = 0L
    private var lastInstalledAppsRefreshAtMillis: Long = 0L
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
    ) { usagePolicySettings, eventLog ->
        PolicyAndLogState(
            usagePolicySettings = usagePolicySettings,
            eventLog = eventLog,
        )
    }

    private val persistedBaseState = combine(
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
            eventLog = policyAndLog.eventLog,
        )
    }

    private val persistedState = combine(
        persistedBaseState,
        repository.foregroundDetectionStatus,
    ) { persistedState, detectionStatus ->
        persistedState.copy(
            foregroundDetectionStatus = detectionStatus?.takeUnless { status ->
                AppVisibility.isHiddenPackage(status.packageName) ||
                    status.packageName in SafetyGate.neverBlockPackages
            },
        )
    }

    private val transientBaseState = combine(
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

    private val transientState = combine(
        transientBaseState,
        policyDraftSettings,
    ) { transientState, draftSettings ->
        transientState.copy(policyDraftSettings = draftSettings)
    }

    val uiState: StateFlow<SafeModeUiState> = combine(
        persistedState,
        transientState,
    ) { persistedState, transientState ->
        val savedSettings = persistedState.usagePolicySettings.normalizedForDraft()
        val draftSettings = (transientState.policyDraftSettings ?: persistedState.usagePolicySettings)
            .normalizedForDraft()
        val budgetValidation = draftSettings.policyBudgetValidation()
        val policySummary = buildPolicySummary(
            settings = persistedState.usagePolicySettings,
            todayUsage = transientState.usageState.todayUsage,
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
            todayUsage = transientState.usageState.todayUsage.take(50),
            installedApps = transientState.usageState.installedApps,
            usagePolicySettings = persistedState.usagePolicySettings,
            policyDraftSettings = draftSettings,
            policyDraftHasChanges = draftSettings != savedSettings,
            policyBudgetValidation = budgetValidation,
            policySummary = policySummary,
            policySaveStatus = transientState.policySaveStatus,
            pinChangeStatus = transientState.pinChangeStatus,
            eventLog = persistedState.eventLog,
            foregroundDetectionStatus = persistedState.foregroundDetectionStatus,
            blockingReadiness = buildBlockingReadiness(
                safeModeEnabled = persistedState.safeModeEnabled,
                policyEnforcementEnabled = persistedState.policyEnforcementEnabled,
                hasUsageAccess = transientState.usageState.hasUsageAccess,
                notificationPermissionReady = transientState.usageState.notificationPermissionReady,
            ),
            blockDecisionResults = buildBlockDecisionResults(
                safeModeEnabled = persistedState.safeModeEnabled,
                policyEnforcementEnabled = persistedState.policyEnforcementEnabled,
                settings = persistedState.usagePolicySettings,
                todayUsage = transientState.usageState.todayUsage,
                installedApps = transientState.usageState.installedApps,
                summary = policySummary,
            ),
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SafeModeUiState(),
        )

    init {
        viewModelScope.launch {
            val recovered = repository.markAppStartedAndRecoverIfNeeded()
            if (recovered) {
                autoRecoveryStatus.value = AutoRecoveryStatus.RecoveredToSafeMode
            }
            refreshForForeground(force = true)
        }
    }

    fun refreshForForeground(force: Boolean = false) {
        refreshSystemPermissionStates()
        val now = SystemClock.elapsedRealtime()
        if (force || now - lastInstalledAppsRefreshAtMillis >= INSTALLED_APPS_REFRESH_THROTTLE_MILLIS) {
            lastInstalledAppsRefreshAtMillis = now
            refreshInstalledApps()
        }
        if (force || now - lastUsageRefreshAtMillis >= USAGE_REFRESH_THROTTLE_MILLIS) {
            lastUsageRefreshAtMillis = now
            refreshUsageStats(force = force)
        }
    }

    private fun refreshSystemPermissionStates() {
        usageState.value = usageState.value.copy(
            notificationPermissionReady = runCatching {
                notificationHelper.canPostNotifications()
            }.getOrDefault(false),
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
                    usageState.value = usageState.value.copy(usageAccessChecking = true)
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
                        if (!previousState.hasUsageAccess || previousState.todayUsage.isEmpty() || usageAccessDeniedCount >= 2) {
                            usageState.value = previousState.copy(
                                hasUsageAccess = false,
                                usageAccessChecking = false,
                                todayUsage = emptyList(),
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

                val todayUsage = try {
                    withTimeoutOrNull(USAGE_QUERY_TIMEOUT_MILLIS) {
                        usageStatsRepository.getTodayUsage(maxItems = 500, skipAccessCheck = true)
                    }
                } catch (exception: Exception) {
                    if (exception is CancellationException) {
                        throw exception
                    }
                    null
                }
                if (todayUsage != null) {
                    if (!isCurrentRefresh()) return@launch
                    usageState.value = usageState.value.copy(
                        hasUsageAccess = true,
                        usageAccessChecking = false,
                        todayUsage = todayUsage,
                    )
                    evaluatePolicyAlertsAsync()
                } else if (isCurrentRefresh()) {
                    usageState.value = usageState.value.copy(usageAccessChecking = false)
                }
            } finally {
                if (isCurrentRefresh() && usageState.value.usageAccessChecking) {
                    usageState.value = usageState.value.copy(usageAccessChecking = false)
                }
            }
        }
    }

    private fun scheduleUsageAccessRetry(generation: Long) {
        val currentState = usageState.value
        if (!currentState.hasUsageAccess && currentState.todayUsage.isEmpty()) {
            return
        }
        viewModelScope.launch {
            delay(USAGE_ACCESS_RETRY_DELAY_MILLIS)
            if (generation == usageRefreshGeneration) {
                refreshUsageStats(force = true)
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
            usageState.value = usageState.value.copy(
                installedApps = appCatalogRepository.getLaunchableApps(),
            )
        }
    }

    fun updatePolicyDraft(settings: UsagePolicySettings) {
        policyDraftSettings.value = settings.normalizedForDraft()
        policySaveStatus.value = PolicySaveStatus.Idle
    }

    fun resetPolicyDraft() {
        policyDraftSettings.value = null
        policySaveStatus.value = PolicySaveStatus.Idle
    }

    fun savePolicyDraft(adminPin: String) {
        viewModelScope.launch {
            val draftSettings = (policyDraftSettings.value ?: uiState.value.usagePolicySettings)
                .normalizedForDraft()
            if (draftSettings.policyBudgetValidation().hasOverflow) {
                policySaveStatus.value = PolicySaveStatus.BudgetExceeded
                return@launch
            }

            val saved = repository.saveUsagePolicySettings(draftSettings, adminPin)
            policySaveStatus.value = if (saved) {
                policyDraftSettings.value = null
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

    fun setSafeModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setSafeModeEnabled(enabled)
        }
    }

    fun setPolicyEnforcementEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setPolicyEnforcementEnabled(enabled)
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

    fun activateKillSwitch() {
        viewModelScope.launch {
            repository.activateKillSwitch()
            emergencyUnlockStatus.value = EmergencyUnlockStatus.Unlocked
        }
    }

    private fun buildPolicySummary(
        settings: UsagePolicySettings,
        todayUsage: List<AppUsageInfo>,
        installedApps: List<InstalledAppInfo>,
    ): PolicySummary {
        val usageByPackage = todayUsage.associateBy { appUsage -> appUsage.packageName }
        val appNameByPackage = installedApps.associate { app -> app.packageName to app.appName }
        val appLimits = settings.appLimitMap()
        val groupSummaries = settings.normalizedAppGroups().map { group ->
            val usedMinutes = group.packageNames
                .sumOf { packageName -> usageByPackage[packageName]?.totalTimeMillis ?: 0L }
                .toDisplayMinutes()
            val appUsages = group.packageNames
                .map { packageName ->
                    val usage = usageByPackage[packageName]
                    GroupAppUsageSummary(
                        appName = usage?.appName ?: appNameByPackage[packageName] ?: packageName,
                        packageName = packageName,
                        usedMinutes = (usage?.totalTimeMillis ?: 0L).toDisplayMinutes(),
                        limitMinutes = appLimits[packageName],
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
                status = calculateLimitStatus(usedMinutes, group.budgetMinutes),
                appUsages = appUsages,
            )
        }

        val totalUsedMinutes = todayUsage.sumOf { appUsage -> appUsage.totalTimeMillis }.toDisplayMinutes()
        val totalLimitMinutes = settings.todayLimitMinutes()
        val primaryGroup = groupSummaries.firstOrNull()
        val groupUsedMinutes = primaryGroup?.usedMinutes ?: 0
        val groupLimitMinutes = primaryGroup?.limitMinutes ?: 0
        val totalStatus = calculateLimitStatus(totalUsedMinutes, totalLimitMinutes)
        val groupStatus = primaryGroup?.status ?: LimitStatus.Normal
        val appLimitSummaries = appLimits.map { (packageName, limitMinutes) ->
                val usage = usageByPackage[packageName]
                val usedMinutes = (usage?.totalTimeMillis ?: 0L).toDisplayMinutes()
                AppLimitSummary(
                    appName = usage?.appName ?: appNameByPackage[packageName] ?: packageName,
                    packageName = packageName,
                    usedMinutes = usedMinutes,
                    limitMinutes = limitMinutes,
                    status = calculateLimitStatus(usedMinutes, limitMinutes),
                )
            }.sortedBy { summary -> summary.appName.lowercase() }
        val statuses = listOf(totalStatus) +
            groupSummaries.map { summary -> summary.status } +
            appLimitSummaries.map { summary -> summary.status }

        return PolicySummary(
            totalUsedMinutes = totalUsedMinutes,
            totalLimitMinutes = totalLimitMinutes,
            totalStatus = totalStatus,
            groupName = primaryGroup?.groupName ?: settings.appGroupName,
            groupUsedMinutes = groupUsedMinutes,
            groupLimitMinutes = groupLimitMinutes,
            groupStatus = groupStatus,
            groupSummaries = groupSummaries,
            appLimitSummaries = appLimitSummaries,
            warningCount = statuses.count { status -> status == LimitStatus.Warning },
            exceededCount = statuses.count { status -> status == LimitStatus.Exceeded },
        )
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
        notificationPermissionReady: Boolean,
    ): BlockingReadiness {
        return BlockingReadiness(
            accessibilityServiceEnabled = isFutureAccessibilityServiceEnabled(),
            safeModeAllowsBlocking = !safeModeEnabled,
            usageAccessReady = hasUsageAccess,
            notificationPermissionReady = notificationPermissionReady,
            policyEnforcementReady = policyEnforcementEnabled,
            whitelistReady = SafetyGate.neverBlockPackages.containsAll(SafetyGate.requiredNeverBlockPackages),
            emergencyUnlockReady = true,
            killSwitchReady = true,
        )
    }

    private fun buildBlockDecisionResults(
        safeModeEnabled: Boolean,
        policyEnforcementEnabled: Boolean,
        settings: UsagePolicySettings,
        todayUsage: List<AppUsageInfo>,
        installedApps: List<InstalledAppInfo>,
        summary: PolicySummary,
    ): List<BlockDecisionResult> {
        val usageByPackage = todayUsage.associateBy { usage -> usage.packageName }
        val groupPackages = settings.selectedPackageSet()
        val installedByPackage = installedApps.associateBy { app -> app.packageName }
        val candidatePackages = (
            installedApps.map { app -> app.packageName } +
                todayUsage.map { usage -> usage.packageName } +
                settings.appLimitMap().keys +
                groupPackages
            ).distinct()

        return candidatePackages
            .map { packageName ->
                val usage = usageByPackage[packageName]
                val installedApp = installedByPackage[packageName]
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
                )
            }
            .sortedWith(
                compareByDescending<BlockDecisionResult> { result -> result.decision.isWouldBlock() }
                    .thenBy { result -> result.appName.lowercase() },
            )
            .take(20)
    }

    private fun isFutureAccessibilityServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            getApplication<Application>().contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        return enabledServices
            .split(':')
            .any { serviceName -> serviceName.equals(SafetyGate.FUTURE_ACCESSIBILITY_SERVICE_ID, ignoreCase = true) }
    }
}

private const val USAGE_REFRESH_THROTTLE_MILLIS = 1_500L
private const val USAGE_ACCESS_CHECK_TIMEOUT_MILLIS = 1_500L
private const val USAGE_QUERY_TIMEOUT_MILLIS = 5_000L
private const val USAGE_ACCESS_RETRY_DELAY_MILLIS = 800L
private const val INSTALLED_APPS_REFRESH_THROTTLE_MILLIS = 30_000L
private const val POLICY_MAX_MINUTES = 720

private fun BlockDecision.isWouldBlock(): Boolean {
    return this == BlockDecision.WouldBlockTotalLimit ||
        this == BlockDecision.WouldBlockGroupLimit ||
        this == BlockDecision.WouldBlockAppLimit
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
    ).map { minutes -> minutes.coerceIn(0, POLICY_MAX_MINUTES) }
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
        .ifEmpty {
            listOf(
                com.example.screentimemanager.data.AppGroupPolicy(
                    name = "Group 1",
                    packageNames = emptySet(),
                    budgetMinutes = 60,
                    id = "default-group-1",
                ),
            )
        }
    val groupBudgetTotal = cleanGroups.sumOf { group -> group.budgetMinutes.coerceAtLeast(0) }
    val dailyLimits = dailyLimitMinutesByDay()
        .map { minutes ->
            if (groupBudgetTotal > 0) {
                minutes.coerceAtLeast(groupBudgetTotal).coerceAtMost(POLICY_MAX_MINUTES)
            } else {
                minutes
            }
        }
    val primaryGroup = cleanGroups.first()
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
        appGroupName = primaryGroup.name,
        appGroupPackages = primaryGroup.packageNames.sorted().joinToString(","),
        appGroupBudgetMinutes = primaryGroup.budgetMinutes,
        appGroups = cleanGroups.toAppGroupsEncoded(),
        appLimitRules = cleanAppLimits.toAppLimitRules(),
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
    return if (groupBudgetTotal > 0) {
        rawLimit.coerceAtLeast(groupBudgetTotal).coerceAtMost(POLICY_MAX_MINUTES)
    } else {
        rawLimit.coerceIn(0, POLICY_MAX_MINUTES)
    }
}

private fun Long.toDisplayMinutes(): Int {
    if (this <= 0L) {
        return 0
    }
    return ((this + 59_999L) / 60_000L).toInt()
}

private data class PersistedState(
    val safeModeEnabled: Boolean = true,
    val appLanguage: AppLanguage = AppLanguage.Korean,
    val policyEnforcementEnabled: Boolean = false,
    val warningNotificationsEnabled: Boolean = true,
    val limitNotificationsEnabled: Boolean = true,
    val usagePolicySettings: UsagePolicySettings = UsagePolicySettings(),
    val eventLog: List<EventLogEntry> = emptyList(),
    val foregroundDetectionStatus: ForegroundDetectionStatus? = null,
)

private data class NotificationSettingsState(
    val warningNotificationsEnabled: Boolean = true,
    val limitNotificationsEnabled: Boolean = true,
)

private data class PolicyAndLogState(
    val usagePolicySettings: UsagePolicySettings = UsagePolicySettings(),
    val eventLog: List<EventLogEntry> = emptyList(),
)

private data class UsageState(
    val hasUsageAccess: Boolean = false,
    val notificationPermissionReady: Boolean = false,
    val usageAccessChecking: Boolean = false,
    val todayUsage: List<AppUsageInfo> = emptyList(),
    val installedApps: List<InstalledAppInfo> = emptyList(),
)

private data class TransientState(
    val emergencyUnlockStatus: EmergencyUnlockStatus = EmergencyUnlockStatus.Idle,
    val autoRecoveryStatus: AutoRecoveryStatus = AutoRecoveryStatus.Idle,
    val usageState: UsageState = UsageState(),
    val policySaveStatus: PolicySaveStatus = PolicySaveStatus.Idle,
    val pinChangeStatus: PinChangeStatus = PinChangeStatus.Idle,
    val policyDraftSettings: UsagePolicySettings? = null,
)
