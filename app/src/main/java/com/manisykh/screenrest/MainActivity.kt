package com.manisykh.screenrest

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.Manifest
import android.util.LruCache
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import com.manisykh.screenrest.blocking.BlockedActivity
import com.manisykh.screenrest.blocking.BootRecoveryReceiver
import com.manisykh.screenrest.blocking.UsageMonitorForegroundService
import com.manisykh.screenrest.data.AppLanguage
import com.manisykh.screenrest.data.AppGroupPolicy
import com.manisykh.screenrest.data.EventLogEntry
import com.manisykh.screenrest.data.ForegroundDetectionStatus
import com.manisykh.screenrest.data.ParentManagementState
import com.manisykh.screenrest.data.RemoteParentCommand
import com.manisykh.screenrest.data.RemoteParentCommandStatus
import com.manisykh.screenrest.data.RemoteParentCommandType
import com.manisykh.screenrest.data.ScheduleTemplatePolicy
import com.manisykh.screenrest.data.SystemHealthStatus
import com.manisykh.screenrest.data.TemporaryUnlockState
import com.manisykh.screenrest.data.UsagePolicySettings
import com.manisykh.screenrest.data.UsageMonitorStatus
import com.manisykh.screenrest.data.activeScheduleTemplate
import com.manisykh.screenrest.data.isScheduleBlockingNow
import com.manisykh.screenrest.data.normalizedAppGroups
import com.manisykh.screenrest.data.normalizedScheduleTemplates
import com.manisykh.screenrest.data.scheduleDaySet
import com.manisykh.screenrest.data.toScheduleDaysEncoded
import com.manisykh.screenrest.data.toScheduleTemplatesEncoded
import com.manisykh.screenrest.data.toAppGroupsEncoded
import com.manisykh.screenrest.notification.UsageNotificationHelper
import com.manisykh.screenrest.safety.BlockDecision
import com.manisykh.screenrest.safety.BlockDecisionResult
import com.manisykh.screenrest.safety.SafetyGate
import com.manisykh.screenrest.ui.safety.AppGroupSummary
import com.manisykh.screenrest.ui.safety.AppLimitSummary
import com.manisykh.screenrest.ui.safety.AutoRecoveryStatus
import com.manisykh.screenrest.ui.safety.BlockingReadiness
import com.manisykh.screenrest.ui.safety.EmergencyUnlockStatus
import com.manisykh.screenrest.ui.safety.LimitStatus
import com.manisykh.screenrest.ui.safety.PinChangeStatus
import com.manisykh.screenrest.ui.safety.PolicyBudgetValidation
import com.manisykh.screenrest.ui.safety.PolicySummary
import com.manisykh.screenrest.ui.safety.PolicySaveStatus
import com.manisykh.screenrest.ui.safety.SafeModeUiState
import com.manisykh.screenrest.ui.safety.SafeModePinStatus
import com.manisykh.screenrest.ui.safety.ScheduleSummary
import com.manisykh.screenrest.ui.safety.SafeModeViewModel
import com.manisykh.screenrest.ui.safety.TopAppsUsageSet
import com.manisykh.screenrest.ui.safety.appLimitMap
import com.manisykh.screenrest.ui.safety.toAppLimitRules
import com.manisykh.screenrest.ui.theme.ScreenTimeManagerTheme
import com.manisykh.screenrest.ui.theme.AppOver
import com.manisykh.screenrest.ui.theme.AppSafe
import com.manisykh.screenrest.ui.theme.AppWarn
import com.manisykh.screenrest.usage.AppUsageInfo
import com.manisykh.screenrest.usage.DailyUsageInfo
import com.manisykh.screenrest.usage.InstalledAppInfo
import com.manisykh.screenrest.worker.SystemHealthCheckWorker
import com.manisykh.screenrest.worker.UsageMonitorRecoveryWorker
import com.manisykh.screenrest.worker.UsagePolicyCheckWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val safeModeViewModel: SafeModeViewModel by viewModels()
    private val suppressPermissionSetupAutoDialog = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyLaunchIntent(intent)
        UsageNotificationHelper(this).ensureChannel()
        UsagePolicyCheckWorker.schedule(this)
        SystemHealthCheckWorker.scheduleNow(this)
        SystemHealthCheckWorker.schedulePeriodic(this)
        BootRecoveryReceiver.scheduleDailyRolloverAlarm(this)
        UsageMonitorRecoveryWorker.schedulePeriodic(this)
        enableEdgeToEdge()
        setContent {
            ScreenTimeManagerTheme {
                val uiState by safeModeViewModel.uiState.collectAsState()
                LaunchedEffect(
                    uiState.safeModeEnabled,
                    uiState.policyEnforcementEnabled,
                ) {
                    if (!uiState.safeModeEnabled && uiState.policyEnforcementEnabled) {
                        UsageMonitorForegroundService.managerVisible(this@MainActivity)
                    }
                }
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = {
                        safeModeViewModel.refreshForForeground(force = true)
                    },
                )

                Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold(
                        containerColor = MaterialTheme.colorScheme.background,
                    ) { innerPadding ->
                        ScreenTimeManagerScreen(
                            uiState = uiState,
                            onSafeModeChanged = safeModeViewModel::setSafeModeEnabled,
                            onSafeModeEnableWithPin = safeModeViewModel::enableSafeModeWithAdminPin,
                            onSafeModePinStatusSeen = safeModeViewModel::clearSafeModePinStatus,
                            onPolicyEnforcementChanged = safeModeViewModel::setPolicyEnforcementEnabled,
                            onPolicyEnforcementDisableWithPin = safeModeViewModel::disablePolicyEnforcementWithAdminPin,
                            onAppLanguageChanged = safeModeViewModel::setAppLanguage,
                            onWarningNotificationsChanged = safeModeViewModel::setWarningNotificationsEnabled,
                            onLimitNotificationsChanged = safeModeViewModel::setLimitNotificationsEnabled,
                            onEmergencyUnlock = safeModeViewModel::submitEmergencyPin,
                            onEmergencyPinChanged = safeModeViewModel::clearEmergencyUnlockStatus,
                            onAllowedAppsChanged = safeModeViewModel::setAllowedAppPackages,
                            onOpenUsageAccessSettings = ::openUsageAccessSettings,
                            onOpenNotificationAccessSettings = ::openNotificationAccessSettings,
                            onOpenExactAlarmSettings = ::openExactAlarmSettings,
                            onOpenOverlaySettings = ::openOverlaySettings,
                            onRefreshUsageStats = { safeModeViewModel.refreshUsageStats(force = true) },
                            onRefreshStatistics = { safeModeViewModel.refreshStatistics() },
                            onPolicyDraftChanged = safeModeViewModel::updatePolicyDraft,
                            onResetPolicyDraft = safeModeViewModel::resetPolicyDraft,
                            onSaveUsagePolicy = safeModeViewModel::savePolicyDraft,
                            onPolicySaveStatusSeen = safeModeViewModel::clearPolicySaveStatus,
                            onRequestNotificationPermission = {
                                if (
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    openNotificationSettings()
                                }
                            },
                            onUpdateAdminPin = safeModeViewModel::updateAdminPin,
                            onUpdateEmergencyPin = safeModeViewModel::updateEmergencyPin,
                            onPinInputChanged = safeModeViewModel::clearPinChangeStatus,
                            onPairParentAccount = safeModeViewModel::pairParentAccount,
                            onUnlinkParentAccount = safeModeViewModel::unlinkParentAccount,
                            onSyncParentDevice = safeModeViewModel::syncParentDevice,
                            onClearRemoteParentCommands = safeModeViewModel::clearRemoteParentCommands,
                            onRemoteAppExtraTime = safeModeViewModel::applyRemoteAppExtraTime,
                            onRemoteAppUnlockToday = safeModeViewModel::applyRemoteAppUnlockToday,
                            onRemoteTotalExtraTime = safeModeViewModel::applyRemoteTotalExtraTime,
                            onRemoteTotalUnlockToday = safeModeViewModel::applyRemoteTotalUnlockToday,
                            onClearEventLog = safeModeViewModel::clearEventLog,
                            onDailyPolicyExpandedChange = safeModeViewModel::setDailyPolicyExpanded,
                            onAppGroupsExpandedChange = safeModeViewModel::setAppGroupsExpanded,
                            onAppLimitsExpandedChange = safeModeViewModel::setAppLimitsExpanded,
                            onScheduleBlockingExpandedChange = safeModeViewModel::setScheduleBlockingExpanded,
                            onAllowOnlyModeExpandedChange = safeModeViewModel::setAllowOnlyModeExpanded,
                            suppressPermissionSetupAutoDialog = this@MainActivity.suppressPermissionSetupAutoDialog.value,
                            modifier = Modifier
                                .padding(innerPadding)
                                .padding(
                                    WindowInsets.safeDrawing
                                        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
                                        .asPaddingValues(),
                                ),
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyLaunchIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        val currentUiState = safeModeViewModel.uiState.value
        if (
            !currentUiState.safeModeEnabled &&
            currentUiState.policyEnforcementEnabled
        ) {
            UsageMonitorForegroundService.managerVisible(this)
        }
        safeModeViewModel.refreshForForeground(force = false)
    }

    override fun onStop() {
        val currentUiState = safeModeViewModel.uiState.value
        if (!currentUiState.safeModeEnabled && currentUiState.policyEnforcementEnabled) {
            UsageMonitorForegroundService.start(this)
        }
        safeModeViewModel.markAppStoppedCleanly()
        super.onStop()
    }

    private fun applyLaunchIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_SUPPRESS_PERMISSION_SETUP_AUTO_DIALOG, false) == true) {
            suppressPermissionSetupAutoDialog.value = true
        }
    }

    private fun openUsageAccessSettings() {
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    private fun openOverlaySettings() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            ),
        )
    }

    private fun openNotificationAccessSettings() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val handled = runCatching {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:$packageName")
                    },
                )
            }.isSuccess
            if (handled) {
                return
            }
        }
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            },
        )
    }

    private fun openNotificationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
        }
        startActivity(intent)
    }

    companion object {
        const val EXTRA_SUPPRESS_PERMISSION_SETUP_AUTO_DIALOG =
            "com.manisykh.screenrest.extra.SUPPRESS_PERMISSION_SETUP_AUTO_DIALOG"
    }
}

private enum class ScreenTab {
    Overview,
    Time,
    Blocking,
    Stats,
    Safety,
    Settings,
}

private enum class SafetyPinAction {
    EnableSafeMode,
    DisablePolicyEnforcement,
}

private val PrimaryScreenTabs = listOf(
    ScreenTab.Overview,
    ScreenTab.Time,
    ScreenTab.Blocking,
    ScreenTab.Stats,
    ScreenTab.Safety,
    ScreenTab.Settings,
)

private fun ScreenTab.next(): ScreenTab {
    val index = PrimaryScreenTabs.indexOf(this).takeIf { tabIndex -> tabIndex >= 0 } ?: 0
    return PrimaryScreenTabs[(index + 1).coerceAtMost(PrimaryScreenTabs.lastIndex)]
}

private fun ScreenTab.previous(): ScreenTab {
    val index = PrimaryScreenTabs.indexOf(this).takeIf { tabIndex -> tabIndex >= 0 } ?: PrimaryScreenTabs.lastIndex
    return PrimaryScreenTabs[(index - 1).coerceAtLeast(0)]
}

@Composable
fun ScreenTimeManagerScreen(
    uiState: SafeModeUiState,
    onSafeModeChanged: (Boolean) -> Unit,
    onSafeModeEnableWithPin: (String) -> Unit,
    onSafeModePinStatusSeen: () -> Unit,
    onPolicyEnforcementChanged: (Boolean) -> Unit,
    onPolicyEnforcementDisableWithPin: (String) -> Unit,
    onAppLanguageChanged: (AppLanguage) -> Unit,
    onWarningNotificationsChanged: (Boolean) -> Unit,
    onLimitNotificationsChanged: (Boolean) -> Unit,
    onEmergencyUnlock: (String) -> Unit,
    onEmergencyPinChanged: () -> Unit,
    onAllowedAppsChanged: (Set<String>) -> Unit,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenNotificationAccessSettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onRefreshUsageStats: () -> Unit,
    onRefreshStatistics: () -> Unit,
    onPolicyDraftChanged: (UsagePolicySettings) -> Unit,
    onResetPolicyDraft: () -> Unit,
    onSaveUsagePolicy: (String) -> Unit,
    onPolicySaveStatusSeen: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onUpdateAdminPin: (String, String) -> Unit,
    onUpdateEmergencyPin: (String, String) -> Unit,
    onPinInputChanged: () -> Unit,
    onPairParentAccount: (String, String, String) -> Unit,
    onUnlinkParentAccount: (String) -> Unit,
    onSyncParentDevice: () -> Unit,
    onClearRemoteParentCommands: (String) -> Unit,
    onRemoteAppExtraTime: (String, String, Int) -> Unit,
    onRemoteAppUnlockToday: (String, String) -> Unit,
    onRemoteTotalExtraTime: (Int) -> Unit,
    onRemoteTotalUnlockToday: () -> Unit,
    onClearEventLog: () -> Unit,
    onDailyPolicyExpandedChange: (Boolean) -> Unit,
    onAppGroupsExpandedChange: (Boolean) -> Unit,
    onAppLimitsExpandedChange: (Boolean) -> Unit,
    onScheduleBlockingExpandedChange: (Boolean) -> Unit,
    onAllowOnlyModeExpandedChange: (Boolean) -> Unit,
    suppressPermissionSetupAutoDialog: Boolean,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableStateOf(ScreenTab.Overview) }
    var previousTab by remember { mutableStateOf<ScreenTab?>(null) }
    var tabTransitionDirection by remember { mutableStateOf(1) }
    var emergencyPin by remember { mutableStateOf("") }
    var policyAdminPin by remember { mutableStateOf("") }
    var showPolicySaveDialog by remember { mutableStateOf(false) }
    var permissionSetupDismissedThisSession by remember { mutableStateOf(false) }
    var permissionSetupGateReady by remember { mutableStateOf(false) }
    var showInitialPermissionSetupDialog by remember { mutableStateOf(false) }
    val text = appStrings(uiState.appLanguage)
    val context = LocalContext.current
    val permissionSetupRequired = !uiState.hasUsageAccess ||
        !uiState.blockingReadiness.overlayPermissionReady ||
        !uiState.blockingReadiness.notificationPermissionReady ||
        !uiState.blockingReadiness.notificationAccessReady ||
        !uiState.blockingReadiness.exactAlarmReady
    val permissionSetupAutoPromptRequired = permissionSetupRequired &&
        !uiState.permissionSetupCompletedOnce
    val hasPolicySaveProblem = uiState.policyBudgetValidation.hasOverflow ||
        uiState.policySaveStatus == PolicySaveStatus.InvalidAdminPin ||
        uiState.policySaveStatus == PolicySaveStatus.BudgetExceeded
    val screenBackground by animateColorAsState(
        targetValue = when {
            hasPolicySaveProblem || uiState.policyDraftHasChanges -> AppOver.copy(alpha = 0.045f)
            uiState.policySaveStatus == PolicySaveStatus.Saved -> AppSafe.copy(alpha = 0.06f)
            else -> MaterialTheme.colorScheme.background
        },
        animationSpec = tween(durationMillis = 420),
        label = "policy-save-background",
    )

    LaunchedEffect(uiState.policySaveStatus) {
        if (uiState.policySaveStatus == PolicySaveStatus.Saved) {
            delay(1_200L)
            onPolicySaveStatusSeen()
        }
    }

    LaunchedEffect(Unit) {
        delay(650L)
        permissionSetupGateReady = true
    }

    LaunchedEffect(
        permissionSetupGateReady,
        permissionSetupAutoPromptRequired,
        suppressPermissionSetupAutoDialog,
        permissionSetupDismissedThisSession,
    ) {
        when {
            !permissionSetupGateReady ||
                suppressPermissionSetupAutoDialog ||
                permissionSetupDismissedThisSession -> {
                showInitialPermissionSetupDialog = false
            }

            permissionSetupAutoPromptRequired -> {
                showInitialPermissionSetupDialog = true
            }

            showInitialPermissionSetupDialog -> {
                delay(650L)
                showInitialPermissionSetupDialog = false
            }

            else -> {
                showInitialPermissionSetupDialog = false
            }
        }
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == ScreenTab.Stats) {
            onRefreshStatistics()
        }
    }

    fun selectTab(nextTab: ScreenTab) {
        if (nextTab == selectedTab) {
            return
        }
        previousTab = selectedTab
        tabTransitionDirection = if (nextTab.ordinal > selectedTab.ordinal) 1 else -1
        selectedTab = nextTab
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(screenBackground)
            .fillMaxWidth(),
    ) {
        val isExpanded = maxWidth >= 720.dp
        val isLandscape = maxWidth > maxHeight
        val screenScrollState = rememberScrollState()
        val isKeyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        val bottomBarOuterVerticalPadding = if (isLandscape && isExpanded) 2.dp else 10.dp
        val contentBottomPadding = when {
            isKeyboardVisible -> 28.dp
            isLandscape && isExpanded -> 88.dp
            else -> 112.dp
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 920.dp)
                .verticalScroll(screenScrollState)
                .padding(horizontal = 20.dp)
                .padding(top = 0.dp, bottom = contentBottomPadding)
                .align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Header(
                showPermissionWarning = permissionSetupRequired,
                text = text,
            )

            Box(
                modifier = Modifier.fillMaxWidth(),
            ) {
                TabContentTransition(
                    selectedTab = selectedTab,
                    previousTab = previousTab,
                    direction = tabTransitionDirection,
                    onAnimationFinished = { previousTab = null },
                ) { tab ->
                    when (tab) {
                        ScreenTab.Overview -> OverviewContent(
                            uiState = uiState,
                            text = text,
                            isExpanded = isExpanded,
                            onRefreshUsageStats = onRefreshUsageStats,
                        )

                        ScreenTab.Time -> UsagePolicySection(
                            settings = uiState.policyDraftSettings,
                            temporaryUnlockState = uiState.temporaryUnlockState,
                            installedApps = uiState.installedApps,
                            allowedAppPackages = uiState.allowedAppPackages,
                            text = text,
                            isExpanded = isExpanded,
                            contentMode = PolicyContentMode.TimeControls,
                            dailyPolicyExpanded = uiState.dailyPolicyExpanded,
                            onDailyPolicyExpandedChange = onDailyPolicyExpandedChange,
                            appGroupsExpanded = uiState.appGroupsExpanded,
                            onAppGroupsExpandedChange = onAppGroupsExpandedChange,
                            appLimitsExpanded = uiState.appLimitsExpanded,
                            onAppLimitsExpandedChange = onAppLimitsExpandedChange,
                            scheduleBlockingExpanded = uiState.scheduleBlockingExpanded,
                            onScheduleBlockingExpandedChange = onScheduleBlockingExpandedChange,
                            allowOnlyModeExpanded = uiState.allowOnlyModeExpanded,
                            onAllowOnlyModeExpandedChange = onAllowOnlyModeExpandedChange,
                            onPolicyDraftChanged = onPolicyDraftChanged,
                            onAllowedAppsChanged = onAllowedAppsChanged,
                        )

                        ScreenTab.Blocking -> UsagePolicySection(
                            settings = uiState.policyDraftSettings,
                            temporaryUnlockState = uiState.temporaryUnlockState,
                            installedApps = uiState.installedApps,
                            allowedAppPackages = uiState.allowedAppPackages,
                            text = text,
                            isExpanded = isExpanded,
                            contentMode = PolicyContentMode.BlockingControls,
                            dailyPolicyExpanded = uiState.dailyPolicyExpanded,
                            onDailyPolicyExpandedChange = onDailyPolicyExpandedChange,
                            appGroupsExpanded = uiState.appGroupsExpanded,
                            onAppGroupsExpandedChange = onAppGroupsExpandedChange,
                            appLimitsExpanded = uiState.appLimitsExpanded,
                            onAppLimitsExpandedChange = onAppLimitsExpandedChange,
                            scheduleBlockingExpanded = uiState.scheduleBlockingExpanded,
                            onScheduleBlockingExpandedChange = onScheduleBlockingExpandedChange,
                            allowOnlyModeExpanded = uiState.allowOnlyModeExpanded,
                            onAllowOnlyModeExpandedChange = onAllowOnlyModeExpandedChange,
                            onPolicyDraftChanged = onPolicyDraftChanged,
                            onAllowedAppsChanged = onAllowedAppsChanged,
                        )

                        ScreenTab.Stats -> StatisticsContent(
                            uiState = uiState,
                            text = text,
                            isExpanded = isExpanded,
                        )

                        ScreenTab.Safety -> SafetyContent(
                            uiState = uiState,
                            emergencyPin = emergencyPin,
                            text = text,
                            isExpanded = isExpanded,
                            onSafeModeChanged = onSafeModeChanged,
                            onSafeModeEnableWithPin = onSafeModeEnableWithPin,
                            onSafeModePinStatusSeen = onSafeModePinStatusSeen,
                            onPolicyEnforcementChanged = onPolicyEnforcementChanged,
                            onPolicyEnforcementDisableWithPin = onPolicyEnforcementDisableWithPin,
                            onOpenBlockScreenPreview = { result ->
                                context.startActivity(
                                    BlockedActivity.previewIntent(
                                        context = context,
                                        appName = result.appName,
                                        packageName = result.packageName,
                                        reason = text.blockDecision(result.decision),
                                        usedMinutes = result.usedMinutes,
                                        limitMinutes = result.limitMinutes,
                                        showAppDetails = result.decision != BlockDecision.WouldBlockTotalLimit,
                                    ),
                                )
                            },
                            onPinChanged = { value ->
                                emergencyPin = value
                                onEmergencyPinChanged()
                            },
                            onUnlockClick = {
                                onEmergencyUnlock(emergencyPin)
                                emergencyPin = ""
                            },
                        )

                        ScreenTab.Settings -> SettingsContent(
                            uiState = uiState,
                            text = text,
                            isExpanded = isExpanded,
                            onAppLanguageChanged = onAppLanguageChanged,
                            onWarningNotificationsChanged = onWarningNotificationsChanged,
                            onLimitNotificationsChanged = onLimitNotificationsChanged,
                            onOpenUsageAccessSettings = onOpenUsageAccessSettings,
                            onOpenOverlaySettings = onOpenOverlaySettings,
                            onOpenNotificationAccessSettings = onOpenNotificationAccessSettings,
                            onOpenExactAlarmSettings = onOpenExactAlarmSettings,
                            onRequestNotificationPermission = onRequestNotificationPermission,
                            onUpdateAdminPin = onUpdateAdminPin,
                            onUpdateEmergencyPin = onUpdateEmergencyPin,
                            onPinInputChanged = onPinInputChanged,
                            onPairParentAccount = onPairParentAccount,
                            onUnlinkParentAccount = onUnlinkParentAccount,
                            onSyncParentDevice = onSyncParentDevice,
                            onClearRemoteParentCommands = onClearRemoteParentCommands,
                            onRemoteAppExtraTime = onRemoteAppExtraTime,
                            onRemoteAppUnlockToday = onRemoteAppUnlockToday,
                            onRemoteTotalExtraTime = onRemoteTotalExtraTime,
                            onRemoteTotalUnlockToday = onRemoteTotalUnlockToday,
                            onClearEventLog = onClearEventLog,
                        )
                    }
                }
            }
        }

        if (!isKeyboardVisible) {
            BottomTabBar(
                selectedTab = selectedTab,
                text = text,
                policySaveStatus = uiState.policySaveStatus,
                hasPolicyChanges = uiState.policyDraftHasChanges,
                budgetValidation = uiState.policyBudgetValidation,
                onTabSelected = { tab -> selectTab(tab) },
                onRequestSavePolicy = { showPolicySaveDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 20.dp, vertical = bottomBarOuterVerticalPadding)
                    .widthIn(max = 680.dp),
            )
        }

        if (showPolicySaveDialog) {
            PolicySaveDialog(
                adminPin = policyAdminPin,
                text = text,
                policySaveStatus = uiState.policySaveStatus,
                hasPolicyChanges = uiState.policyDraftHasChanges,
                budgetValidation = uiState.policyBudgetValidation,
                onAdminPinChanged = { policyAdminPin = it },
                onResetPolicyDraft = onResetPolicyDraft,
                onDismiss = {
                    showPolicySaveDialog = false
                    policyAdminPin = ""
                },
                onSave = {
                    onSaveUsagePolicy(policyAdminPin)
                    policyAdminPin = ""
                    showPolicySaveDialog = false
                },
            )
        }

        if (showInitialPermissionSetupDialog) {
            PermissionSetupDialog(
                readiness = uiState.blockingReadiness,
                text = text,
                onOpenUsageAccessSettings = onOpenUsageAccessSettings,
                onOpenOverlaySettings = onOpenOverlaySettings,
                onOpenNotificationAccessSettings = onOpenNotificationAccessSettings,
                onOpenExactAlarmSettings = onOpenExactAlarmSettings,
                onRequestNotificationPermission = onRequestNotificationPermission,
                onDismiss = {
                    permissionSetupDismissedThisSession = true
                    showInitialPermissionSetupDialog = false
                },
            )
        }
    }
}

@Composable
private fun TabContentTransition(
    selectedTab: ScreenTab,
    previousTab: ScreenTab?,
    direction: Int,
    onAnimationFinished: () -> Unit,
    content: @Composable (ScreenTab) -> Unit,
) {
    val hasPreviousTab = previousTab != null && previousTab != selectedTab
    var visible by remember(selectedTab, previousTab) { mutableStateOf(!hasPreviousTab) }
    LaunchedEffect(selectedTab) {
        visible = true
    }
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "tab-content-transition",
        finishedListener = { value ->
            if (value >= 1f) {
                onAnimationFinished()
            }
        },
    )

    BoxWithConstraints(modifier = Modifier.clipToBounds()) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        Box(
            modifier = Modifier.graphicsLayer {
                alpha = 0.9f + (progress * 0.1f)
                translationX = direction * (1f - progress) * widthPx * 0.12f
            },
        ) {
            content(selectedTab)
        }
    }
}

@Composable
private fun PermissionSetupDialog(
    readiness: BlockingReadiness,
    text: AppStrings,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenNotificationAccessSettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 12.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text.permissionSetupTitle,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text.permissionSetupDescription,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    StatusBadge(text.notReady, LimitStatus.Warning)
                }

                PermissionSetupRow(
                    title = text.usageAccessReady,
                    ready = readiness.usageAccessReady,
                    readyLabel = text.ready,
                    actionLabel = text.allowPermission,
                    onClick = onOpenUsageAccessSettings,
                )
                PermissionSetupRow(
                    title = text.overlayPermission,
                    ready = readiness.overlayPermissionReady,
                    readyLabel = text.ready,
                    actionLabel = text.allowPermission,
                    onClick = onOpenOverlaySettings,
                )
                PermissionSetupRow(
                    title = text.notificationPermission,
                    ready = readiness.notificationPermissionReady,
                    readyLabel = text.ready,
                    actionLabel = text.allowPermission,
                    onClick = onRequestNotificationPermission,
                )
                PermissionSetupRow(
                    title = text.notificationAccessPermission,
                    ready = readiness.notificationAccessReady,
                    readyLabel = text.ready,
                    actionLabel = text.allowPermission,
                    onClick = onOpenNotificationAccessSettings,
                )
                PermissionSetupRow(
                    title = text.exactAlarmPermission,
                    ready = readiness.exactAlarmReady,
                    readyLabel = text.ready,
                    actionLabel = text.allowPermission,
                    onClick = onOpenExactAlarmSettings,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(text.cancel)
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionSetupRow(
    title: String,
    ready: Boolean,
    readyLabel: String,
    actionLabel: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (ready) {
                StatusBadge(readyLabel, LimitStatus.Normal)
            } else {
                Button(
                    onClick = onClick,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(actionLabel, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun PolicySaveDialog(
    adminPin: String,
    text: AppStrings,
    policySaveStatus: PolicySaveStatus,
    hasPolicyChanges: Boolean,
    budgetValidation: PolicyBudgetValidation,
    onAdminPinChanged: (String) -> Unit,
    onResetPolicyDraft: () -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val hasBudgetOverflow = budgetValidation.hasOverflow
    val status = when {
        hasBudgetOverflow -> LimitStatus.Exceeded
        policySaveStatus == PolicySaveStatus.InvalidAdminPin -> LimitStatus.Exceeded
        policySaveStatus == PolicySaveStatus.BudgetExceeded -> LimitStatus.Exceeded
        hasPolicyChanges -> LimitStatus.Warning
        else -> LimitStatus.Normal
    }
    val statusLabel = when {
        hasBudgetOverflow -> text.policyBudgetExceeded
        policySaveStatus == PolicySaveStatus.Saved -> text.policySaved
        policySaveStatus == PolicySaveStatus.InvalidAdminPin -> text.invalidAdminPin
        policySaveStatus == PolicySaveStatus.BudgetExceeded -> text.policyBudgetExceeded
        hasPolicyChanges -> text.unsavedChanges
        else -> text.policyUpToDate
    }
    val canSave = adminPin.isNotBlank() && hasPolicyChanges && !hasBudgetOverflow

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle(text.savePolicy, Modifier.weight(1f))
                    StatusBadge(label = statusLabel, status = status)
                }

                policyValidationMessage(budgetValidation, text)?.let { message ->
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = AppOver.copy(alpha = 0.10f),
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = AppOver,
                        )
                    }
                }

                SecurePinTextField(
                    value = adminPin,
                    onValueChange = onAdminPinChanged,
                    label = text.adminPin,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            if (canSave) {
                                onSave()
                            }
                        },
                    ),
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = {
                            focusManager.clearFocus()
                            onResetPolicyDraft()
                            onDismiss()
                        },
                        enabled = hasPolicyChanges,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text.resetChanges)
                    }
                    TextButton(
                        onClick = {
                            focusManager.clearFocus()
                            onDismiss()
                        },
                        modifier = Modifier.weight(0.86f),
                    ) {
                        Text(text.cancel)
                    }
                }

                Button(
                    onClick = {
                        focusManager.clearFocus()
                        onSave()
                    },
                    enabled = canSave,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                ) {
                    Text(
                        text.savePolicy,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
fun SafetyAdminPinDialog(
    adminPin: String,
    text: AppStrings,
    safeModePinStatus: SafeModePinStatus,
    title: String,
    description: String,
    acceptedLabel: String,
    confirmLabel: String,
    onAdminPinChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val status = when (safeModePinStatus) {
        SafeModePinStatus.Accepted -> LimitStatus.Normal
        SafeModePinStatus.TooShort, SafeModePinStatus.InvalidAdminPin -> LimitStatus.Exceeded
        SafeModePinStatus.Idle -> LimitStatus.Warning
    }
    val statusLabel = when (safeModePinStatus) {
        SafeModePinStatus.Accepted -> acceptedLabel
        SafeModePinStatus.TooShort -> text.pinTooShort
        SafeModePinStatus.InvalidAdminPin -> text.invalidAdminPin
        SafeModePinStatus.Idle -> text.adminPin
    }
    val canConfirm = adminPin.isNotBlank()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle(title, Modifier.weight(1f))
                    StatusBadge(label = statusLabel, status = status)
                }

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                SecurePinTextField(
                    value = adminPin,
                    onValueChange = { value -> onAdminPinChanged(value.filter { char -> char.isDigit() }) },
                    label = text.adminPin,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            if (canConfirm) {
                                onConfirm()
                            }
                        },
                    ),
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            focusManager.clearFocus()
                            onDismiss()
                        },
                        modifier = Modifier.weight(0.9f),
                    ) {
                        Text(text.cancel)
                    }
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            onConfirm()
                        },
                        enabled = canConfirm,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.weight(1.1f),
                    ) {
                        Text(confirmLabel)
                    }
                }
            }
        }
    }
}

private fun policyValidationMessage(
    budgetValidation: PolicyBudgetValidation,
    text: AppStrings,
): String? {
    if (!budgetValidation.hasOverflow) {
        return null
    }
    if (budgetValidation.appGroupLimitConflictCount > 0) {
        return text.appGroupLimitConflict(budgetValidation.appGroupLimitConflictCount)
    }
    val overflowingDays = budgetValidation.overflowingDayIndexes
        .joinToString(", ") { index -> text.dayLabels.getOrElse(index) { "" } }
    return "${text.policyBudgetExceeded}: $overflowingDays - ${text.appLimits} ${formatLimitMinutesLabel(budgetValidation.appLimitTotalMinutes)}, ${text.appGroups} ${formatLimitMinutesLabel(budgetValidation.groupBudgetTotalMinutes)}"
}

@Composable
fun Header(
    showPermissionWarning: Boolean,
    text: AppStrings,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                "ScreenRest",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Manager \u00B7 Today",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (showPermissionWarning) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = AppOver.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, AppOver.copy(alpha = 0.22f)),
            ) {
                Text(
                    text.permissionWarning,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppOver,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun BottomTabBar(
    selectedTab: ScreenTab,
    text: AppStrings,
    policySaveStatus: PolicySaveStatus,
    hasPolicyChanges: Boolean,
    budgetValidation: PolicyBudgetValidation,
    onTabSelected: (ScreenTab) -> Unit,
    onRequestSavePolicy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PrimaryScreenTabs.forEach { tab ->
                BottomTabItem(
                    tab = tab,
                    label = text.tabLabel(tab),
                    selected = selectedTab == tab,
                    onClick = { onTabSelected(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
            BottomSaveAction(
                text = text,
                policySaveStatus = policySaveStatus,
                hasPolicyChanges = hasPolicyChanges,
                hasBudgetOverflow = budgetValidation.hasOverflow,
                onClick = onRequestSavePolicy,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BottomSaveAction(
    text: AppStrings,
    policySaveStatus: PolicySaveStatus,
    hasPolicyChanges: Boolean,
    hasBudgetOverflow: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pulseTransition = rememberInfiniteTransition(label = "bottom-save-pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.62f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 780),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bottom-save-pulse-alpha",
    )
    val activeAlpha = if ((hasPolicyChanges || hasBudgetOverflow) && policySaveStatus != PolicySaveStatus.Saved) pulseAlpha else 1f
    val status = when {
        hasBudgetOverflow -> LimitStatus.Exceeded
        policySaveStatus == PolicySaveStatus.InvalidAdminPin -> LimitStatus.Exceeded
        policySaveStatus == PolicySaveStatus.BudgetExceeded -> LimitStatus.Exceeded
        hasPolicyChanges -> LimitStatus.Exceeded
        else -> LimitStatus.Normal
    }
    val containerColor = when (status) {
        LimitStatus.Normal -> if (policySaveStatus == PolicySaveStatus.Saved) {
            AppSafe.copy(alpha = 0.18f)
        } else {
            Color.Transparent
        }
        LimitStatus.Warning -> Color(0xFFFFF1D6).copy(alpha = activeAlpha)
        LimitStatus.Exceeded -> AppOver.copy(alpha = 0.12f * activeAlpha)
    }
    val contentColor = when (status) {
        LimitStatus.Normal -> if (policySaveStatus == PolicySaveStatus.Saved) AppSafe else MaterialTheme.colorScheme.onSurfaceVariant
        LimitStatus.Warning -> Color(0xFF9A6500)
        LimitStatus.Exceeded -> AppOver
    }
    val borderColor = when {
        hasBudgetOverflow || hasPolicyChanges -> AppOver.copy(alpha = 0.72f * activeAlpha)
        policySaveStatus == PolicySaveStatus.Saved -> AppSafe.copy(alpha = 0.78f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val dotColor = when {
        hasBudgetOverflow || hasPolicyChanges -> AppOver
        policySaveStatus == PolicySaveStatus.Saved -> AppSafe
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        onClick = onClick,
        modifier = modifier.height(58.dp),
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
        border = BorderStroke(1.4.dp, borderColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier.size(22.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.size(14.dp)) {
                    drawCircle(color = dotColor)
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = text.savePolicy,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BottomTabItem(
    tab: ScreenTab,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant
    val contentColor = if (selected) activeColor else inactiveColor
    Surface(
        onClick = onClick,
        modifier = modifier.height(58.dp),
        shape = RoundedCornerShape(24.dp),
        color = if (selected) activeColor.copy(alpha = 0.12f) else Color.Transparent,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BottomTabIcon(tab = tab, color = contentColor)
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BottomTabIcon(tab: ScreenTab, color: Color) {
    Canvas(modifier = Modifier.size(22.dp)) {
        val stroke = 2.1.dp.toPx()
        val center = Offset(size.width / 2f, size.height / 2f)
        when (tab) {
            ScreenTab.Overview -> {
                drawCircle(color = color, radius = size.minDimension * 0.36f, style = Stroke(width = stroke))
                drawLine(
                    color = color,
                    start = center,
                    end = Offset(size.width * 0.72f, size.height * 0.35f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }

            ScreenTab.Time -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * 0.18f, size.height * 0.22f),
                    size = Size(size.width * 0.64f, size.height * 0.62f),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                    style = Stroke(width = stroke),
                )
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.3f, size.height * 0.12f),
                    end = Offset(size.width * 0.3f, size.height * 0.32f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.7f, size.height * 0.12f),
                    end = Offset(size.width * 0.7f, size.height * 0.32f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.28f, size.height * 0.46f),
                    end = Offset(size.width * 0.72f, size.height * 0.46f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }

            ScreenTab.Blocking -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * 0.26f, size.height * 0.46f),
                    size = Size(size.width * 0.48f, size.height * 0.34f),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                    style = Stroke(width = stroke),
                )
                drawArc(
                    color = color,
                    startAngle = 205f,
                    sweepAngle = 130f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.33f, size.height * 0.18f),
                    size = Size(size.width * 0.34f, size.height * 0.44f),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }

            ScreenTab.Stats -> {
                val barWidth = size.width * 0.14f
                listOf(
                    0.28f to 0.66f,
                    0.50f to 0.42f,
                    0.72f to 0.24f,
                ).forEach { (x, top) ->
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(size.width * x - barWidth / 2f, size.height * top),
                        size = Size(barWidth, size.height * (0.86f - top)),
                        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                    )
                }
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.18f, size.height * 0.88f),
                    end = Offset(size.width * 0.82f, size.height * 0.88f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }

            ScreenTab.Safety -> {
                val points = listOf(
                    Offset(size.width * 0.5f, size.height * 0.12f),
                    Offset(size.width * 0.78f, size.height * 0.25f),
                    Offset(size.width * 0.72f, size.height * 0.68f),
                    Offset(size.width * 0.5f, size.height * 0.88f),
                    Offset(size.width * 0.28f, size.height * 0.68f),
                    Offset(size.width * 0.22f, size.height * 0.25f),
                    Offset(size.width * 0.5f, size.height * 0.12f),
                )
                points.zipWithNext().forEach { (start, end) ->
                    drawLine(color = color, start = start, end = end, strokeWidth = stroke, cap = StrokeCap.Round)
                }
            }

            ScreenTab.Settings -> {
                drawCircle(color = color, radius = size.minDimension * 0.34f, style = Stroke(width = stroke))
                drawCircle(color = color, radius = size.minDimension * 0.13f, style = Stroke(width = stroke))
                repeat(8) { index ->
                    val angle = Math.toRadians((index * 45).toDouble())
                    val inner = Offset(
                        x = center.x + kotlin.math.cos(angle).toFloat() * size.minDimension * 0.42f,
                        y = center.y + kotlin.math.sin(angle).toFloat() * size.minDimension * 0.42f,
                    )
                    val outer = Offset(
                        x = center.x + kotlin.math.cos(angle).toFloat() * size.minDimension * 0.51f,
                        y = center.y + kotlin.math.sin(angle).toFloat() * size.minDimension * 0.51f,
                    )
                    drawLine(color = color, start = inner, end = outer, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                }
            }
        }
    }
}

private fun AppStrings.tabLabel(tab: ScreenTab): String {
    return when (tab) {
        ScreenTab.Overview -> overview
        ScreenTab.Time -> time
        ScreenTab.Blocking -> blocking
        ScreenTab.Stats -> stats
        ScreenTab.Safety -> safety
        ScreenTab.Settings -> settings
    }
}

@Composable
fun ChoiceButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(7.dp),
        color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun OverviewContent(
    uiState: SafeModeUiState,
    text: AppStrings,
    isExpanded: Boolean,
    onRefreshUsageStats: () -> Unit,
) {
    AdaptiveTwoPane(
        isExpanded = isExpanded,
        leftContent = {
            StatusCard(uiState, text)
            PolicySummarySection(uiState.policySummary, text)
        },
        rightContent = {
            UsageStatsSection(
                hasUsageAccess = uiState.hasUsageAccess,
                usageAccessChecking = uiState.usageAccessChecking,
                todayUsage = uiState.todayUsage,
                policySummary = uiState.policySummary,
                text = text,
                onRefreshUsageStats = onRefreshUsageStats,
            )
        },
    )
}

@Composable
fun StatisticsContent(
    uiState: SafeModeUiState,
    text: AppStrings,
    isExpanded: Boolean,
) {
    val dailyUsage = uiState.usageStatistics.dailyUsage
    val todayUsage = uiState.todayUsage
    val policySummary = uiState.policySummary

    AdaptiveTwoPane(
        isExpanded = isExpanded,
        leftContent = {
            StatisticsSummaryCard(
                dailyUsage = dailyUsage,
                todayUsage = todayUsage,
                text = text,
            )
            DailyTrendCard(
                dailyUsage = dailyUsage,
                text = text,
            )
        },
        rightContent = {
            TopAppsStatsCard(
                topApps = uiState.usageStatistics.topApps,
                policySummary = policySummary,
                text = text,
            )
            GroupStatsCard(
                groupSummaries = policySummary.groupSummaries,
                text = text,
            )
        },
    )
}

@Composable
private fun StatisticsSummaryCard(
    dailyUsage: List<DailyUsageInfo>,
    todayUsage: List<AppUsageInfo>,
    text: AppStrings,
) {
    val todayTotalMillis = todayUsage.sumOf { appUsage -> appUsage.totalTimeMillis }
    val averageMillis = if (dailyUsage.isNotEmpty()) {
        dailyUsage.sumOf { usage -> usage.totalTimeMillis } / dailyUsage.size
    } else {
        todayTotalMillis
    }
    val peakUsage = dailyUsage.maxByOrNull { usage -> usage.totalTimeMillis }
    val topApp = todayUsage.firstOrNull()

    SimpleCard {
        SectionTitle(text.statistics)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MetricTile(
                label = text.todayStatus,
                value = formatDuration(todayTotalMillis),
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                label = text.averageDaily,
                value = formatDuration(averageMillis),
                modifier = Modifier.weight(1f),
            )
        }
        peakUsage?.takeIf { usage -> usage.totalTimeMillis > 0L }?.let { usage ->
            MetricTile(
                label = text.peakDay,
                value = "${formatStatsDateLabel(usage.dayStartMillis)} · ${formatDuration(usage.totalTimeMillis)}",
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (topApp != null) {
            AppRow(
                appName = topApp.appName,
                packageName = topApp.packageName,
                supportingText = text.usedMinutes(topApp.totalTimeMillis.toDisplayMinutes()),
                trailingContent = {
                    LimitTimeChip(formatDuration(topApp.totalTimeMillis))
                },
            )
        } else {
            Text(text.noStats, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DailyTrendCard(
    dailyUsage: List<DailyUsageInfo>,
    text: AppStrings,
) {
    val hasDailyUsageData = dailyUsage.any { usage -> usage.totalTimeMillis > 0L }
    val maxUsageMillis = dailyUsage.maxOfOrNull { usage -> usage.totalTimeMillis }?.coerceAtLeast(1L) ?: 1L
    val listState = rememberLazyListState()

    LaunchedEffect(dailyUsage.size, hasDailyUsageData) {
        if (hasDailyUsageData) {
            listState.scrollToItem((dailyUsage.lastIndex - 6).coerceAtLeast(0))
        }
    }

    SimpleCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionTitle(text.dailyTrend, Modifier.weight(1f))
            Text(
                text.statsScrollHint,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
        if (!hasDailyUsageData) {
            Text(text.noStats, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f))
                    .padding(horizontal = 14.dp, vertical = 14.dp),
            ) {
                LazyRow(
                    state = listState,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(dailyUsage, key = { usage -> usage.dayStartMillis }) { usage ->
                        DailyUsageBar(
                            usage = usage,
                            maxUsageMillis = maxUsageMillis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyUsageBar(
    usage: DailyUsageInfo,
    maxUsageMillis: Long,
) {
    val fraction = if (maxUsageMillis <= 0L) {
        0f
    } else {
        (usage.totalTimeMillis.toFloat() / maxUsageMillis.toFloat()).coerceIn(0f, 1f)
    }
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 450),
        label = "dailyUsageBar",
    )
    val isToday = isToday(usage.dayStartMillis)
    val barColor = usageIntensityColor(fraction)
    val labelColor = if (isToday) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val barHeight = (132.dp * animatedFraction).coerceAtLeast(8.dp)

    Column(
        modifier = Modifier.width(54.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(
            formatDuration(usage.totalTimeMillis),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .width(22.dp)
                .height(136.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight)
                    .clip(RoundedCornerShape(50))
                    .background(barColor),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            formatStatsWeekdayLabel(usage.dayStartMillis),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = labelColor,
            maxLines = 1,
        )
        Text(
            formatStatsDateLabel(usage.dayStartMillis),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

private fun usageIntensityColor(fraction: Float): Color {
    return when {
        fraction < 0.25f -> Color(0xFF2F80ED)
        fraction < 0.50f -> AppSafe
        fraction < 0.75f -> AppWarn
        else -> AppOver
    }
}

private enum class TopAppsStatsRange {
    OneDay,
    SevenDays,
    ThirtyDays,
}

private fun TopAppsUsageSet.appsFor(range: TopAppsStatsRange): List<AppUsageInfo> {
    return when (range) {
        TopAppsStatsRange.OneDay -> oneDay
        TopAppsStatsRange.SevenDays -> sevenDays
        TopAppsStatsRange.ThirtyDays -> thirtyDays
    }
}

@Composable
private fun TopAppsStatsCard(
    topApps: TopAppsUsageSet,
    policySummary: PolicySummary,
    text: AppStrings,
) {
    var selectedRange by remember { mutableStateOf(TopAppsStatsRange.OneDay) }
    val selectedTopApps = topApps.appsFor(selectedRange)
    val topUsageMillis = selectedTopApps.firstOrNull()?.totalTimeMillis?.coerceAtLeast(1L) ?: 1L

    SimpleCard {
        SectionTitle(text.topApps)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppFilterChip(text.statsRangeOneDay, selectedRange == TopAppsStatsRange.OneDay) {
                selectedRange = TopAppsStatsRange.OneDay
            }
            AppFilterChip(text.statsRangeSevenDays, selectedRange == TopAppsStatsRange.SevenDays) {
                selectedRange = TopAppsStatsRange.SevenDays
            }
            AppFilterChip(text.statsRangeThirtyDays, selectedRange == TopAppsStatsRange.ThirtyDays) {
                selectedRange = TopAppsStatsRange.ThirtyDays
            }
        }
        if (selectedTopApps.isEmpty()) {
            Text(text.noStats, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            ContainedLazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 430.dp),
                resetKey = selectedRange to selectedTopApps.map { usage -> usage.packageName },
            ) {
                items(selectedTopApps, key = { appUsage -> appUsage.packageName }) { appUsage ->
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
    SimpleCard {
        SectionTitle(text.groupStats)
        if (groupSummaries.isEmpty()) {
            Text(text.noStats, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            groupSummaries.forEach { groupSummary ->
                ProgressLine(
                    label = groupSummary.groupName.ifBlank { text.groupName },
                    usedMinutes = groupSummary.usedMinutes,
                    limitMinutes = groupSummary.limitMinutes,
                    status = groupSummary.status,
                    text = text,
                    extraMinutes = groupSummary.extraMinutes,
                )
                val topGroupApps = groupSummary.appUsages
                    .filter { appUsage -> appUsage.usedMinutes > 0 }
                    .sortedByDescending { appUsage -> appUsage.usedMinutes }
                    .take(3)
                if (topGroupApps.isNotEmpty()) {
                    Text(
                        text.groupTopApps,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    topGroupApps.forEach { appUsage ->
                        AppRow(
                            appName = appUsage.appName,
                            packageName = appUsage.packageName,
                            supportingText = buildString {
                                append(text.usedMinutes(appUsage.usedMinutes))
                                appUsage.limitMinutes?.let { limitMinutes ->
                                    append(" / ")
                                    append(formatLimitWithAllowance(limitMinutes, appUsage.extraMinutes, appUsage.unlockedForToday, text))
                                }
                            },
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
    val hasTotalLimit = summary.totalLimitMinutes > 0 && !summary.totalUnlockedForToday
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
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        formatLimitMinutesLabel(summary.totalUsedMinutes),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        " / ${formatLimitWithAllowance(summary.totalLimitMinutes, summary.totalExtraMinutes, summary.totalUnlockedForToday, text)}",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    if (overMinutes > 0) "+${formatLimitMinutesLabel(overMinutes)} over limit" else text.limitStatus(summary.totalStatus),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (overMinutes > 0) AppOver else summary.totalStatus.semanticColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
    val rawProgress = if (limitMinutes <= 0) 0f else usedMinutes.toFloat() / limitMinutes.toFloat()
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
        shape = RoundedCornerShape(8.dp),
        color = color,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "${formatLimitMinutesLabel(usedMinutes)} / ${formatLimitWithAllowance(limitMinutes, extraMinutes, unlockedForToday, text)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ProgressOnlyBar(
            usedMinutes = usedMinutes,
            limitMinutes = limitMinutes,
            extraMinutes = extraMinutes,
            unlockedForToday = unlockedForToday,
            status = status,
        )
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
        limitMinutes <= 0 -> text.noLimit
        extraMinutes > 0 -> "${formatLimitMinutesLabel(limitMinutes)}+${formatLimitMinutesLabel(extraMinutes)}"
        else -> formatLimitMinutesLabel(limitMinutes)
    }
}

private val GaugeTrackColor = Color(0xFFE5E7EB)

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

@Composable
fun SafetyContent(
    uiState: SafeModeUiState,
    emergencyPin: String,
    text: AppStrings,
    isExpanded: Boolean,
    onSafeModeChanged: (Boolean) -> Unit,
    onSafeModeEnableWithPin: (String) -> Unit,
    onSafeModePinStatusSeen: () -> Unit,
    onPolicyEnforcementChanged: (Boolean) -> Unit,
    onPolicyEnforcementDisableWithPin: (String) -> Unit,
    onOpenBlockScreenPreview: (BlockDecisionResult) -> Unit,
    onPinChanged: (String) -> Unit,
    onUnlockClick: () -> Unit,
) {
    var pendingSafetyPinAction by remember { mutableStateOf<SafetyPinAction?>(null) }
    var safetyAdminPin by remember { mutableStateOf("") }

    LaunchedEffect(
        uiState.safeModeEnabled,
        uiState.policyEnforcementEnabled,
        uiState.safeModePinStatus,
        pendingSafetyPinAction,
    ) {
        val shouldClose = when (pendingSafetyPinAction) {
            SafetyPinAction.EnableSafeMode -> uiState.safeModeEnabled ||
                uiState.safeModePinStatus == SafeModePinStatus.Accepted
            SafetyPinAction.DisablePolicyEnforcement -> !uiState.policyEnforcementEnabled ||
                uiState.safeModePinStatus == SafeModePinStatus.Accepted
            null -> false
        }
        if (shouldClose) {
            pendingSafetyPinAction = null
            safetyAdminPin = ""
            onSafeModePinStatusSeen()
        }
    }

    val safetyCore: @Composable ColumnScope.() -> Unit = {
        SimpleCard {
        SectionTitle(text.developerSafeMode)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (uiState.safeModeEnabled) text.safeModeOn else text.safeModeOff,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Switch(
                checked = uiState.safeModeEnabled,
                onCheckedChange = { enabled ->
                    if (enabled && !uiState.safeModeEnabled && uiState.policyEnforcementEnabled) {
                        safetyAdminPin = ""
                        onSafeModePinStatusSeen()
                        pendingSafetyPinAction = SafetyPinAction.EnableSafeMode
                    } else {
                        onSafeModeChanged(enabled)
                    }
                },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text.policyEnforcement, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (uiState.policyEnforcementEnabled) text.policyEnforcementEnabled else text.policyEnforcementDisabled,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = uiState.policyEnforcementEnabled,
                onCheckedChange = { enabled ->
                    if (!enabled && uiState.policyEnforcementEnabled) {
                        safetyAdminPin = ""
                        onSafeModePinStatusSeen()
                        pendingSafetyPinAction = SafetyPinAction.DisablePolicyEnforcement
                    } else {
                        onPolicyEnforcementChanged(enabled)
                    }
                },
                enabled = !uiState.safeModeEnabled &&
                    (
                        uiState.policyEnforcementEnabled ||
                            (
                                uiState.blockingReadiness.usageAccessReady &&
                                    uiState.blockingReadiness.overlayPermissionReady &&
                                    uiState.blockingReadiness.notificationPermissionReady &&
                                    uiState.blockingReadiness.notificationAccessReady &&
                                    uiState.blockingReadiness.exactAlarmReady
                                )
                        ),
            )
        }
        Text(
            when (uiState.autoRecoveryStatus) {
                AutoRecoveryStatus.Idle -> text.autoRecoveryReady
                AutoRecoveryStatus.RecoveredToSafeMode -> text.autoRecoveryEnabledSafeMode
            },
        )
        }

        BlockingReadinessSection(
            readiness = uiState.blockingReadiness,
            text = text,
        )

        SystemHealthStatusSection(
            healthStatus = uiState.systemHealthStatus,
            text = text,
        )

        MonitorStatusSection(
            monitorStatus = uiState.usageMonitorStatus,
            text = text,
        )

        UsageConsistencySection(
            monitorStatus = uiState.usageMonitorStatus,
            todayUsage = uiState.todayUsage,
            text = text,
        )

        BlockSafetyStatusSection(
            results = uiState.blockDecisionResults,
            temporaryUnlockState = uiState.temporaryUnlockState,
            policySummary = uiState.policySummary,
            installedApps = uiState.installedApps,
            text = text,
        )

        DetectionStatusSection(
            detectionStatus = uiState.foregroundDetectionStatus,
            text = text,
        )

        BlockDecisionSimulationSection(
            results = uiState.blockDecisionResults,
            text = text,
        )

        BlockScreenPreviewSection(
            results = uiState.blockDecisionResults,
            text = text,
            onOpenPreview = onOpenBlockScreenPreview,
        )
    }

    val emergencyUnlock: @Composable ColumnScope.() -> Unit = {
        SimpleCard {
        SectionTitle(text.emergencyUnlock)
        SecurePinTextField(
            value = emergencyPin,
            onValueChange = onPinChanged,
            label = text.developerPin,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = onUnlockClick) { Text(text.unlock) }
        Text(
            when (uiState.emergencyUnlockStatus) {
                EmergencyUnlockStatus.Idle -> text.offlinePinAvailable
                EmergencyUnlockStatus.Unlocked -> text.safeModeEnabled
                EmergencyUnlockStatus.InvalidPin -> text.invalidPin
            },
        )
        }
    }

    AdaptiveTwoPane(
        isExpanded = isExpanded,
        leftContent = safetyCore,
        rightContent = emergencyUnlock,
    )

    pendingSafetyPinAction?.let { action ->
        SafetyAdminPinDialog(
            adminPin = safetyAdminPin,
            text = text,
            safeModePinStatus = uiState.safeModePinStatus,
            title = when (action) {
                SafetyPinAction.EnableSafeMode -> text.safeModePinRequiredTitle
                SafetyPinAction.DisablePolicyEnforcement -> text.policyOffPinRequiredTitle
            },
            description = when (action) {
                SafetyPinAction.EnableSafeMode -> text.safeModePinRequiredDescription
                SafetyPinAction.DisablePolicyEnforcement -> text.policyOffPinRequiredDescription
            },
            acceptedLabel = when (action) {
                SafetyPinAction.EnableSafeMode -> text.safeModePinAccepted
                SafetyPinAction.DisablePolicyEnforcement -> text.policyOffPinAccepted
            },
            confirmLabel = when (action) {
                SafetyPinAction.EnableSafeMode -> text.enableSafeMode
                SafetyPinAction.DisablePolicyEnforcement -> text.disablePolicyEnforcement
            },
            onAdminPinChanged = {
                safetyAdminPin = it
                if (uiState.safeModePinStatus != SafeModePinStatus.Idle) {
                    onSafeModePinStatusSeen()
                }
            },
            onDismiss = {
                pendingSafetyPinAction = null
                safetyAdminPin = ""
                onSafeModePinStatusSeen()
            },
            onConfirm = {
                when (action) {
                    SafetyPinAction.EnableSafeMode -> onSafeModeEnableWithPin(safetyAdminPin)
                    SafetyPinAction.DisablePolicyEnforcement -> onPolicyEnforcementDisableWithPin(safetyAdminPin)
                }
            },
        )
    }
}

@Composable
fun SettingsContent(
    uiState: SafeModeUiState,
    text: AppStrings,
    isExpanded: Boolean,
    onAppLanguageChanged: (AppLanguage) -> Unit,
    onWarningNotificationsChanged: (Boolean) -> Unit,
    onLimitNotificationsChanged: (Boolean) -> Unit,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenNotificationAccessSettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onUpdateAdminPin: (String, String) -> Unit,
    onUpdateEmergencyPin: (String, String) -> Unit,
    onPinInputChanged: () -> Unit,
    onPairParentAccount: (String, String, String) -> Unit,
    onUnlinkParentAccount: (String) -> Unit,
    onSyncParentDevice: () -> Unit,
    onClearRemoteParentCommands: (String) -> Unit,
    onRemoteAppExtraTime: (String, String, Int) -> Unit,
    onRemoteAppUnlockToday: (String, String) -> Unit,
    onRemoteTotalExtraTime: (Int) -> Unit,
    onRemoteTotalUnlockToday: () -> Unit,
    onClearEventLog: () -> Unit,
) {
    var currentAdminPin by remember { mutableStateOf("") }
    var newAdminPin by remember { mutableStateOf("") }
    var currentEmergencyPin by remember { mutableStateOf("") }
    var newEmergencyPin by remember { mutableStateOf("") }
    var pinFeedbackTarget by remember { mutableStateOf<PinFeedbackTarget?>(null) }

    val preferences: @Composable ColumnScope.() -> Unit = {
        PermissionSettingsSection(
            readiness = uiState.blockingReadiness,
            text = text,
            onOpenUsageAccessSettings = onOpenUsageAccessSettings,
            onOpenOverlaySettings = onOpenOverlaySettings,
            onRequestNotificationPermission = onRequestNotificationPermission,
            onOpenNotificationAccessSettings = onOpenNotificationAccessSettings,
            onOpenExactAlarmSettings = onOpenExactAlarmSettings,
        )

        SimpleCard {
            SectionTitle(text.language)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceButton(
                    label = text.korean,
                    selected = uiState.appLanguage == AppLanguage.Korean,
                    onClick = { onAppLanguageChanged(AppLanguage.Korean) },
                )
                ChoiceButton(
                    label = "English",
                    selected = uiState.appLanguage == AppLanguage.English,
                    onClick = { onAppLanguageChanged(AppLanguage.English) },
                )
            }
        }

        SimpleCard {
            SectionTitle(text.notificationSettings)
            NotificationPreferenceRow(
                title = text.warningNotifications,
                description = text.warningNotificationsDescription,
                checked = uiState.warningNotificationsEnabled,
                onCheckedChange = onWarningNotificationsChanged,
            )
            NotificationPreferenceRow(
                title = text.limitNotifications,
                description = text.limitNotificationsDescription,
                checked = uiState.limitNotificationsEnabled,
                onCheckedChange = onLimitNotificationsChanged,
            )
        }

        SimpleCard {
            SectionTitle(text.pinSettings)
            PinChangeFields(
                currentPin = currentAdminPin,
                newPin = newAdminPin,
                currentLabel = text.currentAdminPin,
                newLabel = text.newAdminPin,
                onCurrentChanged = {
                    currentAdminPin = it
                    pinFeedbackTarget = null
                    onPinInputChanged()
                },
                onNewChanged = {
                    newAdminPin = it
                    pinFeedbackTarget = null
                    onPinInputChanged()
                },
                onSave = {
                    pinFeedbackTarget = PinFeedbackTarget.Admin
                    onUpdateAdminPin(currentAdminPin, newAdminPin)
                    currentAdminPin = ""
                    newAdminPin = ""
                },
                status = if (pinFeedbackTarget == PinFeedbackTarget.Admin) {
                    uiState.pinChangeStatus
                } else {
                    PinChangeStatus.Idle
                },
                text = text,
            )
            PinChangeFields(
                currentPin = currentEmergencyPin,
                newPin = newEmergencyPin,
                currentLabel = text.currentEmergencyPin,
                newLabel = text.newEmergencyPin,
                onCurrentChanged = {
                    currentEmergencyPin = it
                    pinFeedbackTarget = null
                    onPinInputChanged()
                },
                onNewChanged = {
                    newEmergencyPin = it
                    pinFeedbackTarget = null
                    onPinInputChanged()
                },
                onSave = {
                    pinFeedbackTarget = PinFeedbackTarget.Emergency
                    onUpdateEmergencyPin(currentEmergencyPin, newEmergencyPin)
                    currentEmergencyPin = ""
                    newEmergencyPin = ""
                },
                status = if (pinFeedbackTarget == PinFeedbackTarget.Emergency) {
                    uiState.pinChangeStatus
                } else {
                    PinChangeStatus.Idle
                },
                text = text,
            )
        }

        ParentManagementSection(
            parentState = uiState.parentManagementState,
            installedApps = uiState.installedApps,
            policySummary = uiState.policySummary,
            text = text,
            onPairParentAccount = onPairParentAccount,
            onUnlinkParentAccount = onUnlinkParentAccount,
            onSyncParentDevice = onSyncParentDevice,
            onClearRemoteParentCommands = onClearRemoteParentCommands,
            onRemoteAppExtraTime = onRemoteAppExtraTime,
            onRemoteAppUnlockToday = onRemoteAppUnlockToday,
            onRemoteTotalExtraTime = onRemoteTotalExtraTime,
            onRemoteTotalUnlockToday = onRemoteTotalUnlockToday,
        )
    }

    val logs: @Composable ColumnScope.() -> Unit = {
        EventLogSection(
            eventLog = uiState.eventLog,
            text = text,
            onClearEventLog = onClearEventLog,
        )
    }

    AdaptiveTwoPane(
        isExpanded = isExpanded,
        leftContent = preferences,
        rightContent = logs,
    )
}

private data class PermissionActionItem(
    val title: String,
    val onClick: () -> Unit,
)

@Composable
fun PermissionSettingsSection(
    readiness: BlockingReadiness,
    text: AppStrings,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationAccessSettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
) {
    val permissionsReady = readiness.usageAccessReady &&
        readiness.overlayPermissionReady &&
        readiness.notificationPermissionReady &&
        readiness.notificationAccessReady &&
        readiness.exactAlarmReady
    val statusColor = if (permissionsReady) AppSafe else AppOver
    val sectionBackground = if (permissionsReady) {
        AppSafe.copy(alpha = 0.08f)
    } else {
        AppOver.copy(alpha = 0.055f)
    }
    val missingPermissions = listOfNotNull(
        if (!readiness.usageAccessReady) {
            PermissionActionItem(text.usageAccessReady, onOpenUsageAccessSettings)
        } else {
            null
        },
        if (!readiness.overlayPermissionReady) {
            PermissionActionItem(text.overlayPermission, onOpenOverlaySettings)
        } else {
            null
        },
        if (!readiness.notificationPermissionReady) {
            PermissionActionItem(text.notificationPermission, onRequestNotificationPermission)
        } else {
            null
        },
        if (!readiness.notificationAccessReady) {
            PermissionActionItem(text.notificationAccessPermission, onOpenNotificationAccessSettings)
        } else {
            null
        },
        if (!readiness.exactAlarmReady) {
            PermissionActionItem(text.exactAlarmPermission, onOpenExactAlarmSettings)
        } else {
            null
        },
    )

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = sectionBackground,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = if (permissionsReady) {
            null
        } else {
            BorderStroke(1.dp, statusColor.copy(alpha = 0.22f))
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text.permissionSetupTitle,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                CompactStatusBadge(
                    label = if (permissionsReady) text.ready else text.notReady,
                    status = if (permissionsReady) LimitStatus.Normal else LimitStatus.Exceeded,
                )
            }
            Text(
                if (permissionsReady) text.permissionSettingsComplete else text.permissionSettingsRequired,
                style = MaterialTheme.typography.bodySmall,
                color = if (permissionsReady) AppSafe else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (permissionsReady) FontWeight.SemiBold else FontWeight.Normal,
            )

            if (!permissionsReady) {
                Surface(
                    shape = RoundedCornerShape(15.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        missingPermissions.forEachIndexed { index, item ->
                            PermissionActionRow(
                                title = item.title,
                                text = text,
                                onClick = item.onClick,
                            )
                            if (index != missingPermissions.lastIndex) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f),
                                        ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionActionRow(
    title: String,
    text: AppStrings,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 42.dp)
            .padding(horizontal = 2.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            modifier = Modifier.size(7.dp),
            shape = CircleShape,
            color = AppOver.copy(alpha = 0.62f),
        ) {}
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier
                .height(32.dp)
                .widthIn(min = 84.dp, max = 104.dp),
            shape = RoundedCornerShape(999.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                contentColor = MaterialTheme.colorScheme.primary,
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
        ) {
            Text(
                text.allowPermission,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun ParentManagementSection(
    parentState: ParentManagementState,
    installedApps: List<InstalledAppInfo>,
    policySummary: PolicySummary,
    text: AppStrings,
    onPairParentAccount: (String, String, String) -> Unit,
    onUnlinkParentAccount: (String) -> Unit,
    onSyncParentDevice: () -> Unit,
    onClearRemoteParentCommands: (String) -> Unit,
    onRemoteAppExtraTime: (String, String, Int) -> Unit,
    onRemoteAppUnlockToday: (String, String) -> Unit,
    onRemoteTotalExtraTime: (Int) -> Unit,
    onRemoteTotalUnlockToday: () -> Unit,
) {
    var parentAccount by remember(parentState.parentAccountId) {
        mutableStateOf(parentState.parentAccountId.ifBlank { "parent@example.com" })
    }
    var childDeviceName by remember(parentState.childDeviceName) {
        mutableStateOf(parentState.childDeviceName.ifBlank { "Tablet" })
    }
    var adminPin by remember { mutableStateOf("") }
    var selectedScope by remember { mutableStateOf(RemoteCommandScope.App) }
    var selectedPackageName by remember { mutableStateOf("") }
    var extraMinutes by remember { mutableStateOf(5) }

    val limitedPackageNames = policySummary.appLimitSummaries.map { summary -> summary.packageName }.toSet()
    val candidateApps = installedApps
        .filter { app -> app.packageName !in SafetyGate.neverBlockPackages }
        .sortedWith(
            compareByDescending<InstalledAppInfo> { app -> app.packageName in limitedPackageNames }
                .thenBy { app -> app.appName.lowercase() },
        )
        .take(24)
    val selectedApp = candidateApps.firstOrNull { app -> app.packageName == selectedPackageName }
        ?: candidateApps.firstOrNull()

    LaunchedEffect(candidateApps) {
        if (selectedPackageName.isBlank() || candidateApps.none { app -> app.packageName == selectedPackageName }) {
            selectedPackageName = candidateApps.firstOrNull()?.packageName.orEmpty()
        }
    }

    SimpleCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(text.parentManagement, Modifier.weight(1f))
            StatusBadge(
                label = if (parentState.paired) text.parentLinked else text.parentNotLinked,
                status = if (parentState.paired) LimitStatus.Normal else LimitStatus.Warning,
            )
        }
        Text(
            text.parentManagementDescription,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text.adminPinRole, style = MaterialTheme.typography.bodyMedium)
                Text(text.emergencyPinRole, style = MaterialTheme.typography.bodyMedium)
            }
        }

        OutlinedTextField(
            value = parentAccount,
            onValueChange = { parentAccount = it },
            label = { Text(text.parentAccount) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
        )
        OutlinedTextField(
            value = childDeviceName,
            onValueChange = { childDeviceName = it },
            label = { Text(text.childDeviceName) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
        )
        SecurePinTextField(
            value = adminPin,
            onValueChange = { adminPin = it },
            label = text.adminPin,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    onPairParentAccount(parentAccount, childDeviceName, adminPin)
                    adminPin = ""
                },
                enabled = parentAccount.isNotBlank() && adminPin.length >= 4,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(text.connectParent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(
                onClick = {
                    onUnlinkParentAccount(adminPin)
                    adminPin = ""
                },
                enabled = parentState.paired && adminPin.length >= 4,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(text.unlinkParent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(
                onClick = onSyncParentDevice,
                enabled = parentState.paired,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.weight(0.82f),
            ) {
                Text(text.syncNow, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        ParentLinkDetailRow(text.childDeviceId, parentState.childDeviceId.ifBlank { "-" })
        ParentLinkDetailRow(
            text.lastSync,
            if (parentState.lastSyncMillis > 0L) formatClockTime(parentState.lastSyncMillis) else "-",
        )

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text.remoteTestMode,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    LimitTimeChip(formatLimitMinutesLabel(extraMinutes))
                }
                Text(
                    if (parentState.paired) text.parentLinkHint else text.parentCommandRequiresLink,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceButton(
                        label = text.remoteAppTarget,
                        selected = selectedScope == RemoteCommandScope.App,
                        onClick = { selectedScope = RemoteCommandScope.App },
                    )
                    ChoiceButton(
                        label = text.remoteDailyLimit,
                        selected = selectedScope == RemoteCommandScope.Total,
                        onClick = { selectedScope = RemoteCommandScope.Total },
                    )
                }
                if (selectedScope == RemoteCommandScope.App) {
                    if (candidateApps.isEmpty()) {
                        Text(
                            text.noSelectableApps,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(candidateApps, key = { app -> app.packageName }) { app ->
                                ChoiceButton(
                                    label = app.appName,
                                    selected = app.packageName == selectedApp?.packageName,
                                    onClick = { selectedPackageName = app.packageName },
                                )
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 5, 15, 30, 60).forEach { minutes ->
                        ChoiceButton(
                            label = formatLimitMinutesLabel(minutes),
                            selected = extraMinutes == minutes,
                            onClick = { extraMinutes = minutes },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            if (selectedScope == RemoteCommandScope.Total) {
                                onRemoteTotalExtraTime(extraMinutes)
                            } else {
                                selectedApp?.let { app ->
                                    onRemoteAppExtraTime(app.packageName, app.appName, extraMinutes)
                                }
                            }
                        },
                        enabled = parentState.paired && (selectedScope == RemoteCommandScope.Total || selectedApp != null),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text.remoteAddTime, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = {
                            if (selectedScope == RemoteCommandScope.Total) {
                                onRemoteTotalUnlockToday()
                            } else {
                                selectedApp?.let { app ->
                                    onRemoteAppUnlockToday(app.packageName, app.appName)
                                }
                            }
                        },
                        enabled = parentState.paired && (selectedScope == RemoteCommandScope.Total || selectedApp != null),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text.remoteUnlockToday, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        RemoteCommandHistory(
            commands = parentState.remoteCommands,
            adminPin = adminPin,
            text = text,
            onAdminPinChanged = { adminPin = it },
            onClearRemoteParentCommands = {
                onClearRemoteParentCommands(adminPin)
                adminPin = ""
            },
        )
    }
}

private enum class RemoteCommandScope {
    App,
    Total,
}

@Composable
private fun ParentLinkDetailRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RemoteCommandHistory(
    commands: List<RemoteParentCommand>,
    adminPin: String,
    text: AppStrings,
    onAdminPinChanged: (String) -> Unit,
    onClearRemoteParentCommands: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text.remoteCommands,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                OutlinedButton(
                    onClick = onClearRemoteParentCommands,
                    enabled = commands.isNotEmpty() && adminPin.length >= 4,
                    shape = RoundedCornerShape(999.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(34.dp),
                ) {
                    Text(text.clear, maxLines = 1)
                }
            }
            if (commands.isEmpty()) {
                Text(
                    text.noRemoteCommands,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                commands.take(5).forEach { command ->
                    RemoteCommandRow(command = command, text = text)
                }
                SecurePinTextField(
                    value = adminPin,
                    onValueChange = onAdminPinChanged,
                    label = text.adminPin,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                )
            }
        }
    }
}

@Composable
private fun RemoteCommandRow(command: RemoteParentCommand, text: AppStrings) {
    val status = when (command.status) {
        RemoteParentCommandStatus.Applied -> LimitStatus.Normal
        RemoteParentCommandStatus.Pending -> LimitStatus.Warning
        RemoteParentCommandStatus.Failed -> LimitStatus.Exceeded
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                command.message.ifBlank { text.remoteCommandLabel(command) },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                formatClockTime(command.timestampMillis),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StatusBadge(label = command.status.name.lowercase(), status = status)
    }
}

private fun AppStrings.remoteCommandLabel(command: RemoteParentCommand): String {
    val target = command.targetAppName.ifBlank { command.targetPackageName }
    return when (command.type) {
        RemoteParentCommandType.AddAppTime -> "${remoteAddTime} ${formatLimitMinutesLabel(command.minutes)} ${target}"
        RemoteParentCommandType.UnlockAppToday -> "${remoteUnlockToday} ${target}"
        RemoteParentCommandType.AddTotalTime -> "${remoteDailyLimit} +${formatLimitMinutesLabel(command.minutes)}"
        RemoteParentCommandType.UnlockTotalToday -> "${remoteDailyLimit} ${remoteUnlockToday}"
    }
}

@Composable
private fun CompactStatusBadge(label: String, status: LimitStatus) {
    val color = when (status) {
        LimitStatus.Normal -> AppSafe.copy(alpha = 0.18f)
        LimitStatus.Warning -> AppWarn.copy(alpha = 0.18f)
        LimitStatus.Exceeded -> AppOver.copy(alpha = 0.16f)
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = color,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
fun NotificationPreferenceRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun AlwaysAllowedAppsSection(
    installedApps: List<InstalledAppInfo>,
    allowedAppPackages: Set<String>,
    text: AppStrings,
    onAllowedAppsChanged: (Set<String>) -> Unit,
) {
    SimpleCard {
        AlwaysAllowedAppsContent(
            installedApps = installedApps,
            allowedAppPackages = allowedAppPackages,
            text = text,
            onAllowedAppsChanged = onAllowedAppsChanged,
        )
    }
}

@Composable
fun ColumnScope.AlwaysAllowedAppsContent(
    installedApps: List<InstalledAppInfo>,
    allowedAppPackages: Set<String>,
    text: AppStrings,
    onAllowedAppsChanged: (Set<String>) -> Unit,
) {
    val userAllowedPackages = allowedAppPackages - SafetyGate.neverBlockPackages
    var listsExpanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionTitle(text.alwaysAllowedApps, Modifier.weight(1f))
        StatusBadge(text.selectedApps(userAllowedPackages.size), LimitStatus.Normal)
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            onClick = { listsExpanded = !listsExpanded },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
        ) {
            Text(
                if (listsExpanded) text.hideList else text.showList,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
    Text(
        text.alwaysAllowedDescription,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (!listsExpanded) {
        return
    }

    Text(
        text.requiredAllowedApps,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
    val requiredPackageNames = remember {
        SafetyGate.neverBlockPackages.sorted()
    }
    ContainedLazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 220.dp),
        resetKey = requiredPackageNames,
    ) {
        items(requiredPackageNames, key = { packageName -> packageName }) { packageName ->
            RequiredAllowedPackageRow(packageName = packageName)
        }
    }

    Text(
        text.userAllowedApps,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
    val sortedApps = remember(installedApps, userAllowedPackages) {
        installedApps
            .filterNot { app -> app.packageName in SafetyGate.neverBlockPackages }
            .sortedWith(
                compareByDescending<InstalledAppInfo> { app -> app.packageName in userAllowedPackages }
                    .thenByDescending { app -> app.packageName in SafetyGate.communicationAppPackages }
                    .thenBy { app -> app.appName.lowercase() },
            )
    }
    if (sortedApps.isEmpty()) {
        Text(text.noSelectableApps, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        ContainedLazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp),
            resetKey = sortedApps.map { app -> app.packageName } + userAllowedPackages.sorted(),
        ) {
            items(sortedApps, key = { app -> app.packageName }) { app ->
                val selected = app.packageName in userAllowedPackages
                UserAllowedAppRow(
                    app = app,
                    selected = selected,
                    text = text,
                    onToggle = {
                        val nextPackages = if (selected) {
                            userAllowedPackages - app.packageName
                        } else {
                            userAllowedPackages + app.packageName
                        }
                        onAllowedAppsChanged(nextPackages)
                    },
                )
            }
        }
    }
}

@Composable
private fun RequiredAllowedPackageRow(packageName: String) {
    val context = LocalContext.current
    val appName = remember(packageName) {
        runCatching {
            val packageManager = context.packageManager
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(applicationInfo).toString()
        }.getOrDefault(packageName)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(packageName = packageName, contentDescription = appName, size = 34.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                appName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        StatusBadge("LOCK", LimitStatus.Normal)
    }
}

@Composable
private fun UserAllowedAppRow(
    app: InstalledAppInfo,
    selected: Boolean,
    text: AppStrings,
    enabled: Boolean = true,
    statusLabel: String = if (selected) text.allowed else text.allow,
    onToggle: () -> Unit,
) {
    Column {
        Surface(
            onClick = { if (enabled) onToggle() },
            shape = RoundedCornerShape(18.dp),
            color = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(packageName = app.packageName, contentDescription = app.appName, size = 36.dp)
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        app.appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        statusLabel,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        )
    }
}

@Composable
fun SecurePinTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions(
        keyboardType = KeyboardType.NumberPassword,
        imeAction = ImeAction.Done,
    ),
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    shape: RoundedCornerShape = RoundedCornerShape(18.dp),
    colors: TextFieldColors? = null,
) {
    var showPin by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        trailingIcon = {
            PinVisibilityToggleButton(
                visible = showPin,
                enabled = enabled,
                onToggle = { showPin = !showPin },
            )
        },
        enabled = enabled,
        isError = isError,
        supportingText = supportingText,
        singleLine = true,
        shape = shape,
        colors = colors ?: OutlinedTextFieldDefaults.colors(),
        modifier = modifier,
    )
}

@Composable
fun PinVisibilityToggleButton(
    visible: Boolean,
    enabled: Boolean = true,
    onToggle: () -> Unit,
) {
    Surface(
        onClick = onToggle,
        enabled = enabled,
        shape = CircleShape,
        color = Color.Transparent,
        modifier = Modifier.size(40.dp),
    ) {
        Canvas(modifier = Modifier.padding(9.dp)) {
            val strokeWidth = 1.8.dp.toPx()
            val iconColor = if (enabled) Color(0xFF6B7280) else Color(0xFFB8BDC7)
            drawOval(
                color = iconColor,
                topLeft = Offset(size.width * 0.08f, size.height * 0.24f),
                size = Size(size.width * 0.84f, size.height * 0.52f),
                style = Stroke(width = strokeWidth),
            )
            drawCircle(
                color = iconColor,
                radius = size.minDimension * 0.15f,
                center = Offset(size.width / 2f, size.height / 2f),
            )
            if (!visible) {
                drawLine(
                    color = iconColor,
                    start = Offset(size.width * 0.16f, size.height * 0.88f),
                    end = Offset(size.width * 0.84f, size.height * 0.12f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

private enum class PinFeedbackTarget {
    Admin,
    Emergency,
}

@Composable
fun PinChangeFields(
    currentPin: String,
    newPin: String,
    currentLabel: String,
    newLabel: String,
    onCurrentChanged: (String) -> Unit,
    onNewChanged: (String) -> Unit,
    onSave: () -> Unit,
    status: PinChangeStatus,
    text: AppStrings,
) {
    val focusManager = LocalFocusManager.current
    val isFailureStatus = status == PinChangeStatus.TooShort ||
        status == PinChangeStatus.SameAsCurrent ||
        status == PinChangeStatus.InvalidCurrentPin ||
        status == PinChangeStatus.Failed
    val feedbackContainerColor = when (status) {
        PinChangeStatus.Changed -> AppSafe.copy(alpha = 0.16f)
        PinChangeStatus.TooShort,
        PinChangeStatus.SameAsCurrent,
        PinChangeStatus.InvalidCurrentPin,
        PinChangeStatus.Failed -> AppOver.copy(alpha = 0.16f)
        PinChangeStatus.Idle -> MaterialTheme.colorScheme.surface
    }
    val feedbackBorderColor = when (status) {
        PinChangeStatus.Changed -> AppSafe
        PinChangeStatus.TooShort,
        PinChangeStatus.SameAsCurrent,
        PinChangeStatus.InvalidCurrentPin,
        PinChangeStatus.Failed -> AppOver
        PinChangeStatus.Idle -> MaterialTheme.colorScheme.outline
    }
    val canSave = currentPin.length >= 4 && newPin.length >= 4

    fun saveAndHideKeyboard() {
        focusManager.clearFocus()
        onSave()
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SecurePinTextField(
            value = currentPin,
            onValueChange = { value -> onCurrentChanged(value.filter { character -> character.isDigit() }.take(12)) },
            label = currentLabel,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        SecurePinTextField(
            value = newPin,
            onValueChange = { value -> onNewChanged(value.filter { character -> character.isDigit() }.take(12)) },
            label = newLabel,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (canSave) {
                        saveAndHideKeyboard()
                    } else {
                        focusManager.clearFocus()
                    }
                },
            ),
            isError = isFailureStatus,
            supportingText = {
                Text(
                    when (status) {
                        PinChangeStatus.Idle -> text.pinChangeIdle
                        PinChangeStatus.Changed -> text.pinChanged
                        PinChangeStatus.TooShort -> text.pinTooShort
                        PinChangeStatus.SameAsCurrent -> text.pinSameAsCurrent
                        PinChangeStatus.InvalidCurrentPin -> text.pinInvalidCurrent
                        PinChangeStatus.Failed -> text.pinChangeFailed
                    },
                    color = when (status) {
                        PinChangeStatus.Changed -> AppSafe
                        PinChangeStatus.TooShort,
                        PinChangeStatus.SameAsCurrent,
                        PinChangeStatus.InvalidCurrentPin,
                        PinChangeStatus.Failed -> AppOver
                        PinChangeStatus.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = feedbackContainerColor,
                unfocusedContainerColor = feedbackContainerColor,
                errorContainerColor = feedbackContainerColor,
                focusedBorderColor = feedbackBorderColor,
                unfocusedBorderColor = feedbackBorderColor,
                errorBorderColor = feedbackBorderColor,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = {
                saveAndHideKeyboard()
            },
            enabled = canSave,
        ) {
            Text(text.savePolicy)
        }
    }
}

@Composable
fun BlockingReadinessSection(
    readiness: BlockingReadiness,
    text: AppStrings,
) {
    SimpleCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(text.blockingReadiness, Modifier.weight(1f))
            StatusBadge(
                if (readiness.readyForBlocking) text.ready else text.notReady,
                if (readiness.readyForBlocking) LimitStatus.Normal else LimitStatus.Warning,
            )
        }
        ReadinessRow(text.safeModeAllowsBlocking, readiness.safeModeAllowsBlocking)
        ReadinessRow(text.policyEnforcementReady, readiness.policyEnforcementReady)
        ReadinessRow(text.usageAccessReady, readiness.usageAccessReady)
        ReadinessRow(text.overlayPermission, readiness.overlayPermissionReady)
        ReadinessRow(text.notificationPermission, readiness.notificationPermissionReady)
        ReadinessRow(text.notificationAccessPermission, readiness.notificationAccessReady)
        ReadinessRow(text.exactAlarmPermission, readiness.exactAlarmReady)
        ReadinessRow(text.whitelistReady, readiness.whitelistReady)
        ReadinessRow(text.emergencyUnlockReady, readiness.emergencyUnlockReady)
    }
}

@Composable
fun ReadinessRow(label: String, ready: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        StatusBadge(
            if (ready) "OK" else "WAIT",
            if (ready) LimitStatus.Normal else LimitStatus.Warning,
        )
    }
}

@Composable
fun SystemHealthStatusSection(
    healthStatus: SystemHealthStatus,
    text: AppStrings,
) {
    val now = System.currentTimeMillis()
    val checkedAgeMillis = healthStatus.lastCheckedMillis
        .takeIf { timestamp -> timestamp > 0L }
        ?.let { timestamp -> (now - timestamp).coerceAtLeast(0L) }
    val status = when {
        healthStatus.lastCheckedMillis <= 0L -> LimitStatus.Warning
        healthStatus.allReady -> LimitStatus.Normal
        else -> LimitStatus.Exceeded
    }
    SimpleCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(text.systemHealthStatus, Modifier.weight(1f))
            StatusBadge(
                if (healthStatus.allReady) text.ready else text.notReady,
                status,
            )
        }
        SafetyStatusRow(
            title = text.systemHealthLastCheck,
            supportingText = if (healthStatus.lastCheckedMillis > 0L) {
                "${formatClockTime(healthStatus.lastCheckedMillis)} - ${formatMonitorAge(checkedAgeMillis ?: 0L)}"
            } else {
                text.systemHealthNotChecked
            },
            statusLabel = if (healthStatus.lastCheckedMillis > 0L) text.ready else text.notReady,
            status = if (healthStatus.lastCheckedMillis > 0L) LimitStatus.Normal else LimitStatus.Warning,
        )
        ReadinessRow(text.usageAccessReady, healthStatus.usageAccessReady)
        ReadinessRow(text.overlayPermission, healthStatus.overlayPermissionReady)
        ReadinessRow(text.notificationPermission, healthStatus.notificationPermissionReady)
        ReadinessRow(text.notificationAccessPermission, healthStatus.notificationAccessReady)
        ReadinessRow(text.exactAlarmPermission, healthStatus.exactAlarmReady)
        ReadinessRow(text.foregroundServiceHealth, !healthStatus.foregroundServiceExpected || (healthStatus.foregroundServiceRunning && healthStatus.foregroundServiceFresh))
        SafetyStatusRow(
            title = text.systemHealthIssue,
            supportingText = healthStatus.lastIssue.ifBlank { text.systemHealthNoIssue },
            statusLabel = if (healthStatus.lastIssue.isBlank()) text.ready else text.notReady,
            status = if (healthStatus.lastIssue.isBlank()) LimitStatus.Normal else LimitStatus.Exceeded,
        )
    }
}

@Composable
fun MonitorStatusSection(
    monitorStatus: UsageMonitorStatus,
    text: AppStrings,
) {
    val now = System.currentTimeMillis()
    val lastTickAgeMillis = monitorStatus.lastTickMillis
        .takeIf { timestamp -> timestamp > 0L }
        ?.let { timestamp -> (now - timestamp).coerceAtLeast(0L) }
    val status = when {
        !monitorStatus.running -> LimitStatus.Warning
        lastTickAgeMillis == null -> LimitStatus.Warning
        lastTickAgeMillis > MONITOR_STALE_WARNING_MILLIS -> LimitStatus.Exceeded
        else -> LimitStatus.Normal
    }
    val statusLabel = when (status) {
        LimitStatus.Normal -> text.monitorRunning
        LimitStatus.Warning -> if (monitorStatus.running) text.monitorDelayed else text.monitorStopped
        LimitStatus.Exceeded -> text.monitorDelayed
    }

    SimpleCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(text.usageMonitorStatus, Modifier.weight(1f))
            StatusBadge(statusLabel, status)
        }

        SafetyStatusRow(
            title = text.lastMonitorTick,
            supportingText = if (monitorStatus.lastTickMillis > 0L) {
                "${formatClockTime(monitorStatus.lastTickMillis)} - ${formatMonitorAge(lastTickAgeMillis ?: 0L)}"
            } else {
                text.noMonitorTick
            },
            statusLabel = statusLabel,
            status = status,
        )

        if (monitorStatus.lastForegroundPackageName.isNotBlank()) {
            val decisionLabel = if (monitorStatus.lastDecision.isBlank()) {
                text.monitorForegroundApp
            } else {
                text.detectionDecision(monitorStatus.lastDecision)
            }
            AppRow(
                appName = monitorStatus.lastForegroundAppName.ifBlank { monitorStatus.lastForegroundPackageName },
                packageName = monitorStatus.lastForegroundPackageName,
                supportingText = monitorStatus.lastDecision.ifBlank { text.monitorForegroundApp },
                trailingContent = {
                    StatusBadge(
                        decisionLabel,
                        monitorStatus.lastDecision.toDetectionLimitStatus(),
                    )
                },
            )
        } else {
            Text(text.noDetectionStatus, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SafetyStatusRow(
            title = text.monitorLimitTarget,
            supportingText = monitorStatus.lastForegroundPackageName.ifBlank { text.noDetectionStatus },
            statusLabel = if (monitorStatus.lastLimitedTarget) text.monitorTarget else text.monitorNotTarget,
            status = if (monitorStatus.lastLimitedTarget) LimitStatus.Normal else LimitStatus.Warning,
        )

        SafetyStatusRow(
            title = text.monitorUsageCounter,
            supportingText = formatMonitorUsageCounter(
                usedMillis = monitorStatus.lastUsedMillis,
                limitMillis = monitorStatus.lastLimitMillis,
            ),
            statusLabel = if (monitorStatus.lastLimitMillis > 0L) text.ready else text.noLimit,
            status = if (monitorStatus.lastLimitMillis > 0L) LimitStatus.Normal else LimitStatus.Warning,
        )

        SafetyStatusRow(
            title = text.monitorBlockReason,
            supportingText = if (monitorStatus.lastBlockReason.isBlank()) {
                text.noBlockAttempt
            } else {
                text.detectionDecision(monitorStatus.lastBlockReason)
            },
            statusLabel = if (monitorStatus.lastBlockReason.isBlank()) text.monitorNotTarget else text.monitorTarget,
            status = if (monitorStatus.lastBlockReason.isBlank()) LimitStatus.Warning else LimitStatus.Exceeded,
        )

        SafetyStatusRow(
            title = text.monitorOverlayResult,
            supportingText = if (monitorStatus.lastBlockAttemptMillis > 0L) {
                "${formatClockTime(monitorStatus.lastBlockAttemptMillis)} - ${text.monitorRetryCount} ${monitorStatus.blockRetryCount}"
            } else {
                text.noBlockAttempt
            },
            statusLabel = when {
                monitorStatus.lastBlockAttemptMillis <= 0L -> text.monitorStopped
                monitorStatus.lastOverlayAttached -> text.monitorOverlayShown
                else -> text.monitorOverlayFailed
            },
            status = when {
                monitorStatus.lastBlockAttemptMillis <= 0L -> LimitStatus.Warning
                monitorStatus.lastOverlayAttached -> LimitStatus.Normal
                else -> LimitStatus.Exceeded
            },
        )

        SafetyStatusRow(
            title = text.monitorLastBlockAttempt,
            supportingText = if (monitorStatus.lastBlockAttemptMillis > 0L) {
                "${formatClockTime(monitorStatus.lastBlockAttemptMillis)} - ${formatMonitorAge((now - monitorStatus.lastBlockAttemptMillis).coerceAtLeast(0L))}"
            } else {
                text.noBlockAttempt
            },
            statusLabel = if (monitorStatus.lastBlockAttemptMillis > 0L) text.ready else text.monitorStopped,
            status = if (monitorStatus.lastBlockAttemptMillis > 0L) LimitStatus.Normal else LimitStatus.Warning,
        )

        SafetyStatusRow(
            title = text.monitorRetryCount,
            supportingText = monitorStatus.blockRetryCount.coerceAtLeast(0).toString(),
            statusLabel = monitorStatus.blockRetryCount.coerceAtLeast(0).toString(),
            status = if (monitorStatus.blockRetryCount <= 1) LimitStatus.Normal else LimitStatus.Warning,
        )

        if (monitorStatus.lastRecoveryMillis > 0L) {
            SafetyStatusRow(
                title = text.lastMonitorRecovery,
                supportingText = "${formatClockTime(monitorStatus.lastRecoveryMillis)} - ${monitorStatus.lastRecoveryReason}",
                statusLabel = text.ready,
                status = LimitStatus.Normal,
            )
        }

        if (!monitorStatus.running && monitorStatus.lastStopReason.isNotBlank()) {
            SafetyStatusRow(
                title = text.monitorStopReason,
                supportingText = monitorStatus.lastStopReason,
                statusLabel = text.monitorStopped,
                status = LimitStatus.Warning,
            )
        }
    }
}

@Composable
fun UsageConsistencySection(
    monitorStatus: UsageMonitorStatus,
    todayUsage: List<AppUsageInfo>,
    text: AppStrings,
) {
    val monitoredPackage = monitorStatus.lastForegroundPackageName
    val monitoredUsageMillis = monitorStatus.lastUsedMillis.coerceAtLeast(0L)
    val todayUsageForPackage = todayUsage.firstOrNull { usage -> usage.packageName == monitoredPackage }
    val todayUsageMillis = todayUsageForPackage?.totalTimeMillis?.coerceAtLeast(0L) ?: 0L
    val hasMonitoredApp = monitoredPackage.isNotBlank() && monitoredUsageMillis > 0L
    val deltaMillis = kotlin.math.abs(monitoredUsageMillis - todayUsageMillis)
    val aligned = hasMonitoredApp && deltaMillis <= USAGE_CONSISTENCY_TOLERANCE_MILLIS
    val status = when {
        !hasMonitoredApp -> LimitStatus.Warning
        aligned -> LimitStatus.Normal
        else -> LimitStatus.Exceeded
    }
    val statusLabel = when {
        !hasMonitoredApp -> text.usageConsistencyIdle
        aligned -> text.usageConsistencyAligned
        else -> text.usageConsistencyMismatch
    }
    val appName = monitorStatus.lastForegroundAppName
        .ifBlank { todayUsageForPackage?.appName ?: monitoredPackage }

    SimpleCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(text.usageConsistency, Modifier.weight(1f))
            StatusBadge(statusLabel, status)
        }
        if (!hasMonitoredApp) {
            Text(text.usageConsistencyIdle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            AppRow(
                appName = appName,
                packageName = monitoredPackage,
                supportingText = text.usageDelta(formatMonitorAge(deltaMillis)),
                trailingContent = {
                    StatusBadge(statusLabel, status)
                },
            )
            SafetyStatusRow(
                title = text.monitorUsageSource,
                supportingText = formatDuration(monitoredUsageMillis),
                statusLabel = text.ready,
                status = LimitStatus.Normal,
            )
            SafetyStatusRow(
                title = text.appUsageSource,
                supportingText = if (todayUsageForPackage != null) {
                    formatDuration(todayUsageMillis)
                } else {
                    text.noUsageRecorded
                },
                statusLabel = if (todayUsageForPackage != null) text.ready else text.notReady,
                status = if (todayUsageForPackage != null) LimitStatus.Normal else LimitStatus.Warning,
            )
        }
    }
}

@Composable
fun BlockSafetyStatusSection(
    results: List<BlockDecisionResult>,
    temporaryUnlockState: TemporaryUnlockState,
    policySummary: PolicySummary,
    installedApps: List<InstalledAppInfo>,
    text: AppStrings,
) {
    val blockTargets = results.filter { result -> result.decision.isWouldBlockDecision() }
    val todayTemporaryState = temporaryUnlockState.forToday()
    val installedNameByPackage = installedApps.associate { app -> app.packageName to app.appName }
    val appSummaryByPackage = policySummary.appLimitSummaries.associateBy { summary -> summary.packageName }
    val groupSummaryByPackage = policySummary.groupSummaries
        .flatMap { groupSummary -> groupSummary.packageNames.map { packageName -> packageName to groupSummary } }
        .toMap()

    SimpleCard {
        SectionTitle(text.blockSafetyStatus)

        if (policySummary.scheduleSummaries.isNotEmpty()) {
            Text(
                text.scheduleDiagnostics,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            policySummary.activeScheduleSummary?.let { scheduleSummary ->
                SafetyStatusRow(
                    title = scheduleSummary.name.ifBlank { text.scheduleBlocking },
                    supportingText = "${formatScheduleWindow(scheduleSummary.startMinutes, scheduleSummary.endMinutes, text)} / ${scheduleDaysSummary(scheduleSummary.days, text)} / ${text.allowedAppCount(scheduleSummary.allowedAppCount)}",
                    statusLabel = text.activeSchedule,
                    status = LimitStatus.Exceeded,
                )
            }
            policySummary.nextScheduleSummary
                ?.takeIf { scheduleSummary -> policySummary.activeScheduleSummary?.id != scheduleSummary.id }
                ?.let { scheduleSummary ->
                    SafetyStatusRow(
                        title = scheduleSummary.name.ifBlank { text.scheduleBlocking },
                        supportingText = "${formatScheduleWindow(scheduleSummary.startMinutes, scheduleSummary.endMinutes, text)} / ${scheduleDaysSummary(scheduleSummary.days, text)} / ${text.allowedAppCount(scheduleSummary.allowedAppCount)}",
                        statusLabel = scheduleSummary.minutesUntilStart?.let { minutes ->
                            text.startsIn(formatLimitMinutesLabel(minutes))
                        } ?: text.nextSchedule,
                        status = LimitStatus.Warning,
                    )
                }
            if (policySummary.activeScheduleSummary == null && policySummary.nextScheduleSummary == null) {
                Text(text.noActiveSchedule, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Text(
            text.currentBlockTargets,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        if (blockTargets.isEmpty()) {
            Text(text.noCurrentBlockTargets, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            blockTargets.take(6).forEach { result ->
                val usageText = listOfNotNull(
                    formatLimitMinutesLabel(result.usedMinutes),
                    result.limitMinutes?.let { limitMinutes -> formatLimitMinutesLabel(limitMinutes) },
                ).joinToString(" / ")
                if (result.decision == BlockDecision.WouldBlockTotalLimit) {
                    SafetyStatusRow(
                        title = text.dailyLimit,
                        supportingText = "${text.blockDecision(result.decision)} - $usageText",
                        statusLabel = text.blockDecision(result.decision),
                        status = LimitStatus.Exceeded,
                    )
                } else {
                    AppRow(
                        appName = result.appName,
                        packageName = result.packageName,
                        supportingText = "${text.blockDecision(result.decision)} - $usageText",
                        trailingContent = {
                            StatusBadge(text.blockDecision(result.decision), LimitStatus.Exceeded)
                        },
                    )
                }
            }
        }

        Text(
            text.temporaryAllowances,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        val hasTemporaryTotalAllowance = todayTemporaryState.totalUnlockedForToday ||
            todayTemporaryState.totalExtraMinutes > 0
        val hasTemporaryPackageAllowance = todayTemporaryState.packageAllowances.any { (_, allowance) ->
            allowance.unlockedForToday || allowance.extraMinutes > 0
        }
        if (!hasTemporaryTotalAllowance && !hasTemporaryPackageAllowance) {
            Text(text.noTemporaryAllowances, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            if (hasTemporaryTotalAllowance) {
                val remaining = remainingTemporaryMinutes(
                    baseLimitMinutes = policySummary.totalLimitMinutes,
                    extraMinutes = todayTemporaryState.totalExtraMinutes,
                    usedMinutes = policySummary.totalUsedMinutes,
                )
                SafetyStatusRow(
                    title = text.dailyLimit,
                    supportingText = if (todayTemporaryState.totalUnlockedForToday) {
                        text.unlockedToday
                    } else {
                        text.temporaryAllowanceDetail(remaining, todayTemporaryState.totalExtraMinutes)
                    },
                    statusLabel = if (todayTemporaryState.totalUnlockedForToday) {
                        text.unlockedToday
                    } else {
                        "+${formatLimitMinutesLabel(todayTemporaryState.totalExtraMinutes)}"
                    },
                    status = LimitStatus.Normal,
                )
            }

            todayTemporaryState.packageAllowances
                .filter { (_, allowance) -> allowance.unlockedForToday || allowance.extraMinutes > 0 }
                .toSortedMap(compareBy { packageName ->
                    installedNameByPackage[packageName]
                        ?: appSummaryByPackage[packageName]?.appName
                        ?: packageName
                })
                .forEach { (packageName, allowance) ->
                    val appSummary = appSummaryByPackage[packageName]
                    val groupSummary = groupSummaryByPackage[packageName]
                    val appName = installedNameByPackage[packageName]
                        ?: appSummary?.appName
                        ?: packageName
                    val remaining = remainingTemporaryPackageMinutes(
                        extraMinutes = allowance.extraMinutes,
                        appSummary = appSummary,
                        groupSummary = groupSummary,
                    )
                    AppRow(
                        appName = appName,
                        packageName = packageName,
                        supportingText = if (allowance.unlockedForToday) {
                            text.unlockedToday
                        } else {
                            text.temporaryAllowanceDetail(remaining, allowance.extraMinutes)
                        },
                        trailingContent = {
                            StatusBadge(
                                if (allowance.unlockedForToday) {
                                    text.unlockedToday
                                } else {
                                    "+${formatLimitMinutesLabel(allowance.extraMinutes)}"
                                },
                                LimitStatus.Normal,
                            )
                        },
                    )
                }
        }
    }
}

@Composable
private fun SafetyStatusRow(
    title: String,
    supportingText: String,
    statusLabel: String,
    status: LimitStatus,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                supportingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StatusBadge(statusLabel, status)
    }
}

private fun remainingTemporaryMinutes(
    baseLimitMinutes: Int,
    extraMinutes: Int,
    usedMinutes: Int,
): Int {
    if (extraMinutes <= 0) {
        return 0
    }
    return (baseLimitMinutes + extraMinutes - usedMinutes).coerceIn(0, extraMinutes)
}

private fun remainingTemporaryPackageMinutes(
    extraMinutes: Int,
    appSummary: AppLimitSummary?,
    groupSummary: AppGroupSummary?,
): Int {
    if (extraMinutes <= 0) {
        return 0
    }
    if (appSummary != null) {
        return remainingTemporaryMinutes(
            baseLimitMinutes = appSummary.limitMinutes,
            extraMinutes = extraMinutes,
            usedMinutes = appSummary.usedMinutes,
        )
    }
    if (groupSummary != null) {
        return (groupSummary.limitMinutes + groupSummary.extraMinutes - groupSummary.usedMinutes)
            .coerceIn(0, extraMinutes)
    }
    return extraMinutes
}

@Composable
fun DetectionStatusSection(
    detectionStatus: ForegroundDetectionStatus?,
    text: AppStrings,
) {
    SimpleCard {
        SectionTitle(text.detectionStatus)
        Text(
            text.detectionStatusDescription,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (detectionStatus == null) {
            Text(text.noDetectionStatus, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            AppRow(
                appName = detectionStatus.appName,
                packageName = detectionStatus.packageName,
                supportingText = "${detectionStatus.packageName} - ${formatClockTime(detectionStatus.timestampMillis)}",
                trailingContent = {
                    StatusBadge(
                        text.detectionDecision(detectionStatus.decision),
                        detectionStatus.decision.toDetectionLimitStatus(),
                    )
                },
            )
        }
    }
}

private fun String.toDetectionLimitStatus(): LimitStatus {
    return if (contains("exceeded", ignoreCase = true) || contains("block", ignoreCase = true)) {
        LimitStatus.Exceeded
    } else {
        LimitStatus.Normal
    }
}

@Composable
fun BlockDecisionSimulationSection(
    results: List<BlockDecisionResult>,
    text: AppStrings,
) {
    SimpleCard {
        SectionTitle(text.blockSimulation)
        if (results.isEmpty()) {
            Text(text.noSimulationTargets, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            results.take(8).forEach { result ->
                AppRow(
                    appName = result.appName,
                    packageName = result.packageName,
                    supportingText = text.usedMinutes(result.usedMinutes),
                    trailingContent = {
                        val wouldBlock = result.decision.isWouldBlockDecision()
                        StatusBadge(
                            text.blockDecision(result.decision),
                            if (wouldBlock) LimitStatus.Exceeded else LimitStatus.Normal,
                        )
                    },
                )
            }
        }
    }
}

@Composable
fun BlockScreenPreviewSection(
    results: List<BlockDecisionResult>,
    text: AppStrings,
    onOpenPreview: (BlockDecisionResult) -> Unit,
) {
    val previewTarget = results.firstOrNull { result -> result.decision.isWouldBlockDecision() }

    SimpleCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(text.blockScreenPreview, Modifier.weight(1f))
            StatusBadge(text.previewOnly, LimitStatus.Warning)
        }

        if (previewTarget == null) {
            Text(text.noBlockPreviewTarget, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val isTotalLimitPreview = previewTarget.decision == BlockDecision.WouldBlockTotalLimit
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = AppOver.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, AppOver.copy(alpha = 0.3f)),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text.blockedTodayMessage,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    if (isTotalLimitPreview) {
                        Text(
                            text = listOfNotNull(
                                text.usedMinutes(previewTarget.usedMinutes),
                                previewTarget.limitMinutes?.let { limitMinutes -> formatLimitMinutesLabel(limitMinutes) },
                            ).joinToString(" / "),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        AppRow(
                            appName = previewTarget.appName,
                            packageName = previewTarget.packageName,
                            supportingText = "${text.usedMinutes(previewTarget.usedMinutes)} - ${text.blockDecision(previewTarget.decision)}",
                            trailingContent = {
                                StatusBadge(text.blockDecision(previewTarget.decision), LimitStatus.Exceeded)
                            },
                        )
                    }
                    Text(
                        "${text.remainingTime}: ${formatLimitMinutesLabel(0)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SecurePinTextField(
                        value = "",
                        onValueChange = {},
                        label = text.parentPin,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { onOpenPreview(previewTarget) },
                        enabled = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Text(text.openBlockScreenPreview)
                    }
                }
            }
        }
    }
}

private fun BlockDecision.isWouldBlockDecision(): Boolean {
    return this == BlockDecision.WouldBlockTotalLimit ||
        this == BlockDecision.WouldBlockSchedule ||
        this == BlockDecision.WouldBlockAllowOnly ||
        this == BlockDecision.WouldBlockGroupLimit ||
        this == BlockDecision.WouldBlockAppLimit
}

@Composable
fun EventLogSection(
    eventLog: List<EventLogEntry>,
    text: AppStrings,
    onClearEventLog: () -> Unit,
) {
    SimpleCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(text.eventLog, Modifier.weight(1f))
            OutlinedButton(onClick = onClearEventLog) {
                Text(text.clear)
            }
        }
        if (eventLog.isEmpty()) {
            Text(text.noEvents, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            eventLog.take(10).forEach { event ->
                Text("${formatClockTime(event.timestampMillis)}  ${event.message}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun UsageStatsSection(
    hasUsageAccess: Boolean,
    usageAccessChecking: Boolean,
    todayUsage: List<AppUsageInfo>,
    policySummary: PolicySummary,
    text: AppStrings,
    onRefreshUsageStats: () -> Unit,
) {
    SimpleCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(text.todayUsage, Modifier.weight(1f))
            if (hasUsageAccess) {
                CircleTextButton(label = "R", onClick = onRefreshUsageStats)
            }
        }

        if (todayUsage.isNotEmpty()) {
            val topUsage = todayUsage.first()
            val remainingUsage = todayUsage.drop(1)
            val totalUsageMillis = todayUsage.sumOf { appUsage -> appUsage.totalTimeMillis }.coerceAtLeast(1L)
            TodayUsageHero(
                appUsage = topUsage,
                text = text,
                totalUsageMillis = totalUsageMillis,
                status = policySummary.statusForPackage(topUsage.packageName),
            )
            if (remainingUsage.size > 4) {
                ContainedLazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    resetKey = todayUsage.map { usage -> usage.packageName },
                ) {
                    items(remainingUsage, key = { appUsage -> appUsage.packageName }) { appUsage ->
                        UsageListRow(
                            appUsage = appUsage,
                            topUsageMillis = topUsage.totalTimeMillis,
                            status = policySummary.statusForPackage(appUsage.packageName),
                        )
                    }
                }
            } else {
                remainingUsage.forEach { appUsage ->
                    UsageListRow(
                        appUsage = appUsage,
                        topUsageMillis = topUsage.totalTimeMillis,
                        status = policySummary.statusForPackage(appUsage.packageName),
                    )
                }
            }
        } else if (!hasUsageAccess) {
            Text(text.usageAccessRequired)
            Text(
                text.permissionSettingsInSettings,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (usageAccessChecking) {
            Text(text.usageAccessChecking, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (todayUsage.isEmpty()) {
            Text(text.noUsageRecorded)
        }
    }
}

@Composable
fun TodayUsageHero(
    appUsage: AppUsageInfo,
    text: AppStrings,
    totalUsageMillis: Long,
    status: LimitStatus,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(packageName = appUsage.packageName, contentDescription = appUsage.appName, size = 48.dp)
                Spacer(modifier = Modifier.width(18.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(appUsage.appName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Most used today", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    formatDuration(appUsage.totalTimeMillis),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    textAlign = TextAlign.End,
                    modifier = Modifier.widthIn(min = 84.dp),
                )
            }
            MiniUsageBar(
                fraction = appUsage.totalTimeMillis.toFloat() / totalUsageMillis.toFloat(),
                status = status,
                height = 10,
            )
        }
    }
}

@Composable
fun UsageListRow(appUsage: AppUsageInfo, topUsageMillis: Long, status: LimitStatus) {
    val fraction = if (topUsageMillis <= 0L) 0f else appUsage.totalTimeMillis.toFloat() / topUsageMillis.toFloat()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(packageName = appUsage.packageName, contentDescription = appUsage.appName, size = 40.dp)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(appUsage.appName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            MiniUsageBar(fraction = fraction, status = status, height = 5)
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            formatDuration(appUsage.totalTimeMillis),
            modifier = Modifier.widthIn(min = 72.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
fun MiniUsageBar(
    fraction: Float,
    status: LimitStatus,
    height: Int = 5,
    modifier: Modifier = Modifier,
) {
    val safeFraction = if (fraction.isFinite()) fraction.coerceIn(0f, 1f) else 0f
    GaugeBar(fraction = safeFraction, status = status, height = height, modifier = modifier)
}

fun PolicySummary.statusForPackage(packageName: String): LimitStatus {
    val appStatus = appLimitSummaries.firstOrNull { summary -> summary.packageName == packageName }?.status
    if (appStatus != null) {
        return appStatus
    }
    val groupStatus = groupSummaries.firstOrNull { summary -> packageName in summary.packageNames }?.status
    if (groupStatus != null) {
        return groupStatus
    }
    return totalStatus
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PolicySummarySection(summary: PolicySummary, text: AppStrings) {
    var selectedGroupSummary by remember { mutableStateOf<AppGroupSummary?>(null) }
    SimpleCard {
        SectionTitle(text.policySummary)
        if (summary.allowOnlyModeEnabled) {
            AllowOnlyPolicySummaryLine(summary = summary, text = text)
        }
        if (summary.scheduleSummaries.isNotEmpty()) {
            Text(text.scheduleBlocking, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            summary.scheduleSummaries.forEach { scheduleSummary ->
                val isActive = summary.activeScheduleSummary?.id == scheduleSummary.id
                val isNext = summary.nextScheduleSummary?.id == scheduleSummary.id
                SchedulePolicySummaryLine(
                    summary = scheduleSummary,
                    text = text,
                    status = when {
                        isActive -> LimitStatus.Exceeded
                        isNext -> LimitStatus.Warning
                        else -> LimitStatus.Normal
                    },
                    label = when {
                        isActive -> text.activeSchedule
                        isNext -> text.nextSchedule
                        else -> text.scheduleTemplate
                    },
                )
            }
        }
        if (summary.groupSummaries.isNotEmpty()) {
            Text(text.appGroups, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            summary.groupSummaries.forEach { groupSummary ->
                PolicyGroupSummaryLine(
                    summary = groupSummary,
                    text = text,
                    onClick = { selectedGroupSummary = groupSummary },
                )
            }
        }
        if (summary.appLimitSummaries.isEmpty()) {
            Text(text.noAppLimits, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(text.appLimits, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            summary.appLimitSummaries.forEach { appSummary ->
                PolicyAppSummaryLine(appSummary, text)
            }
        }
    }
    selectedGroupSummary?.let { groupSummary ->
        GroupSummarySheet(
            groupSummary = groupSummary,
            text = text,
            onDismiss = { selectedGroupSummary = null },
        )
    }
}

@Composable
fun AllowOnlyPolicySummaryLine(summary: PolicySummary, text: AppStrings) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GroupSummaryDot(status = LimitStatus.Warning)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text.allowOnlyMode,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text.allowOnlyModeSummary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            StatusBadge(text.allowedAppCount(summary.allowOnlyAllowedAppCount), LimitStatus.Warning)
        }
    }
}

@Composable
fun SchedulePolicySummaryLine(
    summary: ScheduleSummary,
    text: AppStrings,
    status: LimitStatus,
    label: String,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GroupSummaryDot(status = status)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    summary.name.ifBlank { text.scheduleBlocking },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                StatusBadge(label, status)
            }
            Text(
                "${formatScheduleWindow(summary.startMinutes, summary.endMinutes, text)} / ${scheduleDaysSummary(summary.days, text)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LimitTimeChip(text.allowedAppCount(summary.allowedAppCount))
                summary.minutesUntilStart?.let { minutes ->
                    LimitTimeChip(text.startsIn(formatLimitMinutesLabel(minutes)))
                }
            }
        }
    }
}

@Composable
fun PolicyGroupSummaryLine(summary: AppGroupSummary, text: AppStrings, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .pointerInput(summary.groupName, summary.usedMinutes, summary.limitMinutes, summary.extraMinutes) {
                detectTapGestures(onTap = { onClick() })
            },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GroupSummaryDot(status = summary.status)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                summary.groupName.ifBlank { text.groupName },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${formatLimitMinutesLabel(summary.usedMinutes)} / ${
                    formatLimitWithAllowance(
                        limitMinutes = summary.limitMinutes,
                        extraMinutes = summary.extraMinutes,
                        unlockedForToday = false,
                        text = text,
                    )
                }",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                textAlign = TextAlign.End,
            )
        }
        ProgressOnlyBar(
            usedMinutes = summary.usedMinutes,
            limitMinutes = summary.limitMinutes,
            extraMinutes = summary.extraMinutes,
            status = summary.status,
        )
    }
}

@Composable
private fun GroupSummaryDot(status: LimitStatus) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(status.semanticColor().copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(status.semanticColor()),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupSummarySheet(
    groupSummary: AppGroupSummary,
    text: AppStrings,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionTitle(groupSummary.groupName.ifBlank { text.groupName })
            ProgressLine(
                text.appGroups,
                groupSummary.usedMinutes,
                groupSummary.limitMinutes,
                groupSummary.status,
                text,
                extraMinutes = groupSummary.extraMinutes,
            )
            Text(text.groupApps, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (groupSummary.appUsages.isEmpty()) {
                Text(text.noSelectableApps, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                ContainedLazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                ) {
                    items(groupSummary.appUsages, key = { appUsage -> appUsage.packageName }) { appUsage ->
                        val limitMinutes = appUsage.limitMinutes
                        AppRow(
                            appName = appUsage.appName,
                            packageName = appUsage.packageName,
                            supportingText = text.usedMinutes(appUsage.usedMinutes),
                            trailingContent = if (limitMinutes != null || appUsage.extraMinutes > 0 || appUsage.unlockedForToday) {
                                {
                                    LimitTimeChip(
                                        if (limitMinutes != null) {
                                            formatLimitWithAllowance(
                                                limitMinutes = limitMinutes,
                                                extraMinutes = appUsage.extraMinutes,
                                                unlockedForToday = appUsage.unlockedForToday,
                                                text = text,
                                            )
                                        } else if (appUsage.unlockedForToday) {
                                            text.unlockedToday
                                        } else {
                                            "+${formatLimitMinutesLabel(appUsage.extraMinutes)}"
                                        },
                                    )
                                }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun SummaryLine(
    label: String,
    usedMinutes: Int,
    limitMinutes: Int,
    status: LimitStatus,
    text: AppStrings,
) {
    ProgressLine(label, usedMinutes, limitMinutes, status, text)
}

@Composable
fun AppLimitSummaryRow(summary: AppLimitSummary, text: AppStrings) {
    AppRow(
        appName = summary.appName,
        packageName = summary.packageName,
        supportingText = "${formatLimitMinutesLabel(summary.usedMinutes)} / ${
            formatLimitWithAllowance(
                limitMinutes = summary.limitMinutes,
                extraMinutes = summary.extraMinutes,
                unlockedForToday = summary.unlockedForToday,
                text = text,
            )
        }",
        trailingContent = {
            StatusBadge(text.limitStatus(summary.status), summary.status)
        },
    )
}

@Composable
fun AppRow(
    appName: String,
    packageName: String,
    supportingText: String,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        AppIcon(packageName = packageName, contentDescription = appName)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                appName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (supportingText.isNotBlank()) {
                Text(
                    supportingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        trailingContent?.invoke()
    }
}

@Composable
private fun LimitTimeChip(label: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun AppIcon(packageName: String, contentDescription: String, size: androidx.compose.ui.unit.Dp = 40.dp) {
    val context = LocalContext.current
    var iconBitmap by remember(packageName) {
        mutableStateOf(AppIconBitmapCache.get(packageName))
    }

    LaunchedEffect(packageName) {
        if (iconBitmap == null) {
            val loadedBitmap = withContext(Dispatchers.IO) {
                runCatching {
                    context.packageManager
                        .getApplicationIcon(packageName)
                        .toSafeBitmap()
                        .asImageBitmap()
                }.getOrNull()
            }
            if (loadedBitmap != null) {
                AppIconBitmapCache.put(packageName, loadedBitmap)
                iconBitmap = loadedBitmap
            }
        }
    }

    if (iconBitmap != null) {
        Image(
            bitmap = iconBitmap!!,
            contentDescription = contentDescription,
            modifier = Modifier
                .size(size)
                .clip(CircleShape),
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(contentDescription.take(1).ifBlank { "?" })
        }
    }
}

private object AppIconBitmapCache {
    private val cache = LruCache<String, ImageBitmap>(160)

    fun get(packageName: String): ImageBitmap? {
        return cache.get(packageName)
    }

    fun put(packageName: String, bitmap: ImageBitmap) {
        cache.put(packageName, bitmap)
    }
}

@Composable
fun PolicyAppSummaryLine(summary: AppLimitSummary, text: AppStrings) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(packageName = summary.packageName, contentDescription = summary.appName, size = 24.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(summary.appName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "${formatLimitMinutesLabel(summary.usedMinutes)} / ${
                    formatLimitWithAllowance(
                        limitMinutes = summary.limitMinutes,
                        extraMinutes = summary.extraMinutes,
                        unlockedForToday = summary.unlockedForToday,
                        text = text,
                    )
                }",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ProgressOnlyBar(
            usedMinutes = summary.usedMinutes,
            limitMinutes = summary.limitMinutes,
            extraMinutes = summary.extraMinutes,
            unlockedForToday = summary.unlockedForToday,
            status = summary.status,
        )
    }
}

@Composable
fun ProgressOnlyBar(
    usedMinutes: Int,
    limitMinutes: Int,
    status: LimitStatus,
    extraMinutes: Int = 0,
    unlockedForToday: Boolean = false,
) {
    val effectiveLimitMinutes = (limitMinutes + extraMinutes).coerceAtLeast(limitMinutes)
    val rawProgress = if (unlockedForToday || effectiveLimitMinutes <= 0) {
        0f
    } else {
        usedMinutes.toFloat() / effectiveLimitMinutes.toFloat()
    }
    val progress by animateFloatAsState(
        targetValue = rawProgress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600),
        label = "summary-app-bar",
    )
    GaugeBar(fraction = progress, status = if (unlockedForToday) LimitStatus.Normal else status, height = 8)
}

@Composable
fun CircleTextButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(36.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (label == "R") {
                RefreshIcon()
            } else {
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun RefreshIcon() {
    Canvas(modifier = Modifier.size(24.dp)) {
        val stroke = 3.5.dp.toPx()
        val iconColor = Color(0xFF4B5563)
        val arcInset = size.minDimension * 0.14f
        val arcSize = size.minDimension - arcInset * 2f
        val startAngle = 112f
        val sweepAngle = 278f
        drawArc(
            color = iconColor,
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = Offset(arcInset, arcInset),
            size = Size(arcSize, arcSize),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = arcSize / 2f
        val endRadians = Math.toRadians((startAngle + sweepAngle).toDouble())
        val arrowTip = Offset(
            x = center.x + cos(endRadians).toFloat() * radius,
            y = center.y + sin(endRadians).toFloat() * radius,
        )
        val arrowPath = Path().apply {
            moveTo(arrowTip.x + size.width * 0.06f, arrowTip.y - size.height * 0.01f)
            lineTo(arrowTip.x - size.width * 0.12f, arrowTip.y - size.height * 0.19f)
            lineTo(arrowTip.x - size.width * 0.12f, arrowTip.y + size.height * 0.17f)
            close()
        }
        drawPath(path = arrowPath, color = iconColor)
    }
}

fun Drawable.toSafeBitmap() = toBitmap(
    width = intrinsicWidth.coerceAtLeast(1),
    height = intrinsicHeight.coerceAtLeast(1),
)

@Composable
fun DayLimitChips(
    dayLabels: List<String>,
    dailyLimits: List<String>,
    text: AppStrings,
    selectedDayIndex: Int,
    onDaySelected: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        dayLabels.forEachIndexed { index, dayLabel ->
            val selected = selectedDayIndex == index
            val isWeekend = index >= 5
            val minutes = dailyLimits.getOrNull(index).orEmpty()
            val rawMinutes = minutes.toIntOrNull() ?: 0
            val formattedMinutes = if (rawMinutes == 0) text.noLimit else formatLimitMinutesLabel(rawMinutes)
            val chipMinutes = formattedMinutes.replace(" ", "\n")
            Surface(
                onClick = { onDaySelected(index) },
                modifier = Modifier
                    .weight(1f)
                    .height(72.dp),
                shape = RoundedCornerShape(16.dp),
                color = when {
                    selected -> MaterialTheme.colorScheme.primary
                    isWeekend -> Color(0xFFFFF1DC)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                shadowElevation = if (selected) 2.dp else 0.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = dayLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) Color.White else if (isWeekend) Color(0xFF8B5A1F) else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = chipMinutes,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) Color.White else if (isWeekend) Color(0xFF8B5A1F) else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        textAlign = TextAlign.Center,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun SmoothMinuteSlider(
    valueMinutes: Int,
    onValueMinutesChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    minMinutes: Int = 0,
    maxMinutes: Int = POLICY_MAX_MINUTES,
    stepMinutes: Int = 1,
) {
    val primary = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    val thumbBorder = MaterialTheme.colorScheme.surface
    val currentOnValueMinutesChange by rememberUpdatedState(onValueMinutesChange)
    val fraction = if (maxMinutes <= 0) {
        0f
    } else {
        valueMinutes.toFloat() / maxMinutes.toFloat()
    }.coerceIn(0f, 1f)

    fun updateFromX(x: Float, width: Float) {
        if (width <= 0f) return
        val rawMinutes = (x / width).coerceIn(0f, 1f) * maxMinutes
        val snappedMinutes = ((rawMinutes / stepMinutes).roundToInt() * stepMinutes)
            .coerceIn(minMinutes.coerceAtMost(maxMinutes), maxMinutes)
        currentOnValueMinutesChange(snappedMinutes)
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(maxMinutes, stepMinutes) {
                detectTapGestures { offset ->
                    updateFromX(offset.x, size.width.toFloat())
                }
            }
            .pointerInput(maxMinutes, stepMinutes) {
                var dragX = 0f
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragX = offset.x
                        updateFromX(offset.x, size.width.toFloat())
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        dragX += dragAmount
                        updateFromX(dragX, size.width.toFloat())
                    },
                )
            },
    ) {
        val trackHeight = 7.dp.toPx()
        val trackTop = (size.height - trackHeight) / 2f
        val corner = CornerRadius(trackHeight / 2f, trackHeight / 2f)
        val endpointRadius = 2.2.dp.toPx()
        val thumbRadius = 8.dp.toPx()
        val thumbCenterX = size.width * fraction
        val trackCenterY = size.height / 2f

        drawRoundRect(
            color = inactive,
            topLeft = Offset(0f, trackTop),
            size = Size(size.width, trackHeight),
            cornerRadius = corner,
        )
        if (fraction > 0f) {
            drawRoundRect(
                color = primary,
                topLeft = Offset(0f, trackTop),
                size = Size(size.width * fraction, trackHeight),
                cornerRadius = corner,
            )
        }
        drawCircle(
            color = inactive,
            radius = endpointRadius,
            center = Offset(endpointRadius, trackCenterY),
        )
        drawCircle(
            color = inactive,
            radius = endpointRadius,
            center = Offset(size.width - endpointRadius, trackCenterY),
        )
        drawCircle(
            color = thumbBorder,
            radius = thumbRadius + 2.dp.toPx(),
            center = Offset(thumbCenterX.coerceIn(thumbRadius, size.width - thumbRadius), trackCenterY),
        )
        drawCircle(
            color = primary,
            radius = thumbRadius,
            center = Offset(thumbCenterX.coerceIn(thumbRadius, size.width - thumbRadius), trackCenterY),
        )
    }
}

@Composable
fun EditableMinuteValue(
    valueMinutes: Int,
    onValueMinutesChange: (Int) -> Unit,
    text: AppStrings,
    pickerTitle: String,
    minMinutes: Int = 0,
    maxMinutes: Int = POLICY_MAX_MINUTES,
    includeMaxPreset: Boolean = true,
) {
    var showPicker by remember { mutableStateOf(false) }

    Surface(
        onClick = { showPicker = true },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        modifier = Modifier
            .width(156.dp)
            .height(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                if (valueMinutes == 0) text.noLimit else formatLimitMinutesLabel(valueMinutes),
                modifier = Modifier.padding(horizontal = 10.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    if (showPicker) {
        DurationPickerSheet(
            title = pickerTitle,
            valueMinutes = valueMinutes,
            minMinutes = minMinutes,
            maxMinutes = maxMinutes,
            includeMaxPreset = includeMaxPreset,
            text = text,
            onDismiss = { showPicker = false },
            onApply = { minutes ->
                onValueMinutesChange(minutes)
                showPicker = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DurationPickerSheet(
    title: String,
    valueMinutes: Int,
    minMinutes: Int,
    maxMinutes: Int,
    includeMaxPreset: Boolean,
    text: AppStrings,
    onDismiss: () -> Unit,
    onApply: (Int) -> Unit,
) {
    val lowerBound = minMinutes.coerceAtMost(maxMinutes)
    val upperBound = maxMinutes.coerceAtLeast(lowerBound)
    TimeWheelPickerDialog(
        title = title,
        valueMinutes = valueMinutes,
        lowerBound = lowerBound,
        upperBound = upperBound,
        displayValue = { minutes -> if (minutes == 0) text.noLimit else formatLimitMinutesLabel(minutes) },
        saveLabel = text.savePolicy,
        onDismiss = onDismiss,
        onApply = onApply,
    )
}

@Composable
fun MinuteControlPanel(
    valueMinutes: Int,
    onValueMinutesChange: (Int) -> Unit,
    text: AppStrings,
    title: String,
    minMinutes: Int = 0,
    maxMinutes: Int = POLICY_MAX_MINUTES,
    includeMaxPreset: Boolean = true,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.66f),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                EditableMinuteValue(
                    valueMinutes = valueMinutes,
                    onValueMinutesChange = onValueMinutesChange,
                    text = text,
                    pickerTitle = title,
                    minMinutes = minMinutes,
                    maxMinutes = maxMinutes,
                    includeMaxPreset = includeMaxPreset,
                )
            }
        }
    }
}

private enum class AppLimitFilter {
    All,
    Limited,
    Unrestricted,
}

private enum class PolicyContentMode {
    TimeControls,
    BlockingControls,
}

private enum class PolicySectionIcon {
    DailyLimit,
    AppGroups,
    AppLimits,
    Schedule,
    AllowOnly,
}

@Composable
private fun PolicyTabIntro(
    description: String,
) {
    Text(
        text = description,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CollapsiblePolicyCard(
    title: String,
    description: String,
    icon: PolicySectionIcon,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    text: AppStrings,
    headerTrailing: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    SimpleCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PolicySectionHeader(
                title = title,
                description = description,
                icon = icon,
                modifier = Modifier.weight(1f),
            )
            headerTrailing()
            SectionExpandButton(
                expanded = expanded,
                text = text,
                onClick = { onExpandedChange(!expanded) },
            )
        }
        if (expanded) {
            content()
        }
    }
}

@Composable
private fun PolicySectionHeader(
    title: String,
    description: String,
    icon: PolicySectionIcon,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PolicySectionIconBadge(icon = icon)
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            SectionTitle(title)
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionExpandButton(
    expanded: Boolean,
    text: AppStrings,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
    ) {
        Text(
            text = if (expanded) text.collapseSection else text.expandSection,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
    }
}

@Composable
private fun PolicySectionIconBadge(
    icon: PolicySectionIcon,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    Surface(
        modifier = modifier.size(42.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.60f),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
        ) {
            val strokeWidth = size.minDimension * 0.11f
            val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            when (icon) {
                PolicySectionIcon.DailyLimit -> {
                    drawRoundRect(
                        color = primary,
                        topLeft = Offset(size.width * 0.12f, size.height * 0.18f),
                        size = Size(size.width * 0.76f, size.height * 0.66f),
                        cornerRadius = CornerRadius(size.minDimension * 0.12f, size.minDimension * 0.12f),
                        style = stroke,
                    )
                    drawLine(
                        color = primary,
                        start = Offset(size.width * 0.12f, size.height * 0.38f),
                        end = Offset(size.width * 0.88f, size.height * 0.38f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                    drawCircle(primary, radius = strokeWidth * 0.65f, center = Offset(size.width * 0.34f, size.height * 0.60f))
                    drawCircle(primary, radius = strokeWidth * 0.65f, center = Offset(size.width * 0.62f, size.height * 0.60f))
                }

                PolicySectionIcon.AppGroups -> {
                    val left = Offset(size.width * 0.28f, size.height * 0.38f)
                    val right = Offset(size.width * 0.72f, size.height * 0.38f)
                    val bottom = Offset(size.width * 0.50f, size.height * 0.72f)
                    drawLine(primary, left, bottom, strokeWidth, StrokeCap.Round)
                    drawLine(primary, right, bottom, strokeWidth, StrokeCap.Round)
                    drawLine(primary, left, right, strokeWidth, StrokeCap.Round)
                    drawCircle(primary, radius = size.minDimension * 0.12f, center = left)
                    drawCircle(primary, radius = size.minDimension * 0.12f, center = right)
                    drawCircle(primary, radius = size.minDimension * 0.12f, center = bottom)
                }

                PolicySectionIcon.AppLimits -> {
                    val cell = size.minDimension * 0.26f
                    listOf(
                        Offset(size.width * 0.18f, size.height * 0.18f),
                        Offset(size.width * 0.56f, size.height * 0.18f),
                        Offset(size.width * 0.18f, size.height * 0.56f),
                        Offset(size.width * 0.56f, size.height * 0.56f),
                    ).forEach { topLeft ->
                        drawRoundRect(
                            color = primary,
                            topLeft = topLeft,
                            size = Size(cell, cell),
                            cornerRadius = CornerRadius(cell * 0.22f, cell * 0.22f),
                        )
                    }
                }

                PolicySectionIcon.Schedule -> {
                    drawCircle(
                        color = primary,
                        radius = size.minDimension * 0.36f,
                        center = Offset(size.width / 2f, size.height / 2f),
                        style = stroke,
                    )
                    drawLine(
                        color = primary,
                        start = Offset(size.width / 2f, size.height / 2f),
                        end = Offset(size.width / 2f, size.height * 0.30f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = primary,
                        start = Offset(size.width / 2f, size.height / 2f),
                        end = Offset(size.width * 0.68f, size.height * 0.58f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                }

                PolicySectionIcon.AllowOnly -> {
                    val path = Path().apply {
                        moveTo(size.width * 0.50f, size.height * 0.12f)
                        lineTo(size.width * 0.82f, size.height * 0.25f)
                        lineTo(size.width * 0.74f, size.height * 0.66f)
                        quadraticTo(size.width * 0.50f, size.height * 0.88f, size.width * 0.26f, size.height * 0.66f)
                        lineTo(size.width * 0.18f, size.height * 0.25f)
                        close()
                    }
                    drawPath(path = path, color = primary, style = stroke)
                    drawLine(
                        color = onPrimaryContainer,
                        start = Offset(size.width * 0.34f, size.height * 0.52f),
                        end = Offset(size.width * 0.46f, size.height * 0.64f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = onPrimaryContainer,
                        start = Offset(size.width * 0.46f, size.height * 0.64f),
                        end = Offset(size.width * 0.68f, size.height * 0.40f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

@Composable
fun GroupBudgetSummary(
    totalMinutes: Int,
    dailyMinimumMinutes: Int,
    text: AppStrings,
) {
    val status = if (totalMinutes > DAILY_POLICY_MAX_MINUTES) LimitStatus.Exceeded else LimitStatus.Normal
    val color = status.semanticColor()
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.22f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text.appGroups,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text.groupBudgetTotal(totalMinutes, dailyMinimumMinutes).replace(" / ", "\n"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            StatusBadge(
                label = if (status == LimitStatus.Exceeded) text.policyBudgetExceeded else text.policyUpToDate,
                status = status,
            )
        }
    }
}

@Composable
fun ScheduleBlockingCard(
    settings: UsagePolicySettings,
    installedApps: List<InstalledAppInfo>,
    allowedAppPackages: Set<String>,
    text: AppStrings,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onUpdateSettings: (UsagePolicySettings) -> Unit,
) {
    val scheduleActiveNow = settings.isScheduleBlockingNow()
    val statusTemplate = settings.activeScheduleTemplate()
    CollapsiblePolicyCard(
        title = text.scheduleBlocking,
        description = text.scheduleBlockingDescription,
        icon = PolicySectionIcon.Schedule,
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        text = text,
        headerTrailing = {
            Switch(
                checked = settings.scheduleBlockingEnabled,
                onCheckedChange = { enabled ->
                    onUpdateSettings(settings.copy(scheduleBlockingEnabled = enabled))
                },
            )
        },
    ) {
        if (settings.scheduleBlockingEnabled) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text.scheduleStatus,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (statusTemplate != null) {
                            Text(
                                "${formatScheduleWindow(statusTemplate.startMinutes, statusTemplate.endMinutes, text)} · ${scheduleDaysSummary(statusTemplate.days, text)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        } else {
                            Text(
                                text.noSchedules,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    StatusBadge(
                        if (scheduleActiveNow) text.scheduleActiveNow else text.scheduleInactiveNow,
                        if (scheduleActiveNow) LimitStatus.Exceeded else LimitStatus.Normal,
                    )
                }
            }
            Text(
                text.scheduleAllowedTemplateHint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            ScheduleTemplateSection(
                settings = settings,
                installedApps = installedApps,
                allowedAppPackages = allowedAppPackages,
                text = text,
                onUpdateSettings = onUpdateSettings,
            )
        }
    }
}

@Composable
fun AllowOnlyModeCard(
    settings: UsagePolicySettings,
    installedApps: List<InstalledAppInfo>,
    allowedAppPackages: Set<String>,
    text: AppStrings,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onUpdateSettings: (UsagePolicySettings) -> Unit,
    onAllowedAppsChanged: (Set<String>) -> Unit,
) {
    CollapsiblePolicyCard(
        title = text.allowOnlyMode,
        description = text.allowOnlyModeDescription,
        icon = PolicySectionIcon.AllowOnly,
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        text = text,
        headerTrailing = {
            Switch(
                checked = settings.allowOnlyModeEnabled,
                onCheckedChange = { enabled ->
                    onUpdateSettings(settings.copy(allowOnlyModeEnabled = enabled))
                },
            )
        },
    ) {
        if (settings.allowOnlyModeEnabled) {
            StatusBadge(text.allowed, LimitStatus.Normal)
            Text(
                text.allowOnlyModeSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AllowedPolicyRelationshipSummary(
                settings = settings,
                allowedAppPackages = allowedAppPackages,
                text = text,
            )
            AlwaysAllowedAppsContent(
                installedApps = installedApps,
                allowedAppPackages = allowedAppPackages,
                text = text,
                onAllowedAppsChanged = onAllowedAppsChanged,
            )
        }
    }
}

@Composable
private fun AllowedPolicyRelationshipSummary(
    settings: UsagePolicySettings,
    allowedAppPackages: Set<String>,
    text: AppStrings,
) {
    val userAllowedCount = (allowedAppPackages - SafetyGate.neverBlockPackages).size
    val activeSchedule = settings.activeScheduleTemplate()
    val scheduleAllowedCount = activeSchedule
        ?.allowedPackageNames
        ?.minus(SafetyGate.neverBlockPackages)
        ?.minus(allowedAppPackages)
        ?.size
        ?: 0

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text.policyRelationship,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            SafetyStatusRow(
                title = text.requiredAllowedApps,
                supportingText = text.requiredAllowedPolicy,
                statusLabel = text.allowedAppCount(SafetyGate.requiredNeverBlockPackages.size),
                status = LimitStatus.Normal,
            )
            SafetyStatusRow(
                title = text.alwaysAllowedApps,
                supportingText = text.globalAllowedPolicy,
                statusLabel = text.allowedAppCount(userAllowedCount),
                status = if (userAllowedCount > 0) LimitStatus.Normal else LimitStatus.Warning,
            )
            SafetyStatusRow(
                title = text.scheduleAllowedApps,
                supportingText = text.scheduleAllowedPolicy,
                statusLabel = text.allowedAppCount(scheduleAllowedCount),
                status = if (scheduleAllowedCount > 0) LimitStatus.Normal else LimitStatus.Warning,
            )
        }
    }
}

@Composable
fun ScheduleTimeControlPanel(
    label: String,
    valueMinutes: Int,
    text: AppStrings,
    onValueMinutesChange: (Int) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.66f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    onClick = { showPicker = true },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                ) {
                    Text(
                        formatClockMinutes(valueMinutes),
                        modifier = Modifier
                            .width(86.dp)
                            .padding(vertical = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
            SmoothMinuteSlider(
                valueMinutes = valueMinutes,
                onValueMinutesChange = onValueMinutesChange,
                minMinutes = 0,
                maxMinutes = SCHEDULE_MAX_MINUTES,
            )
            ScheduleAxisLabels()
        }
    }

    if (showPicker) {
        ClockTimePickerSheet(
            title = label,
            valueMinutes = valueMinutes,
            text = text,
            onDismiss = { showPicker = false },
            onApply = { minutes ->
                onValueMinutesChange(minutes)
                showPicker = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClockTimePickerSheet(
    title: String,
    valueMinutes: Int,
    text: AppStrings,
    onDismiss: () -> Unit,
    onApply: (Int) -> Unit,
) {
    TimeWheelPickerDialog(
        title = title,
        valueMinutes = valueMinutes,
        lowerBound = 0,
        upperBound = SCHEDULE_MAX_MINUTES,
        displayValue = ::formatClockMinutes,
        saveLabel = text.savePolicy,
        onDismiss = onDismiss,
        onApply = onApply,
    )
}

@Composable
private fun TimeWheelPickerDialog(
    title: String,
    valueMinutes: Int,
    lowerBound: Int,
    upperBound: Int,
    displayValue: (Int) -> String,
    saveLabel: String,
    onDismiss: () -> Unit,
    onApply: (Int) -> Unit,
) {
    val cleanLowerBound = lowerBound.coerceAtMost(upperBound)
    val cleanUpperBound = upperBound.coerceAtLeast(cleanLowerBound)
    var draftMinutes by remember(valueMinutes, cleanLowerBound, cleanUpperBound) {
        mutableStateOf(valueMinutes.coerceIn(cleanLowerBound, cleanUpperBound))
    }
    val hourValue = draftMinutes / 60
    val minuteValue = draftMinutes % 60
    val minHours = cleanLowerBound / 60
    val maxHours = cleanUpperBound / 60
    val minSelectableMinutes = if (hourValue == minHours) cleanLowerBound % 60 else 0
    val maxSelectableMinutes = if (hourValue == maxHours) cleanUpperBound % 60 else 59

    fun updateTime(hours: Int = hourValue, minutes: Int = minuteValue) {
        draftMinutes = (hours.coerceIn(minHours, maxHours) * 60 + minutes.coerceIn(0, 59))
            .coerceIn(cleanLowerBound, cleanUpperBound)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp),
            shape = RoundedCornerShape(32.dp),
            color = Color(0xFFF8FAFF),
            tonalElevation = 0.dp,
            shadowElevation = 18.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = Color(0xFF171A2E),
                    )
                    Surface(
                        modifier = Modifier.padding(top = 10.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFFEAF2FF),
                    ) {
                        Text(
                            displayValue(draftMinutes),
                            modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2F6FE4),
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color.White.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .height(66.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFFF0F3F8)),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ClockTimeWheel(
                            label = "h",
                            value = hourValue,
                            values = minHours..maxHours,
                            onValueChange = { hours -> updateTime(hours = hours) },
                            modifier = Modifier.weight(1f),
                        )
                        ClockTimeWheel(
                            label = "m",
                            value = minuteValue,
                            values = minSelectableMinutes..maxSelectableMinutes,
                            onValueChange = { minutes -> updateTime(minutes = minutes) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(78.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color(0xFFF8FAFF), Color(0x00F8FAFF)),
                                ),
                            ),
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(78.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color(0x00F8FAFF), Color(0xFFF8FAFF)),
                                ),
                            ),
                    )
                }

                Button(
                    onClick = { onApply(draftMinutes) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    shape = RoundedCornerShape(30.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2F6FE4)),
                ) {
                    Text(
                        saveLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClockTimeWheel(
    label: String,
    value: Int,
    values: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDirectInput by remember { mutableStateOf(false) }
    val wheelValues = remember(values.first, values.last) {
        values.toList().ifEmpty { listOf(value) }
    }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = wheelValues.indexOf(value).coerceAtLeast(0),
    )
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val currentValue by rememberUpdatedState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    LaunchedEffect(value, values.first, values.last) {
        val index = wheelValues.indexOf(value)
        if (index >= 0 && !listState.isScrollInProgress) {
            listState.animateScrollToItem(index)
        }
    }
    LaunchedEffect(listState, wheelValues) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val center = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
            layoutInfo.visibleItemsInfo
                .minByOrNull { item -> abs((item.offset + item.size / 2) - center) }
                ?.index
        }
            .distinctUntilChanged()
            .collect { index ->
                val nextValue = wheelValues.getOrNull(index ?: return@collect) ?: return@collect
                if (nextValue != currentValue) {
                    currentOnValueChange(nextValue)
                }
            }
    }

    LazyColumn(
        state = listState,
        flingBehavior = flingBehavior,
        modifier = modifier
            .height(300.dp)
            .clipToBounds(),
        contentPadding = PaddingValues(vertical = 117.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        items(wheelValues, key = { item -> item }) { item ->
            val selected = item == value
            Surface(
                onClick = {
                    if (selected) {
                        showDirectInput = true
                    } else {
                        currentOnValueChange(item)
                    }
                },
                shape = RoundedCornerShape(18.dp),
                color = Color.Transparent,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(66.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "%02d".format(item),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface.copy(
                            alpha = if (selected) 0.94f else 0.22f,
                        ),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }

    if (showDirectInput) {
        WheelNumberInputDialog(
            label = label,
            initialValue = value,
            values = values,
            onDismiss = { showDirectInput = false },
            onApply = { input ->
                currentOnValueChange(input)
                showDirectInput = false
            },
        )
    }
}

@Composable
private fun WheelNumberInputDialog(
    label: String,
    initialValue: Int,
    values: IntRange,
    onDismiss: () -> Unit,
    onApply: (Int) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var input by remember(initialValue) { mutableStateOf("%02d".format(initialValue)) }
    val parsedInput = input.filter { character -> character.isDigit() }.toIntOrNull()
    val canApply = parsedInput != null && parsedInput in values

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 320.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 14.dp,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = { value ->
                        input = value.filter { character -> character.isDigit() }.take(2)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            if (canApply) {
                                onApply(parsedInput!!)
                            }
                        },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.headlineMedium.copy(
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        if (parsedInput != null) {
                            onApply(parsedInput.coerceIn(values.first, values.last))
                        }
                    },
                    enabled = input.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10C7B3)),
                ) {
                    Text("OK", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ScheduleAxisLabels() {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        listOf(0, 6 * 60, 12 * 60, 18 * 60, SCHEDULE_MAX_MINUTES).forEach { minutes ->
            Text(
                formatClockMinutes(minutes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleTemplateSection(
    settings: UsagePolicySettings,
    installedApps: List<InstalledAppInfo>,
    allowedAppPackages: Set<String>,
    text: AppStrings,
    onUpdateSettings: (UsagePolicySettings) -> Unit,
) {
    val templates = settings.normalizedScheduleTemplates()
    var selectedTemplateId by remember(templates.map { template -> template.id }, settings.activeScheduleTemplateId) {
        mutableStateOf(
            settings.activeScheduleTemplateId
                .takeIf { activeId -> templates.any { template -> template.id == activeId } }
                .orEmpty(),
        )
    }
    var appSearchQuery by remember(selectedTemplateId) { mutableStateOf("") }
    val selectedTemplate = templates.firstOrNull { template -> template.id == selectedTemplateId }
    val globalUserAllowedPackages = allowedAppPackages - SafetyGate.neverBlockPackages

    fun applyTemplates(
        nextTemplates: List<ScheduleTemplatePolicy>,
        activeTemplate: ScheduleTemplatePolicy?,
    ) {
        val cleanedTemplates = nextTemplates.map { template ->
            template.copy(
                allowedPackageNames = template.allowedPackageNames -
                    globalUserAllowedPackages -
                    SafetyGate.neverBlockPackages,
            )
        }
        val cleanedActiveTemplate = activeTemplate?.let { active ->
            cleanedTemplates.firstOrNull { template -> template.id == active.id }
        }
        onUpdateSettings(
            settings.copy(
                scheduleBlockingEnabled = true,
                scheduleStartMinutes = cleanedActiveTemplate?.startMinutes ?: settings.scheduleStartMinutes,
                scheduleEndMinutes = cleanedActiveTemplate?.endMinutes ?: settings.scheduleEndMinutes,
                scheduleDays = cleanedActiveTemplate?.days?.toScheduleDaysEncoded() ?: settings.scheduleDays,
                scheduleTemplates = cleanedTemplates.toScheduleTemplatesEncoded(),
                activeScheduleTemplateId = cleanedActiveTemplate?.id.orEmpty(),
            ),
        )
    }

    fun updateSelectedTemplate(transform: (ScheduleTemplatePolicy) -> ScheduleTemplatePolicy) {
        val currentTemplate = selectedTemplate ?: return
        val nextTemplate = transform(currentTemplate)
        val nextTemplates = templates.map { template ->
            if (template.id == currentTemplate.id) nextTemplate else template
        }
        applyTemplates(nextTemplates, nextTemplate)
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text.scheduleList,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Surface(
                    onClick = {
                        val nextTemplate = ScheduleTemplatePolicy(
                            id = newScheduleTemplateId(),
                            name = "${text.scheduleTemplate} ${templates.size + 1}",
                            startMinutes = settings.scheduleStartMinutes,
                            endMinutes = settings.scheduleEndMinutes,
                            days = settings.scheduleDaySet(),
                            allowedPackageNames = emptySet(),
                        )
                        val nextTemplates = (templates + nextTemplate).takeLast(12)
                        selectedTemplateId = nextTemplate.id
                        applyTemplates(nextTemplates, nextTemplate)
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                ) {
                    Text(
                        text.newSchedule,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (selectedTemplate != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        onClick = {
                            val deletedIndex = templates.indexOfFirst { template -> template.id == selectedTemplate.id }
                            val nextTemplates = templates.filterNot { template -> template.id == selectedTemplate.id }
                            val nextTemplate = nextTemplates.getOrNull(
                                deletedIndex.coerceAtMost(nextTemplates.lastIndex.coerceAtLeast(0)),
                            )
                            selectedTemplateId = nextTemplate?.id.orEmpty()
                            applyTemplates(nextTemplates, nextTemplate)
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = AppOver.copy(alpha = 0.12f),
                    ) {
                        Text(
                            text.deleteSchedule,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = AppOver,
                        )
                    }
                }
            }
            if (templates.isEmpty()) {
                Text(text.noSchedules, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    templates.forEach { template ->
                        val scheduleOnlyPackages = template.allowedPackageNames -
                            SafetyGate.neverBlockPackages -
                            globalUserAllowedPackages
                        ScheduleTemplateChip(
                            template = template,
                            allowedAppCount = scheduleOnlyPackages.size + globalUserAllowedPackages.size,
                            selected = selectedTemplateId == template.id,
                            text = text,
                            onClick = {
                                selectedTemplateId = template.id
                                applyTemplates(templates, template)
                            },
                        )
                    }
                }
            }
            if (selectedTemplate != null) {
                ScheduleNameTextField(
                    scheduleId = selectedTemplate.id,
                    value = selectedTemplate.name,
                    label = text.scheduleName,
                    onValueChange = { name ->
                        updateSelectedTemplate { template -> template.copy(name = name) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                ScheduleTimeControlPanel(
                    label = text.scheduleStart,
                    valueMinutes = selectedTemplate.startMinutes.coerceIn(0, SCHEDULE_MAX_MINUTES),
                    text = text,
                    onValueMinutesChange = { minutes ->
                        updateSelectedTemplate { template -> template.copy(startMinutes = minutes) }
                    },
                )
                ScheduleTimeControlPanel(
                    label = text.scheduleEnd,
                    valueMinutes = selectedTemplate.endMinutes.coerceIn(0, SCHEDULE_MAX_MINUTES),
                    text = text,
                    onValueMinutesChange = { minutes ->
                        updateSelectedTemplate { template -> template.copy(endMinutes = minutes) }
                    },
                )
                ScheduleDaysEditor(
                    selectedDays = selectedTemplate.days,
                    text = text,
                    onDaysChanged = { days ->
                        updateSelectedTemplate { template -> template.copy(days = days) }
                    },
                )
                ScheduleTemplateAllowedAppsEditor(
                    template = selectedTemplate,
                    templates = templates,
                    installedApps = installedApps,
                    globalAllowedPackages = allowedAppPackages,
                    appSearchQuery = appSearchQuery,
                    text = text,
                    onSearchQueryChange = { query -> appSearchQuery = query },
                    onTemplatesChanged = { nextTemplates ->
                        val nextSelectedTemplate = nextTemplates.firstOrNull { template -> template.id == selectedTemplate.id }
                        applyTemplates(nextTemplates, nextSelectedTemplate)
                    },
                )
            }
        }
    }
}

@Composable
private fun ScheduleTemplateChip(
    template: ScheduleTemplatePolicy,
    allowedAppCount: Int,
    selected: Boolean,
    text: AppStrings,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                template.name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${formatScheduleWindow(template.startMinutes, template.endMinutes, text)} · ${scheduleDaysSummary(template.days, text)}",
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) Color.White.copy(alpha = 0.86f) else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${text.scheduleAllowedApps} $allowedAppCount",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) Color.White.copy(alpha = 0.92f) else MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ScheduleNameTextField(
    scheduleId: String,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    var fieldValue by remember(scheduleId) { mutableStateOf(TextFieldValue(value)) }
    LaunchedEffect(scheduleId, value) {
        if (value != fieldValue.text && fieldValue.composition == null) {
            fieldValue = TextFieldValue(value)
        }
    }
    OutlinedTextField(
        value = fieldValue,
        onValueChange = { nextValue ->
            fieldValue = nextValue
            onValueChange(nextValue.text)
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        singleLine = true,
        modifier = modifier,
    )
}

@Composable
private fun ScheduleDaysEditor(
    selectedDays: Set<Int>,
    text: AppStrings,
    onDaysChanged: (Set<Int>) -> Unit,
) {
    val safeSelectedDays = selectedDays.filter { day -> day in 1..7 }.toSet().ifEmpty { (1..7).toSet() }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text.scheduleDays,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                scheduleDaysSummary(safeSelectedDays, text),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ScheduleDayPresetChip(
                label = text.everyDay,
                selected = safeSelectedDays == (1..7).toSet(),
                onClick = { onDaysChanged((1..7).toSet()) },
            )
            ScheduleDayPresetChip(
                label = text.weekdayShort,
                selected = safeSelectedDays == (1..5).toSet(),
                onClick = { onDaysChanged((1..5).toSet()) },
            )
            ScheduleDayPresetChip(
                label = text.weekendShort,
                selected = safeSelectedDays == setOf(6, 7),
                onClick = { onDaysChanged(setOf(6, 7)) },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            text.dayLabels.forEachIndexed { index, label ->
                val day = index + 1
                val selected = day in safeSelectedDays
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    onClick = {
                        val nextDays = if (selected) {
                            safeSelectedDays - day
                        } else {
                            safeSelectedDays + day
                        }
                        if (nextDays.isNotEmpty()) {
                            onDaysChanged(nextDays)
                        }
                    },
                    shape = RoundedCornerShape(18.dp),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ) {
                    Text(
                        label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduleTemplateAllowedAppsEditor(
    template: ScheduleTemplatePolicy,
    templates: List<ScheduleTemplatePolicy>,
    installedApps: List<InstalledAppInfo>,
    globalAllowedPackages: Set<String>,
    appSearchQuery: String,
    text: AppStrings,
    onSearchQueryChange: (String) -> Unit,
    onTemplatesChanged: (List<ScheduleTemplatePolicy>) -> Unit,
) {
    val globalUserAllowedPackages = globalAllowedPackages - SafetyGate.neverBlockPackages
    val scheduleOnlyAllowedPackages = template.allowedPackageNames -
        globalUserAllowedPackages -
        SafetyGate.neverBlockPackages
    val totalVisibleAllowedCount = (scheduleOnlyAllowedPackages + globalUserAllowedPackages).size
    val visibleApps = remember(installedApps, scheduleOnlyAllowedPackages, globalUserAllowedPackages, appSearchQuery) {
        installedApps
            .filterNot { app -> app.packageName in SafetyGate.neverBlockPackages }
            .filter { app ->
                app.matchesAppSearch(appSearchQuery)
            }
            .sortedWith(
                compareByDescending<InstalledAppInfo> { app -> app.packageName in globalUserAllowedPackages }
                    .thenByDescending { app -> app.packageName in scheduleOnlyAllowedPackages }
                    .thenByDescending { app -> app.packageName in SafetyGate.communicationAppPackages }
                    .thenBy { app -> app.appName.lowercase() },
            )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text.scheduleAllowedApps,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            StatusBadge(text.selectedApps(totalVisibleAllowedCount), LimitStatus.Normal)
        }
        Text(
            text.scheduleAllowedDescription,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SearchBox(
            value = appSearchQuery,
            placeholder = text.searchApps,
            onValueChange = onSearchQueryChange,
        )
        if (visibleApps.isEmpty()) {
            Text(text.noSelectableApps, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            ContainedLazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
                resetKey = template.id to (
                    appSearchQuery +
                        scheduleOnlyAllowedPackages.sorted().joinToString(",") +
                        globalUserAllowedPackages.sorted().joinToString(",")
                    ),
            ) {
                items(visibleApps, key = { app -> app.packageName }) { app ->
                    val globallyAllowed = app.packageName in globalUserAllowedPackages
                    val selected = globallyAllowed || app.packageName in scheduleOnlyAllowedPackages
                    UserAllowedAppRow(
                        app = app,
                        selected = selected,
                        text = text,
                        enabled = !globallyAllowed,
                        statusLabel = if (globallyAllowed) text.allowed else if (selected) text.allowed else text.allow,
                        onToggle = {
                            val nextPackages = if (selected) {
                                scheduleOnlyAllowedPackages - app.packageName
                            } else {
                                scheduleOnlyAllowedPackages + app.packageName
                            }
                            onTemplatesChanged(
                                templates.map { item ->
                                    if (item.id == template.id) {
                                        item.copy(allowedPackageNames = nextPackages - SafetyGate.neverBlockPackages)
                                    } else {
                                        item
                                    }
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduleDayPresetChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        },
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
    }
}

@Composable
private fun rememberContainedScrollConnection(): NestedScrollConnection {
    return remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                return available
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                return available
            }
        }
    }
}

private fun scheduleDaysSummary(selectedDays: Set<Int>, text: AppStrings): String {
    return when (selectedDays) {
        (1..7).toSet() -> text.everyDay
        (1..5).toSet() -> text.weekdayShort
        setOf(6, 7) -> text.weekendShort
        else -> selectedDays
            .filter { day -> day in 1..7 }
            .sorted()
            .joinToString(", ") { day -> text.dayLabels.getOrElse(day - 1) { "" } }
            .ifBlank { text.everyDay }
    }
}

@Composable
fun PolicyPillButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
            contentColor = MaterialTheme.colorScheme.primary,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun AppGroupChip(
    name: String,
    budgetMinutes: Int,
    appCount: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val dotColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f) else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(modifier = Modifier.size(10.dp), shape = CircleShape, color = dotColor) {}
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${formatLimitMinutesLabel(budgetMinutes)} \u00B7 $appCount",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun SearchBox(value: String, placeholder: String, onValueChange: (String) -> Unit) {
    var fieldValue by remember { mutableStateOf(TextFieldValue(value)) }
    LaunchedEffect(value) {
        if (value != fieldValue.text) {
            fieldValue = TextFieldValue(value)
        }
    }
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchIcon()
            Spacer(modifier = Modifier.width(10.dp))
            BasicTextField(
                value = fieldValue,
                onValueChange = { nextValue ->
                    fieldValue = nextValue
                    onValueChange(nextValue.text)
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    if (fieldValue.text.isBlank()) {
                        Text(placeholder, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    innerTextField()
                },
            )
        }
    }
}

@Composable
private fun GroupNameTextField(
    groupId: String,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    var fieldValue by remember(groupId) { mutableStateOf(TextFieldValue(value)) }
    LaunchedEffect(groupId, value) {
        if (value != fieldValue.text && fieldValue.composition == null) {
            fieldValue = TextFieldValue(value)
        }
    }
    OutlinedTextField(
        value = fieldValue,
        onValueChange = { nextValue ->
            fieldValue = nextValue
            onValueChange(nextValue.text)
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        singleLine = true,
        modifier = modifier,
    )
}

private fun InstalledAppInfo.matchesAppSearch(query: String): Boolean {
    val normalizedQuery = query.trim()
    return normalizedQuery.isBlank() ||
        appName.contains(normalizedQuery, ignoreCase = true) ||
        packageName.contains(normalizedQuery, ignoreCase = true)
}

@Composable
fun SearchIcon() {
    Canvas(modifier = Modifier.size(22.dp)) {
        val stroke = 2.dp.toPx()
        drawCircle(
            color = Color(0xFF6B7280),
            radius = size.minDimension * 0.28f,
            center = Offset(size.width * 0.42f, size.height * 0.42f),
            style = Stroke(width = stroke),
        )
        drawLine(
            color = Color(0xFF6B7280),
            start = Offset(size.width * 0.62f, size.height * 0.62f),
            end = Offset(size.width * 0.82f, size.height * 0.82f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
fun AppFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun AppLimitRow(
    app: InstalledAppInfo,
    limitMinutes: Int?,
    extraMinutes: Int,
    unlockedForToday: Boolean,
    text: AppStrings,
    onClick: () -> Unit,
) {
    val hasLimit = limitMinutes != null && limitMinutes > 0

    Column {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(packageName = app.packageName, contentDescription = app.appName, size = 40.dp)
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(app.appName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            unlockedForToday -> text.unlockedToday
                            hasLimit -> formatLimitWithAllowance(limitMinutes ?: 0, extraMinutes, false, text)
                            extraMinutes > 0 -> "+${formatLimitMinutesLabel(extraMinutes)}"
                            else -> text.noLimit
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (hasLimit || extraMinutes > 0 || unlockedForToday) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ) {
                    Text(
                        when {
                            unlockedForToday -> text.unlockedToday
                            hasLimit -> formatLimitWithAllowance(limitMinutes ?: 0, extraMinutes, false, text)
                            extraMinutes > 0 -> "+${formatLimitMinutesLabel(extraMinutes)}"
                            else -> text.addLimit
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    color = if (hasLimit || extraMinutes > 0 || unlockedForToday) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    )
                }
                Text(
                    ">",
                    modifier = Modifier.padding(start = 10.dp, end = 2.dp),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        )
    }
}

private data class AppLimitAllocationInfo(
    val groupName: String,
    val groupBudgetMinutes: Int,
    val usedByOtherAppsMinutes: Int,
    val maxAllowedMinutes: Int,
)

private fun appLimitAllocationInfo(
    packageName: String,
    appGroups: List<AppGroupPolicy>,
    appLimits: Map<String, Int>,
): AppLimitAllocationInfo? {
    val group = appGroups.firstOrNull { appGroup -> packageName in appGroup.packageNames } ?: return null
    val usedByOtherApps = group.packageNames
        .filterNot { groupPackageName -> groupPackageName == packageName }
        .sumOf { groupPackageName -> appLimits[groupPackageName] ?: 0 }
        .coerceAtLeast(0)
    val maxAllowed = (group.budgetMinutes - usedByOtherApps)
        .coerceIn(0, POLICY_MAX_MINUTES)
    return AppLimitAllocationInfo(
        groupName = group.name,
        groupBudgetMinutes = group.budgetMinutes,
        usedByOtherAppsMinutes = usedByOtherApps,
        maxAllowedMinutes = maxAllowed,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppLimitEditorSheet(
    app: InstalledAppInfo,
    initialLimitMinutes: Int?,
    allocationInfo: AppLimitAllocationInfo?,
    text: AppStrings,
    onDismiss: () -> Unit,
    onApply: (Int?) -> Unit,
) {
    val maxAllowedMinutes = allocationInfo?.maxAllowedMinutes ?: POLICY_MAX_MINUTES
    var draftMinutes by remember(app.packageName, initialLimitMinutes, maxAllowedMinutes) {
        mutableStateOf((initialLimitMinutes ?: 0).coerceIn(0, maxAllowedMinutes))
    }
    val presets = listOf(0, 1, 2, 3, 5, 10, 15, 30, 60, 120, 240, 360, 720)
        .filter { minutes -> minutes == 0 || minutes <= maxAllowedMinutes }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetScrollState = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(sheetScrollState)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(packageName = app.packageName, contentDescription = app.appName, size = 44.dp)
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(app.appName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        if (draftMinutes > 0) text.minutesPerDay(draftMinutes) else text.noLimit,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                presets.forEach { minutes ->
                    LimitPresetChip(
                        label = if (minutes == 0) text.noLimit else formatLimitMinutesLabel(minutes),
                        selected = draftMinutes == minutes,
                        onClick = { draftMinutes = minutes },
                    )
                }
            }
            allocationInfo?.let { info ->
                Text(
                    text = text.appLimitGroupAllowance(
                        info.groupName.ifBlank { text.groupName },
                        info.maxAllowedMinutes,
                        info.groupBudgetMinutes,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            MinuteControlPanel(
                valueMinutes = draftMinutes,
                onValueMinutesChange = { minutes -> draftMinutes = minutes.coerceAtMost(maxAllowedMinutes) },
                text = text,
                title = text.appLimits,
                maxMinutes = maxAllowedMinutes,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { draftMinutes = 0 },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(text.noLimit)
                }
                Button(
                    onClick = {
                        onApply(draftMinutes.coerceAtMost(maxAllowedMinutes).takeIf { minutes -> minutes > 0 })
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(text.applyLimit)
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}

@Composable
private fun LimitPresetChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

fun formatLimitMinutesLabel(minutes: Int): String {
    val safeMinutes = minutes.coerceAtLeast(0)
    val hours = safeMinutes / 60
    val remainingMinutes = safeMinutes % 60
    return when {
        hours > 0 && remainingMinutes > 0 -> "${hours}h ${remainingMinutes}m"
        hours > 0 -> "${hours}h"
        else -> "${remainingMinutes}m"
    }
}

@Composable
fun GroupAppSelectionRow(
    app: InstalledAppInfo,
    selected: Boolean,
    text: AppStrings,
    onToggle: () -> Unit,
) {
    Column {
        Surface(
            onClick = onToggle,
            color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(packageName = app.packageName, contentDescription = app.appName, size = 36.dp)
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    app.appName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        if (selected) text.inGroup else text.addToGroup,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        )
    }
}

@Composable
private fun UsagePolicySection(
    settings: UsagePolicySettings,
    temporaryUnlockState: TemporaryUnlockState,
    installedApps: List<InstalledAppInfo>,
    allowedAppPackages: Set<String>,
    text: AppStrings,
    isExpanded: Boolean,
    contentMode: PolicyContentMode,
    dailyPolicyExpanded: Boolean = true,
    onDailyPolicyExpandedChange: (Boolean) -> Unit = {},
    appGroupsExpanded: Boolean = true,
    onAppGroupsExpandedChange: (Boolean) -> Unit = {},
    appLimitsExpanded: Boolean = true,
    onAppLimitsExpandedChange: (Boolean) -> Unit = {},
    scheduleBlockingExpanded: Boolean = true,
    onScheduleBlockingExpandedChange: (Boolean) -> Unit = {},
    allowOnlyModeExpanded: Boolean = true,
    onAllowOnlyModeExpandedChange: (Boolean) -> Unit = {},
    onPolicyDraftChanged: (UsagePolicySettings) -> Unit,
    onAllowedAppsChanged: (Set<String>) -> Unit,
) {
    val dailyLimits = listOf(
        settings.mondayLimitMinutes,
        settings.tuesdayLimitMinutes,
        settings.wednesdayLimitMinutes,
        settings.thursdayLimitMinutes,
        settings.fridayLimitMinutes,
        settings.saturdayLimitMinutes,
        settings.sundayLimitMinutes,
    ).map { minutes -> minutes.coerceIn(0, DAILY_POLICY_MAX_MINUTES).toString() }
    var selectedDayIndex by remember { mutableStateOf(Calendar.getInstance().get(Calendar.DAY_OF_WEEK).toDayIndex()) }
    val appGroups = settings.normalizedAppGroups().normalizedForEditing()
    val groupBudgetTotal = appGroups.sumOf { group -> group.budgetMinutes.coerceAtLeast(0) }
    val minimumDailyLimit = groupBudgetTotal.coerceAtMost(DAILY_POLICY_MAX_MINUTES)
    var activeGroupId by remember { mutableStateOf(appGroups.firstOrNull()?.id.orEmpty()) }
    LaunchedEffect(appGroups.map { group -> group.id }) {
        if (appGroups.isNotEmpty() && appGroups.none { group -> group.id == activeGroupId }) {
            activeGroupId = appGroups.first().id
        }
    }
    val appLimits = settings.appLimitMap()
    val todayTemporaryUnlockState = temporaryUnlockState.forToday()
    var appSearchQuery by remember { mutableStateOf("") }
    var appLimitFilter by remember { mutableStateOf(AppLimitFilter.All) }
    var selectedLimitApp by remember { mutableStateOf<InstalledAppInfo?>(null) }
    var groupAppSearchQuery by remember { mutableStateOf("") }

    fun updateDraft(
        nextDailyLimits: List<String> = dailyLimits,
        nextAppGroups: List<AppGroupPolicy> = appGroups,
        nextAppLimits: Map<String, Int> = appLimits,
    ) {
        onPolicyDraftChanged(
            buildUsagePolicySettings(
                base = settings,
                dailyLimits = nextDailyLimits,
                appGroups = nextAppGroups,
                appLimits = nextAppLimits,
            ),
        )
    }
    val limitPolicy: @Composable ColumnScope.() -> Unit = {
        CollapsiblePolicyCard(
            title = text.dailyPolicy,
            description = text.dailyPolicyDescription,
            icon = PolicySectionIcon.DailyLimit,
            expanded = dailyPolicyExpanded,
            onExpandedChange = onDailyPolicyExpandedChange,
            text = text,
            headerTrailing = {
                Text(text.timePerDay, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            },
        ) {
            DayLimitChips(
                dayLabels = text.dayLabels,
                dailyLimits = dailyLimits,
                text = text,
                selectedDayIndex = selectedDayIndex,
                onDaySelected = { index -> selectedDayIndex = index },
            )
            val selectedMinutes = dailyLimits[selectedDayIndex].toIntOrNull() ?: 0
            MinuteControlPanel(
                valueMinutes = selectedMinutes,
                onValueMinutesChange = { minutes ->
                    val safeMinutes = if (minutes == 0) {
                        0
                    } else {
                        minutes.coerceAtLeast(minimumDailyLimit)
                    }
                    val nextDailyLimits = dailyLimits.toMutableList().also { limits ->
                        limits[selectedDayIndex] = safeMinutes.toString()
                    }
                    updateDraft(nextDailyLimits = nextDailyLimits)
                },
                text = text,
                title = text.dayLabels[selectedDayIndex],
                minMinutes = 0,
                maxMinutes = DAILY_POLICY_MAX_MINUTES,
                includeMaxPreset = false,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PolicyPillButton(
                    label = text.applyWeekdays,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val selectedValue = dailyLimits[selectedDayIndex]
                        val nextDailyLimits = dailyLimits.toMutableList().also { limits ->
                            for (index in 0..4) limits[index] = selectedValue
                        }
                        updateDraft(nextDailyLimits = nextDailyLimits)
                    },
                )
                PolicyPillButton(
                    label = text.applyWeekend,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val selectedValue = dailyLimits[selectedDayIndex]
                        val nextDailyLimits = dailyLimits.toMutableList().also { limits ->
                            limits[5] = selectedValue
                            limits[6] = selectedValue
                        }
                        updateDraft(nextDailyLimits = nextDailyLimits)
                    },
                )
            }
        }
    }

    val groupPolicy: @Composable ColumnScope.() -> Unit = {
        CollapsiblePolicyCard(
            title = text.appGroups,
            description = text.appGroupsDescription,
            icon = PolicySectionIcon.AppGroups,
            expanded = appGroupsExpanded,
            onExpandedChange = onAppGroupsExpandedChange,
            text = text,
        ) {
            val activeGroup = appGroups.firstOrNull { group -> group.id == activeGroupId }
                ?: appGroups.firstOrNull()
            val groupVisibleApps = installedApps
                .filter { app -> app.matchesAppSearch(groupAppSearchQuery) }
                .sortedWith(
                    compareByDescending<InstalledAppInfo> { app ->
                        app.packageName in activeGroup?.packageNames.orEmpty()
                    }.thenBy { app -> app.appName.lowercase() },
                )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    onClick = {
                        val nextGroups = appGroups + AppGroupPolicy(
                            name = "${text.appGroupBudget} ${appGroups.size + 1}",
                            packageNames = emptySet(),
                            budgetMinutes = 60,
                            id = newAppGroupId(),
                        )
                        activeGroupId = nextGroups.last().id
                        updateDraft(nextAppGroups = nextGroups)
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                ) {
                    Text(
                        text.newGroup,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (activeGroup != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        onClick = {
                            val deletedGroupIndex = appGroups.indexOfFirst { group -> group.id == activeGroup.id }
                            val nextGroups = if (appGroups.size <= 1) {
                                emptyList()
                            } else {
                                appGroups.filter { group -> group.id != activeGroup.id }
                            }
                            activeGroupId = nextGroups
                                .getOrNull(deletedGroupIndex.coerceAtMost(nextGroups.lastIndex.coerceAtLeast(0)))
                                ?.id
                                .orEmpty()
                            updateDraft(nextAppGroups = nextGroups)
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = AppOver.copy(alpha = 0.12f),
                    ) {
                        Text(
                            text.deleteCurrentGroup,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppOver,
                        )
                    }
                }
            }
            GroupBudgetSummary(
                totalMinutes = groupBudgetTotal,
                dailyMinimumMinutes = minimumDailyLimit,
                text = text,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                appGroups.forEach { group ->
                    AppGroupChip(
                        name = group.name.ifBlank { text.groupName },
                        budgetMinutes = group.budgetMinutes,
                        appCount = group.packageNames.size,
                        selected = activeGroupId == group.id,
                    ) {
                        activeGroupId = group.id
                    }
                }
            }
            if (activeGroup != null) {
                GroupNameTextField(
                    groupId = activeGroup.id,
                    value = activeGroup.name,
                    label = text.groupName,
                    onValueChange = { value ->
                        val nextGroups = appGroups.replaceGroupById(
                            activeGroup.id,
                            activeGroup.copy(name = value),
                        )
                        updateDraft(nextAppGroups = nextGroups)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                key(activeGroup.id) {
                    val assignedAppLimitTotal = activeGroup.packageNames
                        .sumOf { packageName -> appLimits[packageName] ?: 0 }
                        .coerceAtMost(POLICY_MAX_MINUTES)
                    MinuteControlPanel(
                        valueMinutes = activeGroup.budgetMinutes.coerceAtLeast(assignedAppLimitTotal),
                        onValueMinutesChange = { minutes ->
                            val nextGroups = appGroups.replaceGroupById(
                                activeGroup.id,
                                activeGroup.copy(budgetMinutes = minutes.coerceAtLeast(assignedAppLimitTotal)),
                            )
                            updateDraft(nextAppGroups = nextGroups)
                        },
                        text = text,
                        title = text.groupBudgetMinutes,
                        minMinutes = assignedAppLimitTotal,
                        maxMinutes = POLICY_MAX_MINUTES,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text.groupApps, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(text.selectedApps(activeGroup.packageNames.size), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SearchBox(
                    value = groupAppSearchQuery,
                    placeholder = text.searchApps,
                    onValueChange = {
                        groupAppSearchQuery = it
                    },
                )
                if (groupVisibleApps.isEmpty()) {
                    Text(text.noSelectableApps)
                } else {
                    ContainedLazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                        resetKey = activeGroup.id to groupAppSearchQuery,
                    ) {
                        items(groupVisibleApps, key = { app -> app.packageName }) { app ->
                            GroupAppSelectionRow(
                                app = app,
                                selected = app.packageName in activeGroup.packageNames,
                                text = text,
                                onToggle = {
                                    updateDraft(
                                        nextAppGroups = appGroups.togglePackageForGroupId(
                                            activeGroup.id,
                                            app.packageName,
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    val appLimitsSection: @Composable ColumnScope.() -> Unit = {
        CollapsiblePolicyCard(
            title = text.appLimits,
            description = text.appLimitsDescription,
            icon = PolicySectionIcon.AppLimits,
            expanded = appLimitsExpanded,
            onExpandedChange = onAppLimitsExpandedChange,
            text = text,
            headerTrailing = {
                Text(text.activeAppLimits(appLimits.size), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            },
        ) {
            val visibleApps = installedApps
                .filter { app -> app.matchesAppSearch(appSearchQuery) }
                .filter { app ->
                    when (appLimitFilter) {
                        AppLimitFilter.All -> true
                        AppLimitFilter.Limited -> app.packageName in appLimits
                        AppLimitFilter.Unrestricted -> app.packageName !in appLimits
                    }
                }
                .sortedWith(
                    compareByDescending<InstalledAppInfo> { app -> app.packageName in appLimits }
                        .thenBy { app -> app.appName.lowercase() },
                )
            SearchBox(
                value = appSearchQuery,
                placeholder = text.searchApps,
                onValueChange = {
                    appSearchQuery = it
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppFilterChip(text.allApps, appLimitFilter == AppLimitFilter.All) {
                    appLimitFilter = AppLimitFilter.All
                }
                AppFilterChip(text.limitedApps, appLimitFilter == AppLimitFilter.Limited) {
                    appLimitFilter = AppLimitFilter.Limited
                }
                AppFilterChip(text.unrestrictedApps, appLimitFilter == AppLimitFilter.Unrestricted) {
                    appLimitFilter = AppLimitFilter.Unrestricted
                }
            }
            if (visibleApps.isEmpty()) {
                Text(text.noSelectableApps)
            } else {
                ContainedLazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                ) {
                    items(visibleApps, key = { app -> app.packageName }) { app ->
                        val appLimit = appLimits[app.packageName]
                        val allowance = todayTemporaryUnlockState.packageAllowances[app.packageName]
                        AppLimitRow(
                            app = app,
                            limitMinutes = appLimit,
                            extraMinutes = allowance?.extraMinutes ?: 0,
                            unlockedForToday = allowance?.unlockedForToday == true,
                            text = text,
                            onClick = { selectedLimitApp = app },
                        )
                    }
                }
            }
        }
    }

    selectedLimitApp?.let { app ->
        val allocationInfo = appLimitAllocationInfo(app.packageName, appGroups, appLimits)
        AppLimitEditorSheet(
            app = app,
            initialLimitMinutes = appLimits[app.packageName],
            allocationInfo = allocationInfo,
            text = text,
            onDismiss = { selectedLimitApp = null },
            onApply = { minutes ->
                val cappedMinutes = minutes?.coerceAtMost(allocationInfo?.maxAllowedMinutes ?: POLICY_MAX_MINUTES)
                val nextAppLimits = if (minutes == null) {
                    appLimits - app.packageName
                } else {
                    appLimits + (app.packageName to (cappedMinutes ?: minutes))
                }
                selectedLimitApp = null
                updateDraft(nextAppLimits = nextAppLimits)
            },
        )
    }

    when (contentMode) {
        PolicyContentMode.TimeControls -> {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PolicyTabIntro(
                    description = text.timeTabDescription,
                )
                AdaptiveTwoPane(
                    isExpanded = isExpanded,
                    leftContent = {
                        limitPolicy()
                        groupPolicy()
                    },
                    rightContent = {
                        appLimitsSection()
                    },
                )
            }
        }

        PolicyContentMode.BlockingControls -> {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PolicyTabIntro(
                    description = text.blockingTabDescription,
                )
                ScheduleBlockingCard(
                    settings = settings,
                    installedApps = installedApps,
                    allowedAppPackages = allowedAppPackages,
                    text = text,
                    expanded = scheduleBlockingExpanded,
                    onExpandedChange = onScheduleBlockingExpandedChange,
                    onUpdateSettings = onPolicyDraftChanged,
                )
                AllowOnlyModeCard(
                    settings = settings,
                    installedApps = installedApps,
                    allowedAppPackages = allowedAppPackages,
                    text = text,
                    expanded = allowOnlyModeExpanded,
                    onExpandedChange = onAllowOnlyModeExpandedChange,
                    onUpdateSettings = onPolicyDraftChanged,
                    onAllowedAppsChanged = onAllowedAppsChanged,
                )
            }
        }
    }
}

@Composable
fun SimpleCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
private fun ContainedLazyColumn(
    modifier: Modifier = Modifier,
    resetKey: Any? = null,
    content: LazyListScope.() -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(resetKey) {
        listState.scrollToItem(0)
    }
    val containedScrollConnection = rememberContainedScrollConnection()

    val shape = RoundedCornerShape(18.dp)
    Surface(
        modifier = modifier,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.92f)),
    ) {
        Box(modifier = Modifier.clip(shape)) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .nestedScroll(containedScrollConnection),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                content = content,
            )
            if (listState.canScrollBackward) {
                ScrollDampEdge(
                    modifier = Modifier.align(Alignment.TopCenter),
                    isTop = true,
                )
            }
            if (listState.canScrollForward) {
                ScrollDampEdge(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    isTop = false,
                )
            }
        }
    }
}

@Composable
private fun ScrollDampEdge(modifier: Modifier = Modifier, isTop: Boolean) {
    val edgeColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val colors = if (isTop) {
        listOf(edgeColor, Color.Transparent)
    } else {
        listOf(Color.Transparent, edgeColor)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(Brush.verticalGradient(colors)),
    )
}

@Composable
fun CompactNumberField(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onValueChange(input.filter { character -> character.isDigit() }) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier,
    )
}

fun List<AppGroupPolicy>.replaceGroupById(groupId: String, group: AppGroupPolicy): List<AppGroupPolicy> {
    if (isEmpty()) {
        return listOf(group)
    }
    return map { currentGroup ->
        if (currentGroup.id == groupId) group.copy(id = groupId) else currentGroup
    }
}

fun List<AppGroupPolicy>.togglePackageForGroupId(groupId: String, packageName: String): List<AppGroupPolicy> {
    if (isEmpty() || packageName.isBlank()) {
        return this
    }
    val targetGroup = firstOrNull { group -> group.id == groupId } ?: return this
    val adding = packageName !in targetGroup.packageNames
    return map { group ->
        when {
            group.id == groupId && adding -> group.copy(packageNames = group.packageNames + packageName)
            group.id == groupId -> group.copy(packageNames = group.packageNames - packageName)
            adding -> group.copy(packageNames = group.packageNames - packageName)
            else -> group
        }
    }
}

fun buildUsagePolicySettings(
    base: UsagePolicySettings,
    dailyLimits: List<String>,
    appGroups: List<AppGroupPolicy>,
    appLimits: Map<String, Int>,
): UsagePolicySettings {
    val cleanAppLimits = appLimits
        .filter { (packageName, minutes) -> packageName.isNotBlank() && minutes > 0 }
        .mapValues { (_, minutes) -> minutes.coerceIn(0, POLICY_MAX_MINUTES) }
    val cleanGroups = appGroups.normalizedForEditing()
        .map { group ->
            val assignedAppLimitTotal = group.packageNames.sumOf { packageName -> cleanAppLimits[packageName] ?: 0 }
            group.copy(budgetMinutes = group.budgetMinutes.coerceAtLeast(assignedAppLimitTotal))
        }
    val groupBudgetTotal = cleanGroups.sumOf { group -> group.budgetMinutes.coerceAtLeast(0) }
    val safeDailyLimits = (0..6).map { index ->
        val minutes = dailyLimits.getOrNull(index).toLimitMinutes()
        if (groupBudgetTotal > 0 && minutes > 0) {
            minutes.coerceAtLeast(groupBudgetTotal).coerceAtMost(DAILY_POLICY_MAX_MINUTES)
        } else {
            minutes
        }
    }
    val primaryGroup = cleanGroups.firstOrNull()
    return base.copy(
        weekdayLimitMinutes = safeDailyLimits[0],
        weekendLimitMinutes = safeDailyLimits[5],
        mondayLimitMinutes = safeDailyLimits[0],
        tuesdayLimitMinutes = safeDailyLimits[1],
        wednesdayLimitMinutes = safeDailyLimits[2],
        thursdayLimitMinutes = safeDailyLimits[3],
        fridayLimitMinutes = safeDailyLimits[4],
        saturdayLimitMinutes = safeDailyLimits[5],
        sundayLimitMinutes = safeDailyLimits[6],
        appGroupName = primaryGroup?.name.orEmpty(),
        appGroupPackages = primaryGroup?.packageNames.orEmpty().sorted().joinToString(","),
        appGroupBudgetMinutes = primaryGroup?.budgetMinutes ?: 0,
        appGroups = cleanGroups.toAppGroupsEncoded(),
        appLimitRules = cleanAppLimits.toAppLimitRules(),
    )
}

fun List<AppGroupPolicy>.normalizedForEditing(): List<AppGroupPolicy> {
    val cleanGroups = mapIndexed { index, group ->
        group.copy(
            name = group.name,
            packageNames = group.packageNames.filter { packageName -> packageName.isNotBlank() }.toSet(),
            budgetMinutes = group.budgetMinutes.coerceIn(0, POLICY_MAX_MINUTES),
            id = group.id.ifBlank { "legacy-$index" },
        )
    }
    return cleanGroups
}

fun newAppGroupId(): String = UUID.randomUUID().toString()
fun newScheduleTemplateId(): String = UUID.randomUUID().toString()

private const val POLICY_MAX_MINUTES = 720
private const val DAILY_POLICY_MAX_MINUTES = 24 * 60 - 1
private const val SCHEDULE_MAX_MINUTES = 24 * 60 - 5

fun String?.toLimitMinutes(): Int {
    return this
        ?.filter { character -> character.isDigit() }
        ?.toIntOrNull()
        ?.coerceIn(0, DAILY_POLICY_MAX_MINUTES)
        ?: 0
}

fun formatDuration(totalTimeMillis: Long): String {
    val totalMinutes = (totalTimeMillis + 59_999L) / 60_000L
    return formatLimitMinutesLabel(totalMinutes.toInt())
}

private fun formatMonitorUsageCounter(usedMillis: Long, limitMillis: Long): String {
    val used = formatMonitorDuration(usedMillis)
    return if (limitMillis > 0L) {
        "$used / ${formatMonitorDuration(limitMillis)}"
    } else {
        used
    }
}

private fun formatMonitorDuration(millis: Long): String {
    val totalSeconds = (millis.coerceAtLeast(0L) / 1_000L).toInt()
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "${hours}h %02dm %02ds".format(minutes, seconds)
        minutes > 0 -> "${minutes}m %02ds".format(seconds)
        else -> "${seconds}s"
    }
}

fun formatStatsWeekdayLabel(dayStartMillis: Long): String {
    return SimpleDateFormat("E", Locale.getDefault()).format(Date(dayStartMillis))
}

fun formatStatsDateLabel(dayStartMillis: Long): String {
    return SimpleDateFormat("M/d", Locale.getDefault()).format(Date(dayStartMillis))
}

fun isToday(dayStartMillis: Long): Boolean {
    val todayStartMillis = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    return dayStartMillis == todayStartMillis
}

fun isWeekend(dayStartMillis: Long): Boolean {
    val dayOfWeek = Calendar.getInstance().apply {
        timeInMillis = dayStartMillis
    }.get(Calendar.DAY_OF_WEEK)
    return dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
}

fun Long.toDisplayMinutes(): Int {
    return ((this + 59_999L) / 60_000L).toInt()
}

fun formatClockMinutes(minutes: Int): String {
    val cleanMinutes = minutes.coerceIn(0, 24 * 60 - 1)
    return String.format(Locale.US, "%02d:%02d", cleanMinutes / 60, cleanMinutes % 60)
}

fun formatScheduleWindow(startMinutes: Int, endMinutes: Int, text: AppStrings): String {
    val cleanStart = startMinutes.coerceIn(0, 24 * 60 - 1)
    val cleanEnd = endMinutes.coerceIn(0, 24 * 60 - 1)
    val endLabel = if (cleanEnd <= cleanStart && cleanStart != cleanEnd) {
        "${formatClockMinutes(cleanEnd)} ${text.nextDay}"
    } else {
        formatClockMinutes(cleanEnd)
    }
    return "${formatClockMinutes(cleanStart)}-${endLabel}"
}

fun parseTimeInputToMinutes(input: String): Int? {
    val normalized = input.trim().lowercase(Locale.US)
    if (normalized.isBlank()) {
        return null
    }
    val compact = normalized.replace("\\s".toRegex(), "")
    if (compact.all { character -> character.isDigit() }) {
        return compact
            .toIntOrNull()
            ?.coerceIn(0, POLICY_MAX_MINUTES)
    }

    val hours = Regex("""(\d+)\s*h""")
        .find(normalized)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
    val minutes = Regex("""(\d+)\s*m""")
        .find(normalized)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()

    if (hours != null || minutes != null) {
        return ((hours ?: 0) * 60 + (minutes ?: 0)).coerceIn(0, POLICY_MAX_MINUTES)
    }

    return compact
        .filter { character -> character.isDigit() }
        .toIntOrNull()
        ?.coerceIn(0, POLICY_MAX_MINUTES)
}

fun formatClockTime(timestampMillis: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestampMillis))
}

private fun formatMonitorAge(ageMillis: Long): String {
    val totalSeconds = (ageMillis.coerceAtLeast(0L) / 1_000L).toInt()
    return when {
        totalSeconds < 60 -> "${totalSeconds}s"
        totalSeconds < 3_600 -> "${totalSeconds / 60}m"
        else -> "${totalSeconds / 3_600}h ${(totalSeconds % 3_600) / 60}m"
    }
}

private const val MONITOR_STALE_WARNING_MILLIS = 90_000L
private const val USAGE_CONSISTENCY_TOLERANCE_MILLIS = 2L * 60L * 1000L

fun Int.toDayIndex(): Int {
    return when (this) {
        Calendar.MONDAY -> 0
        Calendar.TUESDAY -> 1
        Calendar.WEDNESDAY -> 2
        Calendar.THURSDAY -> 3
        Calendar.FRIDAY -> 4
        Calendar.SATURDAY -> 5
        Calendar.SUNDAY -> 6
        else -> 0
    }
}

class AppStrings {
    var korean: String = ""
    var overview: String = ""
    var time: String = ""
    var timeTabDescription: String = ""
    var blocking: String = ""
    var blockingTabDescription: String = ""
    var stats: String = ""
    var safety: String = ""
    var settings: String = ""
    var expandSection: String = ""
    var collapseSection: String = ""
    var language: String = ""
    var todayStatus: String = ""
    var developerSafeMode: String = ""
    var safeModeOn: String = ""
    var safeModeOff: String = ""
    var status: String = ""
    var blockingDisabled: String = ""
    var safetyChecksRequired: String = ""
    var policyEnforcement: String = ""
    var policyEnforcementEnabled: String = ""
    var policyEnforcementDisabled: String = ""
    var autoRecoveryReady: String = ""
    var autoRecoveryEnabledSafeMode: String = ""
    var emergencyUnlock: String = ""
    var developerPin: String = ""
    var unlock: String = ""
    var offlinePinAvailable: String = ""
    var safeModeEnabled: String = ""
    var invalidPin: String = ""
    var todayUsage: String = ""
    var usageAccessChecking: String = ""
    var usageAccessRequired: String = ""
    var openUsageAccessSettings: String = ""
    var refresh: String = ""
    var noUsageRecorded: String = ""
    var policySummary: String = ""
    var totalUsageSummary: String = ""
    var noAppLimits: String = ""
    var warningSummary: (Int, Int) -> String = { _, _ -> "" }
    var limitStatus: (LimitStatus) -> String = { _ -> "" }
    var weekdayShort: String = ""
    var weekendShort: String = ""
    var dailyPolicy: String = ""
    var dailyPolicyDescription: String = ""
    var timePerDay: String = ""
    var dayLabels: List<String> = emptyList()
    var applyWeekdays: String = ""
    var applyWeekend: String = ""
    var appGroupBudget: String = ""
    var appGroups: String = ""
    var appGroupsDescription: String = ""
    var groupName: String = ""
    var groupBudgetMinutes: String = ""
    var groupApps: String = ""
    var selectedApps: (Int) -> String = { _ -> "" }
    var inGroup: String = ""
    var addToGroup: String = ""
    var addGroup: String = ""
    var deleteGroup: String = ""
    var deleteCurrentGroup: String = ""
    var groupBudgetTotal: (Int, Int) -> String = { _, _ -> "" }
    var appLimits: String = ""
    var appLimitsDescription: String = ""
    var activeAppLimits: (Int) -> String = { _ -> "" }
    var allApps: String = ""
    var limitedApps: String = ""
    var unrestrictedApps: String = ""
    var searchApps: String = ""
    var noLimit: String = ""
    var unlockedToday: String = ""
    var addLimit: String = ""
    var clearLimit: String = ""
    var minutesPerDay: (Int) -> String = { _ -> "" }
    var applyLimit: String = ""
    var appLimitGroupAllowance: (String, Int, Int) -> String = { _, _, _ -> "" }
    var newGroup: String = ""
    var noSelectableApps: String = ""
    var savePolicy: String = ""
    var resetChanges: String = ""
    var unsavedChanges: String = ""
    var policyUpToDate: String = ""
    var policyBudgetExceeded: String = ""
    var appGroupLimitConflict: (Int) -> String = { _ -> "" }
    var scheduleBlocking: String = ""
    var scheduleBlockingDescription: String = ""
    var scheduleStart: String = ""
    var scheduleEnd: String = ""
    var scheduleDays: String = ""
    var scheduleStatus: String = ""
    var scheduleActiveNow: String = ""
    var scheduleInactiveNow: String = ""
    var everyDay: String = ""
    var scheduleList: String = ""
    var scheduleTemplate: String = ""
    var scheduleName: String = ""
    var newSchedule: String = ""
    var deleteSchedule: String = ""
    var noSchedules: String = ""
    var scheduleAllowedApps: String = ""
    var scheduleAllowedDescription: String = ""
    var scheduleAllowedTemplateHint: String = ""
    var activeSchedule: String = ""
    var nextSchedule: String = ""
    var nextDay: String = ""
    var noActiveSchedule: String = ""
    var scheduleDiagnostics: String = ""
    var allowedAppCount: (Int) -> String = { _ -> "" }
    var startsIn: (String) -> String = { _ -> "" }
    var allowOnlyMode: String = ""
    var allowOnlyModeDescription: String = ""
    var allowOnlyModeSummary: String = ""
    var policyEnforcementStillDisabled: String = ""
    var policySaved: String = ""
    var invalidAdminPin: String = ""
    var adminPin: String = ""
    var cancel: String = ""
    var safeModePinRequiredTitle: String = ""
    var safeModePinRequiredDescription: String = ""
    var safeModePinAccepted: String = ""
    var enableSafeMode: String = ""
    var policyOffPinRequiredTitle: String = ""
    var policyOffPinRequiredDescription: String = ""
    var policyOffPinAccepted: String = ""
    var disablePolicyEnforcement: String = ""
    var permissionSetupTitle: String = ""
    var permissionSetupDescription: String = ""
    var allowPermission: String = ""
    var permissionSettingsRequired: String = ""
    var permissionSettingsComplete: String = ""
    var permissionSettingsInSettings: String = ""
    var permissionWarning: String = ""
    var overlayPermission: String = ""
    var notificationPermission: String = ""
    var notificationAccessPermission: String = ""
    var exactAlarmPermission: String = ""
    var notificationSettings: String = ""
    var warningNotifications: String = ""
    var warningNotificationsDescription: String = ""
    var limitNotifications: String = ""
    var limitNotificationsDescription: String = ""
    var alwaysAllowedApps: String = ""
    var alwaysAllowedDescription: String = ""
    var requiredAllowedApps: String = ""
    var userAllowedApps: String = ""
    var allow: String = ""
    var allowed: String = ""
    var showList: String = ""
    var hideList: String = ""
    var pinSettings: String = ""
    var currentAdminPin: String = ""
    var newAdminPin: String = ""
    var currentEmergencyPin: String = ""
    var newEmergencyPin: String = ""
    var pinChangeIdle: String = ""
    var pinChanged: String = ""
    var pinTooShort: String = ""
    var pinSameAsCurrent: String = ""
    var pinInvalidCurrent: String = ""
    var pinChangeFailed: String = ""
    var eventLog: String = ""
    var clear: String = ""
    var noEvents: String = ""
    var blockingReadiness: String = ""
    var ready: String = ""
    var notReady: String = ""
    var safeModeAllowsBlocking: String = ""
    var policyEnforcementReady: String = ""
    var usageAccessReady: String = ""
    var whitelistReady: String = ""
    var emergencyUnlockReady: String = ""
    var openOverlaySettings: String = ""
    var openNotificationAccessSettings: String = ""
    var openExactAlarmSettings: String = ""
    var blockSafetyStatus: String = ""
    var currentBlockTargets: String = ""
    var temporaryAllowances: String = ""
    var noCurrentBlockTargets: String = ""
    var noTemporaryAllowances: String = ""
    var dailyLimit: String = ""
    var temporaryAllowanceDetail: (Int, Int) -> String = { _, _ -> "" }
    var blockSimulation: String = ""
    var blockScreenPreview: String = ""
    var previewOnly: String = ""
    var systemHealthStatus: String = ""
    var systemHealthLastCheck: String = ""
    var systemHealthNotChecked: String = ""
    var systemHealthIssue: String = ""
    var systemHealthNoIssue: String = ""
    var foregroundServiceHealth: String = ""
    var blockedTodayMessage: String = ""
    var remainingTime: String = ""
    var parentPin: String = ""
    var parentManagement: String = ""
    var parentManagementDescription: String = ""
    var parentLinked: String = ""
    var parentNotLinked: String = ""
    var parentAccount: String = ""
    var childDeviceName: String = ""
    var childDeviceId: String = ""
    var lastSync: String = ""
    var connectParent: String = ""
    var unlinkParent: String = ""
    var syncNow: String = ""
    var adminPinRole: String = ""
    var emergencyPinRole: String = ""
    var remoteTestMode: String = ""
    var remoteDailyLimit: String = ""
    var remoteAppTarget: String = ""
    var remoteExtraTime: String = ""
    var remoteAddTime: String = ""
    var remoteUnlockToday: String = ""
    var remoteCommands: String = ""
    var noRemoteCommands: String = ""
    var clearRemoteCommands: String = ""
    var parentLinkHint: String = ""
    var parentCommandRequiresLink: String = ""
    var noBlockPreviewTarget: String = ""
    var openBlockScreenPreview: String = ""
    var noSimulationTargets: String = ""
    var detectionStatus: String = ""
    var detectionStatusDescription: String = ""
    var noDetectionStatus: String = ""
    var usageMonitorStatus: String = ""
    var monitorRunning: String = ""
    var monitorDelayed: String = ""
    var monitorStopped: String = ""
    var lastMonitorTick: String = ""
    var monitorForegroundApp: String = ""
    var lastMonitorRecovery: String = ""
    var monitorStopReason: String = ""
    var noMonitorTick: String = ""
    var monitorLimitTarget: String = ""
    var monitorUsageCounter: String = ""
    var monitorBlockReason: String = ""
    var monitorOverlayResult: String = ""
    var monitorLastBlockAttempt: String = ""
    var monitorRetryCount: String = ""
    var monitorTarget: String = ""
    var monitorNotTarget: String = ""
    var monitorOverlayShown: String = ""
    var monitorOverlayFailed: String = ""
    var noBlockAttempt: String = ""
    var usageConsistency: String = ""
    var usageConsistencyAligned: String = ""
    var usageConsistencyMismatch: String = ""
    var usageConsistencyIdle: String = ""
    var monitorUsageSource: String = ""
    var appUsageSource: String = ""
    var usageDelta: (String) -> String = { _ -> "" }
    var policyRelationship: String = ""
    var requiredAllowedPolicy: String = ""
    var globalAllowedPolicy: String = ""
    var scheduleAllowedPolicy: String = ""
    var statistics: String = ""
    var dailyTrend: String = ""
    var topApps: String = ""
    var groupStats: String = ""
    var groupTopApps: String = ""
    var noGroupAppStats: String = ""
    var averageDaily: String = ""
    var peakDay: String = ""
    var statsScrollHint: String = ""
    var statsRangeOneDay: String = ""
    var statsRangeSevenDays: String = ""
    var statsRangeThirtyDays: String = ""
    var noStats: String = ""
    var usedMinutes: (Int) -> String = { _ -> "" }
    var detectionDecision: (String) -> String = { _ -> "" }
    var blockDecision: (BlockDecision) -> String = { _ -> "" }
}
fun appStrings(appLanguage: AppLanguage): AppStrings {
    return AppStrings().apply {
        korean = "Korean"
        overview = "Overview"
        time = "Time"
        timeTabDescription = "Set daily budgets, app groups, and per-app limits."
        blocking = "Blocking"
        blockingTabDescription = "Control schedules, allow-only mode, and blocking exceptions."
        stats = "Stats"
        safety = "Safety"
        settings = "Settings"
        expandSection = "Open"
        collapseSection = "Close"
        language = "Language"
        todayStatus = "Today Status"
        developerSafeMode = "Developer Safe Mode"
        safeModeOn = "Safe Mode On"
        safeModeOff = "Safe Mode Off"
        status = "Status"
        blockingDisabled = "Blocking Disabled"
        safetyChecksRequired = "Safety Checks Required"
        policyEnforcement = "Policy Enforcement"
        policyEnforcementEnabled = "Policy Enforcement Enabled"
        policyEnforcementDisabled = "Policy Enforcement Disabled"
        autoRecoveryReady = "Auto Recovery Ready"
        autoRecoveryEnabledSafeMode = "Auto Recovery Enabled Safe Mode"
        emergencyUnlock = "Emergency Unlock"
        developerPin = "Developer Pin"
        unlock = "Unlock"
        offlinePinAvailable = "Offline Pin Available"
        safeModeEnabled = "Safe Mode Enabled"
        invalidPin = "Invalid Pin"
        todayUsage = "Today Usage"
        usageAccessChecking = "Usage Access Checking"
        usageAccessRequired = "Usage Access Required"
        openUsageAccessSettings = "Open Usage Access Settings"
        refresh = "Refresh"
        noUsageRecorded = "No Usage Recorded"
        policySummary = "Policy Summary"
        totalUsageSummary = "Total Usage Summary"
        noAppLimits = "No App Limits"
        weekdayShort = "Weekday Short"
        weekendShort = "Weekend Short"
        dailyPolicy = "Daily Policy"
        dailyPolicyDescription = "Set the total screen time budget for each day."
        timePerDay = "Time Per Day"
        applyWeekdays = "Apply Weekdays"
        applyWeekend = "Apply Weekend"
        appGroupBudget = "App Group Budget"
        appGroups = "App Groups"
        appGroupsDescription = "Group related apps and assign a shared budget."
        groupName = "Group Name"
        groupBudgetMinutes = "Group Budget Minutes"
        groupApps = "Group Apps"
        inGroup = "In Group"
        addToGroup = "Add To Group"
        addGroup = "Add Group"
        deleteGroup = "Delete Group"
        deleteCurrentGroup = "Delete Current Group"
        appLimits = "App Limits"
        appLimitsDescription = "Set limits for individual apps."
        allApps = "All Apps"
        limitedApps = "Limited Apps"
        unrestrictedApps = "Unrestricted Apps"
        searchApps = "Search Apps"
        noLimit = "No Limit"
        unlockedToday = "Unlocked Today"
        addLimit = "Add Limit"
        clearLimit = "Clear Limit"
        applyLimit = "Apply Limit"
        newGroup = "New Group"
        noSelectableApps = "No Selectable Apps"
        savePolicy = "Save Policy"
        resetChanges = "Reset Changes"
        unsavedChanges = "Unsaved Changes"
        policyUpToDate = "Policy Up To Date"
        policyBudgetExceeded = "Policy Budget Exceeded"
        scheduleBlocking = "Schedule Blocking"
        scheduleBlockingDescription = "Block apps during selected days and times."
        scheduleStart = "Schedule Start"
        scheduleEnd = "Schedule End"
        scheduleDays = "Schedule Days"
        scheduleStatus = "Schedule Status"
        scheduleActiveNow = "Active"
        scheduleInactiveNow = "Inactive"
        everyDay = "Every Day"
        scheduleList = "Schedule List"
        scheduleTemplate = "Schedule Template"
        scheduleName = "Schedule Name"
        newSchedule = "New Schedule"
        deleteSchedule = "Delete Schedule"
        noSchedules = "No Schedules"
        scheduleAllowedApps = "Schedule Allowed Apps"
        scheduleAllowedDescription = "Schedule Allowed Description"
        scheduleAllowedTemplateHint = "Schedule Allowed Template Hint"
        activeSchedule = "Active Schedule"
        nextSchedule = "Next Schedule"
        nextDay = "next day"
        noActiveSchedule = "No Active Schedule"
        scheduleDiagnostics = "Schedule Diagnostics"
        allowOnlyMode = "Allow Only Mode"
        allowOnlyModeDescription = "Allow only selected apps while this mode is active."
        allowOnlyModeSummary = "Allow Only Mode Summary"
        policyEnforcementStillDisabled = "Policy Enforcement Still Disabled"
        policySaved = "Policy Saved"
        invalidAdminPin = "Invalid Admin Pin"
        adminPin = "Admin Pin"
        cancel = "Cancel"
        safeModePinRequiredTitle = "Safe Mode Pin Required Title"
        safeModePinRequiredDescription = "Safe Mode Pin Required Description"
        safeModePinAccepted = "Safe Mode Pin Accepted"
        enableSafeMode = "Enable Safe Mode"
        policyOffPinRequiredTitle = "Policy Off Pin Required Title"
        policyOffPinRequiredDescription = "Policy Off Pin Required Description"
        policyOffPinAccepted = "Policy Off Pin Accepted"
        disablePolicyEnforcement = "Disable Policy Enforcement"
        permissionSetupTitle = "Permission Setup Title"
        permissionSetupDescription = "Permission Setup Description"
        allowPermission = "Allow Permission"
        permissionSettingsRequired = "Permission Settings Required"
        permissionSettingsComplete = "Permission Settings Complete"
        permissionSettingsInSettings = "Permission Settings In Settings"
        permissionWarning = "Permission Warning"
        overlayPermission = "Overlay Permission"
        notificationPermission = "Notification Permission"
        notificationAccessPermission = "Notification Access Permission"
        exactAlarmPermission = "Exact Alarm Permission"
        notificationSettings = "Notification Settings"
        warningNotifications = "Warning Notifications"
        warningNotificationsDescription = "Warning Notifications Description"
        limitNotifications = "Limit Notifications"
        limitNotificationsDescription = "Limit Notifications Description"
        alwaysAllowedApps = "Always Allowed Apps"
        alwaysAllowedDescription = "Always Allowed Description"
        requiredAllowedApps = "Required Allowed Apps"
        userAllowedApps = "User Allowed Apps"
        allow = "Allow"
        allowed = "Allowed"
        showList = "Show List"
        hideList = "Hide List"
        pinSettings = "Pin Settings"
        currentAdminPin = "Current Admin Pin"
        newAdminPin = "New Admin Pin"
        currentEmergencyPin = "Current Emergency Pin"
        newEmergencyPin = "New Emergency Pin"
        pinChangeIdle = "Pin Change Idle"
        pinChanged = "Pin Changed"
        pinTooShort = "Pin Too Short"
        pinSameAsCurrent = "Pin Same As Current"
        pinInvalidCurrent = "Pin Invalid Current"
        pinChangeFailed = "Pin Change Failed"
        eventLog = "Event Log"
        clear = "Clear"
        noEvents = "No Events"
        blockingReadiness = "Blocking Readiness"
        ready = "Ready"
        notReady = "Not Ready"
        safeModeAllowsBlocking = "Safe Mode Allows Blocking"
        policyEnforcementReady = "Policy Enforcement Ready"
        usageAccessReady = "Usage Access Ready"
        whitelistReady = "Whitelist Ready"
        emergencyUnlockReady = "Emergency Unlock Ready"
        openOverlaySettings = "Open Overlay Settings"
        openNotificationAccessSettings = "Open Notification Access Settings"
        openExactAlarmSettings = "Open Exact Alarm Settings"
        blockSafetyStatus = "Block Safety Status"
        currentBlockTargets = "Current Block Targets"
        temporaryAllowances = "Temporary Allowances"
        noCurrentBlockTargets = "No Current Block Targets"
        noTemporaryAllowances = "No Temporary Allowances"
        dailyLimit = "Daily Limit"
        blockSimulation = "Block Simulation"
        blockScreenPreview = "Block Screen Preview"
        previewOnly = "Preview Only"
        systemHealthStatus = "System Health Status"
        systemHealthLastCheck = "System Health Last Check"
        systemHealthNotChecked = "System Health Not Checked"
        systemHealthIssue = "System Health Issue"
        systemHealthNoIssue = "System Health No Issue"
        foregroundServiceHealth = "Foreground Service Health"
        blockedTodayMessage = "Blocked Today Message"
        remainingTime = "Remaining Time"
        parentPin = "Parent Pin"
        parentManagement = "Parent Management"
        parentManagementDescription = "Parent Management Description"
        parentLinked = "Parent Linked"
        parentNotLinked = "Parent Not Linked"
        parentAccount = "Parent Account"
        childDeviceName = "Child Device Name"
        childDeviceId = "Child Device Id"
        lastSync = "Last Sync"
        connectParent = "Connect Parent"
        unlinkParent = "Unlink Parent"
        syncNow = "Sync Now"
        adminPinRole = "Admin Pin Role"
        emergencyPinRole = "Emergency Pin Role"
        remoteTestMode = "Remote Test Mode"
        remoteDailyLimit = "Remote Daily Limit"
        remoteAppTarget = "Remote App Target"
        remoteExtraTime = "Remote Extra Time"
        remoteAddTime = "Remote Add Time"
        remoteUnlockToday = "Remote Unlock Today"
        remoteCommands = "Remote Commands"
        noRemoteCommands = "No Remote Commands"
        clearRemoteCommands = "Clear Remote Commands"
        parentLinkHint = "Parent Link Hint"
        parentCommandRequiresLink = "Parent Command Requires Link"
        noBlockPreviewTarget = "No Block Preview Target"
        openBlockScreenPreview = "Open Block Screen Preview"
        noSimulationTargets = "No Simulation Targets"
        detectionStatus = "Detection Status"
        detectionStatusDescription = "Detection Status Description"
        noDetectionStatus = "No Detection Status"
        usageMonitorStatus = "Usage Monitor Status"
        monitorRunning = "Monitor Running"
        monitorDelayed = "Monitor Delayed"
        monitorStopped = "Monitor Stopped"
        lastMonitorTick = "Last Monitor Tick"
        monitorForegroundApp = "Monitor Foreground App"
        lastMonitorRecovery = "Last Monitor Recovery"
        monitorStopReason = "Monitor Stop Reason"
        noMonitorTick = "No Monitor Tick"
        monitorLimitTarget = "Monitor Limit Target"
        monitorUsageCounter = "Monitor Usage Counter"
        monitorBlockReason = "Monitor Block Reason"
        monitorOverlayResult = "Monitor Overlay Result"
        monitorLastBlockAttempt = "Monitor Last Block Attempt"
        monitorRetryCount = "Monitor Retry Count"
        monitorTarget = "Monitor Target"
        monitorNotTarget = "Monitor Not Target"
        monitorOverlayShown = "Monitor Overlay Shown"
        monitorOverlayFailed = "Monitor Overlay Failed"
        noBlockAttempt = "No Block Attempt"
        usageConsistency = "Usage Consistency"
        usageConsistencyAligned = "Usage Consistency Aligned"
        usageConsistencyMismatch = "Usage Consistency Mismatch"
        usageConsistencyIdle = "Usage Consistency Idle"
        monitorUsageSource = "Monitor Usage Source"
        appUsageSource = "App Usage Source"
        policyRelationship = "Policy Relationship"
        requiredAllowedPolicy = "Required Allowed Policy"
        globalAllowedPolicy = "Global Allowed Policy"
        scheduleAllowedPolicy = "Schedule Allowed Policy"
        statistics = "Statistics"
        dailyTrend = "Daily Trend"
        topApps = "Top Apps"
        groupStats = "Group Stats"
        groupTopApps = "Group Top Apps"
        noGroupAppStats = "No Group App Stats"
        averageDaily = "Average Daily"
        peakDay = "Peak Day"
        statsScrollHint = "Stats Scroll Hint"
        statsRangeOneDay = "Stats Range One Day"
        statsRangeSevenDays = "Stats Range Seven Days"
        statsRangeThirtyDays = "Stats Range Thirty Days"
        noStats = "No Stats"
        korean = if (appLanguage == AppLanguage.Korean) "Korean" else "Korean"
        overview = "Overview"
        time = "Time"
        timeTabDescription = "Set daily budgets, app groups, and per-app limits."
        blocking = "Block"
        blockingTabDescription = "Control schedules, allow-only mode, and blocking exceptions."
        stats = "Stats"
        safety = "Safety"
        settings = "Settings"
        expandSection = "Open"
        collapseSection = "Close"
        safeModeOn = "Safe Mode ON"
        safeModeOff = "Safe Mode OFF"
        todayUsage = "Today Usage"
        policySummary = "Policy Summary"
        dailyPolicyDescription = "Set the total screen time budget for each day."
        appGroups = "App Groups"
        appGroupsDescription = "Group related apps and assign a shared budget."
        appLimits = "App Limits"
        appLimitsDescription = "Set limits for individual apps."
        noLimit = "No limit"
        savePolicy = "Save"
        resetChanges = "Reset"
        policyUpToDate = "Up to date"
        warningSummary = { warningCount, exceededCount -> warningCount.toString() + " warning, " + exceededCount + " exceeded" }
        limitStatus = { statusValue ->
            when (statusValue) {
                LimitStatus.Normal -> "Normal"
                LimitStatus.Warning -> "Warning"
                LimitStatus.Exceeded -> "Exceeded"
            }
        }
        dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        selectedApps = { count -> count.toString() + " selected" }
        groupBudgetTotal = { total, dailyMinimum -> "Group budgets " + formatLimitMinutesLabel(total) + " / daily minimum " + formatLimitMinutesLabel(dailyMinimum) }
        activeAppLimits = { count -> count.toString() + " active" }
        minutesPerDay = { minutes -> formatLimitMinutesLabel(minutes) + "/day" }
        appLimitGroupAllowance = { groupNameValue, maxMinutes, groupBudget -> "Up to " + formatLimitMinutesLabel(maxMinutes) + " available in " + groupNameValue + " group (" + formatLimitMinutesLabel(groupBudget) + ")" }
        appGroupLimitConflict = { count -> count.toString() + " app limits exceed group budgets" }
        allowedAppCount = { count -> count.toString() + " allowed" }
        startsIn = { duration -> "in " + duration }
        temporaryAllowanceDetail = { remaining, extra -> "Remaining extra " + formatLimitMinutesLabel(remaining) + " / added " + formatLimitMinutesLabel(extra) }
        usageDelta = { delta -> "Delta " + delta }
        usedMinutes = { minutes -> formatLimitMinutesLabel(minutes) + " used" }
        detectionDecision = { decision -> decision }
        blockDecision = { decision ->
            when (decision) {
                BlockDecision.AllowedSafeMode -> "Safe Mode"
                BlockDecision.AllowedPolicyDisabled -> "Policy OFF"
                BlockDecision.AllowedWhitelist -> "Whitelist"
                BlockDecision.AllowedNoLimit -> "No limit"
                BlockDecision.AllowedUnderLimit -> "Allowed"
                BlockDecision.WouldBlockTotalLimit -> "Total"
                BlockDecision.WouldBlockSchedule -> "Schedule"
                BlockDecision.WouldBlockAllowOnly -> "Allow only"
                BlockDecision.WouldBlockGroupLimit -> "Group"
                BlockDecision.WouldBlockAppLimit -> "App"
            }
        }
        if (appLanguage == AppLanguage.Korean) {
            applyKoreanStrings()
        }
    }
}

private fun AppStrings.applyKoreanStrings() {
    korean = "한국어"
    overview = "개요"
    time = "시간"
    timeTabDescription = "요일별 예산, 앱 그룹, 앱별 제한을 설정합니다"
    blocking = "차단"
    blockingTabDescription = "스케줄, 허용 앱만 모드, 차단 예외를 관리합니다"
    stats = "통계"
    safety = "안전"
    settings = "설정"
    expandSection = "열기"
    collapseSection = "접기"
    language = "언어"
    todayStatus = "오늘 상태"
    developerSafeMode = "안전 모드"
    safeModeOn = "안전 모드 ON"
    safeModeOff = "안전 모드 OFF"
    status = "상태"
    blockingDisabled = "차단 비활성화"
    safetyChecksRequired = "차단 전 안전 확인 필요"
    policyEnforcement = "정책 적용"
    policyEnforcementEnabled = "정책 적용 ON"
    policyEnforcementDisabled = "정책 적용 OFF"
    autoRecoveryReady = "자동 복구 준비됨"
    autoRecoveryEnabledSafeMode = "자동 복구로 안전 모드 전환됨"
    emergencyUnlock = "긴급 해제"
    developerPin = "개발자 PIN"
    unlock = "해제"
    offlinePinAvailable = "오프라인 PIN 사용 가능"
    safeModeEnabled = "안전 모드 활성화"
    invalidPin = "PIN이 올바르지 않습니다"
    todayUsage = "오늘 사용"
    usageAccessChecking = "사용정보 권한 확인 중"
    usageAccessRequired = "사용정보 접근 권한이 필요합니다"
    openUsageAccessSettings = "권한 설정"
    refresh = "새로고침"
    noUsageRecorded = "오늘 기록된 사용 시간이 없습니다"
    policySummary = "정책 요약"
    totalUsageSummary = "전체"
    noAppLimits = "앱별 제한 없음"
    weekdayShort = "평일"
    weekendShort = "주말"
    dailyPolicy = "요일별 제한"
    dailyPolicyDescription = "요일마다 전체 사용 가능 시간을 정합니다"
    timePerDay = "h/m / 일"
    dayLabels = listOf("월", "화", "수", "목", "금", "토", "일")
    applyWeekdays = "평일"
    applyWeekend = "주말"
    appGroupBudget = "앱 그룹"
    appGroups = "앱 그룹"
    appGroupsDescription = "관련 앱을 묶고 공통 예산을 설정합니다"
    groupName = "그룹 이름"
    groupBudgetMinutes = "예산"
    groupApps = "그룹 앱"
    inGroup = "포함"
    addToGroup = "추가"
    addGroup = "그룹 추가"
    deleteGroup = "그룹 삭제"
    deleteCurrentGroup = "- 그룹 삭제"
    appLimits = "앱별 제한"
    appLimitsDescription = "개별 앱의 사용 시간을 따로 제한합니다"
    allApps = "전체"
    limitedApps = "제한"
    unrestrictedApps = "미제한"
    searchApps = "앱 검색"
    noLimit = "제한 없음"
    unlockedToday = "오늘 차단 해제"
    addLimit = "추가"
    clearLimit = "해제"
    applyLimit = "적용"
    newGroup = "+ 새 그룹"
    noSelectableApps = "선택 가능한 앱이 없습니다"
    savePolicy = "저장"
    resetChanges = "되돌리기"
    unsavedChanges = "변경 있음"
    policyUpToDate = "최신 상태"
    policyBudgetExceeded = "예산 초과"
    scheduleBlocking = "스케줄 차단"
    scheduleBlockingDescription = "정해진 시간에는 허용 앱과 필수 앱 외의 앱을 차단합니다"
    scheduleStart = "시작"
    scheduleEnd = "종료"
    scheduleDays = "적용 요일"
    scheduleStatus = "스케줄 상태"
    scheduleActiveNow = "차단 중"
    scheduleInactiveNow = "비활성"
    everyDay = "매일"
    scheduleList = "스케줄"
    scheduleTemplate = "스케줄"
    scheduleName = "스케줄 이름"
    newSchedule = "+ 스케줄 만들기"
    deleteSchedule = "- 스케줄 삭제"
    noSchedules = "아직 스케줄이 없습니다"
    scheduleAllowedApps = "스케줄 허용 앱"
    scheduleAllowedDescription = "선택한 앱은 이 스케줄이 활성화된 동안 차단에서 제외됩니다"
    scheduleAllowedTemplateHint = "스케줄마다 허용 앱을 따로 설정할 수 있습니다"
    activeSchedule = "활성"
    nextSchedule = "다음"
    nextDay = "다음날"
    noActiveSchedule = "현재 적용 중인 스케줄이 없습니다"
    scheduleDiagnostics = "스케줄 진단"
    allowOnlyMode = "허용 앱만"
    allowOnlyModeDescription = "필수 앱과 허용한 앱만 실행할 수 있습니다"
    allowOnlyModeSummary = "허용 앱만 모드에서는 항상 허용 앱과 필수 앱만 실행됩니다"
    policyEnforcementStillDisabled = "정책 적용은 아직 꺼져 있습니다"
    policySaved = "저장됨"
    invalidAdminPin = "관리 PIN이 올바르지 않습니다"
    adminPin = "관리 PIN"
    cancel = "취소"
    safeModePinRequiredTitle = "안전 모드 확인"
    safeModePinRequiredDescription = "정책 적용 중에는 관리 PIN을 입력해야 안전 모드로 전환할 수 있습니다"
    safeModePinAccepted = "안전 모드로 전환됨"
    enableSafeMode = "안전 모드 켜기"
    policyOffPinRequiredTitle = "정책 적용 해제"
    policyOffPinRequiredDescription = "정책 적용을 끄면 감시와 차단이 중지됩니다. 관리 PIN을 입력하세요"
    policyOffPinAccepted = "정책 적용 해제됨"
    disablePolicyEnforcement = "정책 끄기"
    permissionSetupTitle = "권한 설정"
    permissionSetupDescription = "사용 시간 집계와 강한 차단에 필요한 권한을 확인합니다"
    allowPermission = "권한 허용"
    permissionSettingsRequired = "필요한 권한이 아직 설정되지 않았습니다"
    permissionSettingsComplete = "권한 설정 완료"
    permissionSettingsInSettings = "설정 탭의 권한 설정에서 허용하세요"
    permissionWarning = "권한 필요"
    overlayPermission = "다른 앱 위에 표시"
    notificationPermission = "알림 권한"
    notificationAccessPermission = "알림 접근"
    exactAlarmPermission = "알람 및 리마인더"
    notificationSettings = "알림 설정"
    warningNotifications = "경고 알림"
    warningNotificationsDescription = "사용량이 제한의 80%에 도달하면 알림을 보냅니다"
    limitNotifications = "초과 알림"
    limitNotificationsDescription = "제한 초과 또는 차단 직전에 알림을 보냅니다"
    alwaysAllowedApps = "항상 허용 앱"
    alwaysAllowedDescription = "선택한 앱은 사용량에는 남지만 차단되지 않습니다"
    requiredAllowedApps = "필수 허용 앱"
    userAllowedApps = "사용자 허용 앱"
    allow = "허용"
    allowed = "허용됨"
    showList = "펼치기"
    hideList = "접기"
    pinSettings = "PIN 설정"
    currentAdminPin = "현재 관리 PIN"
    newAdminPin = "새 관리 PIN"
    currentEmergencyPin = "현재 긴급 PIN"
    newEmergencyPin = "새 긴급 PIN"
    pinChangeIdle = "PIN은 4자리 이상으로 설정하세요"
    pinChanged = "PIN이 변경됨"
    pinTooShort = "PIN은 4자리 이상이어야 합니다"
    pinSameAsCurrent = "새 PIN이 현재 PIN과 같습니다"
    pinInvalidCurrent = "현재 PIN이 올바르지 않습니다"
    pinChangeFailed = "PIN 변경 실패"
    eventLog = "이벤트"
    clear = "지우기"
    noEvents = "기록된 이벤트가 없습니다"
    blockingReadiness = "차단 준비 상태"
    ready = "준비됨"
    notReady = "대기"
    safeModeAllowsBlocking = "Safe Mode OFF"
    policyEnforcementReady = "정책 적용 ON"
    usageAccessReady = "사용정보 권한"
    whitelistReady = "필수 예외 목록"
    emergencyUnlockReady = "긴급 해제"
    openOverlaySettings = "오버레이 설정"
    openNotificationAccessSettings = "알림 접근 설정"
    openExactAlarmSettings = "알람 설정"
    blockSafetyStatus = "차단 안전성 검증"
    currentBlockTargets = "현재 차단 대상"
    temporaryAllowances = "임시 허용"
    noCurrentBlockTargets = "현재 차단될 대상이 없습니다"
    noTemporaryAllowances = "오늘 적용된 임시 허용이 없습니다"
    dailyLimit = "일일 제한"
    blockSimulation = "차단 시뮬레이션"
    blockScreenPreview = "차단 화면"
    previewOnly = "미리보기"
    systemHealthStatus = "서비스 상태"
    systemHealthLastCheck = "마지막 점검"
    systemHealthNotChecked = "아직 점검 기록이 없습니다"
    systemHealthIssue = "해제된 권한/서비스"
    systemHealthNoIssue = "문제 없음"
    foregroundServiceHealth = "감시 서비스"
    blockedTodayMessage = "오늘 사용 시간이 종료되었습니다"
    remainingTime = "남은 시간"
    parentPin = "관리 PIN"
    parentManagement = "부모 관리"
    parentManagementDescription = "부모 계정과 이 기기를 연결해 원격 추가 시간과 오늘만 해제를 받을 수 있습니다"
    parentLinked = "연결됨"
    parentNotLinked = "미연결"
    parentAccount = "부모 계정"
    childDeviceName = "자녀 기기 이름"
    childDeviceId = "기기 연결 ID"
    lastSync = "마지막 동기화"
    connectParent = "연결"
    unlinkParent = "연결 해제"
    syncNow = "동기화"
    adminPinRole = "관리 PIN: 정책 저장, 안전 모드 전환, 차단 화면의 시간 추가에 사용합니다"
    emergencyPinRole = "긴급 PIN: 인터넷 없이 Safe Mode로 복구하는 비상 해제 PIN입니다"
    remoteTestMode = "원격 명령 테스트"
    remoteDailyLimit = "하루 전체"
    remoteAppTarget = "앱 선택"
    remoteExtraTime = "추가 시간"
    remoteAddTime = "시간 추가"
    remoteUnlockToday = "오늘만 해제"
    remoteCommands = "원격 명령 기록"
    noRemoteCommands = "아직 원격 명령이 없습니다"
    clearRemoteCommands = "기록 지우기"
    parentLinkHint = "서버 연동 전에는 이 화면에서 원격 명령을 로컬로 시뮬레이션합니다"
    parentCommandRequiresLink = "부모 계정 연결 후 원격 명령을 적용할 수 있습니다"
    noBlockPreviewTarget = "차단 미리보기 대상이 없습니다"
    openBlockScreenPreview = "차단 화면 미리보기"
    noSimulationTargets = "시뮬레이션 대상 앱이 없습니다"
    detectionStatus = "최근 감지"
    detectionStatusDescription = "감시 서비스가 마지막으로 평가한 일반 사용자 앱을 표시합니다"
    noDetectionStatus = "아직 감지된 앱이 없습니다"
    usageMonitorStatus = "감시 서비스"
    monitorRunning = "정상"
    monitorDelayed = "지연"
    monitorStopped = "중단"
    lastMonitorTick = "마지막 감시"
    monitorForegroundApp = "감시 앱"
    lastMonitorRecovery = "최근 복구"
    monitorStopReason = "중지 사유"
    noMonitorTick = "감시 기록 없음"
    monitorLimitTarget = "제한 대상"
    monitorUsageCounter = "사용 카운터"
    monitorBlockReason = "차단 사유"
    monitorOverlayResult = "오버레이 표시"
    monitorLastBlockAttempt = "마지막 차단 시도"
    monitorRetryCount = "재시도"
    monitorTarget = "대상"
    monitorNotTarget = "아님"
    monitorOverlayShown = "성공"
    monitorOverlayFailed = "실패"
    noBlockAttempt = "차단 시도 없음"
    usageConsistency = "사용 집계 일치"
    usageConsistencyAligned = "일치"
    usageConsistencyMismatch = "점검 필요"
    usageConsistencyIdle = "감시 중인 앱 없음"
    monitorUsageSource = "알림 카운터"
    appUsageSource = "오늘 사용"
    policyRelationship = "허용 정책 관계"
    requiredAllowedPolicy = "필수 허용 앱은 안전을 위해 항상 차단하지 않습니다"
    globalAllowedPolicy = "항상 허용 앱은 허용 앱만 모드와 모든 스케줄에서 허용됩니다"
    scheduleAllowedPolicy = "스케줄 허용 앱은 해당 스케줄이 활성일 때만 추가로 허용됩니다"
    statistics = "통계"
    dailyTrend = "지난 30일"
    topApps = "앱 사용 Top"
    groupStats = "그룹 통계"
    groupTopApps = "그룹 내 사용 앱"
    noGroupAppStats = "그룹 내 사용 기록이 없습니다"
    averageDaily = "일평균"
    peakDay = "최고 사용일"
    statsScrollHint = "좌우 스크롤"
    statsRangeOneDay = "1일"
    statsRangeSevenDays = "7일"
    statsRangeThirtyDays = "30일"
    noStats = "표시할 통계가 없습니다"
    warningSummary = { warningCount, exceededCount -> "주의 ${warningCount}개, 초과 ${exceededCount}개" }
    limitStatus = { statusValue ->
        when (statusValue) {
            LimitStatus.Normal -> "정상"
            LimitStatus.Warning -> "주의"
            LimitStatus.Exceeded -> "초과"
        }
    }
    selectedApps = { count -> "선택 ${count}개" }
    groupBudgetTotal = { total, dailyMinimum ->
        "그룹 예산 합계 ${formatLimitMinutesLabel(total)} / 일일 최소 ${formatLimitMinutesLabel(dailyMinimum)}"
    }
    activeAppLimits = { count -> "활성 ${count}개" }
    minutesPerDay = { minutes -> formatLimitMinutesLabel(minutes) + "/일" }
    appLimitGroupAllowance = { groupNameValue, maxMinutes, groupBudget ->
        "${groupNameValue} 그룹에서 최대 ${formatLimitMinutesLabel(maxMinutes)}까지 설정할 수 있습니다 (그룹 ${formatLimitMinutesLabel(groupBudget)})"
    }
    appGroupLimitConflict = { count -> "그룹 예산보다 큰 앱별 제한 ${count}개" }
    allowedAppCount = { count -> "허용 앱 ${count}개" }
    startsIn = { duration -> "${duration} 후" }
    temporaryAllowanceDetail = { remaining, extra ->
        "남은 추가 ${formatLimitMinutesLabel(remaining)} / 추가 ${formatLimitMinutesLabel(extra)}"
    }
    usageDelta = { delta -> "차이 ${delta}" }
    usedMinutes = { minutes -> formatLimitMinutesLabel(minutes) + " 사용" }
    detectionDecision = { decision ->
        when (decision) {
            "safe mode" -> "Safe Mode"
            "policy disabled" -> "정책 OFF"
            "whitelist" -> "예외"
            "no limit" -> "제한 없음"
            "under limit" -> "허용"
            "total limit exceeded" -> "전체 초과"
            "schedule block active" -> "스케줄"
            "allow-only mode active" -> "허용 앱만"
            "group limit exceeded" -> "그룹 초과"
            "app limit exceeded" -> "앱 초과"
            else -> decision
        }
    }
    blockDecision = { decision ->
        when (decision) {
            BlockDecision.AllowedSafeMode -> "Safe Mode"
            BlockDecision.AllowedPolicyDisabled -> "정책 OFF"
            BlockDecision.AllowedWhitelist -> "예외"
            BlockDecision.AllowedNoLimit -> "제한 없음"
            BlockDecision.AllowedUnderLimit -> "허용"
            BlockDecision.WouldBlockTotalLimit -> "전체"
            BlockDecision.WouldBlockSchedule -> "스케줄"
            BlockDecision.WouldBlockAllowOnly -> "허용 앱만"
            BlockDecision.WouldBlockGroupLimit -> "그룹"
            BlockDecision.WouldBlockAppLimit -> "앱"
        }
    }
}

@Preview(name = "Phone", showBackground = true, widthDp = 411, heightDp = 891)
@Composable
fun ScreenTimeManagerPhonePreview() {
    ScreenTimeManagerPreviewContent()
}

@Preview(name = "Tablet", showBackground = true, widthDp = 900, heightDp = 1200)
@Composable
fun ScreenTimeManagerTabletPreview() {
    ScreenTimeManagerPreviewContent()
}

@Composable
private fun ScreenTimeManagerPreviewContent() {
    ScreenTimeManagerTheme {
        ScreenTimeManagerScreen(
            uiState = SafeModeUiState(safeModeEnabled = true),
            onSafeModeChanged = {},
            onSafeModeEnableWithPin = {},
            onSafeModePinStatusSeen = {},
            onPolicyEnforcementChanged = {},
            onPolicyEnforcementDisableWithPin = {},
            onAppLanguageChanged = {},
            onWarningNotificationsChanged = {},
            onLimitNotificationsChanged = {},
            onEmergencyUnlock = {},
            onEmergencyPinChanged = {},
            onAllowedAppsChanged = {},
            onOpenUsageAccessSettings = {},
            onOpenNotificationAccessSettings = {},
            onOpenExactAlarmSettings = {},
            onOpenOverlaySettings = {},
            onRefreshUsageStats = {},
            onRefreshStatistics = {},
            onPolicyDraftChanged = {},
            onResetPolicyDraft = {},
            onSaveUsagePolicy = {},
            onPolicySaveStatusSeen = {},
            onRequestNotificationPermission = {},
            onUpdateAdminPin = { _, _ -> },
            onUpdateEmergencyPin = { _, _ -> },
            onPinInputChanged = {},
            onPairParentAccount = { _, _, _ -> },
            onUnlinkParentAccount = {},
            onSyncParentDevice = {},
            onClearRemoteParentCommands = {},
            onRemoteAppExtraTime = { _, _, _ -> },
            onRemoteAppUnlockToday = { _, _ -> },
            onRemoteTotalExtraTime = {},
            onRemoteTotalUnlockToday = {},
            onClearEventLog = {},
            onDailyPolicyExpandedChange = {},
            onAppGroupsExpandedChange = {},
            onAppLimitsExpandedChange = {},
            onScheduleBlockingExpandedChange = {},
            onAllowOnlyModeExpandedChange = {},
            suppressPermissionSetupAutoDialog = false,
        )
    }
}
