package com.bass.bumpdesk

import android.content.Context

/**
 * Per layout-profile camera anchor saved when the user pans or zooms in DEFAULT view.
 * Applied when rotating or changing display posture instead of computed profile defaults.
 */
object OrientationCameraAnchor {

    private const val PREFS = "bump_prefs"

    data class Anchor(
        val pos: FloatArray,
        val lookAt: FloatArray,
        val zoom: Float,
        val fov: Float,
    )

    fun saveForProfile(context: Context, profile: ScreenMetrics.DisplayProfile, camera: CameraManager) {
        save(context, profile.layoutProfileKey, camera)
    }

    fun loadForProfile(context: Context, profile: ScreenMetrics.DisplayProfile): Anchor? {
        load(context, profile.layoutProfileKey)?.let { return it }
        return load(context, profile.orientationKey)
    }

    fun clearForProfile(context: Context, profile: ScreenMetrics.DisplayProfile) {
        clear(context, profile.layoutProfileKey)
    }

    fun save(context: Context, profileKey: String, camera: CameraManager) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            putFloat(key(profileKey, "pos_x"), camera.targetPos[0])
            putFloat(key(profileKey, "pos_y"), camera.targetPos[1])
            putFloat(key(profileKey, "pos_z"), camera.targetPos[2])
            putFloat(key(profileKey, "look_x"), camera.targetLookAt[0])
            putFloat(key(profileKey, "look_y"), camera.targetLookAt[1])
            putFloat(key(profileKey, "look_z"), camera.targetLookAt[2])
            putFloat(key(profileKey, "zoom"), camera.zoomLevel)
            putFloat(key(profileKey, "fov"), camera.baseFieldOfView)
            putBoolean(key(profileKey, "set"), true)
        }.apply()
    }

    fun load(context: Context, profileKey: String): Anchor? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(key(profileKey, "set"), false)) return null
        return Anchor(
            pos = floatArrayOf(
                prefs.getFloat(key(profileKey, "pos_x"), 0f),
                prefs.getFloat(key(profileKey, "pos_y"), 12f),
                prefs.getFloat(key(profileKey, "pos_z"), 25f),
            ),
            lookAt = floatArrayOf(
                prefs.getFloat(key(profileKey, "look_x"), 0f),
                prefs.getFloat(key(profileKey, "look_y"), 0f),
                prefs.getFloat(key(profileKey, "look_z"), 5f),
            ),
            zoom = prefs.getFloat(key(profileKey, "zoom"), 1f),
            fov = prefs.getFloat(key(profileKey, "fov"), 60f),
        )
    }

    fun clear(context: Context, profileKey: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            remove(key(profileKey, "pos_x"))
            remove(key(profileKey, "pos_y"))
            remove(key(profileKey, "pos_z"))
            remove(key(profileKey, "look_x"))
            remove(key(profileKey, "look_y"))
            remove(key(profileKey, "look_z"))
            remove(key(profileKey, "zoom"))
            remove(key(profileKey, "fov"))
            remove(key(profileKey, "set"))
        }.apply()
    }

    private fun key(profileKey: String, field: String): String =
        "cam_anchor_${profileKey}_$field"
}
