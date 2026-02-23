package com.bass.bumpdesk

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class GestureSlopTest {
    @Test
    fun testSlopLogic() {
        val slop = 25f
        val initialX = 100f
        val initialY = 100f
        
        // Micro-movement (10 pixels) - should be ignored
        val moveX1 = 105f
        val moveY1 = 105f
        val dist1 = hypot(moveX1 - initialX, moveY1 - initialY)
        assertFalse("Micro-movement should be within slop", dist1 > slop)
        
        // Significant movement (50 pixels) - should be a drag
        val moveX2 = 140f
        val moveY2 = 140f
        val dist2 = hypot(moveX2 - initialX, moveY2 - initialY)
        assertTrue("Large movement should exceed slop", dist2 > slop)
    }
}
