package com.manisykh.screenrest.blocking

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.lifecycleScope
import com.manisykh.screenrest.AppIcon
import com.manisykh.screenrest.MainActivity
import com.manisykh.screenrest.R
import com.manisykh.screenrest.SecurePinTextField
import com.manisykh.screenrest.TimeWheelPickerDialog
import com.manisykh.screenrest.data.AppLanguage
import com.manisykh.screenrest.data.HardshipLevel
import com.manisykh.screenrest.data.HardshipPolicyKey
import com.manisykh.screenrest.data.HardshipPolicyType
import com.manisykh.screenrest.data.HardshipRuntimeState
import com.manisykh.screenrest.data.MAX_TEMPORARY_EXTRA_MINUTES
import com.manisykh.screenrest.data.HardshipLevelOneGrantResult
import com.manisykh.screenrest.data.HardshipLevelTwoUnlockResult
import com.manisykh.screenrest.data.EmergencyPassUseResult
import com.manisykh.screenrest.data.RemoteRequestBlockReason
import com.manisykh.screenrest.data.RemoteUnlockRequestSubmitResult
import com.manisykh.screenrest.data.ParentRemoteSyncDataSourceFactory
import com.manisykh.screenrest.data.ParentManagementState
import com.manisykh.screenrest.data.SettingsRepository
import com.manisykh.screenrest.data.TemporaryUnlockState
import com.manisykh.screenrest.data.UsagePolicySettings
import com.manisykh.screenrest.data.canRequestParentApproval
import com.manisykh.screenrest.data.emergencyPassExpiryFor
import com.manisykh.screenrest.data.levelOneReflectionEntry
import com.manisykh.screenrest.data.settingsDataStore
import com.manisykh.screenrest.formatLimitMinutesLabel
import com.manisykh.screenrest.ui.theme.AppOver
import com.manisykh.screenrest.ui.theme.AppSafe
import com.manisykh.screenrest.ui.theme.ScreenTimeManagerTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BlockedActivity : ComponentActivity() {
    private val repository by lazy {
        SettingsRepository(
            applicationContext.settingsDataStore,
            ParentRemoteSyncDataSourceFactory.create(applicationContext),
        )
    }
    private var previewOnlyActivity: Boolean = true
    private var blockedPackageName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appName = intent.getStringExtra(EXTRA_APP_NAME).orEmpty().ifBlank { "App" }
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()
        blockedPackageName = packageName
        val reason = intent.getStringExtra(EXTRA_REASON).orEmpty().ifBlank { "Limit exceeded" }
        val usedMinutes = intent.getIntExtra(EXTRA_USED_MINUTES, 0).coerceAtLeast(0)
        val limitMinutes = intent.getIntExtra(EXTRA_LIMIT_MINUTES, -1).takeIf { minutes -> minutes >= 0 }
        val showAppDetails = intent.getBooleanExtra(EXTRA_SHOW_APP_DETAILS, true)
        val previewOnly = intent.getBooleanExtra(EXTRA_PREVIEW_ONLY, true)
        val hardshipLevel = HardshipLevel.fromStorageValue(
            intent.getIntExtra(EXTRA_HARDSHIP_LEVEL, HardshipLevel.Off.storageValue),
        )
        val hardshipPolicyKey = intent.getStringExtra(EXTRA_HARDSHIP_POLICY_KEY)
            ?.let(HardshipPolicyKey::fromStorageKey)
        val activeHardshipPolicyKeys = intent.getStringExtra(EXTRA_ACTIVE_HARDSHIP_POLICY_KEYS)
            .orEmpty()
            .split(',')
            .mapNotNull(HardshipPolicyKey::fromStorageKey)
            .toSet()
            .ifEmpty { listOfNotNull(hardshipPolicyKey).toSet() }
        val hardshipPolicyType = hardshipPolicyKey?.policyType ?: reason.toHardshipPolicyType()
        previewOnlyActivity = previewOnly

        val blockedSystemBarStyle = SystemBarStyle.dark(BLOCK_SCREEN_BACKGROUND_ARGB)
        enableEdgeToEdge(
            statusBarStyle = blockedSystemBarStyle,
            navigationBarStyle = blockedSystemBarStyle,
        )
        window.isNavigationBarContrastEnforced = false

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (previewOnly) {
                        finish()
                    } else {
                        sendHomeAndFinish()
                    }
                }
            },
        )

        setContent {
            ScreenTimeManagerTheme {
                val language by repository.appLanguage.collectAsState(initial = AppLanguage.Korean)
                val parentState by repository.parentManagementState.collectAsState(initial = ParentManagementState())
                val hardshipRuntimeState by repository.hardshipRuntimeState.collectAsState(
                    initial = HardshipRuntimeState(),
                )
                val temporaryUnlockState by repository.temporaryUnlockState.collectAsState(
                    initial = TemporaryUnlockState(),
                )
                val usagePolicySettings by repository.usagePolicySettings.collectAsState(
                    initial = UsagePolicySettings(),
                )
                val hardshipAllowanceEnded = temporaryUnlockState.forToday()
                    .packageAllowances[packageName]
                    ?.hardshipAllowanceUntilMillis
                    ?.let { untilMillis ->
                        untilMillis > 0L && untilMillis <= System.currentTimeMillis()
                    } == true
                val emergencyPassExpiresAtMillis = activeHardshipPolicyKeys
                    .maxOfOrNull { policyKey ->
                        usagePolicySettings.emergencyPassExpiryFor(policyKey)
                    } ?: 0L
                BlockedScreen(
                    appName = appName,
                    packageName = packageName,
                    reason = reason,
                    usedMinutes = usedMinutes,
                    limitMinutes = limitMinutes,
                    showAppDetails = showAppDetails,
                    previewOnly = previewOnly,
                    hardshipLevel = hardshipLevel,
                    hardshipPolicyType = hardshipPolicyType,
                    canRequestParent = parentState.canRequestParentApproval() &&
                        reason != "parent immediate block active",
                    hardshipAllowanceEnded = hardshipAllowanceEnded,
                    levelOneReflectionReadyAtMillis = hardshipPolicyKey?.let { policyKey ->
                        hardshipRuntimeState.forToday()
                            .levelOneReflectionEntry(policyKey, packageName)
                            ?.readyAtMillis
                    } ?: 0L,
                    emergencyPassNextAvailableAtMillis = hardshipRuntimeState.emergencyPassNextAvailableAtMillis(),
                    emergencyPassExpiresAtMillis = emergencyPassExpiresAtMillis,
                    language = language,
                    text = blockedScreenStrings(language),
                    onSafeRecovery = { pin, onResult ->
                        lifecycleScope.launch {
                            val unlocked = repository.safeRecovery(pin)
                            onResult(unlocked)
                            if (unlocked) {
                                finish()
                            }
                        }
                    },
                    onLevelOneAllowance = { onResult ->
                        lifecycleScope.launch {
                            val result = hardshipPolicyKey?.let { policyKey ->
                                repository.requestLevelOneAllowance(
                                    policyKey = policyKey,
                                    packageName = packageName,
                                    appName = appName,
                                )
                            } ?: HardshipLevelOneGrantResult.NotAvailable
                            onResult(result)
                            if (result == HardshipLevelOneGrantResult.Granted) finish()
                        }
                    },
                    onEmergencyPass = { pin, onResult ->
                        lifecycleScope.launch {
                            val result = if (hardshipPolicyKey == null) {
                                EmergencyPassUseResult.NotAvailable
                            } else {
                                repository.useHardshipEmergencyPass(
                                    adminPin = pin,
                                    packageName = packageName,
                                    blockingPolicyKeys = activeHardshipPolicyKeys,
                                )
                            }
                            onResult(result)
                            if (result == EmergencyPassUseResult.Used) {
                                val nextAvailableAtMillis = System.currentTimeMillis() +
                                    7L * 24L * 60L * 60L * 1000L
                                val message = if (language == AppLanguage.Korean) {
                                    "$appName 앱을 ${formatEmergencyPassTime(emergencyPassExpiresAtMillis)}까지 허용했습니다. " +
                                        "다음 사용 가능 ${formatEmergencyPassTime(nextAvailableAtMillis)}"
                                } else {
                                    "$appName is allowed until ${formatEmergencyPassTime(emergencyPassExpiresAtMillis)}. " +
                                        "Available again ${formatEmergencyPassTime(nextAvailableAtMillis)}"
                                }
                                Toast.makeText(this@BlockedActivity, message, Toast.LENGTH_LONG).show()
                                finish()
                            }
                        }
                    },
                    onRequestParent = { onResult ->
                        lifecycleScope.launch {
                            val remoteReason = hardshipPolicyType?.toRemoteRequestBlockReason()
                            var submission = if (remoteReason == null) {
                                RemoteUnlockRequestSubmitResult.Failed("Block reason unavailable")
                            } else {
                                repository.createRemoteUnlockRequest(
                                    blockReason = remoteReason,
                                    targetPackageName = packageName,
                                    targetAppName = appName,
                                    targetGroupName = "",
                                    scheduleName = "",
                                    usedMillis = usedMinutes.toLong() * 60_000L,
                                    limitMillis = limitMinutes?.toLong()?.times(60_000L),
                                    alreadyGrantedExtraMinutes = 0,
                                    unlockedForToday = false,
                                    requestedMinutes = 5,
                                )
                            }
                            onResult(submission)
                            var retryDelayMillis = 2_000L
                            while (submission is RemoteUnlockRequestSubmitResult.Retrying) {
                                delay(retryDelayMillis)
                                submission = repository.retryRemoteUnlockRequest(submission.requestId)
                                onResult(submission)
                                retryDelayMillis = (retryDelayMillis * 2L).coerceAtMost(30_000L)
                            }
                        }
                    },
                    onLevelTwoUnlock = { pin, onResult ->
                        lifecycleScope.launch {
                            val result = hardshipPolicyKey?.let { policyKey ->
                                repository.requestLevelTwoPolicyUnlock(policyKey, pin)
                            } ?: HardshipLevelTwoUnlockResult.NotAvailable
                            onResult(result)
                            if (result == HardshipLevelTwoUnlockResult.Unlocked) finish()
                        }
                    },
                    onParentAddTime = { pin, minutes, onResult ->
                        lifecycleScope.launch {
                            val granted = if (showAppDetails) {
                                repository.addTemporaryAppTime(
                                    packageName = packageName,
                                    appName = appName,
                                    extraMinutes = minutes,
                                    adminPin = pin,
                                )
                            } else {
                                repository.addTemporaryTotalTime(
                                    extraMinutes = minutes,
                                    adminPin = pin,
                                )
                            }
                            onResult(granted)
                            if (granted) {
                                finish()
                            }
                        }
                    },
                    onParentUnlockToday = { pin, onResult ->
                        lifecycleScope.launch {
                            val granted = if (showAppDetails) {
                                repository.unlockAppForToday(
                                    packageName = packageName,
                                    appName = appName,
                                    adminPin = pin,
                                )
                            } else {
                                repository.unlockTotalForToday(adminPin = pin)
                            }
                            onResult(granted)
                            if (granted) {
                                finish()
                            }
                        }
                    },
                    onClosePreview = {
                        if (previewOnly) {
                            finish()
                        } else {
                            sendHomeAndFinish()
                        }
                    },
                    onOpenManager = {
                        UsageMonitorForegroundService.managerVisible(this, blockedPackageName)
                        startActivity(
                            Intent(this, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                                putExtra(MainActivity.EXTRA_SUPPRESS_PERMISSION_SETUP_AUTO_DIALOG, true)
                            },
                        )
                        finish()
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!previewOnlyActivity) {
            UsageMonitorForegroundService.blockedActivityVisible(this, blockedPackageName)
        }
    }

    private fun sendHomeAndFinish() {
        UsageMonitorForegroundService.allowHomeExit(this, blockedPackageName)
        startActivity(
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            },
        )
        finish()
    }

    companion object {
        private val BLOCK_SCREEN_BACKGROUND_ARGB = 0xFF101E36.toInt()
        private const val EXTRA_APP_NAME = "extra_app_name"
        private const val EXTRA_PACKAGE_NAME = "extra_package_name"
        private const val EXTRA_REASON = "extra_reason"
        private const val EXTRA_USED_MINUTES = "extra_used_minutes"
        private const val EXTRA_LIMIT_MINUTES = "extra_limit_minutes"
        private const val EXTRA_SHOW_APP_DETAILS = "extra_show_app_details"
        private const val EXTRA_PREVIEW_ONLY = "extra_preview_only"
        private const val EXTRA_HARDSHIP_LEVEL = "extra_hardship_level"
        private const val EXTRA_HARDSHIP_POLICY_KEY = "extra_hardship_policy_key"
        private const val EXTRA_ACTIVE_HARDSHIP_POLICY_KEYS = "extra_active_hardship_policy_keys"

        fun previewIntent(
            context: Context,
            appName: String,
            packageName: String,
            reason: String,
            usedMinutes: Int,
            limitMinutes: Int?,
            showAppDetails: Boolean,
        ): Intent {
            return Intent(context, BlockedActivity::class.java).apply {
                putExtra(EXTRA_APP_NAME, appName)
                putExtra(EXTRA_PACKAGE_NAME, packageName)
                putExtra(EXTRA_REASON, reason)
                putExtra(EXTRA_USED_MINUTES, usedMinutes)
                if (limitMinutes != null) {
                    putExtra(EXTRA_LIMIT_MINUTES, limitMinutes)
                }
                putExtra(EXTRA_SHOW_APP_DETAILS, showAppDetails)
                putExtra(EXTRA_PREVIEW_ONLY, true)
            }
        }

        fun blockIntent(
            context: Context,
            appName: String,
            packageName: String,
            reason: String,
            usedMinutes: Int,
            limitMinutes: Int?,
            showAppDetails: Boolean,
            hardshipLevel: HardshipLevel = HardshipLevel.Off,
            hardshipPolicyKey: HardshipPolicyKey? = null,
            activeHardshipPolicyKeys: Set<HardshipPolicyKey> = emptySet(),
        ): Intent {
            return Intent(context, BlockedActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
                putExtra(EXTRA_APP_NAME, appName)
                putExtra(EXTRA_PACKAGE_NAME, packageName)
                putExtra(EXTRA_REASON, reason)
                putExtra(EXTRA_USED_MINUTES, usedMinutes)
                if (limitMinutes != null) {
                    putExtra(EXTRA_LIMIT_MINUTES, limitMinutes)
                }
                putExtra(EXTRA_SHOW_APP_DETAILS, showAppDetails)
                putExtra(EXTRA_PREVIEW_ONLY, false)
                putExtra(EXTRA_HARDSHIP_LEVEL, hardshipLevel.storageValue)
                hardshipPolicyKey?.let { key -> putExtra(EXTRA_HARDSHIP_POLICY_KEY, key.storageKey) }
                putExtra(
                    EXTRA_ACTIVE_HARDSHIP_POLICY_KEYS,
                    activeHardshipPolicyKeys.joinToString(",") { key -> key.storageKey },
                )
            }
        }
    }
}

@Composable
private fun BlockedScreen(
    appName: String,
    packageName: String,
    reason: String,
    usedMinutes: Int,
    limitMinutes: Int?,
    showAppDetails: Boolean,
    previewOnly: Boolean,
    hardshipLevel: HardshipLevel,
    hardshipPolicyType: HardshipPolicyType?,
    canRequestParent: Boolean,
    hardshipAllowanceEnded: Boolean,
    levelOneReflectionReadyAtMillis: Long,
    emergencyPassNextAvailableAtMillis: Long,
    emergencyPassExpiresAtMillis: Long,
    language: AppLanguage,
    text: BlockedScreenStrings,
    onSafeRecovery: (String, (Boolean) -> Unit) -> Unit,
    onLevelOneAllowance: ((HardshipLevelOneGrantResult) -> Unit) -> Unit,
    onEmergencyPass: (String, (EmergencyPassUseResult) -> Unit) -> Unit,
    onRequestParent: ((RemoteUnlockRequestSubmitResult) -> Unit) -> Unit,
    onLevelTwoUnlock: (String, (HardshipLevelTwoUnlockResult) -> Unit) -> Unit,
    onParentAddTime: (String, Int, (Boolean) -> Unit) -> Unit,
    onParentUnlockToday: (String, (Boolean) -> Unit) -> Unit,
    onClosePreview: () -> Unit,
    onOpenManager: () -> Unit,
) {
    var extraMinutes by remember { mutableStateOf(30) }
    var showExtraTimePicker by remember { mutableStateOf(false) }
    var pendingPinAction by remember { mutableStateOf<BlockedPinAction?>(null) }
    var actionPin by remember { mutableStateOf("") }
    var actionPinFailed by remember { mutableStateOf(false) }
    var actionSubmitting by remember { mutableStateOf(false) }
    var hardshipStatus by remember { mutableStateOf("") }
    var showEmergencyPassConfirmation by remember { mutableStateOf(false) }
    var parentRequestInProgress by remember { mutableStateOf(false) }
    var countdownNowMillis by remember(levelOneReflectionReadyAtMillis) {
        mutableStateOf(System.currentTimeMillis())
    }
    LaunchedEffect(levelOneReflectionReadyAtMillis) {
        while (
            levelOneReflectionReadyAtMillis > 0L &&
            countdownNowMillis < levelOneReflectionReadyAtMillis
        ) {
            delay(1_000L)
            countdownNowMillis = System.currentTimeMillis()
        }
    }
    val levelOneReflectionRemainingMillis =
        (levelOneReflectionReadyAtMillis - countdownNowMillis).coerceAtLeast(0L)
    val levelOneReflectionWaiting = levelOneReflectionReadyAtMillis > 0L &&
        levelOneReflectionRemainingMillis > 0L
    val levelOneActionLabel = when {
        levelOneReflectionWaiting -> {
            val totalSeconds = (levelOneReflectionRemainingMillis + 999L) / 1_000L
            val minutes = totalSeconds / 60L
            val seconds = totalSeconds % 60L
            if (language == AppLanguage.Korean) {
                "숙고 중 · %02d:%02d 남음".format(minutes, seconds)
            } else {
                "Reflecting · %02d:%02d left".format(minutes, seconds)
            }
        }
        levelOneReflectionReadyAtMillis > 0L ->
            if (language == AppLanguage.Korean) "5분 임시 허용" else "Allow for 5 minutes"
        else -> text.hardshipReflectionAction
    }
    val emergencyPassAvailable = emergencyPassNextAvailableAtMillis <= 0L ||
        System.currentTimeMillis() >= emergencyPassNextAvailableAtMillis
    val visual = BlockScreenVisualModel.forReason(reason, hardshipLevel)
    val localizedReason = text.blockReason(reason)
    val blockedTitle = if (showAppDetails) text.blockTitle(reason) else text.dailyTitle

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(visual.background))
            .safeDrawingPadding()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(visual.accentSoft),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_block_lock),
                        contentDescription = null,
                        modifier = Modifier.padding(22.dp).size(42.dp),
                        tint = Color(visual.accent),
                    )
                }
                Surface(shape = CircleShape, color = Color(0xFF2A3B59)) {
                    Text(
                        text = if (previewOnly) text.preview else BlockScreenVisualModel.categoryLabel(
                            reason,
                            language == AppLanguage.Korean,
                        ),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }

                Text(
                    text = blockedTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(visual.onDark),
                    textAlign = TextAlign.Center,
                )
                if (showAppDetails) {
                    AppIcon(
                        packageName = packageName,
                        contentDescription = appName,
                        size = 48.dp,
                    )
                    Text(
                        text = appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(visual.onDark),
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = if (reason == "parent immediate block active") {
                            if (language == AppLanguage.Korean) "부모가 설정한 차단이 종료될 때까지 사용할 수 없습니다."
                            else "Blocked until the parent's timer ends."
                        } else text.usedReason(usedMinutes, limitMinutes, localizedReason),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(visual.onDarkMuted),
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Text(
                        text = text.dailyReason(usedMinutes, limitMinutes),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(visual.onDarkMuted),
                        textAlign = TextAlign.Center,
                    )
                }
                if (reason != "parent immediate block active") {
                    Text(
                        text = text.remaining,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(visual.accent),
                        fontWeight = FontWeight.Bold,
                    )
                }

                if (hardshipLevel == HardshipLevel.Level1 && hardshipAllowanceEnded) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(visual.accentSoft),
                        border = BorderStroke(1.dp, Color(visual.accent)),
                    ) {
                        Text(
                            text = text.hardshipAllowanceExpired,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(visual.onDark),
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                if (hardshipLevel != HardshipLevel.Off) {
                    BlockedHardshipIndicator(
                        level = hardshipLevel,
                        text = text,
                        accentColor = Color(visual.accent),
                    )
                    Text(
                        text = text.hardshipDescription(hardshipLevel, canRequestParent),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(visual.onDarkMuted),
                        textAlign = TextAlign.Center,
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = Color(visual.panel),
                    border = BorderStroke(1.dp, Color(visual.panelBorder)),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {

                if (!previewOnly && hardshipLevel == HardshipLevel.Off &&
                    reason != "parent immediate block active") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        OutlinedButton(
                            onClick = { showExtraTimePicker = true },
                            modifier = Modifier.fillMaxWidth().height(58.dp),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            BlockedActionLabel(
                                R.drawable.ic_block_add_time,
                                text.addTime,
                                Color(0xFF2563EB),
                            )
                        }
                        Button(
                            onClick = {
                                actionPin = ""
                                actionPinFailed = false
                                actionSubmitting = false
                                pendingPinAction = BlockedPinAction.UnlockToday
                            },
                            modifier = Modifier.fillMaxWidth().height(58.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE8F7EF),
                                contentColor = Color(0xFF047857),
                            ),
                        ) {
                            BlockedActionLabel(
                                R.drawable.ic_block_unlock_today,
                                text.unlockToday,
                                Color(0xFF059669),
                            )
                        }
                    }
                }

                if (!previewOnly && hardshipLevel == HardshipLevel.Level1) {
                    Button(
                        onClick = {
                            onLevelOneAllowance { result ->
                                hardshipStatus = text.hardshipLevelOneResult(result)
                            }
                        },
                        enabled = !levelOneReflectionWaiting,
                        modifier = Modifier.fillMaxWidth().height(58.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = hardshipColor(HardshipLevel.Level1),
                        ),
                    ) {
                        BlockedActionLabel(
                            R.drawable.ic_block_add_time,
                            levelOneActionLabel,
                            Color.White,
                        )
                    }
                }

                if (
                    !previewOnly &&
                    canRequestParent &&
                    hardshipLevel != HardshipLevel.Level3
                ) {
                    OutlinedButton(
                        onClick = {
                            parentRequestInProgress = true
                            hardshipStatus = text.parentRequestSent
                            onRequestParent { result ->
                                when (result) {
                                    is RemoteUnlockRequestSubmitResult.Sent -> {
                                        parentRequestInProgress = true
                                        hardshipStatus = text.parentRequestSent
                                    }
                                    is RemoteUnlockRequestSubmitResult.Retrying -> {
                                        parentRequestInProgress = true
                                        hardshipStatus = text.parentRequestRetrying
                                    }
                                    RemoteUnlockRequestSubmitResult.NotPaired -> {
                                        parentRequestInProgress = false
                                        hardshipStatus = text.parentRequestNotPaired
                                    }
                                    is RemoteUnlockRequestSubmitResult.Failed -> {
                                        parentRequestInProgress = false
                                        hardshipStatus = text.parentRequestFailed
                                    }
                                }
                            }
                        },
                        enabled = !parentRequestInProgress,
                        modifier = Modifier.fillMaxWidth().height(58.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF7C3AED),
                        ),
                    ) {
                        BlockedActionLabel(
                            R.drawable.ic_block_parent,
                            text.requestParent,
                            Color(0xFF7C3AED),
                        )
                    }
                }

                if (!previewOnly && hardshipLevel == HardshipLevel.Level2) {
                    Button(
                        onClick = {
                            actionPin = ""
                            actionPinFailed = false
                            actionSubmitting = false
                            pendingPinAction = BlockedPinAction.LevelTwoUnlock
                        },
                        modifier = Modifier.fillMaxWidth().height(58.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = hardshipColor(HardshipLevel.Level2),
                        ),
                    ) {
                        BlockedActionLabel(
                            R.drawable.ic_block_unlock_today,
                            text.hardshipLevel2Action,
                            Color.White,
                        )
                    }
                }

                if (hardshipStatus.isNotBlank()) {
                    Text(
                        text = hardshipStatus,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = hardshipColor(hardshipLevel),
                        textAlign = TextAlign.Center,
                    )
                }

                if (hardshipLevel == HardshipLevel.Level3) {
                    Text(
                        text = if (emergencyPassAvailable) {
                            if (language == AppLanguage.Korean) "Emergency Pass 사용 가능 · 1회" else "Emergency Pass available · 1 use"
                        } else {
                            if (language == AppLanguage.Korean) {
                                "Emergency Pass 사용 완료 · 다음 사용 가능 ${formatEmergencyPassTime(emergencyPassNextAvailableAtMillis)}"
                            } else {
                                "Emergency Pass used · available again ${formatEmergencyPassTime(emergencyPassNextAvailableAtMillis)}"
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (emergencyPassAvailable) AppSafe else AppOver,
                        textAlign = TextAlign.Center,
                    )
                }
                Button(
                    onClick = {
                        actionPin = ""
                        actionPinFailed = false
                        actionSubmitting = false
                        pendingPinAction = if (hardshipLevel == HardshipLevel.Level3) {
                            showEmergencyPassConfirmation = true
                            null
                        } else {
                            BlockedPinAction.SafeRecovery
                        }
                    },
                    enabled = !previewOnly &&
                        (hardshipLevel != HardshipLevel.Level3 || emergencyPassAvailable),
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFDE8EC),
                        contentColor = Color(0xFFBE123C),
                        disabledContainerColor = Color(0xFFF3F4F6),
                        disabledContentColor = Color(0xFF9CA3AF),
                    ),
                ) {
                    BlockedActionLabel(
                        R.drawable.ic_block_emergency,
                        if (hardshipLevel == HardshipLevel.Level3) text.emergencyPass else text.safeRecovery,
                        Color(0xFFDC2626),
                    )
                }

                if (!previewOnly) {
                    Button(
                        onClick = onOpenManager,
                        modifier = Modifier.fillMaxWidth().height(58.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEEF2FF),
                            contentColor = Color(0xFF4338CA),
                        ),
                    ) {
                        BlockedActionLabel(
                            R.drawable.ic_block_manager,
                            text.openManager,
                            Color(0xFF4F46E5),
                        )
                    }
                }

                    }
                }

                OutlinedButton(
                    onClick = onClosePreview,
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFF778BAE)),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White,
                    ),
                ) {
                    BlockedActionLabel(
                        R.drawable.ic_block_home,
                        if (previewOnly) text.close else text.back,
                        Color.White,
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = text.safetyNote,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(visual.onDarkMuted),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    if (showExtraTimePicker) {
        TimeWheelPickerDialog(
            title = text.extraTime,
            valueMinutes = extraMinutes,
            lowerBound = 1,
            upperBound = MAX_TEMPORARY_EXTRA_MINUTES,
            displayValue = ::formatLimitMinutesLabel,
            saveLabel = if (language == AppLanguage.Korean) "다음" else "Next",
            onDismiss = { showExtraTimePicker = false },
            onApply = { minutes ->
                extraMinutes = minutes
                showExtraTimePicker = false
                actionPin = ""
                actionPinFailed = false
                actionSubmitting = false
                pendingPinAction = BlockedPinAction.AddTime
            },
            headerIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_block_add_time),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp),
                )
            },
        )
    }

    if (showEmergencyPassConfirmation) {
        val expiryLabel = if (emergencyPassExpiresAtMillis > 0L) {
            formatEmergencyPassTime(emergencyPassExpiresAtMillis)
        } else if (language == AppLanguage.Korean) {
            "현재 정책 종료 시점"
        } else {
            "the current policy end"
        }
        AlertDialog(
            onDismissRequest = { showEmergencyPassConfirmation = false },
            title = { Text(if (language == AppLanguage.Korean) "Emergency Pass 확인" else "Confirm Emergency Pass") },
            text = {
                Text(
                    if (language == AppLanguage.Korean) {
                        "$appName 앱을 $expiryLabel 까지 허용합니다. 현재 이 앱을 막고 있는 정책에만 예외가 적용됩니다. 사용 후 7일 동안 다른 앱과 다른 고행 3단계에서도 Pass를 사용할 수 없으며 취소하거나 되돌릴 수 없습니다."
                    } else {
                        "Allow $appName until $expiryLabel. Only policies currently blocking this app are bypassed. The Pass cannot be used again for any app or level-3 policy for 7 days, and it cannot be refunded."
                    },
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEmergencyPassConfirmation = false
                        actionPin = ""
                        actionPinFailed = false
                        actionSubmitting = false
                        pendingPinAction = BlockedPinAction.EmergencyPass
                    },
                ) {
                    Text(if (language == AppLanguage.Korean) "관리 PIN 입력" else "Enter Admin PIN")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showEmergencyPassConfirmation = false }) {
                    Text(if (language == AppLanguage.Korean) "취소" else "Cancel")
                }
            },
        )
    }

    pendingPinAction?.let { action ->
        BlockedPinDialog(
            title = when (action) {
                BlockedPinAction.AddTime -> "${text.addTime} · ${formatLimitMinutesLabel(extraMinutes)}"
                BlockedPinAction.UnlockToday -> text.unlockToday
                BlockedPinAction.SafeRecovery -> text.safeRecovery
                BlockedPinAction.EmergencyPass -> text.emergencyPass
                BlockedPinAction.LevelTwoUnlock -> text.hardshipLevel2Action
            },
            pin = actionPin,
            pinLabel = text.parentPin,
            errorText = if (actionPinFailed) {
                text.invalidParentPin
            } else {
                ""
            },
            confirmLabel = when (action) {
                BlockedPinAction.AddTime -> text.addTime
                BlockedPinAction.UnlockToday -> text.unlockToday
                BlockedPinAction.SafeRecovery -> text.safeRecovery
                BlockedPinAction.EmergencyPass -> if (language == AppLanguage.Korean) "Pass 사용" else "Use Pass"
                BlockedPinAction.LevelTwoUnlock -> if (language == AppLanguage.Korean) "확인" else "Confirm"
            },
            cancelLabel = if (language == AppLanguage.Korean) "취소" else "Cancel",
            submitting = actionSubmitting,
            onPinChanged = { value ->
                actionPin = value.filter(Char::isDigit)
                actionPinFailed = false
            },
            onDismiss = {
                pendingPinAction = null
                actionPin = ""
                actionPinFailed = false
                actionSubmitting = false
            },
            onConfirm = {
                actionSubmitting = true
                when (action) {
                    BlockedPinAction.AddTime -> {
                        onParentAddTime(actionPin, extraMinutes) { granted ->
                            actionSubmitting = false
                            actionPinFailed = !granted
                            if (granted) pendingPinAction = null
                        }
                    }
                    BlockedPinAction.UnlockToday -> {
                        onParentUnlockToday(actionPin) { granted ->
                            actionSubmitting = false
                            actionPinFailed = !granted
                            if (granted) pendingPinAction = null
                        }
                    }
                    BlockedPinAction.SafeRecovery -> {
                        onSafeRecovery(actionPin) { unlocked ->
                            actionSubmitting = false
                            actionPinFailed = !unlocked
                            if (unlocked) pendingPinAction = null
                        }
                    }
                    BlockedPinAction.EmergencyPass -> {
                        onEmergencyPass(actionPin) { result ->
                            actionSubmitting = false
                            actionPinFailed = result == EmergencyPassUseResult.InvalidPin
                            hardshipStatus = if (result == EmergencyPassUseResult.Used) {
                                val expiry = if (emergencyPassExpiresAtMillis > 0L) {
                                    formatEmergencyPassTime(emergencyPassExpiresAtMillis)
                                } else {
                                    if (language == AppLanguage.Korean) "현재 정책 종료 시점" else "the current policy end"
                                }
                                val nextAvailable = formatEmergencyPassTime(
                                    System.currentTimeMillis() + 7L * 24L * 60L * 60L * 1000L,
                                )
                                if (language == AppLanguage.Korean) {
                                    "$appName 앱을 $expiry 까지 허용했습니다. 다음 사용 가능: $nextAvailable"
                                } else {
                                    "$appName is allowed until $expiry. Available again: $nextAvailable"
                                }
                            } else {
                                text.emergencyPassResult(result)
                            }
                            if (result != EmergencyPassUseResult.InvalidPin) pendingPinAction = null
                        }
                    }
                    BlockedPinAction.LevelTwoUnlock -> {
                        onLevelTwoUnlock(actionPin) { result ->
                            actionSubmitting = false
                            actionPinFailed = result == HardshipLevelTwoUnlockResult.InvalidPin
                            hardshipStatus = if (
                                result == HardshipLevelTwoUnlockResult.Unlocked &&
                                hardshipPolicyType == HardshipPolicyType.Schedule
                            ) {
                                if (language == AppLanguage.Korean) {
                                    "고행 2단계를 이번 스케줄에서 종료했습니다."
                                } else {
                                    "Level 2 was ended for this schedule occurrence."
                                }
                            } else {
                                text.hardshipLevelTwoResult(result)
                            }
                            if (result != HardshipLevelTwoUnlockResult.InvalidPin) pendingPinAction = null
                        }
                    }
                }
            },
        )
    }
}

private enum class BlockedPinAction {
    AddTime,
    UnlockToday,
    SafeRecovery,
    EmergencyPass,
    LevelTwoUnlock,
}

@Composable
private fun BlockedActionLabel(iconRes: Int, label: String, iconTint: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(26.dp),
            tint = iconTint,
        )
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            textAlign = TextAlign.Start,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun BlockedPinDialog(
    title: String,
    pin: String,
    pinLabel: String,
    errorText: String,
    confirmLabel: String,
    cancelLabel: String,
    submitting: Boolean,
    onPinChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                SecurePinTextField(
                    value = pin,
                    onValueChange = onPinChanged,
                    label = pinLabel,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (errorText.isNotBlank()) {
                    Text(
                        errorText,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = AppOver,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(cancelLabel)
                    }
                    Button(
                        onClick = onConfirm,
                        enabled = pin.isNotBlank() && !submitting,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(confirmLabel, maxLines = 2, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

private fun formatEmergencyPassTime(timestampMillis: Long): String {
    return SimpleDateFormat("M/d HH:mm", Locale.getDefault()).format(Date(timestampMillis))
}

@Composable
private fun BlockedHardshipIndicator(
    level: HardshipLevel,
    text: BlockedScreenStrings,
    accentColor: Color,
) {
    val levelColor = accentColor
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_hardship_meditation),
            contentDescription = null,
            tint = levelColor,
            modifier = Modifier.size(34.dp),
        )
        Text(
            text = text.hardshipLevelLabel(level),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = levelColor,
        )
    }
}

@Composable
private fun hardshipColor(level: HardshipLevel): Color {
    return when (level) {
        HardshipLevel.Off -> MaterialTheme.colorScheme.onSurfaceVariant
        HardshipLevel.Level1 -> Color(0xFFB07A16)
        HardshipLevel.Level2 -> Color(0xFFE26822)
        HardshipLevel.Level3 -> Color(0xFF8E2745)
    }
}

private data class BlockedScreenStrings(
    val preview: String,
    val blocked: String,
    val dailyTitle: String,
    val appTitle: String,
    val blockTitle: (String) -> String,
    val blockReason: (String) -> String,
    val usedReason: (Int, Int?, String) -> String,
    val dailyReason: (Int, Int?) -> String,
    val remaining: String,
    val parentControls: String,
    val parentPin: String,
    val extraTime: String,
    val extraTimeHint: String,
    val addTime: String,
    val unlockToday: String,
    val invalidParentPin: String,
    val safeRecovery: String,
    val openManager: String,
    val close: String,
    val back: String,
    val safetyNote: String,
    val hardshipLevelPrefix: String,
    val hardshipLevel1Description: String,
    val hardshipLevel2Description: String,
    val hardshipLevel3Description: String,
    val hardshipReflectionAction: String,
    val hardshipReflectionStarted: String,
    val hardshipReflectionWaiting: String,
    val hardshipFiveMinutesGranted: String,
    val hardshipAllowanceExpired: String,
    val hardshipAllowanceLimitReached: String,
    val hardshipUnavailable: String,
    val emergencyPass: String,
    val emergencyPassUsed: String,
    val emergencyPassCooldown: String,
    val requestParent: String,
    val parentRequestSent: String,
    val parentRequestRetrying: String,
    val parentRequestNotPaired: String,
    val parentRequestFailed: String,
    val hardshipLevel2Action: String,
    val hardshipLevel2Started: String,
    val hardshipLevel2Waiting: String,
    val hardshipLevel2Unlocked: String,
)

private fun BlockedScreenStrings.hardshipLevelLabel(level: HardshipLevel): String =
    if (hardshipLevelPrefix == "Level") "Level ${level.storageValue}" else "${level.storageValue}단계"

private fun BlockedScreenStrings.hardshipDescription(
    level: HardshipLevel,
    canRequestParent: Boolean,
): String = when (level) {
    HardshipLevel.Off -> ""
    HardshipLevel.Level1 -> hardshipLevel1Description
    HardshipLevel.Level2 -> if (canRequestParent) {
        "$hardshipLevel2Description ${if (hardshipLevelPrefix == "Level") "You can also ask the linked parent for approval." else "연결된 부모에게 승인도 요청할 수 있습니다."}"
    } else {
        hardshipLevel2Description
    }
    HardshipLevel.Level3 -> hardshipLevel3Description
}

private fun BlockedScreenStrings.hardshipLevelOneResult(result: HardshipLevelOneGrantResult): String = when (result) {
    HardshipLevelOneGrantResult.Granted -> hardshipFiveMinutesGranted
    HardshipLevelOneGrantResult.WaitingStarted -> hardshipReflectionStarted
    HardshipLevelOneGrantResult.Waiting -> hardshipReflectionWaiting
    HardshipLevelOneGrantResult.DailyLimitReached -> hardshipAllowanceLimitReached
    HardshipLevelOneGrantResult.NotAvailable -> hardshipUnavailable
}

private fun BlockedScreenStrings.emergencyPassResult(result: EmergencyPassUseResult): String = when (result) {
    EmergencyPassUseResult.Used -> emergencyPassUsed
    EmergencyPassUseResult.InvalidPin -> invalidParentPin
    EmergencyPassUseResult.CooldownActive -> emergencyPassCooldown
    EmergencyPassUseResult.NotAvailable -> hardshipUnavailable
}

private fun BlockedScreenStrings.hardshipLevelTwoResult(result: HardshipLevelTwoUnlockResult): String = when (result) {
    HardshipLevelTwoUnlockResult.Unlocked -> hardshipLevel2Unlocked
    HardshipLevelTwoUnlockResult.WaitingStarted -> hardshipLevel2Started
    HardshipLevelTwoUnlockResult.Waiting -> hardshipLevel2Waiting
    HardshipLevelTwoUnlockResult.InvalidPin -> invalidParentPin
    HardshipLevelTwoUnlockResult.NotAvailable -> hardshipUnavailable
}

private fun blockedScreenStrings(language: AppLanguage): BlockedScreenStrings {
    return when (language) {
        AppLanguage.Korean -> BlockedScreenStrings(
            preview = "\uBBF8\uB9AC\uBCF4\uAE30",
            blocked = "\uCC28\uB2E8\uB428",
            dailyTitle = "\uC624\uB298 \uC0AC\uC6A9 \uC2DC\uAC04\uC774 \uC885\uB8CC\uB418\uC5C8\uC2B5\uB2C8\uB2E4",
            appTitle = "\uC774 \uC571\uC758 \uC0AC\uC6A9 \uC2DC\uAC04\uC774 \uC885\uB8CC\uB418\uC5C8\uC2B5\uB2C8\uB2E4",
            blockTitle = { reason ->
                when (reason) {
                    "schedule block active" -> "\uC2A4\uCF00\uC904\uB85C \uCC28\uB2E8\uB418\uC5C8\uC2B5\uB2C8\uB2E4"
                    "allow-only mode active" -> "\uD5C8\uC6A9\uB41C \uC571\uC774 \uC544\uB2D9\uB2C8\uB2E4"
                    "group limit exceeded" -> "\uADF8\uB8F9 \uC0AC\uC6A9 \uC2DC\uAC04\uC774 \uC885\uB8CC\uB418\uC5C8\uC2B5\uB2C8\uB2E4"
                    "app limit exceeded" -> "\uC774 \uC571\uC758 \uC0AC\uC6A9 \uC2DC\uAC04\uC774 \uC885\uB8CC\uB418\uC5C8\uC2B5\uB2C8\uB2E4"
                    "parent immediate block active" -> "부모가 지금 차단 중입니다"
                    else -> "\uC774 \uC571\uC740 \uD604\uC7AC \uCC28\uB2E8\uB418\uC5C8\uC2B5\uB2C8\uB2E4"
                }
            },
            blockReason = { reason ->
                when (reason) {
                    "total limit exceeded" -> "\uC77C\uC77C \uC81C\uD55C \uCD08\uACFC"
                    "schedule block active" -> "\uC2A4\uCF00\uC904 \uCC28\uB2E8"
                    "allow-only mode active" -> "\uD5C8\uC6A9\uB41C \uC571\uB9CC \uC0AC\uC6A9 \uAC00\uB2A5"
                    "group limit exceeded" -> "\uADF8\uB8F9 \uC81C\uD55C \uCD08\uACFC"
                    "app limit exceeded" -> "\uC571 \uC81C\uD55C \uCD08\uACFC"
                    "parent immediate block active" -> "부모 즉시 차단"
                    else -> reason
                }
            },
            usedReason = { usedMinutes, limitMinutes, reason ->
                val limitText = limitMinutes?.let { minutes -> " / ${formatLimitMinutesLabel(minutes)}" }.orEmpty()
                "${formatLimitMinutesLabel(usedMinutes)}$limitText \uC0AC\uC6A9 - $reason"
            },
            dailyReason = { usedMinutes, limitMinutes ->
                val limitText = limitMinutes?.let { minutes -> " / ${formatLimitMinutesLabel(minutes)}" }.orEmpty()
                "\uC804\uCCB4 \uC0AC\uC6A9 ${formatLimitMinutesLabel(usedMinutes)}$limitText"
            },
            remaining = "\uB0A8\uC740 \uC2DC\uAC04: ${formatLimitMinutesLabel(0)}",
            parentControls = "\uBCF4\uD638\uC790 \uC2DC\uAC04 \uCD94\uAC00",
            parentPin = "\uAD00\uB9AC PIN",
            extraTime = "\uCD94\uAC00 \uC2DC\uAC04",
            extraTimeHint = "1m, 30m, 1h",
            addTime = "\uC2DC\uAC04 \uCD94\uAC00",
            unlockToday = "\uC624\uB298\uB9CC \uD574\uC81C",
            invalidParentPin = "\uAD00\uB9AC PIN\uC774 \uC62C\uBC14\uB974\uC9C0 \uC54A\uC2B5\uB2C8\uB2E4",
            safeRecovery = "안전 복구",
            openManager = "폰 쉼 열기",
            close = "\uB2EB\uAE30",
            back = "\uD648\uC73C\uB85C",
            safetyNote = "오작동 시 관리 PIN으로 안전 복구할 수 있습니다. 고행 3단계에서는 Emergency Pass만 사용할 수 있습니다.",
            hardshipLevelPrefix = "단계",
            hardshipLevel1Description = "2분 숙고 후 5분 임시 허용을 최대 2회 사용할 수 있습니다. 고행 종료는 2분 숙고 후 관리 PIN이 필요합니다.",
            hardshipLevel2Description = "고행 종료는 30분 숙고 후 관리 PIN이 필요합니다.",
            hardshipLevel3Description = "관리 PIN만으로 일반 해제할 수 없습니다. Emergency Pass는 현재 앱에만 적용되며 모든 3단계에서 7일에 한 번 사용할 수 있습니다.",
            hardshipReflectionAction = "2분 숙고 / 5분 허용",
            hardshipReflectionStarted = "2분 숙고를 시작했습니다. 시간이 지난 뒤 다시 눌러 주세요.",
            hardshipReflectionWaiting = "아직 숙고 시간이 끝나지 않았습니다.",
            hardshipFiveMinutesGranted = "5분 임시 허용이 적용되었습니다.",
            hardshipAllowanceExpired = "5분 임시 허용 시간이 끝나 다시 차단되었습니다.",
            hardshipAllowanceLimitReached = "오늘 사용할 수 있는 임시 허용을 모두 사용했습니다.",
            hardshipUnavailable = "현재 이 기능을 사용할 수 없습니다.",
            emergencyPass = "Emergency Pass 사용",
            emergencyPassUsed = "Emergency Pass를 사용했습니다.",
            emergencyPassCooldown = "아직 Emergency Pass를 사용할 수 없습니다. 표시된 다음 사용 가능 시각을 확인하세요.",
            requestParent = "부모에게 요청",
            parentRequestSent = "요청을 보냈습니다.",
            parentRequestRetrying = "재시도 중입니다.",
            parentRequestNotPaired = "부모 기기가 연결되어 있지 않습니다.",
            parentRequestFailed = "요청을 처리하지 못했습니다. 관리 앱에서 연결 상태를 확인하세요.",
            hardshipLevel2Action = "30분 숙고 / 관리 PIN으로 종료",
            hardshipLevel2Started = "30분 숙고를 시작했습니다. 시간이 지난 뒤 관리 PIN을 입력하세요.",
            hardshipLevel2Waiting = "아직 30분 숙고 시간이 끝나지 않았습니다.",
            hardshipLevel2Unlocked = "고행 2단계 정책을 오늘만 종료했습니다.",
        )

        AppLanguage.English -> BlockedScreenStrings(
            preview = "PREVIEW",
            blocked = "BLOCKED",
            dailyTitle = "Today's screen time is over",
            appTitle = "This app's time is over",
            blockTitle = { reason ->
                when (reason) {
                    "schedule block active" -> "Blocked by schedule"
                    "allow-only mode active" -> "This app is not allowed now"
                    "group limit exceeded" -> "Group time is over"
                    "app limit exceeded" -> "This app's time is over"
                    "parent immediate block active" -> "Blocked by parent"
                    else -> "This app is blocked"
                }
            },
            blockReason = { reason ->
                when (reason) {
                    "total limit exceeded" -> "daily limit exceeded"
                    "schedule block active" -> "schedule block"
                    "allow-only mode active" -> "allow-only mode"
                    "group limit exceeded" -> "group limit exceeded"
                    "app limit exceeded" -> "app limit exceeded"
                    "parent immediate block active" -> "parent block"
                    else -> reason
                }
            },
            usedReason = { usedMinutes, limitMinutes, reason ->
                val limitText = limitMinutes?.let { minutes -> " / ${formatLimitMinutesLabel(minutes)}" }.orEmpty()
                "${formatLimitMinutesLabel(usedMinutes)}$limitText used - $reason"
            },
            dailyReason = { usedMinutes, limitMinutes ->
                val limitText = limitMinutes?.let { minutes -> " / ${formatLimitMinutesLabel(minutes)}" }.orEmpty()
                "Total usage ${formatLimitMinutesLabel(usedMinutes)}$limitText"
            },
            remaining = "Remaining time: ${formatLimitMinutesLabel(0)}",
            parentControls = "Parent Time Override",
            parentPin = "Admin PIN",
            extraTime = "Extra time",
            extraTimeHint = "1m, 30m, 1h",
            addTime = "Add time",
            unlockToday = "Unlock for today",
            invalidParentPin = "Invalid admin PIN",
            safeRecovery = "Safe Recovery",
            openManager = "Open ScreenRest",
            close = "Close",
            back = "Home",
            safetyNote = "Use the Admin PIN for Safe Recovery after a malfunction. Level 3 only permits Emergency Pass.",
            hardshipLevelPrefix = "Level",
            hardshipLevel1Description = "After 2 minutes of reflection, you can use a 5-minute allowance up to twice. Ending hardship also requires 2 minutes and the Admin PIN.",
            hardshipLevel2Description = "Ending hardship requires 30 minutes of reflection and the Admin PIN.",
            hardshipLevel3Description = "The Admin PIN cannot normally unlock level 3. Emergency Pass applies only to this app and is shared across all level-3 policies once every 7 days.",
            hardshipReflectionAction = "Reflect 2 min / allow 5 min",
            hardshipReflectionStarted = "The 2-minute reflection started. Try again when it ends.",
            hardshipReflectionWaiting = "The reflection period has not ended yet.",
            hardshipFiveMinutesGranted = "A 5-minute allowance was granted.",
            hardshipAllowanceExpired = "The 5-minute allowance ended, so the app is blocked again.",
            hardshipAllowanceLimitReached = "Today's temporary allowances have all been used.",
            hardshipUnavailable = "This action is not available now.",
            emergencyPass = "Use Emergency Pass",
            emergencyPassUsed = "Emergency Pass used.",
            emergencyPassCooldown = "Emergency Pass is not available yet. Check the displayed next available time.",
            requestParent = "Ask parent",
            parentRequestSent = "Request sent to parent.",
            parentRequestRetrying = "Retrying.",
            parentRequestNotPaired = "A parent device is not linked.",
            parentRequestFailed = "Could not send the request. Check the connection.",
            hardshipLevel2Action = "Reflect 30 min / end with Admin PIN",
            hardshipLevel2Started = "The 30-minute reflection started. Enter the Admin PIN when it ends.",
            hardshipLevel2Waiting = "The 30-minute reflection has not ended yet.",
            hardshipLevel2Unlocked = "The level 2 policy was ended for today.",
        )
    }
}

private fun String.toHardshipPolicyType(): HardshipPolicyType? {
    return when (this) {
        "total limit exceeded" -> HardshipPolicyType.DailyLimit
        "group limit exceeded" -> HardshipPolicyType.AppGroups
        "app limit exceeded" -> HardshipPolicyType.AppLimits
        "schedule block active" -> HardshipPolicyType.Schedule
        "allow-only mode active" -> HardshipPolicyType.AllowOnly
        else -> null
    }
}

private fun HardshipPolicyType.toRemoteRequestBlockReason(): RemoteRequestBlockReason {
    return when (this) {
        HardshipPolicyType.DailyLimit -> RemoteRequestBlockReason.DailyLimit
        HardshipPolicyType.AppGroups -> RemoteRequestBlockReason.AppGroupLimit
        HardshipPolicyType.AppLimits -> RemoteRequestBlockReason.AppLimit
        HardshipPolicyType.Schedule -> RemoteRequestBlockReason.ScheduleBlock
        HardshipPolicyType.AllowOnly -> RemoteRequestBlockReason.AllowOnlyMode
    }
}
