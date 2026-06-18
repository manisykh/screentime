package com.example.screentimemanager.safety

import com.example.screentimemanager.data.UsagePolicySettings
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
    ): BlockDecisionResult {
        val safetyGateResult = SafetyGate.evaluateBlocking(
            safeModeEnabled = safeModeEnabled,
            policyEnforcementEnabled = policyEnforcementEnabled,
            targetPackageName = packageName,
        )
        val appLimitMinutes = settings.appLimitMap()[packageName] ?: 0
        val totalLimitMinutes = settings.todayLimitMinutes()
        val exceededGroupLimitMinutes = settings.normalizedAppGroups()
            .firstOrNull { group -> packageName in group.packageNames && group.budgetMinutes > 0 }
            ?.budgetMinutes
        val decision = when {
            safetyGateResult.reason == SafetyGateReason.SafeModeEnabled -> BlockDecision.AllowedSafeMode
            safetyGateResult.reason == SafetyGateReason.PolicyEnforcementDisabled -> BlockDecision.AllowedPolicyDisabled
            safetyGateResult.reason == SafetyGateReason.WhitelistedPackage -> BlockDecision.AllowedWhitelist
            totalLimitMinutes > 0 && totalUsedMinutes >= totalLimitMinutes -> {
                BlockDecision.WouldBlockTotalLimit
            }
            packageName in exceededGroupPackages -> {
                BlockDecision.WouldBlockGroupLimit
            }
            appLimitMinutes > 0 && appUsedMinutes >= appLimitMinutes -> {
                BlockDecision.WouldBlockAppLimit
            }
            hasAnyLimit(packageName, settings) -> BlockDecision.AllowedUnderLimit
            else -> BlockDecision.AllowedNoLimit
        }

        return BlockDecisionResult(
            packageName = packageName,
            appName = appName,
            usedMinutes = appUsedMinutes,
            limitMinutes = when (decision) {
                BlockDecision.WouldBlockTotalLimit -> totalLimitMinutes
                BlockDecision.WouldBlockGroupLimit -> exceededGroupLimitMinutes
                BlockDecision.WouldBlockAppLimit -> appLimitMinutes
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
