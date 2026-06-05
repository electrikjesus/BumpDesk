package com.bass.bumpdesk

import android.opengl.GLES20

/**
 * Keeps icons, folders, and widgets visually above room geometry to prevent z-fighting
 * when the camera views surfaces at shallow angles.
 */
object SurfaceRenderDepth {
    /** Matches [Box] top-face local Y — the textured quad sits this far above the anchor. */
    const val BOX_TOP_THICKNESS = 0.0415f

    /** Minimum lift from the floor plane (y = 0) for desktop anchors at y ≈ 0.05. */
    const val FLOOR_DRAW_LIFT = 0.10f

    /** Extra push along the outward wall normal at draw time (physics may already inset). */
    const val WALL_DRAW_OFFSET = 0.08f

    /** Bias content quads toward the camera in the depth buffer. */
    const val CONTENT_POLY_OFFSET_FACTOR = -2.0f
    const val CONTENT_POLY_OFFSET_UNITS = -2.0f

    /** Bias room planes away from the camera so content wins depth tests. */
    const val ROOM_POLY_OFFSET_FACTOR = 1.0f
    const val ROOM_POLY_OFFSET_UNITS = 1.0f

    fun floorDrawY(anchorY: Float): Float = anchorY + FLOOR_DRAW_LIFT

    fun offsetDrawPosition(
        surface: BumpItem.Surface,
        x: Float,
        y: Float,
        z: Float,
        skipWallOffset: Boolean = false,
    ): Triple<Float, Float, Float> = when (surface) {
        BumpItem.Surface.FLOOR -> Triple(x, floorDrawY(y), z)
        BumpItem.Surface.BACK_WALL -> {
            val dz = if (skipWallOffset) 0f else WALL_DRAW_OFFSET
            Triple(x, y, z + dz)
        }
        BumpItem.Surface.LEFT_WALL -> {
            val dx = if (skipWallOffset) 0f else WALL_DRAW_OFFSET
            Triple(x + dx, y, z)
        }
        BumpItem.Surface.RIGHT_WALL -> {
            val dx = if (skipWallOffset) 0f else WALL_DRAW_OFFSET
            Triple(x - dx, y, z)
        }
    }

    inline fun withContentPolygonOffset(block: () -> Unit) {
        GLES20.glEnable(GLES20.GL_POLYGON_OFFSET_FILL)
        GLES20.glPolygonOffset(CONTENT_POLY_OFFSET_FACTOR, CONTENT_POLY_OFFSET_UNITS)
        try {
            block()
        } finally {
            GLES20.glDisable(GLES20.GL_POLYGON_OFFSET_FILL)
        }
    }

    inline fun withRoomPolygonOffset(block: () -> Unit) {
        GLES20.glEnable(GLES20.GL_POLYGON_OFFSET_FILL)
        GLES20.glPolygonOffset(ROOM_POLY_OFFSET_FACTOR, ROOM_POLY_OFFSET_UNITS)
        try {
            block()
        } finally {
            GLES20.glDisable(GLES20.GL_POLYGON_OFFSET_FILL)
        }
    }
}
