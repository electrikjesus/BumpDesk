package com.bass.bumpdesk

import android.content.Context
import android.content.SharedPreferences

object RecentsPreferences {
    const val PREF_VIEW_MODE = "recents_view_mode"
    const val PREF_PINNED_OPEN = "recents_pinned_open"
    const val PREF_SURFACE = "recents_surface"
    const val PREF_POS_X = "recents_pos_x"
    const val PREF_POS_Y = "recents_pos_y"
    const val PREF_POS_Z = "recents_pos_z"
    const val PREF_SCALE = "recents_pile_scale"
    const val PREF_GRID_COLS = "recents_grid_cols"
    const val PREF_GRID_ROWS = "recents_grid_rows"

    /** Default drawer scale when none saved (Grow/Shrink adjusts from here). */
    const val DEFAULT_SCALE = 0.82f

    const val VIEW_ICONS = "icons"
    const val VIEW_TASK_CARDS = "task_cards"

    fun viewModeFromPref(value: String?): Pile.RecentsViewMode =
        if (value == VIEW_TASK_CARDS) Pile.RecentsViewMode.TASK_CARDS else Pile.RecentsViewMode.ICONS

    fun prefValueFor(mode: Pile.RecentsViewMode): String =
        if (mode == Pile.RecentsViewMode.TASK_CARDS) VIEW_TASK_CARDS else VIEW_ICONS

    fun applyToPile(pile: Pile, prefs: SharedPreferences, useFloorLayout: Boolean, roomSize: Float) {
        pile.recentsViewMode = viewModeFromPref(prefs.getString(PREF_VIEW_MODE, VIEW_ICONS))
        pile.isPinnedOpen = prefs.getBoolean(PREF_PINNED_OPEN, false)

        val defaultSurface = if (useFloorLayout) {
            BumpItem.Surface.FLOOR
        } else {
            BumpItem.Surface.BACK_WALL
        }
        pile.surface = prefs.getString(PREF_SURFACE, null)?.let { name ->
            runCatching { BumpItem.Surface.valueOf(name) }.getOrNull()
        } ?: defaultSurface

        if (prefs.contains(PREF_POS_X) && prefs.contains(PREF_POS_Z)) {
            pile.position = Vector3(
                prefs.getFloat(PREF_POS_X, pile.position.x),
                prefs.getFloat(PREF_POS_Y, if (pile.surface == BumpItem.Surface.FLOOR) 0.05f else 4f),
                prefs.getFloat(PREF_POS_Z, pile.position.z),
            )
        } else if (useFloorLayout) {
            pile.position = Vector3(-6f, 0.05f, 6f)
            pile.surface = BumpItem.Surface.FLOOR
        } else {
            pile.position = Vector3(0f, 4f, -roomSize + 0.6f)
            pile.surface = BumpItem.Surface.BACK_WALL
        }

        if (prefs.contains(PREF_SCALE)) {
            pile.scale = prefs.getFloat(PREF_SCALE, pile.scale).coerceIn(0.5f, 3.0f)
        } else {
            pile.scale = DEFAULT_SCALE
        }

        pile.drawerGridColumns = FolderDrawerStyle.coerceRecentsDrawerColumns(
            prefs.getInt(PREF_GRID_COLS, 2),
        )
        pile.drawerGridRows = FolderDrawerStyle.coerceRecentsDrawerRows(
            prefs.getInt(PREF_GRID_ROWS, 2),
        )

        if (pile.isPinnedOpen) {
            pile.isExpanded = true
        }
    }

    fun saveFromPile(pile: Pile, prefs: SharedPreferences) {
        prefs.edit()
            .putString(PREF_VIEW_MODE, prefValueFor(pile.recentsViewMode))
            .putBoolean(PREF_PINNED_OPEN, pile.isPinnedOpen)
            .putString(PREF_SURFACE, pile.surface.name)
            .putFloat(PREF_POS_X, pile.position.x)
            .putFloat(PREF_POS_Y, pile.position.y)
            .putFloat(PREF_POS_Z, pile.position.z)
            .putFloat(PREF_SCALE, pile.scale)
            .putInt(PREF_GRID_COLS, FolderDrawerStyle.coerceRecentsDrawerColumns(pile.drawerGridColumns))
            .putInt(PREF_GRID_ROWS, FolderDrawerStyle.coerceRecentsDrawerRows(pile.drawerGridRows))
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
