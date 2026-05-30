package com.bass.bumpdesk

import android.content.Context

/**
 * Per-orientation camera anchor saved when the user pans or zooms in DEFAULT view.
 * Applied when rotating to that orientation instead of computed profile defaults.
 */
object OrientationCameraAnchor {

    private const val PREFS = "bump_prefs"

    data class Anchor(
        val pos: FloatArray,
        val lookAt: FloatArray,
        val zoom: Float,
        val fov: Float,
    )

    fun save(context: Context, orientationKey: String, camera: CameraManager) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            putFloat(key(orientationKey, "pos_x"), camera.targetPos[0])
            putFloat(key(orientationKey, "pos_y"), camera.targetPos[1])
            putFloat(key(orientationKey, "pos_z"), camera.targetPos[2])
            putFloat(key(orientationKey, "look_x"), camera.targetLookAt[0])
            putFloat(key(orientationKey, "look_y"), camera.targetLookAt[1])
            putFloat(key(orientationKey, "look_z"), camera.targetLookAt[2])
            putFloat(key(orientationKey, "zoom"), camera.zoomLevel)
            putFloat(key(orientationKey, "fov"), camera.baseFieldOfView)
            putBoolean(key(orientationKey, "set"), true)
        }.apply()
    }

    fun load(context: Context, orientationKey: String): Anchor? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(key(orientationKey, "set"), false)) return null
        return Anchor(
            pos = floatArrayOf(
                prefs.getFloat(key(orientationKey, "pos_x"), 0f),
                prefs.getFloat(key(orientationKey, "pos_y"), 12f),
                prefs.getFloat(key(orientationKey, "pos_z"), 25f),
            ),
            lookAt = floatArrayOf(
                prefs.getFloat(key(orientationKey, "look_x"), 0f),
                prefs.getFloat(key(orientationKey, "look_y"), 0f),
                prefs.getFloat(key(orientationKey, "look_z"), 5f),
            ),
            zoom = prefs.getFloat(key(orientationKey, "zoom"), 1f),
            fov = prefs.getFloat(key(orientationKey, "fov"), 60f),
        )
    }

    fun clear(context: Context, orientationKey: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            remove(key(orientationKey, "pos_x"))
            remove(key(orientationKey, "pos_y"))
            remove(key(orientationKey, "pos_z"))
            remove(key(orientationKey, "look_x"))
            remove(key(orientationKey, "look_y"))
            remove(key(orientationKey, "look_z"))
            remove(key(orientationKey, "zoom"))
            remove(key(orientationKey, "fov"))
            remove(key(orientationKey, "set"))
        }.apply()
    }

    private fun key(orientationKey: String, field: String): String =
        "cam_anchor_${orientationKey}_$field"
}
