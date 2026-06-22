package com.example.screentimemanager.blocking

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.screentimemanager.AppIcon
import com.example.screentimemanager.SmoothMinuteSlider
import com.example.screentimemanager.data.AppLanguage
import com.example.screentimemanager.data.SettingsRepository
import com.example.screentimemanager.data.settingsDataStore
import com.example.screentimemanager.formatLimitMinutesLabel
import com.example.screentimemanager.ui.theme.AppOver
import com.example.screentimemanager.ui.theme.AppSafe
import com.example.screentimemanager.ui.theme.ScreenTimeManagerTheme
import kotlinx.coroutines.launch

class BlockedActivity : ComponentActivity() {
    private val repository by lazy { SettingsRepository(applicationContext.settingsDataStore) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appName = intent.getStringExtra(EXTRA_APP_NAME).orEmpty().ifBlank { "App" }
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()
        val reason = intent.getStringExtra(EXTRA_REASON).orEmpty().ifBlank { "Limit exceeded" }
        val usedMinutes = intent.getIntExtra(EXTRA_USED_MINUTES, 0).coerceAtLeast(0)
        val limitMinutes = intent.getIntExtra(EXTRA_LIMIT_MINUTES, -1).takeIf { minutes -> minutes >= 0 }
        val showAppDetails = intent.getBooleanExtra(EXTRA_SHOW_APP_DETAILS, true)
        val previewOnly = intent.getBooleanExtra(EXTRA_PREVIEW_ONLY, true)

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
                BlockedScreen(
                    appName = appName,
                    packageName = packageName,
                    reason = reason,
                    usedMinutes = usedMinutes,
                    limitMinutes = limitMinutes,
                    showAppDetails = showAppDetails,
                    previewOnly = previewOnly,
                    text = blockedScreenStrings(language),
                    onEmergencyUnlock = { pin, onResult ->
                        lifecycleScope.launch {
                            val unlocked = repository.emergencyUnlock(pin)
                            onResult(unlocked)
                            if (unlocked) {
                                finish()
                            }
                        }
                    },
                    onKillSwitch = {
                        lifecycleScope.launch {
                            repository.activateKillSwitch()
                            finish()
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
                )
            }
        }
    }

    private fun sendHomeAndFinish() {
        startActivity(
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
        )
        finish()
    }

    companion object {
        private const val EXTRA_APP_NAME = "extra_app_name"
        private const val EXTRA_PACKAGE_NAME = "extra_package_name"
        private const val EXTRA_REASON = "extra_reason"
        private const val EXTRA_USED_MINUTES = "extra_used_minutes"
        private const val EXTRA_LIMIT_MINUTES = "extra_limit_minutes"
        private const val EXTRA_SHOW_APP_DETAILS = "extra_show_app_details"
        private const val EXTRA_PREVIEW_ONLY = "extra_preview_only"

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
        ): Intent {
            return Intent(context, BlockedActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_APP_NAME, appName)
                putExtra(EXTRA_PACKAGE_NAME, packageName)
                putExtra(EXTRA_REASON, reason)
                putExtra(EXTRA_USED_MINUTES, usedMinutes)
                if (limitMinutes != null) {
                    putExtra(EXTRA_LIMIT_MINUTES, limitMinutes)
                }
                putExtra(EXTRA_SHOW_APP_DETAILS, showAppDetails)
                putExtra(EXTRA_PREVIEW_ONLY, false)
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
    text: BlockedScreenStrings,
    onEmergencyUnlock: (String, (Boolean) -> Unit) -> Unit,
    onKillSwitch: () -> Unit,
    onParentAddTime: (String, Int, (Boolean) -> Unit) -> Unit,
    onParentUnlockToday: (String, (Boolean) -> Unit) -> Unit,
    onClosePreview: () -> Unit,
) {
    var parentPin by remember { mutableStateOf("") }
    var extraMinutes by remember { mutableStateOf(30) }
    var parentFailed by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var unlockFailed by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (previewOnly) MaterialTheme.colorScheme.primary else AppOver,
                ) {
                    Text(
                        text = if (previewOnly) text.preview else text.blocked,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }

                Text(
                    text = if (showAppDetails) text.appTitle else text.dailyTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                if (showAppDetails) {
                    AppIcon(
                        packageName = packageName,
                        contentDescription = appName,
                        size = 64.dp,
                    )
                    Text(
                        text = appName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = text.usedReason(usedMinutes, limitMinutes, reason),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Text(
                        text = text.dailyReason(usedMinutes, limitMinutes),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                Text(
                    text = text.remaining,
                    style = MaterialTheme.typography.titleMedium,
                    color = AppOver,
                    fontWeight = FontWeight.Bold,
                )

                if (!previewOnly) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = text.parentControls,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        OutlinedTextField(
                            value = parentPin,
                            onValueChange = {
                                parentPin = it
                                parentFailed = false
                            },
                            label = { Text(text.parentPin) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        BlockedExtraTimeControl(
                            valueMinutes = extraMinutes,
                            text = text,
                            onValueMinutesChange = { minutes ->
                                extraMinutes = minutes
                                parentFailed = false
                            },
                        )
                        OutlinedButton(
                            onClick = {
                                if (extraMinutes > 0) {
                                    onParentAddTime(parentPin, extraMinutes) { granted ->
                                        parentFailed = !granted
                                    }
                                }
                            },
                            enabled = parentPin.isNotBlank() && extraMinutes > 0,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Text(text.addTime)
                        }
                        Button(
                            onClick = {
                                onParentUnlockToday(parentPin) { granted ->
                                    parentFailed = !granted
                                }
                            },
                            enabled = parentPin.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Text(text.unlockToday)
                        }
                        if (parentFailed) {
                            Text(
                                text = text.invalidParentPin,
                                color = AppOver,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        pin = it
                        unlockFailed = false
                    },
                    label = { Text(text.emergencyPin) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (unlockFailed) {
                    Text(
                        text = text.invalidPin,
                        color = AppOver,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                Button(
                    onClick = {
                        onEmergencyUnlock(pin) { unlocked ->
                            unlockFailed = !unlocked
                        }
                    },
                    enabled = pin.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(text.emergencyUnlock)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onKillSwitch,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Text(text.killSwitch)
                    }
                    OutlinedButton(
                        onClick = onClosePreview,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Text(if (previewOnly) text.close else text.back)
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = text.safetyNote,
                    style = MaterialTheme.typography.labelLarge,
                    color = AppSafe,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun BlockedExtraTimeControl(
    valueMinutes: Int,
    text: BlockedScreenStrings,
    onValueMinutesChange: (Int) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = text.extraTime,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                ) {
                    Text(
                        text = formatLimitMinutesLabel(valueMinutes),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                }
            }
            SmoothMinuteSlider(
                valueMinutes = valueMinutes,
                onValueMinutesChange = onValueMinutesChange,
                minMinutes = 5,
                maxMinutes = 240,
                stepMinutes = 5,
            )
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                listOf(5, 60, 120, 240).forEach { minutes ->
                    Text(
                        text = formatLimitMinutesLabel(minutes),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private data class BlockedScreenStrings(
    val preview: String,
    val blocked: String,
    val dailyTitle: String,
    val appTitle: String,
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
    val emergencyPin: String,
    val invalidPin: String,
    val emergencyUnlock: String,
    val killSwitch: String,
    val close: String,
    val back: String,
    val safetyNote: String,
)

private fun blockedScreenStrings(language: AppLanguage): BlockedScreenStrings {
    return when (language) {
        AppLanguage.Korean -> BlockedScreenStrings(
            preview = "\uBBF8\uB9AC\uBCF4\uAE30",
            blocked = "\uCC28\uB2E8\uB428",
            dailyTitle = "\uC624\uB298 \uC0AC\uC6A9 \uC2DC\uAC04\uC774 \uC885\uB8CC\uB418\uC5C8\uC2B5\uB2C8\uB2E4",
            appTitle = "\uC774 \uC571\uC758 \uC0AC\uC6A9 \uC2DC\uAC04\uC774 \uC885\uB8CC\uB418\uC5C8\uC2B5\uB2C8\uB2E4",
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
            extraTimeHint = "30m, 1h, 1h 30m",
            addTime = "\uC2DC\uAC04 \uCD94\uAC00",
            unlockToday = "\uC624\uB298\uB9CC \uD574\uC81C",
            invalidParentPin = "\uAD00\uB9AC PIN\uC774 \uC62C\uBC14\uB974\uC9C0 \uC54A\uC2B5\uB2C8\uB2E4",
            emergencyPin = "\uAE34\uAE09 PIN",
            invalidPin = "PIN\uC774 \uC62C\uBC14\uB974\uC9C0 \uC54A\uC2B5\uB2C8\uB2E4",
            emergencyUnlock = "\uAE34\uAE09 \uD574\uC81C",
            killSwitch = "\uD0AC \uC2A4\uC704\uCE58",
            close = "\uB2EB\uAE30",
            back = "\uD648\uC73C\uB85C",
            safetyNote = "Safe Mode\uC640 Kill Switch\uB294 \uD56D\uC0C1 \uC0AC\uC6A9\uD560 \uC218 \uC788\uC2B5\uB2C8\uB2E4.",
        )

        AppLanguage.English -> BlockedScreenStrings(
            preview = "PREVIEW",
            blocked = "BLOCKED",
            dailyTitle = "Today's screen time is over",
            appTitle = "This app's time is over",
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
            extraTimeHint = "30m, 1h, 1h 30m",
            addTime = "Add time",
            unlockToday = "Unlock for today",
            invalidParentPin = "Invalid admin PIN",
            emergencyPin = "Emergency PIN",
            invalidPin = "Invalid PIN",
            emergencyUnlock = "Emergency Unlock",
            killSwitch = "Kill Switch",
            close = "Close",
            back = "Home",
            safetyNote = "Safe Mode and Kill Switch always remain available.",
        )
    }
}
