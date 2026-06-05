package com.bass.bumpdesk

data class Pile(
    val items: MutableList<BumpItem> = mutableListOf(),
    var position: Vector3 = Vector3(0f, 0.05f, 0f),
    var isExpanded: Boolean = false,
    var isFannedOut: Boolean = false,
    var name: String = "Folder",
    var nameTextureId: Int = -1,
    var layoutMode: LayoutMode = LayoutMode.STACK,
    var surface: BumpItem.Surface = BumpItem.Surface.FLOOR,
    var scale: Float = 1.0f,
    var isSystem: Boolean = false,
    var currentIndex: Int = 0, // For CAROUSEL or LEAFING navigation
    var scrollIndex: Int = 0, // For gridded scroll
    var previewTextureId: Int = -1,
    var previewSignature: String = "",
    /** True while the pinned/desktop drawer is being dragged as a unit. */
    var isDraggingOnDesktop: Boolean = false,
) {
    enum class LayoutMode { STACK, GRID, CAROUSEL, FOLDER }

    enum class RecentsViewMode { ICONS, TASK_CARDS }

    var recentsViewMode: RecentsViewMode = RecentsViewMode.ICONS
    /** When true, Recents stays expanded at the normal camera view (floor or wall). */
    var isPinnedOpen: Boolean = false
    /** Pinned Recents drawer grid width in cells (1–12). */
    var drawerGridColumns: Int = 2
    /** Pinned Recents drawer grid height in cells (1–4). */
    var drawerGridRows: Int = 2

    /** Collapsed floor pile shown as a single 2×2 app preview (no labels). */
    fun showsFolderPreview(): Boolean =
        !isExpanded && layoutMode == LayoutMode.FOLDER && !isSystem && surface == BumpItem.Surface.FLOOR

    /** Folder/group piles show their name under the collapsed 2×2 preview. */
    fun showsFolderLabel(): Boolean = showsFolderPreview() && name.isNotBlank()

    fun isRecentsPile(): Boolean = isSystem && name == "Recents"

    fun recentsOnWall(): Boolean =
        surface == BumpItem.Surface.BACK_WALL ||
            surface == BumpItem.Surface.LEFT_WALL ||
            surface == BumpItem.Surface.RIGHT_WALL

    fun showsRecentsMaterialDrawer(): Boolean =
        isRecentsPile() && layoutAsExpandedDrawer() &&
            (surface == BumpItem.Surface.FLOOR || recentsOnWall())

    fun showsRecentsIconGrid(): Boolean =
        showsRecentsMaterialDrawer() && recentsViewMode == RecentsViewMode.ICONS

    fun showsRecentsTaskCards(): Boolean =
        showsRecentsMaterialDrawer() && recentsViewMode == RecentsViewMode.TASK_CARDS

    fun showsDesktopPinnedDrawer(): Boolean =
        isPinnedOpen && layoutMode == LayoutMode.FOLDER &&
            (surface == BumpItem.Surface.FLOOR || recentsOnWall())

    /** Expanded drawer grid (temporary expand or pinned open). */
    fun layoutAsExpandedDrawer(): Boolean =
        isExpanded || showsDesktopPinnedDrawer()

    /** Collapsed preview tile (user folders or Recents). */
    fun showsCollapsedPreview(): Boolean =
        showsFolderPreview() ||
            (isRecentsPile() && !layoutAsExpandedDrawer())

    fun showsCollapsedLabel(): Boolean =
        (showsFolderPreview() && name.isNotBlank()) ||
            (isRecentsPile() && showsCollapsedPreview())

    /** Keeps pinned-open recents expanded after global collapse passes. */
    fun reconcilePinnedOpenState() {
        if (!isPinnedOpen) return
        isExpanded = true
        if (isRecentsPile()) {
            layoutMode = LayoutMode.FOLDER
        }
    }

    /** Drawer/panel scale (spacing, chrome). Icon sizes inside use [PhysicsEngine] defaults for recents. */
    fun usesIndependentDrawerScale(): Boolean = isRecentsPile()
}
