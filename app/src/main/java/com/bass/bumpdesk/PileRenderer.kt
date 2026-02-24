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
    private val ITEMS_PER_PAGE = 16

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
            
            if (isExpanded) {
                // Performance: Virtualized rendering for expanded folders.
                val startIdx = pile.scrollIndex * ITEMS_PER_PAGE
                val endIdx = (startIdx + ITEMS_PER_PAGE).coerceAtMost(pile.items.size)
                
                // Task: Clear texture IDs for items on non-visible pages to allow 
                // TextureManager's LRU cache to manage memory effectively.
                // We keep a 1-page buffer for smoother scrolling.
                val bufferRange = (pile.scrollIndex - 1) * ITEMS_PER_PAGE until (pile.scrollIndex + 2) * ITEMS_PER_PAGE
                
                pile.items.forEachIndexed { index, item ->
                    if (index in startIdx until endIdx) {
                        itemRenderer.drawItems(vPMatrix, listOf(item), lightPos, searchQuery, onUpdateTexture)
                    } else if (index !in bufferRange) {
                        // Mark texture as eligible for eviction if far from visible page
                        item.appearance.textureId = -1
                    }
                }
                return@forEach
            }

            val isCarousel = pile.layoutMode == Pile.LayoutMode.CAROUSEL
            val widthLimit = 10f * pile.scale

            pile.items.forEachIndexed { index, item ->
                if (isCarousel) {
                    val dist = when (pile.surface) {
                        BumpItem.Surface.BACK_WALL -> Math.abs(item.transform.position.x - pile.position.x)
                        BumpItem.Surface.LEFT_WALL -> Math.abs(item.transform.position.z - pile.position.z)
                        BumpItem.Surface.RIGHT_WALL -> Math.abs(item.transform.position.z - pile.position.z)
                        else -> Math.abs(item.transform.position.x - pile.position.x)
                    }
                    
                    if (dist > widthLimit) {
                        // Mark non-visible carousel items for texture eviction
                        item.appearance.textureId = -1
                        return@forEachIndexed
                    }
                }
                
                itemRenderer.drawItems(vPMatrix, listOf(item), lightPos, searchQuery, onUpdateTexture)
            }
        }
    }
}
