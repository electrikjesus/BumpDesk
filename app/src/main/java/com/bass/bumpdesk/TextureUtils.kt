package com.bass.bumpdesk

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.app.WallpaperManager
import android.opengl.GLES20
import android.opengl.GLUtils
import kotlin.math.abs
import kotlin.math.max
import android.graphics.BitmapFactory
import com.caverock.androidsvg.PreserveAspectRatio
import com.caverock.androidsvg.SVG
import java.io.InputStream

object TextureUtils {
    private var arrowOverlayCache: Bitmap? = null

    /**
     * Converts a Drawable to a Bitmap, handling VectorDrawables and ensuring a minimum size.
     */
    fun getBitmapFromDrawable(drawable: Drawable, targetSize: Int = 128): Bitmap {
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else targetSize
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else targetSize
        
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)
        return bitmap
    }

    fun prepareBitmapForGl(source: Bitmap): Bitmap {
        var bitmap = source
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else if (bitmap.config != Bitmap.Config.ARGB_8888) {
            bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }

        val maxDim = 2048
        if (bitmap.width > maxDim || bitmap.height > maxDim) {
            val scale = maxDim.toFloat() / max(bitmap.width, bitmap.height)
            val scaled = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true
            )
            if (scaled !== bitmap) {
                if (bitmap !== source) {
                    bitmap.recycle()
                }
            }
            bitmap = scaled
        }
        return bitmap
    }

    /**
     * Center-crops like CSS `object-fit: cover` so the floor keeps aspect ratio
     * instead of stretching the wallpaper to the square plane.
     */
    fun centerCropToAspect(source: Bitmap, aspectWidth: Float, aspectHeight: Float): Bitmap {
        if (aspectWidth <= 0f || aspectHeight <= 0f) return source
        val targetAspect = aspectWidth / aspectHeight
        val srcAspect = source.width.toFloat() / source.height.toFloat()
        if (abs(srcAspect - targetAspect) < 0.001f) return source

        val cropW: Int
        val cropH: Int
        if (srcAspect > targetAspect) {
            cropH = source.height
            cropW = (cropH * targetAspect).toInt().coerceIn(1, source.width)
        } else {
            cropW = source.width
            cropH = (cropW / targetAspect).toInt().coerceIn(1, source.height)
        }
        val x = ((source.width - cropW) / 2).coerceAtLeast(0)
        val y = ((source.height - cropH) / 2).coerceAtLeast(0)
        return Bitmap.createBitmap(source, x, y, cropW, cropH)
    }

    /** GL-safe sizing plus center crop for the floor plane aspect (width:depth). */
    fun prepareWallpaperForFloor(
        source: Bitmap,
        aspectWidth: Float = 1f,
        aspectHeight: Float = 1f,
    ): Bitmap {
        var bitmap = prepareBitmapForGl(source)
        val cropped = centerCropToAspect(bitmap, aspectWidth, aspectHeight)
        if (cropped !== bitmap && bitmap !== source) {
            bitmap.recycle()
        }
        return cropped
    }

    fun loadSystemWallpaperBitmap(context: Context): Bitmap? {
        loadCachedPickedWallpaper(context)?.let { return it }

        val wm = WallpaperManager.getInstance(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            WallpaperPermissions.canReadWallpaperFile(context)
        ) {
            loadWallpaperFromFile(wm)?.let { return validateWallpaperBitmap(it, "getWallpaperFile") }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            loadWallpaperFromDrawables(context, wm)?.let { return it }
        }

        loadWallpaperFromManager(context, "drawable") { wm.drawable }
            ?.let { return validateWallpaperBitmap(it, "drawable") }

        BumpDeskLog.w(
            BumpDeskLog.Tag.WALLPAPER,
            "loadSystemWallpaperBitmap",
            "WallpaperManager unavailable; ${WallpaperPermissions.diagnose(context).toLogString()}"
        )
        return null
    }

    private fun loadWallpaperFromDrawables(context: Context, wm: WallpaperManager): Bitmap? {
        val attempts = buildList {
            add("drawable" to { wm.drawable })
            add("getDrawable()" to { wm.getDrawable() })
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                add("getDrawable(FLAG_SYSTEM)" to { wm.getDrawable(WallpaperManager.FLAG_SYSTEM) })
                add("peekDrawable(FLAG_SYSTEM)" to { wm.peekDrawable(WallpaperManager.FLAG_SYSTEM) })
            }
        }
        for ((source, supplier) in attempts) {
            loadWallpaperFromManager(context, source, supplier)
                ?.let { return validateWallpaperBitmap(it, source) }
        }
        return null
    }

    private fun loadCachedPickedWallpaper(context: Context): Bitmap? {
        val file = java.io.File(context.filesDir, WallpaperFloorProvider.WALLPAPER_FLOOR_FILE)
        if (!file.exists()) return null
        return try {
            BitmapFactory.decodeFile(file.absolutePath)?.let { decoded ->
                validateWallpaperBitmap(prepareBitmapForGl(decoded), "picked_cache")?.also {
                    BumpDeskLog.d(
                        BumpDeskLog.Tag.WALLPAPER,
                        "loadCachedPickedWallpaper",
                        "loaded ${it.width}x${it.height}"
                    )
                }
            }
        } catch (e: Exception) {
            BumpDeskLog.w(BumpDeskLog.Tag.WALLPAPER, "loadCachedPickedWallpaper", "failed: ${e.message}")
            null
        }
    }

    private fun validateWallpaperBitmap(bitmap: Bitmap, source: String): Bitmap? {
        if (isMostlyBlank(bitmap)) {
            BumpDeskLog.w(BumpDeskLog.Tag.WALLPAPER, "loadSystemWallpaperBitmap", "$source returned blank bitmap")
            bitmap.recycle()
            return null
        }
        return bitmap
    }

    /** Rejects uniform near-black captures (e.g. failed window copy), not legitimate dark wallpapers. */
    fun isMostlyBlank(bitmap: Bitmap): Boolean {
        val stepX = (bitmap.width / 8).coerceAtLeast(1)
        val stepY = (bitmap.height / 8).coerceAtLeast(1)
        var minLuminance = 255
        var maxLuminance = 0
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val luminance = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                minLuminance = minOf(minLuminance, luminance)
                maxLuminance = maxOf(maxLuminance, luminance)
                x += stepX
            }
            y += stepY
        }
        return maxLuminance < 8 || (maxLuminance - minLuminance < 4 && maxLuminance < 16)
    }

    private fun loadWallpaperFromManager(
        context: Context,
        source: String,
        drawableSupplier: () -> Drawable?
    ): Bitmap? {
        return try {
            loadWallpaperFromDrawable(drawableSupplier(), context, source)
        } catch (e: SecurityException) {
            BumpDeskLog.w(BumpDeskLog.Tag.WALLPAPER, "loadSystemWallpaperBitmap", "$source permission denied")
            null
        } catch (e: LinkageError) {
            BumpDeskLog.w(
                BumpDeskLog.Tag.WALLPAPER,
                "loadSystemWallpaperBitmap",
                "$source unavailable on this device: ${e.message}"
            )
            null
        } catch (e: Exception) {
            BumpDeskLog.w(BumpDeskLog.Tag.WALLPAPER, "loadSystemWallpaperBitmap", "$source failed: ${e.message}")
            null
        }
    }

    private fun loadWallpaperFromFile(wm: WallpaperManager): Bitmap? {
        return try {
            wm.getWallpaperFile(WallpaperManager.FLAG_SYSTEM)?.use { pfd ->
                BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor)?.let { decoded ->
                    BumpDeskLog.d(
                        BumpDeskLog.Tag.WALLPAPER,
                        "loadSystemWallpaperBitmap",
                        "loaded via getWallpaperFile ${decoded.width}x${decoded.height}"
                    )
                    prepareBitmapForGl(decoded)
                }
            }
        } catch (e: SecurityException) {
            BumpDeskLog.w(BumpDeskLog.Tag.WALLPAPER, "loadSystemWallpaperBitmap", "getWallpaperFile permission denied")
            null
        } catch (e: LinkageError) {
            BumpDeskLog.w(
                BumpDeskLog.Tag.WALLPAPER,
                "loadSystemWallpaperBitmap",
                "getWallpaperFile unavailable on this device: ${e.message}"
            )
            null
        } catch (e: Exception) {
            BumpDeskLog.w(BumpDeskLog.Tag.WALLPAPER, "loadSystemWallpaperBitmap", "getWallpaperFile failed: ${e.message}")
            null
        }
    }

    private fun loadWallpaperFromDrawable(
        drawable: Drawable?,
        context: Context,
        source: String
    ): Bitmap? {
        if (drawable == null) return null
        return try {
            val bitmap = if (drawable is BitmapDrawable && drawable.bitmap != null) {
                prepareBitmapForGl(drawable.bitmap)
            } else {
                val dm = context.resources.displayMetrics
                val target = maxOf(dm.widthPixels, dm.heightPixels).coerceIn(512, 2048)
                prepareBitmapForGl(getBitmapFromDrawable(drawable, target))
            }
            BumpDeskLog.d(
                BumpDeskLog.Tag.WALLPAPER,
                "loadSystemWallpaperBitmap",
                "loaded via $source ${bitmap.width}x${bitmap.height}"
            )
            bitmap
        } catch (e: SecurityException) {
            BumpDeskLog.w(BumpDeskLog.Tag.WALLPAPER, "loadSystemWallpaperBitmap", "$source permission denied")
            null
        }
    }

    /**
     * Loads an SVG from an InputStream and renders it to a Bitmap.
     * Forces the SVG to stretch to fill the provided dimensions.
     */
    fun getBitmapFromSvg(inputStream: InputStream, width: Int = 512, height: Int = 512): Bitmap? {
        return try {
            val svg = SVG.getFromInputStream(inputStream)
            
            val targetW = if (width > 0) width else {
                if (svg.documentWidth > 0) svg.documentWidth.toInt() else 512
            }
            val targetH = if (height > 0) height else {
                if (svg.documentHeight > 0) svg.documentHeight.toInt() else 512
            }

            val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            
            // Perspective Correction: Force SVG to stretch to fill the bitmap dimensions
            svg.documentWidth = targetW.toFloat()
            svg.documentHeight = targetH.toFloat()
            svg.documentPreserveAspectRatio = PreserveAspectRatio.STRETCH

            svg.renderToCanvas(canvas)
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    fun createAppDrawerIcon(context: Context, size: Int = 256): Bitmap {
        val slots = List(4) { index -> createAppDrawerSlotBitmap(index) }
        return createPileFolderIcon(slots, label = "All Apps", size = size)
    }

    /** Mini tile for the All Apps 2×2 preview (matches folder group cell style). */
    private fun createAppDrawerSlotBitmap(index: Int): Bitmap {
        val size = 64
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val sel = ThemeManager.getSelectionColor()
        val tint = when (index) {
            0 -> floatArrayOf(sel[0], sel[1], sel[2], 1f)
            1 -> floatArrayOf(sel[0] * 0.85f + 0.15f, sel[1] * 0.85f + 0.15f, sel[2], 1f)
            2 -> floatArrayOf(sel[0], sel[1] * 0.9f + 0.1f, sel[2] * 0.9f + 0.1f, 1f)
            else -> floatArrayOf(sel[0] * 0.75f + 0.25f, sel[1] * 0.75f + 0.25f, sel[2] * 0.75f + 0.25f, 1f)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(230, (tint[0] * 255).toInt(), (tint[1] * 255).toInt(), (tint[2] * 255).toInt())
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), size * 0.22f, size * 0.22f, paint)
        return bitmap
    }

    /** Folder pile icon: rounded background with up to four app icons in a 2×2 grid; optional label below. */
    fun createPileFolderIcon(iconBitmaps: List<Bitmap>, label: String? = null, size: Int = 256): Bitmap {
        val hasLabel = !label.isNullOrBlank()
        val iconSize = size
        val combinedHeight = if (hasLabel) (iconSize * 1.38f).toInt() else iconSize
        val bitmap = Bitmap.createBitmap(iconSize, combinedHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(240, 45, 48, 58)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(0f, 0f, iconSize.toFloat(), iconSize.toFloat(), iconSize * 0.22f, iconSize * 0.22f, bgPaint)

        val padding = iconSize * 0.14f
        val gap = iconSize * 0.06f
        val cell = (iconSize - 2f * padding - gap) / 2f
        val iconPaint = Paint(Paint.FILTER_BITMAP_FLAG)

        for (index in 0 until 4) {
            if (index >= iconBitmaps.size) break
            val row = index / 2
            val col = index % 2
            val left = padding + col * (cell + gap)
            val top = padding + row * (cell + gap)
            val src = android.graphics.Rect(0, 0, iconBitmaps[index].width, iconBitmaps[index].height)
            val dst = android.graphics.RectF(left, top, left + cell, top + cell)
            canvas.drawBitmap(iconBitmaps[index], src, dst, iconPaint)
        }

        if (hasLabel) {
            val labelBitmap = TextRenderer.createAppLabelBitmap(label!!, iconSize, (iconSize * 0.32f).toInt())
            val labelAreaHeight = combinedHeight - iconSize
            val labelScale = labelAreaHeight.toFloat() / labelBitmap.height
            val targetLabelW = (labelBitmap.width * labelScale).toInt().coerceAtMost(iconSize)
            val targetLabelH = (labelBitmap.height * labelScale).toInt()
            val labelX = (iconSize - targetLabelW) / 2f
            val labelY = iconSize + (labelAreaHeight - targetLabelH) / 2f
            val src = android.graphics.Rect(0, 0, labelBitmap.width, labelBitmap.height)
            val dst = android.graphics.Rect(
                labelX.toInt(),
                labelY.toInt(),
                (labelX + targetLabelW).toInt(),
                (labelY + targetLabelH).toInt(),
            )
            canvas.drawBitmap(labelBitmap, src, dst, Paint(Paint.FILTER_BITMAP_FLAG))
            labelBitmap.recycle()
        }

        return bitmap
    }

    fun getCombinedBitmap(context: Context, icon: Bitmap, label: Bitmap, isShortcut: Boolean = false): Bitmap {
        val iconSize = icon.width.coerceAtLeast(icon.height)
        val combinedWidth = iconSize
        val combinedHeight = (iconSize * 1.38f).toInt()
        
        val combinedBitmap = Bitmap.createBitmap(combinedWidth, combinedHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(combinedBitmap)
        
        val drawW = icon.width
        val drawH = icon.height
        val iconX = (combinedWidth - drawW) / 2f
        val iconY = (iconSize - drawH) / 2f
        canvas.drawBitmap(icon, iconX, iconY, null)
        
        if (isShortcut) {
            if (arrowOverlayCache == null) {
                arrowOverlayCache = ThemeManager.loadBitmapFromAsset(context, "BumpTop/${ThemeManager.currentThemeName}/core/icon/link_arrow_overlay.png")
            }
            arrowOverlayCache?.let {
                val overlaySize = (iconSize * 0.3f).toInt()
                val dst = Rect(0, iconSize - overlaySize, overlaySize, iconSize)
                canvas.drawBitmap(it, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
            }
        }
        
        val labelAreaHeight = combinedHeight - iconSize
        val labelScale = labelAreaHeight.toFloat() / label.height
        val targetLabelW = (label.width * labelScale).toInt().coerceAtMost(combinedWidth)
        val targetLabelH = (label.height * labelScale).toInt()
        
        val labelX = (combinedWidth - targetLabelW) / 2f
        val labelY = iconSize.toFloat() + (labelAreaHeight - targetLabelH) / 2f
        
        val src = Rect(0, 0, label.width, label.height)
        val dst = Rect(labelX.toInt(), labelY.toInt(), (labelX + targetLabelW).toInt(), (labelY + targetLabelH).toInt())
        canvas.drawBitmap(label, src, dst, Paint(Paint.FILTER_BITMAP_FLAG))
        
        return combinedBitmap
    }

    /**
     * Recents icon-grid tile: snapshot fills the icon square; app icon is a corner badge.
     * Falls back to a scaled launcher icon when no snapshot is available.
     * Live tasks ([taskId] >= 0) get a tinted tile background and an accent dot.
     */
    fun createRecentsGridIconBitmap(
        context: Context,
        snapshot: Bitmap?,
        icon: Bitmap,
        label: String,
        taskId: Int = -1,
    ): Bitmap {
        val tileSize = 192
        val combinedHeight = (tileSize * 1.38f).toInt()
        val combinedBitmap = Bitmap.createBitmap(tileSize, combinedHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(combinedBitmap)
        val corner = tileSize * 0.18f
        val isLiveTask = taskId >= 0

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = when {
                snapshot != null -> Color.argb(255, 32, 35, 42)
                isLiveTask -> Color.argb(255, 36, 42, 56)
                else -> Color.argb(255, 48, 50, 58)
            }
        }
        canvas.drawRoundRect(0f, 0f, tileSize.toFloat(), tileSize.toFloat(), corner, corner, bgPaint)

        if (snapshot != null) {
            val cropped = centerCropToAspect(snapshot, tileSize.toFloat(), tileSize.toFloat())
            canvas.drawBitmap(cropped, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
            if (cropped !== snapshot) cropped.recycle()

            canvas.drawRect(
                0f,
                tileSize * 0.45f,
                tileSize.toFloat(),
                tileSize.toFloat(),
                Paint().apply {
                    shader = LinearGradient(
                        0f,
                        tileSize * 0.45f,
                        0f,
                        tileSize.toFloat(),
                        Color.TRANSPARENT,
                        Color.argb(150, 0, 0, 0),
                        Shader.TileMode.CLAMP,
                    )
                },
            )

            val badgeSize = (tileSize * 0.34f).toInt()
            val margin = (tileSize * 0.06f).toInt()
            val dst = Rect(
                tileSize - badgeSize - margin,
                tileSize - badgeSize - margin,
                tileSize - margin,
                tileSize - margin,
            )
            val src = Rect(0, 0, icon.width, icon.height)
            canvas.drawBitmap(icon, src, dst, Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            val contentSize = tileSize * 0.62f
            val left = (tileSize - contentSize) / 2f
            val dst = RectF(left, left, left + contentSize, left + contentSize)
            canvas.drawBitmap(icon, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
        }

        if (isLiveTask) {
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(255, 100, 181, 246)
            }
            canvas.drawCircle(tileSize / 2f, tileSize - 10f, 5f, dotPaint)
        }

        val labelBitmap = TextRenderer.createAppLabelBitmap(label)
        val labelAreaHeight = combinedHeight - tileSize
        val labelScale = labelAreaHeight.toFloat() / labelBitmap.height
        val targetLabelW = (labelBitmap.width * labelScale).toInt().coerceAtMost(tileSize)
        val targetLabelH = (labelBitmap.height * labelScale).toInt()

        val labelX = (tileSize - targetLabelW) / 2f
        val labelY = tileSize.toFloat() + (labelAreaHeight - targetLabelH) / 2f

        val src = Rect(0, 0, labelBitmap.width, labelBitmap.height)
        val dst = Rect(labelX.toInt(), labelY.toInt(), (labelX + targetLabelW).toInt(), (labelY + targetLabelH).toInt())
        canvas.drawBitmap(labelBitmap, src, dst, Paint(Paint.FILTER_BITMAP_FLAG))
        labelBitmap.recycle()

        return combinedBitmap
    }

    fun loadCombinedTexture(context: Context, icon: Bitmap, label: Bitmap, isShortcut: Boolean = false): Int {
        val combinedBitmap = getCombinedBitmap(context, icon, label, isShortcut)
        val textureId = loadTextureFromBitmap(combinedBitmap)
        combinedBitmap.recycle()
        return textureId
    }

    /**
     * Creates a bitmap for a recent task tile.
     */
    fun createRecentTaskBitmap(context: Context, snapshot: Bitmap?, icon: Drawable?, label: String): Bitmap {
        val width = 512
        val snapshotHeight = 720
        val actionsHeight = 120
        val labelHeight = 100
        val combinedHeight = snapshotHeight + actionsHeight + labelHeight
        
        val bitmap = Bitmap.createBitmap(width, combinedHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Background
        val bgPaint = Paint().apply { color = Color.argb(255, 30, 30, 30); style = Paint.Style.FILL }
        canvas.drawRect(0f, 0f, width.toFloat(), combinedHeight.toFloat(), bgPaint)
        
        // Snapshot
        if (snapshot != null) {
            val src = Rect(0, 0, snapshot.width, snapshot.height)
            val dst = Rect(0, 0, width, snapshotHeight)
            canvas.drawBitmap(snapshot, src, dst, Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            val p = Paint().apply { 
                shader = LinearGradient(0f, 0f, 0f, snapshotHeight.toFloat(), Color.parseColor("#333333"), Color.parseColor("#111111"), Shader.TileMode.CLAMP)
            }
            canvas.drawRect(0f, 0f, width.toFloat(), snapshotHeight.toFloat(), p)
            
            icon?.let {
                val largeIconSize = 240
                val iconBitmap = getBitmapFromDrawable(it, 256)
                val dst = Rect((width - largeIconSize)/2, (snapshotHeight - largeIconSize)/2, (width + largeIconSize)/2, (snapshotHeight + largeIconSize)/2)
                val iconPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 120 }
                canvas.drawBitmap(iconBitmap, null, dst, iconPaint)
                iconBitmap.recycle()
            }
            
            val textP = Paint().apply { color = Color.GRAY; textSize = 30f; textAlign = Paint.Align.CENTER; isAntiAlias = true }
            canvas.drawText("No Preview", width/2f, snapshotHeight - 40f, textP)
        }
        
        // Small icon in top-left
        icon?.let {
            val iconSize = 80; val margin = 20; val iconBitmap = getBitmapFromDrawable(it, 128)
            val dst = Rect(margin, margin, margin + iconSize, margin + iconSize)
            canvas.drawBitmap(iconBitmap, null, dst, Paint(Paint.FILTER_BITMAP_FLAG)); iconBitmap.recycle()
        }
        
        // Close Button (Top-Right)
        val closePaint = Paint().apply { color = Color.parseColor("#CCFF4444"); style = Paint.Style.FILL; isAntiAlias = true }
        canvas.drawCircle(width - 50f, 50f, 25f, closePaint)
        val xPaint = Paint().apply { color = Color.WHITE; strokeWidth = 5f; style = Paint.Style.STROKE; isAntiAlias = true }
        canvas.drawLine(width - 62f, 38f, width - 38f, 62f, xPaint)
        canvas.drawLine(width - 62f, 62f, width - 38f, 38f, xPaint)

        // Action Icons Bar
        val actionIconSize = 70
        val actionMargin = (width - (4 * actionIconSize)) / 5
        val actionY = snapshotHeight + (actionsHeight - actionIconSize) / 2f
        
        val actionDrawables = listOf(
            android.R.drawable.ic_menu_info_details, // App Info
            android.R.drawable.ic_menu_zoom,         // Fullscreen
            android.R.drawable.ic_menu_crop,         // Freeform
            android.R.drawable.ic_lock_lock          // Pinned
        )
        
        actionDrawables.forEachIndexed { i, resId ->
            val d = context.getDrawable(resId)
            d?.let {
                val left = actionMargin + i * (actionIconSize + actionMargin)
                it.setBounds(left, actionY.toInt(), left + actionIconSize, (actionY + actionIconSize).toInt())
                it.setTint(Color.WHITE)
                it.draw(canvas)
            }
        }

        // Label
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { 
            color = Color.WHITE; textSize = 38f; textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) 
        }
        canvas.drawText(label, width/2f, snapshotHeight + actionsHeight + labelHeight/2f + 15f, labelPaint)
        
        return bitmap
    }

    fun loadRecentTaskTexture(context: Context, snapshot: Bitmap?, icon: Drawable?, label: String): Int {
        val bitmap = createRecentTaskBitmap(context, snapshot, icon, label)
        val textureId = loadTextureFromBitmap(bitmap)
        bitmap.recycle()
        return textureId
    }

    fun loadTextureFromBitmap(bitmap: Bitmap): Int {
        val textureHandle = IntArray(1); GLES20.glGenTextures(1, textureHandle, 0)
        if (textureHandle[0] != 0) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            return textureHandle[0]
        }
        return -1
    }

    fun clearArrowCache() {
        arrowOverlayCache?.recycle()
        arrowOverlayCache = null
    }
}
