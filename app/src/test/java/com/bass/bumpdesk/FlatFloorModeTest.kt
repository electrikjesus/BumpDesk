package com.bass.bumpdesk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlatFloorModeTest {
    @Test
    fun landscapeAspectProducesWiderFloorBounds() {
        val landscape = FlatFloorMode.computeFloorBounds(
            FlatFloorMode.DEFAULT_EYE_Y,
            FlatFloorMode.DEFAULT_EYE_Z,
            FlatFloorMode.DEFAULT_FOV,
            aspect = 2160f / 1440f,
            zoom = FlatFloorMode.DEFAULT_ZOOM,
            inset = 0f,
        )
        val portrait = FlatFloorMode.computeFloorBounds(
            FlatFloorMode.DEFAULT_EYE_Y,
            FlatFloorMode.DEFAULT_EYE_Z,
            FlatFloorMode.DEFAULT_FOV,
            aspect = 1440f / 2160f,
            zoom = FlatFloorMode.DEFAULT_ZOOM,
            inset = 0f,
        )
        assertTrue(landscape.halfX > landscape.halfZ)
        assertTrue(portrait.halfZ > portrait.halfX)
    }

    @Test
    fun zoomOutIncreasesVisibleBounds() {
        val tight = FlatFloorMode.computeFloorBounds(24f, 0.1f, 60f, 16f / 9f, 0.8f, inset = 0f)
        val wide = FlatFloorMode.computeFloorBounds(24f, 0.1f, 60f, 16f / 9f, 1.0f, inset = 0f)
        assertTrue(wide.halfX > tight.halfX)
        assertTrue(wide.halfZ > tight.halfZ)
    }

    @Test
    fun boundsRespectMinimumSize() {
        val bounds = FlatFloorMode.computeFloorBounds(4f, 0.1f, 30f, 1f, 1f, inset = 100f)
        assertEquals(4f, bounds.halfX, 0.01f)
        assertEquals(4f, bounds.halfZ, 0.01f)
    }
}
