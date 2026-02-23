package com.bass.bumpdesk

import android.opengl.Matrix
import kotlin.math.ceil
import kotlin.math.sqrt

class OverlayRenderer(private val shader: DefaultShader) {
    private val folderBgPlane = Plane(shader)
    private val modelMatrix = FloatArray(16)

    data class FolderUIData(val halfDim: Float, val totalHalfDimZ: Float, val halfDimY: Float, val pos: FloatArray)

    fun drawFolderUI(vPMatrix: FloatArray, pile: Pile, closeBtnTextureId: Int, nameTextureId: Int, lightPos: FloatArray) {
        val data = getConstrainedFolderUI(pile)
        val uiX = data.pos[0]; val uiZ = data.pos[2]

        // Background - lowered to 2.90f to avoid intersection with icons at 3.05f
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, uiX, 2.90f, uiZ)
        Matrix.scaleM(modelMatrix, 0, data.halfDim, 1f, data.totalHalfDimZ)
        folderBgPlane.draw(vPMatrix, modelMatrix, floatArrayOf(0.1f, 0.1f, 0.1f, 0.8f), -1, lightPos, 1.0f, false)

        // Title
        if (nameTextureId != -1) {
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, uiX, 2.91f, uiZ - data.totalHalfDimZ + 0.3f * pile.scale)
            Matrix.scaleM(modelMatrix, 0, data.halfDim * 0.8f, 1f, 0.25f * pile.scale)
            folderBgPlane.draw(vPMatrix, modelMatrix, floatArrayOf(1f, 1f, 1f, 1.0f), nameTextureId, lightPos, 1.0f, false)
        }

        // Close Button
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, uiX + data.halfDim - 0.2f * pile.scale, 2.91f, uiZ - data.totalHalfDimZ + 0.2f * pile.scale)
        Matrix.scaleM(modelMatrix, 0, 0.2f * pile.scale, 1f, 0.2f * pile.scale)
        folderBgPlane.draw(vPMatrix, modelMatrix, floatArrayOf(0.8f, 0.2f, 0.2f, 1.0f), closeBtnTextureId, lightPos, 1.0f, false)
    }

    fun drawGridScrollButtons(vPMatrix: FloatArray, pile: Pile, scrollUpId: Int, scrollDownId: Int, lightPos: FloatArray) {
        val data = getConstrainedFolderUI(pile)
        val uiX = data.pos[0]; val uiZ = data.pos[2]

        // Scroll Up
        if (scrollUpId != -1) {
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, uiX - data.halfDim + 0.4f * pile.scale, 2.91f, uiZ - data.totalHalfDimZ + 0.2f * pile.scale)
            Matrix.scaleM(modelMatrix, 0, 0.25f * pile.scale, 1f, 0.25f * pile.scale)
            folderBgPlane.draw(vPMatrix, modelMatrix, floatArrayOf(1f, 1f, 1f, 1.0f), scrollUpId, lightPos, 1.0f, false)
        }

        // Scroll Down
        if (scrollDownId != -1) {
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, uiX - data.halfDim + 1.0f * pile.scale, 2.91f, uiZ - data.totalHalfDimZ + 0.2f * pile.scale)
            Matrix.scaleM(modelMatrix, 0, 0.25f * pile.scale, 1f, 0.25f * pile.scale)
            folderBgPlane.draw(vPMatrix, modelMatrix, floatArrayOf(1f, 1f, 1f, 1.0f), scrollDownId, lightPos, 1.0f, false)
        }
    }

    fun drawRecentsOverlay(vPMatrix: FloatArray, pile: Pile, arrowLeftTextureId: Int, arrowRightTextureId: Int, lightPos: FloatArray) {
        val width = 6f * pile.scale
        val height = 4f * pile.scale
        
        // Background - moved slightly forward to z = -9.45f (Wall is at -9.95f, pile items at -9.4f)
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, pile.position[0], pile.position[1], pile.position[2] - 0.05f)
        Matrix.rotateM(modelMatrix, 0, 90f, 1f, 0f, 0f)
        Matrix.scaleM(modelMatrix, 0, width, 1f, height)
        folderBgPlane.draw(vPMatrix, modelMatrix, floatArrayOf(0.1f, 0.1f, 0.1f, 0.7f), -1, lightPos, 1.0f, false)

        // Arrows - moved slightly forward to z = -9.35f
        drawArrow(vPMatrix, pile.position[0] - width + 0.5f, pile.position[1], pile.position[2] + 0.05f, arrowLeftTextureId, lightPos)
        drawArrow(vPMatrix, pile.position[0] + width - 0.5f, pile.position[1], pile.position[2] + 0.05f, arrowRightTextureId, lightPos)
    }

    private fun drawArrow(vPMatrix: FloatArray, x: Float, y: Float, z: Float, textureId: Int, lightPos: FloatArray) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, x, y, z)
        Matrix.rotateM(modelMatrix, 0, 90f, 1f, 0f, 0f)
        Matrix.scaleM(modelMatrix, 0, 0.4f, 1f, 0.4f)
        // Ensure white square fix: check if textureId is valid
        folderBgPlane.draw(vPMatrix, modelMatrix, floatArrayOf(1f, 1f, 1f, 1f), textureId, lightPos, 1.0f, false)
    }

    fun getConstrainedFolderUI(pile: Pile): FolderUIData {
        val count = pile.items.size
        val side = ceil(sqrt(count.toDouble())).toInt().coerceAtLeast(1)
        val spacing = 1.2f
        val sideDim = side * spacing
        val halfDim = (sideDim / 2f + 0.2f) * pile.scale
        val totalHalfDimZ = (sideDim / 2f + 0.6f) * pile.scale
        val halfDimY = 0.01f 
        val uiX = pile.position[0].coerceIn(-50f + halfDim, 50f - halfDim)
        val uiZ = pile.position[2].coerceIn(-50f + totalHalfDimZ, 50f - totalHalfDimZ)
        return FolderUIData(halfDim, totalHalfDimZ, halfDimY, floatArrayOf(uiX, 2.90f, uiZ))
    }
}
