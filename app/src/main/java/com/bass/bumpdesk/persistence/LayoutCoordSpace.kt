package com.bass.bumpdesk.persistence

import android.content.SharedPreferences

/** Tracks whether desk positions in Room are stored as normalized fractions (Phase 3). */
object LayoutCoordSpace {
    const val PREF_KEY = "desk_layout_coord_space_v1"
    const val WORLD = "world"
    const val NORMALIZED = "normalized_v1"

    private const val PREF_SHARED_SEEDED = "desk_shared_layout_seeded_v1"

    fun usesNormalized(prefs: SharedPreferences): Boolean =
        prefs.getString(PREF_KEY, WORLD) == NORMALIZED

    fun markNormalized(prefs: SharedPreferences) {
        prefs.edit().putString(PREF_KEY, NORMALIZED).apply()
    }

    fun isSharedSeeded(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(PREF_SHARED_SEEDED, false)

    fun markSharedSeeded(prefs: SharedPreferences) {
        prefs.edit().putBoolean(PREF_SHARED_SEEDED, true).apply()
    }
}
