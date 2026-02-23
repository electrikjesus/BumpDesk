package com.bass.bumpdesk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraManagerTest {
    @Test
    fun testZoomLogic() {
        val camera = CameraManager()
        // New Defaults for 30f room: 
        // targetPos: (0, 12, 25), targetLookAt: (0, 0, 5)
        
        // Zoom in (zoomLevel < 1.0)
        camera.zoomLevel = 0.5f
        camera.update()
        
        // relPos = targetPos - targetLookAt = (0, 12, 20)
        // zoomedTargetPos = targetLookAt + relPos * zoomLevel = (0, 0, 5) + (0, 12, 20) * 0.5 = (0, 6, 15)
        // currentPos starts at (0, 12, 25)
        // currentPos[1] += (6 - 12) * 0.1 = 11.4
        // currentPos[2] += (15 - 25) * 0.1 = 24.0
        
        assertEquals(11.4f, camera.currentPos[1], 0.01f)
        assertEquals(24.0f, camera.currentPos[2], 0.01f)
    }

    @Test
    fun testPanLogic() {
        val camera = CameraManager()
        camera.currentViewMode = CameraManager.ViewMode.DEFAULT
        
        val initialX = camera.targetPos[0]
        
        // Pan right (dx > 0) should move camera left (targetPos[0] decreases)
        // In current handlePan implementation: targetPos[0] -= dx * s
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
