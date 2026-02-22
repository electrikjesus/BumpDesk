package com.bass.bumpdesk

import android.content.Context
import android.opengl.Matrix
import android.view.MotionEvent
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sqrt

class InteractionManager(
    private val context: Context,
    private val camera: CameraManager
) {
    val lassoPoints = mutableListOf<FloatArray>()
    
    var lastTouchX = 0f
    var lastTouchY = 0f
    var isDragging = false
    private val TOUCH_THRESHOLD = 15f

    var screenWidth = 0
    var screenHeight = 0
    val invertedVPMatrix = FloatArray(16)

    private val undoManager = UndoManager()
    private var dragStartPos: FloatArray? = null
    private var dragStartSurface: BumpItem.Surface? = null

    // For leafing gesture
    private var isLeafing = false
    private var leafStartY = 0f

    // For widget resizing
    private var isResizingWidget = false
    private var resizeWidget: WidgetItem? = null
    private var resizeStartSize: FloatArray? = null
    private var resizeStartPos: FloatArray? = null

    fun handleTouchDown(x: Float, y: Float, sceneState: SceneState): Any? {
        lastTouchX = x
        lastTouchY = y
        isDragging = false
        isLeafing = false
        isResizingWidget = false
        
        val rS = FloatArray(4)
        val rE = FloatArray(4)
        calculateRay(x, y, rS, rE)
        
        // Task: Check for resize handle intersection first
        val widgetHit = findIntersectingWidget(rS, rE, sceneState.widgetItems)
        if (widgetHit != null) {
            val widget = widgetHit.first
            if (isTouchOnResizeHandle(widget, rS, rE)) {
                isResizingWidget = true
                resizeWidget = widget
                resizeStartSize = widget.size.clone()
                resizeStartPos = getWidgetPoint(widget, x, y)
                return widget
            }
        }

        sceneState.selectedItem = findIntersectingItem(rS, rE, sceneState.bumpItems, sceneState.piles)
        sceneState.selectedWidget = widgetHit?.first

        sceneState.selectedItem?.let {
            dragStartPos = it.position.clone()
            dragStartSurface = it.surface
            
            val pile = sceneState.getPileOf(it)
            if (pile != null && !pile.isExpanded) {
                leafStartY = y
            }
            return it
        }

        if (sceneState.selectedItem == null && sceneState.selectedWidget == null && 
            (camera.currentViewMode == CameraManager.ViewMode.DEFAULT || camera.currentViewMode == CameraManager.ViewMode.FLOOR)) { 
            lassoPoints.clear()
            lassoPoints.add(getFloorPoint(x, y)) 
        }
        
        return sceneState.selectedWidget
    }

    private fun isTouchOnResizeHandle(widget: WidgetItem, rS: FloatArray, rE: FloatArray): Boolean {
        val t = getWidgetT(widget, rS, rE)
        if (t < 0) return false
        
        val (u, v) = getWidgetUV(widget, rS, rE, t)
        return u > 0.85f && v > 0.85f
    }

    private fun getWidgetT(widget: WidgetItem, rS: FloatArray, rE: FloatArray): Float {
        return when (widget.surface) {
            BumpItem.Surface.BACK_WALL -> (-9.9f - rS[2]) / (rE[2] - rS[2])
            BumpItem.Surface.LEFT_WALL -> (-9.9f - rS[0]) / (rE[0] - rS[0])
            BumpItem.Surface.RIGHT_WALL -> (9.9f - rS[0]) / (rE[0] - rS[0])
            BumpItem.Surface.FLOOR -> (0.1f - rS[1]) / (rE[1] - rS[1])
        }
    }

    private fun getWidgetUV(widget: WidgetItem, rS: FloatArray, rE: FloatArray, t: Float): Pair<Float, Float> {
        val iX = rS[0] + t * (rE[0] - rS[0])
        val iY = rS[1] + t * (rE[1] - rS[1])
        val iZ = rS[2] + t * (rE[2] - rS[2])
        
        return when (widget.surface) {
            BumpItem.Surface.BACK_WALL -> (iX - (widget.position[0] - widget.size[0])) / (2f * widget.size[0]) to 1f - (iY - (widget.position[1] - widget.size[1])) / (2f * widget.size[1])
            BumpItem.Surface.LEFT_WALL -> (iZ - (widget.position[2] - widget.size[0])) / (2f * widget.size[0]) to 1f - (iY - (widget.position[1] - widget.size[1])) / (2f * widget.size[1])
            BumpItem.Surface.RIGHT_WALL -> 1f - (iZ - (widget.position[2] - widget.size[0])) / (2f * widget.size[0]) to 1f - (iY - (widget.position[1] - widget.size[1])) / (2f * widget.size[1])
            BumpItem.Surface.FLOOR -> (iX - (widget.position[0] - widget.size[0])) / (2f * widget.size[0]) to (iZ - (widget.position[2] - widget.size[1])) / (2f * widget.size[1])
        }
    }

    fun handleTouchMove(x: Float, y: Float, sceneState: SceneState, pointerCount: Int) {
        if (pointerCount > 1) {
            isDragging = false
            isLeafing = false
            isResizingWidget = false
            lassoPoints.clear()
            return
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
                    // isResizingWidget is already set in handleTouchDown
                } else {
                    isDragging = true
                }
            }
        }
        
        val rS = FloatArray(4)
        val rE = FloatArray(4)
        calculateRay(x, y, rS, rE)

        if (isResizingWidget && resizeWidget != null) {
            val point = getWidgetPoint(resizeWidget!!, x, y)
            resizeStartPos?.let { start ->
                val du = point[0] - start[0]
                val dv = point[1] - start[1]
                resizeStartSize?.let { size ->
                    resizeWidget!!.size[0] = (size[0] + du).coerceIn(1.0f, 5.0f)
                    resizeWidget!!.size[1] = (size[1] + dv).coerceIn(1.0f, 5.0f)
                }
            }
            return
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
                }
            }
            return
        }

        if (sceneState.selectedItem != null && isDragging) {
            val item = sceneState.selectedItem!!
            val pile = sceneState.getPileOf(item)
            
            val floorY = if (pile?.isExpanded == true) 3.0f else 0.05f
            val hit = findWallOrFloorHit(rS, rE, floorY)
            hit?.let { (surface, pos) ->
                val raiseOffset = 0.2f
                
                item.velocity[0] = (pos[0] - item.position[0]) * 0.5f
                item.velocity[1] = (pos[1] - item.position[1]) * 0.5f
                item.velocity[2] = (pos[2] - item.position[2]) * 0.5f

                item.surface = surface
                item.position[0] = pos[0]
                item.position[1] = pos[1] + (if (surface == BumpItem.Surface.FLOOR) raiseOffset else 0f)
                item.position[2] = pos[2]
                
                when (surface) {
                    BumpItem.Surface.BACK_WALL -> item.position[2] = pos[2] + raiseOffset
                    BumpItem.Surface.LEFT_WALL -> item.position[0] = pos[0] + raiseOffset
                    BumpItem.Surface.RIGHT_WALL -> item.position[0] = pos[0] - raiseOffset
                    else -> {}
                }
                
                pile?.let { 
                    if (!it.isExpanded) {
                        it.position[0] = pos[0]
                        it.position[1] = pos[1]
                        it.position[2] = pos[2]
                        it.surface = surface
                        
                        it.items.forEach { pileItem ->
                            pileItem.surface = surface
                            pileItem.position[0] = pos[0]
                            pileItem.position[1] = pos[1]
                            pileItem.position[2] = pos[2]
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
    }

    private fun getWidgetPoint(widget: WidgetItem, x: Float, y: Float): FloatArray {
        val rS = FloatArray(4); val rE = FloatArray(4); calculateRay(x, y, rS, rE)
        val t = getWidgetT(widget, rS, rE)
        val iX = rS[0] + t * (rE[0] - rS[0]); val iY = rS[1] + t * (rE[1] - rS[1]); val iZ = rS[2] + t * (rE[2] - rS[2])
        return when (widget.surface) {
            BumpItem.Surface.BACK_WALL -> floatArrayOf(iX, iY)
            BumpItem.Surface.LEFT_WALL -> floatArrayOf(iZ, iY)
            BumpItem.Surface.RIGHT_WALL -> floatArrayOf(-iZ, iY)
            BumpItem.Surface.FLOOR -> floatArrayOf(iX, iZ)
        }
    }

    fun handleTouchUp(sceneState: SceneState, onCaptured: (List<BumpItem>) -> Unit) {
        if (isResizingWidget) {
            isResizingWidget = false
            resizeWidget = null
            return
        }

        if (sceneState.selectedItem != null) {
            val item = sceneState.selectedItem!!
            val pile = sceneState.getPileOf(item)
            
            if (item.surface != BumpItem.Surface.FLOOR) {
                item.isPinned = true
            }

            dragStartPos?.let { startPos ->
                dragStartSurface?.let { startSurface ->
                    if (isDragging && !isLeafing) {
                        undoManager.execute(MoveCommand(item, startPos, startSurface, item.position.clone(), item.surface))
                    }
                }
            }
            dragStartPos = null
            dragStartSurface = null

            if (pile != null && pile.isExpanded) {
                val dx = item.position[0] - pile.position[0]
                val dz = item.position[2] - pile.position[2]
                val side = ceil(sqrt(pile.items.size.toDouble())).toInt().coerceAtLeast(1)
                val spacing = 1.2f
                val halfDim = ((side * spacing) / 2f + 0.5f) * pile.scale
                
                if (abs(dx) > halfDim || abs(dz) > halfDim) {
                    val appInfo = item.appInfo
                    if (appInfo != null) {
                        if (!sceneState.isAlreadyOnDesktop(appInfo)) {
                            pile.items.remove(item)
                            sceneState.bumpItems.add(item)
                            if (item.surface == BumpItem.Surface.FLOOR) item.position[1] = 0.05f
                        }
                    } else {
                        pile.items.remove(item)
                        sceneState.bumpItems.add(item)
                        if (item.surface == BumpItem.Surface.FLOOR) item.position[1] = 0.05f
                    }
                }
            } else if (pile == null && isDragging) {
                val nearbyPile = sceneState.piles.find { p ->
                    if (p.isSystem) return@find false
                    val dX = item.position[0] - p.position[0]
                    val dZ = item.position[2] - p.position[2]
                    val dist = sqrt((dX * dX + dZ * dZ).toDouble())
                    dist < 1.5f
                }
                if (nearbyPile != null) {
                    (context as? LauncherActivity)?.showAddToPileMenu(item, nearbyPile)
                }
            }
        } else if (isDragging && (camera.currentViewMode == CameraManager.ViewMode.DEFAULT || camera.currentViewMode == CameraManager.ViewMode.FLOOR) && lassoPoints.isNotEmpty()) {
            val capturedItems = sceneState.bumpItems.filter { isPointInPolygon(it.position[0], it.position[2], lassoPoints) }
            if (capturedItems.size > 1) {
                onCaptured(capturedItems)
            }
        }
        sceneState.selectedItem = null
        sceneState.selectedWidget = null
        lassoPoints.clear()
        isLeafing = false
        isDragging = false
    }

    fun undo() = undoManager.undo()
    fun redo() = undoManager.redo()

    fun calculateRay(sX: Float, sY: Float, rS: FloatArray, rE: FloatArray) {
        val x = (2f * sX) / screenWidth - 1f
        val y = 1f - (2f * sY) / screenHeight
        val nearPoint = floatArrayOf(x, y, -1f, 1f)
        val farPoint = floatArrayOf(x, y, 1f, 1f)
        
        Matrix.multiplyMV(rS, 0, invertedVPMatrix, 0, nearPoint, 0)
        Matrix.multiplyMV(rE, 0, invertedVPMatrix, 0, farPoint, 0)
        
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
        val dX = item.position[0] - rS[0]; val dY = item.position[1] - rS[1]; val dZ = item.position[2] - rS[2]
        val rDX = rE[0] - rS[0]; val rDY = rE[1] - rS[1]; val rDZ = rE[2] - rS[2]
        val rL = sqrt((rDX*rDX + rDY*rDY + rDZ*rDZ).toDouble()).toFloat()
        val dot = (dX*(rDX/rL) + dY*(rDY/rL) + dZ*(rDZ/rL))
        val pX = rS[0] + dot*(rDX/rL); val pY = rS[1] + dot*(rDY/rL); val pZ = rS[2] + dot*(rDZ/rL)
        val distSq = (pX-item.position[0])*(pX-item.position[0]) + (pY-item.position[1])*(pY-item.position[1]) + (pZ-item.position[2])*(pZ-item.position[2])
        return if (distSq < 1.5f) dot else -1f
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
            BumpItem.Surface.BACK_WALL -> if (abs(iX - widget.position[0]) < widget.size[0] && abs(iY - widget.position[1]) < widget.size[1]) t else -1f
            BumpItem.Surface.LEFT_WALL -> if (abs(iZ - widget.position[2]) < widget.size[0] && abs(iY - widget.position[1]) < widget.size[1]) t else -1f
            BumpItem.Surface.RIGHT_WALL -> if (abs(iZ - widget.position[2]) < widget.size[0] && abs(iY - widget.position[1]) < widget.size[1]) t else -1f
            BumpItem.Surface.FLOOR -> if (abs(iX - widget.position[0]) < widget.size[0] && abs(iZ - widget.position[2]) < widget.size[1]) t else -1f
        }
    }

    fun findWallOrFloorHit(rS: FloatArray, rE: FloatArray, floorY: Float): Pair<BumpItem.Surface, FloatArray>? {
        val surfaces = listOf(BumpItem.Surface.FLOOR, BumpItem.Surface.BACK_WALL, BumpItem.Surface.LEFT_WALL, BumpItem.Surface.RIGHT_WALL)
        var bestSurface: BumpItem.Surface? = null
        var minT = Float.MAX_VALUE
        var bestPos = FloatArray(3)
        
        surfaces.forEach { surface ->
            val t = when (surface) {
                BumpItem.Surface.FLOOR -> (floorY - rS[1]) / (rE[1] - rS[1])
                BumpItem.Surface.BACK_WALL -> (-9.95f - rS[2]) / (rE[2] - rS[2])
                BumpItem.Surface.LEFT_WALL -> (-9.95f - rS[0]) / (rE[0] - rS[0])
                BumpItem.Surface.RIGHT_WALL -> (9.95f - rS[0]) / (rE[0] - rS[0])
            }
            if (t > 0 && t < minT) {
                val hitX = rS[0] + t * (rE[0] - rS[0])
                val hitY = rS[1] + t * (rE[1] - rS[1])
                val hitZ = rS[2] + t * (rE[2] - rS[2])
                val margin = 50.1f // Infinite desk margin
                if (abs(hitX) <= margin && abs(hitZ) <= margin && hitY >= 0f && hitY <= 12f) {
                    minT = t; bestSurface = surface; bestPos = floatArrayOf(hitX, hitY, hitZ)
                }
            }
        }
        return bestSurface?.let { it to bestPos }
    }

    private fun dragWidget(widget: WidgetItem, rS: FloatArray, rE: FloatArray) {
        val hit = findWallOrFloorHit(rS, rE, 0.1f)
        hit?.let { (surface, pos) ->
            widget.surface = surface
            widget.position[0] = pos[0]
            widget.position[1] = pos[1]
            widget.position[2] = pos[2]
            
            when (surface) {
                BumpItem.Surface.BACK_WALL -> widget.position[2] = -9.9f
                BumpItem.Surface.LEFT_WALL -> widget.position[0] = -9.9f
                BumpItem.Surface.RIGHT_WALL -> widget.position[0] = 9.9f
                BumpItem.Surface.FLOOR -> widget.position[1] = 0.1f
            }
        }
    }

    fun getFloorPoint(x: Float, y: Float): FloatArray {
        val rS = FloatArray(4); val rE = FloatArray(4); calculateRay(x, y, rS, rE)
        if (abs(rE[1] - rS[1]) < 0.0001f) return floatArrayOf(0f, 0.05f, 0f)
        val t = -rS[1] / (rE[1] - rS[1])
        return floatArrayOf(rS[0] + t * (rE[0] - rS[0]), 0.05f, rS[2] + t * (rE[2] - rS[2]))
    }

    private fun isPointInPolygon(x: Float, z: Float, poly: List<FloatArray>): Boolean {
        var c = false; var j = poly.size - 1
        for (i in poly.indices) {
            if (((poly[i][2] > z) != (poly[j][2] > z)) && (x < (poly[j][0] - poly[i][0]) * (z - poly[i][2]) / (poly[j][2] - poly[i][2]) + poly[i][0])) c = !c
            j = i
        }
        return c
    }
}
