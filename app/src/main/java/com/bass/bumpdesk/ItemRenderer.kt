package com.bass.bumpdesk

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
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
            ensureItemTexture(item)
            // Task: Live updates for web widgets. Update every ~1 second (1000ms)
            if (item.type == BumpItem.Type.WEB_WIDGET && (System.currentTimeMillis() % 1000 < 16)) {
                updateWebTexture(item, onUpdateTexture)
            }
            drawItem(vPMatrix, item, lightPos, searchQuery)
        }
    }

    fun ensureItemTexture(item: BumpItem) {
        if (item.textureId > 0) return
        
        when (item.type) {
            BumpItem.Type.APP -> {
                item.appInfo?.let { app ->
                    if (app.icon != null) {
                        val iconBitmap = TextureUtils.getBitmapFromDrawable(app.icon)
                        val labelBitmap = TextRenderer.createTextBitmap(app.label, 256, 64)
                        val combined = TextureUtils.getCombinedBitmap(context, iconBitmap, labelBitmap, false)
                        item.textureId = textureManager.loadTextureFromBitmap(combined)
                        combined.recycle()
                        iconBitmap.recycle()
                        labelBitmap.recycle()
                    }
                }
            }
            BumpItem.Type.STICKY_NOTE -> {
                val bitmap = TextRenderer.createTextBitmap(item.text, 512, 512)
                item.textureId = textureManager.loadTextureFromBitmap(bitmap)
                bitmap.recycle()
            }
            BumpItem.Type.PHOTO_FRAME -> {
                if (item.text.isNotEmpty()) {
                    try {
                        val inputStream = context.contentResolver.openInputStream(Uri.parse(item.text))
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        if (bitmap != null) {
                            item.textureId = textureManager.loadTextureFromBitmap(bitmap)
                            bitmap.recycle()
                        }
                    } catch (e: Exception) {
                        item.textureId = textureManager.loadTextureFromBitmap(TextRenderer.createTextBitmap("Photo missing", 256, 256))
                    }
                } else {
                    item.textureId = textureManager.loadTextureFromBitmap(TextRenderer.createTextBitmap("Pick Photo", 256, 256))
                }
            }
            BumpItem.Type.WEB_WIDGET -> {
                if (item.text.isNotEmpty()) {
                    item.textureId = textureManager.loadTextureFromBitmap(TextRenderer.createTextBitmap("Loading Web...", 256, 256))
                } else {
                    item.textureId = textureManager.loadTextureFromBitmap(TextRenderer.createTextBitmap("Set URL", 256, 256))
                }
            }
            BumpItem.Type.RECENT_APP -> {
                item.appInfo?.let { app ->
                    item.textureId = TextureUtils.loadRecentTaskTexture(context, app.snapshot, app.icon, app.label)
                }
            }
            BumpItem.Type.APP_DRAWER -> {
                val labelBitmap = TextRenderer.createTextBitmap("All Apps", 256, 64)
                val icon = context.getDrawable(context.applicationInfo.icon) ?: context.getDrawable(android.R.drawable.sym_def_app_icon)
                icon?.let {
                    val iconBitmap = TextureUtils.getBitmapFromDrawable(it)
                    val combined = TextureUtils.getCombinedBitmap(context, iconBitmap, labelBitmap, false)
                    item.textureId = textureManager.loadTextureFromBitmap(combined)
                    combined.recycle()
                    iconBitmap.recycle()
                }
                labelBitmap.recycle()
            }
        }
    }

    private fun updateWebTexture(item: BumpItem, onUpdateTexture: (Runnable) -> Unit) {
        val webView = sceneState.webViews[item.hashCode()] ?: run {
            val wv = WebView(context).apply {
                settings.javaScriptEnabled = true
                webViewClient = WebViewClient()
                layout(0, 0, 1024, 1024)
                loadUrl(item.text)
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
                    if (item.textureId <= 0) {
                        item.textureId = textureManager.loadTextureFromBitmap(bitmap)
                    } else {
                        textureManager.updateTextureFromBitmap(item.textureId, bitmap)
                    }
                    bitmap.recycle()
                })
            } catch (e: Exception) {}
        }
    }

    private fun drawItem(vPMatrix: FloatArray, item: BumpItem, lightPos: FloatArray, searchQuery: String) {
        Matrix.setIdentityM(modelMatrix, 0)
        
        val pile = sceneState.getPileOf(item)
        val surfaceToUse = if (pile?.isExpanded == true) BumpItem.Surface.FLOOR else item.surface
        
        // Task: Add a small offset to prevent Z-fighting with walls/floor
        val zOffset = 0.01f
        var posX = item.position[0]
        var posY = item.position[1]
        var posZ = item.position[2]

        when (surfaceToUse) {
            BumpItem.Surface.BACK_WALL -> {
                posZ += zOffset
                Matrix.translateM(modelMatrix, 0, posX, posY, posZ)
                Matrix.rotateM(modelMatrix, 0, 180f, 0f, 1f, 0f)
                Matrix.rotateM(modelMatrix, 0, 90f, 1f, 0f, 0f)
            }
            BumpItem.Surface.LEFT_WALL -> {
                posX += zOffset
                Matrix.translateM(modelMatrix, 0, posX, posY, posZ)
                Matrix.rotateM(modelMatrix, 0, 90f, 0f, 1f, 0f)
                Matrix.rotateM(modelMatrix, 0, 90f, 1f, 0f, 0f)
            }
            BumpItem.Surface.RIGHT_WALL -> {
                posX -= zOffset
                Matrix.translateM(modelMatrix, 0, posX, posY, posZ)
                Matrix.rotateM(modelMatrix, 0, -90f, 0f, 1f, 0f)
                Matrix.rotateM(modelMatrix, 0, 90f, 1f, 0f, 0f)
            }
            BumpItem.Surface.FLOOR -> {
                posY += zOffset
                Matrix.translateM(modelMatrix, 0, posX, posY, posZ)
            }
        }
        
        val heightMult = when (item.type) { 
            BumpItem.Type.APP -> if (pile?.layoutMode == Pile.LayoutMode.CAROUSEL) 1.6f else 1.25f
            BumpItem.Type.RECENT_APP -> 1.6f
            BumpItem.Type.APP_DRAWER -> 1.25f
            else -> 1.0f 
        }
        Matrix.scaleM(modelMatrix, 0, item.scale, 1f, item.scale * heightMult)
        
        var color = if (item.isPinned) floatArrayOf(0.8f, 0.8f, 1.0f, 1.0f) else item.color
        if (item == sceneState.selectedItem) {
            val selectionColor = ThemeManager.getSelectionColor()
            color = floatArrayOf(selectionColor[0], selectionColor[1], selectionColor[2], 1.0f)
        }
        if (searchQuery.isNotEmpty()) {
            val label = when (item.type) {
                BumpItem.Type.APP, BumpItem.Type.RECENT_APP -> item.appInfo?.label ?: ""
                BumpItem.Type.STICKY_NOTE -> item.text
                BumpItem.Type.WEB_WIDGET -> item.text
                else -> ""
            }
            if (label.lowercase().contains(searchQuery)) {
                val freshness = ThemeManager.getFreshnessColor()
                color = floatArrayOf(freshness[0], freshness[1], freshness[2], 1.0f)
            } else {
                color = floatArrayOf(color[0]*0.3f, color[1]*0.3f, color[2]*0.3f, 0.5f)
            }
        }
        appIconBox.draw(vPMatrix, modelMatrix, item.textureId, color)
    }
}
