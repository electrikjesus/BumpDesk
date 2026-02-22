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
            } catch (e: Exception) {}
        }
    }

    private fun drawWidget(vPMatrix: FloatArray, widget: WidgetItem, isSelected: Boolean) {
        Matrix.setIdentityM(modelMatrix, 0)
        
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
        
        val savedModelMatrix = modelMatrix.clone()
        Matrix.scaleM(modelMatrix, 0, widget.size[0], 1f, widget.size[1])
        widgetBox.draw(vPMatrix, modelMatrix, widget.textureId, floatArrayOf(1f, 1f, 1f, 1.0f))

        // Task: Draw resizing handle if selected
        if (isSelected) {
            drawResizeHandle(vPMatrix, savedModelMatrix, widget.size)
        }
    }

    private fun drawResizeHandle(vPMatrix: FloatArray, baseModelMatrix: FloatArray, size: FloatArray) {
        val handleMatrix = baseModelMatrix.clone()
        // Position handle at bottom-right corner
        Matrix.translateM(handleMatrix, 0, size[0] - 0.2f, 0.01f, size[1] - 0.2f)
        Matrix.scaleM(handleMatrix, 0, 0.2f, 1f, 0.2f)
        
        val selectionColor = ThemeManager.getSelectionColor()
        handlePlane.draw(vPMatrix, handleMatrix, floatArrayOf(selectionColor[0], selectionColor[1], selectionColor[2], 1.0f), -1, floatArrayOf(0f, 10f, 0f), 1.0f, false)
    }
}
