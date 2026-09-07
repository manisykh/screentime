package com.manisykh.screenrest

import com.manisykh.screenrest.data.UsagePolicySettings
import com.manisykh.screenrest.safety.BlockDecision
import com.manisykh.screenrest.safety.BlockDecisionEngine
import com.manisykh.screenrest.safety.SafetyGate
import com.manisykh.screenrest.safety.SafetyGateReason
import com.manisykh.screenrest.usage.AppVisibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class SafetyGateTest {
    @Test
    fun playStore_isNotARequiredNeverBlockPackage() {
        val result = SafetyGate.evaluateBlocking(
            safeModeEnabled = false,
            policyEnforcementEnabled = true,
            targetPackageName = "com.android.vending",
        )

        assertEquals(SafetyGateReason.Allowed, result.reason)
    }

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
    fun phoneAllowance_marksContactsAsLinkedWithoutPersistingItAsDirectChoice() {
        val directPackages = setOf("com.samsung.android.dialer")

        assertTrue("com.samsung.android.contacts" in SafetyGate.expandedUserAllowedPackages(directPackages))
        assertEquals(
            com.manisykh.screenrest.safety.LinkedAppFamily.Phone,
            SafetyGate.linkedAppFamily("com.samsung.android.contacts", directPackages),
        )
    }

    @Test
    fun contactsAllowance_doesNotImplicitlyUnrestrictThePhoneApp() {
        val directPackages = setOf("com.samsung.android.contacts")

        assertFalse("com.samsung.android.dialer" in SafetyGate.expandedUserAllowedPackages(directPackages))
    }

    @Test
    fun systemPickerAndResolver_areAlwaysAllowed() {
        listOf(
            "com.android.intentresolver",
            "com.google.android.providers.media.module",
            "com.samsung.android.photopicker",
            "com.vendor.android.providers.media.photopicker",
            "com.android.documentsui",
        ).forEach { packageName ->
            val result = SafetyGate.evaluateBlocking(
                safeModeEnabled = false,
                policyEnforcementEnabled = true,
                targetPackageName = packageName,
            )

            assertEquals(SafetyGateReason.WhitelistedPackage, result.reason)
        }
    }

    @Test
    fun launcherClearsForegroundTracking_butIsNotADelegatedSystemPicker() {
        val launcherPackage = "com.samsung.android.oneui.home"

        assertTrue(AppVisibility.clearsForegroundSession(launcherPackage))
        assertFalse(SafetyGate.isSystemInteractionPackage(launcherPackage))
    }

    @Test
    fun galleryAllowance_expandsToItsCompanionEditor() {
        val result = SafetyGate.evaluateBlocking(
            safeModeEnabled = false,
            policyEnforcementEnabled = true,
            targetPackageName = "com.samsung.android.app.photoeditor",
            userAllowedPackages = setOf("com.sec.android.gallery3d"),
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
            allowOnlyAllowedPackages = setOf("com.samsung.android.dialer"),
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
            allowOnlyAllowedPackages = setOf("com.samsung.android.dialer"),
        )

        assertEquals(BlockDecision.AllowedNoLimit, phoneDecision.decision)
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
