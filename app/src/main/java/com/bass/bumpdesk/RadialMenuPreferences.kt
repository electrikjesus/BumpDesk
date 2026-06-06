package com.bass.bumpdesk

import android.content.Context
import android.content.SharedPreferences

/** User-selectable radial context menu size presets. */
object RadialMenuPreferences {
    const val PREF_KEY = "radial_menu_size_preset"

    const val PRESET_AUTO = "auto"
    const val PRESET_PHONE = "phone"
    const val PRESET_TABLET = "tablet"
    const val PRESET_LARGE = "large"

    /** Relative scale applied to inner/outer radii and secondary ring offset. */
    fun scaleForPreset(preset: String, profile: ScreenMetrics.DisplayProfile): Float = when (preset) {
        PRESET_PHONE -> 0.72f
        PRESET_TABLET -> 1.0f
        PRESET_LARGE -> 1.18f
        else -> scaleForPreset(autoPreset(profile), profile)
    }

    fun autoPreset(profile: ScreenMetrics.DisplayProfile): String = when (profile.posture) {
        ScreenMetrics.LayoutPosture.COVER, ScreenMetrics.LayoutPosture.INNER -> PRESET_PHONE
        ScreenMetrics.LayoutPosture.LARGE -> PRESET_LARGE
        ScreenMetrics.LayoutPosture.TABLET -> PRESET_TABLET
    }

    fun readPreset(prefs: SharedPreferences): String =
        prefs.getString(PREF_KEY, PRESET_AUTO) ?: PRESET_AUTO

    fun resolvedPreset(context: Context, prefs: SharedPreferences = context.bumpPrefs()): String {
        val stored = readPreset(prefs)
        return if (stored == PRESET_AUTO) autoPreset(ScreenMetrics.from(context)) else stored
    }

    fun sizeScale(context: Context): Float {
        val profile = ScreenMetrics.from(context)
        val preset = resolvedPreset(context)
        return scaleForPreset(preset, profile)
    }

    /** Caps growth from item count on compact presets so desktop menus stay usable. */
    fun itemCountScaleFactor(itemCount: Int, sizeScale: Float): Float {
        val count = itemCount.coerceAtLeast(1)
        val growth = if (count > 4) 1f + (count - 4) * 0.12f else 1f
        val maxGrowth = when {
            sizeScale <= 0.75f -> 1.28f
            sizeScale <= 1.0f -> 1.45f
            else -> 1.6f
        }
        return growth.coerceAtMost(maxGrowth)
    }

    private fun Context.bumpPrefs(): SharedPreferences =
        getSharedPreferences("bump_prefs", Context.MODE_PRIVATE)
}
