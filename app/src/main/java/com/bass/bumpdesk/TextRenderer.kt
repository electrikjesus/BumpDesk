package com.bass.bumpdesk

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.opengl.GLES20
import android.opengl.GLUtils

object TextRenderer {
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

    fun createRoundedPanelBitmap(
        width: Int = 512,
        height: Int = 640,
        cornerRadiusPx: Float = 42f,
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
