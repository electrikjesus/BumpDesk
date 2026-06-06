package com.bass.bumpdesk

/**
 * Keeps user folder expand state in sync with the folder camera.
 * When the view leaves [CameraManager.ViewMode.FOLDER_EXPANDED], temporary
 * expanded folders collapse so they cannot render without chrome (close button).
 */
object ExpandedFolderDrawer {

    fun isOpen(sceneState: SceneState): Boolean =
        sceneState.piles.any { shouldCollapseWhenLeavingFolderFocus(it) && it.layoutAsExpandedDrawer() }

    /** Collapse orphaned expanded folders when the folder camera is not active. */
    fun reconcile(sceneState: SceneState, viewMode: CameraManager.ViewMode): Boolean {
        if (viewMode == CameraManager.ViewMode.FOLDER_EXPANDED) return false
        var changed = false
        sceneState.piles.forEach { pile ->
            if (shouldCollapseWhenLeavingFolderFocus(pile) && pile.isExpanded) {
                pile.isExpanded = false
                changed = true
            } else if (pile.isRecentsPile() && pile.isPinnedOpen) {
                pile.reconcilePinnedOpenState()
            }
        }
        return changed
    }

    private fun shouldCollapseWhenLeavingFolderFocus(pile: Pile): Boolean =
        !pile.showsDesktopPinnedDrawer()
}
