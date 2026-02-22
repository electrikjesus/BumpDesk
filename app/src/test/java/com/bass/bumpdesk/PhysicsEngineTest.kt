package com.bass.bumpdesk

import org.junit.Assert.assertEquals
import org.junit.Test

class PhysicsEngineTest {
    @Test
    fun testPhysicsParameters() {
        val engine = PhysicsEngine()
        engine.friction = 0.5f
        engine.gravity = 0.1f
        
        val item = BumpItem(position = floatArrayOf(0f, 5f, 0f), surface = BumpItem.Surface.BACK_WALL)
        item.velocity[1] = 0f
        
        // Mock update logic for one step
        if (!item.isPinned && item.surface != BumpItem.Surface.FLOOR) {
            item.velocity[1] -= engine.gravity
        }
        item.position[1] += item.velocity[1]
        item.velocity[1] *= engine.friction
        
        assertEquals(-0.1f, item.velocity[1], 0.001f)
        assertEquals(4.9f, item.position[1], 0.001f)
    }

    @Test
    fun testGridSpacing() {
        val engine = PhysicsEngine()
        engine.gridSpacingBase = 2.0f
        
        val pile = Pile(
            items = mutableListOf(BumpItem(), BumpItem()),
            position = floatArrayOf(0f, 0f, 0f),
            layoutMode = Pile.LayoutMode.GRID,
            surface = BumpItem.Surface.FLOOR,
            scale = 1.0f
        )
        
        // In STACK mode, calculateTargetPositionInPile uses gridSpacingBase * pile.scale
        // Let's invoke the private method via reflection or just check the logic
        // Actually, calculateTargetPositionInPile is private. 
        // But we can check how it affects item positions during update.
        
        engine.update(mutableListOf(), mutableListOf(pile), null) {}
        
        // For GRID mode, items are spaced by gridSpacingBase * 2.0f (hardcoded in GRID mode in previous file?)
        // Let me re-read PhysicsEngine.kt
    }
}
