package com.bass.bumpdesk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentsWallDrawerTest {

    @Test
    fun defaultWallDrawerIsCompactSquare() {
        val pile = Pile(
            items = mutableListOf(),
            name = "Recents",
            layoutMode = Pile.LayoutMode.FOLDER,
            surface = BumpItem.Surface.BACK_WALL,
            isSystem = true,
            scale = 1f,
        ).apply {
            drawerGridColumns = 2
            drawerGridRows = 2
        }
        val halfX = FolderDrawerStyle.recentsWallHalfDimX(pile)
        val halfY = FolderDrawerStyle.recentsWallHalfDimY(pile)
        assertTrue(halfX in 1.35f..1.75f)
        assertTrue(halfY in 1.55f..2.05f)
        assertTrue(halfY < 3.0f)
    }

    @Test
    fun wallGridIconsFitInsideContentBounds() {
        val pile = Pile(
            items = (0 until 4).map { i ->
                BumpItem(
                    appInfo = AppInfo("pkg$i", "App $i", null),
                    position = Vector3(0f, 0f, 0f),
                )
            }.toMutableList(),
            name = "Recents",
            layoutMode = Pile.LayoutMode.FOLDER,
            surface = BumpItem.Surface.BACK_WALL,
            isSystem = true,
            scale = 1f,
            isExpanded = true,
        ).apply {
            drawerGridColumns = 2
            drawerGridRows = 2
        }
        val layout = FolderDrawerStyle.backWallLayout(pile, roomHalfX = 30f, roomSize = 30f)
        pile.items.forEachIndexed { index, item ->
            val (x, y) = FolderDrawerStyle.itemGridPositionOnBackWall(pile, index, layout)
            item.transform.position = Vector3(x, y, pile.position.z)
            assertTrue(
                "icon $index x=$x y=$y",
                FolderDrawerStyle.isInsideWallContentArea(pile, item, layout, pile.scale),
            )
        }
        val ys = pile.items.map { it.transform.position.y }
        val contentTop = layout.pos[1] + layout.halfDimZ - FolderDrawerStyle.WALL_TITLE_BAND * pile.scale
        val contentBottom = layout.pos[1] - layout.halfDimZ
        val contentMid = (contentTop + contentBottom) / 2f
        val iconMid = (ys.maxOrNull()!! + ys.minOrNull()!!) / 2f
        assertTrue(
            "icons should be vertically centered in content (mid=$iconMid contentMid=$contentMid)",
            kotlin.math.abs(iconMid - contentMid) < 0.15f,
        )
    }

    @Test
    fun chromeButtonsScaleLinearlyWithPileScale() {
        val base = Pile(
            items = mutableListOf(),
            name = "Recents",
            layoutMode = Pile.LayoutMode.FOLDER,
            surface = BumpItem.Surface.BACK_WALL,
            isSystem = true,
            scale = 1f,
        ).apply {
            drawerGridColumns = 2
            drawerGridRows = 2
        }
        val enlarged = base.copy(scale = 2f).apply {
            drawerGridColumns = 2
            drawerGridRows = 2
        }
        val layoutSmall = FolderDrawerStyle.backWallLayout(base, roomHalfX = 30f, roomSize = 30f)
        val layoutLarge = FolderDrawerStyle.backWallLayout(enlarged, roomHalfX = 30f, roomSize = 30f)
        val buttonSmall = FolderDrawerStyle.chromeButtonHalfSize(base, layoutSmall, base.scale)
        val buttonLarge = FolderDrawerStyle.chromeButtonHalfSize(enlarged, layoutLarge, enlarged.scale)
        assertTrue(buttonLarge > buttonSmall * 1.9f)
        assertTrue(buttonLarge < buttonSmall * 2.1f)
        assertEquals(
            buttonSmall,
            FolderDrawerStyle.recentsDrawerResizeHandleHalfSize(base, layoutSmall),
            0.01f,
        )
        assertEquals(
            buttonLarge,
            FolderDrawerStyle.recentsDrawerResizeHandleHalfSize(enlarged, layoutLarge),
            0.01f,
        )
    }

    @Test
    fun chromeButtonsDoNotGrowWithGridColumns() {
        val pile2 = Pile(
            items = mutableListOf(),
            name = "Recents",
            layoutMode = Pile.LayoutMode.FOLDER,
            surface = BumpItem.Surface.BACK_WALL,
            isSystem = true,
            scale = 1f,
        ).apply {
            drawerGridColumns = 2
            drawerGridRows = 2
        }
        val pile4 = Pile(
            items = mutableListOf(),
            name = "Recents",
            layoutMode = Pile.LayoutMode.FOLDER,
            surface = BumpItem.Surface.BACK_WALL,
            isSystem = true,
            scale = 1f,
        ).apply {
            drawerGridColumns = 4
            drawerGridRows = 2
        }
        val layout2 = FolderDrawerStyle.backWallLayout(pile2, roomHalfX = 30f, roomSize = 30f)
        val layout4 = FolderDrawerStyle.backWallLayout(pile4, roomHalfX = 30f, roomSize = 30f)
        val button2 = FolderDrawerStyle.chromeButtonHalfSize(pile2, layout2, pile2.scale)
        val button4 = FolderDrawerStyle.chromeButtonHalfSize(pile4, layout4, pile4.scale)
        assertEquals(button2, button4, 0.01f)
        val title2 = FolderDrawerStyle.wallTitleBarLayout(pile2, layout2, pile2.scale)
        val title4 = FolderDrawerStyle.wallTitleBarLayout(pile4, layout4, pile4.scale)
        assertEquals(title2[2], title4[2], 0.01f)
        assertEquals(
            FolderDrawerStyle.compactRecentsTitleHalfDepth(pile2),
            FolderDrawerStyle.compactRecentsTitleHalfDepth(pile4),
            0.01f,
        )
    }

    @Test
    fun nextPaginationArrowClearsResizeHandle() {
        val pile = Pile(
            items = mutableListOf(),
            name = "Recents",
            layoutMode = Pile.LayoutMode.FOLDER,
            surface = BumpItem.Surface.BACK_WALL,
            isSystem = true,
            scale = 1f,
        ).apply {
            drawerGridColumns = 2
            drawerGridRows = 2
            isPinnedOpen = true
            isExpanded = true
        }
        val layout = FolderDrawerStyle.backWallLayout(pile, roomHalfX = 30f, roomSize = 30f)
        val next = FolderDrawerStyle.wallNextButtonCenter(pile, pile.surface, layout, pile.scale)
        val resize = FolderDrawerStyle.recentsWallResizeHandleCenter(pile, pile.surface, layout)
        val separation = kotlin.math.abs(next[0] - resize[0])
        assertTrue(
            "next arrow should sit left of resize handle (sep=$separation)",
            separation > FolderDrawerStyle.recentsDrawerResizeHandleHalfSize(pile, layout),
        )
    }

    @Test
    fun resizeGridSnapsWithinBounds() {
        val pile = Pile(
            items = mutableListOf(),
            name = "Recents",
            surface = BumpItem.Surface.BACK_WALL,
            isSystem = true,
            scale = 1f,
        )
        val (wideCols, oneRow) = FolderDrawerStyle.computeRecentsWallGridResize(
            pile,
            deltaPrimary = 13.5f,
            deltaVertical = 1.35f,
            startCols = 2,
            startRows = 2,
        )
        assertEquals(12, wideCols)
        assertEquals(1, oneRow)

        val (_, maxRows) = FolderDrawerStyle.computeRecentsWallGridResize(
            pile,
            deltaPrimary = 0f,
            deltaVertical = -5f,
            startCols = 2,
            startRows = 1,
        )
        assertEquals(4, maxRows)
    }

    @Test
    fun wideSingleRowDrawerFitsRoomWidth() {
        val pile = Pile(
            items = (0 until 12).map { i ->
                BumpItem(
                    appInfo = AppInfo("pkg$i", "App $i", null),
                    position = Vector3(0f, 0f, 0f),
                )
            }.toMutableList(),
            name = "Recents",
            layoutMode = Pile.LayoutMode.FOLDER,
            surface = BumpItem.Surface.BACK_WALL,
            isSystem = true,
            scale = 1f,
            isExpanded = true,
        ).apply {
            drawerGridColumns = 12
            drawerGridRows = 1
        }
        val layout = FolderDrawerStyle.backWallLayout(pile, roomHalfX = 30f, roomSize = 30f)
        assertTrue(layout.halfDimX < 30f)
        pile.items.take(12).forEachIndexed { index, item ->
            val (x, y) = FolderDrawerStyle.itemGridPositionOnBackWall(pile, index, layout)
            item.transform.position = Vector3(x, y, pile.position.z)
            assertTrue(
                "icon $index x=$x y=$y",
                FolderDrawerStyle.isInsideWallContentArea(pile, item, layout, pile.scale),
            )
        }
    }

    @Test
    fun wideSingleRowPanelCornerRadiusStaysFixed() {
        val pile = Pile(
            items = mutableListOf(),
            name = "Recents",
            surface = BumpItem.Surface.BACK_WALL,
            isSystem = true,
            scale = 1f,
        ).apply {
            drawerGridColumns = 12
            drawerGridRows = 1
        }
        val halfX = FolderDrawerStyle.recentsWallHalfDimX(pile)
        val halfY = FolderDrawerStyle.recentsWallHalfDimY(pile)
        val corner = FolderDrawerStyle.panelCornerRadius(halfX, halfY, pile.scale)
        assertTrue(halfX > halfY * 3f)
        assertTrue(corner in 0.18f..0.24f)
        assertTrue(corner < halfY * 0.5f)
    }
}
