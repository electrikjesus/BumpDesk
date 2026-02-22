package com.bass.bumpdesk

import android.opengl.Matrix
import kotlin.math.ceil
import kotlin.math.sqrt

class OverlayRenderer(private val shader: DefaultShader) {
    private val folderBgPlane = Plane(shader)
    private val modelMatrix = FloatArray(16)

    fun drawFolderUI(vPMatrix: FloatArray, pile: Pile, closeBtnTextureId: Int, nameTextureId: Int, lightPos: FloatArray) {
        val (halfDim, totalHalfDimZ, pos) = getConstrainedFolderUI(pile)
        val uiX = pos[0]; val uiZ = pos[1]

        // Background
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, uiX, 2.98f, uiZ)
        Matrix.scaleM(modelMatrix, 0, halfDim, 1f, totalHalfDimZ)
        folderBgPlane.draw(vPMatrix, modelMatrix, floatArrayOf(0.1f, 0.1f, 0.1f, 0.8f), -1, lightPos, 1.0f, false)

        // Title
        if (nameTextureId != -1) {
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, uiX, 2.99f, uiZ - totalHalfDimZ + 0.3f * pile.scale)
            Matrix.scaleM(modelMatrix, 0, halfDim * 0.8f, 1f, 0.25f * pile.scale)
            folderBgPlane.draw(vPMatrix, modelMatrix, floatArrayOf(1f, 1f, 1f, 1.0f), nameTextureId, lightPos, 1.0f, false)
        }

        // Close Button
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, uiX + halfDim - 0.2f * pile.scale, 2.99f, uiZ - totalHalfDimZ + 0.2f * pile.scale)
        Matrix.scaleM(modelMatrix, 0, 0.2f * pile.scale, 1f, 0.2f * pile.scale)
        folderBgPlane.draw(vPMatrix, modelMatrix, floatArrayOf(0.8f, 0.2f, 0.2f, 1.0f), closeBtnTextureId, lightPos, 1.0f, false)
    }

    fun drawGridScrollButtons(vPMatrix: FloatArray, pile: Pile, scrollUpId: Int, scrollDownId: Int, lightPos: FloatArray) {
        val (halfDim, totalHalfDimZ, pos) = getConstrainedFolderUI(pile)
        val uiX = pos[0]; val uiZ = pos[1]

        // Scroll Up
        if (scrollUpId != -1) {
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, uiX - halfDim + 0.4f * pile.scale, 2.99f, uiZ - totalHalfDimZ + 0.2f * pile.scale)
            Matrix.scaleM(modelMatrix, 0, 0.25f * pile.scale, 1f, 0.25f * pile.scale)
            folderBgPlane.draw(vPMatrix, modelMatrix, floatArrayOf(1f, 1f, 1f, 1.0f), scrollUpId, lightPos, 1.0f, false)
        }

        // Scroll Down
        if (scrollDownId != -1) {
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, uiX - halfDim + 1.0f * pile.scale, 2.99f, uiZ - totalHalfDimZ + 0.2f * pile.scale)
            Matrix.scaleM(modelMatrix, 0, 0.25f * pile.scale, 1f, 0.25f * pile.scale)
            folderBgPlane.draw(vPMatrix, modelMatrix, floatArrayOf(1f, 1f, 1f, 1.0f), scrollDownId, lightPos, 1.0f, false)
        }
    }

    fun drawRecentsOverlay(vPMatrix: FloatArray, pile: Pile, arrowLeftTextureId: Int, arrowRightTextureId: Int, lightPos: FloatArray) {
        val width = 6f * pile.scale
        val height = 4f * pile.scale
        
        // Background - Task: Place BEHIND items (items are at pile.position[2] + zOffset)
        // Background should be further back than the items.
        // Items are at Z = pile.position[2] + 0.01f.
        // Let's put background at Z = pile.position[2] - 0.1f.
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, pile.position[0], pile.position[1], pile.position[2] - 0.1f)
        Matrix.rotateM(modelMatrix, 0, 90f, 1f, 0f, 0f)
        Matrix.scaleM(modelMatrix, 0, width, 1f, height)
        folderBgPlane.draw(vPMatrix, modelMatrix, floatArrayOf(0.1f, 0.1f, 0.1f, 0.7f), -1, lightPos, 1.0f, false)

        // Task: Ensure arrows are in front of items correctly
        // Items are at Z = pile.position[2] + 0.01f.
        // Arrows at Z = pile.position[2] + 0.2f.
        drawArrow(vPMatrix, pile.position[0] - width + 0.5f, pile.position[1], pile.position[2] + 0.2f, arrowLeftTextureId, lightPos)
        drawArrow(vPMatrix, pile.position[0] + width - 0.5f, pile.position[1], pile.position[2] + 0.2f, arrowRightTextureId, lightPos)
    }

    private fun drawArrow(vPMatrix: FloatArray, x: Float, y: Float, z: Float, textureId: Int, lightPos: FloatArray) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, x, y, z)
        Matrix.rotateM(modelMatrix, 0, 90f, 1f, 0f, 0f)
        Matrix.scaleM(modelMatrix, 0, 0.4f, 1f, 0.4f)
        folderBgPlane.draw(vPMatrix, modelMatrix, floatArrayOf(1f, 1f, 1f, 1f), textureId, lightPos, 1.0f, false)
    }

    fun getConstrainedFolderUI(pile: Pile): Triple<Float, Float, FloatArray> {
        val count = pile.items.size
        val side = ceil(sqrt(count.toDouble())).toInt().coerceAtLeast(1)
        val spacing = 1.2f
        val sideDim = side * spacing
        val halfDim = (sideDim / 2f + 0.2f) * pile.scale
        val totalHalfDimZ = (sideDim / 2f + 0.6f) * pile.scale
        val uiX = pile.position[0].coerceIn(-50f + halfDim, 50f - halfDim)
        val uiZ = pile.position[2].coerceIn(-50f + totalHalfDimZ, 50f - totalHalfDimZ)
        return Triple(halfDim, totalHalfDimZ, floatArrayOf(uiX, uiZ))
    }
}
