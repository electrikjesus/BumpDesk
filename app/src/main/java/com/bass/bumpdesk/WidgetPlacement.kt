package com.bass.bumpdesk

import kotlin.math.max

/** Keeps widgets fully inside the active desktop bounds when switching room / flat-floor modes. */
object WidgetPlacement {
    private const val WALL_INSET = 0.1f
    private const val FLOOR_Y = 0.1f
    private const val EDGE_MARGIN = 0.08f

    data class RoomBounds(
        val boundX: Float,
        val boundZ: Float,
        val roomSize: Float,
        val roomHeight: Float,
        val isFlatFloorMode: Boolean,
        val isInfiniteMode: Boolean,
    )

    fun boundsFrom(
        boundX: Float,
        boundZ: Float,
        roomSize: Float,
        roomHeight: Float,
        isFlatFloorMode: Boolean,
        isInfiniteMode: Boolean,
    ): RoomBounds = RoomBounds(
        boundX = boundX,
        boundZ = boundZ,
        roomSize = roomSize,
        roomHeight = roomHeight,
        isFlatFloorMode = isFlatFloorMode,
        isInfiniteMode = isInfiniteMode,
    )

    fun boundsFrom(interactionManager: InteractionManager): RoomBounds {
        val boundX = if (interactionManager.isFlatFloorMode) {
            interactionManager.floorHalfX
        } else {
            interactionManager.roomSize
        }
        val boundZ = if (interactionManager.isFlatFloorMode) {
            interactionManager.floorHalfZ
        } else {
            interactionManager.roomSize
        }
        return RoomBounds(
            boundX = boundX,
            boundZ = boundZ,
            roomSize = interactionManager.roomSize,
            roomHeight = interactionManager.roomHeight,
            isFlatFloorMode = interactionManager.isFlatFloorMode,
            isInfiniteMode = interactionManager.isInfiniteMode,
        )
    }

    fun constrainAll(
        widgets: List<WidgetItem>,
        bounds: RoomBounds,
        relocateOverlaps: Boolean = true,
    ): Boolean {
        if (bounds.isInfiniteMode) return false
        var changed = false
        widgets.forEach { widget ->
            if (constrain(widget, bounds)) changed = true
        }
        if (relocateOverlaps && separateOverlappingFloorWidgets(widgets, bounds)) changed = true
        return changed
    }

    fun constrain(widget: WidgetItem, bounds: RoomBounds): Boolean {
        if (bounds.isInfiniteMode) return false

        if (bounds.isFlatFloorMode && widget.surface != BumpItem.Surface.FLOOR) {
            migrateWallWidgetToFloor(widget, bounds)
            BumpDeskLog.i(
                BumpDeskLog.Tag.WIDGET,
                "pinSurface",
                "migratedToFloor ${WidgetSurfaceTransform.surfaceAttachmentDescription(widget, bounds.roomSize)}",
            )
            return true
        }

        val before = widget.position.copy()
        val beforeSurface = widget.surface
        when (widget.surface) {
            BumpItem.Surface.FLOOR -> constrainFloorWidget(widget, bounds)
            BumpItem.Surface.BACK_WALL -> constrainBackWallWidget(widget, bounds)
            BumpItem.Surface.LEFT_WALL -> constrainLeftWallWidget(widget, bounds)
            BumpItem.Surface.RIGHT_WALL -> constrainRightWallWidget(widget, bounds)
        }
        return if (widget.surface != beforeSurface || widget.position != before) {
            if (widget.surface != beforeSurface) {
                BumpDeskLog.i(
                    BumpDeskLog.Tag.WIDGET,
                    "pinSurface",
                    "faceChanged ${beforeSurface}->${widget.surface} " +
                        WidgetSurfaceTransform.surfaceAttachmentDescription(widget, bounds.roomSize),
                )
            }
            true
        } else {
            false
        }
    }

    private fun migrateWallWidgetToFloor(widget: WidgetItem, bounds: RoomBounds) {
        val half = widget.displayHalfSize()
        val mappedZ = when (widget.surface) {
            BumpItem.Surface.BACK_WALL -> {
                val span = (bounds.roomHeight - 1f).coerceAtLeast(1f)
                ((widget.position.y / span) * 2f - 1f) * bounds.boundZ
            }
            BumpItem.Surface.LEFT_WALL, BumpItem.Surface.RIGHT_WALL -> widget.position.z
            else -> widget.position.z
        }
        widget.surface = BumpItem.Surface.FLOOR
        widget.position = Vector3(
            clampHorizontal(widget.position.x, half.x, bounds.boundX),
            FLOOR_Y,
            clampHorizontal(mappedZ, half.z, bounds.boundZ),
        )
    }

    private fun constrainFloorWidget(widget: WidgetItem, bounds: RoomBounds) {
        val half = widget.displayHalfSize()
        widget.position = Vector3(
            clampHorizontal(widget.position.x, half.x, bounds.boundX),
            FLOOR_Y,
            clampHorizontal(widget.position.z, half.z, bounds.boundZ),
        )
    }

    private fun constrainBackWallWidget(widget: WidgetItem, bounds: RoomBounds) {
        val half = widget.displayHalfSize()
        val minY = half.z + 0.5f
        val maxY = bounds.roomHeight - 2f - half.z
        widget.position = Vector3(
            clampHorizontal(widget.position.x, half.x, bounds.boundX),
            if (minY <= maxY) widget.position.y.coerceIn(minY, maxY) else widget.position.y,
            -bounds.roomSize + WALL_INSET,
        )
    }

    private fun constrainLeftWallWidget(widget: WidgetItem, bounds: RoomBounds) {
        val half = widget.displayHalfSize()
        val minY = half.z + 0.5f
        val maxY = bounds.roomHeight - 2f - half.z
        widget.position = Vector3(
            -bounds.roomSize + WALL_INSET,
            if (minY <= maxY) widget.position.y.coerceIn(minY, maxY) else widget.position.y,
            clampHorizontal(widget.position.z, half.x, bounds.boundZ),
        )
    }

    private fun constrainRightWallWidget(widget: WidgetItem, bounds: RoomBounds) {
        val half = widget.displayHalfSize()
        val minY = half.z + 0.5f
        val maxY = bounds.roomHeight - 2f - half.z
        widget.position = Vector3(
            bounds.roomSize - WALL_INSET,
            if (minY <= maxY) widget.position.y.coerceIn(minY, maxY) else widget.position.y,
            clampHorizontal(widget.position.z, half.x, bounds.boundZ),
        )
    }

    private fun clampHorizontal(value: Float, half: Float, bound: Float): Float {
        val limit = max(bound - half - EDGE_MARGIN, 0f)
        return if (limit <= 0f) 0f else value.coerceIn(-limit, limit)
    }

    private fun separateOverlappingFloorWidgets(widgets: List<WidgetItem>, bounds: RoomBounds): Boolean {
        if (!bounds.isFlatFloorMode) return false
        val floorWidgets = widgets.filter { it.surface == BumpItem.Surface.FLOOR }
        var changed = false
        for (i in floorWidgets.indices) {
            for (j in i + 1 until floorWidgets.size) {
                val first = floorWidgets[i]
                val second = floorWidgets[j]
                if (!floorWidgetsOverlap(first, second)) continue
                val moved = if (first.appWidgetId <= second.appWidgetId) second else first
                val anchor = if (moved === second) first else second
                nudgeFloorWidgetApart(moved, anchor, bounds)
                changed = true
            }
        }
        return changed
    }

    private fun floorWidgetsOverlap(a: WidgetItem, b: WidgetItem): Boolean {
        val aHalf = a.displayHalfSize()
        val bHalf = b.displayHalfSize()
        return kotlin.math.abs(a.position.x - b.position.x) < (aHalf.x + bHalf.x + 0.05f) &&
            kotlin.math.abs(a.position.z - b.position.z) < (aHalf.z + bHalf.z + 0.05f)
    }

    private fun nudgeFloorWidgetApart(widget: WidgetItem, anchor: WidgetItem, bounds: RoomBounds) {
        val gap = widget.displayHalfSize().x + anchor.displayHalfSize().x + 0.35f
        widget.position = widget.position.copy(
            x = anchor.position.x + gap,
            z = anchor.position.z,
            y = FLOOR_Y,
        )
        constrainFloorWidget(widget, bounds)
    }
}
