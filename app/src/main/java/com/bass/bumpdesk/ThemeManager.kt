package com.bass.bumpdesk

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import java.io.InputStream
import org.json.JSONObject

object ThemeManager {
    var currentThemeName: String = "BumpTop Classic"
        internal set
        
    private var isInitialized = false
    var themeConfig: JSONObject? = null
        private set

    fun init(context: Context, forceReload: Boolean = false) {
        if (isInitialized && !forceReload) return
        val prefs = context.getSharedPreferences("bump_prefs", Context.MODE_PRIVATE)
        currentThemeName = prefs.getString("selected_theme", "BumpTop Classic") ?: "BumpTop Classic"
        loadThemeConfig(context)
        isInitialized = true
    }

    private fun loadThemeConfig(context: Context) {
        try {
            val jsonString = context.assets.open("BumpTop/$currentThemeName/theme.json").bufferedReader().use { it.readText() }
            // Remove comments from theme.json if present
            val cleanJson = jsonString.replace(Regex("//.*"), "")
            themeConfig = JSONObject(cleanJson)
        } catch (e: Exception) {
            Log.e("ThemeManager", "Error loading theme.json for $currentThemeName", e)
            themeConfig = null
        }
    }

    fun setTheme(themeName: String, context: Context) {
        currentThemeName = themeName
        loadThemeConfig(context)
        isInitialized = true
    }

    fun getThemeList(context: Context): List<String> {
        return try {
            context.assets.list("BumpTop")?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getFloorTexture(context: Context, textureManager: TextureManager): Int {
        init(context)
        val prefs = context.getSharedPreferences("bump_prefs", Context.MODE_PRIVATE)
        
        // Handle wallpaper option
        if (prefs.getBoolean("use_wallpaper_as_floor", false)) {
            try {
                val wm = WallpaperManager.getInstance(context)
                val drawable = wm.drawable
                if (drawable is BitmapDrawable) {
                    val tex = textureManager.loadTextureFromBitmap(drawable.bitmap)
                    if (tex != -1) return tex
                }
            } catch (e: SecurityException) {
                Log.e("ThemeManager", "Permission denied to read wallpaper", e)
            } catch (e: Exception) {
                Log.e("ThemeManager", "Error getting wallpaper", e)
            }
        }

        // Try floor_desktop.jpg first as per task
        val themePathBase = "BumpTop/$currentThemeName/desktop/"
        var textureId = textureManager.loadTextureFromAsset("${themePathBase}floor_desktop.jpg")
        
        if (textureId == -1) {
            // Fallback to config path
            val relativePath = themeConfig?.optJSONObject("textures")?.optJSONObject("floor")?.optString("desktop", "floor_desktop.jpg") ?: "floor_desktop.jpg"
            textureId = textureManager.loadTextureFromAsset("$themePathBase$relativePath")
        }

        if (textureId == -1) textureId = textureManager.loadTextureFromAsset("floor.png")
        return textureId
    }

    fun getWallTextures(context: Context, textureManager: TextureManager): IntArray {
        init(context)
        val walls = themeConfig?.optJSONObject("textures")?.optJSONObject("wall")
        val ids = IntArray(4) { -1 }
        
        val backPath = walls?.optString("bottom", "wall.png") ?: "wall.png"
        val leftPath = walls?.optString("left", "wall.png") ?: "wall.png"
        val rightPath = walls?.optString("right", "wall.png") ?: "wall.png"
        val topPath = walls?.optString("top", "wall.png") ?: "wall.png"

        ids[0] = textureManager.loadTextureFromAsset("BumpTop/$currentThemeName/desktop/$backPath")
        ids[1] = textureManager.loadTextureFromAsset("BumpTop/$currentThemeName/desktop/$leftPath")
        ids[2] = textureManager.loadTextureFromAsset("BumpTop/$currentThemeName/desktop/$rightPath")
        ids[3] = textureManager.loadTextureFromAsset("BumpTop/$currentThemeName/desktop/$topPath")

        for (i in ids.indices) {
            if (ids[i] == -1) ids[i] = textureManager.loadTextureFromAsset("wall.png")
        }
        return ids
    }
    
    fun getPileBackgroundTexture(context: Context, textureManager: TextureManager): Int {
        init(context)
        val themePath = "BumpTop/$currentThemeName/core/pile/background.png"
        return textureManager.loadTextureFromAsset(themePath)
    }

    fun loadBitmapFromAsset(context: Context, assetPath: String): Bitmap? {
        return try {
            context.assets.open(assetPath).use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            null
        }
    }

    fun getSelectionColor(): FloatArray {
        val colorArray = themeConfig?.optJSONObject("ui")?.optJSONObject("icon")?.optJSONObject("highlight")?.optJSONObject("color")?.optJSONArray("selection")
        if (colorArray != null && colorArray.length() == 4) {
            return floatArrayOf(
                colorArray.getDouble(0).toFloat() / 255f,
                colorArray.getDouble(1).toFloat() / 255f,
                colorArray.getDouble(2).toFloat() / 255f,
                colorArray.getDouble(3).toFloat() / 255f
            )
        }
        return floatArrayOf(1f, 1f, 1f, 0.5f)
    }

    fun getFreshnessColor(): FloatArray {
        val colorArray = themeConfig?.optJSONObject("ui")?.optJSONObject("icon")?.optJSONObject("highlight")?.optJSONObject("color")?.optJSONArray("freshness")
        if (colorArray != null && colorArray.length() == 4) {
            return floatArrayOf(
                colorArray.getDouble(0).toFloat() / 255f,
                colorArray.getDouble(1).toFloat() / 255f,
                colorArray.getDouble(2).toFloat() / 255f,
                colorArray.getDouble(3).toFloat() / 255f
            )
        }
        return floatArrayOf(0.6f, 1f, 0.3f, 0.8f)
    }

    fun getStickyNoteTypeface(context: Context): Typeface? {
        init(context)
        val families = themeConfig?.optJSONObject("ui")?.optJSONObject("stickyNote")?.optJSONObject("font")?.optJSONArray("family")
        families?.let {
            for (i in 0 until it.length()) {
                val name = it.getString(i).lowercase()
                if (name.contains("comic")) return Typeface.create("comic sans ms", Typeface.NORMAL)
            }
        }
        return null
    }
}
