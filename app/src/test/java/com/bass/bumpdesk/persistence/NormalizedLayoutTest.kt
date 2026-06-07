package com.bass.bumpdesk.persistence

import com.bass.bumpdesk.BumpItem
import com.bass.bumpdesk.Pile
import com.bass.bumpdesk.SceneState
import com.bass.bumpdesk.Vector3
import com.bass.bumpdesk.WidgetItem
import org.junit.Assert.assertEquals
import org.junit.Test

class NormalizedLayoutTest {

    private val coverBounds = LayoutBounds(
        boundX = 8f,
        boundZ = 14f,
        roomSize = 8f,
        roomHeight = 30f,
    )
    private val innerBounds = LayoutBounds(
        boundX = 18f,
        boundZ = 12f,
        roomSize = 18f,
        roomHeight = 30f,
    )

    @Test
    fun floorPosition_roundTripsThroughNormalization() {
        val world = Vector3(4f, 0.1f, -7f)
        val norm = NormalizedLayout.normalizePosition(world, BumpItem.Surface.FLOOR, coverBounds)
        val restored = NormalizedLayout.denormalizePosition(norm, BumpItem.Surface.FLOOR, coverBounds)
        assertEquals(world.x, restored.x, 0.001f)
        assertEquals(world.y, restored.y, 0.001f)
        assertEquals(world.z, restored.z, 0.001f)
    }

    @Test
    fun floorPosition_remappedFromCoverToInnerBounds() {
        val coverPos = Vector3(4f, 0.1f, -7f)
        val innerPos = NormalizedLayout.remapPosition(
            coverPos,
            BumpItem.Surface.FLOOR,
            coverBounds,
            innerBounds,
        )
        assertEquals(9f, innerPos.x, 0.01f)
        assertEquals(-6f, innerPos.z, 0.01f)
    }

    @Test
    fun backWallPosition_remappedPreservesHorizontalFraction() {
        val wallPos = Vector3(6f, 12f, -7.9f)
        val remapped = NormalizedLayout.remapPosition(
            wallPos,
            BumpItem.Surface.BACK_WALL,
            coverBounds,
            innerBounds,
        )
        assertEquals(13.5f, remapped.x, 0.01f)
        assertEquals(12f, remapped.y, 0.01f)
        assertEquals(-innerBounds.roomSize + 0.1f, remapped.z, 0.01f)
    }

    @Test
    fun remapScene_keepsWidgetSizeWhileMovingPosition() {
        val sceneState = SceneState()
        val widget = WidgetItem(
            appWidgetId = 1,
            position = Vector3(4f, 0.1f, 0f),
            size = Vector3(2f, 0f, 1.5f),
            surface = BumpItem.Surface.FLOOR,
        )
        sceneState.widgetItems.add(widget)

        NormalizedLayout.remapScene(sceneState, coverBounds, innerBounds)

        assertEquals(9f, widget.position.x, 0.01f)
        assertEquals(2f, widget.size.x, 0.001f)
        assertEquals(1.5f, widget.size.z, 0.001f)
    }

    @Test
    fun remapScene_movesPilesAndWidgetsTogether() {
        val sceneState = SceneState()
        val widget = WidgetItem(
            appWidgetId = 2,
            position = Vector3(4f, 0.1f, 0f),
            size = Vector3(1.5f, 0f, 1f),
            surface = BumpItem.Surface.FLOOR,
        )
        val pile = Pile(
            name = "Apps",
            position = Vector3(-4f, 0.05f, 2f),
            surface = BumpItem.Surface.FLOOR,
        )
        sceneState.widgetItems.add(widget)
        sceneState.piles.add(pile)

        NormalizedLayout.remapScene(sceneState, coverBounds, innerBounds)

        assertEquals(9f, widget.position.x, 0.01f)
        assertEquals(-9f, pile.position.x, 0.01f)
    }
}
