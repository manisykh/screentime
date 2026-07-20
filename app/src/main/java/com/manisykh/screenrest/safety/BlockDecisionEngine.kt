package com.manisykh.screenrest.safety

import com.manisykh.screenrest.data.UsagePolicySettings
import com.manisykh.screenrest.data.TemporaryUnlockState
import com.manisykh.screenrest.data.isTemporarilyAllowed
import com.manisykh.screenrest.data.normalizedAppGroups
import com.manisykh.screenrest.data.isScheduleBlockingNow
import com.manisykh.screenrest.ui.safety.appLimitMap
import com.manisykh.screenrest.ui.safety.todayLimitMinutes

data class BlockDecisionResult(
    val packageName: String,
    val appName: String,
    val usedMinutes: Int,
    val limitMinutes: Int? = null,
    val decision: BlockDecision,
)

data class BlockDecisionEvaluation(
    val result: BlockDecisionResult,
    val usedMillis: Long,
    val limitMillis: Long?,
    val baseLimitMinutes: Int?,
    val effectiveLimitMinutes: Int?,
    val activeExtraMinutes: Int,
    val unlockedForToday: Boolean,
    val directlyManaged: Boolean,
)

enum class BlockDecision {
    AllowedSafeMode,
    AllowedPolicyDisabled,
    AllowedWhitelist,
    AllowedNoLimit,
    AllowedUnderLimit,
    WouldBlockTotalLimit,
    WouldBlockSchedule,
    WouldBlockAllowOnly,
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
        scheduleAllowedPackages: Set<String> = emptySet(),
    ): BlockDecisionResult {
        return evaluateDetailed(
            packageName = packageName,
            appName = appName,
            safeModeEnabled = safeModeEnabled,
            policyEnforcementEnabled = policyEnforcementEnabled,
            settings = settings,
            appUsedMillis = appUsedMinutes.toMillisLimit(),
            totalUsedMillis = totalUsedMinutes.toMillisLimit(),
            groupLimitKnownExceeded = packageName in exceededGroupPackages,
            targetGroupUsedMillis = targetGroupUsedMinutes?.toMillisLimit(),
            targetGroupLimitMinutes = targetGroupLimitMinutes,
            temporaryUnlockState = temporaryUnlockState,
            userAllowedPackages = userAllowedPackages,
            scheduleAllowedPackages = scheduleAllowedPackages,
        ).result
    }

    fun evaluateDetailed(
        packageName: String,
        appName: String,
        safeModeEnabled: Boolean,
        policyEnforcementEnabled: Boolean,
        settings: UsagePolicySettings,
        appUsedMillis: Long,
        totalUsedMillis: Long,
        groupLimitKnownExceeded: Boolean = false,
        targetGroupUsedMillis: Long? = null,
        targetGroupLimitMinutes: Int? = null,
        temporaryUnlockState: TemporaryUnlockState = TemporaryUnlockState(),
        userAllowedPackages: Set<String> = emptySet(),
        scheduleAllowedPackages: Set<String> = emptySet(),
    ): BlockDecisionEvaluation {
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
        val packageTemporarilyAllowed = packageAllowance?.isTemporarilyAllowed() == true
        val effectiveTotalLimitMinutes = (totalLimitMinutes + todayTemporaryUnlockState.totalExtraMinutes)
            .coerceAtLeast(totalLimitMinutes)
        val effectiveAppLimitMinutes = (appLimitMinutes + packageExtraMinutes)
            .coerceAtLeast(appLimitMinutes)
        val targetGroup = settings.normalizedAppGroups()
            .firstOrNull { group -> packageName in group.packageNames && group.budgetMinutes > 0 }
        val groupLimitMinutes = targetGroupLimitMinutes ?: targetGroup?.budgetMinutes
        val effectiveGroupLimitMinutes = groupLimitMinutes
            ?.let { limit -> (limit + packageExtraMinutes).coerceAtLeast(limit) }
        val groupLimitExceeded = if (
            targetGroupUsedMillis != null &&
            effectiveGroupLimitMinutes != null &&
            effectiveGroupLimitMinutes > 0
        ) {
            targetGroupUsedMillis >= effectiveGroupLimitMinutes.toMillisLimit()
        } else {
            groupLimitKnownExceeded && packageExtraMinutes <= 0
        }
        val schedulePackageAllowed = SafetyGate.isUserAllowedPackage(
            targetPackageName = packageName,
            userAllowedPackages = scheduleAllowedPackages,
        )
        // Priority is explicit: fail-safe exits first, then mode/time policy blocks.
        val decision = when {
            safetyGateResult.reason == SafetyGateReason.SafeModeEnabled -> BlockDecision.AllowedSafeMode
            safetyGateResult.reason == SafetyGateReason.PolicyEnforcementDisabled -> BlockDecision.AllowedPolicyDisabled
            safetyGateResult.reason == SafetyGateReason.WhitelistedPackage -> BlockDecision.AllowedWhitelist
            settings.allowOnlyModeEnabled && !packageUnlockedForToday && !packageTemporarilyAllowed -> {
                BlockDecision.WouldBlockAllowOnly
            }
            !todayTemporaryUnlockState.totalUnlockedForToday &&
                totalLimitMinutes > 0 &&
                totalUsedMillis >= effectiveTotalLimitMinutes.toMillisLimit() -> {
                BlockDecision.WouldBlockTotalLimit
            }
            !todayTemporaryUnlockState.totalUnlockedForToday &&
                !packageUnlockedForToday &&
                !packageTemporarilyAllowed &&
                settings.isScheduleBlockingNow() &&
                !schedulePackageAllowed -> {
                BlockDecision.WouldBlockSchedule
            }
            !packageUnlockedForToday && groupLimitExceeded -> {
                BlockDecision.WouldBlockGroupLimit
            }
            !packageUnlockedForToday &&
                appLimitMinutes > 0 &&
                appUsedMillis >= effectiveAppLimitMinutes.toMillisLimit() -> {
                BlockDecision.WouldBlockAppLimit
            }
            hasAnyLimit(packageName, settings) -> BlockDecision.AllowedUnderLimit
            else -> BlockDecision.AllowedNoLimit
        }

        val usedMillis = when (decision) {
            BlockDecision.WouldBlockTotalLimit -> totalUsedMillis
            BlockDecision.WouldBlockSchedule -> appUsedMillis
            BlockDecision.WouldBlockAllowOnly -> appUsedMillis
            BlockDecision.WouldBlockGroupLimit -> targetGroupUsedMillis ?: appUsedMillis
            else -> appUsedMillis
        }
        val baseLimitMinutes = when (decision) {
            BlockDecision.WouldBlockTotalLimit -> totalLimitMinutes
            BlockDecision.WouldBlockSchedule -> null
            BlockDecision.WouldBlockAllowOnly -> null
            BlockDecision.WouldBlockGroupLimit -> groupLimitMinutes
            BlockDecision.WouldBlockAppLimit -> appLimitMinutes
            else -> when {
                appLimitMinutes > 0 -> appLimitMinutes
                groupLimitMinutes != null -> groupLimitMinutes
                totalLimitMinutes > 0 -> totalLimitMinutes
                else -> null
            }
        }
        val effectiveLimitMinutes = when (decision) {
            BlockDecision.WouldBlockTotalLimit -> effectiveTotalLimitMinutes
            BlockDecision.WouldBlockSchedule -> null
            BlockDecision.WouldBlockAllowOnly -> null
            BlockDecision.WouldBlockGroupLimit -> effectiveGroupLimitMinutes
            BlockDecision.WouldBlockAppLimit -> effectiveAppLimitMinutes
            else -> when {
                appLimitMinutes > 0 -> effectiveAppLimitMinutes
                effectiveGroupLimitMinutes != null -> effectiveGroupLimitMinutes
                totalLimitMinutes > 0 -> effectiveTotalLimitMinutes
                else -> null
            }
        }
        val activeExtraMinutes = ((effectiveLimitMinutes ?: 0) - (baseLimitMinutes ?: 0)).coerceAtLeast(0)
        val unlockedForToday = when {
            decision == BlockDecision.WouldBlockTotalLimit -> todayTemporaryUnlockState.totalUnlockedForToday
            decision == BlockDecision.WouldBlockSchedule -> {
                todayTemporaryUnlockState.totalUnlockedForToday || packageUnlockedForToday || packageTemporarilyAllowed
            }
            decision == BlockDecision.WouldBlockAllowOnly -> packageUnlockedForToday || packageTemporarilyAllowed
            appLimitMinutes <= 0 && targetGroup == null && totalLimitMinutes > 0 -> {
                todayTemporaryUnlockState.totalUnlockedForToday
            }
            else -> packageUnlockedForToday
        }

        return BlockDecisionEvaluation(
            result = BlockDecisionResult(
                packageName = packageName,
                appName = appName,
                usedMinutes = usedMillis.toDisplayMinutesCeil(),
                limitMinutes = effectiveLimitMinutes,
                decision = decision,
            ),
            usedMillis = usedMillis,
            limitMillis = effectiveLimitMinutes?.toMillisLimit(),
            baseLimitMinutes = baseLimitMinutes,
            effectiveLimitMinutes = effectiveLimitMinutes,
            activeExtraMinutes = activeExtraMinutes,
            unlockedForToday = unlockedForToday,
            directlyManaged = appLimitMinutes > 0 ||
                targetGroup != null ||
                settings.isScheduleBlockingNow() ||
                settings.allowOnlyModeEnabled,
        )
    }

    private fun hasAnyLimit(packageName: String, settings: UsagePolicySettings): Boolean {
        return settings.todayLimitMinutes() > 0 ||
            settings.allowOnlyModeEnabled ||
            settings.normalizedAppGroups().any { group ->
                packageName in group.packageNames && group.budgetMinutes > 0
            } ||
            ((settings.appLimitMap()[packageName] ?: 0) > 0)
    }

    private fun Int.toMillisLimit(): Long {
        return coerceAtLeast(0) * 60_000L
    }

    private fun Long.toDisplayMinutesCeil(): Int {
        if (this <= 0L) {
            return 0
        }
        return ((this + 59_999L) / 60_000L).toInt()
    }
}
