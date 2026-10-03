package com.manisykh.screenrest

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.manisykh.screenrest.data.AppLanguage
import com.manisykh.screenrest.data.OnboardingMode
import com.manisykh.screenrest.data.UsagePolicySettings
import com.manisykh.screenrest.ui.designsystem.ScreenRestPalette
import com.manisykh.screenrest.ui.safety.PolicySaveStatus
import com.manisykh.screenrest.ui.safety.SafeModeUiState
import com.manisykh.screenrest.usage.InstalledAppInfo
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private enum class GuideStage {
    Welcome,
    Notice,
    Goal,
    Details,
    Pin,
    Permissions,
    Activate,
    Done,
    ParentDone,
}

private enum class MissingPermission {
    Usage,
    Overlay,
    Notification,
}

private data class OnboardingInfo(
    val iconRes: Int,
    val title: String,
    val body: String,
)

@Composable
internal fun FirstRunOnboarding(
    uiState: SafeModeUiState,
    onSelectMode: (OnboardingMode) -> Unit,
    onAcceptDisclosure: () -> Unit,
    onPrepareRule: (UsagePolicySettings, String, OnboardingMode) -> Unit,
    onPrepareParent: (String) -> Unit,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onActivateProtection: () -> Unit,
    onComplete: () -> Unit,
    onExit: () -> Unit,
) {
    val korean = uiState.appLanguage == AppLanguage.Korean
    var selectedModeName by rememberSaveable {
        mutableStateOf(uiState.onboardingProgress.mode?.name.orEmpty())
    }
    var selectedGoalName by rememberSaveable { mutableStateOf(FirstRuleGoal.DailyLimit.name) }
    var dailyMinutes by rememberSaveable { mutableIntStateOf(120) }
    var appMinutes by rememberSaveable { mutableIntStateOf(30) }
    var selectedPackage by rememberSaveable { mutableStateOf("") }
    var pin by rememberSaveable { mutableStateOf("") }
    var pinConfirmation by rememberSaveable { mutableStateOf("") }
    var pinError by rememberSaveable { mutableStateOf("") }
    var activationRequested by rememberSaveable { mutableStateOf(false) }
    var stageName by rememberSaveable {
        mutableStateOf(initialGuideStage(uiState).name)
    }

    val selectedMode = selectedModeName.toOnboardingModeOrNull()
        ?: uiState.onboardingProgress.mode
    val selectedGoal = selectedGoalName.toFirstRuleGoal()
    val missingPermission = when {
        !uiState.hasUsageAccess -> MissingPermission.Usage
        !uiState.blockingReadiness.overlayPermissionReady -> MissingPermission.Overlay
        !uiState.blockingReadiness.notificationPermissionReady -> MissingPermission.Notification
        else -> null
    }
    val stage = stageName.toGuideStage()

    LaunchedEffect(uiState.monitoringDisclosureAccepted, stage) {
        if (stage == GuideStage.Notice && uiState.monitoringDisclosureAccepted) {
            stageName = if (selectedMode == OnboardingMode.ParentOnly) {
                GuideStage.Pin.name
            } else {
                GuideStage.Goal.name
            }
        }
    }
    LaunchedEffect(uiState.onboardingProgress.setupPrepared, stage) {
        if (uiState.onboardingProgress.setupPrepared && stage == GuideStage.Pin) {
            pin = ""
            pinConfirmation = ""
            stageName = if (selectedMode == OnboardingMode.ParentOnly) {
                GuideStage.ParentDone.name
            } else {
                GuideStage.Permissions.name
            }
        }
    }
    LaunchedEffect(missingPermission, stage) {
        if (stage == GuideStage.Permissions && missingPermission == null) {
            stageName = GuideStage.Activate.name
        }
    }
    LaunchedEffect(uiState.safeModeEnabled, uiState.policyEnforcementEnabled, activationRequested) {
        if (activationRequested && !uiState.safeModeEnabled && uiState.policyEnforcementEnabled) {
            stageName = GuideStage.Done.name
        }
    }
    LaunchedEffect(activationRequested) {
        if (activationRequested) {
            delay(2_500L)
            if (uiState.safeModeEnabled || !uiState.policyEnforcementEnabled) {
                activationRequested = false
            }
        }
    }

    BackHandler {
        when (stage) {
            GuideStage.Welcome, GuideStage.Notice -> onExit()
            GuideStage.Goal -> stageName = GuideStage.Notice.name
            GuideStage.Details -> stageName = GuideStage.Goal.name
            GuideStage.Pin -> stageName = if (selectedMode == OnboardingMode.ParentOnly) {
                GuideStage.Notice.name
            } else {
                GuideStage.Details.name
            }
            GuideStage.Permissions -> Unit
            GuideStage.Activate -> stageName = GuideStage.Permissions.name
            GuideStage.Done, GuideStage.ParentDone -> Unit
        }
    }

    OnboardingScaffold(
        stage = stage,
    ) {
        when (stage) {
            GuideStage.Welcome -> WelcomeStep(
                korean = korean,
                selectedMode = selectedMode,
                onSelect = { mode ->
                    selectedModeName = mode.name
                    onSelectMode(mode)
                },
                onContinue = {
                    if (selectedMode != null) stageName = GuideStage.Notice.name
                },
            )

            GuideStage.Notice -> DisclosureStep(
                korean = korean,
                parentOnly = selectedMode == OnboardingMode.ParentOnly,
                onAccept = onAcceptDisclosure,
                onExit = onExit,
            )

            GuideStage.Goal -> GoalStep(
                korean = korean,
                selectedGoal = selectedGoal,
                onSelect = { selectedGoalName = it.name },
                onContinue = { stageName = GuideStage.Details.name },
            )

            GuideStage.Details -> RuleDetailsStep(
                korean = korean,
                goal = selectedGoal,
                dailyMinutes = dailyMinutes,
                appMinutes = appMinutes,
                selectedPackage = selectedPackage,
                installedApps = uiState.installedApps,
                onDailyMinutesChange = { dailyMinutes = it },
                onAppMinutesChange = { appMinutes = it },
                onPackageSelect = { selectedPackage = it },
                onContinue = { stageName = GuideStage.Pin.name },
            )

            GuideStage.Pin -> PinStep(
                korean = korean,
                alreadyConfigured = uiState.securityPinsConfigured,
                parentOnly = selectedMode == OnboardingMode.ParentOnly,
                pin = pin,
                confirmation = pinConfirmation,
                error = pinError,
                saving = uiState.policySaveStatus == PolicySaveStatus.Saving,
                saveStatus = uiState.policySaveStatus,
                onPinChange = { pin = it.onlyPinDigits(); pinError = "" },
                onConfirmationChange = {
                    pinConfirmation = it.onlyPinDigits()
                    pinError = ""
                },
                onContinue = {
                    pinError = validatePin(
                        pin = pin,
                        confirmation = pinConfirmation,
                        confirmationRequired = !uiState.securityPinsConfigured,
                        korean = korean,
                    )
                    if (pinError.isEmpty()) {
                        if (selectedMode == OnboardingMode.ParentOnly) {
                            onPrepareParent(pin)
                        } else {
                            val mode = selectedMode ?: OnboardingMode.LocalDevice
                            onPrepareRule(
                                buildFirstRule(
                                    goal = selectedGoal,
                                    dailyMinutes = dailyMinutes,
                                    appMinutes = appMinutes,
                                    selectedPackage = selectedPackage,
                                    korean = korean,
                                ),
                                pin,
                                mode,
                            )
                        }
                    }
                },
            )

            GuideStage.Permissions -> PermissionStep(
                korean = korean,
                permission = missingPermission,
                onOpenUsage = onOpenUsageAccessSettings,
                onOpenOverlay = onOpenOverlaySettings,
                onOpenNotification = onRequestNotificationPermission,
                onLater = onComplete,
            )

            GuideStage.Activate -> ActivationStep(
                korean = korean,
                goal = selectedGoal,
                dailyMinutes = dailyMinutes,
                appMinutes = appMinutes,
                selectedAppName = uiState.installedApps
                    .firstOrNull { it.packageName == selectedPackage }
                    ?.appName,
                activating = activationRequested &&
                    (uiState.safeModeEnabled || !uiState.policyEnforcementEnabled),
                onActivate = {
                    activationRequested = true
                    onActivateProtection()
                },
                onLater = onComplete,
            )

            GuideStage.Done -> DoneStep(
                korean = korean,
                parentOnly = false,
                onComplete = onComplete,
            )

            GuideStage.ParentDone -> DoneStep(
                korean = korean,
                parentOnly = true,
                onComplete = onComplete,
            )
        }
    }
}

@Composable
private fun OnboardingScaffold(
    stage: GuideStage,
    content: @Composable () -> Unit,
) {
    val step = when (stage) {
        GuideStage.Welcome -> 1
        GuideStage.Notice -> 2
        GuideStage.Goal -> 3
        GuideStage.Details, GuideStage.Pin -> 4
        GuideStage.Permissions -> 5
        GuideStage.Activate -> 6
        GuideStage.Done, GuideStage.ParentDone -> 7
    }
    Surface(
        color = ScreenRestPalette.WarmBackground,
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(Modifier.fillMaxSize()) {
            SoftSpatialBackdrop(stage = stage)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    LinearProgressIndicator(
                        progress = { step / 7f },
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(CircleShape),
                        color = ScreenRestPalette.Cobalt,
                        trackColor = ScreenRestPalette.SurfaceStrong,
                    )
                    Text(
                        text = "$step / 7",
                        style = MaterialTheme.typography.titleSmall,
                        color = ScreenRestPalette.NavySoft,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun SoftSpatialBackdrop(stage: GuideStage) {
    if (stage !in setOf(GuideStage.Goal, GuideStage.Details, GuideStage.Permissions, GuideStage.Done)) return
    Canvas(Modifier.fillMaxSize()) {
        val blue = Color(0xFFE5F2FF)
        val mint = Color(0xFFDDF8F1)
        fun bubble(x: Float, y: Float, radius: Float, color: Color, alpha: Float = 0.58f) {
            drawCircle(color = color.copy(alpha = alpha), radius = radius, center = androidx.compose.ui.geometry.Offset(x, y))
        }
        when (stage) {
            GuideStage.Goal -> {
                bubble(size.width + 18.dp.toPx(), 162.dp.toPx(), 90.dp.toPx(), mint)
                bubble(-24.dp.toPx(), size.height - 92.dp.toPx(), 118.dp.toPx(), blue)
                bubble(size.width + 12.dp.toPx(), size.height - 38.dp.toPx(), 100.dp.toPx(), mint)
            }
            GuideStage.Details -> {
                bubble(size.width + 24.dp.toPx(), 148.dp.toPx(), 94.dp.toPx(), mint, 0.42f)
                bubble(-20.dp.toPx(), size.height - 44.dp.toPx(), 114.dp.toPx(), blue)
                bubble(size.width - 18.dp.toPx(), size.height + 30.dp.toPx(), 120.dp.toPx(), mint)
            }
            GuideStage.Permissions -> {
                bubble(size.width + 8.dp.toPx(), 160.dp.toPx(), 88.dp.toPx(), blue)
                bubble(size.width - 12.dp.toPx(), 230.dp.toPx(), 54.dp.toPx(), mint)
                bubble(-34.dp.toPx(), size.height - 72.dp.toPx(), 112.dp.toPx(), mint)
            }
            GuideStage.Done -> {
                bubble(-24.dp.toPx(), 250.dp.toPx(), 116.dp.toPx(), blue)
                bubble(size.width + 18.dp.toPx(), 204.dp.toPx(), 98.dp.toPx(), mint)
                bubble(size.width + 8.dp.toPx(), size.height - 140.dp.toPx(), 120.dp.toPx(), mint, 0.46f)
            }
            else -> Unit
        }
    }
}

@Composable
private fun WelcomeStep(
    korean: Boolean,
    selectedMode: OnboardingMode?,
    onSelect: (OnboardingMode) -> Unit,
    onContinue: () -> Unit,
) {
    StepColumn {
        StepHeading(
            title = if (korean) "어떻게 사용하실 건가요?" else "How will you use ScreenRest?",
            body = if (korean) {
                "폰 쉼을 가장 잘 활용할 수 있는 방법을 선택해주세요."
            } else {
                "We will guide you through only the setup your device needs."
            },
        )
        ModeOption(
            iconRes = R.drawable.ic_more_account,
            selected = selectedMode == OnboardingMode.LocalDevice,
            title = if (korean) "내 사용 습관을 관리할게요" else "Manage my own screen time",
            body = if (korean) "더 나은 집중과 균형 있는 일상을 만들어요" else "Create screen-time rules for this device",
            onClick = { onSelect(OnboardingMode.LocalDevice) },
        )
        ModeOption(
            iconRes = R.drawable.ic_family_device,
            selected = selectedMode == OnboardingMode.ChildDevice,
            title = if (korean) "자녀 기기에 규칙을 설정할게요" else "Set up a child's device",
            body = if (korean) "우리 아이의 건강한 디지털 습관을 지켜요" else "Create rules, then optionally link a parent device",
            onClick = { onSelect(OnboardingMode.ChildDevice) },
        )
        ModeOption(
            iconRes = R.drawable.ic_family_manage,
            selected = selectedMode == OnboardingMode.ParentOnly,
            title = if (korean) "부모 기기에서 관리할게요" else "Use this as a parent device",
            body = if (korean) "부모님의 더 편안한 디지털 생활을 도와요" else "This device will not request blocking permissions",
            onClick = { onSelect(OnboardingMode.ParentOnly) },
        )
        Spacer(Modifier.weight(1f))
        PrimaryAction(
            label = if (korean) "계속" else "Continue",
            enabled = selectedMode != null,
            onClick = onContinue,
        )
    }
}

@Composable
private fun DisclosureStep(
    korean: Boolean,
    parentOnly: Boolean,
    onAccept: () -> Unit,
    onExit: () -> Unit,
) {
    StepColumn(scrollable = true) {
        StepHeading(
            title = if (korean) "먼저, 안심하고 사용할 수 있도록 알려드릴게요" else "First, a quick privacy note",
            body = if (parentOnly) {
                if (korean) "연결한 자녀 기기의 상태와 승인 요청을 안전하게 전달받습니다." else "Receive status and approval requests from linked child devices."
            } else {
                if (korean) "폰 쉼은 항상 사용자의 개인정보를 소중히 생각합니다." else "ScreenRest needs to check device usage to apply your rules."
            },
        )
        SoftInfoGroup(
            items = listOf(
                OnboardingInfo(
                    iconRes = R.drawable.ic_more_protection,
                    title = if (parentOnly) {
                        if (korean) "부모 기기에서 확인하는 정보" else "Information shown on the parent device"
                    } else {
                        if (korean) "개인정보는 안전하게" else "Your information stays safe"
                    },
                    body = if (parentOnly) {
                        if (korean) "연결한 자녀 기기의 사용·제한 시간, 차단 상태와 승인 요청을 확인합니다. 이 부모 기기의 앱 사용 시간은 수집하지 않습니다." else "See usage, limits, blocking status, and approval requests from linked child devices. App usage on this parent device is not collected."
                    } else if (korean) {
                        "설치된 앱 이름과 사용 시간은 규칙 적용에만 사용하며 안전하게 보호해요."
                    } else {
                        "App names and usage time are used only to apply your rules."
                    },
                ),
                OnboardingInfo(
                    iconRes = R.drawable.ic_block_manager,
                    title = if (korean) "내가 직접 관리해요" else "You stay in control",
                    body = if (korean) "모든 설정과 데이터는 언제든지 수정하거나 삭제할 수 있어요." else "You can change or delete settings and data at any time.",
                ),
                OnboardingInfo(
                    iconRes = R.drawable.ic_more_diagnostics,
                    title = if (korean) "광고로 사용하지 않아요" else "Never used for ads",
                    body = if (korean) "Google Analytics는 사용하지 않으며, 충돌과 응답 없음 기록만 오류 진단에 사용해요." else "Google Analytics is not used; crash and ANR records are used only for diagnostics.",
                ),
            ),
        )
        Text(
            text = if (korean) {
                "동의 전에는 감시, 원격 동기화, 푸시 토큰 등록을 시작하지 않습니다."
            } else {
                "Monitoring, remote sync, and push-token registration do not start before consent."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = ScreenRestPalette.NavySoft,
        )
        PrimaryAction(if (korean) "동의하고 계속" else "Agree and continue", onClick = onAccept)
        OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(if (korean) "앱 종료" else "Exit app")
        }
    }
}

@Composable
private fun GoalStep(
    korean: Boolean,
    selectedGoal: FirstRuleGoal,
    onSelect: (FirstRuleGoal) -> Unit,
    onContinue: () -> Unit,
) {
    StepColumn {
        StepHeading(
            title = if (korean) "첫 번째 규칙을 골라볼까요?" else "Choose your first rule",
            body = if (korean) "지금 가장 필요한 규칙부터 시작해보세요." else "Start with the rule you need most right now.",
        )
        GoalOption(
            iconRes = R.drawable.ic_family_clock,
            selected = selectedGoal == FirstRuleGoal.DailyLimit,
            title = if (korean) "하루 사용 시간" else "Daily screen time",
            body = if (korean) "하루 동안 사용할 수 있는 총 시간을 제한해요" else "Set one total limit for the day",
        ) { onSelect(FirstRuleGoal.DailyLimit) }
        HorizontalDivider(color = ScreenRestPalette.Border.copy(alpha = 0.68f))
        GoalOption(
            iconRes = R.drawable.ic_onboarding_apps,
            selected = selectedGoal == FirstRuleGoal.AppLimit,
            title = if (korean) "특정 앱 30분" else "Limit one app",
            body = if (korean) "자주 사용하는 앱의 사용 시간을 제한해요" else "Start gently with one frequently used app",
        ) { onSelect(FirstRuleGoal.AppLimit) }
        HorizontalDivider(color = ScreenRestPalette.Border.copy(alpha = 0.68f))
        GoalOption(
            iconRes = R.drawable.ic_onboarding_bedtime,
            selected = selectedGoal == FirstRuleGoal.Bedtime,
            title = if (korean) "취침 시간" else "Bedtime",
            body = if (korean) "지정한 시간 이후에는 사용을 제한해요" else "Take a break from 10 PM to 7 AM",
        ) { onSelect(FirstRuleGoal.Bedtime) }
        Spacer(Modifier.weight(1f))
        PrimaryAction(if (korean) "이 규칙으로 시작" else "Start with this rule", onClick = onContinue)
    }
}

@Composable
private fun RuleDetailsStep(
    korean: Boolean,
    goal: FirstRuleGoal,
    dailyMinutes: Int,
    appMinutes: Int,
    selectedPackage: String,
    installedApps: List<InstalledAppInfo>,
    onDailyMinutesChange: (Int) -> Unit,
    onAppMinutesChange: (Int) -> Unit,
    onPackageSelect: (String) -> Unit,
    onContinue: () -> Unit,
) {
    var appSearch by rememberSaveable { mutableStateOf("") }
    val visibleApps = remember(installedApps, appSearch) {
        installedApps.filter { app ->
            appSearch.isBlank() || app.appName.contains(appSearch, ignoreCase = true)
        }.take(20)
    }
    StepColumn(scrollable = true) {
        when (goal) {
            FirstRuleGoal.DailyLimit -> {
                StepHeading(
                    if (korean) "하루에 얼마나 사용할까요?" else "How much time each day?",
                    if (korean) "나에게 맞는 하루 사용 시간을 선택해주세요." else "Choose a daily limit that feels right for you.",
                )
                DurationHero(
                    value = if (korean) "${dailyMinutes / 60}시간 ${dailyMinutes % 60}분" else "$dailyMinutes minutes",
                )
                Slider(
                    value = dailyMinutes.coerceIn(30, 240).toFloat(),
                    onValueChange = { onDailyMinutesChange((it / 15f).roundToInt() * 15) },
                    valueRange = 30f..240f,
                    steps = 13,
                    colors = SliderDefaults.colors(
                        thumbColor = ScreenRestPalette.Cobalt,
                        activeTrackColor = ScreenRestPalette.Cobalt,
                        inactiveTrackColor = ScreenRestPalette.SurfaceStrong,
                        activeTickColor = Color.White.copy(alpha = 0.82f),
                        inactiveTickColor = ScreenRestPalette.Cobalt.copy(alpha = 0.26f),
                    ),
                )
                DurationLabels(korean = korean)
                InfoCard(
                    if (korean) "설정 결과" else "What will happen",
                    if (korean) "하루 ${dailyMinutes / 60}시간 ${dailyMinutes % 60}분까지 폰을 사용할 수 있어요." else "You can use your phone for $dailyMinutes minutes each day.",
                    iconRes = R.drawable.ic_family_clock,
                )
            }
            FirstRuleGoal.AppLimit -> {
                StepHeading(
                    if (korean) "어떤 앱부터 줄여볼까요?" else "Which app should we limit?",
                    if (korean) "앱 하나를 고르고 하루 사용 시간을 정해 주세요." else "Choose one app and set its daily time.",
                )
                LargeValue(if (korean) "하루 ${appMinutes}분" else "$appMinutes minutes per day")
                Slider(
                    value = appMinutes.toFloat(),
                    onValueChange = { onAppMinutesChange((it / 5f).roundToInt() * 5) },
                    valueRange = 5f..120f,
                    steps = 22,
                )
                if (installedApps.isEmpty()) {
                    InfoCard(
                        if (korean) "앱 목록을 준비하고 있어요" else "Preparing your app list",
                        if (korean) "잠시 후 앱 목록이 표시됩니다." else "Your apps will appear in a moment.",
                    )
                } else {
                    OutlinedTextField(
                        value = appSearch,
                        onValueChange = { appSearch = it },
                        label = { Text(if (korean) "앱 이름 검색" else "Search apps") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 310.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(visibleApps, key = { app -> app.packageName }) { app ->
                            AppChoiceRow(
                                app = app,
                                selected = selectedPackage == app.packageName,
                                onClick = { onPackageSelect(app.packageName) },
                            )
                        }
                    }
                }
            }
            FirstRuleGoal.Bedtime -> {
                StepHeading(
                    if (korean) "밤에는 편하게 쉬어가요" else "Make nights easier",
                    if (korean) "매일 밤 10시부터 아침 7시까지 앱 사용을 제한합니다." else "Apps are limited every night from 10 PM to 7 AM.",
                )
                LargeValue(if (korean) "오후 10:00 – 오전 7:00" else "10:00 PM – 7:00 AM")
                InfoCard(
                    if (korean) "안심하세요" else "You stay in control",
                    if (korean) "전화와 시스템 필수 기능은 항상 사용할 수 있고, 세부 시간은 나중에 바꿀 수 있어요." else "Calls and essential system functions remain available, and the schedule can be changed later.",
                )
            }
        }
        Spacer(Modifier.weight(1f))
        PrimaryAction(
            label = if (korean) "규칙 확인" else "Review rule",
            enabled = goal != FirstRuleGoal.AppLimit || selectedPackage.isNotBlank(),
            onClick = onContinue,
        )
    }
}

@Composable
private fun PinStep(
    korean: Boolean,
    alreadyConfigured: Boolean,
    parentOnly: Boolean,
    pin: String,
    confirmation: String,
    error: String,
    saving: Boolean,
    saveStatus: PolicySaveStatus,
    onPinChange: (String) -> Unit,
    onConfirmationChange: (String) -> Unit,
    onContinue: () -> Unit,
) {
    StepColumn(scrollable = true) {
        StepHeading(
            title = if (alreadyConfigured) {
                if (korean) "관리 PIN을 확인해 주세요" else "Confirm your Admin PIN"
            } else {
                if (korean) "마지막으로 관리 PIN을 만들어요" else "Create an Admin PIN"
            },
            body = if (parentOnly) {
                if (korean) "기기 연결과 중요한 설정 변경을 보호하는 데 사용합니다." else "It protects device links and important settings."
            } else {
                if (korean) "규칙을 실수로 바꾸거나 해제하지 않도록 보호해요." else "It prevents rules from being changed or disabled by mistake."
            },
        )
        OutlinedTextField(
            value = pin,
            onValueChange = onPinChange,
            label = { Text(if (korean) "관리 PIN" else "Admin PIN") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!alreadyConfigured) {
            OutlinedTextField(
                value = confirmation,
                onValueChange = onConfirmationChange,
                label = { Text(if (korean) "PIN 한 번 더 입력" else "Confirm PIN") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (error.isNotEmpty() || saveStatus == PolicySaveStatus.InvalidAdminPin) {
            Text(
                text = error.ifEmpty {
                    if (korean) "PIN을 확인하고 다시 시도해 주세요." else "Check the PIN and try again."
                },
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        InfoCard(
            if (korean) "PIN을 안전한 곳에 보관해 주세요" else "Keep your PIN somewhere safe",
            if (korean) "PIN은 복구할 수 없으며, 5회 연속 틀리면 30초 동안 입력이 잠깁니다." else "The PIN cannot be recovered. Five failed attempts lock input for 30 seconds.",
        )
        Spacer(Modifier.weight(1f))
        PrimaryAction(
            label = if (saving) {
                if (korean) "안전하게 저장하는 중…" else "Saving securely…"
            } else if (parentOnly) {
                if (korean) "부모 기기 준비" else "Prepare parent device"
            } else {
                if (korean) "PIN과 첫 규칙 저장" else "Save PIN and first rule"
            },
            enabled = !saving,
            onClick = onContinue,
        )
    }
}

@Composable
private fun PermissionStep(
    korean: Boolean,
    permission: MissingPermission?,
    onOpenUsage: () -> Unit,
    onOpenOverlay: () -> Unit,
    onOpenNotification: () -> Unit,
    onLater: () -> Unit,
) {
    val title: String
    val body: String
    val action: String
    val onClick: () -> Unit
    when (permission) {
        MissingPermission.Usage -> {
            title = if (korean) "사용 시간을 먼저 확인할게요" else "Let us read screen time first"
            body = if (korean) "정확한 사용 시간 확인을 위해 사용정보 접근 권한이 필요해요." else "Usage access lets ScreenRest calculate today's screen time accurately."
            action = if (korean) "사용정보 접근 설정 열기" else "Open usage access settings"
            onClick = onOpenUsage
        }
        MissingPermission.Overlay -> {
            title = if (korean) "제한된 이유를 화면에 보여드릴게요" else "Show why an app is limited"
            body = if (korean) "제한된 앱을 열었을 때 남은 시간과 차단 이유를 안내하려면 다른 앱 위에 표시할 수 있어야 해요." else "Display-over-other-apps lets ScreenRest explain the limit and remaining time when a restricted app opens."
            action = if (korean) "화면 표시 권한 설정" else "Allow display over apps"
            onClick = onOpenOverlay
        }
        MissingPermission.Notification -> {
            title = if (korean) "규칙이 작동 중임을 알려드릴게요" else "Stay informed while rules run"
            body = if (korean) "백그라운드에서도 규칙을 안정적으로 유지하고 제한 상태를 알려드리기 위해 알림을 사용해요." else "Notifications show that rules are active and keep you informed about limits in the background."
            action = if (korean) "알림 허용" else "Allow notifications"
            onClick = onOpenNotification
        }
        null -> {
            title = if (korean) "필요한 설정을 모두 마쳤어요" else "All permissions are ready"
            body = if (korean) "이제 첫 규칙을 켤 수 있어요." else "Your first rule is ready to turn on."
            action = if (korean) "계속" else "Continue"
            onClick = {}
        }
    }
    StepColumn {
        StepHeading(title, body)
        PermissionVisual(permission = permission)
        PermissionInfoPanel(
            title = when (permission) {
                MissingPermission.Usage -> if (korean) "사용정보 접근 권한" else "Usage access"
                MissingPermission.Overlay -> if (korean) "다른 앱 위에 표시" else "Display over other apps"
                MissingPermission.Notification -> if (korean) "알림 권한" else "Notifications"
                null -> if (korean) "필요한 설정 완료" else "Permissions ready"
            },
            body = if (korean) {
                "설정 화면에서 돌아오면 허용 여부를 자동으로 확인해요. 이 권한은 설정에서 언제든지 끌 수 있어요."
            } else {
                "When you return, ScreenRest checks the setting automatically. You can turn this permission off at any time."
            },
            iconRes = when (permission) {
                MissingPermission.Usage -> R.drawable.ic_nav_statistics
                MissingPermission.Overlay -> R.drawable.ic_block_manager
                MissingPermission.Notification -> R.drawable.ic_more_notifications
                null -> R.drawable.ic_more_protection
            },
            korean = korean,
        )
        Spacer(Modifier.weight(1f))
        PrimaryAction(action, onClick = onClick)
        TextButton(onClick = onLater, modifier = Modifier.fillMaxWidth()) {
            Text(if (korean) "나중에 설정할게요" else "Set up later")
        }
    }
}

@Composable
private fun ActivationStep(
    korean: Boolean,
    goal: FirstRuleGoal,
    dailyMinutes: Int,
    appMinutes: Int,
    selectedAppName: String?,
    activating: Boolean,
    onActivate: () -> Unit,
    onLater: () -> Unit,
) {
    val summary = when (goal) {
        FirstRuleGoal.DailyLimit -> if (korean) "매일 총 ${dailyMinutes}분" else "$dailyMinutes total minutes each day"
        FirstRuleGoal.AppLimit -> if (korean) "${selectedAppName ?: "선택한 앱"} 하루 ${appMinutes}분" else "${selectedAppName ?: "Selected app"}: $appMinutes minutes per day"
        FirstRuleGoal.Bedtime -> if (korean) "매일 오후 10시 – 오전 7시" else "Every day, 10 PM – 7 AM"
    }
    StepColumn {
        StepHeading(
            if (korean) "첫 규칙을 시작할까요?" else "Ready to start your first rule?",
            if (korean) "설정을 확인했어요. 시작한 뒤에도 규칙 탭에서 언제든 조정할 수 있습니다." else "Everything is ready. You can adjust the rule anytime from the Rules tab.",
        )
        Surface(
            color = ScreenRestPalette.CobaltSoft.copy(alpha = 0.82f),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SoftIconBubble(iconRes = R.drawable.ic_more_protection)
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        text = if (korean) "내 첫 번째 규칙" else "My first rule",
                        style = MaterialTheme.typography.labelLarge,
                        color = ScreenRestPalette.Cobalt,
                    )
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.headlineSmall,
                        color = ScreenRestPalette.Navy,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (korean) "필요한 권한 3개 준비 완료" else "All 3 required permissions are ready",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ScreenRestPalette.NavySoft,
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        PrimaryAction(
            label = if (activating) {
                if (korean) "보호를 시작하는 중…" else "Starting protection…"
            } else {
                if (korean) "지금 보호 시작" else "Start protection"
            },
            enabled = !activating,
            onClick = onActivate,
        )
        TextButton(onClick = onLater, modifier = Modifier.fillMaxWidth()) {
            Text(if (korean) "규칙만 저장하고 나중에 켤게요" else "Save the rule and turn it on later")
        }
    }
}

@Composable
private fun DoneStep(
    korean: Boolean,
    parentOnly: Boolean,
    onComplete: () -> Unit,
) {
    StepColumn(scrollable = true) {
        SuccessHalo()
        StepHeading(
            title = if (parentOnly) {
                if (korean) "부모 기기 준비가 끝났어요" else "Your parent device is ready"
            } else {
                if (korean) "첫 규칙이 시작됐어요" else "Your first rule is active"
            },
            body = if (parentOnly) {
                if (korean) "가족 탭에서 자녀 기기를 연결하면 상태와 요청을 확인할 수 있어요." else "Link a child device from the Family tab to see status and requests."
            } else {
                if (korean) "오늘 화면에서 사용 시간과 규칙 상태를 한눈에 확인할 수 있어요." else "The Today screen shows usage and rule status at a glance."
            },
            centered = true,
        )
        if (!parentOnly) {
            TodayPreviewCard(korean = korean)
        }
        Spacer(Modifier.height(6.dp))
        PrimaryAction(
            if (parentOnly) {
                if (korean) "홈으로 시작" else "Go to home"
            } else {
                if (korean) "오늘 화면으로 시작" else "Go to Today"
            },
            onClick = onComplete,
        )
    }
}

@Composable
private fun SuccessHalo() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(168.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(modifier = Modifier.size(160.dp), shape = CircleShape, color = ScreenRestPalette.TealSoft.copy(alpha = 0.42f)) {}
        Surface(modifier = Modifier.size(126.dp), shape = CircleShape, color = ScreenRestPalette.TealSoft.copy(alpha = 0.66f)) {}
        Surface(modifier = Modifier.size(86.dp), shape = CircleShape, color = Color.White.copy(alpha = 0.94f)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(42.dp),
                    tint = ScreenRestPalette.Teal,
                )
            }
        }
        Surface(
            modifier = Modifier
                .size(13.dp)
                .align(Alignment.CenterStart)
                .offset(x = 34.dp, y = (-20).dp),
            shape = CircleShape,
            color = ScreenRestPalette.Teal.copy(alpha = 0.38f),
        ) {}
        Surface(
            modifier = Modifier
                .size(12.dp)
                .align(Alignment.CenterEnd)
                .offset(x = (-34).dp, y = 30.dp),
            shape = CircleShape,
            color = ScreenRestPalette.Cobalt.copy(alpha = 0.26f),
        ) {}
    }
}

@Composable
private fun TodayPreviewCard(korean: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = ScreenRestPalette.CobaltSoft.copy(alpha = 0.68f),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = if (korean) "오늘 화면에서 이런 내용을 확인할 수 있어요." else "See these on the Today screen.",
                style = MaterialTheme.typography.titleSmall,
                color = ScreenRestPalette.NavyStrong,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PreviewFact(R.drawable.ic_nav_statistics, if (korean) "오늘의 사용 시간" else "Today's screen time")
                    PreviewFact(R.drawable.ic_family_clock, if (korean) "남은 시간" else "Time remaining")
                    PreviewFact(R.drawable.ic_nav_more, if (korean) "자주 사용하는 앱" else "Frequently used apps")
                }
                MiniTodayScreen()
            }
        }
    }
}

@Composable
private fun PreviewFact(iconRes: Int, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        SoftIconBubble(
            iconRes = iconRes,
            modifier = Modifier.size(34.dp),
            containerColor = Color.White.copy(alpha = 0.80f),
        )
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            color = ScreenRestPalette.NavyStrong,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun MiniTodayScreen() {
    Surface(
        modifier = Modifier
            .width(104.dp)
            .height(150.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.94f),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom,
            ) {
                listOf(20.dp, 32.dp, 25.dp, 42.dp).forEachIndexed { index, height ->
                    Surface(
                        modifier = Modifier
                            .width(10.dp)
                            .height(height),
                        shape = RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp),
                        color = if (index == 3) ScreenRestPalette.Cobalt else ScreenRestPalette.Cobalt.copy(alpha = 0.24f),
                    ) {}
                }
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .height(5.dp),
                shape = CircleShape,
                color = ScreenRestPalette.Cobalt,
            ) {}
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf(
                    ScreenRestPalette.Cobalt,
                    Color(0xFFFF7E7E),
                    Color(0xFFFFC857),
                    ScreenRestPalette.Teal,
                ).forEach { color ->
                    Surface(modifier = Modifier.size(16.dp), shape = RoundedCornerShape(5.dp), color = color.copy(alpha = 0.84f)) {}
                }
            }
        }
    }
}

@Composable
private fun StepColumn(
    scrollable: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val baseModifier = Modifier.fillMaxSize()
    Column(
        modifier = if (scrollable) baseModifier.verticalScroll(rememberScrollState()) else baseModifier,
        verticalArrangement = Arrangement.spacedBy(18.dp),
        content = content,
    )
}

@Composable
private fun StepHeading(title: String, body: String, centered: Boolean = false) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = ScreenRestPalette.NavyStrong,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = ScreenRestPalette.NavySoft,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        )
    }
}

@Composable
private fun ModeOption(
    iconRes: Int,
    selected: Boolean,
    title: String,
    body: String,
    onClick: () -> Unit,
) {
    SelectableCard(selected, onClick) {
        SoftIconBubble(
            iconRes = iconRes,
            containerColor = if (selected) Color.White.copy(alpha = 0.72f) else ScreenRestPalette.CobaltSoft,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = ScreenRestPalette.NavyStrong, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = ScreenRestPalette.NavySoft)
        }
        RadioButton(selected = selected, onClick = onClick)
    }
}

@Composable
private fun GoalOption(iconRes: Int, selected: Boolean, title: String, body: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(26.dp),
        color = if (selected) ScreenRestPalette.CobaltSoft.copy(alpha = 0.80f) else Color.Transparent,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 17.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SoftIconBubble(
                iconRes = iconRes,
                modifier = Modifier.size(54.dp),
                containerColor = if (selected) Color.White.copy(alpha = 0.78f) else ScreenRestPalette.CobaltSoft.copy(alpha = 0.86f),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = ScreenRestPalette.NavyStrong, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(body, style = MaterialTheme.typography.bodySmall, color = ScreenRestPalette.NavySoft)
            }
            SelectionDot(selected = selected)
        }
    }
}

@Composable
private fun SelectionDot(selected: Boolean) {
    Surface(
        modifier = Modifier.size(24.dp),
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.82f),
        border = BorderStroke(
            width = 2.dp,
            color = if (selected) ScreenRestPalette.Cobalt else ScreenRestPalette.NavySoft.copy(alpha = 0.48f),
        ),
    ) {
        if (selected) {
            Box(contentAlignment = Alignment.Center) {
                Surface(modifier = Modifier.size(12.dp), shape = CircleShape, color = ScreenRestPalette.Cobalt) {}
            }
        }
    }
}

@Composable
private fun SelectableCard(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        color = if (selected) ScreenRestPalette.CobaltSoft.copy(alpha = 0.88f) else Color.Transparent,
        border = BorderStroke(
            if (selected) 0.dp else 1.dp,
            if (selected) Color.Transparent else ScreenRestPalette.Border,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

@Composable
private fun InfoCard(title: String, body: String, iconRes: Int? = null) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = ScreenRestPalette.CobaltSoft.copy(alpha = 0.62f),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            iconRes?.let {
                SoftIconBubble(iconRes = it, modifier = Modifier.size(52.dp))
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = ScreenRestPalette.NavyStrong, fontWeight = FontWeight.Bold)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = ScreenRestPalette.NavySoft)
            }
        }
    }
}

@Composable
private fun SoftIconBubble(
    iconRes: Int,
    modifier: Modifier = Modifier,
    containerColor: Color = Color.White.copy(alpha = 0.72f),
    tint: Color = ScreenRestPalette.Cobalt,
) {
    Surface(
        modifier = modifier.size(60.dp),
        shape = CircleShape,
        color = containerColor,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = tint,
            )
        }
    }
}

@Composable
private fun SoftInfoGroup(
    items: List<OnboardingInfo>,
    title: String? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = ScreenRestPalette.CobaltSoft.copy(alpha = 0.64f),
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp)) {
            title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    color = ScreenRestPalette.NavyStrong,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
            items.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier.padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SoftIconBubble(
                        iconRes = item.iconRes,
                        modifier = Modifier.size(48.dp),
                        containerColor = Color.White.copy(alpha = 0.70f),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = ScreenRestPalette.NavyStrong,
                            fontWeight = FontWeight.Bold,
                        )
                        if (item.body.isNotBlank()) {
                            Text(
                                text = item.body,
                                style = MaterialTheme.typography.bodyMedium,
                                color = ScreenRestPalette.NavySoft,
                            )
                        }
                    }
                }
                if (index < items.lastIndex) {
                    HorizontalDivider(color = ScreenRestPalette.Border.copy(alpha = 0.72f))
                }
            }
        }
    }
}

@Composable
private fun DurationHero(value: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.size(214.dp),
            shape = CircleShape,
            color = ScreenRestPalette.CobaltSoft.copy(alpha = 0.34f),
        ) {}
        Surface(
            modifier = Modifier.size(176.dp),
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.54f),
        ) {}
        Surface(
            modifier = Modifier.size(142.dp),
            shape = CircleShape,
            color = ScreenRestPalette.WarmBackground.copy(alpha = 0.92f),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                SoftIconBubble(
                    iconRes = R.drawable.ic_family_clock,
                    modifier = Modifier.size(44.dp),
                    containerColor = ScreenRestPalette.CobaltSoft.copy(alpha = 0.78f),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineLarge,
                    color = ScreenRestPalette.NavyStrong,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun DurationLabels(korean: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val labels = if (korean) {
            listOf("30분", "1시간", "2시간", "3시간", "4시간")
        } else {
            listOf("30m", "1h", "2h", "3h", "4h")
        }
        labels.forEach { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = ScreenRestPalette.NavySoft,
            )
        }
    }
}

@Composable
private fun PermissionVisual(permission: MissingPermission?) {
    val iconRes = when (permission) {
        MissingPermission.Usage -> R.drawable.ic_nav_statistics
        MissingPermission.Overlay -> R.drawable.ic_block_manager
        MissingPermission.Notification -> R.drawable.ic_more_notifications
        null -> R.drawable.ic_more_protection
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(width = 270.dp, height = 160.dp)) {
            drawArc(
                color = ScreenRestPalette.Cobalt.copy(alpha = 0.24f),
                startAngle = 198f,
                sweepAngle = 142f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(38.dp.toPx(), 34.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(194.dp.toPx(), 104.dp.toPx()),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
            )
            drawCircle(
                color = ScreenRestPalette.Cobalt,
                radius = 5.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(size.width / 2f, 35.dp.toPx()),
            )
        }
        Surface(
            modifier = Modifier
                .size(116.dp)
                .align(Alignment.CenterStart)
                .offset(x = 28.dp, y = 16.dp),
            shape = CircleShape,
            color = ScreenRestPalette.CobaltSoft.copy(alpha = 0.58f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                SoftIconBubble(
                    iconRes = R.drawable.ic_family_device,
                    modifier = Modifier.size(72.dp),
                    containerColor = Color.White.copy(alpha = 0.84f),
                )
            }
        }
        Surface(
            modifier = Modifier
                .size(128.dp)
                .align(Alignment.CenterEnd)
                .offset(x = (-24).dp, y = 14.dp),
            shape = CircleShape,
            color = ScreenRestPalette.TealSoft.copy(alpha = 0.80f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                SoftIconBubble(
                    iconRes = iconRes,
                    modifier = Modifier.size(78.dp),
                    containerColor = Color.White.copy(alpha = 0.88f),
                )
            }
        }
    }
}

@Composable
private fun PermissionInfoPanel(
    title: String,
    body: String,
    iconRes: Int,
    korean: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = ScreenRestPalette.CobaltSoft.copy(alpha = 0.68f),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SoftIconBubble(
                    iconRes = iconRes,
                    modifier = Modifier.size(50.dp),
                    containerColor = Color.White.copy(alpha = 0.76f),
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = ScreenRestPalette.NavyStrong, fontWeight = FontWeight.Bold)
                    Text(body, style = MaterialTheme.typography.bodySmall, color = ScreenRestPalette.NavySoft)
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 14.dp),
                color = ScreenRestPalette.Border.copy(alpha = 0.72f),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_block_lock),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = ScreenRestPalette.Teal,
                )
                Text(
                    text = if (korean) "이 권한은 설정에서 언제든지 끌 수 있어요." else "You can turn this permission off at any time.",
                    style = MaterialTheme.typography.labelMedium,
                    color = ScreenRestPalette.NavySoft,
                )
            }
        }
    }
}

@Composable
private fun AppChoiceRow(app: InstalledAppInfo, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) ScreenRestPalette.CobaltSoft else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (selected) ScreenRestPalette.Cobalt else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(app.packageName, app.appName, 36.dp)
            Spacer(Modifier.width(12.dp))
            Text(app.appName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}

@Composable
private fun LargeValue(value: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = ScreenRestPalette.CobaltSoft.copy(alpha = 0.78f),
    ) {
        Text(
            text = value,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 26.dp),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = ScreenRestPalette.NavyStrong,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PrimaryAction(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
        shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ScreenRestPalette.Cobalt,
            contentColor = Color.White,
            disabledContainerColor = ScreenRestPalette.SurfaceStrong,
            disabledContentColor = ScreenRestPalette.Disabled,
        ),
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

private fun initialGuideStage(uiState: SafeModeUiState): GuideStage {
    val progress = uiState.onboardingProgress
    return when {
        progress.mode == null -> GuideStage.Welcome
        !uiState.monitoringDisclosureAccepted -> GuideStage.Notice
        !progress.setupPrepared && progress.mode == OnboardingMode.ParentOnly -> GuideStage.Pin
        !progress.setupPrepared -> GuideStage.Goal
        progress.mode == OnboardingMode.ParentOnly -> GuideStage.ParentDone
        !uiState.hasUsageAccess ||
            !uiState.blockingReadiness.overlayPermissionReady ||
            !uiState.blockingReadiness.notificationPermissionReady -> GuideStage.Permissions
        uiState.safeModeEnabled || !uiState.policyEnforcementEnabled -> GuideStage.Activate
        else -> GuideStage.Done
    }
}

private fun validatePin(
    pin: String,
    confirmation: String,
    confirmationRequired: Boolean,
    korean: Boolean,
): String = when {
    pin.length !in 4..8 -> if (korean) "숫자 4~8자리로 입력해 주세요." else "Enter 4 to 8 digits."
    confirmationRequired && pin != confirmation -> if (korean) "두 PIN이 일치하지 않아요." else "The PINs do not match."
    else -> ""
}

private fun String.onlyPinDigits(): String = filter(Char::isDigit).take(8)

private fun String.toOnboardingModeOrNull(): OnboardingMode? =
    OnboardingMode.entries.firstOrNull { it.name == this }

private fun String.toFirstRuleGoal(): FirstRuleGoal =
    FirstRuleGoal.entries.firstOrNull { it.name == this } ?: FirstRuleGoal.DailyLimit

private fun String.toGuideStage(): GuideStage =
    GuideStage.entries.firstOrNull { it.name == this } ?: GuideStage.Welcome
