package com.bass.bumpdesk

import android.content.Context
import android.opengl.Matrix

class PileRenderer(
    private val context: Context,
    private val shader: DefaultShader,
    private val textureManager: TextureManager,
    private val itemRenderer: ItemRenderer,
    private val overlayRenderer: OverlayRenderer,
    private val sceneState: SceneState
) {
    fun drawPiles(
        vPMatrix: FloatArray,
        piles: List<Pile>,
        lightPos: FloatArray,
        searchQuery: String,
        currentViewMode: CameraManager.ViewMode,
        onUpdateTexture: (Runnable) -> Unit
    ) {
        piles.forEach { pile ->
            val isExpanded = pile.isExpanded
            val isCarousel = pile.layoutMode == Pile.LayoutMode.CAROUSEL && !isExpanded
            val widthLimit = 6f * pile.scale

            pile.items.forEachIndexed { index, item ->
                if (isCarousel) {
                    // Task: Position items in carousel relative to pile position and currentIndex
                    val offset = (index - pile.currentIndex).toFloat()
                    val targetX = pile.position[0] + offset * 2.0f * pile.scale
                    
                    // Smoothly animate towards target position
                    item.position[0] += (targetX - item.position[0]) * 0.1f
                    item.position[1] = pile.position[1]
                    item.position[2] = pile.position[2]
                    
                    // Basic culling for carousel mode
                    if (Math.abs(item.position[0] - pile.position[0]) > widthLimit) {
                        return@forEachIndexed
                    }
                }
                
                itemRenderer.drawItems(vPMatrix, listOf(item), lightPos, searchQuery, onUpdateTexture)
            }
        }
    }
}
