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
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val radius = size * 0.28f
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = backgroundColor
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), radius, radius, bgPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = foregroundColor
            textSize = size * 0.46f
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
