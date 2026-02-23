package com.bass.bumpdesk

import android.opengl.Matrix
import kotlin.math.ceil
import kotlin.math.sqrt

class OverlayRenderer(private val shader: DefaultShader) {
    private val folderBgPlane = Plane(shader)
    private val modelMatrix = FloatArray(16)

    data class FolderUIData(val halfDimX: Float, val halfDimZ: Float, val pos: FloatArray)

    fun drawFolderUI(vPMatrix: FloatArray, pile: Pile, closeBtnTextureId: Int, nameTextureId: Int, lightPos: FloatArray) {
        val data = getConstrainedFolderUI(pile)
        val uiX = data.pos[0]; val uiZ = data.pos[2]

        // Background
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, uiX, 2.90f, uiZ)
        Matrix.scaleM(modelMatrix, 0, data.halfDimX, 1f, data.halfDimZ)
        folderBgPlane.draw(vPMatrix, modelMatrix, floatArrayOf(0.1f, 0.1f, 0.1f, 0.8f), -1, lightPos, 1.0f, false)

        // Title
        if (nameTextureId != -1) {
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, uiX, 2.91f, uiZ - data.halfDimZ + 0.3f * pile.scale)
            Matrix.scaleM(modelMatrix, 0, data.halfDimX * 0.8f, 1f, 0.25f * pile.scale)
            folderBgPlane.draw(vPMatrix, modelMatrix, floatArrayOf(1f, 1f, 1f, 1.0f), nameTextureId, lightPos, 1.0f, false)
        }

        // Close Button
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, uiX + data.halfDimX - 0.2f * pile.scale, 2.91f, uiZ - data.halfDimZ + 0.2f * pile.scale)
        Matrix.scaleM(modelMatrix, 0, 0.2f * pile.scale, 1f, 0.2f * pile.scale)
        folderBgPlane.draw(vPMatrix, modelMatrix, floatArrayOf(0.8f, 0.2f, 0.2f, 1.0f), closeBtnTextureId, lightPos, 1.0f, false)
    }

    fun drawPaginationUI(vPMatrix: FloatArray, pile: Pile, arrowLeftId: Int, arrowRightId: Int, lightPos: FloatArray) {
        val data = getConstrainedFolderUI(pile)
        val uiX = data.pos[0]; val uiZ = data.pos[2]
        val totalPages = ceil(pile.items.size.toFloat() / 16f).toInt().coerceAtLeast(1)
        val currentPage = pile.scrollIndex

        // Pagination Arrows
        if (arrowLeftId != -1 && currentPage > 0) {
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, uiX - 1.5f * pile.scale, 2.91f, uiZ + data.halfDimZ - 0.4f * pile.scale)
            Matrix.scaleM(modelMatrix, 0, 0.25f * pile.scale, 1f, 0.25f * pile.scale)
            folderBgPlane.draw(vPMatrix, modelMatrix, floatArrayOf(1f, 1f, 1f, 1.0f), arrowLeftId, lightPos, 1.0f, false)
        }

        if (arrowRightId != -1 && currentPage < totalPages - 1) {
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, uiX + 1.5f * pile.scale, 2.91f, uiZ + data.halfDimZ - 0.4f * pile.scale)
            Matrix.scaleM(modelMatrix, 0, 0.25f * pile.scale, 1f, 0.25f * pile.scale)
            folderBgPlane.draw(vPMatrix, modelMatrix, floatArrayOf(1f, 1f, 1f, 1.0f), arrowRightId, lightPos, 1.0f, false)
        }

        // Pagination Dots
        val dotSpacing = 0.3f * pile.scale
        val startX = uiX - ((totalPages - 1) * dotSpacing) / 2f
        for (i in 0 until totalPages) {
            val isCurrent = i == currentPage
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, startX + i * dotSpacing, 2.91f, uiZ + data.halfDimZ - 0.4f * pile.scale)
            Matrix.scaleM(modelMatrix, 0, 0.08f * pile.scale, 1f, 0.08f * pile.scale)
            val color = if (isCurrent) floatArrayOf(1f, 1f, 1f, 1f) else floatArrayOf(0.5f, 0.5f, 0.5f, 0.6f)
            folderBgPlane.draw(vPMatrix, modelMatrix, color, -1, lightPos, 1.0f, false)
        }
    }

    fun drawRecentsOverlay(vPMatrix: FloatArray, pile: Pile, arrowLeftTextureId: Int, arrowRightTextureId: Int, lightPos: FloatArray) {
        val width = 6f * pile.scale
        val height = 4f * pile.scale
        
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, pile.position[0], pile.position[1], pile.position[2] - 0.05f)
        Matrix.rotateM(modelMatrix, 0, 90f, 1f, 0f, 0f)
        Matrix.scaleM(modelMatrix, 0, width, 1f, height)
        folderBgPlane.draw(vPMatrix, modelMatrix, floatArrayOf(0.1f, 0.1f, 0.1f, 0.7f), -1, lightPos, 1.0f, false)

        drawArrow(vPMatrix, pile.position[0] - width + 0.5f, pile.position[1], pile.position[2] + 0.05f, arrowLeftTextureId, lightPos)
        drawArrow(vPMatrix, pile.position[0] + width - 0.5f, pile.position[1], pile.position[2] + 0.05f, arrowRightTextureId, lightPos)
    }

    private fun drawArrow(vPMatrix: FloatArray, x: Float, y: Float, z: Float, textureId: Int, lightPos: FloatArray) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, x, y, z)
        Matrix.rotateM(modelMatrix, 0, 90f, 1f, 0f, 0f)
        Matrix.scaleM(modelMatrix, 0, 0.4f, 1f, 0.4f)
        folderBgPlane.draw(vPMatrix, modelMatrix, floatArrayOf(1f, 1f, 1f, 1f), textureId, lightPos, 1.0f, false)
    }

    fun getConstrainedFolderUI(pile: Pile): FolderUIData {
        // Fixed 4x4 Grid dimensions
        val spacing = 2.0f
        val sideDim = 4 * spacing
        val halfDimX = (sideDim / 2f + 0.5f) * pile.scale
        val halfDimZ = (sideDim / 2f + 1.0f) * pile.scale // Extra room for pagination at bottom
        
        val uiX = pile.position[0].coerceIn(-50f + halfDimX, 50f - halfDimX)
        val uiZ = pile.position[2].coerceIn(-50f + halfDimZ, 50f - halfDimZ)
        return FolderUIData(halfDimX, halfDimZ, floatArrayOf(uiX, 2.90f, uiZ))
    }
}
