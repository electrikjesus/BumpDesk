package com.bass.bumpdesk.persistence

import com.bass.bumpdesk.BumpItem
import com.bass.bumpdesk.Pile
import com.bass.bumpdesk.SceneState
import com.bass.bumpdesk.Vector3
import com.bass.bumpdesk.WidgetItem
import kotlin.math.max

/**
 * Phase 3: positions and sizes as fractions of the active floor/wall bounds so one layout
 * remaps cleanly when posture or flat-floor frustum changes.
 */
object NormalizedLayout {

    private const val WALL_INSET = 0.1f
    private const val WALL_Y_BASE = 0.5f

    data class NormalizedVector(val nx: Float, val ny: Float, val nz: Float)

    fun normalizePosition(position: Vector3, surface: BumpItem.Surface, bounds: LayoutBounds): NormalizedVector {
        val safeBoundX = bounds.boundX.coerceAtLeast(0.01f)
        val safeBoundZ = bounds.boundZ.coerceAtLeast(0.01f)
        val safeRoomH = bounds.roomHeight.coerceAtLeast(1f)
        return when (surface) {
            BumpItem.Surface.FLOOR -> NormalizedVector(
                nx = position.x / safeBoundX,
                ny = position.y,
                nz = position.z / safeBoundZ,
            )
            BumpItem.Surface.BACK_WALL -> NormalizedVector(
                nx = position.x / safeBoundX,
                ny = position.y / safeRoomH,
                nz = 0f,
            )
            BumpItem.Surface.LEFT_WALL -> NormalizedVector(
                nx = 0f,
                ny = position.y / safeRoomH,
                nz = position.z / safeBoundZ,
            )
            BumpItem.Surface.RIGHT_WALL -> NormalizedVector(
                nx = 0f,
                ny = position.y / safeRoomH,
                nz = position.z / safeBoundZ,
            )
        }
    }

    fun denormalizePosition(normalized: NormalizedVector, surface: BumpItem.Surface, bounds: LayoutBounds): Vector3 {
        val safeBoundX = bounds.boundX.coerceAtLeast(0.01f)
        val safeBoundZ = bounds.boundZ.coerceAtLeast(0.01f)
        val safeRoomH = bounds.roomHeight.coerceAtLeast(1f)
        val safeRoomSize = bounds.roomSize.coerceAtLeast(0.01f)
        return when (surface) {
            BumpItem.Surface.FLOOR -> Vector3(
                normalized.nx * safeBoundX,
                normalized.ny,
                normalized.nz * safeBoundZ,
            )
            BumpItem.Surface.BACK_WALL -> Vector3(
                normalized.nx * safeBoundX,
                normalized.ny * safeRoomH,
                -safeRoomSize + WALL_INSET,
            )
            BumpItem.Surface.LEFT_WALL -> Vector3(
                -safeRoomSize + WALL_INSET,
                normalized.ny * safeRoomH,
                normalized.nz * safeBoundZ,
            )
            BumpItem.Surface.RIGHT_WALL -> Vector3(
                safeRoomSize - WALL_INSET,
                normalized.ny * safeRoomH,
                normalized.nz * safeBoundZ,
            )
        }
    }

    fun remapPosition(
        position: Vector3,
        surface: BumpItem.Surface,
        from: LayoutBounds,
        to: LayoutBounds,
    ): Vector3 {
        if (from.isSameGeometry(to)) return position.copy()
        val normalized = normalizePosition(position, surface, from)
        return denormalizePosition(normalized, surface, to)
    }

    fun normalizeHalfSize(sizeX: Float, sizeZ: Float, surface: BumpItem.Surface, bounds: LayoutBounds): Pair<Float, Float> {
        val safeBoundX = bounds.boundX.coerceAtLeast(0.01f)
        val safeBoundZ = bounds.boundZ.coerceAtLeast(0.01f)
        return when (surface) {
            BumpItem.Surface.FLOOR,
            BumpItem.Surface.BACK_WALL,
            -> sizeX / safeBoundX to sizeZ / safeBoundX
            BumpItem.Surface.LEFT_WALL,
            BumpItem.Surface.RIGHT_WALL,
            -> sizeX / safeBoundZ to sizeZ / safeBoundX
        }
    }

    fun denormalizeHalfSize(normX: Float, normZ: Float, surface: BumpItem.Surface, bounds: LayoutBounds): Pair<Float, Float> {
        val safeBoundX = bounds.boundX.coerceAtLeast(0.01f)
        val safeBoundZ = bounds.boundZ.coerceAtLeast(0.01f)
        return when (surface) {
            BumpItem.Surface.FLOOR,
            BumpItem.Surface.BACK_WALL,
            -> normX * safeBoundX to normZ * safeBoundX
            BumpItem.Surface.LEFT_WALL,
            BumpItem.Surface.RIGHT_WALL,
            -> normX * safeBoundZ to normZ * safeBoundX
        }
    }

    fun remapHalfSize(
        sizeX: Float,
        sizeZ: Float,
        surface: BumpItem.Surface,
        from: LayoutBounds,
        to: LayoutBounds,
    ): Pair<Float, Float> {
        if (from.isSameGeometry(to)) return sizeX to sizeZ
        val (nx, nz) = normalizeHalfSize(sizeX, sizeZ, surface, from)
        return denormalizeHalfSize(nx, nz, surface, to)
    }

    fun remapScene(sceneState: SceneState, from: LayoutBounds, to: LayoutBounds) {
        if (from.isSameGeometry(to)) return
        sceneState.bumpItems.forEach { item ->
            val surface = item.transform.surface
            item.transform.position = remapPosition(item.transform.position, surface, from, to)
            if (item.appearance.type == BumpItem.Type.STICKY_NOTE) {
                val (hx, hz) = remapHalfSize(
                    item.transform.shapeHalfX,
                    item.transform.shapeHalfZ,
                    surface,
                    from,
                    to,
                )
                item.transform.shapeHalfX = hx
                item.transform.shapeHalfZ = hz
            }
        }
        sceneState.piles.filter { !it.isSystem }.forEach { pile ->
            pile.position = remapPosition(pile.position, pile.surface, from, to)
            pile.items.forEach { item ->
                val surface = item.transform.surface
                item.transform.position = remapPosition(item.transform.position, surface, from, to)
            }
        }
        sceneState.widgetItems.forEach { widget ->
            widget.position = remapPosition(widget.position, widget.surface, from, to)
        }
    }

    /** Wall-attached Y uses a span that scales with room height but keeps a stable floor offset. */
    fun wallVerticalSpan(bounds: LayoutBounds, halfDepth: Float): Float =
        max(bounds.roomHeight - 2f - halfDepth - WALL_Y_BASE, 1f)
}
