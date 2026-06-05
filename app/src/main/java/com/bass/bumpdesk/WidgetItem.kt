package com.bass.bumpdesk

data class WidgetItem(
    val appWidgetId: Int,
    var position: Vector3 = Vector3(0f, 0f, 0f),
    /** Grid footprint on the desk (resize handle); maps to provider dp / cell grid. */
    var size: Vector3 = Vector3(2f, 0f, 2f),
    var surface: BumpItem.Surface = BumpItem.Surface.BACK_WALL,
    var textureId: Int = -1,
    var aspectRatio: Float = 1f,
    /** Visual scale within the grid footprint (scale up/down menu). */
    var scale: Float = 1f,
) {
    /** Visual half-extents on the desk (grid size × scale). Handles and drawing use this. */
    fun displayHalfSize(): Vector3 = Vector3(size.x * scale, 0f, size.z * scale)
}
