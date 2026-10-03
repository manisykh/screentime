package com.manisykh.screenrest

import com.manisykh.screenrest.data.normalizedScheduleTemplates
import com.manisykh.screenrest.ui.safety.appLimitMap
import com.manisykh.screenrest.ui.safety.dailyLimitMinutesByDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingRuleFactoryTest {
    @Test
    fun dailyTemplateUsesTheChosenLimitForEveryDay() {
        val rule = buildFirstRule(
            goal = FirstRuleGoal.DailyLimit,
            dailyMinutes = 150,
            appMinutes = 30,
            selectedPackage = "",
            korean = true,
        )

        assertTrue(rule.dailyPolicyEnabled)
        assertEquals(listOf(150, 150, 150, 150, 150, 150, 150), rule.dailyLimitMinutesByDay())
        assertTrue(rule.appLimitMap().isEmpty())
        assertTrue(rule.normalizedScheduleTemplates().isEmpty())
    }

    @Test
    fun appTemplateStartsWithOnlyOneExplicitAppLimit() {
        val rule = buildFirstRule(
            goal = FirstRuleGoal.AppLimit,
            dailyMinutes = 120,
            appMinutes = 35,
            selectedPackage = "example.app",
            korean = true,
        )

        assertFalse(rule.dailyPolicyEnabled)
        assertEquals(mapOf("example.app" to 35), rule.appLimitMap())
        assertTrue(rule.normalizedScheduleTemplates().isEmpty())
    }

    @Test
    fun bedtimeTemplateCreatesAnEverydayOvernightSchedule() {
        val rule = buildFirstRule(
            goal = FirstRuleGoal.Bedtime,
            dailyMinutes = 120,
            appMinutes = 30,
            selectedPackage = "",
            korean = true,
        )

        val schedule = rule.normalizedScheduleTemplates().single()
        assertFalse(rule.dailyPolicyEnabled)
        assertEquals(22 * 60, schedule.startMinutes)
        assertEquals(7 * 60, schedule.endMinutes)
        assertEquals((1..7).toSet(), schedule.days)
        assertTrue(schedule.enabled)
    }
}
