package com.bass.bumpdesk

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.FileOutputStream

/**
 * Loads system wallpaper on the main thread and supplies a copy for GL upload.
 * WallpaperManager can throw SecurityException when called from the GL thread.
 */
object WallpaperFloorProvider {

    const val WALLPAPER_FLOOR_FILE = "wallpaper_floor.jpg"

    @Volatile
    private var cachedBitmap: Bitmap? = null

    fun hasBitmap(): Boolean = cachedBitmap != null

    fun refresh(context: Context): Boolean {
        WallpaperPermissions.logStatus(context, "refresh")
        return loadBitmap(TextureUtils.loadSystemWallpaperBitmap(context))
    }

    fun refreshWithRetry(context: Context, onComplete: (Boolean) -> Unit) {
        if (refresh(context)) {
            onComplete(true)
            return
        }
        Handler(Looper.getMainLooper()).postDelayed({
            onComplete(refresh(context))
        }, 300)
    }

    fun savePickedWallpaper(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val decoded = BitmapFactory.decodeStream(input) ?: return false
                val prepared = TextureUtils.prepareBitmapForGl(decoded)
                if (prepared !== decoded) {
                    decoded.recycle()
                }
                if (TextureUtils.isMostlyBlank(prepared)) {
                    prepared.recycle()
                    BumpDeskLog.w(BumpDeskLog.Tag.WALLPAPER, "savePickedWallpaper", "picked image is blank")
                    return false
                }
                FileOutputStream(File(context.filesDir, WALLPAPER_FLOOR_FILE)).use { out ->
                    prepared.compress(Bitmap.CompressFormat.JPEG, 92, out)
                }
                loadBitmap(prepared)
            } ?: false
        } catch (e: Exception) {
            BumpDeskLog.fail(BumpDeskLog.Tag.WALLPAPER, "savePickedWallpaper", "failed to save picked wallpaper", e)
            false
        }
    }

    fun clearPickedWallpaper(context: Context) {
        File(context.filesDir, WALLPAPER_FLOOR_FILE).delete()
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

    private fun loadBitmap(bitmap: Bitmap?): Boolean {
        val usable = when {
            bitmap == null -> null
            TextureUtils.isMostlyBlank(bitmap) -> {
                BumpDeskLog.w(BumpDeskLog.Tag.WALLPAPER, "refresh", "rejected blank wallpaper bitmap")
                bitmap.recycle()
                null
            }
            else -> bitmap
        }
        val previous = cachedBitmap
        cachedBitmap = usable
        previous?.recycle()
        if (cachedBitmap != null) {
            BumpDeskLog.d(
                BumpDeskLog.Tag.WALLPAPER,
                "refresh",
                "cached ${cachedBitmap!!.width}x${cachedBitmap!!.height}"
            )
            return true
        }
        BumpDeskLog.w(BumpDeskLog.Tag.WALLPAPER, "refresh", "no wallpaper bitmap available")
        return false
    }
}
