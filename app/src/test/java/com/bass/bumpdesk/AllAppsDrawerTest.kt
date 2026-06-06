package com.bass.bumpdesk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AllAppsDrawerTest {

    @Test
    fun reconcileRemovesOrphanAllAppsPileWhenCameraNotFolderExpanded() {
        val sceneState = SceneState()
        val drawer = BumpItem(type = BumpItem.Type.APP_DRAWER, position = Vector3(1f, 0.05f, 2f))
        sceneState.appDrawerItem = drawer
        sceneState.bumpItems.add(drawer)
        sceneState.piles.add(
            Pile(
                items = mutableListOf(BumpItem(appInfo = AppInfo("com.example.app", "Example", null))),
                position = Vector3(5f, 0.05f, 5f),
                name = AllAppsDrawer.PILE_NAME,
                isSystem = true,
                isExpanded = true,
            ),
        )

        assertTrue(AllAppsDrawer.reconcile(sceneState, CameraManager.ViewMode.FLOOR))

        assertNull(AllAppsDrawer.findPile(sceneState))
        assertEquals(5f, drawer.transform.position.x, 0.01f)
    }

    @Test
    fun visibleBumpItemsHidesLauncherIconWhileDrawerPileExists() {
        val sceneState = SceneState()
        val drawer = BumpItem(type = BumpItem.Type.APP_DRAWER)
        sceneState.bumpItems.add(drawer)
        sceneState.bumpItems.add(BumpItem(appInfo = AppInfo("com.example.app", "Example", null)))
        sceneState.piles.add(
            Pile(
                items = mutableListOf(),
                name = AllAppsDrawer.PILE_NAME,
                isSystem = true,
            ),
        )

        assertEquals(1, AllAppsDrawer.visibleBumpItems(sceneState).size)
    }

    @Test
    fun removeIfEmptyClearsDrawerPile() {
        val sceneState = SceneState()
        sceneState.piles.add(
            Pile(
                items = mutableListOf(),
                name = AllAppsDrawer.PILE_NAME,
                isSystem = true,
                isExpanded = true,
            ),
        )

        assertTrue(AllAppsDrawer.removeIfEmpty(sceneState))
        assertFalse(AllAppsDrawer.hasPile(sceneState))
    }
}
