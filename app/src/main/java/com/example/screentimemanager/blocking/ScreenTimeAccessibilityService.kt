package com.example.screentimemanager.blocking

import android.accessibilityservice.AccessibilityService
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import com.example.screentimemanager.data.EventLogType
import com.example.screentimemanager.data.SettingsRepository
import com.example.screentimemanager.data.UsagePolicySettings
import com.example.screentimemanager.data.normalizedAppGroups
import com.example.screentimemanager.data.settingsDataStore
import com.example.screentimemanager.safety.BlockDecision
import com.example.screentimemanager.safety.BlockDecisionEngine
import com.example.screentimemanager.safety.SafetyGate
import com.example.screentimemanager.usage.AppVisibility
import com.example.screentimemanager.usage.UsageStatsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ScreenTimeAccessibilityService : AccessibilityService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val repository by lazy { SettingsRepository(applicationContext.settingsDataStore) }
    private val usageStatsRepository by lazy { UsageStatsRepository(applicationContext) }
    private val lastLoggedAtByPackage = mutableMapOf<String, Long>()

    private var safeModeEnabled: Boolean = true
    private var policyEnforcementEnabled: Boolean = false
    private var usagePolicySettings: UsagePolicySettings = UsagePolicySettings()

    override fun onServiceConnected() {
        super.onServiceConnected()
        startSettingsCollectors()
        logEvent(EventLogType.Safety, "AccessibilityService connected in no-op mode")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val accessibilityEvent = event ?: return
        val packageName = accessibilityEvent.packageName?.toString().orEmpty()
        if (packageName.isBlank() || !accessibilityEvent.isForegroundAppEvent()) {
            return
        }

        if (packageName == applicationContext.packageName) {
            return
        }

        if (isIgnoredForegroundPackage(packageName)) {
            return
        }

        val safetyGateResult = SafetyGate.evaluateBlocking(
            safeModeEnabled = safeModeEnabled,
            policyEnforcementEnabled = policyEnforcementEnabled,
            targetPackageName = packageName,
        )
        if (!safetyGateResult.canEvaluateBlocking) {
            return
        }

        if (!shouldLogPackage(packageName)) {
            return
        }

        serviceScope.launch {
            handleForegroundPackage(packageName)
        }
    }

    private fun handleForegroundPackage(packageName: String) {
        val appName = packageName.toAppName()
        val safetyGateResult = SafetyGate.evaluateBlocking(
            safeModeEnabled = safeModeEnabled,
            policyEnforcementEnabled = policyEnforcementEnabled,
            targetPackageName = packageName,
        )
        if (!safetyGateResult.canEvaluateBlocking) {
            return
        }

        val decision = evaluateBlockDecision(packageName, appName)
        updateDetectionStatus(appName, packageName, decision.toLogReason())
        if (decision.isWouldBlock()) {
            // Actual block screen launch is intentionally not connected yet.
            logEvent(EventLogType.Warning, "Simulation only: $appName would be blocked (${decision.toLogReason()})")
        } else {
            logEvent(EventLogType.Info, "$appName detected: ${decision.toLogReason()}")
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        logEvent(EventLogType.Safety, "AccessibilityService disconnected")
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startSettingsCollectors() {
        serviceScope.launch {
            repository.safeModeEnabled.collectLatest { enabled ->
                safeModeEnabled = enabled
            }
        }
        serviceScope.launch {
            repository.policyEnforcementEnabled.collectLatest { enabled ->
                policyEnforcementEnabled = enabled
            }
        }
        serviceScope.launch {
            repository.usagePolicySettings.collectLatest { settings ->
                usagePolicySettings = settings
            }
        }
    }

    private fun AccessibilityEvent.isForegroundAppEvent(): Boolean {
        return eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
    }

    private fun shouldLogPackage(packageName: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        val lastLoggedAt = lastLoggedAtByPackage[packageName] ?: 0L
        if (now - lastLoggedAt < LOG_THROTTLE_MILLIS) {
            return false
        }
        lastLoggedAtByPackage[packageName] = now
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

    private fun evaluateBlockDecision(packageName: String, appName: String): BlockDecision {
        val todayUsage = usageStatsRepository.getTodayUsage(maxItems = 500)
        val usageByPackage = todayUsage.associateBy { usage -> usage.packageName }
        val totalUsedMinutes = todayUsage.sumOf { usage -> usage.totalTimeMillis }.toDisplayMinutes()
        val exceededGroupPackages = usagePolicySettings.normalizedAppGroups()
            .filter { group ->
                group.budgetMinutes > 0 &&
                    group.packageNames.sumOf { groupPackageName ->
                        usageByPackage[groupPackageName]?.totalTimeMillis ?: 0L
                    }.toDisplayMinutes() >= group.budgetMinutes
            }
            .flatMap { group -> group.packageNames }
            .toSet()
        val appUsedMinutes = (usageByPackage[packageName]?.totalTimeMillis ?: 0L).toDisplayMinutes()

        return BlockDecisionEngine.evaluate(
            packageName = packageName,
            appName = appName,
            safeModeEnabled = safeModeEnabled,
            policyEnforcementEnabled = policyEnforcementEnabled,
            settings = usagePolicySettings,
            appUsedMinutes = appUsedMinutes,
            totalUsedMinutes = totalUsedMinutes,
            exceededGroupPackages = exceededGroupPackages,
        ).decision
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

    private companion object {
        const val LOG_THROTTLE_MILLIS = 30_000L
    }
}
