package com.bass.bumpdesk

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.opengl.Matrix
import android.view.View
import android.appwidget.AppWidgetHostView
import android.opengl.GLES20

class WidgetRenderer(
    private val context: Context,
    private val shader: DefaultShader,
    private val textureManager: TextureManager
) {
    private val widgetBox = Box(shader)
    private val modelMatrix = FloatArray(16)
    private val handlePlane = Plane(shader)
    
    // Memory Audit: Reuse a single canvas and bitmap for widget updates to reduce GC pressure.
    private var updateBitmap: Bitmap? = null
    private var updateCanvas: Canvas? = null

    fun drawWidgets(
        vPMatrix: FloatArray,
        widgetItems: List<WidgetItem>,
        widgetViews: Map<Int, AppWidgetHostView>,
        frameCount: Int,
        selectedWidget: WidgetItem?,
        onUpdateTexture: (Runnable) -> Unit
    ) {
        widgetItems.forEach { widget ->
            val view = widgetViews[widget.appWidgetId]
            if (view != null) {
                // Task: Memory Audit - Only update every 15 frames to save battery/CPU
                if (widget.textureId <= 0 || (frameCount % 15 == 0)) {
                    updateWidgetTexture(widget, view, onUpdateTexture)
                }
            }
            drawWidget(vPMatrix, widget, widget == selectedWidget)
        }
    }

    private fun updateWidgetTexture(widget: WidgetItem, view: View, onUpdateTexture: (Runnable) -> Unit) {
        if (view.width <= 0 || view.height <= 0) {
            val widthSpec = View.MeasureSpec.makeMeasureSpec(1024, View.MeasureSpec.EXACTLY)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(1024, View.MeasureSpec.EXACTLY)
            view.measure(widthSpec, heightSpec)
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        }

        view.post {
            view.invalidate()
            val w = view.width.coerceAtLeast(1)
            val h = view.height.coerceAtLeast(1)
            
            try {
                // Memory Audit: Reuse bitmap if dimensions match
                if (updateBitmap == null || updateBitmap!!.width != w || updateBitmap!!.height != h) {
                    updateBitmap?.recycle()
                    updateBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    updateCanvas = Canvas(updateBitmap!!)
                }
                
                updateCanvas?.let { canvas ->
                    view.draw(canvas)
                    
                    onUpdateTexture(Runnable {
                        if (widget.textureId <= 0) {
                            widget.textureId = textureManager.loadTextureFromBitmap(updateBitmap!!)
                        } else {
                            textureManager.updateTextureFromBitmap(widget.textureId, updateBitmap!!)
                        }
                        // Note: We DON'T recycle here because we are reusing it in the next update
                    })
                }
            } catch (e: Exception) {}
        }
    }

    private fun drawWidget(vPMatrix: FloatArray, widget: WidgetItem, isSelected: Boolean) {
        Matrix.setIdentityM(modelMatrix, 0)
        
        val zOffset = 0.02f
        val posX = widget.position.x
        val posY = widget.position.y
        val posZ = widget.position.z
        
        when (widget.surface) {
            BumpItem.Surface.BACK_WALL -> {
                Matrix.translateM(modelMatrix, 0, posX, posY, posZ + zOffset)
                Matrix.rotateM(modelMatrix, 0, 180f, 0f, 1f, 0f)
                Matrix.rotateM(modelMatrix, 0, 90f, 1f, 0f, 0f)
            }
            BumpItem.Surface.LEFT_WALL -> {
                Matrix.translateM(modelMatrix, 0, posX + zOffset, posY, posZ)
                Matrix.rotateM(modelMatrix, 0, 90f, 0f, 1f, 0f)
                Matrix.rotateM(modelMatrix, 0, 90f, 1f, 0f, 0f)
            }
            BumpItem.Surface.RIGHT_WALL -> {
                Matrix.translateM(modelMatrix, 0, posX - zOffset, posY, posZ)
                Matrix.rotateM(modelMatrix, 0, -90f, 0f, 1f, 0f)
                Matrix.rotateM(modelMatrix, 0, 90f, 1f, 0f, 0f)
            }
            BumpItem.Surface.FLOOR -> {
                Matrix.translateM(modelMatrix, 0, posX, posY + zOffset, posZ)
            }
        }
        
        val savedModelMatrix = modelMatrix.clone()
        Matrix.scaleM(modelMatrix, 0, widget.size.x, 1f, widget.size.z)
        widgetBox.draw(vPMatrix, modelMatrix, widget.textureId, floatArrayOf(1f, 1f, 1f, 1.0f))

        if (isSelected) {
            drawResizeHandle(vPMatrix, savedModelMatrix, widget.size)
        }
    }

    private fun drawResizeHandle(vPMatrix: FloatArray, baseModelMatrix: FloatArray, size: Vector3) {
        val handleMatrix = baseModelMatrix.clone()
        // Position handle at bottom-right corner of the scaled widget
        Matrix.translateM(handleMatrix, 0, size.x - 0.2f, 0.01f, size.z - 0.2f)
        Matrix.scaleM(handleMatrix, 0, 0.2f, 1f, 0.2f)
        
        val selectionColor = ThemeManager.getSelectionColor()
        handlePlane.draw(vPMatrix, handleMatrix, floatArrayOf(selectionColor[0], selectionColor[1], selectionColor[2], 1.0f), -1, floatArrayOf(0f, 10f, 0f), 1.0f, false)
    }
}
