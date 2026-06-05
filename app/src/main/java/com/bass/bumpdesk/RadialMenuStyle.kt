package com.bass.bumpdesk

import android.graphics.Color

/** Material-style palette and sizing for radial context menus. */
object RadialMenuStyle {
    const val MIN_SWEEP_DEG = 26f
    const val MIN_SUB_SWEEP_DEG = 22f
    const val BASE_ARC_DEG = 160f
    const val MAX_ARC_DEG = 280f

    fun totalArcForItemCount(count: Int): Float =
        RadialMenuGeometry.totalArcForItemCount(count, MIN_SWEEP_DEG, BASE_ARC_DEG, MAX_ARC_DEG)

    val surfaceFill: Int = Color.argb(240, 33, 35, 43)
    val secondaryFill: Int = Color.argb(235, 45, 48, 58)
    val labelChipFill: Int = Color.argb(225, 55, 58, 68)
    val strokeColor: Int = Color.argb(90, 180, 190, 210)
}
