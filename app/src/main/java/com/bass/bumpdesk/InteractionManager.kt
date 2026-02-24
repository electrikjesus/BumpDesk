package com.bass.bumpdesk

import android.content.Context
import android.view.MotionEvent
import android.appwidget.AppWidgetHostView
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sqrt

class InteractionManager(
    private val context: Context?,
    private val camera: CameraManager
) {
    val lassoPoints = mutableListOf<Vector3>()
    
    var lastTouchX = 0f
    var lastTouchY = 0f
    var isDragging = false
    private val TOUCH_THRESHOLD = 15f

    var screenWidth = 0
    var screenHeight = 0
    val invertedVPMatrix = FloatArray(16)

    private val undoManager = UndoManager()
    private var dragStartPos: Vector3? = null
    private var dragStartSurface: BumpItem.Surface? = null

    // For leafing gesture
    private var isLeafing = false
    private var leafStartY = 0f

    // For widget resizing
    private var isResizingWidget = false
    private var resizeWidget: WidgetItem? = null
    private var resizeStartSize: Vector3? = null
    private var resizeStartPos: Vector3? = null

    // For widget interaction
    private var activeInteractingWidget: WidgetItem? = null
    private var activeWidgetView: AppWidgetHostView? = null
    private var widgetDownTime: Long = 0

    // For lasso suppression
    private var isLassoPending = false
    private var lassoStartPoint: Vector3? = null

    var isInfiniteMode = false
    var roomSize = 30f
    var roomHeight = 30f

    fun handleTouchDown(x: Float, y: Float, sceneState: SceneState): Any? {
        lastTouchX = x
        lastTouchY = y
        isDragging = false
        isLeafing = false
        isResizingWidget = false
        isLassoPending = false
        lassoStartPoint = null
        activeInteractingWidget = null
        activeWidgetView = null
        
        val rS = FloatArray(4)
        val rE = FloatArray(4)
        calculateRay(x, y, rS, rE)
        
        // Check for widget interaction first
        val widgetHit = findIntersectingWidget(rS, rE, sceneState.widgetItems)
        if (widgetHit != null) {
            val widget = widgetHit.first
            if (isTouchOnResizeHandle(widget, rS, rE)) {
                isResizingWidget = true
                resizeWidget = widget
                resizeStartSize = widget.size.copy()
                resizeStartPos = getWidgetPoint(widget, x, y)
                return widget
            } else if (camera.currentViewMode == CameraManager.ViewMode.WIDGET_FOCUS) {
                // In focus mode, we allow direct interaction
                activeInteractingWidget = widget
                activeWidgetView = sceneState.widgetViews[widget.appWidgetId]
                widgetDownTime = System.currentTimeMillis()
                dispatchWidgetTouchEvent(MotionEvent.ACTION_DOWN, x, y)
                return widget
            }
        }

        sceneState.selectedItem = findIntersectingItem(rS, rE, sceneState.bumpItems, sceneState.piles)
        sceneState.selectedWidget = widgetHit?.first

        sceneState.selectedItem?.let {
            dragStartPos = it.transform.position.copy()
            dragStartSurface = it.transform.surface
            
            val pile = sceneState.getPileOf(it)
            if (pile != null && !pile.isExpanded) {
                leafStartY = y
            }
            return it
        }

        if (sceneState.selectedItem == null && sceneState.selectedWidget == null && 
            (camera.currentViewMode == CameraManager.ViewMode.DEFAULT || camera.currentViewMode == CameraManager.ViewMode.FLOOR)) { 
            lassoPoints.clear()
            isLassoPending = true
            lassoStartPoint = getFloorPoint(x, y)
        }
        
        return sceneState.selectedWidget
    }

    fun handleTouchMove(x: Float, y: Float, sceneState: SceneState, pointerCount: Int): Boolean {
        if (pointerCount > 1) {
            // Multi-touch transition: immediately clear single-finger states
            isDragging = false
            isLeafing = false
            isResizingWidget = false
            isLassoPending = false
            lassoStartPoint = null
            if (activeInteractingWidget != null) {
                dispatchWidgetTouchEvent(MotionEvent.ACTION_CANCEL, x, y)
                activeInteractingWidget = null
                activeWidgetView = null
            }
            lassoPoints.clear()
            return false
        }

        if (activeInteractingWidget != null) {
            dispatchWidgetTouchEvent(MotionEvent.ACTION_MOVE, x, y)
            return true
        }

        val dxTouch = abs(x - lastTouchX)
        val dyTouch = abs(y - lastTouchY)
        
        if (dxTouch > TOUCH_THRESHOLD || dyTouch > TOUCH_THRESHOLD) {
            if (!isDragging && !isLeafing && !isResizingWidget) {
                val selectedItem = sceneState.selectedItem
                if (selectedItem != null) {
                    val pile = sceneState.getPileOf(selectedItem)
                    if (pile != null && !pile.isExpanded && dyTouch > dxTouch * 2.5f && dyTouch > 30f) {
                        isLeafing = true
                    } else {
                        isDragging = true
                    }
                } else if (resizeWidget != null && isResizingWidget) {
                    // isResizingWidget is already set
                } else {
                    if (isLassoPending && lassoStartPoint != null) {
                        isDragging = true
                        lassoPoints.add(lassoStartPoint!!)
                        isLassoPending = false
                    }
                }
            }
        }
        
        val rS = FloatArray(4); val rE = FloatArray(4); calculateRay(x, y, rS, rE)

        if (isResizingWidget && resizeWidget != null) {
            val point = getWidgetPoint(resizeWidget!!, x, y)
            resizeStartPos?.let { start ->
                val du = point.x - start.x
                val dv = point.z - start.z
                resizeStartSize?.let { size ->
                    resizeWidget!!.size = size.copy(
                        x = (size.x + du).coerceIn(1.0f, 5.0f),
                        z = (size.z + dv).coerceIn(1.0f, 5.0f)
                    )
                }
            }
            return true
        }

        if (isLeafing) {
            if (abs(y - leafStartY) > 80f) {
                val item = sceneState.selectedItem!!
                val pile = sceneState.getPileOf(item)
                pile?.let {
                    if (y > leafStartY) {
                        it.currentIndex = (it.currentIndex + 1) % it.items.size
                    } else {
                        it.currentIndex = (it.currentIndex - 1 + it.items.size) % it.items.size
                    }
                    leafStartY = y
                    return true
                }
            }
            return false
        }

        if (sceneState.selectedItem != null && isDragging) {
            val item = sceneState.selectedItem!!
            val pile = sceneState.getPileOf(item)
            
            val floorY = if (pile?.isExpanded == true) 2.90f else 0.05f
            val hit = findWallOrFloorHit(rS, rE, floorY)
            hit?.let { (surface, pos) ->
                val raiseOffset = 0.2f
                
                val targetPos = Vector3.fromArray(pos)
                item.transform.velocity = (targetPos - item.transform.position) * 0.5f

                item.transform.surface = surface
                var finalPos = targetPos
                if (surface == BumpItem.Surface.FLOOR) {
                    finalPos = finalPos.copy(y = finalPos.y + raiseOffset)
                } else {
                    when (surface) {
                        BumpItem.Surface.BACK_WALL -> finalPos = finalPos.copy(z = finalPos.z + raiseOffset)
                        BumpItem.Surface.LEFT_WALL -> finalPos = finalPos.copy(x = finalPos.x + raiseOffset)
                        BumpItem.Surface.RIGHT_WALL -> finalPos = finalPos.copy(x = finalPos.x - raiseOffset)
                        else -> {}
                    }
                }
                item.transform.position = finalPos
                
                pile?.let { 
                    if (!it.isExpanded) {
                        it.position = targetPos
                        it.surface = surface
                        it.items.forEach { pileItem ->
                            pileItem.transform.surface = surface
                            pileItem.transform.position = targetPos
                        }
                    }
                }
            }
        } else if (sceneState.selectedWidget != null && isDragging) {
            dragWidget(sceneState.selectedWidget!!, rS, rE)
        } else if (lassoPoints.isNotEmpty() && isDragging) {
            lassoPoints.add(getFloorPoint(x, y))
        }
        
        if (isDragging || isLeafing || isResizingWidget) {
            lastTouchX = x
            lastTouchY = y
        }
        return false
    }

    fun handleTouchUp(sceneState: SceneState, onCaptured: (List<BumpItem>) -> Unit) {
        if (activeInteractingWidget != null) {
            dispatchWidgetTouchEvent(MotionEvent.ACTION_UP, lastTouchX, lastTouchY)
            activeInteractingWidget = null
            activeWidgetView = null
            return
        }

        if (isResizingWidget) {
            isResizingWidget = false
            resizeWidget = null
            return
        }

        if (sceneState.selectedItem != null) {
            val item = sceneState.selectedItem!!
            val pile = sceneState.getPileOf(item)
            
            if (item.transform.surface != BumpItem.Surface.FLOOR) {
                item.transform.isPinned = true
            }

            dragStartPos?.let { startPos ->
                dragStartSurface?.let { startSurface ->
                    if (isDragging && !isLeafing) {
                        undoManager.execute(MoveCommand(item, startPos, startSurface, item.transform.position.copy(), item.transform.surface))
                    }
                }
            }
            dragStartPos = null
            dragStartSurface = null

            if (pile != null && pile.isExpanded) {
                val dx = item.transform.position.x - pile.position.x
                val dz = item.transform.position.z - pile.position.z
                val side = ceil(sqrt(pile.items.size.toDouble())).toInt().coerceAtLeast(1)
                val spacing = 1.2f
                val halfDim = ((side * spacing) / 2f + 0.5f) * pile.scale
                
                if (abs(dx) > halfDim || abs(dz) > halfDim) {
                    val appInfo = item.appData?.appInfo
                    if (appInfo != null) {
                        if (!sceneState.isAlreadyOnDesktop(appInfo)) {
                            pile.items.remove(item)
                            sceneState.bumpItems.add(item)
                            if (item.transform.surface == BumpItem.Surface.FLOOR) item.transform.position = item.transform.position.copy(y = 0.05f)
                        }
                    } else {
                        pile.items.remove(item)
                        sceneState.bumpItems.add(item)
                        if (item.transform.surface == BumpItem.Surface.FLOOR) item.transform.position = item.transform.position.copy(y = 0.05f)
                    }
                }
            } else if (pile == null && isDragging) {
                val nearbyPile = sceneState.piles.find { p ->
                    if (p.isSystem) return@find false
                    val dist = item.transform.position.distance(p.position)
                    dist < 1.5f
                }
                if (nearbyPile != null) {
                    (context as? LauncherActivity)?.showAddToPileMenu(item, nearbyPile)
                }
            }
        } else if (isDragging && (camera.currentViewMode == CameraManager.ViewMode.DEFAULT || camera.currentViewMode == CameraManager.ViewMode.FLOOR) && lassoPoints.isNotEmpty()) {
            val capturedItems = sceneState.bumpItems.filter { isPointInPolygon(it.transform.position.x, it.transform.position.z, lassoPoints) }
            if (capturedItems.size > 1) {
                onCaptured(capturedItems)
            }
        }
        sceneState.selectedItem = null
        sceneState.selectedWidget = null
        isLassoPending = false
        lassoStartPoint = null
        lassoPoints.clear()
        isLeafing = false
        isDragging = false
    }

    private fun dispatchWidgetTouchEvent(action: Int, x: Float, y: Float) {
        val widget = activeInteractingWidget ?: return
        val view = activeWidgetView ?: return
        
        val rS = FloatArray(4); val rE = FloatArray(4); calculateRay(x, y, rS, rE)
        val t = getWidgetT(widget, rS, rE)
        if (t < 0 && action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL) return
        
        val (u, v) = getWidgetUV(widget, rS, rE, t)
        
        view.post {
            val eventTime = System.currentTimeMillis()
            val event = MotionEvent.obtain(widgetDownTime, eventTime, action, u * view.width, v * view.height, 0)
            view.dispatchTouchEvent(event)
            event.recycle()
        }
    }

    private fun isTouchOnResizeHandle(widget: WidgetItem, rS: FloatArray, rE: FloatArray): Boolean {
        val t = getWidgetT(widget, rS, rE)
        if (t < 0) return false
        
        val (u, v) = getWidgetUV(widget, rS, rE, t)
        return u > 0.85f && v > 0.85f
    }

    private fun getWidgetT(widget: WidgetItem, rS: FloatArray, rE: FloatArray): Float {
        return when (widget.surface) {
            BumpItem.Surface.BACK_WALL -> (-roomSize + 0.1f - rS[2]) / (rE[2] - rS[2])
            BumpItem.Surface.LEFT_WALL -> (-roomSize + 0.1f - rS[0]) / (rE[0] - rS[0])
            BumpItem.Surface.RIGHT_WALL -> (roomSize - 0.1f - rS[0]) / (rE[0] - rS[0])
            BumpItem.Surface.FLOOR -> (0.1f - rS[1]) / (rE[1] - rS[1])
        }
    }

    private fun getWidgetUV(widget: WidgetItem, rS: FloatArray, rE: FloatArray, t: Float): Pair<Float, Float> {
        val iX = rS[0] + t * (rE[0] - rS[0])
        val iY = rS[1] + t * (rE[1] - rS[1])
        val iZ = rS[2] + t * (rE[2] - rS[2])
        
        return when (widget.surface) {
            BumpItem.Surface.BACK_WALL -> (iX - (widget.position.x - widget.size.x)) / (2f * widget.size.x) to 1f - (iY - (widget.position.y - widget.size.z)) / (2f * widget.size.z)
            BumpItem.Surface.LEFT_WALL -> (iZ - (widget.position.z - widget.size.x)) / (2f * widget.size.x) to 1f - (iY - (widget.position.y - widget.size.z)) / (2f * widget.size.z)
            BumpItem.Surface.RIGHT_WALL -> 1f - (iZ - (widget.position.z - widget.size.x)) / (2f * widget.size.x) to 1f - (iY - (widget.position.y - widget.size.z)) / (2f * widget.size.z)
            BumpItem.Surface.FLOOR -> (iX - (widget.position.x - widget.size.x)) / (2f * widget.size.x) to (iZ - (widget.position.z - widget.size.z)) / (2f * widget.size.z)
        }
    }

    private fun getWidgetPoint(widget: WidgetItem, x: Float, y: Float): Vector3 {
        val rS = FloatArray(4); val rE = FloatArray(4); calculateRay(x, y, rS, rE)
        val t = getWidgetT(widget, rS, rE)
        val iX = rS[0] + t * (rE[0] - rS[0]); val iY = rS[1] + t * (rE[1] - rS[1]); val iZ = rS[2] + t * (rE[2] - rS[2])
        return Vector3(iX, iY, iZ)
    }

    fun calculateRay(sX: Float, sY: Float, rS: FloatArray, rE: FloatArray) {
        val x = (2f * sX) / screenWidth - 1f
        val y = 1f - (2f * sY) / screenHeight
        val nearPoint = floatArrayOf(x, y, -1f, 1f)
        val farPoint = floatArrayOf(x, y, 1f, 1f)
        
        GLMatrix.multiplyMV(rS, 0, invertedVPMatrix, 0, nearPoint, 0)
        GLMatrix.multiplyMV(rE, 0, invertedVPMatrix, 0, farPoint, 0)
        
        for (i in 0..3) { 
            rS[i] /= rS[3]
            rE[i] /= rE[3] 
        }
    }

    fun findIntersectingItem(rS: FloatArray, rE: FloatArray, bumpItems: List<BumpItem>, piles: List<Pile>): BumpItem? {
        var best: BumpItem? = null
        var minD = Float.MAX_VALUE
        val allItems = bumpItems + piles.flatMap { it.items }
        allItems.forEach { item ->
            val d = checkIntersection(item, rS, rE)
            if (d > 0 && d < minD) { minD = d; best = item }
        }
        return best
    }

    private fun checkIntersection(item: BumpItem, rS: FloatArray, rE: FloatArray): Float {
        val rDX = rE[0] - rS[0]; val rDY = rE[1] - rS[1]; val rDZ = rE[2] - rS[2]
        val rL = sqrt((rDX*rDX + rDY*rDY + rDZ*rDZ).toDouble()).toFloat()
        
        val pos = item.transform.position
        val scale = item.transform.scale
        
        val dX = pos.x - rS[0]; val dY = pos.y - rS[1]; val dZ = pos.z - rS[2]
        val dot = (dX*(rDX/rL) + dY*(rDY/rL) + dZ*(rDZ/rL))
        
        val pX = rS[0] + dot*(rDX/rL); val pY = rS[1] + dot*(rDY/rL); val pZ = rS[2] + dot*(rDZ/rL)
        val distSq = (pX-pos.x)*(pX-pos.x) + (pY-pos.y)*(pY-pos.y) + (pZ-pos.z)*(pZ-pos.z)
        
        // Threshold is now proportional to the item's current scale (with some padding)
        val threshold = (scale * 1.8f) * (scale * 1.8f)
        return if (distSq < threshold) dot else -1f
    }

    fun findIntersectingWidget(rS: FloatArray, rE: FloatArray, widgetItems: List<WidgetItem>): Pair<WidgetItem, Float>? {
        var best: WidgetItem? = null
        var minD = Float.MAX_VALUE
        widgetItems.forEach { widget ->
            val d = checkWidgetIntersection(widget, rS, rE)
            if (d > 0 && d < minD) {
                minD = d
                best = widget
            }
        }
        return best?.let { it to minD }
    }

    private fun checkWidgetIntersection(widget: WidgetItem, rS: FloatArray, rE: FloatArray): Float {
        val t = getWidgetT(widget, rS, rE)
        if (t <= 0) return -1f
        
        val iX = rS[0] + t * (rE[0] - rS[0])
        val iY = rS[1] + t * (rE[1] - rS[1])
        val iZ = rS[2] + t * (rE[2] - rS[2])
        
        return when (widget.surface) {
            BumpItem.Surface.BACK_WALL -> if (!isInfiniteMode && abs(iX - widget.position.x) < widget.size.x && abs(iY - widget.position.y) < widget.size.z) t else -1f
            BumpItem.Surface.LEFT_WALL -> if (!isInfiniteMode && abs(iZ - widget.position.z) < widget.size.x && abs(iY - widget.position.y) < widget.size.z) t else -1f
            BumpItem.Surface.RIGHT_WALL -> if (!isInfiniteMode && abs(iZ - widget.position.z) < widget.size.x && abs(iY - widget.position.y) < widget.size.z) t else -1f
            BumpItem.Surface.FLOOR -> if (abs(iX - widget.position.x) < widget.size.x && abs(iZ - widget.position.z) < widget.size.z) t else -1f
        }
    }

    fun findWallOrFloorHit(rS: FloatArray, rE: FloatArray, floorY: Float): Pair<BumpItem.Surface, FloatArray>? {
        val surfaces = mutableListOf(BumpItem.Surface.FLOOR)
        if (!isInfiniteMode) {
            surfaces.addAll(listOf(BumpItem.Surface.BACK_WALL, BumpItem.Surface.LEFT_WALL, BumpItem.Surface.RIGHT_WALL))
        }
        
        var bestSurface: BumpItem.Surface? = null
        var minT = Float.MAX_VALUE
        var bestPos = FloatArray(3)
        
        surfaces.forEach { surface ->
            val t = when (surface) {
                BumpItem.Surface.FLOOR -> (floorY - rS[1]) / (rE[1] - rS[1])
                BumpItem.Surface.BACK_WALL -> (-roomSize + 0.05f - rS[2]) / (rE[2] - rS[2])
                BumpItem.Surface.LEFT_WALL -> (-roomSize + 0.05f - rS[0]) / (rE[0] - rS[0])
                BumpItem.Surface.RIGHT_WALL -> (roomSize - 0.05f - rS[0]) / (rE[0] - rS[0])
            }
            if (t > 0 && t < minT) {
                val hitX = rS[0] + t * (rE[0] - rS[0])
                val hitY = rS[1] + t * (rE[1] - rS[1])
                val hitZ = rS[2] + t * (rE[2] - rS[2])
                
                if (isInfiniteMode && surface == BumpItem.Surface.FLOOR) {
                    minT = t; bestSurface = surface; bestPos = floatArrayOf(hitX, hitY, hitZ)
                } else if (!isInfiniteMode) {
                    val margin = roomSize + 0.1f
                    if (abs(hitX) <= margin && abs(hitZ) <= margin && hitY >= 0f && hitY <= roomHeight) {
                        minT = t; bestSurface = surface; bestPos = floatArrayOf(hitX, hitY, hitZ)
                    }
                }
            }
        }
        return bestSurface?.let { it to bestPos }
    }

    fun dragWidget(widget: WidgetItem, rS: FloatArray, rE: FloatArray) {
        val hit = findWallOrFloorHit(rS, rE, 0.1f)
        hit?.let { (surface, pos) ->
            widget.surface = surface
            widget.position = Vector3.fromArray(pos)
            
            when (surface) {
                BumpItem.Surface.BACK_WALL -> widget.position = widget.position.copy(z = -roomSize + 0.1f)
                BumpItem.Surface.LEFT_WALL -> widget.position = widget.position.copy(x = -roomSize + 0.1f)
                BumpItem.Surface.RIGHT_WALL -> widget.position = widget.position.copy(x = roomSize - 0.1f)
                BumpItem.Surface.FLOOR -> widget.position = widget.position.copy(y = 0.1f)
            }
        }
    }

    fun getFloorPoint(x: Float, y: Float): Vector3 {
        val rS = FloatArray(4); val rE = FloatArray(4); calculateRay(x, y, rS, rE)
        if (abs(rE[1] - rS[1]) < 0.0001f) return Vector3(0f, 0.05f, 0f)
        val t = -rS[1] / (rE[1] - rS[1])
        return Vector3(rS[0] + t * (rE[0] - rS[0]), 0.05f, rS[2] + t * (rE[2] - rS[2]))
    }

    private fun isPointInPolygon(x: Float, z: Float, poly: List<Vector3>): Boolean {
        var c = false; var j = poly.size - 1
        for (i in poly.indices) {
            if (((poly[i].z > z) != (poly[j].z > z)) && (x < (poly[j].x - poly[i].x) * (z - poly[i].z) / (poly[j].z - poly[i].z) + poly[i].x)) c = !c
            j = i
        }
        return c
    }
    
    fun undo() = undoManager.undo()
    fun redo() = undoManager.redo()
}
