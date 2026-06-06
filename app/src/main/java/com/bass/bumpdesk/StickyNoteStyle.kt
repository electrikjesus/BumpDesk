package com.bass.bumpdesk

import kotlin.math.abs

/** Sticky note footprint + resize handle hit testing (shape uses transform.shapeHalfX/Z). */
object StickyNoteStyle {
    const val MIN_SHAPE_HALF = 0.5f
    const val MAX_SHAPE_HALF = 3.5f
    const val DEFAULT_SHAPE_HALF = 1f

    fun displayHalfSize(item: BumpItem): Vector3 {
        val transform = item.transform
        return Vector3(
            transform.scale * transform.shapeHalfX,
            0f,
            transform.scale * transform.shapeHalfZ,
        )
    }

    fun wallPlaneT(item: BumpItem, roomSize: Float, rS: FloatArray, rE: FloatArray): Float =
        WidgetSurfaceTransform.wallPlaneT(item.transform.surface, roomSize, rS, rE)

    fun intersectionToTextureUv(item: BumpItem, iX: Float, iY: Float, iZ: Float): Pair<Float, Float> {
        val half = displayHalfSize(item)
        val pos = item.transform.position
        return when (item.transform.surface) {
            BumpItem.Surface.BACK_WALL -> {
                val u = (iX - (pos.x - half.x)) / (2f * half.x)
                val v = 1f - (iY - (pos.y - half.z)) / (2f * half.z)
                u to v
            }
            BumpItem.Surface.LEFT_WALL -> {
                val u = (iZ - (pos.z - half.x)) / (2f * half.x)
                val v = 1f - (iY - (pos.y - half.z)) / (2f * half.z)
                u to v
            }
            BumpItem.Surface.RIGHT_WALL -> {
                val u = 1f - (iZ - (pos.z - half.x)) / (2f * half.x)
                val v = 1f - (iY - (pos.y - half.z)) / (2f * half.z)
                u to v
            }
            BumpItem.Surface.FLOOR -> {
                val u = (iX - (pos.x - half.x)) / (2f * half.x)
                val v = (iZ - (pos.z - half.z)) / (2f * half.z)
                u to v
            }
        }
    }

    fun handleHitUv(item: BumpItem): Pair<Float, Float> {
        val half = displayHalfSize(item)
        val (cx, _, cz) = WidgetHandleStyle.handleCenter(half, WidgetHandleStyle.Kind.RESIZE)
        return WidgetSurfaceTransform.localQuadToTextureUv(
            item.transform.surface,
            cx,
            cz,
            half.x,
            half.z,
        )
    }

    fun isTouchOnResizeHandle(item: BumpItem, u: Float, v: Float): Boolean {
        val (hu, hv) = handleHitUv(item)
        val half = displayHalfSize(item)
        val (ru, rv) = WidgetHandleStyle.hitRadiusUv(half)
        val du = (u - hu) / ru
        val dv = (v - hv) / rv
        return du * du + dv * dv <= 1f
    }

    fun clampShapeHalf(value: Float): Float = value.coerceIn(MIN_SHAPE_HALF, MAX_SHAPE_HALF)

    fun resizeHandleWorldCenter(item: BumpItem): Triple<Float, Float, Float> {
        val transform = item.transform
        val (posX, posY, posZ) = SurfaceRenderDepth.offsetDrawPosition(
            transform.surface,
            transform.position.x,
            transform.position.y,
            transform.position.z,
        )
        val half = displayHalfSize(item)
        val (cx, lift, cz) = WidgetHandleStyle.handleCenter(half, WidgetHandleStyle.Kind.RESIZE)
        return when (transform.surface) {
            BumpItem.Surface.FLOOR -> Triple(posX + cx, posY + lift, posZ + cz)
            BumpItem.Surface.BACK_WALL -> Triple(posX + cx, posY + cz, posZ + lift)
            BumpItem.Surface.LEFT_WALL -> Triple(posX + lift, posY + cz, posZ + cx)
            BumpItem.Surface.RIGHT_WALL -> Triple(posX - lift, posY + cz, posZ - cx)
        }
    }

    fun hitTestResizeHandleWorld(item: BumpItem, iX: Float, iY: Float, iZ: Float): Boolean {
        val (hx, hy, hz) = resizeHandleWorldCenter(item)
        val radius = WidgetHandleStyle.HANDLE_SIZE * 0.55f
        val radiusSq = radius * radius
        return when (item.transform.surface) {
            BumpItem.Surface.FLOOR -> {
                val dx = iX - hx
                val dz = iZ - hz
                dx * dx + dz * dz <= radiusSq
            }
            BumpItem.Surface.BACK_WALL -> {
                val dx = iX - hx
                val dy = iY - hy
                dx * dx + dy * dy <= radiusSq
            }
            BumpItem.Surface.LEFT_WALL,
            BumpItem.Surface.RIGHT_WALL,
            -> {
                val dz = iZ - hz
                val dy = iY - hy
                dz * dz + dy * dy <= radiusSq
            }
        }
    }

    fun intersectsRay(item: BumpItem, roomSize: Float, rS: FloatArray, rE: FloatArray): Float {
        val t = wallPlaneT(item, roomSize, rS, rE)
        if (t <= 0f) return -1f
        val half = displayHalfSize(item)
        val pos = item.transform.position
        val iX = rS[0] + t * (rE[0] - rS[0])
        val iY = rS[1] + t * (rE[1] - rS[1])
        val iZ = rS[2] + t * (rE[2] - rS[2])
        val hit = when (item.transform.surface) {
            BumpItem.Surface.BACK_WALL ->
                abs(iX - pos.x) < half.x && abs(iY - pos.y) < half.z
            BumpItem.Surface.LEFT_WALL ->
                abs(iZ - pos.z) < half.x && abs(iY - pos.y) < half.z
            BumpItem.Surface.RIGHT_WALL ->
                abs(iZ - pos.z) < half.x && abs(iY - pos.y) < half.z
            BumpItem.Surface.FLOOR ->
                abs(iX - pos.x) < half.x && abs(iZ - pos.z) < half.z
        }
        return if (hit) t else -1f
    }
}
