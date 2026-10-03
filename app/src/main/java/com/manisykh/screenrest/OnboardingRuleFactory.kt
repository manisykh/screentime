package com.manisykh.screenrest

import com.manisykh.screenrest.data.ScheduleTemplatePolicy
import com.manisykh.screenrest.data.UsagePolicySettings
import com.manisykh.screenrest.data.toScheduleTemplatesEncoded
import com.manisykh.screenrest.ui.safety.toAppLimitRules

internal enum class FirstRuleGoal {
    DailyLimit,
    AppLimit,
    Bedtime,
}

internal fun buildFirstRule(
    goal: FirstRuleGoal,
    dailyMinutes: Int,
    appMinutes: Int,
    selectedPackage: String,
    korean: Boolean,
): UsagePolicySettings {
    val empty = UsagePolicySettings(
        dailyPolicyEnabled = false,
        appGroupName = "",
        appGroupPackages = "",
        appGroupBudgetMinutes = 0,
        appGroups = "",
        appLimitRules = "",
        appLimitActiveDays = "",
        appLimitHardshipLevels = "",
        scheduleBlockingEnabled = false,
        scheduleTemplates = "",
        activeScheduleTemplateId = "",
        allowOnlyModeEnabled = false,
    )
    return when (goal) {
        FirstRuleGoal.DailyLimit -> empty.copy(
            weekdayLimitMinutes = dailyMinutes,
            weekendLimitMinutes = dailyMinutes,
            mondayLimitMinutes = dailyMinutes,
            tuesdayLimitMinutes = dailyMinutes,
            wednesdayLimitMinutes = dailyMinutes,
            thursdayLimitMinutes = dailyMinutes,
            fridayLimitMinutes = dailyMinutes,
            saturdayLimitMinutes = dailyMinutes,
            sundayLimitMinutes = dailyMinutes,
            dailyPolicyEnabled = true,
        )
        FirstRuleGoal.AppLimit -> empty.copy(
            appLimitRules = mapOf(selectedPackage to appMinutes).toAppLimitRules(),
        )
        FirstRuleGoal.Bedtime -> {
            val schedule = ScheduleTemplatePolicy(
                id = "first-bedtime",
                name = if (korean) "취침 시간" else "Bedtime",
                startMinutes = 22 * 60,
                endMinutes = 7 * 60,
                days = (1..7).toSet(),
            )
            empty.copy(
                scheduleBlockingEnabled = true,
                scheduleTemplates = listOf(schedule).toScheduleTemplatesEncoded(),
                activeScheduleTemplateId = schedule.id,
            )
        }
    }
}
