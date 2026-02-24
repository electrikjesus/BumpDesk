package com.bass.bumpdesk

import android.annotation.SuppressLint
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
    var currentThemeName: String = "BumpDesk Animated"
        internal set
        
    private var isInitialized = false
    var themeConfig: JSONObject? = null
        private set

    fun init(context: Context, forceReload: Boolean = false) {
        if (isInitialized && !forceReload) return
        val prefs = context.getSharedPreferences("bump_prefs", Context.MODE_PRIVATE)
        currentThemeName = prefs.getString("selected_theme", "BumpDesk Animated") ?: "BumpDesk Animated"
        loadThemeConfig(context)
        isInitialized = true
    }

    private fun loadThemeConfig(context: Context) {
        try {
            val jsonString = context.assets.open("BumpTop/$currentThemeName/theme.json").bufferedReader().use { it.readText() }
            val cleanJson = jsonString.replace(Regex("(?<!:)//.*"), "")
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

    @SuppressLint("MissingPermission")
    fun getFloorTexture(context: Context, textureManager: TextureManager): Int {
        init(context)
        val prefs = context.getSharedPreferences("bump_prefs", Context.MODE_PRIVATE)
        
        if (prefs.getBoolean("use_wallpaper_as_floor", false)) {
            try {
                val wm = WallpaperManager.getInstance(context)
                val drawable = wm.drawable
                if (drawable is BitmapDrawable) {
                    val tex = textureManager.loadTextureFromBitmap(drawable.bitmap, "desktop:wallpaper_floor")
                    if (tex != -1) return tex
                }
            } catch (e: Exception) {
                Log.e("ThemeManager", "Error getting wallpaper", e)
            }
        }

        val themePathBase = "BumpTop/$currentThemeName/desktop/"
        
        var textureId = loadTextureWithFallback(context, textureManager, "${themePathBase}floor.svg", 1024, 1024)
        
        if (textureId == -1) {
            val relativePath = themeConfig?.optJSONObject("textures")?.optJSONObject("floor")?.optString("desktop", "floor_desktop.jpg") ?: "floor_desktop.jpg"
            textureId = loadTextureWithFallback(context, textureManager, "$themePathBase$relativePath", 1024, 1024)
        }

        if (textureId == -1) textureId = loadTextureWithFallback(context, textureManager, "floor.png", 1024, 1024)
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

        val themePathBase = "BumpTop/$currentThemeName/desktop/"
        
        // Walls should use a 2:1 aspect ratio to match geometry (e.g. 1024x512)
        val wW = 1024
        val wH = 512
        
        ids[0] = loadTextureWithFallback(context, textureManager, "$themePathBase$backPath", wW, wH)
        ids[1] = loadTextureWithFallback(context, textureManager, "$themePathBase$leftPath", wW, wH)
        ids[2] = loadTextureWithFallback(context, textureManager, "$themePathBase$rightPath", wW, wH)
        ids[3] = loadTextureWithFallback(context, textureManager, "$themePathBase$topPath", 1024, 1024)

        for (i in ids.indices) {
            if (ids[i] == -1) ids[i] = textureManager.loadTextureFromAsset("wall.png")
        }
        return ids
    }

    private fun loadTextureWithFallback(context: Context, textureManager: TextureManager, assetPath: String, width: Int = 512, height: Int = 512): Int {
        if (assetPath.endsWith(".svg")) {
            try {
                val inputStream = context.assets.open(assetPath)
                val bitmap = TextureUtils.getBitmapFromSvg(inputStream, width, height)
                if (bitmap != null) {
                    val id = textureManager.loadTextureFromBitmap(bitmap, assetPath)
                    bitmap.recycle()
                    return id
                }
            } catch (e: Exception) {}
        }
        
        return textureManager.loadTextureFromAsset(assetPath)
    }
    
    fun getShaderCode(context: Context, type: String): String? {
        val fileName = themeConfig?.optJSONObject("shaders")?.optString(type) ?: return null
        return try {
            context.assets.open("BumpTop/$currentThemeName/$fileName").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            null
        }
    }

    fun getPileBackgroundTexture(context: Context, textureManager: TextureManager): Int {
        init(context)
        val themePath = "BumpTop/$currentThemeName/core/pile/background.png"
        return loadTextureWithFallback(context, textureManager, themePath)
    }

    fun getIconOverride(context: Context, packageName: String): Bitmap? {
        init(context)
        val genericName = when {
            packageName.contains("android.calendar") -> "calendar"
            packageName.contains("android.email") -> "email"
            packageName.contains("android.browser") || packageName.contains("chrome") -> "browser"
            packageName.contains("camera") -> "camera"
            packageName.contains("gallery") || packageName.contains("photos") -> "gallery"
            else -> null
        }
        
        if (genericName != null) {
            val bitmap = loadBitmapFromAssetWithSvg(context, "BumpTop/$currentThemeName/override/$genericName")
            if (bitmap != null) return bitmap
        }
        
        return loadBitmapFromAssetWithSvg(context, "BumpTop/$currentThemeName/override/$packageName")
    }

    private fun loadBitmapFromAssetWithSvg(context: Context, basePath: String): Bitmap? {
        try {
            return context.assets.open("$basePath.svg").use { TextureUtils.getBitmapFromSvg(it) }
        } catch (e: Exception) {}
        
        try {
            return context.assets.open("$basePath.png").use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {}
        
        return null
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
