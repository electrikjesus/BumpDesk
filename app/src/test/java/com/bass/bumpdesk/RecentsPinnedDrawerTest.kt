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
}
