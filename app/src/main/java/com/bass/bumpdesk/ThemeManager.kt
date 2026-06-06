package com.bass.bumpdesk

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import org.json.JSONObject

object ThemeManager {
    private const val FALLBACK_THEME = "BumpDesk Animated"
    const val DEFAULT_THEME = "Material Expressive"
    const val PREF_EXPRESSIVE_M3_FOLDER_CHROME = "expressive_m3_folder_chrome"

    private val STANDARD_DARK_SURFACE = floatArrayOf(0.13f, 0.14f, 0.17f, 0.94f)
    private val STANDARD_DARK_ON_SURFACE = floatArrayOf(0.96f, 0.97f, 0.99f, 1f)
    private val STANDARD_DARK_PRIMARY = floatArrayOf(0.55f, 0.74f, 1f, 1f)
    private val STANDARD_DARK_BUTTON_CONTAINER = floatArrayOf(0.24f, 0.26f, 0.33f, 0.98f)
    private val STANDARD_DARK_INACTIVE_INDICATOR = floatArrayOf(0.45f, 0.47f, 0.52f, 0.75f)

    /** Maps json-only theme packs to the nearest complete asset bundle. */
    private val THEME_ASSET_SOURCES = mapOf(
        "Bump Blue" to "BumpDesk Blue",
        "Bumped Next" to "BumpDesk Next",
        "BumpTop Classic" to "BumpDesk Blue",
        "BumpTop Test" to "BumpDesk Animated"
    )

    var currentThemeName: String = DEFAULT_THEME
        internal set
        
    private var isInitialized = false
    var themeConfig: JSONObject? = null
        private set

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var cachedSystemPalette: SystemMaterialColors.Palette? = null

    fun usesSystemColors(): Boolean =
        themeConfig?.optString("colorSource", "") == "system"

    fun usesM3FolderChrome(): Boolean {
        if (!usesSystemColors()) return true
        val ctx = appContext ?: return true
        return ctx.getSharedPreferences("bump_prefs", Context.MODE_PRIVATE)
            .getBoolean(PREF_EXPRESSIVE_M3_FOLDER_CHROME, true)
    }

    fun colorFloatArrayToArgb(color: FloatArray, alphaScale: Float = 1f): Int {
        val a = (color[3] * alphaScale * 255f).toInt().coerceIn(0, 255)
        return android.graphics.Color.argb(
            a,
            (color[0] * 255f).toInt().coerceIn(0, 255),
            (color[1] * 255f).toInt().coerceIn(0, 255),
            (color[2] * 255f).toInt().coerceIn(0, 255),
        )
    }

    fun init(context: Context, forceReload: Boolean = false) {
        if (isInitialized && !forceReload) return
        BumpDeskLog.enter(BumpDeskLog.Tag.THEME, "init", "forceReload=$forceReload")
        val prefs = context.getSharedPreferences("bump_prefs", Context.MODE_PRIVATE)
        currentThemeName = prefs.getString("selected_theme", DEFAULT_THEME) ?: DEFAULT_THEME
        appContext = context.applicationContext
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
        cachedSystemPalette = null
    }

    fun setTheme(themeName: String, context: Context) {
        currentThemeName = themeName
        appContext = context.applicationContext
        loadThemeConfig(context)
        isInitialized = true
    }

    fun invalidateSystemColors() {
        cachedSystemPalette = null
    }

    private fun systemPalette(context: Context): SystemMaterialColors.Palette? {
        if (!usesSystemColors()) return null
        cachedSystemPalette?.let { return it }
        val palette = SystemMaterialColors.resolve(context, themeConfig)
        cachedSystemPalette = palette
        return palette
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
                val wallpaperBitmap = WallpaperFloorProvider.createBitmapForGl()
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

        if (usesSystemColors()) {
            val palette = systemPalette(context) ?: return -1
            val bitmap = TextureUtils.createExpressiveFloorBitmap(1024, 1024, palette)
            val tex = textureManager.loadTextureFromBitmap(bitmap, "system:expressive:floor")
            bitmap.recycle()
            if (tex != -1) {
                BumpDeskLog.d(BumpDeskLog.Tag.THEME, "getFloorTexture", "loaded system expressive floor textureId=$tex")
                return tex
            }
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
        val ids = IntArray(4) { -1 }

        if (usesSystemColors()) {
            val palette = systemPalette(context) ?: return ids
            val backBitmap = TextureUtils.createExpressiveWallBitmap(1024, 512, palette)
            val backId = textureManager.loadTextureFromBitmap(backBitmap, "system:expressive:wall:back")
            backBitmap.recycle()
            val topBitmap = TextureUtils.createExpressiveWallBitmap(1024, 1024, palette)
            val topId = textureManager.loadTextureFromBitmap(topBitmap, "system:expressive:wall:top")
            topBitmap.recycle()
            if (backId != -1) {
                ids[0] = backId
                ids[1] = backId
                ids[2] = backId
            }
            if (topId != -1) {
                ids[3] = topId
            }
            if (ids.any { it != -1 }) {
                BumpDeskLog.d(BumpDeskLog.Tag.THEME, "getWallTextures", "loaded system expressive walls")
                return ids
            }
        }

        val walls = themeConfig?.optJSONObject("textures")?.optJSONObject("wall")
        
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
        val selectionAlpha = themeConfig?.optJSONObject("ui")?.optJSONObject("icon")
            ?.optJSONObject("highlight")?.optJSONObject("color")?.optJSONArray("selection")
            ?.optInt(3, 130) ?: 130
        appContext?.let { ctx ->
            systemPalette(ctx)?.let {
                return SystemMaterialColors.toFloatArray(it.primary, selectionAlpha)
            }
        }
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

    private fun colorFromTheme(section: String, key: String, fallback: FloatArray): FloatArray {
        val colorArray = themeConfig?.optJSONObject(section)?.optJSONArray(key)
        if (colorArray != null && colorArray.length() == 4) {
            return floatArrayOf(
                colorArray.getDouble(0).toFloat() / 255f,
                colorArray.getDouble(1).toFloat() / 255f,
                colorArray.getDouble(2).toFloat() / 255f,
                colorArray.getDouble(3).toFloat() / 255f,
            )
        }
        return fallback
    }

    fun getMaterialSurfaceColor(): FloatArray {
        if (usesSystemColors()) {
            if (usesM3FolderChrome()) {
                appContext?.let { ctx ->
                    systemPalette(ctx)?.let {
                        return SystemMaterialColors.toFloatArray(it.surface, 245)
                    }
                }
            }
            return STANDARD_DARK_SURFACE
        }
        return colorFromTheme("material", "surface", STANDARD_DARK_SURFACE)
    }

    fun getMaterialOnSurfaceColor(): FloatArray {
        if (usesSystemColors()) {
            if (usesM3FolderChrome()) {
                appContext?.let { ctx ->
                    systemPalette(ctx)?.let {
                        return SystemMaterialColors.toFloatArray(it.onSurface)
                    }
                }
            }
            return STANDARD_DARK_ON_SURFACE
        }
        return colorFromTheme("material", "onSurface", STANDARD_DARK_ON_SURFACE)
    }

    fun getMaterialPrimaryColor(): FloatArray {
        if (usesSystemColors()) {
            if (usesM3FolderChrome()) {
                appContext?.let { ctx ->
                    systemPalette(ctx)?.let {
                        return SystemMaterialColors.toFloatArray(it.primary)
                    }
                }
            }
            return STANDARD_DARK_PRIMARY
        }
        return colorFromTheme("material", "primary", STANDARD_DARK_PRIMARY)
    }

    fun getMaterialButtonContainerColor(): FloatArray {
        if (usesSystemColors()) {
            if (usesM3FolderChrome()) {
                appContext?.let { ctx ->
                    systemPalette(ctx)?.let {
                        return SystemMaterialColors.toFloatArray(it.primaryContainer, 252)
                    }
                }
            }
            return STANDARD_DARK_BUTTON_CONTAINER
        }
        return colorFromTheme("material", "buttonContainer", STANDARD_DARK_BUTTON_CONTAINER)
    }

    fun getMaterialInactiveIndicatorColor(): FloatArray {
        if (usesSystemColors()) {
            if (usesM3FolderChrome()) {
                appContext?.let { ctx ->
                    systemPalette(ctx)?.let {
                        return SystemMaterialColors.toFloatArray(it.inactiveIndicator, 200)
                    }
                }
            }
            return STANDARD_DARK_INACTIVE_INDICATOR
        }
        return colorFromTheme("material", "inactiveIndicator", STANDARD_DARK_INACTIVE_INDICATOR)
    }
}
