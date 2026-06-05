package com.bass.bumpdesk

import android.opengl.Matrix

class UIRenderer(
    private val shader: DefaultShader,
    private val overlayRenderer: OverlayRenderer
) {
    private val searchPlane = Plane(shader)
    private val modelMatrix = FloatArray(16)
    private var lastQuery = ""
    private var searchTextureId = -1

    fun drawOverlays(
        vPMatrix: FloatArray,
        sceneState: SceneState,
        camera: CameraManager,
        textures: UIAssets,
        lightPos: FloatArray,
        searchQuery: String,
        textureManager: TextureManager,
        roomHalfX: Float,
        roomHalfZ: Float,
        roomSize: Float = roomHalfX,
    ) {
        // Draw Search Overlay if active
        if (searchQuery.isNotEmpty()) {
            drawSearchQuery(vPMatrix, searchQuery, textureManager, camera)
        }

        sceneState.recentsPile?.reconcilePinnedOpenState()

        val activePile = FolderDrawerStyle.resolveOverlayPile(sceneState.piles, camera.currentViewMode)
        if (activePile != null) {
            ensureFolderTitle(activePile, textureManager)
            overlayRenderer.drawFolderUI(
                vPMatrix,
                activePile,
                textures.closeBtn,
                activePile.nameTextureId,
                lightPos,
                roomHalfX = roomHalfX,
                roomHalfZ = roomHalfZ,
                roomSize = roomSize,
            )
            overlayRenderer.drawPaginationUI(
                vPMatrix,
                activePile,
                textures.arrowLeft,
                textures.arrowRight,
                lightPos,
                roomHalfX = roomHalfX,
                roomHalfZ = roomHalfZ,
                roomSize = roomSize,
            )
        }
    }

    private fun drawSearchQuery(vPMatrix: FloatArray, query: String, textureManager: TextureManager, camera: CameraManager) {
        if (query != lastQuery) {
            lastQuery = query
            val bitmap = TextRenderer.createTextBitmap("Find: $query", 512, 128)
            searchTextureId = textureManager.loadTextureFromBitmap(bitmap)
            bitmap.recycle()
        }

        if (searchTextureId != -1) {
            Matrix.setIdentityM(modelMatrix, 0)
            // Position in center of view, slightly in front of camera
            val posX = camera.currentLookAt[0]
            val posY = camera.currentLookAt[1] + 2f
            val posZ = camera.currentLookAt[2]
            
            Matrix.translateM(modelMatrix, 0, posX, posY, posZ)
            // Face the camera
            Matrix.rotateM(modelMatrix, 0, 180f, 0f, 1f, 0f)
            Matrix.rotateM(modelMatrix, 0, 90f, 1f, 0f, 0f)
            Matrix.scaleM(modelMatrix, 0, 4f, 1f, 1f)
            
            val freshnessColor = ThemeManager.getFreshnessColor()
            searchPlane.draw(vPMatrix, modelMatrix, floatArrayOf(freshnessColor[0], freshnessColor[1], freshnessColor[2], 0.9f), searchTextureId, floatArrayOf(0f, 10f, 0f), 1.0f, false)
        }
    }

    data class UIAssets(
        val closeBtn: Int,
        val arrowLeft: Int,
        val arrowRight: Int,
        val scrollUp: Int,
        val scrollDown: Int
    )

    private fun ensureFolderTitle(pile: Pile, textureManager: TextureManager) {
        if (pile.nameTextureId != -1) return
        val bitmap = if (FolderDrawerStyle.isMaterialDrawer(pile)) {
            TextRenderer.createMaterialTitleBitmap(pile.name)
        } else {
            TextRenderer.createTextBitmap(pile.name, 256, 64)
        }
        pile.nameTextureId = textureManager.loadTextureFromBitmap(bitmap)
        bitmap.recycle()
    }
}
