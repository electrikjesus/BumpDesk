package com.bass.bumpdesk

import android.opengl.Matrix

class RoomRenderer(private val shader: DefaultShader) {
    private val floor = Plane(shader)
    private val wallBack = Plane(shader)
    private val wallLeft = Plane(shader)
    private val wallRight = Plane(shader)
    private val wallTop = Plane(shader) // Added ceiling/top wall
    
    private val modelMatrix = FloatArray(16)

    fun draw(vPMatrix: FloatArray, floorTexture: Int, wallTextures: IntArray, lightPos: FloatArray) {
        // Floor
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.scaleM(modelMatrix, 0, 50f, 1f, 50f)
        // Task: Fix floor plane texture tiling. 1.0f means 1x1 stretch across the whole floor.
        floor.updateUVs(1.0f)
        floor.draw(vPMatrix, modelMatrix, floatArrayOf(0.4f, 0.4f, 0.4f, 1.0f), floorTexture, lightPos, 0.3f, true)
        
        // Back Wall
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, 0f, 6f, -10f)
        Matrix.rotateM(modelMatrix, 0, 90f, 1f, 0f, 0f)
        Matrix.scaleM(modelMatrix, 0, 10f, 1f, 6f)
        wallBack.updateUVs(1f)
        wallBack.draw(vPMatrix, modelMatrix, floatArrayOf(0.5f, 0.5f, 0.5f, 1.0f), wallTextures[0], lightPos, 0.2f, true)
        
        // Left Wall
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, -10f, 6f, 0f)
        Matrix.rotateM(modelMatrix, 0, -90f, 0f, 0f, 1f)
        Matrix.scaleM(modelMatrix, 0, 6f, 1f, 10f)
        wallLeft.updateUVs(1f)
        wallLeft.draw(vPMatrix, modelMatrix, floatArrayOf(0.5f, 0.5f, 0.5f, 1.0f), wallTextures[1], lightPos, 0.2f, true)
        
        // Right Wall
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, 10f, 6f, 0f)
        Matrix.rotateM(modelMatrix, 0, 90f, 0f, 0f, 1f)
        Matrix.scaleM(modelMatrix, 0, 6f, 1f, 10f)
        wallRight.updateUVs(1f)
        wallRight.draw(vPMatrix, modelMatrix, floatArrayOf(0.5f, 0.5f, 0.5f, 1.0f), wallTextures[2], lightPos, 0.2f, true)

        // Top Wall (Ceiling)
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, 0f, 12f, 0f)
        Matrix.rotateM(modelMatrix, 0, 180f, 1f, 0f, 0f)
        Matrix.scaleM(modelMatrix, 0, 10f, 1f, 10f)
        wallTop.updateUVs(1f)
        wallTop.draw(vPMatrix, modelMatrix, floatArrayOf(0.3f, 0.3f, 0.3f, 1.0f), wallTextures[3], lightPos, 0.1f, true)
    }
}
