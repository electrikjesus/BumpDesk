package com.bass.bumpdesk.persistence

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "desk_items")
data class DeskItem(
    @PrimaryKey val packageName: String,
    val posX: Float,
    val posY: Float,
    val posZ: Float,
    val surface: String,
    val isPinned: Boolean,
    val scale: Float,
    val pileId: String? = null
)
