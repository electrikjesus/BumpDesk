package com.bass.bumpdesk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class ScreenMetricsTest {

    @Test
    fun phonePortraitZoomsInForVerticalFloorFit() {
        val profile = ScreenMetrics.computeProfile(1080, 2400, 3f)
        assertTrue(profile.isPortrait)
        assertTrue(profile.isPhone)
        assertEquals(ScreenMetrics.LayoutPosture.COVER, profile.posture)
        assertEquals("cover_portrait", profile.layoutProfileKey)
        assertTrue(profile.isCompactPosture())
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
        assertEquals(ScreenMetrics.LayoutPosture.TABLET, profile.posture)
        assertEquals("tablet_portrait", profile.layoutProfileKey)
        assertFalse(profile.isCompactPosture())
        assertEquals(28, profile.recommendedRoomSize)
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
        assertEquals(ScreenMetrics.LayoutPosture.TABLET, profile.posture)
        assertEquals("tablet_landscape", profile.layoutProfileKey)
        assertEquals(28, profile.recommendedRoomSize)
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

    @Test
    fun foldCoverScreenUsesCoverPosture() {
        val profile = ScreenMetrics.computeProfile(1080, 2424, 2.625f)
        assertEquals(ScreenMetrics.LayoutPosture.COVER, profile.posture)
        assertEquals("cover_portrait", profile.layoutProfileKey)
        assertTrue(profile.isCompactPosture())
        assertEquals(0.85f, profile.uiScale, 0.01f)
    }

    @Test
    fun foldInnerScreenUsesInnerPosture() {
        val profile = ScreenMetrics.computeProfile(1840, 2208, 2.625f)
        assertEquals(ScreenMetrics.LayoutPosture.INNER, profile.posture)
        assertEquals("inner_portrait", profile.layoutProfileKey)
        assertTrue(profile.isCompactPosture())
        assertEquals(0.95f, profile.uiScale, 0.01f)
        assertEquals(22, profile.recommendedRoomSize)
    }

    @Test
    fun largeDisplayUsesLargePosture() {
        val profile = ScreenMetrics.computeProfile(3840, 2160, 2f)
        assertEquals(ScreenMetrics.LayoutPosture.LARGE, profile.posture)
        assertEquals("large_landscape", profile.layoutProfileKey)
        assertEquals(30, profile.recommendedRoomSize)
    }

    @Test
    fun computePostureThresholds() {
        assertEquals(ScreenMetrics.LayoutPosture.COVER, ScreenMetrics.computePosture(419f))
        assertEquals(ScreenMetrics.LayoutPosture.INNER, ScreenMetrics.computePosture(420f))
        assertEquals(ScreenMetrics.LayoutPosture.INNER, ScreenMetrics.computePosture(719f))
        assertEquals(ScreenMetrics.LayoutPosture.TABLET, ScreenMetrics.computePosture(720f))
        assertEquals(ScreenMetrics.LayoutPosture.TABLET, ScreenMetrics.computePosture(899f))
        assertEquals(ScreenMetrics.LayoutPosture.LARGE, ScreenMetrics.computePosture(900f))
    }

    @Test
    fun fromConfiguration_matchesComputeProfileForFoldCover() {
        val density = 2.625f
        val config = android.content.res.Configuration().apply {
            screenWidthDp = 411
            screenHeightDp = 797
        }
        val fromConfig = ScreenMetrics.fromConfiguration(config, density)
        val fromPx = ScreenMetrics.computeProfile(
            (411 * density).roundToInt(),
            (797 * density).roundToInt(),
            density,
        )
        assertEquals(fromPx.layoutProfileKey, fromConfig.layoutProfileKey)
        assertEquals(ScreenMetrics.LayoutPosture.COVER, fromConfig.posture)
    }
}
