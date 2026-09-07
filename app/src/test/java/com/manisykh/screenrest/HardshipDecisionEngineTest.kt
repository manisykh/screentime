package com.manisykh.screenrest

import com.manisykh.screenrest.data.HardshipLevel
import com.manisykh.screenrest.data.HardshipPolicyType
import com.manisykh.screenrest.data.AppGroupPolicy
import com.manisykh.screenrest.data.EXPLICIT_ZERO_LIMIT_STORAGE_MINUTES
import com.manisykh.screenrest.data.ScheduleTemplatePolicy
import com.manisykh.screenrest.data.UsagePolicySettings
import com.manisykh.screenrest.data.appLimitHardshipKey
import com.manisykh.screenrest.data.currentPolicyDayOfWeek
import com.manisykh.screenrest.data.scheduleHardshipKey
import com.manisykh.screenrest.data.toAppLimitHardshipLevelsEncoded
import com.manisykh.screenrest.data.toAppGroupsEncoded
import com.manisykh.screenrest.data.toAppLimitActiveDaysEncoded
import com.manisykh.screenrest.data.toScheduleTemplatesEncoded
import com.manisykh.screenrest.safety.BlockDecision
import com.manisykh.screenrest.safety.BlockDecisionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class HardshipDecisionEngineTest {
    @Test
    fun disabledDailyPolicy_preservesLimitsWithoutBlocking() {
        val settings = UsagePolicySettings(
            mondayLimitMinutes = 30,
            tuesdayLimitMinutes = 30,
            wednesdayLimitMinutes = 30,
            thursdayLimitMinutes = 30,
            fridayLimitMinutes = 30,
            saturdayLimitMinutes = 30,
            sundayLimitMinutes = 30,
            dailyPolicyEnabled = false,
        )

        val evaluation = BlockDecisionEngine.evaluateDetailed(
            packageName = "com.example.video",
            appName = "Video",
            safeModeEnabled = false,
            policyEnforcementEnabled = true,
            settings = settings,
            appUsedMillis = 31L * 60_000L,
            totalUsedMillis = 31L * 60_000L,
        )

        assertEquals(BlockDecision.AllowedNoLimit, evaluation.result.decision)
        assertTrue(BlockDecision.WouldBlockTotalLimit !in evaluation.activeBlockDecisions)
        assertEquals(30, settings.mondayLimitMinutes)
    }

    @Test
    fun overlappingBlocks_selectHighestHardshipLevel() {
        val settings = UsagePolicySettings(
            mondayLimitMinutes = 60,
            tuesdayLimitMinutes = 60,
            wednesdayLimitMinutes = 60,
            thursdayLimitMinutes = 60,
            fridayLimitMinutes = 60,
            saturdayLimitMinutes = 60,
            sundayLimitMinutes = 60,
            appLimitRules = "com.example.video=30",
            allowOnlyModeEnabled = true,
            dailyHardshipLevel = HardshipLevel.Level2,
            appLimitsHardshipLevel = HardshipLevel.Level3,
            allowOnlyHardshipLevel = HardshipLevel.Level1,
        )

        val evaluation = BlockDecisionEngine.evaluateDetailed(
            packageName = "com.example.video",
            appName = "Video",
            safeModeEnabled = false,
            policyEnforcementEnabled = true,
            settings = settings,
            appUsedMillis = 31L * 60_000L,
            totalUsedMillis = 61L * 60_000L,
        )

        assertEquals(BlockDecision.WouldBlockAppLimit, evaluation.result.decision)
        assertEquals(HardshipPolicyType.AppLimits, evaluation.hardshipPolicyType)
        assertEquals(HardshipLevel.Level3, evaluation.hardshipLevel)
        assertEquals(3, evaluation.activeBlockDecisions.size)
    }

    @Test
    fun equalHardshipLevels_preserveExistingBlockPriority() {
        val settings = UsagePolicySettings(
            appLimitRules = "com.example.video=30",
            allowOnlyModeEnabled = true,
        )

        val evaluation = BlockDecisionEngine.evaluateDetailed(
            packageName = "com.example.video",
            appName = "Video",
            safeModeEnabled = false,
            policyEnforcementEnabled = true,
            settings = settings,
            appUsedMillis = 31L * 60_000L,
            totalUsedMillis = 0L,
        )

        assertEquals(BlockDecision.WouldBlockAllowOnly, evaluation.result.decision)
        assertEquals(HardshipPolicyType.AllowOnly, evaluation.hardshipPolicyType)
    }

    @Test
    fun emergencyPassBypass_removesOnlySelectedPolicy() {
        val settings = UsagePolicySettings(
            appLimitRules = "com.example.video=30",
            allowOnlyModeEnabled = true,
            appLimitsHardshipLevel = HardshipLevel.Level3,
            allowOnlyHardshipLevel = HardshipLevel.Level2,
        )

        val evaluation = BlockDecisionEngine.evaluateDetailed(
            packageName = "com.example.video",
            appName = "Video",
            safeModeEnabled = false,
            policyEnforcementEnabled = true,
            settings = settings,
            appUsedMillis = 31L * 60_000L,
            totalUsedMillis = 0L,
            hardshipBypassedPolicies = setOf(HardshipPolicyType.AppLimits),
        )

        assertEquals(BlockDecision.WouldBlockAllowOnly, evaluation.result.decision)
        assertEquals(HardshipLevel.Level2, evaluation.hardshipLevel)
        assertTrue(BlockDecision.WouldBlockAppLimit !in evaluation.activeBlockDecisions)
    }

    @Test
    fun appLimitHardship_isIndependentPerApp() {
        val settings = UsagePolicySettings(
            appLimitRules = "com.example.one=10|com.example.two=10",
            appLimitHardshipLevels = mapOf(
                "com.example.one" to HardshipLevel.Level3,
                "com.example.two" to HardshipLevel.Level1,
            ).toAppLimitHardshipLevelsEncoded(),
        )

        val first = BlockDecisionEngine.evaluateDetailed(
            packageName = "com.example.one",
            appName = "One",
            safeModeEnabled = false,
            policyEnforcementEnabled = true,
            settings = settings,
            appUsedMillis = 11L * 60_000L,
            totalUsedMillis = 0L,
        )
        val second = BlockDecisionEngine.evaluateDetailed(
            packageName = "com.example.two",
            appName = "Two",
            safeModeEnabled = false,
            policyEnforcementEnabled = true,
            settings = settings,
            appUsedMillis = 11L * 60_000L,
            totalUsedMillis = 0L,
        )

        assertEquals(HardshipLevel.Level3, first.hardshipLevel)
        assertEquals(appLimitHardshipKey("com.example.one"), first.hardshipPolicyKey)
        assertEquals(HardshipLevel.Level1, second.hardshipLevel)
        assertEquals(appLimitHardshipKey("com.example.two"), second.hardshipPolicyKey)
    }

    @Test
    fun keyedEmergencyPassBypass_doesNotBypassAnotherAppLimit() {
        val settings = UsagePolicySettings(
            appLimitRules = "com.example.one=10|com.example.two=10",
            appLimitHardshipLevels = mapOf(
                "com.example.one" to HardshipLevel.Level3,
                "com.example.two" to HardshipLevel.Level3,
            ).toAppLimitHardshipLevelsEncoded(),
        )

        val second = BlockDecisionEngine.evaluateDetailed(
            packageName = "com.example.two",
            appName = "Two",
            safeModeEnabled = false,
            policyEnforcementEnabled = true,
            settings = settings,
            appUsedMillis = 11L * 60_000L,
            totalUsedMillis = 0L,
            hardshipBypassedPolicyKeys = setOf(appLimitHardshipKey("com.example.one")),
        )

        assertEquals(BlockDecision.WouldBlockAppLimit, second.result.decision)
        assertEquals(appLimitHardshipKey("com.example.two"), second.hardshipPolicyKey)
    }

    @Test
    fun overlappingSchedules_selectStrongestScheduleItem() {
        val now = LocalDateTime.now()
        val minute = now.hour * 60 + now.minute
        val schedules = listOf(
            ScheduleTemplatePolicy(
                id = "gentle",
                name = "Gentle",
                startMinutes = (minute + 1439) % 1440,
                endMinutes = (minute + 2) % 1440,
                days = (1..7).toSet(),
                hardshipLevel = HardshipLevel.Level1,
            ),
            ScheduleTemplatePolicy(
                id = "strict",
                name = "Strict",
                startMinutes = (minute + 1439) % 1440,
                endMinutes = (minute + 2) % 1440,
                days = (1..7).toSet(),
                hardshipLevel = HardshipLevel.Level3,
            ),
        )
        val settings = UsagePolicySettings(
            mondayLimitMinutes = 0,
            tuesdayLimitMinutes = 0,
            wednesdayLimitMinutes = 0,
            thursdayLimitMinutes = 0,
            fridayLimitMinutes = 0,
            saturdayLimitMinutes = 0,
            sundayLimitMinutes = 0,
            scheduleBlockingEnabled = true,
            scheduleTemplates = schedules.toScheduleTemplatesEncoded(),
        )

        val evaluation = BlockDecisionEngine.evaluateDetailed(
            packageName = "com.example.video",
            appName = "Video",
            safeModeEnabled = false,
            policyEnforcementEnabled = true,
            settings = settings,
            appUsedMillis = 0L,
            totalUsedMillis = 0L,
        )

        assertEquals(BlockDecision.WouldBlockSchedule, evaluation.result.decision)
        assertEquals(scheduleHardshipKey("strict"), evaluation.hardshipPolicyKey)
        assertEquals(HardshipLevel.Level3, evaluation.hardshipLevel)
        assertEquals(2, evaluation.activeHardshipPolicyKeys.size)
    }

    @Test
    fun absentAppLimit_doesNotCreateTimePolicy() {
        val evaluation = BlockDecisionEngine.evaluateDetailed(
            packageName = "com.example.video",
            appName = "Video",
            safeModeEnabled = false,
            policyEnforcementEnabled = true,
            settings = UsagePolicySettings(dailyPolicyEnabled = false),
            appUsedMillis = 0L,
            totalUsedMillis = 0L,
        )

        assertEquals(BlockDecision.AllowedNoLimit, evaluation.result.decision)
    }

    @Test
    fun explicitZeroAppLimit_blocksImmediately() {
        val evaluation = BlockDecisionEngine.evaluateDetailed(
            packageName = "com.example.video",
            appName = "Video",
            safeModeEnabled = false,
            policyEnforcementEnabled = true,
            settings = UsagePolicySettings(
                dailyPolicyEnabled = false,
                appLimitRules = "com.example.video=0",
            ),
            appUsedMillis = 0L,
            totalUsedMillis = 0L,
        )

        assertEquals(BlockDecision.WouldBlockAppLimit, evaluation.result.decision)
        assertEquals(0, evaluation.result.limitMinutes ?: -1)
    }

    @Test
    fun legacyZeroDailyLimitsRemainUnlimited_butExplicitZeroBlocks() {
        fun settings(rawMinutes: Int) = UsagePolicySettings(
            dailyPolicyEnabled = true,
            mondayLimitMinutes = rawMinutes,
            tuesdayLimitMinutes = rawMinutes,
            wednesdayLimitMinutes = rawMinutes,
            thursdayLimitMinutes = rawMinutes,
            fridayLimitMinutes = rawMinutes,
            saturdayLimitMinutes = rawMinutes,
            sundayLimitMinutes = rawMinutes,
        )
        fun evaluate(settings: UsagePolicySettings) = BlockDecisionEngine.evaluateDetailed(
            packageName = "com.example.video",
            appName = "Video",
            safeModeEnabled = false,
            policyEnforcementEnabled = true,
            settings = settings,
            appUsedMillis = 0L,
            totalUsedMillis = 0L,
        )

        assertEquals(BlockDecision.AllowedNoLimit, evaluate(settings(0)).result.decision)
        assertEquals(
            BlockDecision.WouldBlockTotalLimit,
            evaluate(settings(EXPLICIT_ZERO_LIMIT_STORAGE_MINUTES)).result.decision,
        )
    }

    @Test
    fun legacyZeroGroupLimitRemainsUnlimited_butExplicitZeroBlocks() {
        fun settings(rawMinutes: Int) = UsagePolicySettings(
            dailyPolicyEnabled = false,
            appGroups = listOf(
                AppGroupPolicy(
                    id = "group",
                    name = "Group",
                    packageNames = setOf("com.example.video"),
                    budgetMinutes = rawMinutes,
                ),
            ).toAppGroupsEncoded(),
        )
        fun evaluate(settings: UsagePolicySettings) = BlockDecisionEngine.evaluateDetailed(
            packageName = "com.example.video",
            appName = "Video",
            safeModeEnabled = false,
            policyEnforcementEnabled = true,
            settings = settings,
            appUsedMillis = 0L,
            totalUsedMillis = 0L,
            targetGroupUsedMillis = 0L,
        )

        assertEquals(BlockDecision.AllowedNoLimit, evaluate(settings(0)).result.decision)
        assertEquals(
            BlockDecision.WouldBlockGroupLimit,
            evaluate(settings(EXPLICIT_ZERO_LIMIT_STORAGE_MINUTES)).result.decision,
        )
    }

    @Test
    fun appLimit_isNotEnforcedOutsideSelectedDays() {
        val inactiveDay = currentPolicyDayOfWeek() % 7 + 1
        val settings = UsagePolicySettings(
            dailyPolicyEnabled = false,
            appLimitRules = "com.example.video=0",
            appLimitActiveDays = mapOf("com.example.video" to setOf(inactiveDay))
                .toAppLimitActiveDaysEncoded(),
        )

        val evaluation = BlockDecisionEngine.evaluateDetailed(
            packageName = "com.example.video",
            appName = "Video",
            safeModeEnabled = false,
            policyEnforcementEnabled = true,
            settings = settings,
            appUsedMillis = 0L,
            totalUsedMillis = 0L,
        )

        assertEquals(BlockDecision.AllowedNoLimit, evaluation.result.decision)
    }

    @Test
    fun groupLimit_isNotEnforcedOutsideSelectedDays() {
        val inactiveDay = currentPolicyDayOfWeek() % 7 + 1
        val settings = UsagePolicySettings(
            dailyPolicyEnabled = false,
            appGroups = listOf(
                AppGroupPolicy(
                    id = "group",
                    name = "Group",
                    packageNames = setOf("com.example.video"),
                    budgetMinutes = EXPLICIT_ZERO_LIMIT_STORAGE_MINUTES,
                    activeDays = setOf(inactiveDay),
                ),
            ).toAppGroupsEncoded(),
        )

        val evaluation = BlockDecisionEngine.evaluateDetailed(
            packageName = "com.example.video",
            appName = "Video",
            safeModeEnabled = false,
            policyEnforcementEnabled = true,
            settings = settings,
            appUsedMillis = 0L,
            totalUsedMillis = 0L,
            targetGroupUsedMillis = 0L,
        )

        assertEquals(BlockDecision.AllowedNoLimit, evaluation.result.decision)
    }
}
