package com.bass.bumpdesk

import android.opengl.Matrix

class CameraManager {
    val DEFAULT_CAMERA_POS = floatArrayOf(0f, 8f, 13f)
    val DEFAULT_CAMERA_LOOKAT = floatArrayOf(0f, 0f, 0f)

    var targetPos = DEFAULT_CAMERA_POS.clone()
    var currentPos = DEFAULT_CAMERA_POS.clone()
    var targetLookAt = DEFAULT_CAMERA_LOOKAT.clone()
    var currentLookAt = DEFAULT_CAMERA_LOOKAT.clone()
    var zoomLevel = 1.0f

    private var savedPos = DEFAULT_CAMERA_POS.clone()
    private var savedLookAt = DEFAULT_CAMERA_LOOKAT.clone()
    private var savedViewMode = ViewMode.DEFAULT

    enum class ViewMode { DEFAULT, FLOOR, BACK_WALL, LEFT_WALL, RIGHT_WALL, FOLDER_EXPANDED, WIDGET_FOCUS }
    var currentViewMode = ViewMode.DEFAULT

    fun update() {
        for (i in 0..2) {
            // Camera position relative to lookAt
            val relX = targetPos[0] - targetLookAt[0]
            val relY = targetPos[1] - targetLookAt[1]
            val relZ = targetPos[2] - targetLookAt[2]
            
            val zoomedTargetPosX = targetLookAt[0] + relX * zoomLevel
            val zoomedTargetPosY = targetLookAt[1] + relY * zoomLevel
            val zoomedTargetPosZ = targetLookAt[2] + relZ * zoomLevel

            currentPos[0] += (zoomedTargetPosX - currentPos[0]) * 0.1f
            currentPos[1] += (zoomedTargetPosY - currentPos[1]) * 0.1f
            currentPos[2] += (zoomedTargetPosZ - currentPos[2]) * 0.1f
            
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
        currentViewMode = ViewMode.DEFAULT
    }

    fun restorePreviousView() {
        targetPos = savedPos.clone()
        targetLookAt = savedLookAt.clone()
        currentViewMode = savedViewMode
    }

    private fun saveCurrentView() {
        if (currentViewMode != ViewMode.FOLDER_EXPANDED && currentViewMode != ViewMode.WIDGET_FOCUS) {
            savedPos = targetPos.clone()
            savedLookAt = targetLookAt.clone()
            savedViewMode = currentViewMode
        }
    }

    fun handlePan(dx: Float, dz: Float) {
        targetPos[0] += dx
        targetPos[2] += dz
        targetLookAt[0] += dx
        targetLookAt[2] += dz
    }

    fun focusOnWall(wall: CameraManager.ViewMode, pos: FloatArray, lookAt: FloatArray) {
        saveCurrentView()
        targetPos = pos
        targetLookAt = lookAt
        currentViewMode = wall
    }

    fun focusOnFloor() {
        saveCurrentView()
        targetPos = floatArrayOf(0f, 12f, 0.1f)
        targetLookAt = floatArrayOf(0f, 0f, 0f)
        currentViewMode = ViewMode.FLOOR
    }

    fun focusOnFolder(folderPos: FloatArray) {
        saveCurrentView()
        targetPos = floatArrayOf(folderPos[0], 11f, folderPos[2] + 2f)
        targetLookAt = floatArrayOf(folderPos[0], 3f, folderPos[2])
        currentViewMode = ViewMode.FOLDER_EXPANDED
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
            else -> {}
        }
        currentViewMode = ViewMode.WIDGET_FOCUS
    }

    fun enterFolderMode() {
        currentViewMode = ViewMode.FOLDER_EXPANDED
    }
}
