package com.bass.bumpdesk

import android.content.Context

interface Component

interface RenderComponent : Component {
    fun ensureTexture(context: Context, textureManager: TextureManager, item: BumpItem)
    fun draw(vPMatrix: FloatArray, modelMatrix: FloatArray, textureId: Int, color: FloatArray)
}

data class TransformComponent(
    var position: Vector3 = Vector3(0f, 0.05f, 0f),
    var rotation: Vector3 = Vector3(0f, 0f, 0f),
    var velocity: Vector3 = Vector3(0f, 0f, 0f),
    var scale: Float = 0.5f,
    var isPinned: Boolean = false,
    var surface: BumpItem.Surface = BumpItem.Surface.FLOOR,
    /** Sticky note width multiplier relative to [scale] (1 = square). */
    var shapeHalfX: Float = StickyNoteStyle.DEFAULT_SHAPE_HALF,
    /** Sticky note depth multiplier relative to [scale] (1 = square). */
    var shapeHalfZ: Float = StickyNoteStyle.DEFAULT_SHAPE_HALF,
) : Component

data class AppearanceComponent(
    var type: BumpItem.Type = BumpItem.Type.APP,
    var color: FloatArray = floatArrayOf(1f, 1f, 1f, 1f),
    var textureId: Int = -1,
    var textTextureId: Int = -1
) : Component {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AppearanceComponent
        if (type != other.type) return false
        if (!color.contentEquals(other.color)) return false
        if (textureId != other.textureId) return false
        if (textTextureId != other.textTextureId) return false
        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + color.contentHashCode()
        result = 31 * result + textureId
        result = 31 * result + textTextureId
        return result
    }
    
    fun copy(): AppearanceComponent = AppearanceComponent(
        type = type,
        color = color.clone(),
        textureId = textureId,
        textTextureId = textTextureId
    )
}

data class AppDataComponent(
    val appInfo: AppInfo
) : Component

data class TextDataComponent(
    var text: String = ""
) : Component
