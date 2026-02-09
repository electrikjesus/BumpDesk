package com.bass.bumpdesk

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class Plane(private val shader: DefaultShader) {

    private var vertexBuffer: FloatBuffer
    private var texCoordBuffer: FloatBuffer
    private var normalBuffer: FloatBuffer

    private var planeCoords = floatArrayOf(
        -1.0f,  0.0f, -1.0f,   // 0: Far Left
        -1.0f,  0.0f,  1.0f,   // 1: Near Left
         1.0f,  0.0f,  1.0f,   // 2: Near Right
         1.0f,  0.0f, -1.0f    // 3: Far Right
    )

    private var normals = floatArrayOf(
        0.0f, 1.0f, 0.0f,
        0.0f, 1.0f, 0.0f,
        0.0f, 1.0f, 0.0f,
        0.0f, 1.0f, 0.0f
    )

    // Default UV mapping
    private var texCoords = floatArrayOf(
        0.0f, 0.0f,
        0.0f, 1.0f,
        1.0f, 1.0f,
        1.0f, 0.0f
    )

    init {
        vertexBuffer = ByteBuffer.allocateDirect(planeCoords.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply { put(planeCoords); position(0) }
        }

        texCoordBuffer = ByteBuffer.allocateDirect(texCoords.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply { put(texCoords); position(0) }
        }

        normalBuffer = ByteBuffer.allocateDirect(normals.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply { put(normals); position(0) }
        }
    }

    fun updateUVs(scale: Float) {
        val newTexCoords = floatArrayOf(
            0.0f, 0.0f,
            0.0f, scale,
            scale, scale,
            scale, 0.0f
        )
        texCoordBuffer.clear()
        texCoordBuffer.put(newTexCoords)
        texCoordBuffer.position(0)
    }

    fun draw(vPMatrix: FloatArray, modelMatrix: FloatArray, color: FloatArray, textureId: Int = -1, lightPos: FloatArray = floatArrayOf(0f, 10f, 0f), ambient: Float = 0.3f, useLighting: Boolean = true) {
        shader.draw(
            vertexBuffer,
            normalBuffer,
            texCoordBuffer,
            vPMatrix,
            modelMatrix,
            color,
            textureId,
            lightPos,
            ambient,
            useLighting
        )
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, 4)
    }
}
