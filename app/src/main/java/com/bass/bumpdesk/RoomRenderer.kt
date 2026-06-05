package com.bass.bumpdesk

import android.opengl.GLES20
import android.opengl.Matrix

class RoomRenderer(private val shader: DefaultShader) {
    private val floor = Plane(shader)
    private val wallBack = Plane(shader)
    private val wallFront = Plane(shader)
    private val wallLeft = Plane(shader)
    private val wallRight = Plane(shader)
    private val wallTop = Plane(shader)
    
    private val modelMatrix = FloatArray(16)

    fun draw(
        vPMatrix: FloatArray, 
        floorTexture: Int, 
        wallTextures: IntArray, 
        lightPos: FloatArray, 
        isInfiniteMode: Boolean = false,
        roomSize: Float = 30f,
        roomHeight: Float = 30f,
        floorHalfWidth: Float = roomSize,
        floorHalfDepth: Float = roomSize,
        time: Float = 0f,
        isAnimated: Boolean = false
    ) {
        // Floor - Fill the entire plane
        Matrix.setIdentityM(modelMatrix, 0)
        if (isInfiniteMode) {
            Matrix.scaleM(modelMatrix, 0, 100f, 1f, 100f)
        } else {
            Matrix.scaleM(modelMatrix, 0, floorHalfWidth, 1f, floorHalfDepth)
        }
        floor.updateUVs(1.0f, 1.0f)
        floor.draw(vPMatrix, modelMatrix, floatArrayOf(0.4f, 0.4f, 0.4f, 1.0f), floorTexture, lightPos, 0.3f, true, time, isAnimated)
        
        if (!isInfiniteMode) {
            GLES20.glEnable(GLES20.GL_CULL_FACE)
            GLES20.glCullFace(GLES20.GL_BACK)

            // Walls - Always stretch (1.0f, 1.0f) UVs to cover the geometry
            val wallUVScaleX = 1.0f
            val wallUVScaleY = 1.0f

            // Back Wall
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, 0f, roomHeight / 2f, -floorHalfDepth)
            Matrix.rotateM(modelMatrix, 0, 90f, 1f, 0f, 0f)
            Matrix.scaleM(modelMatrix, 0, floorHalfWidth, 1f, roomHeight / 2f)
            wallBack.updateUVs(wallUVScaleX, wallUVScaleY)
            wallBack.draw(vPMatrix, modelMatrix, floatArrayOf(0.5f, 0.5f, 0.5f, 1.0f), wallTextures[0], lightPos, 0.2f, true, time, isAnimated)
            
            // Front Wall
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, 0f, roomHeight / 2f, floorHalfDepth)
            Matrix.rotateM(modelMatrix, 0, -90f, 1f, 0f, 0f)
            Matrix.scaleM(modelMatrix, 0, floorHalfWidth, 1f, roomHeight / 2f)
            wallFront.updateUVs(wallUVScaleX, wallUVScaleY)
            wallFront.draw(vPMatrix, modelMatrix, floatArrayOf(0.5f, 0.5f, 0.5f, 1.0f), wallTextures[0], lightPos, 0.2f, true, time, isAnimated)
            
            // Left Wall
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, -floorHalfWidth, roomHeight / 2f, 0f)
            Matrix.rotateM(modelMatrix, 0, -90f, 0f, 0f, 1f)
            Matrix.scaleM(modelMatrix, 0, roomHeight / 2f, 1f, floorHalfDepth)
            wallLeft.updateUVs(wallUVScaleX, wallUVScaleY)
            wallLeft.draw(vPMatrix, modelMatrix, floatArrayOf(0.5f, 0.5f, 0.5f, 1.0f), wallTextures[1], lightPos, 0.2f, true, time, isAnimated)
            
            // Right Wall
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, floorHalfWidth, roomHeight / 2f, 0f)
            Matrix.rotateM(modelMatrix, 0, 90f, 0f, 0f, 1f)
            Matrix.scaleM(modelMatrix, 0, roomHeight / 2f, 1f, floorHalfDepth)
            wallRight.updateUVs(wallUVScaleX, wallUVScaleY)
            wallRight.draw(vPMatrix, modelMatrix, floatArrayOf(0.5f, 0.5f, 0.5f, 1.0f), wallTextures[2], lightPos, 0.2f, true, time, isAnimated)

            // Top Wall (Ceiling)
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, 0f, roomHeight, 0f)
            Matrix.rotateM(modelMatrix, 0, 180f, 1f, 0f, 0f)
            Matrix.scaleM(modelMatrix, 0, floorHalfWidth, 1f, floorHalfDepth)
            wallTop.updateUVs(1.0f, 1.0f)
            wallTop.draw(vPMatrix, modelMatrix, floatArrayOf(0.3f, 0.3f, 0.3f, 1.0f), wallTextures[3], lightPos, 0.1f, true, time, isAnimated)

            GLES20.glDisable(GLES20.GL_CULL_FACE)
        }
    }
}
