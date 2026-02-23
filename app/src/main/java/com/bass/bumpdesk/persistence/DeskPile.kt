package com.bass.bumpdesk.persistence

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "desk_piles")
data class DeskPile(
    @PrimaryKey val name: String,
    val posX: Float,
    val posY: Float,
    val posZ: Float,
    val layoutMode: String,
    val surface: String,
    val scale: Float,
    val isSystem: Boolean,
    val isFannedOut: Boolean
)
