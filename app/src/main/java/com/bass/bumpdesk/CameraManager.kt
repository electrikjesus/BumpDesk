package com.bass.bumpdesk

import android.opengl.Matrix
import kotlin.math.*

class CameraManager {
    val ABSOLUTE_DEFAULT_POS = floatArrayOf(0f, 12f, 25f)
    val ABSOLUTE_DEFAULT_LOOKAT = floatArrayOf(0f, 0f, 5f)
    
    // Configurable defaults
    var customDefaultPos = ABSOLUTE_DEFAULT_POS.clone()
    var customDefaultLookAt = ABSOLUTE_DEFAULT_LOOKAT.clone()

    // Boundaries matching new RoomRenderer geometry (30f size)
    var MAX_Z = 29.0f
    var MAX_Y = 29.0f
    var MIN_Z = -29.0f
    var MIN_X = -29.0f
    var MAX_X = 29.0f

    var targetPos = customDefaultPos.clone()
    var currentPos = customDefaultPos.clone()
    var targetLookAt = customDefaultLookAt.clone()
    var currentLookAt = customDefaultLookAt.clone()
    var zoomLevel = 1.0f
    var currentZoomLevel = 1.0f
        private set
    var fieldOfView = 60f
    var baseFieldOfView = 60f
    var customDefaultZoomLevel = 1.0f
    var isInfiniteMode = false
    var isFlatFloorMode = false

    private var savedPos = customDefaultPos.clone()
    private var savedLookAt = customDefaultLookAt.clone()
    private var savedViewMode = ViewMode.DEFAULT
    private var savedZoomLevel = 1.0f
    private var savedFieldOfView = 60f

    private var animationLogFrames = 0
    private var wasAnimating = false

    var onBoundaryHit: (() -> Unit)? = null
    private var wasAtBoundary = false

    enum class ViewMode { DEFAULT, FLOOR, BACK_WALL, LEFT_WALL, RIGHT_WALL, FOLDER_EXPANDED, WIDGET_FOCUS }
    var currentViewMode = ViewMode.DEFAULT

    fun update() {
        // Apply focal length shift (FOV) when boundaries are reached in any mode
        if (!isInfiniteMode) {
            val overflowZ = max(0f, abs(targetPos[2]) - MAX_Z)
            val overflowY = max(0f, targetPos[1] - MAX_Y)
            val overflowX = max(0f, abs(targetPos[0]) - MAX_X)
            
            val totalOverflow = max(max(overflowZ, overflowY), overflowX)
            if (totalOverflow > 0) {
                // Adjust FOV to fit more scene instead of moving camera back/out
                fieldOfView = (baseFieldOfView + totalOverflow * 2.5f).coerceIn(baseFieldOfView, 120f)
                
                // Clamp physical position to stay inside walls
                clampTargetToRoom()

                if (!wasAtBoundary) {
                    onBoundaryHit?.invoke()
                    wasAtBoundary = true
                }
            } else {
                fieldOfView = baseFieldOfView
                wasAtBoundary = false
            }
        } else {
            fieldOfView = baseFieldOfView
            wasAtBoundary = false
        }

        currentZoomLevel += (zoomLevel - currentZoomLevel) * 0.1f

        val relX = targetPos[0] - targetLookAt[0]
        val relY = targetPos[1] - targetLookAt[1]
        val relZ = targetPos[2] - targetLookAt[2]
        
        val zoomedTargetPosX = targetLookAt[0] + relX * currentZoomLevel
        val zoomedTargetPosY = targetLookAt[1] + relY * currentZoomLevel
        val zoomedTargetPosZ = targetLookAt[2] + relZ * currentZoomLevel

        for (i in 0..2) {
            val targetP = when(i) {
                0 -> zoomedTargetPosX
                1 -> zoomedTargetPosY
                else -> zoomedTargetPosZ
            }
            currentPos[i] += (targetP - currentPos[i]) * 0.1f
            currentLookAt[i] += (targetLookAt[i] - currentLookAt[i]) * 0.1f
        }

        trackAnimation()
    }

    fun isAnimating(): Boolean {
        val zoomed = zoomedEyePosition()
        return distance(currentPos, zoomed) > 0.05f ||
            distance(currentLookAt, targetLookAt) > 0.05f ||
            abs(currentZoomLevel - zoomLevel) > 0.005f
    }

    private fun zoomedEyePosition(): FloatArray {
        val relX = targetPos[0] - targetLookAt[0]
        val relY = targetPos[1] - targetLookAt[1]
        val relZ = targetPos[2] - targetLookAt[2]
        return floatArrayOf(
            targetLookAt[0] + relX * currentZoomLevel,
            targetLookAt[1] + relY * currentZoomLevel,
            targetLookAt[2] + relZ * currentZoomLevel,
        )
    }

    private fun distance(a: FloatArray, b: FloatArray): Float {
        val dx = a[0] - b[0]
        val dy = a[1] - b[1]
        val dz = a[2] - b[2]
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun trackAnimation() {
        val animating = isAnimating()
        if (animating) {
            animationLogFrames++
            if (animationLogFrames == 1 || animationLogFrames % 8 == 0) {
                CameraDiagnostics.logAnimation(this, "progress")
            }
        } else if (wasAnimating) {
            CameraDiagnostics.logAnimation(this, "complete")
            animationLogFrames = 0
        }
        wasAnimating = animating
    }

    fun setViewMatrix(viewMatrix: FloatArray) {
        Matrix.setLookAtM(viewMatrix, 0,
            currentPos[0], currentPos[1], currentPos[2],
            currentLookAt[0], currentLookAt[1], currentLookAt[2],
            0f, 1.0f, 0.0f)
    }

    fun reset() {
        targetPos = customDefaultPos.clone()
        targetLookAt = customDefaultLookAt.clone()
        zoomLevel = customDefaultZoomLevel
        fieldOfView = baseFieldOfView
        currentViewMode = if (isFlatFloorMode) ViewMode.FLOOR else ViewMode.DEFAULT
        snapToTargets()
    }

    fun snapToTargets() {
        clampTargetToRoom()
        val relX = targetPos[0] - targetLookAt[0]
        val relY = targetPos[1] - targetLookAt[1]
        val relZ = targetPos[2] - targetLookAt[2]
        currentPos[0] = targetLookAt[0] + relX * currentZoomLevel
        currentPos[1] = targetLookAt[1] + relY * currentZoomLevel
        currentPos[2] = targetLookAt[2] + relZ * currentZoomLevel
        currentLookAt = targetLookAt.clone()
        currentZoomLevel = zoomLevel
    }

    private fun clampTargetToRoom() {
        if (isInfiniteMode) return
        targetPos[2] = targetPos[2].coerceIn(-MAX_Z, MAX_Z)
        targetPos[1] = targetPos[1].coerceIn(1f, MAX_Y)
        targetPos[0] = targetPos[0].coerceIn(-MAX_X, MAX_X)
    }

    fun applyAnchor(anchor: OrientationCameraAnchor.Anchor) {
        customDefaultPos = anchor.pos.clone()
        customDefaultLookAt = anchor.lookAt.clone()
        customDefaultZoomLevel = anchor.zoom
        baseFieldOfView = anchor.fov
        reset()
    }

    fun applyProfileDefaults(profile: ScreenMetrics.DisplayProfile) {
        customDefaultPos = profile.defaultCameraPos.clone()
        customDefaultLookAt = profile.defaultCameraLookAt.clone()
        customDefaultZoomLevel = profile.defaultZoomLevel
        baseFieldOfView = profile.defaultFieldOfView
        reset()
        CameraDiagnostics.log(
            this,
            "applyProfileDefaults",
            "orientation=${profile.orientationKey} phone=${profile.isPhone} ${profile.widthPx}x${profile.heightPx}"
        )
    }

    fun transitionToFlatFloorDefaults(bounds: FlatFloorMode.Bounds, aspect: Float) {
        val pos = FlatFloorMode.defaultEyePosition()
        val lookAt = FlatFloorMode.defaultLookAt()
        customDefaultPos = pos.clone()
        customDefaultLookAt = lookAt.clone()
        customDefaultZoomLevel = FlatFloorMode.DEFAULT_CAMERA_ZOOM
        baseFieldOfView = FlatFloorMode.DEFAULT_FOV
        targetPos = pos.clone()
        targetLookAt = lookAt.clone()
        zoomLevel = customDefaultZoomLevel
        fieldOfView = baseFieldOfView
        currentViewMode = ViewMode.FLOOR
        MAX_X = bounds.halfX - 1f
        MAX_Z = bounds.halfZ - 1f
        MIN_X = -bounds.halfX + 1f
        MIN_Z = -bounds.halfZ + 1f
        CameraDiagnostics.logTransition(
            this,
            "flatFloorDefaults",
            "snap=false aspect=${"%.2f".format(aspect)} bounds=${bounds.halfX}x${bounds.halfZ}"
        )
    }

    fun applyFlatFloorDefaults(bounds: FlatFloorMode.Bounds, aspect: Float) {
        transitionToFlatFloorDefaults(bounds, aspect)
        snapToTargets()
        CameraDiagnostics.logTransition(
            this,
            "flatFloorDefaults",
            "snap=true aspect=${"%.2f".format(aspect)} bounds=${bounds.halfX}x${bounds.halfZ}"
        )
    }

    fun saveAsDefault() {
        customDefaultPos = targetPos.clone()
        customDefaultLookAt = targetLookAt.clone()
    }

    fun resetToAbsoluteDefaults() {
        customDefaultPos = ABSOLUTE_DEFAULT_POS.clone()
        customDefaultLookAt = ABSOLUTE_DEFAULT_LOOKAT.clone()
        customDefaultZoomLevel = 1.0f
        baseFieldOfView = 60f
        reset()
    }

    fun restorePreviousView() {
        targetPos = savedPos.clone()
        targetLookAt = savedLookAt.clone()
        currentViewMode = savedViewMode
        zoomLevel = savedZoomLevel
        fieldOfView = savedFieldOfView
        CameraDiagnostics.logTransition(
            this,
            "restorePrevious",
            "mode=$savedViewMode zoom=$savedZoomLevel"
        )
    }

    private fun saveCurrentView() {
        if (currentViewMode != ViewMode.FOLDER_EXPANDED && currentViewMode != ViewMode.WIDGET_FOCUS) {
            savedPos = targetPos.clone()
            savedLookAt = targetLookAt.clone()
            savedViewMode = currentViewMode
            savedZoomLevel = zoomLevel
            savedFieldOfView = fieldOfView
        }
    }

    fun handlePan(dx: Float, dy: Float) {
        val s = 0.02f * zoomLevel
        
        when (currentViewMode) {
            ViewMode.BACK_WALL, ViewMode.LEFT_WALL, ViewMode.RIGHT_WALL -> {
                // In wall modes, we pan relative to the wall plane
                if (currentViewMode == ViewMode.BACK_WALL) {
                    targetPos[0] -= dx * s; targetLookAt[0] -= dx * s
                } else {
                    targetPos[2] -= (if (currentViewMode == ViewMode.LEFT_WALL) dx else -dx) * s
                    targetLookAt[2] -= (if (currentViewMode == ViewMode.LEFT_WALL) dx else -dx) * s
                }
                targetPos[1] += dy * s; targetLookAt[1] += dy * s
            }
            ViewMode.FLOOR, ViewMode.FOLDER_EXPANDED -> {
                targetPos[0] -= dx * s; targetLookAt[0] -= dx * s
                targetPos[2] -= dy * s; targetLookAt[2] -= dy * s
            }
            else -> {
                targetPos[0] -= dx * s; targetLookAt[0] -= dx * s
                targetPos[2] -= dy * s; targetLookAt[2] -= dy * s
            }
        }
    }

    /** Full horizontal screen drag = one 360° yaw; full vertical drag = this much pitch (radians). */
    private val orbitPitchRangeRad = (PI / 4).toFloat()

    fun handleOrbit(screenDx: Float, screenDy: Float, screenWidth: Int, screenHeight: Int) {
        if (currentViewMode != ViewMode.DEFAULT && currentViewMode != ViewMode.FLOOR) return
        if (screenDx == 0f && screenDy == 0f) return
        val width = screenWidth.coerceAtLeast(1).toFloat()
        val height = screenHeight.coerceAtLeast(1).toFloat()

        if (screenDx != 0f) {
            val yaw = -(screenDx / width) * (2f * PI.toFloat())
            val cosY = cos(yaw)
            val sinY = sin(yaw)
            val lx = targetPos[0] - targetLookAt[0]
            val lz = targetPos[2] - targetLookAt[2]
            targetPos[0] = targetLookAt[0] + lx * cosY + lz * sinY
            targetPos[2] = targetLookAt[2] - lx * sinY + lz * cosY
        }

        if (screenDy != 0f) {
            val pitch = -(screenDy / height) * orbitPitchRangeRad
            val cosP = cos(pitch)
            val sinP = sin(pitch)
            val px = targetPos[0] - targetLookAt[0]
            val py = targetPos[1] - targetLookAt[1]
            val pz = targetPos[2] - targetLookAt[2]
            val newPy = (py * cosP - pz * sinP).coerceAtLeast(1f)
            val newPz = py * sinP + pz * cosP
            targetPos[0] = targetLookAt[0] + px
            targetPos[1] = targetLookAt[1] + newPy
            targetPos[2] = targetLookAt[2] + newPz
        }
    }

    fun focusOnWall(wall: CameraManager.ViewMode, pos: FloatArray, lookAt: FloatArray, zoom: Float = 1.0f) {
        saveCurrentView()
        targetPos = pos.clone()
        targetLookAt = lookAt.clone()
        currentViewMode = wall
        zoomLevel = zoom
        fieldOfView = 60f
        CameraDiagnostics.logTransition(this, "focusWall", "mode=$wall zoom=$zoom")
    }

    fun focusOnFloor() {
        saveCurrentView()
        targetPos = FlatFloorMode.defaultEyePosition().clone()
        targetLookAt = FlatFloorMode.defaultLookAt().clone()
        currentViewMode = ViewMode.FLOOR
        zoomLevel = FlatFloorMode.DEFAULT_CAMERA_ZOOM
        fieldOfView = FlatFloorMode.DEFAULT_FOV
        CameraDiagnostics.logTransition(this, "focusFloor", "zoom=$zoomLevel")
    }

    fun focusOnFolder(folderPos: FloatArray, scale: Float = 1.0f) {
        saveCurrentView()
        // 14f distance satisfies 2/3 rule and leaves enough gap above/below
        val focusDist = 14f * scale 
        targetPos = floatArrayOf(folderPos[0], folderPos[1] + focusDist, folderPos[2] + focusDist * 0.5f)
        targetLookAt = floatArrayOf(folderPos[0], folderPos[1], folderPos[2])
        currentViewMode = ViewMode.FOLDER_EXPANDED
        zoomLevel = 1.0f
        fieldOfView = 60f
        CameraDiagnostics.logTransition(
            this,
            "focusFolder",
            "pos=(${folderPos[0]},${folderPos[1]},${folderPos[2]}) scale=$scale"
        )
    }

    fun focusOnWidget(widget: WidgetItem) {
        saveCurrentView()
        val maxDim = max(widget.size.x, widget.size.z)
        val dist = (maxDim * 2.5f).coerceIn(4f, 15f)
        
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
                targetPos = floatArrayOf(widget.position[0], widget.position[1] + dist, widget.position[2] + dist * 0.2f)
                targetLookAt = floatArrayOf(widget.position[0], widget.position[1], widget.position[2])
            }
        }
        currentViewMode = ViewMode.WIDGET_FOCUS
        zoomLevel = 1.0f
        fieldOfView = 60f
    }
}
