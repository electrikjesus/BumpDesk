package com.bass.bumpdesk

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLUtils
import android.util.Log
import android.util.LruCache

class TextureManager(private val context: Context) {
    // Max 100 textures in memory at once. Adjust based on profiling.
    private val textureCache = object : LruCache<String, Int>(100) {
        override fun entryRemoved(evicted: Boolean, key: String?, oldValue: Int?, newValue: Int?) {
            if (evicted && oldValue != null && oldValue > 0) {
                val textures = intArrayOf(oldValue)
                GLES20.glDeleteTextures(1, textures, 0)
                allTextures.remove(oldValue)
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
            val textureId = loadTextureFromBitmap(bitmap)
            bitmap.recycle()
            if (textureId != -1) {
                textureCache.put(fileName, textureId)
            }
            textureId
        } catch (e: Exception) {
            Log.e("TextureManager", "Error loading texture $fileName", e)
            -1
        }
    }

    fun loadTextureFromBitmap(bitmap: Bitmap): Int {
        val textureHandle = IntArray(1)
        GLES20.glGenTextures(1, textureHandle, 0)

        if (textureHandle[0] != 0) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            
            allTextures.add(textureHandle[0])
            return textureHandle[0]
        }

        return -1
    }

    fun updateTextureFromBitmap(textureId: Int, bitmap: Bitmap) {
        if (textureId <= 0) return
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
    }

    fun clearCache() {
        textureCache.evictAll()
        val textures = allTextures.toIntArray()
        if (textures.isNotEmpty()) {
            GLES20.glDeleteTextures(textures.size, textures, 0)
        }
        allTextures.clear()
    }
}
