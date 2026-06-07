package com.bass.bumpdesk.persistence

import androidx.room.Entity

@Entity(
    tableName = "desk_items",
    primaryKeys = ["id", "layoutProfileKey"],
)
data class DeskItem(
    val id: String,
    val layoutProfileKey: String,
    val type: String,
    val packageName: String?,
    val appWidgetId: Int?,
    val text: String?,
    val posX: Float,
    val posY: Float,
    val posZ: Float,
    val sizeX: Float,
    val sizeZ: Float,
    val surface: String,
    val isPinned: Boolean,
    val scale: Float,
    val pileId: String? = null,
)
