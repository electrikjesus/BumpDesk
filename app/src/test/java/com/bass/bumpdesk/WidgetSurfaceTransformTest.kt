package com.bass.bumpdesk

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetSurfaceTransformTest {

    @Test
    fun backWallHandleUvMatchesMoveAndResizeCorners() {
        val widget = WidgetItem(
            appWidgetId = 1,
            size = Vector3(4f, 0f, 2f),
            surface = BumpItem.Surface.BACK_WALL,
        )
        val (moveU, moveV) = WidgetHandleStyle.handleHitUv(widget, WidgetHandleStyle.Kind.MOVE)
        val (resizeU, resizeV) = WidgetHandleStyle.handleHitUv(widget, WidgetHandleStyle.Kind.RESIZE)
        assertTrue(WidgetHandleStyle.isTouchOnHandle(widget, moveU, moveV, WidgetHandleStyle.Kind.MOVE))
        assertTrue(WidgetHandleStyle.isTouchOnHandle(widget, resizeU, resizeV, WidgetHandleStyle.Kind.RESIZE))
        assertTrue(moveU < 0.35f)
        assertTrue(resizeU > 0.65f)
    }

    @Test
    fun leftWallHandleUvMatchesMoveAndResizeCorners() {
        val widget = WidgetItem(
            appWidgetId = 2,
            size = Vector3(3f, 0f, 2f),
            surface = BumpItem.Surface.LEFT_WALL,
        )
        val (moveU, moveV) = WidgetHandleStyle.handleHitUv(widget, WidgetHandleStyle.Kind.MOVE)
        val (resizeU, resizeV) = WidgetHandleStyle.handleHitUv(widget, WidgetHandleStyle.Kind.RESIZE)
        assertTrue(WidgetHandleStyle.isTouchOnHandle(widget, moveU, moveV, WidgetHandleStyle.Kind.MOVE))
        assertTrue(WidgetHandleStyle.isTouchOnHandle(widget, resizeU, resizeV, WidgetHandleStyle.Kind.RESIZE))
        assertFalse(WidgetHandleStyle.isTouchOnHandle(widget, resizeU, resizeV, WidgetHandleStyle.Kind.MOVE))
    }

    @Test
    fun intersectionUvAlignsWithHandleUvOnBackWall() {
        val widget = WidgetItem(
            appWidgetId = 3,
            position = Vector3(0f, 8f, -29.9f),
            size = Vector3(2f, 0f, 1.5f),
            surface = BumpItem.Surface.BACK_WALL,
        )
        val half = widget.displayHalfSize()
        val (moveU, moveV) = WidgetHandleStyle.handleHitUv(widget, WidgetHandleStyle.Kind.MOVE)
        val cornerX = widget.position.x - half.x + WidgetHandleStyle.HANDLE_INSET
        val cornerY = widget.position.y - half.z + WidgetHandleStyle.HANDLE_INSET
        val (hitU, hitV) = WidgetSurfaceTransform.intersectionToTextureUv(
            widget,
            cornerX,
            cornerY,
            widget.position.z,
        )
        assertTrue(kotlin.math.abs(hitU - moveU) < 0.15f)
        assertTrue(kotlin.math.abs(hitV - moveV) < 0.15f)
    }
}
