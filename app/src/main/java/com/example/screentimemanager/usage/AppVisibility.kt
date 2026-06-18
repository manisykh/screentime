package com.example.screentimemanager.usage

object AppVisibility {
    val hiddenPackages = setOf(
        "android",
        "com.android.inputmethod.latin",
        "com.android.packageinstaller",
        "com.android.permissioncontroller",
        "com.android.providers.downloads.ui",
        "com.android.settings",
        "com.android.systemui",
        "com.google.android.marvin.talkback",
        "com.google.android.inputmethod.latin",
        "com.google.android.gms",
        "com.google.android.apps.nexuslauncher",
        "com.google.android.googlesdksetup",
        "com.google.android.permissioncontroller",
        "com.google.android.packageinstaller",
        "com.google.android.play.games",
        "com.google.android.setupwizard",
        "com.google.android.syncadapters.contacts",
        "com.samsung.android.app.cocktailbarservice",
        "com.samsung.accessibility",
        "com.samsung.android.honeyboard",
        "com.samsung.android.keyboard",
        "com.sec.android.app.launcher",
        "com.sec.android.inputmethod",
    )

    private val hiddenPackageFragments = listOf(
        "accessibility",
        ".aodservice",
        ".launcher",
        ".oneui",
        ".systemui",
        ".wallpaper",
        "inputmethod",
        "keyboard",
        "permissioncontroller",
        "packageinstaller",
        "setupwizard",
    )

    fun isHiddenPackage(packageName: String): Boolean {
        val normalizedPackageName = packageName.lowercase()
        return packageName in hiddenPackages ||
            hiddenPackageFragments.any { fragment -> fragment in normalizedPackageName }
    }
}
