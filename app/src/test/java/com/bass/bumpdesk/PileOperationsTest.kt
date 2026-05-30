package com.bass.bumpdesk

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PileOperationsTest {

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
                className = "MainActivity"
            ),
            position = Vector3(x, 0.05f, z)
        )
    }

    @Test
    fun createPileFromCaptured_removesItemsFromDesktopAndCreatesPile() {
        val a = appItem("A", 0f, 0f)
        val b = appItem("B", 1f, 1f)
        sceneState.bumpItems.addAll(listOf(a, b))

        val pile = PileOperations.createPileFromCaptured(sceneState, listOf(a, b))

        assertNotNull(pile)
        assertEquals(1, sceneState.piles.size)
        assertFalse(sceneState.bumpItems.contains(a))
        assertFalse(sceneState.bumpItems.contains(b))
        assertEquals(2, pile!!.items.size)
    }

    @Test
    fun createPileFromCaptured_requiresAtLeastTwoItems() {
        val a = appItem("A", 0f, 0f)
        sceneState.bumpItems.add(a)

        val pile = PileOperations.createPileFromCaptured(sceneState, listOf(a))

        assertNull(pile)
        assertTrue(sceneState.piles.isEmpty())
        assertTrue(sceneState.bumpItems.contains(a))
    }

    @Test
    fun addItemToPile_movesItemOffDesktop() {
        val pile = Pile(mutableListOf(appItem("Existing", 0f, 0f)), Vector3(2f, 0.05f, 2f))
        val incoming = appItem("Incoming", 4f, 4f)
        sceneState.piles.add(pile)
        sceneState.bumpItems.add(incoming)

        val added = PileOperations.addItemToPile(sceneState, incoming, pile)

        assertTrue(added)
        assertEquals(2, pile.items.size)
        assertFalse(sceneState.bumpItems.contains(incoming))
    }

    @Test
    fun breakPile_releasesItemsToDesktop() {
        val a = appItem("A", 0f, 0f)
        val b = appItem("B", 1f, 1f)
        val pile = Pile(mutableListOf(a, b), Vector3(0f, 0.05f, 0f))
        sceneState.piles.add(pile)

        val released = PileOperations.breakPile(sceneState, pile)

        assertEquals(2, released)
        assertTrue(sceneState.piles.isEmpty())
        assertTrue(sceneState.bumpItems.contains(a))
        assertTrue(sceneState.bumpItems.contains(b))
    }

    @Test
    fun breakPile_skipsSystemPile() {
        val pile = Pile(
            items = mutableListOf(appItem("Recent", 0f, 0f)),
            position = Vector3(0f, 4f, -10f),
            name = "Recents",
            isSystem = true
        )
        sceneState.piles.add(pile)

        val released = PileOperations.breakPile(sceneState, pile)

        assertEquals(0, released)
        assertEquals(1, sceneState.piles.size)
    }

    @Test
    fun removeItemFromExpandedPile_movesDraggedItemBackToDesktop() {
        val a = appItem("A", 0f, 0f)
        val b = appItem("B", 1f, 1f)
        val pile = Pile(mutableListOf(a, b), Vector3(0f, 0.05f, 0f), isExpanded = true)
        sceneState.piles.add(pile)

        a.transform.position = Vector3(5f, 0.05f, 5f)

        val removed = PileOperations.removeItemFromExpandedPile(sceneState, pile, a)

        assertTrue(removed)
        assertFalse(pile.items.contains(a))
        assertTrue(sceneState.bumpItems.contains(a))
    }

    @Test
    fun pruneEmptyPiles_removesSingleItemNonSystemPiles() {
        val lone = appItem("Lone", 0f, 0f)
        val pile = Pile(mutableListOf(lone), Vector3(0f, 0.05f, 0f))
        sceneState.piles.add(pile)

        PileOperations.pruneEmptyPiles(sceneState)

        assertTrue(sceneState.piles.isEmpty())
    }
}
