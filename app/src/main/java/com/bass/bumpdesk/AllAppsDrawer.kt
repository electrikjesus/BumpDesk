package com.bass.bumpdesk

/**
 * Ephemeral floor All Apps drawer (system pile + launcher icon).
 * Keeps expand/collapse state consistent so the drawer cannot get stuck without chrome.
 */
object AllAppsDrawer {

    const val PILE_NAME = "All Apps"

    fun findPile(sceneState: SceneState): Pile? =
        sceneState.piles.find { it.isSystem && it.name == PILE_NAME }

    fun isOpen(sceneState: SceneState): Boolean =
        findPile(sceneState)?.layoutAsExpandedDrawer() == true

    fun hasPile(sceneState: SceneState): Boolean = findPile(sceneState) != null

    /** Hide the floor launcher tile while the ephemeral drawer pile exists. */
    fun visibleBumpItems(sceneState: SceneState): List<BumpItem> {
        if (!hasPile(sceneState)) return sceneState.bumpItems
        return sceneState.bumpItems.filter { it.appearance.type != BumpItem.Type.APP_DRAWER }
    }

    fun removePile(sceneState: SceneState, syncDrawerPosition: Boolean = true) {
        val pile = findPile(sceneState) ?: return
        if (syncDrawerPosition) {
            sceneState.appDrawerItem?.transform?.position = pile.position.copy(y = 0.05f)
        }
        pile.isExpanded = false
        sceneState.piles.remove(pile)
    }

    /** All Apps is only valid while the folder camera is active. */
    fun reconcile(sceneState: SceneState, viewMode: CameraManager.ViewMode): Boolean {
        val pile = findPile(sceneState) ?: return false
        if (viewMode == CameraManager.ViewMode.FOLDER_EXPANDED) return false
        removePile(sceneState)
        return true
    }

    fun removeIfEmpty(sceneState: SceneState): Boolean {
        val pile = findPile(sceneState) ?: return false
        if (pile.items.isNotEmpty()) return false
        removePile(sceneState)
        return true
    }

    fun clearForCollapse(sceneState: SceneState) {
        removePile(sceneState, syncDrawerPosition = false)
    }
}
