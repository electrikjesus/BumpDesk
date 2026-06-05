package com.bass.bumpdesk

import org.junit.Assert.*
import org.junit.Test

class PileFolderPreviewTest {

    @Test
    fun showsFolderPreview_onlyForCollapsedFloorFolderPiles() {
        val pile = Pile(
            items = mutableListOf(),
            layoutMode = Pile.LayoutMode.FOLDER,
            surface = BumpItem.Surface.FLOOR,
        )
        assertTrue(pile.showsFolderPreview())

        pile.isExpanded = true
        assertFalse(pile.showsFolderPreview())

        pile.isExpanded = false
        pile.isSystem = true
        assertFalse(pile.showsFolderPreview())
    }

    @Test
    fun pileFolderIcons_signatureUsesFirstFourPackages() {
        fun item(pkg: String) = BumpItem(
            type = BumpItem.Type.APP,
            appInfo = AppInfo(
                packageName = pkg,
                label = pkg,
                icon = null,
                className = "Main",
            ),
        )
        val pile = Pile(
            items = mutableListOf(
                item("a"),
                item("b"),
                item("c"),
                item("d"),
                item("e"),
            ),
        )
        assertEquals("a|b|c|d", PileFolderIcons.signature(pile))
    }

    @Test
    fun pileFolderIcons_signatureIncludesNameForFolderGroups() {
        val pile = Pile(
            items = mutableListOf(),
            name = "Games",
            layoutMode = Pile.LayoutMode.FOLDER,
            surface = BumpItem.Surface.FLOOR,
        )
        assertTrue(pile.showsFolderLabel())
        assertEquals("::Games", PileFolderIcons.signature(pile))
    }
}
