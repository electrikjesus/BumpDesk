package com.bass.bumpdesk

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class CameraDiagnosticsTest {

    @Before
    fun setUp() {
        BumpDeskLog.logEnabled = false
    }

    @Test
    fun centeredCameraHasZeroYaw() {
        val eye = floatArrayOf(0f, 30f, 45f)
        val lookAt = floatArrayOf(0f, 0f, 5f)
        val (yaw, _) = CameraDiagnostics.yawPitchDeg(eye, lookAt)
        assertEquals(0f, yaw, 0.01f)
    }

    @Test
    fun offsetLookAtProducesPositiveYaw() {
        val eye = floatArrayOf(0f, 30f, 45f)
        val lookAt = floatArrayOf(4f, 0f, 5f)
        val (yaw, _) = CameraDiagnostics.yawPitchDeg(eye, lookAt)
        assert(yaw > 1f)
    }

    @Test
    fun applyProfileDefaultsReportsZeroTargetYaw() {
        val camera = CameraManager()
        val profile = ScreenMetrics.computeProfile(1080, 2400, 3f)
        camera.applyProfileDefaults(profile)
        val state = CameraDiagnostics.from(camera)
        assertEquals(0f, state.targetYawDeg, 0.01f)
        assertEquals(0f, state.yawDeg, 0.01f)
    }
}
