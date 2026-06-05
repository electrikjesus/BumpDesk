package com.bass.bumpdesk

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentsPinnedDrawerTest {

    @Test
    fun pinnedOpenRecentsUsesExpandedDrawerLayoutEvenWhenCollapsedFlagStale() {
        val pile = Pile(
            items = mutableListOf(),
            name = "Recents",
            layoutMode = Pile.LayoutMode.FOLDER,
            surface = BumpItem.Surface.FLOOR,
            isSystem = true,
            isExpanded = false,
        )
        pile.isPinnedOpen = true

        assertTrue(pile.showsDesktopPinnedDrawer())
        assertTrue(pile.layoutAsExpandedDrawer())
        assertFalse(pile.showsCollapsedPreview())

        pile.reconcilePinnedOpenState()
        assertTrue(pile.isExpanded)
    }

    @Test
    fun flatFloorRecentsMaterialDrawerStaysOnFloor() {
        val pile = Pile(
            items = mutableListOf(),
            name = "Recents",
            layoutMode = Pile.LayoutMode.FOLDER,
            surface = BumpItem.Surface.FLOOR,
            position = Vector3(-6f, 0.05f, 6f),
            isSystem = true,
            isExpanded = true,
        )

        assertFalse(pile.recentsOnWall())
        assertTrue(pile.showsRecentsMaterialDrawer())
    }
}
