package com.bass.bumpdesk

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import kotlin.math.min
import kotlin.math.roundToInt

object ScreenMetrics {

    const val PREFS_DISPLAY_DEFAULTS_APPLIED = "display_profile_defaults_v1"
    /** @deprecated Legacy orientation-only key; use [PREFS_LAST_LAYOUT_PROFILE]. */
    const val PREFS_LAST_ORIENTATION = "orientation_camera_profile_v6"
    const val PREFS_LAST_LAYOUT_PROFILE = "layout_profile_v1"

    /** Display size class for foldables and responsive chrome (Phase 1). */
    enum class LayoutPosture {
        COVER,
        INNER,
        TABLET,
        LARGE,
    }

    data class DisplayProfile(
        val widthPx: Int,
        val heightPx: Int,
        val density: Float,
        val shortestSideDp: Float,
        val isPortrait: Boolean,
        val isPhone: Boolean,
        val posture: LayoutPosture,
        val uiScale: Float,
        val recommendedRoomSize: Int,
        val defaultCameraPos: FloatArray,
        val defaultCameraLookAt: FloatArray,
        val defaultZoomLevel: Float,
        val defaultFieldOfView: Float,
    ) {
        val orientationKey: String get() = if (isPortrait) "portrait" else "landscape"

        val postureKey: String
            get() = when (posture) {
                LayoutPosture.COVER -> "cover"
                LayoutPosture.INNER -> "inner"
                LayoutPosture.TABLET -> "tablet"
                LayoutPosture.LARGE -> "large"
            }

        /** Camera anchor + config-change tracking: e.g. `inner_landscape`, `cover_portrait`. */
        val layoutProfileKey: String get() = "${postureKey}_$orientationKey"

        fun isCompactPosture(): Boolean =
            posture == LayoutPosture.COVER || posture == LayoutPosture.INNER
    }

    fun computePosture(shortestSideDp: Float): LayoutPosture = when {
        shortestSideDp < 420f -> LayoutPosture.COVER
        shortestSideDp < 720f -> LayoutPosture.INNER
        shortestSideDp < 900f -> LayoutPosture.TABLET
        else -> LayoutPosture.LARGE
    }

    fun from(context: Context): DisplayProfile {
        val dm = context.resources.displayMetrics
        return computeProfile(dm.widthPixels, dm.heightPixels, dm.density)
    }

    /** Use the [Configuration] from [android.app.Activity.onConfigurationChanged] (stable during fold). */
    fun fromConfiguration(config: Configuration, density: Float): DisplayProfile {
        val widthPx = (config.screenWidthDp * density).roundToInt().coerceAtLeast(1)
        val heightPx = (config.screenHeightDp * density).roundToInt().coerceAtLeast(1)
        return computeProfile(widthPx, heightPx, density)
    }

    fun computeProfile(widthPx: Int, heightPx: Int, density: Float): DisplayProfile {
        val shortestSidePx = min(widthPx, heightPx)
        val shortestSideDp = shortestSidePx / density
        val isPortrait = heightPx > widthPx
        val posture = computePosture(shortestSideDp)
        val isPhone = posture == LayoutPosture.COVER ||
            (posture == LayoutPosture.INNER && shortestSideDp < 600f)
        val uiScale = when (posture) {
            LayoutPosture.COVER -> 0.85f
            LayoutPosture.INNER -> 0.95f
            else -> 1.0f
        }
        val recommendedRoomSize = when (posture) {
            LayoutPosture.COVER -> if (isPortrait) 20 else 22
            LayoutPosture.INNER -> if (isPortrait) 22 else 26
            LayoutPosture.TABLET -> 28
            LayoutPosture.LARGE -> 30
        }
        val camera = defaultCameraFor(posture, isPortrait)
        return DisplayProfile(
            widthPx = widthPx,
            heightPx = heightPx,
            density = density,
            shortestSideDp = shortestSideDp,
            isPortrait = isPortrait,
            isPhone = isPhone,
            posture = posture,
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
        posture: LayoutPosture,
        isPortrait: Boolean,
    ): CameraDefaults {
        val pos = floatArrayOf(0f, 12f, 25f)
        val lookAt = floatArrayOf(0f, 0f, 5f)
        if (isPortrait) {
            if (posture == LayoutPosture.COVER || posture == LayoutPosture.INNER) {
                return CameraDefaults(
                    pos = pos,
                    lookAt = lookAt,
                    zoom = 0.78f,
                    fov = 60f,
                )
            }
            return CameraDefaults(
                pos = floatArrayOf(0.29f, 12f, 27.20f),
                lookAt = floatArrayOf(0.29f, 0f, 7.20f),
                zoom = 1.12f,
                fov = 60f,
            )
        }
        return when (posture) {
            LayoutPosture.COVER, LayoutPosture.INNER -> CameraDefaults(
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
            if (!prefs.contains("selected_theme")) {
                putString("selected_theme", ThemeManager.DEFAULT_THEME)
            }
            if (!prefs.contains("use_wallpaper_as_floor")) {
                putBoolean("use_wallpaper_as_floor", true)
            }
            if (!prefs.contains(ThemeManager.PREF_EXPRESSIVE_M3_FOLDER_CHROME)) {
                putBoolean(ThemeManager.PREF_EXPRESSIVE_M3_FOLDER_CHROME, true)
            }
            if (!prefs.contains(RadialMenuPreferences.PREF_KEY)) {
                putString(RadialMenuPreferences.PREF_KEY, RadialMenuPreferences.PRESET_AUTO)
            }
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

    fun radialInnerRadiusPx(context: Context): Float = dpToPx(context, 56f)

    fun radialOuterRadiusPx(context: Context): Float = dpToPx(context, 152f)

    fun radialSecondaryOffsetPx(context: Context): Float = dpToPx(context, 92f)
}
