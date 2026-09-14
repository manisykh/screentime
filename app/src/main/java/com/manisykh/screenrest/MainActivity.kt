package com.manisykh.screenrest

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.Manifest
import android.util.LruCache
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import com.manisykh.screenrest.blocking.BlockedActivity
import com.manisykh.screenrest.blocking.BootRecoveryReceiver
import com.manisykh.screenrest.blocking.UsageMonitorForegroundService
import com.manisykh.screenrest.data.AppLanguage
import com.manisykh.screenrest.data.AppGroupPolicy
import com.manisykh.screenrest.data.EventLogEntry
import com.manisykh.screenrest.data.ForegroundDetectionStatus
import com.manisykh.screenrest.data.HardshipLevel
import com.manisykh.screenrest.data.HardshipPolicyKey
import com.manisykh.screenrest.data.HardshipPolicyType
import com.manisykh.screenrest.data.HardshipRuntimeState
import com.manisykh.screenrest.data.ParentManagementState
import com.manisykh.screenrest.data.ChildUsageSnapshot
import com.manisykh.screenrest.data.ChildUsageRefreshRequest
import com.manisykh.screenrest.data.ImmediateBlockState
import com.manisykh.screenrest.data.ImmediateBlockReadState
import com.manisykh.screenrest.data.ImmediateBlockAvailability
import com.manisykh.screenrest.data.immediateBlockAvailability
import com.manisykh.screenrest.data.ParentAccountAuthState
import com.manisykh.screenrest.data.ParentDeviceRole
import com.manisykh.screenrest.data.ParentNotificationState
import com.manisykh.screenrest.data.LinkedChildDevice
import com.manisykh.screenrest.data.LinkedParentDevice
import com.manisykh.screenrest.data.RemoteRequestBlockReason
import com.manisykh.screenrest.data.RemoteUnlockRequest
import com.manisykh.screenrest.data.RemoteUnlockRequestStatus
import com.manisykh.screenrest.data.groupRemoteRequestsForDisplay
import com.manisykh.screenrest.data.ScheduleTemplatePolicy
import com.manisykh.screenrest.data.SystemHealthStatus
import com.manisykh.screenrest.data.TemporaryUnlockState
import com.manisykh.screenrest.data.temporaryRemainingMinutes
import com.manisykh.screenrest.data.UsagePolicySettings
import com.manisykh.screenrest.data.UsageMonitorStatus
import com.manisykh.screenrest.data.activeScheduleTemplate
import com.manisykh.screenrest.data.isScheduleBlockingNow
import com.manisykh.screenrest.data.normalizedAppGroups
import com.manisykh.screenrest.data.normalizedScheduleTemplates
import com.manisykh.screenrest.data.overlappingSchedulePairs
import com.manisykh.screenrest.data.scheduleDaySet
import com.manisykh.screenrest.data.selectedScheduleTemplate
import com.manisykh.screenrest.data.toScheduleDaysEncoded
import com.manisykh.screenrest.data.toScheduleTemplatesEncoded
import com.manisykh.screenrest.data.toAppGroupsEncoded
import com.manisykh.screenrest.data.hardshipLevelFor
import com.manisykh.screenrest.data.configurationReflectionReadyAtMillis
import com.manisykh.screenrest.data.withHardshipLevel
import com.manisykh.screenrest.data.allowOnlyHardshipKey
import com.manisykh.screenrest.data.appGroupHardshipKey
import com.manisykh.screenrest.data.appLimitHardshipKey
import com.manisykh.screenrest.data.appLimitActiveDayMap
import com.manisykh.screenrest.data.currentPolicyDayOfWeek
import com.manisykh.screenrest.data.normalizedPolicyDays
import com.manisykh.screenrest.data.toAppLimitActiveDaysEncoded
import com.manisykh.screenrest.data.dailyHardshipKey
import com.manisykh.screenrest.data.scheduleHardshipKey
import com.manisykh.screenrest.data.nextOccurrenceEndMillis
import com.manisykh.screenrest.data.encodeOptionalLimitMinutes
import com.manisykh.screenrest.data.limitMinutesOrNull
import com.manisykh.screenrest.notification.UsageNotificationHelper
import com.manisykh.screenrest.safety.BlockDecision
import com.manisykh.screenrest.safety.BlockDecisionResult
import com.manisykh.screenrest.safety.AndroidSystemInteractionResolver
import com.manisykh.screenrest.safety.LinkedAppFamily
import com.manisykh.screenrest.safety.SafetyGate
import com.manisykh.screenrest.ui.safety.AppGroupSummary
import com.manisykh.screenrest.ui.safety.AppLimitSummary
import com.manisykh.screenrest.ui.safety.AutoRecoveryStatus
import com.manisykh.screenrest.ui.safety.BlockingReadiness
import com.manisykh.screenrest.ui.safety.SafeRecoveryStatus
import com.manisykh.screenrest.ui.safety.LimitStatus
import com.manisykh.screenrest.ui.safety.PinChangeStatus
import com.manisykh.screenrest.ui.safety.PolicyBudgetValidation
import com.manisykh.screenrest.ui.safety.PolicySummary
import com.manisykh.screenrest.ui.safety.EffectiveAppAccess
import com.manisykh.screenrest.ui.safety.EffectiveAppPolicySummary
import com.manisykh.screenrest.ui.safety.EffectiveTimeLimiter
import com.manisykh.screenrest.ui.safety.PolicySaveStatus
import com.manisykh.screenrest.ui.safety.SafeModeUiState
import com.manisykh.screenrest.ui.safety.SafeModePinStatus
import com.manisykh.screenrest.ui.safety.ScheduleSummary
import com.manisykh.screenrest.ui.safety.SafeModeViewModel
import com.manisykh.screenrest.ui.safety.TemporaryAllowedAppSummary
import com.manisykh.screenrest.ui.safety.TopAppsUsageSet
import com.manisykh.screenrest.ui.safety.buildTemporaryAllowedAppSummaries
import com.manisykh.screenrest.ui.safety.appLimitMap
import com.manisykh.screenrest.ui.safety.dailyLimitMinutesByDayOrNull
import com.manisykh.screenrest.ui.safety.toAppLimitRules
import com.manisykh.screenrest.ui.designsystem.ScreenRestCard
import com.manisykh.screenrest.ui.designsystem.ScreenRestDesignTheme
import com.manisykh.screenrest.ui.designsystem.ScreenRestIconContainer
import com.manisykh.screenrest.ui.designsystem.ScreenRestListRow
import com.manisykh.screenrest.ui.designsystem.ScreenRestPalette
import com.manisykh.screenrest.ui.designsystem.ScreenRestPageHeader
import com.manisykh.screenrest.ui.designsystem.ScreenRestPrimaryButton
import com.manisykh.screenrest.ui.designsystem.ScreenRestSectionHeader
import com.manisykh.screenrest.ui.designsystem.ScreenRestStatusPill
import com.manisykh.screenrest.ui.designsystem.ScreenRestTheme
import com.manisykh.screenrest.ui.designsystem.ScreenRestTone
import com.manisykh.screenrest.ui.theme.AppOver
import com.manisykh.screenrest.ui.theme.AppSafe
import com.manisykh.screenrest.ui.theme.AppWarn
import com.manisykh.screenrest.usage.AppUsageInfo
import com.manisykh.screenrest.usage.DailyUsageInfo
import com.manisykh.screenrest.usage.ForegroundAppTracker
import com.manisykh.screenrest.usage.InstalledAppInfo
import com.manisykh.screenrest.worker.SystemHealthCheckWorker
import com.manisykh.screenrest.worker.UsageMonitorRecoveryWorker
import com.manisykh.screenrest.worker.UsagePolicyCheckWorker
import com.manisykh.screenrest.worker.RemoteParentSyncWorker
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
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
    private val openParentRequestsSignal = mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyLaunchIntent(intent)
        UsageNotificationHelper(this).ensureChannel()
        UsageNotificationHelper(this).ensureParentRequestChannel()
        enableEdgeToEdge()
        setContent {
            ScreenRestDesignTheme {
                val uiState by safeModeViewModel.uiState.collectAsStateWithLifecycle()
                if (!uiState.monitoringDisclosureLoaded) {
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        modifier = Modifier.fillMaxSize(),
                    ) {}
                } else if (!uiState.monitoringDisclosureAccepted) {
                    MonitoringDisclosureScreen(
                        appLanguage = uiState.appLanguage,
                        onAccept = safeModeViewModel::acceptMonitoringDisclosure,
                        onExit = ::finishAffinity,
                    )
                } else if (!uiState.securityPinsConfigured) {
                    InitialPinSetupScreen(
                        appLanguage = uiState.appLanguage,
                        onConfigure = safeModeViewModel::configureInitialAdminPin,
                        onExit = ::finishAffinity,
                    )
                } else {
                    val parentAccountAuthState by safeModeViewModel.parentAccountAuthState
                        .collectAsStateWithLifecycle()
                    LaunchedEffect(Unit) {
                        AndroidSystemInteractionResolver(this@MainActivity)
                            .refreshDetectedRelationships()
                        UsagePolicyCheckWorker.schedule(this@MainActivity)
                        SystemHealthCheckWorker.scheduleNow(this@MainActivity)
                        SystemHealthCheckWorker.schedulePeriodic(this@MainActivity)
                        RemoteParentSyncWorker.schedule(this@MainActivity)
                        BootRecoveryReceiver.scheduleDailyRolloverAlarm(this@MainActivity)
                        UsageMonitorRecoveryWorker.schedulePeriodic(this@MainActivity)
                    }
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
                            parentAccountAuthState = parentAccountAuthState,
                            onSafeModeChanged = safeModeViewModel::setSafeModeEnabled,
                            onSafeModeEnableWithPin = safeModeViewModel::enableSafeModeWithAdminPin,
                            onSafeModePinStatusSeen = safeModeViewModel::clearSafeModePinStatus,
                            onPolicyEnforcementChanged = safeModeViewModel::setPolicyEnforcementEnabled,
                            onPolicyEnforcementDisableWithPin = safeModeViewModel::disablePolicyEnforcementWithAdminPin,
                            onAppLanguageChanged = safeModeViewModel::setAppLanguage,
                            onWarningNotificationsChanged = safeModeViewModel::setWarningNotificationsEnabled,
                            onLimitNotificationsChanged = safeModeViewModel::setLimitNotificationsEnabled,
                            onSafeRecovery = safeModeViewModel::submitSafeRecoveryAdminPin,
                            onSafeRecoveryPinChanged = safeModeViewModel::clearSafeRecoveryStatus,
                            onAllowedAppsChanged = safeModeViewModel::setAllowedAppPackages,
                            onAllRestrictionsExemptAppsChanged =
                                safeModeViewModel::setAllRestrictionsExemptPackages,
                            onOpenUsageAccessSettings = ::openUsageAccessSettings,
                            onOpenNotificationAccessSettings = ::openNotificationAccessSettings,
                            onOpenExactAlarmSettings = ::openExactAlarmSettings,
                            onOpenOverlaySettings = ::openOverlaySettings,
                            onRefreshUsageStats = { safeModeViewModel.refreshUsageStats(force = true) },
                            onRefreshStatistics = { safeModeViewModel.refreshStatistics() },
                            onPolicyDraftChanged = safeModeViewModel::updatePolicyDraft,
                            onStartHardshipConfigurationReflection =
                                safeModeViewModel::startHardshipConfigurationReflection,
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
                            onPinInputChanged = safeModeViewModel::clearPinChangeStatus,
                            onPairParentAccount = safeModeViewModel::pairParentAccount,
                            onParentProfileNameChanged = safeModeViewModel::setParentProfileName,
                            onParentDeviceRoleChanged = safeModeViewModel::setParentDeviceRole,
                            onGenerateChildPairingCode = safeModeViewModel::generateChildPairingCode,
                            onRegisterChildPairingCode = safeModeViewModel::registerChildPairingCode,
                            onParentGoogleSignIn = ::launchParentGoogleSignIn,
                            onDeleteAccountAndCloudData =
                                safeModeViewModel::deleteCurrentAccountAndCloudData,
                            onUnlinkParentAccount = safeModeViewModel::unlinkParentAccount,
                            onUnlinkLinkedChildDevice = safeModeViewModel::unlinkLinkedChildDevice,
                            onUnlinkLinkedParentDevice = safeModeViewModel::unlinkLinkedParentDevice,
                            onSyncParentDevice = safeModeViewModel::syncParentDevice,
                            onCheckImmediateBlock = safeModeViewModel::checkImmediateBlock,
                            onChildTopAppsSharingChanged = safeModeViewModel::setChildTopAppsSharingEnabled,
                            onStartImmediateBlock = safeModeViewModel::startImmediateBlock,
                            onStopImmediateBlock = safeModeViewModel::stopImmediateBlock,
                            onClearRemoteParentCommands = safeModeViewModel::clearRemoteParentCommands,
                            onRemoteAppExtraTime = safeModeViewModel::applyRemoteAppExtraTime,
                            onRemoteAppUnlockToday = safeModeViewModel::applyRemoteAppUnlockToday,
                            onRemoteTotalExtraTime = safeModeViewModel::applyRemoteTotalExtraTime,
                            onRemoteTotalUnlockToday = safeModeViewModel::applyRemoteTotalUnlockToday,
                            onApproveRemoteUnlockRequest = safeModeViewModel::approveRemoteUnlockRequest,
                            onRejectRemoteUnlockRequest = safeModeViewModel::rejectRemoteUnlockRequest,
                            onClearEventLog = safeModeViewModel::clearEventLog,
                            onDailyPolicyExpandedChange = safeModeViewModel::setDailyPolicyExpanded,
                            onAppGroupsExpandedChange = safeModeViewModel::setAppGroupsExpanded,
                            onAppLimitsExpandedChange = safeModeViewModel::setAppLimitsExpanded,
                            onScheduleBlockingExpandedChange = safeModeViewModel::setScheduleBlockingExpanded,
                            onAllowOnlyModeExpandedChange = safeModeViewModel::setAllowOnlyModeExpanded,
                            onSettingsLanguageExpandedChange = safeModeViewModel::setSettingsLanguageExpanded,
                            onSettingsNotificationExpandedChange = safeModeViewModel::setSettingsNotificationExpanded,
                            onSettingsPinExpandedChange = safeModeViewModel::setSettingsPinExpanded,
                            onSettingsParentManagementExpandedChange = safeModeViewModel::setSettingsParentManagementExpanded,
                            onSettingsEventLogExpandedChange = safeModeViewModel::setSettingsEventLogExpanded,
                            suppressPermissionSetupAutoDialog = this@MainActivity.suppressPermissionSetupAutoDialog.value,
                            openParentRequestsSignal = this@MainActivity.openParentRequestsSignal.value,
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
    }

    private fun launchParentGoogleSignIn() {
        val clientIdResource = resources.getIdentifier(
            "default_web_client_id",
            "string",
            packageName,
        )
        if (clientIdResource == 0) {
            safeModeViewModel.reportParentGoogleSignInFailure("default_web_client_id is missing")
            return
        }
        val serverClientId = getString(clientIdResource).trim()
        if (serverClientId.isBlank()) {
            safeModeViewModel.reportParentGoogleSignInFailure("default_web_client_id is blank")
            return
        }
        lifecycleScope.launch {
            try {
                val googleOption = GetSignInWithGoogleOption.Builder(serverClientId).build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleOption)
                    .build()
                val credential = CredentialManager.create(this@MainActivity)
                    .getCredential(
                        context = this@MainActivity,
                        request = request,
                    )
                    .credential
                if (
                    credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    safeModeViewModel.signInParentWithGoogleIdToken(googleCredential.idToken)
                } else {
                    safeModeViewModel.reportParentGoogleSignInFailure("Unexpected credential type")
                }
            } catch (error: GetCredentialException) {
                safeModeViewModel.reportParentGoogleSignInFailure(
                    "${error.javaClass.simpleName}: ${error.message.orEmpty()}",
                )
            } catch (error: RuntimeException) {
                safeModeViewModel.reportParentGoogleSignInFailure(
                    "${error.javaClass.simpleName}: ${error.message.orEmpty()}",
                )
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
        ForegroundAppTracker.clear()
        val currentUiState = safeModeViewModel.uiState.value
        if (
            currentUiState.monitoringDisclosureAccepted &&
            !currentUiState.safeModeEnabled &&
            currentUiState.policyEnforcementEnabled
        ) {
            UsageMonitorForegroundService.managerVisible(this)
        }
        safeModeViewModel.refreshForForeground(
            force = true,
            settleUsageEvents = true,
        )
    }

    override fun onStop() {
        val currentUiState = safeModeViewModel.uiState.value
        if (
            currentUiState.monitoringDisclosureAccepted &&
            !currentUiState.safeModeEnabled &&
            currentUiState.policyEnforcementEnabled
        ) {
            UsageMonitorForegroundService.start(this)
        }
        safeModeViewModel.markAppStoppedCleanly()
        super.onStop()
    }

    private fun applyLaunchIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_SUPPRESS_PERMISSION_SETUP_AUTO_DIALOG, false) == true) {
            suppressPermissionSetupAutoDialog.value = true
        }
        if (intent?.getBooleanExtra(EXTRA_OPEN_PARENT_REQUESTS, false) == true) {
            openParentRequestsSignal.value += 1
            intent.removeExtra(EXTRA_OPEN_PARENT_REQUESTS)
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
        const val EXTRA_OPEN_PARENT_REQUESTS =
            "com.manisykh.screenrest.extra.OPEN_PARENT_REQUESTS"
    }
}

@Composable
private fun MonitoringDisclosureScreen(
    appLanguage: AppLanguage,
    onAccept: () -> Unit,
    onExit: () -> Unit,
) {
    val korean = appLanguage == AppLanguage.Korean
    BackHandler(onBack = onExit)
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(WindowInsets.safeDrawing.asPaddingValues())
                .padding(horizontal = 24.dp, vertical = 28.dp),
        ) {
            Text(
                text = if (korean) "사용 시간 관리 안내" else "Screen time monitoring notice",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (korean) {
                    "폰 쉼은 사용 시간 제한과 자녀·부모 승인을 제공하기 위해 기기 사용 정보를 지속적으로 확인합니다."
                } else {
                    "ScreenRest continuously checks device usage to enforce screen-time limits and support parent approvals."
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(20.dp),
                ) {
                    MonitoringDisclosureItem(
                        title = if (korean) "기기에서 확인" else "Checked on this device",
                        body = if (korean) {
                            "설치된 앱 이름, 앱별 사용 시간, 현재 실행 앱과 화면 켜짐 상태를 확인합니다. 화면이 꺼진 시간은 사용 시간에 포함하지 않습니다."
                        } else {
                            "Installed app names, per-app usage time, the foreground app, and screen-on state are checked. Screen-off time is not counted."
                        },
                    )
                    MonitoringDisclosureItem(
                        title = if (korean) "연결된 부모에게 전송" else "Shared with a linked parent",
                        body = if (korean) {
                            "자녀 기기를 연결하면 차단 대상, 전체 사용·제한 시간, 승인 요청과 처리 상태, 기기 연결 식별자 및 알림 토큰을 Firebase를 통해 연결된 부모에게 전송합니다. 앱별 공유에 별도 동의하면 오늘 많이 사용한 앱 최대 5개의 이름과 사용 시간도 전송합니다."
                        } else {
                            "When a child device is linked, blocked targets, total used and limited time, approval requests and decisions, a device-link identifier, and notification token are sent through Firebase to linked parents. With separate app-usage sharing consent, names and durations of up to five most-used apps are also sent."
                        },
                    )
                    MonitoringDisclosureItem(
                        title = if (korean) "백그라운드 동작" else "Background operation",
                        body = if (korean) {
                            "앱을 닫아도 제한 적용과 승인 알림을 유지하기 위해 감시 서비스가 백그라운드에서 실행되며 지속 알림이 표시될 수 있습니다."
                        } else {
                            "A monitoring service can run in the background and show a persistent notification so limits and approval alerts continue after the app is closed."
                        },
                    )
                    MonitoringDisclosureItem(
                        title = if (korean) "오류 진단" else "Crash diagnostics",
                        body = if (korean) {
                            "앱 충돌과 응답 없음 문제를 개선하기 위해 충돌 시각, 기기·OS 정보와 오류 기록을 Firebase Crashlytics로 전송합니다. Google Analytics는 사용하지 않습니다."
                        } else {
                            "To improve crashes and ANRs, crash time, device and OS information, and error logs are sent to Firebase Crashlytics. Google Analytics is not used."
                        },
                    )
                }
            }
            Text(
                text = if (korean) {
                    "이 정보는 화면 시간 관리와 부모 승인 목적으로만 사용됩니다. 동의하기 전에는 감시, 원격 동기화 및 푸시 토큰 등록을 시작하지 않습니다."
                } else {
                    "This information is used only for screen-time management and parent approvals. Monitoring, remote sync, and push-token registration do not start before consent."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = onAccept,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
            ) {
                Text(if (korean) "동의하고 계속" else "Agree and continue")
            }
            OutlinedButton(
                onClick = onExit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
            ) {
                Text(if (korean) "앱 종료" else "Exit app")
            }
        }
    }
}

@Composable
private fun MonitoringDisclosureItem(
    title: String,
    body: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun InitialPinSetupScreen(
    appLanguage: AppLanguage,
    onConfigure: (String) -> Unit,
    onExit: () -> Unit,
) {
    val korean = appLanguage == AppLanguage.Korean
    var adminPin by rememberSaveable { mutableStateOf("") }
    var adminPinConfirm by rememberSaveable { mutableStateOf("") }
    var errorMessage by rememberSaveable { mutableStateOf("") }
    BackHandler(onBack = onExit)

    fun updatePin(value: String): String = value.filter(Char::isDigit).take(8)
    fun submit() {
        errorMessage = when {
            adminPin.length !in 4..8 -> if (korean) {
                "관리 PIN을 숫자 4~8자리로 입력하세요."
            } else {
                "Enter 4 to 8 digits for the Admin PIN."
            }
            adminPin != adminPinConfirm -> if (korean) {
                "관리 PIN 확인 값이 일치하지 않습니다."
            } else {
                "Admin PIN confirmation does not match."
            }
            else -> ""
        }
        if (errorMessage.isEmpty()) {
            onConfigure(adminPin)
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(WindowInsets.safeDrawing.asPaddingValues())
                .padding(horizontal = 24.dp, vertical = 28.dp),
        ) {
            Text(
                text = if (korean) "관리 PIN 설정" else "Set Admin PIN",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (korean) {
                    "관리 PIN은 설정 변경, 부모 연결, 차단 해제 확인에 사용됩니다. 고행 3단계는 관리 PIN만으로 즉시 종료할 수 없습니다."
                } else {
                    "The Admin PIN confirms settings, pairing, and allowed recovery actions. It cannot immediately end active hardship level 3."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = adminPin,
                onValueChange = { adminPin = updatePin(it); errorMessage = "" },
                label = { Text(if (korean) "관리 PIN" else "Admin PIN") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = adminPinConfirm,
                onValueChange = { adminPinConfirm = updatePin(it); errorMessage = "" },
                label = { Text(if (korean) "관리 PIN 확인" else "Confirm admin PIN") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            if (errorMessage.isNotEmpty()) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = if (korean) {
                    "관리 PIN은 복구할 수 없습니다. 안전한 곳에 보관하세요. 5회 연속 오류 시 30초 동안 입력이 잠깁니다."
                } else {
                    "The Admin PIN cannot be recovered. Keep it in a safe place. Five failed attempts lock input for 30 seconds."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = ::submit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
            ) {
                Text(if (korean) "PIN 저장" else "Save PIN")
            }
            OutlinedButton(
                onClick = onExit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
            ) {
                Text(if (korean) "앱 종료" else "Exit app")
            }
        }
    }
}

private enum class ScreenTab {
    Today,
    Rules,
    Stats,
    Family,
    More,
    Time,
    Blocking,
    Safety,
    Settings,
}

private enum class ScreenLayoutMode {
    Modern,
    Classic,
}

private const val SCREEN_LAYOUT_PREFS = "screen_layout"
private const val SCREEN_LAYOUT_MODE_KEY = "mode"

private enum class MoreDestination {
    Home,
    ProtectionStatus,
    AdminPin,
    EmergencyPass,
    Permissions,
    Notifications,
    Language,
    GoogleAccount,
    Help,
    Diagnostics,
}

private enum class FamilyDestination {
    Home,
    Management,
}

private enum class SafetyPinAction {
    EnableSafeMode,
    DisablePolicyEnforcement,
}

private val PrimaryScreenTabs = listOf(
    ScreenTab.Today,
    ScreenTab.Rules,
    ScreenTab.Stats,
    ScreenTab.Family,
    ScreenTab.More,
)

private val ClassicScreenTabs = listOf(
    ScreenTab.Today,
    ScreenTab.Time,
    ScreenTab.Blocking,
    ScreenTab.Stats,
    ScreenTab.Safety,
    ScreenTab.Settings,
)

private fun ScreenLayoutMode.tabs(): List<ScreenTab> = when (this) {
    ScreenLayoutMode.Modern -> PrimaryScreenTabs
    ScreenLayoutMode.Classic -> ClassicScreenTabs
}

@Composable
fun ScreenTimeManagerScreen(
    uiState: SafeModeUiState,
    parentAccountAuthState: ParentAccountAuthState,
    onSafeModeChanged: (Boolean) -> Unit,
    onSafeModeEnableWithPin: (String) -> Unit,
    onSafeModePinStatusSeen: () -> Unit,
    onPolicyEnforcementChanged: (Boolean) -> Unit,
    onPolicyEnforcementDisableWithPin: (String) -> Unit,
    onAppLanguageChanged: (AppLanguage) -> Unit,
    onWarningNotificationsChanged: (Boolean) -> Unit,
    onLimitNotificationsChanged: (Boolean) -> Unit,
    onSafeRecovery: (String) -> Unit,
    onSafeRecoveryPinChanged: () -> Unit,
    onAllowedAppsChanged: (Set<String>) -> Unit,
    onAllRestrictionsExemptAppsChanged: (Set<String>) -> Unit,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenNotificationAccessSettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onRefreshUsageStats: () -> Unit,
    onRefreshStatistics: () -> Unit,
    onPolicyDraftChanged: (UsagePolicySettings) -> Unit,
    onStartHardshipConfigurationReflection: (HardshipPolicyKey) -> Unit,
    onResetPolicyDraft: () -> Unit,
    onSaveUsagePolicy: (String) -> Unit,
    onPolicySaveStatusSeen: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onUpdateAdminPin: (String, String) -> Unit,
    onPinInputChanged: () -> Unit,
    onPairParentAccount: (String, String, String) -> Unit,
    onParentProfileNameChanged: (String) -> Unit,
    onParentDeviceRoleChanged: (ParentDeviceRole, String) -> Unit,
    onGenerateChildPairingCode: (String) -> Unit,
    onRegisterChildPairingCode: (String, String, String) -> Unit,
    onParentGoogleSignIn: () -> Unit,
    onDeleteAccountAndCloudData: (String) -> Unit,
    onUnlinkParentAccount: (String) -> Unit,
    onUnlinkLinkedChildDevice: (String, String) -> Unit,
    onUnlinkLinkedParentDevice: (String, String) -> Unit,
    onSyncParentDevice: () -> Unit,
    onCheckImmediateBlock: (String) -> Unit,
    onChildTopAppsSharingChanged: (Boolean) -> Unit,
    onStartImmediateBlock: (String, Int, String) -> Unit,
    onStopImmediateBlock: (String, String, String) -> Unit,
    onClearRemoteParentCommands: (String) -> Unit,
    onRemoteAppExtraTime: (String, String, Int) -> Unit,
    onRemoteAppUnlockToday: (String, String) -> Unit,
    onRemoteTotalExtraTime: (Int) -> Unit,
    onRemoteTotalUnlockToday: () -> Unit,
    onApproveRemoteUnlockRequest: (String, Int, Boolean) -> Unit,
    onRejectRemoteUnlockRequest: (String) -> Unit,
    onClearEventLog: () -> Unit,
    onDailyPolicyExpandedChange: (Boolean) -> Unit,
    onAppGroupsExpandedChange: (Boolean) -> Unit,
    onAppLimitsExpandedChange: (Boolean) -> Unit,
    onScheduleBlockingExpandedChange: (Boolean) -> Unit,
    onAllowOnlyModeExpandedChange: (Boolean) -> Unit,
    onSettingsLanguageExpandedChange: (Boolean) -> Unit,
    onSettingsNotificationExpandedChange: (Boolean) -> Unit,
    onSettingsPinExpandedChange: (Boolean) -> Unit,
    onSettingsParentManagementExpandedChange: (Boolean) -> Unit,
    onSettingsEventLogExpandedChange: (Boolean) -> Unit,
    suppressPermissionSetupAutoDialog: Boolean,
    openParentRequestsSignal: Int = 0,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableStateOf(ScreenTab.Today) }
    var moreDestination by rememberSaveable { mutableStateOf(MoreDestination.Home) }
    var familyDestination by rememberSaveable { mutableStateOf(FamilyDestination.Home) }
    var previousTab by remember { mutableStateOf<ScreenTab?>(null) }
    var tabTransitionDirection by remember { mutableStateOf(1) }
    var safeRecoveryAdminPin by remember { mutableStateOf("") }
    var policyAdminPin by remember { mutableStateOf("") }
    var pendingParentManagementAction by remember { mutableStateOf<ParentManagementPendingAction?>(null) }
    var showPolicySaveDialog by remember { mutableStateOf(false) }
    var permissionSetupDismissedThisSession by remember { mutableStateOf(false) }
    var permissionSetupGateReady by remember { mutableStateOf(false) }
    var showInitialPermissionSetupDialog by remember { mutableStateOf(false) }
    val text = appStrings(uiState.appLanguage)
    val context = LocalContext.current
    val layoutPrefs = remember(context) {
        context.getSharedPreferences(SCREEN_LAYOUT_PREFS, android.content.Context.MODE_PRIVATE)
    }
    var layoutMode by rememberSaveable {
        mutableStateOf(
            runCatching {
                ScreenLayoutMode.valueOf(
                    layoutPrefs.getString(SCREEN_LAYOUT_MODE_KEY, ScreenLayoutMode.Modern.name)
                        ?: ScreenLayoutMode.Modern.name,
                )
            }.getOrDefault(ScreenLayoutMode.Modern),
        )
    }
    val permissionSetupRequired = !uiState.hasUsageAccess ||
        !uiState.blockingReadiness.overlayPermissionReady ||
        !uiState.blockingReadiness.notificationPermissionReady
    val permissionSetupAutoPromptRequired = permissionSetupRequired &&
        !uiState.permissionSetupCompletedOnce
    val hasPolicySaveProblem = uiState.policyBudgetValidation.hasOverflow ||
        uiState.policySaveStatus == PolicySaveStatus.InvalidAdminPin ||
        uiState.policySaveStatus == PolicySaveStatus.BudgetExceeded ||
        uiState.policySaveStatus == PolicySaveStatus.PolicyConflict ||
        uiState.policySaveStatus == PolicySaveStatus.HardshipLocked ||
        uiState.policySaveStatus == PolicySaveStatus.HardshipReflectionRequired ||
        uiState.policySaveStatus == PolicySaveStatus.HardshipReflectionWaiting
    val hasPendingParentManagementAction = pendingParentManagementAction != null
    val hasSaveChanges = uiState.policyDraftHasChanges || hasPendingParentManagementAction
    val saveBudgetValidation = if (uiState.policyDraftHasChanges) {
        uiState.policyBudgetValidation
    } else {
        PolicyBudgetValidation()
    }
    val screenBackground by animateColorAsState(
        targetValue = when {
            hasPolicySaveProblem || hasSaveChanges -> AppOver.copy(alpha = 0.045f)
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

    LaunchedEffect(showPolicySaveDialog, uiState.policySaveStatus) {
        if (showPolicySaveDialog && uiState.policySaveStatus == PolicySaveStatus.Saved) {
            delay(450L)
            showPolicySaveDialog = false
            policyAdminPin = ""
        }
    }

    LaunchedEffect(uiState.parentManagementState.deviceRole, pendingParentManagementAction) {
        val pendingRole = pendingParentManagementAction as? ParentManagementPendingAction.ChangeRole
        if (pendingRole != null && uiState.parentManagementState.deviceRole == pendingRole.role) {
            pendingParentManagementAction = null
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
        if (selectedTab != ScreenTab.More) {
            moreDestination = MoreDestination.Home
        }
        if (selectedTab != ScreenTab.Family) {
            familyDestination = FamilyDestination.Home
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val resumedSelectedTab by rememberUpdatedState(selectedTab)
    val resumedRefreshStatistics by rememberUpdatedState(onRefreshStatistics)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && resumedSelectedTab == ScreenTab.Stats) {
                resumedRefreshStatistics()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun selectTab(nextTab: ScreenTab) {
        if (nextTab !in layoutMode.tabs()) return
        if (nextTab == selectedTab) {
            if (nextTab == ScreenTab.More) {
                moreDestination = MoreDestination.Home
            }
            if (nextTab == ScreenTab.Family) {
                familyDestination = FamilyDestination.Home
            }
            return
        }
        previousTab = selectedTab
        tabTransitionDirection = if (
            layoutMode.tabs().indexOf(nextTab) > layoutMode.tabs().indexOf(selectedTab)
        ) 1 else -1
        selectedTab = nextTab
    }

    fun changeLayoutMode(nextMode: ScreenLayoutMode) {
        if (nextMode == layoutMode) return
        layoutPrefs.edit().putString(SCREEN_LAYOUT_MODE_KEY, nextMode.name).apply()
        layoutMode = nextMode
        selectedTab = ScreenTab.Today
        previousTab = null
        moreDestination = MoreDestination.Home
        familyDestination = FamilyDestination.Home
    }

    BackHandler(
        enabled = selectedTab == ScreenTab.More && moreDestination != MoreDestination.Home,
    ) {
        moreDestination = MoreDestination.Home
    }
    BackHandler(
        enabled = selectedTab == ScreenTab.Family && familyDestination != FamilyDestination.Home,
    ) {
        familyDestination = FamilyDestination.Home
    }

    LaunchedEffect(openParentRequestsSignal) {
        if (openParentRequestsSignal > 0) {
            selectTab(if (layoutMode == ScreenLayoutMode.Modern) ScreenTab.Family else ScreenTab.Settings)
            onSettingsParentManagementExpandedChange(true)
        }
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
        LaunchedEffect(selectedTab, moreDestination, familyDestination) {
            screenScrollState.scrollTo(0)
        }
        val isKeyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        val bottomBarOuterVerticalPadding = if (isLandscape && isExpanded) 2.dp else 10.dp
        val contentBottomPadding = when {
            isKeyboardVisible -> 28.dp
            isLandscape && isExpanded -> if (hasSaveChanges) 158.dp else 88.dp
            hasSaveChanges -> 184.dp
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
                        ScreenTab.Today -> OverviewContent(
                            uiState = uiState,
                            parentAccountAuthState = parentAccountAuthState,
                            permissionSetupRequired = permissionSetupRequired,
                            text = text,
                            isExpanded = isExpanded,
                            onRefreshUsageStats = onRefreshUsageStats,
                            onOpenRules = { selectTab(if (layoutMode == ScreenLayoutMode.Modern) ScreenTab.Rules else ScreenTab.Time) },
                            onOpenFamily = { selectTab(if (layoutMode == ScreenLayoutMode.Modern) ScreenTab.Family else ScreenTab.Settings) },
                            onOpenStatistics = { selectTab(ScreenTab.Stats) },
                        )

                        ScreenTab.Rules -> RulesContent(
                            uiState = uiState,
                            parentAccountAuthState = parentAccountAuthState,
                            text = text,
                            onOpenFamily = { selectTab(if (layoutMode == ScreenLayoutMode.Modern) ScreenTab.Family else ScreenTab.Settings) },
                        ) {
                            UsagePolicySection(
                                settings = uiState.policyDraftSettings,
                                temporaryUnlockState = uiState.temporaryUnlockState,
                                activeHardshipPolicyKeys = uiState.hardshipRuntimeState.activePolicyKeys,
                                hardshipRuntimeState = uiState.hardshipRuntimeState,
                                installedApps = uiState.installedApps,
                                allowedAppPackages = uiState.allowedAppPackages,
                                allRestrictionsExemptPackages = uiState.allRestrictionsExemptPackages,
                                text = text,
                                isExpanded = isExpanded,
                                contentMode = PolicyContentMode.AllControls,
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
                                onStartHardshipConfigurationReflection = onStartHardshipConfigurationReflection,
                                onHardshipPolicyDraftChanged = { nextSettings, adminPin ->
                                    onPolicyDraftChanged(nextSettings)
                                    policyAdminPin = adminPin
                                    showPolicySaveDialog = true
                                },
                                onAllowedAppsChanged = onAllowedAppsChanged,
                                onAllRestrictionsExemptAppsChanged = onAllRestrictionsExemptAppsChanged,
                            )
                        }

                        ScreenTab.Stats -> StatisticsContent(
                            uiState = uiState,
                            parentAccountAuthState = parentAccountAuthState,
                            text = text,
                            isExpanded = isExpanded,
                            onOpenFamily = { selectTab(if (layoutMode == ScreenLayoutMode.Modern) ScreenTab.Family else ScreenTab.Settings) },
                        )

                        ScreenTab.Family -> FamilyContent(
                            destination = familyDestination,
                            onDestinationChanged = { familyDestination = it },
                            parentState = uiState.parentManagementState,
                            childTopAppsSharingEnabled = uiState.childTopAppsSharingEnabled,
                            childUsageSnapshots = uiState.childUsageSnapshots,
                            childUsageRefreshRequests = uiState.childUsageRefreshRequests,
                            childImmediateBlocks = uiState.childImmediateBlocks,
                            localImmediateBlock = uiState.localImmediateBlock,
                            parentAccountAuthState = parentAccountAuthState,
                            notificationState = uiState.parentNotificationState,
                            parentRequestNotificationReady = uiState.parentRequestNotificationReady,
                            parentRequestNotificationIssue = uiState.parentRequestNotificationIssue,
                            installedApps = uiState.installedApps,
                            policySummary = uiState.policySummary,
                            safeModeEnabled = uiState.safeModeEnabled,
                            policyEnforcementEnabled = uiState.policyEnforcementEnabled,
                            text = text,
                            onPairParentAccount = onPairParentAccount,
                            onParentProfileNameChanged = onParentProfileNameChanged,
                            onParentDeviceRoleChanged = onParentDeviceRoleChanged,
                            onGenerateChildPairingCode = onGenerateChildPairingCode,
                            onRegisterChildPairingCode = onRegisterChildPairingCode,
                            onParentGoogleSignIn = onParentGoogleSignIn,
                            onDeleteAccountAndCloudData = onDeleteAccountAndCloudData,
                            onUnlinkParentAccount = onUnlinkParentAccount,
                            onUnlinkLinkedChildDevice = onUnlinkLinkedChildDevice,
                            onUnlinkLinkedParentDevice = onUnlinkLinkedParentDevice,
                            onSyncParentDevice = onSyncParentDevice,
                            onCheckImmediateBlock = onCheckImmediateBlock,
                            onChildTopAppsSharingChanged = onChildTopAppsSharingChanged,
                            onStartImmediateBlock = onStartImmediateBlock,
                            onStopImmediateBlock = onStopImmediateBlock,
                            pendingParentManagementAction = pendingParentManagementAction,
                            onPendingParentManagementActionChanged = { pendingParentManagementAction = it },
                            onClearRemoteParentCommands = onClearRemoteParentCommands,
                            onRemoteAppExtraTime = onRemoteAppExtraTime,
                            onRemoteAppUnlockToday = onRemoteAppUnlockToday,
                            onRemoteTotalExtraTime = onRemoteTotalExtraTime,
                            onRemoteTotalUnlockToday = onRemoteTotalUnlockToday,
                            onApproveRemoteUnlockRequest = onApproveRemoteUnlockRequest,
                            onRejectRemoteUnlockRequest = onRejectRemoteUnlockRequest,
                            onOpenLocalRules = {
                                selectTab(ScreenTab.Rules)
                            },
                        )

                        ScreenTab.More -> MoreContent(
                            destination = moreDestination,
                            onDestinationChanged = { moreDestination = it },
                            uiState = uiState,
                            parentAccountAuthState = parentAccountAuthState,
                            safeRecoveryAdminPin = safeRecoveryAdminPin,
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
                            onSafeRecoveryPinChanged = { value ->
                                safeRecoveryAdminPin = value
                                onSafeRecoveryPinChanged()
                            },
                            onSafeRecoveryClick = {
                                onSafeRecovery(safeRecoveryAdminPin)
                                safeRecoveryAdminPin = ""
                            },
                            onAppLanguageChanged = onAppLanguageChanged,
                            onWarningNotificationsChanged = onWarningNotificationsChanged,
                            onLimitNotificationsChanged = onLimitNotificationsChanged,
                            onOpenUsageAccessSettings = onOpenUsageAccessSettings,
                            onOpenOverlaySettings = onOpenOverlaySettings,
                            onOpenNotificationAccessSettings = onOpenNotificationAccessSettings,
                            onOpenExactAlarmSettings = onOpenExactAlarmSettings,
                            onRequestNotificationPermission = onRequestNotificationPermission,
                            onUpdateAdminPin = onUpdateAdminPin,
                            onPinInputChanged = onPinInputChanged,
                            onParentGoogleSignIn = onParentGoogleSignIn,
                            onDeleteAccountAndCloudData = onDeleteAccountAndCloudData,
                            onClearEventLog = onClearEventLog,
                            layoutMode = layoutMode,
                            onLayoutModeChanged = ::changeLayoutMode,
                        )

                        ScreenTab.Time, ScreenTab.Blocking -> UsagePolicySection(
                            settings = uiState.policyDraftSettings,
                            temporaryUnlockState = uiState.temporaryUnlockState,
                            activeHardshipPolicyKeys = uiState.hardshipRuntimeState.activePolicyKeys,
                            hardshipRuntimeState = uiState.hardshipRuntimeState,
                            installedApps = uiState.installedApps,
                            allowedAppPackages = uiState.allowedAppPackages,
                            allRestrictionsExemptPackages = uiState.allRestrictionsExemptPackages,
                            text = text,
                            isExpanded = isExpanded,
                            contentMode = if (tab == ScreenTab.Time) {
                                PolicyContentMode.TimeControls
                            } else {
                                PolicyContentMode.BlockingControls
                            },
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
                            onStartHardshipConfigurationReflection = onStartHardshipConfigurationReflection,
                            onHardshipPolicyDraftChanged = { nextSettings, adminPin ->
                                onPolicyDraftChanged(nextSettings)
                                policyAdminPin = adminPin
                                showPolicySaveDialog = true
                            },
                            onAllowedAppsChanged = onAllowedAppsChanged,
                            onAllRestrictionsExemptAppsChanged = onAllRestrictionsExemptAppsChanged,
                        )

                        ScreenTab.Safety -> SafetyContent(
                            uiState = uiState,
                            safeRecoveryAdminPin = safeRecoveryAdminPin,
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
                            onSafeRecoveryPinChanged = { value ->
                                safeRecoveryAdminPin = value
                                onSafeRecoveryPinChanged()
                            },
                            onSafeRecoveryClick = {
                                onSafeRecovery(safeRecoveryAdminPin)
                                safeRecoveryAdminPin = ""
                            },
                        )

                        ScreenTab.Settings -> Column {
                            ScreenLayoutModePicker(
                                layoutMode = layoutMode,
                                text = text,
                                onLayoutModeChanged = ::changeLayoutMode,
                            )
                            SettingsContent(
                                uiState = uiState,
                                parentAccountAuthState = parentAccountAuthState,
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
                                onPinInputChanged = onPinInputChanged,
                                onPairParentAccount = onPairParentAccount,
                                onParentProfileNameChanged = onParentProfileNameChanged,
                                onParentDeviceRoleChanged = onParentDeviceRoleChanged,
                                onGenerateChildPairingCode = onGenerateChildPairingCode,
                                onRegisterChildPairingCode = onRegisterChildPairingCode,
                                onParentGoogleSignIn = onParentGoogleSignIn,
                                onDeleteAccountAndCloudData = onDeleteAccountAndCloudData,
                                onUnlinkParentAccount = onUnlinkParentAccount,
                                onUnlinkLinkedChildDevice = onUnlinkLinkedChildDevice,
                                onUnlinkLinkedParentDevice = onUnlinkLinkedParentDevice,
                                onSyncParentDevice = onSyncParentDevice,
                                pendingParentManagementAction = pendingParentManagementAction,
                                onPendingParentManagementActionChanged = { pendingParentManagementAction = it },
                                onClearRemoteParentCommands = onClearRemoteParentCommands,
                                onRemoteAppExtraTime = onRemoteAppExtraTime,
                                onRemoteAppUnlockToday = onRemoteAppUnlockToday,
                                onRemoteTotalExtraTime = onRemoteTotalExtraTime,
                                onRemoteTotalUnlockToday = onRemoteTotalUnlockToday,
                                onApproveRemoteUnlockRequest = onApproveRemoteUnlockRequest,
                                onRejectRemoteUnlockRequest = onRejectRemoteUnlockRequest,
                                onClearEventLog = onClearEventLog,
                                onSettingsLanguageExpandedChange = onSettingsLanguageExpandedChange,
                                onSettingsNotificationExpandedChange = onSettingsNotificationExpandedChange,
                                onSettingsPinExpandedChange = onSettingsPinExpandedChange,
                                onSettingsParentManagementExpandedChange = onSettingsParentManagementExpandedChange,
                                onSettingsEventLogExpandedChange = onSettingsEventLogExpandedChange,
                            )
                        }
                    }
                }
            }
        }

        if (!isKeyboardVisible) {
            if (hasSaveChanges) {
                PendingPolicySaveBanner(
                    text = text,
                    onSaveClick = { showPolicySaveDialog = true },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 20.dp)
                        .padding(bottom = if (isLandscape && isExpanded) 74.dp else 82.dp)
                        .widthIn(max = 680.dp),
                )
            }
            BottomTabBar(
                selectedTab = selectedTab,
                layoutMode = layoutMode,
                text = text,
                onTabSelected = { tab -> selectTab(tab) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp, vertical = bottomBarOuterVerticalPadding)
                    .widthIn(max = 720.dp),
            )
        }

        if (showPolicySaveDialog) {
            PolicySaveDialog(
                adminPin = policyAdminPin,
                text = text,
                policySaveStatus = uiState.policySaveStatus,
                hasPolicyChanges = hasSaveChanges,
                budgetValidation = saveBudgetValidation,
                onAdminPinChanged = { value ->
                    policyAdminPin = value
                    if (uiState.policySaveStatus == PolicySaveStatus.InvalidAdminPin) {
                        onPolicySaveStatusSeen()
                    }
                },
                onResetPolicyDraft = onResetPolicyDraft,
                onResetParentManagementAction = { pendingParentManagementAction = null },
                onDismiss = {
                    showPolicySaveDialog = false
                    policyAdminPin = ""
                },
                onSave = {
                    val savingPolicyDraft = uiState.policyDraftHasChanges
                    if (uiState.policyDraftHasChanges) {
                        onSaveUsagePolicy(policyAdminPin)
                    }
                    pendingParentManagementAction?.let { action ->
                        when (action) {
                            is ParentManagementPendingAction.ChangeRole -> {
                                onParentDeviceRoleChanged(action.role, policyAdminPin)
                            }
                            ParentManagementPendingAction.UnlinkParentAccount -> {
                                onUnlinkParentAccount(policyAdminPin)
                                pendingParentManagementAction = null
                            }
                            is ParentManagementPendingAction.UnlinkLinkedChild -> {
                                onUnlinkLinkedChildDevice(action.childDeviceId, policyAdminPin)
                                pendingParentManagementAction = null
                            }
                            is ParentManagementPendingAction.UnlinkLinkedParent -> {
                                onUnlinkLinkedParentDevice(action.parentUid, policyAdminPin)
                                pendingParentManagementAction = null
                            }
                        }
                    }
                    if (!savingPolicyDraft) {
                        policyAdminPin = ""
                        showPolicySaveDialog = false
                    }
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
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shadowElevation = 3.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
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
    onResetParentManagementAction: () -> Unit = {},
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val hasBudgetOverflow = budgetValidation.hasOverflow
    val status = when {
        hasBudgetOverflow -> LimitStatus.Exceeded
        policySaveStatus == PolicySaveStatus.InvalidAdminPin -> LimitStatus.Exceeded
        policySaveStatus == PolicySaveStatus.BudgetExceeded -> LimitStatus.Exceeded
        policySaveStatus == PolicySaveStatus.PolicyConflict -> LimitStatus.Exceeded
        policySaveStatus == PolicySaveStatus.HardshipLocked -> LimitStatus.Exceeded
        policySaveStatus == PolicySaveStatus.HardshipReflectionRequired -> LimitStatus.Exceeded
        policySaveStatus == PolicySaveStatus.HardshipReflectionWaiting -> LimitStatus.Warning
        policySaveStatus == PolicySaveStatus.Saving -> LimitStatus.Warning
        hasPolicyChanges -> LimitStatus.Warning
        else -> LimitStatus.Normal
    }
    val statusLabel = when {
        hasBudgetOverflow -> text.policyBudgetExceeded
        policySaveStatus == PolicySaveStatus.Saved -> text.policySaved
        policySaveStatus == PolicySaveStatus.InvalidAdminPin -> text.invalidAdminPin
        policySaveStatus == PolicySaveStatus.BudgetExceeded -> text.policyBudgetExceeded
        policySaveStatus == PolicySaveStatus.PolicyConflict -> if (text.appLanguage == AppLanguage.Korean) {
            "설정 충돌을 확인해 주세요"
        } else {
            "Review setting conflicts"
        }
        policySaveStatus == PolicySaveStatus.HardshipLocked -> text.hardshipLockedLabel()
        policySaveStatus == PolicySaveStatus.HardshipReflectionRequired ->
            if (text.appLanguage == AppLanguage.Korean) "고행 종료 숙고를 먼저 시작하세요" else "Start hardship exit reflection first"
        policySaveStatus == PolicySaveStatus.HardshipReflectionWaiting ->
            if (text.appLanguage == AppLanguage.Korean) "고행 종료 숙고가 아직 끝나지 않았습니다" else "Hardship exit reflection is still active"
        policySaveStatus == PolicySaveStatus.Saving -> text.savingChanges
        hasPolicyChanges -> text.unsavedChanges
        else -> text.policyUpToDate
    }
    val canSave = adminPin.isNotBlank() &&
        hasPolicyChanges &&
        !hasBudgetOverflow &&
        policySaveStatus != PolicySaveStatus.Saving &&
        policySaveStatus != PolicySaveStatus.BudgetExceeded &&
        policySaveStatus != PolicySaveStatus.PolicyConflict &&
        policySaveStatus != PolicySaveStatus.HardshipLocked &&
        policySaveStatus != PolicySaveStatus.HardshipReflectionRequired &&
        policySaveStatus != PolicySaveStatus.HardshipReflectionWaiting

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shadowElevation = 3.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle(text.savePolicy, Modifier.weight(1f))
                    StatusBadge(label = statusLabel, status = status)
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                    ),
                ) {
                    Text(
                        text = text.saveInstructions,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                policyAdvisoryMessage(budgetValidation, text)?.let { message ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.34f),
                        ),
                    ) {
                        Text(
                            message,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
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
                            onResetParentManagementAction()
                            onDismiss()
                        },
                        enabled = hasPolicyChanges && policySaveStatus != PolicySaveStatus.Saving,
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
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                ) {
                    Text(
                        if (policySaveStatus == PolicySaveStatus.Saving) text.savingChanges else text.savePolicy,
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
        SafeModePinStatus.TooShort,
        SafeModePinStatus.InvalidAdminPin,
        SafeModePinStatus.HardshipLocked -> LimitStatus.Exceeded
        SafeModePinStatus.Idle -> LimitStatus.Warning
    }
    val statusLabel = when (safeModePinStatus) {
        SafeModePinStatus.Accepted -> acceptedLabel
        SafeModePinStatus.TooShort -> text.pinTooShort
        SafeModePinStatus.InvalidAdminPin -> text.invalidAdminPin
        SafeModePinStatus.HardshipLocked -> if (text.appLanguage == AppLanguage.Korean) {
            "고행 3단계 적용 중 · 다음 날 해제 가능"
        } else {
            "Hardship level 3 active · available next day"
        }
        SafeModePinStatus.Idle -> text.adminPin
    }
    val canConfirm = adminPin.isNotBlank()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shadowElevation = 3.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
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
    if (budgetValidation.duplicateGroupPackageNames.isNotEmpty()) {
        return if (text.appLanguage == AppLanguage.Korean) {
            "같은 앱이 여러 앱 그룹에 포함되어 있습니다. 중복 앱 ${budgetValidation.duplicateGroupPackageNames.size}개를 한 그룹에만 남겨 주세요."
        } else {
            "${budgetValidation.duplicateGroupPackageNames.size} apps belong to multiple groups. Keep each app in one group."
        }
    }
    if (budgetValidation.overlappingSchedulePairs.isNotEmpty()) {
        val pairs = budgetValidation.overlappingSchedulePairs
            .take(2)
            .joinToString(", ") { (first, second) -> "$first ↔ $second" }
        return if (text.appLanguage == AppLanguage.Korean) {
            "스케줄 시간이 겹칩니다: $pairs. 겹치지 않도록 수정해야 저장할 수 있습니다."
        } else {
            "Schedule times overlap: $pairs. Adjust them before saving."
        }
    }
    if (budgetValidation.appGroupLimitConflictCount > 0) {
        return text.appGroupLimitConflict(budgetValidation.appGroupLimitConflictCount)
    }
    val overflowingDays = budgetValidation.overflowingDayIndexes
        .joinToString(", ") { index -> text.dayLabels.getOrElse(index) { "" } }
    return "${text.policyBudgetExceeded}: $overflowingDays - ${text.appLimits} ${formatLimitMinutesLabel(budgetValidation.appLimitTotalMinutes)}, ${text.appGroups} ${formatLimitMinutesLabel(budgetValidation.groupBudgetTotalMinutes)}"
}

private fun policyAdvisoryMessage(
    validation: PolicyBudgetValidation,
    text: AppStrings,
): String? {
    val affectedCount = (
        validation.exemptAppLimitPackages + validation.exemptGroupPackages
        ).size
    if (affectedCount == 0) return null
    return if (text.appLanguage == AppLanguage.Korean) {
        "제한 없음 앱 ${affectedCount}개에는 설정된 앱별 또는 그룹 제한이 적용되지 않습니다. 의도한 설정인지 확인한 후 저장해 주세요."
    } else {
        "$affectedCount apps excluded from all restrictions also have app or group limits. Those limits will not apply."
    }
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
                text.appTitle,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text.appSubtitle,
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
private fun PendingPolicySaveBanner(
    text: AppStrings,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = AppOver.copy(alpha = 0.95f),
        tonalElevation = 2.dp,
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, AppOver.copy(alpha = 0.70f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = text.unsavedChanges,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    text = text.pendingChangesHint,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.92f),
                )
            }
            Button(
                onClick = onSaveClick,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = AppOver,
                ),
            ) {
                Text(text.saveNow, maxLines = 1)
            }
        }
    }
}

@Composable
private fun BottomTabBar(
    selectedTab: ScreenTab,
    layoutMode: ScreenLayoutMode,
    text: AppStrings,
    onTabSelected: (ScreenTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(ScreenRestTheme.radii.card),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.99f),
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            layoutMode.tabs().forEach { tab ->
                BottomTabItem(
                    tab = tab,
                    label = text.tabLabel(tab),
                    selected = selectedTab == tab,
                    onClick = { onTabSelected(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
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
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(ScreenRestTheme.radii.control),
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (selected) ScreenRestPalette.CobaltSoft else Color.Transparent,
            ) {
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(30.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BottomTabIcon(tab = tab, color = contentColor)
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
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
    val iconRes = when (tab) {
        ScreenTab.Today -> R.drawable.ic_nav_today
        ScreenTab.Rules -> R.drawable.ic_nav_rules
        ScreenTab.Stats -> R.drawable.ic_nav_statistics
        ScreenTab.Family -> R.drawable.ic_nav_family
        ScreenTab.More -> R.drawable.ic_nav_more
        ScreenTab.Time -> R.drawable.ic_family_clock
        ScreenTab.Blocking -> R.drawable.ic_family_block
        ScreenTab.Safety -> R.drawable.ic_more_protection
        ScreenTab.Settings -> R.drawable.ic_more_account
    }
    Icon(
        painter = painterResource(iconRes),
        contentDescription = null,
        modifier = Modifier.size(22.dp),
        tint = color,
    )
}

private fun AppStrings.tabLabel(tab: ScreenTab): String {
    return when (tab) {
        ScreenTab.Today -> if (appLanguage == AppLanguage.Korean) "오늘" else "Today"
        ScreenTab.Rules -> if (appLanguage == AppLanguage.Korean) "규칙" else "Rules"
        ScreenTab.Stats -> stats
        ScreenTab.Family -> if (appLanguage == AppLanguage.Korean) "가족" else "Family"
        ScreenTab.More -> if (appLanguage == AppLanguage.Korean) "더보기" else "More"
        ScreenTab.Time -> time
        ScreenTab.Blocking -> blocking
        ScreenTab.Safety -> safety
        ScreenTab.Settings -> settings
    }
}

@Composable
fun ChoiceButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.30f) else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RulesContent(
    uiState: SafeModeUiState,
    parentAccountAuthState: ParentAccountAuthState,
    text: AppStrings,
    onOpenFamily: () -> Unit,
    editorContent: @Composable ColumnScope.() -> Unit,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    val profileName = screenProfileName(
        parentState = uiState.parentManagementState,
        authState = parentAccountAuthState,
    )
    Column(verticalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.lg)) {
        ScreenRestPageHeader(
            title = if (korean) "사용 규칙" else "Usage rules",
            trailing = {
                if (profileName.isNotBlank()) {
                    CompactProfilePill(
                        profileName = profileName,
                        onClick = onOpenFamily,
                    )
                }
            },
        )
        RulesAppliedResultCard(
            uiState = uiState,
            text = text,
        )
        if (uiState.policyDraftHasChanges) {
            ScreenRestCard(tone = ScreenRestTone.Blocked) {
                ScreenRestSectionHeader(
                    title = if (korean) "저장 전 변경사항" else "Unsaved changes",
                    supportingText = if (korean) {
                        "아래 저장 버튼을 눌러야 변경한 규칙이 적용됩니다"
                    } else {
                        "Use the save button below to apply these rule changes"
                    },
                    action = {
                        ScreenRestStatusPill(
                            label = if (korean) "저장 필요" else "Save required",
                            tone = ScreenRestTone.Blocked,
                        )
                    },
                )
            }
        }
        editorContent()
        Text(
            text = if (korean) {
                "스케줄은 선택 사항이며, 시간 제한은 함께 적용됩니다."
            } else {
                "Schedules are optional; active time limits work together."
            },
            modifier = Modifier.padding(horizontal = ScreenRestTheme.spacing.xs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CompactProfilePill(
    profileName: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(ScreenRestTheme.radii.button),
        color = ScreenRestPalette.CobaltSoft,
        border = BorderStroke(1.dp, ScreenRestPalette.Cobalt.copy(alpha = 0.18f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.xs),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_more_account),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = ScreenRestPalette.Cobalt,
            )
            Text(
                text = profileName,
                style = MaterialTheme.typography.labelLarge,
                color = ScreenRestPalette.Navy,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RulesAppliedResultCard(
    uiState: SafeModeUiState,
    text: AppStrings,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    val summary = uiState.policySummary
    val currentMode = when {
        uiState.safeModeEnabled -> if (korean) "보호 일시 중지" else "Protection paused"
        !uiState.policyEnforcementEnabled -> if (korean) "규칙 적용 꺼짐" else "Rules are off"
        summary.activeScheduleSummary != null -> summary.activeScheduleSummary.name
            .ifBlank { if (korean) "스케줄 적용" else "Schedule active" }
        summary.allowOnlyModeEnabled -> if (korean) "허용앱만" else "Allow-only"
        else -> if (korean) "일반 사용" else "Normal use"
    }
    val activeTimeLimitCount =
        (if (summary.dailyPolicyEnabled && summary.totalLimitEnabled) 1 else 0) +
            summary.groupSummaries.count { it.limitConfigured && it.activeToday } +
            summary.appLimitSummaries.count { it.activeToday && !it.excludedFromRestrictions }
    val earliest = if (!uiState.safeModeEnabled && uiState.policyEnforcementEnabled) {
        summary.effectiveAppSummaries
            .asSequence()
            .filter { it.remainingMinutes != null && it.status != LimitStatus.Exceeded }
            .minByOrNull { it.remainingMinutes ?: Int.MAX_VALUE }
    } else {
        null
    }
    val tone = when {
        uiState.safeModeEnabled || !uiState.policyEnforcementEnabled -> ScreenRestTone.Warning
        summary.activeScheduleSummary != null -> ScreenRestTone.Schedule
        summary.allowOnlyModeEnabled -> ScreenRestTone.Success
        summary.exceededCount > 0 -> ScreenRestTone.Blocked
        else -> ScreenRestTone.Success
    }
    ScreenRestCard(tone = tone) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.md),
        ) {
            MoreMenuIcon(R.drawable.ic_family_clock, tone)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.xxs),
            ) {
                Text(
                    text = if (korean) "현재 적용 결과" else "Current result",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = currentMode,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = tone.contentColor(),
                )
                Text(
                    text = when {
                        (uiState.safeModeEnabled || !uiState.policyEnforcementEnabled) && activeTimeLimitCount > 0 ->
                            if (korean) "시간 제한 ${activeTimeLimitCount}개 설정됨 · 현재 미적용" else "$activeTimeLimitCount time limits configured · currently inactive"
                        activeTimeLimitCount > 0 ->
                            if (korean) "시간 제한 ${activeTimeLimitCount}개 적용 중" else "$activeTimeLimitCount time limits active"
                        else -> if (korean) "적용 중인 시간 제한 없음" else "No active time limits"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        earliest?.let { app ->
            Surface(
                shape = RoundedCornerShape(ScreenRestTheme.radii.button),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.sm),
                ) {
                    AppIcon(
                        packageName = app.packageName,
                        contentDescription = app.appName,
                        size = 30.dp,
                    )
                    Text(
                        text = if (korean) {
                            "가장 먼저 끝나는 제한 · ${app.appName} ${formatLimitMinutesLabel(app.remainingMinutes ?: 0)} 남음"
                        } else {
                            "First limit · ${app.appName} ${formatLimitMinutesLabel(app.remainingMinutes ?: 0)} left"
                        },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun OverviewContent(
    uiState: SafeModeUiState,
    parentAccountAuthState: ParentAccountAuthState,
    permissionSetupRequired: Boolean,
    text: AppStrings,
    isExpanded: Boolean,
    onRefreshUsageStats: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenFamily: () -> Unit,
    onOpenStatistics: () -> Unit,
) {
    val profileName = screenProfileName(
        parentState = uiState.parentManagementState,
        authState = parentAccountAuthState,
    )
    val presentation = todayStatusPresentation(
        uiState = uiState,
        permissionSetupRequired = permissionSetupRequired,
        text = text,
    )
    Column(verticalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.lg)) {
        ScreenRestBrandHeader(
            profileName = profileName,
            text = text,
            onProfileClick = onOpenFamily,
        )
        Column(verticalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.xs)) {
            Text(
                text = presentation.title,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = presentation.tone.contentColor(),
            )
            Text(
                text = presentation.subtitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AdaptiveTwoPane(
            isExpanded = isExpanded,
            leftContent = {
                TodayUsageSummaryCard(
                    uiState = uiState,
                    text = text,
                    onRefreshUsageStats = onRefreshUsageStats,
                )
                TodayActiveRulesCard(
                    summary = uiState.policySummary,
                    rulesActive = !permissionSetupRequired &&
                        !uiState.safeModeEnabled &&
                        uiState.policyEnforcementEnabled,
                    text = text,
                    onOpenRules = onOpenRules,
                )
                TodayFamilyRequestCard(
                    parentState = uiState.parentManagementState,
                    text = text,
                    onOpenFamily = onOpenFamily,
                )
            },
            rightContent = {
                TodayTopAppsCard(
                    hasUsageAccess = uiState.hasUsageAccess,
                    usageAccessChecking = uiState.usageAccessChecking,
                    lastUpdatedAtMillis = uiState.usageLastUpdatedAtMillis,
                    todayUsage = uiState.todayUsage,
                    policySummary = uiState.policySummary,
                    text = text,
                    onRefreshUsageStats = onRefreshUsageStats,
                    onOpenStatistics = onOpenStatistics,
                )
            },
        )
    }
}

private data class TodayStatusPresentation(
    val title: String,
    val subtitle: String,
    val tone: ScreenRestTone,
)

private fun todayStatusPresentation(
    uiState: SafeModeUiState,
    permissionSetupRequired: Boolean,
    text: AppStrings,
): TodayStatusPresentation {
    val korean = text.appLanguage == AppLanguage.Korean
    val summary = uiState.policySummary
    return when {
        permissionSetupRequired -> TodayStatusPresentation(
            title = if (korean) "보호 상태 확인 필요" else "Protection needs attention",
            subtitle = if (korean) "권한 또는 차단 준비 상태를 확인하세요" else "Review permissions or blocking readiness",
            tone = ScreenRestTone.Blocked,
        )
        uiState.safeModeEnabled -> TodayStatusPresentation(
            title = if (korean) "보호 일시 중지" else "Protection paused",
            subtitle = if (korean) "규칙은 보존되지만 현재 차단하지 않습니다" else "Rules are preserved but blocking is paused",
            tone = ScreenRestTone.Warning,
        )
        !uiState.policyEnforcementEnabled -> TodayStatusPresentation(
            title = if (korean) "규칙 적용 꺼짐" else "Rules are off",
            subtitle = if (korean) "설정은 보존되어 있으며 현재 적용되지 않습니다" else "Settings are preserved but not currently applied",
            tone = ScreenRestTone.Warning,
        )
        summary.totalStatus == LimitStatus.Exceeded -> TodayStatusPresentation(
            title = if (korean) "오늘 사용 시간이 끝났어요" else "Today's time is up",
            subtitle = if (korean) "추가 허용 전까지 시간 제한 앱이 차단됩니다" else "Time-limited apps stay blocked until more time is allowed",
            tone = ScreenRestTone.Blocked,
        )
        summary.activeScheduleSummary != null -> TodayStatusPresentation(
            title = if (korean) "${summary.activeScheduleSummary.name} 적용 중" else "${summary.activeScheduleSummary.name} is active",
            subtitle = if (korean) "허용된 앱만 사용할 수 있습니다" else "Only allowed apps can be used",
            tone = ScreenRestTone.Schedule,
        )
        summary.allowOnlyModeEnabled -> TodayStatusPresentation(
            title = if (korean) "허용된 앱만 사용 중" else "Allow-only mode",
            subtitle = if (korean) "허용 앱 ${summary.allowOnlyAllowedAppCount}개를 사용할 수 있습니다" else "${summary.allowOnlyAllowedAppCount} apps are available",
            tone = ScreenRestTone.Success,
        )
        summary.warningCount > 0 || summary.totalStatus == LimitStatus.Warning -> TodayStatusPresentation(
            title = if (korean) "사용 시간이 얼마 남지 않았어요" else "Time is running low",
            subtitle = if (korean) "가장 먼저 끝나는 제한을 확인하세요" else "Review the limit that will end first",
            tone = ScreenRestTone.Warning,
        )
        else -> TodayStatusPresentation(
            title = if (korean) "현재 자유 시간" else "Free time now",
            subtitle = if (korean) "적용 중인 차단 없음" else "No active block",
            tone = ScreenRestTone.Primary,
        )
    }
}

private fun ScreenRestTone.contentColor(): Color {
    return when (this) {
        ScreenRestTone.Success -> ScreenRestPalette.Teal
        ScreenRestTone.Warning -> ScreenRestPalette.Amber
        ScreenRestTone.Blocked -> ScreenRestPalette.Coral
        ScreenRestTone.Schedule -> ScreenRestPalette.Indigo
        ScreenRestTone.Primary -> ScreenRestPalette.Cobalt
        ScreenRestTone.Neutral -> ScreenRestPalette.Navy
    }
}

private fun screenProfileName(
    parentState: ParentManagementState,
    authState: ParentAccountAuthState,
): String {
    return parentState.localProfileName
        .ifBlank { authState.displayName }
        .ifBlank { parentState.childDeviceName }
        .trim()
}

@Composable
private fun ScreenRestBrandHeader(
    profileName: String,
    text: AppStrings,
    onProfileClick: () -> Unit,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = ScreenRestTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.sm),
        ) {
            Image(
                painter = painterResource(R.mipmap.app_icon_round),
                contentDescription = if (korean) "폰 쉼 앱 아이콘" else "ScreenRest app icon",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape),
            )
            Text(
                text = if (korean) "폰 쉼" else "ScreenRest",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = ScreenRestPalette.Navy,
            )
        }
        if (profileName.isNotBlank()) {
            Surface(
                onClick = onProfileClick,
                shape = RoundedCornerShape(ScreenRestTheme.radii.button),
                color = ScreenRestPalette.CobaltSoft,
                border = BorderStroke(1.dp, ScreenRestPalette.Cobalt.copy(alpha = 0.18f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.xs),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_more_account),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = ScreenRestPalette.Cobalt,
                    )
                    Text(
                        text = profileName,
                        style = MaterialTheme.typography.labelLarge,
                        color = ScreenRestPalette.Navy,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_right),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayUsageSummaryCard(
    uiState: SafeModeUiState,
    text: AppStrings,
    onRefreshUsageStats: () -> Unit,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    val summary = uiState.policySummary
    val availableMinutes = (summary.totalLimitMinutes + summary.totalExtraMinutes).coerceAtLeast(0)
    val remainingMinutes = (availableMinutes - summary.totalUsedMinutes).coerceAtLeast(0)
    val progress = if (summary.totalLimitEnabled && availableMinutes > 0) {
        summary.totalUsedMinutes.toFloat().div(availableMinutes).coerceIn(0f, 1f)
    } else {
        0f
    }
    ScreenRestCard(
        tone = when (summary.totalStatus) {
            LimitStatus.Normal -> ScreenRestTone.Success
            LimitStatus.Warning -> ScreenRestTone.Warning
            LimitStatus.Exceeded -> ScreenRestTone.Blocked
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.md),
        ) {
            MoreMenuIcon(R.drawable.ic_family_clock, ScreenRestTone.Success)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (summary.totalLimitEnabled) {
                        if (korean) {
                            "${formatLimitMinutesLabel(summary.actualTotalUsedMinutes)} 사용 · ${formatLimitMinutesLabel(remainingMinutes)} 남음"
                        } else {
                            "${formatLimitMinutesLabel(summary.actualTotalUsedMinutes)} used · ${formatLimitMinutesLabel(remainingMinutes)} left"
                        }
                    } else {
                        if (korean) "${formatLimitMinutesLabel(summary.actualTotalUsedMinutes)} 사용" else "${formatLimitMinutesLabel(summary.actualTotalUsedMinutes)} used"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (summary.totalLimitEnabled) {
                        if (korean) "오늘 ${formatLimitMinutesLabel(availableMinutes)}" else "Today ${formatLimitMinutesLabel(availableMinutes)}"
                    } else {
                        text.noLimit
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRefreshUsageStats, enabled = uiState.hasUsageAccess) {
                Icon(
                    painter = painterResource(R.drawable.ic_family_sync),
                    contentDescription = if (korean) "사용량 새로고침" else "Refresh usage",
                    tint = ScreenRestPalette.Teal,
                )
            }
        }
        if (summary.totalLimitEnabled) {
            FamilyUsageProgress(
                progress = progress,
                tone = when (summary.totalStatus) {
                    LimitStatus.Normal -> ScreenRestTone.Success
                    LimitStatus.Warning -> ScreenRestTone.Warning
                    LimitStatus.Exceeded -> ScreenRestTone.Blocked
                },
            )
        }
        if (summary.actualTotalUsedMinutes != summary.totalUsedMinutes) {
            Text(
                text = if (korean) {
                    "제한에 계산된 사용량 ${formatLimitMinutesLabel(summary.totalUsedMinutes)}"
                } else {
                    "${formatLimitMinutesLabel(summary.totalUsedMinutes)} counted toward limits"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = if (uiState.usageAccessChecking) text.updating else usageLastUpdatedLabel(uiState.usageLastUpdatedAtMillis, text),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private data class TodayRuleItem(
    val title: String,
    val value: String,
    val iconRes: Int,
    val tone: ScreenRestTone,
)

private fun todayRuleItems(summary: PolicySummary, text: AppStrings): List<TodayRuleItem> {
    val korean = text.appLanguage == AppLanguage.Korean
    return buildList {
        if (summary.dailyPolicyEnabled && summary.totalLimitEnabled) {
            add(
                TodayRuleItem(
                    title = if (korean) "요일별 제한" else "Daily limit",
                    value = formatLimitMinutesLabel((summary.totalLimitMinutes + summary.totalExtraMinutes).coerceAtLeast(0)),
                    iconRes = R.drawable.ic_nav_today,
                    tone = ScreenRestTone.Primary,
                ),
            )
        }
        val activeGroups = summary.groupSummaries.count { it.limitConfigured && it.activeToday }
        if (activeGroups > 0) {
            add(
                TodayRuleItem(
                    title = if (korean) "앱 그룹" else "App groups",
                    value = if (korean) "${activeGroups}개 적용" else "$activeGroups active",
                    iconRes = R.drawable.ic_nav_rules,
                    tone = ScreenRestTone.Warning,
                ),
            )
        }
        val activeAppLimits = summary.appLimitSummaries.count { it.activeToday && !it.excludedFromRestrictions }
        if (activeAppLimits > 0) {
            add(
                TodayRuleItem(
                    title = if (korean) "앱별 제한" else "App limits",
                    value = if (korean) "${activeAppLimits}개 적용" else "$activeAppLimits active",
                    iconRes = R.drawable.ic_family_device,
                    tone = ScreenRestTone.Primary,
                ),
            )
        }
        summary.activeScheduleSummary?.let { schedule ->
            add(
                TodayRuleItem(
                    title = schedule.name.ifBlank { if (korean) "스케줄" else "Schedule" },
                    value = if (korean) "허용 앱 ${schedule.allowedAppCount}개" else "${schedule.allowedAppCount} allowed apps",
                    iconRes = R.drawable.ic_family_clock,
                    tone = ScreenRestTone.Schedule,
                ),
            )
        }
        if (summary.activeScheduleSummary == null && summary.allowOnlyModeEnabled) {
            add(
                TodayRuleItem(
                    title = if (korean) "허용앱만" else "Allow-only",
                    value = if (korean) "허용 앱 ${summary.allowOnlyAllowedAppCount}개" else "${summary.allowOnlyAllowedAppCount} allowed apps",
                    iconRes = R.drawable.ic_more_protection,
                    tone = ScreenRestTone.Success,
                ),
            )
        }
    }
}

@Composable
private fun TodayActiveRulesCard(
    summary: PolicySummary,
    rulesActive: Boolean,
    text: AppStrings,
    onOpenRules: () -> Unit,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    val items = if (rulesActive) todayRuleItems(summary, text) else emptyList()
    Column(verticalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.sm)) {
        FamilySectionTitle(if (korean) "현재 적용 중" else "Active now")
        ScreenRestCard(contentPadding = PaddingValues(vertical = ScreenRestTheme.spacing.xs)) {
            if (items.isEmpty()) {
                ScreenRestListRow(
                    title = if (rulesActive) {
                        if (korean) "적용 중인 규칙 없음" else "No active rules"
                    } else {
                        if (korean) "현재 규칙이 적용되지 않음" else "Rules are not currently applied"
                    },
                    supportingText = if (rulesActive) {
                        if (korean) "필요할 때 규칙을 설정할 수 있습니다" else "Set a rule whenever you need one"
                    } else {
                        if (korean) "보호 상태를 확인하세요" else "Review protection status"
                    },
                    onClick = onOpenRules,
                    leading = { MoreMenuIcon(R.drawable.ic_nav_rules, ScreenRestTone.Neutral) },
                    trailing = { MoreChevron() },
                )
            } else {
                items.forEachIndexed { index, item ->
                    ScreenRestListRow(
                        title = item.title,
                        onClick = onOpenRules,
                        leading = { MoreMenuIcon(item.iconRes, item.tone) },
                        trailing = {
                            Text(
                                text = item.value,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                            MoreChevron()
                        },
                    )
                    if (index != items.lastIndex) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(ScreenRestTheme.colors.divider),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayFamilyRequestCard(
    parentState: ParentManagementState,
    text: AppStrings,
    onOpenFamily: () -> Unit,
) {
    val connected = parentState.paired || parentState.linkedChildDevices.isNotEmpty() || parentState.linkedParentDevices.isNotEmpty()
    if (!connected) return
    val korean = text.appLanguage == AppLanguage.Korean
    val request = when (parentState.deviceRole) {
        ParentDeviceRole.Parent -> parentState.remoteUnlockRequests
            .filter { it.status == RemoteUnlockRequestStatus.Pending }
            .maxByOrNull { it.createdAtMillis }
        ParentDeviceRole.Child -> parentState.remoteUnlockRequests.maxByOrNull { it.createdAtMillis }
    } ?: return
    val title = when (parentState.deviceRole) {
        ParentDeviceRole.Parent -> if (korean) {
            "${request.childDeviceName.ifBlank { request.targetAppName }}의 요청"
        } else {
            "Request from ${request.childDeviceName.ifBlank { request.targetAppName }}"
        }
        ParentDeviceRole.Child -> if (korean) "보낸 요청 ${familyRequestStatusLabel(request.status, true)}" else "Sent request ${familyRequestStatusLabel(request.status, false)}"
    }
    val detail = if (korean) {
        "${request.targetAppName.ifBlank { request.remoteRequestTitle(text) }} · ${formatLimitMinutesLabel(request.requestedMinutes)} 요청"
    } else {
        "${request.targetAppName.ifBlank { request.remoteRequestTitle(text) }} · ${formatLimitMinutesLabel(request.requestedMinutes)} requested"
    }
    ScreenRestCard(tone = ScreenRestTone.Warning) {
        ScreenRestListRow(
            title = title,
            supportingText = detail,
            onClick = onOpenFamily,
            leading = { MoreMenuIcon(R.drawable.ic_family_clock, ScreenRestTone.Warning) },
            trailing = {
                Text(
                    text = if (korean) "요청 보기" else "View",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = ScreenRestPalette.Amber,
                )
                MoreChevron()
            },
        )
    }
}

@Composable
private fun TodayTopAppsCard(
    hasUsageAccess: Boolean,
    usageAccessChecking: Boolean,
    lastUpdatedAtMillis: Long,
    todayUsage: List<AppUsageInfo>,
    policySummary: PolicySummary,
    text: AppStrings,
    onRefreshUsageStats: () -> Unit,
    onOpenStatistics: () -> Unit,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    Column(verticalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FamilySectionTitle(if (korean) "오늘 많이 사용한 앱" else "Most used today")
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRefreshUsageStats, enabled = hasUsageAccess) {
                Icon(
                    painter = painterResource(R.drawable.ic_family_sync),
                    contentDescription = if (korean) "새로고침" else "Refresh",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        ScreenRestCard(contentPadding = PaddingValues(horizontal = ScreenRestTheme.spacing.md, vertical = ScreenRestTheme.spacing.xs)) {
            when {
                usageAccessChecking && hasUsageAccess -> Text(
                    text = text.updating,
                    modifier = Modifier.padding(vertical = ScreenRestTheme.spacing.lg),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                !hasUsageAccess -> Text(
                    text = text.usageAccessRequired,
                    modifier = Modifier.padding(vertical = ScreenRestTheme.spacing.md),
                    style = MaterialTheme.typography.bodyMedium,
                )
                todayUsage.isEmpty() -> Text(
                    text = text.noUsageRecorded,
                    modifier = Modifier.padding(vertical = ScreenRestTheme.spacing.md),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> todayUsage.take(5).forEachIndexed { index, appUsage ->
                    ScreenRestListRow(
                        title = appUsage.appName,
                        supportingText = if (index == 0) {
                            if (korean) "오늘 가장 많이 사용" else "Most used today"
                        } else {
                            null
                        },
                        leading = {
                            AppIcon(
                                packageName = appUsage.packageName,
                                contentDescription = appUsage.appName,
                                size = 40.dp,
                            )
                        },
                        trailing = {
                            Text(
                                text = formatDuration(appUsage.totalTimeMillis),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = policySummary.statusForPackage(appUsage.packageName).semanticColor(),
                            )
                        },
                    )
                    if (index != todayUsage.take(5).lastIndex) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(ScreenRestTheme.colors.divider),
                        )
                    }
                }
            }
            if (todayUsage.size > 5) {
                TextButton(
                    onClick = onOpenStatistics,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = if (korean) "전체 앱은 통계에서 보기" else "View all apps in Statistics",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Start,
                    )
                    MoreChevron()
                }
            }
            Text(
                text = if (usageAccessChecking) text.updating else usageLastUpdatedLabel(lastUpdatedAtMillis, text),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun StatisticsContent(
    uiState: SafeModeUiState,
    parentAccountAuthState: ParentAccountAuthState,
    text: AppStrings,
    isExpanded: Boolean,
    onOpenFamily: () -> Unit,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    var selectedRange by rememberSaveable { mutableStateOf(StatisticsDisplayRange.SevenDays) }
    val dayCount = selectedRange.dayCount
    val selectedDailyUsage = uiState.usageStatistics.dailyUsage.takeLast(dayCount)
    val selectedTopApps = when (selectedRange) {
        StatisticsDisplayRange.SevenDays -> uiState.usageStatistics.topApps.sevenDays
        StatisticsDisplayRange.ThirtyDays -> uiState.usageStatistics.topApps.thirtyDays
    }
    val profileName = screenProfileName(
        parentState = uiState.parentManagementState,
        authState = parentAccountAuthState,
    )

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = if (isExpanded) Modifier.fillMaxWidth(0.82f) else Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.lg),
        ) {
            ScreenRestPageHeader(
                title = if (korean) "사용 흐름" else "Usage trends",
                trailing = {
                    if (profileName.isNotBlank()) {
                        CompactProfilePill(
                            profileName = profileName,
                            onClick = onOpenFamily,
                        )
                    }
                },
            )
            StatisticsRangeSelector(
                selectedRange = selectedRange,
                onRangeSelected = { selectedRange = it },
                korean = korean,
            )
            StatisticsFlowCard(
                dailyUsage = selectedDailyUsage,
                previousDailyUsage = uiState.usageStatistics.dailyUsage
                    .dropLast(dayCount)
                    .takeLast(dayCount),
                selectedRange = selectedRange,
                text = text,
            )
            StatisticsTopAppsCard(
                topApps = selectedTopApps,
                policySummary = uiState.policySummary,
                text = text,
            )
            GroupStatsCard(
                groupSummaries = uiState.policySummary.groupSummaries,
                text = text,
            )
            Text(
                text = if (uiState.statisticsRefreshing) {
                    text.updating
                } else {
                    usageLastUpdatedLabel(uiState.statisticsLastUpdatedAtMillis, text)
                },
                modifier = Modifier.padding(horizontal = ScreenRestTheme.spacing.xs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private enum class StatisticsDisplayRange(val dayCount: Int) {
    SevenDays(7),
    ThirtyDays(30),
}

@Composable
private fun StatisticsRangeSelector(
    selectedRange: StatisticsDisplayRange,
    onRangeSelected: (StatisticsDisplayRange) -> Unit,
    korean: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(ScreenRestTheme.radii.button),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(modifier = Modifier.padding(4.dp)) {
            StatisticsDisplayRange.entries.forEach { range ->
                val selected = range == selectedRange
                Surface(
                    onClick = { onRangeSelected(range) },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(ScreenRestTheme.radii.button - 4.dp),
                    color = if (selected) ScreenRestPalette.Cobalt else Color.Transparent,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = when (range) {
                                StatisticsDisplayRange.SevenDays -> if (korean) "7일" else "7 days"
                                StatisticsDisplayRange.ThirtyDays -> if (korean) "30일" else "30 days"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatisticsFlowCard(
    dailyUsage: List<DailyUsageInfo>,
    previousDailyUsage: List<DailyUsageInfo>,
    selectedRange: StatisticsDisplayRange,
    text: AppStrings,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    val recordedDailyUsage = dailyUsage.filter { usage -> usage.hasRecordedData }
    val averageMillis = if (recordedDailyUsage.isNotEmpty()) {
        recordedDailyUsage.sumOf { usage -> usage.totalTimeMillis } / recordedDailyUsage.size
    } else {
        0L
    }
    val previousRecordedUsage = previousDailyUsage.filter { usage -> usage.hasRecordedData }
    val previousAverageMillis = previousRecordedUsage
        .takeIf { values -> values.isNotEmpty() }
        ?.let { values -> values.sumOf { usage -> usage.totalTimeMillis } / values.size }
    val averageDifferenceMillis = previousAverageMillis?.let { previous -> averageMillis - previous }
    val comparisonColor = when {
        averageDifferenceMillis == null -> MaterialTheme.colorScheme.onSurfaceVariant
        averageDifferenceMillis <= 0L -> ScreenRestPalette.Teal
        else -> ScreenRestPalette.Coral
    }
    val comparisonLabel = when {
        averageDifferenceMillis == null -> if (korean) {
            "비교할 이전 기록이 아직 없어요"
        } else {
            "No earlier period to compare yet"
        }
        averageDifferenceMillis == 0L -> if (korean) "이전 기간과 같아요" else "Same as the previous period"
        averageDifferenceMillis < 0L -> if (korean) {
            "이전 기간보다 ${formatDuration(abs(averageDifferenceMillis))} 줄었어요"
        } else {
            "${formatDuration(abs(averageDifferenceMillis))} less than the previous period"
        }
        else -> if (korean) {
            "이전 기간보다 ${formatDuration(averageDifferenceMillis)} 늘었어요"
        } else {
            "${formatDuration(averageDifferenceMillis)} more than the previous period"
        }
    }

    ScreenRestCard(contentPadding = PaddingValues(ScreenRestTheme.spacing.lg)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.xs),
        ) {
            Text(
                text = when (selectedRange) {
                    StatisticsDisplayRange.SevenDays -> if (korean) "최근 7일 평균" else "7-day average"
                    StatisticsDisplayRange.ThirtyDays -> if (korean) "최근 30일 평균" else "30-day average"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (recordedDailyUsage.isEmpty()) "—" else formatDuration(averageMillis),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = ScreenRestPalette.Navy,
            )
            Text(
                text = comparisonLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = comparisonColor,
            )
        }
        StatisticsDailyTrendChart(
            dailyUsage = dailyUsage,
            text = text,
        )
        StatisticsGoalSummary(
            dailyUsage = dailyUsage,
            text = text,
        )
    }
}

@Composable
private fun StatisticsDailyTrendChart(
    dailyUsage: List<DailyUsageInfo>,
    text: AppStrings,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    val hasDailyUsageData = dailyUsage.any { usage -> usage.hasRecordedData }
    val maxUsageMillis = dailyUsage.maxOfOrNull { usage ->
        maxOf(usage.totalTimeMillis, (usage.dailyGoalMinutes ?: 0) * 60_000L)
    }?.coerceAtLeast(1L) ?: 1L
    val listState = rememberLazyListState()

    LaunchedEffect(dailyUsage.size, hasDailyUsageData) {
        if (hasDailyUsageData && dailyUsage.size > 7) {
            listState.scrollToItem((dailyUsage.lastIndex - 6).coerceAtLeast(0))
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (korean) "일별 사용" else "Daily use",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = ScreenRestPalette.Navy,
            )
            if (dailyUsage.size > 7) {
                Text(
                    text = if (korean) "좌우로 움직여 보세요" else "Swipe to see more",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!hasDailyUsageData) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text.noStats, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
            ) {
                val spacing = 6.dp
                val itemWidth = if (dailyUsage.size <= 7) {
                    ((maxWidth - spacing * (dailyUsage.size - 1).coerceAtLeast(0)) /
                        dailyUsage.size.coerceAtLeast(1)).coerceAtLeast(38.dp)
                } else {
                    46.dp
                }
                LazyRow(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    items(dailyUsage, key = { usage -> usage.dayStartMillis }) { usage ->
                        StatisticsDailyUsageBar(
                            usage = usage,
                            maxUsageMillis = maxUsageMillis,
                            itemWidth = itemWidth,
                        )
                    }
                }
            }
        }
        StatisticsDailyTrendLegend(korean = korean)
    }
}

@Composable
private fun StatisticsDailyTrendLegend(korean: Boolean) {
    val noGoalColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatisticsDailyTrendLegendItem(
            color = ScreenRestPalette.Teal,
            label = if (korean) "목표 이내" else "Within goal",
        )
        StatisticsDailyTrendLegendItem(
            color = ScreenRestPalette.Amber,
            label = if (korean) "목표 임박" else "Near goal",
        )
        StatisticsDailyTrendLegendItem(
            color = ScreenRestPalette.Coral,
            label = if (korean) "목표 초과" else "Over goal",
        )
        StatisticsDailyTrendLegendItem(
            color = noGoalColor,
            label = if (korean) "목표·기록 없음" else "No goal/data",
        )
    }
}

@Composable
private fun StatisticsDailyTrendLegendItem(
    color: Color,
    label: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun StatisticsDailyUsageBar(
    usage: DailyUsageInfo,
    maxUsageMillis: Long,
    itemWidth: androidx.compose.ui.unit.Dp,
) {
    val fraction = if (maxUsageMillis <= 0L) 0f else {
        (usage.totalTimeMillis.toFloat() / maxUsageMillis.toFloat()).coerceIn(0f, 1f)
    }
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 450),
        label = "statisticsDailyUsageBar",
    )
    val today = isToday(usage.dayStartMillis)
    val goalMillis = usage.dailyGoalMinutes?.times(60_000L)
    val goalFraction = if (goalMillis == null || goalMillis <= 0L) null else {
        usage.totalTimeMillis.toFloat() / goalMillis.toFloat()
    }
    val noGoalColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    val barColor = when {
        !usage.hasRecordedData -> noGoalColor
        !usage.hasRecordedGoal || goalMillis == null -> noGoalColor
        usage.totalTimeMillis > goalMillis -> ScreenRestPalette.Coral
        goalFraction != null && goalFraction >= 0.85f -> ScreenRestPalette.Amber
        else -> ScreenRestPalette.Teal
    }
    val labelColor = if (today) ScreenRestPalette.Cobalt else MaterialTheme.colorScheme.onSurfaceVariant
    val barHeight = (126.dp * animatedFraction).coerceAtLeast(if (usage.hasRecordedData) 7.dp else 3.dp)

    Column(
        modifier = Modifier.width(itemWidth),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(
            text = if (usage.hasRecordedData) {
                formatDuration(usage.totalTimeMillis).replace(" ", "\n")
            } else {
                "—"
            },
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = labelColor,
            maxLines = 2,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .width(24.dp)
                .height(132.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight)
                    .clip(RoundedCornerShape(50))
                    .background(barColor),
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = formatStatsWeekdayLabel(usage.dayStartMillis),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = labelColor,
            maxLines = 1,
        )
        Text(
            text = formatStatsDateLabel(usage.dayStartMillis),
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun StatisticsGoalSummary(
    dailyUsage: List<DailyUsageInfo>,
    text: AppStrings,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    val goalDays = dailyUsage.filter { usage ->
        usage.hasRecordedData && usage.hasRecordedGoal && usage.dailyGoalMinutes != null
    }
    if (goalDays.isEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(ScreenRestTheme.radii.button))
                .background(ScreenRestPalette.CobaltSoft.copy(alpha = 0.48f))
                .padding(ScreenRestTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.sm),
        ) {
            MoreMenuIcon(R.drawable.ic_nav_statistics, ScreenRestTone.Primary)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (korean) "날짜별 목표 기록을 시작했어요" else "Daily goal tracking has started",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = ScreenRestPalette.Navy,
                )
                Text(
                    text = if (korean) {
                        "기록된 목표가 있는 날부터 달성 결과를 보여드립니다."
                    } else {
                        "Results appear only for days with a recorded goal."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        val withinGoalCount = goalDays.count { usage ->
            usage.totalTimeMillis <= (usage.dailyGoalMinutes ?: 0) * 60_000L
        }
        val exceededGoalCount = goalDays.size - withinGoalCount
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(ScreenRestTheme.radii.button))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f))
                .padding(vertical = ScreenRestTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatisticsGoalMetric(
                label = if (korean) "목표 안에서 사용" else "Within goal",
                value = if (korean) "${withinGoalCount}일" else "$withinGoalCount days",
                tone = ScreenRestTone.Success,
                iconRes = R.drawable.ic_family_clock,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(54.dp)
                    .background(ScreenRestTheme.colors.divider),
            )
            StatisticsGoalMetric(
                label = if (korean) "목표 초과" else "Over goal",
                value = if (korean) "${exceededGoalCount}일" else "$exceededGoalCount days",
                tone = ScreenRestTone.Blocked,
                iconRes = R.drawable.ic_family_block,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = if (korean) {
                "목표 기록 ${goalDays.size}일 기준"
            } else {
                "Based on ${goalDays.size} days with recorded goals"
            },
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun StatisticsGoalMetric(
    label: String,
    value: String,
    tone: ScreenRestTone,
    iconRes: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = ScreenRestTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.sm),
    ) {
        MoreMenuIcon(iconRes = iconRes, tone = tone)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = tone.contentColor(),
            )
        }
    }
}

@Composable
private fun StatisticsTopAppsCard(
    topApps: List<AppUsageInfo>,
    policySummary: PolicySummary,
    text: AppStrings,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    val topUsageMillis = topApps.firstOrNull()?.totalTimeMillis?.coerceAtLeast(1L) ?: 1L

    ScreenRestCard {
        ScreenRestSectionHeader(
            title = if (korean) "많이 사용한 앱" else "Most used apps",
            supportingText = if (korean) "아래 목록을 스크롤해 더 볼 수 있습니다." else "Scroll the list to see more.",
        )
        if (topApps.isEmpty()) {
            Text(text.noStats, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            ContainedLazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 430.dp),
                resetKey = topApps.map { usage -> usage.packageName },
            ) {
                items(topApps, key = { appUsage -> appUsage.packageName }) { appUsage ->
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
    val korean = text.appLanguage == AppLanguage.Korean
    var expanded by rememberSaveable { mutableStateOf(false) }
    ScreenRestCard {
        ScreenRestSectionHeader(
            title = text.groupStats,
            supportingText = if (groupSummaries.isEmpty()) {
                text.noStats
            } else if (korean) {
                "${groupSummaries.size}개 그룹"
            } else {
                "${groupSummaries.size} groups"
            },
            action = if (groupSummaries.isEmpty()) null else {
                {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(
                            text = if (expanded) {
                                if (korean) "접기" else "Collapse"
                            } else {
                                if (korean) "보기" else "View"
                            },
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            },
        )
        if (groupSummaries.isNotEmpty() && expanded) {
            groupSummaries.forEach { groupSummary ->
                ProgressLine(
                    label = groupSummary.groupName.ifBlank { text.groupName },
                    usedMinutes = groupSummary.usedMinutes,
                    limitMinutes = groupSummary.limitMinutes,
                    status = groupSummary.status,
                    text = text,
                    extraMinutes = groupSummary.extraMinutes,
                    limitEnabled = groupSummary.limitEnabled,
                    limitTextOverride = if (groupSummary.activeToday) null else text.todayNotAppliedLabel(),
                )
                val topGroupApps = groupSummary.appUsages
                    .filter { appUsage -> appUsage.usedMinutes > 0 }
                    .sortedByDescending { appUsage -> appUsage.usedMinutes }
                    .take(3)
                if (topGroupApps.isNotEmpty()) {
                    topGroupApps.forEach { appUsage ->
                        AppRow(
                            appName = appUsage.appName,
                            packageName = appUsage.packageName,
                            supportingText = appUsage.limitMinutes?.let { limitMinutes ->
                                formatLimitWithAllowance(
                                    limitMinutes,
                                    appUsage.extraMinutes,
                                    appUsage.unlockedForToday,
                                    text,
                                )
                            }.orEmpty(),
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
    val hasTotalLimit = summary.totalLimitEnabled && !summary.totalUnlockedForToday
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
                Text(
                    if (text.appLanguage == AppLanguage.Korean) {
                        "제한 적용 사용량"
                    } else {
                        "Usage counted toward limits"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        formatLimitMinutesLabel(summary.totalUsedMinutes),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        " / ${if (summary.totalLimitEnabled) {
                            formatLimitWithAllowance(summary.totalLimitMinutes, summary.totalExtraMinutes, summary.totalUnlockedForToday, text)
                        } else {
                            text.noLimit
                        }}",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    when {
                        !summary.dailyPolicyEnabled -> text.scheduleInactiveNow
                        overMinutes > 0 -> "+${formatLimitMinutesLabel(overMinutes)} over limit"
                        else -> text.limitStatus(summary.totalStatus)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (overMinutes > 0) AppOver else summary.totalStatus.semanticColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (summary.actualTotalUsedMinutes != summary.totalUsedMinutes) {
                    val excludedMinutes =
                        (summary.actualTotalUsedMinutes - summary.totalUsedMinutes).coerceAtLeast(0)
                    Text(
                        if (text.appLanguage == AppLanguage.Korean) {
                            "오늘 전체 사용 ${formatLimitMinutesLabel(summary.actualTotalUsedMinutes)} · 제한 미적용 ${formatLimitMinutesLabel(excludedMinutes)}"
                        } else {
                            "All usage today ${formatLimitMinutesLabel(summary.actualTotalUsedMinutes)} · excluded ${formatLimitMinutesLabel(excludedMinutes)}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
    val rawProgress = if (limitMinutes <= 0) {
        if (status == LimitStatus.Exceeded) 1f else 0f
    } else {
        usedMinutes.toFloat() / limitMinutes.toFloat()
    }
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
        shape = RoundedCornerShape(999.dp),
        color = color,
        border = BorderStroke(1.dp, status.semanticColor().copy(alpha = 0.22f)),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = status.semanticColor(),
        )
    }
}

@Composable
fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.64f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.64f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
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
    limitEnabled: Boolean = true,
    limitTextOverride: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "${formatLimitMinutesLabel(usedMinutes)} / ${limitTextOverride ?: if (limitEnabled) {
                    formatLimitWithAllowance(limitMinutes, extraMinutes, unlockedForToday, text)
                } else {
                    text.noLimit
                }}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (limitEnabled) {
            ProgressOnlyBar(
                usedMinutes = usedMinutes,
                limitMinutes = limitMinutes,
                extraMinutes = extraMinutes,
                unlockedForToday = unlockedForToday,
                status = status,
            )
        }
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
        limitMinutes <= 0 -> text.zeroMinuteBlockLabel()
        extraMinutes > 0 -> "${formatLimitMinutesLabel(limitMinutes)}+${formatLimitMinutesLabel(extraMinutes)}"
        else -> formatLimitMinutesLabel(limitMinutes)
    }
}

fun formatLimitWithTemporaryAllowance(
    limitMinutes: Int,
    extraMinutes: Int,
    unlockedForToday: Boolean,
    temporaryRemainingMinutes: Int,
    text: AppStrings,
): String {
    return when {
        unlockedForToday -> text.unlockedToday
        temporaryRemainingMinutes > 0 ->
            "${formatLimitMinutesLabel(temporaryRemainingMinutes)} ${text.temporaryAllowances}"
        else -> formatLimitWithAllowance(limitMinutes, extraMinutes, false, text)
    }
}

private val GaugeTrackColor = Color(0xFFE6EAF2)

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

private data class MoreMenuItem(
    val title: String,
    val description: String,
    val iconRes: Int,
    val tone: ScreenRestTone,
    val destination: MoreDestination,
)

@Composable
private fun MoreContent(
    destination: MoreDestination,
    onDestinationChanged: (MoreDestination) -> Unit,
    layoutMode: ScreenLayoutMode,
    onLayoutModeChanged: (ScreenLayoutMode) -> Unit,
    uiState: SafeModeUiState,
    parentAccountAuthState: ParentAccountAuthState,
    safeRecoveryAdminPin: String,
    text: AppStrings,
    isExpanded: Boolean,
    onSafeModeChanged: (Boolean) -> Unit,
    onSafeModeEnableWithPin: (String) -> Unit,
    onSafeModePinStatusSeen: () -> Unit,
    onPolicyEnforcementChanged: (Boolean) -> Unit,
    onPolicyEnforcementDisableWithPin: (String) -> Unit,
    onOpenBlockScreenPreview: (BlockDecisionResult) -> Unit,
    onSafeRecoveryPinChanged: (String) -> Unit,
    onSafeRecoveryClick: () -> Unit,
    onAppLanguageChanged: (AppLanguage) -> Unit,
    onWarningNotificationsChanged: (Boolean) -> Unit,
    onLimitNotificationsChanged: (Boolean) -> Unit,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenNotificationAccessSettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onUpdateAdminPin: (String, String) -> Unit,
    onPinInputChanged: () -> Unit,
    onParentGoogleSignIn: () -> Unit,
    onDeleteAccountAndCloudData: (String) -> Unit,
    onClearEventLog: () -> Unit,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    when (destination) {
        MoreDestination.Home -> MoreHomeContent(
            uiState = uiState,
            parentAccountAuthState = parentAccountAuthState,
            text = text,
            onDestinationChanged = onDestinationChanged,
            layoutMode = layoutMode,
            onLayoutModeChanged = onLayoutModeChanged,
        )

        MoreDestination.ProtectionStatus -> Column(
            verticalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.md),
        ) {
            MoreDetailHeader(
                title = if (korean) "보호 상태" else "Protection status",
                subtitle = if (korean) "차단 준비 상태와 보호 작동을 확인합니다" else "Review protection and blocking readiness",
                onBack = { onDestinationChanged(MoreDestination.Home) },
            )
            SafetyContent(
                uiState = uiState,
                safeRecoveryAdminPin = safeRecoveryAdminPin,
                text = text,
                isExpanded = isExpanded,
                onSafeModeChanged = onSafeModeChanged,
                onSafeModeEnableWithPin = onSafeModeEnableWithPin,
                onSafeModePinStatusSeen = onSafeModePinStatusSeen,
                onPolicyEnforcementChanged = onPolicyEnforcementChanged,
                onPolicyEnforcementDisableWithPin = onPolicyEnforcementDisableWithPin,
                onOpenBlockScreenPreview = onOpenBlockScreenPreview,
                onSafeRecoveryPinChanged = onSafeRecoveryPinChanged,
                onSafeRecoveryClick = onSafeRecoveryClick,
            )
        }

        MoreDestination.AdminPin -> MoreAdminPinContent(
            uiState = uiState,
            text = text,
            onBack = { onDestinationChanged(MoreDestination.Home) },
            onUpdateAdminPin = onUpdateAdminPin,
            onPinInputChanged = onPinInputChanged,
        )

        MoreDestination.EmergencyPass -> MoreEmergencyPassContent(
            summary = uiState.policySummary,
            text = text,
            onBack = { onDestinationChanged(MoreDestination.Home) },
        )

        MoreDestination.Permissions -> Column(
            verticalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.md),
        ) {
            MoreDetailHeader(
                title = if (korean) "권한 및 작동 상태" else "Permissions and operation",
                subtitle = if (korean) "차단에 필요한 Android 권한을 확인합니다" else "Review Android permissions required for blocking",
                onBack = { onDestinationChanged(MoreDestination.Home) },
            )
            PermissionSettingsSection(
                readiness = uiState.blockingReadiness,
                text = text,
                onOpenUsageAccessSettings = onOpenUsageAccessSettings,
                onOpenOverlaySettings = onOpenOverlaySettings,
                onRequestNotificationPermission = onRequestNotificationPermission,
                onOpenNotificationAccessSettings = onOpenNotificationAccessSettings,
                onOpenExactAlarmSettings = onOpenExactAlarmSettings,
            )
        }

        MoreDestination.Notifications -> MoreNotificationContent(
            uiState = uiState,
            text = text,
            onBack = { onDestinationChanged(MoreDestination.Home) },
            onWarningNotificationsChanged = onWarningNotificationsChanged,
            onLimitNotificationsChanged = onLimitNotificationsChanged,
        )

        MoreDestination.Language -> MoreLanguageContent(
            appLanguage = uiState.appLanguage,
            text = text,
            onBack = { onDestinationChanged(MoreDestination.Home) },
            onAppLanguageChanged = onAppLanguageChanged,
        )

        MoreDestination.GoogleAccount -> MoreGoogleAccountContent(
            parentState = uiState.parentManagementState,
            authState = parentAccountAuthState,
            text = text,
            onBack = { onDestinationChanged(MoreDestination.Home) },
            onParentGoogleSignIn = onParentGoogleSignIn,
            onDeleteAccountAndCloudData = onDeleteAccountAndCloudData,
        )

        MoreDestination.Help -> MoreHelpContent(
            text = text,
            onBack = { onDestinationChanged(MoreDestination.Home) },
        )

        MoreDestination.Diagnostics -> Column(
            verticalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.md),
        ) {
            MoreDetailHeader(
                title = if (korean) "진단 정보" else "Diagnostics",
                subtitle = if (korean) "최근 앱 작동 기록을 확인합니다" else "Review recent app activity records",
                onBack = { onDestinationChanged(MoreDestination.Home) },
            )
            EventLogSection(
                eventLog = uiState.eventLog,
                text = text,
                onClearEventLog = onClearEventLog,
            )
            ScreenRestCard {
                ScreenRestListRow(
                    title = if (korean) "상세 작동 진단" else "Detailed operation diagnostics",
                    supportingText = if (korean) "감시 서비스와 차단 판단은 보호 상태에서 확인합니다" else "Monitor and blocking diagnostics are available under Protection status",
                    onClick = { onDestinationChanged(MoreDestination.ProtectionStatus) },
                    leading = {
                        MoreMenuIcon(R.drawable.ic_more_protection, ScreenRestTone.Primary)
                    },
                    trailing = { MoreChevron() },
                )
            }
        }
    }
}

@Composable
private fun ScreenLayoutModePicker(
    layoutMode: ScreenLayoutMode,
    text: AppStrings,
    onLayoutModeChanged: (ScreenLayoutMode) -> Unit,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    ScreenRestCard {
        ScreenRestSectionHeader(
            title = if (korean) "화면 구성" else "Screen layout",
            supportingText = if (korean) {
                "탭 배치만 바뀝니다. 규칙과 사용 기록은 그대로 유지됩니다."
            } else {
                "Only navigation changes. Rules and usage history stay the same."
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.xs)) {
            ChoiceButton(
                label = if (korean) "새 구성" else "New layout",
                selected = layoutMode == ScreenLayoutMode.Modern,
                onClick = { onLayoutModeChanged(ScreenLayoutMode.Modern) },
            )
            ChoiceButton(
                label = if (korean) "기존 탭 구성" else "Classic tabs",
                selected = layoutMode == ScreenLayoutMode.Classic,
                onClick = { onLayoutModeChanged(ScreenLayoutMode.Classic) },
            )
        }
    }
}

@Composable
private fun MoreHomeContent(
    uiState: SafeModeUiState,
    parentAccountAuthState: ParentAccountAuthState,
    text: AppStrings,
    onDestinationChanged: (MoreDestination) -> Unit,
    layoutMode: ScreenLayoutMode,
    onLayoutModeChanged: (ScreenLayoutMode) -> Unit,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    val protectionReady = uiState.blockingReadiness.readyForBlocking &&
        uiState.policyEnforcementEnabled &&
        !uiState.safeModeEnabled
    val protectionTitle = when {
        uiState.safeModeEnabled -> if (korean) "보호 일시 중지" else "Protection paused"
        !uiState.blockingReadiness.readyForBlocking -> if (korean) "확인 필요" else "Needs attention"
        !uiState.policyEnforcementEnabled -> if (korean) "규칙 적용 꺼짐" else "Rules are off"
        else -> if (korean) "정상 작동 중" else "Working normally"
    }
    val protectionDescription = when {
        uiState.safeModeEnabled -> if (korean) "규칙은 보존되지만 현재 차단하지 않습니다" else "Rules are preserved, but blocking is paused"
        !uiState.blockingReadiness.readyForBlocking -> if (korean) "필수 권한 또는 감시 상태를 확인해 주세요" else "Review required permissions or monitoring status"
        !uiState.policyEnforcementEnabled -> if (korean) "저장된 규칙을 적용하지 않고 있습니다" else "Saved rules are not being enforced"
        else -> if (korean) "저장된 규칙과 차단 기능이 작동하고 있습니다" else "Saved rules and blocking are active"
    }

    Column(verticalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.md)) {
        ScreenRestPageHeader(
            title = if (korean) "더보기" else "More",
            subtitle = if (korean) "보호 상태와 앱 설정을 관리합니다" else "Manage protection and app settings",
        )

        ScreenLayoutModePicker(
            layoutMode = layoutMode,
            text = text,
            onLayoutModeChanged = onLayoutModeChanged,
        )

        ScreenRestCard(tone = if (protectionReady) ScreenRestTone.Success else ScreenRestTone.Warning) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.sm),
            ) {
                MoreMenuIcon(
                    iconRes = R.drawable.ic_more_protection,
                    tone = if (protectionReady) ScreenRestTone.Success else ScreenRestTone.Warning,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = protectionTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = protectionDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ScreenRestStatusPill(
                    label = if (protectionReady) {
                        if (korean) "정상" else "Ready"
                    } else {
                        if (korean) "확인" else "Check"
                    },
                    tone = if (protectionReady) ScreenRestTone.Success else ScreenRestTone.Warning,
                )
            }
            ScreenRestPrimaryButton(
                label = if (korean) "상태 확인" else "View status",
                onClick = { onDestinationChanged(MoreDestination.ProtectionStatus) },
            )
        }

        MoreMenuGroup(
            title = if (korean) "보호 및 보안" else "Protection and security",
            items = listOf(
                MoreMenuItem(
                    title = if (korean) "관리 PIN" else "Admin PIN",
                    description = if (korean) "설정 변경과 보호 기능 확인" else "Confirm settings and protection actions",
                    iconRes = R.drawable.ic_more_pin,
                    tone = ScreenRestTone.Primary,
                    destination = MoreDestination.AdminPin,
                ),
                MoreMenuItem(
                    title = if (korean) "긴급 사용권" else "Emergency Pass",
                    description = if (korean) {
                        "Emergency Pass · ${moreEmergencyPassSummary(uiState.policySummary, true)}"
                    } else {
                        moreEmergencyPassSummary(uiState.policySummary, false)
                    },
                    iconRes = R.drawable.ic_more_emergency,
                    tone = ScreenRestTone.Warning,
                    destination = MoreDestination.EmergencyPass,
                ),
                MoreMenuItem(
                    title = if (korean) "권한 및 작동 상태" else "Permissions and operation",
                    description = if (uiState.blockingReadiness.readyForBlocking) {
                        if (korean) "필수 권한 준비됨" else "Required permissions ready"
                    } else {
                        if (korean) "확인이 필요한 항목이 있습니다" else "Some items need attention"
                    },
                    iconRes = R.drawable.ic_more_protection,
                    tone = if (uiState.blockingReadiness.readyForBlocking) ScreenRestTone.Success else ScreenRestTone.Warning,
                    destination = MoreDestination.Permissions,
                ),
            ),
            onDestinationChanged = onDestinationChanged,
        )

        MoreMenuGroup(
            title = if (korean) "앱 설정" else "App settings",
            items = listOf(
                MoreMenuItem(
                    title = if (korean) "알림" else "Notifications",
                    description = if (korean) "경고 및 사용 초과 알림" else "Warnings and limit alerts",
                    iconRes = R.drawable.ic_more_notifications,
                    tone = ScreenRestTone.Primary,
                    destination = MoreDestination.Notifications,
                ),
                MoreMenuItem(
                    title = if (korean) "언어" else "Language",
                    description = if (uiState.appLanguage == AppLanguage.Korean) "한국어" else "English",
                    iconRes = R.drawable.ic_more_language,
                    tone = ScreenRestTone.Success,
                    destination = MoreDestination.Language,
                ),
            ),
            onDestinationChanged = onDestinationChanged,
        )

        MoreMenuGroup(
            title = if (korean) "계정 및 지원" else "Account and support",
            items = listOf(
                MoreMenuItem(
                    title = if (korean) "Google 계정" else "Google account",
                    description = when {
                        parentAccountAuthState.recoverable -> parentAccountAuthState.email
                            .ifBlank { parentAccountAuthState.displayName }
                            .ifBlank { if (korean) "연결됨" else "Connected" }
                        uiState.parentManagementState.deviceRole == ParentDeviceRole.Child ->
                            if (korean) "자녀 기기 익명 계정" else "Child device anonymous account"
                        else -> if (korean) "로그인 및 계정 관리" else "Sign in and manage account"
                    },
                    iconRes = R.drawable.ic_more_account,
                    tone = ScreenRestTone.Primary,
                    destination = MoreDestination.GoogleAccount,
                ),
                MoreMenuItem(
                    title = if (korean) "도움말" else "Help",
                    description = if (korean) "규칙과 보호 기능 알아보기" else "Learn about rules and protection",
                    iconRes = R.drawable.ic_more_help,
                    tone = ScreenRestTone.Schedule,
                    destination = MoreDestination.Help,
                ),
                MoreMenuItem(
                    title = if (korean) "진단 정보" else "Diagnostics",
                    description = if (korean) "최근 작동 기록 ${uiState.eventLog.size}개" else "${uiState.eventLog.size} recent records",
                    iconRes = R.drawable.ic_more_diagnostics,
                    tone = ScreenRestTone.Neutral,
                    destination = MoreDestination.Diagnostics,
                ),
            ),
            onDestinationChanged = onDestinationChanged,
        )
    }
}

@Composable
private fun MoreMenuGroup(
    title: String,
    items: List<MoreMenuItem>,
    onDestinationChanged: (MoreDestination) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.xs)) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = ScreenRestTheme.spacing.xs),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        ScreenRestCard(contentPadding = PaddingValues(vertical = ScreenRestTheme.spacing.xxs)) {
            items.forEachIndexed { index, item ->
                ScreenRestListRow(
                    title = item.title,
                    supportingText = item.description,
                    onClick = { onDestinationChanged(item.destination) },
                    leading = { MoreMenuIcon(item.iconRes, item.tone) },
                    trailing = { MoreChevron() },
                )
                if (index != items.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 72.dp, end = ScreenRestTheme.spacing.sm)
                            .height(1.dp)
                            .background(ScreenRestTheme.colors.divider),
                    )
                }
            }
        }
    }
}

@Composable
private fun MoreMenuIcon(iconRes: Int, tone: ScreenRestTone) {
    val tint = when (tone) {
        ScreenRestTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
        ScreenRestTone.Primary -> MaterialTheme.colorScheme.primary
        ScreenRestTone.Success -> ScreenRestTheme.colors.success
        ScreenRestTone.Warning -> ScreenRestTheme.colors.warning
        ScreenRestTone.Blocked -> ScreenRestTheme.colors.blocked
        ScreenRestTone.Schedule -> ScreenRestTheme.colors.schedule
    }
    ScreenRestIconContainer(tone = tone) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(ScreenRestTheme.sizes.icon),
            tint = tint,
        )
    }
}

@Composable
private fun MoreChevron() {
    Icon(
        painter = painterResource(R.drawable.ic_chevron_right),
        contentDescription = null,
        modifier = Modifier.size(20.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun MoreDetailHeader(title: String, subtitle: String, onBack: () -> Unit) {
    ScreenRestPageHeader(
        title = title,
        subtitle = subtitle,
        leading = {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        },
    )
}

@Composable
private fun MoreAdminPinContent(
    uiState: SafeModeUiState,
    text: AppStrings,
    onBack: () -> Unit,
    onUpdateAdminPin: (String, String) -> Unit,
    onPinInputChanged: () -> Unit,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    var currentAdminPin by remember { mutableStateOf("") }
    var newAdminPin by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.md)) {
        MoreDetailHeader(
            title = if (korean) "관리 PIN" else "Admin PIN",
            subtitle = if (korean) "설정과 허용된 보호 동작을 확인합니다" else "Confirm settings and permitted protection actions",
            onBack = onBack,
        )
        ScreenRestCard {
            PinChangeFields(
                currentPin = currentAdminPin,
                newPin = newAdminPin,
                currentLabel = text.currentAdminPin,
                newLabel = text.newAdminPin,
                onCurrentChanged = {
                    currentAdminPin = it
                    onPinInputChanged()
                },
                onNewChanged = {
                    newAdminPin = it
                    onPinInputChanged()
                },
                onSave = {
                    onUpdateAdminPin(currentAdminPin, newAdminPin)
                    currentAdminPin = ""
                    newAdminPin = ""
                },
                status = uiState.pinChangeStatus,
                text = text,
            )
            Text(
                text = if (korean) {
                    "관리 PIN은 설정 변경, 부모 연결, 허용된 차단 해제와 안전 복구에 사용됩니다. 고행 3단계는 PIN만으로 종료할 수 없습니다."
                } else {
                    "The Admin PIN confirms settings, pairing, allowed unlocks, and Safe Recovery. It cannot end active hardship level 3 by itself."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MoreEmergencyPassContent(
    summary: PolicySummary,
    text: AppStrings,
    onBack: () -> Unit,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    val nextAvailableAt = summary.emergencyPassNextAvailableAtMillis
    val available = nextAvailableAt <= 0L || System.currentTimeMillis() >= nextAvailableAt
    Column(verticalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.md)) {
        MoreDetailHeader(
            title = if (korean) "긴급 사용권" else "Emergency Pass",
            subtitle = if (korean) "고행 3단계의 제한된 긴급 예외 권한" else "A limited emergency exception for hardship level 3",
            onBack = onBack,
        )
        ScreenRestCard(tone = if (available) ScreenRestTone.Success else ScreenRestTone.Warning) {
            ScreenRestSectionHeader(
                title = if (available) {
                    if (korean) "사용 가능" else "Available"
                } else {
                    if (korean) "이미 사용함" else "Already used"
                },
                supportingText = if (available) {
                    if (korean) "필요할 때 블록 화면에서 사용할 수 있습니다" else "Use it from the block screen when needed"
                } else {
                    if (korean) "다음 사용 가능 ${formatDateTime(nextAvailableAt)}" else "Available again ${formatDateTime(nextAvailableAt)}"
                },
                action = {
                    ScreenRestStatusPill(
                        label = if (available) {
                            if (korean) "1회" else "1 use"
                        } else {
                            if (korean) "대기" else "Waiting"
                        },
                        tone = if (available) ScreenRestTone.Success else ScreenRestTone.Warning,
                    )
                },
            )
        }
        ScreenRestCard {
            MoreInformationRow(
                title = if (korean) "공유 주기" else "Shared interval",
                description = if (korean) "모든 고행 3단계에서 7일에 한 번" else "Once every 7 days across all level-3 policies",
            )
            MoreInformationRow(
                title = if (korean) "적용 범위" else "Scope",
                description = if (korean) "현재 차단된 앱 하나에만 적용" else "Applies only to the currently blocked app",
            )
            MoreInformationRow(
                title = if (korean) "종료 시점" else "Expiration",
                description = if (korean) "현재 차단 정책이 끝나면 자동 만료" else "Expires when the current blocking policy ends",
            )
            MoreInformationRow(
                title = if (korean) "사용 위치" else "Where to use",
                description = if (korean) "고행 3단계 블록 화면에서만 사용" else "Available only from a level-3 block screen",
            )
        }
    }
}

@Composable
private fun MoreInformationRow(title: String, description: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = ScreenRestTheme.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.xxs),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MoreNotificationContent(
    uiState: SafeModeUiState,
    text: AppStrings,
    onBack: () -> Unit,
    onWarningNotificationsChanged: (Boolean) -> Unit,
    onLimitNotificationsChanged: (Boolean) -> Unit,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    Column(verticalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.md)) {
        MoreDetailHeader(
            title = if (korean) "알림" else "Notifications",
            subtitle = if (korean) "사용 시간과 차단 상태 알림을 설정합니다" else "Configure usage and blocking alerts",
            onBack = onBack,
        )
        ScreenRestCard {
            NotificationPreferenceRow(
                title = text.warningNotifications,
                checked = uiState.warningNotificationsEnabled,
                onCheckedChange = onWarningNotificationsChanged,
            )
            NotificationPreferenceRow(
                title = text.limitNotifications,
                checked = uiState.limitNotificationsEnabled,
                onCheckedChange = onLimitNotificationsChanged,
            )
        }
    }
}

@Composable
private fun MoreLanguageContent(
    appLanguage: AppLanguage,
    text: AppStrings,
    onBack: () -> Unit,
    onAppLanguageChanged: (AppLanguage) -> Unit,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    Column(verticalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.md)) {
        MoreDetailHeader(
            title = if (korean) "언어" else "Language",
            subtitle = if (korean) "앱에서 사용할 언어를 선택합니다" else "Choose the language used in the app",
            onBack = onBack,
        )
        ScreenRestCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.xs),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    ChoiceButton(
                        label = text.korean,
                        selected = appLanguage == AppLanguage.Korean,
                        onClick = { onAppLanguageChanged(AppLanguage.Korean) },
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    ChoiceButton(
                        label = "English",
                        selected = appLanguage == AppLanguage.English,
                        onClick = { onAppLanguageChanged(AppLanguage.English) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MoreGoogleAccountContent(
    parentState: ParentManagementState,
    authState: ParentAccountAuthState,
    text: AppStrings,
    onBack: () -> Unit,
    onParentGoogleSignIn: () -> Unit,
    onDeleteAccountAndCloudData: (String) -> Unit,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    var showDeleteDialog by remember { mutableStateOf(false) }
    val isParent = parentState.deviceRole == ParentDeviceRole.Parent
    Column(verticalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.md)) {
        MoreDetailHeader(
            title = if (korean) "Google 계정" else "Google account",
            subtitle = if (korean) "로그인과 클라우드 계정 데이터를 관리합니다" else "Manage sign-in and cloud account data",
            onBack = onBack,
        )
        ScreenRestCard(tone = if (authState.recoverable) ScreenRestTone.Success else ScreenRestTone.Neutral) {
            ScreenRestSectionHeader(
                title = when {
                    authState.recoverable -> if (korean) "Google 계정 연결됨" else "Google account connected"
                    isParent -> if (korean) "Google 로그인 필요" else "Google sign-in required"
                    else -> if (korean) "자녀 기기 계정" else "Child device account"
                },
                supportingText = when {
                    authState.recoverable -> authState.email
                        .ifBlank { authState.displayName }
                        .ifBlank { authState.uid }
                    isParent -> if (korean) "재설치 후 가족 연결을 복구하려면 로그인하세요" else "Sign in to restore family links after reinstalling"
                    else -> if (korean) "자녀 기기는 익명 계정으로 연결 정보를 동기화합니다" else "Child devices sync links with an anonymous account"
                },
                action = {
                    ScreenRestStatusPill(
                        label = if (authState.recoverable) {
                            if (korean) "연결됨" else "Connected"
                        } else {
                            if (korean) "미연결" else "Not linked"
                        },
                        tone = if (authState.recoverable) ScreenRestTone.Success else ScreenRestTone.Neutral,
                    )
                },
            )
            if (!authState.recoverable && isParent) {
                ScreenRestPrimaryButton(
                    label = if (korean) "Google 계정으로 로그인" else "Sign in with Google",
                    onClick = onParentGoogleSignIn,
                    enabled = authState.available,
                )
            }
            authState.lastError.takeIf(String::isNotBlank)?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        ScreenRestCard(tone = ScreenRestTone.Blocked) {
            ScreenRestSectionHeader(
                title = if (korean) "계정 및 클라우드 데이터 삭제" else "Delete account and cloud data",
                supportingText = if (korean) {
                    "부모·자녀 연결, 요청, 명령과 알림 토큰이 삭제되며 복구할 수 없습니다"
                } else {
                    "Family links, requests, commands, and notification tokens are permanently deleted"
                },
            )
            OutlinedButton(
                onClick = { showDeleteDialog = true },
                enabled = authState.authenticated && (parentState.paired || authState.recoverable),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(ScreenRestTheme.radii.button),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f)),
            ) {
                Text(if (korean) "계정 데이터 삭제" else "Delete account data")
            }
        }
    }
    if (showDeleteDialog) {
        AdminPinConfirmDialog(
            title = if (korean) "계정 및 클라우드 데이터 삭제" else "Delete account and cloud data",
            description = if (korean) {
                "이 기기의 Firebase 계정과 연결된 클라우드 데이터를 삭제합니다. 삭제 후 복구할 수 없습니다."
            } else {
                "This deletes the Firebase account and linked cloud data. It cannot be undone."
            },
            confirmLabel = if (korean) "삭제" else "Delete",
            text = text,
            onDismiss = { showDeleteDialog = false },
            onConfirm = { adminPin ->
                showDeleteDialog = false
                onDeleteAccountAndCloudData(adminPin)
            },
        )
    }
}

@Composable
private fun MoreHelpContent(text: AppStrings, onBack: () -> Unit) {
    val korean = text.appLanguage == AppLanguage.Korean
    Column(verticalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.md)) {
        MoreDetailHeader(
            title = if (korean) "도움말" else "Help",
            subtitle = if (korean) "폰 쉼의 규칙과 보호 방식을 확인합니다" else "Learn how ScreenRest rules and protection work",
            onBack = onBack,
        )
        listOf(
            Pair(
                if (korean) "시간 규칙" else "Time rules",
                if (korean) "요일별·앱 그룹·앱별 제한은 함께 적용되며 가장 먼저 끝나는 제한으로 차단됩니다." else "Daily, app-group, and per-app limits work together; the first limit reached blocks usage.",
            ),
            Pair(
                if (korean) "사용 가능 앱 규칙" else "Available-app rules",
                if (korean) "스케줄 차단과 허용앱만 모드는 지금 실행할 수 있는 앱의 범위를 정합니다." else "Schedule blocking and allow-only mode decide which apps can run now.",
            ),
            Pair(
                if (korean) "고행 모드" else "Hardship mode",
                if (korean) "단계가 높을수록 변경과 해제가 더 엄격합니다. 3단계는 시작 후 정책 종료 전까지 관리 PIN만으로 해제할 수 없습니다." else "Higher levels make changes and unlocks stricter. Level 3 cannot be ended with the Admin PIN alone before the policy ends.",
            ),
            Pair(
                if (korean) "부모 연결" else "Parent connection",
                if (korean) "가족 탭에서 기기 역할, 연결 코드, 승인 요청과 연결 기기를 관리합니다." else "Use Family to manage device roles, pairing codes, approval requests, and linked devices.",
            ),
            Pair(
                "Emergency Pass",
                if (korean) "모든 고행 3단계에서 7일에 한 번 공유되며 블록 화면의 현재 앱에만 적용됩니다." else "Shared across all level-3 policies once every 7 days and applies only to the current app from the block screen.",
            ),
        ).forEach { (title, description) ->
            ScreenRestCard {
                MoreInformationRow(title = title, description = description)
            }
        }
    }
}

private fun moreEmergencyPassSummary(summary: PolicySummary, korean: Boolean): String {
    val nextAvailableAt = summary.emergencyPassNextAvailableAtMillis
    return if (nextAvailableAt <= 0L || System.currentTimeMillis() >= nextAvailableAt) {
        if (korean) "사용 가능 · 1회" else "Available · 1 use"
    } else {
        if (korean) "사용 완료 · ${formatDateTime(nextAvailableAt)} 갱신" else "Used · renews ${formatDateTime(nextAvailableAt)}"
    }
}

@Composable
fun SafetyContent(
    uiState: SafeModeUiState,
    safeRecoveryAdminPin: String,
    text: AppStrings,
    isExpanded: Boolean,
    onSafeModeChanged: (Boolean) -> Unit,
    onSafeModeEnableWithPin: (String) -> Unit,
    onSafeModePinStatusSeen: () -> Unit,
    onPolicyEnforcementChanged: (Boolean) -> Unit,
    onPolicyEnforcementDisableWithPin: (String) -> Unit,
    onOpenBlockScreenPreview: (BlockDecisionResult) -> Unit,
    onSafeRecoveryPinChanged: (String) -> Unit,
    onSafeRecoveryClick: () -> Unit,
) {
    var pendingSafetyPinAction by remember { mutableStateOf<SafetyPinAction?>(null) }
    var safetyAdminPin by remember { mutableStateOf("") }
    var diagnosticsExpanded by rememberSaveable { mutableStateOf(false) }

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
        SectionTitle(if (text.appLanguage == AppLanguage.Korean) "보호 제어" else "Protection controls")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (text.appLanguage == AppLanguage.Korean) "보호 일시 중지" else "Pause protection",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (uiState.safeModeEnabled) {
                        if (text.appLanguage == AppLanguage.Korean) "사용 중 · 규칙은 보존되고 차단은 중지됩니다" else "On · rules are kept and blocking is paused"
                    } else {
                        if (text.appLanguage == AppLanguage.Korean) "사용 안 함 · 차단 기능이 작동할 수 있습니다" else "Off · blocking can operate normally"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                Text(
                    if (text.appLanguage == AppLanguage.Korean) "규칙 적용" else "Rule enforcement",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (uiState.policyEnforcementEnabled) {
                        if (text.appLanguage == AppLanguage.Korean) "적용 중" else "On"
                    } else {
                        if (text.appLanguage == AppLanguage.Korean) "꺼짐 · 저장된 규칙을 적용하지 않습니다" else "Off · saved rules are not enforced"
                    },
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
                                    uiState.blockingReadiness.notificationPermissionReady
                                )
                        ),
            )
        }
        if (uiState.autoRecoveryStatus == AutoRecoveryStatus.RecoveredToSafeMode) {
            Text(text.autoRecoveryEnabledSafeMode)
        }
        }

        BlockingReadinessSection(
            readiness = uiState.blockingReadiness,
            text = text,
        )

        SystemHealthStatusSection(
            healthStatus = uiState.systemHealthStatus,
            text = text,
        )

        OutlinedButton(
            onClick = { diagnosticsExpanded = !diagnosticsExpanded },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (diagnosticsExpanded) {
                    if (text.appLanguage == AppLanguage.Korean) "상세 진단 닫기" else "Hide diagnostics"
                } else {
                    if (text.appLanguage == AppLanguage.Korean) "상세 진단 보기" else "View diagnostics"
                },
            )
        }

        if (diagnosticsExpanded) {
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
    }

    val safeRecovery: @Composable ColumnScope.() -> Unit = {
        SimpleCard {
        SectionTitle(if (text.appLanguage == AppLanguage.Korean) "안전 복구" else "Safe Recovery")
        SecurePinTextField(
            value = safeRecoveryAdminPin,
            onValueChange = onSafeRecoveryPinChanged,
            label = text.adminPin,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = onSafeRecoveryClick) {
            Text(if (text.appLanguage == AppLanguage.Korean) "안전 복구" else "Recover safely")
        }
        Text(
            when (uiState.safeRecoveryStatus) {
                SafeRecoveryStatus.Idle -> if (text.appLanguage == AppLanguage.Korean) {
                    "오작동 시 관리 PIN으로 Safe Mode를 켜고 정책 감시를 안전하게 중단합니다."
                } else {
                    "Use the Admin PIN to enter Safe Mode and stop enforcement when the app malfunctions."
                }
                SafeRecoveryStatus.Unlocked -> text.safeModeEnabled
                SafeRecoveryStatus.InvalidPin -> text.invalidAdminPin
                SafeRecoveryStatus.HardshipLocked -> if (text.appLanguage == AppLanguage.Korean) {
                    "고행 3단계에서는 블록 화면의 Emergency Pass만 사용할 수 있습니다."
                } else {
                    "During hardship level 3, use Emergency Pass from the block screen."
                }
            },
        )
        }
    }

    AdaptiveTwoPane(
        isExpanded = isExpanded,
        leftContent = safetyCore,
        rightContent = safeRecovery,
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
    parentAccountAuthState: ParentAccountAuthState,
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
    onPinInputChanged: () -> Unit,
    onPairParentAccount: (String, String, String) -> Unit,
    onParentProfileNameChanged: (String) -> Unit,
    onParentDeviceRoleChanged: (ParentDeviceRole, String) -> Unit,
    onGenerateChildPairingCode: (String) -> Unit,
    onRegisterChildPairingCode: (String, String, String) -> Unit,
    onParentGoogleSignIn: () -> Unit,
    onDeleteAccountAndCloudData: (String) -> Unit,
    onUnlinkParentAccount: (String) -> Unit,
    onUnlinkLinkedChildDevice: (String, String) -> Unit,
    onUnlinkLinkedParentDevice: (String, String) -> Unit,
    onSyncParentDevice: () -> Unit,
    pendingParentManagementAction: ParentManagementPendingAction?,
    onPendingParentManagementActionChanged: (ParentManagementPendingAction?) -> Unit,
    onClearRemoteParentCommands: (String) -> Unit,
    onRemoteAppExtraTime: (String, String, Int) -> Unit,
    onRemoteAppUnlockToday: (String, String) -> Unit,
    onRemoteTotalExtraTime: (Int) -> Unit,
    onRemoteTotalUnlockToday: () -> Unit,
    onApproveRemoteUnlockRequest: (String, Int, Boolean) -> Unit,
    onRejectRemoteUnlockRequest: (String) -> Unit,
    onClearEventLog: () -> Unit,
    onSettingsLanguageExpandedChange: (Boolean) -> Unit,
    onSettingsNotificationExpandedChange: (Boolean) -> Unit,
    onSettingsPinExpandedChange: (Boolean) -> Unit,
    onSettingsParentManagementExpandedChange: (Boolean) -> Unit,
    onSettingsEventLogExpandedChange: (Boolean) -> Unit,
    showParentManagement: Boolean = true,
) {
    var currentAdminPin by remember { mutableStateOf("") }
    var newAdminPin by remember { mutableStateOf("") }

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

        CollapsiblePolicyCard(
            title = text.language,
            icon = PolicySectionIcon.Language,
            expanded = uiState.settingsLanguageExpanded,
            onExpandedChange = onSettingsLanguageExpandedChange,
            text = text,
        ) {
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

        CollapsiblePolicyCard(
            title = text.notificationSettings,
            icon = PolicySectionIcon.Notifications,
            expanded = uiState.settingsNotificationExpanded,
            onExpandedChange = onSettingsNotificationExpandedChange,
            text = text,
        ) {
            NotificationPreferenceRow(
                title = text.warningNotifications,
                checked = uiState.warningNotificationsEnabled,
                onCheckedChange = onWarningNotificationsChanged,
            )
            NotificationPreferenceRow(
                title = text.limitNotifications,
                checked = uiState.limitNotificationsEnabled,
                onCheckedChange = onLimitNotificationsChanged,
            )
        }

        CollapsiblePolicyCard(
            title = text.pinSettings,
            icon = PolicySectionIcon.Pin,
            expanded = uiState.settingsPinExpanded,
            onExpandedChange = onSettingsPinExpandedChange,
            text = text,
        ) {
            PinChangeFields(
                currentPin = currentAdminPin,
                newPin = newAdminPin,
                currentLabel = text.currentAdminPin,
                newLabel = text.newAdminPin,
                onCurrentChanged = {
                    currentAdminPin = it
                    onPinInputChanged()
                },
                onNewChanged = {
                    newAdminPin = it
                    onPinInputChanged()
                },
                onSave = {
                    onUpdateAdminPin(currentAdminPin, newAdminPin)
                    currentAdminPin = ""
                    newAdminPin = ""
                },
                status = uiState.pinChangeStatus,
                text = text,
            )
            Text(
                text = if (text.appLanguage == AppLanguage.Korean) {
                    "관리 PIN은 설정 변경, 부모 연결, 허용된 차단 해제와 안전 복구에 사용됩니다. 고행 3단계는 PIN만으로 종료할 수 없습니다."
                } else {
                    "The Admin PIN confirms settings, pairing, allowed unlocks, and Safe Recovery. It cannot end active hardship level 3 by itself."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (showParentManagement) {
            CollapsiblePolicyCard(
                title = text.parentManagement,
                icon = PolicySectionIcon.ParentManagement,
                expanded = uiState.settingsParentManagementExpanded,
                onExpandedChange = onSettingsParentManagementExpandedChange,
                text = text,
                headerTrailing = {
                    CompactStatusBadge(
                        label = if (uiState.parentManagementState.paired) {
                            text.parentLinked
                        } else {
                            text.parentNotLinked
                        },
                        status = if (uiState.parentManagementState.paired) {
                            LimitStatus.Normal
                        } else {
                            LimitStatus.Warning
                        },
                    )
                },
            ) {
                ParentManagementSection(
                    parentState = uiState.parentManagementState,
                    parentAccountAuthState = parentAccountAuthState,
                    notificationState = uiState.parentNotificationState,
                    parentRequestNotificationReady = uiState.parentRequestNotificationReady,
                    parentRequestNotificationIssue = uiState.parentRequestNotificationIssue,
                    installedApps = uiState.installedApps,
                    policySummary = uiState.policySummary,
                    text = text,
                    onPairParentAccount = onPairParentAccount,
                    onParentProfileNameChanged = onParentProfileNameChanged,
                    onParentDeviceRoleChanged = onParentDeviceRoleChanged,
                    onGenerateChildPairingCode = onGenerateChildPairingCode,
                    onRegisterChildPairingCode = onRegisterChildPairingCode,
                    onParentGoogleSignIn = onParentGoogleSignIn,
                    onDeleteAccountAndCloudData = onDeleteAccountAndCloudData,
                    onUnlinkParentAccount = onUnlinkParentAccount,
                    onUnlinkLinkedChildDevice = onUnlinkLinkedChildDevice,
                    onUnlinkLinkedParentDevice = onUnlinkLinkedParentDevice,
                    onSyncParentDevice = onSyncParentDevice,
                    pendingParentManagementAction = pendingParentManagementAction,
                    onPendingParentManagementActionChanged = onPendingParentManagementActionChanged,
                    onClearRemoteParentCommands = onClearRemoteParentCommands,
                    onRemoteAppExtraTime = onRemoteAppExtraTime,
                    onRemoteAppUnlockToday = onRemoteAppUnlockToday,
                    onRemoteTotalExtraTime = onRemoteTotalExtraTime,
                    onRemoteTotalUnlockToday = onRemoteTotalUnlockToday,
                    onApproveRemoteUnlockRequest = onApproveRemoteUnlockRequest,
                    onRejectRemoteUnlockRequest = onRejectRemoteUnlockRequest,
                    wrapInCard = false,
                )
            }
        }
    }

    val logs: @Composable ColumnScope.() -> Unit = {
        CollapsiblePolicyCard(
            title = text.eventLog,
            icon = PolicySectionIcon.EventLog,
            expanded = uiState.settingsEventLogExpanded,
            onExpandedChange = onSettingsEventLogExpandedChange,
            text = text,
        ) {
            EventLogSection(
                eventLog = uiState.eventLog,
                text = text,
                onClearEventLog = onClearEventLog,
                wrapInCard = false,
            )
        }
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
        readiness.notificationPermissionReady
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
            if (!permissionsReady) {
                Text(
                    text.permissionSettingsRequired,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

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
private fun FamilyContent(
    destination: FamilyDestination,
    onDestinationChanged: (FamilyDestination) -> Unit,
    parentState: ParentManagementState,
    childTopAppsSharingEnabled: Boolean,
    childUsageSnapshots: Map<String, ChildUsageSnapshot>,
    childUsageRefreshRequests: Map<String, ChildUsageRefreshRequest>,
    childImmediateBlocks: Map<String, ImmediateBlockReadState>,
    localImmediateBlock: ImmediateBlockState,
    parentAccountAuthState: ParentAccountAuthState,
    notificationState: ParentNotificationState,
    parentRequestNotificationReady: Boolean,
    parentRequestNotificationIssue: String,
    installedApps: List<InstalledAppInfo>,
    policySummary: PolicySummary,
    safeModeEnabled: Boolean,
    policyEnforcementEnabled: Boolean,
    text: AppStrings,
    onPairParentAccount: (String, String, String) -> Unit,
    onParentProfileNameChanged: (String) -> Unit,
    onParentDeviceRoleChanged: (ParentDeviceRole, String) -> Unit,
    onGenerateChildPairingCode: (String) -> Unit,
    onRegisterChildPairingCode: (String, String, String) -> Unit,
    onParentGoogleSignIn: () -> Unit,
    onDeleteAccountAndCloudData: (String) -> Unit,
    onUnlinkParentAccount: (String) -> Unit,
    onUnlinkLinkedChildDevice: (String, String) -> Unit,
    onUnlinkLinkedParentDevice: (String, String) -> Unit,
    onSyncParentDevice: () -> Unit,
    onCheckImmediateBlock: (String) -> Unit,
    onChildTopAppsSharingChanged: (Boolean) -> Unit,
    onStartImmediateBlock: (String, Int, String) -> Unit,
    onStopImmediateBlock: (String, String, String) -> Unit,
    pendingParentManagementAction: ParentManagementPendingAction?,
    onPendingParentManagementActionChanged: (ParentManagementPendingAction?) -> Unit,
    onClearRemoteParentCommands: (String) -> Unit,
    onRemoteAppExtraTime: (String, String, Int) -> Unit,
    onRemoteAppUnlockToday: (String, String) -> Unit,
    onRemoteTotalExtraTime: (Int) -> Unit,
    onRemoteTotalUnlockToday: () -> Unit,
    onApproveRemoteUnlockRequest: (String, Int, Boolean) -> Unit,
    onRejectRemoteUnlockRequest: (String) -> Unit,
    onOpenLocalRules: () -> Unit,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    when (destination) {
        FamilyDestination.Home -> FamilyHomeContent(
            parentState = parentState,
            childTopAppsSharingEnabled = childTopAppsSharingEnabled,
            childUsageSnapshots = childUsageSnapshots,
            childUsageRefreshRequests = childUsageRefreshRequests,
            childImmediateBlocks = childImmediateBlocks,
            localImmediateBlock = localImmediateBlock,
            policySummary = policySummary,
            safeModeEnabled = safeModeEnabled,
            policyEnforcementEnabled = policyEnforcementEnabled,
            text = text,
            onOpenManagement = { onDestinationChanged(FamilyDestination.Management) },
            onSyncParentDevice = onSyncParentDevice,
            onCheckImmediateBlock = onCheckImmediateBlock,
            onChildTopAppsSharingChanged = onChildTopAppsSharingChanged,
            onStartImmediateBlock = onStartImmediateBlock,
            onStopImmediateBlock = onStopImmediateBlock,
            onOpenLocalRules = onOpenLocalRules,
            onApproveRemoteUnlockRequest = onApproveRemoteUnlockRequest,
            onRejectRemoteUnlockRequest = onRejectRemoteUnlockRequest,
        )

        FamilyDestination.Management -> Column(
            verticalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.md),
        ) {
            MoreDetailHeader(
                title = if (korean) "가족 및 기기 관리" else "Family and device management",
                subtitle = if (korean) "역할, 프로필, 연결과 동기화를 관리합니다" else "Manage roles, profiles, pairing, and sync",
                onBack = { onDestinationChanged(FamilyDestination.Home) },
            )
            ParentManagementSection(
                parentState = parentState,
                parentAccountAuthState = parentAccountAuthState,
                notificationState = notificationState,
                parentRequestNotificationReady = parentRequestNotificationReady,
                parentRequestNotificationIssue = parentRequestNotificationIssue,
                installedApps = installedApps,
                policySummary = policySummary,
                text = text,
                onPairParentAccount = onPairParentAccount,
                onParentProfileNameChanged = onParentProfileNameChanged,
                onParentDeviceRoleChanged = onParentDeviceRoleChanged,
                onGenerateChildPairingCode = onGenerateChildPairingCode,
                onRegisterChildPairingCode = onRegisterChildPairingCode,
                onParentGoogleSignIn = onParentGoogleSignIn,
                onDeleteAccountAndCloudData = onDeleteAccountAndCloudData,
                onUnlinkParentAccount = onUnlinkParentAccount,
                onUnlinkLinkedChildDevice = onUnlinkLinkedChildDevice,
                onUnlinkLinkedParentDevice = onUnlinkLinkedParentDevice,
                onSyncParentDevice = onSyncParentDevice,
                pendingParentManagementAction = pendingParentManagementAction,
                onPendingParentManagementActionChanged = onPendingParentManagementActionChanged,
                onClearRemoteParentCommands = onClearRemoteParentCommands,
                onRemoteAppExtraTime = onRemoteAppExtraTime,
                onRemoteAppUnlockToday = onRemoteAppUnlockToday,
                onRemoteTotalExtraTime = onRemoteTotalExtraTime,
                onRemoteTotalUnlockToday = onRemoteTotalUnlockToday,
                onApproveRemoteUnlockRequest = onApproveRemoteUnlockRequest,
                onRejectRemoteUnlockRequest = onRejectRemoteUnlockRequest,
                wrapInCard = false,
            )
        }
    }
}

@Composable
private fun FamilyHomeContent(
    parentState: ParentManagementState,
    childTopAppsSharingEnabled: Boolean,
    childUsageSnapshots: Map<String, ChildUsageSnapshot>,
    childUsageRefreshRequests: Map<String, ChildUsageRefreshRequest>,
    childImmediateBlocks: Map<String, ImmediateBlockReadState>,
    localImmediateBlock: ImmediateBlockState,
    policySummary: PolicySummary,
    safeModeEnabled: Boolean,
    policyEnforcementEnabled: Boolean,
    text: AppStrings,
    onOpenManagement: () -> Unit,
    onSyncParentDevice: () -> Unit,
    onCheckImmediateBlock: (String) -> Unit,
    onChildTopAppsSharingChanged: (Boolean) -> Unit,
    onStartImmediateBlock: (String, Int, String) -> Unit,
    onStopImmediateBlock: (String, String, String) -> Unit,
    onOpenLocalRules: () -> Unit,
    onApproveRemoteUnlockRequest: (String, Int, Boolean) -> Unit,
    onRejectRemoteUnlockRequest: (String) -> Unit,
) {
    val childDevices = remember(
        parentState.linkedChildDevices,
        parentState.linkedChildPairingCodes,
        parentState.remoteUnlockRequests,
    ) {
        parentState.linkedChildDevices.ifEmpty {
            parentState.linkedChildPairingCodes.map { code ->
                LinkedChildDevice(
                    childDeviceId = code,
                    childDeviceName = code,
                    pairingCode = code,
                )
            }
        }.ifEmpty {
            parentState.remoteUnlockRequests
                .distinctBy { request -> request.childDeviceId }
                .map { request ->
                    LinkedChildDevice(
                        childDeviceId = request.childDeviceId,
                        childDeviceName = request.childDeviceName,
                    )
                }
        }
    }
    val connected = if (parentState.deviceRole == ParentDeviceRole.Parent) {
        parentState.paired || childDevices.isNotEmpty()
    } else {
        parentState.paired || parentState.linkedParentDevices.isNotEmpty()
    }
    val korean = text.appLanguage == AppLanguage.Korean

    Column(verticalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.md)) {
        ScreenRestPageHeader(
            title = if (korean) "가족" else "Family",
            subtitle = when (parentState.deviceRole) {
                ParentDeviceRole.Parent -> if (korean) "자녀 기기와 승인 요청을 관리합니다" else "Manage child devices and approval requests"
                ParentDeviceRole.Child -> if (korean) "내 기기와 부모 연결 상태를 확인합니다" else "Review this device and its parent connection"
            },
            trailing = {
                ScreenRestStatusPill(
                    label = if (connected) {
                        if (korean) "정상 연결" else "Connected"
                    } else {
                        if (korean) "미연결" else "Not linked"
                    },
                    tone = if (connected) ScreenRestTone.Success else ScreenRestTone.Warning,
                )
            },
        )

        when {
            !connected -> FamilyUnpairedContent(
                parentState = parentState,
                text = text,
                onOpenManagement = onOpenManagement,
            )

            parentState.deviceRole == ParentDeviceRole.Parent -> FamilyParentDashboard(
                parentState = parentState,
                childDevices = childDevices,
                childUsageSnapshots = childUsageSnapshots,
                childUsageRefreshRequests = childUsageRefreshRequests,
                childImmediateBlocks = childImmediateBlocks,
                text = text,
                onOpenManagement = onOpenManagement,
                onSyncParentDevice = onSyncParentDevice,
                onCheckImmediateBlock = onCheckImmediateBlock,
                onStartImmediateBlock = onStartImmediateBlock,
                onStopImmediateBlock = onStopImmediateBlock,
                onApproveRemoteUnlockRequest = onApproveRemoteUnlockRequest,
                onRejectRemoteUnlockRequest = onRejectRemoteUnlockRequest,
            )

            else -> FamilyChildDashboard(
                parentState = parentState,
                childTopAppsSharingEnabled = childTopAppsSharingEnabled,
                immediateBlock = localImmediateBlock,
                policySummary = policySummary,
                safeModeEnabled = safeModeEnabled,
                policyEnforcementEnabled = policyEnforcementEnabled,
                text = text,
                onOpenManagement = onOpenManagement,
                onSyncParentDevice = onSyncParentDevice,
                onChildTopAppsSharingChanged = onChildTopAppsSharingChanged,
                onOpenLocalRules = onOpenLocalRules,
            )
        }
    }
}

@Composable
private fun FamilyUnpairedContent(
    parentState: ParentManagementState,
    text: AppStrings,
    onOpenManagement: () -> Unit,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    ScreenRestCard(tone = ScreenRestTone.Warning) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.sm),
        ) {
            MoreMenuIcon(R.drawable.ic_nav_family, ScreenRestTone.Warning)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (korean) "가족 연결이 필요합니다" else "Family connection required",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (korean) {
                        if (parentState.deviceRole == ParentDeviceRole.Parent) {
                            "부모 프로필을 저장하고 자녀의 연결 코드를 등록하세요"
                        } else {
                            "자녀 프로필을 저장하고 부모에게 보낼 연결 코드를 만드세요"
                        }
                    } else if (parentState.deviceRole == ParentDeviceRole.Parent) {
                        "Save the parent profile and register the child's pairing code"
                    } else {
                        "Save the child profile and create a pairing code for a parent"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        ScreenRestPrimaryButton(
            label = if (korean) "연결 설정" else "Set up connection",
            onClick = onOpenManagement,
        )
    }
}

@Composable
private fun FamilyParentDashboard(
    parentState: ParentManagementState,
    childDevices: List<LinkedChildDevice>,
    childUsageSnapshots: Map<String, ChildUsageSnapshot>,
    childUsageRefreshRequests: Map<String, ChildUsageRefreshRequest>,
    childImmediateBlocks: Map<String, ImmediateBlockReadState>,
    text: AppStrings,
    onOpenManagement: () -> Unit,
    onSyncParentDevice: () -> Unit,
    onCheckImmediateBlock: (String) -> Unit,
    onStartImmediateBlock: (String, Int, String) -> Unit,
    onStopImmediateBlock: (String, String, String) -> Unit,
    onApproveRemoteUnlockRequest: (String, Int, Boolean) -> Unit,
    onRejectRemoteUnlockRequest: (String) -> Unit,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    var selectedChildId by rememberSaveable {
        mutableStateOf(childDevices.firstOrNull()?.childDeviceId.orEmpty())
    }
    LaunchedEffect(childDevices, selectedChildId) {
        if (childDevices.none { child -> child.childDeviceId == selectedChildId }) {
            selectedChildId = childDevices.firstOrNull()?.childDeviceId.orEmpty()
        }
    }
    val selectedChild = childDevices.firstOrNull { child -> child.childDeviceId == selectedChildId }
        ?: childDevices.firstOrNull()
    val selectedBlockRead = selectedChild?.childDeviceId?.let(childImmediateBlocks::get)
    val selectedBlock = (selectedBlockRead as? ImmediateBlockReadState.Known)?.block
    val selectedRefreshRequest = selectedChild?.childDeviceId?.let(childUsageRefreshRequests::get)
    var blockClockMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(selectedBlock?.requestId) {
        while (true) {
            delay(30_000L)
            blockClockMillis = System.currentTimeMillis()
        }
    }
    val blockAvailability = immediateBlockAvailability(selectedBlockRead, blockClockMillis)
    val blockActive = blockAvailability == ImmediateBlockAvailability.Active
    var showDurationDialog by rememberSaveable { mutableStateOf(false) }
    var showBlockPinDialog by rememberSaveable { mutableStateOf(false) }
    var showStopPinDialog by rememberSaveable { mutableStateOf(false) }
    var selectedBlockMinutes by rememberSaveable { mutableStateOf(60) }
    val selectedRequests = parentState.remoteUnlockRequests.filter { request ->
        selectedChild == null || request.childDeviceId == selectedChild.childDeviceId
    }

    FamilySectionTitle(if (korean) "연결된 자녀" else "Connected children")
    if (childDevices.size > 1) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.xs),
        ) {
            childDevices.forEach { child ->
                Surface(
                    onClick = { selectedChildId = child.childDeviceId },
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(ScreenRestTheme.radii.chip),
                    color = if (child.childDeviceId == selectedChild?.childDeviceId) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    border = BorderStroke(
                        1.dp,
                        if (child.childDeviceId == selectedChild?.childDeviceId) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                    ),
                ) {
                    Box(
                        modifier = Modifier.heightIn(min = 48.dp).padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = child.childDeviceName.ifBlank { child.childDeviceId },
                            style = MaterialTheme.typography.labelLarge,
                            color = if (child.childDeviceId == selectedChild?.childDeviceId) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
    }
    selectedChild?.let { child ->
        FamilyDeviceCard(
            name = child.childDeviceName.ifBlank { child.childDeviceId },
            deviceLabel = child.childDeviceId.take(12).ifBlank { child.pairingCode },
            lastSyncMillis = childUsageSnapshots[child.childDeviceId]?.capturedAtMillis
                ?: parentState.lastSyncMillis,
            text = text,
            onSync = onSyncParentDevice,
            syncEnabled = selectedRefreshRequest?.canRequestAgain(blockClockMillis) != false,
        )
    }

    FamilySectionTitle(if (korean) "자녀 사용 현황" else "Child usage status")
    FamilyRemoteSnapshotCard(
        snapshot = selectedChild?.childDeviceId?.let(childUsageSnapshots::get),
        refreshRequest = selectedRefreshRequest,
        text = text,
    )

    FamilySectionTitle(if (korean) "부모님이 할 수 있는 작업" else "Parent actions")
    ScreenRestCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.sm),
        ) {
            MoreMenuIcon(R.drawable.ic_family_block,
                if (blockActive) ScreenRestTone.Warning else ScreenRestTone.Blocked)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (korean) "즉시 차단" else "Immediate block",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (blockActive) {
                        if (korean) "자녀 기기에 적용 중" else "Active on child device"
                    } else {
                        if (korean) "필요할 때 사용을 잠시 멈춥니다" else "Pause use when needed"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Button(
            onClick = {
                when (blockAvailability) {
                    ImmediateBlockAvailability.Active -> showStopPinDialog = true
                    ImmediateBlockAvailability.Inactive -> showDurationDialog = true
                    ImmediateBlockAvailability.Unverified, ImmediateBlockAvailability.Failed ->
                        selectedChild?.childDeviceId?.let(onCheckImmediateBlock)
                }
            },
            enabled = selectedChild != null,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(ScreenRestTheme.radii.button),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (blockAvailability == ImmediateBlockAvailability.Inactive)
                    ScreenRestPalette.Coral else ScreenRestPalette.Cobalt,
            ),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_family_block),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(ScreenRestTheme.spacing.xs))
            Text(when (blockAvailability) {
                ImmediateBlockAvailability.Active -> if (korean) "차단 종료" else "End block"
                ImmediateBlockAvailability.Inactive -> if (korean) "지금 차단" else "Block now"
                ImmediateBlockAvailability.Unverified -> if (korean) "차단 상태 확인" else "Check block status"
                ImmediateBlockAvailability.Failed -> if (korean) "상태 다시 확인" else "Retry block status"
            })
        }
        if (blockAvailability == ImmediateBlockAvailability.Unverified ||
            blockAvailability == ImmediateBlockAvailability.Failed
        ) {
            Text(
                text = if (blockAvailability == ImmediateBlockAvailability.Failed) {
                    if (korean) "차단 상태를 읽지 못했습니다. 다시 확인해 주세요."
                    else "Could not read block status. Tap to retry."
                } else {
                    if (korean) "차단 상태 확인 중 · 오래 걸리면 버튼을 눌러 주세요."
                    else "Checking block status · tap if this takes too long."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (blockActive && selectedBlock != null) {
            val remainingMinutes = (((selectedBlock.expiresAtMillis ?: blockClockMillis) -
                blockClockMillis + 59_999L) / 60_000L).toInt().coerceAtLeast(0)
            ScreenRestStatusPill(
                label = if (selectedBlock.appliedAtMillis > 0L) {
                    if (korean) "차단 적용됨 · ${formatLimitMinutesLabel(remainingMinutes)} 남음"
                    else "Block applied · ${formatLimitMinutesLabel(remainingMinutes)} left"
                } else {
                    if (korean) "서버 등록됨 · 자녀 기기 적용 대기" else "Saved · waiting for child device"
                },
                tone = if (selectedBlock.appliedAtMillis > 0L) ScreenRestTone.Warning
                    else ScreenRestTone.Neutral,
            )
            Text(
                text = if (korean) "제한 없는 앱과 필수 시스템 앱은 계속 사용 가능 · 시간이 끝나면 자동 해제"
                    else "Unrestricted and essential apps remain available · ends automatically",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selectedBlock != null && selectedBlock.revokedAtMillis > 0L &&
            (selectedBlock.expiresAtMillis ?: 0L) > blockClockMillis
        ) {
            ScreenRestStatusPill(
                label = if (selectedBlock.releasedAtMillis > 0L) {
                    if (korean) "차단 종료 확인됨" else "Block ended on child"
                } else {
                    if (korean) "종료 요청됨 · 자녀 기기 확인 대기" else "Stop sent · waiting for child device"
                },
                tone = ScreenRestTone.Neutral,
            )
        }
        val childSnapshot = selectedChild?.childDeviceId?.let(childUsageSnapshots::get)
        if (childSnapshot?.protectionPaused == true || childSnapshot?.usageAccessReady == false) {
            Text(
                text = if (korean) "자녀 기기의 보호가 중지되었거나 사용 기록 권한이 없습니다. 자녀 기기에서 확인해 주세요."
                    else "Protection is paused or usage access is missing on the child device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(1.dp)
                .background(ScreenRestTheme.colors.divider),
        )
        ScreenRestListRow(
            title = if (korean) "자녀 규칙" else "Child rules",
            supportingText = if (korean) "원격 변경은 준비 중" else "Remote editing is not yet available",
            enabled = false,
            leading = { MoreMenuIcon(R.drawable.ic_nav_rules, ScreenRestTone.Neutral) },
            trailing = {
                ScreenRestStatusPill(
                    label = if (korean) "준비 중" else "Coming soon",
                    tone = ScreenRestTone.Neutral,
                )
            },
        )
    }

    if (showDurationDialog && selectedChild != null) {
        Dialog(onDismissRequest = { showDurationDialog = false }) {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        if (korean) "지금 차단" else "Block now",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (korean) "일반 앱을 선택한 시간 동안 차단합니다. 제한 없는 앱과 필수 시스템 앱은 계속 사용됩니다."
                        else "Blocks regular apps for the selected duration. Unrestricted and essential apps stay available.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(15, 30, 60, 120, 240, 480, 1440).forEach { minutes ->
                            Surface(
                                onClick = { selectedBlockMinutes = minutes },
                                shape = RoundedCornerShape(12.dp),
                                color = if (selectedBlockMinutes == minutes)
                                    MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                            ) {
                                Text(
                                    formatLimitMinutesLabel(minutes),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                )
                            }
                        }
                    }
                    Text(
                        if (korean) "오프라인이면 종료 전 재연결할 때만 적용됩니다. 기존 고행 차단은 유지됩니다."
                        else "If offline, applies only when reconnected before expiry. Existing hardship blocks stay in place.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showDurationDialog = false }, modifier = Modifier.weight(1f)) {
                            Text(if (korean) "취소" else "Cancel")
                        }
                        Button(onClick = {
                            showDurationDialog = false
                            showBlockPinDialog = true
                        }, modifier = Modifier.weight(1f)) {
                            Text(if (korean) "다음" else "Next")
                        }
                    }
                }
            }
        }
    }
    if (showBlockPinDialog && selectedChild != null) {
        AdminPinConfirmDialog(
            title = if (korean) "차단 확인" else "Confirm block",
            description = if (korean) "${selectedChild.childDeviceName} · ${formatLimitMinutesLabel(selectedBlockMinutes)} 동안 차단"
                else "Block ${selectedChild.childDeviceName} for ${formatLimitMinutesLabel(selectedBlockMinutes)}",
            confirmLabel = if (korean) "차단 요청" else "Send block",
            text = text,
            onDismiss = { showBlockPinDialog = false },
            onConfirm = { pin ->
                onStartImmediateBlock(selectedChild.childDeviceId, selectedBlockMinutes, pin)
                showBlockPinDialog = false
            },
        )
    }
    if (showStopPinDialog && selectedChild != null && selectedBlock != null) {
        AdminPinConfirmDialog(
            title = if (korean) "차단 종료" else "End block",
            description = if (korean) "이 즉시 차단만 종료합니다. 기존 규칙과 고행 차단은 유지됩니다."
                else "Only this immediate block ends. Existing rules and hardship blocks remain.",
            confirmLabel = if (korean) "종료 요청" else "Send stop",
            text = text,
            onDismiss = { showStopPinDialog = false },
            onConfirm = { pin ->
                onStopImmediateBlock(selectedChild.childDeviceId, selectedBlock.requestId, pin)
                showStopPinDialog = false
            },
        )
    }

    FamilySectionTitle(if (korean) "승인 요청" else "Approval requests")
    RemoteUnlockRequestList(
        requests = selectedRequests,
        text = text,
        onApproveExtraTime = { request, minutes ->
            onApproveRemoteUnlockRequest(request.id, minutes, false)
        },
        onApproveUnlockToday = { request ->
            onApproveRemoteUnlockRequest(request.id, 0, true)
        },
        onReject = { request -> onRejectRemoteUnlockRequest(request.id) },
    )

    FamilyManagementEntry(text = text, onClick = onOpenManagement)
}

@Composable
private fun FamilyChildDashboard(
    parentState: ParentManagementState,
    childTopAppsSharingEnabled: Boolean,
    immediateBlock: ImmediateBlockState,
    policySummary: PolicySummary,
    safeModeEnabled: Boolean,
    policyEnforcementEnabled: Boolean,
    text: AppStrings,
    onOpenManagement: () -> Unit,
    onSyncParentDevice: () -> Unit,
    onChildTopAppsSharingChanged: (Boolean) -> Unit,
    onOpenLocalRules: () -> Unit,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    var blockClockMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(immediateBlock.requestId) {
        while (true) {
            delay(30_000L)
            blockClockMillis = System.currentTimeMillis()
        }
    }
    if (parentState.paired && immediateBlock.isActiveAt(blockClockMillis) &&
        parentState.linkedParentDevices.any { it.parentUid == immediateBlock.parentUid }
    ) {
        ScreenRestCard(tone = ScreenRestTone.Warning) {
            Text(
                if (safeModeEnabled || !policyEnforcementEnabled) {
                    if (korean) "부모 차단 요청 · 보호 중지 상태" else "Parent block requested · protection paused"
                } else {
                    if (korean) "부모가 지금 차단 중입니다" else "Parent block is active"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (safeModeEnabled || !policyEnforcementEnabled) {
                    if (korean) "차단을 적용하려면 자녀 기기의 보호 기능을 켜야 합니다."
                    else "Turn on protection on the child device to apply the block."
                } else {
                    if (korean) "제한 없는 앱과 필수 시스템 앱은 사용 가능합니다. 지정된 시간이 끝나면 자동 해제됩니다."
                    else "Unrestricted and essential apps remain available. The block ends automatically."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    val profileName = parentState.localProfileName.ifBlank {
        parentState.childDeviceName.ifBlank { if (korean) "내 기기" else "This device" }
    }
    FamilySectionTitle(if (korean) "내 기기" else "This device")
    FamilyDeviceCard(
        name = profileName,
        deviceLabel = parentState.childDeviceName.ifBlank { parentState.childDeviceId.take(12) },
        lastSyncMillis = parentState.lastSyncMillis,
        text = text,
        onSync = onSyncParentDevice,
    )

    FamilySectionTitle(if (korean) "오늘 사용 현황" else "Today's usage")
    FamilyLocalUsageCard(
        summary = policySummary,
        safeModeEnabled = safeModeEnabled,
        policyEnforcementEnabled = policyEnforcementEnabled,
        text = text,
        onOpenRules = onOpenLocalRules,
    )

    ChildTopAppsSharingCard(
        enabled = childTopAppsSharingEnabled,
        text = text,
        onChanged = onChildTopAppsSharingChanged,
    )

    FamilySectionTitle(if (korean) "연결된 부모" else "Connected parents")
    ScreenRestCard {
        if (parentState.linkedParentDevices.isEmpty()) {
            Text(
                text = parentState.parentAccountId.ifBlank {
                    if (korean) "연결된 부모 정보가 없습니다" else "No linked parent information"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            parentState.linkedParentDevices.forEach { parent ->
                ScreenRestListRow(
                    title = parent.parentDisplayName.ifBlank { parent.parentUid },
                    supportingText = if (korean) "연결된 부모 기기" else "Linked parent device",
                    leading = { MoreMenuIcon(R.drawable.ic_nav_family, ScreenRestTone.Success) },
                    trailing = {
                        ScreenRestStatusPill(
                            label = if (korean) "연결됨" else "Linked",
                            tone = ScreenRestTone.Success,
                        )
                    },
                )
            }
        }
    }

    FamilyChildRequestHistory(requests = parentState.remoteUnlockRequests, text = text)
    FamilyManagementEntry(text = text, onClick = onOpenManagement)
}

@Composable
private fun ChildTopAppsSharingCard(
    enabled: Boolean,
    text: AppStrings,
    onChanged: (Boolean) -> Unit,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    var showConsent by rememberSaveable { mutableStateOf(false) }
    ScreenRestCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (korean) "앱별 사용 현황 공유" else "Share app usage summary",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            ScreenRestStatusPill(
                label = if (enabled) {
                    if (korean) "공유 중" else "Sharing"
                } else {
                    if (korean) "꺼짐" else "Off"
                },
                tone = if (enabled) ScreenRestTone.Success else ScreenRestTone.Neutral,
            )
        }
        Text(
            if (korean) "오늘 많이 사용한 앱 최대 5개의 이름과 사용 시간을 연결된 부모에게 보여줍니다."
            else "Share names and durations of up to five most-used apps with linked parents.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (enabled) {
            TextButton(onClick = { onChanged(false) }) {
                Text(if (korean) "공유 중지" else "Stop sharing")
            }
        } else {
            Button(onClick = { showConsent = true }) {
                Text(if (korean) "공유 내용 확인" else "Review sharing")
            }
        }
    }
    if (showConsent) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showConsent = false },
            title = { Text(if (korean) "앱별 사용 현황 공유" else "Share app usage summary") },
            text = {
                Text(
                    if (korean) {
                        "자녀 기기에서 오늘 많이 사용한 앱 최대 5개의 이름과 사용 시간을 Firebase로 전송해 연결된 부모 기기에 표시합니다. 전체 설치 앱 목록과 앱을 사용한 정확한 시각은 보내지 않습니다. 공유를 중지해도 오프라인인 동안에는 이전 요약이 보일 수 있으며, 재연결 후 갱신됩니다."
                    } else {
                        "Up to five app names and today's usage durations are sent through Firebase to linked parents. The full installed-app list and exact usage times are not sent. If this device is offline when sharing is stopped, the previous summary may remain visible until it reconnects."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showConsent = false
                    onChanged(true)
                }) { Text(if (korean) "동의하고 공유" else "Agree and share") }
            },
            dismissButton = {
                TextButton(onClick = { showConsent = false }) {
                    Text(if (korean) "나중에" else "Not now")
                }
            },
        )
    }
}

@Composable
private fun FamilyDeviceCard(
    name: String,
    deviceLabel: String,
    lastSyncMillis: Long,
    text: AppStrings,
    onSync: () -> Unit,
    syncEnabled: Boolean = true,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    ScreenRestCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.sm),
        ) {
            MoreMenuIcon(R.drawable.ic_family_device, ScreenRestTone.Primary)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.xxs),
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = deviceLabel.ifBlank { if (korean) "기기 정보" else "Device" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onSync, enabled = syncEnabled) {
                Icon(
                    painter = painterResource(R.drawable.ic_family_sync),
                    contentDescription = if (syncEnabled) {
                        if (korean) "새 사용 현황 요청" else "Request fresh usage"
                    } else {
                        if (korean) "다시 요청은 1분 후 가능" else "Try again in one minute"
                    },
                    tint = if (syncEnabled) ScreenRestTheme.colors.success
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(1.dp)
                .background(ScreenRestTheme.colors.divider),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MoreMenuIcon(R.drawable.ic_family_sync, ScreenRestTone.Success)
            Text(
                text = familySyncLabel(lastSyncMillis, korean),
                modifier = Modifier.weight(1f).padding(start = ScreenRestTheme.spacing.sm),
                style = MaterialTheme.typography.bodyMedium,
                color = ScreenRestTheme.colors.success,
            )
            if (!syncEnabled) {
                ScreenRestStatusPill(
                    label = if (korean) "잠시 후" else "Wait",
                    tone = ScreenRestTone.Neutral,
                )
            }
        }
    }
}

@Composable
private fun FamilyRemoteSnapshotCard(
    snapshot: ChildUsageSnapshot?,
    refreshRequest: ChildUsageRefreshRequest?,
    text: AppStrings,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    var clockMillis by remember(snapshot?.capturedAtMillis) {
        mutableStateOf(System.currentTimeMillis())
    }
    LaunchedEffect(snapshot?.capturedAtMillis) {
        while (true) {
            delay(60_000L)
            clockMillis = System.currentTimeMillis()
        }
    }
    val limitMinutes = snapshot?.effectiveDailyLimitMinutes
    val usedMinutes = snapshot?.todayUsedMillis?.let { millis -> (millis / 60_000L).toInt() } ?: 0
    val countedMinutes = snapshot?.dailyCountedUsageMillis?.let { millis -> (millis / 60_000L).toInt() } ?: 0
    val remainingMinutes = snapshot?.remainingDailyMinutes()
    val stale = snapshot?.let { usage ->
        usage.isDelayed(clockMillis) ||
            usage.dateKey != java.time.Instant.ofEpochMilli(clockMillis)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
    } == true
    val refreshPending = refreshRequest?.isPending(clockMillis) == true
    val refreshDelayed = refreshRequest?.isDelayed(clockMillis) == true
    val refreshExpired = refreshRequest != null && refreshRequest.completedAtMillis == 0L &&
        refreshRequest.expiresAtMillis <= clockMillis &&
        clockMillis - refreshRequest.expiresAtMillis < 60 * 60_000L &&
        (snapshot == null || snapshot.capturedAtMillis < refreshRequest.requestedAtMillis - 2 * 60_000L)
    val progress = if (limitMinutes == 0) {
        1f
    } else if (limitMinutes != null && limitMinutes > 0) {
        countedMinutes.toFloat().div(limitMinutes.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    ScreenRestCard(tone = if (snapshot == null || stale || !snapshot.usageAccessReady || refreshPending) {
        ScreenRestTone.Warning
    } else {
        ScreenRestTone.Success
    }) {
        if (snapshot == null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.sm),
            ) {
                MoreMenuIcon(R.drawable.ic_family_clock, ScreenRestTone.Neutral)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (korean) "자녀 사용량 대기 중" else "Waiting for child usage",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (refreshExpired) {
                            if (korean) "새 기록 요청이 만료됐습니다. 다시 동기화해 주세요."
                            else "The refresh request expired. Try syncing again."
                        } else if (refreshPending) {
                            if (korean) "새 사용 현황을 요청했습니다. 자녀 기기 응답을 기다리는 중입니다."
                            else "Fresh usage requested. Waiting for the child device."
                        } else if (korean) {
                            "동기화를 누르거나 자녀 기기의 정기 동기화를 기다려 주세요."
                        } else {
                            "Tap sync or wait for the child's scheduled update."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            ScreenRestSectionHeader(
                title = if (stale) {
                    if (korean) "마지막 사용 기록" else "Last usage snapshot"
                } else {
                    if (korean) "오늘 사용" else "Today's usage"
                },
                supportingText = snapshot.dateKey,
                action = {
                    ScreenRestStatusPill(
                        label = if (refreshDelayed) {
                            if (korean) "연결 대기" else "Waiting for device"
                        } else if (refreshPending) {
                            if (korean) "새 기록 요청 중" else "Refreshing"
                        } else if (stale) {
                            if (korean) "업데이트 지연" else "Update delayed"
                        } else {
                            if (korean) "동기화됨" else "Synced"
                        },
                        tone = if (stale || refreshPending) ScreenRestTone.Warning else ScreenRestTone.Success,
                    )
                },
            )
            if (!snapshot.usageAccessReady) {
                Text(
                    text = if (korean) "자녀 기기의 사용 기록 권한을 확인해 주세요" else "Check usage access on the child device",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    text = if (remainingMinutes != null) {
                        if (korean) "전체 ${formatLimitMinutesLabel(usedMinutes)} · 제한 ${formatLimitMinutesLabel(remainingMinutes)} 남음" else "${formatLimitMinutesLabel(usedMinutes)} total · ${formatLimitMinutesLabel(remainingMinutes)} limit left"
                    } else {
                        if (korean) "${formatLimitMinutesLabel(usedMinutes)} 사용" else "${formatLimitMinutesLabel(usedMinutes)} used"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (remainingMinutes != null) {
                    FamilyUsageProgress(progress = progress, tone = ScreenRestTone.Warning)
                }
                if (snapshot.appUsageSharingEnabled) {
                    Text(
                        if (korean) "많이 사용한 앱" else "Most-used apps",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    if (snapshot.topApps.isEmpty()) {
                        Text(
                            if (korean) "아직 기록된 앱이 없습니다" else "No app usage recorded yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(ScreenRestTheme.radii.row),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, ScreenRestTheme.colors.divider),
                        ) {
                            Column(modifier = Modifier.padding(horizontal = ScreenRestTheme.spacing.xs)) {
                                val visibleApps = snapshot.topApps.take(5)
                                visibleApps.forEachIndexed { index, app ->
                                    ScreenRestListRow(
                                        title = app.appName,
                                        leading = {
                                            FamilyTopAppBadge(app.appName)
                                        },
                                        trailing = {
                                            Text(
                                                formatDuration(app.usedMillis),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        },
                                    )
                                    if (index != visibleApps.lastIndex) {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().padding(start = 54.dp)
                                                .height(1.dp).background(ScreenRestTheme.colors.divider),
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        if (korean) "앱별 현황은 자녀 기기에서 공유에 동의하면 표시됩니다"
                        else "App usage appears after sharing is enabled on the child device",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = when {
                    snapshot.protectionPaused -> if (korean) "자녀 기기에서 보호가 일시 중지되었습니다" else "Protection is paused on the child device"
                    snapshot.dailyUnlockedForToday -> if (korean) "오늘의 전체 시간 제한이 해제되었습니다" else "The daily limit is unlocked for today"
                    limitMinutes == null -> if (korean) "오늘의 전체 시간 제한이 없습니다" else "No overall daily limit is set"
                    else -> if (korean) "자녀 기기에서 측정한 값입니다" else "Measured on the child device"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = familySyncLabel(snapshot.capturedAtMillis, korean),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (refreshPending || refreshExpired) {
                Text(
                    text = if (refreshExpired) {
                        if (korean) "새 기록 요청이 만료됐습니다. 마지막 측정값을 표시합니다."
                        else "Refresh request expired. Showing the last measurement."
                    } else if (refreshDelayed) {
                        if (korean) "자녀 기기가 오프라인이거나 절전 중일 수 있습니다."
                        else "The child device may be offline or sleeping."
                    } else {
                        if (korean) "자녀 기기에서 새 사용량을 측정하고 있습니다."
                        else "Waiting for a new measurement from the child device."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FamilyTopAppBadge(appName: String) {
    val paletteIndex = Math.floorMod(appName.trim().lowercase().hashCode(), 5)
    val (background, foreground) = when (paletteIndex) {
        0 -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
        1 -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.secondary
        2 -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.tertiary
        3 -> ScreenRestTheme.colors.scheduleContainer to ScreenRestTheme.colors.schedule
        else -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.error
    }
    val initial = appName.trim().takeIf { it.isNotEmpty() }
        ?.let { name -> String(Character.toChars(name.codePointAt(0))).uppercase() }
        ?: "·"
    Surface(
        modifier = Modifier.size(34.dp),
        shape = RoundedCornerShape(11.dp),
        color = background,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = initial,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = foreground,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun FamilyLocalUsageCard(
    summary: PolicySummary,
    safeModeEnabled: Boolean,
    policyEnforcementEnabled: Boolean,
    text: AppStrings,
    onOpenRules: () -> Unit,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    val availableMinutes = (summary.totalLimitMinutes + summary.totalExtraMinutes).coerceAtLeast(0)
    val remainingMinutes = (availableMinutes - summary.totalUsedMinutes).coerceAtLeast(0)
    val progress = if (summary.totalLimitEnabled && availableMinutes > 0) {
        summary.totalUsedMinutes.toFloat().div(availableMinutes.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val statusTitle = when {
        safeModeEnabled -> if (korean) "보호 일시 중지" else "Protection paused"
        !policyEnforcementEnabled -> if (korean) "규칙 적용 꺼짐" else "Rules are off"
        summary.totalUnlockedForToday -> if (korean) "오늘만 허용" else "Allowed for today"
        summary.totalStatus == LimitStatus.Exceeded -> if (korean) "사용 시간 종료" else "Time is up"
        summary.activeScheduleSummary != null -> summary.activeScheduleSummary.name
        summary.allowOnlyModeEnabled -> if (korean) "허용앱만 사용" else "Allow-only mode"
        else -> if (korean) "자유 시간" else "Free time"
    }
    ScreenRestCard(tone = when {
        summary.totalStatus == LimitStatus.Exceeded -> ScreenRestTone.Blocked
        summary.totalStatus == LimitStatus.Warning -> ScreenRestTone.Warning
        else -> ScreenRestTone.Success
    }) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.sm),
        ) {
            MoreMenuIcon(R.drawable.ic_family_clock, ScreenRestTone.Success)
            Column(modifier = Modifier.weight(1f)) {
                Text(statusTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    text = if (summary.totalLimitEnabled) {
                        if (korean) "${formatLimitMinutesLabel(summary.totalUsedMinutes)} 사용 · ${formatLimitMinutesLabel(remainingMinutes)} 남음" else "${formatLimitMinutesLabel(summary.totalUsedMinutes)} used · ${formatLimitMinutesLabel(remainingMinutes)} left"
                    } else {
                        if (korean) "${formatLimitMinutesLabel(summary.actualTotalUsedMinutes)} 사용 · 제한 없음" else "${formatLimitMinutesLabel(summary.actualTotalUsedMinutes)} used · no daily limit"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        if (summary.totalLimitEnabled) {
            FamilyUsageProgress(
                progress = progress,
                tone = when (summary.totalStatus) {
                    LimitStatus.Normal -> ScreenRestTone.Success
                    LimitStatus.Warning -> ScreenRestTone.Warning
                    LimitStatus.Exceeded -> ScreenRestTone.Blocked
                },
            )
            Text(
                text = if (korean) "오늘 ${formatLimitMinutesLabel(availableMinutes)}" else "Today ${formatLimitMinutesLabel(availableMinutes)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(ScreenRestTheme.colors.divider),
        )
        Surface(
            shape = RoundedCornerShape(ScreenRestTheme.radii.row),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, ScreenRestTheme.colors.divider),
        ) {
            Column(modifier = Modifier.padding(horizontal = ScreenRestTheme.spacing.xs)) {
                FamilyRuleSummaryRow(
                    title = if (korean) "요일별 제한" else "Daily limit",
                    value = if (summary.totalLimitEnabled) formatLimitMinutesLabel(availableMinutes) else text.noLimit,
                    iconRes = R.drawable.ic_nav_today,
                )
                Box(modifier = Modifier.fillMaxWidth().padding(start = 62.dp)
                    .height(1.dp).background(ScreenRestTheme.colors.divider))
                FamilyRuleSummaryRow(
                    title = if (korean) "앱별 제한" else "App limits",
                    value = if (korean) "${summary.appLimitSummaries.count { it.activeToday }}개 적용" else "${summary.appLimitSummaries.count { it.activeToday }} active",
                    iconRes = R.drawable.ic_nav_rules,
                )
                Box(modifier = Modifier.fillMaxWidth().padding(start = 62.dp)
                    .height(1.dp).background(ScreenRestTheme.colors.divider))
                FamilyRuleSummaryRow(
                    title = if (korean) "실행 범위" else "App availability",
                    value = when {
                        summary.activeScheduleSummary != null -> if (korean) "스케줄 적용 중" else "Schedule active"
                        summary.allowOnlyModeEnabled -> if (korean) "허용앱만 적용 중" else "Allow-only active"
                        else -> if (korean) "일반" else "Normal"
                    },
                    iconRes = R.drawable.ic_more_protection,
                )
            }
        }
        OutlinedButton(
            onClick = onOpenRules,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(ScreenRestTheme.radii.button),
        ) {
            Text(if (korean) "내 규칙 보기" else "View my rules")
        }
    }
}

@Composable
private fun FamilyUsageProgress(progress: Float, tone: ScreenRestTone) {
    val color = when (tone) {
        ScreenRestTone.Success -> ScreenRestTheme.colors.success
        ScreenRestTone.Warning -> ScreenRestTheme.colors.warning
        ScreenRestTone.Blocked -> ScreenRestTheme.colors.blocked
        else -> MaterialTheme.colorScheme.primary
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(ScreenRestTheme.colors.progressTrack),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(color),
        )
    }
}

@Composable
private fun FamilyRuleSummaryRow(title: String, value: String, iconRes: Int) {
    ScreenRestListRow(
        title = title,
        leading = { MoreMenuIcon(iconRes, ScreenRestTone.Primary) },
        trailing = {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

@Composable
private fun FamilyChildRequestHistory(requests: List<RemoteUnlockRequest>, text: AppStrings) {
    val korean = text.appLanguage == AppLanguage.Korean
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            now = System.currentTimeMillis()
        }
    }
    var showAllToday by rememberSaveable { mutableStateOf(false) }
    var showRecentHistory by rememberSaveable { mutableStateOf(false) }
    val grouped = groupRemoteRequestsForDisplay(requests, now)
    val visibleRequests = grouped.pending + (if (showAllToday) grouped.today else grouped.today.take(3)) +
        (if (showRecentHistory) grouped.recentHistory else emptyList())
    FamilySectionTitle(if (korean) "보낸 요청" else "Sent requests")
    ScreenRestCard(tone = if (grouped.pending.isNotEmpty()) ScreenRestTone.Warning else ScreenRestTone.Neutral) {
        if (grouped.pending.isEmpty() && grouped.today.isEmpty() && grouped.recentHistory.isEmpty()) {
            Text(
                text = if (korean) "보낸 요청이 없습니다" else "No sent requests",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            visibleRequests.forEachIndexed { index, request ->
                val displayStatus = if (request.status == RemoteUnlockRequestStatus.Pending &&
                    request.expiresAtMillis < now
                ) RemoteUnlockRequestStatus.Expired else request.status
                ScreenRestListRow(
                    title = request.remoteRequestTitle(text),
                    supportingText = if (korean) {
                        "${formatClockTime(request.createdAtMillis)} · ${formatLimitMinutesLabel(request.requestedMinutes)} 요청"
                    } else {
                        "${formatClockTime(request.createdAtMillis)} · requested ${formatLimitMinutesLabel(request.requestedMinutes)}"
                    },
                    leading = {
                        MoreMenuIcon(
                            R.drawable.ic_family_clock,
                            if (displayStatus == RemoteUnlockRequestStatus.Pending) ScreenRestTone.Warning else ScreenRestTone.Neutral,
                        )
                    },
                    trailing = {
                        ScreenRestStatusPill(
                            label = familyRequestStatusLabel(displayStatus, korean),
                            tone = when (displayStatus) {
                                RemoteUnlockRequestStatus.Pending -> ScreenRestTone.Warning
                                RemoteUnlockRequestStatus.Approved -> ScreenRestTone.Success
                                else -> ScreenRestTone.Neutral
                            },
                        )
                    },
                )
                if (index != visibleRequests.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(ScreenRestTheme.colors.divider),
                    )
                }
            }
            if (grouped.today.size > 3) {
                TextButton(onClick = { showAllToday = !showAllToday }) {
                    Text(if (showAllToday) {
                        if (korean) "오늘 이력 접기" else "Show less"
                    } else {
                        if (korean) "오늘 이력 ${grouped.today.size}건 보기" else "View all ${grouped.today.size} today"
                    })
                }
            }
            if (grouped.recentHistory.isNotEmpty()) {
                TextButton(onClick = { showRecentHistory = !showRecentHistory }) {
                    Text(if (showRecentHistory) {
                        if (korean) "지난 이력 접기" else "Hide recent history"
                    } else {
                        if (korean) "지난 7일 이력 ${grouped.recentHistory.size}건" else "Last 7 days · ${grouped.recentHistory.size}"
                    })
                }
            }
        }
    }
}

@Composable
private fun FamilyManagementEntry(text: AppStrings, onClick: () -> Unit) {
    val korean = text.appLanguage == AppLanguage.Korean
    FamilySectionTitle(if (korean) "가족 관리" else "Family management")
    ScreenRestCard(contentPadding = PaddingValues(vertical = ScreenRestTheme.spacing.xxs)) {
        ScreenRestListRow(
            title = if (korean) "가족 및 기기 관리" else "Family and device management",
            supportingText = if (korean) "연결 · 역할 · 프로필 · 동기화" else "Pairing · roles · profiles · sync",
            onClick = onClick,
            leading = { MoreMenuIcon(R.drawable.ic_family_manage, ScreenRestTone.Primary) },
            trailing = { MoreChevron() },
        )
    }
}

@Composable
private fun FamilySectionTitle(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(horizontal = ScreenRestTheme.spacing.xs),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

private fun familySyncLabel(lastSyncMillis: Long, korean: Boolean): String {
    if (lastSyncMillis <= 0L) {
        return if (korean) "아직 동기화되지 않음" else "Not synced yet"
    }
    val elapsedMinutes = ((System.currentTimeMillis() - lastSyncMillis).coerceAtLeast(0L) / 60_000L).toInt()
    return when {
        elapsedMinutes < 1 -> if (korean) "방금 동기화" else "Synced just now"
        elapsedMinutes < 60 -> if (korean) "${elapsedMinutes}분 전 동기화" else "Synced ${elapsedMinutes}m ago"
        elapsedMinutes < 1_440 -> {
            val hours = elapsedMinutes / 60
            if (korean) "${hours}시간 전 동기화" else "Synced ${hours}h ago"
        }
        else -> if (korean) "마지막 동기화 ${formatDateTime(lastSyncMillis)}" else "Last sync ${formatDateTime(lastSyncMillis)}"
    }
}

private fun familyRequestStatusLabel(status: RemoteUnlockRequestStatus, korean: Boolean): String {
    return when (status) {
        RemoteUnlockRequestStatus.Pending -> if (korean) "대기" else "Pending"
        RemoteUnlockRequestStatus.Approved -> if (korean) "승인" else "Approved"
        RemoteUnlockRequestStatus.Rejected -> if (korean) "거절" else "Rejected"
        RemoteUnlockRequestStatus.Expired -> if (korean) "만료" else "Expired"
        RemoteUnlockRequestStatus.Failed -> if (korean) "실패" else "Failed"
    }
}

@Composable
fun ParentManagementSection(
    parentState: ParentManagementState,
    parentAccountAuthState: ParentAccountAuthState,
    notificationState: ParentNotificationState,
    parentRequestNotificationReady: Boolean,
    parentRequestNotificationIssue: String,
    installedApps: List<InstalledAppInfo>,
    policySummary: PolicySummary,
    text: AppStrings,
    onPairParentAccount: (String, String, String) -> Unit,
    onParentProfileNameChanged: (String) -> Unit,
    onParentDeviceRoleChanged: (ParentDeviceRole, String) -> Unit,
    onGenerateChildPairingCode: (String) -> Unit,
    onRegisterChildPairingCode: (String, String, String) -> Unit,
    onParentGoogleSignIn: () -> Unit,
    onDeleteAccountAndCloudData: (String) -> Unit,
    onUnlinkParentAccount: (String) -> Unit,
    onUnlinkLinkedChildDevice: (String, String) -> Unit,
    onUnlinkLinkedParentDevice: (String, String) -> Unit,
    onSyncParentDevice: () -> Unit,
    pendingParentManagementAction: ParentManagementPendingAction?,
    onPendingParentManagementActionChanged: (ParentManagementPendingAction?) -> Unit,
    onClearRemoteParentCommands: (String) -> Unit,
    onRemoteAppExtraTime: (String, String, Int) -> Unit,
    onRemoteAppUnlockToday: (String, String) -> Unit,
    onRemoteTotalExtraTime: (Int) -> Unit,
    onRemoteTotalUnlockToday: () -> Unit,
    onApproveRemoteUnlockRequest: (String, Int, Boolean) -> Unit,
    onRejectRemoteUnlockRequest: (String) -> Unit,
    wrapInCard: Boolean = true,
) {
    val focusManager = LocalFocusManager.current
    var profileName by remember(parentState.localProfileName, parentState.deviceRole) {
        mutableStateOf(
            parentState.localProfileName.ifBlank {
                if (parentState.deviceRole == ParentDeviceRole.Parent) {
                    parentState.parentAccountId.ifBlank { "Parent device" }
                } else {
                    parentState.childDeviceName.ifBlank { "Child device" }
                }
            },
        )
    }
    var profileSaveRequested by rememberSaveable { mutableStateOf(false) }
    var profileSaveAcknowledged by rememberSaveable { mutableStateOf(false) }
    var selectedRoleDraft by remember { mutableStateOf(parentState.deviceRole) }
    var childPairingCodeInput by remember { mutableStateOf("") }
    var showGeneratePairingCodePinDialog by remember { mutableStateOf(false) }
    var pairingCodePendingRegistration by remember { mutableStateOf("") }
    var showDeleteAccountPinDialog by remember { mutableStateOf(false) }
    val parentRoleConfirmed = parentState.deviceRole == ParentDeviceRole.Parent
    val profileSavedVisible = parentState.localProfileName.isNotBlank() &&
        profileName.trim() == parentState.localProfileName
    val visibleRole = selectedRoleDraft
    val roleContainerColor = when (visibleRole) {
        ParentDeviceRole.Child -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
        ParentDeviceRole.Parent -> AppSafe.copy(alpha = 0.12f)
    }
    val roleBorderColor = when (visibleRole) {
        ParentDeviceRole.Child -> MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
        ParentDeviceRole.Parent -> AppSafe.copy(alpha = 0.28f)
    }

    LaunchedEffect(parentState.deviceRole) {
        selectedRoleDraft = parentState.deviceRole
    }
    LaunchedEffect(parentState.deviceRole, pendingParentManagementAction) {
        val action = pendingParentManagementAction
        selectedRoleDraft = if (action is ParentManagementPendingAction.ChangeRole) {
            action.role
        } else {
            parentState.deviceRole
        }
    }
    LaunchedEffect(parentState.localProfileName, profileSaveRequested, profileName) {
        if (
            profileSaveRequested &&
            profileName.trim().isNotBlank() &&
            parentState.localProfileName == profileName.trim()
        ) {
            profileSaveAcknowledged = true
            profileSaveRequested = false
            focusManager.clearFocus(force = true)
        }
    }

    OptionalSimpleCard(wrapInCard = wrapInCard) {
        if (wrapInCard) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(text.parentManagement, Modifier.weight(1f))
                StatusBadge(
                    label = if (parentState.paired) text.parentLinked else text.parentNotLinked,
                    status = if (parentState.paired) LimitStatus.Normal else LimitStatus.Warning,
                )
            }
        }

        if (visibleRole == ParentDeviceRole.Parent) {
            RemoteUnlockRequestList(
                requests = parentState.remoteUnlockRequests,
                text = text,
                onApproveExtraTime = { request, minutes ->
                    onApproveRemoteUnlockRequest(request.id, minutes, false)
                },
                onApproveUnlockToday = { request ->
                    onApproveRemoteUnlockRequest(request.id, 0, true)
                },
                onReject = { request ->
                    onRejectRemoteUnlockRequest(request.id)
                },
            )
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = roleContainerColor,
            border = BorderStroke(1.dp, roleBorderColor),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text.parentDeviceRole,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceButton(
                        label = text.childDeviceMode,
                        selected = selectedRoleDraft == ParentDeviceRole.Child,
                        onClick = {
                            selectedRoleDraft = ParentDeviceRole.Child
                            onPendingParentManagementActionChanged(
                                if (ParentDeviceRole.Child == parentState.deviceRole) {
                                    null
                                } else {
                                    ParentManagementPendingAction.ChangeRole(ParentDeviceRole.Child)
                                },
                            )
                        },
                    )
                    ChoiceButton(
                        label = text.parentDeviceMode,
                        selected = selectedRoleDraft == ParentDeviceRole.Parent,
                        onClick = {
                            selectedRoleDraft = ParentDeviceRole.Parent
                            onPendingParentManagementActionChanged(
                                if (ParentDeviceRole.Parent == parentState.deviceRole) {
                                    null
                                } else {
                                    ParentManagementPendingAction.ChangeRole(ParentDeviceRole.Parent)
                                },
                            )
                        },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = profileName,
                        onValueChange = {
                            profileName = it
                            profileSaveRequested = false
                            profileSaveAcknowledged = false
                        },
                        label = { Text(text.profileName) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = { focusManager.clearFocus(force = true) },
                        ),
                    )
                    Button(
                        onClick = {
                            focusManager.clearFocus(force = true)
                            profileSaveRequested = true
                            profileSaveAcknowledged = false
                            onParentProfileNameChanged(profileName)
                        },
                        enabled = profileName.isNotBlank(),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    ) {
                        Text(
                            if (profileSaveAcknowledged || profileSavedVisible) text.profileSaved else text.savePolicy,
                            maxLines = 1,
                        )
                    }
                }
                if (profileSaveAcknowledged || profileSavedVisible) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = AppSafe.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, AppSafe.copy(alpha = 0.26f)),
                    ) {
                        Text(
                            text.profileSaved,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppSafe,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                if (visibleRole == ParentDeviceRole.Child) {
                    ParentLinkDetailRow(
                        text.childPairingCode,
                        parentState.pairingCode.ifBlank { "-" },
                    )
                    LinkedParentDeviceList(
                        parents = parentState.linkedParentDevices,
                        text = text,
                        onUnlink = { parentUid ->
                            onPendingParentManagementActionChanged(
                                ParentManagementPendingAction.UnlinkLinkedParent(parentUid),
                            )
                        },
                    )
                    Button(
                        onClick = { showGeneratePairingCodePinDialog = true },
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text.generatePairingCode, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = if (parentAccountAuthState.recoverable) {
                            AppSafe.copy(alpha = 0.12f)
                        } else {
                            AppWarn.copy(alpha = 0.12f)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (parentAccountAuthState.recoverable) {
                                AppSafe.copy(alpha = 0.28f)
                            } else {
                                AppWarn.copy(alpha = 0.28f)
                            },
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = if (parentAccountAuthState.recoverable) {
                                    if (text.appLanguage == AppLanguage.Korean) {
                                        "부모 Google 계정 연결됨"
                                    } else {
                                        "Parent Google account connected"
                                    }
                                } else {
                                    if (text.appLanguage == AppLanguage.Korean) {
                                        "재설치 후에도 자녀 연결을 복구하려면 Google 로그인이 필요합니다."
                                    } else {
                                        "Google sign-in is required to restore child links after reinstalling."
                                    }
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (parentAccountAuthState.recoverable) AppSafe else AppWarn,
                            )
                            if (parentAccountAuthState.recoverable) {
                                Text(
                                    text = parentAccountAuthState.email
                                        .ifBlank { parentAccountAuthState.displayName }
                                        .ifBlank { parentAccountAuthState.uid },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            } else {
                                Button(
                                    onClick = onParentGoogleSignIn,
                                    enabled = parentRoleConfirmed && parentAccountAuthState.available,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                ) {
                                    Text(
                                        if (text.appLanguage == AppLanguage.Korean) {
                                            "Google 계정으로 로그인"
                                        } else {
                                            "Sign in with Google"
                                        },
                                    )
                                }
                                if (!parentRoleConfirmed) {
                                    Text(
                                        text = if (text.appLanguage == AppLanguage.Korean) {
                                            "먼저 부모 기기 모드를 관리 PIN으로 저장해 주세요."
                                        } else {
                                            "Save parent device mode with the admin PIN first."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = childPairingCodeInput,
                        onValueChange = { value ->
                            childPairingCodeInput = value
                                .uppercase()
                                .filter { char -> char.isLetterOrDigit() }
                                .removePrefix("SR")
                                .take(6)
                        },
                        label = { Text(text.childPairingCode) },
                        leadingIcon = {
                            Text(
                                "SR-",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                    )
                    Button(
                        onClick = {
                            pairingCodePendingRegistration = "SR-$childPairingCodeInput"
                        },
                        enabled = childPairingCodeInput.length == 6 &&
                            parentAccountAuthState.recoverable && parentRoleConfirmed,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text.registerChildDevice, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    ParentLinkDetailRow(
                        text.linkedChildDevices,
                        parentState.linkedChildDevices
                            .ifEmpty {
                                parentState.linkedChildPairingCodes.map { code ->
                                    LinkedChildDevice(
                                        childDeviceId = code,
                                        childDeviceName = code,
                                        pairingCode = code,
                                    )
                                }
                            }
                            .size
                            .toString(),
                    )
                    LinkedChildDeviceList(
                        children = parentState.linkedChildDevices,
                        text = text,
                        onUnlink = { childDeviceId ->
                            onPendingParentManagementActionChanged(
                                ParentManagementPendingAction.UnlinkLinkedChild(childDeviceId),
                            )
                        },
                    )
                }
            }
        }

        pendingParentManagementAction?.let { action ->
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.18f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.18f)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        action.parentManagementActionTitle(text),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(
                        onClick = {
                            onPendingParentManagementActionChanged(null)
                        },
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    ) {
                        Text(text.cancel, maxLines = 1)
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = {
                    onPendingParentManagementActionChanged(ParentManagementPendingAction.UnlinkParentAccount)
                },
                enabled = parentState.paired,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(text.unlinkParent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(
                onClick = onSyncParentDevice,
                enabled = parentState.paired,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(text.syncNow, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        OutlinedButton(
            onClick = { showDeleteAccountPinDialog = true },
            enabled = parentAccountAuthState.authenticated &&
                (parentState.paired || parentAccountAuthState.recoverable),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
        ) {
            Text(
                if (text.appLanguage == AppLanguage.Korean) {
                    "계정 및 클라우드 데이터 삭제"
                } else {
                    "Delete account and cloud data"
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        ParentLinkDetailRow(text.childDeviceId, parentState.childDeviceId.ifBlank { "-" })
        ParentLinkDetailRow(
            text.lastSync,
            if (parentState.lastSyncMillis > 0L) formatClockTime(parentState.lastSyncMillis) else "-",
        )
        ParentLinkDetailRow(
            if (text.appLanguage == AppLanguage.Korean) "요청 알림 상태" else "Request alerts",
            if (parentRequestNotificationReady) {
                if (text.appLanguage == AppLanguage.Korean) "사용 가능" else "Available"
            } else {
                text.parentNotificationIssueLabel(parentRequestNotificationIssue)
            },
        )
        ParentLinkDetailRow(
            if (text.appLanguage == AppLanguage.Korean) "마지막 요청 알림" else "Last request alert",
            if (notificationState.lastSuccessMillis > 0L) {
                formatClockTime(notificationState.lastSuccessMillis)
            } else {
                if (text.appLanguage == AppLanguage.Korean) "아직 없음" else "None yet"
            },
        )
        if (!parentRequestNotificationReady && notificationState.lastError.isNotBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.error.copy(alpha = 0.24f),
                ),
            ) {
                Text(
                    text = if (text.appLanguage == AppLanguage.Korean) {
                        "최근 요청 알림 문제: ${text.parentNotificationIssueLabel(notificationState.lastError)}"
                    } else {
                        "Recent request alert issue: ${text.parentNotificationIssueLabel(notificationState.lastError)}"
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }

    if (showGeneratePairingCodePinDialog) {
        AdminPinConfirmDialog(
            title = text.generatePairingCode,
            description = text.generatePairingCodePinInstruction(),
            confirmLabel = text.generatePairingCode,
            text = text,
            onDismiss = { showGeneratePairingCodePinDialog = false },
            onConfirm = { adminPin ->
                showGeneratePairingCodePinDialog = false
                onGenerateChildPairingCode(adminPin)
            },
        )
    }
    if (pairingCodePendingRegistration.isNotBlank()) {
        AdminPinConfirmDialog(
            title = text.registerChildDevice,
            description = text.registerChildDevicePinInstruction(),
            confirmLabel = text.registerChildDevice,
            text = text,
            onDismiss = { pairingCodePendingRegistration = "" },
            onConfirm = { adminPin ->
                val pairingCode = pairingCodePendingRegistration
                pairingCodePendingRegistration = ""
                onRegisterChildPairingCode(pairingCode, "", adminPin)
            },
        )
    }
    if (showDeleteAccountPinDialog) {
        AdminPinConfirmDialog(
            title = if (text.appLanguage == AppLanguage.Korean) {
                "계정 및 클라우드 데이터 삭제"
            } else {
                "Delete account and cloud data"
            },
            description = if (text.appLanguage == AppLanguage.Korean) {
                "이 기기의 Firebase 계정, 부모·자녀 연결, 요청, 명령과 알림 토큰을 삭제합니다. 삭제한 클라우드 데이터는 복구할 수 없습니다."
            } else {
                "This deletes the Firebase account, parent-child links, requests, commands, and notification tokens. Deleted cloud data cannot be restored."
            },
            confirmLabel = if (text.appLanguage == AppLanguage.Korean) "삭제" else "Delete",
            text = text,
            onDismiss = { showDeleteAccountPinDialog = false },
            onConfirm = { adminPin ->
                showDeleteAccountPinDialog = false
                onDeleteAccountAndCloudData(adminPin)
            },
        )
    }
}

private fun AppStrings.parentNotificationIssueLabel(issue: String): String {
    val korean = appLanguage == AppLanguage.Korean
    return when (issue.trim()) {
        "notification permission denied" ->
            if (korean) "알림 권한이 꺼져 있음" else "Notification permission is off"

        "app notifications disabled" ->
            if (korean) "앱 알림이 꺼져 있음" else "App notifications are off"

        "parent request notification channel disabled" ->
            if (korean) "부모 승인 요청 알림이 꺼져 있음" else "Parent request alerts are off"

        "notification manager rejected the request" ->
            if (korean) "알림 전송 실패" else "Notification delivery failed"

        "notification readiness check failed" ->
            if (korean) "알림 상태 확인 실패" else "Could not check notification status"

        "" -> if (korean) "확인 필요" else "Needs attention"
        else -> issue
    }
}

sealed class ParentManagementPendingAction {
    data class ChangeRole(val role: ParentDeviceRole) : ParentManagementPendingAction()
    object UnlinkParentAccount : ParentManagementPendingAction()
    data class UnlinkLinkedChild(val childDeviceId: String) : ParentManagementPendingAction()
    data class UnlinkLinkedParent(val parentUid: String) : ParentManagementPendingAction()
}

private fun ParentManagementPendingAction.parentManagementActionTitle(text: AppStrings): String {
    return when (this) {
        is ParentManagementPendingAction.ChangeRole -> "${text.parentDeviceRole} ${text.savePolicy}"
        ParentManagementPendingAction.UnlinkParentAccount,
        is ParentManagementPendingAction.UnlinkLinkedChild,
        is ParentManagementPendingAction.UnlinkLinkedParent -> text.unlinkParent
    }
}

@Composable
private fun RemoteUnlockRequestList(
    requests: List<RemoteUnlockRequest>,
    text: AppStrings,
    onApproveExtraTime: (RemoteUnlockRequest, Int) -> Unit,
    onApproveUnlockToday: (RemoteUnlockRequest) -> Unit,
    onReject: (RemoteUnlockRequest) -> Unit,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var showAllToday by rememberSaveable { mutableStateOf(false) }
    var showRecentHistory by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            now = System.currentTimeMillis()
        }
    }
    val grouped = groupRemoteRequestsForDisplay(requests, now)
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.30f),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text.remoteRequests,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                StatusBadge(
                    label = grouped.pending.size.toString(),
                    status = if (grouped.pending.isNotEmpty()) {
                        LimitStatus.Warning
                    } else {
                        LimitStatus.Normal
                    },
                )
            }
            if (grouped.pending.isEmpty() && grouped.today.isEmpty() && grouped.recentHistory.isEmpty()) {
                Text(
                    text.noRemoteRequests,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                if (grouped.pending.isNotEmpty()) {
                    Text(
                        if (korean) "처리 대기" else "Pending",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    grouped.pending.forEach { request ->
                        RemoteUnlockRequestRow(request, now, text, onApproveExtraTime, onApproveUnlockToday, onReject)
                    }
                }
                if (grouped.today.isNotEmpty()) {
                    Text(
                        if (korean) "오늘 요청·처리" else "Today's requests",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    (if (showAllToday) grouped.today else grouped.today.take(3)).forEach { request ->
                        RemoteUnlockRequestRow(request, now, text, onApproveExtraTime, onApproveUnlockToday, onReject)
                    }
                    if (grouped.today.size > 3) {
                        TextButton(onClick = { showAllToday = !showAllToday }) {
                            Text(if (showAllToday) {
                                if (korean) "오늘 이력 접기" else "Show less"
                            } else {
                                if (korean) "오늘 이력 ${grouped.today.size}건 보기" else "View all ${grouped.today.size} today"
                            })
                        }
                    }
                }
                if (grouped.recentHistory.isNotEmpty()) {
                    TextButton(onClick = { showRecentHistory = !showRecentHistory }) {
                        Text(if (showRecentHistory) {
                            if (korean) "지난 이력 접기" else "Hide recent history"
                        } else {
                            if (korean) "지난 7일 이력 ${grouped.recentHistory.size}건" else "Last 7 days · ${grouped.recentHistory.size}"
                        })
                    }
                    if (showRecentHistory) {
                        grouped.recentHistory.forEach { request ->
                            RemoteUnlockRequestRow(request, now, text, onApproveExtraTime, onApproveUnlockToday, onReject)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoteUnlockRequestRow(
    request: RemoteUnlockRequest,
    now: Long,
    text: AppStrings,
    onApproveExtraTime: (RemoteUnlockRequest, Int) -> Unit,
    onApproveUnlockToday: (RemoteUnlockRequest) -> Unit,
    onReject: (RemoteUnlockRequest) -> Unit,
) {
    var selectedMinutes by remember(request.id, request.requestedMinutes) {
        mutableStateOf(request.requestedMinutes.coerceAtLeast(1))
    }
    var showExtraTimePicker by remember(request.id) { mutableStateOf(false) }
    val expired = request.expiresAtMillis < now && request.status == RemoteUnlockRequestStatus.Pending
    val active = request.status == RemoteUnlockRequestStatus.Pending && !expired
    val status = when {
        active -> LimitStatus.Warning
        request.status == RemoteUnlockRequestStatus.Approved -> LimitStatus.Normal
        else -> LimitStatus.Exceeded
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.70f)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        request.remoteRequestTitle(text),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        request.remoteRequestDetail(text),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                StatusBadge(
                    label = if (expired) RemoteUnlockRequestStatus.Expired.name.lowercase() else request.status.name.lowercase(),
                    status = status,
                )
            }
            if (active) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text.remoteExtraTime,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Surface(
                        onClick = { showExtraTimePicker = true },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.50f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                    ) {
                        Text(
                            formatLimitMinutesLabel(selectedMinutes),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { onApproveExtraTime(request, selectedMinutes) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text.remoteAddTime, maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { onApproveUnlockToday(request) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text.remoteUnlockToday, maxLines = 1)
                    }
                }
                OutlinedButton(
                    onClick = { onReject(request) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.65f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(text.reject, maxLines = 1)
                }
            }
        }
    }
    if (showExtraTimePicker) {
        DurationPickerSheet(
            title = text.remoteExtraTime,
            valueMinutes = selectedMinutes,
            minMinutes = 1,
            maxMinutes = REMOTE_PARENT_MAX_EXTRA_MINUTES,
            includeMaxPreset = false,
            text = text,
            onDismiss = { showExtraTimePicker = false },
            onApply = { minutes ->
                selectedMinutes = minutes.coerceIn(1, REMOTE_PARENT_MAX_EXTRA_MINUTES)
                showExtraTimePicker = false
            },
        )
    }
}

private fun RemoteUnlockRequest.remoteRequestTitle(text: AppStrings): String {
    val target = if (blockReason == RemoteRequestBlockReason.DailyLimit) {
        text.remoteDailyLimit
    } else {
        targetAppName.ifBlank {
            targetGroupName.ifBlank {
                scheduleName.ifBlank {
                    targetPackageName
                }
            }
        }
    }
    return "${blockReason.remoteLabel(text)} - $target"
}

private fun RemoteUnlockRequest.remoteRequestDetail(text: AppStrings): String {
    val limitText = limitMillis?.let { limit -> " / ${formatDuration(limit)}" }.orEmpty()
    val extraText = alreadyGrantedExtraMinutes
        .takeIf { minutes -> minutes > 0 }
        ?.let { minutes -> ", +${formatLimitMinutesLabel(minutes)}" }
        .orEmpty()
    val unlockText = if (unlockedForToday) ", ${text.remoteUnlockToday}" else ""
    val messageText = childMessage.takeIf { message -> message.isNotBlank() }?.let { message -> "\n$message" }.orEmpty()
    return "${childDeviceName.ifBlank { childDeviceId }} - ${formatDuration(usedMillis)}$limitText - ${text.remoteAddTime} ${formatLimitMinutesLabel(requestedMinutes)}$extraText$unlockText$messageText"
}

private fun RemoteRequestBlockReason.remoteLabel(text: AppStrings): String {
    return when (this) {
        RemoteRequestBlockReason.DailyLimit -> text.remoteDailyLimit
        RemoteRequestBlockReason.AppGroupLimit -> text.appGroups
        RemoteRequestBlockReason.AppLimit -> text.appLimits
        RemoteRequestBlockReason.ScheduleBlock -> text.scheduleBlocking
        RemoteRequestBlockReason.AllowOnlyMode -> text.allowOnlyMode
    }
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
private fun LinkedChildDeviceList(
    children: List<LinkedChildDevice>,
    text: AppStrings,
    onUnlink: (String) -> Unit,
) {
    if (children.isEmpty()) {
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        children.take(5).forEach { child ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            child.childDeviceName.ifBlank { child.childDeviceId },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            child.childDeviceId.take(8).ifBlank { child.pairingCode },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    StatusBadge(label = text.parentLinked, status = LimitStatus.Normal)
                    TextButton(
                        onClick = { onUnlink(child.childDeviceId) },
                    ) {
                        Text(text.unlinkParent, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkedParentDeviceList(
    parents: List<LinkedParentDevice>,
    text: AppStrings,
    onUnlink: (String) -> Unit,
) {
    if (parents.isEmpty()) {
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text.linkedParentDevices,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        parents.take(5).forEach { parent ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            parent.parentDisplayName.ifBlank { "Parent device" },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            parent.parentUid.take(8),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    StatusBadge(label = text.parentLinked, status = LimitStatus.Normal)
                    TextButton(
                        onClick = { onUnlink(parent.parentUid) },
                    ) {
                        Text(text.unlinkParent, maxLines = 1)
                    }
                }
            }
        }
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
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun AlwaysAllowedAppsSection(
    installedApps: List<InstalledAppInfo>,
    allowedAppPackages: Set<String>,
    allRestrictionsExemptPackages: Set<String> = emptySet(),
    temporaryAllowedApps: List<TemporaryAllowedAppSummary> = emptyList(),
    text: AppStrings,
    onAllowedAppsChanged: (Set<String>) -> Unit,
) {
    SimpleCard {
        AlwaysAllowedAppsContent(
            installedApps = installedApps,
            allowedAppPackages = allowedAppPackages,
            allRestrictionsExemptPackages = allRestrictionsExemptPackages,
            temporaryAllowedApps = temporaryAllowedApps,
            text = text,
            onAllowedAppsChanged = onAllowedAppsChanged,
        )
    }
}

@Composable
fun ColumnScope.AlwaysAllowedAppsContent(
    installedApps: List<InstalledAppInfo>,
    allowedAppPackages: Set<String>,
    allRestrictionsExemptPackages: Set<String> = emptySet(),
    temporaryAllowedApps: List<TemporaryAllowedAppSummary> = emptyList(),
    text: AppStrings,
    onAllowedAppsChanged: (Set<String>) -> Unit,
    preventAdditions: Boolean = false,
    appLimitMinutesByPackage: Map<String, Int> = emptyMap(),
    appLimitActiveDaysByPackage: Map<String, Set<Int>> = emptyMap(),
) {
    val directlyUnrestrictedPackages =
        allRestrictionsExemptPackages - SafetyGate.neverBlockPackages
    val unrestrictedPackages =
        SafetyGate.expandedUserAllowedPackages(directlyUnrestrictedPackages)
    val userAllowedPackages =
        (allowedAppPackages - SafetyGate.neverBlockPackages) - unrestrictedPackages
    val temporaryAllowanceByPackage = temporaryAllowedApps.associateBy { allowance -> allowance.packageName }
    val visibleInstalledPackages = installedApps
        .map { app -> app.packageName }
        .filterNot { packageName -> packageName in SafetyGate.neverBlockPackages }
        .toSet()
    val effectiveUserAllowedPackages = (
        userAllowedPackages + temporaryAllowanceByPackage.keys + unrestrictedPackages
    ) intersect visibleInstalledPackages
    var listsExpanded by remember { mutableStateOf(false) }
    var appSearchQuery by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionTitle(
            if (text.appLanguage == AppLanguage.Korean) {
                "허용앱만 모드의 허용 앱"
            } else {
                "Apps allowed in allow-only mode"
            },
            Modifier.weight(1f),
        )
        StatusBadge(text.allowedAppCount(effectiveUserAllowedPackages.size), LimitStatus.Normal)
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
        if (text.appLanguage == AppLanguage.Korean) {
            "선택 앱과 제한 없음 앱이 실행됩니다. 시간 제한은 유지됩니다."
        } else {
            "Selected and unrestricted apps can open. Time limits still apply."
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (preventAdditions) {
        Text(
            text = if (text.appLanguage == AppLanguage.Korean) {
                "고행 모드 중에는 앱을 추가할 수 없습니다."
            } else {
                "Apps cannot be added while hardship mode is active."
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = AppOver,
        )
    }

    if (!listsExpanded) {
        return
    }

    Text(
        if (text.appLanguage == AppLanguage.Korean) "실행 허용 앱" else "Allowed to open",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
    SearchBox(
        value = appSearchQuery,
        placeholder = text.searchApps,
        onValueChange = { query -> appSearchQuery = query },
    )
    val sortedApps = remember(
        installedApps,
        appSearchQuery,
        effectiveUserAllowedPackages,
    ) {
        installedApps
            .filterNot { app -> app.packageName in SafetyGate.neverBlockPackages }
            .filter { app -> app.matchesAppSearch(appSearchQuery) }
            .sortedWith(
                compareByDescending<InstalledAppInfo> { app ->
                    app.packageName in effectiveUserAllowedPackages
                }.thenBy { app -> app.appName.lowercase() },
            )
    }
    if (sortedApps.isEmpty()) {
        Text(text.noSelectableApps, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        ContainedLazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp),
            resetKey = appSearchQuery,
        ) {
            items(sortedApps, key = { app -> app.packageName }) { app ->
                val unrestricted = app.packageName in unrestrictedPackages
                val directlyUnrestricted = app.packageName in directlyUnrestrictedPackages
                val linkedFamily = SafetyGate.linkedAppFamily(
                    targetPackageName = app.packageName,
                    directlyAllowedPackages = directlyUnrestrictedPackages,
                )
                val permanentlyAllowed = app.packageName in userAllowedPackages
                val temporaryAllowance = temporaryAllowanceByPackage[app.packageName]
                val selected = unrestricted || permanentlyAllowed || temporaryAllowance != null
                UserAllowedAppRow(
                    app = app,
                    selected = selected,
                    text = text,
                    enabled = !unrestricted &&
                        temporaryAllowance == null &&
                        (!preventAdditions || permanentlyAllowed),
                    statusLabel = when {
                        directlyUnrestricted -> if (text.appLanguage == AppLanguage.Korean) {
                            "제한 없음"
                        } else {
                            "Unrestricted"
                        }
                        linkedFamily != null -> linkedUnrestrictedLabel(linkedFamily, text)
                        permanentlyAllowed -> appLimitMinutesByPackage[app.packageName]
                            ?.let { minutes ->
                                val limitLabel = if (minutes == 0) {
                                    text.zeroMinuteBlockLabel()
                                } else {
                                    formatLimitMinutesLabel(minutes)
                                }
                                val days = appLimitActiveDaysByPackage[app.packageName]
                                    ?: (1..7).toSet()
                                "${text.allowed} · $limitLabel · ${scheduleDaysSummary(days, text)}"
                            }
                            ?: text.allowed
                        temporaryAllowance != null -> temporaryAllowanceStatusLabel(temporaryAllowance, text)
                        else -> text.allow
                    },
                    onToggle = {
                        if (!unrestricted) {
                            val nextPackages = if (permanentlyAllowed) {
                                userAllowedPackages - app.packageName
                            } else {
                                userAllowedPackages + app.packageName
                            }
                            onAllowedAppsChanged(nextPackages)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun PolicyExceptionAppsCard(
    settings: UsagePolicySettings,
    installedApps: List<InstalledAppInfo>,
    exemptPackages: Set<String>,
    activeHardshipPolicyKeys: Set<HardshipPolicyKey>,
    text: AppStrings,
    onExemptPackagesChanged: (Set<String>) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var pendingAddition by remember { mutableStateOf<InstalledAppInfo?>(null) }
    var appSearchQuery by remember { mutableStateOf("") }
    val cleanExemptPackages = exemptPackages - SafetyGate.neverBlockPackages
    val effectiveExemptPackages = SafetyGate.expandedUserAllowedPackages(cleanExemptPackages)
    val effectiveVisibleExemptCount = installedApps.count { app ->
        app.packageName in effectiveExemptPackages &&
            app.packageName !in SafetyGate.neverBlockPackages
    }
    val additionsLocked = activeHardshipPolicyKeys.any { key ->
        settings.hardshipLevelFor(key) == HardshipLevel.Level3
    }
    val conflictingLimitedPackages = remember(settings, cleanExemptPackages) {
        val expandedExemptPackages =
            SafetyGate.expandedUserAllowedPackages(cleanExemptPackages)
        val limitedByApp = settings.appLimitMap().keys
        val limitedByGroup = settings.normalizedAppGroups()
            .flatMap { group -> group.packageNames }
            .toSet()
        expandedExemptPackages intersect (limitedByApp + limitedByGroup)
    }

    ScreenRestCard(
        contentPadding = PaddingValues(horizontal = ScreenRestTheme.spacing.sm, vertical = ScreenRestTheme.spacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = ScreenRestTheme.sizes.minimumTouchTarget),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.xs),
        ) {
            Text(
                if (text.appLanguage == AppLanguage.Korean) "제한 없는 앱" else "Unrestricted apps",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            StatusBadge(
                if (text.appLanguage == AppLanguage.Korean) {
                    "제한 없음 $effectiveVisibleExemptCount"
                } else {
                    "$effectiveVisibleExemptCount unrestricted"
                },
                LimitStatus.Normal,
            )
            SectionExpandButton(
                expanded = expanded,
                text = text,
                onClick = { expanded = !expanded },
            )
        }

        if (expanded) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (text.appLanguage == AppLanguage.Korean) "시스템 자동 허용" else "Automatically allowed",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        StatusBadge(
                            if (text.appLanguage == AppLanguage.Korean) "자동 관리" else "Managed",
                            LimitStatus.Normal,
                        )
                    }
                    Text(
                        if (text.appLanguage == AppLanguage.Korean) {
                            "설정·키보드·사진 선택 등 차단하면 안 되는 시스템 기능"
                        } else {
                            "Settings, keyboard, photo picker, and other required system functions"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                if (text.appLanguage == AppLanguage.Korean) {
                    "전화·메시지·갤러리·카메라를 제한 없이 사용하면 필요한 보조 앱도 ‘연동 제한 없음’으로 표시됩니다. 대표 앱을 해제하면 연동 허용도 함께 해제됩니다."
                } else {
                    "Phone, Messages, Gallery, and Camera companion apps are marked as linked unrestricted. Removing the main app removes its linked allowance too."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                if (text.appLanguage == AppLanguage.Korean) "제한 없음 앱" else "Excluded from all restrictions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (text.appLanguage == AppLanguage.Korean) {
                    "모든 차단 제외 · 통계에는 기록"
                } else {
                    "Bypasses all blocking · remains in statistics"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (additionsLocked) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = AppOver.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, AppOver.copy(alpha = 0.32f)),
                ) {
                    Text(
                        if (text.appLanguage == AppLanguage.Korean) {
                            "고행 3단계 중에는 앱을 추가할 수 없습니다."
                        } else {
                            "Apps cannot be added during hardship level 3."
                        },
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = AppOver,
                    )
                }
            }
            if (conflictingLimitedPackages.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.38f)),
                ) {
                    Text(
                        if (text.appLanguage == AppLanguage.Korean) {
                            "제한 없음 ${conflictingLimitedPackages.size}개 · 앱별·그룹 제한 미적용"
                        } else {
                            "${conflictingLimitedPackages.size} unrestricted · app and group limits ignored"
                        },
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }

            SearchBox(
                value = appSearchQuery,
                placeholder = if (text.appLanguage == AppLanguage.Korean) {
                    "제한 없는 앱 검색"
                } else {
                    "Search exception apps"
                },
                onValueChange = { query -> appSearchQuery = query },
            )
            val selectableApps = remember(installedApps, appSearchQuery) {
                installedApps
                    .filterNot { app -> app.packageName in SafetyGate.neverBlockPackages }
                    .filter { app -> app.matchesAppSearch(appSearchQuery) }
                    .sortedBy { app -> app.appName.lowercase() }
            }
            if (selectableApps.isEmpty()) {
                Text(
                    if (text.appLanguage == AppLanguage.Korean) {
                        "검색 결과가 없습니다."
                    } else {
                        "No apps match your search."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                ContainedLazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    resetKey = appSearchQuery to selectableApps.map { app -> app.packageName },
                ) {
                    items(selectableApps, key = { app -> app.packageName }) { app ->
                        val directlySelected = app.packageName in cleanExemptPackages
                        val linkedFamily = SafetyGate.linkedAppFamily(
                            targetPackageName = app.packageName,
                            directlyAllowedPackages = cleanExemptPackages,
                        )
                        val selected = app.packageName in effectiveExemptPackages
                        UserAllowedAppRow(
                            app = app,
                            selected = selected,
                            text = text,
                            enabled = linkedFamily == null && (directlySelected || !additionsLocked),
                            statusLabel = when {
                                directlySelected && text.appLanguage == AppLanguage.Korean -> "제한 없음"
                                directlySelected -> "Unrestricted"
                                linkedFamily != null -> linkedUnrestrictedLabel(linkedFamily, text)
                                else -> text.allow
                            },
                            onToggle = {
                                if (directlySelected) {
                                    onExemptPackagesChanged(cleanExemptPackages - app.packageName)
                                } else {
                                    pendingAddition = app
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    pendingAddition?.let { app ->
        Dialog(onDismissRequest = { pendingAddition = null }) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        if (text.appLanguage == AppLanguage.Korean) {
                            "이 앱을 제한 없이 사용할까요?"
                        } else {
                            "Exclude from all restrictions?"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (text.appLanguage == AppLanguage.Korean) {
                            "${app.appName}은 요일별·앱 그룹·앱별·스케줄·허용앱만 제한과 고행 차단을 모두 우회합니다. 사용 기록은 통계에 계속 표시됩니다."
                        } else {
                            "${app.appName} will bypass daily, group, app, schedule, allow-only, and hardship blocking. Its usage will remain visible in statistics."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick = { pendingAddition = null },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(text.cancel)
                        }
                        Button(
                            onClick = {
                                onExemptPackagesChanged(cleanExemptPackages + app.packageName)
                                pendingAddition = null
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = AppOver),
                        ) {
                            Text(
                                if (text.appLanguage == AppLanguage.Korean) {
                                    "제한 없이 사용"
                                } else {
                                    "Add exemption"
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun linkedUnrestrictedLabel(family: LinkedAppFamily, text: AppStrings): String {
    return if (text.appLanguage == AppLanguage.Korean) {
        when (family) {
            LinkedAppFamily.Phone -> "전화 연동 · 제한 없음"
            LinkedAppFamily.Messaging -> "메시지 연동 · 제한 없음"
            LinkedAppFamily.Gallery -> "갤러리 연동 · 제한 없음"
            LinkedAppFamily.Camera -> "카메라 연동 · 제한 없음"
        }
    } else {
        when (family) {
            LinkedAppFamily.Phone -> "Linked to Phone · Unrestricted"
            LinkedAppFamily.Messaging -> "Linked to Messages · Unrestricted"
            LinkedAppFamily.Gallery -> "Linked to Gallery · Unrestricted"
            LinkedAppFamily.Camera -> "Linked to Camera · Unrestricted"
        }
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
            allowance.unlockedForToday || allowance.extraMinutes > 0 || allowance.temporaryRemainingMinutes() > 0
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
                .filter { (_, allowance) ->
                    allowance.unlockedForToday || allowance.extraMinutes > 0 || allowance.temporaryRemainingMinutes() > 0
                }
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
                    val temporaryRemaining = allowance.temporaryRemainingMinutes()
                    val remaining = temporaryRemaining.takeIf { minutes -> minutes > 0 }
                        ?: remainingTemporaryPackageMinutes(
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
        this == BlockDecision.WouldBlockImmediate ||
        this == BlockDecision.WouldBlockGroupLimit ||
        this == BlockDecision.WouldBlockAppLimit
}

@Composable
fun EventLogSection(
    eventLog: List<EventLogEntry>,
    text: AppStrings,
    onClearEventLog: () -> Unit,
    wrapInCard: Boolean = true,
) {
    OptionalSimpleCard(wrapInCard = wrapInCard) {
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
    lastUpdatedAtMillis: Long,
    todayUsage: List<AppUsageInfo>,
    policySummary: PolicySummary,
    text: AppStrings,
    onRefreshUsageStats: () -> Unit,
) {
    SimpleCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(text.todayUsage, Modifier.weight(1f))
            Text(
                if (usageAccessChecking) text.updating else usageLastUpdatedLabel(lastUpdatedAtMillis, text),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(8.dp))
            if (hasUsageAccess) {
                CircleTextButton(label = "R", onClick = onRefreshUsageStats)
            }
        }

        if (usageAccessChecking && hasUsageAccess) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text.usageAccessChecking,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        } else if (todayUsage.isNotEmpty()) {
            val topUsage = todayUsage.first()
            val remainingUsage = todayUsage.drop(1)
            val totalUsageMillis = todayUsage.sumOf { appUsage -> appUsage.totalTimeMillis }.coerceAtLeast(1L)
            TodayUsageHero(
                appUsage = topUsage,
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
        } else if (todayUsage.isEmpty()) {
            Text(text.noUsageRecorded)
        }
    }
}

private fun usageLastUpdatedLabel(lastUpdatedAtMillis: Long, text: AppStrings): String {
    return if (lastUpdatedAtMillis > 0L) {
        text.lastUpdated(formatClockTime(lastUpdatedAtMillis))
    } else {
        text.notUpdatedYet
    }
}

@Composable
fun TodayUsageHero(
    appUsage: AppUsageInfo,
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
    effectiveAppSummaries.firstOrNull { summary -> summary.packageName == packageName }
        ?.let { summary -> return summary.status }
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
    var showPolicyDetails by remember { mutableStateOf(false) }
    SimpleCard {
        SectionTitle(text.policySummary)
        CompactPolicyOverview(summary = summary, text = text)
        EffectiveAppResultsSection(summary = summary, text = text)
        OutlinedButton(
            onClick = { showPolicyDetails = !showPolicyDetails },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (showPolicyDetails) {
                    if (text.appLanguage == AppLanguage.Korean) "정책 상세 닫기" else "Hide policy details"
                } else {
                    if (text.appLanguage == AppLanguage.Korean) "정책 상세 보기" else "View policy details"
                },
            )
        }
        if (showPolicyDetails) {
            HardshipPolicySummary(summary = summary, text = text)
            EffectiveAccessScopeSummary(summary = summary, text = text)
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
                        temporaryAllowedApps = summary.temporaryAllowedApps,
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
private fun CompactPolicyOverview(
    summary: PolicySummary,
    text: AppStrings,
) {
    val currentMode = when {
        summary.activeScheduleSummary != null -> summary.activeScheduleSummary.name
        summary.allowOnlyModeEnabled ->
            if (text.appLanguage == AppLanguage.Korean) "허용앱만" else "Allow-only"
        else ->
            if (text.appLanguage == AppLanguage.Korean) "일반" else "Normal"
    }
    val modeType = when {
        summary.activeScheduleSummary != null ->
            if (text.appLanguage == AppLanguage.Korean) "스케줄" else "Schedule"
        summary.allowOnlyModeEnabled ->
            if (text.appLanguage == AppLanguage.Korean) "실행 범위" else "Access"
        else ->
            if (text.appLanguage == AppLanguage.Korean) "현재 상태" else "Current"
    }
    val timeLimitCount =
        (if (summary.dailyPolicyEnabled && summary.totalLimitEnabled) 1 else 0) +
            summary.groupSummaries.count { group -> group.limitConfigured } +
            summary.appLimitSummaries.size
    val summaryLine = if (text.appLanguage == AppLanguage.Korean) {
        "시간 제한 $timeLimitCount · 제한 없음 ${summary.allRestrictionsExemptAppCount} · 고행 ${summary.hardshipItems.size}"
    } else {
        "Time limits $timeLimitCount · Unrestricted ${summary.allRestrictionsExemptAppCount} · Hardship ${summary.hardshipItems.size}"
    }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    currentMode,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    summaryLine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (
                    summary.activeScheduleSummary != null &&
                    summary.allowOnlyModeEnabled
                ) {
                    Text(
                        if (text.appLanguage == AppLanguage.Korean) {
                            "종료 후 허용앱만 자동 재개"
                        } else {
                            "Allow-only resumes afterward"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            StatusBadge(
                label = modeType,
                status = if (
                    summary.activeScheduleSummary != null ||
                    summary.allowOnlyModeEnabled
                ) {
                    LimitStatus.Warning
                } else {
                    LimitStatus.Normal
                },
            )
        }
    }
}

private enum class EffectiveAppResultFilter {
    All,
    Blocked,
    Limited,
    Temporary,
    Allowed,
    Exempt,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EffectiveAppResultsSection(
    summary: PolicySummary,
    text: AppStrings,
) {
    val apps = summary.effectiveAppSummaries
    if (apps.isEmpty()) return

    var showAllApps by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf(EffectiveAppResultFilter.All) }
    var expandedPackageName by remember { mutableStateOf<String?>(null) }
    fun openAppResults(filter: EffectiveAppResultFilter) {
        selectedFilter = filter
        expandedPackageName = null
        showAllApps = true
    }
    val temporaryPackages = remember(summary.temporaryAllowedApps) {
        summary.temporaryAllowedApps.map { allowance -> allowance.packageName }.toSet()
    }
    fun EffectiveAppPolicySummary.isBlocked(): Boolean {
        return status == LimitStatus.Exceeded ||
            access == EffectiveAppAccess.BlockedBySchedule ||
            access == EffectiveAppAccess.BlockedByAllowOnly ||
            access == EffectiveAppAccess.BlockedByParent
    }
    fun EffectiveAppPolicySummary.isExempt(): Boolean {
        return access == EffectiveAppAccess.AllRestrictionsExempt
    }
    fun EffectiveAppPolicySummary.isTemporary(): Boolean {
        return !isBlocked() && !isExempt() && packageName in temporaryPackages
    }
    fun EffectiveAppPolicySummary.isLimited(): Boolean {
        return !isBlocked() &&
            !isExempt() &&
            !isTemporary() &&
            limitingPolicy != EffectiveTimeLimiter.None
    }
    fun EffectiveAppPolicySummary.isAvailable(): Boolean {
        return !isBlocked() && !isExempt() && !isTemporary() && !isLimited()
    }
    val blockedCount = apps.count { app -> app.isBlocked() }
    val limitedCount = apps.count { app -> app.isLimited() }
    val temporaryCount = apps.count { app -> app.isTemporary() }
    val exemptCount = apps.count { app -> app.isExempt() }
    val availableCount = apps.count { app -> app.isAvailable() }

    Text(
        if (text.appLanguage == AppLanguage.Korean) "앱 상태" else "App status",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EffectiveResultCountChip(
            label = if (text.appLanguage == AppLanguage.Korean) {
                "전체 ${apps.size}"
            } else {
                "All ${apps.size}"
            },
            status = LimitStatus.Normal,
            onClick = { openAppResults(EffectiveAppResultFilter.All) },
        )
        EffectiveResultCountChip(
            label = if (text.appLanguage == AppLanguage.Korean) {
                "차단 $blockedCount"
            } else {
                "Blocked $blockedCount"
            },
            status = if (blockedCount > 0) LimitStatus.Exceeded else LimitStatus.Normal,
            onClick = { openAppResults(EffectiveAppResultFilter.Blocked) },
        )
        EffectiveResultCountChip(
            label = if (text.appLanguage == AppLanguage.Korean) {
                "시간 제한 $limitedCount"
            } else {
                "Time limited $limitedCount"
            },
            status = if (limitedCount > 0) LimitStatus.Warning else LimitStatus.Normal,
            onClick = { openAppResults(EffectiveAppResultFilter.Limited) },
        )
        EffectiveResultCountChip(
            label = if (text.appLanguage == AppLanguage.Korean) {
                "일시 허용 $temporaryCount"
            } else {
                "Temporary $temporaryCount"
            },
            status = LimitStatus.Normal,
            onClick = { openAppResults(EffectiveAppResultFilter.Temporary) },
        )
        EffectiveResultCountChip(
            label = if (text.appLanguage == AppLanguage.Korean) {
                "제한 없음 $exemptCount"
            } else {
                "Exempt $exemptCount"
            },
            status = if (exemptCount > 0) LimitStatus.Warning else LimitStatus.Normal,
            onClick = { openAppResults(EffectiveAppResultFilter.Exempt) },
        )
        EffectiveResultCountChip(
            label = if (text.appLanguage == AppLanguage.Korean) {
                "사용 가능 $availableCount"
            } else {
                "Available $availableCount"
            },
            status = LimitStatus.Normal,
            onClick = { openAppResults(EffectiveAppResultFilter.Allowed) },
        )
    }

    if (showAllApps) {
        val filteredApps = apps.filter { app ->
            when (selectedFilter) {
                EffectiveAppResultFilter.All -> true
                EffectiveAppResultFilter.Blocked -> app.isBlocked()
                EffectiveAppResultFilter.Limited -> app.isLimited()
                EffectiveAppResultFilter.Temporary -> app.isTemporary()
                EffectiveAppResultFilter.Allowed -> app.isAvailable()
                EffectiveAppResultFilter.Exempt -> app.isExempt()
            }
        }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showAllApps = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SectionTitle(
                    if (text.appLanguage == AppLanguage.Korean) {
                        "앱별 실제 적용 결과"
                    } else {
                        "Effective result by app"
                    },
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EffectiveAppResultFilter.values().forEach { filter ->
                        AppFilterChip(
                            label = filter.label(text),
                            selected = selectedFilter == filter,
                        ) {
                            selectedFilter = filter
                            expandedPackageName = null
                        }
                    }
                }
                Text(
                    if (text.appLanguage == AppLanguage.Korean) {
                        "${filteredApps.size}개 앱"
                    } else {
                        "${filteredApps.size} apps"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (filteredApps.isEmpty()) {
                    Text(
                        if (text.appLanguage == AppLanguage.Korean) {
                            "이 상태에 해당하는 앱이 없습니다."
                        } else {
                            "No apps match this status."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    ContainedLazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 560.dp),
                        resetKey = selectedFilter to filteredApps.map { app -> app.packageName },
                    ) {
                        items(filteredApps, key = { app -> app.packageName }) { app ->
                            val expanded = expandedPackageName == app.packageName
                            EffectiveAppPolicyRow(
                                summary = app,
                                text = text,
                                temporaryAllowed = app.packageName in temporaryPackages,
                                expanded = expanded,
                                onClick = {
                                    expandedPackageName = if (expanded) null else app.packageName
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EffectiveResultCountChip(
    label: String,
    status: LimitStatus,
    onClick: () -> Unit,
) {
    val color = status.semanticColor()
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.26f)),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
        )
    }
}

private fun EffectiveAppResultFilter.label(text: AppStrings): String {
    return when (this) {
        EffectiveAppResultFilter.All ->
            if (text.appLanguage == AppLanguage.Korean) "전체" else "All"
        EffectiveAppResultFilter.Blocked ->
            if (text.appLanguage == AppLanguage.Korean) "차단" else "Blocked"
        EffectiveAppResultFilter.Limited ->
            if (text.appLanguage == AppLanguage.Korean) "시간 제한" else "Time limited"
        EffectiveAppResultFilter.Temporary ->
            if (text.appLanguage == AppLanguage.Korean) "일시 허용" else "Temporary"
        EffectiveAppResultFilter.Allowed ->
            if (text.appLanguage == AppLanguage.Korean) "사용 가능" else "Allowed"
        EffectiveAppResultFilter.Exempt ->
            if (text.appLanguage == AppLanguage.Korean) "제한 없음" else "Exempt"
    }
}

@Composable
private fun EffectiveAccessScopeSummary(
    summary: PolicySummary,
    text: AppStrings,
) {
    val scopeLabel = when {
        summary.activeScheduleSummary != null -> if (text.appLanguage == AppLanguage.Korean) {
            "스케줄 · ${summary.activeScheduleSummary.name}"
        } else {
            "Schedule · ${summary.activeScheduleSummary.name}"
        }
        summary.allowOnlyModeEnabled -> if (text.appLanguage == AppLanguage.Korean) {
            "허용앱만 모드"
        } else {
            "Allow-only mode"
        }
        else -> if (text.appLanguage == AppLanguage.Korean) "일반 상태" else "Normal access"
    }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.40f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SafetyStatusRow(
                title = if (text.appLanguage == AppLanguage.Korean) {
                    "현재 앱 사용 범위"
                } else {
                    "Current app access range"
                },
                supportingText = if (
                    summary.activeScheduleSummary != null &&
                    summary.allowOnlyModeEnabled
                ) {
                    if (text.appLanguage == AppLanguage.Korean) {
                        "스케줄 종료 후 허용앱만 모드가 자동으로 다시 적용됩니다"
                    } else {
                        "Allow-only mode resumes automatically when the schedule ends"
                    }
                } else {
                    if (text.appLanguage == AppLanguage.Korean) {
                        "현재 실행 가능 여부를 결정하는 범위 정책입니다"
                    } else {
                        "This range policy currently determines which apps may open"
                    }
                },
                statusLabel = scopeLabel,
                status = if (
                    summary.activeScheduleSummary != null ||
                    summary.allowOnlyModeEnabled
                ) {
                    LimitStatus.Warning
                } else {
                    LimitStatus.Normal
                },
            )
            SafetyStatusRow(
                title = if (text.appLanguage == AppLanguage.Korean) {
                    "제한 없음 앱"
                } else {
                    "Excluded from all restrictions"
                },
                supportingText = if (text.appLanguage == AppLanguage.Korean) {
                    "통계에는 기록되지만 차단 사용량에는 합산되지 않습니다"
                } else {
                    "Tracked in statistics but excluded from enforcement usage"
                },
                statusLabel = if (text.appLanguage == AppLanguage.Korean) {
                    "${summary.allRestrictionsExemptAppCount}개"
                } else {
                    "${summary.allRestrictionsExemptAppCount} apps"
                },
                status = if (summary.allRestrictionsExemptAppCount > 0) {
                    LimitStatus.Warning
                } else {
                    LimitStatus.Normal
                },
            )
        }
    }
}

@Composable
private fun EffectiveAppPolicyRow(
    summary: EffectiveAppPolicySummary,
    text: AppStrings,
    temporaryAllowed: Boolean = false,
    expanded: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val accessLabel = when (summary.access) {
        EffectiveAppAccess.RequiredAllowed ->
            if (text.appLanguage == AppLanguage.Korean) "필수 허용" else "Required"
        EffectiveAppAccess.AllRestrictionsExempt ->
            if (text.appLanguage == AppLanguage.Korean) "제한 없음" else "Exempt"
        EffectiveAppAccess.ScheduleAllowed ->
            if (text.appLanguage == AppLanguage.Korean) "스케줄 허용" else "Schedule allowed"
        EffectiveAppAccess.AllowOnlyAllowed ->
            if (text.appLanguage == AppLanguage.Korean) "허용앱만 허용" else "Allow-only allowed"
        EffectiveAppAccess.NormallyAllowed ->
            if (text.appLanguage == AppLanguage.Korean) "실행 가능" else "Available"
        EffectiveAppAccess.BlockedBySchedule ->
            if (text.appLanguage == AppLanguage.Korean) "스케줄 차단" else "Blocked by schedule"
        EffectiveAppAccess.BlockedByAllowOnly ->
            if (text.appLanguage == AppLanguage.Korean) "허용앱만 차단" else "Blocked by allow-only"
        EffectiveAppAccess.BlockedByParent ->
            if (text.appLanguage == AppLanguage.Korean) "부모 차단" else "Blocked by parent"
    }
    val limitingLabel = when (summary.limitingPolicy) {
        EffectiveTimeLimiter.None -> null
        EffectiveTimeLimiter.Daily ->
            if (text.appLanguage == AppLanguage.Korean) "요일별 제한" else "Daily limit"
        EffectiveTimeLimiter.AppGroup ->
            if (text.appLanguage == AppLanguage.Korean) "그룹 제한" else "Group limit"
        EffectiveTimeLimiter.App ->
            if (text.appLanguage == AppLanguage.Korean) "앱별 제한" else "App limit"
    }
    val rowContent: @Composable () -> Unit = {
        Column {
            AppRow(
                appName = summary.appName,
                packageName = summary.packageName,
                supportingText = listOfNotNull(
                    if (temporaryAllowed) {
                        if (text.appLanguage == AppLanguage.Korean) "일시 허용" else "Temporarily allowed"
                    } else {
                        null
                    },
                    accessLabel,
                    limitingLabel?.let { label ->
                        val remaining = summary.remainingMinutes ?: 0
                        if (text.appLanguage == AppLanguage.Korean) {
                            "$label · ${formatLimitMinutesLabel(remaining)} 남음"
                        } else {
                            "$label · ${formatLimitMinutesLabel(remaining)} remaining"
                        }
                    },
                ).joinToString(" · "),
                trailingContent = {
                    StatusBadge(
                        when {
                            temporaryAllowed ->
                                if (text.appLanguage == AppLanguage.Korean) "일시 허용" else "Temporary"
                            summary.status == LimitStatus.Exceeded ->
                                if (text.appLanguage == AppLanguage.Korean) "차단" else "Blocked"
                            summary.status == LimitStatus.Warning ->
                                if (text.appLanguage == AppLanguage.Korean) "곧 종료" else "Ending soon"
                            else ->
                                if (text.appLanguage == AppLanguage.Korean) "사용 가능" else "Available"
                        },
                        if (temporaryAllowed) LimitStatus.Normal else summary.status,
                    )
                },
            )
            if (expanded) {
                Text(
                    effectiveAppPolicyExplanation(
                        summary = summary,
                        temporaryAllowed = temporaryAllowed,
                        text = text,
                    ),
                    modifier = Modifier.padding(start = 52.dp, end = 12.dp, bottom = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (onClick == null) {
        rowContent()
    } else {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(16.dp),
            color = if (expanded) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
            } else {
                Color.Transparent
            },
        ) {
            rowContent()
        }
    }
}

private fun effectiveAppPolicyExplanation(
    summary: EffectiveAppPolicySummary,
    temporaryAllowed: Boolean,
    text: AppStrings,
): String {
    if (temporaryAllowed) {
        return if (text.appLanguage == AppLanguage.Korean) {
            "현재 임시 허용이 적용되어 있습니다. 임시 허용이 끝나면 기존 앱 사용 범위와 시간 제한이 다시 적용됩니다."
        } else {
            "Temporary access is active. The normal access range and time limits resume when it ends."
        }
    }
    return when (summary.access) {
        EffectiveAppAccess.RequiredAllowed ->
            if (text.appLanguage == AppLanguage.Korean) {
                "안전과 기기 복구에 필요한 필수 앱이므로 차단하지 않습니다."
            } else {
                "This safety-required app is never blocked."
            }
        EffectiveAppAccess.AllRestrictionsExempt ->
            if (text.appLanguage == AppLanguage.Korean) {
                "제한 없이 사용할 수 있는 앱입니다. 사용량은 통계에 기록되지만 차단 제한에는 합산되지 않습니다."
            } else {
                "This app is exempt from all restrictions. Usage is tracked but not counted toward enforcement."
            }
        EffectiveAppAccess.BlockedBySchedule ->
            if (text.appLanguage == AppLanguage.Korean) {
                "현재 스케줄의 허용 앱 목록에 포함되지 않아 차단됩니다."
            } else {
                "Blocked because it is not in the active schedule's allowed-app list."
            }
        EffectiveAppAccess.BlockedByAllowOnly ->
            if (text.appLanguage == AppLanguage.Korean) {
                "허용앱만 모드의 허용 목록에 포함되지 않아 차단됩니다."
            } else {
                "Blocked because it is not in the allow-only list."
            }
        EffectiveAppAccess.BlockedByParent ->
            if (text.appLanguage == AppLanguage.Korean) {
                "부모의 즉시 차단이 적용 중입니다. 지정한 종료 시각이 되면 자동으로 해제됩니다."
            } else {
                "The parent's immediate block is active. It ends at the selected time."
            }
        else -> when (summary.limitingPolicy) {
            EffectiveTimeLimiter.Daily ->
                if (text.appLanguage == AppLanguage.Korean) {
                    "적용 중인 시간 제한 가운데 요일별 제한이 가장 먼저 끝납니다."
                } else {
                    "The daily limit is the first active time limit that will end."
                }
            EffectiveTimeLimiter.AppGroup ->
                if (text.appLanguage == AppLanguage.Korean) {
                    "적용 중인 시간 제한 가운데 앱 그룹 제한이 가장 먼저 끝납니다."
                } else {
                    "The app-group limit is the first active time limit that will end."
                }
            EffectiveTimeLimiter.App ->
                if (text.appLanguage == AppLanguage.Korean) {
                    "적용 중인 시간 제한 가운데 앱별 제한이 가장 먼저 끝납니다."
                } else {
                    "The app limit is the first active time limit that will end."
                }
            EffectiveTimeLimiter.None ->
                if (text.appLanguage == AppLanguage.Korean) {
                    "현재 이 앱을 차단하는 앱 사용 범위 또는 시간 제한이 없습니다."
                } else {
                    "No current access-range or time-limit policy blocks this app."
                }
        }
    }
}

@Composable
private fun HardshipPolicySummary(summary: PolicySummary, text: AppStrings) {
    val configured = summary.hardshipItems
    if (configured.isEmpty()) return

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.70f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = if (text.appLanguage == AppLanguage.Korean) {
                    "고행 모드 적용 정책 ${configured.size}개"
                } else {
                    "${configured.size} hardship policies"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            configured.forEach { item ->
                val policyType = item.policyKey.policyType
                val level = item.level
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    HardshipMeditationIcon(level = level, modifier = Modifier.size(30.dp))
                    Text(
                        text = buildString {
                            append(text.hardshipPolicyLabel(policyType))
                            if (item.targetName.isNotBlank()) append(" · ${item.targetName}")
                        },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (item.policyKey in summary.activeHardshipPolicyKeys) {
                            if (text.appLanguage == AppLanguage.Korean) {
                                "${text.hardshipLevelLabel(level)} · 적용 중"
                            } else {
                                "${text.hardshipLevelLabel(level)} · active"
                            }
                        } else {
                            text.hardshipLevelLabel(level)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = hardshipLevelColor(level),
                    )
                }
            }
            if (configured.any { item -> item.level == HardshipLevel.Level3 }) {
                val nextAvailableAt = summary.emergencyPassNextAvailableAtMillis
                Text(
                    text = when {
                        nextAvailableAt <= 0L || System.currentTimeMillis() >= nextAvailableAt ->
                            if (text.appLanguage == AppLanguage.Korean) "Emergency Pass 사용 가능 · 1회" else "Emergency Pass available · 1 use"
                        else -> if (text.appLanguage == AppLanguage.Korean) {
                            "Emergency Pass 사용 완료 · 다음 사용 가능 ${formatDateTime(nextAvailableAt)}"
                        } else {
                            "Emergency Pass used · available again ${formatDateTime(nextAvailableAt)}"
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun AllowOnlyPolicySummaryLine(summary: PolicySummary, text: AppStrings) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
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
            if (summary.temporaryAllowedApps.isNotEmpty()) {
                TemporaryAllowedAppsSummaryList(summary.temporaryAllowedApps, text)
            }
        }
    }
}

@Composable
fun SchedulePolicySummaryLine(
    summary: ScheduleSummary,
    temporaryAllowedApps: List<TemporaryAllowedAppSummary>,
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
            if (summary.activeNow && temporaryAllowedApps.isNotEmpty()) {
                TemporaryAllowedAppsSummaryList(temporaryAllowedApps, text)
            }
        }
    }
}

@Composable
private fun TemporaryAllowedAppsSummaryList(
    temporaryAllowedApps: List<TemporaryAllowedAppSummary>,
    text: AppStrings,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        temporaryAllowedApps.forEach { allowance ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AppIcon(
                    packageName = allowance.packageName,
                    contentDescription = allowance.appName,
                    size = 30.dp,
                )
                Text(
                    text = allowance.appName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                StatusBadge(temporaryAllowanceStatusLabel(allowance, text), LimitStatus.Normal)
            }
        }
    }
}

private fun temporaryAllowanceStatusLabel(
    allowance: TemporaryAllowedAppSummary,
    text: AppStrings,
): String {
    return if (allowance.unlockedForToday) {
        text.unlockedToday
    } else {
        text.temporaryAllowanceRemaining(allowance.temporaryRemainingMinutes)
    }
}

@Composable
fun PolicyGroupSummaryLine(summary: AppGroupSummary, text: AppStrings, onClick: () -> Unit) {
    val configuredLimitLabel = when {
        !summary.limitConfigured -> text.noLimit
        summary.limitMinutes == 0 && summary.extraMinutes <= 0 -> text.zeroMinuteBlockLabel()
        else -> formatLimitWithAllowance(
            limitMinutes = summary.limitMinutes,
            extraMinutes = summary.extraMinutes,
            unlockedForToday = false,
            text = text,
        )
    }
    val activeDaysLabel = scheduleDaysSummary(summary.activeDays, text)
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
                "${formatLimitMinutesLabel(summary.usedMinutes)} / $configuredLimitLabel",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                textAlign = TextAlign.End,
            )
        }
        if (summary.limitConfigured) {
            Text(
                text = if (summary.activeToday) {
                    activeDaysLabel
                } else {
                    "$activeDaysLabel · ${text.todayNotAppliedLabel()}"
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (summary.activeToday) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        if (summary.limitEnabled) {
            ProgressOnlyBar(
                usedMinutes = summary.usedMinutes,
                limitMinutes = summary.limitMinutes,
                extraMinutes = summary.extraMinutes,
                status = summary.status,
            )
        }
        if (summary.excludedPackageCount > 0) {
            Text(
                if (text.appLanguage == AppLanguage.Korean) {
                    "제한 없음 앱 ${summary.excludedPackageCount}개의 사용량은 그룹 제한에 합산되지 않습니다."
                } else {
                    "Usage from ${summary.excludedPackageCount} exempt apps is not counted toward this group limit."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
                limitEnabled = groupSummary.limitEnabled,
                limitTextOverride = if (groupSummary.activeToday) null else text.todayNotAppliedLabel(),
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
                            supportingText = if (appUsage.excludedFromRestrictions) {
                                if (text.appLanguage == AppLanguage.Korean) {
                                    "${text.usedMinutes(appUsage.usedMinutes)} · 제한 없음"
                                } else {
                                    "${text.usedMinutes(appUsage.usedMinutes)} · exempt"
                                }
                            } else {
                                text.usedMinutes(appUsage.usedMinutes)
                            },
                            trailingContent = if (limitMinutes != null || appUsage.extraMinutes > 0 || appUsage.unlockedForToday) {
                                {
                                    LimitTimeChip(
                                        if (limitMinutes != null) {
                                            formatLimitWithTemporaryAllowance(
                                                limitMinutes = limitMinutes,
                                                extraMinutes = appUsage.extraMinutes,
                                                unlockedForToday = appUsage.unlockedForToday,
                                                temporaryRemainingMinutes = appUsage.temporaryRemainingMinutes,
                                                text = text,
                                            )
                                        } else if (appUsage.temporaryRemainingMinutes > 0) {
                                            "${formatLimitMinutesLabel(appUsage.temporaryRemainingMinutes)} ${text.temporaryAllowances}"
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
            formatLimitWithTemporaryAllowance(
                limitMinutes = summary.limitMinutes,
                extraMinutes = summary.extraMinutes,
                unlockedForToday = summary.unlockedForToday,
                temporaryRemainingMinutes = summary.temporaryRemainingMinutes,
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
    val configuredLimitLabel = if (
        summary.limitMinutes == 0 &&
        summary.extraMinutes <= 0 &&
        !summary.unlockedForToday &&
        summary.temporaryRemainingMinutes <= 0
    ) {
        text.zeroMinuteBlockLabel()
    } else {
        formatLimitWithTemporaryAllowance(
            limitMinutes = summary.limitMinutes,
            extraMinutes = summary.extraMinutes,
            unlockedForToday = summary.unlockedForToday,
            temporaryRemainingMinutes = summary.temporaryRemainingMinutes,
            text = text,
        )
    }
    val activeDaysLabel = scheduleDaysSummary(summary.activeDays, text)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(packageName = summary.packageName, contentDescription = summary.appName, size = 24.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(summary.appName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                if (summary.excludedFromRestrictions) {
                    if (text.appLanguage == AppLanguage.Korean) "제한 없음" else "Excluded"
                } else {
                    "${formatLimitMinutesLabel(summary.usedMinutes)} / $configuredLimitLabel"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = if (summary.activeToday) {
                activeDaysLabel
            } else {
                "$activeDaysLabel · ${text.todayNotAppliedLabel()}"
            },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (summary.activeToday) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        if (summary.excludedFromRestrictions) {
            Text(
                if (text.appLanguage == AppLanguage.Korean) {
                    "사용량은 통계에 기록되지만 설정된 앱별 제한은 적용되지 않습니다."
                } else {
                    "Usage is tracked in statistics, but this app limit is not enforced."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (summary.activeToday) {
            ProgressOnlyBar(
                usedMinutes = summary.usedMinutes,
                limitMinutes = summary.limitMinutes,
                extraMinutes = summary.extraMinutes,
                unlockedForToday = summary.unlockedForToday,
                status = summary.status,
            )
        }
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
    val rawProgress = if (unlockedForToday) {
        0f
    } else if (effectiveLimitMinutes <= 0) {
        if (status == LimitStatus.Exceeded) 1f else 0f
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
            val rawMinutes = minutes.toIntOrNull()
            val formattedMinutes = when (rawMinutes) {
                null -> text.noLimit
                0 -> text.zeroMinuteBlockLabel()
                else -> formatLimitMinutesLabel(rawMinutes)
            }
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
    valueMinutes: Int?,
    onValueMinutesChange: (Int) -> Unit,
    onRemoveLimit: () -> Unit,
    text: AppStrings,
    pickerTitle: String,
    minMinutes: Int = 0,
    maxMinutes: Int = POLICY_MAX_MINUTES,
    includeMaxPreset: Boolean = true,
    pickerSaveLabel: String? = null,
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
                when (valueMinutes) {
                    null -> text.noLimit
                    0 -> text.zeroMinuteBlockLabel()
                    else -> formatLimitMinutesLabel(valueMinutes)
                },
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
            valueMinutes = valueMinutes ?: 0,
            minMinutes = minMinutes,
            maxMinutes = maxMinutes,
            includeMaxPreset = includeMaxPreset,
            text = text,
            onDismiss = { showPicker = false },
            onRemoveLimit = {
                onRemoveLimit()
                showPicker = false
            },
            saveLabel = pickerSaveLabel ?: text.savePolicy,
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
    onRemoveLimit: (() -> Unit)? = null,
    saveLabel: String = text.savePolicy,
    onApply: (Int) -> Unit,
) {
    val lowerBound = minMinutes.coerceAtMost(maxMinutes)
    val upperBound = maxMinutes.coerceAtLeast(lowerBound)
    TimeWheelPickerDialog(
        title = title,
        valueMinutes = valueMinutes,
        lowerBound = lowerBound,
        upperBound = upperBound,
        displayValue = { minutes ->
            if (minutes == 0) text.zeroMinuteBlockLabel() else formatLimitMinutesLabel(minutes)
        },
        saveLabel = saveLabel,
        onDismiss = onDismiss,
        onApply = onApply,
        secondaryActionLabel = text.noLimit.takeIf { onRemoveLimit != null },
        onSecondaryAction = onRemoveLimit,
        zeroValueWarning = text.zeroMinuteBlockWarning().takeIf { lowerBound == 0 },
    )
}

@Composable
fun MinuteControlPanel(
    valueMinutes: Int?,
    onValueMinutesChange: (Int) -> Unit,
    onRemoveLimit: () -> Unit,
    text: AppStrings,
    title: String,
    minMinutes: Int = 0,
    maxMinutes: Int = POLICY_MAX_MINUTES,
    includeMaxPreset: Boolean = true,
    pickerSaveLabel: String? = null,
) {
    Surface(
        shape = RoundedCornerShape(ScreenRestTheme.radii.row),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, ScreenRestTheme.colors.divider),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                EditableMinuteValue(
                    valueMinutes = valueMinutes,
                    onValueMinutesChange = onValueMinutesChange,
                    onRemoveLimit = onRemoveLimit,
                    text = text,
                    pickerTitle = title,
                    minMinutes = minMinutes,
                    maxMinutes = maxMinutes,
                    includeMaxPreset = includeMaxPreset,
                    pickerSaveLabel = pickerSaveLabel,
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
    AllControls,
}

private enum class PolicySectionIcon {
    DailyLimit,
    AppGroups,
    AppLimits,
    Schedule,
    AllowOnly,
    Language,
    Notifications,
    Pin,
    ParentManagement,
    EventLog,
}

@Composable
private fun CollapsiblePolicyCard(
    title: String,
    icon: PolicySectionIcon,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    text: AppStrings,
    helpText: String? = null,
    supportingText: String? = null,
    collapsedStatusText: String? = null,
    collapsedStatusColor: Color? = null,
    hardshipLevel: HardshipLevel = HardshipLevel.Off,
    reserveHeaderTrailingSpace: Boolean = false,
    showHeaderTrailingOnlyWhenExpanded: Boolean = false,
    headerTrailing: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    var helpVisible by rememberSaveable(title) { mutableStateOf(false) }
    val korean = text.appLanguage == AppLanguage.Korean
    val tone = when (icon) {
        PolicySectionIcon.DailyLimit, PolicySectionIcon.AppLimits -> ScreenRestTone.Primary
        PolicySectionIcon.AppGroups -> ScreenRestTone.Success
        PolicySectionIcon.Schedule -> ScreenRestTone.Schedule
        PolicySectionIcon.AllowOnly -> ScreenRestTone.Warning
        else -> ScreenRestTone.Neutral
    }
    ScreenRestCard(
        contentPadding = PaddingValues(ScreenRestTheme.spacing.md),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.xs)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ScreenRestTheme.sizes.minimumTouchTarget)
                    .clickable { onExpandedChange(!expanded) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.xs),
            ) {
                PolicySectionHeader(
                    title = title,
                    icon = icon,
                    modifier = Modifier.weight(1f),
                    hardshipLevel = hardshipLevel,
                    text = text,
                )
                if (!showHeaderTrailingOnlyWhenExpanded && !reserveHeaderTrailingSpace) {
                    headerTrailing()
                }
                SectionExpandButton(
                    expanded = expanded,
                    text = text,
                    onClick = { onExpandedChange(!expanded) },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.xs),
            ) {
                if (!supportingText.isNullOrBlank()) {
                    Text(
                        text = supportingText,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (!collapsedStatusText.isNullOrBlank()) {
                    ScreenRestStatusPill(
                        label = collapsedStatusText,
                        tone = if (collapsedStatusColor != null) tone else ScreenRestTone.Neutral,
                    )
                }
            }
        }
        if (expanded) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(ScreenRestTheme.colors.divider),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.sm),
            ) {
                Text(
                    text = if (korean) "사용 설정" else "Controls",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = tone.contentColor(),
                )
                if (showHeaderTrailingOnlyWhenExpanded || reserveHeaderTrailingSpace) {
                    headerTrailing()
                }
            }
            if (!helpText.isNullOrBlank()) {
                TextButton(onClick = { helpVisible = !helpVisible }) {
                    Text(
                        text = if (helpVisible) {
                            if (korean) "설명 접기" else "Hide explanation"
                        } else {
                            if (korean) "자세한 설명" else "How this works"
                        },
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                if (helpVisible) {
                    Surface(
                        shape = RoundedCornerShape(ScreenRestTheme.radii.row),
                        color = tone.contentColor().copy(alpha = 0.08f),
                    ) {
                        Text(
                            text = helpText,
                            modifier = Modifier.padding(ScreenRestTheme.spacing.sm),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Surface(
                shape = RoundedCornerShape(ScreenRestTheme.radii.row),
                color = tone.contentColor().copy(alpha = 0.045f),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(ScreenRestTheme.spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.sm),
                    content = content,
                )
            }
        }
    }
}

@Composable
private fun PolicyHelpButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(34.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                "?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun PolicyHelpDialog(
    title: String,
    description: String,
    text: AppStrings,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SectionTitle(title)
                Text(
                    description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (text.appLanguage == AppLanguage.Korean) "확인" else "OK")
                }
            }
        }
    }
}

@Composable
private fun PolicySectionHeader(
    title: String,
    icon: PolicySectionIcon,
    modifier: Modifier = Modifier,
    hardshipLevel: HardshipLevel = HardshipLevel.Off,
    text: AppStrings,
) {
    val (iconRes, tone) = when (icon) {
        PolicySectionIcon.DailyLimit -> R.drawable.ic_nav_today to ScreenRestTone.Primary
        PolicySectionIcon.AppGroups -> R.drawable.ic_nav_rules to ScreenRestTone.Success
        PolicySectionIcon.AppLimits -> R.drawable.ic_family_device to ScreenRestTone.Primary
        PolicySectionIcon.Schedule -> R.drawable.ic_family_clock to ScreenRestTone.Schedule
        PolicySectionIcon.AllowOnly -> R.drawable.ic_more_protection to ScreenRestTone.Warning
        else -> R.drawable.ic_more_protection to ScreenRestTone.Neutral
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.sm),
    ) {
        MoreMenuIcon(iconRes, tone)
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.xxs),
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (hardshipLevel != HardshipLevel.Off) {
                HardshipStageIndicator(level = hardshipLevel, text = text)
            }
        }
    }
}

@Composable
private fun HardshipStageIndicator(
    level: HardshipLevel,
    text: AppStrings,
    modifier: Modifier = Modifier,
) {
    if (level == HardshipLevel.Off) return
    val color = hardshipLevelColor(level)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        HardshipMeditationIcon(level = level, modifier = Modifier.size(22.dp))
        Text(
            text = text.hardshipLevelLabel(level),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
private fun HardshipMeditationIcon(
    level: HardshipLevel,
    modifier: Modifier = Modifier,
) {
    val color = hardshipLevelColor(level)
    Icon(
        painter = painterResource(R.drawable.ic_hardship_meditation),
        contentDescription = null,
        tint = color,
        modifier = modifier,
    )
}

@Composable
private fun hardshipLevelColor(level: HardshipLevel): Color {
    return when (level) {
        HardshipLevel.Off -> MaterialTheme.colorScheme.onSurfaceVariant
        HardshipLevel.Level1 -> Color(0xFFB07A16)
        HardshipLevel.Level2 -> Color(0xFFE26822)
        HardshipLevel.Level3 -> Color(0xFF8E2745)
    }
}

@Composable
private fun hardshipAwareSwitchColors(level: HardshipLevel): SwitchColors {
    val hardshipColor = hardshipLevelColor(level)
    return SwitchDefaults.colors(
        disabledCheckedThumbColor = Color.White.copy(alpha = 0.94f),
        disabledCheckedTrackColor = hardshipColor.copy(alpha = 0.78f),
        disabledCheckedBorderColor = hardshipColor,
    )
}

@Composable
private fun CompactPolicySwitch(
    checked: Boolean,
    enabled: Boolean,
    hardshipLevel: HardshipLevel,
    onCheckedChange: (Boolean) -> Unit,
) {
    Box(
        modifier = Modifier
            .size(width = 56.dp, height = ScreenRestTheme.sizes.minimumTouchTarget)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = hardshipAwareSwitchColors(hardshipLevel),
            modifier = Modifier
                .graphicsLayer(scaleX = 0.82f, scaleY = 0.82f)
                .clearAndSetSemantics {},
        )
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
        modifier = Modifier.size(width = 60.dp, height = 40.dp),
        shape = RoundedCornerShape(999.dp),
        color = if (expanded) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = if (expanded) text.collapseSection else text.expandSection,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun HardshipModeFooter(
    policyType: HardshipPolicyType,
    level: HardshipLevel,
    enabled: Boolean,
    text: AppStrings,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
    )
    Surface(
        shape = RoundedCornerShape(ScreenRestTheme.radii.row),
        color = if (level == HardshipLevel.Off) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
        } else {
            hardshipLevelColor(level).copy(alpha = 0.10f)
        },
        border = BorderStroke(
            1.dp,
            if (level == HardshipLevel.Off) {
                MaterialTheme.colorScheme.outlineVariant
            } else {
                hardshipLevelColor(level).copy(alpha = 0.52f)
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HardshipMeditationIcon(level = level, modifier = Modifier.size(28.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = text.hardshipModeTitle(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (level == HardshipLevel.Off) {
                            text.hardshipPolicyDescription(policyType)
                        } else {
                            text.hardshipConfiguredDescription(level)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Button(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(14.dp),
                colors = if (level == HardshipLevel.Off) {
                    ButtonDefaults.buttonColors()
                } else {
                    ButtonDefaults.buttonColors(
                        containerColor = hardshipLevelColor(level),
                        disabledContainerColor = hardshipLevelColor(level).copy(alpha = 0.72f),
                        disabledContentColor = Color.White.copy(alpha = 0.92f),
                    )
                },
            ) {
                Text(
                    text = if (level == HardshipLevel.Off) {
                        text.hardshipConfigureLabel()
                    } else {
                        text.hardshipLevelLabel(level) + " · " + text.hardshipChangeLabel()
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (!enabled) {
                Text(
                    text = text.hardshipNeedsPolicyLabel(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HardshipModeDialog(
    policyKey: HardshipPolicyKey,
    policyType: HardshipPolicyType,
    targetName: String,
    currentLevel: HardshipLevel,
    level3Locked: Boolean,
    allRestrictionsExemptAppCount: Int,
    configurationReflectionReadyAtMillis: Long,
    text: AppStrings,
    onDismiss: () -> Unit,
    onStartConfigurationReflection: () -> Unit,
    onApply: (HardshipLevel, String) -> Unit,
) {
    var selectedLevel by remember(policyKey, currentLevel) { mutableStateOf(currentLevel) }
    var showLevel3FirstWarning by remember(policyType) { mutableStateOf(false) }
    var showLevel3FinalWarning by remember(policyType) { mutableStateOf(false) }
    var pendingLevelForPin by remember(policyType) { mutableStateOf<HardshipLevel?>(null) }
    var countdownNowMillis by remember(configurationReflectionReadyAtMillis) {
        mutableStateOf(System.currentTimeMillis())
    }
    LaunchedEffect(configurationReflectionReadyAtMillis) {
        while (
            configurationReflectionReadyAtMillis > 0L &&
            countdownNowMillis < configurationReflectionReadyAtMillis
        ) {
            delay(1_000L)
            countdownNowMillis = System.currentTimeMillis()
        }
    }
    val reflectionRemainingMillis =
        (configurationReflectionReadyAtMillis - countdownNowMillis).coerceAtLeast(0L)
    val reflectionStarted = configurationReflectionReadyAtMillis > 0L
    val reflectionWaiting = reflectionStarted && reflectionRemainingMillis > 0L
    val currentLevelNeedsReflection = currentLevel in
        setOf(HardshipLevel.Level1, HardshipLevel.Level2)
    val hasSelectedChange = selectedLevel != currentLevel
    fun countdownLabel(): String {
        val totalSeconds = (reflectionRemainingMillis + 999L) / 1_000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return "%02d:%02d".format(minutes, seconds)
    }
    fun requestWeakening(nextLevel: HardshipLevel) {
        when {
            !currentLevelNeedsReflection -> pendingLevelForPin = nextLevel
            !reflectionStarted -> onStartConfigurationReflection()
            reflectionWaiting -> Unit
            else -> pendingLevelForPin = nextLevel
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .heightIn(max = 720.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = text.hardshipDialogTitle(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = text.hardshipDialogTarget(policyType) +
                        targetName.takeIf { value -> value.isNotBlank() }?.let { value -> " · $value" }.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (level3Locked) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = hardshipLevelColor(HardshipLevel.Level3).copy(alpha = 0.10f),
                        border = BorderStroke(
                            1.dp,
                            hardshipLevelColor(HardshipLevel.Level3).copy(alpha = 0.45f),
                        ),
                    ) {
                        Text(
                            text = text.hardshipLockedLabel(),
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = hardshipLevelColor(HardshipLevel.Level3),
                        )
                    }
                }
                listOf(HardshipLevel.Level1, HardshipLevel.Level2, HardshipLevel.Level3).forEach { level ->
                    val selected = selectedLevel == level
                    Surface(
                        onClick = { selectedLevel = level },
                        enabled = !level3Locked,
                        shape = RoundedCornerShape(18.dp),
                        color = if (selected) {
                            hardshipLevelColor(level).copy(alpha = 0.12f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
                        },
                        border = BorderStroke(
                            if (selected) 2.dp else 1.dp,
                            if (selected) hardshipLevelColor(level) else MaterialTheme.colorScheme.outlineVariant,
                        ),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            HardshipStageIndicator(level = level, text = text)
                            Text(
                                text = text.hardshipLevelName(level),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = text.hardshipLevelDescription(level, policyType),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (selectedLevel == HardshipLevel.Level3) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = hardshipLevelColor(HardshipLevel.Level3).copy(alpha = 0.10f),
                    ) {
                        Text(
                            text = text.hardshipLevel3Warning(policyType),
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = hardshipLevelColor(HardshipLevel.Level3),
                        )
                    }
                    if (allRestrictionsExemptAppCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.34f),
                            ),
                        ) {
                            Text(
                                if (text.appLanguage == AppLanguage.Korean) {
                                    "제한 없음 앱 ${allRestrictionsExemptAppCount}개는 3단계가 시작되어도 차단되지 않습니다. 해당 목록을 확인한 후 적용해 주세요."
                                } else {
                                    "$allRestrictionsExemptAppCount apps excluded from all restrictions will remain available after level 3 starts. Review the exemption list before applying."
                                },
                                modifier = Modifier.padding(14.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                if (currentLevel != HardshipLevel.Off && !level3Locked) {
                    OutlinedButton(
                        onClick = { requestWeakening(HardshipLevel.Off) },
                        enabled = !reflectionWaiting,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(
                            text = when {
                                !currentLevelNeedsReflection -> text.hardshipDisableLabel()
                                !reflectionStarted -> if (text.appLanguage == AppLanguage.Korean) {
                                    "고행 종료 숙고 시작"
                                } else {
                                    "Start exit reflection"
                                }
                                reflectionWaiting -> if (text.appLanguage == AppLanguage.Korean) {
                                    "숙고 중 · ${countdownLabel()}"
                                } else {
                                    "Reflecting · ${countdownLabel()}"
                                }
                                else -> if (text.appLanguage == AppLanguage.Korean) {
                                    "관리 PIN으로 고행 종료"
                                } else {
                                    "End with admin PIN"
                                }
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    if (currentLevelNeedsReflection) {
                        Text(
                            text = if (text.appLanguage == AppLanguage.Korean) {
                                if (currentLevel == HardshipLevel.Level1) {
                                    "종료 또는 단계 하향 전 2분 숙고와 관리 PIN 확인이 필요합니다."
                                } else {
                                    "종료 또는 단계 하향 전 30분 숙고와 관리 PIN 확인이 필요합니다."
                                }
                            } else if (currentLevel == HardshipLevel.Level1) {
                                "Ending or lowering this level requires 2 minutes of reflection and the admin PIN."
                            } else {
                                "Ending or lowering this level requires 30 minutes of reflection and the admin PIN."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(text.cancel, style = MaterialTheme.typography.titleMedium)
                    }
                    Button(
                        onClick = {
                            if (selectedLevel == HardshipLevel.Level3 && currentLevel != HardshipLevel.Level3) {
                                showLevel3FirstWarning = true
                            } else if (
                                selectedLevel.storageValue < currentLevel.storageValue
                            ) {
                                requestWeakening(selectedLevel)
                            } else {
                                pendingLevelForPin = selectedLevel
                            }
                        },
                        enabled = selectedLevel != HardshipLevel.Off &&
                            hasSelectedChange &&
                            !level3Locked &&
                            !(selectedLevel.storageValue < currentLevel.storageValue && reflectionWaiting),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = hardshipLevelColor(
                                selectedLevel.takeIf { it != HardshipLevel.Off } ?: HardshipLevel.Level1,
                            ),
                        ),
                    ) {
                        Text(
                            text = if (
                                selectedLevel.storageValue < currentLevel.storageValue &&
                                currentLevelNeedsReflection
                            ) {
                                when {
                                    !reflectionStarted -> if (text.appLanguage == AppLanguage.Korean) {
                                        "단계 하향 숙고 시작"
                                    } else {
                                        "Start downgrade reflection"
                                    }
                                    reflectionWaiting -> if (text.appLanguage == AppLanguage.Korean) {
                                        "숙고 중 · ${countdownLabel()}"
                                    } else {
                                        "Reflecting · ${countdownLabel()}"
                                    }
                                    else -> text.hardshipApplyLabel(selectedLevel)
                                }
                            } else {
                                text.hardshipApplyLabel(selectedLevel)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
    if (showLevel3FirstWarning) {
        Dialog(onDismissRequest = { showLevel3FirstWarning = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 500.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    HardshipStageIndicator(level = HardshipLevel.Level3, text = text)
                    Text(
                        text = text.hardshipLevel3FinalTitle(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = text.hardshipLevel3Warning(policyType),
                        style = MaterialTheme.typography.bodyLarge,
                        color = hardshipLevelColor(HardshipLevel.Level3),
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = text.hardshipLevel3FinalBody(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = { showLevel3FirstWarning = false },
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text(text.cancel, style = MaterialTheme.typography.titleMedium)
                        }
                        Button(
                            onClick = {
                                showLevel3FirstWarning = false
                                showLevel3FinalWarning = true
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = hardshipLevelColor(HardshipLevel.Level3),
                            ),
                        ) {
                            Text(
                                text = text.hardshipLevel3ShortContinueLabel(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
    if (showLevel3FinalWarning) {
        var level3ConfirmInput by remember(policyType) { mutableStateOf("") }
        val requiredConfirmation = text.hardshipLevel3ConfirmationPhrase()
        val confirmationMatched = level3ConfirmInput.trim() == requiredConfirmation
        Dialog(onDismissRequest = { showLevel3FinalWarning = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 500.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    HardshipStageIndicator(level = HardshipLevel.Level3, text = text)
                    Text(
                        text = text.hardshipLevel3IrreversibleTitle(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = hardshipLevelColor(HardshipLevel.Level3),
                    )
                    Text(
                        text = text.hardshipLevel3IrreversibleBody(policyType),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = hardshipLevelColor(HardshipLevel.Level3).copy(alpha = 0.10f),
                        border = BorderStroke(
                            1.dp,
                            hardshipLevelColor(HardshipLevel.Level3).copy(alpha = 0.28f),
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = text.hardshipLevel3ConfirmationInstruction(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = requiredConfirmation,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = hardshipLevelColor(HardshipLevel.Level3),
                            )
                        }
                    }
                    OutlinedTextField(
                        value = level3ConfirmInput,
                        onValueChange = { value -> level3ConfirmInput = value },
                        label = { Text(text.hardshipLevel3ConfirmationLabel()) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        isError = level3ConfirmInput.isNotBlank() && !confirmationMatched,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = { showLevel3FinalWarning = false },
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text(text.cancel, style = MaterialTheme.typography.titleMedium)
                        }
                        Button(
                            onClick = {
                                showLevel3FinalWarning = false
                                pendingLevelForPin = HardshipLevel.Level3
                            },
                            enabled = confirmationMatched,
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = hardshipLevelColor(HardshipLevel.Level3),
                            ),
                        ) {
                            Text(
                                text = text.hardshipLevel3ShortFinalApplyLabel(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
    pendingLevelForPin?.let { level ->
        HardshipPinConfirmDialog(
            level = level,
            text = text,
            onDismiss = { pendingLevelForPin = null },
            onConfirm = { adminPin ->
                pendingLevelForPin = null
                onApply(level, adminPin)
            },
        )
    }
}

@Composable
private fun AdminPinConfirmDialog(
    title: String,
    description: String,
    confirmLabel: String,
    text: AppStrings,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var adminPin by remember(title) { mutableStateOf("") }
    val pinReady = adminPin.length >= 4

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SecurePinTextField(
                    value = adminPin,
                    onValueChange = { value -> adminPin = value.filter { char -> char.isDigit() } },
                    label = text.adminPin,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (pinReady) {
                                onConfirm(adminPin)
                            }
                        },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(text.cancel, style = MaterialTheme.typography.titleMedium)
                    }
                    Button(
                        onClick = { onConfirm(adminPin) },
                        enabled = pinReady,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(
                            text = confirmLabel,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HardshipPinConfirmDialog(
    level: HardshipLevel,
    text: AppStrings,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var adminPin by remember(level) { mutableStateOf("") }
    val pinReady = adminPin.length >= 4
    val confirmLabel = if (level == HardshipLevel.Off) {
        text.hardshipDisableLabel()
    } else {
        text.hardshipApplyLabel(level)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = text.adminPin,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = text.hardshipPinInstruction(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SecurePinTextField(
                    value = adminPin,
                    onValueChange = { value -> adminPin = value.filter { char -> char.isDigit() } },
                    label = text.adminPin,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (pinReady) {
                                onConfirm(adminPin)
                            }
                        },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(text.cancel, style = MaterialTheme.typography.titleMedium)
                    }
                    Button(
                        onClick = { onConfirm(adminPin) },
                        enabled = pinReady,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (level == HardshipLevel.Off) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                hardshipLevelColor(level)
                            },
                        ),
                    ) {
                        Text(
                            text = confirmLabel,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
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
        modifier = modifier.size(36.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.60f),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
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

                PolicySectionIcon.Language -> {
                    drawCircle(
                        color = primary,
                        radius = size.minDimension * 0.36f,
                        center = Offset(size.width / 2f, size.height / 2f),
                        style = stroke,
                    )
                    drawLine(
                        color = primary,
                        start = Offset(size.width * 0.18f, size.height / 2f),
                        end = Offset(size.width * 0.82f, size.height / 2f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                    drawArc(
                        color = primary,
                        startAngle = 100f,
                        sweepAngle = 160f,
                        useCenter = false,
                        topLeft = Offset(size.width * 0.31f, size.height * 0.14f),
                        size = Size(size.width * 0.38f, size.height * 0.72f),
                        style = stroke,
                    )
                    drawArc(
                        color = primary,
                        startAngle = -80f,
                        sweepAngle = 160f,
                        useCenter = false,
                        topLeft = Offset(size.width * 0.31f, size.height * 0.14f),
                        size = Size(size.width * 0.38f, size.height * 0.72f),
                        style = stroke,
                    )
                }

                PolicySectionIcon.Notifications -> {
                    val bellPath = Path().apply {
                        moveTo(size.width * 0.30f, size.height * 0.56f)
                        lineTo(size.width * 0.30f, size.height * 0.42f)
                        quadraticTo(size.width * 0.30f, size.height * 0.24f, size.width * 0.50f, size.height * 0.24f)
                        quadraticTo(size.width * 0.70f, size.height * 0.24f, size.width * 0.70f, size.height * 0.42f)
                        lineTo(size.width * 0.70f, size.height * 0.56f)
                        lineTo(size.width * 0.78f, size.height * 0.68f)
                        lineTo(size.width * 0.22f, size.height * 0.68f)
                        close()
                    }
                    drawPath(path = bellPath, color = primary, style = stroke)
                    drawCircle(
                        color = primary,
                        radius = strokeWidth * 0.62f,
                        center = Offset(size.width * 0.50f, size.height * 0.78f),
                    )
                    drawLine(
                        color = primary,
                        start = Offset(size.width * 0.50f, size.height * 0.18f),
                        end = Offset(size.width * 0.50f, size.height * 0.12f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                }

                PolicySectionIcon.Pin -> {
                    drawArc(
                        color = primary,
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(size.width * 0.30f, size.height * 0.16f),
                        size = Size(size.width * 0.40f, size.height * 0.46f),
                        style = stroke,
                    )
                    drawRoundRect(
                        color = primary,
                        topLeft = Offset(size.width * 0.22f, size.height * 0.44f),
                        size = Size(size.width * 0.56f, size.height * 0.38f),
                        cornerRadius = CornerRadius(size.minDimension * 0.10f, size.minDimension * 0.10f),
                        style = stroke,
                    )
                    drawCircle(
                        color = primary,
                        radius = strokeWidth * 0.68f,
                        center = Offset(size.width * 0.50f, size.height * 0.62f),
                    )
                    drawLine(
                        color = primary,
                        start = Offset(size.width * 0.50f, size.height * 0.66f),
                        end = Offset(size.width * 0.50f, size.height * 0.74f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                }

                PolicySectionIcon.ParentManagement -> {
                    drawCircle(
                        color = primary,
                        radius = size.minDimension * 0.13f,
                        center = Offset(size.width * 0.39f, size.height * 0.34f),
                        style = stroke,
                    )
                    drawCircle(
                        color = primary,
                        radius = size.minDimension * 0.11f,
                        center = Offset(size.width * 0.66f, size.height * 0.38f),
                        style = stroke,
                    )
                    drawArc(
                        color = primary,
                        startAngle = 205f,
                        sweepAngle = 130f,
                        useCenter = false,
                        topLeft = Offset(size.width * 0.18f, size.height * 0.48f),
                        size = Size(size.width * 0.43f, size.height * 0.35f),
                        style = stroke,
                    )
                    drawArc(
                        color = primary,
                        startAngle = 205f,
                        sweepAngle = 130f,
                        useCenter = false,
                        topLeft = Offset(size.width * 0.50f, size.height * 0.52f),
                        size = Size(size.width * 0.35f, size.height * 0.28f),
                        style = stroke,
                    )
                }

                PolicySectionIcon.EventLog -> {
                    drawRoundRect(
                        color = primary,
                        topLeft = Offset(size.width * 0.20f, size.height * 0.14f),
                        size = Size(size.width * 0.60f, size.height * 0.72f),
                        cornerRadius = CornerRadius(size.minDimension * 0.10f, size.minDimension * 0.10f),
                        style = stroke,
                    )
                    listOf(0.34f, 0.50f, 0.66f).forEach { y ->
                        drawLine(
                            color = primary,
                            start = Offset(size.width * 0.34f, size.height * y),
                            end = Offset(size.width * 0.68f, size.height * y),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round,
                        )
                    }
                    listOf(0.34f, 0.50f, 0.66f).forEach { y ->
                        drawCircle(
                            color = primary,
                            radius = strokeWidth * 0.45f,
                            center = Offset(size.width * 0.28f, size.height * y),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GroupBudgetSummary(
    totalMinutes: Int,
    text: AppStrings,
) {
    val status = LimitStatus.Normal
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
                    if (text.appLanguage == AppLanguage.Korean) {
                        "설정 합계 ${formatLimitMinutesLabel(totalMinutes)}"
                    } else {
                        "Configured ${formatLimitMinutesLabel(totalMinutes)}"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            StatusBadge(
                label = if (text.appLanguage == AppLanguage.Korean) "독립 적용" else "Independent",
                status = status,
            )
        }
    }
}

@Composable
fun ScheduleBlockingCard(
    settings: UsagePolicySettings,
    installedApps: List<InstalledAppInfo>,
    allRestrictionsExemptPackages: Set<String>,
    temporaryAllowedApps: List<TemporaryAllowedAppSummary>,
    text: AppStrings,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onUpdateSettings: (UsagePolicySettings) -> Unit,
    activeHardshipPolicyKeys: Set<HardshipPolicyKey>,
    onHardshipConfigure: (HardshipPolicyKey) -> Unit,
) {
    val schedules = settings.normalizedScheduleTemplates()
    val enabledScheduleCount = schedules.count { schedule -> schedule.enabled }
    val scheduleActiveNow = settings.isScheduleBlockingNow()
    val statusTemplate = settings.activeScheduleTemplate()
        ?: settings.selectedScheduleTemplate()
    val hasConfiguredScheduleHardship = schedules.any { schedule ->
        schedule.hardshipLevel != HardshipLevel.Off
    }
    val scheduleHardshipLevel = settings.hardshipLevelFor(HardshipPolicyType.Schedule)
    CollapsiblePolicyCard(
        title = text.scheduleBlocking,
        icon = PolicySectionIcon.Schedule,
        supportingText = if (text.appLanguage == AppLanguage.Korean) {
            "정해진 시간에는 선택한 앱만 허용합니다"
        } else {
            "Allow only selected apps at scheduled times"
        },
        collapsedStatusText = if (text.appLanguage == AppLanguage.Korean) {
            "활성 $enabledScheduleCount/${schedules.size}"
        } else {
            "$enabledScheduleCount/${schedules.size} active"
        },
        collapsedStatusColor = if (enabledScheduleCount > 0) ScreenRestPalette.Teal else null,
        helpText = if (text.appLanguage == AppLanguage.Korean) {
            "지정한 시간에는 선택한 앱만 실행됩니다. 요일별·그룹·앱별 시간 제한은 계속 적용되며, 여러 스케줄은 겹치게 저장할 수 없습니다."
        } else {
            "Only selected apps can open during the schedule. Daily, group, and app limits still apply, and schedules cannot overlap."
        },
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        text = text,
        hardshipLevel = scheduleHardshipLevel,
    ) {
        if (schedules.isNotEmpty()) {
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
        }
        if (hasConfiguredScheduleHardship) {
            Text(
                text = if (text.appLanguage == AppLanguage.Korean) {
                    "고행 모드가 설정된 스케줄은 고행 종료 또는 해제 후 끌 수 있습니다."
                } else {
                    "A schedule with hardship can be turned off after hardship ends or is removed."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = AppOver,
                fontWeight = FontWeight.SemiBold,
            )
        }
        ScheduleTemplateSection(
            settings = settings,
            installedApps = installedApps,
            allRestrictionsExemptPackages = allRestrictionsExemptPackages,
            temporaryAllowedApps = temporaryAllowedApps,
            text = text,
            onUpdateSettings = onUpdateSettings,
            activeHardshipPolicyKeys = activeHardshipPolicyKeys,
            onHardshipConfigure = onHardshipConfigure,
        )
    }
}

@Composable
fun AllowOnlyModeCard(
    settings: UsagePolicySettings,
    installedApps: List<InstalledAppInfo>,
    allowedAppPackages: Set<String>,
    allRestrictionsExemptPackages: Set<String>,
    temporaryAllowedApps: List<TemporaryAllowedAppSummary>,
    text: AppStrings,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onUpdateSettings: (UsagePolicySettings) -> Unit,
    onAllowedAppsChanged: (Set<String>) -> Unit,
    hardshipActive: Boolean,
    onHardshipConfigure: () -> Unit,
) {
    CollapsiblePolicyCard(
        title = text.allowOnlyMode,
        icon = PolicySectionIcon.AllowOnly,
        supportingText = if (text.appLanguage == AppLanguage.Korean) {
            "선택한 앱만 실행할 수 있습니다"
        } else {
            "Allow only the apps you select"
        },
        collapsedStatusText = if (settings.allowOnlyModeEnabled) {
            if (text.appLanguage == AppLanguage.Korean) "사용 중" else "Active"
        } else {
            if (text.appLanguage == AppLanguage.Korean) "꺼짐" else "Off"
        },
        collapsedStatusColor = if (settings.allowOnlyModeEnabled) ScreenRestPalette.Teal else null,
        helpText = if (text.appLanguage == AppLanguage.Korean) {
            "선택한 앱과 제한 없는 앱만 실행됩니다. 실행이 허용된 앱에도 요일별·그룹·앱별 시간 제한은 계속 적용됩니다."
        } else {
            "Only selected and unrestricted apps can open. Daily, group, and app limits still apply to allowed apps."
        },
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        text = text,
        hardshipLevel = settings.allowOnlyHardshipLevel,
        reserveHeaderTrailingSpace = true,
        showHeaderTrailingOnlyWhenExpanded = true,
        headerTrailing = {
            CompactPolicySwitch(
                checked = settings.allowOnlyModeEnabled,
                enabled = !settings.allowOnlyModeEnabled ||
                    settings.allowOnlyHardshipLevel == HardshipLevel.Off,
                onCheckedChange = { enabled ->
                    onUpdateSettings(settings.copy(allowOnlyModeEnabled = enabled))
                },
                hardshipLevel = settings.allowOnlyHardshipLevel,
            )
        },
    ) {
        if (settings.allowOnlyModeEnabled) {
            val pausedBySchedule = settings.isScheduleBlockingNow()
            StatusBadge(
                if (pausedBySchedule) {
                    if (text.appLanguage == AppLanguage.Korean) "스케줄 동안 대기" else "Paused by schedule"
                } else {
                    if (text.appLanguage == AppLanguage.Korean) "현재 적용 중" else "Active now"
                },
                if (pausedBySchedule) LimitStatus.Warning else LimitStatus.Normal,
            )
            if (pausedBySchedule) {
                Text(
                    if (text.appLanguage == AppLanguage.Korean) {
                        "스케줄 종료 후 다시 적용됩니다."
                    } else {
                        "Resumes after the schedule ends."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AllowedPolicyRelationshipSummary(
                settings = settings,
                installedApps = installedApps,
                allowedAppPackages = allowedAppPackages,
                allRestrictionsExemptPackages = allRestrictionsExemptPackages,
                temporaryAllowedApps = temporaryAllowedApps,
                text = text,
            )
            AlwaysAllowedAppsContent(
                installedApps = installedApps,
                allowedAppPackages = allowedAppPackages,
                allRestrictionsExemptPackages = allRestrictionsExemptPackages,
                temporaryAllowedApps = temporaryAllowedApps,
                text = text,
                onAllowedAppsChanged = onAllowedAppsChanged,
                preventAdditions = hardshipActive,
                appLimitMinutesByPackage = settings.appLimitMap(),
                appLimitActiveDaysByPackage = settings.appLimitActiveDayMap(),
            )
        }
        HardshipModeFooter(
            policyType = HardshipPolicyType.AllowOnly,
            level = settings.allowOnlyHardshipLevel,
            enabled = settings.allowOnlyModeEnabled,
            text = text,
            onClick = onHardshipConfigure,
        )
        if (settings.allowOnlyHardshipLevel != HardshipLevel.Off) {
            Text(
                text = if (hardshipActive) {
                    text.hardshipLockedLabel()
                } else if (text.appLanguage == AppLanguage.Korean) {
                    "모드를 끄려면 고행 모드를 먼저 해제하세요."
                } else {
                    "Disable hardship before turning this mode off."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = AppOver,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun AllowedPolicyRelationshipSummary(
    settings: UsagePolicySettings,
    installedApps: List<InstalledAppInfo>,
    allowedAppPackages: Set<String>,
    allRestrictionsExemptPackages: Set<String>,
    temporaryAllowedApps: List<TemporaryAllowedAppSummary>,
    text: AppStrings,
) {
    val cleanExemptPackages = allRestrictionsExemptPackages - SafetyGate.neverBlockPackages
    val unrestrictedPackages = SafetyGate.expandedUserAllowedPackages(cleanExemptPackages)
    val visibleInstalledPackages = installedApps.map { app -> app.packageName }.toSet()
    val visibleUnrestrictedCount = unrestrictedPackages.count { packageName ->
        packageName in visibleInstalledPackages && packageName !in SafetyGate.neverBlockPackages
    }
    val userAllowedCount =
        ((allowedAppPackages - SafetyGate.neverBlockPackages) - unrestrictedPackages).size
    val activeSchedule = settings.activeScheduleTemplate()
    val scheduleAllowedCount = activeSchedule
        ?.allowedPackageNames
        ?.minus(SafetyGate.neverBlockPackages)
        ?.minus(unrestrictedPackages)
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
                if (text.appLanguage == AppLanguage.Korean) "현재 허용 범위" else "Currently allowed",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CompactStatusBadge(
                    label = if (text.appLanguage == AppLanguage.Korean) {
                        "시스템 자동"
                    } else {
                        "Automatic"
                    },
                    status = LimitStatus.Normal,
                )
                CompactStatusBadge(
                    label = if (text.appLanguage == AppLanguage.Korean) {
                        "직접 선택 $userAllowedCount"
                    } else {
                        "Selected $userAllowedCount"
                    },
                    status = if (userAllowedCount > 0) LimitStatus.Normal else LimitStatus.Warning,
                )
                if (visibleUnrestrictedCount > 0) {
                    CompactStatusBadge(
                        label = if (text.appLanguage == AppLanguage.Korean) {
                            "제한 없음 $visibleUnrestrictedCount"
                        } else {
                            "Unrestricted $visibleUnrestrictedCount"
                        },
                        status = LimitStatus.Normal,
                    )
                }
                if (temporaryAllowedApps.isNotEmpty()) {
                    CompactStatusBadge(
                        label = if (text.appLanguage == AppLanguage.Korean) {
                            "임시 허용 ${temporaryAllowedApps.size}"
                        } else {
                            "Temporary ${temporaryAllowedApps.size}"
                        },
                        status = LimitStatus.Normal,
                    )
                }
                if (scheduleAllowedCount > 0) {
                    CompactStatusBadge(
                        label = if (text.appLanguage == AppLanguage.Korean) {
                            "스케줄 $scheduleAllowedCount"
                        } else {
                            "Schedule $scheduleAllowedCount"
                        },
                        status = LimitStatus.Normal,
                    )
                }
            }
            Text(
                if (text.appLanguage == AppLanguage.Korean) {
                    "시스템 자동은 설정·키보드·사진 선택기처럼 차단하면 안 되는 화면입니다. ‘연동 제한 없음’은 대표 앱에 필요한 보조 앱이며, 임시 허용은 블록 화면에서 추가 시간 또는 오늘 허용을 적용한 앱입니다."
                } else {
                    "System covers required Android surfaces. Linked unrestricted apps support a selected Phone, Messages, Gallery, or Camera app. Temporary means extra time or an unlock granted from the block screen."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
internal fun TimeWheelPickerDialog(
    title: String,
    valueMinutes: Int,
    lowerBound: Int,
    upperBound: Int,
    displayValue: (Int) -> String,
    saveLabel: String,
    onDismiss: () -> Unit,
    onApply: (Int) -> Unit,
    headerIcon: (@Composable () -> Unit)? = null,
    supportingText: String? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    zeroValueWarning: String? = null,
    additionalContent: (@Composable () -> Unit)? = null,
    compactLayout: Boolean = false,
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
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 4.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = 22.dp,
                    vertical = if (compactLayout) 14.dp else 20.dp,
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (compactLayout) 12.dp else 16.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (compactLayout && headerIcon != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            headerIcon()
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else {
                        headerIcon?.let { content ->
                            content()
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        Text(
                            title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    supportingText?.takeIf { value -> value.isNotBlank() }?.let { value ->
                        Text(
                            text = value,
                            modifier = Modifier.padding(top = 6.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = if (compactLayout) 2 else Int.MAX_VALUE,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Surface(
                        modifier = Modifier.padding(top = 10.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            displayValue(draftMinutes),
                            modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    if (!zeroValueWarning.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                                .height(42.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (draftMinutes == 0) {
                                Text(
                                    text = zeroValueWarning,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AppOver,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (compactLayout) 210.dp else 300.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .height(66.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surface),
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
                            compactLayout = compactLayout,
                        )
                        ClockTimeWheel(
                            label = "m",
                            value = minuteValue,
                            values = minSelectableMinutes..maxSelectableMinutes,
                            onValueChange = { minutes -> updateTime(minutes = minutes) },
                            modifier = Modifier.weight(1f),
                            compactLayout = compactLayout,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(if (compactLayout) 56.dp else 78.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(MaterialTheme.colorScheme.surface, Color.Transparent),
                                ),
                            ),
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(if (compactLayout) 56.dp else 78.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, MaterialTheme.colorScheme.surface),
                                ),
                            ),
                    )
                }

                additionalContent?.invoke()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (!secondaryActionLabel.isNullOrBlank() && onSecondaryAction != null) {
                        OutlinedButton(
                            onClick = onSecondaryAction,
                            modifier = Modifier
                                .weight(1f)
                                .height(58.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text(
                                secondaryActionLabel,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Button(
                        onClick = { onApply(draftMinutes) },
                        modifier = Modifier
                            .weight(1f)
                            .height(58.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClockTimeWheel(
    label: String,
    value: Int,
    values: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    compactLayout: Boolean = false,
) {
    var showDirectInput by remember { mutableStateOf(false) }
    var suppressWheelSelection by remember { mutableStateOf(false) }
    var ignoreWheelSelectionUntilElapsed by remember { mutableStateOf(0L) }
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
        if (index >= 0) {
            suppressWheelSelection = true
            try {
                listState.scrollToItem(index)
                delay(80L)
            } finally {
                suppressWheelSelection = false
            }
        }
    }
    LaunchedEffect(listState, wheelValues) {
        snapshotFlow {
            if (listState.isScrollInProgress || suppressWheelSelection) {
                return@snapshotFlow null
            }
            val layoutInfo = listState.layoutInfo
            val center = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
            layoutInfo.visibleItemsInfo
                .minByOrNull { item -> abs((item.offset + item.size / 2) - center) }
                ?.index
        }
            .distinctUntilChanged()
            .collect { index ->
                if (SystemClock.elapsedRealtime() < ignoreWheelSelectionUntilElapsed) {
                    return@collect
                }
                val nextValue = wheelValues.getOrNull(index ?: return@collect) ?: return@collect
                if (nextValue != currentValue) {
                    suppressWheelSelection = true
                    currentOnValueChange(nextValue)
                }
            }
    }

    LazyColumn(
        state = listState,
        flingBehavior = flingBehavior,
        modifier = modifier
            .height(if (compactLayout) 210.dp else 300.dp)
            .clipToBounds(),
        contentPadding = PaddingValues(vertical = if (compactLayout) 72.dp else 117.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        items(wheelValues, key = { item -> item }) { item ->
            val selected = item == value
            Surface(
                onClick = {
                    if (selected) {
                        showDirectInput = true
                    } else {
                        suppressWheelSelection = true
                        ignoreWheelSelectionUntilElapsed = SystemClock.elapsedRealtime() + 250L
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
                if (input != currentValue) {
                    suppressWheelSelection = true
                    ignoreWheelSelectionUntilElapsed = SystemClock.elapsedRealtime() + 350L
                    currentOnValueChange(input)
                }
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

    fun confirmInput() {
        val confirmedValue = input
            .filter { character -> character.isDigit() }
            .toIntOrNull()
            ?.coerceIn(values.first, values.last)
            ?: return
        focusManager.clearFocus(force = true)
        onApply(confirmedValue)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 320.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shadowElevation = 4.dp,
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
                            confirmInput()
                        },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.headlineMedium.copy(
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Button(
                    onClick = { confirmInput() },
                    enabled = input.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
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
    allRestrictionsExemptPackages: Set<String>,
    temporaryAllowedApps: List<TemporaryAllowedAppSummary>,
    text: AppStrings,
    onUpdateSettings: (UsagePolicySettings) -> Unit,
    activeHardshipPolicyKeys: Set<HardshipPolicyKey>,
    onHardshipConfigure: (HardshipPolicyKey) -> Unit,
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
    val selectedTemplateLocked = selectedTemplate?.let { template ->
        template.hardshipLevel == HardshipLevel.Level3 &&
            scheduleHardshipKey(template.id) in activeHardshipPolicyKeys
    } == true
    val temporaryAllowedPackages = temporaryAllowedApps
        .map { allowance -> allowance.packageName }
        .toSet() - SafetyGate.neverBlockPackages
    val unrestrictedPackages = SafetyGate.expandedUserAllowedPackages(
        allRestrictionsExemptPackages - SafetyGate.neverBlockPackages,
    )
    val visibleInstalledPackages = installedApps.map { app -> app.packageName }.toSet()

    fun applyTemplates(
        nextTemplates: List<ScheduleTemplatePolicy>,
        activeTemplate: ScheduleTemplatePolicy?,
    ) {
        val cleanedTemplates = nextTemplates.map { template ->
            template.copy(
                allowedPackageNames = template.allowedPackageNames -
                    SafetyGate.neverBlockPackages -
                    unrestrictedPackages,
            )
        }
        val cleanedActiveTemplate = activeTemplate?.let { active ->
            cleanedTemplates.firstOrNull { template -> template.id == active.id }
        }
        onUpdateSettings(
            settings.copy(
                scheduleBlockingEnabled = cleanedTemplates.any { template -> template.enabled },
                scheduleStartMinutes = cleanedActiveTemplate?.startMinutes ?: settings.scheduleStartMinutes,
                scheduleEndMinutes = cleanedActiveTemplate?.endMinutes ?: settings.scheduleEndMinutes,
                scheduleDays = cleanedActiveTemplate?.days?.toScheduleDaysEncoded() ?: settings.scheduleDays,
                scheduleTemplates = cleanedTemplates.toScheduleTemplatesEncoded(),
                activeScheduleTemplateId = cleanedActiveTemplate?.id.orEmpty(),
            ),
        )
    }

    fun updateSelectedTemplate(transform: (ScheduleTemplatePolicy) -> ScheduleTemplatePolicy) {
        if (selectedTemplateLocked) return
        val currentTemplate = selectedTemplate ?: return
        val transformedTemplate = transform(currentTemplate)
        val scheduleWindowChanged = transformedTemplate.startMinutes != currentTemplate.startMinutes ||
            transformedTemplate.endMinutes != currentTemplate.endMinutes ||
            transformedTemplate.days != currentTemplate.days
        val nextTemplate = if (
            scheduleWindowChanged && transformedTemplate.hardshipLevel != HardshipLevel.Off
        ) {
            transformedTemplate.copy(
                hardshipEndAtMillis = transformedTemplate.nextOccurrenceEndMillis(),
            )
        } else {
            transformedTemplate
        }
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
                            enabled = false,
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
                        enabled = !selectedTemplateLocked,
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
            val overlappingPairs = remember(templates) { templates.overlappingSchedulePairs() }
            if (overlappingPairs.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = AppOver.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, AppOver.copy(alpha = 0.30f)),
                ) {
                    Text(
                        if (text.appLanguage == AppLanguage.Korean) {
                            val names = overlappingPairs
                                .take(2)
                                .joinToString(", ") { (first, second) -> "$first ↔ $second" }
                            "스케줄 시간이 겹칩니다: $names. 동시에 두 허용 목록이 적용되지 않도록 시간을 조정해야 저장할 수 있습니다."
                        } else {
                            val names = overlappingPairs
                                .take(2)
                                .joinToString(", ") { (first, second) -> "$first ↔ $second" }
                            "Schedule times overlap: $names. Adjust the times before saving."
                        },
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = AppOver,
                    )
                }
            }
            if (templates.isEmpty()) {
                Text(text.noSchedules, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    templates.forEach { template ->
                        val scheduleOnlyPackages = template.allowedPackageNames -
                            SafetyGate.neverBlockPackages -
                            unrestrictedPackages
                        ScheduleTemplateChip(
                            template = template,
                            allowedAppCount = (
                                scheduleOnlyPackages + temporaryAllowedPackages + unrestrictedPackages
                                ).count { packageName -> packageName in visibleInstalledPackages },
                            selected = selectedTemplateId == template.id,
                            hardshipLevel = template.hardshipLevel,
                            enabled = template.enabled,
                            toggleEnabled = template.hardshipLevel == HardshipLevel.Off,
                            text = text,
                            onClick = {
                                selectedTemplateId = template.id
                                applyTemplates(templates, template)
                            },
                            onEnabledChange = { enabled ->
                                val nextTemplates = templates.map { current ->
                                    if (current.id == template.id) current.copy(enabled = enabled) else current
                                }
                                val nextSelected = nextTemplates.firstOrNull { current -> current.id == template.id }
                                selectedTemplateId = template.id
                                applyTemplates(nextTemplates, nextSelected)
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
                    allRestrictionsExemptPackages = allRestrictionsExemptPackages,
                    temporaryAllowedApps = temporaryAllowedApps,
                    appSearchQuery = appSearchQuery,
                    text = text,
                    onSearchQueryChange = { query -> appSearchQuery = query },
                    onTemplatesChanged = { nextTemplates ->
                        if (!selectedTemplateLocked) {
                            val nextSelectedTemplate = nextTemplates
                                .firstOrNull { template -> template.id == selectedTemplate.id }
                            applyTemplates(nextTemplates, nextSelectedTemplate)
                        }
                    },
                )
                val selectedHardshipKey = scheduleHardshipKey(selectedTemplate.id)
                HardshipModeFooter(
                    policyType = HardshipPolicyType.Schedule,
                    level = selectedTemplate.hardshipLevel,
                    enabled = true,
                    text = text,
                    onClick = { onHardshipConfigure(selectedHardshipKey) },
                )
                if (
                    selectedHardshipKey in activeHardshipPolicyKeys &&
                    selectedTemplate.hardshipLevel == HardshipLevel.Level3
                ) {
                    Text(
                        text = text.hardshipLockedLabel(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = AppOver,
                    )
                } else if (selectedTemplate.hardshipLevel != HardshipLevel.Off) {
                    Text(
                        text = if (text.appLanguage == AppLanguage.Korean) {
                            "고행 모드를 해제한 뒤 이 스케줄을 끌 수 있습니다."
                        } else {
                            "Remove hardship before turning this schedule off."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduleTemplateChip(
    template: ScheduleTemplatePolicy,
    allowedAppCount: Int,
    selected: Boolean,
    hardshipLevel: HardshipLevel,
    enabled: Boolean,
    toggleEnabled: Boolean,
    text: AppStrings,
    onClick: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (hardshipLevel != HardshipLevel.Off) {
                HardshipMeditationIcon(level = hardshipLevel, modifier = Modifier.size(28.dp))
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    template.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${formatScheduleWindow(template.startMinutes, template.endMinutes, text)} · ${scheduleDaysSummary(template.days, text)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${text.scheduleAllowedApps} $allowedAppCount",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            CompactPolicySwitch(
                checked = enabled,
                enabled = toggleEnabled,
                onCheckedChange = onEnabledChange,
                hardshipLevel = hardshipLevel,
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
    var isFocused by remember(scheduleId) { mutableStateOf(false) }
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val focusManager = LocalFocusManager.current
    LaunchedEffect(scheduleId, value, isFocused) {
        if (!isFocused && value != fieldValue.text) {
            fieldValue = TextFieldValue(value)
        }
    }
    OutlinedTextField(
        value = fieldValue,
        onValueChange = { nextValue ->
            fieldValue = nextValue
            // Keep the draft saveable while the field has focus. External value
            // synchronization is paused while focused, so Korean IME composition is
            // no longer overwritten or sent back to the previous cursor position.
            currentOnValueChange(nextValue.text)
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(
            onDone = { focusManager.clearFocus() },
        ),
        singleLine = true,
        modifier = modifier.onFocusChanged { focusState ->
            val lostFocus = isFocused && !focusState.isFocused
            isFocused = focusState.isFocused
            if (lostFocus) {
                val committedName = fieldValue.text.trim().ifBlank { value }
                if (committedName != value) {
                    currentOnValueChange(committedName)
                }
                if (committedName != fieldValue.text) {
                    fieldValue = TextFieldValue(committedName)
                }
            }
        },
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
    allRestrictionsExemptPackages: Set<String>,
    temporaryAllowedApps: List<TemporaryAllowedAppSummary>,
    appSearchQuery: String,
    text: AppStrings,
    onSearchQueryChange: (String) -> Unit,
    onTemplatesChanged: (List<ScheduleTemplatePolicy>) -> Unit,
) {
    val directlyUnrestrictedPackages =
        allRestrictionsExemptPackages - SafetyGate.neverBlockPackages
    val unrestrictedPackages =
        SafetyGate.expandedUserAllowedPackages(directlyUnrestrictedPackages)
    val scheduleOnlyAllowedPackages = template.allowedPackageNames -
        SafetyGate.neverBlockPackages -
        unrestrictedPackages
    val temporaryAllowanceByPackage = temporaryAllowedApps.associateBy { allowance -> allowance.packageName }
    val temporaryAllowedPackages = temporaryAllowanceByPackage.keys - SafetyGate.neverBlockPackages
    val visibleInstalledPackages = installedApps.map { app -> app.packageName }.toSet()
    val totalVisibleAllowedCount = (
        scheduleOnlyAllowedPackages + temporaryAllowedPackages + unrestrictedPackages
        ).count { packageName -> packageName in visibleInstalledPackages }
    val visibleApps = remember(
        installedApps,
        appSearchQuery,
        scheduleOnlyAllowedPackages,
        temporaryAllowedPackages,
        unrestrictedPackages,
    ) {
        val effectiveAllowedPackages =
            scheduleOnlyAllowedPackages + temporaryAllowedPackages + unrestrictedPackages
        installedApps
            .filterNot { app -> app.packageName in SafetyGate.neverBlockPackages }
            .filter { app ->
                app.matchesAppSearch(appSearchQuery)
            }
            .sortedWith(
                compareByDescending<InstalledAppInfo> { app ->
                    app.packageName in effectiveAllowedPackages
                }.thenBy { app -> app.appName.lowercase() },
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
                resetKey = template.id to appSearchQuery,
            ) {
                items(visibleApps, key = { app -> app.packageName }) { app ->
                    val scheduleAllowed = app.packageName in scheduleOnlyAllowedPackages
                    val directlyUnrestricted = app.packageName in directlyUnrestrictedPackages
                    val linkedFamily = SafetyGate.linkedAppFamily(
                        targetPackageName = app.packageName,
                        directlyAllowedPackages = directlyUnrestrictedPackages,
                    )
                    val unrestricted = app.packageName in unrestrictedPackages
                    val temporaryAllowance = temporaryAllowanceByPackage[app.packageName]
                    val selected = unrestricted || scheduleAllowed || temporaryAllowance != null
                    UserAllowedAppRow(
                        app = app,
                        selected = selected,
                        text = text,
                        enabled = !unrestricted && temporaryAllowance == null,
                        statusLabel = when {
                            directlyUnrestricted -> if (text.appLanguage == AppLanguage.Korean) {
                                "제한 없음"
                            } else {
                                "Unrestricted"
                            }
                            linkedFamily != null -> linkedUnrestrictedLabel(linkedFamily, text)
                            scheduleAllowed -> text.allowed
                            temporaryAllowance != null -> temporaryAllowanceStatusLabel(temporaryAllowance, text)
                            else -> text.allow
                        },
                        onToggle = {
                            val nextPackages = if (scheduleAllowed) {
                                scheduleOnlyAllowedPackages - app.packageName
                            } else {
                                scheduleOnlyAllowedPackages + app.packageName
                            }
                            onTemplatesChanged(
                                templates.map { item ->
                                    if (item.id == template.id) {
                                        item.copy(
                                            allowedPackageNames = nextPackages -
                                                SafetyGate.neverBlockPackages -
                                                unrestrictedPackages,
                                        )
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
    budgetMinutes: Int?,
    appCount: Int,
    selected: Boolean,
    text: AppStrings,
    activeDays: Set<Int>,
    hardshipLevel: HardshipLevel = HardshipLevel.Off,
    enabled: Boolean,
    toggleEnabled: Boolean,
    onClick: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
) {
    val dotColor = if (enabled) ScreenRestPalette.Teal else MaterialTheme.colorScheme.outline
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f) else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (hardshipLevel != HardshipLevel.Off) {
                HardshipMeditationIcon(level = hardshipLevel, modifier = Modifier.size(28.dp))
            }
            Surface(modifier = Modifier.size(10.dp), shape = CircleShape, color = dotColor) {}
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${when (budgetMinutes) {
                        null -> text.noLimit
                        0 -> text.zeroMinuteBlockLabel()
                        else -> formatLimitMinutesLabel(budgetMinutes)
                    }} \u00B7 $appCount \u00B7 ${scheduleDaysSummary(activeDays, text)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            CompactPolicySwitch(
                checked = enabled,
                enabled = toggleEnabled,
                onCheckedChange = onEnabledChange,
                hardshipLevel = hardshipLevel,
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
    temporaryRemainingMinutes: Int,
    text: AppStrings,
    activeDays: Set<Int> = (1..7).toSet(),
    accessScopeLabel: String? = null,
    accessAllowed: Boolean? = null,
    onClick: () -> Unit,
    hardshipLevel: HardshipLevel = HardshipLevel.Off,
    onHardshipClick: (() -> Unit)? = null,
) {
    val hasLimit = limitMinutes != null

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
                    accessScopeLabel?.let { label ->
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = when (accessAllowed) {
                                true -> AppSafe
                                false -> AppOver
                                null -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    if (hasLimit) {
                        val normalizedDays = activeDays.normalizedPolicyDays()
                        val activeToday = currentPolicyDayOfWeek() in normalizedDays
                        Text(
                            text = if (activeToday) {
                                scheduleDaysSummary(normalizedDays, text)
                            } else if (text.appLanguage == AppLanguage.Korean) {
                                "${scheduleDaysSummary(normalizedDays, text)} · 오늘 미적용"
                            } else {
                                "${scheduleDaysSummary(normalizedDays, text)} · Not active today"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (hasLimit || extraMinutes > 0 || unlockedForToday || temporaryRemainingMinutes > 0) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ) {
                    Text(
                        when {
                            unlockedForToday -> text.unlockedToday
                            temporaryRemainingMinutes > 0 ->
                                "${formatLimitMinutesLabel(temporaryRemainingMinutes)} ${text.temporaryAllowances}"
                            hasLimit && limitMinutes == 0 -> text.zeroMinuteBlockLabel()
                            hasLimit -> formatLimitWithAllowance(limitMinutes ?: 0, extraMinutes, false, text)
                            extraMinutes > 0 -> "+${formatLimitMinutesLabel(extraMinutes)}"
                            else -> text.addLimit
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    color = if (hasLimit || extraMinutes > 0 || unlockedForToday || temporaryRemainingMinutes > 0) {
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
        if (onHardshipClick != null) {
            Surface(
                onClick = onHardshipClick,
                modifier = Modifier.fillMaxWidth(),
                color = if (hardshipLevel == HardshipLevel.Off) {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                } else {
                    hardshipLevelColor(hardshipLevel).copy(alpha = 0.10f)
                },
                shape = RoundedCornerShape(14.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    HardshipMeditationIcon(level = hardshipLevel, modifier = Modifier.size(28.dp))
                    Text(
                        text = if (hardshipLevel == HardshipLevel.Off) {
                            text.hardshipConfigureLabel()
                        } else {
                            text.hardshipLevelLabel(hardshipLevel)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (hardshipLevel == HardshipLevel.Off) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            hardshipLevelColor(hardshipLevel)
                        },
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

private data class AppLimitAllocationInfo(
    val groupName: String,
    val groupBudgetMinutes: Int,
)

private fun appLimitAllocationInfo(
    packageName: String,
    appGroups: List<AppGroupPolicy>,
): AppLimitAllocationInfo? {
    val group = appGroups.firstOrNull { appGroup -> packageName in appGroup.packageNames } ?: return null
    val groupLimitMinutes = group.limitMinutesOrNull() ?: return null
    return AppLimitAllocationInfo(
        groupName = group.name,
        groupBudgetMinutes = groupLimitMinutes,
    )
}

@Composable
private fun PolicyAccessWarningDialog(
    appName: String,
    scopeName: String,
    text: AppStrings,
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, AppOver.copy(alpha = 0.28f)),
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    if (text.appLanguage == AppLanguage.Korean) "현재 실행할 수 없는 앱" else "App currently blocked",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = AppOver,
                )
                Text(
                    if (text.appLanguage == AppLanguage.Korean) {
                        "$appName 앱은 ‘$scopeName’에서 허용되지 않아 시간만 설정해도 실행되지 않습니다. 허용 목록에 추가한 뒤 시간을 설정할 수 있습니다."
                    } else {
                        "$appName is not allowed by ‘$scopeName’. Add it to the allowed list before setting its time."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text(if (text.appLanguage == AppLanguage.Korean) "취소" else "Cancel")
                    }
                    Button(onClick = onContinue, modifier = Modifier.weight(1f)) {
                        Text(if (text.appLanguage == AppLanguage.Korean) "허용 후 설정" else "Allow & set")
                    }
                }
            }
        }
    }
}

@Composable
private fun AppLimitPickerDialog(
    app: InstalledAppInfo,
    initialLimitMinutes: Int?,
    initialActiveDays: Set<Int>,
    allocationInfo: AppLimitAllocationInfo?,
    text: AppStrings,
    onDismiss: () -> Unit,
    onApply: (Int?, Set<Int>) -> Unit,
) {
    var selectedDays by remember(app.packageName, initialActiveDays) {
        mutableStateOf(initialActiveDays.normalizedPolicyDays())
    }
    val supportingText = allocationInfo?.let { info ->
        if (text.appLanguage == AppLanguage.Korean) {
            "${info.groupName.ifBlank { text.groupName }} 그룹 제한 ${formatLimitMinutesLabel(info.groupBudgetMinutes)}과 앱별 제한 중 먼저 도달하는 제한이 적용됩니다."
        } else {
            "The first limit reached applies: this app limit or the ${
                info.groupName.ifBlank { text.groupName }
            } group limit (${formatLimitMinutesLabel(info.groupBudgetMinutes)})."
        }
    }
    TimeWheelPickerDialog(
        title = app.appName,
        valueMinutes = (initialLimitMinutes ?: 0).coerceIn(0, POLICY_MAX_MINUTES),
        lowerBound = 0,
        upperBound = POLICY_MAX_MINUTES,
        displayValue = { minutes ->
            if (minutes == 0) text.zeroMinuteBlockLabel() else text.minutesPerDay(minutes)
        },
        saveLabel = text.applyLimit,
        onDismiss = onDismiss,
        onApply = { minutes -> onApply(minutes, selectedDays) },
        secondaryActionLabel = text.noLimit,
        onSecondaryAction = { onApply(null, selectedDays) },
        zeroValueWarning = text.zeroMinuteBlockWarning(),
        additionalContent = {
            ScheduleDaysEditor(
                selectedDays = selectedDays,
                text = text,
                onDaysChanged = { days -> selectedDays = days.normalizedPolicyDays() },
            )
        },
        headerIcon = {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                AppIcon(packageName = app.packageName, contentDescription = app.appName, size = 44.dp)
            }
        },
        supportingText = supportingText,
        compactLayout = true,
    )
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

private fun AppStrings.zeroMinuteBlockLabel(): String =
    if (appLanguage == AppLanguage.Korean) "사용 불가 · 0분" else "Blocked · 0m"

private fun AppStrings.zeroMinuteBlockWarning(): String =
    if (appLanguage == AppLanguage.Korean) {
        "사용 시간이 0분이므로 이 정책이 적용되는 동안 즉시 차단됩니다."
    } else {
        "A zero-minute limit blocks access immediately while this policy is active."
    }

private fun AppStrings.todayNotAppliedLabel(): String =
    if (appLanguage == AppLanguage.Korean) "오늘 미적용" else "Not active today"

@Composable
fun GroupAppSelectionRow(
    app: InstalledAppInfo,
    selected: Boolean,
    text: AppStrings,
    assignedGroupName: String? = null,
    accessScopeLabel: String? = null,
    accessAllowed: Boolean? = null,
    enabled: Boolean = true,
    onToggle: () -> Unit,
) {
    Column {
        Surface(
            onClick = onToggle,
            enabled = enabled,
            color = when {
                selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                assignedGroupName != null -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                else -> MaterialTheme.colorScheme.surface
            },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(packageName = app.packageName, contentDescription = app.appName, size = 36.dp)
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        app.appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (enabled || selected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    accessScopeLabel?.let { label ->
                        Text(
                            label,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (accessAllowed == false) AppOver else AppSafe,
                            maxLines = 1,
                        )
                    }
                }
                Surface(
                    modifier = Modifier.widthIn(max = 180.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        assignedGroupName
                            ?.takeIf { groupName -> groupName.isNotBlank() }
                            ?.let { groupName -> "${text.inGroup} · $groupName" }
                            ?: accessScopeLabel?.takeIf { !enabled }
                            ?: text.addToGroup,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
    activeHardshipPolicyKeys: Set<HardshipPolicyKey>,
    hardshipRuntimeState: HardshipRuntimeState,
    installedApps: List<InstalledAppInfo>,
    allowedAppPackages: Set<String>,
    allRestrictionsExemptPackages: Set<String>,
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
    onStartHardshipConfigurationReflection: (HardshipPolicyKey) -> Unit,
    onHardshipPolicyDraftChanged: (UsagePolicySettings, String) -> Unit,
    onAllowedAppsChanged: (Set<String>) -> Unit,
    onAllRestrictionsExemptAppsChanged: (Set<String>) -> Unit,
) {
    val dailyLimits = settings.dailyLimitMinutesByDayOrNull()
        .map { minutes -> minutes?.toString().orEmpty() }
    val dailyPolicyLocked = settings.dailyHardshipLevel == HardshipLevel.Level3 &&
        dailyHardshipKey() in activeHardshipPolicyKeys
    var selectedDayIndex by remember { mutableStateOf(Calendar.getInstance().get(Calendar.DAY_OF_WEEK).toDayIndex()) }
    val appGroups = settings.normalizedAppGroups().normalizedForEditing()
    val enabledAppGroupCount = appGroups.count { group -> group.enabled }
    val groupBudgetTotal = appGroups.mapNotNull { group -> group.limitMinutesOrNull() }.sum()
    var activeGroupId by remember { mutableStateOf(appGroups.firstOrNull()?.id.orEmpty()) }
    LaunchedEffect(appGroups.map { group -> group.id }) {
        if (appGroups.isNotEmpty() && appGroups.none { group -> group.id == activeGroupId }) {
            activeGroupId = appGroups.first().id
        }
    }
    val appLimits = settings.appLimitMap()
    val appLimitActiveDays = settings.appLimitActiveDayMap()
    val todayTemporaryUnlockState = temporaryUnlockState.forToday()
    var temporaryAllowanceNowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(todayTemporaryUnlockState) {
        while (true) {
            delay(30_000L)
            temporaryAllowanceNowMillis = System.currentTimeMillis()
        }
    }
    val temporaryAllowedApps = remember(
        todayTemporaryUnlockState,
        installedApps,
        temporaryAllowanceNowMillis,
    ) {
        buildTemporaryAllowedAppSummaries(
            temporaryUnlockState = todayTemporaryUnlockState,
            appNameByPackage = installedApps.associate { app -> app.packageName to app.appName },
            nowMillis = temporaryAllowanceNowMillis,
        )
    }
    var appSearchQuery by remember { mutableStateOf("") }
    var appLimitFilter by remember { mutableStateOf(AppLimitFilter.All) }
    var selectedLimitApp by remember { mutableStateOf<InstalledAppInfo?>(null) }
    var pendingInaccessibleLimitApp by remember { mutableStateOf<InstalledAppInfo?>(null) }
    var groupAppSearchQuery by remember { mutableStateOf("") }
    var hardshipDialogPolicyKey by remember { mutableStateOf<HardshipPolicyKey?>(null) }
    val directlyUnrestrictedPackages =
        allRestrictionsExemptPackages - SafetyGate.neverBlockPackages
    val expandedExemptPackages = remember(allRestrictionsExemptPackages) {
        SafetyGate.expandedUserAllowedPackages(allRestrictionsExemptPackages) +
            SafetyGate.neverBlockPackages
    }
    val activeAccessSchedule = settings.activeScheduleTemplate()

    fun accessScopeFor(packageName: String): Pair<String, Boolean>? {
        if (packageName in expandedExemptPackages) {
            val linkedFamily = SafetyGate.linkedAppFamily(
                targetPackageName = packageName,
                directlyAllowedPackages = directlyUnrestrictedPackages,
            )
            return when {
                linkedFamily != null -> linkedUnrestrictedLabel(linkedFamily, text) to true
                text.appLanguage == AppLanguage.Korean -> "제한 없음" to true
                else -> "Unrestricted" to true
            }
        }
        val schedule = activeAccessSchedule
        if (schedule != null) {
            val allowed = SafetyGate.isUserAllowedPackage(packageName, schedule.allowedPackageNames) ||
                packageName in expandedExemptPackages
            return if (text.appLanguage == AppLanguage.Korean) {
                "스케줄 · ${if (allowed) "허용" else "차단"}" to allowed
            } else {
                "Schedule · ${if (allowed) "Allowed" else "Blocked"}" to allowed
            }
        }
        if (settings.allowOnlyModeEnabled) {
            val allowed = SafetyGate.isUserAllowedPackage(packageName, allowedAppPackages) ||
                packageName in expandedExemptPackages
            return if (text.appLanguage == AppLanguage.Korean) {
                "허용앱만 · ${if (allowed) "허용" else "차단"}" to allowed
            } else {
                "Allow-only · ${if (allowed) "Allowed" else "Blocked"}" to allowed
            }
        }
        return null
    }

    fun updateDraft(
        nextDailyLimits: List<String> = dailyLimits,
        nextAppGroups: List<AppGroupPolicy> = appGroups,
        nextAppLimits: Map<String, Int> = appLimits,
        nextAppLimitActiveDays: Map<String, Set<Int>> = appLimitActiveDays,
    ) {
        onPolicyDraftChanged(
            buildUsagePolicySettings(
                base = settings,
                dailyLimits = if (dailyPolicyLocked) dailyLimits else nextDailyLimits,
                appGroups = nextAppGroups,
                appLimits = nextAppLimits,
                appLimitActiveDays = nextAppLimitActiveDays,
            ),
        )
    }
    val limitPolicy: @Composable ColumnScope.() -> Unit = {
        CollapsiblePolicyCard(
            title = text.dailyPolicy,
            icon = PolicySectionIcon.DailyLimit,
            supportingText = if (text.appLanguage == AppLanguage.Korean) {
                "요일마다 전체 사용 시간을 정합니다"
            } else {
                "Set total use time for each day"
            },
            collapsedStatusText = if (settings.dailyPolicyEnabled) {
                if (text.appLanguage == AppLanguage.Korean) "사용 중" else "Active"
            } else {
                if (text.appLanguage == AppLanguage.Korean) "꺼짐" else "Off"
            },
            collapsedStatusColor = if (settings.dailyPolicyEnabled) ScreenRestPalette.Teal else null,
            helpText = if (text.appLanguage == AppLanguage.Korean) {
                "요일마다 기기 전체 사용시간을 정합니다. 앱별·그룹 제한과 함께 사용하면 가장 먼저 끝나는 시간 제한으로 차단됩니다."
            } else {
                "Sets total device time for each day. When combined with app or group limits, the first limit reached blocks usage."
            },
            expanded = dailyPolicyExpanded,
            onExpandedChange = onDailyPolicyExpandedChange,
            text = text,
            hardshipLevel = settings.dailyHardshipLevel,
            reserveHeaderTrailingSpace = true,
            showHeaderTrailingOnlyWhenExpanded = true,
            headerTrailing = {
                CompactPolicySwitch(
                    checked = settings.dailyPolicyEnabled,
                    enabled = !settings.dailyPolicyEnabled ||
                        settings.dailyHardshipLevel == HardshipLevel.Off,
                    onCheckedChange = { enabled ->
                        onPolicyDraftChanged(settings.copy(dailyPolicyEnabled = enabled))
                    },
                    hardshipLevel = settings.dailyHardshipLevel,
                )
            },
        ) {
            DayLimitChips(
                dayLabels = text.dayLabels,
                dailyLimits = dailyLimits,
                text = text,
                selectedDayIndex = selectedDayIndex,
                onDaySelected = { index -> selectedDayIndex = index },
            )
            val selectedMinutes = dailyLimits[selectedDayIndex].toIntOrNull()
            MinuteControlPanel(
                valueMinutes = selectedMinutes,
                onValueMinutesChange = { minutes ->
                    val nextDailyLimits = dailyLimits.toMutableList().also { limits ->
                        limits[selectedDayIndex] =
                            minutes.coerceIn(0, DAILY_POLICY_MAX_MINUTES).toString()
                    }
                    updateDraft(nextDailyLimits = nextDailyLimits)
                },
                onRemoveLimit = {
                    val nextDailyLimits = dailyLimits.toMutableList().also { limits ->
                        limits[selectedDayIndex] = ""
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
            HardshipModeFooter(
                policyType = HardshipPolicyType.DailyLimit,
                level = settings.dailyHardshipLevel,
                enabled = settings.dailyPolicyEnabled &&
                    dailyLimits.any { value -> value.toIntOrNull() != null },
                text = text,
                onClick = { hardshipDialogPolicyKey = dailyHardshipKey() },
            )
            if (dailyPolicyLocked) {
                Text(
                    text = text.hardshipLockedLabel(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AppOver,
                )
            } else if (settings.dailyHardshipLevel != HardshipLevel.Off) {
                Text(
                    text = if (text.appLanguage == AppLanguage.Korean) {
                        "요일별 제한을 끄려면 고행 모드를 먼저 해제하세요."
                    } else {
                        "Disable hardship before turning daily limits off."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AppOver,
                )
            }
        }
    }

    val groupPolicy: @Composable ColumnScope.() -> Unit = {
        CollapsiblePolicyCard(
            title = text.appGroups,
            icon = PolicySectionIcon.AppGroups,
            supportingText = if (text.appLanguage == AppLanguage.Korean) {
                "여러 앱이 하나의 시간을 함께 사용합니다"
            } else {
                "Let multiple apps share one time budget"
            },
            collapsedStatusText = if (text.appLanguage == AppLanguage.Korean) {
                "활성 $enabledAppGroupCount/${appGroups.size}"
            } else {
                "$enabledAppGroupCount/${appGroups.size} active"
            },
            collapsedStatusColor = if (enabledAppGroupCount > 0) ScreenRestPalette.Teal else null,
            helpText = if (text.appLanguage == AppLanguage.Korean) {
                "여러 앱이 하나의 시간을 함께 사용합니다. 앱은 한 그룹에만 포함되며, 앱별 제한이 있으면 먼저 끝나는 제한이 적용됩니다."
            } else {
                "Apps share one group budget. An app can belong to one group, and the first group or app limit reached applies."
            },
            expanded = appGroupsExpanded,
            onExpandedChange = onAppGroupsExpandedChange,
            text = text,
            hardshipLevel = settings.hardshipLevelFor(HardshipPolicyType.AppGroups),
        ) {
            val activeGroup = appGroups.firstOrNull { group -> group.id == activeGroupId }
                ?: appGroups.firstOrNull()
            val assignedGroupByPackage = buildMap {
                appGroups.forEach { group ->
                    group.packageNames.forEach { packageName ->
                        if (packageName !in this) {
                            put(packageName, group)
                        }
                    }
                }
            }
            val activeGroupLocked = activeGroup?.let { group ->
                group.hardshipLevel == HardshipLevel.Level3 &&
                    appGroupHardshipKey(group.id) in activeHardshipPolicyKeys
            } == true
            val groupVisibleApps = installedApps
                .filterNot { app -> app.packageName in SafetyGate.neverBlockPackages }
                .filter { app -> app.matchesAppSearch(groupAppSearchQuery) }
                .sortedBy { app -> app.appName.lowercase() }
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
                            enabled = false,
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
                        enabled = !activeGroupLocked,
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
                text = text,
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                appGroups.forEach { group ->
                    AppGroupChip(
                        name = group.name.ifBlank { text.groupName },
                        budgetMinutes = group.limitMinutesOrNull(),
                        appCount = group.packageNames.size,
                        selected = activeGroupId == group.id,
                        text = text,
                        activeDays = group.activeDays,
                        hardshipLevel = group.hardshipLevel,
                        enabled = group.enabled,
                        toggleEnabled = group.hardshipLevel == HardshipLevel.Off,
                        onClick = { activeGroupId = group.id },
                        onEnabledChange = { enabled ->
                            activeGroupId = group.id
                            updateDraft(
                                nextAppGroups = appGroups.replaceGroupById(
                                    group.id,
                                    group.copy(enabled = enabled),
                                ),
                            )
                        },
                    )
                }
            }
            if (activeGroup != null) {
                GroupNameTextField(
                    groupId = activeGroup.id,
                    value = activeGroup.name,
                    label = text.groupName,
                    onValueChange = { value ->
                        if (!activeGroupLocked) {
                            val nextGroups = appGroups.replaceGroupById(
                                activeGroup.id,
                                activeGroup.copy(name = value),
                            )
                            updateDraft(nextAppGroups = nextGroups)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                key(activeGroup.id) {
                    MinuteControlPanel(
                        valueMinutes = activeGroup.limitMinutesOrNull(),
                        onValueMinutesChange = { minutes ->
                            if (!activeGroupLocked) {
                                val nextGroups = appGroups.replaceGroupById(
                                    activeGroup.id,
                                    activeGroup.copy(budgetMinutes = encodeOptionalLimitMinutes(minutes)),
                                )
                                updateDraft(nextAppGroups = nextGroups)
                            }
                        },
                        onRemoveLimit = {
                            if (!activeGroupLocked) {
                                val nextGroups = appGroups.replaceGroupById(
                                    activeGroup.id,
                                    activeGroup.copy(budgetMinutes = 0),
                                )
                                updateDraft(nextAppGroups = nextGroups)
                            }
                        },
                        text = text,
                        title = text.groupBudgetMinutes,
                        minMinutes = 0,
                        maxMinutes = POLICY_MAX_MINUTES,
                        pickerSaveLabel = if (text.appLanguage == AppLanguage.Korean) "저장" else "Save",
                    )
                }
                if (activeGroupLocked) {
                    Text(
                        text = "${text.scheduleDays}: ${scheduleDaysSummary(activeGroup.activeDays, text)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    ScheduleDaysEditor(
                        selectedDays = activeGroup.activeDays,
                        text = text,
                        onDaysChanged = { days ->
                            val nextGroups = appGroups.replaceGroupById(
                                activeGroup.id,
                                activeGroup.copy(activeDays = days.normalizedPolicyDays()),
                            )
                            updateDraft(nextAppGroups = nextGroups)
                        },
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
                if (activeAccessSchedule != null || settings.allowOnlyModeEnabled) {
                    Text(
                        if (text.appLanguage == AppLanguage.Korean) {
                            "현재 실행 범위에서 차단된 앱은 그룹에 새로 넣을 수 없습니다. 허용앱만 또는 스케줄에서 먼저 허용해 주세요. 기존 그룹 앱은 제거할 수 있습니다."
                        } else {
                            "Apps blocked by the active access mode cannot be added to a group. Allow them in allow-only or schedule first. Existing members can still be removed."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
                            val assignedGroup = assignedGroupByPackage[app.packageName]
                            val selectedInActiveGroup = assignedGroup?.id == activeGroup.id
                            val accessScope = accessScopeFor(app.packageName)
                            val unrestricted = app.packageName in expandedExemptPackages
                            val selectable = !activeGroupLocked &&
                                (assignedGroup == null || selectedInActiveGroup) &&
                                (selectedInActiveGroup || (!unrestricted && accessScope?.second != false))
                            GroupAppSelectionRow(
                                app = app,
                                selected = selectedInActiveGroup,
                                assignedGroupName = assignedGroup?.name?.ifBlank { text.groupName },
                                accessScopeLabel = accessScope?.first,
                                accessAllowed = accessScope?.second,
                                enabled = selectable,
                                text = text,
                                onToggle = {
                                    if (selectable) {
                                        updateDraft(
                                            nextAppGroups = appGroups.togglePackageForGroupId(
                                                activeGroup.id,
                                                app.packageName,
                                            ),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
            HardshipModeFooter(
                policyType = HardshipPolicyType.AppGroups,
                level = activeGroup?.hardshipLevel ?: HardshipLevel.Off,
                enabled = activeGroup != null &&
                    activeGroup.limitMinutesOrNull() != null &&
                    activeGroup.packageNames.isNotEmpty(),
                text = text,
                onClick = { activeGroup?.let { group -> hardshipDialogPolicyKey = appGroupHardshipKey(group.id) } },
            )
            if (activeGroupLocked) {
                Text(
                    text = text.hardshipLockedLabel(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AppOver,
                )
            } else if (activeGroup?.hardshipLevel != null && activeGroup.hardshipLevel != HardshipLevel.Off) {
                Text(
                    text = if (text.appLanguage == AppLanguage.Korean) {
                        "고행 모드를 해제한 뒤 이 그룹을 끌 수 있습니다."
                    } else {
                        "Remove hardship before turning this group off."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    val appLimitsSection: @Composable ColumnScope.() -> Unit = {
        CollapsiblePolicyCard(
            title = text.appLimits,
            icon = PolicySectionIcon.AppLimits,
            supportingText = if (text.appLanguage == AppLanguage.Korean) {
                "앱마다 사용할 시간을 정합니다"
            } else {
                "Set a use time for each app"
            },
            collapsedStatusText = if (text.appLanguage == AppLanguage.Korean) {
                "제한 ${appLimits.size}개"
            } else {
                "${appLimits.size} limits"
            },
            collapsedStatusColor = if (appLimits.isNotEmpty()) ScreenRestPalette.Cobalt else null,
            helpText = if (text.appLanguage == AppLanguage.Korean) {
                "앱마다 사용할 시간을 정합니다. 허용앱만 또는 스케줄에서 실행이 허용된 앱에만 실제로 사용할 수 있습니다."
            } else {
                "Sets time per app. The app must also be allowed by the active allow-only mode or schedule to open."
            },
            expanded = appLimitsExpanded,
            onExpandedChange = onAppLimitsExpandedChange,
            text = text,
            hardshipLevel = settings.hardshipLevelFor(HardshipPolicyType.AppLimits),
        ) {
            val visibleApps = installedApps
                .filterNot { app -> app.packageName in SafetyGate.neverBlockPackages }
                .filter { app -> app.matchesAppSearch(appSearchQuery) }
                .filter { app ->
                    when (appLimitFilter) {
                        AppLimitFilter.All -> true
                        AppLimitFilter.Limited -> app.packageName in appLimits
                        AppLimitFilter.Unrestricted -> app.packageName !in appLimits
                    }
                }
                .sortedBy { app -> app.appName.lowercase() }
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
                AppFilterChip(
                    if (text.appLanguage == AppLanguage.Korean) "미설정" else "Not set",
                    appLimitFilter == AppLimitFilter.Unrestricted,
                ) {
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
                        val appHardshipKey = appLimitHardshipKey(app.packageName)
                        val appHardshipLevel = settings.hardshipLevelFor(appHardshipKey)
                        val appLimitLocked = appHardshipLevel == HardshipLevel.Level3 &&
                            appHardshipKey in activeHardshipPolicyKeys
                        val accessScope = accessScopeFor(app.packageName)
                        val unrestricted = app.packageName in expandedExemptPackages
                        AppLimitRow(
                            app = app,
                            limitMinutes = appLimit,
                            extraMinutes = allowance?.extraMinutes ?: 0,
                            unlockedForToday = allowance?.unlockedForToday == true,
                            temporaryRemainingMinutes = allowance?.temporaryRemainingMinutes() ?: 0,
                            text = text,
                            activeDays = appLimitActiveDays[app.packageName] ?: (1..7).toSet(),
                            accessScopeLabel = accessScope?.first,
                            accessAllowed = accessScope?.second,
                            onClick = {
                                when {
                                    unrestricted -> Unit
                                    appLimitLocked -> hardshipDialogPolicyKey = appHardshipKey
                                    accessScope?.second == false -> pendingInaccessibleLimitApp = app
                                    else -> selectedLimitApp = app
                                }
                            },
                            hardshipLevel = appHardshipLevel,
                            onHardshipClick = if (appLimit != null) {
                                { hardshipDialogPolicyKey = appHardshipKey }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }

    pendingInaccessibleLimitApp?.let { app ->
        PolicyAccessWarningDialog(
            appName = app.appName,
            scopeName = if (activeAccessSchedule != null) {
                activeAccessSchedule.name.ifBlank { text.scheduleBlocking }
            } else {
                text.allowOnlyMode
            },
            text = text,
            onDismiss = { pendingInaccessibleLimitApp = null },
            onContinue = {
                if (activeAccessSchedule != null) {
                    val updatedSchedules = settings.normalizedScheduleTemplates().map { schedule ->
                        if (schedule.id == activeAccessSchedule.id) {
                            schedule.copy(allowedPackageNames = schedule.allowedPackageNames + app.packageName)
                        } else {
                            schedule
                        }
                    }
                    onPolicyDraftChanged(
                        settings.copy(scheduleTemplates = updatedSchedules.toScheduleTemplatesEncoded()),
                    )
                } else if (settings.allowOnlyModeEnabled) {
                    onAllowedAppsChanged(allowedAppPackages + app.packageName)
                }
                pendingInaccessibleLimitApp = null
                selectedLimitApp = app
            },
        )
    }

    selectedLimitApp?.let { app ->
        val allocationInfo = appLimitAllocationInfo(app.packageName, appGroups)
        AppLimitPickerDialog(
            app = app,
            initialLimitMinutes = appLimits[app.packageName],
            initialActiveDays = appLimitActiveDays[app.packageName] ?: (1..7).toSet(),
            allocationInfo = allocationInfo,
            text = text,
            onDismiss = { selectedLimitApp = null },
            onApply = { minutes, activeDays ->
                val nextAppLimits = if (minutes == null) {
                    appLimits - app.packageName
                } else {
                    appLimits + (app.packageName to minutes.coerceAtMost(POLICY_MAX_MINUTES))
                }
                val nextActiveDays = if (minutes == null) {
                    appLimitActiveDays - app.packageName
                } else {
                    appLimitActiveDays + (app.packageName to activeDays.normalizedPolicyDays())
                }
                selectedLimitApp = null
                updateDraft(
                    nextAppLimits = nextAppLimits,
                    nextAppLimitActiveDays = nextActiveDays,
                )
            },
        )
    }

    when (contentMode) {
        PolicyContentMode.TimeControls -> {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                ScheduleBlockingCard(
                    settings = settings,
                    installedApps = installedApps,
                    allRestrictionsExemptPackages = allRestrictionsExemptPackages,
                    temporaryAllowedApps = temporaryAllowedApps,
                    text = text,
                    expanded = scheduleBlockingExpanded,
                    onExpandedChange = onScheduleBlockingExpandedChange,
                    onUpdateSettings = onPolicyDraftChanged,
                    activeHardshipPolicyKeys = activeHardshipPolicyKeys,
                    onHardshipConfigure = { key -> hardshipDialogPolicyKey = key },
                )
                AllowOnlyModeCard(
                    settings = settings,
                    installedApps = installedApps,
                    allowedAppPackages = allowedAppPackages,
                    allRestrictionsExemptPackages = allRestrictionsExemptPackages,
                    temporaryAllowedApps = temporaryAllowedApps,
                    text = text,
                    expanded = allowOnlyModeExpanded,
                    onExpandedChange = onAllowOnlyModeExpandedChange,
                    onUpdateSettings = onPolicyDraftChanged,
                    onAllowedAppsChanged = onAllowedAppsChanged,
                    hardshipActive = allowOnlyHardshipKey() in activeHardshipPolicyKeys &&
                        settings.allowOnlyHardshipLevel == HardshipLevel.Level3,
                    onHardshipConfigure = { hardshipDialogPolicyKey = allowOnlyHardshipKey() },
                )
                PolicyExceptionAppsCard(
                    settings = settings,
                    installedApps = installedApps,
                    exemptPackages = allRestrictionsExemptPackages,
                    activeHardshipPolicyKeys = activeHardshipPolicyKeys,
                    text = text,
                    onExemptPackagesChanged = onAllRestrictionsExemptAppsChanged,
                )
            }
        }

        PolicyContentMode.AllControls -> {
            Column(verticalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.lg)) {
                FamilySectionTitle(
                    if (text.appLanguage == AppLanguage.Korean) "시간 제한" else "Time limits",
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
                FamilySectionTitle(
                    if (text.appLanguage == AppLanguage.Korean) "실행 범위" else "App availability",
                )
                ScheduleBlockingCard(
                    settings = settings,
                    installedApps = installedApps,
                    allRestrictionsExemptPackages = allRestrictionsExemptPackages,
                    temporaryAllowedApps = temporaryAllowedApps,
                    text = text,
                    expanded = scheduleBlockingExpanded,
                    onExpandedChange = onScheduleBlockingExpandedChange,
                    onUpdateSettings = onPolicyDraftChanged,
                    activeHardshipPolicyKeys = activeHardshipPolicyKeys,
                    onHardshipConfigure = { key -> hardshipDialogPolicyKey = key },
                )
                AllowOnlyModeCard(
                    settings = settings,
                    installedApps = installedApps,
                    allowedAppPackages = allowedAppPackages,
                    allRestrictionsExemptPackages = allRestrictionsExemptPackages,
                    temporaryAllowedApps = temporaryAllowedApps,
                    text = text,
                    expanded = allowOnlyModeExpanded,
                    onExpandedChange = onAllowOnlyModeExpandedChange,
                    onUpdateSettings = onPolicyDraftChanged,
                    onAllowedAppsChanged = onAllowedAppsChanged,
                    hardshipActive = allowOnlyHardshipKey() in activeHardshipPolicyKeys &&
                        settings.allowOnlyHardshipLevel == HardshipLevel.Level3,
                    onHardshipConfigure = { hardshipDialogPolicyKey = allowOnlyHardshipKey() },
                )
                PolicyExceptionAppsCard(
                    settings = settings,
                    installedApps = installedApps,
                    exemptPackages = allRestrictionsExemptPackages,
                    activeHardshipPolicyKeys = activeHardshipPolicyKeys,
                    text = text,
                    onExemptPackagesChanged = onAllRestrictionsExemptAppsChanged,
                )
            }
        }
    }

    hardshipDialogPolicyKey?.let { policyKey ->
        val targetName = when (policyKey.policyType) {
            HardshipPolicyType.AppGroups -> appGroups.firstOrNull { group -> group.id == policyKey.targetId }?.name.orEmpty()
            HardshipPolicyType.AppLimits -> installedApps
                .firstOrNull { app -> app.packageName == policyKey.targetId }
                ?.appName
                ?: policyKey.targetId
            HardshipPolicyType.Schedule -> settings.normalizedScheduleTemplates()
                .firstOrNull { schedule -> schedule.id == policyKey.targetId }
                ?.name
                .orEmpty()
            else -> ""
        }
        HardshipModeDialog(
            policyKey = policyKey,
            policyType = policyKey.policyType,
            targetName = targetName,
            currentLevel = settings.hardshipLevelFor(policyKey),
            level3Locked = policyKey in activeHardshipPolicyKeys &&
                settings.hardshipLevelFor(policyKey) == HardshipLevel.Level3,
            allRestrictionsExemptAppCount = allRestrictionsExemptPackages.size,
            configurationReflectionReadyAtMillis =
                hardshipRuntimeState.configurationReflectionReadyAtMillis(policyKey),
            text = text,
            onDismiss = { hardshipDialogPolicyKey = null },
            onStartConfigurationReflection = {
                onStartHardshipConfigurationReflection(policyKey)
            },
            onApply = { level, adminPin ->
                hardshipDialogPolicyKey = null
                onHardshipPolicyDraftChanged(
                    settings.withHardshipLevel(policyKey, level),
                    adminPin,
                )
            },
        )
    }
}

@Composable
fun SimpleCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
fun OptionalSimpleCard(
    wrapInCard: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (wrapInCard) {
        SimpleCard(content)
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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

    val shape = RoundedCornerShape(14.dp)
    Surface(
        modifier = modifier,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.78f)),
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
    if (adding && any { group -> group.id != groupId && packageName in group.packageNames }) {
        return this
    }
    return map { group ->
        when {
            group.id == groupId && adding -> group.copy(packageNames = group.packageNames + packageName)
            group.id == groupId -> group.copy(packageNames = group.packageNames - packageName)
            else -> group
        }
    }
}

fun buildUsagePolicySettings(
    base: UsagePolicySettings,
    dailyLimits: List<String>,
    appGroups: List<AppGroupPolicy>,
    appLimits: Map<String, Int>,
    appLimitActiveDays: Map<String, Set<Int>>,
): UsagePolicySettings {
    val cleanAppLimits = appLimits
        .filter { (packageName, minutes) -> packageName.isNotBlank() && minutes >= 0 }
        .mapValues { (_, minutes) -> minutes.coerceIn(0, POLICY_MAX_MINUTES) }
    val cleanGroups = appGroups.normalizedForEditing()
        .map { group ->
            group.copy(
                budgetMinutes = group.budgetMinutes.coerceIn(
                    com.manisykh.screenrest.data.EXPLICIT_ZERO_LIMIT_STORAGE_MINUTES,
                    POLICY_MAX_MINUTES,
                ),
            )
        }
    val cleanAppLimitActiveDays = cleanAppLimits.keys.associateWith { packageName ->
        appLimitActiveDays[packageName]?.normalizedPolicyDays() ?: (1..7).toSet()
    }
    val safeDailyLimits = (0..6).map { index ->
        dailyLimits.getOrNull(index)
            ?.trim()
            ?.toIntOrNull()
            ?.coerceIn(0, DAILY_POLICY_MAX_MINUTES)
    }
    val primaryGroup = cleanGroups.firstOrNull()
    return base.copy(
        weekdayLimitMinutes = encodeOptionalLimitMinutes(safeDailyLimits[0]),
        weekendLimitMinutes = encodeOptionalLimitMinutes(safeDailyLimits[5]),
        mondayLimitMinutes = encodeOptionalLimitMinutes(safeDailyLimits[0]),
        tuesdayLimitMinutes = encodeOptionalLimitMinutes(safeDailyLimits[1]),
        wednesdayLimitMinutes = encodeOptionalLimitMinutes(safeDailyLimits[2]),
        thursdayLimitMinutes = encodeOptionalLimitMinutes(safeDailyLimits[3]),
        fridayLimitMinutes = encodeOptionalLimitMinutes(safeDailyLimits[4]),
        saturdayLimitMinutes = encodeOptionalLimitMinutes(safeDailyLimits[5]),
        sundayLimitMinutes = encodeOptionalLimitMinutes(safeDailyLimits[6]),
        appGroupName = primaryGroup?.name.orEmpty(),
        appGroupPackages = primaryGroup?.packageNames.orEmpty().sorted().joinToString(","),
        appGroupBudgetMinutes = primaryGroup?.budgetMinutes ?: 0,
        appGroups = cleanGroups.toAppGroupsEncoded(),
        appLimitRules = cleanAppLimits.toAppLimitRules(),
        appLimitActiveDays = cleanAppLimitActiveDays.toAppLimitActiveDaysEncoded(),
    )
}

fun List<AppGroupPolicy>.normalizedForEditing(): List<AppGroupPolicy> {
    val assignedPackages = mutableSetOf<String>()
    val cleanGroups = mapIndexed { index, group ->
        group.copy(
            name = group.name,
            packageNames = group.packageNames
                .filter { packageName -> packageName.isNotBlank() && assignedPackages.add(packageName) }
                .toSet(),
            budgetMinutes = group.budgetMinutes.coerceIn(
                com.manisykh.screenrest.data.EXPLICIT_ZERO_LIMIT_STORAGE_MINUTES,
                POLICY_MAX_MINUTES,
            ),
            activeDays = group.activeDays.normalizedPolicyDays(),
            id = group.id.ifBlank { "legacy-$index" },
        )
    }
    return cleanGroups
}

fun newAppGroupId(): String = UUID.randomUUID().toString()
fun newScheduleTemplateId(): String = UUID.randomUUID().toString()

private const val POLICY_MAX_MINUTES = 720
private const val REMOTE_PARENT_MAX_EXTRA_MINUTES = POLICY_MAX_MINUTES
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

private fun AppStrings.recordedDaysLabel(recordedDays: Int, totalDays: Int): String {
    return if (appLanguage == AppLanguage.Korean) {
        "${totalDays}일 중 ${recordedDays}일 기록"
    } else {
        "$recordedDays of $totalDays days recorded"
    }
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

private fun formatDateTime(timestampMillis: Long): String {
    return SimpleDateFormat("M/d HH:mm", Locale.getDefault()).format(Date(timestampMillis))
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

private fun AppStrings.hardshipModeTitle(): String =
    if (appLanguage == AppLanguage.Korean) "고행 모드" else "Hardship mode"

private fun AppStrings.shortSaveLabel(): String =
    if (appLanguage == AppLanguage.Korean) "저장" else "Save"

private fun AppStrings.shortPolicyUpToDateLabel(): String =
    if (appLanguage == AppLanguage.Korean) "완료" else "OK"

private fun AppStrings.generatePairingCodePinInstruction(): String =
    if (appLanguage == AppLanguage.Korean) {
        "자녀 기기 연결 코드를 생성하려면 관리 PIN을 입력하세요."
    } else {
        "Enter the admin PIN to generate a child device pairing code."
    }

private fun AppStrings.registerChildDevicePinInstruction(): String =
    if (appLanguage == AppLanguage.Korean) {
        "자녀 기기를 등록하려면 관리 PIN을 입력하세요."
    } else {
        "Enter the admin PIN to register the child device."
    }

private fun AppStrings.hardshipLevel3ShortContinueLabel(): String =
    if (appLanguage == AppLanguage.Korean) "다음" else "Next"

private fun AppStrings.hardshipLevel3ShortFinalApplyLabel(): String =
    if (appLanguage == AppLanguage.Korean) "적용" else "Apply"

private fun AppStrings.hardshipConfigureLabel(): String =
    if (appLanguage == AppLanguage.Korean) "고행 모드 설정" else "Configure hardship mode"

private fun AppStrings.hardshipChangeLabel(): String =
    if (appLanguage == AppLanguage.Korean) "설정 변경" else "Change settings"

private fun AppStrings.hardshipDisableLabel(): String =
    if (appLanguage == AppLanguage.Korean) "고행 모드 사용 안 함" else "Turn off hardship mode"

private fun AppStrings.hardshipNeedsPolicyLabel(): String =
    if (appLanguage == AppLanguage.Korean) "먼저 이 차단 정책을 설정하고 활성화해 주세요." else
        "Configure and enable this blocking policy first."

private fun AppStrings.hardshipLockedLabel(): String =
    if (appLanguage == AppLanguage.Korean) {
        "고행 3단계 적용 중에는 이 정책을 약화하거나 해제할 수 없습니다."
    } else {
        "This policy cannot be weakened or disabled while hardship level 3 is active."
    }

private fun AppStrings.hardshipDialogTitle(): String =
    if (appLanguage == AppLanguage.Korean) "고행 모드 단계 설정" else "Set hardship level"

private fun AppStrings.hardshipLevelLabel(level: HardshipLevel): String {
    return if (appLanguage == AppLanguage.Korean) {
        "${level.storageValue}단계"
    } else {
        "Level ${level.storageValue}"
    }
}

private fun AppStrings.hardshipLevelName(level: HardshipLevel): String {
    return when (level) {
        HardshipLevel.Off -> if (appLanguage == AppLanguage.Korean) "사용 안 함" else "Off"
        HardshipLevel.Level1 -> if (appLanguage == AppLanguage.Korean) "숙고" else "Reflect"
        HardshipLevel.Level2 -> if (appLanguage == AppLanguage.Korean) "통제" else "Controlled"
        HardshipLevel.Level3 -> if (appLanguage == AppLanguage.Korean) "절대 집중" else "Absolute focus"
    }
}

private fun AppStrings.hardshipPolicyLabel(policyType: HardshipPolicyType): String {
    return when (policyType) {
        HardshipPolicyType.DailyLimit -> dailyPolicy
        HardshipPolicyType.AppGroups -> appGroups
        HardshipPolicyType.AppLimits -> appLimits
        HardshipPolicyType.Schedule -> scheduleBlocking
        HardshipPolicyType.AllowOnly -> allowOnlyMode
    }
}

private fun AppStrings.hardshipDialogTarget(policyType: HardshipPolicyType): String =
    if (appLanguage == AppLanguage.Korean) {
        "적용 대상 · ${hardshipPolicyLabel(policyType)}"
    } else {
        "Applies to · ${hardshipPolicyLabel(policyType)}"
    }

private fun AppStrings.hardshipPolicyDescription(policyType: HardshipPolicyType): String {
    return if (appLanguage == AppLanguage.Korean) {
        "${hardshipPolicyLabel(policyType)}으로 차단됐을 때 사용할 해제 방식을 제한합니다."
    } else {
        "Restrict the unlock methods available when ${hardshipPolicyLabel(policyType)} blocks an app."
    }
}

private fun AppStrings.hardshipConfiguredDescription(level: HardshipLevel): String =
    if (appLanguage == AppLanguage.Korean) {
        "${hardshipLevelLabel(level)} · ${hardshipLevelName(level)} 설정됨"
    } else {
        "${hardshipLevelLabel(level)} · ${hardshipLevelName(level)} configured"
    }

private fun AppStrings.hardshipLevelDescription(
    level: HardshipLevel,
    policyType: HardshipPolicyType,
): String {
    if (appLanguage != AppLanguage.Korean) {
        return when (level) {
            HardshipLevel.Off -> "Use the standard unlock controls."
            HardshipLevel.Level1 -> "Wait 2 minutes, then use a fixed 5-minute temporary allowance."
            HardshipLevel.Level2 -> "Temporary allowances are disabled. Ask a parent, or wait 30 minutes and enter the Admin PIN."
            HardshipLevel.Level3 -> "The Admin PIN cannot normally unlock level 3. Parent approval is disabled, and one app-scoped Emergency Pass is shared every 7 days."
        }
    }
    return when (level) {
        HardshipLevel.Off -> "기본 해제 기능을 사용합니다."
        HardshipLevel.Level1 -> "2분을 기다린 후 고정된 5분 임시 허용을 사용할 수 있습니다."
        HardshipLevel.Level2 -> "임시 허용은 사용할 수 없습니다. 부모 승인을 받거나 30분 숙고 후 관리 PIN으로 종료합니다."
        HardshipLevel.Level3 -> when (policyType) {
            HardshipPolicyType.Schedule -> "스케줄이 끝날 때까지 관리 PIN 일반 해제와 부모 승인을 사용할 수 없습니다. Emergency Pass는 현재 앱에만 적용되며 모든 3단계에서 7일에 1회 사용할 수 있습니다."
            else -> "정책이 끝날 때까지 관리 PIN 일반 해제와 부모 승인을 사용할 수 없습니다. Emergency Pass는 현재 앱에만 적용되며 모든 3단계에서 7일에 1회 사용할 수 있습니다."
        }
    }
}

private fun AppStrings.hardshipLevel3Warning(policyType: HardshipPolicyType): String {
    return if (appLanguage == AppLanguage.Korean) {
        when (policyType) {
            HardshipPolicyType.Schedule -> "주의: 차단 스케줄 종료 전에는 관리 PIN, 시간 추가, 오늘만 허용을 사용할 수 없습니다."
            HardshipPolicyType.AllowOnly -> "주의: 오늘 자정까지 관리 PIN, 시간 추가, 오늘만 허용을 사용할 수 없으며 이후 허용 앱만 모드가 종료됩니다."
            else -> "주의: 다음 날 사용량이 초기화되기 전에는 관리 PIN, 시간 추가, 오늘만 허용을 사용할 수 없습니다."
        }
    } else {
        "Warning: admin PIN, extra time, unlock-today, and parent approval are unavailable while this policy is active."
    }
}

private fun AppStrings.hardshipLevel3FinalTitle(): String =
    if (appLanguage == AppLanguage.Korean) "3단계를 정말 적용하시겠습니까?" else
        "Apply level 3?"

private fun AppStrings.hardshipLevel3FinalBody(): String =
    if (appLanguage == AppLanguage.Korean) {
        "차단이 시작되면 관리 PIN만으로 일반 해제할 수 없고 부모 승인도 사용할 수 없습니다. Emergency Pass는 현재 앱에만 적용되며 모든 고행 3단계에 공통으로 7일에 한 번만 사용할 수 있습니다."
    } else {
        "Once blocking starts, the Admin PIN cannot normally unlock it and parent approval is unavailable. Emergency Pass applies only to the current app and is shared by all level-3 policies once every 7 days."
    }

private fun AppStrings.hardshipLevel3FinalApplyLabel(): String =
    if (appLanguage == AppLanguage.Korean) "이해하고 적용" else "Understand and apply"

private fun AppStrings.hardshipLevel3ContinueLabel(): String =
    if (appLanguage == AppLanguage.Korean) "다음 경고 확인" else "Review final warning"

private fun AppStrings.hardshipLevel3ConfirmationPhrase(): String =
    if (appLanguage == AppLanguage.Korean) {
        "3단계 진입을 이해했고 허용합니다"
    } else {
        "I understand and allow Level 3"
    }

private fun AppStrings.hardshipLevel3ConfirmationInstruction(): String =
    if (appLanguage == AppLanguage.Korean) {
        "최종 적용하려면 아래 문구를 그대로 입력하세요."
    } else {
        "Type the phrase below exactly to continue."
    }

private fun AppStrings.hardshipLevel3ConfirmationLabel(): String =
    if (appLanguage == AppLanguage.Korean) "확인 문구 입력" else "Confirmation phrase"

private fun AppStrings.hardshipLevel3IrreversibleTitle(): String =
    if (appLanguage == AppLanguage.Korean) "마지막 확인 · 시작 후 취소 불가" else
        "Final confirmation · cannot be cancelled after start"

private fun AppStrings.hardshipLevel3IrreversibleBody(policyType: HardshipPolicyType): String {
    return if (appLanguage == AppLanguage.Korean) {
        when (policyType) {
            HardshipPolicyType.Schedule ->
                "차단이 시작되면 해당 스케줄이 끝날 때까지 3단계를 낮추거나 해제할 수 없습니다."
            else ->
                "차단이 시작되면 다음 날 사용량이 초기화될 때까지 3단계를 낮추거나 해제할 수 없습니다."
        }
    } else {
        when (policyType) {
            HardshipPolicyType.Schedule ->
                "After blocking starts, level 3 cannot be lowered or disabled until the schedule ends."
            else ->
                "After blocking starts, level 3 cannot be lowered or disabled until the next daily reset."
        }
    }
}

private fun AppStrings.hardshipPinInstruction(): String =
    if (appLanguage == AppLanguage.Korean) {
        "고행 모드를 적용하거나 해제하려면 관리 PIN이 필요합니다."
    } else {
        "The admin PIN is required to apply or turn off hardship mode."
    }

private fun AppStrings.hardshipApplyLabel(level: HardshipLevel): String =
    if (appLanguage == AppLanguage.Korean) "${hardshipLevelLabel(level)} 적용" else "Apply ${hardshipLevelLabel(level)}"

class AppStrings {
    var appLanguage: AppLanguage = AppLanguage.English
    var appTitle: String = "ScreenRest"
    var appSubtitle: String = "Manager \u00B7 Today"
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
    var updating: String = ""
    var notUpdatedYet: String = ""
    var lastUpdated: (String) -> String = { _ -> "" }
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
    var saveNow: String = ""
    var savingChanges: String = ""
    var pendingChangesHint: String = ""
    var saveInstructions: String = ""
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
    var temporaryAllowancePolicy: String = ""
    var temporaryAllowanceRemaining: (Int) -> String = { _ -> "" }
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
    var parentDeviceRole: String = ""
    var childDeviceMode: String = ""
    var parentDeviceMode: String = ""
    var childPairingCode: String = ""
    var childPairingCodeHint: String = ""
    var generatePairingCode: String = ""
    var registerChildDevice: String = ""
    var linkedChildDevices: String = ""
    var linkedParentDevices: String = "연결된 부모 기기"
    var parentLinked: String = ""
    var parentNotLinked: String = ""
    var profileName: String = "내 이름"
    var profileSaved: String = ""
    var profileSyncHint: String = ""
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
    var remoteRequests: String = ""
    var noRemoteRequests: String = ""
    var reject: String = ""
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
        this.appLanguage = appLanguage
        appTitle = "ScreenRest"
        appSubtitle = "Manager \u00B7 Today"
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
        developerSafeMode = "Protection controls"
        safeModeOn = "Protection paused"
        safeModeOff = "Protection active"
        status = "Status"
        blockingDisabled = "Blocking Disabled"
        safetyChecksRequired = "Safety Checks Required"
        policyEnforcement = "Rule enforcement"
        policyEnforcementEnabled = "Rules on"
        policyEnforcementDisabled = "Rules off"
        autoRecoveryReady = "Auto Recovery Ready"
        autoRecoveryEnabledSafeMode = "Auto Recovery Enabled Safe Mode"
        emergencyUnlock = "Safe Recovery"
        developerPin = "Admin PIN"
        unlock = "Recover"
        offlinePinAvailable = "Admin PIN is available offline"
        safeModeEnabled = "Safe Mode Enabled"
        invalidPin = "Invalid Pin"
        todayUsage = "Today Usage"
        usageAccessChecking = "Usage Access Checking"
        usageAccessRequired = "Usage Access Required"
        openUsageAccessSettings = "Open Usage Access Settings"
        refresh = "Refresh"
        updating = "Updating"
        notUpdatedYet = "Not updated yet"
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
        unlockedToday = "Allowed Today"
        addLimit = "Add Limit"
        clearLimit = "Clear Limit"
        applyLimit = "Apply Limit"
        newGroup = "New Group"
        noSelectableApps = "No Selectable Apps"
        savePolicy = "Save Policy"
        saveNow = "Save Now"
        savingChanges = "Saving Changes"
        pendingChangesHint = "Changes are not saved yet. Tap Save now to apply them."
        saveInstructions = "Enter the Admin PIN to save."
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
        scheduleAllowedDescription = "Selected apps can open during this schedule; daily, group, and app limits still apply."
        scheduleAllowedTemplateHint = "Each schedule has its own allowed-app list."
        activeSchedule = "Active Schedule"
        nextSchedule = "Next Schedule"
        nextDay = "next day"
        noActiveSchedule = "No Active Schedule"
        scheduleDiagnostics = "Schedule Diagnostics"
        allowOnlyMode = "Allow Only Mode"
        allowOnlyModeDescription = "Allow only selected apps while this mode is active."
        allowOnlyModeSummary = "Only selected apps can open; daily, group, and app limits still apply."
        policyEnforcementStillDisabled = "Rule enforcement is still off"
        policySaved = "Policy Saved"
        invalidAdminPin = "Invalid Admin Pin"
        adminPin = "Admin Pin"
        cancel = "Cancel"
        safeModePinRequiredTitle = "Pause protection"
        safeModePinRequiredDescription = "Enter the Admin PIN to pause blocking while keeping the saved rules."
        safeModePinAccepted = "Protection paused"
        enableSafeMode = "Pause protection"
        policyOffPinRequiredTitle = "Turn off rule enforcement"
        policyOffPinRequiredDescription = "Monitoring and blocking will stop. Enter the Admin PIN to continue."
        policyOffPinAccepted = "Rule enforcement turned off"
        disablePolicyEnforcement = "Turn rules off"
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
        alwaysAllowedApps = "Allow-only Apps"
        alwaysAllowedDescription = "These apps can open in allow-only mode, while time limits still apply."
        requiredAllowedApps = "Required Allowed Apps"
        userAllowedApps = "User Allowed Apps"
        allow = "Allow"
        allowed = "Allowed"
        showList = "Show List"
        hideList = "Hide List"
        pinSettings = "Pin Settings"
        currentAdminPin = "Current Admin Pin"
        newAdminPin = "New Admin Pin"
        currentEmergencyPin = "Current Admin PIN"
        newEmergencyPin = "New Admin PIN"
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
        policyEnforcementReady = "Rule enforcement ready"
        usageAccessReady = "Usage Access Ready"
        whitelistReady = "Whitelist Ready"
        emergencyUnlockReady = "Safe Recovery"
        openOverlaySettings = "Open Overlay Settings"
        openNotificationAccessSettings = "Open Notification Access Settings"
        openExactAlarmSettings = "Open Exact Alarm Settings"
        blockSafetyStatus = "Block Safety Status"
        currentBlockTargets = "Current Block Targets"
        temporaryAllowances = "Temporary Allowances"
        temporaryAllowancePolicy = "Exceptions approved from the block screen and active now."
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
        parentDeviceRole = "Device Role"
        childDeviceMode = "Child Device"
        parentDeviceMode = "Parent Device"
        childPairingCode = "Pairing Code"
        childPairingCodeHint = "Use this code to connect this child device from a parent device."
        generatePairingCode = "Generate Code"
        registerChildDevice = "Register Child"
        linkedChildDevices = "Linked Child Devices"
        linkedParentDevices = "Linked Parent Devices"
        parentLinked = "Parent Linked"
        parentNotLinked = "Parent Not Linked"
        profileName = "Profile Name"
        profileSaved = "Profile Saved"
        profileSyncHint = "Linked devices will show this name after sync."
        parentAccount = "Parent Display Name"
        childDeviceName = "Child Nickname"
        childDeviceId = "Child Device Id"
        lastSync = "Last Sync"
        connectParent = "Connect Parent"
        unlinkParent = "Unlink Parent"
        syncNow = "Sync Now"
        adminPinRole = "Admin Pin Role"
        emergencyPinRole = "Emergency Pass is a limited level-3 exception, not a separate PIN"
        remoteTestMode = "Remote Test Mode"
        remoteDailyLimit = "Remote Daily Limit"
        remoteAppTarget = "Remote App Target"
        remoteExtraTime = "Remote Extra Time"
        remoteAddTime = "Remote Add Time"
        remoteUnlockToday = "Remote Unlock Today"
        remoteRequests = "Remote Requests"
        noRemoteRequests = "No Remote Requests"
        reject = "Reject"
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
        globalAllowedPolicy = "Allow-only apps can open in that mode but do not bypass time limits."
        scheduleAllowedPolicy = "Schedule-allowed apps can open only while that schedule is active; time limits still apply."
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
        safeModeOn = "Protection paused"
        safeModeOff = "Protection active"
        todayUsage = "Today Usage"
        policySummary = "Policy Summary"
        dailyPolicyDescription = "Set the total screen time budget for each day."
        appGroups = "App Groups"
        appGroupsDescription = "Group related apps and assign a shared budget."
        appLimits = "App Limits"
        appLimitsDescription = "Set limits for individual apps."
        noLimit = "No limit"
        savePolicy = "Save"
        saveNow = "Save now"
        savingChanges = "Saving"
        pendingChangesHint = "Changes are not saved yet. Tap Save now to apply them."
        saveInstructions = "Enter the Admin PIN to save."
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
        groupBudgetTotal = { total, _ -> "Configured group limits " + formatLimitMinutesLabel(total) + " · independently enforced" }
        activeAppLimits = { count -> count.toString() + " active" }
        minutesPerDay = { minutes -> formatLimitMinutesLabel(minutes) + "/day" }
        appLimitGroupAllowance = { groupNameValue, _, groupBudget -> "The first limit reached applies: app limit or " + groupNameValue + " group (" + formatLimitMinutesLabel(groupBudget) + ")" }
        appGroupLimitConflict = { count -> count.toString() + " apps need a policy review" }
        allowedAppCount = { count -> count.toString() + " allowed" }
        startsIn = { duration -> "in " + duration }
        temporaryAllowanceDetail = { remaining, extra -> "Remaining extra " + formatLimitMinutesLabel(remaining) + " / added " + formatLimitMinutesLabel(extra) }
        temporaryAllowanceRemaining = { remaining -> "Temporary · " + formatLimitMinutesLabel(remaining) + " left" }
        lastUpdated = { time -> "Updated " + time }
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
                BlockDecision.WouldBlockImmediate -> "Parent block"
            }
        }
        if (appLanguage == AppLanguage.Korean) {
            applyKoreanStrings()
        }
    }
}

private fun AppStrings.applyKoreanStrings() {
    appTitle = "폰쉼"
    appSubtitle = "관리 \u00B7 오늘"
    korean = "한국어"
    overview = "개요"
    time = "시간"
    timeTabDescription = "요일별 예산, 앱 그룹, 앱별 제한을 설정합니다"
    blocking = "차단"
    blockingTabDescription = "스케줄, 허용앱만 모드, 제한 없이 사용할 앱을 관리합니다"
    stats = "통계"
    safety = "안전"
    settings = "설정"
    expandSection = "열기"
    collapseSection = "접기"
    language = "언어"
    todayStatus = "오늘 상태"
    developerSafeMode = "보호 제어"
    safeModeOn = "보호 일시 중지"
    safeModeOff = "보호 작동 중"
    status = "상태"
    blockingDisabled = "차단 비활성화"
    safetyChecksRequired = "차단 전 안전 확인 필요"
    policyEnforcement = "규칙 적용"
    policyEnforcementEnabled = "규칙 적용 중"
    policyEnforcementDisabled = "규칙 적용 꺼짐"
    autoRecoveryReady = "자동 복구 준비됨"
    autoRecoveryEnabledSafeMode = "자동 복구로 안전 모드 전환됨"
    emergencyUnlock = "안전 복구"
    developerPin = "관리 PIN"
    unlock = "복구"
    offlinePinAvailable = "관리 PIN으로 오프라인 복구 가능"
    safeModeEnabled = "안전 모드 활성화"
    invalidPin = "PIN이 올바르지 않습니다"
    todayUsage = "오늘 사용"
    usageAccessChecking = "사용정보 권한 확인 중"
    usageAccessRequired = "사용정보 접근 권한이 필요합니다"
    openUsageAccessSettings = "권한 설정"
    refresh = "새로고침"
    updating = "업데이트 중"
    notUpdatedYet = "업데이트 전"
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
    unlockedToday = "오늘만 허용"
    addLimit = "추가"
    clearLimit = "해제"
    applyLimit = "적용"
    newGroup = "+ 새 그룹"
    noSelectableApps = "선택 가능한 앱이 없습니다"
    savePolicy = "변경사항 저장"
    saveNow = "지금 저장"
    savingChanges = "저장 중"
    pendingChangesHint = "지금 저장을 눌러야 변경 사항이 적용됩니다"
    saveInstructions = "관리 PIN을 입력해 저장하세요"
    resetChanges = "변경 취소"
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
    scheduleAllowedDescription = "선택한 앱은 이 스케줄 동안 실행할 수 있으며 요일별·그룹·앱별 시간 제한은 계속 적용됩니다"
    scheduleAllowedTemplateHint = "스케줄마다 허용 앱을 따로 설정할 수 있습니다"
    activeSchedule = "활성"
    nextSchedule = "다음"
    nextDay = "다음날"
    noActiveSchedule = "현재 적용 중인 스케줄이 없습니다"
    scheduleDiagnostics = "스케줄 진단"
    allowOnlyMode = "허용 앱만"
    allowOnlyModeDescription = "필수 앱과 허용한 앱만 실행할 수 있습니다"
    allowOnlyModeSummary = "선택한 앱만 실행할 수 있으며 요일별·그룹·앱별 시간 제한은 계속 적용됩니다"
    policyEnforcementStillDisabled = "규칙 적용은 아직 꺼져 있습니다"
    policySaved = "저장됨"
    invalidAdminPin = "관리 PIN이 올바르지 않습니다"
    adminPin = "관리 PIN"
    cancel = "취소"
    safeModePinRequiredTitle = "보호 일시 중지"
    safeModePinRequiredDescription = "저장된 규칙은 유지하고 차단만 중지합니다. 계속하려면 관리 PIN을 입력하세요."
    safeModePinAccepted = "보호가 일시 중지되었습니다"
    enableSafeMode = "보호 중지"
    policyOffPinRequiredTitle = "규칙 적용 끄기"
    policyOffPinRequiredDescription = "감시와 차단이 중지됩니다. 계속하려면 관리 PIN을 입력하세요."
    policyOffPinAccepted = "규칙 적용이 꺼졌습니다"
    disablePolicyEnforcement = "규칙 끄기"
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
    alwaysAllowedApps = "허용앱만 허용 앱"
    alwaysAllowedDescription = "허용앱만 모드에서 실행할 수 있으며 시간 제한은 계속 적용됩니다"
    requiredAllowedApps = "필수 허용 앱"
    userAllowedApps = "사용자 허용 앱"
    allow = "허용"
    allowed = "허용됨"
    showList = "펼치기"
    hideList = "접기"
    pinSettings = "PIN 설정"
    currentAdminPin = "현재 관리 PIN"
    newAdminPin = "새 관리 PIN"
    currentEmergencyPin = "현재 관리 PIN"
    newEmergencyPin = "새 관리 PIN"
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
    policyEnforcementReady = "규칙 적용 중"
    usageAccessReady = "사용정보 권한"
    whitelistReady = "필수 예외 목록"
    emergencyUnlockReady = "안전 복구"
    openOverlaySettings = "오버레이 설정"
    openNotificationAccessSettings = "알림 접근 설정"
    openExactAlarmSettings = "알람 설정"
    blockSafetyStatus = "차단 안전성 검증"
    currentBlockTargets = "현재 차단 대상"
    temporaryAllowances = "일시 허용"
    temporaryAllowancePolicy = "블록창에서 승인되어 현재 적용 중인 예외입니다"
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
    parentDeviceRole = "기기 역할"
    childDeviceMode = "자녀 기기"
    parentDeviceMode = "부모 기기"
    childPairingCode = "연결 코드"
    childPairingCodeHint = "부모 기기에서 이 코드를 입력해 자녀 기기를 연결합니다"
    generatePairingCode = "코드 생성"
    registerChildDevice = "자녀 등록"
    linkedChildDevices = "연결된 자녀 기기"
    parentLinked = "연결됨"
    parentNotLinked = "미연결"
    profileName = "내 이름"
    profileSaved = "프로필 저장 완료"
    profileSyncHint = "연결된 기기에는 동기화 후 이 이름이 표시됩니다"
    parentAccount = "부모 계정"
    childDeviceName = "자녀 기기 이름"
    childDeviceId = "기기 연결 ID"
    lastSync = "마지막 동기화"
    connectParent = "연결"
    unlinkParent = "연결 해제"
    syncNow = "동기화"
    adminPinRole = "관리 PIN: 정책 저장, 안전 모드 전환, 차단 화면의 시간 추가에 사용합니다"
    emergencyPinRole = "Emergency Pass는 별도 PIN이 아니라 고행 3단계의 제한된 예외 권한입니다"
    remoteTestMode = "원격 명령 테스트"
    remoteDailyLimit = "하루 전체"
    remoteAppTarget = "앱 선택"
    remoteExtraTime = "추가 시간"
    remoteAddTime = "시간 추가"
    remoteUnlockToday = "오늘만 해제"
    remoteRequests = "자녀 요청"
    noRemoteRequests = "아직 자녀 요청이 없습니다"
    reject = "거절"
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
    globalAllowedPolicy = "허용앱만 허용 앱은 해당 모드에서 실행할 수 있지만 시간 제한을 우회하지 않습니다"
    scheduleAllowedPolicy = "스케줄 허용 앱은 해당 스케줄 동안만 실행할 수 있으며 시간 제한은 계속 적용됩니다"
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
    groupBudgetTotal = { total, _ ->
        "설정된 그룹 제한 ${formatLimitMinutesLabel(total)} · 각 제한 독립 적용"
    }
    activeAppLimits = { count -> "활성 ${count}개" }
    minutesPerDay = { minutes -> formatLimitMinutesLabel(minutes) + "/일" }
    appLimitGroupAllowance = { groupNameValue, _, groupBudget ->
        "앱별 제한과 ${groupNameValue} 그룹 제한 ${formatLimitMinutesLabel(groupBudget)} 중 먼저 도달한 제한이 적용됩니다"
    }
    appGroupLimitConflict = { count -> "정책 확인이 필요한 앱 ${count}개" }
    allowedAppCount = { count -> "허용 앱 ${count}개" }
    startsIn = { duration -> "${duration} 후" }
    temporaryAllowanceDetail = { remaining, extra ->
        "남은 추가 ${formatLimitMinutesLabel(remaining)} / 추가 ${formatLimitMinutesLabel(extra)}"
    }
    temporaryAllowanceRemaining = { remaining -> "일시 허용 · ${formatLimitMinutesLabel(remaining)} 남음" }
    lastUpdated = { time -> "업데이트 ${time}" }
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
            "parent immediate block active" -> "부모 차단"
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
            BlockDecision.WouldBlockImmediate -> "부모 차단"
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
    ScreenRestDesignTheme {
        ScreenTimeManagerScreen(
            uiState = SafeModeUiState(safeModeEnabled = true),
            parentAccountAuthState = ParentAccountAuthState(),
            onSafeModeChanged = {},
            onSafeModeEnableWithPin = {},
            onSafeModePinStatusSeen = {},
            onPolicyEnforcementChanged = {},
            onPolicyEnforcementDisableWithPin = {},
            onAppLanguageChanged = {},
            onWarningNotificationsChanged = {},
            onLimitNotificationsChanged = {},
            onSafeRecovery = {},
            onSafeRecoveryPinChanged = {},
            onAllowedAppsChanged = {},
            onAllRestrictionsExemptAppsChanged = {},
            onOpenUsageAccessSettings = {},
            onOpenNotificationAccessSettings = {},
            onOpenExactAlarmSettings = {},
            onOpenOverlaySettings = {},
            onRefreshUsageStats = {},
            onRefreshStatistics = {},
            onPolicyDraftChanged = {},
            onStartHardshipConfigurationReflection = {},
            onResetPolicyDraft = {},
            onSaveUsagePolicy = {},
            onPolicySaveStatusSeen = {},
            onRequestNotificationPermission = {},
            onUpdateAdminPin = { _, _ -> },
            onPinInputChanged = {},
            onPairParentAccount = { _, _, _ -> },
            onParentProfileNameChanged = {},
            onParentDeviceRoleChanged = { _, _ -> },
            onGenerateChildPairingCode = {},
            onRegisterChildPairingCode = { _, _, _ -> },
            onParentGoogleSignIn = {},
            onDeleteAccountAndCloudData = {},
            onUnlinkParentAccount = {},
            onUnlinkLinkedChildDevice = { _, _ -> },
            onUnlinkLinkedParentDevice = { _, _ -> },
            onSyncParentDevice = {},
            onCheckImmediateBlock = {},
            onChildTopAppsSharingChanged = {},
            onStartImmediateBlock = { _, _, _ -> },
            onStopImmediateBlock = { _, _, _ -> },
            onClearRemoteParentCommands = {},
            onRemoteAppExtraTime = { _, _, _ -> },
            onRemoteAppUnlockToday = { _, _ -> },
            onRemoteTotalExtraTime = {},
            onRemoteTotalUnlockToday = {},
            onApproveRemoteUnlockRequest = { _, _, _ -> },
            onRejectRemoteUnlockRequest = {},
            onClearEventLog = {},
            onDailyPolicyExpandedChange = {},
            onAppGroupsExpandedChange = {},
            onAppLimitsExpandedChange = {},
            onScheduleBlockingExpandedChange = {},
            onAllowOnlyModeExpandedChange = {},
            onSettingsLanguageExpandedChange = {},
            onSettingsNotificationExpandedChange = {},
            onSettingsPinExpandedChange = {},
            onSettingsParentManagementExpandedChange = {},
            onSettingsEventLogExpandedChange = {},
            suppressPermissionSetupAutoDialog = false,
        )
    }
}
