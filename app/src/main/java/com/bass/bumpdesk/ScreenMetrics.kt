package com.bass.bumpdesk

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.min

object ScreenMetrics {

    const val PREFS_DISPLAY_DEFAULTS_APPLIED = "display_profile_defaults_v1"
    const val PREFS_LAST_ORIENTATION = "orientation_camera_profile_v6"

    data class DisplayProfile(
        val widthPx: Int,
        val heightPx: Int,
        val density: Float,
        val shortestSideDp: Float,
        val isPortrait: Boolean,
        val isPhone: Boolean,
        val uiScale: Float,
        val recommendedRoomSize: Int,
        val defaultCameraPos: FloatArray,
        val defaultCameraLookAt: FloatArray,
        val defaultZoomLevel: Float,
        val defaultFieldOfView: Float,
    ) {
        val orientationKey: String get() = if (isPortrait) "portrait" else "landscape"
    }

    fun from(context: Context): DisplayProfile {
        val dm = context.resources.displayMetrics
        return computeProfile(dm.widthPixels, dm.heightPixels, dm.density)
    }

    fun computeProfile(widthPx: Int, heightPx: Int, density: Float): DisplayProfile {
        val shortestSidePx = min(widthPx, heightPx)
        val shortestSideDp = shortestSidePx / density
        val isPortrait = heightPx > widthPx
        val isPhone = shortestSideDp < 600f
        val uiScale = when {
            shortestSideDp < 360f -> 0.85f
            shortestSideDp < 600f -> 0.95f
            else -> 1.0f
        }
        val recommendedRoomSize = when {
            isPhone && isPortrait -> 20
            isPhone -> 24
            shortestSideDp < 720f -> 26
            else -> 30
        }
        val camera = defaultCameraFor(isPhone, isPortrait, widthPx, heightPx)
        return DisplayProfile(
            widthPx = widthPx,
            heightPx = heightPx,
            density = density,
            shortestSideDp = shortestSideDp,
            isPortrait = isPortrait,
            isPhone = isPhone,
            uiScale = uiScale,
            recommendedRoomSize = recommendedRoomSize,
            defaultCameraPos = camera.pos,
            defaultCameraLookAt = camera.lookAt,
            defaultZoomLevel = camera.zoom,
            defaultFieldOfView = camera.fov,
        )
    }

    private data class CameraDefaults(
        val pos: FloatArray,
        val lookAt: FloatArray,
        val zoom: Float,
        val fov: Float,
    )

    private fun defaultCameraFor(
        isPhone: Boolean,
        isPortrait: Boolean,
        widthPx: Int,
        heightPx: Int,
    ): CameraDefaults {
        val pos = floatArrayOf(0f, 12f, 25f)
        val lookAt = floatArrayOf(0f, 0f, 5f)
        if (isPortrait) {
            if (isPhone) {
                return CameraDefaults(
                    pos = pos,
                    lookAt = lookAt,
                    zoom = 0.78f,
                    fov = 60f,
                )
            }
            // Tuned from device logs (1440×2160 tablet, twoFingerEnd 2025-05-30).
            return CameraDefaults(
                pos = floatArrayOf(0.29f, 12f, 27.20f),
                lookAt = floatArrayOf(0.29f, 0f, 7.20f),
                zoom = 1.12f,
                fov = 60f,
            )
        }
        return when {
            isPhone -> CameraDefaults(
                pos = pos,
                lookAt = lookAt,
                zoom = 1.65f,
                fov = 64f,
            )
            else -> CameraDefaults(
                pos = pos,
                lookAt = lookAt,
                zoom = 1.0f,
                fov = 60f,
            )
        }
    }

    fun applyFirstLaunchDefaults(context: Context, prefs: SharedPreferences) {
        if (prefs.getBoolean(PREFS_DISPLAY_DEFAULTS_APPLIED, false)) return
        val profile = from(context)
        prefs.edit().apply {
            if (!prefs.contains("room_size_scale")) {
                putInt("room_size_scale", profile.recommendedRoomSize)
            }
            if (!prefs.contains("layout_item_scale")) {
                putInt("layout_item_scale", if (profile.isPhone) 45 else 50)
            }
            putBoolean(PREFS_DISPLAY_DEFAULTS_APPLIED, true)
        }.apply()
    }

    fun dpToPx(context: Context, dp: Float): Float = dp * context.resources.displayMetrics.density

    fun touchThresholdPx(context: Context): Float = dpToPx(context, 12f)

    fun leafGestureThresholdPx(context: Context): Float = dpToPx(context, 48f)

    fun touchSlopPx(context: Context): Float = dpToPx(context, 16f)

    fun radialInnerRadiusPx(context: Context): Float = dpToPx(context, 48f)

    fun radialOuterRadiusPx(context: Context): Float = dpToPx(context, 120f)

    fun radialSecondaryOffsetPx(context: Context): Float = dpToPx(context, 72f)
}
