package com.bass.bumpdesk

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class LaunchPreferencesTest {

    @Test
    fun resolveLaunchMode_usesExplicitModeFirst() {
        val prefs = FakeSharedPreferences(
            mapOf(
                LaunchPreferences.PREF_REMEMBER_LAUNCH_MODE to true,
                "launch_mode_pkg_com.example" to "fullscreen",
            ),
        )
        assertEquals("maximized", LaunchPreferences.resolveLaunchMode(prefs, "com.example", "maximized"))
    }

    @Test
    fun resolveLaunchMode_usesPerPackageWhenRememberEnabled() {
        val prefs = FakeSharedPreferences(
            mapOf(
                LaunchPreferences.PREF_REMEMBER_LAUNCH_MODE to true,
                "launch_mode_pkg_com.example" to "portrait",
            ),
        )
        assertEquals("portrait", LaunchPreferences.resolveLaunchMode(prefs, "com.example", null))
    }

    @Test
    fun resolveLaunchMode_fallsBackToDefault() {
        val prefs = FakeSharedPreferences(
            mapOf(
                LaunchPreferences.PREF_REMEMBER_LAUNCH_MODE to false,
                LaunchPreferences.PREF_DEFAULT_LAUNCH_MODE to "fullscreen",
            ),
        )
        assertEquals("fullscreen", LaunchPreferences.resolveLaunchMode(prefs, "com.example", null))
    }

    @Test
    fun scaleFactor_clampsToRange() {
        val prefs = FakeSharedPreferences(mapOf(LaunchPreferences.PREF_SCALE_FACTOR to 2.5f))
        assertEquals(1.5f, LaunchPreferences.scaleFactor(prefs), 0.001f)
    }

    @Test
    fun windowingModeToLaunchMode_mapsModes() {
        assertEquals("standard", AppLaunchUtils.windowingModeToLaunchMode(LauncherActivity.WINDOWING_MODE_FREEFORM))
        assertEquals("fullscreen", AppLaunchUtils.windowingModeToLaunchMode(LauncherActivity.WINDOWING_MODE_FULLSCREEN))
        assertEquals("pinned", AppLaunchUtils.windowingModeToLaunchMode(LauncherActivity.WINDOWING_MODE_PINNED))
        assertEquals(null, AppLaunchUtils.windowingModeToLaunchMode(LauncherActivity.WINDOWING_MODE_UNDEFINED))
    }

    private class FakeSharedPreferences(
        private val values: Map<String, Any?>,
    ) : SharedPreferences {
        override fun getAll(): MutableMap<String, *> = values.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? =
            values[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = values.containsKey(key)
        override fun edit(): SharedPreferences.Editor = throw UnsupportedOperationException()
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    }
}
