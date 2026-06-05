package com.bass.bumpdesk

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

/**
 * Loads system wallpaper on the main thread and supplies a copy for GL upload.
 * WallpaperManager can throw SecurityException when called from the GL thread.
 */
object WallpaperFloorProvider {

    const val WALLPAPER_FLOOR_FILE = "wallpaper_floor.jpg"

    /** Full-res source kept for re-cropping when floor aspect changes. */
    @Volatile
    private var sourceBitmap: Bitmap? = null

    /** GL-safe, downscaled copy of [sourceBitmap]; safe to crop repeatedly. */
    @Volatile
    private var preparedSource: Bitmap? = null

    @Volatile
    private var cachedBitmap: Bitmap? = null

    @Volatile
    private var cropAspectWidth = 1f

    @Volatile
    private var cropAspectHeight = 1f

    fun hasBitmap(): Boolean = cachedBitmap != null

    fun refresh(context: Context, floorAspectWidth: Float = 1f, floorAspectDepth: Float = 1f): Boolean {
        cropAspectWidth = floorAspectWidth
        cropAspectHeight = floorAspectDepth
        WallpaperPermissions.logStatus(context, "refresh")
        val loaded = TextureUtils.loadSystemWallpaperBitmap(context) ?: return false
        return ingestSource(loaded)
    }

    fun refreshWithRetry(
        context: Context,
        floorAspectWidth: Float = 1f,
        floorAspectDepth: Float = 1f,
        onComplete: (Boolean) -> Unit,
    ) {
        if (refresh(context, floorAspectWidth, floorAspectDepth)) {
            onComplete(true)
            return
        }
        Handler(Looper.getMainLooper()).postDelayed({
            onComplete(refresh(context, floorAspectWidth, floorAspectDepth))
        }, 300)
    }

    /** Re-center-crops the cached source when the floor plane aspect changes (e.g. flat floor mode). */
    fun updateFloorCropAspect(floorAspectWidth: Float, floorAspectDepth: Float): Boolean {
        val unchanged = abs(floorAspectWidth * cropAspectHeight - floorAspectDepth * cropAspectWidth) < 0.01f
        cropAspectWidth = floorAspectWidth
        cropAspectHeight = floorAspectDepth
        if (unchanged || preparedSource == null) {
            return false
        }
        return publishCroppedCache()
    }

    fun savePickedWallpaper(
        context: Context,
        uri: Uri,
        floorAspectWidth: Float = 1f,
        floorAspectDepth: Float = 1f,
    ): Boolean {
        cropAspectWidth = floorAspectWidth
        cropAspectHeight = floorAspectDepth
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
                ingestSource(prepared)
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
        preparedSource?.recycle()
        preparedSource = null
        sourceBitmap?.recycle()
        sourceBitmap = null
    }

    /** Returns an ARGB copy safe to upload on the GL thread; caller must recycle. */
    fun createBitmapForGl(): Bitmap? {
        val source = cachedBitmap ?: return null
        val config = source.config ?: Bitmap.Config.ARGB_8888
        return source.copy(config, false)
    }

    private fun ingestSource(bitmap: Bitmap): Boolean {
        val previousSource = sourceBitmap
        val previousPrepared = preparedSource
        sourceBitmap = bitmap
        preparedSource = TextureUtils.prepareBitmapForGl(bitmap)
        previousPrepared?.recycle()
        if (previousSource != null && previousSource !== bitmap && previousSource !== preparedSource) {
            previousSource.recycle()
        }
        return publishCroppedCache()
    }

    private fun publishCroppedCache(): Boolean {
        val source = preparedSource ?: return false
        val cropped = TextureUtils.centerCropToAspect(source, cropAspectWidth, cropAspectHeight)
        if (TextureUtils.isMostlyBlank(cropped)) {
            BumpDeskLog.w(BumpDeskLog.Tag.WALLPAPER, "refresh", "rejected blank wallpaper bitmap")
            if (cropped !== source) {
                cropped.recycle()
            }
            cachedBitmap?.recycle()
            cachedBitmap = null
            return false
        }
        val previous = cachedBitmap
        cachedBitmap = cropped
        if (previous != null && previous !== cropped && previous !== source) {
            previous.recycle()
        }
        BumpDeskLog.d(
            BumpDeskLog.Tag.WALLPAPER,
            "refresh",
            "cached ${cachedBitmap!!.width}x${cachedBitmap!!.height} " +
                "crop=${cropAspectWidth}x${cropAspectHeight}"
        )
        return true
    }
}
