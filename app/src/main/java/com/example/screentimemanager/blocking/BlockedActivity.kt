package com.example.screentimemanager.blocking

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import com.example.screentimemanager.data.AppLanguage
import com.example.screentimemanager.data.SettingsRepository
import com.example.screentimemanager.data.settingsDataStore
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
                    onClosePreview = { finish() },
                )
            }
        }
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
    onClosePreview: () -> Unit,
) {
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
                modifier = Modifier.padding(24.dp),
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

private data class BlockedScreenStrings(
    val preview: String,
    val blocked: String,
    val dailyTitle: String,
    val appTitle: String,
    val usedReason: (Int, Int?, String) -> String,
    val dailyReason: (Int, Int?) -> String,
    val remaining: String,
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
                val limitText = limitMinutes?.let { minutes -> " / ${minutes}\uBD84" }.orEmpty()
                "${usedMinutes}\uBD84$limitText \uC0AC\uC6A9 - $reason"
            },
            dailyReason = { usedMinutes, limitMinutes ->
                val limitText = limitMinutes?.let { minutes -> " / ${minutes}\uBD84" }.orEmpty()
                "\uC804\uCCB4 \uC0AC\uC6A9 ${usedMinutes}\uBD84$limitText"
            },
            remaining = "\uB0A8\uC740 \uC2DC\uAC04: 0\uBD84",
            emergencyPin = "\uAE34\uAE09 PIN",
            invalidPin = "PIN\uC774 \uC62C\uBC14\uB974\uC9C0 \uC54A\uC2B5\uB2C8\uB2E4",
            emergencyUnlock = "\uAE34\uAE09 \uD574\uC81C",
            killSwitch = "\uD0AC \uC2A4\uC704\uCE58",
            close = "\uB2EB\uAE30",
            back = "\uB4A4\uB85C",
            safetyNote = "Safe Mode\uC640 Kill Switch\uB294 \uD56D\uC0C1 \uC0AC\uC6A9\uD560 \uC218 \uC788\uC2B5\uB2C8\uB2E4.",
        )

        AppLanguage.English -> BlockedScreenStrings(
            preview = "PREVIEW",
            blocked = "BLOCKED",
            dailyTitle = "Today's screen time is over",
            appTitle = "This app's time is over",
            usedReason = { usedMinutes, limitMinutes, reason ->
                val limitText = limitMinutes?.let { minutes -> " / ${minutes} min" }.orEmpty()
                "$usedMinutes min$limitText used - $reason"
            },
            dailyReason = { usedMinutes, limitMinutes ->
                val limitText = limitMinutes?.let { minutes -> " / ${minutes} min" }.orEmpty()
                "Total usage $usedMinutes min$limitText"
            },
            remaining = "Remaining time: 0 min",
            emergencyPin = "Emergency PIN",
            invalidPin = "Invalid PIN",
            emergencyUnlock = "Emergency Unlock",
            killSwitch = "Kill Switch",
            close = "Close",
            back = "Back",
            safetyNote = "Safe Mode and Kill Switch always remain available.",
        )
    }
}
