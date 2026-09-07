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

enum class LinkedAppFamily {
    Phone,
    Messaging,
    Gallery,
    Camera,
}

object SafetyGate {
    private val detectedPhoneAppPackages = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val detectedMessagingAppPackages = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val detectedSystemInteractionPackages = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    val requiredNeverBlockPackages = setOf(
        "com.android.settings",
        "com.google.android.packageinstaller",
        "com.manisykh.screenrest",
    )

    private val builtInPhoneAppPackages = setOf(
        "com.samsung.android.dialer",
        "com.google.android.dialer",
        "com.android.dialer",
        "com.sec.android.app.dialertab",
    )

    val phoneAppPackages: Set<String>
        get() = builtInPhoneAppPackages + detectedPhoneAppPackages

    private val builtInPhoneRelatedPackages = builtInPhoneAppPackages + setOf(
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

    val phoneRelatedPackages: Set<String>
        get() = builtInPhoneRelatedPackages + detectedPhoneAppPackages

    private val builtInMessagingAppPackages = setOf(
        "com.samsung.android.messaging",
        "com.google.android.apps.messaging",
        "com.android.mms",
    )

    val messagingAppPackages: Set<String>
        get() = builtInMessagingAppPackages + detectedMessagingAppPackages

    private val builtInMessagingRelatedPackages = builtInMessagingAppPackages + setOf(
        "com.android.providers.telephony",
    )

    val messagingRelatedPackages: Set<String>
        get() = builtInMessagingRelatedPackages + detectedMessagingAppPackages

    val samsungGalleryRelatedPackages = setOf(
        "com.sec.android.gallery3d",
        "com.samsung.android.app.photoeditor",
        "com.sec.android.mimage.photoretouching",
        "com.samsung.android.photoremasterservice",
    )

    private val samsungGalleryAppPackages = setOf("com.sec.android.gallery3d")

    val samsungCameraRelatedPackages = setOf(
        "com.sec.android.app.camera",
        "com.samsung.android.provider.filterprovider",
        "com.samsung.android.app.camera.sticker.facearavatar.preload",
    )

    private val samsungCameraAppPackages = setOf("com.sec.android.app.camera")

    val communicationAppPackages: Set<String>
        get() = phoneRelatedPackages + messagingAppPackages

    /**
     * Transient Android surfaces opened on behalf of another app. They are not meaningful
     * parental-control targets and blocking them breaks file, photo, permission, and intent flows.
     */
    val systemInteractionPackages = setOf(
        "android",
        "com.android.intentresolver",
        "com.google.android.intentresolver",
        "com.samsung.android.intentresolver",
        "com.android.documentsui",
        "com.google.android.documentsui",
        "com.android.providers.downloads.ui",
        "com.android.providers.media",
        "com.android.providers.media.module",
        "com.google.android.providers.media.module",
        "com.samsung.android.providers.media",
        "com.samsung.android.providers.media.module",
        "com.samsung.android.photopicker",
        "com.google.android.photopicker",
        "com.android.externalstorage",
        "com.android.webview",
        "com.google.android.webview",
        "com.android.printspooler",
        "com.google.android.printspooler",
        "com.android.bips",
    )

    private val systemInteractionPackageFragments = listOf(
        "intentresolver",
        "documentsui",
        "photopicker",
        "photo.picker",
        "providers.media",
        "externalstorage",
        "providers.downloads.ui",
        "printspooler",
    )

    val neverBlockPackages = requiredNeverBlockPackages + systemInteractionPackages + setOf(
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

    fun registerDetectedDefaultApps(
        phonePackageName: String? = null,
        messagingPackageName: String? = null,
    ) {
        phonePackageName?.trim()?.takeIf(String::isNotBlank)?.let(detectedPhoneAppPackages::add)
        messagingPackageName?.trim()?.takeIf(String::isNotBlank)?.let(detectedMessagingAppPackages::add)
    }

    fun registerDetectedSystemInteractionPackages(packageNames: Collection<String>) {
        packageNames.asSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .forEach(detectedSystemInteractionPackages::add)
    }

    fun expandedUserAllowedPackages(userAllowedPackages: Set<String>): Set<String> {
        val normalizedPackages = userAllowedPackages
            .filter { packageName -> packageName.isNotBlank() }
            .toSet()
        if (normalizedPackages.isEmpty()) {
            return emptySet()
        }
        return buildSet {
            addAll(normalizedPackages)
            if (normalizedPackages.any { packageName -> packageName in phoneAppPackages }) {
                addAll(phoneRelatedPackages)
            }
            if (normalizedPackages.any { packageName -> packageName in messagingAppPackages }) {
                addAll(messagingRelatedPackages)
            }
            if (normalizedPackages.any { packageName -> packageName in samsungGalleryAppPackages }) {
                addAll(samsungGalleryRelatedPackages)
            }
            if (normalizedPackages.any { packageName -> packageName in samsungCameraAppPackages }) {
                addAll(samsungCameraRelatedPackages)
            }
        }
    }

    /**
     * Returns why [targetPackageName] is allowed even though the user did not select it directly.
     * Linked packages are derived at runtime and are deliberately not persisted as user choices,
     * so removing the representative app also removes every companion allowance atomically.
     */
    fun linkedAppFamily(
        targetPackageName: String,
        directlyAllowedPackages: Set<String>,
    ): LinkedAppFamily? {
        if (targetPackageName in directlyAllowedPackages) return null
        return when {
            targetPackageName in phoneRelatedPackages &&
                directlyAllowedPackages.any { packageName -> packageName in phoneAppPackages } ->
                LinkedAppFamily.Phone
            targetPackageName in messagingRelatedPackages &&
                directlyAllowedPackages.any { packageName -> packageName in messagingAppPackages } ->
                LinkedAppFamily.Messaging
            targetPackageName in samsungGalleryRelatedPackages &&
                directlyAllowedPackages.any { packageName -> packageName in samsungGalleryAppPackages } ->
                LinkedAppFamily.Gallery
            targetPackageName in samsungCameraRelatedPackages &&
                directlyAllowedPackages.any { packageName -> packageName in samsungCameraAppPackages } ->
                LinkedAppFamily.Camera
            else -> null
        }
    }

    fun isUserAllowedPackage(targetPackageName: String, userAllowedPackages: Set<String>): Boolean {
        return targetPackageName in expandedUserAllowedPackages(userAllowedPackages)
    }

    /**
     * Android/OEM picker and resolver packages vary by OS and vendor. These surfaces run on
     * behalf of the app the user is already using, so they must never become independent block
     * targets. Fragment matching covers modular Photo Picker package variants without exposing
     * ordinary user apps.
     */
    fun isSystemInteractionPackage(packageName: String): Boolean {
        val normalizedPackageName = packageName.trim().lowercase()
        if (normalizedPackageName.isBlank()) return false
        return normalizedPackageName in systemInteractionPackages ||
            normalizedPackageName in detectedSystemInteractionPackages ||
            systemInteractionPackageFragments.any { fragment -> fragment in normalizedPackageName }
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
                isSystemInteractionPackage(targetPackageName) ||
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
