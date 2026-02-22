package com.bass.bumpdesk

import android.opengl.GLES20
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class LassoRenderer(private val shader: LassoShader) {
    private var vertexBuffer: FloatBuffer? = null

    fun draw(vPMatrix: FloatArray, points: List<FloatArray>) {
        if (points.size < 2) return

        // Task: Smooth Lasso - Use more points or interpolation if needed
        // For now, we'll draw the raw points with additive blending for a "glow"
        val coords = FloatArray(points.size * 3)
        for (i in points.indices) {
            coords[i * 3] = points[i][0]
            coords[i * 3 + 1] = points[i][1] + 0.02f // Slightly above floor
            coords[i * 3 + 2] = points[i][2]
        }

        val bb = ByteBuffer.allocateDirect(coords.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply { put(coords); position(0) }
        }
        vertexBuffer = bb

        // Enable additive blending for glow effect
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)

        shader.use()
        GLES20.glEnableVertexAttribArray(shader.posHandle)
        GLES20.glVertexAttribPointer(shader.posHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        val color = ThemeManager.getSelectionColor()
        GLES20.glUniform4fv(shader.colorHandle, 1, floatArrayOf(color[0], color[1], color[2], 0.8f), 0)
        GLES20.glUniformMatrix4fv(shader.vPMatrixHandle, 1, false, vPMatrix, 0)

        GLES20.glLineWidth(8f)
        GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, points.size)
        
        // Draw points for a "beaded" look
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, points.size)

        GLES20.glDisableVertexAttribArray(shader.posHandle)
        
        // Restore standard alpha blending
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
    }
}
