package com.manisykh.screenrest.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class HardshipMigrationTest {
    @Test
    fun emergencyPassCooldown_isRollingSevenDaysAndDoesNotAccumulate() {
        val usedAt = 1_000L
        val sevenDays = 7L * 24L * 60L * 60L * 1_000L
        val state = HardshipRuntimeState(lastEmergencyPassUsedAtMillis = usedAt)

        assertEquals(usedAt + sevenDays, state.emergencyPassNextAvailableAtMillis())
        assertEquals(0L, HardshipRuntimeState().emergencyPassNextAvailableAtMillis())
    }

    @Test
    fun expiredEmergencyPassGrant_isIgnoredWithoutResettingCooldown() {
        val key = appLimitHardshipKey("com.example.one")
        val state = HardshipRuntimeState(
            dateKey = currentTemporaryUnlockDateKey(),
            lastEmergencyPassUsedAtMillis = 5_000L,
            emergencyPassGrants = listOf(
                EmergencyPassGrant(
                    packageName = "com.example.one",
                    policyKey = key,
                    grantedAtMillis = 1_000L,
                    expiresAtMillis = 2_000L,
                ),
            ),
        ).forToday(nowMillis = 3_000L)

        assertTrue(state.emergencyPassGrants.isEmpty())
        assertEquals(5_000L, state.lastEmergencyPassUsedAtMillis)
    }

    @Test
    fun emergencyPassGrant_isScopedToPackageAndPolicyKey() {
        val key = appLimitHardshipKey("com.example.one")
        val state = HardshipRuntimeState(
            dateKey = currentTemporaryUnlockDateKey(),
            emergencyPassGrants = listOf(
                EmergencyPassGrant(
                    packageName = "com.example.one",
                    policyKey = key,
                    grantedAtMillis = 1_000L,
                    expiresAtMillis = Long.MAX_VALUE,
                ),
            ),
        )

        assertEquals(setOf(key), state.bypassedKeysForPackage("com.example.one", nowMillis = 2_000L))
        assertEquals(emptySet<HardshipPolicyKey>(), state.bypassedKeysForPackage("com.example.two", nowMillis = 2_000L))
    }

    @Test
    fun legacyGroupLevel_isAppliedToEachMigratedGroup() {
        val settings = UsagePolicySettings(
            appGroups = "group-id^Study^60^com.example.study",
            appGroupsHardshipLevel = HardshipLevel.Level2,
        )

        val group = settings.normalizedAppGroups().single()

        assertEquals(HardshipLevel.Level2, group.hardshipLevel)
        assertEquals(HardshipLevel.Level2, settings.hardshipLevelFor(appGroupHardshipKey("group-id")))
        assertEquals((1..7).toSet(), group.activeDays)
    }

    @Test
    fun groupEncoding_preservesSelectedActiveDays() {
        val encoded = listOf(
            AppGroupPolicy(
                id = "games",
                name = "Games",
                packageNames = setOf("com.example.game"),
                budgetMinutes = 30,
                activeDays = setOf(1, 3, 5),
            ),
        ).toAppGroupsEncoded()

        assertEquals(setOf(1, 3, 5), encoded.toAppGroupPolicies().single().activeDays)
    }

    @Test
    fun appLimitActiveDays_defaultToEveryDayAndRoundTrip() {
        val legacy = UsagePolicySettings(appLimitRules = "com.example.video=20")
        assertEquals((1..7).toSet(), legacy.appLimitActiveDayMap().getValue("com.example.video"))

        val encodedDays = mapOf("com.example.video" to setOf(2, 4, 6))
            .toAppLimitActiveDaysEncoded()
        val restored = legacy.copy(appLimitActiveDays = encodedDays)

        assertEquals(setOf(2, 4, 6), restored.appLimitActiveDayMap().getValue("com.example.video"))
    }

    @Test
    fun legacyAppLimitLevel_isAppliedOnlyToExistingRules() {
        val settings = UsagePolicySettings(
            appLimitRules = "com.example.one=10|com.example.two=20",
            appLimitsHardshipLevel = HardshipLevel.Level3,
        )

        assertEquals(
            mapOf(
                "com.example.one" to HardshipLevel.Level3,
                "com.example.two" to HardshipLevel.Level3,
            ),
            settings.appLimitHardshipLevelMap(),
        )
    }

    @Test
    fun legacyScheduleLevel_isAppliedToEachMigratedSchedule() {
        val settings = UsagePolicySettings(
            scheduleTemplates = "schedule-id^Bedtime^1320^420^1%2C2%2C3%2C4%2C5^com.example.allowed",
            scheduleHardshipLevel = HardshipLevel.Level1,
        )

        val schedule = settings.normalizedScheduleTemplates().single()

        assertEquals(HardshipLevel.Level1, schedule.hardshipLevel)
        assertEquals(HardshipLevel.Level1, settings.hardshipLevelFor(scheduleHardshipKey("schedule-id")))
    }

    @Test
    fun applyingHardship_enablesTheRequiredBasePolicy() {
        val schedule = ScheduleTemplatePolicy(
            id = "study",
            name = "Study",
            startMinutes = 60,
            endMinutes = 120,
            days = setOf(1),
        )
        val settings = UsagePolicySettings(
            dailyPolicyEnabled = false,
            scheduleBlockingEnabled = false,
            scheduleTemplates = listOf(schedule).toScheduleTemplatesEncoded(),
            allowOnlyModeEnabled = false,
        )

        assertTrue(
            settings.withHardshipLevel(dailyHardshipKey(), HardshipLevel.Level1).dailyPolicyEnabled,
        )
        assertTrue(
            settings.withHardshipLevel(scheduleHardshipKey("study"), HardshipLevel.Level2)
                .scheduleBlockingEnabled,
        )
        assertTrue(
            settings.withHardshipLevel(allowOnlyHardshipKey(), HardshipLevel.Level3)
                .allowOnlyModeEnabled,
        )
    }

    @Test
    fun level3Keys_areTrackedPerPolicyInstance() {
        val group = AppGroupPolicy(
            id = "games",
            name = "Games",
            packageNames = setOf("com.example.game"),
            budgetMinutes = 30,
            hardshipLevel = HardshipLevel.Level3,
        )
        val schedule = ScheduleTemplatePolicy(
            id = "study",
            name = "Study",
            startMinutes = 60,
            endMinutes = 120,
            days = setOf(1),
            hardshipLevel = HardshipLevel.Level3,
        )
        val settings = UsagePolicySettings(
            dailyHardshipLevel = HardshipLevel.Level3,
            appGroups = listOf(group).toAppGroupsEncoded(),
            appLimitRules = "com.example.video=20",
            appLimitHardshipLevels = mapOf("com.example.video" to HardshipLevel.Level3)
                .toAppLimitHardshipLevelsEncoded(),
            scheduleTemplates = listOf(schedule).toScheduleTemplatesEncoded(),
            allowOnlyHardshipLevel = HardshipLevel.Level3,
        )

        assertEquals(
            setOf(
                dailyHardshipKey(),
                appGroupHardshipKey("games"),
                appLimitHardshipKey("com.example.video"),
                scheduleHardshipKey("study"),
                allowOnlyHardshipKey(),
            ),
            settings.level3HardshipPolicyKeys(),
        )
    }

    @Test
    fun disabledBasePolicy_withConfiguredHardship_isRejected() {
        assertTrue(
            UsagePolicySettings(
                dailyPolicyEnabled = false,
                dailyHardshipLevel = HardshipLevel.Level1,
            ).hasDisabledPolicyWithHardship(),
        )
        assertFalse(
            UsagePolicySettings(
                dailyPolicyEnabled = true,
                dailyHardshipLevel = HardshipLevel.Level1,
            ).hasDisabledPolicyWithHardship(),
        )
    }

    @Test
    fun scheduleEncoding_preservesHardshipOccurrenceEnd() {
        val expectedEnd = 1_800_000_000_000L
        val encoded = listOf(
            ScheduleTemplatePolicy(
                id = "study",
                name = "Study",
                startMinutes = 22 * 60,
                endMinutes = 7 * 60,
                days = setOf(1, 2, 3, 4, 5),
                hardshipLevel = HardshipLevel.Level3,
                hardshipEndAtMillis = expectedEnd,
            ),
        ).toScheduleTemplatesEncoded()

        val restored = encoded.toScheduleTemplatePolicies().single()

        assertEquals(HardshipLevel.Level3, restored.hardshipLevel)
        assertEquals(expectedEnd, restored.hardshipEndAtMillis)
    }

    @Test
    fun overnightSchedule_hardshipEndsAtScheduleEndInsteadOfMidnight() {
        val mondayBeforeStart = LocalDateTime.of(2026, 8, 3, 20, 0)
        val expectedTuesdayEnd = LocalDateTime.of(2026, 8, 4, 7, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val schedule = ScheduleTemplatePolicy(
            id = "sleep",
            name = "Sleep",
            startMinutes = 22 * 60,
            endMinutes = 7 * 60,
            days = setOf(1),
        )

        assertEquals(expectedTuesdayEnd, schedule.nextOccurrenceEndMillis(mondayBeforeStart))
        assertEquals(
            expectedTuesdayEnd,
            schedule.currentOccurrenceEndMillis(LocalDateTime.of(2026, 8, 3, 23, 0)),
        )
    }

    @Test
    fun dailyRollover_preservesOnlyScheduleRuntimeLocks() {
        val scheduleKey = scheduleHardshipKey("overnight")
        val dailyKey = dailyHardshipKey()
        val rolled = HardshipRuntimeState(
            dateKey = "2026-08-03",
            activePolicyKeys = setOf(scheduleKey, dailyKey),
            activePolicies = setOf(HardshipPolicyType.Schedule, HardshipPolicyType.DailyLimit),
        ).forToday(todayKey = "2026-08-04", nowMillis = 1_000L)

        assertEquals(setOf(scheduleKey), rolled.activePolicyKeys)
        assertEquals(setOf(HardshipPolicyType.Schedule), rolled.activePolicies)
    }
}
