package com.manisykh.screenrest.safety

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
    val requiredNeverBlockPackages = setOf(
        "com.android.settings",
        "com.android.vending",
        "com.google.android.packageinstaller",
        "com.manisykh.screenrest",
    )

    val phoneAppPackages = setOf(
        "com.samsung.android.dialer",
        "com.google.android.dialer",
        "com.android.dialer",
        "com.sec.android.app.dialertab",
    )

    val phoneRelatedPackages = phoneAppPackages + setOf(
        "com.samsung.android.incallui",
        "com.android.incallui",
        "com.google.android.dialer",
        "com.samsung.android.app.telephonyui",
        "com.android.server.telecom",
        "com.android.phone",
        "com.sec.phone",
        "com.samsung.android.contacts",
        "com.samsung.android.app.contacts",
        "com.google.android.contacts",
        "com.android.contacts",
    )

    val messagingAppPackages = setOf(
        "com.samsung.android.messaging",
        "com.google.android.apps.messaging",
        "com.android.mms",
    )

    val messagingRelatedPackages = messagingAppPackages + setOf(
        "com.android.providers.telephony",
    )

    val communicationAppPackages = phoneRelatedPackages + messagingAppPackages

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
        "com.samsung.android.app.telephonyui",
    )

    fun expandedUserAllowedPackages(userAllowedPackages: Set<String>): Set<String> {
        val normalizedPackages = userAllowedPackages
            .filter { packageName -> packageName.isNotBlank() }
            .toSet()
        if (normalizedPackages.isEmpty()) {
            return emptySet()
        }
        return buildSet {
            addAll(normalizedPackages)
            if (normalizedPackages.any { packageName -> packageName in phoneRelatedPackages }) {
                addAll(phoneRelatedPackages)
            }
            if (normalizedPackages.any { packageName -> packageName in messagingRelatedPackages }) {
                addAll(messagingRelatedPackages)
            }
        }
    }

    fun isUserAllowedPackage(targetPackageName: String, userAllowedPackages: Set<String>): Boolean {
        return targetPackageName in expandedUserAllowedPackages(userAllowedPackages)
    }

    fun evaluateBlocking(
        safeModeEnabled: Boolean,
        policyEnforcementEnabled: Boolean,
        targetPackageName: String,
        userAllowedPackages: Set<String> = emptySet(),
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

            targetPackageName in neverBlockPackages ||
                isUserAllowedPackage(targetPackageName, userAllowedPackages) -> SafetyGateResult(
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
        userAllowedPackages: Set<String> = emptySet(),
    ): Boolean {
        return evaluateBlocking(
            safeModeEnabled = safeModeEnabled,
            policyEnforcementEnabled = policyEnforcementEnabled,
            targetPackageName = targetPackageName,
            userAllowedPackages = userAllowedPackages,
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
