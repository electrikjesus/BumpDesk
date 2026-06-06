package com.bass.bumpdesk

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpandedFolderDrawerTest {

    @Test
    fun reconcileCollapsesUserFolderWhenCameraNotFolderExpanded() {
        val sceneState = SceneState()
        val pile = Pile(
            items = mutableListOf(
                BumpItem(appInfo = AppInfo("com.example.app", "Example", null)),
            ),
            layoutMode = Pile.LayoutMode.FOLDER,
            name = "Games",
            isExpanded = true,
        )
        sceneState.piles.add(pile)

        assertTrue(ExpandedFolderDrawer.reconcile(sceneState, CameraManager.ViewMode.FLOOR))
        assertFalse(pile.isExpanded)
    }

    @Test
    fun reconcileLeavesPinnedRecentsExpandedOnFloor() {
        val sceneState = SceneState()
        val pile = Pile(
            items = mutableListOf(),
            layoutMode = Pile.LayoutMode.FOLDER,
            name = "Recents",
            isSystem = true,
            isExpanded = true,
        )
        pile.isPinnedOpen = true
        sceneState.piles.add(pile)

        assertFalse(ExpandedFolderDrawer.reconcile(sceneState, CameraManager.ViewMode.FLOOR))
        assertTrue(pile.isExpanded)
    }

    @Test
    fun reconcileNoOpWhileFolderCameraActive() {
        val sceneState = SceneState()
        val pile = Pile(
            items = mutableListOf(),
            layoutMode = Pile.LayoutMode.FOLDER,
            name = "Games",
            isExpanded = true,
        )
        sceneState.piles.add(pile)

        assertFalse(ExpandedFolderDrawer.reconcile(sceneState, CameraManager.ViewMode.FOLDER_EXPANDED))
        assertTrue(pile.isExpanded)
    }
}
