package com.bass.bumpdesk

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sqrt

class PhysicsEngine {
    var friction = 0.94f
    var wallBounce = 0.4f
    var restitution = 0.25f
    var gravity = 0.01f
    var defaultScale = 0.5f
    var gridSpacingBase = 1.2f
    
    val ROOM_SIZE = 10.0f
    val INFINITE_SIZE = 50.0f
    val UI_MARGIN = 0.2f

    var isInfiniteMode = false

    fun update(
        items: MutableList<BumpItem>,
        piles: MutableList<Pile>,
        selectedItem: BumpItem?,
        onBump: (Float) -> Unit
    ) {
        piles.forEach { pile ->
            constrainPile(pile)

            pile.items.forEachIndexed { index, item ->
                if (item == selectedItem) return@forEachIndexed
                
                val targetScale = when {
                    pile.isExpanded -> defaultScale
                    pile.layoutMode == Pile.LayoutMode.CAROUSEL -> 1.5f * pile.scale
                    pile.layoutMode == Pile.LayoutMode.GRID -> 0.8f * pile.scale
                    else -> defaultScale
                }
                
                item.scale += (targetScale - item.scale) * 0.1f
                
                val targetPos = calculateTargetPositionInPile(pile, index)
                item.position = item.position + (targetPos - item.position) * 0.15f
                
                item.surface = pile.surface
                item.velocity = Vector3()
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
                    item.velocity = item.velocity.copy(y = item.velocity.y - gravity)
                }

                item.position = item.position + item.velocity
                item.velocity = item.velocity * friction
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
        val spacing = if (pile.layoutMode == Pile.LayoutMode.GRID) 2.0f * pile.scale else gridSpacingBase * pile.scale
        val halfDim = (side * spacing) / 2f
        
        when (pile.surface) {
            BumpItem.Surface.FLOOR -> {
                val limit = if (isInfiniteMode) INFINITE_SIZE else ROOM_SIZE
                val bound = limit - halfDim - UI_MARGIN
                pile.position = pile.position.copy(
                    x = pile.position.x.coerceIn(-bound, bound),
                    z = pile.position.z.coerceIn(-bound, bound),
                    y = 0.05f
                )
            }
            BumpItem.Surface.BACK_WALL -> {
                val limit = ROOM_SIZE - halfDim - UI_MARGIN
                pile.position = pile.position.copy(
                    x = pile.position.x.coerceIn(-limit, limit),
                    y = pile.position.y.coerceIn(0.05f + halfDim, 12f - halfDim),
                    z = -9.4f
                )
            }
            BumpItem.Surface.LEFT_WALL -> {
                val limit = ROOM_SIZE - halfDim - UI_MARGIN
                pile.position = pile.position.copy(
                    z = pile.position.z.coerceIn(-limit, limit),
                    y = pile.position.y.coerceIn(0.05f + halfDim, 12f - halfDim),
                    x = -9.4f
                )
            }
            BumpItem.Surface.RIGHT_WALL -> {
                val limit = ROOM_SIZE - halfDim - UI_MARGIN
                pile.position = pile.position.copy(
                    z = pile.position.z.coerceIn(-limit, limit),
                    y = pile.position.y.coerceIn(0.05f + halfDim, 12f - halfDim),
                    x = 9.4f
                )
            }
        }
    }

    private fun applyConstraints(item: BumpItem, onBump: (Float) -> Unit) {
        val limit = if (isInfiniteMode && item.surface == BumpItem.Surface.FLOOR) INFINITE_SIZE - item.scale - 0.05f else ROOM_SIZE - item.scale - 0.05f
        
        when (item.surface) {
            BumpItem.Surface.FLOOR -> {
                var newVel = item.velocity
                var newPos = item.position.copy(y = item.position.y.coerceAtLeast(0.05f))
                var magnitude = 0f
                
                if (newPos.x > limit) { newPos = newPos.copy(x = limit); magnitude = abs(newVel.x); newVel = newVel.copy(x = -magnitude * wallBounce) }
                if (newPos.x < -limit) { newPos = newPos.copy(x = -limit); magnitude = abs(newVel.x); newVel = newVel.copy(x = magnitude * wallBounce) }
                if (newPos.z > limit) { newPos = newPos.copy(z = limit); magnitude = abs(newVel.z); newVel = newVel.copy(z = -magnitude * wallBounce) }
                if (newPos.z < -limit) { newPos = newPos.copy(z = -limit); magnitude = abs(newVel.z); newVel = newVel.copy(z = magnitude * wallBounce) }
                
                item.position = newPos
                item.velocity = newVel
                if (!item.isPinned && magnitude > 0.05f) onBump(magnitude)
            }
            BumpItem.Surface.BACK_WALL -> {
                item.position = item.position.copy(
                    z = -9.95f,
                    x = item.position.x.coerceIn(-9.5f, 9.5f),
                    y = item.position.y.coerceIn(0.05f, 11.5f)
                )
                if (!item.isPinned && item.position.y <= 0.05f) {
                    item.surface = BumpItem.Surface.FLOOR
                    item.position = item.position.copy(y = 0.05f)
                    item.velocity = item.velocity.copy(y = 0f)
                }
            }
            BumpItem.Surface.LEFT_WALL -> {
                item.position = item.position.copy(
                    x = -9.95f,
                    z = item.position.z.coerceIn(-9.5f, 9.5f),
                    y = item.position.y.coerceIn(0.05f, 11.5f)
                )
                if (!item.isPinned && item.position.y <= 0.05f) {
                    item.surface = BumpItem.Surface.FLOOR
                    item.position = item.position.copy(y = 0.05f)
                    item.velocity = item.velocity.copy(y = 0f)
                }
            }
            BumpItem.Surface.RIGHT_WALL -> {
                item.position = item.position.copy(
                    x = 9.95f,
                    z = item.position.z.coerceIn(-9.5f, 9.5f),
                    y = item.position.y.coerceIn(0.05f, 11.5f)
                )
                if (!item.isPinned && item.position.y <= 0.05f) {
                    item.surface = BumpItem.Surface.FLOOR
                    item.position = item.position.copy(y = 0.05f)
                    item.velocity = item.velocity.copy(y = 0f)
                }
            }
        }
    }

    private fun calculateTargetPositionInPile(pile: Pile, index: Int): Vector3 {
        val count = pile.items.size
        val side = ceil(sqrt(count.toDouble())).toInt().coerceAtLeast(1)
        val spacing = if (pile.layoutMode == Pile.LayoutMode.GRID) 2.0f * pile.scale else gridSpacingBase * pile.scale
        val halfDim = (side * spacing) / 2f
        val totalHalfDimZ = (side * spacing) / 2f + 0.6f * pile.scale

        if (pile.isExpanded) {
            val limit = if (isInfiniteMode) INFINITE_SIZE else ROOM_SIZE
            val limitX = limit - halfDim - UI_MARGIN
            val limitZ = limit - totalHalfDimZ - UI_MARGIN
            val uiX = pile.position.x.coerceIn(-limitX, limitX)
            val uiZ = pile.position.z.coerceIn(-limitZ, limitZ)

            return Vector3(
                uiX + (index % side - (side - 1) / 2f) * spacing,
                3.05f,
                uiZ + (index / side - (side - 1) / 2f) * spacing + 0.5f * pile.scale
            )
        } else if (pile.isFannedOut) {
            val offset = (index - (count - 1) / 2f) * spacing
            return when (pile.surface) {
                BumpItem.Surface.FLOOR -> pile.position.copy(x = pile.position.x + offset, y = 0.05f)
                BumpItem.Surface.BACK_WALL -> pile.position.copy(x = pile.position.x + offset)
                BumpItem.Surface.LEFT_WALL -> pile.position.copy(z = pile.position.z + offset)
                BumpItem.Surface.RIGHT_WALL -> pile.position.copy(z = pile.position.z - offset)
            }
        } else if (pile.layoutMode == Pile.LayoutMode.CAROUSEL) {
            val carouselSpacing = 3.5f * pile.scale
            val offset = (index - pile.currentIndex) * carouselSpacing
            return when (pile.surface) {
                BumpItem.Surface.LEFT_WALL -> pile.position.copy(z = pile.position.z + offset)
                BumpItem.Surface.RIGHT_WALL -> pile.position.copy(z = pile.position.z - offset)
                else -> pile.position.copy(x = pile.position.x + offset)
            }
        } else if (pile.layoutMode == Pile.LayoutMode.GRID) {
            val gridSpacing = 2.0f * pile.scale
            return when (pile.surface) {
                BumpItem.Surface.FLOOR -> pile.position.copy(x = pile.position.x + (index % side - (side - 1) / 2f) * gridSpacing, y = 0.05f, z = pile.position.z + (index / side - (side - 1) / 2f) * gridSpacing)
                BumpItem.Surface.BACK_WALL -> pile.position.copy(x = pile.position.x + (index % side - (side - 1) / 2f) * gridSpacing, y = pile.position.y + ((side - 1) / 2f - index / side) * gridSpacing)
                else -> pile.position
            }
        } else {
            val leafOffset = if (index == pile.currentIndex) 0.5f else index * 0.05f
            return pile.position.copy(y = pile.position.y + leafOffset)
        }
    }

    private fun resolveCollision(item: BumpItem, other: BumpItem, selectedItem: BumpItem?, onBump: (Float) -> Unit) {
        val itemCanMove = !item.isPinned && item != selectedItem
        val otherCanMove = !other.isPinned && other != selectedItem
        if (!itemCanMove && !otherCanMove) return

        val itemMass = item.scale * item.scale
        val otherMass = other.scale * other.scale
        val totalMass = itemMass + otherMass

        val delta = item.position - other.position
        val distSq = delta.lengthSq()
        val minDist = item.scale + other.scale
        
        if (distSq < minDist * minDist && distSq > 0.0001f) {
            val dist = sqrt(distSq.toDouble()).toFloat()
            val overlap = (minDist - dist)
            val normal = delta / dist

            if (itemCanMove && !otherCanMove) {
                item.position = item.position + normal * overlap
            } else if (!itemCanMove && otherCanMove) {
                other.position = other.position - normal * overlap
            } else {
                val itemRatio = otherMass / totalMass; val otherRatio = itemMass / totalMass
                item.position = item.position + normal * (overlap * itemRatio)
                other.position = other.position - normal * (overlap * otherRatio)
            }

            val relVel = item.velocity - other.velocity
            val velAlongNormal = relVel.dot(normal)

            if (velAlongNormal < 0) {
                val j = -(1 + restitution) * velAlongNormal
                val impulse = j / (1 / itemMass + 1 / otherMass)
                if (itemCanMove) item.velocity = item.velocity + normal * (impulse / itemMass)
                if (otherCanMove) other.velocity = other.velocity - normal * (impulse / otherMass)
                if (abs(j) > 0.1f) onBump(abs(j))
            }
        }
    }

    fun isInPile(item: BumpItem, piles: List<Pile>) = piles.any { it.items.contains(item) }
}
