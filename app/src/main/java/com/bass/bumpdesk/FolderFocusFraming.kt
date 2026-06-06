package com.bass.bumpdesk

import kotlin.math.atan
import kotlin.math.max
import kotlin.math.tan

/** Computes camera distance/zoom so an expanded folder drawer fits on screen. */
object FolderFocusFraming {
    private const val BASE_DISTANCE = 18f
    private const val PANEL_EXTENT_COEFF = 0.38f
    private const val EYE_SLANT = 1.1180339f // sqrt(1.25) — matches focusOnFolder eye offset (0.5z)

    data class Params(
        val panelHalfX: Float,
        val panelHalfZ: Float,
        val pileScale: Float,
        val screenWidthPx: Int,
        val screenHeightPx: Int,
        val isPhone: Boolean,
        val vFovDeg: Float = 60f,
    )

    data class Result(
        val focusDistance: Float,
        val zoomLevel: Float,
        val fieldOfView: Float,
    )

    fun compute(params: Params): Result {
        val scale = params.pileScale.coerceIn(0.5f, 2.5f)
        val halfX = params.panelHalfX * scale
        val halfZ = params.panelHalfZ * scale
        val margin = if (params.isPhone) 1.2f else 1.12f

        val width = params.screenWidthPx.coerceAtLeast(1)
        val height = params.screenHeightPx.coerceAtLeast(1)
        val aspect = width.toFloat() / height
        val vFov = Math.toRadians(params.vFovDeg.toDouble()).toFloat()
        val hFov = 2f * atan(tan(vFov / 2f) * aspect)

        val oblique = 0.82f
        val minProductX = halfX * margin / (EYE_SLANT * tan(hFov / 2f) * oblique)
        val minProductZ = halfZ * margin / (EYE_SLANT * tan(vFov / 2f) * oblique * 0.88f)

        val baseDistance = (BASE_DISTANCE + max(halfX, halfZ) * PANEL_EXTENT_COEFF) * scale
        val requiredProduct = max(max(minProductX, minProductZ), baseDistance)

        val focusDistance = max(baseDistance, minProductX / 1.12f)
        var zoomLevel = (requiredProduct / focusDistance).coerceIn(1.06f, if (params.isPhone) 2.35f else 1.5f)

        if (params.isPhone) {
            val phoneFloor = when {
                aspect < 0.55f -> 1.38f
                aspect < 0.78f -> 1.24f
                else -> 1.16f
            }
            zoomLevel = max(zoomLevel, phoneFloor)
        }

        val fieldOfView = when {
            params.isPhone && max(halfX, halfZ) > 4.5f -> max(params.vFovDeg, 64f)
            params.isPhone -> 62f
            else -> params.vFovDeg
        }

        return Result(focusDistance, zoomLevel, fieldOfView)
    }

    /**
     * Pan the camera rig on the floor plane so [panelCenter] projects near the screen center.
     * Used on phones where oblique folder focus otherwise leaves the drawer off-center.
     */
    fun centerLookAtForPhone(
        panelCenter: FloatArray,
        initialLookAt: FloatArray,
        focusDistance: Float,
        zoomLevel: Float,
        vFovDeg: Float,
        screenWidthPx: Int,
        screenHeightPx: Int,
    ): FloatArray {
        val width = screenWidthPx.coerceAtLeast(1)
        val height = screenHeightPx.coerceAtLeast(1)
        val aspect = width.toFloat() / height
        var lookAt = initialLookAt.copyOf()
        val eyeRel = floatArrayOf(0f, focusDistance, focusDistance * 0.5f)
        val widthF = width.toFloat()
        val heightF = height.toFloat()

        for (i in 0 until 8) {
            val eye = floatArrayOf(
                lookAt[0] + eyeRel[0] * zoomLevel,
                lookAt[1] + eyeRel[1] * zoomLevel,
                lookAt[2] + eyeRel[2] * zoomLevel,
            )
            val projected = projectToScreen(panelCenter, eye, lookAt, vFovDeg, aspect, widthF, heightF)
            val errX = widthF * 0.5f - projected[0]
            val errY = heightF * 0.52f - projected[1]
            if (kotlin.math.abs(errX) < 8f && kotlin.math.abs(errY) < 8f) break

            val viewDist = eyeDistance(eye, lookAt).coerceAtLeast(1f)
            val vFov = Math.toRadians(vFovDeg.toDouble()).toFloat()
            val hFov = 2f * atan(tan(vFov / 2f) * aspect)
            val metersPerPixelX = 2f * viewDist * tan(hFov / 2f) / widthF
            val metersPerPixelY = 2f * viewDist * tan(vFov / 2f) / heightF

            lookAt[0] += errX * metersPerPixelX * 0.9f
            lookAt[2] -= errY * metersPerPixelY * 0.7f
        }
        return lookAt
    }

    internal fun projectToScreen(
        world: FloatArray,
        eye: FloatArray,
        center: FloatArray,
        vFovDeg: Float,
        aspect: Float,
        width: Float,
        height: Float,
    ): FloatArray {
        val vFov = Math.toRadians(vFovDeg.toDouble()).toFloat()
        val forward = normalize(
            floatArrayOf(center[0] - eye[0], center[1] - eye[1], center[2] - eye[2]),
        )
        val worldUp = floatArrayOf(0f, 1f, 0f)
        val right = normalize(cross(worldUp, forward))
        val up = cross(forward, right)

        val rel = floatArrayOf(world[0] - eye[0], world[1] - eye[1], world[2] - eye[2])
        val camX = dot(rel, right)
        val camY = dot(rel, up)
        val camZ = -dot(rel, forward)
        if (camZ <= 0.01f) return floatArrayOf(width * 0.5f, height * 0.5f)

        val ndcX = (camX / camZ) / (tan(vFov / 2f) * aspect)
        val ndcY = (camY / camZ) / tan(vFov / 2f)
        return floatArrayOf(
            (ndcX + 1f) * 0.5f * width,
            (1f - ndcY) * 0.5f * height,
        )
    }

    private fun eyeDistance(a: FloatArray, b: FloatArray): Float {
        val dx = a[0] - b[0]
        val dy = a[1] - b[1]
        val dz = a[2] - b[2]
        return kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun dot(a: FloatArray, b: FloatArray): Float = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]

    private fun cross(a: FloatArray, b: FloatArray): FloatArray =
        floatArrayOf(
            a[1] * b[2] - a[2] * b[1],
            a[2] * b[0] - a[0] * b[2],
            a[0] * b[1] - a[1] * b[0],
        )

    private fun normalize(v: FloatArray): FloatArray {
        val len = eyeDistance(v, floatArrayOf(0f, 0f, 0f)).coerceAtLeast(1e-6f)
        return floatArrayOf(v[0] / len, v[1] / len, v[2] / len)
    }
}
