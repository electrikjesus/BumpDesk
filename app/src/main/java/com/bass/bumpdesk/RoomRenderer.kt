package com.bass.bumpdesk

import android.opengl.Matrix

class RoomRenderer(private val shader: DefaultShader) {
    private val floor = Plane(shader)
    private val wallBack = Plane(shader)
    private val wallFront = Plane(shader)
    private val wallLeft = Plane(shader)
    private val wallRight = Plane(shader)
    private val wallTop = Plane(shader)
    
    private val modelMatrix = FloatArray(16)

    fun draw(vPMatrix: FloatArray, floorTexture: Int, wallTextures: IntArray, lightPos: FloatArray, isInfiniteMode: Boolean = false) {
        val roomSize = 30f
        val roomHeight = 30f
        
        // Floor
        Matrix.setIdentityM(modelMatrix, 0)
        if (isInfiniteMode) {
            Matrix.scaleM(modelMatrix, 0, 100f, 1f, 100f)
            floor.updateUVs(2.0f)
        } else {
            Matrix.scaleM(modelMatrix, 0, roomSize, 1f, roomSize)
            floor.updateUVs(1.0f)
        }
        floor.draw(vPMatrix, modelMatrix, floatArrayOf(0.4f, 0.4f, 0.4f, 1.0f), floorTexture, lightPos, 0.3f, true)
        
        if (!isInfiniteMode) {
            // Back Wall
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, 0f, roomHeight / 2f, -roomSize)
            Matrix.rotateM(modelMatrix, 0, 90f, 1f, 0f, 0f)
            Matrix.scaleM(modelMatrix, 0, roomSize, 1f, roomHeight / 2f)
            wallBack.updateUVs(1f)
            wallBack.draw(vPMatrix, modelMatrix, floatArrayOf(0.5f, 0.5f, 0.5f, 1.0f), wallTextures[0], lightPos, 0.2f, true)
            
            // Front Wall (Behind Camera)
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, 0f, roomHeight / 2f, roomSize)
            Matrix.rotateM(modelMatrix, 0, -90f, 1f, 0f, 0f)
            Matrix.scaleM(modelMatrix, 0, roomSize, 1f, roomHeight / 2f)
            wallFront.updateUVs(1f)
            wallFront.draw(vPMatrix, modelMatrix, floatArrayOf(0.5f, 0.5f, 0.5f, 1.0f), wallTextures[0], lightPos, 0.2f, true)
            
            // Left Wall
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, -roomSize, roomHeight / 2f, 0f)
            Matrix.rotateM(modelMatrix, 0, -90f, 0f, 0f, 1f)
            Matrix.scaleM(modelMatrix, 0, roomHeight / 2f, 1f, roomSize)
            wallLeft.updateUVs(1f)
            wallLeft.draw(vPMatrix, modelMatrix, floatArrayOf(0.5f, 0.5f, 0.5f, 1.0f), wallTextures[1], lightPos, 0.2f, true)
            
            // Right Wall
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, roomSize, roomHeight / 2f, 0f)
            Matrix.rotateM(modelMatrix, 0, 90f, 0f, 0f, 1f)
            Matrix.scaleM(modelMatrix, 0, roomHeight / 2f, 1f, roomSize)
            wallRight.updateUVs(1f)
            wallRight.draw(vPMatrix, modelMatrix, floatArrayOf(0.5f, 0.5f, 0.5f, 1.0f), wallTextures[2], lightPos, 0.2f, true)

            // Top Wall (Ceiling)
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, 0f, roomHeight, 0f)
            Matrix.rotateM(modelMatrix, 0, 180f, 1f, 0f, 0f)
            Matrix.scaleM(modelMatrix, 0, roomSize, 1f, roomSize)
            wallTop.updateUVs(1f)
            wallTop.draw(vPMatrix, modelMatrix, floatArrayOf(0.3f, 0.3f, 0.3f, 1.0f), wallTextures[3], lightPos, 0.1f, true)
        }
    }
}
