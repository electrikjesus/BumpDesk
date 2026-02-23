package com.bass.bumpdesk

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class Plane(private val shader: DefaultShader) {
    private var vboId = -1
    private var tboId = -1
    private var nboId = -1

    private val planeCoords = floatArrayOf(
        -1.0f,  0.0f, -1.0f,   // 0: Far Left
        -1.0f,  0.0f,  1.0f,   // 1: Near Left
         1.0f,  0.0f,  1.0f,   // 2: Near Right
         1.0f,  0.0f, -1.0f    // 3: Far Right
    )

    private val normals = floatArrayOf(
        0.0f, 1.0f, 0.0f,
        0.0f, 1.0f, 0.0f,
        0.0f, 1.0f, 0.0f,
        0.0f, 1.0f, 0.0f
    )

    private val texCoords = floatArrayOf(
        0.0f, 0.0f,
        0.0f, 1.0f,
        1.0f, 1.0f,
        1.0f, 0.0f
    )

    init {
        val buffers = IntArray(3)
        GLES20.glGenBuffers(3, buffers, 0)
        vboId = buffers[0]
        tboId = buffers[1]
        nboId = buffers[2]

        val vertexBuffer = ByteBuffer.allocateDirect(planeCoords.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply { put(planeCoords); position(0) }
        }
        val texCoordBuffer = ByteBuffer.allocateDirect(texCoords.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply { put(texCoords); position(0) }
        }
        val normalBuffer = ByteBuffer.allocateDirect(normals.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply { put(normals); position(0) }
        }

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, planeCoords.size * 4, vertexBuffer, GLES20.GL_STATIC_DRAW)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, tboId)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, texCoords.size * 4, texCoordBuffer, GLES20.GL_STATIC_DRAW)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, nboId)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, normals.size * 4, normalBuffer, GLES20.GL_STATIC_DRAW)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    fun updateUVs(scale: Float) {
        val newTexCoords = floatArrayOf(
            0.0f, 0.0f,
            0.0f, scale,
            scale, scale,
            scale, 0.0f
        )
        val texCoordBuffer = ByteBuffer.allocateDirect(newTexCoords.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply { put(newTexCoords); position(0) }
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, tboId)
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, newTexCoords.size * 4, texCoordBuffer)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    fun draw(vPMatrix: FloatArray, modelMatrix: FloatArray, color: FloatArray, textureId: Int = -1, lightPos: FloatArray = floatArrayOf(0f, 10f, 0f), ambient: Float = 0.3f, useLighting: Boolean = true) {
        shader.use()
        
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId)
        GLES20.glEnableVertexAttribArray(shader.posHandle)
        GLES20.glVertexAttribPointer(shader.posHandle, 3, GLES20.GL_FLOAT, false, 0, 0)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, tboId)
        GLES20.glEnableVertexAttribArray(shader.texCoordHandle)
        GLES20.glVertexAttribPointer(shader.texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, 0)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, nboId)
        GLES20.glEnableVertexAttribArray(shader.normalHandle)
        GLES20.glVertexAttribPointer(shader.normalHandle, 3, GLES20.GL_FLOAT, false, 0, 0)

        GLES20.glUniformMatrix4fv(shader.vPMatrixHandle, 1, false, vPMatrix, 0)
        GLES20.glUniformMatrix4fv(shader.modelMatrixHandle, 1, false, modelMatrix, 0)
        GLES20.glUniform4fv(shader.colorHandle, 1, color, 0)
        
        val hasTexture = textureId > 0
        GLES20.glUniform1i(shader.useTextureHandle, if (hasTexture) 1 else 0)
        if (hasTexture) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            GLES20.glUniform1i(shader.textureHandle, 0)
        }

        GLES20.glUniform1i(shader.useLightingHandle, if (useLighting) 1 else 0)
        GLES20.glUniform3fv(shader.lightPosHandle, 1, lightPos, 0)
        GLES20.glUniform1f(shader.ambientHandle, ambient)
        
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, 4)
        
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }
}
