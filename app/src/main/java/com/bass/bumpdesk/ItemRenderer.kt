package com.bass.bumpdesk

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.opengl.Matrix
import android.webkit.WebView
import android.webkit.WebViewClient
import android.graphics.BitmapFactory

class ItemRenderer(
    private val context: Context,
    private val shader: DefaultShader,
    private val textureManager: TextureManager,
    private val sceneState: SceneState
) {
    private val appIconBox = Box(shader)
    private val modelMatrix = FloatArray(16)

    fun drawItems(
        vPMatrix: FloatArray,
        items: List<BumpItem>,
        lightPos: FloatArray,
        searchQuery: String,
        onUpdateTexture: (Runnable) -> Unit
    ) {
        items.forEach { item ->
            // Performance optimization: skip rendering and texture loading for hidden items
            if (item.transform.position.y < -5f) return@forEach
            
            ensureItemTexture(item)
            val appearance = item.appearance
            if (appearance.type == BumpItem.Type.WEB_WIDGET && (System.currentTimeMillis() % 1000 < 16)) {
                updateWebTexture(item, onUpdateTexture)
            }
            drawItem(vPMatrix, item, lightPos, searchQuery)
        }
    }

    fun ensureItemTexture(item: BumpItem) {
        val appearance = item.appearance
        if (appearance.textureId > 0) return
        
        val appInfo = item.appData?.appInfo
        
        // Use a unique key for caching dynamically generated bitmaps
        val cacheKey = when (appearance.type) {
            BumpItem.Type.APP -> "app:v2:${appInfo?.packageName}"
            BumpItem.Type.APP_DRAWER -> "drawer:icon:v2"
            else -> null
        }
        
        if (cacheKey != null) {
            val cached = textureManager.getCachedTexture(cacheKey)
            if (cached > 0) {
                appearance.textureId = cached
                return
            }
        }

        when (appearance.type) {
            BumpItem.Type.APP -> {
                appInfo?.let { app -> loadAppIconTexture(item, app, cacheKey) }
            }
            BumpItem.Type.STICKY_NOTE -> {
                val text = item.textData?.text ?: ""
                val bitmap = TextRenderer.createTextBitmap(text, 512, 512)
                appearance.textureId = textureManager.loadTextureFromBitmap(bitmap)
                bitmap.recycle()
            }
            BumpItem.Type.PHOTO_FRAME -> {
                val uri = item.textData?.text ?: ""
                if (uri.isNotEmpty()) {
                    try {
                        val inputStream = context.contentResolver.openInputStream(Uri.parse(uri))
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        if (bitmap != null) {
                            appearance.textureId = textureManager.loadTextureFromBitmap(bitmap)
                            bitmap.recycle()
                        }
                    } catch (e: Exception) {
                        appearance.textureId = textureManager.loadTextureFromBitmap(TextRenderer.createTextBitmap("Photo missing", 256, 256))
                    }
                } else {
                    appearance.textureId = textureManager.loadTextureFromBitmap(TextRenderer.createTextBitmap("Pick Photo", 256, 256))
                }
            }
            BumpItem.Type.WEB_WIDGET -> {
                val url = item.textData?.text ?: ""
                if (url.isNotEmpty()) {
                    appearance.textureId = textureManager.loadTextureFromBitmap(TextRenderer.createTextBitmap("Loading Web...", 256, 256))
                } else {
                    appearance.textureId = textureManager.loadTextureFromBitmap(TextRenderer.createTextBitmap("Set URL", 256, 256))
                }
            }
            BumpItem.Type.RECENT_APP -> {
                appInfo?.let { app ->
                    val pile = sceneState.getPileOf(item)
                    val folderGrid = pile?.showsRecentsIconGrid() == true
                    if (folderGrid) {
                        loadRecentsGridIconTexture(item, app, cacheKey)
                    } else {
                        val overrideBitmap = ThemeManager.getIconOverride(context, app.packageName)
                        val iconDrawable = if (overrideBitmap != null) BitmapDrawable(context.resources, overrideBitmap) else app.icon
                        val bitmap = TextureUtils.createRecentTaskBitmap(context, app.snapshot, iconDrawable, app.label)
                        appearance.textureId = textureManager.loadTextureFromBitmap(bitmap)
                        bitmap.recycle()
                        overrideBitmap?.recycle()
                    }
                }
            }
            BumpItem.Type.APP_DRAWER -> {
                val combined = TextureUtils.createAppDrawerIcon(context)
                appearance.textureId = textureManager.loadTextureFromBitmap(combined)

                if (cacheKey != null) textureManager.cacheTexture(cacheKey, appearance.textureId)

                combined.recycle()
            }
        }
    }

    private fun loadAppIconTexture(item: BumpItem, app: AppInfo, cacheKey: String?) {
        val appearance = item.appearance
        val overrideBitmap = ThemeManager.getIconOverride(context, app.packageName)
        val iconBitmap = overrideBitmap ?: (app.icon?.let { TextureUtils.getBitmapFromDrawable(it) })

        if (iconBitmap != null) {
            val labelBitmap = TextRenderer.createAppLabelBitmap(app.label)
            val combined = TextureUtils.getCombinedBitmap(context, iconBitmap, labelBitmap, false)
            appearance.textureId = textureManager.loadTextureFromBitmap(combined)

            if (cacheKey != null) textureManager.cacheTexture(cacheKey, appearance.textureId)

            combined.recycle()
            labelBitmap.recycle()
            if (overrideBitmap != null) iconBitmap.recycle()
        }
    }

    private fun loadRecentsGridIconTexture(item: BumpItem, app: AppInfo, cacheKey: String?) {
        val appearance = item.appearance
        val overrideBitmap = ThemeManager.getIconOverride(context, app.packageName)
        val iconBitmap = overrideBitmap ?: (app.icon?.let { TextureUtils.getBitmapFromDrawable(it, 192) }) ?: return

        val snapshot = if (RecentsSnapshotCapability.isAvailable()) app.snapshot else null
        val combined = TextureUtils.createRecentsGridIconBitmap(
            context,
            snapshot,
            iconBitmap,
            app.label,
            app.taskId,
        )
        appearance.textureId = textureManager.loadTextureFromBitmap(combined)

        if (cacheKey != null) textureManager.cacheTexture(cacheKey, appearance.textureId)

        combined.recycle()
        if (overrideBitmap != null) iconBitmap.recycle()
    }

    private fun updateWebTexture(item: BumpItem, onUpdateTexture: (Runnable) -> Unit) {
        val appearance = item.appearance
        val url = item.textData?.text ?: ""
        val webView = sceneState.webViews[item.hashCode()] ?: run {
            val wv = WebView(context).apply {
                settings.javaScriptEnabled = true
                webViewClient = WebViewClient()
                layout(0, 0, 1024, 1024)
                loadUrl(url)
            }
            sceneState.webViews[item.hashCode()] = wv
            wv
        }
        webView.post {
            try {
                val bitmap = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                webView.draw(canvas)
                onUpdateTexture(Runnable {
                    if (appearance.textureId <= 0) {
                        appearance.textureId = textureManager.loadTextureFromBitmap(bitmap)
                    } else {
                        textureManager.updateTextureFromBitmap(appearance.textureId, bitmap)
                    }
                    bitmap.recycle()
                })
            } catch (e: Exception) {}
        }
    }

    private fun drawItem(vPMatrix: FloatArray, item: BumpItem, lightPos: FloatArray, searchQuery: String) {
        Matrix.setIdentityM(modelMatrix, 0)
        
        val transform = item.transform
        val appearance = item.appearance
        
        val pile = sceneState.getPileOf(item)
        val surfaceToUse = when {
            pile?.showsRecentsTaskCards() == true -> BumpItem.Surface.BACK_WALL
            pile?.layoutAsExpandedDrawer() == true && pile.recentsOnWall() -> pile.surface
            pile?.isExpanded == true -> BumpItem.Surface.FLOOR
            else -> transform.surface
        }
        
        val wallRecentsDrawer =
            pile?.layoutAsExpandedDrawer() == true && pile.recentsOnWall() && pile.showsRecentsIconGrid()
        val (posX, posY, posZ) = SurfaceRenderDepth.offsetDrawPosition(
            surfaceToUse,
            transform.position.x,
            transform.position.y,
            transform.position.z,
            skipWallOffset = wallRecentsDrawer,
        )

        when (surfaceToUse) {
            BumpItem.Surface.BACK_WALL -> {
                Matrix.translateM(modelMatrix, 0, posX, posY, posZ)
                Matrix.rotateM(modelMatrix, 0, 180f, 0f, 1f, 0f)
                Matrix.rotateM(modelMatrix, 0, 90f, 1f, 0f, 0f)
                Matrix.rotateM(modelMatrix, 0, 180f, 0f, 0f, 1f)
            }
            BumpItem.Surface.LEFT_WALL -> {
                Matrix.translateM(modelMatrix, 0, posX, posY, posZ)
                Matrix.rotateM(modelMatrix, 0, 90f, 0f, 1f, 0f)
                Matrix.rotateM(modelMatrix, 0, 180f, 0f, 1f, 0f)
                Matrix.rotateM(modelMatrix, 0, 90f, 1f, 0f, 0f)
                Matrix.rotateM(modelMatrix, 0, 180f, 0f, 0f, 1f)
            }
            BumpItem.Surface.RIGHT_WALL -> {
                Matrix.translateM(modelMatrix, 0, posX, posY, posZ)
                Matrix.rotateM(modelMatrix, 0, -90f, 0f, 1f, 0f)
                Matrix.rotateM(modelMatrix, 0, 180f, 0f, 1f, 0f)
                Matrix.rotateM(modelMatrix, 0, 90f, 1f, 0f, 0f)
                Matrix.rotateM(modelMatrix, 0, 180f, 0f, 0f, 1f)
            }
            BumpItem.Surface.FLOOR -> {
                Matrix.translateM(modelMatrix, 0, posX, posY, posZ)
            }
        }
        
        val heightMult = when (appearance.type) { 
            BumpItem.Type.APP -> if (pile?.layoutMode == Pile.LayoutMode.CAROUSEL) 1.6f else 1.25f
            BumpItem.Type.RECENT_APP -> {
                if (pile?.showsRecentsIconGrid() == true) {
                    1.25f
                } else {
                    1.6f
                }
            }
            BumpItem.Type.APP_DRAWER -> 1.38f
            else -> 1.0f 
        }
        Matrix.scaleM(modelMatrix, 0, transform.scale, 1f, transform.scale * heightMult)
        
        var color = if (transform.isPinned) floatArrayOf(0.8f, 0.8f, 1.0f, 1.0f) else appearance.color
        
        // Search Highlighting Logic
        if (searchQuery.isNotEmpty()) {
            val appInfo = item.appData?.appInfo
            val label = when (appearance.type) {
                BumpItem.Type.APP, BumpItem.Type.RECENT_APP -> appInfo?.label ?: ""
                BumpItem.Type.STICKY_NOTE -> item.textData?.text ?: ""
                BumpItem.Type.WEB_WIDGET -> item.textData?.text ?: ""
                else -> ""
            }
            
            if (label.lowercase().contains(searchQuery.lowercase())) {
                val freshness = ThemeManager.getFreshnessColor()
                color = floatArrayOf(freshness[0], freshness[1], freshness[2], 1.0f)
            } else {
                // Fade out non-matching items
                color = floatArrayOf(color[0] * 0.3f, color[1] * 0.3f, color[2] * 0.3f, 0.3f)
            }
        } else if (item == sceneState.selectedItem || sceneState.groupSelectedItems?.contains(item) == true) {
            val selectionColor = ThemeManager.getSelectionColor()
            color = floatArrayOf(selectionColor[0], selectionColor[1], selectionColor[2], 1.0f)
        }

        appIconBox.draw(vPMatrix, modelMatrix, appearance.textureId, color)
    }

    fun drawPileFolderPreview(vPMatrix: FloatArray, pile: Pile, lightPos: FloatArray) {
        if (pile.previewTextureId <= 0) return
        Matrix.setIdentityM(modelMatrix, 0)
        val heightScale = if (pile.showsCollapsedLabel()) 1.38f else 1f
        val (posX, posY, posZ) = SurfaceRenderDepth.offsetDrawPosition(
            pile.surface,
            pile.position.x,
            pile.position.y,
            pile.position.z,
        )
        when (pile.surface) {
            BumpItem.Surface.BACK_WALL -> {
                Matrix.translateM(modelMatrix, 0, posX, posY, posZ)
                Matrix.rotateM(modelMatrix, 0, 90f, 1f, 0f, 0f)
                Matrix.scaleM(modelMatrix, 0, pile.scale, 1f, pile.scale * heightScale)
            }
            BumpItem.Surface.LEFT_WALL -> {
                Matrix.translateM(modelMatrix, 0, posX, posY, posZ)
                Matrix.rotateM(modelMatrix, 0, 90f, 0f, 1f, 0f)
                Matrix.rotateM(modelMatrix, 0, 180f, 0f, 1f, 0f)
                Matrix.rotateM(modelMatrix, 0, 90f, 1f, 0f, 0f)
                Matrix.scaleM(modelMatrix, 0, pile.scale, 1f, pile.scale * heightScale)
            }
            BumpItem.Surface.RIGHT_WALL -> {
                Matrix.translateM(modelMatrix, 0, posX, posY, posZ)
                Matrix.rotateM(modelMatrix, 0, -90f, 0f, 1f, 0f)
                Matrix.rotateM(modelMatrix, 0, 180f, 0f, 1f, 0f)
                Matrix.rotateM(modelMatrix, 0, 90f, 1f, 0f, 0f)
                Matrix.scaleM(modelMatrix, 0, pile.scale, 1f, pile.scale * heightScale)
            }
            else -> {
                Matrix.translateM(modelMatrix, 0, posX, posY, posZ)
                Matrix.scaleM(modelMatrix, 0, pile.scale, 1f, pile.scale * heightScale)
            }
        }
        appIconBox.draw(
            vPMatrix,
            modelMatrix,
            pile.previewTextureId,
            floatArrayOf(1f, 1f, 1f, 1f),
        )
    }
}
