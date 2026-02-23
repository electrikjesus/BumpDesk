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

        val smoothedPoints = smoothPath(points)
        val coords = FloatArray(smoothedPoints.size * 3)
        for (i in smoothedPoints.indices) {
            coords[i * 3] = smoothedPoints[i][0]
            coords[i * 3 + 1] = smoothedPoints[i][1] + 0.02f
            coords[i * 3 + 2] = smoothedPoints[i][2]
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

    /**
     * Smoothes a path of points using Chaikin's corner-cutting algorithm.
     */
    private fun smoothPath(points: List<FloatArray>, iterations: Int = 2): List<FloatArray> {
        if (points.size < 3) return points
        
        var current = points
        repeat(iterations) {
            val next = mutableListOf<FloatArray>()
            next.add(current.first())
            
            for (i in 0 until current.size - 1) {
                val p0 = current[i]
                val p1 = current[i + 1]
                
                // Cut the corner at 25% and 75%
                val q = floatArrayOf(
                    0.75f * p0[0] + 0.25f * p1[0],
                    0.75f * p0[1] + 0.25f * p1[1],
                    0.75f * p0[2] + 0.25f * p1[2]
                )
                val r = floatArrayOf(
                    0.25f * p0[0] + 0.75f * p1[0],
                    0.25f * p0[1] + 0.75f * p1[1],
                    0.25f * p0[2] + 0.75f * p1[2]
                )
                next.add(q)
                next.add(r)
            }
            
            next.add(current.last())
            current = next
        }
        return current
    }
}
