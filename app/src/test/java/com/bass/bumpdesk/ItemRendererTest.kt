package com.bass.bumpdesk

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ItemRendererTest {

    private lateinit var sceneState: SceneState

    @Before
    fun setUp() {
        sceneState = SceneState()
    }

    @Test
    fun testAppDrawerItemCreation() {
        // Verify that the APP_DRAWER type exists and can be assigned to a BumpItem
        val item = BumpItem(type = BumpItem.Type.APP_DRAWER)
        assertEquals(BumpItem.Type.APP_DRAWER, item.type)
    }
    
    @Test
    fun testSceneStateIsAlreadyOnDesktop() {
        val appInfo = AppInfo("com.test", "Test App", null)
        val item = BumpItem(type = BumpItem.Type.APP, appInfo = appInfo)
        sceneState.bumpItems.add(item)
        
        assertTrue(sceneState.isAlreadyOnDesktop(appInfo))
    }

    @Test
    fun isAlreadyOnDesktop_includesAppsInUserPiles() {
        val appInfo = AppInfo("com.test", "Test App", null)
        val item = BumpItem(type = BumpItem.Type.APP, appInfo = appInfo)
        val pile = Pile(
            items = mutableListOf(item),
            name = "Games",
            layoutMode = Pile.LayoutMode.FOLDER,
        )
        sceneState.piles.add(pile)

        assertTrue(sceneState.isAlreadyOnDesktop(appInfo))
    }

    @Test
    fun appsNotInAllAppsDrawer_excludesPlacedApps() {
        val placed = AppInfo("com.placed", "Placed", null)
        val available = AppInfo("com.free", "Free", null)
        sceneState.allAppsList.addAll(listOf(placed, available))
        sceneState.bumpItems.add(BumpItem(type = BumpItem.Type.APP, appInfo = placed))

        val drawerApps = sceneState.appsNotInAllAppsDrawer()

        assertEquals(1, drawerApps.size)
        assertEquals("com.free", drawerApps.first().packageName)
    }
}
