package com.bass.bumpdesk

import kotlin.math.sqrt
import kotlin.math.tan
import android.content.Context

/**
 * Top-down desktop mode: default camera matches double-click floor view (slightly zoomed out),
 * with physics walls aligned to the visible screen bounds.
 */
object FlatFloorMode {
    const val PREF_KEY = "flat_floor_mode"

    /** Slightly higher than [CameraManager.focusOnFloor] (y=20) for a bit more context. */
    const val DEFAULT_EYE_Y = 24f
    const val DEFAULT_EYE_Z = 0.1f
    /** Floor/wall bounds match the visible frustum at zoom 1.0. */
    const val DEFAULT_ZOOM = 1.0f
    /** Camera sits slightly closer so wall edges stay off-screen. */
    const val DEFAULT_CAMERA_ZOOM = 0.93f
    const val DEFAULT_FOV = 60f

    /** Keep icons/walls just inside the visible floor edge. */
    const val EDGE_INSET = 0.8f

    data class Bounds(val halfX: Float, val halfZ: Float)

    fun defaultEyePosition(): FloatArray = floatArrayOf(0f, DEFAULT_EYE_Y, DEFAULT_EYE_Z)

    fun defaultLookAt(): FloatArray = floatArrayOf(0f, 0f, 0f)

    fun computeFloorBounds(
        eyeY: Float,
        eyeZ: Float,
        fovDeg: Float,
        aspect: Float,
        zoom: Float,
        inset: Float = EDGE_INSET,
    ): Bounds {
        val dist = sqrt(eyeY * eyeY + eyeZ * eyeZ) * zoom.coerceAtLeast(0.1f)
        val halfVert = dist * tan(Math.toRadians(fovDeg / 2.0)).toFloat()
        val halfHoriz = halfVert * aspect.coerceAtLeast(0.1f)
        return Bounds(
            halfX = (halfHoriz - inset).coerceAtLeast(4f),
            halfZ = (halfVert - inset).coerceAtLeast(4f),
        )
    }

    /** Floor plane width:depth for wallpaper center-crop when flat floor mode is enabled. */
    fun floorCropAspectFor(context: Context): Pair<Float, Float> {
        val prefs = context.getSharedPreferences("bump_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean(PREF_KEY, false)) {
            return 1f to 1f
        }
        val profile = ScreenMetrics.from(context)
        val aspect = profile.widthPx.toFloat() / profile.heightPx.coerceAtLeast(1)
        val bounds = computeFloorBounds(
            DEFAULT_EYE_Y,
            DEFAULT_EYE_Z,
            DEFAULT_FOV,
            aspect,
            DEFAULT_ZOOM,
        )
        return bounds.halfX to bounds.halfZ
    }
}
