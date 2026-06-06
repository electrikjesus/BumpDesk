package com.bass.bumpdesk

import android.content.SharedPreferences

/** Launch mode prefs aligned with SmartDock (`launch_mode`, `remember_launch_mode`, per-app memory). */
object LaunchPreferences {
    const val PREF_REMEMBER_LAUNCH_MODE = "remember_launch_mode"
    const val PREF_DEFAULT_LAUNCH_MODE = "launch_mode"
    const val PREF_SCALE_FACTOR = "launch_scale_factor"
    private const val PREFIX_PKG_MODE = "launch_mode_pkg_"

    val DEFAULT_MODES = listOf("standard", "maximized", "portrait", "fullscreen")

    fun rememberLaunchMode(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(PREF_REMEMBER_LAUNCH_MODE, true)

    fun defaultLaunchMode(prefs: SharedPreferences): String =
        prefs.getString(PREF_DEFAULT_LAUNCH_MODE, "standard") ?: "standard"

    fun scaleFactor(prefs: SharedPreferences): Float =
        prefs.getFloat(PREF_SCALE_FACTOR, 1.0f).coerceIn(0.5f, 1.5f)

    fun getPackageLaunchMode(prefs: SharedPreferences, packageName: String): String? =
        prefs.getString("$PREFIX_PKG_MODE$packageName", null)

    fun savePackageLaunchMode(prefs: SharedPreferences, packageName: String, mode: String) {
        prefs.edit().putString("$PREFIX_PKG_MODE$packageName", mode).apply()
    }

    fun resolveLaunchMode(
        prefs: SharedPreferences,
        packageName: String,
        explicitMode: String?,
    ): String {
        if (explicitMode != null) return explicitMode
        if (rememberLaunchMode(prefs)) {
            getPackageLaunchMode(prefs, packageName)?.let { return it }
        }
        return defaultLaunchMode(prefs)
    }
}
