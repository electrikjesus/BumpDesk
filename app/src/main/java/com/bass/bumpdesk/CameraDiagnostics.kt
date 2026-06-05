package com.bass.bumpdesk

import android.content.Context
import android.util.Log
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

object CameraDiagnostics {

    /** Bump when log format/placement changes so device logs confirm APK version. */
    private const val LOG_VERSION = "v3"

    data class ViewState(
        val yawDeg: Float,
        val pitchDeg: Float,
        val eyeX: Float,
        val eyeY: Float,
        val eyeZ: Float,
        val lookX: Float,
        val lookY: Float,
        val lookZ: Float,
        val targetPosX: Float,
        val targetPosY: Float,
        val targetPosZ: Float,
        val targetLookX: Float,
        val targetLookY: Float,
        val targetLookZ: Float,
        val targetYawDeg: Float,
        val zoomLevel: Float,
        val currentZoomLevel: Float,
        val defaultZoomLevel: Float,
        val fieldOfView: Float,
        val baseFieldOfView: Float,
        val viewMode: CameraManager.ViewMode,
    ) {
        fun toLogMessage(reason: String, extras: String = ""): String {
            val tail = if (extras.isEmpty()) "" else " | $extras"
            return buildString {
                append("log=$LOG_VERSION reason=$reason")
                append(tail)
                append(" | mode=$viewMode")
                append(" | yaw=${fmt(yawDeg)} pitch=${fmt(pitchDeg)} targetYaw=${fmt(targetYawDeg)}")
                append(" | eye=(${fmt(eyeX)},${fmt(eyeY)},${fmt(eyeZ)})")
                append(" | lookAt=(${fmt(lookX)},${fmt(lookY)},${fmt(lookZ)})")
                append(" | targetPos=(${fmt(targetPosX)},${fmt(targetPosY)},${fmt(targetPosZ)})")
                append(" | targetLookAt=(${fmt(targetLookX)},${fmt(targetLookY)},${fmt(targetLookZ)})")
                append(" | zoom=${fmt(zoomLevel)}/${fmt(currentZoomLevel)}/${fmt(defaultZoomLevel)} fov=${fmt(fieldOfView)}/${fmt(baseFieldOfView)}")
            }
        }

        private fun fmt(v: Float): String = "%.2f".format(v)
    }

    fun from(camera: CameraManager): ViewState {
        val (yaw, pitch) = yawPitchDeg(camera.currentPos, camera.currentLookAt)
        val (targetYaw, _) = yawPitchDeg(camera.targetPos, camera.targetLookAt)
        return ViewState(
            yawDeg = yaw,
            pitchDeg = pitch,
            eyeX = camera.currentPos[0],
            eyeY = camera.currentPos[1],
            eyeZ = camera.currentPos[2],
            lookX = camera.currentLookAt[0],
            lookY = camera.currentLookAt[1],
            lookZ = camera.currentLookAt[2],
            targetPosX = camera.targetPos[0],
            targetPosY = camera.targetPos[1],
            targetPosZ = camera.targetPos[2],
            targetLookX = camera.targetLookAt[0],
            targetLookY = camera.targetLookAt[1],
            targetLookZ = camera.targetLookAt[2],
            targetYawDeg = targetYaw,
            zoomLevel = camera.zoomLevel,
            currentZoomLevel = camera.currentZoomLevel,
            defaultZoomLevel = camera.customDefaultZoomLevel,
            fieldOfView = camera.fieldOfView,
            baseFieldOfView = camera.baseFieldOfView,
            viewMode = camera.currentViewMode,
        )
    }

    /** Yaw: degrees right of center (+ = looking right). Pitch: degrees down from horizon. */
    fun yawPitchDeg(eye: FloatArray, lookAt: FloatArray): Pair<Float, Float> {
        val fx = lookAt[0] - eye[0]
        val fy = lookAt[1] - eye[1]
        val fz = lookAt[2] - eye[2]
        val horiz = sqrt(fx * fx + fz * fz).coerceAtLeast(1e-6f)
        val yaw = Math.toDegrees(atan2(fx.toDouble(), horiz.toDouble())).toFloat()
        val pitch = Math.toDegrees(atan2((-fy).toDouble(), horiz.toDouble())).toFloat()
        return yaw to pitch
    }

    fun logProbe(context: Context, reason: String) {
        val profile = ScreenMetrics.from(context)
        emit(
            "log=$LOG_VERSION probe reason=$reason " +
                "orientation=${profile.orientationKey} ${profile.widthPx}x${profile.heightPx} " +
                "defaultZoom=${profile.defaultZoomLevel} defaultFov=${profile.defaultFieldOfView}"
        )
    }

    fun log(camera: CameraManager, reason: String, extras: String = "") {
        emit(from(camera).toLogMessage(reason, extras))
    }

    fun logTransition(camera: CameraManager, action: String, extras: String = "") {
        emit(from(camera).toLogMessage("transition|$action", extras))
    }

    fun logAnimation(camera: CameraManager, phase: String) {
        val state = from(camera)
        val eyeDelta = dist(state.eyeX, state.eyeY, state.eyeZ, state.targetPosX, state.targetPosY, state.targetPosZ)
        val lookDelta = dist(state.lookX, state.lookY, state.lookZ, state.targetLookX, state.targetLookY, state.targetLookZ)
        val zoomDelta = abs(state.zoomLevel - state.currentZoomLevel)
        emit(
            "log=$LOG_VERSION animation phase=$phase | mode=${state.viewMode} " +
                "eyeDelta=${fmt(eyeDelta)} lookDelta=${fmt(lookDelta)} zoomDelta=${fmt(zoomDelta)} | " +
                "eye=(${fmt(state.eyeX)},${fmt(state.eyeY)},${fmt(state.eyeZ)}) " +
                "target=(${fmt(state.targetPosX)},${fmt(state.targetPosY)},${fmt(state.targetPosZ)}) " +
                "zoom=${fmt(state.zoomLevel)}/${fmt(state.currentZoomLevel)}"
        )
    }

    private fun dist(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float): Float {
        val dx = ax - bx
        val dy = ay - by
        val dz = az - bz
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun fmt(v: Float): String = "%.2f".format(v)

    private fun emit(message: String) {
        if (!BumpDeskLog.logEnabled) return
        val line = "[viewState] $message"
        try {
            Log.i(BumpDeskLog.Tag.CAMERA, line)
            // Mirror to CORE so `adb logcat -s "BumpDesk:Core"` still captures camera lines.
            Log.i(BumpDeskLog.Tag.CORE, line)
        } catch (_: Throwable) {
            // JVM unit tests without android.util.Log.
        }
    }
}
