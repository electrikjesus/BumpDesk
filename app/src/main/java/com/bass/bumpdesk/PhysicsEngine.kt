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
    
    var roomSize = 30.0f
    var roomHeight = 30.0f
    val INFINITE_SIZE = 100.0f
    val UI_MARGIN = 0.2f
    val ITEMS_PER_PAGE = 16

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
                
                val isVisibleInPage = !pile.isExpanded || (index >= pile.scrollIndex * ITEMS_PER_PAGE && index < (pile.scrollIndex + 1) * ITEMS_PER_PAGE)
                
                val targetScale = when {
                    pile.isExpanded -> if (isVisibleInPage) 0.8f * pile.scale else 0.01f
                    pile.layoutMode == Pile.LayoutMode.CAROUSEL -> 1.5f * pile.scale
                    pile.layoutMode == Pile.LayoutMode.GRID -> 0.8f * pile.scale
                    else -> defaultScale
                }
                
                // If the pile is expanded, we want to scale the items relative to the pile scale,
                // but we also need to account for the global defaultScale.
                // We use a fixed base scale for expanded icons (0.8f) and multiply by the folder's scale.
                val finalTargetScale = if (pile.isExpanded) {
                    (0.8f * pile.scale).coerceIn(0.4f, 2.0f)
                } else {
                    targetScale
                }

                item.transform.scale += (finalTargetScale - item.transform.scale) * 0.1f
                
                val targetPos = calculateTargetPositionInPile(pile, index)
                item.transform.position = item.transform.position + (targetPos - item.transform.position) * 0.15f
                
                item.transform.surface = pile.surface
                item.transform.velocity = Vector3()
            }
        }

        val activeItems = items.filter { item -> !isInPile(item, piles) }
        activeItems.forEach { item ->
            if (item == selectedItem) {
                applyConstraints(item, onBump)
                return@forEach
            }

            if (!item.transform.isPinned) {
                if (item.transform.surface != BumpItem.Surface.FLOOR) {
                    item.transform.velocity = item.transform.velocity.copy(y = item.transform.velocity.y - gravity)
                }

                item.transform.position = item.transform.position + item.transform.velocity
                item.transform.velocity = item.transform.velocity * friction
            }

            applyConstraints(item, onBump)

            val otherItems = activeItems + listOfNotNull(selectedItem)
            otherItems.forEach { other ->
                if (item != other && item.transform.surface == other.transform.surface) {
                    resolveCollision(item, other, selectedItem, onBump)
                }
            }
        }
    }

    private fun constrainPile(pile: Pile) {
        val count = pile.items.size
        // 4x4 grid when expanded
        val side = if (pile.isExpanded) 4 else ceil(sqrt(count.toDouble())).toInt().coerceAtLeast(1)
        val spacing = if (pile.layoutMode == Pile.LayoutMode.GRID || pile.isExpanded) 2.0f * pile.scale else gridSpacingBase * pile.scale
        val halfDim = (side * spacing) / 2f
        
        when (pile.surface) {
            BumpItem.Surface.FLOOR -> {
                val limit = if (isInfiniteMode) INFINITE_SIZE else roomSize
                val bound = limit - halfDim - UI_MARGIN
                pile.position = pile.position.copy(
                    x = pile.position.x.coerceIn(-bound, bound),
                    z = pile.position.z.coerceIn(-bound, bound),
                    y = 0.05f
                )
            }
            BumpItem.Surface.BACK_WALL -> {
                val limit = roomSize - halfDim - UI_MARGIN
                pile.position = pile.position.copy(
                    x = pile.position.x.coerceIn(-limit, limit),
                    y = pile.position.y.coerceIn(0.05f + halfDim, roomHeight - 2f - halfDim),
                    z = -roomSize + 0.6f
                )
            }
            BumpItem.Surface.LEFT_WALL -> {
                val limit = roomSize - halfDim - UI_MARGIN
                pile.position = pile.position.copy(
                    z = pile.position.z.coerceIn(-limit, limit),
                    y = pile.position.y.coerceIn(0.05f + halfDim, roomHeight - 2f - halfDim),
                    x = -roomSize + 0.6f
                )
            }
            BumpItem.Surface.RIGHT_WALL -> {
                val limit = roomSize - halfDim - UI_MARGIN
                pile.position = pile.position.copy(
                    z = pile.position.z.coerceIn(-limit, limit),
                    y = pile.position.y.coerceIn(0.05f + halfDim, roomHeight - 2f - halfDim),
                    x = roomSize - 0.6f
                )
            }
        }
    }

    private fun applyConstraints(item: BumpItem, onBump: (Float) -> Unit) {
        val scale = item.transform.scale
        val limit = if (isInfiniteMode && item.transform.surface == BumpItem.Surface.FLOOR) INFINITE_SIZE - scale - 0.05f else roomSize - scale - 0.05f
        
        when (item.transform.surface) {
            BumpItem.Surface.FLOOR -> {
                var newVel = item.transform.velocity
                var newPos = item.transform.position.copy(y = item.transform.position.y.coerceAtLeast(0.05f))
                var magnitude = 0f
                
                if (newPos.x > limit) { newPos = newPos.copy(x = limit); magnitude = abs(newVel.x); newVel = newVel.copy(x = -magnitude * wallBounce) }
                if (newPos.x < -limit) { newPos = newPos.copy(x = -limit); magnitude = abs(newVel.x); newVel = newVel.copy(x = magnitude * wallBounce) }
                if (newPos.z > limit) { newPos = newPos.copy(z = limit); magnitude = abs(newVel.z); newVel = newVel.copy(z = -magnitude * wallBounce) }
                if (newPos.z < -limit) { newPos = newPos.copy(z = -limit); magnitude = abs(newVel.z); newVel = newVel.copy(z = magnitude * wallBounce) }
                
                item.transform.position = newPos
                item.transform.velocity = newVel
                if (!item.transform.isPinned && magnitude > 0.05f) onBump(magnitude)
            }
            BumpItem.Surface.BACK_WALL -> {
                item.transform.position = item.transform.position.copy(
                    z = -roomSize + 0.05f,
                    x = item.transform.position.x.coerceIn(-roomSize + 0.5f, roomSize - 0.5f),
                    y = item.transform.position.y.coerceIn(0.05f, roomHeight - 0.5f)
                )
                if (!item.transform.isPinned && item.transform.position.y <= 0.05f) {
                    item.transform.surface = BumpItem.Surface.FLOOR
                    item.transform.position = item.transform.position.copy(y = 0.05f)
                    item.transform.velocity = item.transform.velocity.copy(y = 0f)
                }
            }
            BumpItem.Surface.LEFT_WALL -> {
                item.transform.position = item.transform.position.copy(
                    x = -roomSize + 0.05f,
                    z = item.transform.position.z.coerceIn(-roomSize + 0.5f, roomSize - 0.5f),
                    y = item.transform.position.y.coerceIn(0.05f, roomHeight - 0.5f)
                )
                if (!item.transform.isPinned && item.transform.position.y <= 0.05f) {
                    item.transform.surface = BumpItem.Surface.FLOOR
                    item.transform.position = item.transform.position.copy(y = 0.05f)
                    item.transform.velocity = item.transform.velocity.copy(y = 0f)
                }
            }
            BumpItem.Surface.RIGHT_WALL -> {
                item.transform.position = item.transform.position.copy(
                    x = roomSize - 0.05f,
                    z = item.transform.position.z.coerceIn(-roomSize + 0.5f, roomSize - 0.5f),
                    y = item.transform.position.y.coerceIn(0.05f, roomHeight - 0.5f)
                )
                if (!item.transform.isPinned && item.transform.position.y <= 0.05f) {
                    item.transform.surface = BumpItem.Surface.FLOOR
                    item.transform.position = item.transform.position.copy(y = 0.05f)
                    item.transform.velocity = item.transform.velocity.copy(y = 0f)
                }
            }
        }
    }

    private fun calculateTargetPositionInPile(pile: Pile, index: Int): Vector3 {
        val count = pile.items.size
        
        if (pile.isExpanded) {
            val pageIndex = pile.scrollIndex
            val itemInPageIndex = index % ITEMS_PER_PAGE
            val isCurrentPage = index / ITEMS_PER_PAGE == pageIndex
            
            // Fixed 4x4 Grid
            val side = 4
            val spacing = 2.0f * pile.scale
            val halfDim = (side * spacing) / 2f
            val totalHalfDimZ = (side * spacing) / 2f + 0.6f * pile.scale

            val limit = if (isInfiniteMode) INFINITE_SIZE else roomSize
            val limitX = limit - halfDim - UI_MARGIN
            val limitZ = limit - totalHalfDimZ - UI_MARGIN
            val uiX = pile.position.x.coerceIn(-limitX, limitX)
            val uiZ = pile.position.z.coerceIn(-limitZ, limitZ)

            val row = itemInPageIndex / side
            val col = itemInPageIndex % side
            
            val yPos = if (isCurrentPage) 3.05f else -10f // Hide items not on current page
            
            return Vector3(
                uiX + (col - (side - 1) / 2f) * spacing,
                yPos,
                uiZ + (row - (side - 1) / 2f) * spacing + 0.5f * pile.scale
            )
        } else if (pile.isFannedOut) {
            val spacing = if (pile.layoutMode == Pile.LayoutMode.GRID) 2.0f * pile.scale else gridSpacingBase * pile.scale
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
            val side = ceil(sqrt(count.toDouble())).toInt().coerceAtLeast(1)
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
        val itemCanMove = !item.transform.isPinned && item != selectedItem
        val otherCanMove = !other.transform.isPinned && other != selectedItem
        if (!itemCanMove && !otherCanMove) return

        val itemScale = item.transform.scale
        val otherScale = other.transform.scale
        val itemMass = itemScale * itemScale
        val otherMass = otherScale * otherScale
        val totalMass = itemMass + otherMass

        val delta = item.transform.position - other.transform.position
        val distSq = delta.lengthSq()
        val minDist = itemScale + otherScale
        
        if (distSq < minDist * minDist && distSq > 0.0001f) {
            val dist = sqrt(distSq.toDouble()).toFloat()
            val overlap = (minDist - dist)
            val normal = delta / dist

            if (itemCanMove && !otherCanMove) {
                item.transform.position = item.transform.position + normal * overlap
            } else if (!itemCanMove && otherCanMove) {
                other.transform.position = other.transform.position - normal * overlap
            } else {
                val itemRatio = otherMass / totalMass; val otherRatio = itemMass / totalMass
                item.transform.position = item.transform.position + normal * (overlap * itemRatio)
                other.transform.position = other.transform.position - normal * (overlap * otherRatio)
            }

            val relVel = item.transform.velocity - other.transform.velocity
            val velAlongNormal = relVel.dot(normal)

            if (velAlongNormal < 0) {
                val j = -(1 + restitution) * velAlongNormal
                val impulse = j / (1 / itemMass + 1 / otherMass)
                if (itemCanMove) item.transform.velocity = item.transform.velocity + normal * (impulse / itemMass)
                if (otherCanMove) other.transform.velocity = other.transform.velocity - normal * (impulse / otherMass)
                if (abs(j) > 0.1f) onBump(abs(j))
            }
        }
    }

    fun isInPile(item: BumpItem, piles: List<Pile>) = piles.any { it.items.contains(item) }
}
