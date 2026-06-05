package com.bass.bumpdesk

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class WidgetHandleStyleTest {

    @Test
    fun hitTest_squareWidget_cornersAreTappable() {
        val widget = WidgetItem(
            appWidgetId = 1,
            size = Vector3(2f, 0f, 2f),
            surface = BumpItem.Surface.FLOOR,
        )
        val (moveU, moveV) = WidgetHandleStyle.handleHitUv(widget, WidgetHandleStyle.Kind.MOVE)
        val (resizeU, resizeV) = WidgetHandleStyle.handleHitUv(widget, WidgetHandleStyle.Kind.RESIZE)
        assertTrue(WidgetHandleStyle.isTouchOnHandle(widget, moveU, moveV, WidgetHandleStyle.Kind.MOVE))
        assertTrue(WidgetHandleStyle.isTouchOnHandle(widget, resizeU, resizeV, WidgetHandleStyle.Kind.RESIZE))
    }

    @Test
    fun resizeHitTest_wideFlatWidget_usesActualCornerNotSquareAssumption() {
        val widget = WidgetItem(
            appWidgetId = 13,
            size = Vector3(2.5f, 0f, 1.1f),
            surface = BumpItem.Surface.FLOOR,
        )
        val (resizeU, resizeV) = WidgetHandleStyle.handleHitUv(widget, WidgetHandleStyle.Kind.RESIZE)
        assertTrue(resizeU > 0.85f)
        assertTrue(resizeV > 0.7f)
        assertTrue(WidgetHandleStyle.isTouchOnHandle(widget, resizeU, resizeV, WidgetHandleStyle.Kind.RESIZE))
        assertFalse(WidgetHandleStyle.isTouchOnHandle(widget, 0.1f, 0.1f, WidgetHandleStyle.Kind.RESIZE))
    }

    @Test
    fun handleCenter_isOffsetFromWidgetCenterAtCorners() {
        val size = Vector3(4f, 0f, 2f)
        val (moveX, _, moveZ) = WidgetHandleStyle.handleCenter(size, WidgetHandleStyle.Kind.MOVE)
        val (resizeX, _, resizeZ) = WidgetHandleStyle.handleCenter(size, WidgetHandleStyle.Kind.RESIZE)
        assertTrue(moveX < 0f)
        assertTrue(moveZ < 0f)
        assertTrue(resizeX > 0f)
        assertTrue(resizeZ > 0f)
        assertTrue(abs(moveX) < size.x)
        assertTrue(resizeX < size.x)
    }

    @Test
    fun handleSize_isConstantRegardlessOfWidgetSpan() {
        val small = WidgetHandleStyle.handleSizeForWidget(Vector3(2f, 0f, 1.5f))
        val large = WidgetHandleStyle.handleSizeForWidget(Vector3(5f, 0f, 4f))
        org.junit.Assert.assertEquals(small, large, 0.001f)
        org.junit.Assert.assertEquals(WidgetHandleStyle.HANDLE_SIZE, small, 0.001f)
    }
}
