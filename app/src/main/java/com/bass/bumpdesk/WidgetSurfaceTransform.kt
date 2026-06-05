package com.bass.bumpdesk

import android.opengl.Matrix

/** Shared wall orientation + texture UV mapping for widgets (matches [ItemRenderer] icon quads). */
object WidgetSurfaceTransform {
    const val WALL_INSET = 0.1f

    fun applyModelRotation(surface: BumpItem.Surface, matrix: FloatArray) {
        when (surface) {
            BumpItem.Surface.BACK_WALL -> {
                Matrix.rotateM(matrix, 0, 180f, 0f, 1f, 0f)
                Matrix.rotateM(matrix, 0, 90f, 1f, 0f, 0f)
                Matrix.rotateM(matrix, 0, 180f, 0f, 0f, 1f)
            }
            BumpItem.Surface.LEFT_WALL -> {
                Matrix.rotateM(matrix, 0, 90f, 0f, 1f, 0f)
                Matrix.rotateM(matrix, 0, 180f, 0f, 1f, 0f)
                Matrix.rotateM(matrix, 0, 90f, 1f, 0f, 0f)
                Matrix.rotateM(matrix, 0, 180f, 0f, 0f, 1f)
            }
            BumpItem.Surface.RIGHT_WALL -> {
                Matrix.rotateM(matrix, 0, -90f, 0f, 1f, 0f)
                Matrix.rotateM(matrix, 0, 180f, 0f, 1f, 0f)
                Matrix.rotateM(matrix, 0, 90f, 1f, 0f, 0f)
                Matrix.rotateM(matrix, 0, 180f, 0f, 0f, 1f)
            }
            BumpItem.Surface.FLOOR -> Unit
        }
    }

    fun wallPlaneT(surface: BumpItem.Surface, roomSize: Float, rS: FloatArray, rE: FloatArray): Float =
        when (surface) {
            BumpItem.Surface.BACK_WALL -> (-roomSize + WALL_INSET - rS[2]) / (rE[2] - rS[2])
            BumpItem.Surface.LEFT_WALL -> (-roomSize + WALL_INSET - rS[0]) / (rE[0] - rS[0])
            BumpItem.Surface.RIGHT_WALL -> (roomSize - WALL_INSET - rS[0]) / (rE[0] - rS[0])
            BumpItem.Surface.FLOOR -> (WALL_INSET - rS[1]) / (rE[1] - rS[1])
        }

    /** Maps a point on the widget quad (local ±halfX / ±halfZ) to texture UV for hit tests. */
    fun localQuadToTextureUv(
        surface: BumpItem.Surface,
        localX: Float,
        localZ: Float,
        halfX: Float,
        halfZ: Float,
    ): Pair<Float, Float> {
        val uNorm = (localX + halfX) / (2f * halfX)
        val vNorm = (localZ + halfZ) / (2f * halfZ)
        return when (surface) {
            BumpItem.Surface.FLOOR -> uNorm to vNorm
            BumpItem.Surface.BACK_WALL -> uNorm to (1f - vNorm)
            BumpItem.Surface.LEFT_WALL -> uNorm to (1f - vNorm)
            BumpItem.Surface.RIGHT_WALL -> (1f - uNorm) to (1f - vNorm)
        }
    }

    fun intersectionToTextureUv(
        widget: WidgetItem,
        iX: Float,
        iY: Float,
        iZ: Float,
    ): Pair<Float, Float> {
        val half = widget.displayHalfSize()
        return when (widget.surface) {
            BumpItem.Surface.BACK_WALL -> {
                val u = (iX - (widget.position.x - half.x)) / (2f * half.x)
                val v = 1f - (iY - (widget.position.y - half.z)) / (2f * half.z)
                u to v
            }
            BumpItem.Surface.LEFT_WALL -> {
                val u = (iZ - (widget.position.z - half.x)) / (2f * half.x)
                val v = 1f - (iY - (widget.position.y - half.z)) / (2f * half.z)
                u to v
            }
            BumpItem.Surface.RIGHT_WALL -> {
                val u = 1f - (iZ - (widget.position.z - half.x)) / (2f * half.x)
                val v = 1f - (iY - (widget.position.y - half.z)) / (2f * half.z)
                u to v
            }
            BumpItem.Surface.FLOOR -> {
                val u = (iX - (widget.position.x - half.x)) / (2f * half.x)
                val v = (iZ - (widget.position.z - half.z)) / (2f * half.z)
                u to v
            }
        }
    }

    /** Human-readable attachment metrics for logcat (face + distance from room plane). */
    fun surfaceAttachmentDescription(
        widget: WidgetItem,
        roomSize: Float,
        floorPlaneY: Float = 0f,
    ): String {
        val p = widget.position
        val half = widget.displayHalfSize()
        return when (widget.surface) {
            BumpItem.Surface.BACK_WALL -> {
                val wallPlaneZ = -roomSize
                "face=BACK_WALL pos=(${fmt(p.x)}, ${fmt(p.y)}, ${fmt(p.z)}) " +
                    "wallPlaneZ=${fmt(wallPlaneZ)} insetFromWall=${fmt(p.z - wallPlaneZ)} " +
                    "displayHalf=(${fmt(half.x)}, ${fmt(half.z)})"
            }
            BumpItem.Surface.LEFT_WALL -> {
                val wallPlaneX = -roomSize
                "face=LEFT_WALL pos=(${fmt(p.x)}, ${fmt(p.y)}, ${fmt(p.z)}) " +
                    "wallPlaneX=${fmt(wallPlaneX)} insetFromWall=${fmt(p.x - wallPlaneX)} " +
                    "displayHalf=(${fmt(half.x)}, ${fmt(half.z)})"
            }
            BumpItem.Surface.RIGHT_WALL -> {
                val wallPlaneX = roomSize
                "face=RIGHT_WALL pos=(${fmt(p.x)}, ${fmt(p.y)}, ${fmt(p.z)}) " +
                    "wallPlaneX=${fmt(wallPlaneX)} insetFromWall=${fmt(wallPlaneX - p.x)} " +
                    "displayHalf=(${fmt(half.x)}, ${fmt(half.z)})"
            }
            BumpItem.Surface.FLOOR -> {
                "face=FLOOR pos=(${fmt(p.x)}, ${fmt(p.y)}, ${fmt(p.z)}) " +
                    "floorPlaneY=${fmt(floorPlaneY)} heightAboveFloor=${fmt(p.y - floorPlaneY)} " +
                    "displayHalf=(${fmt(half.x)}, ${fmt(half.z)})"
            }
        }
    }

    private fun fmt(value: Float): String = String.format("%.3f", value)
}
