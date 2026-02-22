package com.bass.bumpdesk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraManagerTest {
    @Test
    fun testZoomLogic() {
        val camera = CameraManager()
        // Default targetPos: (0, 8, 13), targetLookAt: (0, 0, 0)
        
        // Zoom in (zoomLevel < 1.0)
        camera.zoomLevel = 0.5f
        camera.update()
        
        // relPos = (0, 8, 13)
        // zoomedTargetPos = (0, 4, 6.5)
        // currentPos starts at (0, 8, 13)
        // currentPos[1] += (4 - 8) * 0.1 = 7.6
        // currentPos[2] += (6.5 - 13) * 0.1 = 12.35
        
        assertEquals(7.6f, camera.currentPos[1], 0.01f)
        assertEquals(12.35f, camera.currentPos[2], 0.01f)
    }

    @Test
    fun testPanLogic() {
        val camera = CameraManager()
        camera.currentViewMode = CameraManager.ViewMode.DEFAULT
        
        val initialX = camera.targetPos[0]
        val initialZ = camera.targetPos[2]
        
        // Pan right (dx > 0) should move camera left (targetPos[0] decreases)
        // In my current handlePan implementation: targetPos[0] -= dx * s
        camera.handlePan(100f, 0f) 
        
        assertTrue("Camera should move left when panning right", camera.targetPos[0] < initialX)
    }
    
    @Test
    fun testViewRestoration() {
        val camera = CameraManager()
        camera.focusOnWall(CameraManager.ViewMode.BACK_WALL, floatArrayOf(0f, 4f, 2f), floatArrayOf(0f, 4f, -10f))
        
        camera.focusOnFolder(floatArrayOf(5f, 0f, 5f))
        assertEquals(CameraManager.ViewMode.FOLDER_EXPANDED, camera.currentViewMode)
        
        camera.restorePreviousView()
        assertEquals(CameraManager.ViewMode.BACK_WALL, camera.currentViewMode)
        assertEquals(0f, camera.targetPos[0], 0.01f)
    }
}
