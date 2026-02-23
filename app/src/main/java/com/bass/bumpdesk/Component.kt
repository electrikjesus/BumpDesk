package com.bass.bumpdesk

import android.content.Context

interface Component

interface RenderComponent : Component {
    fun ensureTexture(context: Context, textureManager: TextureManager, item: BumpItem)
    fun draw(vPMatrix: FloatArray, modelMatrix: FloatArray, textureId: Int, color: FloatArray)
}
