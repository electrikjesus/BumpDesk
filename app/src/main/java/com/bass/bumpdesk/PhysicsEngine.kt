package com.bass.bumpdesk

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sqrt

class PhysicsEngine {
    var friction = 0.94f
    var wallBounce = 0.4f
    var restitution = 0.25f
    val ROOM_SIZE = 10.0f
    val INFINITE_SIZE = 50.0f
    val DEFAULT_SCALE = 0.5f
    val UI_MARGIN = 0.2f

    fun update(
        items: MutableList<BumpItem>,
        piles: MutableList<Pile>,
        selectedItem: BumpItem?,
        onBump: () -> Unit
    ) {
        piles.forEach { pile ->
            constrainPile(pile)

            pile.items.forEachIndexed { index, item ->
                if (item == selectedItem) return@forEachIndexed
                
                val targetScale = when {
                    pile.isExpanded -> DEFAULT_SCALE
                    pile.layoutMode == Pile.LayoutMode.CAROUSEL -> 1.5f * pile.scale
                    pile.layoutMode == Pile.LayoutMode.GRID -> 0.8f * pile.scale
                    else -> DEFAULT_SCALE
                }
                
                item.scale += (targetScale - item.scale) * 0.1f
                
                val targetPos = calculateTargetPositionInPile(pile, index)
                item.position[0] += (targetPos[0] - item.position[0]) * 0.15f
                item.position[1] += (targetPos[1] - item.position[1]) * 0.15f
                item.position[2] += (targetPos[2] - item.position[2]) * 0.15f
                
                item.surface = pile.surface
                item.velocity[0] = 0f
                item.velocity[1] = 0f
                item.velocity[2] = 0f
            }
        }

        val activeItems = items.filter { item -> !isInPile(item, piles) }
        activeItems.forEach { item ->
            if (item == selectedItem) {
                applyConstraints(item, onBump)
                return@forEach
            }

            if (!item.isPinned) {
                if (item.surface != BumpItem.Surface.FLOOR) {
                    item.velocity[1] -= 0.01f
                }

                item.position[0] += item.velocity[0]
                item.position[1] += item.velocity[1]
                item.position[2] += item.velocity[2]
                
                item.velocity[0] *= friction
                item.velocity[1] *= friction
                item.velocity[2] *= friction
            }

            applyConstraints(item, onBump)

            val otherItems = activeItems + listOfNotNull(selectedItem)
            otherItems.forEach { other ->
                if (item != other && item.surface == other.surface) {
                    resolveCollision(item, other, selectedItem, onBump)
                }
            }
        }
    }

    private fun constrainPile(pile: Pile) {
        val count = pile.items.size
        val side = ceil(sqrt(count.toDouble())).toInt().coerceAtLeast(1)
        val spacing = if (pile.layoutMode == Pile.LayoutMode.GRID) 2.0f * pile.scale else 1.2f
        val halfDim = (side * spacing) / 2f
        
        when (pile.surface) {
            BumpItem.Surface.FLOOR -> {
                val limit = INFINITE_SIZE - halfDim - UI_MARGIN
                pile.position[0] = pile.position[0].coerceIn(-limit, limit)
                pile.position[2] = pile.position[2].coerceIn(-limit, limit)
                pile.position[1] = 0.05f
            }
            BumpItem.Surface.BACK_WALL -> {
                val limit = ROOM_SIZE - halfDim - UI_MARGIN
                pile.position[0] = pile.position[0].coerceIn(-limit, limit)
                pile.position[1] = pile.position[1].coerceIn(0.05f + halfDim, 12f - halfDim)
                pile.position[2] = -9.4f // Standardized Recents/Wall Pile depth
            }
            BumpItem.Surface.LEFT_WALL -> {
                val limit = ROOM_SIZE - halfDim - UI_MARGIN
                pile.position[2] = pile.position[2].coerceIn(-limit, limit)
                pile.position[1] = pile.position[1].coerceIn(0.05f + halfDim, 12f - halfDim)
                pile.position[0] = -9.4f
            }
            BumpItem.Surface.RIGHT_WALL -> {
                val limit = ROOM_SIZE - halfDim - UI_MARGIN
                pile.position[2] = pile.position[2].coerceIn(-limit, limit)
                pile.position[1] = pile.position[1].coerceIn(0.05f + halfDim, 12f - halfDim)
                pile.position[0] = 9.4f
            }
        }
    }

    private fun applyConstraints(item: BumpItem, onBump: () -> Unit) {
        when (item.surface) {
            BumpItem.Surface.FLOOR -> {
                val limit = INFINITE_SIZE - item.scale - 0.05f
                item.position[1] = item.position[1].coerceAtLeast(0.05f)
                var hit = false
                if (item.position[0] > limit) { item.position[0] = limit; item.velocity[0] = -abs(item.velocity[0]) * wallBounce; hit = true }
                if (item.position[0] < -limit) { item.position[0] = -limit; item.velocity[0] = abs(item.velocity[0]) * wallBounce; hit = true }
                if (item.position[2] > limit) { item.position[2] = limit; item.velocity[2] = -abs(item.velocity[2]) * wallBounce; hit = true }
                if (item.position[2] < -limit) { item.position[2] = -limit; item.velocity[2] = abs(item.velocity[2]) * wallBounce; hit = true }
                if (!item.isPinned && hit && abs(item.velocity[0]) + abs(item.velocity[2]) > 0.05f) onBump()
            }
            BumpItem.Surface.BACK_WALL -> {
                item.position[2] = -9.95f
                if (!item.isPinned && item.position[1] <= 0.05f) {
                    item.surface = BumpItem.Surface.FLOOR; item.position[1] = 0.05f; item.velocity[1] = 0f
                }
                item.position[0] = item.position[0].coerceIn(-9.5f, 9.5f)
                item.position[1] = item.position[1].coerceIn(0.05f, 11.5f)
            }
            BumpItem.Surface.LEFT_WALL -> {
                item.position[0] = -9.95f
                if (!item.isPinned && item.position[1] <= 0.05f) {
                    item.surface = BumpItem.Surface.FLOOR; item.position[1] = 0.05f; item.velocity[1] = 0f
                }
                item.position[2] = item.position[2].coerceIn(-9.5f, 9.5f)
                item.position[1] = item.position[1].coerceIn(0.05f, 11.5f)
            }
            BumpItem.Surface.RIGHT_WALL -> {
                item.position[0] = 9.95f
                if (!item.isPinned && item.position[1] <= 0.05f) {
                    item.surface = BumpItem.Surface.FLOOR; item.position[1] = 0.05f; item.velocity[1] = 0f
                }
                item.position[2] = item.position[2].coerceIn(-9.5f, 9.5f)
                item.position[1] = item.position[1].coerceIn(0.05f, 11.5f)
            }
        }
    }

    private fun calculateTargetPositionInPile(pile: Pile, index: Int): FloatArray {
        val count = pile.items.size
        val side = ceil(sqrt(count.toDouble())).toInt().coerceAtLeast(1)
        val spacing = 1.2f
        val sideDim = side * spacing
        val halfDim = (sideDim / 2f + 0.2f) * pile.scale
        val totalHalfDimZ = (sideDim / 2f + 0.6f) * pile.scale

        if (pile.isExpanded) {
            val limitX = INFINITE_SIZE - halfDim - UI_MARGIN
            val limitZ = INFINITE_SIZE - totalHalfDimZ - UI_MARGIN
            val uiX = pile.position[0].coerceIn(-limitX, limitX)
            val uiZ = pile.position[2].coerceIn(-limitZ, limitZ)

            return floatArrayOf(
                uiX + (index % side - (side - 1) / 2f) * spacing,
                3.05f, // Task: Slightly higher than UI background (2.98) and elements (2.99)
                uiZ + (index / side - (side - 1) / 2f) * spacing + 0.5f * pile.scale
            )
        } else if (pile.isFannedOut) {
            val fanOutSpacing = 1.2f * pile.scale
            val offset = (index - (count - 1) / 2f) * fanOutSpacing
            
            return when (pile.surface) {
                BumpItem.Surface.FLOOR -> floatArrayOf(pile.position[0] + offset, 0.05f, pile.position[2])
                BumpItem.Surface.BACK_WALL -> floatArrayOf(pile.position[0] + offset, pile.position[1], pile.position[2])
                BumpItem.Surface.LEFT_WALL -> floatArrayOf(pile.position[0], pile.position[1], pile.position[2] + offset)
                BumpItem.Surface.RIGHT_WALL -> floatArrayOf(pile.position[0], pile.position[1], pile.position[2] - offset)
            }
        } else if (pile.layoutMode == Pile.LayoutMode.CAROUSEL) {
            val carouselSpacing = 3.5f * pile.scale
            val offset = (index - pile.currentIndex) * carouselSpacing
            
            return when (pile.surface) {
                BumpItem.Surface.BACK_WALL -> floatArrayOf(pile.position[0] + offset, pile.position[1], pile.position[2])
                BumpItem.Surface.LEFT_WALL -> floatArrayOf(pile.position[0], pile.position[1], pile.position[2] + offset)
                BumpItem.Surface.RIGHT_WALL -> floatArrayOf(pile.position[0], pile.position[1], pile.position[2] - offset)
                else -> floatArrayOf(pile.position[0] + offset, pile.position[1], pile.position[2])
            }
        } else if (pile.layoutMode == Pile.LayoutMode.GRID) {
            val gridSpacing = 2.0f * pile.scale
            return when (pile.surface) {
                BumpItem.Surface.FLOOR -> floatArrayOf(pile.position[0] + (index % side - (side - 1) / 2f) * gridSpacing, 0.05f, pile.position[2] + (index / side - (side - 1) / 2f) * gridSpacing)
                BumpItem.Surface.BACK_WALL -> floatArrayOf(pile.position[0] + (index % side - (side - 1) / 2f) * gridSpacing, pile.position[1] + ((side - 1) / 2f - index / side) * gridSpacing, pile.position[2])
                else -> floatArrayOf(pile.position[0], pile.position[1], pile.position[2])
            }
        } else {
            val leafOffset = if (index == pile.currentIndex) 0.5f else index * 0.05f
            return floatArrayOf(pile.position[0], pile.position[1] + leafOffset, pile.position[2])
        }
    }

    private fun resolveCollision(item: BumpItem, other: BumpItem, selectedItem: BumpItem?, onBump: () -> Unit) {
        val itemCanMove = !item.isPinned && item != selectedItem
        val otherCanMove = !other.isPinned && other != selectedItem
        if (!itemCanMove && !otherCanMove) return

        val itemMass = item.scale * item.scale
        val otherMass = other.scale * other.scale
        val totalMass = itemMass + otherMass

        val dx = item.position[0] - other.position[0]
        val dy = item.position[1] - other.position[1]
        val dz = item.position[2] - other.position[2]
        
        val distSq = dx * dx + dy * dy + dz * dz
        val minDist = item.scale + other.scale
        
        if (distSq < minDist * minDist && distSq > 0.0001f) {
            val dist = sqrt(distSq.toDouble()).toFloat()
            val overlap = (minDist - dist)
            val nx = dx / dist; val ny = dy / dist; val nz = dz / dist

            if (itemCanMove && !otherCanMove) {
                item.position[0] += nx * overlap; item.position[1] += ny * overlap; item.position[2] += nz * overlap
            } else if (!itemCanMove && otherCanMove) {
                other.position[0] -= nx * overlap; other.position[1] -= ny * overlap; other.position[2] -= nz * overlap
            } else {
                val itemRatio = otherMass / totalMass; val otherRatio = itemMass / totalMass
                item.position[0] += nx * overlap * itemRatio; item.position[1] += ny * overlap * itemRatio; item.position[2] += nz * overlap * itemRatio
                other.position[0] -= nx * overlap * otherRatio; other.position[1] -= ny * overlap * otherRatio; other.position[2] -= nz * overlap * otherRatio
            }

            val relVelX = item.velocity[0] - other.velocity[0]
            val relVelY = item.velocity[1] - other.velocity[1]
            val relVelZ = item.velocity[2] - other.velocity[2]
            val velAlongNormal = relVelX * nx + relVelY * ny + relVelZ * nz

            if (velAlongNormal < 0) {
                val j = -(1 + restitution) * velAlongNormal
                val impulse = j / (1 / itemMass + 1 / otherMass)
                if (itemCanMove) { item.velocity[0] += (impulse / itemMass) * nx; item.velocity[1] += (impulse / itemMass) * ny; item.velocity[2] += (impulse / itemMass) * nz }
                if (otherCanMove) { other.velocity[0] -= (impulse / otherMass) * nx; other.velocity[1] -= (impulse / otherMass) * ny; other.velocity[2] -= (impulse / otherMass) * nz }
                if (abs(j) > 0.1f) onBump()
            }
        }
    }

    fun isInPile(item: BumpItem, piles: List<Pile>) = piles.any { it.items.contains(item) }
}
