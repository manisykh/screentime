package com.example.screentimemanager.safety

data class SafetyGateResult(
    val canEvaluateBlocking: Boolean,
    val reason: SafetyGateReason,
)

enum class SafetyGateReason {
    Allowed,
    SafeModeEnabled,
    PolicyEnforcementDisabled,
    WhitelistedPackage,
}

object SafetyGate {
    const val FUTURE_ACCESSIBILITY_SERVICE_ID =
        "com.example.screentimemanager/com.example.screentimemanager.blocking.ScreenTimeAccessibilityService"

    val requiredNeverBlockPackages = setOf(
        "com.android.settings",
        "com.android.vending",
        "com.google.android.packageinstaller",
        "com.example.screentimemanager",
    )

    val neverBlockPackages = requiredNeverBlockPackages + setOf(
        "com.google.android.settings",
        "com.android.packageinstaller",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.systemui",
        "com.google.android.marvin.talkback",
        "com.samsung.accessibility",
        "com.sec.android.app.launcher",
        "com.samsung.android.oneui.home",
        "com.google.android.apps.nexuslauncher",
        "com.android.launcher",
        "com.android.launcher3",
        "com.samsung.android.honeyboard",
        "com.sec.android.inputmethod",
        "com.samsung.android.keyboard",
        "com.google.android.inputmethod.latin",
        "com.android.inputmethod.latin",
        "com.samsung.android.dialer",
        "com.google.android.dialer",
        "com.android.dialer",
        "com.samsung.android.app.telephonyui",
        "com.samsung.android.messaging",
        "com.google.android.apps.messaging",
        "com.android.mms",
    )

    fun evaluateBlocking(
        safeModeEnabled: Boolean,
        policyEnforcementEnabled: Boolean,
        targetPackageName: String,
    ): SafetyGateResult {
        return when {
            safeModeEnabled -> SafetyGateResult(
                canEvaluateBlocking = false,
                reason = SafetyGateReason.SafeModeEnabled,
            )

            !policyEnforcementEnabled -> SafetyGateResult(
                canEvaluateBlocking = false,
                reason = SafetyGateReason.PolicyEnforcementDisabled,
            )

            targetPackageName in neverBlockPackages -> SafetyGateResult(
                canEvaluateBlocking = false,
                reason = SafetyGateReason.WhitelistedPackage,
            )

            else -> SafetyGateResult(
                canEvaluateBlocking = true,
                reason = SafetyGateReason.Allowed,
            )
        }
    }

    fun canEvaluateBlocking(
        safeModeEnabled: Boolean,
        policyEnforcementEnabled: Boolean,
        targetPackageName: String,
    ): Boolean {
        return evaluateBlocking(
            safeModeEnabled = safeModeEnabled,
            policyEnforcementEnabled = policyEnforcementEnabled,
            targetPackageName = targetPackageName,
        ).canEvaluateBlocking
    }

    fun canEvaluateBlocking(
        safeModeEnabled: Boolean,
        targetPackageName: String,
    ): Boolean {
        return canEvaluateBlocking(
            safeModeEnabled = safeModeEnabled,
            policyEnforcementEnabled = true,
            targetPackageName = targetPackageName,
        )
    }
}
