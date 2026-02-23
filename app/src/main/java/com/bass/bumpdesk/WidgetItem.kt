package com.bass.bumpdesk

data class WidgetItem(
    val appWidgetId: Int,
    var position: Vector3 = Vector3(0f, 0f, 0f),
    var size: Vector3 = Vector3(2f, 0f, 2f), // Width, Height in 3D units
    var surface: BumpItem.Surface = BumpItem.Surface.BACK_WALL,
    var textureId: Int = -1
)
