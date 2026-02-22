package com.bass.bumpdesk.persistence

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "desk_items")
data class DeskItem(
    @PrimaryKey val id: String, // Use a unique ID instead of packageName to support widgets and multiple instances
    val type: String, // APP, WIDGET, STICKY_NOTE, etc.
    val packageName: String?,
    val appWidgetId: Int?,
    val text: String?, // For notes, photo frame URIs, web URLs
    val posX: Float,
    val posY: Float,
    val posZ: Float,
    val sizeX: Float,
    val sizeZ: Float,
    val surface: String,
    val isPinned: Boolean,
    val scale: Float,
    val pileId: String? = null
)
