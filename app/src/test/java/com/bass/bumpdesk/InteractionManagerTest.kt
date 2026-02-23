package com.bass.bumpdesk

import android.content.Context
import android.opengl.Matrix
import android.view.MotionEvent
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyFloat
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.*

class InteractionManagerTest {

    private lateinit var interactionManager: InteractionManager
    private lateinit var cameraManager: CameraManager
    private lateinit var mockContext: Context
    private lateinit var sceneState: SceneState

    @Before
    fun setUp() {
        mockContext = mock(Context::class.java)
        cameraManager = CameraManager()
        interactionManager = InteractionManager(mockContext, cameraManager)
        sceneState = SceneState()
        
        interactionManager.screenWidth = 1080
        interactionManager.screenHeight = 1920
        Matrix.setIdentityM(interactionManager.invertedVPMatrix, 0)
    }

    @Test
    fun testWidgetSelectionInFocusMode() {
        // Setup: A widget on the back wall
        val widget = WidgetItem(appWidgetId = 1, position = floatArrayOf(0f, 4f, -9.9f), surface = BumpItem.Surface.BACK_WALL)
        sceneState.widgetItems.add(widget)
        
        // Mode: WIDGET_FOCUS
        cameraManager.focusOnWidget(widget)
        
        // Action: Touch down on the widget (center)
        // Since invertedVPMatrix is Identity, x=0, y=0 maps to screen center
        val hit = interactionManager.handleTouchDown(540f, 960f, sceneState)
        
        assertEquals(widget, hit)
    }
}
