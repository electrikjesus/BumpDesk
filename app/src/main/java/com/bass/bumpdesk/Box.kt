package com.bass.bumpdesk

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class Box(private val shader: DefaultShader) {
    private var vboId = -1
    private var tboId = -1

    private val thickness = 0.0415f
    private val vertices = floatArrayOf(
        // Top Face (Texture)
        -1.0f,  thickness,  1.0f, // v0: Near Left
         1.0f,  thickness,  1.0f, // v1: Near Right
         1.0f,  thickness, -1.0f, // v2: Far Right
        -1.0f,  thickness, -1.0f, // v3: Far Left

        // Bottom Face
        -1.0f, -thickness,  1.0f,
        -1.0f, -thickness, -1.0f,
         1.0f, -thickness, -1.0f,
         1.0f, -thickness,  1.0f,

        // Front Face
        -1.0f, -thickness,  1.0f,
         1.0f, -thickness,  1.0f,
         1.0f,  thickness,  1.0f,
        -1.0f,  thickness,  1.0f,

        // Back Face
        -1.0f, -thickness, -1.0f,
        -1.0f,  thickness, -1.0f,
         1.0f,  thickness, -1.0f,
         1.0f, -thickness, -1.0f,

        // Left Face
        -1.0f, -thickness, -1.0f,
        -1.0f, -thickness,  1.0f,
        -1.0f,  thickness,  1.0f,
        -1.0f,  thickness, -1.0f,

        // Right Face
         1.0f, -thickness,  1.0f,
         1.0f, -thickness, -1.0f,
         1.0f,  thickness, -1.0f,
         1.0f,  thickness,  1.0f
    )

    private val texCoords = FloatArray(24 * 2) { 0f }.apply {
        // Top Face
        this[0] = 0f; this[1] = 1f 
        this[2] = 1f; this[3] = 1f 
        this[4] = 1f; this[5] = 0f 
        this[6] = 0f; this[7] = 0f 
    }

    init {
        val buffers = IntArray(2)
        GLES20.glGenBuffers(2, buffers, 0)
        vboId = buffers[0]
        tboId = buffers[1]

        val vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply { put(vertices); position(0) }
        }
        val texCoordBuffer = ByteBuffer.allocateDirect(texCoords.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply { put(texCoords); position(0) }
        }

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertices.size * 4, vertexBuffer, GLES20.GL_STATIC_DRAW)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, tboId)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, texCoords.size * 4, texCoordBuffer, GLES20.GL_STATIC_DRAW)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    fun draw(vPMatrix: FloatArray, modelMatrix: FloatArray, textureId: Int, color: FloatArray, isAnimated: Boolean = false) {
        shader.use()
        
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId)
        GLES20.glEnableVertexAttribArray(shader.posHandle)
        GLES20.glVertexAttribPointer(shader.posHandle, 3, GLES20.GL_FLOAT, false, 0, 0)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, tboId)
        GLES20.glEnableVertexAttribArray(shader.texCoordHandle)
        GLES20.glVertexAttribPointer(shader.texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, 0)

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

        GLES20.glUniform1i(shader.useLightingHandle, 0)
        GLES20.glUniform1i(shader.animatedHandle, if (isAnimated) 1 else 0)
        
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, 4)
        
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }
}
