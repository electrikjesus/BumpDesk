package com.bass.bumpdesk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadialMenuGeometryTest {
    @Test
    fun subMenuLayout_fitsThreeItemsInsideParentWedge() {
        val layout = RadialMenuGeometry.subMenuLayout(
            parentAngle = 100f,
            parentSweep = 40f,
            subItemCount = 3,
            minSubSweepDeg = RadialMenuStyle.MIN_SUB_SWEEP_DEG,
        )

        assertTrue(layout.subArc <= 40.01f)
        assertEquals(100f, layout.subStart, 0.01f)
        assertEquals(40f / 3f, layout.subSweep, 0.01f)
    }

    @Test
    fun hitSubMenuItem_selectsLastSubItemInLassoLayoutMenu() {
        val layout = RadialMenuGeometry.subMenuLayout(
            parentAngle = 100f,
            parentSweep = 40f,
            subItemCount = 3,
            minSubSweepDeg = RadialMenuStyle.MIN_SUB_SWEEP_DEG,
        )
        val columnCenter = layout.subStart + 2 * layout.subSweep + layout.subSweep / 2f

        assertEquals(2, RadialMenuGeometry.hitSubMenuItem(columnCenter, layout, subItemCount = 3))
    }

    @Test
    fun hitSubMenuItem_selectsMiddleSubItem() {
        val layout = RadialMenuGeometry.subMenuLayout(
            parentAngle = 50f,
            parentSweep = 40f,
            subItemCount = 3,
            minSubSweepDeg = RadialMenuStyle.MIN_SUB_SWEEP_DEG,
        )
        val rowCenter = layout.subStart + layout.subSweep + layout.subSweep / 2f

        assertEquals(1, RadialMenuGeometry.hitSubMenuItem(rowCenter, layout, subItemCount = 3))
    }
}
