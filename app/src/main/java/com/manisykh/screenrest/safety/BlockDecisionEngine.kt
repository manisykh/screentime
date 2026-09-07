package com.manisykh.screenrest.safety

import com.manisykh.screenrest.data.UsagePolicySettings
import com.manisykh.screenrest.data.TemporaryUnlockState
import com.manisykh.screenrest.data.HardshipLevel
import com.manisykh.screenrest.data.HardshipPolicyType
import com.manisykh.screenrest.data.HardshipPolicyKey
import com.manisykh.screenrest.data.activeScheduleTemplates
import com.manisykh.screenrest.data.appliesOn
import com.manisykh.screenrest.data.allowOnlyHardshipKey
import com.manisykh.screenrest.data.appGroupHardshipKey
import com.manisykh.screenrest.data.appLimitHardshipKey
import com.manisykh.screenrest.data.dailyHardshipKey
import com.manisykh.screenrest.data.hardshipLevelFor
import com.manisykh.screenrest.data.isTemporarilyAllowed
import com.manisykh.screenrest.data.normalizedAppGroups
import com.manisykh.screenrest.data.isScheduleBlockingNow
import com.manisykh.screenrest.data.limitMinutesOrNull
import com.manisykh.screenrest.data.currentPolicyDayOfWeek
import com.manisykh.screenrest.data.scheduleHardshipKey
import com.manisykh.screenrest.ui.safety.activeAppLimitMap
import com.manisykh.screenrest.ui.safety.todayLimitMinutesOrNull

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
    val hardshipPolicyType: HardshipPolicyType? = null,
    val hardshipPolicyKey: HardshipPolicyKey? = null,
    val hardshipLevel: HardshipLevel = HardshipLevel.Off,
    val activeBlockDecisions: Set<BlockDecision> = emptySet(),
    val activeHardshipPolicyKeys: Set<HardshipPolicyKey> = emptySet(),
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

private data class HardshipBlockCandidate(
    val decision: BlockDecision,
    val policyKey: HardshipPolicyKey,
)

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
        allowOnlyAllowedPackages: Set<String> = emptySet(),
        userAllowedPackages: Set<String> = emptySet(),
        scheduleAllowedPackages: Set<String> = emptySet(),
        hardshipBypassedPolicies: Set<HardshipPolicyType> = emptySet(),
        hardshipBypassedPolicyKeys: Set<HardshipPolicyKey> = emptySet(),
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
            allowOnlyAllowedPackages = allowOnlyAllowedPackages,
            userAllowedPackages = userAllowedPackages,
            scheduleAllowedPackages = scheduleAllowedPackages,
            hardshipBypassedPolicies = hardshipBypassedPolicies,
            hardshipBypassedPolicyKeys = hardshipBypassedPolicyKeys,
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
        allowOnlyAllowedPackages: Set<String> = emptySet(),
        userAllowedPackages: Set<String> = emptySet(),
        scheduleAllowedPackages: Set<String> = emptySet(),
        hardshipBypassedPolicies: Set<HardshipPolicyType> = emptySet(),
        hardshipBypassedPolicyKeys: Set<HardshipPolicyKey> = emptySet(),
    ): BlockDecisionEvaluation {
        val safetyGateResult = SafetyGate.evaluateBlocking(
            safeModeEnabled = safeModeEnabled,
            policyEnforcementEnabled = policyEnforcementEnabled,
            targetPackageName = packageName,
            userAllowedPackages = userAllowedPackages,
        )
        val appLimitMinutes = settings.activeAppLimitMap()[packageName]
        val totalLimitMinutes = settings.todayLimitMinutesOrNull()
        val todayTemporaryUnlockState = temporaryUnlockState.forToday()
        val packageAllowance = todayTemporaryUnlockState.packageAllowances[packageName]
        val packageExtraMinutes = packageAllowance?.extraMinutes ?: 0
        val packageUnlockedForToday = packageAllowance?.unlockedForToday == true
        val packageTemporarilyAllowed = packageAllowance?.isTemporarilyAllowed() == true
        val effectiveTotalLimitMinutes = totalLimitMinutes
            ?.let { limit -> (limit + todayTemporaryUnlockState.totalExtraMinutes).coerceAtLeast(limit) }
        val effectiveAppLimitMinutes = appLimitMinutes
            ?.let { limit -> (limit + packageExtraMinutes).coerceAtLeast(limit) }
        val targetGroup = settings.normalizedAppGroups()
            .firstOrNull { group ->
                packageName in group.packageNames &&
                    group.appliesOn(currentPolicyDayOfWeek()) &&
                    group.limitMinutesOrNull() != null
            }
        val groupLimitMinutes = targetGroupLimitMinutes?.coerceAtLeast(0) ?: targetGroup?.limitMinutesOrNull()
        val effectiveGroupLimitMinutes = groupLimitMinutes
            ?.let { limit -> (limit + packageExtraMinutes).coerceAtLeast(limit) }
        val groupLimitExceeded = if (
            targetGroupUsedMillis != null &&
            effectiveGroupLimitMinutes != null
        ) {
            targetGroupUsedMillis >= effectiveGroupLimitMinutes.toMillisLimit()
        } else {
            groupLimitKnownExceeded && packageExtraMinutes <= 0
        }
        val schedulePackageAllowed = SafetyGate.isUserAllowedPackage(
            targetPackageName = packageName,
            userAllowedPackages = scheduleAllowedPackages,
        )
        val allowOnlyPackageAllowed = SafetyGate.isUserAllowedPackage(
            targetPackageName = packageName,
            userAllowedPackages = allowOnlyAllowedPackages,
        )
        // Only one app-usage-range policy may control entry at a time. An
        // active schedule temporarily takes precedence over the manual
        // allow-only mode; the manual mode naturally resumes after the
        // schedule occurrence ends.
        val activeSchedules = settings.activeScheduleTemplates()
        fun isHardshipBypassed(key: HardshipPolicyKey): Boolean {
            return key.policyType in hardshipBypassedPolicies || key in hardshipBypassedPolicyKeys
        }
        val activeBlockCandidates = if (!safetyGateResult.canEvaluateBlocking) {
            emptyList()
        } else buildList {
            val allowOnlyKey = allowOnlyHardshipKey()
            if (
                !isHardshipBypassed(allowOnlyKey) &&
                activeSchedules.isEmpty() &&
                settings.allowOnlyModeEnabled &&
                !allowOnlyPackageAllowed &&
                !packageUnlockedForToday &&
                !packageTemporarilyAllowed
            ) {
                add(HardshipBlockCandidate(BlockDecision.WouldBlockAllowOnly, allowOnlyKey))
            }
            val dailyKey = dailyHardshipKey()
            if (
                !todayTemporaryUnlockState.totalUnlockedForToday &&
                !isHardshipBypassed(dailyKey) &&
                totalLimitMinutes != null &&
                effectiveTotalLimitMinutes != null &&
                totalUsedMillis >= effectiveTotalLimitMinutes.toMillisLimit()
            ) {
                add(HardshipBlockCandidate(BlockDecision.WouldBlockTotalLimit, dailyKey))
            }
            if (
                !todayTemporaryUnlockState.totalUnlockedForToday &&
                !packageUnlockedForToday &&
                !packageTemporarilyAllowed
            ) {
                activeSchedules.forEach { schedule ->
                    val scheduleKey = scheduleHardshipKey(schedule.id)
                    val allowedByThisSchedule = SafetyGate.isUserAllowedPackage(
                        targetPackageName = packageName,
                        userAllowedPackages = schedule.allowedPackageNames,
                    )
                    if (
                        !isHardshipBypassed(scheduleKey) &&
                        !allowedByThisSchedule &&
                        !schedulePackageAllowed
                    ) {
                        add(HardshipBlockCandidate(BlockDecision.WouldBlockSchedule, scheduleKey))
                    }
                }
            }
            val groupKey = targetGroup?.let { group -> appGroupHardshipKey(group.id) }
            if (
                groupKey != null &&
                !isHardshipBypassed(groupKey) &&
                !packageUnlockedForToday &&
                groupLimitExceeded
            ) {
                add(HardshipBlockCandidate(BlockDecision.WouldBlockGroupLimit, groupKey))
            }
            val appLimitKey = appLimitHardshipKey(packageName)
            if (
                !packageUnlockedForToday &&
                !isHardshipBypassed(appLimitKey) &&
                appLimitMinutes != null &&
                effectiveAppLimitMinutes != null &&
                appUsedMillis >= effectiveAppLimitMinutes.toMillisLimit()
            ) {
                add(HardshipBlockCandidate(BlockDecision.WouldBlockAppLimit, appLimitKey))
            }
        }
        val strongestBlockCandidate = activeBlockCandidates.maxByOrNull { candidate ->
            settings.hardshipLevelFor(candidate.policyKey).storageValue * 10 +
                candidate.decision.defaultBlockPriority()
        }
        val strongestBlockDecision = strongestBlockCandidate?.decision
        // Fail-safe exits remain absolute. Otherwise the strongest hardship level wins;
        // ties preserve the existing policy priority.
        val decision = when {
            safetyGateResult.reason == SafetyGateReason.SafeModeEnabled -> BlockDecision.AllowedSafeMode
            safetyGateResult.reason == SafetyGateReason.PolicyEnforcementDisabled -> BlockDecision.AllowedPolicyDisabled
            safetyGateResult.reason == SafetyGateReason.WhitelistedPackage -> BlockDecision.AllowedWhitelist
            strongestBlockDecision != null -> strongestBlockDecision
            hasAnyLimit(packageName, settings) -> BlockDecision.AllowedUnderLimit
            else -> BlockDecision.AllowedNoLimit
        }
        val hardshipPolicyKey = strongestBlockCandidate?.policyKey
        val hardshipPolicyType = hardshipPolicyKey?.policyType
        val hardshipLevel = hardshipPolicyKey?.let(settings::hardshipLevelFor) ?: HardshipLevel.Off

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
                appLimitMinutes != null -> appLimitMinutes
                groupLimitMinutes != null -> groupLimitMinutes
                totalLimitMinutes != null -> totalLimitMinutes
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
                appLimitMinutes != null -> effectiveAppLimitMinutes
                effectiveGroupLimitMinutes != null -> effectiveGroupLimitMinutes
                totalLimitMinutes != null -> effectiveTotalLimitMinutes
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
            appLimitMinutes == null && targetGroup == null && totalLimitMinutes != null -> {
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
            directlyManaged = appLimitMinutes != null ||
                targetGroup != null ||
                settings.isScheduleBlockingNow() ||
                settings.allowOnlyModeEnabled,
            hardshipPolicyType = hardshipPolicyType,
            hardshipPolicyKey = hardshipPolicyKey,
            hardshipLevel = hardshipLevel,
            activeBlockDecisions = activeBlockCandidates.map { candidate -> candidate.decision }.toSet(),
            activeHardshipPolicyKeys = activeBlockCandidates.map { candidate -> candidate.policyKey }.toSet(),
        )
    }

    private fun hasAnyLimit(packageName: String, settings: UsagePolicySettings): Boolean {
        return settings.todayLimitMinutesOrNull() != null ||
            settings.allowOnlyModeEnabled ||
            settings.normalizedAppGroups().any { group ->
                packageName in group.packageNames &&
                    group.appliesOn(currentPolicyDayOfWeek()) &&
                    group.limitMinutesOrNull() != null
            } ||
            settings.activeAppLimitMap().containsKey(packageName)
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

fun BlockDecision.hardshipPolicyTypeOrNull(): HardshipPolicyType? {
    return when (this) {
        BlockDecision.WouldBlockTotalLimit -> HardshipPolicyType.DailyLimit
        BlockDecision.WouldBlockGroupLimit -> HardshipPolicyType.AppGroups
        BlockDecision.WouldBlockAppLimit -> HardshipPolicyType.AppLimits
        BlockDecision.WouldBlockSchedule -> HardshipPolicyType.Schedule
        BlockDecision.WouldBlockAllowOnly -> HardshipPolicyType.AllowOnly
        else -> null
    }
}

private fun BlockDecision.defaultBlockPriority(): Int {
    return when (this) {
        BlockDecision.WouldBlockAllowOnly -> 5
        BlockDecision.WouldBlockTotalLimit -> 4
        BlockDecision.WouldBlockSchedule -> 3
        BlockDecision.WouldBlockGroupLimit -> 2
        BlockDecision.WouldBlockAppLimit -> 1
        else -> 0
    }
}
