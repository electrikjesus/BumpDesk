package com.bass.bumpdesk

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Filters [ActivityManager.getRecentTasks] results down to user-facing apps and Settings.
 */
object RecentTasksFilter {
    private val EXCLUDED_PACKAGES = setOf(
        "android",
        "com.android.systemui",
        "com.android.shell",
        "com.android.launcher",
        "com.android.launcher3",
        "com.google.android.apps.nexuslauncher",
        "com.android.inputmethod.latin",
        "com.google.android.inputmethod.latin",
        "com.android.permissioncontroller",
        "com.android.packageinstaller",
        "com.android.intentresolver",
        "com.android.documentsui",
        "com.android.providers.downloads.ui",
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.keychain",
        "com.android.wallpaper",
        "com.android.wallpaperpicker",
    )

    fun isSettingsRelated(packageName: String, className: String?, intentAction: String?): Boolean {
        if (packageName == "com.android.settings" || packageName.endsWith(".settings")) {
            return true
        }
        if (!className.isNullOrBlank() && className.contains("Settings", ignoreCase = true)) {
            return true
        }
        if (!intentAction.isNullOrBlank()) {
            if (intentAction == Settings.ACTION_SETTINGS ||
                intentAction.startsWith("android.settings.")
            ) {
                return true
            }
        }
        return false
    }

    fun isExcludedSystemPackage(packageName: String): Boolean =
        packageName in EXCLUDED_PACKAGES

    fun shouldIncludeTask(
        context: Context,
        baseIntent: Intent,
        packageName: String,
        launcherPackageName: String,
    ): Boolean {
        if (packageName.isBlank() || packageName == launcherPackageName) return false
        if (isExcludedSystemPackage(packageName)) return false

        val className = baseIntent.component?.className
        val action = baseIntent.action
        if (isSettingsRelated(packageName, className, action)) return true

        val pm = context.packageManager
        if (!hasLauncherActivity(pm, packageName)) return false

        val resolveInfo = pm.resolveActivity(baseIntent, PackageManager.MATCH_DEFAULT_ONLY)
        if (resolveInfo?.filter?.hasCategory(Intent.CATEGORY_LAUNCHER) == true) return true

        // Package has a launcher entry; include tasks for that app even when the task
        // targets an in-app activity (e.g. browser tab, game level).
        return true
    }

    fun shouldIncludeUsagePackage(
        context: Context,
        packageName: String,
        launcherPackageName: String,
    ): Boolean {
        if (packageName.isBlank() || packageName == launcherPackageName) return false
        if (isExcludedSystemPackage(packageName)) return false
        if (isSettingsRelated(packageName, className = null, intentAction = null)) return true
        return hasLauncherActivity(context.packageManager, packageName)
    }

    private fun hasLauncherActivity(pm: PackageManager, packageName: String): Boolean {
        val launcherIntent = Intent(Intent.ACTION_MAIN, null)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(packageName)
        return pm.queryIntentActivities(launcherIntent, 0).isNotEmpty()
    }
}
