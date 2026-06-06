package com.bass.bumpdesk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SelectionLayoutTest {

    private lateinit var sceneState: SceneState

    @Before
    fun setUp() {
        BumpDeskLog.logEnabled = false
        sceneState = SceneState()
    }

    private fun appItem(label: String, x: Float, z: Float): BumpItem {
        return BumpItem(
            type = BumpItem.Type.APP,
            appInfo = AppInfo(
                packageName = "com.test.$label",
                label = label,
                icon = null,
                className = "MainActivity",
            ),
            position = Vector3(x, 0.05f, z),
            scale = 1f,
        )
    }

    @Test
    fun releaseItemsToDesktop_pullsItemsOutOfPile() {
        val a = appItem("A", 0f, 0f)
        val b = appItem("B", 0f, 0f)
        val pile = Pile(mutableListOf(a, b), Vector3(2f, 0.05f, 2f))
        sceneState.piles.add(pile)

        val released = PileOperations.releaseItemsToDesktop(sceneState, listOf(a, b))

        assertEquals(2, released.size)
        assertTrue(sceneState.piles.isEmpty())
        assertTrue(sceneState.bumpItems.contains(a))
        assertTrue(sceneState.bumpItems.contains(b))
    }

    @Test
    fun releaseItemsToDesktop_keepsLooseDesktopItems() {
        val a = appItem("A", 1f, 1f)
        val b = appItem("B", 2f, 2f)
        sceneState.bumpItems.addAll(listOf(a, b))

        val released = PileOperations.releaseItemsToDesktop(sceneState, listOf(a, b))

        assertEquals(2, released.size)
        assertTrue(sceneState.piles.isEmpty())
    }

    @Test
    fun scaleItems_growsAndShrinksWithinBounds() {
        val a = appItem("A", 0f, 0f)
        val b = appItem("B", 1f, 1f)
        SelectionOperations.scaleItems(listOf(a, b), grow = true)
        assertEquals(1.25f, a.transform.scale, 0.001f)
        assertEquals(1.25f, b.transform.scale, 0.001f)
        SelectionOperations.scaleItems(listOf(a, b), grow = false)
        assertEquals(1f, a.transform.scale, 0.001f)
        assertEquals(1f, b.transform.scale, 0.001f)
    }

    @Test
    fun scaleItems_respectsMinMaxScale() {
        val tiny = appItem("Tiny", 0f, 0f).apply { transform.scale = 0.2f }
        val huge = appItem("Huge", 1f, 1f).apply { transform.scale = 2f }
        SelectionOperations.scaleItems(listOf(tiny), grow = false)
        SelectionOperations.scaleItems(listOf(huge), grow = true)
        assertEquals(SelectionOperations.MIN_ITEM_SCALE, tiny.transform.scale, 0.001f)
        assertEquals(SelectionOperations.MAX_ITEM_SCALE, huge.transform.scale, 0.001f)
    }

    @Test
    fun removeItemsFromScene_removesFromPilesAndDesktop() {
        val a = appItem("A", 0f, 0f)
        val b = appItem("B", 0f, 0f)
        val pile = Pile(mutableListOf(a, b), Vector3(2f, 0.05f, 2f))
        sceneState.piles.add(pile)

        PileOperations.removeItemsFromScene(sceneState, listOf(a, b))

        assertTrue(sceneState.piles.isEmpty())
        assertFalse(sceneState.bumpItems.contains(a))
        assertFalse(sceneState.bumpItems.contains(b))
    }
}
