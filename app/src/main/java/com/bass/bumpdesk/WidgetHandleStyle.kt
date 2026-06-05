package com.bass.bumpdesk

import kotlin.math.abs

/** Material-style widget chrome: move + resize handles at opposite corners. */
object WidgetHandleStyle {
    /** Fixed world-space handle diameter (independent of widget resize). */
    const val HANDLE_SIZE = 0.68f
    const val HANDLE_INSET = 0.08f
    /** Sits above the widget box top face (box thickness ≈ 0.042). */
    const val SURFACE_LIFT = 0.068f

    enum class Kind { MOVE, RESIZE }

    fun handleSizeForWidget(@Suppress("UNUSED_PARAMETER") size: Vector3): Float = HANDLE_SIZE

    fun insetForWidget(@Suppress("UNUSED_PARAMETER") size: Vector3): Float = HANDLE_INSET

    /** Offset from widget center in local XZ (widget quad spans ±size.x / ±size.z). */
    fun handleCenter(size: Vector3, kind: Kind): Triple<Float, Float, Float> {
        val cx = cornerOffset(size.x, towardMin = kind == Kind.MOVE)
        val cz = cornerOffset(size.z, towardMin = kind == Kind.MOVE)
        return Triple(cx, SURFACE_LIFT, cz)
    }

    private fun cornerOffset(span: Float, towardMin: Boolean): Float {
        val half = HANDLE_SIZE * 0.5f
        if (span <= half) {
            return if (towardMin) -span + half else span - half
        }
        val target = if (towardMin) {
            -span + HANDLE_INSET + half
        } else {
            span - HANDLE_INSET - half
        }
        return target.coerceIn(-span + half, span - half)
    }

    /** UV on the widget quad where the handle is drawn; must match [InteractionManager.getWidgetUV]. */
    fun handleHitUv(widget: WidgetItem, kind: Kind): Pair<Float, Float> {
        val size = widget.displayHalfSize()
        val (cx, _, cz) = handleCenter(size, kind)
        return WidgetSurfaceTransform.localQuadToTextureUv(widget.surface, cx, cz, size.x, size.z)
    }

    fun hitRadiusUv(size: Vector3): Pair<Float, Float> {
        val half = HANDLE_SIZE * 0.5f + HANDLE_INSET * 0.5f
        val radiusU = (half / (2f * size.x)).coerceIn(0.08f, 0.22f)
        val radiusV = (half / (2f * size.z)).coerceIn(0.08f, 0.25f)
        return radiusU to radiusV
    }

    fun isTouchOnHandle(widget: WidgetItem, u: Float, v: Float, kind: Kind): Boolean {
        val (hu, hv) = handleHitUv(widget, kind)
        val (ru, rv) = hitRadiusUv(widget.displayHalfSize())
        val du = (u - hu) / ru
        val dv = (v - hv) / rv
        return du * du + dv * dv <= 1f
    }

    const val moveBackground: Int = 0xEB2D303A.toInt()
    const val resizeBackground: Int = 0xF53E4452.toInt()
    const val moveForeground: Int = 0xFFE8EAF0.toInt()
    const val resizeForeground: Int = 0xFFA9C7FF.toInt()
    const val strokeColor: Int = 0x5AB4BEC2.toInt()
}
