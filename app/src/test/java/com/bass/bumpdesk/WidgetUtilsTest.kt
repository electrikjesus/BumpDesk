package com.bass.bumpdesk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetUtilsTest {

    private fun atAGlanceGrid() = WidgetResizeGrid(
        cellDp = 70f,
        minWidthDp = 180,
        minHeightDp = 40,
        maxWidthDp = 530,
        maxHeightDp = 200,
        defaultWidthDp = 250,
        defaultHeightDp = 110,
        resizeMode = 3, // RESIZE_BOTH
        aspectRatio = 250f / 110f,
    )

    @Test
    fun computeResizedSize_freeform_allowsIndependentAxes() {
        val grid = atAGlanceGrid()
        val start = WidgetUtils.worldHalfSizeFromDp(grid, 250, 110)
        val resized = WidgetUtils.computeResizedSize(
            startSize = start,
            du = 0.5f,
            dv = 0.2f,
            grid = grid,
        )
        val (newW, newH) = WidgetUtils.dpSizeFromWorldHalf(grid, resized)
        assertTrue(newW > 250)
        assertTrue(newH > 110)
    }

    @Test
    fun computeResizedSize_locked_keepsProviderAspect() {
        val grid = atAGlanceGrid().copy(resizeMode = 0) // RESIZE_NONE
        val start = WidgetUtils.worldHalfSizeFromDp(grid, 250, 110)
        val resized = WidgetUtils.computeResizedSize(
            startSize = start,
            du = 0.4f,
            dv = 0f,
            grid = grid,
        )
        val (newW, newH) = WidgetUtils.dpSizeFromWorldHalf(grid, resized)
        assertEquals(grid.aspectRatio, newW.toFloat() / newH, 0.15f)
    }

    @Test
    fun computeResizedSize_clampsToProviderMaxWidth() {
        val grid = atAGlanceGrid()
        val start = WidgetUtils.worldHalfSizeFromDp(grid, 480, 110)
        val resized = WidgetUtils.computeResizedSize(
            startSize = start,
            du = 2f,
            dv = 0f,
            grid = grid,
        )
        val (newW, _) = WidgetUtils.dpSizeFromWorldHalf(grid, resized)
        assertTrue(newW <= grid.maxWidthDp)
    }

    @Test
    fun snapDpToCellGrid_snapsToCellMultiples() {
        val snapped = WidgetUtils.snapDpToCellGrid(183, 70f, 70, 530)
        assertEquals(210, snapped)
    }

    @Test
    fun worldHalfSize_preservesProviderDpAspect() {
        val grid = atAGlanceGrid()
        val size = WidgetUtils.worldHalfSizeFromDp(grid, 500, 200)
        val (wDp, hDp) = WidgetUtils.dpSizeFromWorldHalf(grid, size)
        assertEquals(500, wDp)
        assertEquals(200, hDp)
        assertEquals(wDp.toFloat() / hDp, size.x / size.z, 0.01f)
    }

    @Test
    fun defaultWorldSize_usesSensibleHeightForStripMinHeightProviders() {
        val info = android.appwidget.AppWidgetProviderInfo().apply {
            minWidth = 450
            minHeight = 56
            minResizeWidth = 450
            minResizeHeight = 56
            maxResizeWidth = 1290
            maxResizeHeight = 896
            resizeMode = 3
        }
        val grid = WidgetUtils.resizeGridFrom(info)
        assertTrue(grid.defaultHeightDp > 56)
        val size = WidgetUtils.defaultWorldSize(info)
        assertTrue("floor depth ${size.z} should be visible", size.z > 0.8f)
        assertTrue(size.x / size.z < 6f)
    }

    @Test
    fun needsAspectCorrection_detectsSquareCorruptionOnly() {
        val grid = atAGlanceGrid()
        val wideSize = WidgetUtils.worldHalfSizeFromDp(grid, 700, 280)
        val squareSize = Vector3(2.5f, 0f, 2.5f)
        val info = syntheticInfo(grid)
        assertFalse(WidgetUtils.needsAspectCorrection(info, wideSize))
        assertTrue(WidgetUtils.needsAspectCorrection(info, squareSize))
    }

    private fun syntheticInfo(grid: WidgetResizeGrid): android.appwidget.AppWidgetProviderInfo {
        return android.appwidget.AppWidgetProviderInfo().apply {
            minWidth = grid.defaultWidthDp
            minHeight = grid.defaultHeightDp
            minResizeWidth = grid.minWidthDp
            minResizeHeight = grid.minHeightDp
            maxResizeWidth = grid.maxWidthDp
            maxResizeHeight = grid.maxHeightDp
            targetCellWidth = 4
            targetCellHeight = 2
            resizeMode = grid.resizeMode
        }
    }
}
