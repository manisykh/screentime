package com.example.screentimemanager.blocking

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.example.screentimemanager.data.EventLogType
import com.example.screentimemanager.data.SettingsRepository
import com.example.screentimemanager.data.TemporaryUnlockState
import com.example.screentimemanager.data.UsagePolicySettings
import com.example.screentimemanager.data.normalizedAppGroups
import com.example.screentimemanager.data.settingsDataStore
import com.example.screentimemanager.safety.BlockDecision
import com.example.screentimemanager.safety.BlockDecisionResult
import com.example.screentimemanager.safety.BlockDecisionEngine
import com.example.screentimemanager.safety.SafetyGate
import com.example.screentimemanager.usage.AppVisibility
import com.example.screentimemanager.usage.UsageStatsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScreenTimeAccessibilityService : AccessibilityService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val repository by lazy { SettingsRepository(applicationContext.settingsDataStore) }
    private val usageStatsRepository by lazy { UsageStatsRepository(applicationContext) }
    private val lastLoggedAtByPackage = mutableMapOf<String, Long>()
    private val lastEvaluatedAtByPackage = mutableMapOf<String, Long>()
    private val lastBlockLaunchedAtByPackage = mutableMapOf<String, Long>()
    private var lastOverlayReassertedAt: Long = 0L
    private var overlayInteractionPausedUntil: Long = 0L
    private val mediaPauseJobsByPackage = mutableMapOf<String, Job>()
    private var blockingOverlayView: View? = null
    private var blockingOverlayPackageName: String? = null
    private var blockingOverlayGuardJob: Job? = null
    private var foregroundEnforcementJob: Job? = null
    private var trackedForegroundPackageName: String? = null
    private var trackedForegroundStartedAtElapsed: Long = 0L
    private var trackedForegroundBaselineUsageMillis: Long? = null

    private var safeModeEnabled: Boolean = true
    private var policyEnforcementEnabled: Boolean = false
    private var usagePolicySettings: UsagePolicySettings = UsagePolicySettings()
    private var temporaryUnlockState: TemporaryUnlockState = TemporaryUnlockState()
    private var allowedAppPackages: Set<String> = emptySet()

    override fun onServiceConnected() {
        super.onServiceConnected()
        startSettingsCollectors()
        startForegroundEnforcementLoop()
        logEvent(EventLogType.Safety, "AccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val accessibilityEvent = event ?: return
        val eventType = accessibilityEvent.eventType
        if (!accessibilityEvent.isForegroundAppEvent()) {
            return
        }

        accessibilityEvent.packageName?.toString()?.let { rawPackageName ->
            if (rawPackageName.shouldClearTrackedForegroundPackage()) {
                clearTrackedForegroundPackage()
            }
        }

        val packageName = resolveForegroundPackageName(accessibilityEvent)
        if (packageName.isBlank()) {
            return
        }

        if (packageName == applicationContext.packageName) {
            return
        }

        if (isIgnoredForegroundPackage(packageName)) {
            return
        }

        updateTrackedForegroundPackage(packageName)

        if (!shouldEvaluatePackage(packageName)) {
            return
        }

        val safetyGateResult = SafetyGate.evaluateBlocking(
            safeModeEnabled = safeModeEnabled,
            policyEnforcementEnabled = policyEnforcementEnabled,
            targetPackageName = packageName,
            userAllowedPackages = allowedAppPackages,
        )
        if (!safetyGateResult.canEvaluateBlocking) {
            return
        }

        serviceScope.launch {
            handleForegroundPackage(
                packageName = packageName,
                eventType = eventType,
            )
        }
    }

    private fun handleForegroundPackage(packageName: String, eventType: Int) {
        val appName = packageName.toAppName()
        val safetyGateResult = SafetyGate.evaluateBlocking(
            safeModeEnabled = safeModeEnabled,
            policyEnforcementEnabled = policyEnforcementEnabled,
            targetPackageName = packageName,
            userAllowedPackages = allowedAppPackages,
        )
        if (!safetyGateResult.canEvaluateBlocking) {
            return
        }

        val result = evaluateBlockDecision(packageName, appName)
        updateDetectionStatus(appName, packageName, result.decision.toLogReason())
        if (result.decision.isWouldBlock()) {
            if (shouldLogPackage(packageName)) {
                logEvent(
                    EventLogType.Warning,
                    "Accessibility detected ${result.appName} would be blocked; foreground monitor handles enforcement",
                )
            }
        } else {
            if (shouldLogPackage(packageName)) {
                logEvent(EventLogType.Info, "$appName detected: ${result.decision.toLogReason()}")
            }
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        logEvent(EventLogType.Safety, "AccessibilityService disconnected")
        cancelMediaPauseGuards()
        removeBlockingOverlay()
        cancelBlockingOverlayGuard()
        cancelForegroundEnforcementLoop()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startSettingsCollectors() {
        serviceScope.launch {
            repository.safeModeEnabled.collectLatest { enabled ->
                safeModeEnabled = enabled
                if (enabled) {
                    serviceScope.launch(Dispatchers.Main) {
                        removeBlockingOverlay()
                    }
                }
            }
        }
        serviceScope.launch {
            repository.policyEnforcementEnabled.collectLatest { enabled ->
                policyEnforcementEnabled = enabled
                if (!enabled) {
                    serviceScope.launch(Dispatchers.Main) {
                        removeBlockingOverlay()
                    }
                }
            }
        }
        serviceScope.launch {
            repository.usagePolicySettings.collectLatest { settings ->
                usagePolicySettings = settings
            }
        }
        serviceScope.launch {
            repository.temporaryUnlockState.collectLatest { state ->
                temporaryUnlockState = state
                if (blockingOverlayPackageName != null) {
                    serviceScope.launch {
                        removeOverlayIfBlockingNoLongerApplies()
                    }
                }
            }
        }
        serviceScope.launch {
            repository.allowedAppPackages.collectLatest { packageNames ->
                allowedAppPackages = packageNames
            }
        }
    }

    private fun AccessibilityEvent.isForegroundAppEvent(): Boolean {
        return eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED
    }

    private fun resolveForegroundPackageName(event: AccessibilityEvent): String {
        return try {
            val activeRootPackageName = rootInActiveWindow?.packageName?.toString().orEmpty()
            if (activeRootPackageName.isOwnPackage()) {
                return ""
            }
            if (activeRootPackageName.isForegroundCandidatePackage()) {
                return activeRootPackageName
            }

            val focusedOrActiveWindowPackages = windows.asSequence()
                .filter { window -> window.isActive || window.isFocused }
                .mapNotNull { window -> window.root?.packageName?.toString() }
                .toList()
            if (focusedOrActiveWindowPackages.any { packageName -> packageName.isOwnPackage() }) {
                return ""
            }

            val eventPackageName = event.packageName?.toString().orEmpty()
            if (eventPackageName.isOwnPackage()) {
                return ""
            }
            if (eventPackageName.isForegroundCandidatePackage()) {
                return eventPackageName
            }

            windows.asSequence()
                .filter { window ->
                    window.type == AccessibilityWindowInfo.TYPE_APPLICATION ||
                        window.isActive ||
                        window.isFocused
                }
                .mapNotNull { window -> window.root?.packageName?.toString() }
                .firstOrNull { packageName -> packageName.isForegroundCandidatePackage() }
                .orEmpty()
        } catch (_: RuntimeException) {
            ""
        }
    }

    private fun String.isForegroundCandidatePackage(): Boolean {
        return isNotBlank() &&
            !isOwnPackage() &&
            !isIgnoredForegroundPackage(this)
    }

    private fun String.isOwnPackage(): Boolean {
        return this == applicationContext.packageName
    }

    private fun String.shouldClearTrackedForegroundPackage(): Boolean {
        val packageName = lowercase()
        return isOwnPackage() ||
            packageName == "com.android.settings" ||
            ".launcher" in packageName ||
            packageName.contains("packageinstaller") ||
            packageName.contains("permissioncontroller")
    }

    private fun resolveCurrentForegroundPackageName(): String {
        val resolvedPackageName = try {
            val activeRootPackageName = rootInActiveWindow?.packageName?.toString().orEmpty()
            if (activeRootPackageName.isOwnPackage()) {
                clearTrackedForegroundPackage()
                return ""
            }
            if (activeRootPackageName.isForegroundCandidatePackage()) {
                activeRootPackageName
            } else {
                if (activeRootPackageName.shouldClearTrackedForegroundPackage()) {
                    clearTrackedForegroundPackage()
                    return ""
                }
                val focusedOrActiveWindowPackages = windows.asSequence()
                    .filter { window -> window.isActive || window.isFocused }
                    .mapNotNull { window -> window.root?.packageName?.toString() }
                    .toList()
                if (focusedOrActiveWindowPackages.any { packageName -> packageName.isOwnPackage() }) {
                    clearTrackedForegroundPackage()
                    return ""
                }
                if (focusedOrActiveWindowPackages.any { packageName -> packageName.shouldClearTrackedForegroundPackage() }) {
                    clearTrackedForegroundPackage()
                    return ""
                }

                windows.asSequence()
                    .filter { window ->
                        window.type == AccessibilityWindowInfo.TYPE_APPLICATION ||
                            window.isActive ||
                            window.isFocused
                    }
                    .mapNotNull { window -> window.root?.packageName?.toString() }
                    .firstOrNull { packageName -> packageName.isForegroundCandidatePackage() }
                    .orEmpty()
            }
        } catch (_: RuntimeException) {
            ""
        }
        return resolvedPackageName
    }

    @Synchronized
    private fun shouldLogPackage(packageName: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        val lastLoggedAt = lastLoggedAtByPackage[packageName] ?: 0L
        if (now - lastLoggedAt < LOG_THROTTLE_MILLIS) {
            return false
        }
        lastLoggedAtByPackage[packageName] = now
        return true
    }

    @Synchronized
    private fun shouldEvaluatePackage(packageName: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        val lastEvaluatedAt = lastEvaluatedAtByPackage[packageName] ?: 0L
        if (now - lastEvaluatedAt < EVALUATION_THROTTLE_MILLIS) {
            return false
        }
        lastEvaluatedAtByPackage[packageName] = now
        return true
    }

    private fun isIgnoredForegroundPackage(packageName: String): Boolean {
        return AppVisibility.isHiddenPackage(packageName) ||
            isEnabledInputMethodPackage(packageName)
    }

    private fun isEnabledInputMethodPackage(packageName: String): Boolean {
        return try {
            val inputMethodManager = getSystemService(InputMethodManager::class.java)
                ?: return false
            inputMethodManager.enabledInputMethodList.any { inputMethod ->
                inputMethod.packageName == packageName
            }
        } catch (_: RuntimeException) {
            false
        }
    }

    private fun startForegroundEnforcementLoop() {
        foregroundEnforcementJob?.cancel()
        foregroundEnforcementJob = serviceScope.launch {
            while (isActive) {
                delay(FOREGROUND_ENFORCEMENT_INTERVAL_MILLIS)
                if (safeModeEnabled || !policyEnforcementEnabled) {
                    continue
                }
                val packageName = resolveForegroundPackageForEnforcement()
                if (packageName.isBlank() || !shouldEvaluatePackage(packageName)) {
                    continue
                }
                updateTrackedForegroundPackage(packageName)
                val safetyGateResult = SafetyGate.evaluateBlocking(
                    safeModeEnabled = safeModeEnabled,
                    policyEnforcementEnabled = policyEnforcementEnabled,
                    targetPackageName = packageName,
                    userAllowedPackages = allowedAppPackages,
                )
                if (!safetyGateResult.canEvaluateBlocking) {
                    continue
                }
                handleForegroundPackage(
                    packageName = packageName,
                    eventType = FOREGROUND_ENFORCEMENT_EVENT_TYPE,
                )
            }
        }
    }

    private fun cancelForegroundEnforcementLoop() {
        foregroundEnforcementJob?.cancel()
        foregroundEnforcementJob = null
    }

    private suspend fun resolveForegroundPackageForEnforcement(): String {
        val windowPackageName = withContext(Dispatchers.Main) {
            resolveCurrentForegroundPackageName()
        }
        if (windowPackageName.isNotBlank()) {
            return windowPackageName
        }

        val usagePackageName = usageStatsRepository.getCurrentForegroundPackageName().orEmpty()
        if (usagePackageName.isForegroundCandidatePackage()) {
            return usagePackageName
        }

        return currentTrackedForegroundPackageName()
    }

    private fun evaluateBlockDecision(packageName: String, appName: String): BlockDecisionResult {
        val todayUsage = usageStatsRepository.getTodayUsage(maxItems = 500)
        val usageByPackage = todayUsage.associateBy { usage -> usage.packageName }
        val rawAppUsageMillis = usageByPackage[packageName]?.totalTimeMillis ?: 0L
        val adjustedAppUsageMillis = adjustedForegroundUsageMillis(packageName, rawAppUsageMillis)
        val totalUsedMinutes = (
            todayUsage.sumOf { usage -> usage.totalTimeMillis } +
                (adjustedAppUsageMillis - rawAppUsageMillis).coerceAtLeast(0L)
            ).toDisplayMinutes()
        val targetGroup = usagePolicySettings.normalizedAppGroups()
            .firstOrNull { group -> packageName in group.packageNames && group.budgetMinutes > 0 }
        val targetGroupUsedMinutes = targetGroup
            ?.packageNames
            ?.sumOf { groupPackageName ->
                val rawGroupPackageUsageMillis = usageByPackage[groupPackageName]?.totalTimeMillis ?: 0L
                if (groupPackageName == packageName) {
                    adjustedForegroundUsageMillis(groupPackageName, rawGroupPackageUsageMillis)
                } else {
                    rawGroupPackageUsageMillis
                }
            }
            ?.toDisplayMinutes()
        val appUsedMinutes = adjustedAppUsageMillis.toDisplayMinutes()

        return BlockDecisionEngine.evaluate(
            packageName = packageName,
            appName = appName,
            safeModeEnabled = safeModeEnabled,
            policyEnforcementEnabled = policyEnforcementEnabled,
            settings = usagePolicySettings,
            appUsedMinutes = appUsedMinutes,
            totalUsedMinutes = totalUsedMinutes,
            exceededGroupPackages = emptySet(),
            targetGroupUsedMinutes = targetGroupUsedMinutes,
            targetGroupLimitMinutes = targetGroup?.budgetMinutes,
            temporaryUnlockState = temporaryUnlockState,
            userAllowedPackages = allowedAppPackages,
        )
    }

    @Synchronized
    private fun updateTrackedForegroundPackage(packageName: String) {
        if (!packageName.isForegroundCandidatePackage()) {
            return
        }
        if (trackedForegroundPackageName != packageName) {
            trackedForegroundPackageName = packageName
            trackedForegroundStartedAtElapsed = SystemClock.elapsedRealtime()
            trackedForegroundBaselineUsageMillis = null
        }
    }

    @Synchronized
    private fun clearTrackedForegroundPackage() {
        trackedForegroundPackageName = null
        trackedForegroundStartedAtElapsed = 0L
        trackedForegroundBaselineUsageMillis = null
    }

    @Synchronized
    private fun currentTrackedForegroundPackageName(): String {
        return trackedForegroundPackageName.orEmpty()
    }

    @Synchronized
    private fun adjustedForegroundUsageMillis(packageName: String, rawUsageMillis: Long): Long {
        if (trackedForegroundPackageName != packageName || trackedForegroundStartedAtElapsed <= 0L) {
            return rawUsageMillis
        }
        val elapsedMillis = (SystemClock.elapsedRealtime() - trackedForegroundStartedAtElapsed).coerceAtLeast(0L)
        if (trackedForegroundBaselineUsageMillis == null) {
            trackedForegroundBaselineUsageMillis = (rawUsageMillis - elapsedMillis).coerceAtLeast(0L)
        }
        val baselineUsageMillis = trackedForegroundBaselineUsageMillis ?: 0L
        return maxOf(rawUsageMillis, baselineUsageMillis + elapsedMillis)
    }

    private fun launchBlockScreen(result: BlockDecisionResult): Boolean {
        if (!shouldLaunchBlockScreen(result.packageName)) {
            return false
        }

        val intent = BlockedActivity.blockIntent(
            context = applicationContext,
            appName = result.appName,
            packageName = result.packageName,
            reason = result.decision.toLogReason(),
            usedMinutes = result.usedMinutes,
            limitMinutes = result.limitMinutes,
            showAppDetails = result.decision != BlockDecision.WouldBlockTotalLimit,
        )
        startBlockingMediaPauseGuard(result.packageName, result.appName)
        showBlockingOverlay(result, intent)
        return true
    }

    private fun canDrawBlockingOverlay(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
    }

    private fun showBlockingOverlay(result: BlockDecisionResult, controlsIntent: android.content.Intent) {
        serviceScope.launch(Dispatchers.Main) {
            if (blockingOverlayPackageName == result.packageName && blockingOverlayView != null) {
                return@launch
            }
            removeBlockingOverlay()
            val overlayView = createBlockingOverlayView(result, controlsIntent)
            if (addBlockingOverlayViewUsingBestType(overlayView)) {
                blockingOverlayView = overlayView
                blockingOverlayPackageName = result.packageName
                startBlockingOverlayGuard(result, controlsIntent)
            } else {
                blockingOverlayView = null
                blockingOverlayPackageName = null
                logEvent(EventLogType.Safety, "Blocking overlay failed; falling back to blocked activity")
                forceBlockedScreenToFront(controlsIntent, result.packageName)
            }
        }
    }

    private fun addBlockingOverlayViewUsingBestType(overlayView: View): Boolean {
        try {
            addBlockingOverlayView(
                overlayView = overlayView,
                windowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            )
            logEvent(EventLogType.Safety, "Blocking overlay attached: accessibility")
            return true
        } catch (_: RuntimeException) {
            logEvent(EventLogType.Safety, "Accessibility overlay attach failed")
        }

        if (!canDrawBlockingOverlay()) {
            logEvent(EventLogType.Safety, "App overlay permission missing")
            return false
        }

        return try {
            addBlockingOverlayView(
                overlayView = overlayView,
                windowType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            )
            logEvent(EventLogType.Safety, "Blocking overlay attached: application")
            true
        } catch (_: RuntimeException) {
            logEvent(EventLogType.Safety, "Application overlay attach failed")
            false
        }
    }

    private fun addBlockingOverlayView(overlayView: View, windowType: Int) {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            windowType,
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
    }

    private fun startBlockingOverlayGuard(
        result: BlockDecisionResult,
        controlsIntent: android.content.Intent,
    ) {
        blockingOverlayGuardJob?.cancel()
        blockingOverlayGuardJob = serviceScope.launch {
            while (isActive) {
                delay(BLOCKING_OVERLAY_GUARD_INTERVAL_MILLIS)
                val packageName = blockingOverlayPackageName ?: break
                if (!canContinueBlocking(packageName) || !isStillWouldBlock(packageName, result.appName)) {
                    withContext(Dispatchers.Main) {
                        removeBlockingOverlay(cancelGuard = false)
                    }
                    break
                }
                val overlayDetached = withContext(Dispatchers.Main) {
                    val overlayView = blockingOverlayView
                    overlayView == null || !overlayView.isAttachedToWindow
                }
                if (overlayDetached) {
                    withContext(Dispatchers.Main) {
                        blockingOverlayView = null
                        blockingOverlayPackageName = null
                    }
                    logEvent(EventLogType.Safety, "Blocking overlay detached; restoring")
                    showBlockingOverlay(result, controlsIntent)
                    break
                }
                val foregroundPackageName = resolveForegroundPackageForEnforcement()
                if (foregroundPackageName == packageName) {
                    reassertBlockingOverlayOnTop(result, controlsIntent)
                }
            }
        }
    }

    private fun cancelBlockingOverlayGuard() {
        blockingOverlayGuardJob?.cancel()
        blockingOverlayGuardJob = null
    }

    private fun createBlockingOverlayView(
        result: BlockDecisionResult,
        controlsIntent: android.content.Intent,
    ): View {
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
            systemUiVisibility =
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            setOnTouchListener { _, _ ->
                overlayInteractionPausedUntil =
                    SystemClock.elapsedRealtime() + OVERLAY_INTERACTION_PAUSE_MILLIS
                false
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

        card.addView(blockOverlayText("\uCC28\uB2E8\uB428", 14, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(Color.rgb(229, 91, 74))
            }
        })
        card.addView(blockOverlayText(result.toOverlayTitle(), 24, Color.rgb(26, 27, 46), true))
        card.addView(blockOverlayText(result.appName, 20, Color.rgb(26, 27, 46), true))
        card.addView(
            blockOverlayText(
                "${result.usedMinutes.toTimeLabel()} / ${result.limitMinutes?.toTimeLabel().orEmpty()}",
                16,
                Color.rgb(107, 114, 128),
                false,
            ),
        )

        val emergencyInput = EditText(this).apply {
            hint = "\uAE34\uAE09 PIN"
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

        card.addView(blockOverlayButton("\uAE34\uAE09 \uD574\uC81C") {
            val pin = emergencyInput.text?.toString().orEmpty()
            serviceScope.launch(Dispatchers.Main) {
                val unlocked = repository.emergencyUnlock(pin)
                if (unlocked) {
                    removeBlockingOverlay()
                } else {
                    statusText.text = "\uAE34\uAE09 PIN\uC774 \uC62C\uBC14\uB974\uC9C0 \uC54A\uC2B5\uB2C8\uB2E4"
                }
            }
        })
        card.addView(blockOverlayButton("\uAD00\uB9AC \uD654\uBA74 \uC5F4\uAE30") {
            removeBlockingOverlay()
            tryStartBlockActivity(controlsIntent)
        })
        card.addView(blockOverlayButton("Kill Switch") {
            serviceScope.launch(Dispatchers.Main) {
                repository.activateKillSwitch()
                removeBlockingOverlay()
            }
        })
        return root
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

    private fun removeBlockingOverlay(cancelGuard: Boolean = true) {
        if (cancelGuard) {
            cancelBlockingOverlayGuard()
        }
        val overlayView = blockingOverlayView ?: return
        try {
            getSystemService(WindowManager::class.java).removeView(overlayView)
        } catch (_: RuntimeException) {
            // Overlay may already be detached by the system.
        } finally {
            blockingOverlayView = null
            blockingOverlayPackageName = null
            if (cancelGuard) {
                lastOverlayReassertedAt = 0L
            }
        }
    }

    private suspend fun reassertBlockingOverlayOnTop(
        result: BlockDecisionResult,
        controlsIntent: android.content.Intent,
    ) {
        val now = SystemClock.elapsedRealtime()
        if (now < overlayInteractionPausedUntil) {
            return
        }
        if (now - lastOverlayReassertedAt < OVERLAY_REASSERT_INTERVAL_MILLIS) {
            return
        }
        lastOverlayReassertedAt = now

        withContext(Dispatchers.Main) {
            val oldOverlay = blockingOverlayView
            if (oldOverlay != null) {
                try {
                    getSystemService(WindowManager::class.java).removeViewImmediate(oldOverlay)
                } catch (_: RuntimeException) {
                    try {
                        getSystemService(WindowManager::class.java).removeView(oldOverlay)
                    } catch (_: RuntimeException) {
                        // The system may already have detached it.
                    }
                }
            }

            val overlayView = createBlockingOverlayView(result, controlsIntent)
            if (addBlockingOverlayViewUsingBestType(overlayView)) {
                blockingOverlayView = overlayView
                blockingOverlayPackageName = result.packageName
            } else {
                blockingOverlayView = null
                blockingOverlayPackageName = null
                logEvent(EventLogType.Safety, "Blocking overlay reassert failed")
            }
        }
    }

    private suspend fun removeOverlayIfBlockingNoLongerApplies() {
        val packageName = blockingOverlayPackageName ?: return
        if (!canContinueBlocking(packageName) || !isStillWouldBlock(packageName, packageName.toAppName())) {
            withContext(Dispatchers.Main) {
                removeBlockingOverlay()
            }
        }
    }

    private fun BlockDecisionResult.toOverlayTitle(): String {
        return when (decision) {
            BlockDecision.WouldBlockTotalLimit -> "\uC624\uB298 \uC0AC\uC6A9 \uC2DC\uAC04\uC774 \uC885\uB8CC\uB418\uC5C8\uC2B5\uB2C8\uB2E4"
            BlockDecision.WouldBlockGroupLimit -> "\uC571 \uADF8\uB8F9 \uC2DC\uAC04\uC774 \uC885\uB8CC\uB418\uC5C8\uC2B5\uB2C8\uB2E4"
            BlockDecision.WouldBlockAppLimit -> "\uC571 \uC0AC\uC6A9 \uC2DC\uAC04\uC774 \uC885\uB8CC\uB418\uC5C8\uC2B5\uB2C8\uB2E4"
            else -> "\uC0AC\uC6A9 \uC2DC\uAC04\uC774 \uC885\uB8CC\uB418\uC5C8\uC2B5\uB2C8\uB2E4"
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun forceBlockedScreenToFront(intent: android.content.Intent, packageName: String) {
        serviceScope.launch(Dispatchers.Main) {
            val homeSent = performGlobalAction(GLOBAL_ACTION_HOME)
            if (!homeSent) {
                logEvent(EventLogType.Safety, "Home action failed before blocking")
            }
            sendHomeIntent()
            delay(BLOCK_SCREEN_HOME_SETTLE_DELAY_MILLIS)
            if (canContinueBlocking(packageName) && !isOwnAppActiveWindow()) {
                tryStartBlockActivity(intent)
            }
            delay(BLOCK_SCREEN_RETRY_DELAY_MILLIS)
            if (canContinueBlocking(packageName) && !isOwnAppActiveWindow()) {
                tryStartBlockActivity(intent)
            }
        }
    }

    private fun sendHomeIntent() {
        try {
            startActivity(
                android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                    addCategory(android.content.Intent.CATEGORY_HOME)
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                },
            )
        } catch (_: RuntimeException) {
            logEvent(EventLogType.Safety, "Home intent failed before blocking")
        }
    }

    private fun tryStartBlockActivity(intent: android.content.Intent) {
        try {
            startActivity(intent)
        } catch (_: RuntimeException) {
            logEvent(EventLogType.Safety, "BlockedActivity launch failed")
        }
    }

    private fun canContinueBlocking(packageName: String): Boolean {
        val safetyGateResult = SafetyGate.evaluateBlocking(
            safeModeEnabled = safeModeEnabled,
            policyEnforcementEnabled = policyEnforcementEnabled,
            targetPackageName = packageName,
            userAllowedPackages = allowedAppPackages,
        )
        return safetyGateResult.canEvaluateBlocking
    }

    private fun isStillWouldBlock(packageName: String, appName: String): Boolean {
        return try {
            evaluateBlockDecision(packageName, appName).decision.isWouldBlock()
        } catch (_: RuntimeException) {
            false
        }
    }

    private fun isOwnAppActiveWindow(): Boolean {
        return try {
            val activeRootPackageName = rootInActiveWindow?.packageName?.toString().orEmpty()
            activeRootPackageName.isOwnPackage() ||
                windows.asSequence()
                    .filter { window -> window.isActive || window.isFocused }
                    .mapNotNull { window -> window.root?.packageName?.toString() }
                    .any { packageName -> packageName.isOwnPackage() }
        } catch (_: RuntimeException) {
            false
        }
    }

    private fun startBlockingMediaPauseGuard(packageName: String, appName: String) {
        mediaPauseJobsByPackage.remove(packageName)?.cancel()
        mediaPauseJobsByPackage[packageName] = serviceScope.launch {
            while (
                isActive &&
                canContinueBlocking(packageName) &&
                isStillWouldBlock(packageName, appName)
            ) {
                pauseActiveMediaPlayback()
                delay(MEDIA_PAUSE_GUARD_INTERVAL_MILLIS)
            }
            mediaPauseJobsByPackage.remove(packageName)
        }
    }

    private fun cancelMediaPauseGuards() {
        mediaPauseJobsByPackage.values.forEach { job -> job.cancel() }
        mediaPauseJobsByPackage.clear()
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
            // Blocking must still proceed even if the active media session ignores pause.
        }
    }

    @Synchronized
    private fun shouldLaunchBlockScreen(packageName: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        val lastLaunchAt = lastBlockLaunchedAtByPackage[packageName] ?: 0L
        if (now - lastLaunchAt < BLOCK_LAUNCH_COOLDOWN_MILLIS) {
            return false
        }
        lastBlockLaunchedAtByPackage[packageName] = now
        return true
    }

    private fun logEvent(type: EventLogType, message: String) {
        serviceScope.launch {
            repository.addEvent(type, message)
        }
    }

    private fun updateDetectionStatus(appName: String, packageName: String, decision: String) {
        serviceScope.launch {
            repository.updateForegroundDetectionStatus(
                appName = appName,
                packageName = packageName,
                decision = decision,
            )
        }
    }

    private fun String.toAppName(): String {
        return try {
            val applicationInfo = getApplicationInfo(this) ?: return this
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            this
        }
    }

    private fun getApplicationInfo(packageName: String): ApplicationInfo? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(packageName, 0)
            }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun Long.toDisplayMinutes(): Int {
        if (this <= 0L) {
            return 0
        }
        return ((this + 59_999L) / 60_000L).toInt()
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

    private fun BlockDecisionResult.toBlockLogMessage(eventType: Int): String {
        val limitText = limitMinutes?.let { minutes -> " / ${minutes.toTimeLabel()}" }.orEmpty()
        val usageText = "${usedMinutes.toTimeLabel()}$limitText"
        val sourceText = eventType.toBlockSourceText()
        return when (decision) {
            BlockDecision.WouldBlockTotalLimit -> "Blocked daily limit: $usageText ($sourceText)"
            BlockDecision.WouldBlockGroupLimit -> "Blocked group limit: $appName - $usageText ($sourceText)"
            BlockDecision.WouldBlockAppLimit -> "Blocked app limit: $appName - $usageText ($sourceText)"
            else -> "$appName blocked: ${decision.toLogReason()} ($sourceText)"
        }
    }

    private fun Int.toBlockSourceText(): String {
        return when (this) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "window state"
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> "windows changed"
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "window content"
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> "view focused"
            FOREGROUND_ENFORCEMENT_EVENT_TYPE -> "foreground watchdog"
            else -> "accessibility event"
        }
    }

    private fun Int.toTimeLabel(): String {
        val safeMinutes = coerceAtLeast(0)
        val hours = safeMinutes / 60
        val minutes = safeMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            else -> "${minutes}m"
        }
    }

    private companion object {
        const val LOG_THROTTLE_MILLIS = 30_000L
        const val EVALUATION_THROTTLE_MILLIS = 150L
        const val BLOCK_LAUNCH_COOLDOWN_MILLIS = 500L
        const val FOREGROUND_ENFORCEMENT_EVENT_TYPE = -10_001
        const val FOREGROUND_ENFORCEMENT_INTERVAL_MILLIS = 500L
        const val BLOCK_SCREEN_HOME_SETTLE_DELAY_MILLIS = 350L
        const val BLOCK_SCREEN_RETRY_DELAY_MILLIS = 250L
        const val BLOCKING_OVERLAY_GUARD_INTERVAL_MILLIS = 700L
        const val OVERLAY_REASSERT_INTERVAL_MILLIS = 1_000L
        const val OVERLAY_INTERACTION_PAUSE_MILLIS = 4_000L
        const val MEDIA_PAUSE_GUARD_INTERVAL_MILLIS = 750L
    }
}
