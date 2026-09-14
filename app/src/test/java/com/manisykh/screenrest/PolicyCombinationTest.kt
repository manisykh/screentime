package com.manisykh.screenrest

import com.manisykh.screenrest.data.AppGroupPolicy
import com.manisykh.screenrest.data.ImmediateBlockState
import com.manisykh.screenrest.data.HardshipLevel
import com.manisykh.screenrest.data.ScheduleTemplatePolicy
import com.manisykh.screenrest.data.UsagePolicySettings
import com.manisykh.screenrest.data.activeScheduleTemplate
import com.manisykh.screenrest.data.hasStructuralPolicyConflict
import com.manisykh.screenrest.data.isScheduleBlockingNow
import com.manisykh.screenrest.data.overlappingSchedulePairs
import com.manisykh.screenrest.data.normalizedAppGroups
import com.manisykh.screenrest.data.selectedScheduleTemplate
import com.manisykh.screenrest.data.toAppGroupsEncoded
import com.manisykh.screenrest.data.toScheduleTemplatesEncoded
import com.manisykh.screenrest.safety.BlockDecision
import com.manisykh.screenrest.safety.BlockDecisionEngine
import com.manisykh.screenrest.ui.safety.normalizedForDraft
import com.manisykh.screenrest.ui.safety.policyBudgetValidation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class PolicyCombinationTest {
    private val packageName = "com.example.video"

    @Test
    fun immediateBlock_blocksOrdinaryApp_withoutChangingSavedPolicies() {
        val now = System.currentTimeMillis()
        val result = BlockDecisionEngine.evaluate(
            packageName = packageName,
            appName = "Video",
            safeModeEnabled = false,
            policyEnforcementEnabled = true,
            settings = UsagePolicySettings(),
            appUsedMinutes = 0,
            totalUsedMinutes = 0,
            exceededGroupPackages = emptySet(),
            immediateBlockState = ImmediateBlockState(
                requestId = "test-order", requestedAtMillis = now - 1_000L,
                expiresAtMillis = now + 60_000L,
            ),
        )
        assertEquals(BlockDecision.WouldBlockImmediate, result.decision)
    }

    @Test
    fun immediateBlock_respectsUnrestrictedAppAndExpiry() {
        val now = System.currentTimeMillis()
        val active = ImmediateBlockState(
            requestId = "test-order", requestedAtMillis = now - 1_000L,
            expiresAtMillis = now + 60_000L,
        )
        fun decide(order: ImmediateBlockState, exempt: Boolean) = BlockDecisionEngine.evaluate(
            packageName = packageName, appName = "Video",
            safeModeEnabled = false, policyEnforcementEnabled = true,
            settings = UsagePolicySettings(), appUsedMinutes = 0, totalUsedMinutes = 0,
            exceededGroupPackages = emptySet(),
            userAllowedPackages = if (exempt) setOf(packageName) else emptySet(),
            immediateBlockState = order,
        ).decision
        assertEquals(BlockDecision.AllowedWhitelist, decide(active, true))
        assertTrue(decide(active.copy(expiresAtMillis = now - 1L), false) !=
            BlockDecision.WouldBlockImmediate)
    }

    @Test
    fun immediateBlock_preservesExistingLevelThreeBlockReason() {
        val now = System.currentTimeMillis()
        val evaluation = BlockDecisionEngine.evaluateDetailed(
            packageName = packageName, appName = "Video",
            safeModeEnabled = false, policyEnforcementEnabled = true,
            settings = UsagePolicySettings(
                appLimitRules = "$packageName=0",
                appLimitsHardshipLevel = HardshipLevel.Level3,
            ),
            appUsedMillis = 0L, totalUsedMillis = 0L,
            immediateBlockState = ImmediateBlockState(
                requestId = "test-order", requestedAtMillis = now - 1_000L,
                expiresAtMillis = now + 60_000L,
            ),
        )
        assertEquals(BlockDecision.WouldBlockAppLimit, evaluation.result.decision)
        assertEquals(HardshipLevel.Level3, evaluation.hardshipLevel)
        assertTrue(BlockDecision.WouldBlockImmediate in evaluation.activeBlockDecisions)
    }

    @Test
    fun allowOnlyAdmission_doesNotBypassAppTimeLimit() {
        val settings = UsagePolicySettings(
            allowOnlyModeEnabled = true,
            appLimitRules = "$packageName=30",
        )

        val result = BlockDecisionEngine.evaluate(
            packageName = packageName,
            appName = "Video",
            safeModeEnabled = false,
            policyEnforcementEnabled = true,
            settings = settings,
            appUsedMinutes = 30,
            totalUsedMinutes = 30,
            exceededGroupPackages = emptySet(),
            allowOnlyAllowedPackages = setOf(packageName),
        )

        assertEquals(BlockDecision.WouldBlockAppLimit, result.decision)
    }

    @Test
    fun scheduleAdmission_doesNotBypassAppTimeLimit() {
        val activeSchedule = activeSchedule(allowedPackages = setOf(packageName))
        val settings = UsagePolicySettings(
            scheduleBlockingEnabled = true,
            scheduleTemplates = listOf(activeSchedule).toScheduleTemplatesEncoded(),
            appLimitRules = "$packageName=20",
        )

        val result = BlockDecisionEngine.evaluate(
            packageName = packageName,
            appName = "Video",
            safeModeEnabled = false,
            policyEnforcementEnabled = true,
            settings = settings,
            appUsedMinutes = 20,
            totalUsedMinutes = 20,
            exceededGroupPackages = emptySet(),
        )

        assertEquals(BlockDecision.WouldBlockAppLimit, result.decision)
    }

    @Test
    fun activeSchedule_temporarilyTakesPrecedenceOverManualAllowOnlyList() {
        val activeSchedule = activeSchedule(allowedPackages = setOf(packageName))
        val settings = UsagePolicySettings(
            allowOnlyModeEnabled = true,
            scheduleBlockingEnabled = true,
            scheduleTemplates = listOf(activeSchedule).toScheduleTemplatesEncoded(),
        )

        val result = BlockDecisionEngine.evaluate(
            packageName = packageName,
            appName = "Video",
            safeModeEnabled = false,
            policyEnforcementEnabled = true,
            settings = settings,
            appUsedMinutes = 0,
            totalUsedMinutes = 0,
            exceededGroupPackages = emptySet(),
            allowOnlyAllowedPackages = emptySet(),
        )

        assertFalse(result.decision == BlockDecision.WouldBlockAllowOnly)
        assertFalse(result.decision == BlockDecision.WouldBlockSchedule)
    }

    @Test
    fun disabledSchedule_isAvailableForEditingButNotReportedAsActive() {
        val now = LocalDateTime.of(2026, 8, 31, 12, 0)
        val configuredSchedule = ScheduleTemplatePolicy(
            id = "lunch",
            name = "Lunch",
            startMinutes = 11 * 60,
            endMinutes = 13 * 60,
            days = setOf(now.dayOfWeek.value),
        )
        val settings = UsagePolicySettings(
            scheduleBlockingEnabled = false,
            scheduleTemplates = listOf(configuredSchedule).toScheduleTemplatesEncoded(),
            activeScheduleTemplateId = configuredSchedule.id,
        )

        assertNull(settings.activeScheduleTemplate(now))
        assertEquals(configuredSchedule, settings.selectedScheduleTemplate())
    }

    @Test
    fun enabledSchedule_isReportedOnlyDuringItsConfiguredWindow() {
        val configuredSchedule = ScheduleTemplatePolicy(
            id = "lunch",
            name = "Lunch",
            startMinutes = 11 * 60,
            endMinutes = 13 * 60,
            days = setOf(1),
        )
        val settings = UsagePolicySettings(
            scheduleBlockingEnabled = true,
            scheduleTemplates = listOf(configuredSchedule).toScheduleTemplatesEncoded(),
        )

        assertEquals(
            configuredSchedule,
            settings.activeScheduleTemplate(LocalDateTime.of(2026, 8, 31, 12, 0)),
        )
        assertNull(settings.activeScheduleTemplate(LocalDateTime.of(2026, 8, 31, 14, 0)))
    }

    @Test
    fun allRestrictionsExemption_bypassesScopeAndTimePolicies() {
        val settings = UsagePolicySettings(
            allowOnlyModeEnabled = true,
            appLimitRules = "$packageName=1",
            appGroups = listOf(
                AppGroupPolicy(
                    id = "video",
                    name = "Video",
                    packageNames = setOf(packageName),
                    budgetMinutes = 1,
                ),
            ).toAppGroupsEncoded(),
        )

        val evaluation = BlockDecisionEngine.evaluateDetailed(
            packageName = packageName,
            appName = "Video",
            safeModeEnabled = false,
            policyEnforcementEnabled = true,
            settings = settings,
            appUsedMillis = 120L * 60_000L,
            totalUsedMillis = 120L * 60_000L,
            groupLimitKnownExceeded = true,
            userAllowedPackages = setOf(packageName),
        )

        assertEquals(BlockDecision.AllowedWhitelist, evaluation.result.decision)
        assertTrue(evaluation.activeHardshipPolicyKeys.isEmpty())
    }

    @Test
    fun scheduleOverlap_isDetectedAcrossMidnight() {
        val schedules = listOf(
            ScheduleTemplatePolicy(
                id = "night",
                name = "Night",
                startMinutes = 22 * 60,
                endMinutes = 2 * 60,
                days = setOf(1),
            ),
            ScheduleTemplatePolicy(
                id = "late",
                name = "Late",
                startMinutes = 60,
                endMinutes = 3 * 60,
                days = setOf(2),
            ),
        )

        val overlaps = schedules.overlappingSchedulePairs()

        assertEquals(listOf("Night" to "Late"), overlaps)
    }

    @Test
    fun separateSchedules_onDifferentDaysDoNotConflict() {
        val schedules = listOf(
            ScheduleTemplatePolicy("one", "One", 10 * 60, 11 * 60, setOf(1)),
            ScheduleTemplatePolicy("two", "Two", 10 * 60, 11 * 60, setOf(2)),
        )

        assertTrue(schedules.overlappingSchedulePairs().isEmpty())
    }

    @Test
    fun disabledSchedule_isExcludedFromOverlapAndRuntime() {
        val monday = LocalDateTime.of(2026, 8, 31, 12, 0)
        val enabled = ScheduleTemplatePolicy("one", "One", 11 * 60, 13 * 60, setOf(1))
        val disabled = ScheduleTemplatePolicy(
            id = "two",
            name = "Two",
            startMinutes = 11 * 60,
            endMinutes = 13 * 60,
            days = setOf(1),
            enabled = false,
        )
        val settings = UsagePolicySettings(
            scheduleBlockingEnabled = true,
            scheduleTemplates = listOf(enabled, disabled).toScheduleTemplatesEncoded(),
        )

        assertTrue(listOf(enabled, disabled).overlappingSchedulePairs().isEmpty())
        assertEquals(enabled, settings.activeScheduleTemplate(monday))
        assertTrue(settings.isScheduleBlockingNow(monday))
        assertNull(settings.copy(scheduleTemplates = listOf(disabled).toScheduleTemplatesEncoded()).activeScheduleTemplate(monday))
    }

    @Test
    fun emptyScheduleDays_followRuntimeEverydayFallbackWhenCheckingOverlap() {
        val schedules = listOf(
            ScheduleTemplatePolicy("everyday", "Everyday", 10 * 60, 11 * 60, emptySet()),
            ScheduleTemplatePolicy("monday", "Monday", 10 * 60 + 30, 12 * 60, setOf(1)),
        )

        assertEquals(listOf("Everyday" to "Monday"), schedules.overlappingSchedulePairs())
    }

    @Test
    fun independentTimeLimits_areNotSilentlyExpandedOrRejected() {
        val settings = UsagePolicySettings(
            mondayLimitMinutes = 30,
            appGroups = listOf(
                AppGroupPolicy(
                    id = "group",
                    name = "Group",
                    packageNames = setOf("com.example.one", "com.example.two"),
                    budgetMinutes = 20,
                ),
            ).toAppGroupsEncoded(),
            appLimitRules = "com.example.one=15|com.example.two=15",
        )

        val normalized = settings.normalizedForDraft()
        val group = normalized.normalizedAppGroups().single()

        assertEquals(30, normalized.mondayLimitMinutes)
        assertEquals(20, group.budgetMinutes)
        assertFalse(normalized.policyBudgetValidation().hasOverflow)
    }

    @Test
    fun duplicateAppGroupMembership_isRejectedAsStructuralConflict() {
        val settings = UsagePolicySettings(
            appGroups = listOf(
                AppGroupPolicy(
                    id = "first",
                    name = "First",
                    packageNames = setOf(packageName),
                    budgetMinutes = 30,
                ),
                AppGroupPolicy(
                    id = "second",
                    name = "Second",
                    packageNames = setOf(packageName),
                    budgetMinutes = 20,
                ),
            ).toAppGroupsEncoded(),
        )

        assertTrue(settings.hasStructuralPolicyConflict())
    }

    private fun activeSchedule(
        allowedPackages: Set<String>,
    ): ScheduleTemplatePolicy {
        val now = LocalDateTime.now()
        val minute = now.hour * 60 + now.minute
        return ScheduleTemplatePolicy(
            id = "active",
            name = "Active",
            startMinutes = (minute + 1439) % 1440,
            endMinutes = (minute + 2) % 1440,
            days = (1..7).toSet(),
            allowedPackageNames = allowedPackages,
        )
    }
}
