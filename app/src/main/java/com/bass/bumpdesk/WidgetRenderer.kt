package com.bass.bumpdesk

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.opengl.GLES20
import android.opengl.Matrix
import android.view.View

class WidgetRenderer(
    private val context: Context,
    private val shader: DefaultShader,
    private val textureManager: TextureManager
) {
    private val widgetBox = Box(shader)
    private val handlePlane = Plane(shader)
    private val modelMatrix = FloatArray(16)
    
    private var captureBitmap: Bitmap? = null
    private var captureCanvas: Canvas? = null
    private val configuredCaptureSizes = mutableMapOf<Int, Pair<Int, Int>>()
    private val lastUploadSizes = mutableMapOf<Int, Pair<Int, Int>>()
    private var moveHandleTextureId = -1
    private var resizeHandleTextureId = -1

    fun drawWidgets(
        vPMatrix: FloatArray,
        widgetItems: List<WidgetItem>,
        widgetViews: Map<Int, AppWidgetHostView>,
        frameCount: Int,
        selectedWidget: WidgetItem?,
        runOnUiThread: (Runnable) -> Unit,
        onUpdateTexture: (Runnable) -> Unit,
    ) {
        widgetItems.forEach { widget ->
            val view = widgetViews[widget.appWidgetId]
            if (view == null) {
                if (frameCount % 120 == 0) {
                    BumpDeskLog.w(
                        BumpDeskLog.Tag.WIDGET,
                        "drawWidgets",
                        "missing host view id=${widget.appWidgetId} size=${widget.size.x}x${widget.size.z}",
                    )
                }
            } else {
                val targetSize = AppWidgetManager.getInstance(context)
                    .getAppWidgetInfo(widget.appWidgetId)
                    ?.let { WidgetUtils.captureSizePx(context, widget, it) }
                val layoutStale = targetSize != null &&
                    configuredCaptureSizes[widget.appWidgetId] != targetSize
                val needsInitialTexture = widget.textureId <= 0
                val periodicRefresh = widget.textureId > 0 && frameCount % 90 == 0
                if (needsInitialTexture || layoutStale || periodicRefresh) {
                    val force = needsInitialTexture
                    if (WidgetCaptureCoordinator.shouldCapture(widget.appWidgetId, force = force)) {
                        runOnUiThread(Runnable {
                            captureWidgetTexture(widget, view, onUpdateTexture)
                        })
                    }
                }
            }
            drawWidgetBody(vPMatrix, widget)
        }

        selectedWidget?.let { widget ->
            if (widget.textureId > 0) {
                drawWidgetChrome(vPMatrix, widget)
            }
        }
    }

    fun invalidateLayoutForWidget(appWidgetId: Int) {
        configuredCaptureSizes.remove(appWidgetId)
        lastUploadSizes.remove(appWidgetId)
    }

    private fun uploadWidgetTexture(
        widget: WidgetItem,
        upload: Bitmap,
        cropped: Bitmap,
        onUpdateTexture: (Runnable) -> Unit,
    ) {
        val uploadSize = upload.width to upload.height
        val glBitmap = upload.copy(Bitmap.Config.ARGB_8888, false)

        if (upload !== captureBitmap && upload !== cropped) {
            upload.recycle()
        }
        if (cropped !== captureBitmap) {
            cropped.recycle()
        }

        onUpdateTexture(Runnable {
            try {
                if (glBitmap.isRecycled) return@Runnable
                val existingId = widget.textureId
                val canUpdate = existingId > 0 && lastUploadSizes[widget.appWidgetId] == uploadSize
                if (canUpdate) {
                    textureManager.updateTextureFromBitmap(existingId, glBitmap)
                } else {
                    if (existingId > 0) {
                        textureManager.deleteTexture(existingId)
                    }
                    widget.textureId = textureManager.loadTextureFromBitmap(glBitmap)
                    lastUploadSizes[widget.appWidgetId] = uploadSize
                    BumpDeskLog.d(
                        BumpDeskLog.Tag.WIDGET,
                        "createTexture",
                        "id=${widget.appWidgetId} tex=${widget.textureId} crop=${glBitmap.width}x${glBitmap.height} quad=${widget.size.x}x${widget.size.z}",
                    )
                }
            } finally {
                if (!glBitmap.isRecycled) {
                    glBitmap.recycle()
                }
                WidgetCaptureCoordinator.markCaptureFinished(widget.appWidgetId)
            }
        })
    }

    private fun ensureHandleTextures() {
        if (moveHandleTextureId <= 0) {
            val bitmap = TextRenderer.createMaterialHandleBitmap(
                glyph = "⠿",
                backgroundColor = WidgetHandleStyle.moveBackground,
                foregroundColor = WidgetHandleStyle.moveForeground,
                strokeColor = WidgetHandleStyle.strokeColor,
            )
            moveHandleTextureId = textureManager.loadTextureFromBitmap(bitmap)
            bitmap.recycle()
        }
        if (resizeHandleTextureId <= 0) {
            val bitmap = TextRenderer.createMaterialHandleBitmap(
                glyph = "⤡",
                backgroundColor = WidgetHandleStyle.resizeBackground,
                foregroundColor = WidgetHandleStyle.resizeForeground,
                strokeColor = WidgetHandleStyle.strokeColor,
            )
            resizeHandleTextureId = textureManager.loadTextureFromBitmap(bitmap)
            bitmap.recycle()
        }
    }

    private fun captureWidgetTexture(
        widget: WidgetItem,
        view: AppWidgetHostView,
        onUpdateTexture: (Runnable) -> Unit,
    ) {
        WidgetCaptureCoordinator.markCaptureStarted(widget.appWidgetId)

        try {
            val info = AppWidgetManager.getInstance(context).getAppWidgetInfo(widget.appWidgetId)
            if (info != null) {
                val targetSize = WidgetUtils.captureSizePx(context, widget, info)
                if (configuredCaptureSizes[widget.appWidgetId] != targetSize ||
                    view.width != targetSize.first ||
                    view.height != targetSize.second
                ) {
                    WidgetUtils.configureHostView(view, context, info, widget)
                    configuredCaptureSizes[widget.appWidgetId] = targetSize
                }
            } else if (view.width <= 0 || view.height <= 0) {
                val aspect = widget.aspectRatio.coerceIn(0.35f, 3.5f)
                val h = 512
                val w = (h * aspect).toInt().coerceIn(320, 1024)
                val widthSpec = View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY)
                val heightSpec = View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
                view.measure(widthSpec, heightSpec)
                view.layout(0, 0, view.measuredWidth, view.measuredHeight)
            }

            view.invalidate()
            val w = view.width.coerceAtLeast(1)
            val h = view.height.coerceAtLeast(1)

            if (captureBitmap == null || captureBitmap!!.width != w || captureBitmap!!.height != h) {
                captureBitmap?.recycle()
                captureBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                captureCanvas = Canvas(captureBitmap!!)
            }

            captureCanvas?.let { canvas ->
                canvas.drawColor(0)
                view.draw(canvas)

                val uploadSource = if (info != null && WidgetUtils.supportsFreeformResize(info)) {
                    captureBitmap!!
                } else {
                    TextureUtils.centerCropToAspect(
                        captureBitmap!!,
                        widget.size.x,
                        widget.size.z,
                    )
                }
                val upload = TextureUtils.prepareBitmapForGl(uploadSource)
                uploadWidgetTexture(widget, upload, uploadSource, onUpdateTexture)
            }
        } catch (e: Exception) {
            WidgetCaptureCoordinator.markCaptureFinished(widget.appWidgetId)
            BumpDeskLog.fail(
                BumpDeskLog.Tag.WIDGET,
                "captureWidgetTexture",
                "id=${widget.appWidgetId} ${e.message}",
                e,
            )
        }
    }

    private fun widgetBaseMatrix(widget: WidgetItem): FloatArray {
        Matrix.setIdentityM(modelMatrix, 0)
        val zOffset = 0.02f
        when (widget.surface) {
            BumpItem.Surface.BACK_WALL -> {
                Matrix.translateM(modelMatrix, 0, widget.position.x, widget.position.y, widget.position.z + zOffset)
                Matrix.rotateM(modelMatrix, 0, 180f, 0f, 1f, 0f)
                Matrix.rotateM(modelMatrix, 0, 90f, 1f, 0f, 0f)
            }
            BumpItem.Surface.LEFT_WALL -> {
                Matrix.translateM(modelMatrix, 0, widget.position.x + zOffset, widget.position.y, widget.position.z)
                Matrix.rotateM(modelMatrix, 0, 90f, 0f, 1f, 0f)
                Matrix.rotateM(modelMatrix, 0, 90f, 1f, 0f, 0f)
            }
            BumpItem.Surface.RIGHT_WALL -> {
                Matrix.translateM(modelMatrix, 0, widget.position.x - zOffset, widget.position.y, widget.position.z)
                Matrix.rotateM(modelMatrix, 0, -90f, 0f, 1f, 0f)
                Matrix.rotateM(modelMatrix, 0, 90f, 1f, 0f, 0f)
            }
            BumpItem.Surface.FLOOR -> {
                Matrix.translateM(modelMatrix, 0, widget.position.x, widget.position.y + zOffset, widget.position.z)
            }
        }
        return modelMatrix.clone()
    }

    private fun drawWidgetBody(vPMatrix: FloatArray, widget: WidgetItem) {
        if (widget.textureId <= 0) return

        val display = widget.displayHalfSize()
        val base = widgetBaseMatrix(widget)
        Matrix.setIdentityM(modelMatrix, 0)
        System.arraycopy(base, 0, modelMatrix, 0, 16)
        Matrix.scaleM(modelMatrix, 0, display.x, 1f, display.z)
        widgetBox.draw(vPMatrix, modelMatrix, widget.textureId, floatArrayOf(1f, 1f, 1f, 1.0f), isAnimated = false)
    }

    private fun drawWidgetChrome(vPMatrix: FloatArray, widget: WidgetItem) {
        ensureHandleTextures()
        val base = widgetBaseMatrix(widget)
        val display = widget.displayHalfSize()

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        drawHandle(vPMatrix, base, display, WidgetHandleStyle.Kind.MOVE, moveHandleTextureId)
        drawHandle(vPMatrix, base, display, WidgetHandleStyle.Kind.RESIZE, resizeHandleTextureId)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    }

    private fun drawHandle(
        vPMatrix: FloatArray,
        baseModelMatrix: FloatArray,
        size: Vector3,
        kind: WidgetHandleStyle.Kind,
        textureId: Int,
    ) {
        val (cx, lift, cz) = WidgetHandleStyle.handleCenter(size, kind)
        val handleSize = WidgetHandleStyle.handleSizeForWidget(size)
        Matrix.setIdentityM(modelMatrix, 0)
        System.arraycopy(baseModelMatrix, 0, modelMatrix, 0, 16)
        Matrix.translateM(modelMatrix, 0, cx, lift, cz)
        Matrix.scaleM(modelMatrix, 0, handleSize, 1f, handleSize)
        handlePlane.draw(
            vPMatrix,
            modelMatrix,
            floatArrayOf(1f, 1f, 1f, 1f),
            textureId,
            floatArrayOf(0f, 10f, 0f),
            1.0f,
            useLighting = false,
            isAnimated = false,
        )
    }
}
