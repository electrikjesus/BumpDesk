package com.bass.bumpdesk

import org.junit.Assert.assertEquals
import org.junit.Test

class SurfaceRenderDepthTest {
    @Test
    fun floorDrawYAddsClearanceAboveAnchor() {
        assertEquals(
            0.15f,
            SurfaceRenderDepth.floorDrawY(0.05f),
            0.0001f,
        )
    }

    @Test
    fun wallDrawOffsetPushesContentTowardRoom() {
        val (x, y, z) = SurfaceRenderDepth.offsetDrawPosition(
            BumpItem.Surface.BACK_WALL,
            1f,
            2f,
            -29.9f,
        )
        assertEquals(1f, x, 0.0001f)
        assertEquals(2f, y, 0.0001f)
        assertEquals(-29.9f + SurfaceRenderDepth.WALL_DRAW_OFFSET, z, 0.0001f)
    }

    @Test
    fun skipWallOffsetLeavesPositionUnchanged() {
        val (x, y, z) = SurfaceRenderDepth.offsetDrawPosition(
            BumpItem.Surface.RIGHT_WALL,
            29.9f,
            4f,
            1f,
            skipWallOffset = true,
        )
        assertEquals(29.9f, x, 0.0001f)
        assertEquals(4f, y, 0.0001f)
        assertEquals(1f, z, 0.0001f)
    }
}
