package com.bass.bumpdesk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PileOperationsTest {

    @Test
    fun pruneEmptyPilesReleasesSingletonItemToDesktop() {
        val sceneState = SceneState()
        val app = BumpItem(
            type = BumpItem.Type.APP,
            appInfo = AppInfo("com.example.app", "Example", null),
            position = Vector3(1f, 0.05f, 2f),
        )
        val pile = Pile(
            items = mutableListOf(app),
            position = Vector3(1f, 0.05f, 2f),
            name = "Test",
        )
        sceneState.piles.add(pile)

        PileOperations.pruneEmptyPiles(sceneState)

        assertEquals(0, sceneState.piles.size)
        assertTrue(sceneState.bumpItems.contains(app))
        assertTrue(sceneState.isAppPlacedOnDesktop("com.example.app"))
    }

    @Test
    fun removeItemFromExpandedPileAllowsWhenAppOnlyInRecents() {
        val sceneState = SceneState()
        val app = BumpItem(
            type = BumpItem.Type.APP,
            appInfo = AppInfo("com.example.app", "Example", null),
            position = Vector3(10f, 0.05f, 10f),
        )
        val allApps = Pile(
            items = mutableListOf(app),
            position = Vector3(0f, 0.05f, 0f),
            name = "All Apps",
            isSystem = true,
            isExpanded = true,
            layoutMode = Pile.LayoutMode.STACK,
        )
        val recentsApp = BumpItem(
            type = BumpItem.Type.RECENT_APP,
            appInfo = AppInfo("com.example.app", "Example", null),
            position = Vector3(1f, 0.05f, 1f),
        )
        sceneState.piles.add(allApps)
        sceneState.piles.add(
            Pile(
                items = mutableListOf(recentsApp),
                name = "Recents",
                isSystem = true,
            ),
        )

        assertTrue(PileOperations.removeItemFromExpandedPile(sceneState, allApps, app))
        assertTrue(sceneState.bumpItems.contains(app))
    }
}
