package com.bass.bumpdesk

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class InteractionManagerTest {

    private lateinit var interactionManager: InteractionManager
    private lateinit var cameraManager: CameraManager
    private lateinit var sceneState: SceneState

    @Before
    fun setUp() {
        cameraManager = CameraManager()
        interactionManager = InteractionManager(null, cameraManager)
        sceneState = SceneState()
        
        interactionManager.screenWidth = 1000
        interactionManager.screenHeight = 1000
        // Identity matrix maps screen center (500, 500) to NDC (0,0)
        // With identity invertedVPMatrix, NDC (0,0) maps to World (0,0)
        GLMatrix.setIdentityM(interactionManager.invertedVPMatrix, 0)
    }

    @Test
    fun testWidgetSelectionOnFloor() {
        // Setup: A widget on the FLOOR at Y=0.1
        val widget = WidgetItem(appWidgetId = 1, position = Vector3(0f, 0.1f, 0f), surface = BumpItem.Surface.FLOOR)
        widget.size = Vector3(2f, 0f, 2f)
        sceneState.widgetItems.add(widget)
        
        // Mode: WIDGET_FOCUS
        cameraManager.focusOnWidget(widget)
        
        // Let's use a non-identity matrix to simulate a top-down view
        // Map Y_NDC to Z_World and Z_NDC to Y_World
        interactionManager.invertedVPMatrix.fill(0f)
        interactionManager.invertedVPMatrix[0] = 1f // X maps to X
        interactionManager.invertedVPMatrix[6] = 1f // Y maps to Z
        interactionManager.invertedVPMatrix[9] = 1f // Z maps to Y
        interactionManager.invertedVPMatrix[15] = 1f
        
        val hit = interactionManager.handleTouchDown(500f, 500f, sceneState)
        
        assertNotNull("Widget on floor should be hit", hit)
        assertEquals(widget, hit)
    }
}
