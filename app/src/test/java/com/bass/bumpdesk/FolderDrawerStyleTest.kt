package com.bass.bumpdesk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderDrawerStyleTest {
    @Test
    fun layoutKeepsDrawerInsideRoomBounds() {
        val pile = Pile(
            position = Vector3(20f, 0.05f, 15f),
            scale = 1f,
            isExpanded = true,
            isSystem = true,
            name = "All Apps",
        )
        val layout = FolderDrawerStyle.layout(pile, roomHalfX = 20f, roomHalfZ = 12f)
        assertTrue(layout.pos[0] <= 20f - layout.halfDimX + 0.01f)
        assertTrue(layout.pos[2] <= 12f - layout.halfDimZ + 0.01f)
    }

    @Test
    fun gridPositionsStayInsideContentBounds() {
        val pile = Pile(
            items = MutableList(FolderDrawerStyle.ITEMS_PER_PAGE) { BumpItem() },
            position = Vector3(0f, 0.05f, 0f),
            scale = 1f,
            isExpanded = true,
            isSystem = true,
            name = "All Apps",
        )
        val layout = FolderDrawerStyle.layout(pile, roomHalfX = 30f, roomHalfZ = 30f)
        val bounds = FolderDrawerStyle.contentBounds(layout, pile.scale)
        for (i in 0 until FolderDrawerStyle.ITEMS_PER_PAGE) {
            val (x, z) = FolderDrawerStyle.itemGridPosition(pile, i, layout)
            assertTrue("icon x=$x", x in bounds[0]..bounds[1])
            assertTrue("icon z=$z", z in bounds[2]..bounds[3])
        }
    }

    @Test
    fun pageCountUsesSixteenItemPages() {
        assertEquals(2, FolderDrawerStyle.totalPages(20))
        assertEquals(1, FolderDrawerStyle.totalPages(8))
    }

    @Test
    fun layoutDoesNotCrashWhenDrawerLargerThanRoom() {
        val pile = Pile(
            position = Vector3(9f, 0.05f, 4f),
            scale = 1f,
            isExpanded = true,
            isSystem = true,
            name = "All Apps",
        )
        val layout = FolderDrawerStyle.layout(pile, roomHalfX = 0.18f, roomHalfZ = 0.18f)
        assertEquals(0f, layout.pos[0], 0.01f)
        assertEquals(0f, layout.pos[2], 0.01f)
    }
}
