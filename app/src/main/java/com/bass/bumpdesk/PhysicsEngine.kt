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
    val ITEMS_PER_PAGE = FolderDrawerStyle.ITEMS_PER_PAGE

    private fun itemsPerPage(pile: Pile) = FolderDrawerStyle.itemsPerPage(pile)

    private fun recentsIconGridUsesInstantScale(pile: Pile): Boolean =
        pile.isRecentsPile() && pile.showsRecentsIconGrid() && pile.layoutAsExpandedDrawer()

    private fun expandedPileItemScale(pile: Pile): Float {
        if (pile.isRecentsPile()) {
            return if (pile.showsRecentsTaskCards()) {
                (defaultScale * 1.25f).coerceIn(0.4f, 2.0f)
            } else if (pile.showsRecentsIconGrid()) {
                FolderDrawerStyle.recentsDrawerIconScale(pile)
            } else if (FolderDrawerStyle.usesCompactRecentsCellGrid(pile)) {
                FolderDrawerStyle.compactRecentsIconScale(pile)
            } else {
                1.05f * pile.scale
            }
        }
        val base = when {
            pile.showsRecentsTaskCards() -> 1.0f * pile.scale
            else -> 0.8f * pile.scale
        }
        return base.coerceIn(0.4f, 2.5f)
    }

    var isInfiniteMode = false
    var isFlatFloorMode = false
    var floorHalfX = 30.0f
    var floorHalfZ = 30.0f

    fun update(
        items: MutableList<BumpItem>,
        piles: MutableList<Pile>,
        selectedItem: BumpItem?,
        onBump: (Float) -> Unit
    ) {
        val pileSnapshot = piles.toList()
        pileSnapshot.forEach { pile ->
            pile.reconcilePinnedOpenState()
            constrainPile(pile)

            if (pile.isDraggingOnDesktop && pile.layoutAsExpandedDrawer()) {
                layoutExpandedPileItems(pile, selectedItem)
                return@forEach
            }

            pile.items.toList().forEachIndexed { index, item ->
                if (item == selectedItem) return@forEachIndexed
                
                val pageSize = itemsPerPage(pile)
                val isExpandedLayout = pile.layoutAsExpandedDrawer()
                val isVisibleInPage = !isExpandedLayout ||
                    (index >= pile.scrollIndex * pageSize && index < (pile.scrollIndex + 1) * pageSize)
                
                val targetScale = when {
                    isExpandedLayout -> if (isVisibleInPage) 0.8f * pile.scale else 0.01f
                    pile.showsCollapsedPreview() -> pile.scale
                    pile.layoutMode == Pile.LayoutMode.CAROUSEL -> 1.5f * pile.scale
                    pile.layoutMode == Pile.LayoutMode.GRID -> 0.8f * pile.scale
                    else -> defaultScale
                }
                
                val finalTargetScale = if (isExpandedLayout) {
                    expandedPileItemScale(pile)
                } else {
                    targetScale
                }

                val scaleBlend = when {
                    recentsIconGridUsesInstantScale(pile) && isExpandedLayout -> 1f
                    else -> 0.1f
                }
                item.transform.scale += (finalTargetScale - item.transform.scale) * scaleBlend
                
                val targetPos = calculateTargetPositionInPile(pile, index)
                item.transform.position = item.transform.position + (targetPos - item.transform.position) * 0.15f
                
                item.transform.surface = pile.surface
                item.transform.velocity = Vector3()
            }
        }

        val itemsSnapshot = items.toList()
        val activeItems = itemsSnapshot.filter { item -> !isInPile(item, pileSnapshot) }
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

    private fun layoutExpandedPileItems(pile: Pile, @Suppress("UNUSED_PARAMETER") selectedItem: BumpItem?) {
        val snap = pile.isDraggingOnDesktop
        pile.items.toList().forEachIndexed { index, item ->
            val pageSize = itemsPerPage(pile)
            val isVisibleInPage =
                index >= pile.scrollIndex * pageSize && index < (pile.scrollIndex + 1) * pageSize
            val finalTargetScale = if (isVisibleInPage) {
                expandedPileItemScale(pile)
            } else {
                0.01f
            }
            val scaleBlend = when {
                snap -> 1f
                recentsIconGridUsesInstantScale(pile) -> 1f
                else -> 0.1f
            }
            item.transform.scale += (finalTargetScale - item.transform.scale) * scaleBlend

            val targetPos = calculateTargetPositionInPile(pile, index)
            val posBlend = if (snap) 1f else 0.15f
            item.transform.position = item.transform.position + (targetPos - item.transform.position) * posBlend
            item.transform.surface = pile.surface
            item.transform.velocity = Vector3()
        }
    }

    private fun constrainPile(pile: Pile) {
        val count = pile.items.size
        // 4x4 grid when expanded
        val side = when {
            pile.layoutAsExpandedDrawer() -> FolderDrawerStyle.gridColumns(pile).coerceAtLeast(FolderDrawerStyle.gridRows(pile))
            pile.showsCollapsedPreview() -> 1
            else -> ceil(sqrt(count.toDouble())).toInt().coerceAtLeast(1)
        }
        val spacing = when {
            pile.layoutAsExpandedDrawer() || pile.layoutMode == Pile.LayoutMode.GRID -> 2.0f * pile.scale
            pile.showsCollapsedPreview() -> gridSpacingBase * pile.scale
            else -> gridSpacingBase * pile.scale
        }
        val halfDim = (side * spacing) / 2f
        val marginX: Float
        val marginZ: Float
        if (pile.layoutAsExpandedDrawer()) {
            marginX = FolderDrawerStyle.panelHalfDimX(pile)
            marginZ = FolderDrawerStyle.panelHalfDimY(pile)
        } else {
            marginX = halfDim
            marginZ = halfDim
        }

        when (pile.surface) {
            BumpItem.Surface.FLOOR -> {
                val limitX = floorLimitX(marginX)
                val limitZ = floorLimitZ(marginZ)
                pile.position = pile.position.copy(
                    x = coerceSymmetric(pile.position.x, limitX),
                    z = coerceSymmetric(pile.position.z, limitZ),
                    y = 0.05f
                )
            }
            BumpItem.Surface.BACK_WALL -> {
                if (pile.isRecentsPile() && pile.layoutAsExpandedDrawer() && pile.recentsOnWall()) {
                    val halfX = FolderDrawerStyle.panelHalfDimX(pile)
                    val halfY = FolderDrawerStyle.panelHalfDimY(pile)
                    pile.position = pile.position.copy(
                        x = coerceRange(
                            pile.position.x,
                            -roomSize + halfX + UI_MARGIN,
                            roomSize - halfX - UI_MARGIN,
                        ),
                        y = coerceRange(pile.position.y, halfY + 0.5f, roomHeight - 2f - halfY),
                        z = -roomSize + FolderDrawerStyle.WALL_DRAWER_INSET,
                    )
                } else {
                    val limit = roomSize - marginX - UI_MARGIN
                    pile.position = pile.position.copy(
                        x = coerceSymmetric(pile.position.x, limit),
                        y = coerceRange(
                            pile.position.y,
                            0.05f + marginZ,
                            roomHeight - 2f - marginZ,
                        ),
                        z = -roomSize + 0.6f,
                    )
                }
            }
            BumpItem.Surface.LEFT_WALL -> {
                val limit = roomSize - marginX - UI_MARGIN
                pile.position = pile.position.copy(
                    z = coerceSymmetric(pile.position.z, limit),
                    y = coerceRange(
                        pile.position.y,
                        0.05f + marginZ,
                        roomHeight - 2f - marginZ,
                    ),
                    x = -roomSize + 0.6f
                )
            }
            BumpItem.Surface.RIGHT_WALL -> {
                val limit = roomSize - marginX - UI_MARGIN
                pile.position = pile.position.copy(
                    z = coerceSymmetric(pile.position.z, limit),
                    y = coerceRange(
                        pile.position.y,
                        0.05f + marginZ,
                        roomHeight - 2f - marginZ,
                    ),
                    x = roomSize - 0.6f
                )
            }
        }
    }

    private fun coerceSymmetric(value: Float, limit: Float): Float {
        val safe = limit.coerceAtLeast(0f)
        return value.coerceIn(-safe, safe)
    }

    private fun coerceRange(value: Float, min: Float, max: Float): Float {
        val low = minOf(min, max)
        val high = maxOf(min, max)
        return value.coerceIn(low, high)
    }

    private fun applyConstraints(item: BumpItem, onBump: (Float) -> Unit) {
        val scale = item.transform.scale
        when (item.transform.surface) {
            BumpItem.Surface.FLOOR -> {
                val limitX = floorLimitX(scale)
                val limitZ = floorLimitZ(scale)
                var newVel = item.transform.velocity
                var newPos = item.transform.position.copy(y = item.transform.position.y.coerceAtLeast(0.05f))
                var magnitude = 0f
                
                if (newPos.x > limitX) { newPos = newPos.copy(x = limitX); magnitude = abs(newVel.x); newVel = newVel.copy(x = -magnitude * wallBounce) }
                if (newPos.x < -limitX) { newPos = newPos.copy(x = -limitX); magnitude = abs(newVel.x); newVel = newVel.copy(x = magnitude * wallBounce) }
                if (newPos.z > limitZ) { newPos = newPos.copy(z = limitZ); magnitude = abs(newVel.z); newVel = newVel.copy(z = -magnitude * wallBounce) }
                if (newPos.z < -limitZ) { newPos = newPos.copy(z = -limitZ); magnitude = abs(newVel.z); newVel = newVel.copy(z = magnitude * wallBounce) }
                
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
        
        if (pile.layoutAsExpandedDrawer()) {
            val pageIndex = pile.scrollIndex
            val pageSize = itemsPerPage(pile)
            val isCurrentPage = index / pageSize == pageIndex

            when (pile.surface) {
                BumpItem.Surface.BACK_WALL -> {
                    val layout = FolderDrawerStyle.backWallLayout(pile, floorRoomHalfX(), roomSize)
                    if (!isCurrentPage) {
                        return Vector3(-10f, -10f, pile.position.z)
                    }
                    val (x, y) = FolderDrawerStyle.itemGridPositionOnBackWall(pile, index, layout)
                    val (_, _, drawZ) = FolderDrawerStyle.offsetFromWallSurface(
                        BumpItem.Surface.BACK_WALL,
                        layout.pos[0],
                        layout.pos[1],
                        layout.pos[2],
                        FolderDrawerStyle.WALL_ICON_DEPTH,
                    )
                    return Vector3(x, y, drawZ)
                }
                BumpItem.Surface.LEFT_WALL -> {
                    val layout = FolderDrawerStyle.leftWallLayout(pile, floorRoomHalfZ(), roomSize)
                    if (!isCurrentPage) {
                        return Vector3(pile.position.x, -10f, -10f)
                    }
                    val (z, y) = FolderDrawerStyle.itemGridPositionOnSideWall(pile, index, layout)
                    val (drawX, _, _) = FolderDrawerStyle.offsetFromWallSurface(
                        BumpItem.Surface.LEFT_WALL,
                        layout.pos[0],
                        layout.pos[1],
                        layout.pos[2],
                        FolderDrawerStyle.WALL_ICON_DEPTH,
                    )
                    return Vector3(drawX, y, z)
                }
                BumpItem.Surface.RIGHT_WALL -> {
                    val layout = FolderDrawerStyle.rightWallLayout(pile, floorRoomHalfZ(), roomSize)
                    if (!isCurrentPage) {
                        return Vector3(pile.position.x, -10f, -10f)
                    }
                    val (z, y) = FolderDrawerStyle.itemGridPositionOnSideWall(pile, index, layout)
                    val (drawX, _, _) = FolderDrawerStyle.offsetFromWallSurface(
                        BumpItem.Surface.RIGHT_WALL,
                        layout.pos[0],
                        layout.pos[1],
                        layout.pos[2],
                        FolderDrawerStyle.WALL_ICON_DEPTH,
                    )
                    return Vector3(drawX, y, z)
                }
                else -> {
                    val layout = FolderDrawerStyle.layout(pile, floorRoomHalfX(), floorRoomHalfZ())

                    val yPos = when {
                        !isCurrentPage -> -10f
                        pile.showsRecentsTaskCards() -> 1.1f * pile.scale
                        else -> FolderDrawerStyle.floorIconY(pile, layout)
                    }
                    val (x, z) = FolderDrawerStyle.itemGridPosition(pile, index, layout)

                    return Vector3(x, yPos, z)
                }
            }
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
        } else if (pile.layoutMode == Pile.LayoutMode.FOLDER && !pile.isExpanded) {
            return collapsedFolderPosition(pile)
        } else if (pile.isRecentsPile() && !pile.isExpanded) {
            return collapsedFolderPosition(pile)
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

    private fun collapsedFolderPosition(pile: Pile): Vector3 =
        when (pile.surface) {
            BumpItem.Surface.FLOOR -> pile.position.copy(y = 0.05f)
            BumpItem.Surface.BACK_WALL -> pile.position.copy(z = -roomSize + 0.6f)
            BumpItem.Surface.LEFT_WALL -> pile.position.copy(x = -roomSize + 0.6f)
            BumpItem.Surface.RIGHT_WALL -> pile.position.copy(x = roomSize - 0.6f)
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

    private fun floorRoomHalfX(): Float {
        if (isInfiniteMode) return INFINITE_SIZE
        if (isFlatFloorMode) return floorHalfX
        return roomSize
    }

    private fun floorRoomHalfZ(): Float {
        if (isInfiniteMode) return INFINITE_SIZE
        if (isFlatFloorMode) return floorHalfZ
        return roomSize
    }

    private fun floorLimitX(extraMargin: Float): Float {
        if (isInfiniteMode) return INFINITE_SIZE - extraMargin - 0.05f
        if (isFlatFloorMode) return floorHalfX - extraMargin - UI_MARGIN
        return roomSize - extraMargin - 0.05f
    }

    private fun floorLimitZ(extraMargin: Float): Float {
        if (isInfiniteMode) return INFINITE_SIZE - extraMargin - 0.05f
        if (isFlatFloorMode) return floorHalfZ - extraMargin - UI_MARGIN
        return roomSize - extraMargin - 0.05f
    }
}
