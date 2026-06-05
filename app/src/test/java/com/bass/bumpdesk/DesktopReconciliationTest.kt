package com.bass.bumpdesk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopReconciliationTest {

    @Test
    fun reconcileRemovesDuplicateListEntriesAndClampsOffScreenItems() {
        val sceneState = SceneState()
        val app = BumpItem(
            type = BumpItem.Type.APP,
            appInfo = AppInfo("com.example.app", "Example", null),
            position = Vector3(50f, 0.05f, -50f),
        )
        val pile = Pile(
            items = mutableListOf(app),
            position = Vector3(25f, 0.05f, 0f),
            layoutMode = Pile.LayoutMode.FOLDER,
            name = "Folder",
        )
        sceneState.piles.add(pile)
        sceneState.bumpItems.add(app)

        val result = DesktopReconciliation.reconcile(sceneState, boundX = 10f, boundZ = 10f)

        assertEquals(1, result.removedDuplicateListEntries)
        assertEquals(1, result.clampedPiles)
        assertEquals(1, result.clampedFloorItems)
        assertFalse(sceneState.bumpItems.contains(app))
        assertEquals(9.5f, pile.position.x, 0.01f)
        assertEquals(9.5f, app.transform.position.x, 0.01f)
        assertEquals(-9.5f, app.transform.position.z, 0.01f)
    }
}
