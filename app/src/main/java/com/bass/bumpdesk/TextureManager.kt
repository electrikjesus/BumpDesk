package com.bass.bumpdesk

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLUtils
import android.util.Log
import android.util.LruCache

class TextureManager(private val context: Context) {
    private val textureCache = object : LruCache<String, Int>(100) {
        override fun entryRemoved(evicted: Boolean, key: String?, oldValue: Int?, newValue: Int?) {
            oldValue?.takeIf { it > 0 }?.let { textureId ->
                val textures = intArrayOf(textureId)
                GLES20.glDeleteTextures(1, textures, 0)
                allTextures.remove(textureId)
            }
        }
    }
    private val allTextures = mutableSetOf<Int>()

    fun getCachedTexture(key: String): Int {
        return textureCache.get(key) ?: -1
    }

    fun cacheTexture(key: String, textureId: Int) {
        if (textureId > 0) {
            textureCache.put(key, textureId)
            allTextures.add(textureId)
        }
    }

    fun loadTextureFromAsset(fileName: String): Int {
        textureCache.get(fileName)?.let { return it }

        return try {
            val inputStream = context.assets.open(fileName)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            val textureId = generateAndCacheTexture(fileName, bitmap)
            bitmap.recycle()
            textureId
        } catch (e: Exception) {
            Log.e("TextureManager", "Error loading texture $fileName", e)
            -1
        }
    }

    fun loadTextureFromBitmap(bitmap: Bitmap, key: String? = null): Int {
        key?.let { textureCache.get(it)?.let { return it } }

        return generateAndCacheTexture(key, bitmap)
    }

    private fun generateAndCacheTexture(key: String?, bitmap: Bitmap): Int {
        val textureHandle = IntArray(1)
        GLES20.glGenTextures(1, textureHandle, 0)

        if (textureHandle[0] != 0) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)

            val textureId = textureHandle[0]
            allTextures.add(textureId)
            key?.let { cacheTexture(it, textureId) }
            return textureId
        }

        return -1
    }

    fun updateTextureFromBitmap(textureId: Int, bitmap: Bitmap) {
        if (textureId <= 0) return
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, bitmap)
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
    }

    fun clearCache() {
        textureCache.evictAll() // This will trigger entryRemoved for all cached items
        // Delete any textures that were loaded without a key and thus not in the LruCache
        val texturesToDelete = allTextures.filter { textureCache.snapshot().values.contains(it).not() }
        if (texturesToDelete.isNotEmpty()) {
            val textures = texturesToDelete.toIntArray()
            GLES20.glDeleteTextures(textures.size, textures, 0)
            allTextures.removeAll(texturesToDelete)
        }
    }

    // Call this when the GL context is destroyed
    fun destroy() {
        // Evicting all will call glDeleteTextures for cached textures
        textureCache.evictAll()
        
        // Ensure any remaining non-cached textures are also deleted
        if (allTextures.isNotEmpty()) {
            val textures = allTextures.toIntArray()
            GLES20.glDeleteTextures(textures.size, textures, 0)
        }
        allTextures.clear()
        Log.d("TextureManager", "All textures destroyed.")
    }
}
