package com.bass.bumpdesk

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import org.json.JSONObject

object ThemeManager {
    private const val FALLBACK_THEME = "BumpDesk Animated"

    /** Maps json-only theme packs to the nearest complete asset bundle. */
    private val THEME_ASSET_SOURCES = mapOf(
        "Bump Blue" to "BumpDesk Blue",
        "Bumped Next" to "BumpDesk Next",
        "BumpTop Classic" to "BumpDesk Blue",
        "BumpTop Test" to "BumpDesk Animated"
    )

    var currentThemeName: String = "BumpDesk Animated"
        internal set
        
    private var isInitialized = false
    var themeConfig: JSONObject? = null
        private set

    fun init(context: Context, forceReload: Boolean = false) {
        if (isInitialized && !forceReload) return
        BumpDeskLog.enter(BumpDeskLog.Tag.THEME, "init", "forceReload=$forceReload")
        val prefs = context.getSharedPreferences("bump_prefs", Context.MODE_PRIVATE)
        currentThemeName = prefs.getString("selected_theme", "BumpDesk Animated") ?: "BumpDesk Animated"
        loadThemeConfig(context)
        isInitialized = true
        BumpDeskLog.exit(BumpDeskLog.Tag.THEME, "init", "theme=$currentThemeName")
    }

    private fun loadThemeConfig(context: Context) {
        try {
            val jsonString = context.assets.open("BumpTop/$currentThemeName/theme.json").bufferedReader().use { it.readText() }
            val cleanJson = jsonString.replace(Regex("(?<!:)//.*"), "")
            themeConfig = JSONObject(cleanJson)
        } catch (e: Exception) {
            BumpDeskLog.fail(BumpDeskLog.Tag.THEME, "loadThemeConfig", "theme=$currentThemeName", e)
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
            BumpDeskLog.enter(BumpDeskLog.Tag.WALLPAPER, "getFloorTexture", "source=system_wallpaper")
            try {
                val wallpaperBitmap = TextureUtils.loadSystemWallpaperBitmap(context)
                if (wallpaperBitmap != null) {
                    val tex = textureManager.loadTextureFromBitmap(wallpaperBitmap, "wallpaper:floor")
                    val sizeLabel = "${wallpaperBitmap.width}x${wallpaperBitmap.height}"
                    wallpaperBitmap.recycle()
                    if (tex != -1) {
                        BumpDeskLog.exit(
                            BumpDeskLog.Tag.WALLPAPER,
                            "getFloorTexture",
                            "textureId=$tex size=$sizeLabel"
                        )
                        return tex
                    }
                    BumpDeskLog.w(BumpDeskLog.Tag.WALLPAPER, "getFloorTexture", "wallpaper bitmap loaded but texture id=-1")
                } else {
                    BumpDeskLog.w(BumpDeskLog.Tag.WALLPAPER, "getFloorTexture", "could not decode system wallpaper")
                }
            } catch (e: Exception) {
                BumpDeskLog.fail(BumpDeskLog.Tag.WALLPAPER, "getFloorTexture", "system wallpaper failed", e)
            }
            BumpDeskLog.d(BumpDeskLog.Tag.WALLPAPER, "getFloorTexture", "falling back to theme floor texture")
        }

        val themePathBase = "BumpTop/$currentThemeName/desktop/"
        val configuredDesktop = themeConfig?.optJSONObject("textures")
            ?.optJSONObject("floor")
            ?.optString("desktop")
            ?.takeIf { it.isNotBlank() }

        val candidates = buildList {
            configuredDesktop?.let { add("$themePathBase$it") }
            add("${themePathBase}floor.svg")
            add("${themePathBase}floor_desktop.jpg")
            add("${themePathBase}floor.png")
            add("${themePathBase}floor_infinite.png")
        }.distinct()

        for (path in candidates) {
            val textureId = loadTextureWithFallback(context, textureManager, path, 1024, 1024)
            if (textureId != -1) {
                BumpDeskLog.d(BumpDeskLog.Tag.THEME, "getFloorTexture", "loaded $path textureId=$textureId")
                return textureId
            }
        }

        BumpDeskLog.w(BumpDeskLog.Tag.THEME, "getFloorTexture", "no floor asset for theme=$currentThemeName")
        return -1
    }

    fun getWallTextures(context: Context, textureManager: TextureManager): IntArray {
        init(context)
        val walls = themeConfig?.optJSONObject("textures")?.optJSONObject("wall")
        val ids = IntArray(4) { -1 }
        
        val backPath = walls?.optString("bottom")?.takeIf { it.isNotBlank() } ?: "wall.svg"
        val leftPath = walls?.optString("left")?.takeIf { it.isNotBlank() } ?: backPath
        val rightPath = walls?.optString("right")?.takeIf { it.isNotBlank() } ?: backPath
        val topPath = walls?.optString("top")?.takeIf { it.isNotBlank() } ?: backPath

        val themePathBase = "BumpTop/$currentThemeName/desktop/"
        
        // Walls should use a 2:1 aspect ratio to match geometry (e.g. 1024x512)
        val wW = 1024
        val wH = 512
        
        ids[0] = loadTextureWithFallback(context, textureManager, "$themePathBase$backPath", wW, wH)
        ids[1] = loadTextureWithFallback(context, textureManager, "$themePathBase$leftPath", wW, wH)
        ids[2] = loadTextureWithFallback(context, textureManager, "$themePathBase$rightPath", wW, wH)
        ids[3] = loadTextureWithFallback(context, textureManager, "$themePathBase$topPath", 1024, 1024)

        for (i in ids.indices) {
            if (ids[i] == -1) {
                ids[i] = loadTextureWithFallback(context, textureManager, "$themePathBase$backPath", wW, wH)
            }
        }
        return ids
    }

    private fun relativeThemePath(assetPath: String): String? {
        val prefix = "BumpTop/$currentThemeName/"
        return if (assetPath.startsWith(prefix)) assetPath.removePrefix(prefix) else null
    }

    private fun themeAssetSource(themeName: String): String? = THEME_ASSET_SOURCES[themeName]

    fun loadOptionalWidgetTexture(context: Context, textureManager: TextureManager, assetName: String): Int {
        init(context)
        val scrollConfig = themeConfig?.optJSONObject("textures")
            ?.optJSONObject("widget")
            ?.optJSONObject("scroll")
        val configuredName = when (assetName) {
            "scrollUp" -> scrollConfig?.optString("up", "scrollUp.png")
            "scrollDown" -> scrollConfig?.optString("down", "scrollDown.png")
            else -> null
        }

        val candidates = buildList {
            configuredName?.let { add("BumpTop/$currentThemeName/widgets/$it") }
            add("BumpTop/$currentThemeName/widgets/$assetName.svg")
            add("BumpTop/$currentThemeName/widgets/$assetName.png")
            add("BumpTop/$FALLBACK_THEME/widgets/$assetName.svg")
            add("BumpTop/$FALLBACK_THEME/widgets/$assetName.png")
        }.distinct()

        for (path in candidates) {
            val textureId = loadTextureWithFallback(context, textureManager, path, 64, 64, silent = true)
            if (textureId != -1) {
                return textureId
            }
        }
        return -1
    }

    internal fun loadTextureWithFallback(
        context: Context,
        textureManager: TextureManager,
        assetPath: String,
        width: Int = 512,
        height: Int = 512,
        silent: Boolean = false
    ): Int {
        loadTextureAtPath(context, textureManager, assetPath, width, height, silent)?.let { return it }

        val relativePath = relativeThemePath(assetPath) ?: return -1

        themeAssetSource(currentThemeName)?.let { sourceTheme ->
            if (sourceTheme != currentThemeName) {
                val sourcePath = "BumpTop/$sourceTheme/$relativePath"
                loadTextureAtPath(context, textureManager, sourcePath, width, height, silent)?.let { return it }
            }
        }

        if (currentThemeName != FALLBACK_THEME) {
            val fallbackPath = "BumpTop/$FALLBACK_THEME/$relativePath"
            loadTextureAtPath(context, textureManager, fallbackPath, width, height, silent)?.let { return it }
        }
        return -1
    }

    private fun loadTextureAtPath(
        context: Context,
        textureManager: TextureManager,
        assetPath: String,
        width: Int,
        height: Int,
        silent: Boolean
    ): Int? {
        if (!assetExists(context, assetPath)) {
            return null
        }

        if (assetPath.endsWith(".svg")) {
            try {
                context.assets.open(assetPath).use { inputStream ->
                    val bitmap = TextureUtils.getBitmapFromSvg(inputStream, width, height)
                    if (bitmap != null) {
                        val id = textureManager.loadTextureFromBitmap(bitmap, assetPath)
                        bitmap.recycle()
                        return id
                    }
                }
            } catch (e: Exception) {
                if (!silent) {
                    BumpDeskLog.w(BumpDeskLog.Tag.THEME, "loadTextureAtPath", "svg failed: $assetPath")
                }
            }
        }

        val textureId = textureManager.loadTextureFromAsset(assetPath, silent)
        return textureId.takeIf { it != -1 }
    }

    private fun assetExists(context: Context, assetPath: String): Boolean {
        return try {
            context.assets.open(assetPath).close()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    fun getShaderCode(context: Context, type: String): String? {
        val fileName = themeConfig?.optJSONObject("shaders")?.optString(type)
            ?: loadFallbackThemeConfig(context)?.optJSONObject("shaders")?.optString(type)
            ?: return null
        return readShaderAsset(context, currentThemeName, fileName)
            ?: readShaderAsset(context, FALLBACK_THEME, fileName)
    }

    private fun loadFallbackThemeConfig(context: Context): JSONObject? {
        if (currentThemeName == FALLBACK_THEME) return null
        return try {
            val jsonString = context.assets.open("BumpTop/$FALLBACK_THEME/theme.json").bufferedReader().use { it.readText() }
            JSONObject(jsonString.replace(Regex("(?<!:)//.*"), ""))
        } catch (e: Exception) {
            null
        }
    }

    private fun readShaderAsset(context: Context, themeName: String, fileName: String): String? {
        val path = "BumpTop/$themeName/$fileName"
        if (!assetExists(context, path)) return null
        return try {
            context.assets.open(path).bufferedReader().use { it.readText() }
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
