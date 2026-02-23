package com.bass.bumpdesk

import android.opengl.GLES20
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class LassoRenderer(private val shader: LassoShader) {
    private var vertexBuffer: FloatBuffer? = null

    fun draw(vPMatrix: FloatArray, points: List<Vector3>) {
        if (points.size < 2) return

        val smoothedPoints = PathUtils.smoothPath(points)
        val coords = FloatArray(smoothedPoints.size * 3)
        for (i in smoothedPoints.indices) {
            coords[i * 3] = smoothedPoints[i].x
            coords[i * 3 + 1] = smoothedPoints[i].y + 0.02f
            coords[i * 3 + 2] = smoothedPoints[i].z
        }

        val bb = ByteBuffer.allocateDirect(coords.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply { put(coords); position(0) }
        }
        vertexBuffer = bb

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)

        shader.use()
        GLES20.glEnableVertexAttribArray(shader.posHandle)
        GLES20.glVertexAttribPointer(shader.posHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        val color = ThemeManager.getSelectionColor()
        GLES20.glUniform4fv(shader.colorHandle, 1, floatArrayOf(color[0], color[1], color[2], 0.8f), 0)
        GLES20.glUniformMatrix4fv(shader.vPMatrixHandle, 1, false, vPMatrix, 0)

        GLES20.glLineWidth(10f)
        GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, smoothedPoints.size)
        
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, smoothedPoints.size)

        GLES20.glDisableVertexAttribArray(shader.posHandle)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
    }
}
