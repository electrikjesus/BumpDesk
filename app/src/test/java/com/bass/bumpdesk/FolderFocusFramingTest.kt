package com.bass.bumpdesk

import org.junit.Assert.assertTrue
import org.junit.Test

class FolderFocusFramingTest {
    @Test
    fun phonePortraitLargePanelZoomsOutMoreThanTablet() {
        val phone = FolderFocusFraming.compute(
            FolderFocusFraming.Params(
                panelHalfX = 5.5f,
                panelHalfZ = 4.8f,
                pileScale = 1f,
                screenWidthPx = 1080,
                screenHeightPx = 2400,
                isPhone = true,
            ),
        )
        val tablet = FolderFocusFraming.compute(
            FolderFocusFraming.Params(
                panelHalfX = 5.5f,
                panelHalfZ = 4.8f,
                pileScale = 1f,
                screenWidthPx = 2560,
                screenHeightPx = 1600,
                isPhone = false,
            ),
        )
        assertTrue(phone.zoomLevel > tablet.zoomLevel)
        assertTrue(phone.focusDistance >= tablet.focusDistance * 0.9f)
    }

    @Test
    fun widerPanelIncreasesZoomOrDistance() {
        val small = FolderFocusFraming.compute(
            FolderFocusFraming.Params(
                panelHalfX = 3f,
                panelHalfZ = 3f,
                pileScale = 1f,
                screenWidthPx = 1080,
                screenHeightPx = 2400,
                isPhone = true,
            ),
        )
        val large = FolderFocusFraming.compute(
            FolderFocusFraming.Params(
                panelHalfX = 8f,
                panelHalfZ = 6f,
                pileScale = 1f,
                screenWidthPx = 1080,
                screenHeightPx = 2400,
                isPhone = true,
            ),
        )
        assertTrue(large.zoomLevel >= small.zoomLevel)
        assertTrue(large.focusDistance >= small.focusDistance)
    }

    @Test
    fun pixelFoldInnerDisplayGetsModerateZoomOut() {
        val fold = FolderFocusFraming.compute(
            FolderFocusFraming.Params(
                panelHalfX = 5f,
                panelHalfZ = 4.5f,
                pileScale = 1f,
                screenWidthPx = 2208,
                screenHeightPx = 1756,
                isPhone = true,
                vFovDeg = 64f,
            ),
        )
        assertTrue(fold.zoomLevel >= 1.16f)
        assertTrue(fold.fieldOfView >= 62f)
    }

    @Test
    fun centerLookAtForPhone_nudgesLookAtWhenPanelIsOffCenter() {
        val panel = floatArrayOf(-14f, FolderDrawerStyle.PANEL_Y, -10f)
        val framing = FolderFocusFraming.compute(
            FolderFocusFraming.Params(
                panelHalfX = 5f,
                panelHalfZ = 4.5f,
                pileScale = 1f,
                screenWidthPx = 1080,
                screenHeightPx = 2400,
                isPhone = true,
                vFovDeg = 62f,
            ),
        )
        val centeredLookAt = FolderFocusFraming.centerLookAtForPhone(
            panelCenter = panel,
            initialLookAt = panel,
            focusDistance = framing.focusDistance,
            zoomLevel = framing.zoomLevel,
            vFovDeg = framing.fieldOfView,
            screenWidthPx = 1080,
            screenHeightPx = 2400,
        )
        assertTrue(centeredLookAt[0] != panel[0] || centeredLookAt[2] != panel[2])
        val eye = floatArrayOf(
            centeredLookAt[0],
            centeredLookAt[1] + framing.focusDistance * framing.zoomLevel,
            centeredLookAt[2] + framing.focusDistance * 0.5f * framing.zoomLevel,
        )
        val projected = FolderFocusFraming.projectToScreen(
            panel,
            eye,
            centeredLookAt,
            framing.fieldOfView,
            1080f / 2400f,
            1080f,
            2400f,
        )
        assertTrue(kotlin.math.abs(projected[0] - 540f) < 180f)
        assertTrue(kotlin.math.abs(projected[1] - 1248f) < 280f)
    }
}
