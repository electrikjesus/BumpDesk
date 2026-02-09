package com.bass.bumpdesk

data class BumpItem(
    var type: Type = Type.APP,
    var appInfo: AppInfo? = null,
    var text: String = "", // For sticky notes, Photo Frame URI, or Web URL
    var color: FloatArray = floatArrayOf(1f, 1f, 1f, 1f),
    var position: FloatArray = floatArrayOf(0f, 0f, 0f),
    var rotation: FloatArray = floatArrayOf(0f, 0f, 0f),
    var scale: Float = 0.5f,
    var scaleX: Float = 1.0f,
    var scaleZ: Float = 1.0f,
    var velocity: FloatArray = floatArrayOf(0f, 0f, 0f),
    var isPinned: Boolean = false,
    var surface: Surface = Surface.FLOOR,
    var textureId: Int = -1,
    var textTextureId: Int = -1
) {
    enum class Surface {
        FLOOR, BACK_WALL, LEFT_WALL, RIGHT_WALL
    }
    
    enum class Type {
        APP, STICKY_NOTE, PHOTO_FRAME, WEB_WIDGET, RECENT_APP, APP_DRAWER
    }
}
