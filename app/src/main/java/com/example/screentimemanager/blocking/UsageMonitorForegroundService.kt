package com.example.screentimemanager.blocking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.screentimemanager.MainActivity
import com.example.screentimemanager.R
import com.example.screentimemanager.data.EventLogType
import com.example.screentimemanager.data.SettingsRepository
import com.example.screentimemanager.data.TemporaryUnlockState
import com.example.screentimemanager.data.UsagePolicySettings
import com.example.screentimemanager.data.normalizedAppGroups
import com.example.screentimemanager.data.settingsDataStore
import com.example.screentimemanager.formatLimitMinutesLabel
import com.example.screentimemanager.safety.BlockDecision
import com.example.screentimemanager.safety.BlockDecisionResult
import com.example.screentimemanager.safety.SafetyGate
import com.example.screentimemanager.ui.safety.appLimitMap
import com.example.screentimemanager.ui.safety.todayLimitMinutes
import com.example.screentimemanager.usage.AppVisibility
import com.example.screentimemanager.usage.UsageStatsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UsageMonitorForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val repository by lazy { SettingsRepository(applicationContext.settingsDataStore) }
    private val usageRepository by lazy { UsageStatsRepository(applicationContext) }
    private var monitorJob: Job? = null
    private var blockingOverlayView: View? = null
    private var blockingOverlayPackageName: String? = null
    private var lastHomeSentAt: Long = 0L
    private var lastLoggedBlockPackageName: String? = null
    private var lastLoggedBlockAt: Long = 0L
    private var activeForegroundPackageName: String? = null
    private var activeForegroundStartedAtElapsed: Long = 0L
    private var activeForegroundBaselineUsageMillis: Long? = null
    private var managerOpenGraceUntilElapsed: Long = 0L

    override fun onCreate() {
        super.onCreate()
        ensureMonitorChannel()
        startForeground(
            MONITOR_NOTIFICATION_ID,
            buildMonitorNotification(
                title = "Screen Time Manager",
                text = "Usage monitor starting",
            ),
        )
        startMonitorLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            removeBlockingOverlay()
            stopSelf()
            return START_NOT_STICKY
        }
        if (monitorJob == null) {
            startMonitorLoop()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        removeBlockingOverlay()
        monitorJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startMonitorLoop() {
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            while (isActive) {
                runCatching {
                    evaluateCurrentForegroundApp()
                }.onFailure {
                    repository.addEvent(EventLogType.Safety, "Usage monitor recovered from evaluation error")
                }
                delay(MONITOR_INTERVAL_MILLIS)
            }
        }
    }

    private suspend fun evaluateCurrentForegroundApp() {
        val safeModeEnabled = repository.safeModeEnabled.first()
        val policyEnforcementEnabled = repository.policyEnforcementEnabled.first()
        if (safeModeEnabled || !policyEnforcementEnabled) {
            removeBlockingOverlay()
            updateMonitorNotification("Screen Time Manager", "Policy enforcement is off")
            stopSelf()
            return
        }

        if (!usageRepository.hasUsageAccess()) {
            removeBlockingOverlay()
            updateMonitorNotification("Screen Time Manager", "Usage access is required")
            return
        }

        if (SystemClock.elapsedRealtime() < managerOpenGraceUntilElapsed) {
            updateMonitorNotification("Screen Time Manager", "Block controls open")
            return
        }

        blockingOverlayPackageName?.let { packageName ->
            val existingDecision = evaluatePackage(packageName)
            if (!existingDecision.result.decision.isWouldBlock()) {
                removeBlockingOverlay()
            }
        }

        val packageName = usageRepository.getCurrentForegroundPackageName().orEmpty()
        if (packageName.isBlank() || packageName == applicationContext.packageName || AppVisibility.isHiddenPackage(packageName)) {
            clearActiveForegroundSession()
            updateMonitorNotification("Screen Time Manager", "Monitoring active apps")
            return
        }

        updateActiveForegroundSession(packageName)
        val decision = evaluatePackage(packageName)
        val notificationText = decision.toNotificationText()
        updateMonitorNotification(decision.result.appName, notificationText)

        if (decision.result.decision.isWouldBlock()) {
            enforceBlock(decision)
        }
    }

    private suspend fun evaluatePackage(packageName: String): MonitorDecision {
        val settings = repository.usagePolicySettings.first()
        val temporaryUnlockState = repository.temporaryUnlockState.first().forToday()
        val allowedPackages = repository.allowedAppPackages.first()
        val usageByPackage = usageRepository.getTodayUsageMillisByPackage()
        val appName = usageRepository.getAppLabel(packageName)
        val rawAppUsedMillis = usageByPackage[packageName] ?: 0L
        val appUsedMillis = adjustedActiveForegroundUsageMillis(packageName, rawAppUsedMillis)
        val activeSessionDeltaMillis = (appUsedMillis - rawAppUsedMillis).coerceAtLeast(0L)
        val totalUsedMillis = usageByPackage.values.sum() + activeSessionDeltaMillis
        val appLimitMinutes = settings.appLimitMap()[packageName] ?: 0
        val totalLimitMinutes = settings.todayLimitMinutes()
        val packageAllowance = temporaryUnlockState.packageAllowances[packageName]
        val packageExtraMinutes = packageAllowance?.extraMinutes ?: 0
        val packageUnlockedForToday = packageAllowance?.unlockedForToday == true
        val targetGroup = settings.normalizedAppGroups()
            .firstOrNull { group -> packageName in group.packageNames && group.budgetMinutes > 0 }
        val targetGroupUsedMillis = targetGroup
            ?.packageNames
            ?.sumOf { groupPackageName ->
                val rawGroupPackageUsageMillis = usageByPackage[groupPackageName] ?: 0L
                if (groupPackageName == packageName) {
                    adjustedActiveForegroundUsageMillis(groupPackageName, rawGroupPackageUsageMillis)
                } else {
                    rawGroupPackageUsageMillis
                }
            }
        val effectiveTotalLimitMinutes = totalLimitMinutes + temporaryUnlockState.totalExtraMinutes
        val effectiveAppLimitMinutes = appLimitMinutes + packageExtraMinutes
        val effectiveGroupLimitMinutes = targetGroup?.budgetMinutes?.plus(packageExtraMinutes)

        val safetyGateResult = SafetyGate.evaluateBlocking(
            safeModeEnabled = false,
            policyEnforcementEnabled = true,
            targetPackageName = packageName,
            userAllowedPackages = allowedPackages,
        )
        val decision = when {
            !safetyGateResult.canEvaluateBlocking -> BlockDecision.AllowedWhitelist
            !temporaryUnlockState.totalUnlockedForToday &&
                totalLimitMinutes > 0 &&
                totalUsedMillis >= effectiveTotalLimitMinutes.toMillisLimit() -> {
                BlockDecision.WouldBlockTotalLimit
            }
            !packageUnlockedForToday &&
                effectiveGroupLimitMinutes != null &&
                targetGroupUsedMillis != null &&
                targetGroupUsedMillis >= effectiveGroupLimitMinutes.toMillisLimit() -> {
                BlockDecision.WouldBlockGroupLimit
            }
            !packageUnlockedForToday &&
                appLimitMinutes > 0 &&
                appUsedMillis >= effectiveAppLimitMinutes.toMillisLimit() -> {
                BlockDecision.WouldBlockAppLimit
            }
            appLimitMinutes > 0 || totalLimitMinutes > 0 || targetGroup != null -> BlockDecision.AllowedUnderLimit
            else -> BlockDecision.AllowedNoLimit
        }

        val usedMillis = when (decision) {
            BlockDecision.WouldBlockTotalLimit -> totalUsedMillis
            BlockDecision.WouldBlockGroupLimit -> targetGroupUsedMillis ?: appUsedMillis
            else -> appUsedMillis
        }
        val limitMinutes = when (decision) {
            BlockDecision.WouldBlockTotalLimit -> effectiveTotalLimitMinutes
            BlockDecision.WouldBlockGroupLimit -> effectiveGroupLimitMinutes
            BlockDecision.WouldBlockAppLimit -> effectiveAppLimitMinutes
            else -> appLimitMinutes.takeIf { minutes -> minutes > 0 }
        }

        return MonitorDecision(
            result = BlockDecisionResult(
                packageName = packageName,
                appName = appName,
                usedMinutes = usedMillis.toDisplayMinutesFloor(),
                limitMinutes = limitMinutes,
                decision = decision,
            ),
            usedMillis = usedMillis,
            limitMillis = limitMinutes?.toMillisLimit(),
        )
    }

    private fun updateActiveForegroundSession(packageName: String) {
        if (activeForegroundPackageName != packageName) {
            activeForegroundPackageName = packageName
            activeForegroundStartedAtElapsed = SystemClock.elapsedRealtime()
            activeForegroundBaselineUsageMillis = null
        }
    }

    private fun clearActiveForegroundSession() {
        activeForegroundPackageName = null
        activeForegroundStartedAtElapsed = 0L
        activeForegroundBaselineUsageMillis = null
    }

    private fun adjustedActiveForegroundUsageMillis(packageName: String, rawUsageMillis: Long): Long {
        if (activeForegroundPackageName != packageName || activeForegroundStartedAtElapsed <= 0L) {
            return rawUsageMillis
        }
        if (activeForegroundBaselineUsageMillis == null) {
            activeForegroundBaselineUsageMillis = rawUsageMillis
        }
        val baselineUsageMillis = activeForegroundBaselineUsageMillis ?: rawUsageMillis
        val elapsedMillis = (SystemClock.elapsedRealtime() - activeForegroundStartedAtElapsed).coerceAtLeast(0L)
        return maxOf(rawUsageMillis, baselineUsageMillis + elapsedMillis)
    }

    private suspend fun enforceBlock(decision: MonitorDecision) {
        logBlockIfNeeded(decision)
        pauseActiveMediaPlayback()
        sendHomeIntentThrottled()
        withContext(Dispatchers.Main) {
            showBlockingOverlay(decision)
        }
    }

    private suspend fun logBlockIfNeeded(decision: MonitorDecision) {
        val now = SystemClock.elapsedRealtime()
        val packageName = decision.result.packageName
        if (lastLoggedBlockPackageName == packageName && now - lastLoggedBlockAt < BLOCK_LOG_THROTTLE_MILLIS) {
            return
        }
        lastLoggedBlockPackageName = packageName
        lastLoggedBlockAt = now
        repository.addEvent(
            EventLogType.Exceeded,
            "Foreground monitor blocked ${decision.result.appName}: ${decision.result.decision.toLogReason()}",
        )
    }

    private fun showBlockingOverlay(decision: MonitorDecision) {
        if (blockingOverlayPackageName == decision.result.packageName && blockingOverlayView?.isAttachedToWindow == true) {
            return
        }
        removeBlockingOverlay()

        if (!canDrawBlockingOverlay()) {
            startBlockedActivity(decision)
            return
        }

        val overlayView = createBlockingOverlayView(decision)
        try {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.CENTER
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            getSystemService(WindowManager::class.java).addView(overlayView, params)
            blockingOverlayView = overlayView
            blockingOverlayPackageName = decision.result.packageName
        } catch (_: RuntimeException) {
            blockingOverlayView = null
            blockingOverlayPackageName = null
            startBlockedActivity(decision)
        }
    }

    private fun createBlockingOverlayView(decision: MonitorDecision): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = true
            setPadding(dp(24), dp(24), dp(24), dp(24))
            background = GradientDrawable().apply {
                setColor(Color.rgb(12, 18, 32))
            }
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
            background = GradientDrawable().apply {
                cornerRadius = dp(28).toFloat()
                setColor(Color.WHITE)
            }
        }
        root.addView(
            card,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        card.addView(blockOverlayText("Blocked", 14, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(Color.rgb(229, 91, 74))
            }
        })
        val isDailyLimitBlock = decision.result.decision == BlockDecision.WouldBlockTotalLimit
        card.addView(blockOverlayText(decision.result.toOverlayTitle(), 24, Color.rgb(26, 27, 46), true))
        if (isDailyLimitBlock) {
            card.addView(blockOverlayText("Daily Time", 20, Color.rgb(26, 27, 46), true))
        } else {
            card.addView(blockOverlayText(decision.result.appName, 20, Color.rgb(26, 27, 46), true))
        }
        card.addView(
            blockOverlayText(
                decision.toOverlayUsageText(),
                16,
                Color.rgb(107, 114, 128),
                false,
            ),
        )

        val emergencyInput = EditText(this).apply {
            hint = "Emergency PIN"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setSingleLine(true)
            textSize = 16f
        }
        card.addView(
            emergencyInput,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(18)
            },
        )

        val statusText = blockOverlayText("", 14, Color.rgb(229, 91, 74), true)
        card.addView(statusText)
        card.addView(blockOverlayButton("Emergency Unlock") {
            val pin = emergencyInput.text?.toString().orEmpty()
            serviceScope.launch {
                val unlocked = repository.emergencyUnlock(pin)
                withContext(Dispatchers.Main) {
                    if (unlocked) {
                        removeBlockingOverlay()
                    } else {
                        statusText.text = "Emergency PIN is incorrect"
                    }
                }
            }
        })
        card.addView(blockOverlayButton("Open Manager") {
            openBlockControls(decision)
        })
        card.addView(blockOverlayButton("Kill Switch") {
            serviceScope.launch {
                repository.activateKillSwitch()
                withContext(Dispatchers.Main) {
                    removeBlockingOverlay()
                    stopSelf()
                }
            }
        })
        return root
    }

    private fun removeBlockingOverlay() {
        val overlayView = blockingOverlayView ?: return
        try {
            getSystemService(WindowManager::class.java).removeView(overlayView)
        } catch (_: RuntimeException) {
            // Overlay may already be detached.
        } finally {
            blockingOverlayView = null
            blockingOverlayPackageName = null
        }
    }

    private fun openBlockControls(decision: MonitorDecision) {
        managerOpenGraceUntilElapsed = SystemClock.elapsedRealtime() + MANAGER_OPEN_GRACE_MILLIS
        removeBlockingOverlay()
        startBlockedActivity(decision)
    }

    private fun startBlockedActivity(decision: MonitorDecision) {
        try {
            startActivity(
                BlockedActivity.blockIntent(
                    context = applicationContext,
                    appName = decision.result.appName,
                    packageName = decision.result.packageName,
                    reason = decision.result.decision.toLogReason(),
                    usedMinutes = decision.result.usedMinutes,
                    limitMinutes = decision.result.limitMinutes,
                    showAppDetails = decision.result.decision != BlockDecision.WouldBlockTotalLimit,
                ),
            )
        } catch (_: RuntimeException) {
            serviceScope.launch {
                repository.addEvent(EventLogType.Safety, "Foreground monitor could not open blocked activity")
            }
        }
    }

    private fun sendHomeIntentThrottled() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastHomeSentAt < HOME_THROTTLE_MILLIS) {
            return
        }
        lastHomeSentAt = now
        try {
            startActivity(
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                },
            )
        } catch (_: RuntimeException) {
            serviceScope.launch {
                repository.addEvent(EventLogType.Safety, "Foreground monitor home intent failed")
            }
        }
    }

    private fun pauseActiveMediaPlayback() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                ?: return
            val downEvent = KeyEvent(
                SystemClock.uptimeMillis(),
                SystemClock.uptimeMillis(),
                KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_MEDIA_PAUSE,
                0,
            )
            val upEvent = KeyEvent.changeAction(downEvent, KeyEvent.ACTION_UP)
            audioManager.dispatchMediaKeyEvent(downEvent)
            audioManager.dispatchMediaKeyEvent(upEvent)
        } catch (_: RuntimeException) {
            // Blocking must continue even if media ignores pause.
        }
    }

    private fun openMainActivity() {
        try {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
            )
        } catch (_: RuntimeException) {
            // Overlay remains available even if opening the manager fails.
        }
    }

    private fun updateMonitorNotification(title: String, text: String) {
        val notification = buildMonitorNotification(title = title, text = text)
        runCatching {
            NotificationManagerCompat.from(this).notify(MONITOR_NOTIFICATION_ID, notification)
        }
    }

    private fun buildMonitorNotification(title: String, text: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, MONITOR_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .build()
    }

    private fun ensureMonitorChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val channel = NotificationChannel(
            MONITOR_CHANNEL_ID,
            "Usage Monitor",
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun canDrawBlockingOverlay(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
    }

    private fun MonitorDecision.toNotificationText(): String {
        val limitText = limitMillis?.let { limit -> " / ${formatSeconds(limit)}" }.orEmpty()
        return "${formatSeconds(usedMillis)}$limitText"
    }

    private fun MonitorDecision.toOverlayUsageText(): String {
        val limitText = result.limitMinutes
            ?.let { limitMinutes -> " / ${formatLimitMinutesLabel(limitMinutes)}" }
            .orEmpty()
        return "${formatLimitMinutesLabel(result.usedMinutes)}$limitText"
    }

    private fun BlockDecisionResult.toOverlayTitle(): String {
        return when (decision) {
            BlockDecision.WouldBlockTotalLimit -> "Daily time is over"
            BlockDecision.WouldBlockGroupLimit -> "Group time is over"
            BlockDecision.WouldBlockAppLimit -> "App time is over"
            else -> "Time is over"
        }
    }

    private fun BlockDecision.isWouldBlock(): Boolean {
        return this == BlockDecision.WouldBlockTotalLimit ||
            this == BlockDecision.WouldBlockGroupLimit ||
            this == BlockDecision.WouldBlockAppLimit
    }

    private fun BlockDecision.toLogReason(): String {
        return when (this) {
            BlockDecision.AllowedSafeMode -> "safe mode"
            BlockDecision.AllowedPolicyDisabled -> "policy disabled"
            BlockDecision.AllowedWhitelist -> "whitelist"
            BlockDecision.AllowedNoLimit -> "no limit"
            BlockDecision.AllowedUnderLimit -> "under limit"
            BlockDecision.WouldBlockTotalLimit -> "total limit exceeded"
            BlockDecision.WouldBlockGroupLimit -> "group limit exceeded"
            BlockDecision.WouldBlockAppLimit -> "app limit exceeded"
        }
    }

    private fun blockOverlayText(text: String, sp: Int, color: Int, bold: Boolean): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = sp.toFloat()
            setTextColor(color)
            gravity = Gravity.CENTER
            if (bold) {
                typeface = Typeface.DEFAULT_BOLD
            }
            setPadding(0, dp(6), 0, dp(6))
        }
    }

    private fun blockOverlayButton(text: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            textSize = 16f
            isAllCaps = false
            setOnClickListener { onClick() }
        }
    }

    private fun Int.toMillisLimit(): Long {
        return coerceAtLeast(0) * 60_000L
    }

    private fun Long.toDisplayMinutesFloor(): Int {
        return (coerceAtLeast(0L) / 60_000L).toInt()
    }

    private fun formatSeconds(millis: Long): String {
        val totalSeconds = (millis.coerceAtLeast(0L) / 1_000L).toInt()
        val hours = totalSeconds / 3_600
        val minutes = (totalSeconds % 3_600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "%dh %02dm %02ds".format(hours, minutes, seconds)
        } else {
            "%02dm %02ds".format(minutes, seconds)
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private data class MonitorDecision(
        val result: BlockDecisionResult,
        val usedMillis: Long,
        val limitMillis: Long?,
    )

    companion object {
        private const val ACTION_STOP = "com.example.screentimemanager.action.STOP_USAGE_MONITOR"
        private const val MONITOR_CHANNEL_ID = "usage_monitor"
        private const val MONITOR_NOTIFICATION_ID = 4301
        private const val MONITOR_INTERVAL_MILLIS = 1_000L
        private const val HOME_THROTTLE_MILLIS = 2_000L
        private const val BLOCK_LOG_THROTTLE_MILLIS = 30_000L
        private const val MANAGER_OPEN_GRACE_MILLIS = 4_000L

        fun start(context: Context) {
            val intent = Intent(context, UsageMonitorForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, UsageMonitorForegroundService::class.java))
            }
        }
    }
}
