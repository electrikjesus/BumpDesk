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
        
        val smoothed = PathUtils.smoothPath(points, iterations = 1)
        
        // Chaikin's produces 2*(N-1) + 2 points per iteration? 
        // For 3 points, 1 iteration: 
        // p0, q0 (75% p0 + 25% p1), r0 (25% p0 + 75% p1), q1, r1, p2
        // Total points should be 6.
        assertEquals(6, smoothed.size)
        
        // Verify math for first refined point q0: 7.5, 0, 0
        assertEquals(7.5f, smoothed[1].x, 0.001f)
        assertEquals(0f, smoothed[1].y, 0.001f)
        assertEquals(0f, smoothed[1].z, 0.001f)
    }
}
