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
) {
    enum class LayoutMode { STACK, GRID, CAROUSEL, FOLDER }

    /** Collapsed floor pile shown as a single 2×2 app preview (no labels). */
    fun showsFolderPreview(): Boolean =
        !isExpanded && layoutMode == LayoutMode.FOLDER && !isSystem && surface == BumpItem.Surface.FLOOR

    /** Folder/group piles show their name under the collapsed 2×2 preview. */
    fun showsFolderLabel(): Boolean = showsFolderPreview() && name.isNotBlank()
}
