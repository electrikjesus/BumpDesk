package com.bass.bumpdesk

data class BumpItem(
    var type: Type = Type.APP,
    var appInfo: AppInfo? = null,
    var text: String = "",
    var color: FloatArray = floatArrayOf(1f, 1f, 1f, 1f),
    var position: Vector3 = Vector3(0f, 0.05f, 0f),
    var rotation: Vector3 = Vector3(0f, 0f, 0f),
    var velocity: Vector3 = Vector3(0f, 0f, 0f),
    var scale: Float = 0.5f,
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

    /**
     * Helper to clone the item with a new position.
     */
    fun copy(position: Vector3 = this.position): BumpItem {
        return BumpItem(
            type = this.type,
            appInfo = this.appInfo,
            text = this.text,
            color = this.color.clone(),
            position = position,
            rotation = this.rotation.copy(),
            velocity = this.velocity.copy(),
            scale = this.scale,
            isPinned = this.isPinned,
            surface = this.surface,
            textureId = this.textureId,
            textTextureId = this.textTextureId
        )
    }
}
