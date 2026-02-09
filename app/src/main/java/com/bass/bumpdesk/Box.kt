package com.bass.bumpdesk

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class Box(private val shader: DefaultShader) {
    internal var vertexBuffer: FloatBuffer
    internal var texCoordBuffer: FloatBuffer

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
        // Top Face - (0,0) is top-left of bitmap.
        // We want label at the bottom (Near side, +Z) and icon at top (Far side, -Z)
        this[0] = 0f; this[1] = 1f // v0 (Near Left) -> (0, 1) Bottom-Left
        this[2] = 1f; this[3] = 1f // v1 (Near Right) -> (1, 1) Bottom-Right
        this[4] = 1f; this[5] = 0f // v2 (Far Right) -> (1, 0) Top-Right
        this[6] = 0f; this[7] = 0f // v3 (Far Left) -> (0, 0) Top-Left
    }

    init {
        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply { put(vertices); position(0) }
        }
        texCoordBuffer = ByteBuffer.allocateDirect(texCoords.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply { put(texCoords); position(0) }
        }
    }

    fun draw(vPMatrix: FloatArray, modelMatrix: FloatArray, textureId: Int, color: FloatArray) {
        // Draw Top Face with Texture
        shader.draw(
            vertexBuffer,
            null,
            texCoordBuffer,
            vPMatrix,
            modelMatrix,
            color,
            textureId,
            useLighting = false
        )
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, 4)

        // Task: Invisible Edges - Don't draw the other 5 faces
        /*
        val sideColor = floatArrayOf(color[0]*0.85f, color[1]*0.85f, color[2]*0.85f, color[3])
        shader.draw(
            vertexBuffer,
            null,
            null,
            vPMatrix,
            modelMatrix,
            sideColor,
            -1,
            useLighting = false
        )
        for (i in 1..5) {
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, i * 4, 4)
        }
        */
    }
}
