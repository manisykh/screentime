package com.manisykh.screenrest

import com.manisykh.screenrest.data.UsagePolicySettings
import com.manisykh.screenrest.safety.BlockDecision
import com.manisykh.screenrest.safety.BlockDecisionEngine
import com.manisykh.screenrest.safety.SafetyGate
import com.manisykh.screenrest.safety.SafetyGateReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class SafetyGateTest {
    @Test
    fun phoneAllowedPackage_allowsInCallUiPackage() {
        val result = SafetyGate.evaluateBlocking(
            safeModeEnabled = false,
            policyEnforcementEnabled = true,
            targetPackageName = "com.samsung.android.incallui",
            userAllowedPackages = setOf("com.samsung.android.dialer"),
        )

        assertEquals(SafetyGateReason.WhitelistedPackage, result.reason)
    }

    @Test
    fun allowOnlyMode_blocksUnrelatedPackageButAllowsPhoneFamily() {
        val settings = UsagePolicySettings(allowOnlyModeEnabled = true)

        val phoneDecision = BlockDecisionEngine.evaluate(
            packageName = "com.android.incallui",
            appName = "Call",
            safeModeEnabled = false,
            policyEnforcementEnabled = true,
            settings = settings,
            appUsedMinutes = 0,
            totalUsedMinutes = 0,
            exceededGroupPackages = emptySet(),
            userAllowedPackages = setOf("com.samsung.android.dialer"),
        )
        val gameDecision = BlockDecisionEngine.evaluate(
            packageName = "com.roblox.client",
            appName = "Roblox",
            safeModeEnabled = false,
            policyEnforcementEnabled = true,
            settings = settings,
            appUsedMinutes = 0,
            totalUsedMinutes = 0,
            exceededGroupPackages = emptySet(),
            userAllowedPackages = setOf("com.samsung.android.dialer"),
        )

        assertEquals(BlockDecision.AllowedWhitelist, phoneDecision.decision)
        assertEquals(BlockDecision.WouldBlockAllowOnly, gameDecision.decision)
    }

    @Test
    fun scheduleAllowedPhonePackage_allowsInCallUiPackageDuringSchedule() {
        val minuteOfDay = LocalDateTime.now().let { now -> now.hour * 60 + now.minute }
        val activeStartMinutes = (minuteOfDay + 24 * 60 - 1) % (24 * 60)
        val activeEndMinutes = (minuteOfDay + 1) % (24 * 60)
        val settings = UsagePolicySettings(
            scheduleBlockingEnabled = true,
            scheduleTemplates = "1^Sleep^$activeStartMinutes^$activeEndMinutes^1,2,3,4,5,6,7^com.samsung.android.dialer",
        )

        val decision = BlockDecisionEngine.evaluate(
            packageName = "com.android.incallui",
            appName = "Call",
            safeModeEnabled = false,
            policyEnforcementEnabled = true,
            settings = settings,
            appUsedMinutes = 0,
            totalUsedMinutes = 0,
            exceededGroupPackages = emptySet(),
            scheduleAllowedPackages = setOf("com.samsung.android.dialer"),
        )

        assertTrue(decision.decision != BlockDecision.WouldBlockSchedule)
    }
}
