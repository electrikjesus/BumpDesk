package com.bass.bumpdesk

import android.opengl.Matrix
import kotlin.math.*

class CameraManager {
    val DEFAULT_CAMERA_POS = floatArrayOf(0f, 8f, 13f)
    val DEFAULT_CAMERA_LOOKAT = floatArrayOf(0f, 0f, 0f)
    val MAX_Z = 13.0f
    val MAX_Y = 15.0f

    var targetPos = DEFAULT_CAMERA_POS.clone()
    var currentPos = DEFAULT_CAMERA_POS.clone()
    var targetLookAt = DEFAULT_CAMERA_LOOKAT.clone()
    var currentLookAt = DEFAULT_CAMERA_LOOKAT.clone()
    var zoomLevel = 1.0f
    var fieldOfView = 60f
    var isInfiniteMode = false

    private var savedPos = DEFAULT_CAMERA_POS.clone()
    private var savedLookAt = DEFAULT_CAMERA_LOOKAT.clone()
    private var savedViewMode = ViewMode.DEFAULT

    enum class ViewMode { DEFAULT, FLOOR, BACK_WALL, LEFT_WALL, RIGHT_WALL, FOLDER_EXPANDED, WIDGET_FOCUS }
    var currentViewMode = ViewMode.DEFAULT

    fun update() {
        // Apply constraints in non-infinite mode
        if (!isInfiniteMode && currentViewMode == ViewMode.DEFAULT) {
            val overflowZ = targetPos[2] - MAX_Z
            val overflowY = targetPos[1] - MAX_Y
            
            if (overflowZ > 0 || overflowY > 0) {
                // Adjust FOV to fit more scene instead of moving camera back
                val overflow = max(overflowZ, overflowY)
                fieldOfView = (60f + overflow * 2f).coerceIn(60f, 100f)
                targetPos[2] = targetPos[2].coerceAtMost(MAX_Z)
                targetPos[1] = targetPos[1].coerceAtMost(MAX_Y)
            } else {
                fieldOfView = 60f
            }
        } else {
            fieldOfView = 60f
        }

        val relX = targetPos[0] - targetLookAt[0]
        val relY = targetPos[1] - targetLookAt[1]
        val relZ = targetPos[2] - targetLookAt[2]
        
        val zoomedTargetPosX = targetLookAt[0] + relX * zoomLevel
        val zoomedTargetPosY = targetLookAt[1] + relY * zoomLevel
        val zoomedTargetPosZ = targetLookAt[2] + relZ * zoomLevel

        for (i in 0..2) {
            val targetP = when(i) {
                0 -> zoomedTargetPosX
                1 -> zoomedTargetPosY
                else -> zoomedTargetPosZ
            }
            currentPos[i] += (targetP - currentPos[i]) * 0.1f
            currentLookAt[i] += (targetLookAt[i] - currentLookAt[i]) * 0.1f
        }
    }

    fun setViewMatrix(viewMatrix: FloatArray) {
        Matrix.setLookAtM(viewMatrix, 0,
            currentPos[0], currentPos[1], currentPos[2],
            currentLookAt[0], currentLookAt[1], currentLookAt[2],
            0f, 1.0f, 0.0f)
    }

    fun reset() {
        targetPos = DEFAULT_CAMERA_POS.clone()
        targetLookAt = DEFAULT_CAMERA_LOOKAT.clone()
        zoomLevel = 1.0f
        fieldOfView = 60f
        currentViewMode = ViewMode.DEFAULT
    }

    fun restorePreviousView() {
        targetPos = savedPos.clone()
        targetLookAt = savedLookAt.clone()
        currentViewMode = savedViewMode
        zoomLevel = 1.0f
        fieldOfView = 60f
    }

    private fun saveCurrentView() {
        if (currentViewMode != ViewMode.FOLDER_EXPANDED && currentViewMode != ViewMode.WIDGET_FOCUS) {
            savedPos = targetPos.clone()
            savedLookAt = targetLookAt.clone()
            savedViewMode = currentViewMode
        }
    }

    fun handlePan(dx: Float, dy: Float) {
        val s = 0.015f * zoomLevel
        
        when (currentViewMode) {
            ViewMode.BACK_WALL -> {
                targetPos[0] -= dx * s
                targetPos[1] += dy * s
                targetLookAt[0] -= dx * s
                targetLookAt[1] += dy * s
            }
            ViewMode.LEFT_WALL -> {
                targetPos[2] -= dx * s
                targetPos[1] += dy * s
                targetLookAt[2] -= dx * s
                targetLookAt[1] += dy * s
            }
            ViewMode.RIGHT_WALL -> {
                targetPos[2] += dx * s
                targetPos[1] += dy * s
                targetLookAt[2] += dx * s
                targetLookAt[1] += dy * s
            }
            ViewMode.FLOOR -> {
                targetPos[0] -= dx * s
                targetPos[2] -= dy * s
                targetLookAt[0] -= dx * s
                targetLookAt[2] -= dy * s
            }
            else -> {
                targetPos[0] -= dx * s
                targetPos[2] -= dy * s
                targetLookAt[0] -= dx * s
                targetLookAt[2] -= dy * s
            }
        }
    }

    fun handleTilt(dy: Float) {
        if (currentViewMode != ViewMode.DEFAULT && currentViewMode != ViewMode.FLOOR) return
        
        val tiltSpeed = 0.05f
        targetPos[1] = (targetPos[1] + dy * tiltSpeed).coerceIn(2f, 25f)
        targetPos[2] = (targetPos[2] - dy * tiltSpeed).coerceIn(2f, 25f)
    }

    fun focusOnWall(wall: CameraManager.ViewMode, pos: FloatArray, lookAt: FloatArray) {
        saveCurrentView()
        targetPos = pos.clone()
        targetLookAt = lookAt.clone()
        currentViewMode = wall
        zoomLevel = 1.0f
        fieldOfView = 60f
    }

    fun focusOnFloor() {
        saveCurrentView()
        targetPos = floatArrayOf(0f, 12f, 0.1f)
        targetLookAt = floatArrayOf(0f, 0f, 0f)
        currentViewMode = ViewMode.FLOOR
        zoomLevel = 1.0f
        fieldOfView = 60f
    }

    fun focusOnFolder(folderPos: FloatArray) {
        saveCurrentView()
        targetPos = floatArrayOf(folderPos[0], 18f, folderPos[2] + 6f)
        targetLookAt = floatArrayOf(folderPos[0], 2.90f, folderPos[2])
        currentViewMode = ViewMode.FOLDER_EXPANDED
        zoomLevel = 1.0f
        fieldOfView = 60f
    }

    fun focusOnWidget(widget: WidgetItem) {
        saveCurrentView()
        val dist = 4f
        when (widget.surface) {
            BumpItem.Surface.BACK_WALL -> {
                targetPos = floatArrayOf(widget.position[0], widget.position[1], widget.position[2] + dist)
                targetLookAt = floatArrayOf(widget.position[0], widget.position[1], widget.position[2])
            }
            BumpItem.Surface.LEFT_WALL -> {
                targetPos = floatArrayOf(widget.position[0] + dist, widget.position[1], widget.position[2])
                targetLookAt = floatArrayOf(widget.position[0], widget.position[1], widget.position[2])
            }
            BumpItem.Surface.RIGHT_WALL -> {
                targetPos = floatArrayOf(widget.position[0] - dist, widget.position[1], widget.position[2])
                targetLookAt = floatArrayOf(widget.position[0], widget.position[1], widget.position[2])
            }
            else -> {
                targetPos = floatArrayOf(widget.position[0], 8f, widget.position[2] + 4f)
                targetLookAt = floatArrayOf(widget.position[0], 0.1f, widget.position[2])
            }
        }
        currentViewMode = ViewMode.WIDGET_FOCUS
        zoomLevel = 1.0f
        fieldOfView = 60f
    }
}
