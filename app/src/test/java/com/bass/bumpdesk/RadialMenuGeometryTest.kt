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

    @Test
    fun fitRadiiToScreen_scalesDownWhenMenuIsTooLarge() {
        val fitted = RadialMenuGeometry.fitRadiiToScreen(
            inner = 270f,
            outer = 734f,
            secondary = 928f,
            maxWidth = 2208f,
            maxHeight = 1756f,
        )
        assertTrue(fitted.secondary <= 1756f / 2f * 0.92f + 0.01f)
        assertTrue(fitted.outer < 734f)
        assertTrue(fitted.inner < 270f)
    }

    @Test
    fun clampMenuCenter_doesNotCrashWhenMarginExceedsHalfSpan() {
        assertEquals(878f, RadialMenuGeometry.clampMenuCenter(100f, margin = 928f, span = 1756f), 0.01f)
    }
}
