package com.bass.bumpdesk

import android.provider.Settings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentTasksFilterTest {
    @Test
    fun includesSettingsPackageAndIntent() {
        assertTrue(RecentTasksFilter.isSettingsRelated("com.android.settings", null, null))
        assertTrue(
            RecentTasksFilter.isSettingsRelated(
                "com.example.app",
                "com.example.app.SettingsActivity",
                null,
            ),
        )
        assertTrue(
            RecentTasksFilter.isSettingsRelated(
                "com.example.app",
                null,
                Settings.ACTION_WIFI_SETTINGS,
            ),
        )
    }

    @Test
    fun excludesSystemUiAndShell() {
        assertTrue(RecentTasksFilter.isExcludedSystemPackage("com.android.systemui"))
        assertTrue(RecentTasksFilter.isExcludedSystemPackage("android"))
        assertFalse(RecentTasksFilter.isExcludedSystemPackage("com.android.settings"))
        assertFalse(RecentTasksFilter.isExcludedSystemPackage("com.example.game"))
    }
}
