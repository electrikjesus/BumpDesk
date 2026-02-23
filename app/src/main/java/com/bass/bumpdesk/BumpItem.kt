package com.bass.bumpdesk

import kotlin.reflect.KClass

class BumpItem(
    type: Type = Type.APP,
    color: FloatArray = floatArrayOf(1f, 1f, 1f, 1f),
    textureId: Int = -1,
    textTextureId: Int = -1,
    position: Vector3 = Vector3(0f, 0.05f, 0f),
    velocity: Vector3 = Vector3(0f, 0f, 0f),
    scale: Float = 0.5f,
    isPinned: Boolean = false,
    surface: Surface = Surface.FLOOR,
    appInfo: AppInfo? = null,
    text: String = ""
) {
    private val components = mutableMapOf<KClass<out Component>, Component>()

    init {
        addComponent(AppearanceComponent(type, color, textureId, textTextureId))
        addComponent(TransformComponent(position, Vector3(), velocity, scale, isPinned, surface))
        appInfo?.let { addComponent(AppDataComponent(it)) }
        if (text.isNotEmpty() || type == Type.STICKY_NOTE || type == Type.PHOTO_FRAME || type == Type.WEB_WIDGET) {
            addComponent(TextDataComponent(text))
        }
    }

    enum class Surface {
        FLOOR, BACK_WALL, LEFT_WALL, RIGHT_WALL
    }
    
    enum class Type {
        APP, STICKY_NOTE, PHOTO_FRAME, WEB_WIDGET, RECENT_APP, APP_DRAWER
    }

    fun <T : Component> getComponent(clazz: KClass<T>): T? {
        @Suppress("UNCHECKED_CAST")
        return components[clazz] as? T
    }

    fun addComponent(component: Component) {
        components[component::class] = component
    }

    // Convenience accessors for commonly used components
    val transform: TransformComponent
        get() = getComponent(TransformComponent::class) ?: run {
            val t = TransformComponent()
            addComponent(t)
            t
        }

    val appearance: AppearanceComponent
        get() = getComponent(AppearanceComponent::class) ?: run {
            val a = AppearanceComponent()
            addComponent(a)
            a
        }

    val appData: AppDataComponent?
        get() = getComponent(AppDataComponent::class)

    val textData: TextDataComponent?
        get() = getComponent(TextDataComponent::class)

    /**
     * Legacy compatibility properties (to be phased out).
     * Delegating to components to avoid breaking everything at once.
     */
    var type: Type
        get() = appearance.type
        set(value) { appearance.type = value }

    var color: FloatArray
        get() = appearance.color
        set(value) { appearance.color = value }

    var textureId: Int
        get() = appearance.textureId
        set(value) { appearance.textureId = value }

    var textTextureId: Int
        get() = appearance.textTextureId
        set(value) { appearance.textTextureId = value }

    var position: Vector3
        get() = transform.position
        set(value) { transform.position = value }

    var velocity: Vector3
        get() = transform.velocity
        set(value) { transform.velocity = value }

    var scale: Float
        get() = transform.scale
        set(value) { transform.scale = value }

    var isPinned: Boolean
        get() = transform.isPinned
        set(value) { transform.isPinned = value }

    var surface: Surface
        get() = transform.surface
        set(value) { transform.surface = value }

    var appInfo: AppInfo?
        get() = appData?.appInfo
        set(value) { if (value != null) addComponent(AppDataComponent(value)) }

    var text: String
        get() = textData?.text ?: ""
        set(value) { addComponent(TextDataComponent(value)) }

    fun copy(position: Vector3 = this.position): BumpItem {
        val newItem = BumpItem()
        // Copy components
        newItem.addComponent(this.transform.copy(position = position))
        newItem.addComponent(this.appearance.copy())
        this.appData?.let { newItem.addComponent(it.copy()) }
        this.textData?.let { newItem.addComponent(it.copy()) }
        return newItem
    }
}
