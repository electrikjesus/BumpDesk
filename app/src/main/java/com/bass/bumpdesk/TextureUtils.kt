package com.bass.bumpdesk

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.opengl.GLES20
import android.opengl.GLUtils

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

    fun createAppDrawerIcon(context: Context, size: Int = 256): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        
        // Background - Rounded rect with theme selection color (semi-transparent)
        val selColor = ThemeManager.getSelectionColor()
        paint.color = Color.argb(180, (selColor[0] * 255).toInt(), (selColor[1] * 255).toInt(), (selColor[2] * 255).toInt())
        canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), size * 0.2f, size * 0.2f, paint)
        
        // Grid pattern
        paint.color = Color.WHITE
        val padding = size * 0.2f
        val cellSize = (size - 2 * padding) / 3f
        val dotSize = cellSize * 0.6f
        val offset = (cellSize - dotSize) / 2f
        
        for (i in 0..2) {
            for (j in 0..2) {
                val left = padding + i * cellSize + offset
                val top = padding + j * cellSize + offset
                canvas.drawRoundRect(left, top, left + dotSize, top + dotSize, dotSize * 0.3f, dotSize * 0.3f, paint)
            }
        }

        return bitmap
    }

    fun getCombinedBitmap(context: Context, icon: Bitmap, label: Bitmap, isShortcut: Boolean = false): Bitmap {
        val iconSize = icon.width.coerceAtLeast(icon.height)
        val combinedWidth = iconSize
        val combinedHeight = (iconSize * 1.25f).toInt()
        
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
