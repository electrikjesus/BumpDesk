package com.bass.bumpdesk

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.opengl.Matrix
import android.view.View
import android.appwidget.AppWidgetHostView

class WidgetRenderer(
    private val context: Context,
    private val shader: DefaultShader,
    private val textureManager: TextureManager
) {
    private val widgetBox = Box(shader)
    private val modelMatrix = FloatArray(16)

    fun drawWidgets(
        vPMatrix: FloatArray,
        widgetItems: List<WidgetItem>,
        widgetViews: Map<Int, AppWidgetHostView>,
        frameCount: Int,
        onUpdateTexture: (Runnable) -> Unit
    ) {
        widgetItems.forEach { widget ->
            val view = widgetViews[widget.appWidgetId]
            if (view != null) {
                // Task: Live widget updates. Update texture every 15 frames for smoother animation (e.g. clocks)
                if (widget.textureId <= 0 || (frameCount % 15 == 0)) {
                    updateWidgetTexture(widget, view, onUpdateTexture)
                }
            }
            drawWidget(vPMatrix, widget)
        }
    }

    private fun updateWidgetTexture(widget: WidgetItem, view: View, onUpdateTexture: (Runnable) -> Unit) {
        // Ensure the view is laid out. Even if off-screen, it needs dimensions.
        if (view.width <= 0 || view.height <= 0) {
            val widthSpec = View.MeasureSpec.makeMeasureSpec(1024, View.MeasureSpec.EXACTLY)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(1024, View.MeasureSpec.EXACTLY)
            view.measure(widthSpec, heightSpec)
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        }

        // Request a redraw of the underlying Android view logic
        view.post {
            view.invalidate()
            val w = view.width.coerceAtLeast(1)
            val h = view.height.coerceAtLeast(1)
            
            try {
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                view.draw(canvas)
                
                onUpdateTexture(Runnable {
                    if (widget.textureId <= 0) {
                        widget.textureId = textureManager.loadTextureFromBitmap(bitmap)
                    } else {
                        textureManager.updateTextureFromBitmap(widget.textureId, bitmap)
                    }
                    bitmap.recycle()
                })
            } catch (e: Exception) {
                // Handle potential OOM or other bitmap issues
            }
        }
    }

    private fun drawWidget(vPMatrix: FloatArray, widget: WidgetItem) {
        Matrix.setIdentityM(modelMatrix, 0)
        
        // Task: Offset widgets slightly from the surface to prevent Z-fighting
        val zOffset = 0.02f
        val posX = widget.position[0]
        val posY = widget.position[1]
        val posZ = widget.position[2]
        
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
        
        Matrix.scaleM(modelMatrix, 0, widget.size[0], 1f, widget.size[1])
        widgetBox.draw(vPMatrix, modelMatrix, widget.textureId, floatArrayOf(1f, 1f, 1f, 1.0f))
    }
}
