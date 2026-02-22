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
            
            // Task: Increased width limit for carousel visibility.
            // Items are spaced at 3.5f * scale. 
            // 8f * scale allows approx 2 items on each side of the center.
            val widthLimit = 10f * pile.scale

            pile.items.forEachIndexed { index, item ->
                // Task: Removed duplicate positioning logic. 
                // Positioning is now handled exclusively by PhysicsEngine.
                
                if (isCarousel) {
                    // Basic culling for carousel mode to save draw calls.
                    // We check distance along the major axis of the carousel.
                    val dist = when (pile.surface) {
                        BumpItem.Surface.BACK_WALL -> Math.abs(item.position[0] - pile.position[0])
                        BumpItem.Surface.LEFT_WALL -> Math.abs(item.position[2] - pile.position[2])
                        BumpItem.Surface.RIGHT_WALL -> Math.abs(item.position[2] - pile.position[2])
                        else -> Math.abs(item.position[0] - pile.position[0])
                    }
                    
                    if (dist > widthLimit) {
                        return@forEachIndexed
                    }
                }
                
                itemRenderer.drawItems(vPMatrix, listOf(item), lightPos, searchQuery, onUpdateTexture)
            }
        }
    }
}
