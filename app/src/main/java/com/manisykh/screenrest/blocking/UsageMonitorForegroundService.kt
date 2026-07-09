package com.manisykh.screenrest.blocking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.session.MediaSessionManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.text.InputType
import android.text.method.DigitsKeyListener
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.manisykh.screenrest.MainActivity
import com.manisykh.screenrest.R
import com.manisykh.screenrest.data.EventLogType
import com.manisykh.screenrest.data.SettingsRepository
import com.manisykh.screenrest.data.UsagePolicySettings
import com.manisykh.screenrest.data.activeScheduleAllowedPackages
import com.manisykh.screenrest.data.isScheduleBlockingNow
import com.manisykh.screenrest.data.normalizedAppGroups
import com.manisykh.screenrest.data.settingsDataStore
import com.manisykh.screenrest.formatLimitMinutesLabel
import com.manisykh.screenrest.notification.ScreenTimeNotificationListenerService
import com.manisykh.screenrest.safety.BlockDecision
import com.manisykh.screenrest.safety.BlockDecisionEngine
import com.manisykh.screenrest.safety.BlockDecisionResult
import com.manisykh.screenrest.safety.OverlayPermissionChecker
import com.manisykh.screenrest.safety.SafetyGate
import com.manisykh.screenrest.ui.safety.appLimitMap
import com.manisykh.screenrest.ui.safety.todayLimitMinutes
import com.manisykh.screenrest.usage.AppVisibility
import com.manisykh.screenrest.usage.ForegroundAppTracker
import com.manisykh.screenrest.usage.UsageStatsRepository
import com.manisykh.screenrest.worker.UsageMonitorRecoveryWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.Calendar

class UsageMonitorForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val repository by lazy { SettingsRepository(applicationContext.settingsDataStore) }
    private val usageRepository by lazy { UsageStatsRepository(applicationContext) }
    private var monitorJob: Job? = null
    @Volatile
    private var blockingOverlayView: View? = null
    @Volatile
    private var blockingOverlayPackageName: String? = null
    private var lastHomeSentAt: Long = 0L
    private var lastBlockedActivityStartedAt: Long = 0L
    private var lastBlockedActivityPackageName: String? = null
    private var lastLoggedBlockPackageName: String? = null
    private var lastLoggedBlockAt: Long = 0L
    private var activeForegroundPackageName: String? = null
    private var activeForegroundStartedAtElapsed: Long = 0L
    private var activeForegroundBaselineUsageMillis: Long? = null
    private var activeForegroundDayStartMillis: Long = 0L
    private var foregroundMissingSinceElapsed: Long = 0L
    private var managerOpenGraceUntilElapsed: Long = 0L
    private var homeExitGraceUntilElapsed: Long = 0L
    private var blockExitTransitionPackageName: String? = null
    private var blockExitTransitionUntilElapsed: Long = 0L
    private var blockEnforcementGuardJob: Job? = null
    private var blockEnforcementGuardPackageName: String? = null
    private var blockForegroundEvictionJob: Job? = null
    private var blockForegroundEvictionPackageName: String? = null
    @Volatile
    private var latestRequestedForegroundPackageName: String? = null
    private var lastDebugMonitorSummary: String = ""
    private var lastDebugMonitorAt: Long = 0L
    private var lastMonitorStatusWrittenAt: Long = 0L
    private var diagnosticBlockPackageName: String? = null
    private var diagnosticBlockRetryCount: Int = 0
    private val lastBlockPipelineEventAtByKey = mutableMapOf<String, Long>()
    private var cachedUsageByPackage: Map<String, Long> = emptyMap()
    private var cachedUsageByPackageAtElapsed: Long = 0L
    private var cachedUsageDayStartMillis: Long = 0L

    override fun onCreate() {
        super.onCreate()
        debugMonitor("created; overlay=${overlayPermissionState().summary}; starting foreground notification and monitor loop")
        ensureMonitorChannel()
        debugMonitor("startForeground request notificationId=$MONITOR_NOTIFICATION_ID")
        startForeground(
            MONITOR_NOTIFICATION_ID,
            buildMonitorNotification(
                title = "ScreenRest",
                text = "Usage monitor starting",
            ),
        )
        debugMonitor("startForeground done notificationId=$MONITOR_NOTIFICATION_ID")
        serviceScope.launch {
            updateMonitorStatus(decision = "service starting", force = true)
        }
        scheduleExactRecoveryAlarm(
            context = applicationContext,
            reason = "foreground monitor watchdog",
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        debugMonitor("onStartCommand action=${intent?.action.orEmpty().ifBlank { "start" }} flags=$flags startId=$startId")
        if (intent?.action == ACTION_STOP) {
            debugMonitor("stop action received")
            removeBlockingOverlay()
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_ALLOW_HOME_EXIT) {
            debugMonitor("allow home exit action received")
            allowHomeExitFromBlockedScreen(intent.getStringExtra(EXTRA_PACKAGE_NAME))
            ensureMonitorLoopRunning()
            return START_STICKY
        }
        if (intent?.action == ACTION_EVALUATE_FOREGROUND_PACKAGE) {
            val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()
            if (packageName.isNotBlank()) {
                debugMonitor("immediate request package=$packageName")
                latestRequestedForegroundPackageName = packageName
                ForegroundAppTracker.update(packageName)
                serviceScope.launch {
                    runCatching {
                        evaluateForegroundPackageNow(packageName)
                    }.onFailure {
                        debugMonitor("immediate evaluation error package=$packageName error=${it.javaClass.simpleName}")
                        repository.addEvent(EventLogType.Safety, "Usage monitor recovered from immediate evaluation error")
                    }
                }
            } else {
                debugMonitor("immediate request ignored: blank package")
            }
            return START_STICKY
        }
        if (intent?.action == ACTION_BLOCKED_ACTIVITY_VISIBLE) {
            debugMonitor("blocked manager action received")
            startBlockExitTransitionGrace(intent.getStringExtra(EXTRA_PACKAGE_NAME))
            managerOpenGraceUntilElapsed = SystemClock.elapsedRealtime() + MANAGER_OPEN_GRACE_MILLIS
            cancelBlockEnforcementGuard()
            clearActiveForegroundSession()
            lastBlockedActivityPackageName = null
            removeBlockingOverlay()
            updateMonitorNotification("ScreenRest", "Block controls open")
            ensureMonitorLoopRunning()
            return START_STICKY
        }
        if (intent?.action == ACTION_MANAGER_VISIBLE) {
            debugMonitor("manager visible action received")
            startBlockExitTransitionGrace(intent.getStringExtra(EXTRA_PACKAGE_NAME))
            managerOpenGraceUntilElapsed = SystemClock.elapsedRealtime() + MANAGER_OPEN_GRACE_MILLIS
            cancelBlockEnforcementGuard()
            clearActiveForegroundSession()
            lastBlockedActivityPackageName = null
            removeBlockingOverlay()
            updateMonitorNotification("ScreenRest", "Manager open")
            ensureMonitorLoopRunning()
            return START_STICKY
        }
        if (intent?.action == ACTION_BLOCKED_SCREEN_VISIBLE) {
            debugMonitor("blocked screen visible action received")
            clearActiveForegroundSession()
            removeBlockingOverlay()
            updateMonitorNotification("ScreenRest", "Blocked screen open")
            ensureMonitorLoopRunning()
            return START_STICKY
        }
        ensureMonitorLoopRunning()
        return START_STICKY
    }

    override fun onDestroy() {
        debugMonitor("destroyed")
        commitActiveForegroundSessionIfPossible(forceWrite = true)
        usageRepository.flushUsageContinuity()
        runCatching {
            runBlocking(Dispatchers.IO) {
                repository.markUsageMonitorStopped("service destroyed")
            }
        }
        cancelBlockEnforcementGuard()
        removeBlockingOverlay()
        monitorJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        debugMonitor("task removed")
        serviceScope.launch {
            val safeModeEnabled = repository.safeModeEnabled.first()
            val policyEnforcementEnabled = repository.policyEnforcementEnabled.first()
            if (!safeModeEnabled && policyEnforcementEnabled) {
                repository.addEvent(EventLogType.Safety, "Usage monitor task removed; requesting restart")
                start(applicationContext)
                UsageMonitorRecoveryWorker.schedule(
                    context = applicationContext,
                    forceRestart = true,
                    reason = "task removed",
                )
                scheduleExactRecoveryAlarm(
                    context = applicationContext,
                    reason = "task removed exact recovery",
                    delayMillis = 5_000L,
                )
            }
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startMonitorLoop() {
        debugMonitor("start monitor loop previousActive=${monitorJob?.isActive == true}")
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            debugMonitor("monitor loop running interval=${MONITOR_INTERVAL_MILLIS}ms")
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

    private fun ensureMonitorLoopRunning() {
        if (monitorJob == null) {
            debugMonitor("monitor loop missing; restarting from onStartCommand")
            startMonitorLoop()
        }
    }

    private suspend fun evaluateCurrentForegroundApp() {
        val safeModeEnabled = repository.safeModeEnabled.first()
        val policyEnforcementEnabled = repository.policyEnforcementEnabled.first()
        if (safeModeEnabled || !policyEnforcementEnabled) {
            debugMonitorState("loop gate off safe=$safeModeEnabled policy=$policyEnforcementEnabled")
            cancelBlockEnforcementGuard()
            withContext(Dispatchers.Main) { removeBlockingOverlay() }
            updateMonitorNotification("ScreenRest", "Policy enforcement is off")
            repository.markUsageMonitorStopped("policy enforcement off")
            stopSelf()
            return
        }

        if (!usageRepository.hasUsageAccess()) {
            debugMonitorState("loop gate no usage access")
            cancelBlockEnforcementGuard()
            withContext(Dispatchers.Main) { removeBlockingOverlay() }
            updateMonitorNotification("ScreenRest", "Usage access is required")
            updateMonitorStatus(decision = "usage access required")
            return
        }

        if (!isDeviceInteractive()) {
            debugMonitorState("loop gate screen off")
            cancelBlockEnforcementGuard()
            clearActiveForegroundSession()
            withContext(Dispatchers.Main) { removeBlockingOverlay() }
            updateMonitorNotification("ScreenRest", "Screen is off")
            updateMonitorStatus(decision = "screen off")
            return
        }

        val overlayPackageName = blockingOverlayPackageName
        var cachedOverlayDecision: MonitorDecision? = null

        if (overlayPackageName != null) {
            val existingDecision = evaluatePackage(overlayPackageName)
            cachedOverlayDecision = existingDecision
            if (!existingDecision.result.decision.isWouldBlock()) {
                withContext(Dispatchers.Main) { removeBlockingOverlay() }
            }
        }

        val rawForegroundPackageName = usageRepository.getRawCurrentForegroundPackageName().orEmpty()
        val detectedForegroundPackageName = usageRepository.getCurrentForegroundPackageName().orEmpty()
        val packageName = resolveForegroundPackageName(
            detectedPackageName = detectedForegroundPackageName,
            rawPackageName = rawForegroundPackageName,
        )
        debugMonitorState(
            "loop foreground raw=$rawForegroundPackageName detected=$detectedForegroundPackageName resolved=$packageName active=${activeForegroundPackageName.orEmpty()} overlay=${overlayPackageName.orEmpty()}",
        )
        if (SystemClock.elapsedRealtime() < managerOpenGraceUntilElapsed) {
            if (
                packageName.isBlank() ||
                packageName == applicationContext.packageName ||
                AppVisibility.isHiddenPackage(packageName) ||
                isBlockExitTransitionPackage(packageName)
            ) {
                debugMonitorState("loop gate manager grace")
                clearActiveForegroundSession()
                updateMonitorNotification("ScreenRest", "Block controls open")
                updateMonitorStatus(decision = "manager controls open")
                return
            }
            debugMonitor("manager grace canceled by foreground package=$packageName")
            managerOpenGraceUntilElapsed = 0L
        }
        if (SystemClock.elapsedRealtime() < homeExitGraceUntilElapsed) {
            if (shouldHonorHomeExitGrace(packageName)) {
                debugMonitorState("loop gate home grace package=$packageName")
                cancelBlockEnforcementGuard()
                clearActiveForegroundSession()
                withContext(Dispatchers.Main) { removeBlockingOverlay() }
                updateMonitorNotification("ScreenRest", "Home is available")
                updateMonitorStatus(decision = "home grace")
                return
            }
            debugMonitor("home grace canceled by foreground package=$packageName")
            homeExitGraceUntilElapsed = 0L
        }
        if (packageName.isBlank() || packageName == applicationContext.packageName || AppVisibility.isHiddenPackage(packageName)) {
            debugMonitorState("loop drop package=$packageName raw=$rawForegroundPackageName detected=$detectedForegroundPackageName")
            clearActiveForegroundSession()
            updateMonitorNotification("ScreenRest", "Monitoring limited apps")
            updateMonitorStatus(decision = "waiting for foreground app")
            return
        }

        val settings = repository.usagePolicySettings.first()
        val hasDirectLimit = settings.hasDirectLimitFor(packageName)
        val hasTotalLimit = settings.todayLimitMinutes() > 0
        val hasActiveScheduleBlock = settings.isScheduleBlockingNow()
        val hasAllowOnlyBlock = settings.allowOnlyModeEnabled
        if (!hasDirectLimit && !hasTotalLimit && !hasActiveScheduleBlock && !hasAllowOnlyBlock) {
            debugMonitorState("loop no policy package=$packageName direct=false total=false schedule=false allowOnly=false")
            clearActiveForegroundSession()
            updateMonitorNotification("ScreenRest", "Monitoring limited apps")
            updateMonitorStatus(
                appName = usageRepository.getAppLabel(packageName),
                packageName = packageName,
                decision = "not limited",
                limitedTarget = false,
                blockReason = "",
            )
            return
        }

        updateActiveForegroundSession(packageName)
        val decision = if (packageName == overlayPackageName && cachedOverlayDecision != null) {
            cachedOverlayDecision
        } else {
            evaluatePackage(packageName, settings)
        }
        updateMonitorNotification(decision.toNotificationTitle(), decision.toNotificationText())
        updateMonitorStatus(
            appName = decision.result.appName,
            packageName = decision.result.packageName,
            decision = decision.result.decision.toLogReason(),
            usedMillis = decision.usedMillis,
            limitMillis = decision.limitMillis,
            limitedTarget = decision.result.decision != BlockDecision.AllowedNoLimit,
            blockReason = if (decision.result.decision.isWouldBlock()) {
                decision.result.decision.toLogReason()
            } else {
                ""
            },
        )
        debugMonitorState(
            "loop decision package=$packageName decision=${decision.result.decision.toLogReason()} used=${decision.usedMillis} limit=${decision.limitMillis} direct=${decision.directLimit}",
        )

        if (decision.result.decision.isWouldBlock()) {
            if (hasActiveBlockSession(decision.result.packageName)) {
                debugMonitorState("loop enforce skipped active block session package=${decision.result.packageName}")
                return
            }
            debugMonitor("loop enforce package=$packageName decision=${decision.result.decision.toLogReason()}")
            enforceBlock(decision)
        }
    }

    private suspend fun evaluateForegroundPackageNow(packageName: String) {
        if (packageName.isBlank() || packageName == applicationContext.packageName || AppVisibility.isHiddenPackage(packageName)) {
            debugMonitorState("immediate drop package=$packageName")
            return
        }

        val safeModeEnabled = repository.safeModeEnabled.first()
        val policyEnforcementEnabled = repository.policyEnforcementEnabled.first()
        if (safeModeEnabled || !policyEnforcementEnabled) {
            debugMonitorState("immediate gate off package=$packageName safe=$safeModeEnabled policy=$policyEnforcementEnabled")
            cancelBlockEnforcementGuard()
            withContext(Dispatchers.Main) { removeBlockingOverlay() }
            updateMonitorNotification("ScreenRest", "Policy enforcement is off")
            repository.markUsageMonitorStopped("policy enforcement off")
            return
        }

        if (!usageRepository.hasUsageAccess()) {
            debugMonitorState("immediate gate no usage access package=$packageName")
            cancelBlockEnforcementGuard()
            withContext(Dispatchers.Main) { removeBlockingOverlay() }
            updateMonitorNotification("ScreenRest", "Usage access is required")
            updateMonitorStatus(decision = "usage access required", force = true)
            return
        }

        if (!isDeviceInteractive()) {
            debugMonitorState("immediate gate screen off package=$packageName")
            cancelBlockEnforcementGuard()
            clearActiveForegroundSession()
            withContext(Dispatchers.Main) { removeBlockingOverlay() }
            updateMonitorNotification("ScreenRest", "Screen is off")
            updateMonitorStatus(decision = "screen off", force = true)
            return
        }

        if (SystemClock.elapsedRealtime() < homeExitGraceUntilElapsed) {
            if (shouldHonorHomeExitGrace(packageName)) {
                debugMonitorState("immediate gate home grace package=$packageName")
                cancelBlockEnforcementGuard()
                clearActiveForegroundSession()
                withContext(Dispatchers.Main) { removeBlockingOverlay() }
                updateMonitorNotification("ScreenRest", "Home is available")
                updateMonitorStatus(decision = "home grace", force = true)
                return
            }
            debugMonitor("home grace canceled by immediate package=$packageName")
            homeExitGraceUntilElapsed = 0L
        }

        if (SystemClock.elapsedRealtime() < managerOpenGraceUntilElapsed) {
            if (isBlockExitTransitionPackage(packageName)) {
                debugMonitorState("immediate gate manager transition package=$packageName")
                clearActiveForegroundSession()
                updateMonitorNotification("ScreenRest", "Block controls open")
                updateMonitorStatus(decision = "manager controls open", force = true)
                return
            }
            debugMonitor("manager grace canceled by immediate package=$packageName")
            managerOpenGraceUntilElapsed = 0L
        }

        val settings = repository.usagePolicySettings.first()
        val hasDirectLimit = settings.hasDirectLimitFor(packageName)
        val hasTotalLimit = settings.todayLimitMinutes() > 0
        val hasActiveScheduleBlock = settings.isScheduleBlockingNow()
        val hasAllowOnlyBlock = settings.allowOnlyModeEnabled
        if (latestRequestedForegroundPackageName != packageName) {
            debugMonitorState("immediate stale package=$packageName latest=${latestRequestedForegroundPackageName.orEmpty()}")
            return
        }
        if (!hasDirectLimit && !hasTotalLimit && !hasActiveScheduleBlock && !hasAllowOnlyBlock) {
            debugMonitorState("immediate no policy package=$packageName direct=false total=false schedule=false allowOnly=false")
            if (activeForegroundPackageName == packageName) {
                clearActiveForegroundSession()
            }
            updateMonitorNotification("ScreenRest", "Monitoring limited apps")
            updateMonitorStatus(
                appName = usageRepository.getAppLabel(packageName),
                packageName = packageName,
                decision = "not limited",
                force = true,
                limitedTarget = false,
                blockReason = "",
            )
            return
        }

        updateActiveForegroundSession(packageName)
        val decision = evaluatePackage(packageName, settings)
        updateMonitorNotification(decision.toNotificationTitle(), decision.toNotificationText())
        updateMonitorStatus(
            appName = decision.result.appName,
            packageName = decision.result.packageName,
            decision = decision.result.decision.toLogReason(),
            force = true,
            usedMillis = decision.usedMillis,
            limitMillis = decision.limitMillis,
            limitedTarget = decision.result.decision != BlockDecision.AllowedNoLimit,
            blockReason = if (decision.result.decision.isWouldBlock()) {
                decision.result.decision.toLogReason()
            } else {
                ""
            },
        )
        debugMonitorState(
            "immediate decision package=$packageName decision=${decision.result.decision.toLogReason()} used=${decision.usedMillis} limit=${decision.limitMillis} direct=${decision.directLimit}",
        )

        if (decision.result.decision.isWouldBlock()) {
            if (hasActiveBlockSession(decision.result.packageName)) {
                debugMonitorState("immediate enforce skipped active block session package=${decision.result.packageName}")
                return
            }
            debugMonitor("immediate enforce package=$packageName decision=${decision.result.decision.toLogReason()}")
            enforceBlock(decision)
        }
    }

    private suspend fun evaluatePackage(
        packageName: String,
        settings: UsagePolicySettings? = null,
    ): MonitorDecision {
        val resolvedSettings = settings ?: repository.usagePolicySettings.first()
        val temporaryUnlockState = repository.temporaryUnlockState.first().forToday()
        val allowedPackages = repository.allowedAppPackages.first()
        val scheduleAllowedPackages = resolvedSettings.activeScheduleAllowedPackages()
        val schedulePackageAllowed = SafetyGate.isUserAllowedPackage(
            targetPackageName = packageName,
            userAllowedPackages = scheduleAllowedPackages,
        )
        val usageByPackage = getCachedTodayUsageMillisByPackage()
        val appName = usageRepository.getAppLabel(packageName)
        val rawAppUsedMillis = usageByPackage[packageName] ?: 0L
        val appUsedMillis = usageRepository.rememberTodayUsageMillis(
            packageName = packageName,
            usageMillis = adjustedActiveForegroundUsageMillis(packageName, rawAppUsedMillis),
        )
        val stableUsageByPackage = usageByPackage + (packageName to appUsedMillis)
        val totalUsedMillis = stableUsageByPackage.values.sum()
        val appLimitMinutes = resolvedSettings.appLimitMap()[packageName] ?: 0
        val targetGroup = resolvedSettings.normalizedAppGroups()
            .firstOrNull { group -> packageName in group.packageNames && group.budgetMinutes > 0 }
        val targetGroupUsedMillis = targetGroup
            ?.packageNames
            ?.sumOf { groupPackageName ->
                val rawGroupPackageUsageMillis = stableUsageByPackage[groupPackageName] ?: 0L
                if (groupPackageName == packageName) {
                    appUsedMillis
                } else {
                    rawGroupPackageUsageMillis
                }
            }
        val evaluation = BlockDecisionEngine.evaluateDetailed(
            packageName = packageName,
            appName = appName,
            safeModeEnabled = false,
            policyEnforcementEnabled = true,
            settings = resolvedSettings,
            appUsedMillis = appUsedMillis,
            totalUsedMillis = totalUsedMillis,
            targetGroupUsedMillis = targetGroupUsedMillis,
            targetGroupLimitMinutes = targetGroup?.budgetMinutes,
            temporaryUnlockState = temporaryUnlockState,
            userAllowedPackages = allowedPackages,
            scheduleAllowedPackages = scheduleAllowedPackages,
        )
        debugMonitorState(
            "EVAL package=$packageName decision=${evaluation.result.decision.toLogReason()} appUsed=$appUsedMillis rawApp=$rawAppUsedMillis total=$totalUsedMillis group=${targetGroup?.name.orEmpty()} groupUsed=${targetGroupUsedMillis ?: 0L} limit=${evaluation.limitMillis} extra=${evaluation.activeExtraMinutes} unlocked=${evaluation.unlockedForToday} scheduleAllowed=$schedulePackageAllowed",
        )

        return MonitorDecision(
            result = evaluation.result,
            usedMillis = evaluation.usedMillis,
            limitMillis = evaluation.limitMillis,
            directLimit = evaluation.directlyManaged,
            extraMinutes = evaluation.activeExtraMinutes,
            unlockedForToday = evaluation.unlockedForToday,
            appUsedMillis = appUsedMillis,
            appLimitMillis = appLimitMinutes
                .takeIf { minutes -> minutes > 0 }
                ?.let { minutes -> minutes.toLong() * 60_000L },
            targetGroupName = targetGroup?.name,
            targetGroupUsedMillis = targetGroupUsedMillis,
            targetGroupLimitMillis = targetGroup
                ?.budgetMinutes
                ?.takeIf { minutes -> minutes > 0 }
                ?.let { minutes -> minutes.toLong() * 60_000L },
        )
    }

    private fun getCachedTodayUsageMillisByPackage(): Map<String, Long> {
        val now = SystemClock.elapsedRealtime()
        val todayStartMillis = localDayStartMillis()
        if (cachedUsageDayStartMillis != todayStartMillis) {
            cachedUsageDayStartMillis = todayStartMillis
            cachedUsageByPackage = emptyMap()
            cachedUsageByPackageAtElapsed = 0L
        }
        if (cachedUsageByPackage.isNotEmpty() && now - cachedUsageByPackageAtElapsed < USAGE_MAP_CACHE_MILLIS) {
            return cachedUsageByPackage
        }
        val usageByPackage = usageRepository.getTodayUsageMillisByPackage()
        cachedUsageByPackage = usageByPackage
        cachedUsageByPackageAtElapsed = now
        return usageByPackage
    }

    private fun updateActiveForegroundSession(packageName: String) {
        foregroundMissingSinceElapsed = 0L
        val todayStartMillis = localDayStartMillis()
        if (activeForegroundDayStartMillis != 0L && activeForegroundDayStartMillis != todayStartMillis) {
            debugMonitor("active foreground day rollover reset package=${activeForegroundPackageName.orEmpty()}")
            resetActiveForegroundSessionWithoutCommit()
        }
        if (activeForegroundPackageName != packageName) {
            commitActiveForegroundSessionIfPossible(forceWrite = true)
            debugMonitor("active foreground package=$packageName previous=${activeForegroundPackageName.orEmpty()}")
            activeForegroundPackageName = packageName
            activeForegroundStartedAtElapsed = SystemClock.elapsedRealtime()
            activeForegroundBaselineUsageMillis = null
            activeForegroundDayStartMillis = todayStartMillis
        } else if (activeForegroundStartedAtElapsed <= 0L) {
            activeForegroundStartedAtElapsed = SystemClock.elapsedRealtime()
            activeForegroundDayStartMillis = todayStartMillis
        }
    }

    private fun clearActiveForegroundSession() {
        commitActiveForegroundSessionIfPossible(forceWrite = true)
        if (activeForegroundPackageName != null) {
            debugMonitor("clear active foreground package=${activeForegroundPackageName.orEmpty()}")
        }
        activeForegroundPackageName = null
        activeForegroundStartedAtElapsed = 0L
        activeForegroundBaselineUsageMillis = null
        activeForegroundDayStartMillis = 0L
        foregroundMissingSinceElapsed = 0L
    }

    private suspend fun updateMonitorStatus(
        appName: String = "",
        packageName: String = "",
        decision: String = "",
        force: Boolean = false,
        usedMillis: Long = 0L,
        limitMillis: Long? = null,
        limitedTarget: Boolean? = null,
        blockReason: String? = null,
        overlayAttached: Boolean? = null,
        blockAttemptMillis: Long? = null,
        blockRetryCount: Int? = null,
    ) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastMonitorStatusWrittenAt < MONITOR_STATUS_WRITE_INTERVAL_MILLIS) {
            return
        }
        lastMonitorStatusWrittenAt = now
        val normalizedLimitedTarget = limitedTarget ?: if (packageName.isBlank()) false else null
        val normalizedBlockReason = blockReason ?: if (packageName.isBlank()) "" else null
        repository.updateUsageMonitorStatus(
            running = true,
            appName = appName,
            packageName = packageName,
            decision = decision,
            usedMillis = usedMillis,
            limitMillis = limitMillis,
            limitedTarget = normalizedLimitedTarget,
            blockReason = normalizedBlockReason,
            overlayAttached = overlayAttached,
            blockAttemptMillis = blockAttemptMillis,
            blockRetryCount = blockRetryCount,
        )
    }

    @Synchronized
    private fun commitActiveForegroundSessionIfPossible(forceWrite: Boolean = false) {
        val packageName = activeForegroundPackageName ?: return
        val baselineUsageMillis = activeForegroundBaselineUsageMillis ?: return
        if (activeForegroundStartedAtElapsed <= 0L) {
            return
        }
        if (activeForegroundDayStartMillis != 0L && activeForegroundDayStartMillis != localDayStartMillis()) {
            debugMonitor("active foreground commit skipped after day rollover package=$packageName")
            resetActiveForegroundSessionWithoutCommit()
            return
        }
        val elapsedMillis = (SystemClock.elapsedRealtime() - activeForegroundStartedAtElapsed).coerceAtLeast(0L)
        usageRepository.rememberTodayUsageMillis(
            packageName = packageName,
            usageMillis = baselineUsageMillis + elapsedMillis,
            forceWrite = forceWrite,
        )
    }

    @Synchronized
    private fun adjustedActiveForegroundUsageMillis(packageName: String, rawUsageMillis: Long): Long {
        if (activeForegroundPackageName != packageName || activeForegroundStartedAtElapsed <= 0L) {
            return rawUsageMillis
        }
        if (activeForegroundDayStartMillis != 0L && activeForegroundDayStartMillis != localDayStartMillis()) {
            debugMonitor("active foreground adjust skipped after day rollover package=$packageName")
            resetActiveForegroundSessionWithoutCommit()
            return rawUsageMillis
        }

        val elapsedMillis = (SystemClock.elapsedRealtime() - activeForegroundStartedAtElapsed).coerceAtLeast(0L)
        val baselineUsageMillis = activeForegroundBaselineUsageMillis ?: rawUsageMillis.also { baseline ->
            activeForegroundBaselineUsageMillis = baseline
            debugMonitor("usage baseline package=$packageName raw=$rawUsageMillis")
        }
        val adjustedUsageMillis = maxOf(rawUsageMillis, baselineUsageMillis + elapsedMillis)
        if (adjustedUsageMillis - rawUsageMillis >= DEBUG_USAGE_ADJUST_LOG_THRESHOLD_MILLIS) {
            debugMonitorState(
                "usage adjusted package=$packageName raw=$rawUsageMillis adjusted=$adjustedUsageMillis elapsed=$elapsedMillis baseline=$baselineUsageMillis",
            )
        }
        return adjustedUsageMillis
    }

    private fun resetActiveForegroundSessionWithoutCommit() {
        activeForegroundPackageName = null
        activeForegroundStartedAtElapsed = 0L
        activeForegroundBaselineUsageMillis = null
        activeForegroundDayStartMillis = 0L
        foregroundMissingSinceElapsed = 0L
        cachedUsageByPackage = emptyMap()
        cachedUsageByPackageAtElapsed = 0L
        cachedUsageDayStartMillis = localDayStartMillis()
    }

    private fun resolveForegroundPackageName(detectedPackageName: String, rawPackageName: String = ""): String {
        if (
            detectedPackageName == applicationContext.packageName ||
            rawPackageName == applicationContext.packageName ||
            rawPackageName.shouldHardClearForegroundSession()
        ) {
            clearActiveForegroundSession()
            foregroundMissingSinceElapsed = 0L
            return ""
        }

        if (
            detectedPackageName.isNotBlank() &&
            !AppVisibility.isHiddenPackage(detectedPackageName)
        ) {
            foregroundMissingSinceElapsed = 0L
            return detectedPackageName
        }

        val activePackageName = activeForegroundPackageName.orEmpty()
        if (activePackageName.isBlank()) {
            foregroundMissingSinceElapsed = 0L
            return ""
        }

        val hasTransientForegroundSignal = rawPackageName.isNotBlank() || detectedPackageName.isNotBlank()
        if (hasTransientForegroundSignal) {
            if (foregroundMissingSinceElapsed <= 0L) {
                foregroundMissingSinceElapsed = SystemClock.elapsedRealtime()
            }
            debugMonitorState(
                "foreground transient raw=$rawPackageName detected=$detectedPackageName usingActive=$activePackageName",
            )
            return activePackageName
        }

        val now = SystemClock.elapsedRealtime()
        if (foregroundMissingSinceElapsed <= 0L) {
            foregroundMissingSinceElapsed = now
        }
        val missingDurationMillis = now - foregroundMissingSinceElapsed
        if (missingDurationMillis >= ACTIVE_FOREGROUND_BLANK_CLEAR_MILLIS) {
            debugMonitor(
                "foreground blank timeout clear active package=$activePackageName missingFor=${missingDurationMillis}ms",
            )
            clearActiveForegroundSession()
            return ""
        }
        debugMonitorState(
            "foreground continuous package=$activePackageName missingFor=${missingDurationMillis}ms usingActive=true",
        )
        return activePackageName
    }

    private fun String.shouldHardClearForegroundSession(): Boolean {
        val normalizedPackageName = lowercase()
        return normalizedPackageName == "com.android.settings" ||
            normalizedPackageName == "com.google.android.settings" ||
            normalizedPackageName == "com.google.android.apps.nexuslauncher" ||
            normalizedPackageName == "com.sec.android.app.launcher" ||
            normalizedPackageName == "com.samsung.android.oneui.home" ||
            normalizedPackageName == "com.android.launcher" ||
            normalizedPackageName == "com.android.launcher3" ||
            normalizedPackageName.contains("launcher") ||
            normalizedPackageName.contains("oneui.home") ||
            normalizedPackageName.contains("packageinstaller") ||
            normalizedPackageName.contains("permissioncontroller") ||
            normalizedPackageName.contains("setupwizard")
    }

    private fun shouldHonorHomeExitGrace(packageName: String): Boolean {
        return packageName.isBlank() ||
            packageName == applicationContext.packageName ||
            packageName.shouldHardClearForegroundSession() ||
            isBlockExitTransitionPackage(packageName)
    }

    private fun startBlockExitTransitionGrace(packageName: String?) {
        if (packageName.isNullOrBlank()) {
            return
        }
        blockExitTransitionPackageName = packageName
        blockExitTransitionUntilElapsed = SystemClock.elapsedRealtime() + BLOCK_EXIT_TRANSITION_GRACE_MILLIS
        debugMonitorState("block exit transition grace package=$packageName")
    }

    private fun isBlockExitTransitionPackage(packageName: String): Boolean {
        if (packageName.isBlank() || packageName != blockExitTransitionPackageName) {
            return false
        }
        val active = SystemClock.elapsedRealtime() < blockExitTransitionUntilElapsed
        if (!active) {
            blockExitTransitionPackageName = null
            blockExitTransitionUntilElapsed = 0L
        }
        return active
    }

    private fun nextBlockDiagnosticRetryCount(packageName: String): Int {
        if (diagnosticBlockPackageName != packageName) {
            diagnosticBlockPackageName = packageName
            diagnosticBlockRetryCount = 0
        }
        diagnosticBlockRetryCount += 1
        return diagnosticBlockRetryCount
    }

    private suspend fun enforceBlock(decision: MonitorDecision) {
        if (
            SystemClock.elapsedRealtime() < homeExitGraceUntilElapsed &&
            shouldHonorHomeExitGrace(decision.result.packageName)
        ) {
            debugMonitorState("enforce skipped home grace package=${decision.result.packageName}")
            return
        }
        if (SystemClock.elapsedRealtime() < homeExitGraceUntilElapsed) {
            debugMonitor("home grace canceled by enforce package=${decision.result.packageName}")
            homeExitGraceUntilElapsed = 0L
        }
        val overlayState = overlayPermissionState()
        val retryCount = nextBlockDiagnosticRetryCount(decision.result.packageName)
        debugMonitor(
            "BLOCK_PIPE enforce-start package=${decision.result.packageName} decision=${decision.result.decision.toLogReason()} used=${decision.usedMillis} limit=${decision.limitMillis} direct=${decision.directLimit} overlay=${overlayState.summary} homeGrace=${remainingGraceMillis(homeExitGraceUntilElapsed)} managerGrace=${remainingGraceMillis(managerOpenGraceUntilElapsed)}",
        )
        logBlockPipelineEvent(
            decision = decision,
            stage = "차단 시도",
            detail = "retry=$retryCount overlay=${overlayState.summary}",
            retryCount = retryCount,
            force = true,
        )
        updateMonitorStatus(
            appName = decision.result.appName,
            packageName = decision.result.packageName,
            decision = decision.result.decision.toLogReason(),
            force = true,
            usedMillis = decision.usedMillis,
            limitMillis = decision.limitMillis,
            limitedTarget = true,
            blockReason = decision.result.decision.toLogReason(),
            blockAttemptMillis = System.currentTimeMillis(),
            blockRetryCount = retryCount,
        )
        logBlockIfNeeded(decision)
        debugMonitor("BLOCK_PIPE pause-media package=${decision.result.packageName}")
        pauseActiveMediaPlayback()
        val overlayAttached = withContext(Dispatchers.Main) {
            showBlockingOverlay(decision)
        }
        updateMonitorStatus(
            appName = decision.result.appName,
            packageName = decision.result.packageName,
            decision = decision.result.decision.toLogReason(),
            force = true,
            usedMillis = decision.usedMillis,
            limitMillis = decision.limitMillis,
            limitedTarget = true,
            blockReason = decision.result.decision.toLogReason(),
            overlayAttached = overlayAttached,
            blockAttemptMillis = System.currentTimeMillis(),
            blockRetryCount = retryCount,
        )
        debugMonitor("BLOCK_PIPE overlay-result package=${decision.result.packageName} attached=$overlayAttached")
        if (overlayAttached) {
            logBlockPipelineEvent(
                decision = decision,
                stage = "Overlay 표시됨",
                detail = "overlay view attached",
                retryCount = retryCount,
            )
        } else {
            logBlockPipelineEvent(
                decision = decision,
                stage = "Overlay 표시 실패",
                detail = "fallback blocked activity requested",
                eventType = EventLogType.Warning,
                retryCount = retryCount,
            )
        }
        startBlockEnforcementGuard(decision)
        debugMonitor("BLOCK_PIPE send-home-force package=${decision.result.packageName}")
        logBlockPipelineEvent(
            decision = decision,
            stage = "홈 이동 시도",
            detail = "overlayAttached=$overlayAttached",
            retryCount = retryCount,
        )
        sendHomeIntent(force = true)
        if (overlayAttached) {
            debugMonitorState("overlay attached; home sent behind overlay package=${decision.result.packageName}")
            startForegroundEvictionLoop(decision, source = "initial")
        } else {
            debugMonitor("BLOCK_PIPE overlay-failed-open-activity package=${decision.result.packageName}")
            openBlockedActivityOrOverlay(
                decision = decision,
                keepExistingOverlay = false,
                forceActivity = true,
            )
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
            "[BLOCK][${decision.result.decision.toBlockCategory()}] ${decision.result.appName} (${decision.result.packageName}) ${decision.result.decision.toKoreanBlockReason()} - used=${formatSeconds(decision.usedMillis)} limit=${decision.limitMillis?.let { formatSeconds(it) } ?: "none"}",
        )
    }

    private suspend fun logBlockPipelineEvent(
        decision: MonitorDecision,
        stage: String,
        detail: String = "",
        eventType: EventLogType = EventLogType.Safety,
        retryCount: Int? = null,
        force: Boolean = false,
    ) {
        val now = SystemClock.elapsedRealtime()
        val key = "${decision.result.packageName}|$stage"
        val lastLoggedAt = lastBlockPipelineEventAtByKey[key] ?: 0L
        if (!force && now - lastLoggedAt < BLOCK_PIPELINE_LOG_THROTTLE_MILLIS) {
            return
        }
        lastBlockPipelineEventAtByKey[key] = now
        val retryText = retryCount?.let { count -> " retry=$count" }.orEmpty()
        val detailText = detail.takeIf { it.isNotBlank() }?.let { value -> " - $value" }.orEmpty()
        repository.addEvent(
            eventType,
            "[BLOCK][${decision.result.decision.toBlockCategory()}][$stage] ${decision.result.appName} (${decision.result.packageName})$retryText$detailText",
        )
    }

    private fun startBlockEnforcementGuard(decision: MonitorDecision) {
        val packageName = decision.result.packageName
        if (blockEnforcementGuardPackageName == packageName && blockEnforcementGuardJob?.isActive == true) {
            return
        }
        cancelBlockEnforcementGuard()
        blockEnforcementGuardPackageName = packageName
        blockEnforcementGuardJob = serviceScope.launch {
            var lastReassertAt = 0L
            while (isActive) {
                if (SystemClock.elapsedRealtime() < homeExitGraceUntilElapsed) {
                    delay(BLOCK_ENFORCEMENT_GUARD_INTERVAL_MILLIS)
                    continue
                }
                if (SystemClock.elapsedRealtime() < managerOpenGraceUntilElapsed) {
                    delay(BLOCK_ENFORCEMENT_GUARD_INTERVAL_MILLIS)
                    continue
                }

                val safeModeEnabled = repository.safeModeEnabled.first()
                val policyEnforcementEnabled = repository.policyEnforcementEnabled.first()
                if (safeModeEnabled || !policyEnforcementEnabled || !usageRepository.hasUsageAccess()) {
                    break
                }

                val currentDecision = evaluatePackage(packageName)
                if (!currentDecision.result.decision.isWouldBlock()) {
                    break
                }

                pauseActiveMediaPlayback()

                val rawForegroundPackageName = usageRepository.getRawCurrentForegroundPackageName().orEmpty()
                val managerVisible = rawForegroundPackageName == applicationContext.packageName
                val foregroundPackageName = resolveForegroundPackageName(
                    detectedPackageName = rawForegroundPackageName.takeUnless { it == applicationContext.packageName }.orEmpty(),
                    rawPackageName = rawForegroundPackageName,
                )
                val targetLockedForBlocking = blockingOverlayPackageName == packageName ||
                    blockForegroundEvictionPackageName == packageName
                val shouldReassertBlock = !managerVisible &&
                    (
                        targetLockedForBlocking ||
                            foregroundPackageName == packageName ||
                            currentDecision.result.decision == BlockDecision.WouldBlockTotalLimit
                    )
                val now = SystemClock.elapsedRealtime()
                debugMonitorState(
                    "BLOCK_PIPE guard package=$packageName raw=$rawForegroundPackageName resolved=$foregroundPackageName managerVisible=$managerVisible shouldReassert=$shouldReassertBlock overlayAttached=${blockingOverlayView?.isAttachedToWindow == true} decision=${currentDecision.result.decision.toLogReason()}",
                )
                if (shouldReassertBlock && now - lastReassertAt >= BLOCK_REASSERT_INTERVAL_MILLIS) {
                    lastReassertAt = now
                    val retryCount = nextBlockDiagnosticRetryCount(packageName)
                    val overlayAttached = withContext(Dispatchers.Main) {
                        showBlockingOverlay(currentDecision)
                    }
                    logBlockPipelineEvent(
                        decision = currentDecision,
                        stage = "?ъ감?⑤맖",
                        detail = "overlayAttached=$overlayAttached foreground=$foregroundPackageName",
                        eventType = if (overlayAttached) EventLogType.Safety else EventLogType.Warning,
                        retryCount = retryCount,
                    )
                    updateMonitorStatus(
                        appName = currentDecision.result.appName,
                        packageName = currentDecision.result.packageName,
                        decision = currentDecision.result.decision.toLogReason(),
                        force = true,
                        usedMillis = currentDecision.usedMillis,
                        limitMillis = currentDecision.limitMillis,
                        limitedTarget = true,
                        blockReason = currentDecision.result.decision.toLogReason(),
                        overlayAttached = overlayAttached,
                        blockAttemptMillis = System.currentTimeMillis(),
                        blockRetryCount = retryCount,
                    )
                    if (!overlayAttached) {
                        logBlockPipelineEvent(
                            decision = currentDecision,
                            stage = "HOME_INTENT_REASSERT",
                            detail = "overlayAttached=false foreground=$foregroundPackageName",
                            eventType = EventLogType.Warning,
                            retryCount = retryCount,
                        )
                        sendHomeIntent(force = true)
                        openBlockedActivityOrOverlay(
                            decision = currentDecision,
                            keepExistingOverlay = false,
                            forceActivity = true,
                        )
                    } else if (
                        targetLockedForBlocking ||
                        foregroundPackageName == packageName ||
                        currentDecision.result.decision == BlockDecision.WouldBlockTotalLimit
                    ) {
                        startForegroundEvictionLoop(currentDecision, source = "guard")
                    }
                }

                delay(BLOCK_ENFORCEMENT_GUARD_INTERVAL_MILLIS)
            }
            if (blockEnforcementGuardPackageName == packageName) {
                blockEnforcementGuardPackageName = null
                blockEnforcementGuardJob = null
            }
        }
    }

    private fun cancelBlockEnforcementGuard() {
        blockEnforcementGuardJob?.cancel()
        blockEnforcementGuardJob = null
        blockEnforcementGuardPackageName = null
        blockForegroundEvictionJob?.cancel()
        blockForegroundEvictionJob = null
        blockForegroundEvictionPackageName = null
    }

    private fun hasActiveBlockSession(packageName: String): Boolean {
        val overlayActive = blockingOverlayPackageName == packageName &&
            blockingOverlayView?.isAttachedToWindow == true
        val guardActive = blockEnforcementGuardPackageName == packageName &&
            blockEnforcementGuardJob?.isActive == true
        val evictionActive = blockForegroundEvictionPackageName == packageName &&
            blockForegroundEvictionJob?.isActive == true
        val blockedActivityActive = lastBlockedActivityPackageName == packageName &&
            SystemClock.elapsedRealtime() - lastBlockedActivityStartedAt < BLOCK_ACTIVITY_SESSION_MILLIS
        return overlayActive || guardActive || evictionActive || blockedActivityActive
    }

    private fun startForegroundEvictionLoop(decision: MonitorDecision, source: String) {
        val packageName = decision.result.packageName
        if (blockForegroundEvictionPackageName == packageName && blockForegroundEvictionJob?.isActive == true) {
            return
        }
        blockForegroundEvictionJob?.cancel()
        blockForegroundEvictionPackageName = packageName
        blockForegroundEvictionJob = serviceScope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            var lastFallbackActivityLaunchAt = 0L
            var homePulseCount = 0
            repository.addEvent(
                EventLogType.Safety,
                "Strong block eviction started for ${decision.result.appName} ($source)",
            )
            while (isActive) {
                val now = SystemClock.elapsedRealtime()
                if (now - startedAt > BLOCK_FOREGROUND_EVICTION_WINDOW_MILLIS) {
                    break
                }
                if (now < homeExitGraceUntilElapsed || now < managerOpenGraceUntilElapsed) {
                    break
                }

                val safeModeEnabled = repository.safeModeEnabled.first()
                val policyEnforcementEnabled = repository.policyEnforcementEnabled.first()
                if (safeModeEnabled || !policyEnforcementEnabled || !usageRepository.hasUsageAccess()) {
                    break
                }

                val currentDecision = evaluatePackage(packageName)
                if (!currentDecision.result.decision.isWouldBlock()) {
                    break
                }

                val rawForegroundPackageName = usageRepository.getRawCurrentForegroundPackageName().orEmpty()
                val targetStillForeground = isBlockedTargetStillForeground(
                    decision = currentDecision,
                    rawForegroundPackageName = rawForegroundPackageName,
                )
                val foregroundSignalUnreliable = rawForegroundPackageName.isBlank() ||
                    rawForegroundPackageName.shouldHardClearForegroundSession()
                val overlayFront = withContext(Dispatchers.Main) {
                    focusBlockingOverlayIfPresent()
                    isBlockingOverlayEffectivelyFront()
                }
                val overlayInputFocused = withContext(Dispatchers.Main) {
                    isBlockingOverlayInputFocused()
                }
                debugMonitorState(
                    "BLOCK_PIPE eviction package=$packageName raw=$rawForegroundPackageName targetFront=$targetStillForeground signalUnreliable=$foregroundSignalUnreliable overlayFront=$overlayFront inputFocused=$overlayInputFocused source=$source pulse=$homePulseCount",
                )

                if (overlayFront && overlayInputFocused) {
                    delay(BLOCK_FOREGROUND_EVICTION_INTERVAL_MILLIS)
                    continue
                }

                if (!targetStillForeground && overlayFront && !foregroundSignalUnreliable) {
                    break
                }

                pauseActiveMediaPlayback()
                withContext(Dispatchers.Main) {
                    showBlockingOverlay(
                        decision = currentDecision,
                        forceRecreate = !overlayFront,
                    )
                    focusBlockingOverlayIfPresent()
                }
                sendHomeIntent(force = true)
                homePulseCount += 1

                if (
                    !overlayFront &&
                    now - startedAt >= BLOCK_ACTIVITY_ESCALATION_DELAY_MILLIS &&
                    now - lastFallbackActivityLaunchAt >= BLOCK_ACTIVITY_ESCALATION_REPEAT_MILLIS
                ) {
                    lastFallbackActivityLaunchAt = now
                    val launched = withContext(Dispatchers.Main) {
                        removeBlockingOverlay()
                        startBlockedActivity(currentDecision, force = true)
                    }
                    debugMonitor(
                        "BLOCK_PIPE eviction-activity package=$packageName launched=$launched overlayFront=$overlayFront targetFront=$targetStillForeground signalUnreliable=$foregroundSignalUnreliable",
                    )
                    repository.addEvent(
                        EventLogType.Safety,
                        "Strong block fallback launched for ${currentDecision.result.appName}; foreground still occupied by blocked app",
                    )
                }

                delay(BLOCK_FOREGROUND_EVICTION_INTERVAL_MILLIS)
            }
            if (blockForegroundEvictionPackageName == packageName) {
                blockForegroundEvictionPackageName = null
                blockForegroundEvictionJob = null
            }
        }
    }

    private fun isBlockedTargetStillForeground(
        decision: MonitorDecision,
        rawForegroundPackageName: String,
    ): Boolean {
        if (rawForegroundPackageName.isBlank() ||
            rawForegroundPackageName == applicationContext.packageName ||
            AppVisibility.isHiddenPackage(rawForegroundPackageName)
        ) {
            return false
        }
        return rawForegroundPackageName == decision.result.packageName ||
            decision.result.decision == BlockDecision.WouldBlockTotalLimit
    }

    private fun focusBlockingOverlayIfPresent() {
        blockingOverlayView?.let { overlayView ->
            overlayView.bringToFront()
            if (overlayView.findFocus() !is EditText) {
                overlayView.requestFocus()
            }
            overlayView.invalidate()
        }
    }

    private fun isBlockingOverlayEffectivelyFront(): Boolean {
        val overlayView = blockingOverlayView ?: return false
        val visibleRect = Rect()
        val visibleEnough = overlayView.getGlobalVisibleRect(visibleRect) &&
            visibleRect.width() >= resources.displayMetrics.widthPixels / 2 &&
            visibleRect.height() >= resources.displayMetrics.heightPixels / 2
        return overlayView.isAttachedToWindow &&
            overlayView.isShown &&
            overlayView.visibility == View.VISIBLE &&
            visibleEnough &&
            (overlayView.hasWindowFocus() || overlayView.hasFocus())
    }

    private fun isBlockingOverlayInputFocused(): Boolean {
        return blockingOverlayView?.findFocus() is EditText
    }

    private suspend fun openBlockedActivityOrOverlay(
        decision: MonitorDecision,
        keepExistingOverlay: Boolean = false,
        forceActivity: Boolean = false,
    ) {
        if (
            SystemClock.elapsedRealtime() < homeExitGraceUntilElapsed &&
            shouldHonorHomeExitGrace(decision.result.packageName)
        ) {
            debugMonitorState("open block skipped home grace package=${decision.result.packageName}")
            return
        }
        if (SystemClock.elapsedRealtime() < homeExitGraceUntilElapsed) {
            debugMonitor("home grace canceled by open block package=${decision.result.packageName}")
            homeExitGraceUntilElapsed = 0L
        }
        debugMonitor(
            "BLOCK_PIPE open-activity-request package=${decision.result.packageName} keepOverlay=$keepExistingOverlay force=$forceActivity",
        )
        val launched = withContext(Dispatchers.Main) {
            if (!keepExistingOverlay) {
                removeBlockingOverlay()
            }
            startBlockedActivity(decision, force = forceActivity)
        }
        debugMonitor("BLOCK_PIPE open-activity-result package=${decision.result.packageName} launched=$launched")
        if (!launched) {
            debugMonitor("blocked activity launch failed; keeping overlay package=${decision.result.packageName}")
            withContext(Dispatchers.Main) {
                showBlockingOverlay(decision)
            }
            return
        }

        delay(BLOCK_ACTIVITY_FRONT_CHECK_DELAY_MILLIS)
        val managerVisible = usageRepository.getRawCurrentForegroundPackageName() == applicationContext.packageName
        debugMonitor(
            "BLOCK_PIPE activity-front-check package=${decision.result.packageName} managerVisible=$managerVisible overlayAttached=${blockingOverlayView?.isAttachedToWindow == true}",
        )
        if (managerVisible || SystemClock.elapsedRealtime() < managerOpenGraceUntilElapsed) {
            debugMonitorState("blocked activity visible package=${decision.result.packageName}; overlay fallback not needed")
            withContext(Dispatchers.Main) {
                removeBlockingOverlay()
            }
            return
        }
        val overlayStillAttached = withContext(Dispatchers.Main) {
            blockingOverlayView?.isAttachedToWindow == true
        }
        if (overlayStillAttached) {
            debugMonitorState("blocked activity launch requested; overlay remains until activity resumes package=${decision.result.packageName}")
            return
        }
        if (
            SystemClock.elapsedRealtime() < homeExitGraceUntilElapsed &&
            shouldHonorHomeExitGrace(decision.result.packageName)
        ) {
            return
        }
        if (SystemClock.elapsedRealtime() < homeExitGraceUntilElapsed) {
            debugMonitor("home grace canceled by overlay fallback package=${decision.result.packageName}")
            homeExitGraceUntilElapsed = 0L
        }

        val currentDecision = evaluatePackage(decision.result.packageName)
        if (currentDecision.result.decision.isWouldBlock()) {
            debugMonitorState("blocked activity not visible; overlay fallback package=${decision.result.packageName}")
            withContext(Dispatchers.Main) {
                showBlockingOverlay(currentDecision)
            }
        }
    }

    private fun showBlockingOverlay(
        decision: MonitorDecision,
        forceRecreate: Boolean = false,
    ): Boolean {
        if (!forceRecreate && blockingOverlayPackageName == decision.result.packageName && blockingOverlayView?.isAttachedToWindow == true) {
            val existingView = blockingOverlayView
            focusBlockingOverlayIfPresent()
            debugMonitorState(
                "BLOCK_PIPE overlay-already package=${decision.result.packageName} attached=${existingView?.isAttachedToWindow} shown=${existingView?.isShown} focus=${existingView?.hasFocus()} windowFocus=${existingView?.hasWindowFocus()}",
            )
            return true
        }
        removeBlockingOverlay()

        val overlayState = overlayPermissionState()
        debugMonitor("BLOCK_PIPE overlay-attach-attempt package=${decision.result.packageName} ${overlayState.summary}")

        val overlayView = createBlockingOverlayView(decision)
        overlayView.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) {
                    debugMonitor(
                        "BLOCK_PIPE overlay-attached-callback package=${decision.result.packageName} shown=${view.isShown} focus=${view.hasFocus()} windowFocus=${view.hasWindowFocus()} visibility=${view.visibility}",
                    )
                }

                override fun onViewDetachedFromWindow(view: View) {
                    debugMonitor("BLOCK_PIPE overlay-detached-callback package=${decision.result.packageName}")
                }
            },
        )
        try {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.CENTER
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            getSystemService(WindowManager::class.java).addView(overlayView, params)
            overlayView.requestFocus()
            blockingOverlayView = overlayView
            blockingOverlayPackageName = decision.result.packageName
            debugMonitor(
                "BLOCK_PIPE overlay-addView-success package=${decision.result.packageName} attached=${overlayView.isAttachedToWindow} shown=${overlayView.isShown} focus=${overlayView.hasFocus()} windowFocus=${overlayView.hasWindowFocus()} ${overlayState.summary}",
            )
            overlayView.postDelayed(
                {
                    debugMonitor(
                        "BLOCK_PIPE overlay-after-300ms package=${decision.result.packageName} attached=${overlayView.isAttachedToWindow} shown=${overlayView.isShown} focus=${overlayView.hasFocus()} windowFocus=${overlayView.hasWindowFocus()} visibility=${overlayView.visibility}",
                    )
                },
                300L,
            )
            return true
        } catch (error: RuntimeException) {
            debugMonitor(
                "BLOCK_PIPE overlay-addView-failed package=${decision.result.packageName} error=${error.javaClass.simpleName}:${error.message.orEmpty()} ${overlayState.summary}",
            )
            updateMonitorNotification("ScreenRest", "Overlay unavailable; opening block controls")
            serviceScope.launch {
                repository.addEvent(
                    EventLogType.Safety,
                    "Overlay attach failed for strong blocking: ${error.javaClass.simpleName}, ${overlayState.summary}",
                )
            }
            blockingOverlayView = null
            blockingOverlayPackageName = null
            return false
        }
    }

    private fun createBlockingOverlayView(decision: MonitorDecision): View {
        val isDailyLimitBlock = decision.result.decision == BlockDecision.WouldBlockTotalLimit
        val strings = blockOverlayStrings()
        val style = decision.result.decision.overlayVisualStyle()
        val root = FrameLayout(this).apply {
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = true
            clipToPadding = true
            background = GradientDrawable().apply {
                setColor(style.backgroundColor)
            }
        }
        root.setOnKeyListener { _, keyCode, _ -> keyCode == KeyEvent.KEYCODE_BACK }

        val scrollView = ScrollView(this).apply {
            isFillViewport = false
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(24), dp(22), dp(22))
            background = GradientDrawable().apply {
                cornerRadius = dp(28).toFloat()
                setColor(style.cardColor)
                setStroke(dp(2), style.borderColor)
            }
        }
        root.addView(
            scrollView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER,
            ).apply {
                val horizontalMargin = dp(32)
                val verticalMargin = dp(18)
                leftMargin = horizontalMargin
                rightMargin = horizontalMargin
                topMargin = verticalMargin
                bottomMargin = verticalMargin
            },
        )
        root.post {
            val horizontalMargin = dp(32)
            val availableWidth = root.width - (horizontalMargin * 2)
            if (availableWidth > 0) {
                val minimumWidth = dp(280).coerceAtMost(availableWidth)
                val targetWidth = availableWidth
                    .coerceAtMost(dp(480))
                    .coerceAtLeast(minimumWidth)
                (scrollView.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                    params.width = targetWidth
                    params.leftMargin = horizontalMargin
                    params.rightMargin = horizontalMargin
                    params.gravity = Gravity.CENTER
                    scrollView.layoutParams = params
                }
            }
        }
        scrollView.addView(
            card,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )

        card.addView(blockOverlayText(strings.titleFor(decision.result.decision), 24, Color.rgb(17, 24, 39), true))
        if (isDailyLimitBlock) {
            card.addView(blockOverlayText(strings.dailyTime, 20, Color.rgb(17, 24, 39), true))
        } else {
            val iconDrawable = runCatching {
                packageManager.getApplicationIcon(decision.result.packageName)
            }.getOrNull()
            if (iconDrawable != null) {
                card.addView(
                    ImageView(this).apply {
                        setImageDrawable(iconDrawable)
                        contentDescription = decision.result.appName
                    },
                    LinearLayout.LayoutParams(dp(72), dp(72)).apply {
                        topMargin = dp(12)
                        bottomMargin = dp(8)
                    },
                )
            }
            card.addView(blockOverlayText(decision.result.appName, 20, Color.rgb(17, 24, 39), true))
        }
        card.addView(
            blockOverlayText(
                decision.toOverlayUsageText(),
                16,
                Color.rgb(107, 114, 128),
                false,
            ),
        )

        val adminInput = blockOverlayPinInput(strings.adminPin, scrollView, strings)
        card.addView(
            adminInput.container,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52),
            ).apply {
                topMargin = dp(18)
            },
        )

        var extraMinutes = 5
        val extraMinutesText = blockOverlayText(strings.extraTimeLabel(extraMinutes), 15, style.accentColor, true)
        card.addView(extraMinutesText)
        card.addView(
            SeekBar(this).apply {
                max = 239
                progress = extraMinutes - 1
                setOnSeekBarChangeListener(
                    object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                            extraMinutes = progress + 1
                            extraMinutesText.text = strings.extraTimeLabel(extraMinutes)
                        }

                        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

                        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
                    },
                )
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        val statusText = blockOverlayText("", 14, Color.rgb(229, 91, 74), true)
        card.addView(statusText)
        card.addView(blockOverlayButton(strings.addTime) {
            val pin = adminInput.input.text?.toString().orEmpty()
            applyParentExtraTime(
                decision = decision,
                pin = pin,
                extraMinutes = extraMinutes,
                statusText = statusText,
                strings = strings,
            )
        })
        card.addView(blockOverlayButton(strings.unlockToday) {
            val pin = adminInput.input.text?.toString().orEmpty()
            applyParentUnlockToday(
                decision = decision,
                pin = pin,
                statusText = statusText,
                strings = strings,
            )
        })

        val emergencyInput = blockOverlayPinInput(strings.emergencyPin, scrollView, strings)
        card.addView(
            emergencyInput.container,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52),
            ).apply {
                topMargin = dp(10)
            },
        )

        card.addView(blockOverlayButton(strings.emergencyUnlock) {
            val pin = emergencyInput.input.text?.toString().orEmpty()
            serviceScope.launch {
                val unlocked = repository.emergencyUnlock(pin)
                withContext(Dispatchers.Main) {
                    if (unlocked) {
                        cancelBlockEnforcementGuard()
                        clearActiveForegroundSession()
                        lastBlockedActivityPackageName = null
                        removeBlockingOverlay()
                    } else {
                        statusText.text = strings.invalidEmergencyPin
                    }
                }
            }
        })
        card.addView(blockOverlayButton(strings.openManager) {
            val now = SystemClock.elapsedRealtime()
            startBlockExitTransitionGrace(decision.result.packageName)
            managerOpenGraceUntilElapsed = now + MANAGER_OPEN_GRACE_MILLIS
            homeExitGraceUntilElapsed = now + HOME_EXIT_GRACE_MILLIS
            cancelBlockEnforcementGuard()
            clearActiveForegroundSession()
            lastBlockedActivityPackageName = null
            removeBlockingOverlay()
            openMainActivity()
        })
        card.addView(blockOverlayButton(strings.home) {
            allowHomeExitFromBlockedScreen(decision.result.packageName)
            sendHomeIntent(force = true)
        })
        return root
    }

    private fun applyParentExtraTime(
        decision: MonitorDecision,
        pin: String,
        extraMinutes: Int,
        statusText: TextView,
        strings: BlockOverlayStrings,
    ) {
        if (pin.isBlank() || extraMinutes <= 0) {
            statusText.text = strings.adminPinRequired
            return
        }
        serviceScope.launch {
            val granted = if (decision.result.decision == BlockDecision.WouldBlockTotalLimit) {
                repository.addTemporaryTotalTime(
                    extraMinutes = extraMinutes,
                    adminPin = pin,
                )
            } else {
                repository.addTemporaryAppTime(
                    packageName = decision.result.packageName,
                    appName = decision.result.appName,
                    extraMinutes = extraMinutes,
                    adminPin = pin,
                )
            }
            withContext(Dispatchers.Main) {
                if (granted) {
                    statusText.setTextColor(Color.rgb(22, 101, 52))
                    statusText.text = strings.addedTime(extraMinutes)
                    cancelBlockEnforcementGuard()
                    removeBlockingOverlay()
                } else {
                    statusText.setTextColor(Color.rgb(229, 91, 74))
                    statusText.text = strings.invalidAdminPin
                }
            }
        }
    }

    private fun applyParentUnlockToday(
        decision: MonitorDecision,
        pin: String,
        statusText: TextView,
        strings: BlockOverlayStrings,
    ) {
        if (pin.isBlank()) {
            statusText.text = strings.adminPinRequired
            return
        }
        serviceScope.launch {
            val granted = if (decision.result.decision == BlockDecision.WouldBlockTotalLimit) {
                repository.unlockTotalForToday(adminPin = pin)
            } else {
                repository.unlockAppForToday(
                    packageName = decision.result.packageName,
                    appName = decision.result.appName,
                    adminPin = pin,
                )
            }
            withContext(Dispatchers.Main) {
                if (granted) {
                    statusText.setTextColor(Color.rgb(22, 101, 52))
                    statusText.text = strings.unlockedForToday
                    cancelBlockEnforcementGuard()
                    removeBlockingOverlay()
                } else {
                    statusText.setTextColor(Color.rgb(229, 91, 74))
                    statusText.text = strings.invalidAdminPin
                }
            }
        }
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

    private fun allowHomeExitFromBlockedScreen(packageName: String? = null) {
        startBlockExitTransitionGrace(packageName ?: blockingOverlayPackageName ?: lastBlockedActivityPackageName)
        homeExitGraceUntilElapsed = SystemClock.elapsedRealtime() + HOME_EXIT_GRACE_MILLIS
        cancelBlockEnforcementGuard()
        clearActiveForegroundSession()
        removeBlockingOverlay()
        lastBlockedActivityPackageName = null
        updateMonitorNotification("ScreenRest", "Home is available")
    }

    private fun startBlockedActivity(decision: MonitorDecision, force: Boolean = false): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (
            !force &&
            lastBlockedActivityPackageName == decision.result.packageName &&
            now - lastBlockedActivityStartedAt < BLOCK_ACTIVITY_LAUNCH_COOLDOWN_MILLIS
        ) {
            debugMonitorState("blocked activity launch throttled package=${decision.result.packageName}")
            return true
        }
        return try {
            debugMonitor("blocked activity launch package=${decision.result.packageName} force=$force")
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
            lastBlockedActivityPackageName = decision.result.packageName
            lastBlockedActivityStartedAt = now
            true
        } catch (error: RuntimeException) {
            debugMonitor("blocked activity launch error package=${decision.result.packageName} error=${error.javaClass.simpleName}")
            serviceScope.launch {
                repository.addEvent(EventLogType.Safety, "Foreground monitor could not open blocked activity")
            }
            false
        }
    }

    private fun sendHomeIntentThrottled() {
        sendHomeIntent(force = false)
    }

    private fun sendHomeIntent(force: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastHomeSentAt < HOME_THROTTLE_MILLIS) {
            debugMonitorState("BLOCK_PIPE home-throttled since=${now - lastHomeSentAt}ms")
            return
        }
        lastHomeSentAt = now
        debugMonitor("BLOCK_PIPE home-intent-send force=$force")
        try {
            startActivity(
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                },
            )
            debugMonitor("BLOCK_PIPE home-intent-sent force=$force")
        } catch (error: RuntimeException) {
            debugMonitor("BLOCK_PIPE home-intent-failed error=${error.javaClass.simpleName}:${error.message.orEmpty()}")
            serviceScope.launch {
                repository.addEvent(EventLogType.Safety, "Foreground monitor home intent failed")
            }
        }
    }

    private fun pauseActiveMediaPlayback() {
        runCatching {
            val listenerComponent = ComponentName(this, ScreenTimeNotificationListenerService::class.java)
            val mediaSessionManager = getSystemService(MediaSessionManager::class.java)
            mediaSessionManager.getActiveSessions(listenerComponent).forEach { controller ->
                controller.transportControls.pause()
                controller.transportControls.stop()
            }
        }
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                ?: return
            audioManager.dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
            audioManager.dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_STOP)
        } catch (_: RuntimeException) {
            // Blocking must continue even if media ignores pause.
        }
    }

    private fun isDeviceInteractive(): Boolean {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            powerManager.isInteractive
        } else {
            @Suppress("DEPRECATION")
            powerManager.isScreenOn
        }
    }

    private fun remainingGraceMillis(untilElapsed: Long): Long {
        if (untilElapsed <= 0L) {
            return 0L
        }
        return (untilElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
    }

    private fun localDayStartMillis(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun AudioManager.dispatchMediaKey(keyCode: Int) {
        val downEvent = KeyEvent(
            SystemClock.uptimeMillis(),
            SystemClock.uptimeMillis(),
            KeyEvent.ACTION_DOWN,
            keyCode,
            0,
        )
        val upEvent = KeyEvent.changeAction(downEvent, KeyEvent.ACTION_UP)
        dispatchMediaKeyEvent(downEvent)
        dispatchMediaKeyEvent(upEvent)
    }

    private fun openMainActivity() {
        try {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    putExtra(MainActivity.EXTRA_SUPPRESS_PERMISSION_SETUP_AUTO_DIALOG, true)
                },
            )
        } catch (_: RuntimeException) {
            // Overlay remains available even if opening the manager fails.
        }
    }

    private fun updateMonitorNotification(title: String, text: String) {
        debugMonitorState("NOTIFICATION update title=$title text=$text")
        val notification = buildMonitorNotification(title = title, text = text)
        runCatching {
            NotificationManagerCompat.from(this).notify(MONITOR_NOTIFICATION_ID, notification)
            debugMonitorState("NOTIFICATION posted id=$MONITOR_NOTIFICATION_ID title=$title")
        }.onFailure { error ->
            debugMonitor("NOTIFICATION failed error=${error.javaClass.simpleName}:${error.message.orEmpty()}")
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
            .setSmallIcon(R.mipmap.notification_icon)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_NONE)
            .setContentIntent(contentIntent)
            .build()
    }

    private fun ensureMonitorChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val channel = NotificationChannel(
            MONITOR_CHANNEL_ID,
            "ScreenRest Monitor",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun overlayPermissionState() = OverlayPermissionChecker.state(this)

    private fun UsagePolicySettings.hasDirectLimitFor(packageName: String): Boolean {
        val hasAppLimit = (appLimitMap()[packageName] ?: 0) > 0
        val hasGroupLimit = normalizedAppGroups()
            .any { group -> packageName in group.packageNames && group.budgetMinutes > 0 }
        return hasAppLimit || hasGroupLimit
    }

    private fun MonitorDecision.toNotificationTitle(): String {
        return when {
            result.decision == BlockDecision.WouldBlockTotalLimit -> "Daily Time"
            result.decision == BlockDecision.WouldBlockSchedule -> result.appName
            result.decision == BlockDecision.WouldBlockAllowOnly -> result.appName
            shouldShowDetailedNotification() -> result.appName
            else -> "ScreenRest"
        }
    }

    private fun MonitorDecision.toNotificationText(): String {
        if (!shouldShowDetailedNotification()) {
            return "Monitoring limited apps"
        }
        if (result.decision == BlockDecision.WouldBlockSchedule) {
            return "Schedule block active"
        }
        if (result.decision == BlockDecision.WouldBlockAllowOnly) {
            return "Allow-only mode active"
        }
        val groupLimitText = groupLimitTrackingText()
        val groupOnlyLimit = result.decision != BlockDecision.WouldBlockTotalLimit &&
            appLimitMillis == null &&
            groupLimitText != null
        val primaryUsedMillis = if (groupOnlyLimit) targetGroupUsedMillis ?: usedMillis else usedMillis
        val primaryLimitMillis = if (groupOnlyLimit) targetGroupLimitMillis else limitMillis
        val primaryPrefix = if (groupOnlyLimit) {
            targetGroupName?.takeIf { name -> name.isNotBlank() }?.let { name -> "$name: " }.orEmpty()
        } else {
            ""
        }
        val limitText = primaryLimitMillis?.let { limit -> " / ${formatSeconds(limit)}" }.orEmpty()
        val extraText = extraMinutes
            .takeIf { minutes -> minutes > 0 }
            ?.let { minutes -> " (+${formatLimitMinutesLabel(minutes)})" }
            .orEmpty()
        val unlockedText = if (unlockedForToday) {
            " - unlocked today"
        } else {
            ""
        }
        val groupText = if (!groupOnlyLimit && result.decision != BlockDecision.WouldBlockTotalLimit) {
            groupLimitText?.let { text -> " 쨌 $text" }.orEmpty()
        } else {
            ""
        }
        return "$primaryPrefix${formatSeconds(primaryUsedMillis)}$limitText$extraText$unlockedText$groupText"
    }

    private fun MonitorDecision.groupLimitTrackingText(): String? {
        val groupName = targetGroupName?.takeIf { name -> name.isNotBlank() } ?: return null
        val groupUsedMillis = targetGroupUsedMillis ?: return null
        val groupLimitMillis = targetGroupLimitMillis ?: return null
        return "$groupName ${formatSeconds(groupUsedMillis)} / ${formatSeconds(groupLimitMillis)}"
    }

    private fun MonitorDecision.shouldShowDetailedNotification(): Boolean {
        return directLimit || result.decision.isWouldBlock()
    }

    private fun MonitorDecision.toOverlayUsageText(): String {
        val limitText = result.limitMinutes
            ?.let { limitMinutes -> " / ${formatLimitMinutesLabel(limitMinutes)}" }
            .orEmpty()
        return "${formatLimitMinutesLabel(result.usedMinutes)}$limitText"
    }

    private fun BlockDecision.isWouldBlock(): Boolean {
        return this == BlockDecision.WouldBlockTotalLimit ||
            this == BlockDecision.WouldBlockSchedule ||
            this == BlockDecision.WouldBlockAllowOnly ||
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
            BlockDecision.WouldBlockSchedule -> "schedule block active"
            BlockDecision.WouldBlockAllowOnly -> "allow-only mode active"
            BlockDecision.WouldBlockGroupLimit -> "group limit exceeded"
            BlockDecision.WouldBlockAppLimit -> "app limit exceeded"
        }
    }

    private fun BlockDecision.toBlockCategory(): String {
        return when (this) {
            BlockDecision.WouldBlockAppLimit -> "APP_LIMIT"
            BlockDecision.WouldBlockGroupLimit -> "GROUP_LIMIT"
            BlockDecision.WouldBlockTotalLimit -> "DAILY_LIMIT"
            BlockDecision.WouldBlockSchedule -> "SCHEDULE"
            BlockDecision.WouldBlockAllowOnly -> "ALLOW_ONLY"
            BlockDecision.AllowedSafeMode -> "SAFE_MODE"
            BlockDecision.AllowedPolicyDisabled -> "POLICY_OFF"
            BlockDecision.AllowedWhitelist -> "WHITELIST"
            BlockDecision.AllowedNoLimit -> "NO_LIMIT"
            BlockDecision.AllowedUnderLimit -> "UNDER_LIMIT"
        }
    }

    private fun BlockDecision.toKoreanBlockReason(): String {
        return when (this) {
            BlockDecision.WouldBlockAppLimit -> "?깅퀎 ?쒗븳 珥덇낵"
            BlockDecision.WouldBlockGroupLimit -> "洹몃９ ?쒗븳 珥덇낵"
            BlockDecision.WouldBlockTotalLimit -> "?쇱씪 ?쒗븳 珥덇낵"
            BlockDecision.WouldBlockSchedule -> "?ㅼ?以?李⑤떒"
            BlockDecision.WouldBlockAllowOnly -> "?덉슜??紐⑤뱶 李⑤떒"
            BlockDecision.AllowedSafeMode -> "?덉쟾 紐⑤뱶"
            BlockDecision.AllowedPolicyDisabled -> "?뺤콉 鍮꾪솢?깊솕"
            BlockDecision.AllowedWhitelist -> "?꾩닔 ?덉쇅"
            BlockDecision.AllowedNoLimit -> "?쒗븳 ?놁쓬"
            BlockDecision.AllowedUnderLimit -> "?쒗븳 誘몃쭔"
        }
    }

    private fun BlockDecision.overlayVisualStyle(): BlockOverlayVisualStyle {
        return when (this) {
            BlockDecision.WouldBlockTotalLimit -> BlockOverlayVisualStyle(
                backgroundColor = Color.rgb(58, 17, 20),
                cardColor = Color.rgb(255, 230, 226),
                accentColor = Color.rgb(226, 58, 46),
                borderColor = Color.rgb(153, 27, 27),
            )
            BlockDecision.WouldBlockGroupLimit -> BlockOverlayVisualStyle(
                backgroundColor = Color.rgb(42, 26, 5),
                cardColor = Color.rgb(255, 244, 216),
                accentColor = Color.rgb(245, 158, 11),
                borderColor = Color.rgb(180, 83, 9),
            )
            BlockDecision.WouldBlockSchedule -> BlockOverlayVisualStyle(
                backgroundColor = Color.rgb(30, 18, 53),
                cardColor = Color.rgb(240, 231, 255),
                accentColor = Color.rgb(124, 58, 237),
                borderColor = Color.rgb(91, 33, 182),
            )
            BlockDecision.WouldBlockAllowOnly -> BlockOverlayVisualStyle(
                backgroundColor = Color.rgb(5, 46, 43),
                cardColor = Color.rgb(225, 251, 244),
                accentColor = Color.rgb(15, 118, 110),
                borderColor = Color.rgb(15, 118, 110),
            )
            BlockDecision.WouldBlockAppLimit,
            BlockDecision.AllowedSafeMode,
            BlockDecision.AllowedPolicyDisabled,
            BlockDecision.AllowedWhitelist,
            BlockDecision.AllowedNoLimit,
            BlockDecision.AllowedUnderLimit -> BlockOverlayVisualStyle(
                backgroundColor = Color.rgb(17, 24, 39),
                cardColor = Color.rgb(234, 241, 255),
                accentColor = Color.rgb(29, 78, 216),
                borderColor = Color.rgb(30, 58, 138),
            )
        }
    }

    private fun blockOverlayStrings(): BlockOverlayStrings {
        val languageCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            resources.configuration.locales.get(0)?.language
        } else {
            @Suppress("DEPRECATION")
            val locale = resources.configuration.locale
            locale?.language
        }.orEmpty()
        return when {
            languageCode.equals("ko", ignoreCase = true) -> BlockOverlayStrings(
                dailyTitle = "하루 사용 시간이 종료되었습니다",
                appTitle = "이 앱의 사용 시간이 종료되었습니다",
                scheduleTitle = "스케줄 차단 시간이 활성화되었습니다",
                allowOnlyTitle = "허용된 앱만 사용할 수 있습니다",
                groupTitle = "앱 그룹 시간이 종료되었습니다",
                fallbackTitle = "사용 시간이 종료되었습니다",
                dailyTime = "하루 사용 시간",
                adminPin = "관리 PIN",
                showPin = "보기",
                hidePin = "숨기기",
                extraTime = "추가 시간",
                addTime = "시간 추가",
                unlockToday = "오늘만 해제",
                emergencyPin = "긴급 PIN",
                emergencyUnlock = "긴급 해제",
                openManager = "관리 앱 열기",
                home = "홈으로",
                adminPinRequired = "관리 PIN을 입력하세요",
                invalidAdminPin = "관리 PIN이 올바르지 않습니다",
                invalidEmergencyPin = "긴급 PIN이 올바르지 않습니다",
                addedPrefix = "추가됨",
                unlockedForToday = "오늘만 해제되었습니다",
            )

            else -> BlockOverlayStrings(
                dailyTitle = "Today's screen time is over",
                appTitle = "This app's time is over",
                scheduleTitle = "Schedule block is active",
                allowOnlyTitle = "Only allowed apps can be used",
                groupTitle = "Group time is over",
                fallbackTitle = "Time is over",
                dailyTime = "Daily Time",
                adminPin = "Admin PIN",
                showPin = "Show",
                hidePin = "Hide",
                extraTime = "Extra time",
                addTime = "Add time",
                unlockToday = "Unlock for today",
                emergencyPin = "Emergency PIN",
                emergencyUnlock = "Emergency Unlock",
                openManager = "Open Manager",
                home = "Home",
                adminPinRequired = "Admin PIN is required",
                invalidAdminPin = "Admin PIN is incorrect",
                invalidEmergencyPin = "Emergency PIN is incorrect",
                addedPrefix = "Added",
                unlockedForToday = "Unlocked for today",
            )
        }
    }

    private fun BlockOverlayStrings.titleFor(decision: BlockDecision): String {
        return when (decision) {
            BlockDecision.WouldBlockTotalLimit -> dailyTitle
            BlockDecision.WouldBlockSchedule -> scheduleTitle
            BlockDecision.WouldBlockAllowOnly -> allowOnlyTitle
            BlockDecision.WouldBlockGroupLimit -> groupTitle
            BlockDecision.WouldBlockAppLimit -> appTitle
            else -> fallbackTitle
        }
    }

    private fun BlockOverlayStrings.extraTimeLabel(extraMinutes: Int): String {
        return "$extraTime ${formatLimitMinutesLabel(extraMinutes)}"
    }

    private fun BlockOverlayStrings.addedTime(extraMinutes: Int): String {
        return "$addedPrefix ${formatLimitMinutesLabel(extraMinutes)}"
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

    private fun blockOverlayButton(
        text: String,
        primary: Boolean = false,
        outline: Boolean = false,
        onClick: () -> Unit,
    ): Button {
        return Button(this).apply {
            this.text = text
            textSize = 16f
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (primary || outline) Color.rgb(37, 99, 235) else Color.rgb(17, 24, 39))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(if (primary) Color.rgb(37, 99, 235) else Color.rgb(229, 231, 235))
                if (outline) {
                    setColor(Color.TRANSPARENT)
                    setStroke(dp(1), Color.rgb(107, 114, 128))
                }
            }
            if (primary) {
                setTextColor(Color.WHITE)
            }
            minHeight = dp(48)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48),
            ).apply {
                topMargin = dp(8)
            }
            setOnClickListener { onClick() }
        }
    }

    private fun blockOverlayPinInput(
        hintText: String,
        scrollView: ScrollView,
        strings: BlockOverlayStrings,
    ): BlockOverlayPinInput {
        var pinVisible = false
        val input = EditText(this).apply {
            hint = hintText
            val pinInputType = InputType.TYPE_CLASS_NUMBER
            inputType = pinInputType
            setRawInputType(pinInputType)
            keyListener = DigitsKeyListener.getInstance("0123456789")
            transformationMethod = PasswordTransformationMethod.getInstance()
            imeOptions = EditorInfo.IME_ACTION_DONE
            setSingleLine(true)
            textSize = 16f
            isFocusable = true
            isFocusableInTouchMode = true
            background = null
            setPadding(dp(14), 0, dp(8), 0)
            setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    revealOverlayInput(scrollView, view)
                    showOverlayKeyboard(view)
                }
            }
            setOnClickListener { view ->
                view.requestFocus()
                revealOverlayInput(scrollView, view)
                showOverlayKeyboard(view)
            }
        }
        val toggleButton = TextView(this).apply {
            text = strings.showPin
            gravity = Gravity.CENTER
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(37, 99, 235))
            isClickable = true
            isFocusable = false
            background = GradientDrawable().apply {
                cornerRadius = dp(999).toFloat()
                setColor(Color.rgb(239, 246, 255))
                setStroke(dp(1), Color.rgb(191, 219, 254))
            }
        }
        fun updatePinVisibility() {
            input.transformationMethod = if (pinVisible) {
                null
            } else {
                PasswordTransformationMethod.getInstance()
            }
            toggleButton.text = if (pinVisible) strings.hidePin else strings.showPin
            input.setSelection(input.text?.length ?: 0)
        }
        toggleButton.setOnClickListener {
            pinVisible = !pinVisible
            updatePinVisibility()
            input.requestFocus()
            showOverlayKeyboard(input)
        }
        updatePinVisibility()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.WHITE)
                setStroke(dp(1), Color.rgb(209, 213, 219))
            }
            setPadding(0, 0, dp(8), 0)
            addView(
                input,
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1f,
                ),
            )
            addView(
                toggleButton,
                LinearLayout.LayoutParams(
                    dp(62),
                    dp(36),
                ),
            )
        }
        return BlockOverlayPinInput(container = container, input = input)
    }

    private fun revealOverlayInput(scrollView: ScrollView, inputView: View) {
        scrollView.postDelayed(
            {
                val targetScrollY = (inputView.top - dp(120)).coerceAtLeast(0)
                scrollView.smoothScrollTo(0, targetScrollY)
            },
            180L,
        )
    }

    private fun showOverlayKeyboard(view: View) {
        view.postDelayed(
            {
                val inputMethodManager =
                    getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                inputMethodManager?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
            },
            80L,
        )
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

    private fun debugMonitor(message: String) {
        Log.d(FOREGROUND_DEBUG_TAG, "Monitor $message")
    }

    private fun debugMonitorState(message: String) {
        val now = SystemClock.elapsedRealtime()
        if (message == lastDebugMonitorSummary && now - lastDebugMonitorAt < DEBUG_LOG_REPEAT_THROTTLE_MILLIS) {
            return
        }
        lastDebugMonitorSummary = message
        lastDebugMonitorAt = now
        debugMonitor(message)
    }

    private data class MonitorDecision(
        val result: BlockDecisionResult,
        val usedMillis: Long,
        val limitMillis: Long?,
        val directLimit: Boolean,
        val extraMinutes: Int,
        val unlockedForToday: Boolean,
        val appUsedMillis: Long,
        val appLimitMillis: Long?,
        val targetGroupName: String?,
        val targetGroupUsedMillis: Long?,
        val targetGroupLimitMillis: Long?,
    )

    private data class BlockOverlayPinInput(
        val container: View,
        val input: EditText,
    )

    private data class BlockOverlayVisualStyle(
        val backgroundColor: Int,
        val cardColor: Int,
        val accentColor: Int,
        val borderColor: Int,
    )

    private data class BlockOverlayStrings(
        val dailyTitle: String,
        val appTitle: String,
        val scheduleTitle: String,
        val allowOnlyTitle: String,
        val groupTitle: String,
        val fallbackTitle: String,
        val dailyTime: String,
        val adminPin: String,
        val showPin: String,
        val hidePin: String,
        val extraTime: String,
        val addTime: String,
        val unlockToday: String,
        val emergencyPin: String,
        val emergencyUnlock: String,
        val openManager: String,
        val home: String,
        val adminPinRequired: String,
        val invalidAdminPin: String,
        val invalidEmergencyPin: String,
        val addedPrefix: String,
        val unlockedForToday: String,
    )

    companion object {
        private const val ACTION_STOP = "com.manisykh.screenrest.action.STOP_USAGE_MONITOR"
        private const val ACTION_ALLOW_HOME_EXIT = "com.manisykh.screenrest.action.ALLOW_HOME_EXIT"
        private const val ACTION_BLOCKED_ACTIVITY_VISIBLE =
            "com.manisykh.screenrest.action.BLOCKED_ACTIVITY_VISIBLE"
        private const val ACTION_BLOCKED_SCREEN_VISIBLE =
            "com.manisykh.screenrest.action.BLOCKED_SCREEN_VISIBLE"
        private const val ACTION_MANAGER_VISIBLE =
            "com.manisykh.screenrest.action.MANAGER_VISIBLE"
        private const val ACTION_EVALUATE_FOREGROUND_PACKAGE =
            "com.manisykh.screenrest.action.EVALUATE_FOREGROUND_PACKAGE"
        const val ACTION_EXACT_RECOVERY_ALARM =
            "com.manisykh.screenrest.action.EXACT_RECOVERY_ALARM"
        private const val EXTRA_PACKAGE_NAME = "com.manisykh.screenrest.extra.PACKAGE_NAME"
        const val EXTRA_RECOVERY_REASON = "com.manisykh.screenrest.extra.RECOVERY_REASON"
        private const val MONITOR_CHANNEL_ID = "usage_monitor"
        private const val MONITOR_NOTIFICATION_ID = 4301
        private const val EXACT_RECOVERY_ALARM_REQUEST_CODE = 4302
        private const val MONITOR_INTERVAL_MILLIS = 250L
        private const val ACTIVE_FOREGROUND_BLANK_CLEAR_MILLIS = 2_000L
        private const val HOME_THROTTLE_MILLIS = 2_000L
        private const val BLOCK_LOG_THROTTLE_MILLIS = 30_000L
        private const val BLOCK_PIPELINE_LOG_THROTTLE_MILLIS = 4_000L
        private const val MANAGER_OPEN_GRACE_MILLIS = 6_000L
        private const val HOME_EXIT_GRACE_MILLIS = 3_500L
        private const val BLOCK_EXIT_TRANSITION_GRACE_MILLIS = 4_000L
        private const val BLOCK_ENFORCEMENT_GUARD_INTERVAL_MILLIS = 650L
        private const val BLOCK_REASSERT_INTERVAL_MILLIS = 1_500L
        private const val BLOCK_FOREGROUND_EVICTION_INTERVAL_MILLIS = 220L
        private const val BLOCK_FOREGROUND_EVICTION_WINDOW_MILLIS = 4_800L
        private const val BLOCK_ACTIVITY_ESCALATION_DELAY_MILLIS = 900L
        private const val BLOCK_ACTIVITY_ESCALATION_REPEAT_MILLIS = 900L
        private const val BLOCK_ACTIVITY_LAUNCH_COOLDOWN_MILLIS = 2_500L
        private const val BLOCK_ACTIVITY_SESSION_MILLIS = 8_000L
        private const val BLOCK_ACTIVITY_FRONT_CHECK_DELAY_MILLIS = 1_000L
        private const val DEBUG_LOG_REPEAT_THROTTLE_MILLIS = 1_000L
        private const val DEBUG_USAGE_ADJUST_LOG_THRESHOLD_MILLIS = 1_000L
        private const val MONITOR_STATUS_WRITE_INTERVAL_MILLIS = 1_000L
        private const val USAGE_MAP_CACHE_MILLIS = 1_000L
        private const val FOREGROUND_DEBUG_TAG = "STM-Foreground"

        fun start(context: Context) {
            val intent = Intent(context, UsageMonitorForegroundService::class.java)
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, UsageMonitorForegroundService::class.java))
            }
        }

        fun allowHomeExit(context: Context, packageName: String? = null) {
            val intent = Intent(context, UsageMonitorForegroundService::class.java)
                .setAction(ACTION_ALLOW_HOME_EXIT)
                .putExtra(EXTRA_PACKAGE_NAME, packageName.orEmpty())
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun blockedActivityVisible(context: Context, packageName: String? = null) {
            val intent = Intent(context, UsageMonitorForegroundService::class.java)
                .setAction(ACTION_BLOCKED_ACTIVITY_VISIBLE)
                .putExtra(EXTRA_PACKAGE_NAME, packageName.orEmpty())
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun blockedScreenVisible(context: Context) {
            val intent = Intent(context, UsageMonitorForegroundService::class.java)
                .setAction(ACTION_BLOCKED_SCREEN_VISIBLE)
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun managerVisible(context: Context, packageName: String? = null) {
            val intent = Intent(context, UsageMonitorForegroundService::class.java)
                .setAction(ACTION_MANAGER_VISIBLE)
                .putExtra(EXTRA_PACKAGE_NAME, packageName.orEmpty())
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun evaluateForegroundPackage(context: Context, packageName: String) {
            if (packageName.isBlank()) {
                return
            }
            val intent = Intent(context, UsageMonitorForegroundService::class.java)
                .setAction(ACTION_EVALUATE_FOREGROUND_PACKAGE)
                .putExtra(EXTRA_PACKAGE_NAME, packageName)
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun scheduleExactRecoveryAlarm(
            context: Context,
            reason: String,
            delayMillis: Long = 60_000L,
        ) {
            val appContext = context.applicationContext
            val alarmManager = appContext.getSystemService(AlarmManager::class.java)
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !alarmManager.canScheduleExactAlarms()
            ) {
                return
            }
            val pendingIntent = PendingIntent.getBroadcast(
                appContext,
                EXACT_RECOVERY_ALARM_REQUEST_CODE,
                Intent(appContext, BootRecoveryReceiver::class.java).apply {
                    action = ACTION_EXACT_RECOVERY_ALARM
                    putExtra(EXTRA_RECOVERY_REASON, reason)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val triggerAt = SystemClock.elapsedRealtime() + delayMillis.coerceAtLeast(1_000L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pendingIntent,
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pendingIntent,
                )
            }
        }
    }
}
