package com.bass.bumpdesk.persistence

import androidx.room.Entity

@Entity(
    tableName = "desk_piles",
    primaryKeys = ["name", "layoutProfileKey"],
)
data class DeskPile(
    val name: String,
    val layoutProfileKey: String,
    val posX: Float,
    val posY: Float,
    val posZ: Float,
    val layoutMode: String,
    val surface: String,
    val scale: Float,
    val isSystem: Boolean,
    val isFannedOut: Boolean,
)
