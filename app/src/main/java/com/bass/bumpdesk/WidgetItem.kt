package com.bass.bumpdesk

data class WidgetItem(
    val appWidgetId: Int,
    var position: FloatArray = floatArrayOf(0f, 0f, 0f),
    var size: FloatArray = floatArrayOf(2f, 2f), // Width, Height in 3D units
    var surface: BumpItem.Surface = BumpItem.Surface.BACK_WALL,
    var textureId: Int = -1
)
