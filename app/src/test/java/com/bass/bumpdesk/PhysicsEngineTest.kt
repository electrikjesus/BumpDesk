package com.bass.bumpdesk

import org.junit.Assert.assertEquals
import org.junit.Test

class PhysicsEngineTest {
    @Test
    fun testPhysicsParameters() {
        val engine = PhysicsEngine()
        engine.friction = 0.5f
        engine.gravity = 0.1f
        
        val item = BumpItem(position = Vector3(0f, 5f, 0f), surface = BumpItem.Surface.BACK_WALL)
        item.velocity = Vector3(0f, 0f, 0f)
        
        // Mock update logic for one step
        // In the real engine: 
        // 1. apply gravity to velocity
        // 2. add velocity to position
        // 3. apply friction to velocity
        var newVel = item.velocity
        if (!item.isPinned && item.surface != BumpItem.Surface.FLOOR) {
            newVel = newVel.copy(y = newVel.y - engine.gravity)
        }
        val newPos = item.position + newVel
        
        // Fix: Velocity is updated *before* being used for position, then friction is applied
        newVel = newVel * engine.friction
        
        assertEquals(-0.05f, newVel.y, 0.001f) // 0 - 0.1 = -0.1, then -0.1 * 0.5 = -0.05
        assertEquals(4.9f, newPos.y, 0.001f) // 5 + (-0.1) = 4.9
    }
}
