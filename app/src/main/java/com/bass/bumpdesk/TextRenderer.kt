package com.bass.bumpdesk

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.ContextCompat

object TextRenderer {
    const val STICKY_NOTE_BITMAP_SIZE = 512
    const val STICKY_NOTE_DEFAULT_SCALE_MULTIPLIER = 5f
    fun createTextBitmap(text: String, width: Int = 256, height: Int = 64): Bitmap {
        return createStyledTextBitmap(
            text = text,
            width = width,
            height = height,
            textSizeSp = 36f,
            bold = false,
            textColor = Color.WHITE,
            align = Paint.Align.CENTER,
            shadow = true,
        )
    }

    /** Sticky note with themed background and wrapped body text. */
    fun createStickyNoteBitmap(
        context: Context,
        text: String,
        width: Int = STICKY_NOTE_BITMAP_SIZE,
        height: Int = STICKY_NOTE_BITMAP_SIZE,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        ContextCompat.getDrawable(context, R.drawable.sticky_note_background)?.let { background ->
            background.setBounds(0, 0, width, height)
            background.draw(canvas)
        }

        val horizontalPadding = width * 0.12f
        val topPadding = height * 0.14f
        val textWidth = (width - horizontalPadding * 2f).toInt().coerceAtLeast(1)
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = width * 0.085f
            color = Color.rgb(38, 38, 38)
            typeface = ThemeManager.getStickyNoteTypeface(context)
                ?: Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }

        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, textPaint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.15f)
            .setIncludePad(false)
            .build()

        canvas.save()
        canvas.translate(horizontalPadding, topPadding)
        layout.draw(canvas)
        canvas.restore()

        return bitmap
    }

    /** App icon label — slightly larger type for readability at default icon scale. */
    fun createAppLabelBitmap(text: String, width: Int = 280, height: Int = 88): Bitmap {
        return createStyledTextBitmap(
            text = text,
            width = width,
            height = height,
            textSizeSp = 44f,
            bold = true,
            textColor = Color.WHITE,
            align = Paint.Align.CENTER,
            shadow = true,
        )
    }

    fun createMaterialTitleBitmap(text: String, width: Int = 768, height: Int = 128): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(235, 45, 48, 58)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), 28f, 28f, bgPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 56f
            textAlign = Paint.Align.LEFT
            color = Color.rgb(245, 246, 250)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val fontMetrics = textPaint.fontMetrics
        val textY = height / 2f - (fontMetrics.ascent + fontMetrics.descent) / 2f
        canvas.drawText(text, 36f, textY, textPaint)
        return bitmap
    }

    fun createIconButtonBitmap(
        glyph: String,
        size: Int = 192,
        backgroundColor: Int = Color.rgb(62, 68, 82),
        foregroundColor: Int = Color.WHITE,
    ): Bitmap {
        return createMaterialHandleBitmap(glyph, size, backgroundColor, foregroundColor)
    }

    /** Rounded Material-style control for widget move/resize handles. */
    fun createMaterialHandleBitmap(
        glyph: String,
        size: Int = 192,
        backgroundColor: Int = Color.rgb(62, 68, 82),
        foregroundColor: Int = Color.WHITE,
        strokeColor: Int = Color.argb(90, 180, 190, 210),
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val radius = size * 0.32f
        val rect = RectF(size * 0.04f, size * 0.04f, size * 0.96f, size * 0.96f)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = backgroundColor
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(rect, radius, radius, bgPaint)

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = strokeColor
            style = Paint.Style.STROKE
            strokeWidth = size * 0.035f
        }
        canvas.drawRoundRect(rect, radius, radius, strokePaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = foregroundColor
            textSize = size * 0.42f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val metrics = textPaint.fontMetrics
        val y = size / 2f - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(glyph, size / 2f, y, textPaint)
        return bitmap
    }

    /** Small circle for folder pagination indicators (anti-aliased, transparent outside shape). */
    fun createPageIndicatorDotBitmap(
        size: Int = 128,
        fillColorArgb: Int,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fillColorArgb
            style = Paint.Style.FILL
        }
        val radius = size * 0.42f
        canvas.drawCircle(size / 2f, size / 2f, radius, paint)
        return bitmap
    }

    fun createRoundedPanelBitmap(
        width: Int = PANEL_BITMAP_WIDTH,
        height: Int = PANEL_BITMAP_HEIGHT,
        cornerRadiusPx: Float = PANEL_CORNER_RADIUS_PX,
        fillColor: Int = Color.argb(240, 33, 35, 43),
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fillColor
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), cornerRadiusPx, cornerRadiusPx, paint)
        return bitmap
    }

    const val PANEL_BITMAP_WIDTH = 512
    const val PANEL_BITMAP_HEIGHT = 640
    const val PANEL_CORNER_RADIUS_PX = 42f

    fun panelCornerUvFractions(): Pair<Float, Float> =
        PANEL_CORNER_RADIUS_PX / PANEL_BITMAP_WIDTH to PANEL_CORNER_RADIUS_PX / PANEL_BITMAP_HEIGHT

    private fun createStyledTextBitmap(
        text: String,
        width: Int,
        height: Int,
        textSizeSp: Float,
        bold: Boolean,
        textColor: Int,
        align: Paint.Align,
        shadow: Boolean,
        horizontalPadding: Float = 0f,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = textSizeSp
            textAlign = align
            color = textColor
            style = Paint.Style.FILL
            typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
            if (shadow) {
                setShadowLayer(4f, 2f, 2f, Color.BLACK)
            }
        }

        val fontMetrics = paint.fontMetrics
        val textY = height / 2f - (fontMetrics.ascent + fontMetrics.descent) / 2f
        val textX = when (align) {
            Paint.Align.LEFT -> horizontalPadding
            Paint.Align.RIGHT -> width - horizontalPadding
            else -> width / 2f
        }
        canvas.drawText(text, textX, textY, paint)
        return bitmap
    }

    fun loadTextTexture(bitmap: Bitmap): Int {
        val textureHandle = IntArray(1)
        GLES20.glGenTextures(1, textureHandle, 0)
        if (textureHandle[0] != 0) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        }
        return textureHandle[0]
    }
}
