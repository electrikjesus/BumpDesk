package com.bass.bumpdesk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetPlacementTest {

    private val flatBounds = WidgetPlacement.boundsFrom(
        boundX = 10f,
        boundZ = 6f,
        roomSize = 10f,
        roomHeight = 20f,
        isFlatFloorMode = true,
        isInfiniteMode = false,
    )

    private val roomBounds = WidgetPlacement.boundsFrom(
        boundX = 30f,
        boundZ = 30f,
        roomSize = 30f,
        roomHeight = 20f,
        isFlatFloorMode = false,
        isInfiniteMode = false,
    )

    @Test
    fun floorWidgetOutsideFlatBoundsIsClamped() {
        val widget = WidgetItem(
            appWidgetId = 1,
            position = Vector3(50f, 0.1f, -20f),
            size = Vector3(2f, 0f, 2f),
            surface = BumpItem.Surface.FLOOR,
        )
        assertTrue(WidgetPlacement.constrain(widget, flatBounds))
        assertTrue(widget.position.x <= 10f - 2f)
        assertTrue(widget.position.z >= -6f + 2f)
        assertEquals(BumpItem.Surface.FLOOR, widget.surface)
    }

    @Test
    fun wallWidgetMigratesToFloorInFlatMode() {
        val widget = WidgetItem(
            appWidgetId = 2,
            position = Vector3(5f, 8f, -29.9f),
            size = Vector3(2f, 0f, 2f),
            surface = BumpItem.Surface.BACK_WALL,
        )
        assertTrue(WidgetPlacement.constrain(widget, flatBounds))
        assertEquals(BumpItem.Surface.FLOOR, widget.surface)
        assertEquals(0.1f, widget.position.y, 0.001f)
        assertTrue(kotlin.math.abs(widget.position.x) <= 10f)
        assertTrue(kotlin.math.abs(widget.position.z) <= 6f)
    }

    @Test
    fun backWallWidgetStaysOnWallInRoomMode() {
        val widget = WidgetItem(
            appWidgetId = 3,
            position = Vector3(40f, 8f, -29.9f),
            size = Vector3(2f, 0f, 2f),
            surface = BumpItem.Surface.BACK_WALL,
        )
        assertTrue(WidgetPlacement.constrain(widget, roomBounds))
        assertEquals(BumpItem.Surface.BACK_WALL, widget.surface)
        assertTrue(widget.position.x <= 30f - 2f)
        assertEquals(-30f + 0.1f, widget.position.z, 0.001f)
    }

    @Test
    fun infiniteModeSkipsConstrain() {
        val bounds = flatBounds.copy(isInfiniteMode = true)
        val widget = WidgetItem(
            appWidgetId = 4,
            position = Vector3(100f, 0.1f, 100f),
            surface = BumpItem.Surface.FLOOR,
        )
        assertFalse(WidgetPlacement.constrain(widget, bounds))
        assertEquals(100f, widget.position.x, 0.001f)
    }

    @Test
    fun constrainAllSeparatesOverlappingFloorWidgets() {
        val calendar = WidgetItem(
            appWidgetId = 10,
            position = Vector3(0f, 0.1f, 0f),
            size = Vector3(2f, 0f, 2f),
            surface = BumpItem.Surface.FLOOR,
        )
        val clock = WidgetItem(
            appWidgetId = 11,
            position = Vector3(0.2f, 0.1f, 0.1f),
            size = Vector3(2f, 0f, 2f),
            surface = BumpItem.Surface.FLOOR,
        )

        assertTrue(WidgetPlacement.constrainAll(listOf(calendar, clock), flatBounds))
        assertTrue(kotlin.math.abs(clock.position.x - calendar.position.x) > 3f)
    }
}
