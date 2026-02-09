package com.bass.bumpdesk

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.opengl.GLES20
import android.opengl.GLUtils

object TextRenderer {
    fun createTextBitmap(text: String, width: Int = 256, height: Int = 64): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        val paint = Paint().apply {
            textSize = 36f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            color = Color.WHITE
            style = Paint.Style.FILL
            setShadowLayer(4f, 2f, 2f, Color.BLACK)
        }

        val fontMetrics = paint.fontMetrics
        val textY = height / 2f - (fontMetrics.ascent + fontMetrics.descent) / 2f
        
        // Optional: draw a very faint background for extra contrast
        // paint.color = Color.argb(80, 0, 0, 0)
        // canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 10f, 10f, paint)
        // paint.color = Color.WHITE
        
        canvas.drawText(text, width / 2f, textY, paint)

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
