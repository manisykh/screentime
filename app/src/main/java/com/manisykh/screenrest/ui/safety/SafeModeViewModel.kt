package com.manisykh.screenrest.ui.safety

import android.app.AlarmManager
import android.app.Application
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
import com.manisykh.screenrest.data.ChildUsageSnapshot
import com.manisykh.screenrest.data.ChildUsageRefreshRequest
import com.manisykh.screenrest.data.ImmediateBlockState
import com.manisykh.screenrest.data.ImmediateBlockReadState
import com.manisykh.screenrest.data.mergeImmediateBlockReadState
import com.manisykh.screenrest.data.ParentRemoteSyncResult
import com.manisykh.screenrest.data.EventLogEntry
import com.manisykh.screenrest.data.EventLogType
import com.manisykh.screenrest.data.EXPLICIT_ZERO_LIMIT_STORAGE_MINUTES
import com.manisykh.screenrest.data.ForegroundDetectionStatus
import com.manisykh.screenrest.data.HardshipLevel
import com.manisykh.screenrest.data.HardshipPolicyType
import com.manisykh.screenrest.data.HardshipRuntimeState
import com.manisykh.screenrest.data.HardshipPolicyKey
import com.manisykh.screenrest.data.ParentManagementState
import com.manisykh.screenrest.data.ParentAccountAuthCoordinatorFactory
import com.manisykh.screenrest.data.ParentAccountAuthFailure
import com.manisykh.screenrest.data.ParentAccountAuthState
import com.manisykh.screenrest.data.ParentDeviceRole
import com.manisykh.screenrest.data.ParentNotificationState
import com.manisykh.screenrest.data.PairingOperationFailure
import com.manisykh.screenrest.data.PairingOperationResult
import com.manisykh.screenrest.data.ParentRemoteSyncDataSourceFactory
import com.manisykh.screenrest.data.ParentRemoteChangeType
import com.manisykh.screenrest.data.PolicySectionExpansionSettings
import com.manisykh.screenrest.data.PolicyConfigurationSaveResult
import com.manisykh.screenrest.data.SettingsRepository
import com.manisykh.screenrest.data.ScheduleTemplatePolicy
import com.manisykh.screenrest.data.SystemHealthStatus
import com.manisykh.screenrest.data.TemporaryUnlockState
import com.manisykh.screenrest.data.UsagePolicySettings
import com.manisykh.screenrest.data.UsageMonitorStatus
import com.manisykh.screenrest.data.activeScheduleAllowedPackages
import com.manisykh.screenrest.data.activeScheduleTemplates
import com.manisykh.screenrest.data.allowOnlyHardshipKey
import com.manisykh.screenrest.data.appGroupHardshipKey
import com.manisykh.screenrest.data.appLimitHardshipKey
import com.manisykh.screenrest.data.dailyHardshipKey
import com.manisykh.screenrest.data.scheduleHardshipKey
import com.manisykh.screenrest.data.appLimitHardshipLevelMap
import com.manisykh.screenrest.data.appLimitActiveDayMap
import com.manisykh.screenrest.data.appliesOn
import com.manisykh.screenrest.data.currentPolicyDayOfWeek
import com.manisykh.screenrest.data.temporaryRemainingMinutes
import com.manisykh.screenrest.data.isTemporarilyAllowed
import com.manisykh.screenrest.data.isScheduleBlockingNow
import com.manisykh.screenrest.data.isActiveAt
import com.manisykh.screenrest.data.nextOccurrenceStartMillis
import com.manisykh.screenrest.data.currentOccurrenceEndMillis
import com.manisykh.screenrest.data.normalizedAppGroups
import com.manisykh.screenrest.data.normalizedPolicyDays
import com.manisykh.screenrest.data.normalizedScheduleTemplates
import com.manisykh.screenrest.data.overlappingSchedulePairs
import com.manisykh.screenrest.data.duplicateAppGroupPackages
import com.manisykh.screenrest.data.scheduleDaySet
import com.manisykh.screenrest.data.toScheduleDaysEncoded
import com.manisykh.screenrest.data.toScheduleTemplatesEncoded
import com.manisykh.screenrest.data.settingsDataStore
import com.manisykh.screenrest.data.toAppGroupsEncoded
import com.manisykh.screenrest.data.toAppLimitHardshipLevelsEncoded
import com.manisykh.screenrest.data.toAppLimitActiveDaysEncoded
import com.manisykh.screenrest.data.decodeOptionalLimitMinutes
import com.manisykh.screenrest.data.limitMinutesOrNull
import com.manisykh.screenrest.notification.ParentRemoteNotificationCoordinator
import com.manisykh.screenrest.notification.PushTokenRegistrationWorker
import com.manisykh.screenrest.notification.UsageNotificationHelper
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
import com.manisykh.screenrest.worker.ChildUsageSnapshotPublisher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
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
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Calendar

data class SafeModeUiState(
    val safeModeEnabled: Boolean = true,
    val appLanguage: AppLanguage = AppLanguage.Korean,
    val monitoringDisclosureLoaded: Boolean = false,
    val monitoringDisclosureAccepted: Boolean = false,
    val childTopAppsSharingEnabled: Boolean = false,
    val securityPinsConfigured: Boolean = false,
    val policyEnforcementEnabled: Boolean = false,
    val warningNotificationsEnabled: Boolean = true,
    val limitNotificationsEnabled: Boolean = true,
    val safeRecoveryStatus: SafeRecoveryStatus = SafeRecoveryStatus.Idle,
    val autoRecoveryStatus: AutoRecoveryStatus = AutoRecoveryStatus.Idle,
    val hasUsageAccess: Boolean = false,
    val usageAccessChecking: Boolean = false,
    val statisticsRefreshing: Boolean = false,
    val usageLastUpdatedAtMillis: Long = 0L,
    val statisticsLastUpdatedAtMillis: Long = 0L,
    val todayUsage: List<AppUsageInfo> = emptyList(),
    val usageStatistics: UsageStatistics = UsageStatistics(),
    val installedApps: List<InstalledAppInfo> = emptyList(),
    /** Apps admitted by the manual allow-only mode. */
    val allowedAppPackages: Set<String> = emptySet(),
    /** User-selected apps that bypass all enforcement policies. */
    val allRestrictionsExemptPackages: Set<String> = emptySet(),
    val parentManagementState: ParentManagementState = ParentManagementState(),
    val childUsageSnapshots: Map<String, ChildUsageSnapshot> = emptyMap(),
    val childUsageRefreshRequests: Map<String, ChildUsageRefreshRequest> = emptyMap(),
    /** Missing key = still loading, Failed = unreadable, Known = server-confirmed. */
    val childImmediateBlocks: Map<String, ImmediateBlockReadState> = emptyMap(),
    val localImmediateBlock: ImmediateBlockState = ImmediateBlockState(),
    val parentNotificationState: ParentNotificationState = ParentNotificationState(),
    val parentRequestNotificationReady: Boolean = false,
    val parentRequestNotificationIssue: String = "",
    val hardshipRuntimeState: HardshipRuntimeState = HardshipRuntimeState(),
    val usagePolicySettings: UsagePolicySettings = UsagePolicySettings(),
    val temporaryUnlockState: TemporaryUnlockState = TemporaryUnlockState(),
    val policyDraftSettings: UsagePolicySettings = UsagePolicySettings(),
    val policyDraftAllowedAppPackages: Set<String> = emptySet(),
    val policyDraftAllRestrictionsExemptPackages: Set<String> = emptySet(),
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
    val actualTotalUsedMinutes: Int = 0,
    val totalUsedMinutes: Int = 0,
    val totalLimitMinutes: Int = 120,
    val totalLimitEnabled: Boolean = true,
    val dailyPolicyEnabled: Boolean = true,
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
    val allRestrictionsExemptAppCount: Int = 0,
    val effectiveAppSummaries: List<EffectiveAppPolicySummary> = emptyList(),
    val temporaryAllowedApps: List<TemporaryAllowedAppSummary> = emptyList(),
    val warningCount: Int = 0,
    val exceededCount: Int = 0,
    val dailyHardshipLevel: HardshipLevel = HardshipLevel.Off,
    val appGroupsHardshipLevel: HardshipLevel = HardshipLevel.Off,
    val appLimitsHardshipLevel: HardshipLevel = HardshipLevel.Off,
    val scheduleHardshipLevel: HardshipLevel = HardshipLevel.Off,
    val allowOnlyHardshipLevel: HardshipLevel = HardshipLevel.Off,
    val activeHardshipPolicies: Set<HardshipPolicyType> = emptySet(),
    val hardshipItems: List<HardshipPolicySummaryItem> = emptyList(),
    val activeHardshipPolicyKeys: Set<HardshipPolicyKey> = emptySet(),
    val emergencyPassLastUsedAtMillis: Long = 0L,
    val emergencyPassNextAvailableAtMillis: Long = 0L,
)

enum class EffectiveAppAccess {
    RequiredAllowed,
    AllRestrictionsExempt,
    ScheduleAllowed,
    AllowOnlyAllowed,
    NormallyAllowed,
    BlockedBySchedule,
    BlockedByAllowOnly,
    BlockedByParent,
}

enum class EffectiveTimeLimiter {
    None,
    Daily,
    AppGroup,
    App,
}

data class EffectiveAppPolicySummary(
    val appName: String,
    val packageName: String,
    val access: EffectiveAppAccess,
    val limitingPolicy: EffectiveTimeLimiter = EffectiveTimeLimiter.None,
    val remainingMinutes: Int? = null,
    val status: LimitStatus = LimitStatus.Normal,
)

data class HardshipPolicySummaryItem(
    val policyKey: HardshipPolicyKey,
    val targetName: String = "",
    val level: HardshipLevel,
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
    val limitEnabled: Boolean = true,
    val limitConfigured: Boolean = true,
    val activeDays: Set<Int> = (1..7).toSet(),
    val activeToday: Boolean = true,
    val extraMinutes: Int = 0,
    val excludedPackageCount: Int = 0,
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
    val excludedFromRestrictions: Boolean = false,
)

data class AppLimitSummary(
    val appName: String,
    val packageName: String,
    val usedMinutes: Int,
    val limitMinutes: Int,
    val activeDays: Set<Int> = (1..7).toSet(),
    val activeToday: Boolean = true,
    val extraMinutes: Int = 0,
    val unlockedForToday: Boolean = false,
    val temporaryRemainingMinutes: Int = 0,
    val excludedFromRestrictions: Boolean = false,
    val status: LimitStatus,
)

data class PolicyBudgetValidation(
    val hasOverflow: Boolean = false,
    val overflowingDayIndexes: List<Int> = emptyList(),
    val appLimitTotalMinutes: Int = 0,
    val groupBudgetTotalMinutes: Int = 0,
    val appGroupLimitConflictCount: Int = 0,
    val duplicateGroupPackageNames: Set<String> = emptySet(),
    val overlappingSchedulePairs: List<Pair<String, String>> = emptyList(),
    val exemptAppLimitPackages: Set<String> = emptySet(),
    val exemptGroupPackages: Set<String> = emptySet(),
)

enum class LimitStatus {
    Normal,
    Warning,
    Exceeded,
}

enum class SafeRecoveryStatus {
    Idle,
    Unlocked,
    InvalidPin,
    HardshipLocked,
}

enum class AutoRecoveryStatus {
    Idle,
    RecoveredToSafeMode,
}

enum class PolicySaveStatus {
    Idle,
    Saving,
    Saved,
    InvalidAdminPin,
    BudgetExceeded,
    PolicyConflict,
    HardshipLocked,
    HardshipReflectionRequired,
    HardshipReflectionWaiting,
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
    HardshipLocked,
}

private enum class PairingAction {
    GenerateCode,
    RegisterChild,
}

private fun PairingOperationFailure?.toKoreanPairingMessage(action: PairingAction): String {
    return when (this) {
        PairingOperationFailure.InvalidAdminPin -> "관리 PIN이 올바르지 않습니다."
        PairingOperationFailure.InvalidCode ->
            "연결 코드를 찾을 수 없습니다. 자녀 기기에서 코드를 다시 생성한 뒤 입력해 주세요."
        PairingOperationFailure.ExpiredCode ->
            "연결 코드가 만료되었습니다. 자녀 기기에서 새 코드를 생성해 주세요."
        PairingOperationFailure.AlreadyUsedCode ->
            "이미 다른 부모 기기에서 사용한 코드입니다. 자녀 기기에서 새 코드를 생성해 주세요."
        PairingOperationFailure.CloudUnavailable ->
            "Firebase 연결 설정을 사용할 수 없습니다. 앱 설정을 확인해 주세요."
        PairingOperationFailure.Network -> if (action == PairingAction.GenerateCode) {
            "네트워크가 불안정해 코드를 서버에 등록하지 못했습니다. 잠시 후 다시 생성해 주세요."
        } else {
            "네트워크가 불안정해 연결을 완료하지 못했습니다. 잠시 후 다시 시도해 주세요."
        }
        PairingOperationFailure.PermissionDenied -> if (action == PairingAction.GenerateCode) {
            "연결 코드의 서버 등록 권한을 확인할 수 없습니다. Firestore 규칙을 확인해 주세요."
        } else {
            "연결 권한을 확인할 수 없습니다. 자녀 기기에서 새 코드를 생성한 뒤 다시 시도해 주세요."
        }
        PairingOperationFailure.Authentication ->
            "기기 인증에 실패했습니다. 앱을 다시 실행한 뒤 다시 시도해 주세요."
        PairingOperationFailure.Unknown,
        null -> "연결 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."
    }
}

private fun PairingOperationFailure?.toEnglishPairingMessage(action: PairingAction): String {
    return when (this) {
        PairingOperationFailure.InvalidAdminPin -> "The admin PIN is incorrect."
        PairingOperationFailure.InvalidCode ->
            "Pairing code not found. Generate a new code on the child device and try again."
        PairingOperationFailure.ExpiredCode ->
            "The pairing code expired. Generate a new code on the child device."
        PairingOperationFailure.AlreadyUsedCode ->
            "This code was already used by another parent device. Generate a new code."
        PairingOperationFailure.CloudUnavailable ->
            "Firebase connection settings are unavailable. Check the app configuration."
        PairingOperationFailure.Network -> if (action == PairingAction.GenerateCode) {
            "The code could not be registered because the network is unstable. Try generating it again."
        } else {
            "The connection could not be completed because the network is unstable. Try again shortly."
        }
        PairingOperationFailure.PermissionDenied -> if (action == PairingAction.GenerateCode) {
            "The pairing code could not be registered. Check the Firestore rules."
        } else {
            "Pairing permission could not be verified. Generate a new child code and try again."
        }
        PairingOperationFailure.Authentication ->
            "Device authentication failed. Restart the app and try again."
        PairingOperationFailure.Unknown,
        null -> "An error occurred while pairing. Try again shortly."
    }
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
            policyEnforcementReady &&
            whitelistReady &&
            emergencyUnlockReady
}

class SafeModeViewModel(application: Application) : AndroidViewModel(application) {
    private val parentAccountAuthCoordinator = ParentAccountAuthCoordinatorFactory.create(application)
    val parentAccountAuthState: StateFlow<ParentAccountAuthState> =
        parentAccountAuthCoordinator.state

    private val repository = SettingsRepository(
        application.settingsDataStore,
        ParentRemoteSyncDataSourceFactory.create(application),
    )
    private val usageStatsRepository = UsageStatsRepository(application)
    private val appCatalogRepository = AppCatalogRepository(application)
    private val notificationHelper = UsageNotificationHelper(application)
    private val parentNotificationCoordinator = ParentRemoteNotificationCoordinator(application, repository)
    private val safeRecoveryStatus = MutableStateFlow(SafeRecoveryStatus.Idle)
    private val autoRecoveryStatus = MutableStateFlow(AutoRecoveryStatus.Idle)
    private val usageState = MutableStateFlow(
        UsageState(
            hasUsageAccess = runCatching { usageStatsRepository.hasUsageAccess() }.getOrDefault(false),
            overlayPermissionReady = runCatching { canDrawOverlays() }.getOrDefault(false),
            notificationPermissionReady = runCatching { notificationHelper.canPostNotifications() }.getOrDefault(false),
            notificationAccessReady = true,
            exactAlarmReady = runCatching { canScheduleExactAlarms() }.getOrDefault(false),
            parentRequestNotificationReady = runCatching {
                parentNotificationCoordinator.notificationReadiness().delivered
            }.getOrDefault(false),
            parentRequestNotificationIssue = runCatching {
                parentNotificationCoordinator.notificationReadiness().diagnostic
            }.getOrDefault("notification readiness check failed"),
        ),
    )
    private val policySaveStatus = MutableStateFlow(PolicySaveStatus.Idle)
    private val policyDraftSettings = MutableStateFlow<UsagePolicySettings?>(null)
    private val policyDraftAllowedAppPackages = MutableStateFlow<Set<String>?>(null)
    private val policyDraftAllRestrictionsExemptPackages = MutableStateFlow<Set<String>?>(null)
    private val pinChangeStatus = MutableStateFlow(PinChangeStatus.Idle)
    private val safeModePinStatus = MutableStateFlow(SafeModePinStatus.Idle)
    private val childUsageSnapshots = MutableStateFlow<Map<String, ChildUsageSnapshot>>(emptyMap())
    private val childUsageRefreshRequests = MutableStateFlow<Map<String, ChildUsageRefreshRequest>>(emptyMap())
    private val childImmediateBlocks = MutableStateFlow<Map<String, ImmediateBlockReadState>>(emptyMap())
    private val remoteFamilyState = combine(
        childUsageSnapshots, childImmediateBlocks, repository.immediateBlockState,
    ) { usage, blocks, localBlock ->
        Triple(usage, blocks, localBlock)
    }
    private var refreshUsageJob: Job? = null
    private var alertEvaluationJob: Job? = null
    private var refreshInstalledAppsJob: Job? = null
    private var parentRemoteListenerJob: Job? = null
    private var lastUsageRefreshAtMillis: Long = 0L
    private var lastInstalledAppsRefreshAtMillis: Long = 0L
    private var lastParentForegroundSyncAtMillis: Long = 0L
    private var usageRefreshGeneration: Long = 0L
    private var usageAccessDeniedCount: Int = 0
    @Volatile
    private var monitoringDisclosureAcceptedCached: Boolean = false

    private val notificationSettingsState = combine(
        repository.warningNotificationsEnabled,
        repository.limitNotificationsEnabled,
    ) { warningNotificationsEnabled, limitNotificationsEnabled ->
        NotificationSettingsState(
            warningNotificationsEnabled = warningNotificationsEnabled,
            limitNotificationsEnabled = limitNotificationsEnabled,
        )
    }

    private val parentAndHardshipState = combine(
        repository.parentManagementState,
        repository.hardshipRuntimeState,
        repository.parentNotificationState,
    ) { parentManagementState, hardshipRuntimeState, parentNotificationState ->
        ParentAndHardshipState(
            parentManagementState = parentManagementState,
            hardshipRuntimeState = hardshipRuntimeState,
            parentNotificationState = parentNotificationState,
        )
    }

    private val policyAppListsState = combine(
        repository.allowOnlyAllowedAppPackages,
        repository.allRestrictionsExemptPackages,
    ) { allowOnlyPackages, allRestrictionsExemptPackages ->
        PolicyAppListsState(
            allowOnlyPackages = allowOnlyPackages,
            allRestrictionsExemptPackages = allRestrictionsExemptPackages,
        )
    }

    private val policyAndLogState = combine(
        repository.usagePolicySettings,
        repository.eventLog,
        repository.temporaryUnlockState,
        policyAppListsState,
        parentAndHardshipState,
    ) { usagePolicySettings, eventLog, temporaryUnlockState, appLists, parentAndHardship ->
        PolicyAndLogState(
            usagePolicySettings = usagePolicySettings,
            eventLog = eventLog,
            temporaryUnlockState = temporaryUnlockState,
            allowedAppPackages = appLists.allowOnlyPackages,
            allRestrictionsExemptPackages = appLists.allRestrictionsExemptPackages,
            parentManagementState = parentAndHardship.parentManagementState,
            hardshipRuntimeState = parentAndHardship.hardshipRuntimeState,
            parentNotificationState = parentAndHardship.parentNotificationState,
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
                allRestrictionsExemptPackages = policyAndLog.allRestrictionsExemptPackages,
                parentManagementState = policyAndLog.parentManagementState,
                hardshipRuntimeState = policyAndLog.hardshipRuntimeState,
                parentNotificationState = policyAndLog.parentNotificationState,
                eventLog = policyAndLog.eventLog,
            )
        },
        repository.policySectionExpansionSettings,
    ) { persistedState, expansionSettings ->
        persistedState.copy(policySectionExpansionSettings = expansionSettings)
    }

    private val releaseComplianceState = combine(
        repository.permissionSetupCompletedOnce,
        repository.monitoringDisclosureAccepted,
        repository.securityPinsConfigured,
        repository.childTopAppsSharingEnabled,
    ) { permissionSetupCompletedOnce, monitoringDisclosureAccepted, securityPinsConfigured, childTopAppsSharingEnabled ->
        ReleaseComplianceState(
            permissionSetupCompletedOnce = permissionSetupCompletedOnce,
            monitoringDisclosureAccepted = monitoringDisclosureAccepted,
            securityPinsConfigured = securityPinsConfigured,
            childTopAppsSharingEnabled = childTopAppsSharingEnabled,
        )
    }

    private val persistedState = combine(
        persistedBaseState,
        repository.foregroundDetectionStatus,
        repository.usageMonitorStatus,
        repository.systemHealthStatus,
        releaseComplianceState,
    ) { persistedState, detectionStatus, usageMonitorStatus, systemHealthStatus, releaseCompliance ->
        persistedState.copy(
            foregroundDetectionStatus = detectionStatus?.takeUnless { status ->
                AppVisibility.isHiddenPackage(status.packageName) ||
                    status.packageName in SafetyGate.neverBlockPackages ||
                    status.packageName in persistedState.allRestrictionsExemptPackages
            },
            usageMonitorStatus = usageMonitorStatus,
            systemHealthStatus = systemHealthStatus,
            permissionSetupCompletedOnce = releaseCompliance.permissionSetupCompletedOnce,
            monitoringDisclosureAccepted = releaseCompliance.monitoringDisclosureAccepted,
            securityPinsConfigured = releaseCompliance.securityPinsConfigured,
            childTopAppsSharingEnabled = releaseCompliance.childTopAppsSharingEnabled,
        )
    }

    private val transientCoreState = combine(
        safeRecoveryStatus,
        autoRecoveryStatus,
        usageState,
        policySaveStatus,
        pinChangeStatus,
    ) { unlockStatus, recoveryStatus, usageState, saveStatus, pinStatus ->
        TransientState(
            safeRecoveryStatus = unlockStatus,
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
        policyDraftAllRestrictionsExemptPackages,
    ) { transientState, draftSettings, draftAllowedPackages, draftExemptPackages ->
        transientState.copy(
            policyDraftSettings = draftSettings,
            policyDraftAllowedAppPackages = draftAllowedPackages,
            policyDraftAllRestrictionsExemptPackages = draftExemptPackages,
        )
    }

    val uiState: StateFlow<SafeModeUiState> = combine(
        persistedState,
        transientState,
        remoteFamilyState,
        childUsageRefreshRequests,
    ) { persistedState, transientState, familyState, refreshRequests ->
        val savedSettings = persistedState.usagePolicySettings.normalizedForDraft()
        val draftSettings = (transientState.policyDraftSettings ?: persistedState.usagePolicySettings)
            .normalizedForDraft()
        val savedAllowedPackages = persistedState.allowedAppPackages
        val draftAllowedPackages = transientState.policyDraftAllowedAppPackages ?: savedAllowedPackages
        val savedExemptPackages = persistedState.allRestrictionsExemptPackages
        val draftExemptPackages =
            transientState.policyDraftAllRestrictionsExemptPackages ?: savedExemptPackages
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
        val budgetValidation = draftSettings.policyBudgetValidation(draftExemptPackages)
        val policySummary = buildPolicySummary(
            settings = draftSettings,
            temporaryUnlockState = persistedState.temporaryUnlockState,
            allowedAppPackages = draftAllowedPackages,
            allRestrictionsExemptPackages = draftExemptPackages,
            todayUsage = todayUsage,
            installedApps = transientState.usageState.installedApps,
            hardshipRuntimeState = persistedState.hardshipRuntimeState,
            immediateBlock = familyState.third.takeIf { block ->
                persistedState.parentManagementState.paired &&
                    !persistedState.safeModeEnabled &&
                    persistedState.policyEnforcementEnabled &&
                    persistedState.parentManagementState.deviceRole == ParentDeviceRole.Child &&
                    persistedState.parentManagementState.linkedParentDevices.any { it.parentUid == block.parentUid }
            } ?: ImmediateBlockState(),
        )
        SafeModeUiState(
            safeModeEnabled = persistedState.safeModeEnabled,
            appLanguage = persistedState.appLanguage,
            monitoringDisclosureLoaded = true,
            monitoringDisclosureAccepted = persistedState.monitoringDisclosureAccepted,
            childTopAppsSharingEnabled = persistedState.childTopAppsSharingEnabled,
            securityPinsConfigured = persistedState.securityPinsConfigured,
            policyEnforcementEnabled = persistedState.policyEnforcementEnabled,
            warningNotificationsEnabled = persistedState.warningNotificationsEnabled,
            limitNotificationsEnabled = persistedState.limitNotificationsEnabled,
            safeRecoveryStatus = transientState.safeRecoveryStatus,
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
            allRestrictionsExemptPackages = draftExemptPackages,
            parentManagementState = persistedState.parentManagementState,
            childUsageSnapshots = familyState.first,
            childUsageRefreshRequests = refreshRequests,
            childImmediateBlocks = familyState.second,
            localImmediateBlock = familyState.third,
            parentNotificationState = persistedState.parentNotificationState,
            parentRequestNotificationReady = transientState.usageState.parentRequestNotificationReady,
            parentRequestNotificationIssue = transientState.usageState.parentRequestNotificationIssue,
            hardshipRuntimeState = persistedState.hardshipRuntimeState,
            usagePolicySettings = persistedState.usagePolicySettings,
            temporaryUnlockState = persistedState.temporaryUnlockState,
            policyDraftSettings = draftSettings,
            policyDraftAllowedAppPackages = draftAllowedPackages,
            policyDraftAllRestrictionsExemptPackages = draftExemptPackages,
            policyDraftHasChanges = draftSettings != savedSettings ||
                draftAllowedPackages != savedAllowedPackages ||
                draftExemptPackages != savedExemptPackages,
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
                allRestrictionsExemptPackages = draftExemptPackages,
            ),
            blockDecisionResults = buildBlockDecisionResults(
                safeModeEnabled = persistedState.safeModeEnabled,
                policyEnforcementEnabled = persistedState.policyEnforcementEnabled,
                settings = draftSettings,
                temporaryUnlockState = persistedState.temporaryUnlockState,
                allowedAppPackages = draftAllowedPackages,
                allRestrictionsExemptPackages = draftExemptPackages,
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
            monitoringDisclosureAcceptedCached = repository.monitoringDisclosureAccepted.first()
            if (monitoringDisclosureAcceptedCached) {
                hydrateCachedTodayUsage()
            } else if (repository.policyEnforcementEnabled.first()) {
                repository.setPolicyEnforcementEnabled(false)
                UsageMonitorForegroundService.stop(getApplication<Application>())
            }
            val recovered = repository.markAppStartedAndRecoverIfNeeded()
            if (recovered) {
                autoRecoveryStatus.value = AutoRecoveryStatus.RecoveredToSafeMode
            }
            reconcileHardshipLifecycleAndNotify()
            if (monitoringDisclosureAcceptedCached) {
                refreshForForeground(force = true)
            }
        }
        viewModelScope.launch {
            runDailyRolloverLoop()
        }
        observeScheduleHardshipBoundaries()
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
                    usageState.notificationPermissionReady
                ) {
                    repository.setPermissionSetupCompletedOnce(true)
                }
            }
        }
    }

    private fun observeParentRemoteChanges() {
        viewModelScope.launch {
            combine(
                repository.parentManagementState,
                repository.monitoringDisclosureAccepted,
            ) { state, disclosureAccepted ->
                    ParentListenerTarget(
                        childDeviceIds = if (disclosureAccepted) {
                            parentListenerChildIds(state)
                        } else {
                            emptySet()
                        },
                        deviceRole = state.deviceRole,
                    )
                }
                .distinctUntilChanged()
                .collect { target ->
                    childUsageSnapshots.value = childUsageSnapshots.value.filterKeys { id ->
                        target.deviceRole == ParentDeviceRole.Parent && id in target.childDeviceIds
                    }
                    childUsageRefreshRequests.value = childUsageRefreshRequests.value.filterKeys { id ->
                        target.deviceRole == ParentDeviceRole.Parent && id in target.childDeviceIds
                    }
                    childImmediateBlocks.update { current ->
                        current.filterKeys { id ->
                            target.deviceRole == ParentDeviceRole.Parent && id in target.childDeviceIds
                        }
                    }
                    restartParentRemoteListener(
                        childDeviceIds = target.childDeviceIds,
                        deviceRole = target.deviceRole,
                    )
                }
        }
    }

    private fun restartParentRemoteListener(
        childDeviceIds: Set<String>,
        deviceRole: ParentDeviceRole,
    ) {
        parentRemoteListenerJob?.cancel()
        parentRemoteListenerJob = null
        if (childDeviceIds.isEmpty()) {
            return
        }
        parentRemoteListenerJob = viewModelScope.launch(Dispatchers.IO) {
            if (deviceRole == ParentDeviceRole.Parent) {
                launch {
                    childDeviceIds.forEach { childDeviceId ->
                        refreshChildImmediateBlock(childDeviceId)
                    }
                }
            }
            repository.observeParentRemoteChanges(childDeviceIds, deviceRole)
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
                .collect { change ->
                    if (change.type == ParentRemoteChangeType.ChildUsageSnapshot &&
                        deviceRole == ParentDeviceRole.Parent
                    ) {
                        childUsageSnapshots.value = if (change.usageSnapshot == null) {
                            childUsageSnapshots.value - change.childDeviceId
                        } else {
                            childUsageSnapshots.value +
                                (change.childDeviceId to change.usageSnapshot)
                        }
                    } else if (change.type == ParentRemoteChangeType.ChildUsageRefresh) {
                        if (deviceRole == ParentDeviceRole.Parent) {
                            childUsageRefreshRequests.value = if (change.usageRefreshRequest == null) {
                                childUsageRefreshRequests.value - change.childDeviceId
                            } else {
                                childUsageRefreshRequests.value +
                                    (change.childDeviceId to change.usageRefreshRequest)
                            }
                        } else if (change.usageRefreshRequest?.isPending(System.currentTimeMillis()) == true) {
                            viewModelScope.launch(Dispatchers.IO) {
                                ChildUsageSnapshotPublisher.respondToRefreshIfPending(getApplication<Application>())
                            }
                        }
                    } else if (change.type == ParentRemoteChangeType.ImmediateBlock) {
                        if (deviceRole == ParentDeviceRole.Parent) {
                            val state = if (change.immediateBlockDocumentExists &&
                                change.immediateBlockState == null
                            ) ImmediateBlockReadState.Failed
                            else ImmediateBlockReadState.Known(change.immediateBlockState)
                            childImmediateBlocks.update { current ->
                                val merged = if (state == ImmediateBlockReadState.Failed) state
                                else {
                                    mergeImmediateBlockReadState(current[change.childDeviceId], state)
                                }
                                current + (change.childDeviceId to merged)
                            }
                        } else {
                            repository.syncImmediateBlock()
                        }
                    } else {
                        synchronizeParentStateFromListener()
                    }
                }
        }
    }

    private suspend fun synchronizeParentStateFromListener() {
        for (attempt in 0 until PARENT_LISTENER_SYNC_RETRY_COUNT) {
            val result = parentNotificationCoordinator.synchronizeAndNotify()
            if (!result.retryable) {
                break
            }
            if (attempt < PARENT_LISTENER_SYNC_RETRY_COUNT - 1) {
                delay(PARENT_LISTENER_SYNC_RETRY_DELAY_MILLIS)
            }
        }
    }

    fun refreshForForeground(
        force: Boolean = false,
        settleUsageEvents: Boolean = false,
    ) {
        if (!monitoringDisclosureAcceptedCached) {
            return
        }
        viewModelScope.launch {
            reconcileHardshipLifecycleAndNotify()
        }
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
            refreshUsageStatsInternal(
                force = force,
                includeStatistics = false,
                initialDelayMillis = if (settleUsageEvents) {
                    FOREGROUND_USAGE_EVENT_SETTLE_MILLIS
                } else {
                    0L
                },
            )
        }
        if (force || now - lastParentForegroundSyncAtMillis >= PARENT_SYNC_FOREGROUND_THROTTLE_MILLIS) {
            lastParentForegroundSyncAtMillis = now
            viewModelScope.launch {
                val parentState = repository.parentManagementState.first()
                val hasSyncTarget = parentState.childDeviceId.isNotBlank() ||
                    parentState.linkedChildDevices.any { child -> child.childDeviceId.isNotBlank() }
                if (parentState.paired && hasSyncTarget) {
                    parentNotificationCoordinator.synchronizeAndNotify()
                }
            }
        }
    }

    private fun refreshSystemPermissionStates() {
        val parentNotificationReadiness = runCatching {
            parentNotificationCoordinator.notificationReadiness()
        }.getOrNull()
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
            notificationAccessReady = true,
            exactAlarmReady = runCatching {
                canScheduleExactAlarms()
            }.getOrDefault(false),
            parentRequestNotificationReady = parentNotificationReadiness?.delivered == true,
            parentRequestNotificationIssue = parentNotificationReadiness
                ?.diagnostic
                .orEmpty()
                .ifBlank {
                    if (parentNotificationReadiness == null) {
                        "notification readiness check failed"
                    } else {
                        ""
                    }
                },
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
                runCatching { reconcileHardshipLifecycleAndNotify() }
                resetUsageIfDateChanged()
                refreshForForeground(force = true)
            }
        }
    }

    private suspend fun reconcileHardshipLifecycleAndNotify() {
        val lifecycle = repository.reconcileHardshipLifecycle()
        policyDraftSettings.value?.let { currentDraft ->
            var nextDraft = currentDraft
            if (lifecycle.dailyHardshipCleared) {
                nextDraft = nextDraft.copy(
                    dailyHardshipLevel = HardshipLevel.Off,
                    appGroupsHardshipLevel = HardshipLevel.Off,
                    appLimitsHardshipLevel = HardshipLevel.Off,
                    appGroups = nextDraft.normalizedAppGroups()
                        .map { group -> group.copy(hardshipLevel = HardshipLevel.Off) }
                        .toAppGroupsEncoded(),
                    appLimitHardshipLevels = "",
                    allowOnlyHardshipLevel = HardshipLevel.Off,
                    allowOnlyHardshipEndAtMillis = 0L,
                )
            }
            if (lifecycle.endedScheduleIds.isNotEmpty()) {
                nextDraft = nextDraft.copy(
                    scheduleTemplates = nextDraft.normalizedScheduleTemplates()
                        .map { schedule ->
                            if (schedule.id in lifecycle.endedScheduleIds) {
                                schedule.copy(
                                    hardshipLevel = HardshipLevel.Off,
                                    hardshipEndAtMillis = 0L,
                                )
                            } else {
                                schedule
                            }
                        }
                        .toScheduleTemplatesEncoded(),
                )
            }
            if (nextDraft != currentDraft) {
                policyDraftSettings.value = nextDraft.normalizedForDraft()
            }
        }
        if (lifecycle.startedSchedules.isEmpty()) return
        val language = repository.appLanguage.first()
        lifecycle.startedSchedules.forEach { schedule ->
            val title = if (language == AppLanguage.Korean) {
                "${schedule.scheduleName} · 고행 ${schedule.level.storageValue}단계"
            } else {
                "${schedule.scheduleName} · Hardship level ${schedule.level.storageValue}"
            }
            val message = if (language == AppLanguage.Korean) {
                "스케줄 고행 모드가 시작되었습니다. 스케줄 종료 시 자동으로 해제됩니다."
            } else {
                "Schedule hardship has started and will unlock automatically when this schedule ends."
            }
            notificationHelper.showPolicyAlert(title, message)
        }
    }

    private fun observeScheduleHardshipBoundaries() {
        viewModelScope.launch {
            repository.usagePolicySettings
                .map { settings ->
                    if (!settings.scheduleBlockingEnabled) return@map 0L
                    val now = LocalDateTime.now()
                    settings.normalizedScheduleTemplates()
                        .filter { schedule -> schedule.hardshipLevel != HardshipLevel.Off }
                        .mapNotNull { schedule ->
                            if (schedule.isActiveAt(now)) {
                                schedule.hardshipEndAtMillis.takeIf { endAt -> endAt > 0L }
                                    ?: schedule.currentOccurrenceEndMillis(now)
                            } else {
                                schedule.nextOccurrenceStartMillis(now).takeIf { startAt -> startAt > 0L }
                            }
                        }
                        .minOrNull()
                        ?: 0L
                }
                .distinctUntilChanged()
                .collectLatest { boundaryMillis ->
                    if (boundaryMillis <= 0L) return@collectLatest
                    delay(
                        (boundaryMillis - System.currentTimeMillis())
                            .coerceAtLeast(0L) + HARDSHIP_BOUNDARY_SETTLE_MILLIS,
                    )
                    reconcileHardshipLifecycleAndNotify()
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

    fun submitSafeRecoveryAdminPin(pin: String) {
        viewModelScope.launch {
            if (repository.hasActiveLevel3Hardship()) {
                repository.addEvent(
                    EventLogType.Warning,
                    "Safe Recovery blocked by active hardship level 3; use Emergency Pass",
                )
                safeRecoveryStatus.value = SafeRecoveryStatus.HardshipLocked
                return@launch
            }
            safeRecoveryStatus.value = if (repository.safeRecovery(pin)) {
                SafeRecoveryStatus.Unlocked
            } else {
                SafeRecoveryStatus.InvalidPin
            }
        }
    }

    fun clearSafeRecoveryStatus() {
        safeRecoveryStatus.value = SafeRecoveryStatus.Idle
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

    private fun refreshUsageStatsInternal(
        force: Boolean = false,
        includeStatistics: Boolean = false,
        initialDelayMillis: Long = 0L,
    ) {
        if (refreshUsageJob?.isActive == true && !force) {
            return
        }
        if (force) {
            refreshUsageJob?.cancel()
        }
        lastUsageRefreshAtMillis = SystemClock.elapsedRealtime()
        val generation = ++usageRefreshGeneration
        // Mark the overview as refreshing before the IO coroutine is dispatched. Without this,
        // the first frame after returning to the app can briefly render the stale foreground app
        // as if it were the latest result, then visibly jump a few moments later.
        val stateBeforeRefresh = usageState.value
        usageState.value = stateBeforeRefresh.copy(
            dateKey = currentUsageDateKey(),
            usageAccessChecking = stateBeforeRefresh.hasUsageAccess || stateBeforeRefresh.todayUsage.isNotEmpty(),
            statisticsRefreshing = includeStatistics,
        )
        refreshUsageJob = viewModelScope.launch(Dispatchers.IO) {
            fun isCurrentRefresh(): Boolean = generation == usageRefreshGeneration

            try {
                if (initialDelayMillis > 0L) {
                    delay(initialDelayMillis)
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

                val appliedUiState = uiState.value
                usageStatsRepository.rememberTodayDailyGoal(
                    goalMinutes = appliedUiState.usagePolicySettings.todayLimitMinutesOrNull().takeIf {
                        !appliedUiState.safeModeEnabled && appliedUiState.policyEnforcementEnabled
                    },
                )

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
                    val todayStartMillis = currentLocalDayStartMillis()
                    val todayTotalMillis = todayUsage.sumOf { appUsage -> appUsage.totalTimeMillis }
                    val alignedDailyUsage = dailyUsage?.map { daily ->
                        if (daily.dayStartMillis == todayStartMillis) {
                            daily.copy(
                                totalTimeMillis = todayTotalMillis,
                                hasRecordedData = true,
                            )
                        } else {
                            daily
                        }
                    }
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
                        dailyUsage = alignedDailyUsage
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
        policyDraftAllRestrictionsExemptPackages.value = null
        policySaveStatus.value = PolicySaveStatus.Idle
    }

    fun savePolicyDraft(adminPin: String) {
        viewModelScope.launch {
            policySaveStatus.value = PolicySaveStatus.Saving
            val rawDraftSettings = (policyDraftSettings.value ?: uiState.value.usagePolicySettings)
                .normalizedForDraft()
            val draftExemptPackages = policyDraftAllRestrictionsExemptPackages.value
                ?: uiState.value.allRestrictionsExemptPackages
            val effectiveExemptPackages =
                SafetyGate.expandedUserAllowedPackages(draftExemptPackages)
            val draftSettings = rawDraftSettings.copy(
                scheduleTemplates = rawDraftSettings.normalizedScheduleTemplates()
                    .map { schedule ->
                        schedule.copy(
                            allowedPackageNames = schedule.allowedPackageNames -
                                SafetyGate.neverBlockPackages -
                                effectiveExemptPackages,
                        )
                    }
                    .toScheduleTemplatesEncoded(),
            ).normalizedForDraft()
            val draftAllowedPackages = (
                policyDraftAllowedAppPackages.value ?: uiState.value.allowedAppPackages
            ) - effectiveExemptPackages
            if (draftSettings.policyBudgetValidation(draftExemptPackages).hasOverflow) {
                policySaveStatus.value = PolicySaveStatus.PolicyConflict
                return@launch
            }
            if (
                repository.isPolicyChangeBlockedByHardship(
                    settings = draftSettings,
                    allowedPackages = draftAllowedPackages,
                    allRestrictionsExemptPackages = draftExemptPackages,
                )
            ) {
                policySaveStatus.value = PolicySaveStatus.HardshipLocked
                return@launch
            }

            val saveResult = repository.saveUsagePolicyConfigurationResult(
                settings = draftSettings,
                allowOnlyPackages = draftAllowedPackages,
                allRestrictionsExemptPackages = draftExemptPackages,
                adminPin = adminPin,
            )
            policySaveStatus.value = if (saveResult == PolicyConfigurationSaveResult.Saved) {
                policyDraftSettings.value = draftSettings
                policyDraftAllowedAppPackages.value = draftAllowedPackages
                policyDraftAllRestrictionsExemptPackages.value = draftExemptPackages
                viewModelScope.launch {
                    repository.usagePolicySettings.first { savedSettings ->
                        savedSettings.normalizedForDraft() == draftSettings
                    }
                    repository.allowOnlyAllowedAppPackages.first { savedAllowedPackages ->
                        savedAllowedPackages == draftAllowedPackages
                    }
                    repository.allRestrictionsExemptPackages.first { savedExemptPackages ->
                        savedExemptPackages == draftExemptPackages
                    }
                    if (policyDraftSettings.value == draftSettings) {
                        policyDraftSettings.value = null
                    }
                    if (policyDraftAllowedAppPackages.value == draftAllowedPackages) {
                        policyDraftAllowedAppPackages.value = null
                    }
                    if (policyDraftAllRestrictionsExemptPackages.value == draftExemptPackages) {
                        policyDraftAllRestrictionsExemptPackages.value = null
                    }
                }
                reconcileHardshipLifecycleAndNotify()
                evaluatePolicyAlertsAsync()
                PolicySaveStatus.Saved
            } else {
                when (saveResult) {
                    PolicyConfigurationSaveResult.Saved -> PolicySaveStatus.Saved
                    PolicyConfigurationSaveResult.InvalidAdminPin -> PolicySaveStatus.InvalidAdminPin
                    PolicyConfigurationSaveResult.StructuralConflict -> PolicySaveStatus.PolicyConflict
                    PolicyConfigurationSaveResult.HardshipLocked -> PolicySaveStatus.HardshipLocked
                    PolicyConfigurationSaveResult.HardshipReflectionRequired ->
                        PolicySaveStatus.HardshipReflectionRequired
                    PolicyConfigurationSaveResult.HardshipReflectionWaiting ->
                        PolicySaveStatus.HardshipReflectionWaiting
                }
            }
        }
    }

    fun startHardshipConfigurationReflection(policyKey: HardshipPolicyKey) {
        viewModelScope.launch {
            policySaveStatus.value = PolicySaveStatus.Idle
            repository.startHardshipConfigurationReflection(policyKey)
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
            val currentState = repository.parentManagementState.first()
            if (currentState.deviceRole != role && currentState.paired) {
                val korean = repository.appLanguage.first() == AppLanguage.Korean
                Toast.makeText(
                    getApplication<Application>(),
                    if (korean) {
                        "연결된 기기를 먼저 해제한 뒤 기기 역할을 변경해 주세요."
                    } else {
                        "Unlink connected devices before changing the device role."
                    },
                    Toast.LENGTH_LONG,
                ).show()
                return@launch
            }
            val success = repository.setParentDeviceRole(role, adminPin)
            if (success && role == ParentDeviceRole.Child) {
                parentAccountAuthCoordinator.switchToChildAnonymousIdentity()
                PushTokenRegistrationWorker.schedule(getApplication<Application>())
            }
            val korean = repository.appLanguage.first() == AppLanguage.Korean
            val message = when {
                success && korean && role == ParentDeviceRole.Parent -> "부모 기기 모드로 변경되었습니다."
                success && korean -> "자녀 기기 모드로 변경되었습니다."
                success && role == ParentDeviceRole.Parent -> "Changed to parent device mode"
                success -> "Changed to child device mode"
                korean -> "기기 모드를 변경하지 못했습니다. 관리 PIN을 확인해 주세요."
                else -> "Could not change device mode. Check the admin PIN."
            }
            Toast.makeText(getApplication<Application>(), message, Toast.LENGTH_LONG).show()
        }
    }

    fun setParentProfileName(profileName: String) {
        viewModelScope.launch {
            if (repository.setParentProfileName(profileName)) {
                parentNotificationCoordinator.synchronizeAndNotify()
            }
        }
    }

    fun generateChildPairingCode(adminPin: String) {
        viewModelScope.launch {
            showPairingOperationResult(
                result = repository.generateChildPairingCode(adminPin),
                action = PairingAction.GenerateCode,
            )
        }
    }

    fun registerChildPairingCode(pairingCode: String, childDeviceName: String, adminPin: String) {
        viewModelScope.launch {
            if (!parentAccountAuthState.value.recoverable) {
                val korean = repository.appLanguage.first() == AppLanguage.Korean
                Toast.makeText(
                    getApplication<Application>(),
                    if (korean) {
                        "자녀 기기를 연결하려면 먼저 부모 Google 계정으로 로그인해 주세요."
                    } else {
                        "Sign in with a parent Google account before linking a child device."
                    },
                    Toast.LENGTH_LONG,
                ).show()
                return@launch
            }
            val result = repository.registerChildPairingCode(
                pairingCode = pairingCode,
                childDeviceName = childDeviceName,
                adminPin = adminPin,
            )
            if (result.success) {
                parentNotificationCoordinator.synchronizeAndNotify()
            }
            showPairingOperationResult(
                result = result,
                action = PairingAction.RegisterChild,
            )
        }
    }

    fun signInParentWithGoogleIdToken(idToken: String) {
        viewModelScope.launch {
            val result = parentAccountAuthCoordinator.signInParentWithGoogleIdToken(idToken)
            val korean = repository.appLanguage.first() == AppLanguage.Korean
            if (!result.success) {
                val message = when (result.failure) {
                    ParentAccountAuthFailure.Network -> if (korean) {
                        "네트워크가 불안정해 Google 로그인을 완료하지 못했습니다. 다시 시도해 주세요."
                    } else {
                        "The network is unstable. Try Google sign-in again."
                    }
                    ParentAccountAuthFailure.Configuration -> if (korean) {
                        "Google 로그인 설정이 완료되지 않았습니다. Firebase OAuth 설정을 확인해 주세요."
                    } else {
                        "Google sign-in is not configured. Check Firebase OAuth settings."
                    }
                    else -> if (korean) {
                        "Google 로그인을 완료하지 못했습니다. 다시 시도해 주세요."
                    } else {
                        "Google sign-in could not be completed. Try again."
                    }
                }
                Toast.makeText(getApplication<Application>(), message, Toast.LENGTH_LONG).show()
                return@launch
            }

            val authState = parentAccountAuthState.value
            val displayName = authState.displayName
                .ifBlank { authState.email.substringBefore('@') }
                .ifBlank { "Parent device" }
            val recovered = repository.recoverParentAccountLinks(
                parentUid = authState.uid,
                parentDisplayName = displayName,
            )
            if (recovered) {
                parentNotificationCoordinator.synchronizeAndNotify()
                PushTokenRegistrationWorker.schedule(getApplication<Application>())
            }
            Toast.makeText(
                getApplication<Application>(),
                if (recovered && korean) {
                    "부모 Google 계정이 연결되고 기존 자녀 목록을 확인했습니다."
                } else if (recovered) {
                    "Parent Google account connected and child links checked."
                } else if (korean) {
                    "로그인은 완료되었지만 자녀 목록을 복구하지 못했습니다. 네트워크를 확인해 주세요."
                } else {
                    "Signed in, but child links could not be restored. Check the network."
                },
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    fun reportParentGoogleSignInFailure(message: String) {
        parentAccountAuthCoordinator.recordUiFailure(message)
        viewModelScope.launch {
            val korean = repository.appLanguage.first() == AppLanguage.Korean
            Toast.makeText(
                getApplication<Application>(),
                if (korean) {
                    "Google 로그인 창을 열지 못했습니다. OAuth 설정과 네트워크를 확인해 주세요."
                } else {
                    "Could not open Google sign-in. Check OAuth settings and the network."
                },
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    fun deleteCurrentAccountAndCloudData(adminPin: String) {
        viewModelScope.launch {
            val deleted = repository.deleteCurrentAccountAndCloudData(adminPin)
            val korean = repository.appLanguage.first() == AppLanguage.Korean
            if (deleted) {
                parentAccountAuthCoordinator.switchToChildAnonymousIdentity()
                PushTokenRegistrationWorker.schedule(getApplication<Application>())
            }
            Toast.makeText(
                getApplication<Application>(),
                if (deleted && korean) {
                    "계정과 클라우드 연결 데이터가 삭제되었습니다."
                } else if (deleted) {
                    "Account and cloud connection data deleted."
                } else if (korean) {
                    "삭제하지 못했습니다. 관리 PIN, 네트워크와 App Check 설정을 확인해 주세요."
                } else {
                    "Deletion failed. Check the admin PIN, network, and App Check setup."
                },
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private suspend fun showPairingOperationResult(
        result: PairingOperationResult,
        action: PairingAction,
    ) {
        val korean = repository.appLanguage.first() == AppLanguage.Korean
        val message = when {
            result.success && action == PairingAction.GenerateCode && korean ->
                "연결 코드가 서버에 등록되었습니다."
            result.success && action == PairingAction.GenerateCode ->
                "Pairing code registered"
            result.success && korean ->
                "자녀 기기가 연결되었습니다."
            result.success ->
                "Child device connected"
            korean -> result.failure.toKoreanPairingMessage(action)
            else -> result.failure.toEnglishPairingMessage(action)
        }
        Toast.makeText(getApplication<Application>(), message, Toast.LENGTH_LONG).show()
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
            val parentState = repository.parentManagementState.first()
            if (parentState.deviceRole == ParentDeviceRole.Child) {
                repository.syncImmediateBlock()
                runCatching {
                    withContext(Dispatchers.IO) {
                        val response = ChildUsageSnapshotPublisher.respondToRefreshIfPending(
                            getApplication<Application>(),
                        )
                        if (response != ParentRemoteSyncResult.Success) {
                            ChildUsageSnapshotPublisher.publishIfDue(getApplication<Application>(), force = true)
                        }
                    }
                }.onFailure { error ->
                    Log.w("STM-ParentSync", "Child usage snapshot publish failed", error)
                }
            }
            parentNotificationCoordinator.synchronizeAndNotify()
            if (parentState.deviceRole == ParentDeviceRole.Parent) {
                var refreshFailed = false
                parentListenerChildIds(parentState).forEach { childDeviceId ->
                    repository.requestChildUsageRefresh(childDeviceId)
                        .onSuccess { request ->
                            childUsageRefreshRequests.value = childUsageRefreshRequests.value +
                                (childDeviceId to request)
                        }
                        .onFailure { error ->
                            refreshFailed = true
                            Log.w("STM-ParentSync", "Child usage refresh request failed", error)
                        }
                    repository.fetchChildUsageSnapshot(childDeviceId)?.let { snapshot ->
                        childUsageSnapshots.value = childUsageSnapshots.value +
                            (childDeviceId to snapshot)
                    }
                    refreshChildImmediateBlock(childDeviceId)
                }
                if (refreshFailed) {
                    Toast.makeText(
                        getApplication<Application>(),
                        if (repository.appLanguage.first() == AppLanguage.Korean) {
                            "자녀 사용 현황 새로고침 요청에 실패했습니다"
                        } else {
                            "Could not request fresh child usage"
                        },
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    private suspend fun refreshChildImmediateBlock(childDeviceId: String): Boolean {
        var success = false
        repository.fetchImmediateBlock(childDeviceId)
            .onSuccess { block ->
                success = true
                childImmediateBlocks.update { current ->
                    current + (childDeviceId to mergeImmediateBlockReadState(
                        current[childDeviceId], ImmediateBlockReadState.Known(block),
                    ))
                }
            }
            .onFailure { error ->
                // Do not turn an unreadable active order into a false "Block now" action.
                if (childImmediateBlocks.value[childDeviceId] !is ImmediateBlockReadState.Known) {
                    childImmediateBlocks.update { current ->
                        if (current[childDeviceId] is ImmediateBlockReadState.Known) current
                        else current + (childDeviceId to ImmediateBlockReadState.Failed)
                    }
                }
                Log.w("STM-ParentSync", "Immediate block state unavailable for $childDeviceId", error)
            }
        return success
    }

    fun checkImmediateBlock(childDeviceId: String) {
        viewModelScope.launch {
            childImmediateBlocks.update { current -> current - childDeviceId }
            if (!refreshChildImmediateBlock(childDeviceId)) {
                Toast.makeText(
                    getApplication<Application>(),
                    if (repository.appLanguage.first() == AppLanguage.Korean) {
                        "차단 상태를 확인하지 못했습니다. 연결과 가족 계정을 확인해 주세요"
                    } else {
                        "Could not check block status. Check connection and family account"
                    },
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    fun startImmediateBlock(childDeviceId: String, durationMinutes: Int, adminPin: String) {
        viewModelScope.launch {
            val verified = repository.verifyAdminPin(adminPin)
            val result = if (verified) repository.issueImmediateBlock(childDeviceId, durationMinutes)
                else ParentRemoteSyncResult.Failed("Invalid Admin PIN")
            if (result == ParentRemoteSyncResult.Success) {
                refreshChildImmediateBlock(childDeviceId)
            }
            showParentOperationResult(
                success = result == ParentRemoteSyncResult.Success,
                successKorean = "차단 요청을 보냈습니다. 자녀 기기 적용을 확인 중입니다",
                failureKorean = if (!verified) "관리 PIN이 올바르지 않습니다" else "차단 요청 실패: 연결과 권한을 확인해 주세요",
                successEnglish = "Block request sent. Waiting for the child device",
                failureEnglish = if (!verified) "Incorrect Admin PIN" else "Could not send block request. Check connection and permissions",
            )
        }
    }

    fun stopImmediateBlock(childDeviceId: String, requestId: String, adminPin: String) {
        viewModelScope.launch {
            val verified = repository.verifyAdminPin(adminPin)
            val result = if (verified) repository.revokeImmediateBlock(childDeviceId, requestId)
                else ParentRemoteSyncResult.Failed("Invalid Admin PIN")
            if (result == ParentRemoteSyncResult.Success) {
                refreshChildImmediateBlock(childDeviceId)
            }
            showParentOperationResult(
                success = result == ParentRemoteSyncResult.Success,
                successKorean = "차단 종료 요청을 보냈습니다",
                failureKorean = if (!verified) "관리 PIN이 올바르지 않습니다" else "차단 종료 실패: 연결과 권한을 확인해 주세요",
                successEnglish = "Block stop request sent",
                failureEnglish = if (!verified) "Incorrect Admin PIN" else "Could not stop block. Check connection and permissions",
            )
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
            if (enabled && repository.hasActiveLevel3Hardship()) {
                rejectLevel3EnforcementBypass("Safe Mode enable blocked by active hardship level 3")
                return@launch
            }
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
            if (repository.hasActiveLevel3Hardship()) {
                rejectLevel3EnforcementBypass("Safe Mode enable blocked by active hardship level 3")
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
            if (repository.hasActiveLevel3Hardship()) {
                rejectLevel3EnforcementBypass("Policy enforcement disable blocked by active hardship level 3")
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

    private suspend fun rejectLevel3EnforcementBypass(eventMessage: String) {
        repository.addEvent(EventLogType.Warning, eventMessage)
        safeModePinStatus.value = SafeModePinStatus.HardshipLocked
        val message = if (repository.appLanguage.first() == AppLanguage.Korean) {
            "고행 3단계가 적용 중이라 다음 날까지 감시를 중지할 수 없습니다."
        } else {
            "Hardship level 3 is active. Monitoring cannot be stopped until the next day."
        }
        Toast.makeText(getApplication<Application>(), message, Toast.LENGTH_LONG).show()
    }

    fun setPolicyEnforcementEnabled(enabled: Boolean) {
        viewModelScope.launch {
            safeModePinStatus.value = SafeModePinStatus.Idle
            if (enabled && !repository.monitoringDisclosureAccepted.first()) {
                repository.setPolicyEnforcementEnabled(false)
                repository.addEvent(
                    EventLogType.Safety,
                    "Policy enforcement requires monitoring disclosure acceptance",
                )
                return@launch
            }
            if (!enabled && repository.hasActiveLevel3Hardship()) {
                rejectLevel3EnforcementBypass("Policy enforcement disable blocked by active hardship level 3")
                return@launch
            }
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
                UsageMonitorForegroundService.scheduleRecoveryAlarm(
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

    fun acceptMonitoringDisclosure() {
        viewModelScope.launch {
            repository.acceptMonitoringDisclosure()
            monitoringDisclosureAcceptedCached = true
            hydrateCachedTodayUsage()
            refreshForForeground(force = true)
        }
    }

    fun setChildTopAppsSharingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (repository.parentManagementState.first().deviceRole != ParentDeviceRole.Child) return@launch
            repository.setChildTopAppsSharingEnabled(enabled)
            runCatching {
                ChildUsageSnapshotPublisher.publishIfDue(getApplication<Application>(), force = true)
            }
        }
    }

    fun configureInitialAdminPin(adminPin: String) {
        viewModelScope.launch {
            repository.configureInitialAdminPin(adminPin)
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
        val exemptPackages = policyDraftAllRestrictionsExemptPackages.value
            ?: uiState.value.allRestrictionsExemptPackages
        val unrestrictedPackages = SafetyGate.expandedUserAllowedPackages(exemptPackages)
        policyDraftAllowedAppPackages.value = packageNames
            .map { packageName -> packageName.trim() }
            .filter { packageName ->
                packageName.isNotBlank() &&
                    packageName !in SafetyGate.neverBlockPackages &&
                    packageName !in unrestrictedPackages
            }
            .toSet()
        policySaveStatus.value = PolicySaveStatus.Idle
    }

    fun setAllRestrictionsExemptPackages(packageNames: Set<String>) {
        val cleanExemptPackages = packageNames
            .map { packageName -> packageName.trim() }
            .filter { packageName ->
                packageName.isNotBlank() && packageName !in SafetyGate.neverBlockPackages
            }
            .toSet()
        val unrestrictedPackages = SafetyGate.expandedUserAllowedPackages(cleanExemptPackages)
        val currentAllowOnlyPackages = policyDraftAllowedAppPackages.value
            ?: uiState.value.allowedAppPackages
        val currentSettings = policyDraftSettings.value ?: uiState.value.policyDraftSettings
        policyDraftAllRestrictionsExemptPackages.value = cleanExemptPackages
        policyDraftAllowedAppPackages.value =
            currentAllowOnlyPackages - unrestrictedPackages
        policyDraftSettings.value = currentSettings.copy(
            scheduleTemplates = currentSettings.normalizedScheduleTemplates()
                .map { schedule ->
                    schedule.copy(
                        allowedPackageNames = schedule.allowedPackageNames - unrestrictedPackages,
                    )
                }
                .toScheduleTemplatesEncoded(),
        ).normalizedForDraft()
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

    fun activateKillSwitch(adminPin: String) {
        viewModelScope.launch {
            if (repository.hasActiveLevel3Hardship()) {
                safeRecoveryStatus.value = SafeRecoveryStatus.HardshipLocked
                return@launch
            }
            if (!repository.activateKillSwitch(adminPin)) {
                safeRecoveryStatus.value = SafeRecoveryStatus.InvalidPin
                return@launch
            }
            policyDraftSettings.value = null
            policySaveStatus.value = PolicySaveStatus.Idle
            safeRecoveryStatus.value = SafeRecoveryStatus.Unlocked
        }
    }

    private fun buildPolicySummary(
        settings: UsagePolicySettings,
        temporaryUnlockState: TemporaryUnlockState,
        allowedAppPackages: Set<String>,
        allRestrictionsExemptPackages: Set<String>,
        todayUsage: List<AppUsageInfo>,
        installedApps: List<InstalledAppInfo>,
        hardshipRuntimeState: HardshipRuntimeState,
        immediateBlock: ImmediateBlockState,
    ): PolicySummary {
        val todayTemporaryUnlockState = temporaryUnlockState.forToday()
        val nowMillis = System.currentTimeMillis()
        val usageByPackage = todayUsage.associateBy { appUsage -> appUsage.packageName }
        val appNameByPackage = installedApps.associate { app -> app.packageName to app.appName }
        val configuredAppLimits = settings.appLimitMap()
        val appLimits = settings.activeAppLimitMap()
        val appLimitActiveDays = settings.appLimitActiveDayMap()
        val enforcementExcludedPackages =
            SafetyGate.neverBlockPackages +
                SafetyGate.expandedUserAllowedPackages(allRestrictionsExemptPackages)
        val groupSummaries = settings.normalizedAppGroups().map { group ->
            val groupLimitMinutes = group.limitMinutesOrNull()
            val groupActiveToday = group.appliesOn(currentPolicyDayOfWeek())
            val enforcedGroupPackages =
                group.packageNames - enforcementExcludedPackages
            val groupExtraMinutes = enforcedGroupPackages
                .sumOf { packageName -> todayTemporaryUnlockState.packageAllowances[packageName]?.extraMinutes ?: 0 }
            val usedMinutes = enforcedGroupPackages
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
                        excludedFromRestrictions = packageName in enforcementExcludedPackages,
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
                limitMinutes = groupLimitMinutes ?: 0,
                limitEnabled = groupActiveToday && groupLimitMinutes != null,
                limitConfigured = groupLimitMinutes != null,
                activeDays = group.activeDays.normalizedPolicyDays(),
                activeToday = groupActiveToday,
                extraMinutes = groupExtraMinutes,
                excludedPackageCount = (group.packageNames intersect enforcementExcludedPackages).size,
                status = if (!groupActiveToday || groupLimitMinutes == null) {
                    LimitStatus.Normal
                } else {
                    calculateLimitStatus(usedMinutes, groupLimitMinutes + groupExtraMinutes)
                },
                appUsages = appUsages,
            )
        }

        val actualTotalUsedMinutes = todayUsage
            .sumOf { appUsage -> appUsage.totalTimeMillis }
            .toDisplayMinutes()
        val totalUsedMinutes = todayUsage
            .filter { appUsage -> appUsage.packageName !in enforcementExcludedPackages }
            .sumOf { appUsage -> appUsage.totalTimeMillis }
            .toDisplayMinutes()
        val totalLimitMinutesOrNull = settings.todayLimitMinutesOrNull()
        val totalLimitMinutes = totalLimitMinutesOrNull ?: 0
        val totalExtraMinutes = todayTemporaryUnlockState.totalExtraMinutes
        val primaryGroup = groupSummaries.firstOrNull { summary -> summary.activeToday }
        val groupUsedMinutes = primaryGroup?.usedMinutes ?: 0
        val groupLimitMinutes = primaryGroup?.limitMinutes ?: 0
        val totalStatus = if (todayTemporaryUnlockState.totalUnlockedForToday || totalLimitMinutesOrNull == null) {
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
            allRestrictionsExemptPackages = allRestrictionsExemptPackages,
            temporarilyAllowedPackages = temporarilyAllowedPackages,
        )
        val activeScheduleSummary = scheduleSummaries.firstOrNull { summary -> summary.activeNow }
        val nextScheduleSummary = scheduleSummaries
            .filter { summary -> !summary.activeNow && summary.minutesUntilStart != null }
            .minByOrNull { summary -> summary.minutesUntilStart ?: Int.MAX_VALUE }
        val allowOnlyAllowedAppCount = ((allowedAppPackages + temporarilyAllowedPackages) -
            SafetyGate.neverBlockPackages).size
        val groupStatus = primaryGroup?.status ?: LimitStatus.Normal
        val appLimitSummaries = configuredAppLimits.map { (packageName, limitMinutes) ->
                val usage = usageByPackage[packageName]
                val activeDays = appLimitActiveDays[packageName]?.normalizedPolicyDays() ?: (1..7).toSet()
                val activeToday = currentPolicyDayOfWeek() in activeDays
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
                    activeDays = activeDays,
                    activeToday = activeToday,
                    extraMinutes = extraMinutes,
                    unlockedForToday = unlockedForToday,
                    temporaryRemainingMinutes = temporaryRemainingMinutes,
                    excludedFromRestrictions = packageName in enforcementExcludedPackages,
                    status = if (!activeToday || unlockedForToday) {
                        LimitStatus.Normal
                    } else if (packageName in enforcementExcludedPackages) {
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

        val activeSchedules = settings.activeScheduleTemplates()
        val installedCandidatePackages = installedApps
            .map { app -> app.packageName }
            .filter { packageName -> packageName.isNotBlank() }
            .toSet()
        val visibleAllRestrictionsExemptCount = if (installedCandidatePackages.isNotEmpty()) {
            SafetyGate.expandedUserAllowedPackages(allRestrictionsExemptPackages)
                .count { packageName ->
                    packageName in installedCandidatePackages &&
                        packageName !in SafetyGate.neverBlockPackages
                }
        } else {
            allRestrictionsExemptPackages.count { packageName ->
                packageName !in SafetyGate.neverBlockPackages
            }
        }
        val configuredCandidatePackages = (
            usageByPackage.keys +
                configuredAppLimits.keys +
                settings.normalizedAppGroups().flatMap { group -> group.packageNames } +
                allowedAppPackages +
                settings.normalizedScheduleTemplates().flatMap { schedule -> schedule.allowedPackageNames } +
                allRestrictionsExemptPackages +
                temporarilyAllowedPackages
            ).toSet()
        val effectiveCandidatePackages = installedCandidatePackages
            .ifEmpty { configuredCandidatePackages }
            .filter { packageName ->
                packageName.isNotBlank() && packageName !in SafetyGate.neverBlockPackages
            }.toSet()
        val effectiveAppSummaries = effectiveCandidatePackages.map { packageName ->
            val appName = appNameByPackage[packageName] ?: usageByPackage[packageName]?.appName ?: packageName
            val temporaryAllowance = todayTemporaryUnlockState.packageAllowances[packageName]
            val temporarilyAllowed = temporaryAllowance?.isTemporarilyAllowed(nowMillis) == true
            val unlockedForToday = temporaryAllowance?.unlockedForToday == true
            val access = when {
                SafetyGate.isUserAllowedPackage(packageName, allRestrictionsExemptPackages) ->
                    EffectiveAppAccess.AllRestrictionsExempt
                immediateBlock.appliesTo(packageName, nowMillis) ->
                    EffectiveAppAccess.BlockedByParent
                activeSchedules.isNotEmpty() && activeSchedules.all { schedule ->
                    SafetyGate.isUserAllowedPackage(packageName, schedule.allowedPackageNames)
                } -> EffectiveAppAccess.ScheduleAllowed
                activeSchedules.isNotEmpty() && (temporarilyAllowed || unlockedForToday) ->
                    EffectiveAppAccess.ScheduleAllowed
                activeSchedules.isNotEmpty() ->
                    EffectiveAppAccess.BlockedBySchedule
                settings.allowOnlyModeEnabled && (
                    SafetyGate.isUserAllowedPackage(packageName, allowedAppPackages) ||
                        temporarilyAllowed ||
                        unlockedForToday
                    ) -> EffectiveAppAccess.AllowOnlyAllowed
                settings.allowOnlyModeEnabled ->
                    EffectiveAppAccess.BlockedByAllowOnly
                else -> EffectiveAppAccess.NormallyAllowed
            }
            val timeCandidates = if (
                access == EffectiveAppAccess.AllRestrictionsExempt ||
                access == EffectiveAppAccess.BlockedByParent ||
                access == EffectiveAppAccess.BlockedBySchedule ||
                access == EffectiveAppAccess.BlockedByAllowOnly
            ) {
                emptyList()
            } else {
                buildList {
                    if (totalLimitMinutesOrNull != null && !todayTemporaryUnlockState.totalUnlockedForToday) {
                        add(
                            EffectiveTimeLimiter.Daily to
                                (totalLimitMinutes + totalExtraMinutes - totalUsedMinutes),
                        )
                    }
                    groupSummaries.firstOrNull { group -> packageName in group.packageNames }
                        ?.takeIf { group -> group.limitEnabled }
                        ?.takeUnless { unlockedForToday }
                        ?.let { group ->
                            add(
                                EffectiveTimeLimiter.AppGroup to
                                    (group.limitMinutes + group.extraMinutes - group.usedMinutes),
                            )
                        }
                    appLimitSummaries.firstOrNull { app -> app.packageName == packageName }
                        ?.takeIf { app -> app.activeToday }
                        ?.takeUnless { unlockedForToday }
                        ?.let { app ->
                            add(
                                EffectiveTimeLimiter.App to
                                    (app.limitMinutes + app.extraMinutes - app.usedMinutes),
                            )
                        }
                }
            }
            val limiting = timeCandidates.minByOrNull { (_, remaining) -> remaining }
            val status = when {
                access == EffectiveAppAccess.BlockedBySchedule ||
                    access == EffectiveAppAccess.BlockedByAllowOnly ||
                    access == EffectiveAppAccess.BlockedByParent -> LimitStatus.Exceeded
                limiting == null -> LimitStatus.Normal
                limiting.second <= 0 -> LimitStatus.Exceeded
                else -> {
                    val configuredLimit = when (limiting.first) {
                        EffectiveTimeLimiter.Daily -> totalLimitMinutes + totalExtraMinutes
                        EffectiveTimeLimiter.AppGroup -> groupSummaries
                            .firstOrNull { group -> packageName in group.packageNames }
                            ?.let { group -> group.limitMinutes + group.extraMinutes }
                            ?: 0
                        EffectiveTimeLimiter.App -> appLimitSummaries
                            .firstOrNull { app -> app.packageName == packageName }
                            ?.let { app -> app.limitMinutes + app.extraMinutes }
                            ?: 0
                        EffectiveTimeLimiter.None -> 0
                    }
                    if (configuredLimit > 0 && limiting.second * 100 <= configuredLimit * 20) {
                        LimitStatus.Warning
                    } else {
                        LimitStatus.Normal
                    }
                }
            }
            EffectiveAppPolicySummary(
                appName = appName,
                packageName = packageName,
                access = access,
                limitingPolicy = limiting?.first ?: EffectiveTimeLimiter.None,
                remainingMinutes = limiting?.second?.coerceAtLeast(0),
                status = status,
            )
        }.sortedWith(
            compareByDescending<EffectiveAppPolicySummary> { summary ->
                summary.status == LimitStatus.Exceeded
            }.thenBy { summary -> summary.appName.lowercase() },
        )

        val hardshipItems = buildList {
            if (settings.dailyHardshipLevel != HardshipLevel.Off) {
                add(HardshipPolicySummaryItem(dailyHardshipKey(), level = settings.dailyHardshipLevel))
            }
            settings.normalizedAppGroups().forEach { group ->
                if (group.hardshipLevel != HardshipLevel.Off) {
                    add(HardshipPolicySummaryItem(appGroupHardshipKey(group.id), group.name, group.hardshipLevel))
                }
            }
            settings.appLimitHardshipLevelMap().forEach { (packageName, level) ->
                if (level != HardshipLevel.Off) {
                    add(
                        HardshipPolicySummaryItem(
                            appLimitHardshipKey(packageName),
                            appNameByPackage[packageName] ?: packageName,
                            level,
                        ),
                    )
                }
            }
            settings.normalizedScheduleTemplates().forEach { schedule ->
                if (schedule.hardshipLevel != HardshipLevel.Off) {
                    add(HardshipPolicySummaryItem(scheduleHardshipKey(schedule.id), schedule.name, schedule.hardshipLevel))
                }
            }
            if (settings.allowOnlyHardshipLevel != HardshipLevel.Off) {
                add(HardshipPolicySummaryItem(allowOnlyHardshipKey(), level = settings.allowOnlyHardshipLevel))
            }
        }

        return PolicySummary(
            actualTotalUsedMinutes = actualTotalUsedMinutes,
            totalUsedMinutes = totalUsedMinutes,
            totalLimitMinutes = totalLimitMinutes,
            totalLimitEnabled = totalLimitMinutesOrNull != null,
            dailyPolicyEnabled = settings.dailyPolicyEnabled,
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
            allRestrictionsExemptAppCount = visibleAllRestrictionsExemptCount,
            effectiveAppSummaries = effectiveAppSummaries,
            temporaryAllowedApps = temporaryAllowedApps,
            warningCount = statuses.count { status -> status == LimitStatus.Warning },
            exceededCount = statuses.count { status -> status == LimitStatus.Exceeded },
            dailyHardshipLevel = settings.dailyHardshipLevel,
            appGroupsHardshipLevel = settings.appGroupsHardshipLevel,
            appLimitsHardshipLevel = settings.appLimitsHardshipLevel,
            scheduleHardshipLevel = settings.scheduleHardshipLevel,
            allowOnlyHardshipLevel = settings.allowOnlyHardshipLevel,
            activeHardshipPolicies = hardshipRuntimeState.activePolicies,
            hardshipItems = hardshipItems,
            activeHardshipPolicyKeys = hardshipRuntimeState.activePolicyKeys,
            emergencyPassLastUsedAtMillis = hardshipRuntimeState.lastEmergencyPassUsedAtMillis,
            emergencyPassNextAvailableAtMillis = hardshipRuntimeState.emergencyPassNextAvailableAtMillis(),
        )
    }

    private fun buildScheduleSummaries(
        settings: UsagePolicySettings,
        allRestrictionsExemptPackages: Set<String>,
        temporarilyAllowedPackages: Set<String>,
    ): List<ScheduleSummary> {
        if (!settings.scheduleBlockingEnabled) {
            return emptyList()
        }
        val now = LocalDateTime.now()
        val transientAndExemptPackages =
            temporarilyAllowedPackages +
                SafetyGate.expandedUserAllowedPackages(allRestrictionsExemptPackages)
        return settings.normalizedScheduleTemplates()
            .filter { template -> template.enabled }
            .map { template ->
                val activeNow = template.isActiveAt(now)
                val effectiveAllowedPackages = template.allowedPackageNames + transientAndExemptPackages
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
        if (limitMinutes <= 0) return LimitStatus.Exceeded
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
        allRestrictionsExemptPackages: Set<String>,
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
        allRestrictionsExemptPackages: Set<String>,
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
                groupPackages +
                allRestrictionsExemptPackages
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
                    allowOnlyAllowedPackages = allowedAppPackages,
                    userAllowedPackages = allRestrictionsExemptPackages,
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
private const val HARDSHIP_BOUNDARY_SETTLE_MILLIS = 300L
private const val FOREGROUND_USAGE_EVENT_SETTLE_MILLIS = 400L
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

private data class ParentListenerTarget(
    val childDeviceIds: Set<String>,
    val deviceRole: ParentDeviceRole,
)

private fun BlockDecision.isWouldBlock(): Boolean {
    return this == BlockDecision.WouldBlockTotalLimit ||
        this == BlockDecision.WouldBlockSchedule ||
        this == BlockDecision.WouldBlockAllowOnly ||
        this == BlockDecision.WouldBlockImmediate ||
        this == BlockDecision.WouldBlockGroupLimit ||
        this == BlockDecision.WouldBlockAppLimit
}

private fun ScheduleTemplatePolicy.isActiveAt(now: LocalDateTime): Boolean {
    if (!enabled) return false
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
    val allRestrictionsExemptPackages: Set<String> = emptySet(),
    val parentManagementState: ParentManagementState = ParentManagementState(),
    val parentNotificationState: ParentNotificationState = ParentNotificationState(),
    val hardshipRuntimeState: HardshipRuntimeState = HardshipRuntimeState(),
    val eventLog: List<EventLogEntry> = emptyList(),
    val foregroundDetectionStatus: ForegroundDetectionStatus? = null,
    val usageMonitorStatus: UsageMonitorStatus = UsageMonitorStatus(),
    val systemHealthStatus: SystemHealthStatus = SystemHealthStatus(),
    val permissionSetupCompletedOnce: Boolean = false,
    val monitoringDisclosureAccepted: Boolean = false,
    val childTopAppsSharingEnabled: Boolean = false,
    val securityPinsConfigured: Boolean = false,
    val policySectionExpansionSettings: PolicySectionExpansionSettings = PolicySectionExpansionSettings(),
)

private data class ReleaseComplianceState(
    val permissionSetupCompletedOnce: Boolean = false,
    val monitoringDisclosureAccepted: Boolean = false,
    val childTopAppsSharingEnabled: Boolean = false,
    val securityPinsConfigured: Boolean = false,
)

private data class NotificationSettingsState(
    val warningNotificationsEnabled: Boolean = true,
    val limitNotificationsEnabled: Boolean = true,
)

private data class PolicyAndLogState(
    val usagePolicySettings: UsagePolicySettings = UsagePolicySettings(),
    val temporaryUnlockState: TemporaryUnlockState = TemporaryUnlockState(),
    val allowedAppPackages: Set<String> = emptySet(),
    val allRestrictionsExemptPackages: Set<String> = emptySet(),
    val parentManagementState: ParentManagementState = ParentManagementState(),
    val parentNotificationState: ParentNotificationState = ParentNotificationState(),
    val hardshipRuntimeState: HardshipRuntimeState = HardshipRuntimeState(),
    val eventLog: List<EventLogEntry> = emptyList(),
)

private data class PolicyAppListsState(
    val allowOnlyPackages: Set<String> = emptySet(),
    val allRestrictionsExemptPackages: Set<String> = emptySet(),
)

private data class ParentAndHardshipState(
    val parentManagementState: ParentManagementState = ParentManagementState(),
    val hardshipRuntimeState: HardshipRuntimeState = HardshipRuntimeState(),
    val parentNotificationState: ParentNotificationState = ParentNotificationState(),
)

private data class UsageState(
    val dateKey: String = currentUsageDateKey(),
    val hasUsageAccess: Boolean = false,
    val overlayPermissionReady: Boolean = false,
    val notificationPermissionReady: Boolean = false,
    val notificationAccessReady: Boolean = false,
    val exactAlarmReady: Boolean = false,
    val parentRequestNotificationReady: Boolean = false,
    val parentRequestNotificationIssue: String = "",
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
    val safeRecoveryStatus: SafeRecoveryStatus = SafeRecoveryStatus.Idle,
    val autoRecoveryStatus: AutoRecoveryStatus = AutoRecoveryStatus.Idle,
    val usageState: UsageState = UsageState(),
    val policySaveStatus: PolicySaveStatus = PolicySaveStatus.Idle,
    val pinChangeStatus: PinChangeStatus = PinChangeStatus.Idle,
    val safeModePinStatus: SafeModePinStatus = SafeModePinStatus.Idle,
    val policyDraftSettings: UsagePolicySettings? = null,
    val policyDraftAllowedAppPackages: Set<String>? = null,
    val policyDraftAllRestrictionsExemptPackages: Set<String>? = null,
)
