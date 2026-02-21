package com.bass.bumpdesk

import android.opengl.Matrix

class UIRenderer(
    private val shader: DefaultShader,
    private val overlayRenderer: OverlayRenderer
) {
    fun drawOverlays(
        vPMatrix: FloatArray,
        sceneState: SceneState,
        camera: CameraManager,
        textures: UIAssets,
        lightPos: FloatArray
    ) {
        val activePile = sceneState.piles.find { it.isExpanded }
        if (camera.currentViewMode == CameraManager.ViewMode.FOLDER_EXPANDED && activePile != null) {
            overlayRenderer.drawFolderUI(vPMatrix, activePile, textures.closeBtn, activePile.nameTextureId, lightPos)
            if (activePile.layoutMode == Pile.LayoutMode.GRID) {
                overlayRenderer.drawGridScrollButtons(vPMatrix, activePile, textures.scrollUp, textures.scrollDown, lightPos)
            }
        }
        
        sceneState.recentsPile?.let { 
            overlayRenderer.drawRecentsOverlay(vPMatrix, it, textures.arrowLeft, textures.arrowRight, lightPos) 
        }
    }

    data class UIAssets(
        val closeBtn: Int,
        val arrowLeft: Int,
        val arrowRight: Int,
        val scrollUp: Int,
        val scrollDown: Int
    )
}
