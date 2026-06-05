package com.bass.bumpdesk

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetCaptureCoordinatorTest {

    @Test
    fun shouldCapture_throttlesRepeatedRequests() {
        WidgetCaptureCoordinator.clear(42)
        assertTrue(WidgetCaptureCoordinator.shouldCapture(42, force = true))
        WidgetCaptureCoordinator.markCaptureStarted(42)
        WidgetCaptureCoordinator.markCaptureFinished(42)

        assertFalse(WidgetCaptureCoordinator.shouldCapture(42))
    }

    @Test
    fun shouldUpdateAppWidgetSize_onlyWhenDpSizeChanges() {
        WidgetCaptureCoordinator.clear(7)
        assertTrue(WidgetCaptureCoordinator.shouldUpdateAppWidgetSize(7, 250, 110))
        assertFalse(WidgetCaptureCoordinator.shouldUpdateAppWidgetSize(7, 250, 110))
        assertTrue(WidgetCaptureCoordinator.shouldUpdateAppWidgetSize(7, 300, 110))
    }
}
