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
}
