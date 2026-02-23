package com.bass.bumpdesk

import org.junit.Assert.*
import org.junit.Test

class LassoSmoothingTest {
    @Test
    fun testPathSmoothing() {
        val points = listOf(
            Vector3(0f, 0f, 0f),
            Vector3(10f, 0f, 0f),
            Vector3(10f, 0f, 10f)
        )
        
        // Test smoothing logic via PathUtils which doesn't depend on OpenGL
        val smoothed = PathUtils.smoothPath(points, iterations = 1)
        
        // For 3 points, 1 iteration produces 2*(3-1) + 2 = 6 points
        assertEquals(6, smoothed.size)
        
        // Verify math for first refined point q0 (75% p0 + 25% p1): 0*0.75 + 10*0.25 = 2.5
        assertEquals(2.5f, smoothed[1].x, 0.001f)
        assertEquals(0f, smoothed[1].y, 0.001f)
        assertEquals(0f, smoothed[1].z, 0.001f)
    }
}
