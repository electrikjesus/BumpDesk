package com.bass.bumpdesk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenMetricsTest {

    @Test
    fun phonePortraitZoomsInForVerticalFloorFit() {
        val profile = ScreenMetrics.computeProfile(1080, 2400, 3f)
        assertTrue(profile.isPortrait)
        assertTrue(profile.isPhone)
        assertEquals(20, profile.recommendedRoomSize)
        assertEquals(0f, profile.defaultCameraLookAt[0], 0.01f)
        assertEquals(0f, profile.defaultCameraPos[0], 0.01f)
        assertEquals(5f, profile.defaultCameraLookAt[2], 0.01f)
        assertEquals(25f, profile.defaultCameraPos[2], 0.01f)
        assertTrue(profile.defaultZoomLevel < 1f)
        assertEquals(60f, profile.defaultFieldOfView, 0.01f)
    }

    @Test
    fun tabletPortraitUsesTunedViewFromDeviceLogs() {
        val profile = ScreenMetrics.computeProfile(1440, 2160, 2f)
        assertTrue(profile.isPortrait)
        assertFalse(profile.isPhone)
        assertEquals(0.29f, profile.defaultCameraPos[0], 0.01f)
        assertEquals(27.20f, profile.defaultCameraPos[2], 0.01f)
        assertEquals(0.29f, profile.defaultCameraLookAt[0], 0.01f)
        assertEquals(7.20f, profile.defaultCameraLookAt[2], 0.01f)
        assertEquals(1.12f, profile.defaultZoomLevel, 0.01f)
        assertEquals(60f, profile.defaultFieldOfView, 0.01f)
    }

    @Test
    fun tabletLandscapeKeepsLargeRoomDefaults() {
        val profile = ScreenMetrics.computeProfile(2560, 1600, 2f)
        assertFalse(profile.isPortrait)
        assertFalse(profile.isPhone)
        assertEquals(30, profile.recommendedRoomSize)
        assertEquals(25f, profile.defaultCameraPos[2], 0.01f)
        assertEquals(1.0f, profile.defaultZoomLevel, 0.01f)
    }

    @Test
    fun orientationKeyTracksLayout() {
        val portrait = ScreenMetrics.computeProfile(1080, 2400, 3f)
        val landscape = ScreenMetrics.computeProfile(2400, 1080, 3f)
        assertEquals("portrait", portrait.orientationKey)
        assertEquals("landscape", landscape.orientationKey)
    }
}
