package com.bass.bumpdesk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadialMenuPreferencesTest {
    private val phoneProfile = ScreenMetrics.computeProfile(1080, 2400, 2.75f)
    private val tabletProfile = ScreenMetrics.computeProfile(2000, 1500, 2f)
    private val largeProfile = ScreenMetrics.computeProfile(3840, 2160, 2f)

    @Test
    fun autoPreset_selectsByScreenClass() {
        assertEquals(RadialMenuPreferences.PRESET_PHONE, RadialMenuPreferences.autoPreset(phoneProfile))
        assertEquals(RadialMenuPreferences.PRESET_TABLET, RadialMenuPreferences.autoPreset(tabletProfile))
        assertEquals(RadialMenuPreferences.PRESET_LARGE, RadialMenuPreferences.autoPreset(largeProfile))
    }

    @Test
    fun scaleForPreset_ordersCompactToSpacious() {
        val phone = RadialMenuPreferences.scaleForPreset(RadialMenuPreferences.PRESET_PHONE, phoneProfile)
        val tablet = RadialMenuPreferences.scaleForPreset(RadialMenuPreferences.PRESET_TABLET, phoneProfile)
        val large = RadialMenuPreferences.scaleForPreset(RadialMenuPreferences.PRESET_LARGE, phoneProfile)
        assertTrue(phone < tablet)
        assertTrue(tablet < large)
    }

    @Test
    fun itemCountScaleFactor_capsGrowthOnPhonePreset() {
        val phoneScale = RadialMenuPreferences.scaleForPreset(RadialMenuPreferences.PRESET_PHONE, phoneProfile)
        val desktopItems = RadialMenuPreferences.itemCountScaleFactor(11, phoneScale)
        assertTrue(desktopItems <= 1.28f)
    }
}
