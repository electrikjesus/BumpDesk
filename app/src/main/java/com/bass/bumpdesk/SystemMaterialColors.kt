package com.bass.bumpdesk

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Color
import org.json.JSONObject

/**
 * Resolves Material 3–style tokens from the system wallpaper (Material You) with
 * theme.json fallbacks when wallpaper colors are unavailable.
 */
object SystemMaterialColors {

    data class Palette(
        val primary: Int,
        val onSurface: Int,
        val surfaceBright: Int,
        val surface: Int,
        val surfaceContainer: Int,
        val surfaceContainerLow: Int,
        val primaryContainer: Int,
        val secondaryContainer: Int,
        val tertiaryContainer: Int,
        val inactiveIndicator: Int,
    )

    fun resolve(context: Context, themeConfig: JSONObject?): Palette {
        val fallback = paletteFromTheme(themeConfig)
        val wallpaperColors = loadWallpaperColors(context) ?: return fallback

        val primary = wallpaperColors.primaryColor?.toArgb() ?: fallback.primary
        val secondary = wallpaperColors.secondaryColor?.toArgb() ?: fallback.secondaryContainer
        val tertiary = wallpaperColors.tertiaryColor?.toArgb() ?: fallback.tertiaryContainer
        val lightSurfaces = wallpaperColors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0
        val base = if (lightSurfaces) Color.WHITE else Color.BLACK
        val onSurface = if (lightSurfaces) Color.parseColor("#1D1B20") else Color.parseColor("#E6E1E5")

        return Palette(
            primary = primary,
            onSurface = onSurface,
            surfaceBright = blend(primary, base, if (lightSurfaces) 0.96f else 0.08f),
            surface = blend(primary, base, if (lightSurfaces) 0.92f else 0.12f),
            surfaceContainer = blend(primary, base, if (lightSurfaces) 0.88f else 0.16f),
            surfaceContainerLow = blend(primary, base, if (lightSurfaces) 0.84f else 0.20f),
            primaryContainer = blend(primary, base, if (lightSurfaces) 0.72f else 0.28f),
            secondaryContainer = blend(secondary, base, if (lightSurfaces) 0.78f else 0.22f),
            tertiaryContainer = blend(tertiary, base, if (lightSurfaces) 0.80f else 0.24f),
            inactiveIndicator = blend(onSurface, base, if (lightSurfaces) 0.55f else 0.45f),
        )
    }

    fun toFloatArray(color: Int, alphaOverride: Int? = null): FloatArray =
        floatArrayOf(
            Color.red(color) / 255f,
            Color.green(color) / 255f,
            Color.blue(color) / 255f,
            (alphaOverride ?: Color.alpha(color)) / 255f,
        )

    private fun loadWallpaperColors(context: Context): WallpaperColors? {
        return try {
            val wm = WallpaperManager.getInstance(context.applicationContext)
            wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
        } catch (e: Exception) {
            BumpDeskLog.w(BumpDeskLog.Tag.THEME, "loadWallpaperColors", e.message ?: "failed")
            null
        }
    }

    private fun paletteFromTheme(themeConfig: JSONObject?): Palette {
        fun rgba(key: String, fallback: Int): Int {
            val arr = themeConfig?.optJSONObject("material")?.optJSONArray(key)
            if (arr != null && arr.length() == 4) {
                return Color.argb(
                    arr.getInt(3),
                    arr.getInt(0),
                    arr.getInt(1),
                    arr.getInt(2),
                )
            }
            return fallback
        }

        val primary = rgba("primary", Color.parseColor("#6750A4"))
        val surface = rgba("surface", Color.parseColor("#ECE6F0"))
        val buttonContainer = rgba("buttonContainer", Color.parseColor("#E8DEF8"))
        val onSurface = rgba("onSurface", Color.parseColor("#1D1B20"))
        val inactive = rgba("inactiveIndicator", Color.parseColor("#CAC4D0"))

        return Palette(
            primary = primary,
            onSurface = onSurface,
            surfaceBright = lighten(surface, 0.04f),
            surface = surface,
            surfaceContainer = darken(surface, 0.03f),
            surfaceContainerLow = darken(surface, 0.06f),
            primaryContainer = buttonContainer,
            secondaryContainer = blend(buttonContainer, primary, 0.35f),
            tertiaryContainer = blend(buttonContainer, primary, 0.20f),
            inactiveIndicator = inactive,
        )
    }

    private fun blend(from: Int, to: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        return Color.argb(
            255,
            lerp(Color.red(from), Color.red(to), t),
            lerp(Color.green(from), Color.green(to), t),
            lerp(Color.blue(from), Color.blue(to), t),
        )
    }

    private fun lighten(color: Int, amount: Float): Int = blend(color, Color.WHITE, amount)

    private fun darken(color: Int, amount: Float): Int = blend(color, Color.BLACK, amount)

    private fun lerp(a: Int, b: Int, t: Float): Int = (a + (b - a) * t).toInt().coerceIn(0, 255)
}
