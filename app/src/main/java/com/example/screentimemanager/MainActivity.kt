package com.example.screentimemanager

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.screentimemanager.blocking.BlockedActivity
import com.example.screentimemanager.data.AppLanguage
import com.example.screentimemanager.data.AppGroupPolicy
import com.example.screentimemanager.data.EventLogEntry
import com.example.screentimemanager.data.ForegroundDetectionStatus
import com.example.screentimemanager.data.UsagePolicySettings
import com.example.screentimemanager.data.normalizedAppGroups
import com.example.screentimemanager.data.toAppGroupsEncoded
import com.example.screentimemanager.notification.UsageNotificationHelper
import com.example.screentimemanager.safety.BlockDecision
import com.example.screentimemanager.safety.BlockDecisionResult
import com.example.screentimemanager.ui.safety.AppGroupSummary
import com.example.screentimemanager.ui.safety.AppLimitSummary
import com.example.screentimemanager.ui.safety.AutoRecoveryStatus
import com.example.screentimemanager.ui.safety.BlockingReadiness
import com.example.screentimemanager.ui.safety.EmergencyUnlockStatus
import com.example.screentimemanager.ui.safety.LimitStatus
import com.example.screentimemanager.ui.safety.PinChangeStatus
import com.example.screentimemanager.ui.safety.PolicyBudgetValidation
import com.example.screentimemanager.ui.safety.PolicySummary
import com.example.screentimemanager.ui.safety.PolicySaveStatus
import com.example.screentimemanager.ui.safety.SafeModeUiState
import com.example.screentimemanager.ui.safety.SafeModeViewModel
import com.example.screentimemanager.ui.safety.appLimitMap
import com.example.screentimemanager.ui.safety.toAppLimitRules
import com.example.screentimemanager.ui.theme.ScreenTimeManagerTheme
import com.example.screentimemanager.ui.theme.AppOver
import com.example.screentimemanager.ui.theme.AppSafe
import com.example.screentimemanager.ui.theme.AppWarn
import com.example.screentimemanager.usage.AppUsageInfo
import com.example.screentimemanager.usage.InstalledAppInfo
import com.example.screentimemanager.worker.UsagePolicyCheckWorker
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val safeModeViewModel: SafeModeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UsageNotificationHelper(this).ensureChannel()
        UsagePolicyCheckWorker.schedule(this)
        enableEdgeToEdge()
        setContent {
            ScreenTimeManagerTheme {
                val uiState by safeModeViewModel.uiState.collectAsState()
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = {
                        safeModeViewModel.refreshForForeground(force = true)
                    },
                )

                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                ) { innerPadding ->
                    ScreenTimeManagerScreen(
                        uiState = uiState,
                        onSafeModeChanged = safeModeViewModel::setSafeModeEnabled,
                        onPolicyEnforcementChanged = safeModeViewModel::setPolicyEnforcementEnabled,
                        onAppLanguageChanged = safeModeViewModel::setAppLanguage,
                        onWarningNotificationsChanged = safeModeViewModel::setWarningNotificationsEnabled,
                        onLimitNotificationsChanged = safeModeViewModel::setLimitNotificationsEnabled,
                        onKillSwitchClick = safeModeViewModel::activateKillSwitch,
                        onEmergencyUnlock = safeModeViewModel::submitEmergencyPin,
                        onEmergencyPinChanged = safeModeViewModel::clearEmergencyUnlockStatus,
                        onOpenUsageAccessSettings = ::openUsageAccessSettings,
                        onOpenAccessibilitySettings = ::openAccessibilitySettings,
                        onRefreshUsageStats = safeModeViewModel::refreshUsageStats,
                        onPolicyDraftChanged = safeModeViewModel::updatePolicyDraft,
                        onResetPolicyDraft = safeModeViewModel::resetPolicyDraft,
                        onSaveUsagePolicy = safeModeViewModel::savePolicyDraft,
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
                        onClearEventLog = safeModeViewModel::clearEventLog,
                        modifier = Modifier
                            .padding(innerPadding)
                            .safeDrawingPadding(),
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        safeModeViewModel.refreshForForeground(force = true)
    }

    override fun onStop() {
        safeModeViewModel.markAppStoppedCleanly()
        super.onStop()
    }

    private fun openUsageAccessSettings() {
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
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
}

private enum class ScreenTab {
    Overview,
    Policy,
    Apps,
    Safety,
    Settings,
}

private val PrimaryScreenTabs = listOf(
    ScreenTab.Overview,
    ScreenTab.Policy,
    ScreenTab.Apps,
    ScreenTab.Safety,
)

private fun ScreenTab.next(): ScreenTab {
    if (this == ScreenTab.Settings) {
        return this
    }
    val index = PrimaryScreenTabs.indexOf(this).takeIf { tabIndex -> tabIndex >= 0 } ?: 0
    return PrimaryScreenTabs[(index + 1).coerceAtMost(PrimaryScreenTabs.lastIndex)]
}

private fun ScreenTab.previous(): ScreenTab {
    if (this == ScreenTab.Settings) {
        return this
    }
    val index = PrimaryScreenTabs.indexOf(this).takeIf { tabIndex -> tabIndex >= 0 } ?: PrimaryScreenTabs.lastIndex
    return PrimaryScreenTabs[(index - 1).coerceAtLeast(0)]
}

@Composable
fun ScreenTimeManagerScreen(
    uiState: SafeModeUiState,
    onSafeModeChanged: (Boolean) -> Unit,
    onPolicyEnforcementChanged: (Boolean) -> Unit,
    onAppLanguageChanged: (AppLanguage) -> Unit,
    onWarningNotificationsChanged: (Boolean) -> Unit,
    onLimitNotificationsChanged: (Boolean) -> Unit,
    onKillSwitchClick: () -> Unit,
    onEmergencyUnlock: (String) -> Unit,
    onEmergencyPinChanged: () -> Unit,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onRefreshUsageStats: () -> Unit,
    onPolicyDraftChanged: (UsagePolicySettings) -> Unit,
    onResetPolicyDraft: () -> Unit,
    onSaveUsagePolicy: (String) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onUpdateAdminPin: (String, String) -> Unit,
    onUpdateEmergencyPin: (String, String) -> Unit,
    onPinInputChanged: () -> Unit,
    onClearEventLog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableStateOf(ScreenTab.Overview) }
    var previousTab by remember { mutableStateOf<ScreenTab?>(null) }
    var tabTransitionDirection by remember { mutableStateOf(1) }
    var emergencyPin by remember { mutableStateOf("") }
    var policyAdminPin by remember { mutableStateOf("") }
    var showPolicySaveDialog by remember { mutableStateOf(false) }
    val text = appStrings(uiState.appLanguage)
    val context = LocalContext.current

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
            .fillMaxWidth(),
    ) {
        val isExpanded = maxWidth >= 720.dp
        val screenScrollState = rememberScrollState()
        val isKeyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 920.dp)
                .verticalScroll(screenScrollState)
                .padding(horizontal = 20.dp)
                .padding(top = 0.dp, bottom = if (isKeyboardVisible) 28.dp else 112.dp)
                .align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Header(onSettingsClick = { selectTab(ScreenTab.Settings) })

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(selectedTab) {
                        var horizontalDragAmount = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { horizontalDragAmount = 0f },
                            onHorizontalDrag = { _, dragAmount ->
                                horizontalDragAmount += dragAmount
                            },
                            onDragEnd = {
                                when {
                                    horizontalDragAmount < -80f -> selectTab(selectedTab.next())
                                    horizontalDragAmount > 80f -> selectTab(selectedTab.previous())
                                }
                            },
                        )
                    },
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
                            onEditPolicy = { selectTab(ScreenTab.Policy) },
                            onOpenUsageAccessSettings = onOpenUsageAccessSettings,
                            onRefreshUsageStats = onRefreshUsageStats,
                        )

                        ScreenTab.Policy -> UsagePolicySection(
                            settings = uiState.policyDraftSettings,
                            installedApps = uiState.installedApps,
                            text = text,
                            isExpanded = isExpanded,
                            contentMode = PolicyContentMode.DaysAndGroups,
                            policySaveStatus = uiState.policySaveStatus,
                            hasPolicyChanges = uiState.policyDraftHasChanges,
                            budgetValidation = uiState.policyBudgetValidation,
                            onRequestSavePolicy = { showPolicySaveDialog = true },
                            onResetPolicyDraft = onResetPolicyDraft,
                            onPolicyDraftChanged = onPolicyDraftChanged,
                        )

                        ScreenTab.Apps -> UsagePolicySection(
                            settings = uiState.policyDraftSettings,
                            installedApps = uiState.installedApps,
                            text = text,
                            isExpanded = isExpanded,
                            contentMode = PolicyContentMode.AppLimits,
                            policySaveStatus = uiState.policySaveStatus,
                            hasPolicyChanges = uiState.policyDraftHasChanges,
                            budgetValidation = uiState.policyBudgetValidation,
                            onRequestSavePolicy = { showPolicySaveDialog = true },
                            onResetPolicyDraft = onResetPolicyDraft,
                            onPolicyDraftChanged = onPolicyDraftChanged,
                        )

                        ScreenTab.Safety -> SafetyContent(
                            uiState = uiState,
                            emergencyPin = emergencyPin,
                            text = text,
                            isExpanded = isExpanded,
                            onSafeModeChanged = onSafeModeChanged,
                            onPolicyEnforcementChanged = onPolicyEnforcementChanged,
                            onKillSwitchClick = onKillSwitchClick,
                            onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                            onRequestNotificationPermission = onRequestNotificationPermission,
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
                            onRequestNotificationPermission = onRequestNotificationPermission,
                            onUpdateAdminPin = onUpdateAdminPin,
                            onUpdateEmergencyPin = onUpdateEmergencyPin,
                            onPinInputChanged = onPinInputChanged,
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
                onTabSelected = { tab -> selectTab(tab) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .widthIn(max = 560.dp),
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
        animationSpec = tween(durationMillis = 360),
        label = "tab-content-transition",
        finishedListener = { value ->
            if (value >= 1f) {
                onAnimationFinished()
            }
        },
    )

    BoxWithConstraints(modifier = Modifier.clipToBounds()) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        if (hasPreviousTab && previousTab != null) {
            Box(
                modifier = Modifier.graphicsLayer {
                    alpha = 1f - (progress * 0.18f)
                    translationX = -direction * progress * widthPx
                },
            ) {
                content(previousTab)
            }
        }
        Box(
            modifier = Modifier.graphicsLayer {
                alpha = 0.82f + (progress * 0.18f)
                translationX = direction * (1f - progress) * widthPx
            },
        ) {
            content(selectedTab)
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
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val hasBudgetOverflow = budgetValidation.hasOverflow
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text.savePolicy, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatusBadge(
                    label = when {
                        hasBudgetOverflow -> text.policyBudgetExceeded
                        policySaveStatus == PolicySaveStatus.Saved -> text.policySaved
                        policySaveStatus == PolicySaveStatus.InvalidAdminPin -> text.invalidAdminPin
                        policySaveStatus == PolicySaveStatus.BudgetExceeded -> text.policyBudgetExceeded
                        hasPolicyChanges -> text.unsavedChanges
                        else -> text.policyUpToDate
                    },
                    status = when {
                        hasBudgetOverflow -> LimitStatus.Exceeded
                        policySaveStatus == PolicySaveStatus.InvalidAdminPin -> LimitStatus.Exceeded
                        policySaveStatus == PolicySaveStatus.BudgetExceeded -> LimitStatus.Exceeded
                        hasPolicyChanges -> LimitStatus.Warning
                        else -> LimitStatus.Normal
                    },
                )
                policyValidationMessage(budgetValidation, text)?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.labelLarge,
                        color = AppOver,
                    )
                }
                OutlinedTextField(
                    value = adminPin,
                    onValueChange = onAdminPinChanged,
                    label = { Text(text.adminPin) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            if (adminPin.isNotBlank() && !hasBudgetOverflow) {
                                onSave()
                            }
                        },
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    focusManager.clearFocus()
                    onSave()
                },
                enabled = adminPin.isNotBlank() && !hasBudgetOverflow,
            ) {
                Text(text.savePolicy)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text.cancel)
            }
        },
    )
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
    return "${text.policyBudgetExceeded}: $overflowingDays - ${text.appLimits} ${budgetValidation.appLimitTotalMinutes}${text.minutes}, ${text.appGroups} ${budgetValidation.groupBudgetTotalMinutes}${text.minutes}"
}

@Composable
fun Header(onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                "Screen Time",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Manager \u00B7 Today",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            modifier = Modifier.size(34.dp),
            onClick = onSettingsClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Box(contentAlignment = Alignment.Center) {
                SettingsGearIcon()
            }
        }
    }
}

@Composable
private fun BottomTabBar(
    selectedTab: ScreenTab,
    text: AppStrings,
    onTabSelected: (ScreenTab) -> Unit,
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

            ScreenTab.Policy -> {
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

            ScreenTab.Apps -> {
                val dotRadius = size.minDimension * 0.09f
                listOf(0.32f, 0.68f).forEach { x ->
                    listOf(0.32f, 0.68f).forEach { y ->
                        drawCircle(color = color, radius = dotRadius, center = Offset(size.width * x, size.height * y))
                    }
                }
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
                drawCircle(color = color, radius = size.minDimension * 0.28f, style = Stroke(width = stroke))
            }
        }
    }
}

private fun AppStrings.tabLabel(tab: ScreenTab): String {
    return when (tab) {
        ScreenTab.Overview -> overview
        ScreenTab.Policy -> policy
        ScreenTab.Apps -> apps
        ScreenTab.Safety -> safety
        ScreenTab.Settings -> settings
    }
}

@Composable
private fun SettingsGearIcon() {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = Modifier.size(18.dp)) {
        val strokeWidth = 2.dp.toPx()
        drawCircle(
            color = color,
            radius = size.minDimension * 0.24f,
            style = Stroke(width = strokeWidth),
        )
        repeat(8) { index ->
            val angle = Math.toRadians((index * 45).toDouble())
            val center = Offset(size.width / 2f, size.height / 2f)
            val inner = Offset(
                x = center.x + kotlin.math.cos(angle).toFloat() * size.minDimension * 0.34f,
                y = center.y + kotlin.math.sin(angle).toFloat() * size.minDimension * 0.34f,
            )
            val outer = Offset(
                x = center.x + kotlin.math.cos(angle).toFloat() * size.minDimension * 0.46f,
                y = center.y + kotlin.math.sin(angle).toFloat() * size.minDimension * 0.46f,
            )
            drawLine(color = color, start = inner, end = outer, strokeWidth = strokeWidth, cap = StrokeCap.Round)
        }
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
    onEditPolicy: () -> Unit,
    onOpenUsageAccessSettings: () -> Unit,
    onRefreshUsageStats: () -> Unit,
) {
    AdaptiveTwoPane(
        isExpanded = isExpanded,
        leftContent = {
            StatusCard(uiState, text)
            PolicySummarySection(uiState.policySummary, text, onEditPolicy)
        },
        rightContent = {
            UsageStatsSection(
                hasUsageAccess = uiState.hasUsageAccess,
                todayUsage = uiState.todayUsage,
                policySummary = uiState.policySummary,
                text = text,
                onOpenUsageAccessSettings = onOpenUsageAccessSettings,
                onRefreshUsageStats = onRefreshUsageStats,
            )
        },
    )
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
    val overMinutes = (summary.totalUsedMinutes - summary.totalLimitMinutes).coerceAtLeast(0)
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
                limitMinutes = summary.totalLimitMinutes,
                status = summary.totalStatus,
            )
            Spacer(modifier = Modifier.width(24.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(summary.totalUsedMinutes.toString(), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                    Text(" / ${summary.totalLimitMinutes}m", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    if (overMinutes > 0) "+${overMinutes}m over limit" else text.limitStatus(summary.totalStatus),
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
) {
    val rawProgress = if (limitMinutes <= 0) 0f else usedMinutes.toFloat() / limitMinutes.toFloat()
    val progress by animateFloatAsState(
        targetValue = rawProgress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600),
        label = "semantic-bar",
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "${usedMinutes}m / ${limitMinutes}m",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        GaugeBar(fraction = progress, status = status, height = 8)
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
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(50))
            .background(GaugeTrackColor),
    ) {
        if (safeFraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(safeFraction)
                    .height(height.dp)
                    .clip(RoundedCornerShape(50))
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
    onPolicyEnforcementChanged: (Boolean) -> Unit,
    onKillSwitchClick: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenBlockScreenPreview: (BlockDecisionResult) -> Unit,
    onPinChanged: (String) -> Unit,
    onUnlockClick: () -> Unit,
) {
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
            Switch(checked = uiState.safeModeEnabled, onCheckedChange = onSafeModeChanged)
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
                onCheckedChange = onPolicyEnforcementChanged,
                enabled = !uiState.safeModeEnabled,
            )
        }
        Text(
            when (uiState.autoRecoveryStatus) {
                AutoRecoveryStatus.Idle -> text.autoRecoveryReady
                AutoRecoveryStatus.RecoveredToSafeMode -> text.autoRecoveryEnabledSafeMode
            },
        )
        Button(onClick = onKillSwitchClick) { Text(text.killSwitch) }
        OutlinedButton(onClick = onRequestNotificationPermission) {
            Text(text.notificationPermission)
        }
        }

        BlockingReadinessSection(
            readiness = uiState.blockingReadiness,
            text = text,
            onOpenAccessibilitySettings = onOpenAccessibilitySettings,
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
        OutlinedTextField(
            value = emergencyPin,
            onValueChange = onPinChanged,
            label = { Text(text.developerPin) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
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
}

@Composable
fun SettingsContent(
    uiState: SafeModeUiState,
    text: AppStrings,
    isExpanded: Boolean,
    onAppLanguageChanged: (AppLanguage) -> Unit,
    onWarningNotificationsChanged: (Boolean) -> Unit,
    onLimitNotificationsChanged: (Boolean) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onUpdateAdminPin: (String, String) -> Unit,
    onUpdateEmergencyPin: (String, String) -> Unit,
    onPinInputChanged: () -> Unit,
    onClearEventLog: () -> Unit,
) {
    var currentAdminPin by remember { mutableStateOf("") }
    var newAdminPin by remember { mutableStateOf("") }
    var currentEmergencyPin by remember { mutableStateOf("") }
    var newEmergencyPin by remember { mutableStateOf("") }
    var pinFeedbackTarget by remember { mutableStateOf<PinFeedbackTarget?>(null) }

    val preferences: @Composable ColumnScope.() -> Unit = {
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
            OutlinedButton(onClick = onRequestNotificationPermission) {
                Text(text.notificationPermission)
            }
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
        OutlinedTextField(
            value = currentPin,
            onValueChange = { value -> onCurrentChanged(value.filter { character -> character.isDigit() }.take(12)) },
            label = { Text(currentLabel) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Next,
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = newPin,
            onValueChange = { value -> onNewChanged(value.filter { character -> character.isDigit() }.take(12)) },
            label = { Text(newLabel) },
            visualTransformation = PasswordVisualTransformation(),
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
            singleLine = true,
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
    onOpenAccessibilitySettings: () -> Unit,
) {
    SimpleCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(text.blockingReadiness, Modifier.weight(1f))
            StatusBadge(
                if (readiness.readyForBlocking) text.ready else text.notReady,
                if (readiness.readyForBlocking) LimitStatus.Normal else LimitStatus.Warning,
            )
        }
        ReadinessRow(text.accessibilityService, readiness.accessibilityServiceEnabled)
        ReadinessRow(text.safeModeAllowsBlocking, readiness.safeModeAllowsBlocking)
        ReadinessRow(text.policyEnforcementReady, readiness.policyEnforcementReady)
        ReadinessRow(text.usageAccessReady, readiness.usageAccessReady)
        ReadinessRow(text.notificationPermission, readiness.notificationPermissionReady)
        ReadinessRow(text.whitelistReady, readiness.whitelistReady)
        ReadinessRow(text.emergencyUnlockReady, readiness.emergencyUnlockReady)
        ReadinessRow(text.killSwitchReady, readiness.killSwitchReady)
        OutlinedButton(onClick = onOpenAccessibilitySettings) {
            Text(text.openAccessibilitySettings)
        }
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
                                previewTarget.limitMinutes?.let { limitMinutes -> "${limitMinutes}${text.minutes}" },
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
                        "${text.remainingTime}: 0${text.minutes}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = "",
                        onValueChange = {},
                        label = { Text(text.parentPin) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        enabled = false,
                        singleLine = true,
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
    todayUsage: List<AppUsageInfo>,
    policySummary: PolicySummary,
    text: AppStrings,
    onOpenUsageAccessSettings: () -> Unit,
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
            Button(onClick = onOpenUsageAccessSettings) { Text(text.openUsageAccessSettings) }
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
                Text(formatDuration(appUsage.totalTimeMillis), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun MiniUsageBar(fraction: Float, status: LimitStatus, height: Int = 5) {
    val safeFraction = if (fraction.isFinite()) fraction.coerceIn(0f, 1f) else 0f
    GaugeBar(fraction = safeFraction, status = status, height = height)
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
fun PolicySummarySection(summary: PolicySummary, text: AppStrings, onEditPolicy: () -> Unit) {
    var selectedGroupSummary by remember { mutableStateOf<AppGroupSummary?>(null) }
    SimpleCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(text.policySummary, Modifier.weight(1f))
            Surface(
                onClick = onEditPolicy,
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
            ) {
                Text(
                    "Edit",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (summary.groupSummaries.isNotEmpty()) {
            Text(text.appGroups, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            summary.groupSummaries.forEach { groupSummary ->
                Surface(
                    onClick = { selectedGroupSummary = groupSummary },
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Transparent,
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        ProgressLine(
                            groupSummary.groupName.ifBlank { text.groupName },
                            groupSummary.usedMinutes,
                            groupSummary.limitMinutes,
                            groupSummary.status,
                            text,
                        )
                    }
                }
            }
        }
        if (summary.appLimitSummaries.isEmpty()) {
            Text(text.noAppLimits, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(text.appLimits, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            summary.appLimitSummaries.forEach { appSummary ->
                PolicyAppSummaryLine(appSummary)
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
                            trailingContent = if (limitMinutes != null) {
                                {
                                    LimitTimeChip(formatLimitMinutesLabel(limitMinutes))
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
        supportingText = "${summary.usedMinutes}m / ${summary.limitMinutes}m",
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
            Text(appName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (supportingText.isNotBlank()) {
                Text(supportingText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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
        )
    }
}

@Composable
fun AppIcon(packageName: String, contentDescription: String, size: androidx.compose.ui.unit.Dp = 40.dp) {
    val context = LocalContext.current
    val iconBitmap = remember(packageName) {
        runCatching {
            context.packageManager
                .getApplicationIcon(packageName)
                .toSafeBitmap()
                .asImageBitmap()
        }.getOrNull()
    }
    if (iconBitmap != null) {
        Image(
            bitmap = iconBitmap,
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

@Composable
fun PolicyAppSummaryLine(summary: AppLimitSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(packageName = summary.packageName, contentDescription = summary.appName, size = 24.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(summary.appName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "${summary.usedMinutes}m / ${summary.limitMinutes}m",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ProgressOnlyBar(
            usedMinutes = summary.usedMinutes,
            limitMinutes = summary.limitMinutes,
            status = summary.status,
        )
    }
}

@Composable
fun ProgressOnlyBar(usedMinutes: Int, limitMinutes: Int, status: LimitStatus) {
    val rawProgress = if (limitMinutes <= 0) 0f else usedMinutes.toFloat() / limitMinutes.toFloat()
    val progress by animateFloatAsState(
        targetValue = rawProgress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600),
        label = "summary-app-bar",
    )
    GaugeBar(fraction = progress, status = status, height = 8)
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
    Canvas(modifier = Modifier.size(18.dp)) {
        val stroke = 2.dp.toPx()
        drawArc(
            color = Color(0xFF6B7280),
            startAngle = 35f,
            sweepAngle = 285f,
            useCenter = false,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawLine(
            color = Color(0xFF6B7280),
            start = Offset(size.width * 0.78f, size.height * 0.20f),
            end = Offset(size.width * 0.92f, size.height * 0.22f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color(0xFF6B7280),
            start = Offset(size.width * 0.78f, size.height * 0.20f),
            end = Offset(size.width * 0.80f, size.height * 0.36f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
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
            Surface(
                onClick = { onDaySelected(index) },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
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
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) Color.White else if (isWeekend) Color(0xFF8B5A1F) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = minutes.ifBlank { "0" },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) Color.White else if (isWeekend) Color(0xFF8B5A1F) else MaterialTheme.colorScheme.onSurfaceVariant,
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
    stepMinutes: Int = 5,
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
    minMinutes: Int = 0,
    maxMinutes: Int = POLICY_MAX_MINUTES,
) {
    var isEditing by remember { mutableStateOf(false) }
    var draftValue by remember(valueMinutes) { mutableStateOf(valueMinutes.takeIf { it > 0 }?.toString().orEmpty()) }
    val focusManager = LocalFocusManager.current

    fun commitValue() {
        val minutes = draftValue
            .filter { character -> character.isDigit() }
            .toIntOrNull()
            ?.let { raw -> ((raw + 2) / 5) * 5 }
            ?.coerceIn(minMinutes.coerceAtMost(maxMinutes), maxMinutes)
            ?: minMinutes.coerceAtMost(maxMinutes)
        onValueMinutesChange(minutes)
        focusManager.clearFocus()
        isEditing = false
    }

    if (isEditing) {
        OutlinedTextField(
            value = draftValue,
            onValueChange = { value ->
                draftValue = value
                    .filter { character -> character.isDigit() }
                    .take(3)
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { commitValue() }),
            singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                color = MaterialTheme.colorScheme.primary,
            ),
            modifier = Modifier
                .width(132.dp)
                .height(54.dp),
        )
    } else {
        Surface(
            onClick = {
                draftValue = valueMinutes.takeIf { it > 0 }?.toString().orEmpty()
                isEditing = true
            },
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
            modifier = Modifier
                .width(132.dp)
                .height(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    if (valueMinutes > 0) formatLimitMinutesLabel(valueMinutes) else text.noLimit,
                    modifier = Modifier.padding(horizontal = 10.dp),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun MinuteControlPanel(
    valueMinutes: Int,
    onValueMinutesChange: (Int) -> Unit,
    text: AppStrings,
    title: String,
    minMinutes: Int = 0,
    maxMinutes: Int = POLICY_MAX_MINUTES,
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
                    minMinutes = minMinutes,
                    maxMinutes = maxMinutes,
                )
            }
            SmoothMinuteSlider(
                valueMinutes = valueMinutes,
                onValueMinutesChange = onValueMinutesChange,
                minMinutes = minMinutes,
                maxMinutes = maxMinutes,
                stepMinutes = 5,
            )
            MinuteAxisLabels(maxMinutes = maxMinutes)
        }
    }
}

@Composable
private fun MinuteAxisLabels(maxMinutes: Int) {
    val labels = remember(maxMinutes) {
        if (maxMinutes <= 0) {
            listOf("0")
        } else if (maxMinutes < 60) {
            listOf(0, maxMinutes / 4, maxMinutes / 2, (maxMinutes * 3) / 4, maxMinutes)
                .mapIndexed { index, minutes -> if (index == 0) "0" else "${minutes}m" }
        } else {
            val maxHours = (maxMinutes / 60).coerceAtLeast(1)
            listOf(0, maxHours / 4, maxHours / 2, (maxHours * 3) / 4, maxHours)
                .mapIndexed { index, hours -> if (index == 0) "0" else "${hours}h" }
        }
    }
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        labels.forEach { label ->
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private enum class AppLimitFilter {
    All,
    Limited,
    Unrestricted,
}

private enum class PolicyContentMode {
    DaysAndGroups,
    AppLimits,
}

@Composable
fun GroupBudgetSummary(
    totalMinutes: Int,
    dailyMinimumMinutes: Int,
    text: AppStrings,
) {
    val status = if (totalMinutes > POLICY_MAX_MINUTES) LimitStatus.Exceeded else LimitStatus.Normal
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
            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("${budgetMinutes}m \u00B7 $appCount", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SearchBox(value: String, placeholder: String, onValueChange: (String) -> Unit) {
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
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    if (value.isBlank()) {
                        Text(placeholder, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    innerTextField()
                },
            )
        }
    }
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
                        if (hasLimit) text.minutesPerDay(limitMinutes ?: 0) else text.noLimit,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (hasLimit) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        if (hasLimit) formatLimitMinutesLabel(limitMinutes ?: 0) else text.addLimit,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (hasLimit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
    val presets = listOf(0, 5, 10, 15, 30, 60, 120, 240, 360, 720)
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
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return when {
        hours > 0 && remainingMinutes > 0 -> "${hours}h ${remainingMinutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
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
    installedApps: List<InstalledAppInfo>,
    text: AppStrings,
    isExpanded: Boolean,
    contentMode: PolicyContentMode,
    policySaveStatus: PolicySaveStatus,
    hasPolicyChanges: Boolean,
    budgetValidation: PolicyBudgetValidation,
    onRequestSavePolicy: () -> Unit,
    onResetPolicyDraft: () -> Unit,
    onPolicyDraftChanged: (UsagePolicySettings) -> Unit,
) {
    val dailyLimits = listOf(
        settings.mondayLimitMinutes,
        settings.tuesdayLimitMinutes,
        settings.wednesdayLimitMinutes,
        settings.thursdayLimitMinutes,
        settings.fridayLimitMinutes,
        settings.saturdayLimitMinutes,
        settings.sundayLimitMinutes,
    ).map { minutes -> minutes.coerceIn(0, POLICY_MAX_MINUTES).toString() }
    var selectedDayIndex by remember { mutableStateOf(Calendar.getInstance().get(Calendar.DAY_OF_WEEK).toDayIndex()) }
    val appGroups = settings.normalizedAppGroups().normalizedForEditing()
    val groupBudgetTotal = appGroups.sumOf { group -> group.budgetMinutes.coerceAtLeast(0) }
    val minimumDailyLimit = groupBudgetTotal.coerceAtMost(POLICY_MAX_MINUTES)
    var activeGroupId by remember { mutableStateOf(appGroups.firstOrNull()?.id.orEmpty()) }
    LaunchedEffect(appGroups.map { group -> group.id }) {
        if (appGroups.isNotEmpty() && appGroups.none { group -> group.id == activeGroupId }) {
            activeGroupId = appGroups.first().id
        }
    }
    val appLimits = settings.appLimitMap()
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
        SimpleCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(text.dailyPolicy, Modifier.weight(1f))
                Text("minutes / day", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DayLimitChips(
                dayLabels = text.dayLabels,
                dailyLimits = dailyLimits,
                selectedDayIndex = selectedDayIndex,
                onDaySelected = { index -> selectedDayIndex = index },
            )
            val selectedMinutes = dailyLimits[selectedDayIndex].toIntOrNull() ?: 0
            MinuteControlPanel(
                valueMinutes = selectedMinutes.coerceAtLeast(minimumDailyLimit),
                onValueMinutesChange = { minutes ->
                    val nextDailyLimits = dailyLimits.toMutableList().also { limits ->
                        limits[selectedDayIndex] = minutes.toString()
                    }
                    updateDraft(nextDailyLimits = nextDailyLimits)
                },
                text = text,
                title = text.dayLabels[selectedDayIndex],
                minMinutes = minimumDailyLimit,
                maxMinutes = POLICY_MAX_MINUTES,
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
        SimpleCard {
            val activeGroup = appGroups.firstOrNull { group -> group.id == activeGroupId }
                ?: appGroups.firstOrNull()
            val groupVisibleApps = installedApps
                .filter { app -> groupAppSearchQuery.isBlank() || app.appName.contains(groupAppSearchQuery, ignoreCase = true) }
                .sortedWith(
                    compareByDescending<InstalledAppInfo> { app ->
                        app.packageName in activeGroup?.packageNames.orEmpty()
                    }.thenBy { app -> app.appName.lowercase() },
                )
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(text.appGroups, Modifier.weight(1f))
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
                                listOf(AppGroupPolicy(text.appGroupBudget, emptySet(), 60, newAppGroupId()))
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
                OutlinedTextField(
                    value = activeGroup.name,
                    onValueChange = { value ->
                        val nextGroups = appGroups.replaceGroupById(
                            activeGroup.id,
                            activeGroup.copy(name = value),
                        )
                        updateDraft(nextAppGroups = nextGroups)
                    },
                    label = { Text(text.groupName) },
                    singleLine = true,
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
        SimpleCard {
            val visibleApps = installedApps
                .filter { app -> appSearchQuery.isBlank() || app.appName.contains(appSearchQuery, ignoreCase = true) }
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(text.appLimits, Modifier.weight(1f))
                Text(text.activeAppLimits(appLimits.size), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
                        AppLimitRow(
                            app = app,
                            limitMinutes = appLimit,
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

    val shouldShowSaveAction = hasPolicyChanges ||
        policySaveStatus != PolicySaveStatus.Idle ||
        budgetValidation.hasOverflow

    if (contentMode == PolicyContentMode.DaysAndGroups) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (shouldShowSaveAction) {
                PolicySaveActionCard(
                    text = text,
                    policySaveStatus = policySaveStatus,
                    hasPolicyChanges = hasPolicyChanges,
                    budgetValidation = budgetValidation,
                    onRequestSavePolicy = onRequestSavePolicy,
                    onResetPolicyDraft = onResetPolicyDraft,
                )
            }
            AdaptiveTwoPane(
                isExpanded = isExpanded,
                leftContent = {
                    limitPolicy()
                },
                rightContent = {
                    groupPolicy()
                },
            )
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (shouldShowSaveAction) {
                PolicySaveActionCard(
                    text = text,
                    policySaveStatus = policySaveStatus,
                    hasPolicyChanges = hasPolicyChanges,
                    budgetValidation = budgetValidation,
                    onRequestSavePolicy = onRequestSavePolicy,
                    onResetPolicyDraft = onResetPolicyDraft,
                )
            }
            appLimitsSection()
        }
    }
}

@Composable
private fun PolicySaveActionCard(
    text: AppStrings,
    policySaveStatus: PolicySaveStatus,
    hasPolicyChanges: Boolean,
    budgetValidation: PolicyBudgetValidation,
    onRequestSavePolicy: () -> Unit,
    onResetPolicyDraft: () -> Unit,
) {
    val hasBudgetOverflow = budgetValidation.hasOverflow
    val validationMessage = policyValidationMessage(budgetValidation, text)
    SimpleCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionTitle(text.savePolicy)
                StatusBadge(
                    label = when {
                        hasBudgetOverflow -> text.policyBudgetExceeded
                        policySaveStatus == PolicySaveStatus.InvalidAdminPin -> text.invalidAdminPin
                        policySaveStatus == PolicySaveStatus.BudgetExceeded -> text.policyBudgetExceeded
                        hasPolicyChanges -> text.unsavedChanges
                        policySaveStatus == PolicySaveStatus.Saved -> text.policySaved
                        else -> text.policyUpToDate
                    },
                    status = when {
                        hasBudgetOverflow -> LimitStatus.Exceeded
                        policySaveStatus == PolicySaveStatus.InvalidAdminPin -> LimitStatus.Exceeded
                        policySaveStatus == PolicySaveStatus.BudgetExceeded -> LimitStatus.Exceeded
                        hasPolicyChanges -> LimitStatus.Warning
                        else -> LimitStatus.Normal
                    },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onResetPolicyDraft,
                    enabled = hasPolicyChanges,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(text.resetChanges)
                }
                Button(
                    onClick = onRequestSavePolicy,
                    enabled = hasPolicyChanges && !hasBudgetOverflow,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(text.savePolicy)
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            if (validationMessage != null) {
                Text(
                    text = validationMessage,
                    style = MaterialTheme.typography.labelLarge,
                    color = AppOver,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
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
    val containedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                return available
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                return available
            }
        }
    }

    Box(modifier = modifier.clip(RoundedCornerShape(18.dp))) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .nestedScroll(containedScrollConnection),
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
        if (groupBudgetTotal > 0) {
            minutes.coerceAtLeast(groupBudgetTotal).coerceAtMost(POLICY_MAX_MINUTES)
        } else {
            minutes
        }
    }
    val primaryGroup = cleanGroups.first()
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
        appGroupName = primaryGroup.name,
        appGroupPackages = primaryGroup.packageNames.sorted().joinToString(","),
        appGroupBudgetMinutes = primaryGroup.budgetMinutes,
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
    return cleanGroups.ifEmpty {
        listOf(AppGroupPolicy("Group 1", emptySet(), 60, newAppGroupId()))
    }
}

fun newAppGroupId(): String = UUID.randomUUID().toString()

private const val POLICY_MAX_MINUTES = 720

fun String?.toLimitMinutes(): Int {
    return this
        ?.filter { character -> character.isDigit() }
        ?.toIntOrNull()
        ?.coerceIn(0, POLICY_MAX_MINUTES)
        ?: 0
}

fun formatDuration(totalTimeMillis: Long): String {
    val totalMinutes = (totalTimeMillis + 59_999L) / 60_000L
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

fun formatClockTime(timestampMillis: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestampMillis))
}

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

data class AppStrings(
    val korean: String,
    val overview: String,
    val policy: String,
    val apps: String,
    val safety: String,
    val settings: String,
    val language: String,
    val todayStatus: String,
    val developerSafeMode: String,
    val safeModeOn: String,
    val safeModeOff: String,
    val status: String,
    val blockingDisabled: String,
    val safetyChecksRequired: String,
    val policyEnforcement: String,
    val policyEnforcementEnabled: String,
    val policyEnforcementDisabled: String,
    val killSwitch: String,
    val autoRecoveryReady: String,
    val autoRecoveryEnabledSafeMode: String,
    val emergencyUnlock: String,
    val developerPin: String,
    val unlock: String,
    val offlinePinAvailable: String,
    val safeModeEnabled: String,
    val invalidPin: String,
    val todayUsage: String,
    val usageAccessRequired: String,
    val openUsageAccessSettings: String,
    val refresh: String,
    val noUsageRecorded: String,
    val policySummary: String,
    val totalUsageSummary: String,
    val noAppLimits: String,
    val warningSummary: (Int, Int) -> String,
    val limitStatus: (LimitStatus) -> String,
    val weekdayShort: String,
    val weekendShort: String,
    val dailyPolicy: String,
    val dayLabels: List<String>,
    val applyWeekdays: String,
    val applyWeekend: String,
    val appGroupBudget: String,
    val appGroups: String,
    val groupName: String,
    val groupBudgetMinutes: String,
    val groupApps: String,
    val selectedApps: (Int) -> String,
    val inGroup: String,
    val addToGroup: String,
    val addGroup: String,
    val deleteGroup: String,
    val deleteCurrentGroup: String,
    val groupBudgetTotal: (Int, Int) -> String,
    val appLimits: String,
    val activeAppLimits: (Int) -> String,
    val allApps: String,
    val limitedApps: String,
    val unrestrictedApps: String,
    val searchApps: String,
    val noLimit: String,
    val addLimit: String,
    val clearLimit: String,
    val minutesPerDay: (Int) -> String,
    val applyLimit: String,
    val appLimitGroupAllowance: (String, Int, Int) -> String,
    val newGroup: String,
    val minutes: String,
    val noSelectableApps: String,
    val savePolicy: String,
    val resetChanges: String,
    val unsavedChanges: String,
    val policyUpToDate: String,
    val policyBudgetExceeded: String,
    val appGroupLimitConflict: (Int) -> String,
    val policyEnforcementStillDisabled: String,
    val policySaved: String,
    val invalidAdminPin: String,
    val adminPin: String,
    val cancel: String,
    val notificationPermission: String,
    val notificationSettings: String,
    val warningNotifications: String,
    val warningNotificationsDescription: String,
    val limitNotifications: String,
    val limitNotificationsDescription: String,
    val pinSettings: String,
    val currentAdminPin: String,
    val newAdminPin: String,
    val currentEmergencyPin: String,
    val newEmergencyPin: String,
    val pinChangeIdle: String,
    val pinChanged: String,
    val pinTooShort: String,
    val pinSameAsCurrent: String,
    val pinInvalidCurrent: String,
    val pinChangeFailed: String,
    val eventLog: String,
    val clear: String,
    val noEvents: String,
    val blockingReadiness: String,
    val ready: String,
    val notReady: String,
    val accessibilityService: String,
    val safeModeAllowsBlocking: String,
    val policyEnforcementReady: String,
    val usageAccessReady: String,
    val whitelistReady: String,
    val emergencyUnlockReady: String,
    val killSwitchReady: String,
    val openAccessibilitySettings: String,
    val blockSimulation: String,
    val blockScreenPreview: String,
    val previewOnly: String,
    val blockedTodayMessage: String,
    val remainingTime: String,
    val parentPin: String,
    val noBlockPreviewTarget: String,
    val openBlockScreenPreview: String,
    val noSimulationTargets: String,
    val detectionStatus: String,
    val detectionStatusDescription: String,
    val noDetectionStatus: String,
    val usedMinutes: (Int) -> String,
    val detectionDecision: (String) -> String,
    val blockDecision: (BlockDecision) -> String,
)

fun appStrings(language: AppLanguage): AppStrings {
    return when (language) {
        AppLanguage.Korean -> AppStrings(
            korean = "\uD55C\uAD6D\uC5B4",
            overview = "\uAC1C\uC694",
            policy = "\uC694\uC77C/\uADF8\uB8F9",
            apps = "\uC571",
            safety = "\uC548\uC804",
            settings = "\uC124\uC815",
            language = "\uC5B8\uC5B4",
            todayStatus = "\uC624\uB298 \uC0C1\uD0DC",
            developerSafeMode = "\uAC1C\uBC1C\uC790 \uC548\uC804 \uBAA8\uB4DC",
            safeModeOn = "\uC548\uC804 \uBAA8\uB4DC ON",
            safeModeOff = "\uC548\uC804 \uBAA8\uB4DC OFF",
            status = "\uC0C1\uD0DC:",
            blockingDisabled = "\uCC28\uB2E8 \uBE44\uD65C\uC131\uD654",
            safetyChecksRequired = "\uCC28\uB2E8 \uC2E4\uD589 \uC804 \uC548\uC804 \uD655\uC778 \uD544\uC694",
            policyEnforcement = "\uC815\uCC45 \uC801\uC6A9",
            policyEnforcementEnabled = "\uC815\uCC45 \uC801\uC6A9 \uD65C\uC131\uD654",
            policyEnforcementDisabled = "\uC815\uCC45 \uC801\uC6A9 \uBE44\uD65C\uC131\uD654",
            killSwitch = "\uD0AC \uC2A4\uC704\uCE58",
            autoRecoveryReady = "\uC790\uB3D9 \uBCF5\uAD6C \uC900\uBE44\uB428",
            autoRecoveryEnabledSafeMode = "\uC790\uB3D9 \uBCF5\uAD6C\uB85C \uC548\uC804 \uBAA8\uB4DC \uD65C\uC131\uD654\uB428",
            emergencyUnlock = "\uAE34\uAE09 \uD574\uC81C",
            developerPin = "\uAC1C\uBC1C\uC790 PIN",
            unlock = "\uD574\uC81C",
            offlinePinAvailable = "\uC624\uD504\uB77C\uC778 PIN \uD574\uC81C \uC0AC\uC6A9 \uAC00\uB2A5",
            safeModeEnabled = "\uC548\uC804 \uBAA8\uB4DC \uD65C\uC131\uD654\uB428",
            invalidPin = "PIN\uC774 \uC62C\uBC14\uB974\uC9C0 \uC54A\uC2B5\uB2C8\uB2E4",
            todayUsage = "\uC624\uB298 \uC0AC\uC6A9",
            usageAccessRequired = "\uC0AC\uC6A9\uC815\uBCF4 \uC811\uADFC \uAD8C\uD55C\uC774 \uD544\uC694\uD569\uB2C8\uB2E4",
            openUsageAccessSettings = "\uAD8C\uD55C \uC124\uC815",
            refresh = "\uC0C8\uB85C\uACE0\uCE68",
            noUsageRecorded = "\uC624\uB298 \uAE30\uB85D\uB41C \uC0AC\uC6A9 \uC2DC\uAC04\uC774 \uC5C6\uC2B5\uB2C8\uB2E4",
            policySummary = "\uC815\uCC45 \uC694\uC57D",
            totalUsageSummary = "\uC804\uCCB4",
            noAppLimits = "\uC571\uBCC4 \uC81C\uD55C\uC774 \uC5C6\uC2B5\uB2C8\uB2E4",
            warningSummary = { warningCount, exceededCount ->
                "\uC8FC\uC758 ${warningCount}\uAC1C, \uCD08\uACFC ${exceededCount}\uAC1C"
            },
            limitStatus = { status ->
                when (status) {
                    LimitStatus.Normal -> "\uC815\uC0C1"
                    LimitStatus.Warning -> "\uC8FC\uC758"
                    LimitStatus.Exceeded -> "\uCD08\uACFC"
                }
            },
            weekdayShort = "\uD3C9\uC77C",
            weekendShort = "\uC8FC\uB9D0",
            dailyPolicy = "\uC694\uC77C\uBCC4 \uC81C\uD55C",
            dayLabels = listOf("\uC6D4", "\uD654", "\uC218", "\uBAA9", "\uAE08", "\uD1A0", "\uC77C"),
            applyWeekdays = "\uD3C9\uC77C",
            applyWeekend = "\uC8FC\uB9D0",
            appGroupBudget = "\uC571 \uADF8\uB8F9",
            appGroups = "\uC571 \uADF8\uB8F9",
            groupName = "\uADF8\uB8F9 \uC774\uB984",
            groupBudgetMinutes = "\uC608\uC0B0(\uBD84)",
            groupApps = "\uADF8\uB8F9 \uC571",
            selectedApps = { count -> "\uC120\uD0DD ${count}\uAC1C" },
            inGroup = "\uD3EC\uD568",
            addToGroup = "\uCD94\uAC00",
            addGroup = "\uADF8\uB8F9 \uCD94\uAC00",
            deleteGroup = "\uADF8\uB8F9 \uC0AD\uC81C",
            deleteCurrentGroup = "- \uADF8\uB8F9 \uC0AD\uC81C",
            groupBudgetTotal = { total, dailyMinimum -> "\uADF8\uB8F9 \uC608\uC0B0 \uD569\uACC4 ${total}\uBD84 / \uC77C\uC77C \uCD5C\uC18C ${dailyMinimum}\uBD84" },
            appLimits = "\uC571\uBCC4 \uC81C\uD55C",
            activeAppLimits = { count -> "\uD65C\uC131 ${count}\uAC1C" },
            allApps = "\uC804\uCCB4",
            limitedApps = "\uC81C\uD55C",
            unrestrictedApps = "\uBBF8\uC81C\uD55C",
            searchApps = "\uC571 \uAC80\uC0C9",
            noLimit = "\uC81C\uD55C \uC5C6\uC74C",
            addLimit = "\uCD94\uAC00",
            clearLimit = "\uD574\uC81C",
            minutesPerDay = { minutes -> "${minutes}\uBD84/\uC77C" },
            applyLimit = "\uC801\uC6A9",
            appLimitGroupAllowance = { groupName, maxMinutes, groupBudget -> "${groupName} \uADF8\uB8F9\uC5D0\uC11C \uCD5C\uB300 ${maxMinutes}\uBD84\uAE4C\uC9C0 \uC124\uC815\uD560 \uC218 \uC788\uC2B5\uB2C8\uB2E4 (\uADF8\uB8F9 ${groupBudget}\uBD84)" },
            newGroup = "+ \uC0C8 \uADF8\uB8F9",
            minutes = "\uBD84",
            noSelectableApps = "\uC120\uD0DD \uAC00\uB2A5\uD55C \uC571\uC774 \uC5C6\uC2B5\uB2C8\uB2E4",
            savePolicy = "\uC800\uC7A5",
            resetChanges = "\uB418\uB3CC\uB9AC\uAE30",
            unsavedChanges = "\uBCC0\uACBD \uC788\uC74C",
            policyUpToDate = "\uCD5C\uC2E0 \uC0C1\uD0DC",
            policyBudgetExceeded = "\uC608\uC0B0 \uCD08\uACFC",
            appGroupLimitConflict = { count -> "\uADF8\uB8F9 \uC608\uC0B0\uBCF4\uB2E4 \uD070 \uC571\uBCC4 \uC81C\uD55C ${count}\uAC1C" },
            policyEnforcementStillDisabled = "\uC815\uCC45 \uC801\uC6A9\uC740 \uC544\uC9C1 \uBE44\uD65C\uC131\uD654\uC785\uB2C8\uB2E4",
            policySaved = "\uC800\uC7A5\uB428",
            invalidAdminPin = "\uAD00\uB9AC PIN\uC774 \uC62C\uBC14\uB974\uC9C0 \uC54A\uC2B5\uB2C8\uB2E4",
            adminPin = "\uAD00\uB9AC PIN",
            cancel = "\uCDE8\uC18C",
            notificationPermission = "\uC54C\uB9BC \uAD8C\uD55C",
            notificationSettings = "\uC54C\uB9BC \uC124\uC815",
            warningNotifications = "\uACBD\uACE0 \uC54C\uB9BC",
            warningNotificationsDescription = "\uC0AC\uC6A9\uB7C9\uC774 \uC81C\uD55C\uC758 80%\uC5D0 \uB3C4\uB2EC\uD558\uBA74 \uC54C\uB9BC\uC744 \uBCF4\uB0C5\uB2C8\uB2E4",
            limitNotifications = "\uCD08\uACFC \uC54C\uB9BC",
            limitNotificationsDescription = "\uC81C\uD55C\uC744 \uB118\uAE30\uAC70\uB098 \uCC28\uB2E8 \uC9C1\uC804 \uC0C1\uD0DC\uC5D0\uC11C \uC54C\uB9BC\uC744 \uBCF4\uB0C5\uB2C8\uB2E4",
            pinSettings = "PIN \uC124\uC815",
            currentAdminPin = "\uD604\uC7AC \uAD00\uB9AC PIN",
            newAdminPin = "\uC0C8 \uAD00\uB9AC PIN",
            currentEmergencyPin = "\uD604\uC7AC \uAE34\uAE09 PIN",
            newEmergencyPin = "\uC0C8 \uAE34\uAE09 PIN",
            pinChangeIdle = "PIN\uC740 4\uC790\uB9AC \uC774\uC0C1\uC73C\uB85C \uC124\uC815\uD558\uC138\uC694",
            pinChanged = "PIN\uC774 \uBCC0\uACBD\uB428",
            pinTooShort = "PIN\uC740 4\uC790\uB9AC \uC774\uC0C1\uC774\uC5B4\uC57C \uD569\uB2C8\uB2E4",
            pinSameAsCurrent = "\uC0C8 PIN\uC774 \uD604\uC7AC PIN\uACFC \uAC19\uC2B5\uB2C8\uB2E4",
            pinInvalidCurrent = "\uD604\uC7AC PIN\uC774 \uC62C\uBC14\uB974\uC9C0 \uC54A\uC2B5\uB2C8\uB2E4",
            pinChangeFailed = "PIN \uBCC0\uACBD \uC2E4\uD328",
            eventLog = "\uC774\uBCA4\uD2B8",
            clear = "\uC9C0\uC6B0\uAE30",
            noEvents = "\uAE30\uB85D\uB41C \uC774\uBCA4\uD2B8\uAC00 \uC5C6\uC2B5\uB2C8\uB2E4",
            blockingReadiness = "\uCC28\uB2E8 \uC900\uBE44 \uC0C1\uD0DC",
            ready = "\uC900\uBE44\uB428",
            notReady = "\uB300\uAE30",
            accessibilityService = "\uC811\uADFC\uC131 \uAD8C\uD55C",
            safeModeAllowsBlocking = "Safe Mode OFF",
            policyEnforcementReady = "\uC815\uCC45 \uC801\uC6A9 ON",
            usageAccessReady = "\uC0AC\uC6A9\uC815\uBCF4 \uAD8C\uD55C",
            whitelistReady = "\uD544\uC218 \uC608\uC678 \uBAA9\uB85D",
            emergencyUnlockReady = "\uAE34\uAE09 \uD574\uC81C",
            killSwitchReady = "Kill Switch",
            openAccessibilitySettings = "\uC811\uADFC\uC131 \uC124\uC815",
            blockSimulation = "\uCC28\uB2E8 \uC2DC\uBBAC\uB808\uC774\uC158",
            blockScreenPreview = "\uCC28\uB2E8 \uD654\uBA74",
            previewOnly = "\uBBF8\uB9AC\uBCF4\uAE30",
            blockedTodayMessage = "\uC624\uB298 \uC0AC\uC6A9 \uC2DC\uAC04\uC774 \uC885\uB8CC\uB418\uC5C8\uC2B5\uB2C8\uB2E4",
            remainingTime = "\uB0A8\uC740 \uC2DC\uAC04",
            parentPin = "\uBCF4\uD638\uC790 PIN",
            noBlockPreviewTarget = "\uCC28\uB2E8 \uBBF8\uB9AC\uBCF4\uAE30 \uB300\uC0C1\uC774 \uC5C6\uC2B5\uB2C8\uB2E4",
            openBlockScreenPreview = "\uCC28\uB2E8 \uD654\uBA74 \uBBF8\uB9AC\uBCF4\uAE30",
            noSimulationTargets = "\uC2DC\uBBAC\uB808\uC774\uC158 \uB300\uC0C1 \uC571\uC774 \uC5C6\uC2B5\uB2C8\uB2E4",
            detectionStatus = "\uCD5C\uADFC \uAC10\uC9C0",
            detectionStatusDescription = "\uC811\uADFC\uC131 \uC11C\uBE44\uC2A4\uAC00 \uB9C8\uC9C0\uB9C9\uC73C\uB85C \uD3C9\uAC00\uD55C \uC77C\uBC18 \uC0AC\uC6A9\uC790 \uC571 1\uAC1C\uB9CC \uD45C\uC2DC\uD569\uB2C8\uB2E4. \uC2DC\uC2A4\uD15C, \uD0A4\uBCF4\uB4DC, \uC124\uC815, \uC608\uC678 \uC571\uC740 \uC81C\uC678\uD569\uB2C8\uB2E4",
            noDetectionStatus = "\uC544\uC9C1 \uAC10\uC9C0\uB41C \uC571\uC774 \uC5C6\uC2B5\uB2C8\uB2E4",
            usedMinutes = { minutes -> "${minutes}\uBD84 \uC0AC\uC6A9" },
            detectionDecision = { decision ->
                when (decision) {
                    "safe mode" -> "Safe Mode"
                    "policy disabled" -> "\uC815\uCC45 OFF"
                    "whitelist" -> "\uC608\uC678"
                    "no limit" -> "\uC81C\uD55C \uC5C6\uC74C"
                    "under limit" -> "\uD5C8\uC6A9"
                    "total limit exceeded" -> "\uC804\uCCB4 \uCD08\uACFC \uC608\uC0C1"
                    "group limit exceeded" -> "\uADF8\uB8F9 \uCD08\uACFC \uC608\uC0C1"
                    "app limit exceeded" -> "\uC571 \uCD08\uACFC \uC608\uC0C1"
                    else -> decision
                }
            },
            blockDecision = { decision ->
                when (decision) {
                    BlockDecision.AllowedSafeMode -> "Safe Mode"
                    BlockDecision.AllowedPolicyDisabled -> "\uC815\uCC45 OFF"
                    BlockDecision.AllowedWhitelist -> "\uC608\uC678"
                    BlockDecision.AllowedNoLimit -> "\uC81C\uD55C \uC5C6\uC74C"
                    BlockDecision.AllowedUnderLimit -> "\uD5C8\uC6A9"
                    BlockDecision.WouldBlockTotalLimit -> "\uC804\uCCB4 \uCD08\uACFC"
                    BlockDecision.WouldBlockGroupLimit -> "\uADF8\uB8F9 \uCD08\uACFC"
                    BlockDecision.WouldBlockAppLimit -> "\uC571 \uCD08\uACFC"
                }
            },
        )

        AppLanguage.English -> AppStrings(
            korean = "Korean",
            overview = "Overview",
            policy = "Days/Groups",
            apps = "Apps",
            safety = "Safety",
            settings = "Settings",
            language = "Language",
            todayStatus = "Today",
            developerSafeMode = "Developer Safe Mode",
            safeModeOn = "Safe Mode ON",
            safeModeOff = "Safe Mode OFF",
            status = "Status:",
            blockingDisabled = "Blocking Disabled",
            safetyChecksRequired = "Safety checks required before blocking",
            policyEnforcement = "Policy Enforcement",
            policyEnforcementEnabled = "Policy Enforcement Enabled",
            policyEnforcementDisabled = "Policy Enforcement Disabled",
            killSwitch = "Kill Switch",
            autoRecoveryReady = "Auto Recovery Ready",
            autoRecoveryEnabledSafeMode = "Auto Recovery Enabled Safe Mode",
            emergencyUnlock = "Emergency Unlock",
            developerPin = "Developer PIN",
            unlock = "Unlock",
            offlinePinAvailable = "Offline PIN unlock is available",
            safeModeEnabled = "Safe Mode Enabled",
            invalidPin = "Invalid PIN",
            todayUsage = "Today Usage",
            usageAccessRequired = "Usage access permission is required",
            openUsageAccessSettings = "Permission Settings",
            refresh = "Refresh",
            noUsageRecorded = "No usage recorded today",
            policySummary = "Policy Summary",
            totalUsageSummary = "Total",
            noAppLimits = "No app limits",
            warningSummary = { warningCount, exceededCount ->
                "$warningCount warning, $exceededCount exceeded"
            },
            limitStatus = { status ->
                when (status) {
                    LimitStatus.Normal -> "Normal"
                    LimitStatus.Warning -> "Warning"
                    LimitStatus.Exceeded -> "Exceeded"
                }
            },
            weekdayShort = "Weekday",
            weekendShort = "Weekend",
            dailyPolicy = "Daily Limits",
            dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"),
            applyWeekdays = "Weekdays",
            applyWeekend = "Weekend",
            appGroupBudget = "App Group",
            appGroups = "App Groups",
            groupName = "Group name",
            groupBudgetMinutes = "Budget minutes",
            groupApps = "Group apps",
            selectedApps = { count -> "$count selected" },
            inGroup = "In",
            addToGroup = "Add",
            addGroup = "Add group",
            deleteGroup = "Delete group",
            deleteCurrentGroup = "- Delete",
            groupBudgetTotal = { total, dailyMinimum -> "Group budgets ${total}m / daily minimum ${dailyMinimum}m" },
            appLimits = "App Limits",
            activeAppLimits = { count -> "$count active" },
            allApps = "All",
            limitedApps = "Limited",
            unrestrictedApps = "Unrestricted",
            searchApps = "Search apps",
            noLimit = "No limit",
            addLimit = "Add",
            clearLimit = "Clear",
            minutesPerDay = { minutes -> "$minutes min/day" },
            applyLimit = "Apply",
            appLimitGroupAllowance = { groupName, maxMinutes, groupBudget -> "Up to ${maxMinutes}m available in $groupName group (${groupBudget}m group)" },
            newGroup = "+ New",
            minutes = "Min",
            noSelectableApps = "No selectable apps",
            savePolicy = "Save",
            resetChanges = "Reset",
            unsavedChanges = "Unsaved",
            policyUpToDate = "Up to date",
            policyBudgetExceeded = "Budget over",
            appGroupLimitConflict = { count -> "$count app limits exceed group budgets" },
            policyEnforcementStillDisabled = "Policy enforcement is still disabled",
            policySaved = "Saved",
            invalidAdminPin = "Invalid admin PIN",
            adminPin = "Admin PIN",
            cancel = "Cancel",
            notificationPermission = "Notification Permission",
            notificationSettings = "Notification Settings",
            warningNotifications = "Warning notifications",
            warningNotificationsDescription = "Send an alert when usage reaches 80% of a limit.",
            limitNotifications = "Limit notifications",
            limitNotificationsDescription = "Send an alert when a limit is exceeded or an app is about to be blocked.",
            pinSettings = "PIN Settings",
            currentAdminPin = "Current admin PIN",
            newAdminPin = "New admin PIN",
            currentEmergencyPin = "Current emergency PIN",
            newEmergencyPin = "New emergency PIN",
            pinChangeIdle = "Use at least 4 digits",
            pinChanged = "PIN changed",
            pinTooShort = "Use at least 4 digits",
            pinSameAsCurrent = "New PIN matches the current PIN",
            pinInvalidCurrent = "Current PIN is incorrect",
            pinChangeFailed = "PIN change failed",
            eventLog = "Events",
            clear = "Clear",
            noEvents = "No events",
            blockingReadiness = "Blocking Readiness",
            ready = "Ready",
            notReady = "Waiting",
            accessibilityService = "Accessibility permission",
            safeModeAllowsBlocking = "Safe Mode OFF",
            policyEnforcementReady = "Policy enforcement ON",
            usageAccessReady = "Usage access",
            whitelistReady = "Required whitelist",
            emergencyUnlockReady = "Emergency unlock",
            killSwitchReady = "Kill Switch",
            openAccessibilitySettings = "Accessibility Settings",
            blockSimulation = "Block Simulation",
            blockScreenPreview = "Block Screen",
            previewOnly = "Preview",
            blockedTodayMessage = "Today's screen time is over",
            remainingTime = "Remaining time",
            parentPin = "Parent PIN",
            noBlockPreviewTarget = "No exceeded app to preview",
            openBlockScreenPreview = "Open Block Screen Preview",
            noSimulationTargets = "No apps to simulate",
            detectionStatus = "Recent Detection",
            detectionStatusDescription = "Shows the last regular user app evaluated by Accessibility. System, keyboard, Settings, and whitelisted apps are excluded.",
            noDetectionStatus = "No app detected yet",
            usedMinutes = { minutes -> "${minutes}m used" },
            detectionDecision = { decision ->
                when (decision) {
                    "total limit exceeded" -> "Total over"
                    "group limit exceeded" -> "Group over"
                    "app limit exceeded" -> "App over"
                    "under limit" -> "Under limit"
                    "no limit" -> "No limit"
                    "policy disabled" -> "Policy OFF"
                    "safe mode" -> "Safe Mode"
                    "whitelist" -> "Whitelist"
                    else -> decision
                }
            },
            blockDecision = { decision ->
                when (decision) {
                    BlockDecision.AllowedSafeMode -> "Safe Mode"
                    BlockDecision.AllowedPolicyDisabled -> "Policy OFF"
                    BlockDecision.AllowedWhitelist -> "Whitelist"
                    BlockDecision.AllowedNoLimit -> "No limit"
                    BlockDecision.AllowedUnderLimit -> "Allowed"
                    BlockDecision.WouldBlockTotalLimit -> "Total"
                    BlockDecision.WouldBlockGroupLimit -> "Group"
                    BlockDecision.WouldBlockAppLimit -> "App"
                }
            },
        )
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
            onPolicyEnforcementChanged = {},
            onAppLanguageChanged = {},
            onWarningNotificationsChanged = {},
            onLimitNotificationsChanged = {},
            onKillSwitchClick = {},
            onEmergencyUnlock = {},
            onEmergencyPinChanged = {},
            onOpenUsageAccessSettings = {},
            onOpenAccessibilitySettings = {},
            onRefreshUsageStats = {},
            onPolicyDraftChanged = {},
            onResetPolicyDraft = {},
            onSaveUsagePolicy = {},
            onRequestNotificationPermission = {},
            onUpdateAdminPin = { _, _ -> },
            onUpdateEmergencyPin = { _, _ -> },
            onPinInputChanged = {},
            onClearEventLog = {},
        )
    }
}
