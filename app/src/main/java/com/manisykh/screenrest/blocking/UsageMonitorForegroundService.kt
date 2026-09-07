package com.manisykh.screenrest.blocking

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
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
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.manisykh.screenrest.MainActivity
import com.manisykh.screenrest.R
import com.manisykh.screenrest.data.EventLogType
import com.manisykh.screenrest.data.AppLanguage
import com.manisykh.screenrest.data.HardshipLevel
import com.manisykh.screenrest.data.HardshipLifecycleResult
import com.manisykh.screenrest.data.MAX_TEMPORARY_EXTRA_MINUTES
import com.manisykh.screenrest.data.HardshipPolicyKey
import com.manisykh.screenrest.data.HardshipPolicyType
import com.manisykh.screenrest.data.HardshipLevelOneGrantResult
import com.manisykh.screenrest.data.HardshipLevelTwoUnlockResult
import com.manisykh.screenrest.data.EmergencyPassUseResult
import com.manisykh.screenrest.data.hardshipLevelFor
import com.manisykh.screenrest.data.canRequestParentApproval
import com.manisykh.screenrest.data.ParentDeviceRole
import com.manisykh.screenrest.data.ParentRemoteSyncDataSourceFactory
import com.manisykh.screenrest.data.SettingsRepository
import com.manisykh.screenrest.data.ScheduleHardshipStarted
import com.manisykh.screenrest.data.UsagePolicySettings
import com.manisykh.screenrest.data.activeScheduleAllowedPackages
import com.manisykh.screenrest.data.appliesOn
import com.manisykh.screenrest.data.currentPolicyDayOfWeek
import com.manisykh.screenrest.data.emergencyPassExpiryFor
import com.manisykh.screenrest.data.isScheduleBlockingNow
import com.manisykh.screenrest.data.limitMinutesOrNull
import com.manisykh.screenrest.data.levelOneReflectionEntry
import com.manisykh.screenrest.data.normalizedAppGroups
import com.manisykh.screenrest.data.normalizedScheduleTemplates
import com.manisykh.screenrest.data.RemoteRequestBlockReason
import com.manisykh.screenrest.data.RemoteUnlockRequestSubmitResult
import com.manisykh.screenrest.data.RemoteUnlockRequestStatus
import com.manisykh.screenrest.data.settingsDataStore
import com.manisykh.screenrest.formatLimitMinutesLabel
import com.manisykh.screenrest.notification.UsageNotificationHelper
import com.manisykh.screenrest.safety.BlockDecision
import com.manisykh.screenrest.safety.BlockDecisionEngine
import com.manisykh.screenrest.safety.BlockDecisionResult
import com.manisykh.screenrest.safety.AndroidSystemInteractionResolver
import com.manisykh.screenrest.safety.OverlayPermissionChecker
import com.manisykh.screenrest.safety.SafetyGate
import com.manisykh.screenrest.ui.safety.activeAppLimitMap
import com.manisykh.screenrest.ui.safety.todayLimitMinutesOrNull
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UsageMonitorForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val repository by lazy {
        SettingsRepository(
            applicationContext.settingsDataStore,
            ParentRemoteSyncDataSourceFactory.create(applicationContext),
        )
    }
    private val usageRepository by lazy { UsageStatsRepository(applicationContext) }
    private val systemInteractionResolver by lazy { AndroidSystemInteractionResolver(applicationContext) }
    private val usageNotificationHelper by lazy { UsageNotificationHelper(applicationContext) }
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
    private var lastRemoteParentSyncAt: Long = 0L
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
    private var remoteParentRequestSubmitJob: Job? = null
    private var remoteParentRequestMonitorJob: Job? = null
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
    private var lastHardshipLifecycleCheckAtElapsed: Long = 0L

    override fun onCreate() {
        super.onCreate()
        systemInteractionResolver.refreshDetectedRelationships()
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
        scheduleRecoveryAlarm(
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
            ForegroundAppTracker.clear()
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
            ForegroundAppTracker.clear()
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
        remoteParentRequestSubmitJob?.cancel()
        remoteParentRequestMonitorJob?.cancel()
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
                scheduleRecoveryAlarm(
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
                    syncRemoteParentStateIfNeeded()
                }.onFailure { error ->
                    Log.e(FOREGROUND_DEBUG_TAG, "Usage monitor evaluation failed", error)
                    runCatching {
                        repository.addEvent(
                            EventLogType.Safety,
                            "Usage monitor recovered from evaluation error: ${error.javaClass.simpleName}",
                        )
                    }.onFailure { logError ->
                        Log.e(FOREGROUND_DEBUG_TAG, "Failed to persist usage monitor error", logError)
                    }
                }
                delay(MONITOR_INTERVAL_MILLIS)
            }
        }
    }

    private fun ensureMonitorLoopRunning() {
        if (monitorJob?.isActive != true) {
            debugMonitor("monitor loop missing; restarting from onStartCommand")
            startMonitorLoop()
        }
    }

    private suspend fun syncRemoteParentStateIfNeeded() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastRemoteParentSyncAt < REMOTE_PARENT_BACKGROUND_SYNC_INTERVAL_MILLIS) {
            return
        }
        lastRemoteParentSyncAt = now
        val parentState = repository.parentManagementState.first()
        if (
            !parentState.paired ||
            parentState.deviceRole != ParentDeviceRole.Child ||
            parentState.childDeviceId.isBlank()
        ) {
            return
        }
        parentState.remoteUnlockRequests
            .filter { request ->
                request.status == RemoteUnlockRequestStatus.Pending &&
                    request.expiresAtMillis >= System.currentTimeMillis()
            }
            .map { request -> request.id }
            .filter { requestId -> requestId.isNotBlank() }
            .distinct()
            .forEach { requestId ->
                repository.syncRemoteUnlockRequest(requestId)
            }
        repository.syncNewRemoteCommands()
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
        if (
            systemInteractionResolver.isProtectedSystemTransition(
                targetPackageName = packageName,
                sourcePackageName = activeForegroundPackageName,
            )
        ) {
            debugMonitorState(
                "loop system handoff source=${activeForegroundPackageName.orEmpty()} target=$packageName",
            )
            cancelBlockEnforcementGuard()
            clearActiveForegroundSession()
            withContext(Dispatchers.Main) { removeBlockingOverlay() }
            updateMonitorNotification("ScreenRest", "System function is available")
            updateMonitorStatus(
                appName = usageRepository.getAppLabel(packageName),
                packageName = packageName,
                decision = "system interaction allowed",
                limitedTarget = false,
                blockReason = "",
            )
            return
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
        val hasTotalLimit = settings.todayLimitMinutesOrNull() != null
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
        if (
            systemInteractionResolver.isProtectedSystemTransition(
                targetPackageName = packageName,
                sourcePackageName = activeForegroundPackageName,
            )
        ) {
            debugMonitorState("immediate system surface package=$packageName")
            cancelBlockEnforcementGuard()
            clearActiveForegroundSession()
            withContext(Dispatchers.Main) { removeBlockingOverlay() }
            return
        }
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
        val hasTotalLimit = settings.todayLimitMinutesOrNull() != null
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
        val nowElapsed = SystemClock.elapsedRealtime()
        val shouldReconcileHardship = lastHardshipLifecycleCheckAtElapsed == 0L ||
            nowElapsed - lastHardshipLifecycleCheckAtElapsed >= HARDSHIP_LIFECYCLE_CHECK_INTERVAL_MILLIS
        val hardshipLifecycle = if (shouldReconcileHardship) {
            lastHardshipLifecycleCheckAtElapsed = nowElapsed
            repository.reconcileHardshipLifecycle()
        } else {
            HardshipLifecycleResult()
        }
        if (hardshipLifecycle.startedSchedules.isNotEmpty()) {
            val language = repository.appLanguage.first()
            hardshipLifecycle.startedSchedules.forEach { schedule ->
                showScheduleHardshipStarted(schedule, language)
            }
        }
        val lifecycleChanged = hardshipLifecycle.endedScheduleIds.isNotEmpty() ||
            hardshipLifecycle.dailyHardshipCleared
        val resolvedSettings = if (settings == null || lifecycleChanged) {
            repository.usagePolicySettings.first()
        } else {
            settings
        }
        val temporaryUnlockState = repository.temporaryUnlockState.first().forToday()
        val hardshipRuntimeState = repository.hardshipRuntimeState.first().forToday()
        val canRequestParent = repository.parentManagementState.first().canRequestParentApproval()
        val allowOnlyPackages = expandAllowedPackagesWithSharedUid(
            repository.allowOnlyAllowedAppPackages.first(),
        )
        val allRestrictionsExemptPackages = expandAllowedPackagesWithSharedUid(
            repository.allRestrictionsExemptPackages.first(),
        )
        val scheduleAllowedPackages = expandAllowedPackagesWithSharedUid(
            resolvedSettings.activeScheduleAllowedPackages(),
        )
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
        val enforcementExcludedPackages =
            SafetyGate.neverBlockPackages +
                SafetyGate.expandedUserAllowedPackages(allRestrictionsExemptPackages)
        val totalUsedMillis = stableUsageByPackage
            .filterKeys { usedPackageName -> usedPackageName !in enforcementExcludedPackages }
            .values
            .sum()
        val appLimitMinutes = resolvedSettings.activeAppLimitMap()[packageName]
        val targetGroup = resolvedSettings.normalizedAppGroups()
            .firstOrNull { group ->
                packageName in group.packageNames &&
                    group.appliesOn(currentPolicyDayOfWeek()) &&
                    group.limitMinutesOrNull() != null
            }
        val targetGroupUsedMillis = targetGroup
            ?.packageNames
            ?.filter { groupPackageName -> groupPackageName !in enforcementExcludedPackages }
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
            targetGroupLimitMinutes = targetGroup?.limitMinutesOrNull(),
            temporaryUnlockState = temporaryUnlockState,
            allowOnlyAllowedPackages = allowOnlyPackages,
            userAllowedPackages = allRestrictionsExemptPackages,
            scheduleAllowedPackages = scheduleAllowedPackages,
            hardshipBypassedPolicies = hardshipRuntimeState.bypassedPolicies,
            hardshipBypassedPolicyKeys = hardshipRuntimeState.bypassedKeysForPackage(packageName),
        )
        evaluation.activeHardshipPolicyKeys
            .filter { key -> resolvedSettings.hardshipLevelFor(key) != HardshipLevel.Off }
            .filter { key -> key !in hardshipRuntimeState.activePolicyKeys }
            .forEach { key -> repository.markHardshipPolicyTriggered(key) }
        hardshipRuntimeState.activePolicyKeys
            .filter { key ->
                val level = resolvedSettings.hardshipLevelFor(key)
                level == HardshipLevel.Off
            }
            .forEach { key -> repository.clearHardshipPolicyTriggered(key) }
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
            appLimitMillis = appLimitMinutes?.let { minutes -> minutes.toLong() * 60_000L },
            targetGroupName = targetGroup?.name,
            targetGroupUsedMillis = targetGroupUsedMillis,
            targetGroupLimitMillis = targetGroup
                ?.limitMinutesOrNull()
                ?.let { minutes -> minutes.toLong() * 60_000L },
            hardshipLevel = evaluation.hardshipLevel,
            hardshipPolicyType = evaluation.hardshipPolicyType,
            hardshipPolicyKey = evaluation.hardshipPolicyKey,
            activeHardshipPolicyKeys = evaluation.activeHardshipPolicyKeys,
            canRequestParent = canRequestParent,
            hardshipAllowanceEnded = temporaryUnlockState.packageAllowances[packageName]
                ?.hardshipAllowanceUntilMillis
                ?.let { untilMillis -> untilMillis > 0L && untilMillis <= System.currentTimeMillis() }
                == true,
            levelOneReflectionReadyAtMillis = evaluation.hardshipPolicyKey?.let { policyKey ->
                hardshipRuntimeState.levelOneReflectionEntry(policyKey, packageName)?.readyAtMillis
            } ?: 0L,
            emergencyPassNextAvailableAtMillis = hardshipRuntimeState.emergencyPassNextAvailableAtMillis(),
            activeBlockCount = evaluation.activeBlockDecisions.size,
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
        return AppVisibility.clearsForegroundSession(this) ||
            normalizedPackageName == "com.android.settings" ||
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
                val systemInteractionVisible = SafetyGate.isSystemInteractionPackage(rawForegroundPackageName)
                val foregroundPackageName = resolveForegroundPackageName(
                    detectedPackageName = rawForegroundPackageName.takeUnless { it == applicationContext.packageName }.orEmpty(),
                    rawPackageName = rawForegroundPackageName,
                )
                val targetLockedForBlocking = blockingOverlayPackageName == packageName ||
                    blockForegroundEvictionPackageName == packageName
                val shouldReassertBlock = !managerVisible &&
                    !systemInteractionVisible &&
                    (
                        targetLockedForBlocking ||
                            foregroundPackageName == packageName ||
                            currentDecision.result.decision == BlockDecision.WouldBlockTotalLimit
                    )
                val now = SystemClock.elapsedRealtime()
                debugMonitorState(
                    "BLOCK_PIPE guard package=$packageName raw=$rawForegroundPackageName resolved=$foregroundPackageName managerVisible=$managerVisible systemSurface=$systemInteractionVisible shouldReassert=$shouldReassertBlock overlayAttached=${blockingOverlayView?.isAttachedToWindow == true} decision=${currentDecision.result.decision.toLogReason()}",
                )
                if (systemInteractionVisible) {
                    withContext(Dispatchers.Main) { removeBlockingOverlay() }
                    clearActiveForegroundSession()
                    delay(BLOCK_ENFORCEMENT_GUARD_INTERVAL_MILLIS)
                    continue
                }
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
                if (SafetyGate.isSystemInteractionPackage(rawForegroundPackageName)) {
                    withContext(Dispatchers.Main) { removeBlockingOverlay() }
                    clearActiveForegroundSession()
                    break
                }
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
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Draw the blocking background behind status/navigation bars. The root view
                    // applies those insets as padding so controls remain in the safe area.
                    setFitInsetsTypes(0)
                    setFitInsetsSides(0)
                    setFitInsetsIgnoringVisibility(true)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                        } else {
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                        }
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
            systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            background = GradientDrawable().apply {
                setColor(style.backgroundColor)
            }
            setOnApplyWindowInsetsListener { view, windowInsets ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val safeInsets = windowInsets.getInsets(
                        WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
                    )
                    view.setPadding(
                        safeInsets.left,
                        safeInsets.top,
                        safeInsets.right,
                        safeInsets.bottom,
                    )
                } else {
                    @Suppress("DEPRECATION")
                    view.setPadding(
                        windowInsets.systemWindowInsetLeft,
                        windowInsets.systemWindowInsetTop,
                        windowInsets.systemWindowInsetRight,
                        windowInsets.systemWindowInsetBottom,
                    )
                }
                windowInsets
            }
        }
        root.setOnKeyListener { _, keyCode, _ -> keyCode == KeyEvent.KEYCODE_BACK }

        val scrollView = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val scrollContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
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
                val horizontalMargin = dp(20)
                val verticalMargin = dp(10)
                leftMargin = horizontalMargin
                rightMargin = horizontalMargin
                topMargin = verticalMargin
                bottomMargin = verticalMargin
            },
        )
        root.post {
            val horizontalMargin = dp(20)
            val availableWidth = root.width - (horizontalMargin * 2)
            if (availableWidth > 0) {
                val minimumWidth = dp(280).coerceAtMost(availableWidth)
                val targetWidth = availableWidth
                    .coerceAtMost(dp(440))
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
            scrollContent,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        scrollContent.addView(
            card,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(8)
                bottomMargin = dp(8)
            },
        )

        card.addView(blockOverlayText(strings.titleFor(decision.result.decision), 21, Color.rgb(17, 24, 39), true))
        if (isDailyLimitBlock) {
            card.addView(blockOverlayText(strings.dailyTime, 18, Color.rgb(17, 24, 39), true))
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
                    LinearLayout.LayoutParams(dp(56), dp(56)).apply {
                        topMargin = dp(8)
                        bottomMargin = dp(4)
                    },
                )
            }
            card.addView(blockOverlayText(decision.result.appName, 18, Color.rgb(17, 24, 39), true))
        }
        card.addView(
            blockOverlayText(
                decision.toOverlayUsageText(),
                14,
                Color.rgb(107, 114, 128),
                false,
            ),
        )
        if (decision.hardshipLevel != HardshipLevel.Off) {
            card.addView(blockOverlayHardshipIndicator(decision.hardshipLevel, strings))
        }
        val statusText = blockOverlayText(
            if (decision.hardshipLevel == HardshipLevel.Level1 && decision.hardshipAllowanceEnded) {
                strings.hardshipAllowanceExpired
            } else {
                ""
            },
            13,
            Color.rgb(229, 91, 74),
            true,
        )
        card.addView(statusText)
        when (decision.hardshipLevel) {
            HardshipLevel.Off -> {
                var requestedExtraMinutes = 5
                card.addView(
                    blockOverlayButton(
                        text = strings.addTime,
                        iconRes = R.drawable.ic_block_add_time,
                        iconColor = Color.rgb(37, 99, 235),
                    ) {
                        showOverlayTimePicker(
                            root = root,
                            initialMinutes = requestedExtraMinutes,
                            strings = strings,
                        ) { selectedMinutes ->
                            requestedExtraMinutes = selectedMinutes
                            showOverlayPinDialog(
                                root = root,
                                title = strings.adminPin,
                                strings = strings,
                            ) { pin, dialogStatus, dismiss ->
                                applyParentExtraTime(
                                    decision = decision,
                                    pin = pin,
                                    extraMinutes = selectedMinutes,
                                    statusText = dialogStatus,
                                    strings = strings,
                                    onSuccess = dismiss,
                                )
                            }
                        }
                    },
                )
                card.addView(
                    blockOverlayButton(
                        text = strings.unlockToday,
                        iconRes = R.drawable.ic_block_unlock_today,
                        iconColor = Color.rgb(5, 150, 105),
                    ) {
                        showOverlayPinDialog(
                            root = root,
                            title = strings.adminPin,
                            strings = strings,
                        ) { pin, dialogStatus, dismiss ->
                            applyParentUnlockToday(
                                decision = decision,
                                pin = pin,
                                statusText = dialogStatus,
                                strings = strings,
                                onSuccess = dismiss,
                            )
                        }
                    },
                )
                if (decision.canRequestParent) {
                    card.addView(blockOverlayParentRequestButton(decision, requestedExtraMinutes, statusText, strings))
                }
                addStandardSafeRecovery(card, root, statusText, strings)
            }
            HardshipLevel.Level1 -> {
                card.addView(blockOverlayText(strings.hardshipLevel1Description, 15, style.accentColor, true))
                lateinit var levelOneButton: Button
                levelOneButton = blockOverlayButton(
                    text = strings.hardshipReflectionAction,
                    iconRes = R.drawable.ic_block_add_time,
                    iconColor = Color.rgb(180, 122, 22),
                ) {
                    serviceScope.launch {
                        val policyKey = decision.hardshipPolicyKey
                        val result = if (policyKey == null) {
                            HardshipLevelOneGrantResult.NotAvailable
                        } else {
                            repository.requestLevelOneAllowance(
                                policyKey = policyKey,
                                packageName = decision.result.packageName,
                                appName = decision.result.appName,
                            )
                        }
                        withContext(Dispatchers.Main) {
                            statusText.text = when (result) {
                                HardshipLevelOneGrantResult.Granted -> strings.hardshipFiveMinutesGranted
                                HardshipLevelOneGrantResult.WaitingStarted -> strings.hardshipReflectionStarted
                                HardshipLevelOneGrantResult.Waiting -> strings.hardshipReflectionWaiting
                                HardshipLevelOneGrantResult.DailyLimitReached -> strings.hardshipAllowanceLimitReached
                                HardshipLevelOneGrantResult.NotAvailable -> strings.hardshipUnavailable
                            }
                            if (result == HardshipLevelOneGrantResult.Granted) {
                                cancelBlockEnforcementGuard()
                                removeBlockingOverlay()
                            } else if (
                                result == HardshipLevelOneGrantResult.WaitingStarted ||
                                result == HardshipLevelOneGrantResult.Waiting
                            ) {
                                val readyAtMillis = policyKey?.let { key ->
                                    repository.hardshipRuntimeState.first().forToday()
                                        .levelOneReflectionEntry(key, decision.result.packageName)
                                        ?.readyAtMillis
                                } ?: 0L
                                bindLevelOneCountdown(levelOneButton, readyAtMillis, strings)
                            }
                        }
                    }
                }
                bindLevelOneCountdown(
                    button = levelOneButton,
                    readyAtMillis = decision.levelOneReflectionReadyAtMillis,
                    strings = strings,
                )
                card.addView(levelOneButton)
                if (decision.canRequestParent) {
                    card.addView(blockOverlayParentRequestButton(decision, 5, statusText, strings))
                }
                addStandardSafeRecovery(card, root, statusText, strings)
            }
            HardshipLevel.Level2 -> {
                val levelTwoDescription = if (decision.canRequestParent) {
                    "${strings.hardshipLevel2Description} ${strings.hardshipParentApprovalAvailable}"
                } else {
                    strings.hardshipLevel2Description
                }
                card.addView(blockOverlayText(levelTwoDescription, 15, style.accentColor, true))
                card.addView(
                    blockOverlayButton(
                        text = strings.hardshipLevel2Action,
                        iconRes = R.drawable.ic_block_unlock_today,
                        iconColor = Color.rgb(234, 88, 12),
                    ) {
                        showOverlayPinDialog(
                            root = root,
                            title = strings.adminPin,
                            strings = strings,
                        ) { pin, dialogStatus, dismiss ->
                            val policyKey = decision.hardshipPolicyKey
                            serviceScope.launch {
                                val result = if (policyKey == null) {
                                    HardshipLevelTwoUnlockResult.NotAvailable
                                } else {
                                    repository.requestLevelTwoPolicyUnlock(
                                        policyKey = policyKey,
                                        adminPin = pin,
                                    )
                                }
                                withContext(Dispatchers.Main) {
                                    dialogStatus.text = when (result) {
                                        HardshipLevelTwoUnlockResult.Unlocked -> if (
                                            decision.hardshipPolicyType == HardshipPolicyType.Schedule
                                        ) {
                                            if (strings.hardshipLevelPrefix == "Level") {
                                                "Level 2 was ended for this schedule occurrence."
                                            } else {
                                                "고행 2단계를 이번 스케줄에서 종료했습니다."
                                            }
                                        } else {
                                            strings.hardshipLevel2Unlocked
                                        }
                                        HardshipLevelTwoUnlockResult.WaitingStarted -> strings.hardshipLevel2Started
                                        HardshipLevelTwoUnlockResult.Waiting -> strings.hardshipLevel2Waiting
                                        HardshipLevelTwoUnlockResult.InvalidPin -> strings.invalidAdminPin
                                        HardshipLevelTwoUnlockResult.NotAvailable -> strings.hardshipUnavailable
                                    }
                                    if (result == HardshipLevelTwoUnlockResult.Unlocked) {
                                        dismiss()
                                        cancelBlockEnforcementGuard()
                                        removeBlockingOverlay()
                                    }
                                }
                            }
                        }
                    },
                )
                if (decision.canRequestParent) {
                    card.addView(blockOverlayParentRequestButton(decision, 5, statusText, strings))
                }
                addStandardSafeRecovery(card, root, statusText, strings)
            }
            HardshipLevel.Level3 -> {
                card.addView(blockOverlayText(strings.hardshipLevel3Description, 15, style.accentColor, true))
                val emergencyPassAvailable = decision.emergencyPassNextAvailableAtMillis <= 0L ||
                    System.currentTimeMillis() >= decision.emergencyPassNextAvailableAtMillis
                card.addView(
                    blockOverlayText(
                        if (emergencyPassAvailable) {
                            if (strings.hardshipLevelPrefix == "Level") "Emergency Pass available · 1 use" else "Emergency Pass 사용 가능 · 1회"
                        } else {
                            if (strings.hardshipLevelPrefix == "Level") {
                                "Emergency Pass used · available again ${formatEmergencyPassTime(decision.emergencyPassNextAvailableAtMillis)}"
                            } else {
                                "Emergency Pass 사용 완료 · 다음 사용 가능 ${formatEmergencyPassTime(decision.emergencyPassNextAvailableAtMillis)}"
                            }
                        },
                        14,
                        if (emergencyPassAvailable) Color.rgb(37, 130, 78) else style.accentColor,
                        true,
                    ),
                )
                if (emergencyPassAvailable) {
                    card.addView(
                        blockOverlayButton(
                            text = strings.emergencyPass,
                            iconRes = R.drawable.ic_block_emergency,
                            iconColor = Color.rgb(190, 24, 93),
                        ) {
                            serviceScope.launch {
                                val settings = repository.usagePolicySettings.first()
                                val expiresAtMillis = decision.activeHardshipPolicyKeys.maxOfOrNull { policyKey ->
                                    settings.emergencyPassExpiryFor(policyKey)
                                } ?: 0L
                                withContext(Dispatchers.Main) {
                                    showOverlayEmergencyPassConfirmation(
                                        root = root,
                                        appName = decision.result.appName,
                                        expiresAtMillis = expiresAtMillis,
                                        strings = strings,
                                    ) {
                                        showOverlayPinDialog(
                                            root = root,
                                            title = strings.adminPin,
                                            strings = strings,
                                        ) { pin, dialogStatus, dismiss ->
                                            serviceScope.launch {
                                                val result = if (decision.hardshipPolicyKey == null) {
                                                    EmergencyPassUseResult.NotAvailable
                                                } else {
                                                    repository.useHardshipEmergencyPass(
                                                        adminPin = pin,
                                                        packageName = decision.result.packageName,
                                                        blockingPolicyKeys = decision.activeHardshipPolicyKeys,
                                                    )
                                                }
                                                withContext(Dispatchers.Main) {
                                                    dialogStatus.text = when (result) {
                                                        EmergencyPassUseResult.Used -> {
                                                            val nextAvailable = formatEmergencyPassTime(
                                                                System.currentTimeMillis() + 7L * 24L * 60L * 60L * 1000L,
                                                            )
                                                            if (strings.hardshipLevelPrefix == "Level") {
                                                                "${decision.result.appName} is allowed until ${formatEmergencyPassTime(expiresAtMillis)}. Available again $nextAvailable"
                                                            } else {
                                                                "${decision.result.appName} 앱을 ${formatEmergencyPassTime(expiresAtMillis)}까지 허용했습니다. 다음 사용 가능 $nextAvailable"
                                                            }
                                                        }
                                                        EmergencyPassUseResult.InvalidPin -> strings.invalidAdminPin
                                                        EmergencyPassUseResult.CooldownActive -> strings.emergencyPassCooldown
                                                        EmergencyPassUseResult.NotAvailable -> strings.hardshipUnavailable
                                                    }
                                                    if (result == EmergencyPassUseResult.Used) {
                                                        Toast.makeText(
                                                            this@UsageMonitorForegroundService,
                                                            dialogStatus.text,
                                                            Toast.LENGTH_LONG,
                                                        ).show()
                                                        dismiss()
                                                        cancelBlockEnforcementGuard()
                                                        removeBlockingOverlay()
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }
        card.addView(blockOverlayButton(
            strings.openManager,
            iconRes = R.drawable.ic_block_manager,
            iconColor = Color.rgb(79, 70, 229),
        ) {
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
        card.addView(blockOverlayButton(
            strings.home,
            iconRes = R.drawable.ic_block_home,
            iconColor = Color.rgb(71, 85, 105),
        ) {
            allowHomeExitFromBlockedScreen(decision.result.packageName)
            sendHomeIntent(force = true)
        })
        return root
    }

    private fun blockOverlayParentRequestButton(
        decision: MonitorDecision,
        extraMinutes: Int,
        statusText: TextView,
        strings: BlockOverlayStrings,
    ): Button {
        return blockOverlayButton(
            text = strings.requestParent,
            outline = true,
            iconRes = R.drawable.ic_block_parent,
            iconColor = Color.rgb(124, 58, 237),
        ) {}.apply {
            setOnClickListener {
                requestParentApproval(decision, extraMinutes, statusText, this, strings)
            }
        }
    }

    private fun showScheduleHardshipStarted(
        schedule: ScheduleHardshipStarted,
        language: AppLanguage,
    ) {
        usageNotificationHelper.showPolicyAlert(
            title = if (language == AppLanguage.Korean) {
                "${schedule.scheduleName} · 고행 ${schedule.level.storageValue}단계"
            } else {
                "${schedule.scheduleName} · Hardship level ${schedule.level.storageValue}"
            },
            message = if (language == AppLanguage.Korean) {
                "스케줄 고행 모드가 시작되었습니다. 스케줄 종료 시 자동으로 해제됩니다."
            } else {
                "Schedule hardship has started and will unlock automatically when this schedule ends."
            },
        )
    }

    private fun requestParentApproval(
        decision: MonitorDecision,
        extraMinutes: Int,
        statusText: TextView,
        requestButton: Button,
        strings: BlockOverlayStrings,
    ) {
        if (!requestButton.isEnabled) return
        requestButton.isEnabled = false
        requestButton.alpha = 0.55f
        statusText.setTextColor(Color.rgb(37, 99, 235))
        statusText.text = strings.requestSent
        remoteParentRequestSubmitJob?.cancel()
        remoteParentRequestSubmitJob = serviceScope.launch {
            val submission = repository.createRemoteUnlockRequest(
                blockReason = decision.result.decision.toRemoteRequestBlockReason(),
                targetPackageName = decision.result.packageName,
                targetAppName = decision.result.appName,
                targetGroupName = decision.targetGroupName.orEmpty(),
                scheduleName = "",
                usedMillis = decision.usedMillis,
                limitMillis = decision.limitMillis,
                alreadyGrantedExtraMinutes = decision.extraMinutes,
                unlockedForToday = decision.unlockedForToday,
                requestedMinutes = extraMinutes,
            )
            when (submission) {
                is RemoteUnlockRequestSubmitResult.Sent -> {
                    withContext(Dispatchers.Main) {
                        statusText.setTextColor(Color.rgb(37, 99, 235))
                        statusText.text = strings.requestSent
                    }
                    startRemoteParentRequestMonitor(
                        requestId = submission.requestId,
                        statusText = statusText,
                        requestButton = requestButton,
                        strings = strings,
                    )
                }
                is RemoteUnlockRequestSubmitResult.Retrying -> {
                    withContext(Dispatchers.Main) {
                        statusText.setTextColor(Color.rgb(180, 83, 9))
                        statusText.text = strings.requestRetrying
                    }
                    retryRemoteParentRequest(
                        requestId = submission.requestId,
                        statusText = statusText,
                        requestButton = requestButton,
                        strings = strings,
                    )
                }
                RemoteUnlockRequestSubmitResult.NotPaired -> withContext(Dispatchers.Main) {
                    requestButton.isEnabled = true
                    requestButton.alpha = 1f
                    statusText.setTextColor(Color.rgb(229, 91, 74))
                    statusText.text = strings.parentNotLinked
                }
                is RemoteUnlockRequestSubmitResult.Failed -> withContext(Dispatchers.Main) {
                    requestButton.isEnabled = true
                    requestButton.alpha = 1f
                    statusText.setTextColor(Color.rgb(229, 91, 74))
                    statusText.text = strings.requestPublishFailed
                }
            }
        }
    }

    private suspend fun retryRemoteParentRequest(
        requestId: String,
        statusText: TextView,
        requestButton: Button,
        strings: BlockOverlayStrings,
    ) {
        var retryDelayMillis = REMOTE_PARENT_REQUEST_RETRY_INITIAL_MILLIS
        while (serviceScope.isActive) {
            delay(retryDelayMillis)
            when (val result = repository.retryRemoteUnlockRequest(requestId)) {
                is RemoteUnlockRequestSubmitResult.Sent -> {
                    withContext(Dispatchers.Main) {
                        statusText.setTextColor(Color.rgb(37, 99, 235))
                        statusText.text = strings.requestSent
                    }
                    startRemoteParentRequestMonitor(
                        requestId = result.requestId,
                        statusText = statusText,
                        requestButton = requestButton,
                        strings = strings,
                    )
                    return
                }
                is RemoteUnlockRequestSubmitResult.Retrying -> {
                    withContext(Dispatchers.Main) {
                        statusText.setTextColor(Color.rgb(180, 83, 9))
                        statusText.text = strings.requestRetrying
                    }
                    retryDelayMillis = (retryDelayMillis * 2L)
                        .coerceAtMost(REMOTE_PARENT_REQUEST_RETRY_MAX_MILLIS)
                }
                RemoteUnlockRequestSubmitResult.NotPaired,
                is RemoteUnlockRequestSubmitResult.Failed -> {
                    withContext(Dispatchers.Main) {
                        requestButton.isEnabled = true
                        requestButton.alpha = 1f
                        statusText.setTextColor(Color.rgb(229, 91, 74))
                        statusText.text = strings.requestPublishFailed
                    }
                    return
                }
            }
        }
    }

    private fun startRemoteParentRequestMonitor(
        requestId: String,
        statusText: TextView,
        requestButton: Button,
        strings: BlockOverlayStrings,
    ) {
        remoteParentRequestMonitorJob?.cancel()
        remoteParentRequestMonitorJob = serviceScope.launch {
            monitorRemoteParentRequestStatus(requestId, statusText, requestButton, strings)
        }
    }

    private suspend fun monitorRemoteParentRequestStatus(
        requestId: String,
        statusText: TextView,
        requestButton: Button,
        strings: BlockOverlayStrings,
    ) {
        val startedAt = SystemClock.elapsedRealtime()
        val deadline = startedAt + REMOTE_PARENT_REQUEST_STATUS_WINDOW_MILLIS
        while (serviceScope.isActive && SystemClock.elapsedRealtime() < deadline) {
            val elapsed = SystemClock.elapsedRealtime() - startedAt
            delay(
                if (elapsed < REMOTE_PARENT_FAST_SYNC_WINDOW_MILLIS) {
                    REMOTE_PARENT_FAST_SYNC_INTERVAL_MILLIS
                } else {
                    REMOTE_PARENT_SYNC_INTERVAL_MILLIS
                },
            )
            repository.syncRemoteUnlockRequest(requestId)
            val now = System.currentTimeMillis()
            val request = repository.parentManagementState.first()
                .remoteUnlockRequests
                .firstOrNull { item -> item.id == requestId }
            val status = when {
                request == null -> RemoteUnlockRequestStatus.Pending
                request.status == RemoteUnlockRequestStatus.Pending && request.expiresAtMillis < now ->
                    RemoteUnlockRequestStatus.Expired
                else -> request.status
            }
            withContext(Dispatchers.Main) {
                when (status) {
                    RemoteUnlockRequestStatus.Pending -> {
                        statusText.setTextColor(Color.rgb(37, 99, 235))
                        statusText.text = strings.requestWaiting
                    }
                    RemoteUnlockRequestStatus.Approved -> {
                        statusText.setTextColor(Color.rgb(22, 101, 52))
                        statusText.text = strings.requestApproved
                        cancelBlockEnforcementGuard()
                        clearActiveForegroundSession()
                        lastBlockedActivityPackageName = null
                        removeBlockingOverlay()
                    }
                    RemoteUnlockRequestStatus.Rejected -> {
                        requestButton.isEnabled = true
                        requestButton.alpha = 1f
                        statusText.setTextColor(Color.rgb(229, 91, 74))
                        statusText.text = strings.requestRejected
                    }
                    RemoteUnlockRequestStatus.Expired -> {
                        requestButton.isEnabled = true
                        requestButton.alpha = 1f
                        statusText.setTextColor(Color.rgb(180, 83, 9))
                        statusText.text = strings.requestExpired
                    }
                    RemoteUnlockRequestStatus.Failed -> {
                        requestButton.isEnabled = true
                        requestButton.alpha = 1f
                        statusText.setTextColor(Color.rgb(229, 91, 74))
                        statusText.text = strings.requestFailed
                    }
                }
            }
            if (status != RemoteUnlockRequestStatus.Pending) {
                return
            }
        }
        withContext(Dispatchers.Main) {
            requestButton.isEnabled = true
            requestButton.alpha = 1f
            statusText.setTextColor(Color.rgb(180, 83, 9))
            statusText.text = strings.requestExpired
        }
    }

    private fun applyParentExtraTime(
        decision: MonitorDecision,
        pin: String,
        extraMinutes: Int,
        statusText: TextView,
        strings: BlockOverlayStrings,
        onSuccess: () -> Unit = {},
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
                    onSuccess()
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
        onSuccess: () -> Unit = {},
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
                    onSuccess()
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
                    hardshipLevel = decision.hardshipLevel,
                    hardshipPolicyKey = decision.hardshipPolicyKey,
                    activeHardshipPolicyKeys = decision.activeHardshipPolicyKeys,
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
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            debugMonitor("NOTIFICATION skipped: permission missing")
            return
        }
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
            .setContentTitle(getString(R.string.monitoring_notification_title))
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
        val hasAppLimit = activeAppLimitMap().containsKey(packageName)
        val hasGroupLimit = normalizedAppGroups()
            .any { group ->
                packageName in group.packageNames &&
                    group.appliesOn(currentPolicyDayOfWeek()) &&
                    group.limitMinutesOrNull() != null
            }
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

    private fun BlockDecision.toRemoteRequestBlockReason(): RemoteRequestBlockReason {
        return when (this) {
            BlockDecision.WouldBlockTotalLimit -> RemoteRequestBlockReason.DailyLimit
            BlockDecision.WouldBlockSchedule -> RemoteRequestBlockReason.ScheduleBlock
            BlockDecision.WouldBlockAllowOnly -> RemoteRequestBlockReason.AllowOnlyMode
            BlockDecision.WouldBlockGroupLimit -> RemoteRequestBlockReason.AppGroupLimit
            BlockDecision.WouldBlockAppLimit,
            BlockDecision.AllowedSafeMode,
            BlockDecision.AllowedPolicyDisabled,
            BlockDecision.AllowedWhitelist,
            BlockDecision.AllowedNoLimit,
            BlockDecision.AllowedUnderLimit -> RemoteRequestBlockReason.AppLimit
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
                selectExtraTime = "추가할 시간 선택",
                addTime = "시간 추가",
                unlockToday = "오늘만 해제",
                requestParent = "부모에게 요청",
                requestSent = "요청을 보냈습니다.",
                requestRetrying = "재시도 중입니다.",
                requestWaiting = "부모에게 요청됨 · 승인 대기 중",
                requestPublishFailed = "요청을 처리하지 못했습니다 · 관리 앱에서 연결 상태를 확인하세요",
                requestApproved = "승인됨 · 차단을 해제합니다",
                requestRejected = "부모가 요청을 거절했습니다",
                requestExpired = "요청이 만료되었습니다",
                requestFailed = "요청 처리에 실패했습니다",
                parentNotLinked = "부모 기기가 연결되어 있지 않습니다",
                safeRecovery = "안전 복구",
                openManager = "폰 쉼 열기",
                home = "홈으로",
                cancel = "취소",
                next = "다음",
                confirm = "확인",
                hoursUnit = "시간",
                minutesUnit = "분",
                timeRequired = "1분 이상의 시간을 선택하세요",
                pinRequired = "PIN을 입력하세요",
                adminPinRequired = "관리 PIN을 입력하세요",
                invalidAdminPin = "관리 PIN이 올바르지 않습니다",
                addedPrefix = "추가됨",
                unlockedForToday = "오늘만 해제되었습니다",
                hardshipLevelPrefix = "단계",
                hardshipLevel1Description = "2분 숙고 후 5분 임시 허용을 최대 2회 사용할 수 있습니다. 고행 종료는 2분 숙고 후 관리 PIN이 필요합니다.",
                hardshipLevel2Description = "고행 종료는 30분 숙고 후 관리 PIN이 필요합니다.",
                hardshipParentApprovalAvailable = "연결된 부모에게 승인도 요청할 수 있습니다.",
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
                hardshipLevel2Action = "30분 숙고 / 관리 PIN으로 종료",
                hardshipLevel2Started = "30분 숙고를 시작했습니다. 시간이 지난 뒤 관리 PIN을 입력하세요.",
                hardshipLevel2Waiting = "아직 30분 숙고 시간이 끝나지 않았습니다.",
                hardshipLevel2Unlocked = "고행 2단계 정책을 오늘만 종료했습니다.",
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
                selectExtraTime = "Select extra time",
                addTime = "Add time",
                unlockToday = "Unlock for today",
                requestParent = "Ask parent",
                requestSent = "Request sent to parent",
                requestRetrying = "Retrying",
                requestWaiting = "Requested · waiting for approval",
                requestPublishFailed = "Request failed · check the connection and network",
                requestApproved = "Approved · unlocking",
                requestRejected = "Parent rejected the request",
                requestExpired = "Request expired",
                requestFailed = "Request failed",
                parentNotLinked = "Parent device is not linked",
                safeRecovery = "Safe Recovery",
                openManager = "Open ScreenRest",
                home = "Home",
                cancel = "Cancel",
                next = "Next",
                confirm = "Confirm",
                hoursUnit = "h",
                minutesUnit = "min",
                timeRequired = "Select at least 1 minute",
                pinRequired = "Enter the PIN",
                adminPinRequired = "Admin PIN is required",
                invalidAdminPin = "Admin PIN is incorrect",
                addedPrefix = "Added",
                unlockedForToday = "Unlocked for today",
                hardshipLevelPrefix = "Level",
                hardshipLevel1Description = "After 2 minutes of reflection, you can use a 5-minute allowance up to twice. Ending hardship also requires 2 minutes and the Admin PIN.",
                hardshipLevel2Description = "Ending hardship requires 30 minutes of reflection and the Admin PIN.",
                hardshipParentApprovalAvailable = "You can also ask the linked parent for approval.",
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
                hardshipLevel2Action = "Reflect 30 min / end with Admin PIN",
                hardshipLevel2Started = "The 30-minute reflection started. Enter the Admin PIN when it ends.",
                hardshipLevel2Waiting = "The 30-minute reflection has not ended yet.",
                hardshipLevel2Unlocked = "The level 2 policy was ended for today.",
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

    private fun BlockOverlayStrings.hardshipLevelLabel(level: HardshipLevel): String {
        return if (hardshipLevelPrefix == "Level") {
            "Level ${level.storageValue}"
        } else {
            "${level.storageValue}단계"
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
            setPadding(0, dp(4), 0, dp(4))
        }
    }

    private fun blockOverlayHardshipIndicator(
        level: HardshipLevel,
        strings: BlockOverlayStrings,
    ): View {
        val color = when (level) {
            HardshipLevel.Off -> Color.rgb(107, 114, 128)
            HardshipLevel.Level1 -> Color.rgb(176, 122, 22)
            HardshipLevel.Level2 -> Color.rgb(226, 104, 34)
            HardshipLevel.Level3 -> Color.rgb(142, 39, 69)
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(
                ImageView(this@UsageMonitorForegroundService).apply {
                    setImageResource(R.drawable.ic_hardship_meditation)
                    setColorFilter(color)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                },
                LinearLayout.LayoutParams(dp(34), dp(34)),
            )
            addView(
                blockOverlayText(strings.hardshipLevelLabel(level), 17, color, true),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { leftMargin = dp(8) },
            )
        }
    }

    private fun addStandardSafeRecovery(
        card: LinearLayout,
        root: FrameLayout,
        statusText: TextView,
        strings: BlockOverlayStrings,
    ) {
        card.addView(
            blockOverlayButton(
                text = strings.safeRecovery,
                iconRes = R.drawable.ic_block_emergency,
                iconColor = Color.rgb(220, 38, 38),
            ) {
                showOverlayPinDialog(
                    root = root,
                    title = strings.adminPin,
                    strings = strings,
                ) { pin, dialogStatus, dismiss ->
                    serviceScope.launch {
                        val unlocked = repository.safeRecovery(pin)
                        withContext(Dispatchers.Main) {
                            if (unlocked) {
                                dismiss()
                                cancelBlockEnforcementGuard()
                                clearActiveForegroundSession()
                                lastBlockedActivityPackageName = null
                                removeBlockingOverlay()
                            } else {
                                dialogStatus.setTextColor(Color.rgb(229, 91, 74))
                                dialogStatus.text = strings.invalidAdminPin
                                statusText.text = ""
                            }
                        }
                    }
                }
            },
        )
    }

    private fun showOverlayEmergencyPassConfirmation(
        root: FrameLayout,
        appName: String,
        expiresAtMillis: Long,
        strings: BlockOverlayStrings,
        onContinue: () -> Unit,
    ) {
        val modal = createOverlayModal(root)
        val content = modal.content
        val korean = strings.hardshipLevelPrefix != "Level"
        val expiry = if (expiresAtMillis > 0L) {
            formatEmergencyPassTime(expiresAtMillis)
        } else if (korean) {
            "현재 정책 종료 시점"
        } else {
            "the current policy end"
        }
        content.addView(
            blockOverlayText(
                if (korean) "Emergency Pass 확인" else "Confirm Emergency Pass",
                19,
                Color.rgb(17, 24, 39),
                true,
            ),
        )
        content.addView(
            blockOverlayText(
                if (korean) {
                    "$appName 앱을 $expiry 까지 허용합니다. 현재 이 앱을 막고 있는 정책에만 예외가 적용됩니다. 사용 후 7일 동안 모든 고행 3단계에서 다시 사용할 수 없으며 취소하거나 되돌릴 수 없습니다."
                } else {
                    "Allow $appName until $expiry. Only policies currently blocking this app are bypassed. The Pass cannot be used again for any level-3 policy for 7 days and cannot be refunded."
                },
                14,
                Color.rgb(55, 65, 81),
                false,
            ),
        )
        content.addView(
            overlayDialogButtons(
                cancelText = strings.cancel,
                confirmText = if (korean) "관리 PIN 입력" else "Enter Admin PIN",
                onCancel = modal.dismiss,
                onConfirm = {
                    modal.dismiss()
                    onContinue()
                },
            ),
        )
    }

    private fun formatEmergencyPassTime(timestampMillis: Long): String {
        if (timestampMillis <= 0L) return "-"
        return SimpleDateFormat("M/d HH:mm", Locale.getDefault()).format(Date(timestampMillis))
    }

    /**
     * Some visible apps delegate work to a sibling package signed and installed under the same
     * Android UID. Treating that helper as an unrelated app breaks photo/edit/share flows and
     * forces users to manage packages that never appear as launchable icons. Shared-UID expansion
     * is deliberately applied only to packages the user already allowed.
     */
    private fun expandAllowedPackagesWithSharedUid(packageNames: Set<String>): Set<String> {
        val expandedPackages = SafetyGate.expandedUserAllowedPackages(packageNames).toMutableSet()
        packageNames.forEach { packageName ->
            val uid = runCatching {
                packageManager.getApplicationInfo(packageName, 0).uid
            }.getOrNull() ?: return@forEach
            packageManager.getPackagesForUid(uid)
                .orEmpty()
                .filter { relatedPackageName -> relatedPackageName.isNotBlank() }
                .forEach(expandedPackages::add)
        }
        return expandedPackages
    }

    private fun blockOverlayButton(
        text: String,
        primary: Boolean = false,
        outline: Boolean = false,
        iconRes: Int? = null,
        iconColor: Int = Color.rgb(37, 99, 235),
        onClick: () -> Unit,
    ): Button {
        return Button(this).apply {
            this.text = text
            textSize = 14.5f
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (primary || outline) Color.rgb(37, 99, 235) else Color.rgb(17, 24, 39))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(if (primary) Color.rgb(37, 99, 235) else Color.rgb(229, 231, 235))
                if (outline) {
                    setColor(Color.TRANSPARENT)
                    setStroke(dp(1), Color.rgb(107, 114, 128))
                }
            }
            if (primary) {
                setTextColor(Color.WHITE)
            }
            iconRes?.let { resourceId ->
                setCompoundDrawablesWithIntrinsicBounds(resourceId, 0, 0, 0)
                compoundDrawableTintList = ColorStateList.valueOf(iconColor)
                compoundDrawablePadding = dp(12)
            }
            gravity = Gravity.CENTER
            setPadding(dp(18), 0, dp(18), 0)
            minHeight = dp(52)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52),
            ).apply {
                topMargin = dp(10)
            }
            setOnClickListener { onClick() }
        }
    }

    private fun bindLevelOneCountdown(
        button: Button,
        readyAtMillis: Long,
        strings: BlockOverlayStrings,
    ) {
        if (readyAtMillis <= 0L) {
            button.text = strings.hardshipReflectionAction
            button.isEnabled = true
            return
        }
        val update = object : Runnable {
            override fun run() {
                val remainingMillis = (readyAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)
                if (remainingMillis <= 0L) {
                    button.text = if (strings.hardshipReflectionAction.startsWith("Reflect")) {
                        "Allow for 5 minutes"
                    } else {
                        "5분 임시 허용"
                    }
                    button.isEnabled = true
                    return
                }
                val totalSeconds = (remainingMillis + 999L) / 1_000L
                val minutes = totalSeconds / 60L
                val seconds = totalSeconds % 60L
                val prefix = if (strings.hardshipReflectionAction.startsWith("Reflect")) {
                    "Reflecting"
                } else {
                    "숙고 중"
                }
                button.text = "$prefix · %02d:%02d".format(minutes, seconds)
                button.isEnabled = false
                button.postDelayed(this, 1_000L)
            }
        }
        button.removeCallbacks(update)
        update.run()
    }

    private fun showOverlayTimePicker(
        root: FrameLayout,
        initialMinutes: Int,
        strings: BlockOverlayStrings,
        onConfirm: (Int) -> Unit,
    ) {
        val modal = createOverlayModal(root)
        val content = modal.content
        content.addView(blockOverlayText(strings.selectExtraTime, 19, Color.rgb(17, 24, 39), true))

        val maxSelectableMinutes = MAX_TEMPORARY_EXTRA_MINUTES
        val maxSelectableHours = maxSelectableMinutes / 60
        val maxMinutesAtLastHour = maxSelectableMinutes % 60
        val normalizedInitialMinutes = initialMinutes.coerceIn(1, maxSelectableMinutes)
        val selectedValueText = blockOverlayText(
            formatLimitMinutesLabel(normalizedInitialMinutes),
            21,
            Color.rgb(30, 64, 175),
            true,
        ).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.rgb(219, 234, 254))
            }
            setPadding(dp(22), dp(10), dp(22), dp(10))
        }
        content.addView(
            selectedValueText,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
                bottomMargin = dp(8)
            },
        )
        val hourPicker = NumberPicker(this).apply {
            minValue = 0
            maxValue = maxSelectableHours
            value = normalizedInitialMinutes / 60
            wrapSelectorWheel = false
        }
        val minutePicker = NumberPicker(this).apply {
            minValue = 0
            maxValue = if (hourPicker.value == maxSelectableHours) maxMinutesAtLastHour else 59
            value = if (hourPicker.value == maxSelectableHours) {
                (normalizedInitialMinutes % 60).coerceAtMost(maxMinutesAtLastHour)
            } else {
                normalizedInitialMinutes % 60
            }
            wrapSelectorWheel = true
        }
        fun updateSelectedValue() {
            val selectedMinutes = ((hourPicker.value * 60) + minutePicker.value)
                .coerceIn(0, maxSelectableMinutes)
            selectedValueText.text = formatLimitMinutesLabel(selectedMinutes)
        }
        hourPicker.setOnValueChangedListener { _, _, newValue ->
            val previousMinute = minutePicker.value
            minutePicker.maxValue = if (newValue == maxSelectableHours) maxMinutesAtLastHour else 59
            minutePicker.value = if (newValue == maxSelectableHours) {
                previousMinute.coerceAtMost(maxMinutesAtLastHour)
            } else {
                previousMinute.coerceAtMost(59)
            }
            updateSelectedValue()
        }
        minutePicker.setOnValueChangedListener { _, _, _ -> updateSelectedValue() }
        val pickerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(Color.rgb(243, 244, 246))
            }
            addView(hourPicker, LinearLayout.LayoutParams(0, dp(150), 1f))
            addView(blockOverlayText(strings.hoursUnit, 15, Color.rgb(55, 65, 81), true))
            addView(minutePicker, LinearLayout.LayoutParams(0, dp(150), 1f))
            addView(blockOverlayText(strings.minutesUnit, 15, Color.rgb(55, 65, 81), true))
        }
        content.addView(
            pickerRow,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT),
        )
        val errorText = blockOverlayText("", 13, Color.rgb(229, 91, 74), true)
        content.addView(errorText)
        content.addView(
            overlayDialogButtons(
                cancelText = strings.cancel,
                confirmText = strings.next,
                onCancel = modal.dismiss,
                onConfirm = {
                    val selectedMinutes = (hourPicker.value * 60) + minutePicker.value
                    if (selectedMinutes <= 0) {
                        errorText.text = strings.timeRequired
                    } else {
                        modal.dismiss()
                        onConfirm(selectedMinutes.coerceAtMost(maxSelectableMinutes))
                    }
                },
            ),
        )
    }

    private fun showOverlayPinDialog(
        root: FrameLayout,
        title: String,
        strings: BlockOverlayStrings,
        onConfirm: (pin: String, statusText: TextView, dismiss: () -> Unit) -> Unit,
    ) {
        val modal = createOverlayModal(root)
        val content = modal.content
        content.addView(blockOverlayText(title, 19, Color.rgb(17, 24, 39), true))
        val inputScroll = ScrollView(this).apply { isFillViewport = true }
        val pinInput = blockOverlayPinInput(title, inputScroll, strings)
        inputScroll.addView(
            pinInput.container,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(50)),
        )
        content.addView(
            inputScroll,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54)).apply {
                topMargin = dp(10)
            },
        )
        val statusText = blockOverlayText("", 13, Color.rgb(229, 91, 74), true)
        content.addView(statusText)
        content.addView(
            overlayDialogButtons(
                cancelText = strings.cancel,
                confirmText = strings.confirm,
                onCancel = modal.dismiss,
                onConfirm = {
                    val pin = pinInput.input.text?.toString().orEmpty()
                    if (pin.isBlank()) {
                        statusText.text = strings.pinRequired
                    } else {
                        onConfirm(pin, statusText, modal.dismiss)
                    }
                },
            ),
        )
        pinInput.input.requestFocus()
        showOverlayKeyboard(pinInput.input)
    }

    private fun createOverlayModal(root: FrameLayout): OverlayModal {
        val modalLayer = FrameLayout(this).apply {
            isClickable = true
            isFocusable = true
            setBackgroundColor(Color.argb(165, 0, 0, 0))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(18), dp(18), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(Color.WHITE)
            }
        }
        modalLayer.addView(
            content,
            FrameLayout.LayoutParams(
                dp(360).coerceAtMost(resources.displayMetrics.widthPixels - dp(32)),
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
        val dismiss = {
            val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            inputMethodManager?.hideSoftInputFromWindow(modalLayer.windowToken, 0)
            root.removeView(modalLayer)
            Unit
        }
        root.addView(
            modalLayer,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
        )
        modalLayer.bringToFront()
        return OverlayModal(content = content, dismiss = dismiss)
    }

    private fun overlayDialogButtons(
        cancelText: String,
        confirmText: String,
        onCancel: () -> Unit,
        onConfirm: () -> Unit,
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(
                blockOverlayButton(cancelText, outline = true, onClick = onCancel),
                LinearLayout.LayoutParams(0, dp(46), 1f).apply { rightMargin = dp(6) },
            )
            addView(
                blockOverlayButton(confirmText, primary = true, onClick = onConfirm),
                LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = dp(6) },
            )
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
            textSize = 15f
            isFocusable = true
            isFocusableInTouchMode = true
            background = null
            setPadding(dp(12), 0, dp(6), 0)
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
            textSize = 12f
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
                    dp(58),
                    dp(32),
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
        val hardshipLevel: HardshipLevel,
        val hardshipPolicyType: HardshipPolicyType?,
        val hardshipPolicyKey: HardshipPolicyKey?,
        val activeHardshipPolicyKeys: Set<HardshipPolicyKey>,
        val canRequestParent: Boolean,
        val hardshipAllowanceEnded: Boolean,
        val levelOneReflectionReadyAtMillis: Long,
        val emergencyPassNextAvailableAtMillis: Long,
        val activeBlockCount: Int,
    )

    private data class BlockOverlayPinInput(
        val container: View,
        val input: EditText,
    )

    private data class OverlayModal(
        val content: LinearLayout,
        val dismiss: () -> Unit,
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
        val selectExtraTime: String,
        val addTime: String,
        val unlockToday: String,
        val requestParent: String,
        val requestSent: String,
        val requestRetrying: String,
        val requestWaiting: String,
        val requestPublishFailed: String,
        val requestApproved: String,
        val requestRejected: String,
        val requestExpired: String,
        val requestFailed: String,
        val parentNotLinked: String,
        val safeRecovery: String,
        val openManager: String,
        val home: String,
        val cancel: String,
        val next: String,
        val confirm: String,
        val hoursUnit: String,
        val minutesUnit: String,
        val timeRequired: String,
        val pinRequired: String,
        val adminPinRequired: String,
        val invalidAdminPin: String,
        val addedPrefix: String,
        val unlockedForToday: String,
        val hardshipLevelPrefix: String,
        val hardshipLevel1Description: String,
        val hardshipLevel2Description: String,
        val hardshipParentApprovalAvailable: String,
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
        val hardshipLevel2Action: String,
        val hardshipLevel2Started: String,
        val hardshipLevel2Waiting: String,
        val hardshipLevel2Unlocked: String,
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
        private const val MONITOR_STATUS_WRITE_INTERVAL_MILLIS = 5_000L
        private const val USAGE_MAP_CACHE_MILLIS = 1_000L
        private const val HARDSHIP_LIFECYCLE_CHECK_INTERVAL_MILLIS = 5_000L
        private const val REMOTE_PARENT_SYNC_INTERVAL_MILLIS = 15_000L
        private const val REMOTE_PARENT_BACKGROUND_SYNC_INTERVAL_MILLIS = 60_000L
        private const val REMOTE_PARENT_FAST_SYNC_INTERVAL_MILLIS = 2_000L
        private const val REMOTE_PARENT_FAST_SYNC_WINDOW_MILLIS = 2L * 60L * 1_000L
        private const val REMOTE_PARENT_REQUEST_STATUS_WINDOW_MILLIS = 10L * 60L * 1_000L
        private const val REMOTE_PARENT_REQUEST_RETRY_INITIAL_MILLIS = 2_000L
        private const val REMOTE_PARENT_REQUEST_RETRY_MAX_MILLIS = 30_000L
        private const val FOREGROUND_DEBUG_TAG = "STM-Foreground"

        fun start(context: Context): Boolean {
            val intent = Intent(context, UsageMonitorForegroundService::class.java)
            return runCatching {
                ContextCompat.startForegroundService(context, intent)
                true
            }.onFailure { error ->
                Log.e(FOREGROUND_DEBUG_TAG, "Unable to start usage monitor foreground service", error)
            }.getOrDefault(false)
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

        fun scheduleRecoveryAlarm(
            context: Context,
            reason: String,
            delayMillis: Long = 60_000L,
        ) {
            val appContext = context.applicationContext
            val alarmManager = appContext.getSystemService(AlarmManager::class.java)
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
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAt,
                pendingIntent,
            )
        }
    }
}
