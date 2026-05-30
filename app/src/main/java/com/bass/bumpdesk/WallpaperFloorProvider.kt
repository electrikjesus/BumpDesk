package com.bass.bumpdesk

import android.content.Context
import android.graphics.Bitmap

/**
 * Loads system wallpaper on the main thread and supplies a copy for GL upload.
 * WallpaperManager can throw SecurityException when called from the GL thread.
 */
object WallpaperFloorProvider {

    @Volatile
    private var cachedBitmap: Bitmap? = null

    fun refresh(context: Context) {
        val previous = cachedBitmap
        cachedBitmap = TextureUtils.loadSystemWallpaperBitmap(context)
        previous?.recycle()
        if (cachedBitmap != null) {
            BumpDeskLog.d(
                BumpDeskLog.Tag.WALLPAPER,
                "refresh",
                "cached ${cachedBitmap!!.width}x${cachedBitmap!!.height}"
            )
        } else {
            BumpDeskLog.w(BumpDeskLog.Tag.WALLPAPER, "refresh", "no wallpaper bitmap available")
        }
    }

    fun clear() {
        cachedBitmap?.recycle()
        cachedBitmap = null
    }

    /** Returns an ARGB copy safe to upload on the GL thread; caller must recycle. */
    fun createBitmapForGl(): Bitmap? {
        val source = cachedBitmap ?: return null
        val config = source.config ?: Bitmap.Config.ARGB_8888
        return source.copy(config, false)
    }
}
