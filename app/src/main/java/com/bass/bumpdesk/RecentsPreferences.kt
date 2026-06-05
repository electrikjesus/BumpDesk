package com.bass.bumpdesk

import android.content.Context
import android.content.SharedPreferences

object RecentsPreferences {
    const val PREF_VIEW_MODE = "recents_view_mode"
    const val PREF_PINNED_OPEN = "recents_pinned_open"
    const val PREF_POS_X = "recents_pos_x"
    const val PREF_POS_Z = "recents_pos_z"
    const val PREF_SCALE = "recents_pile_scale"

    const val VIEW_ICONS = "icons"
    const val VIEW_TASK_CARDS = "task_cards"

    fun viewModeFromPref(value: String?): Pile.RecentsViewMode =
        if (value == VIEW_TASK_CARDS) Pile.RecentsViewMode.TASK_CARDS else Pile.RecentsViewMode.ICONS

    fun prefValueFor(mode: Pile.RecentsViewMode): String =
        if (mode == Pile.RecentsViewMode.TASK_CARDS) VIEW_TASK_CARDS else VIEW_ICONS

    fun applyToPile(pile: Pile, prefs: SharedPreferences, isFlatFloorMode: Boolean, roomSize: Float) {
        pile.recentsViewMode = viewModeFromPref(prefs.getString(PREF_VIEW_MODE, VIEW_ICONS))
        pile.isPinnedOpen = prefs.getBoolean(PREF_PINNED_OPEN, false) && isFlatFloorMode

        if (isFlatFloorMode && prefs.contains(PREF_POS_X) && prefs.contains(PREF_POS_Z)) {
            pile.position = Vector3(
                prefs.getFloat(PREF_POS_X, pile.position.x),
                0.05f,
                prefs.getFloat(PREF_POS_Z, pile.position.z),
            )
        }
        if (prefs.contains(PREF_SCALE)) {
            pile.scale = prefs.getFloat(PREF_SCALE, pile.scale).coerceIn(0.5f, 3.0f)
        }

        if (pile.isPinnedOpen) {
            pile.isExpanded = true
        }
    }

    fun saveFromPile(pile: Pile, prefs: SharedPreferences) {
        prefs.edit()
            .putString(PREF_VIEW_MODE, prefValueFor(pile.recentsViewMode))
            .putBoolean(PREF_PINNED_OPEN, pile.isPinnedOpen)
            .putFloat(PREF_POS_X, pile.position.x)
            .putFloat(PREF_POS_Z, pile.position.z)
            .putFloat(PREF_SCALE, pile.scale)
            .apply()
    }

    fun toggleViewMode(pile: Pile, context: Context) {
        pile.recentsViewMode = when (pile.recentsViewMode) {
            Pile.RecentsViewMode.ICONS -> Pile.RecentsViewMode.TASK_CARDS
            Pile.RecentsViewMode.TASK_CARDS -> Pile.RecentsViewMode.ICONS
        }
        pile.items.forEach { it.appearance.textureId = -1 }
        saveFromPile(pile, context.getSharedPreferences("bump_prefs", Context.MODE_PRIVATE))
    }
}
