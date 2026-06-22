package com.example.screentimemanager.safety

import com.example.screentimemanager.data.UsagePolicySettings
import com.example.screentimemanager.data.TemporaryUnlockState
import com.example.screentimemanager.data.normalizedAppGroups
import com.example.screentimemanager.ui.safety.appLimitMap
import com.example.screentimemanager.ui.safety.todayLimitMinutes

data class BlockDecisionResult(
    val packageName: String,
    val appName: String,
    val usedMinutes: Int,
    val limitMinutes: Int? = null,
    val decision: BlockDecision,
)

enum class BlockDecision {
    AllowedSafeMode,
    AllowedPolicyDisabled,
    AllowedWhitelist,
    AllowedNoLimit,
    AllowedUnderLimit,
    WouldBlockTotalLimit,
    WouldBlockGroupLimit,
    WouldBlockAppLimit,
}

object BlockDecisionEngine {
    fun evaluate(
        packageName: String,
        appName: String,
        safeModeEnabled: Boolean,
        policyEnforcementEnabled: Boolean,
        settings: UsagePolicySettings,
        appUsedMinutes: Int,
        totalUsedMinutes: Int,
        exceededGroupPackages: Set<String>,
        targetGroupUsedMinutes: Int? = null,
        targetGroupLimitMinutes: Int? = null,
        temporaryUnlockState: TemporaryUnlockState = TemporaryUnlockState(),
        userAllowedPackages: Set<String> = emptySet(),
    ): BlockDecisionResult {
        val safetyGateResult = SafetyGate.evaluateBlocking(
            safeModeEnabled = safeModeEnabled,
            policyEnforcementEnabled = policyEnforcementEnabled,
            targetPackageName = packageName,
            userAllowedPackages = userAllowedPackages,
        )
        val appLimitMinutes = settings.appLimitMap()[packageName] ?: 0
        val totalLimitMinutes = settings.todayLimitMinutes()
        val todayTemporaryUnlockState = temporaryUnlockState.forToday()
        val packageAllowance = todayTemporaryUnlockState.packageAllowances[packageName]
        val packageExtraMinutes = packageAllowance?.extraMinutes ?: 0
        val packageUnlockedForToday = packageAllowance?.unlockedForToday == true
        val effectiveTotalLimitMinutes = (totalLimitMinutes + todayTemporaryUnlockState.totalExtraMinutes)
            .coerceAtLeast(totalLimitMinutes)
        val effectiveAppLimitMinutes = (appLimitMinutes + packageExtraMinutes)
            .coerceAtLeast(appLimitMinutes)
        val exceededGroupLimitMinutes = settings.normalizedAppGroups()
            .firstOrNull { group -> packageName in group.packageNames && group.budgetMinutes > 0 }
            ?.budgetMinutes
        val effectiveGroupLimitMinutes = targetGroupLimitMinutes
            ?.let { limit -> (limit + packageExtraMinutes).coerceAtLeast(limit) }
            ?: exceededGroupLimitMinutes?.let { limit -> (limit + packageExtraMinutes).coerceAtLeast(limit) }
        val groupLimitExceeded = if (
            targetGroupUsedMinutes != null &&
            effectiveGroupLimitMinutes != null &&
            effectiveGroupLimitMinutes > 0
        ) {
            targetGroupUsedMinutes >= effectiveGroupLimitMinutes
        } else {
            packageName in exceededGroupPackages && packageExtraMinutes <= 0
        }
        val decision = when {
            safetyGateResult.reason == SafetyGateReason.SafeModeEnabled -> BlockDecision.AllowedSafeMode
            safetyGateResult.reason == SafetyGateReason.PolicyEnforcementDisabled -> BlockDecision.AllowedPolicyDisabled
            safetyGateResult.reason == SafetyGateReason.WhitelistedPackage -> BlockDecision.AllowedWhitelist
            !todayTemporaryUnlockState.totalUnlockedForToday &&
                totalLimitMinutes > 0 &&
                totalUsedMinutes >= effectiveTotalLimitMinutes -> {
                BlockDecision.WouldBlockTotalLimit
            }
            !packageUnlockedForToday && groupLimitExceeded -> {
                BlockDecision.WouldBlockGroupLimit
            }
            !packageUnlockedForToday && appLimitMinutes > 0 && appUsedMinutes >= effectiveAppLimitMinutes -> {
                BlockDecision.WouldBlockAppLimit
            }
            hasAnyLimit(packageName, settings) -> BlockDecision.AllowedUnderLimit
            else -> BlockDecision.AllowedNoLimit
        }

        return BlockDecisionResult(
            packageName = packageName,
            appName = appName,
            usedMinutes = if (decision == BlockDecision.WouldBlockTotalLimit) {
                totalUsedMinutes
            } else {
                appUsedMinutes
            },
            limitMinutes = when (decision) {
                BlockDecision.WouldBlockTotalLimit -> effectiveTotalLimitMinutes
                BlockDecision.WouldBlockGroupLimit -> effectiveGroupLimitMinutes
                BlockDecision.WouldBlockAppLimit -> effectiveAppLimitMinutes
                else -> null
            },
            decision = decision,
        )
    }

    private fun hasAnyLimit(packageName: String, settings: UsagePolicySettings): Boolean {
        return settings.todayLimitMinutes() > 0 ||
            settings.normalizedAppGroups().any { group ->
                packageName in group.packageNames && group.budgetMinutes > 0
            } ||
            ((settings.appLimitMap()[packageName] ?: 0) > 0)
    }
}
