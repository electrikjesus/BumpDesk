package com.bass.bumpdesk

import android.graphics.Color
import android.opengl.Matrix

class OverlayRenderer(private val shader: DefaultShader) {
    private val folderBgPlane = Plane(shader)
    private val modelMatrix = FloatArray(16)
    private var panelTextureId = -1

    fun drawFolderUI(
        vPMatrix: FloatArray,
        pile: Pile,
        closeBtnTextureId: Int,
        nameTextureId: Int,
        lightPos: FloatArray,
        roomHalfX: Float,
        roomHalfZ: Float,
    ) {
        val data = FolderDrawerStyle.layout(pile, roomHalfX, roomHalfZ)
        val uiX = data.pos[0]
        val uiZ = data.pos[2]
        val material = FolderDrawerStyle.usesMaterialChrome(pile)

        if (material) {
            if (panelTextureId == -1) {
                val panelBitmap = TextRenderer.createRoundedPanelBitmap(
                    fillColor = Color.argb(240, 33, 35, 43),
                )
                panelTextureId = TextRenderer.loadTextTexture(panelBitmap)
                panelBitmap.recycle()
            }
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, uiX, FolderDrawerStyle.PANEL_Y, uiZ)
            Matrix.scaleM(modelMatrix, 0, data.halfDimX, 1f, data.halfDimZ)
            folderBgPlane.draw(
                vPMatrix,
                modelMatrix,
                FolderDrawerStyle.surfaceColor(),
                panelTextureId,
                lightPos,
                1.0f,
                false,
            )
        } else {
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, uiX, FolderDrawerStyle.PANEL_Y, uiZ)
            Matrix.scaleM(modelMatrix, 0, data.halfDimX, 1f, data.halfDimZ)
            folderBgPlane.draw(vPMatrix, modelMatrix, floatArrayOf(0.1f, 0.1f, 0.1f, 0.8f), -1, lightPos, 1.0f, false)
        }

        if (nameTextureId != -1) {
            val title = FolderDrawerStyle.titleBarLayout(data, pile.scale)
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, title[0], FolderDrawerStyle.CHROME_Y, title[1])
            val titleDepth = if (material) FolderDrawerStyle.TITLE_BAND * 0.72f * pile.scale else 0.3f * pile.scale
            Matrix.scaleM(modelMatrix, 0, title[2], 1f, titleDepth)
            folderBgPlane.draw(
                vPMatrix,
                modelMatrix,
                FolderDrawerStyle.onSurfaceColor(),
                nameTextureId,
                lightPos,
                1.0f,
                false,
            )
        }

        val buttonSize = FolderDrawerStyle.touchButtonSize(data.halfDimX, pile.scale)
        val close = FolderDrawerStyle.closeButtonCenter(data, pile.scale)
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, close[0], FolderDrawerStyle.CHROME_Y, close[1])
        Matrix.scaleM(modelMatrix, 0, buttonSize, 1f, buttonSize)
        folderBgPlane.draw(
            vPMatrix,
            modelMatrix,
            if (material) FolderDrawerStyle.buttonContainerColor() else floatArrayOf(0.8f, 0.2f, 0.2f, 1.0f),
            closeBtnTextureId,
            lightPos,
            1.0f,
            false,
        )
    }

    fun drawPaginationUI(
        vPMatrix: FloatArray,
        pile: Pile,
        arrowLeftId: Int,
        arrowRightId: Int,
        lightPos: FloatArray,
        roomHalfX: Float,
        roomHalfZ: Float,
    ) {
        val data = FolderDrawerStyle.layout(pile, roomHalfX, roomHalfZ)
        val totalPages = FolderDrawerStyle.totalPages(pile.items.size)
        val currentPage = pile.scrollIndex
        val material = FolderDrawerStyle.usesMaterialChrome(pile)
        val buttonSize = FolderDrawerStyle.touchButtonSize(data.halfDimX, pile.scale)

        if (arrowLeftId != -1 && currentPage > 0) {
            val prev = FolderDrawerStyle.prevButtonCenter(data, pile.scale)
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, prev[0], FolderDrawerStyle.CHROME_Y, prev[1])
            Matrix.scaleM(modelMatrix, 0, buttonSize, 1f, buttonSize)
            folderBgPlane.draw(
                vPMatrix,
                modelMatrix,
                if (material) FolderDrawerStyle.buttonContainerColor() else floatArrayOf(1f, 1f, 1f, 1f),
                arrowLeftId,
                lightPos,
                1.0f,
                false,
            )
        }

        if (arrowRightId != -1 && currentPage < totalPages - 1) {
            val next = FolderDrawerStyle.nextButtonCenter(data, pile.scale)
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, next[0], FolderDrawerStyle.CHROME_Y, next[1])
            Matrix.scaleM(modelMatrix, 0, buttonSize, 1f, buttonSize)
            folderBgPlane.draw(
                vPMatrix,
                modelMatrix,
                if (material) FolderDrawerStyle.buttonContainerColor() else floatArrayOf(1f, 1f, 1f, 1f),
                arrowRightId,
                lightPos,
                1.0f,
                false,
            )
        }

        for (i in 0 until totalPages) {
            val dot = FolderDrawerStyle.pageIndicatorCenter(data, pile.scale, i, totalPages)
            val isCurrent = i == currentPage
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, dot[0], FolderDrawerStyle.CHROME_Y, dot[1])
            val width = if (isCurrent) 0.24f * pile.scale else 0.12f * pile.scale
            val depth = if (isCurrent) 0.12f * pile.scale else 0.1f * pile.scale
            Matrix.scaleM(modelMatrix, 0, width, 1f, depth)
            val color = if (isCurrent) FolderDrawerStyle.primaryColor() else FolderDrawerStyle.inactiveIndicatorColor()
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

    fun getConstrainedFolderUI(pile: Pile, roomHalfX: Float, roomHalfZ: Float): FolderDrawerStyle.Layout =
        FolderDrawerStyle.layout(pile, roomHalfX, roomHalfZ)
}
