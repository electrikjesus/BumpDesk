package com.bass.bumpdesk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun recentsUsesFourItemPages() {
        val pile = Pile(
            items = MutableList(5) { BumpItem(type = BumpItem.Type.RECENT_APP) },
            name = "Recents",
            isSystem = true,
        )
        assertEquals(2, FolderDrawerStyle.totalPages(pile))
        assertEquals(4, FolderDrawerStyle.itemsPerPage(pile))
    }

    @Test
    fun drawerProfilesSeparateRecentsFromStandardFloor() {
        val recents = Pile(
            name = "Recents",
            isSystem = true,
            isExpanded = true,
            layoutMode = Pile.LayoutMode.FOLDER,
            surface = BumpItem.Surface.FLOOR,
        ).apply { isPinnedOpen = true }
        val allApps = Pile(
            name = "All Apps",
            isSystem = true,
            isExpanded = true,
            surface = BumpItem.Surface.FLOOR,
        )
        val folder = Pile(
            name = "Folder",
            isExpanded = true,
            layoutMode = Pile.LayoutMode.FOLDER,
            surface = BumpItem.Surface.FLOOR,
        )
        assertEquals(FolderDrawerStyle.DrawerProfile.COMPACT_RECENTS, FolderDrawerStyle.drawerProfile(recents))
        assertEquals(FolderDrawerStyle.DrawerProfile.STANDARD_FLOOR, FolderDrawerStyle.drawerProfile(allApps))
        assertEquals(FolderDrawerStyle.DrawerProfile.STANDARD_FLOOR, FolderDrawerStyle.drawerProfile(folder))
        assertTrue(FolderDrawerStyle.usesNineSlicePanel(recents))
        assertFalse(FolderDrawerStyle.usesNineSlicePanel(allApps))
    }

    @Test
    fun floorPinnedRecentsChromeDoesNotGrowWithGridColumns() {
        val narrow = Pile(
            name = "Recents",
            isSystem = true,
            isExpanded = true,
            layoutMode = Pile.LayoutMode.FOLDER,
            surface = BumpItem.Surface.FLOOR,
            scale = 1f,
        ).apply {
            isPinnedOpen = true
            drawerGridColumns = 2
            drawerGridRows = 2
        }
        val wide = Pile(
            name = "Recents",
            isSystem = true,
            isExpanded = true,
            layoutMode = Pile.LayoutMode.FOLDER,
            surface = BumpItem.Surface.FLOOR,
            scale = 1f,
        ).apply {
            isPinnedOpen = true
            drawerGridColumns = 12
            drawerGridRows = 1
        }
        val layoutNarrow = FolderDrawerStyle.layout(narrow, roomHalfX = 30f, roomHalfZ = 30f)
        val layoutWide = FolderDrawerStyle.layout(wide, roomHalfX = 30f, roomHalfZ = 30f)
        val buttonNarrow = FolderDrawerStyle.floorChromeHalfSize(narrow, layoutNarrow, narrow.scale)
        val buttonWide = FolderDrawerStyle.floorChromeHalfSize(wide, layoutWide, wide.scale)
        assertEquals(buttonNarrow, buttonWide, 0.01f)
        assertTrue(layoutWide.halfDimX > layoutNarrow.halfDimX * 3f)
    }

    @Test
    fun compactRecentsIconScaleTracksDrawerScaleLinearly() {
        val small = Pile(
            name = "Recents",
            isSystem = true,
            scale = 0.8f,
        ).apply {
            isPinnedOpen = true
            layoutMode = Pile.LayoutMode.FOLDER
            surface = BumpItem.Surface.BACK_WALL
        }
        val large = small.copy(scale = 1.6f)
        val iconSmall = FolderDrawerStyle.compactRecentsIconScale(small)
        val iconLarge = FolderDrawerStyle.compactRecentsIconScale(large)
        assertEquals(iconLarge, iconSmall * 2f, 0.01f)
        assertTrue(iconLarge > iconSmall)
    }

    @Test
    fun pageIndicatorDotsAreCircularAndDoNotOverlap() {
        val pile = Pile(
            items = MutableList(32) { BumpItem() },
            scale = 1f,
            isExpanded = true,
            isSystem = true,
            name = "All Apps",
        )
        val layout = FolderDrawerStyle.layout(pile, roomHalfX = 30f, roomHalfZ = 30f)
        val pages = FolderDrawerStyle.totalPages(pile)
        val spacing = FolderDrawerStyle.pageIndicatorSpacing(pile, layout, pages)
        val inactiveRadius = FolderDrawerStyle.pageIndicatorDotHalfSize(pile, isCurrent = false)
        val currentRadius = FolderDrawerStyle.pageIndicatorDotHalfSize(pile, isCurrent = true)
        assertEquals(inactiveRadius, FolderDrawerStyle.pageIndicatorHalfDepth(pile, layout, false), 0.0001f)
        assertTrue(currentRadius > inactiveRadius)
        assertTrue(spacing >= inactiveRadius * 2.8f)
        val first = FolderDrawerStyle.pageIndicatorCenter(pile, layout, 0, pages)
        val last = FolderDrawerStyle.pageIndicatorCenter(pile, layout, pages - 1, pages)
        assertTrue(last[0] - first[0] >= spacing * (pages - 1) - 0.001f)
    }

    @Test
    fun syncRecentsDrawerItemScalesSetsUniformVisibleScale() {
        val pile = Pile(
            items = MutableList(6) { BumpItem().apply { scale = 0.5f } },
            name = "Recents",
            isSystem = true,
            isExpanded = true,
            surface = BumpItem.Surface.BACK_WALL,
            scale = 0.82f,
        ).apply {
            isPinnedOpen = true
            layoutMode = Pile.LayoutMode.FOLDER
            drawerGridColumns = 2
            drawerGridRows = 2
            scrollIndex = 1
        }
        FolderDrawerStyle.syncRecentsDrawerItemScales(pile)
        val expected = FolderDrawerStyle.recentsDrawerIconScale(pile)
        pile.items.forEachIndexed { index, item ->
            val inPage = index in 4 until 6
            val expectedScale = if (inPage) expected else 0.01f
            assertEquals(
                "index=$index",
                expectedScale,
                item.transform.scale,
                0.0001f,
            )
        }
    }

    @Test
    fun floorDrawerNextPageHitMatchesRenderedButton() {
        val pile = Pile(
            items = MutableList(20) { BumpItem() },
            scale = 1f,
            isExpanded = true,
            isSystem = true,
            name = "All Apps",
        )
        val layout = FolderDrawerStyle.layout(pile, roomHalfX = 30f, roomHalfZ = 30f)
        val next = FolderDrawerStyle.nextButtonCenter(pile, layout, pile.scale)
        val hitHalf = FolderDrawerStyle.floorChromeHitHalf(pile, layout, pile.scale)
        val hit = FolderDrawerStyle.hitTestFloorDrawer(
            pile,
            next[0],
            next[1],
            roomHalfX = 30f,
            roomHalfZ = 30f,
        )
        assertEquals(FolderDrawerStyle.Hit.NEXT_PAGE, hit.kind)
        assertTrue(hitHalf > 0.1f)
    }

    @Test
    fun folderExpandedOverlayPreferredOverPinnedRecents() {
        val pinnedRecents = Pile(
            name = "Recents",
            isSystem = true,
            isExpanded = true,
            layoutMode = Pile.LayoutMode.FOLDER,
            surface = BumpItem.Surface.FLOOR,
        ).apply { isPinnedOpen = true }
        val allApps = Pile(
            name = "All Apps",
            isSystem = true,
            isExpanded = true,
            surface = BumpItem.Surface.FLOOR,
        )
        val piles = listOf(pinnedRecents, allApps)
        val expandedOverlay = FolderDrawerStyle.resolveOverlayPile(
            piles,
            CameraManager.ViewMode.FOLDER_EXPANDED,
        )
        val defaultOverlay = FolderDrawerStyle.resolveOverlayPile(
            piles,
            CameraManager.ViewMode.DEFAULT,
        )
        assertEquals(allApps, expandedOverlay)
        assertEquals(pinnedRecents, defaultOverlay)
    }
}
