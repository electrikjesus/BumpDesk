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
}
