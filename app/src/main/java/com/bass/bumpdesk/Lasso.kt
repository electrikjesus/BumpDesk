package com.bass.bumpdesk

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class Lasso : BaseShader(
    vertexShaderCode = """
        uniform mat4 uVPMatrix;
        attribute vec4 vPosition;
        void main() {
          gl_Position = uVPMatrix * vPosition;
        }
    """.trimIndent(),
    fragmentShaderCode = """
        precision mediump float;
        uniform vec4 vColor;
        void main() {
          gl_FragColor = vColor;
        }
    """.trimIndent()
) {
    private var vertexBuffer: FloatBuffer? = null
    private val posHandle = getAttrib("vPosition")
    private val colorHandle = getUniform("vColor")
    private val vPMatrixHandle = getUniform("uVPMatrix")

    fun draw(vPMatrix: FloatArray, points: List<FloatArray>) {
        if (points.size < 2) return

        val coords = FloatArray(points.size * 3)
        for (i in points.indices) {
            coords[i * 3] = points[i][0]
            coords[i * 3 + 1] = points[i][1]
            coords[i * 3 + 2] = points[i][2]
        }

        val bb = ByteBuffer.allocateDirect(coords.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply { put(coords); position(0) }
        }
        vertexBuffer = bb

        use()
        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        GLES20.glUniform4fv(colorHandle, 1, floatArrayOf(1f, 1f, 0f, 1f), 0)
        GLES20.glUniformMatrix4fv(vPMatrixHandle, 1, false, vPMatrix, 0)

        GLES20.glLineWidth(5f)
        GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, points.size)
        GLES20.glDisableVertexAttribArray(posHandle)
    }
}
