package com.bass.bumpdesk

import android.graphics.Color
import android.opengl.Matrix

class OverlayRenderer(
    private val shader: DefaultShader,
    private val textureManager: TextureManager,
) {
    private val folderBgPlane = Plane(shader)
    private val modelMatrix = FloatArray(16)
    private var panelTextureId = -1

    companion object {
        private const val PAGE_DOT_ACTIVE_KEY = "folder:pageDot:active:v2"
        private const val PAGE_DOT_INACTIVE_KEY = "folder:pageDot:inactive:v2"
    }

    private fun floatColorToArgb(color: FloatArray, forceOpaque: Boolean = false): Int {
        val alpha = if (forceOpaque) 255 else (color[3] * 255f).toInt().coerceIn(0, 255)
        return Color.argb(
            alpha,
            (color[0] * 255f).toInt().coerceIn(0, 255),
            (color[1] * 255f).toInt().coerceIn(0, 255),
            (color[2] * 255f).toInt().coerceIn(0, 255),
        )
    }

    private fun pageDotTextureId(isCurrent: Boolean): Int {
        val key = if (isCurrent) PAGE_DOT_ACTIVE_KEY else PAGE_DOT_INACTIVE_KEY
        textureManager.getCachedTexture(key).takeIf { it > 0 }?.let { return it }

        val fill = if (isCurrent) {
            floatColorToArgb(FolderDrawerStyle.primaryColor(), forceOpaque = true)
        } else {
            floatColorToArgb(FolderDrawerStyle.inactiveIndicatorColor(), forceOpaque = true)
        }
        val bitmap = TextRenderer.createPageIndicatorDotBitmap(fillColorArgb = fill)
        val textureId = textureManager.loadTextureFromBitmap(bitmap, key)
        bitmap.recycle()
        if (textureId <= 0) {
            BumpDeskLog.e(
                BumpDeskLog.Tag.CORE,
                "pageDotTexture",
                "upload failed isCurrent=$isCurrent key=$key",
            )
        } else {
            BumpDeskLog.d(
                BumpDeskLog.Tag.CORE,
                "pageDotTexture",
                "loaded isCurrent=$isCurrent tex=$textureId key=$key",
            )
        }
        return textureId
    }

    private fun drawPageIndicatorDot(
        vPMatrix: FloatArray,
        centerX: Float,
        centerY: Float,
        centerZ: Float,
        pile: Pile,
        isCurrent: Boolean,
        lightPos: FloatArray,
    ) {
        val textureId = pageDotTextureId(isCurrent)
        if (textureId <= 0) return

        folderBgPlane.resetUVs()
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, centerX, centerY, centerZ)
        val radius = FolderDrawerStyle.pageIndicatorDotHalfSize(pile, isCurrent)
        Matrix.scaleM(modelMatrix, 0, radius, 1f, radius)
        folderBgPlane.draw(
            vPMatrix,
            modelMatrix,
            floatArrayOf(1f, 1f, 1f, 1f),
            textureId,
            lightPos,
            1.0f,
            false,
        )
    }

    private data class PanelUvRect(val u0: Float, val v0: Float, val u1: Float, val v1: Float)

    private fun ensurePanelTexture() {
        if (panelTextureId != -1) return
        val panelBitmap = TextRenderer.createRoundedPanelBitmap(
            fillColor = Color.argb(240, 33, 35, 43),
        )
        panelTextureId = TextRenderer.loadTextTexture(panelBitmap)
        panelBitmap.recycle()
    }

    private fun drawFloorPanelRect(
        vPMatrix: FloatArray,
        centerX: Float,
        centerY: Float,
        centerZ: Float,
        halfWidth: Float,
        halfDepth: Float,
        color: FloatArray,
        textureId: Int,
        lightPos: FloatArray,
        uv: PanelUvRect? = null,
    ) {
        if (uv != null) {
            folderBgPlane.updateUVRect(uv.u0, uv.v0, uv.u1, uv.v1)
        }
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, centerX, centerY, centerZ)
        Matrix.scaleM(modelMatrix, 0, halfWidth, 1f, halfDepth)
        folderBgPlane.draw(vPMatrix, modelMatrix, color, textureId, lightPos, 1.0f, false)
        if (uv != null) {
            folderBgPlane.resetUVs()
        }
    }

    private enum class PanelSliceAxis {
        /** World +Y is the top edge (wall-mounted drawers). */
        WALL_Y,
        /** World −Z is the top/title edge (floor-mounted drawers). */
        FLOOR_Z,
    }

    private fun drawNineSliceMaterialPanel(
        vPMatrix: FloatArray,
        pile: Pile,
        halfX: Float,
        halfZ: Float,
        axis: PanelSliceAxis,
        lightPos: FloatArray,
        drawRect: (
            offsetPrimary: Float,
            offsetCross: Float,
            rectHalfPrimary: Float,
            rectHalfCross: Float,
            textureId: Int,
            uv: PanelUvRect?,
        ) -> Unit,
    ) {
        ensurePanelTexture()
        val cornerRadius = FolderDrawerStyle.panelCornerRadius(halfX, halfZ, pile.scale)
        if (cornerRadius < 0.04f * pile.scale) {
            drawRect(0f, 0f, halfX, halfZ, panelTextureId, null)
            return
        }

        val (uCorner, vCorner) = TextRenderer.panelCornerUvFractions()
        val centerHalfX = (halfX - cornerRadius).coerceAtLeast(0f)
        val centerHalfCross = (halfZ - cornerRadius).coerceAtLeast(0f)
        val cornerHalf = cornerRadius / 2f
        val topCross = if (axis == PanelSliceAxis.WALL_Y) halfZ - cornerHalf else -halfZ + cornerHalf
        val bottomCross = if (axis == PanelSliceAxis.WALL_Y) -halfZ + cornerHalf else halfZ - cornerHalf

        if (centerHalfX > 0f && centerHalfCross > 0f) {
            drawRect(0f, 0f, centerHalfX, centerHalfCross, -1, null)
        }
        if (centerHalfX > 0f) {
            drawRect(0f, topCross, centerHalfX, cornerHalf, -1, null)
            drawRect(0f, bottomCross, centerHalfX, cornerHalf, -1, null)
        }
        if (centerHalfCross > 0f) {
            drawRect(-halfX + cornerHalf, 0f, cornerHalf, centerHalfCross, -1, null)
            drawRect(halfX - cornerHalf, 0f, cornerHalf, centerHalfCross, -1, null)
        }

        drawRect(
            -halfX + cornerHalf,
            topCross,
            cornerHalf,
            cornerHalf,
            panelTextureId,
            PanelUvRect(0f, 0f, uCorner, vCorner),
        )
        drawRect(
            halfX - cornerHalf,
            topCross,
            cornerHalf,
            cornerHalf,
            panelTextureId,
            PanelUvRect(1f - uCorner, 0f, 1f, vCorner),
        )
        drawRect(
            -halfX + cornerHalf,
            bottomCross,
            cornerHalf,
            cornerHalf,
            panelTextureId,
            PanelUvRect(0f, 1f - vCorner, uCorner, 1f),
        )
        drawRect(
            halfX - cornerHalf,
            bottomCross,
            cornerHalf,
            cornerHalf,
            panelTextureId,
            PanelUvRect(1f - uCorner, 1f - vCorner, 1f, 1f),
        )
    }

    private fun drawFloorStandardMaterialPanel(
        vPMatrix: FloatArray,
        data: FolderDrawerStyle.Layout,
        lightPos: FloatArray,
    ) {
        ensurePanelTexture()
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, data.pos[0], data.pos[1], data.pos[2])
        Matrix.scaleM(modelMatrix, 0, data.halfDimX, 1f, data.halfDimZ)
        folderBgPlane.draw(
            vPMatrix,
            modelMatrix,
            FolderDrawerStyle.surfaceColor(),
            panelTextureId,
            lightPos,
            1.0f,
            false,
        )
    }

    private fun drawFloorMaterialPanel(
        vPMatrix: FloatArray,
        pile: Pile,
        data: FolderDrawerStyle.Layout,
        lightPos: FloatArray,
    ) {
        drawNineSliceMaterialPanel(
            vPMatrix,
            pile,
            data.halfDimX,
            data.halfDimZ,
            PanelSliceAxis.FLOOR_Z,
            lightPos,
        ) { offsetX, offsetZ, rectHalfX, rectHalfZ, textureId, uv ->
            drawFloorPanelRect(
                vPMatrix,
                data.pos[0] + offsetX,
                data.pos[1],
                data.pos[2] + offsetZ,
                rectHalfX,
                rectHalfZ,
                FolderDrawerStyle.surfaceColor(),
                textureId,
                lightPos,
                uv,
            )
        }
    }

    private fun drawWallMaterialPanel(
        vPMatrix: FloatArray,
        pile: Pile,
        data: FolderDrawerStyle.Layout,
        color: FloatArray,
        lightPos: FloatArray,
        depth: Float,
        nineSlice: Boolean,
    ) {
            if (!nineSlice) {
            ensurePanelTexture()
            drawWallElement(
                vPMatrix,
                pile.surface,
                data.pos[0],
                data.pos[1],
                data.pos[2],
                data.halfDimX,
                data.halfDimZ,
                color,
                panelTextureId,
                lightPos,
                depth,
            )
            return
        }
        drawNineSliceMaterialPanel(
            vPMatrix,
            pile,
            data.halfDimX,
            data.halfDimZ,
            PanelSliceAxis.WALL_Y,
            lightPos,
        ) { offsetPrimary, offsetVertical, rectHalfPrimary, rectHalfVertical, textureId, uv ->
            val primary = when (pile.surface) {
                BumpItem.Surface.LEFT_WALL, BumpItem.Surface.RIGHT_WALL -> data.pos[2] + offsetPrimary
                else -> data.pos[0] + offsetPrimary
            }
            val vertical = data.pos[1] + offsetVertical
            drawWallElementWithUv(
                vPMatrix,
                pile.surface,
                primary,
                vertical,
                data.pos[0],
                data.pos[2],
                rectHalfPrimary,
                rectHalfVertical,
                color,
                textureId,
                lightPos,
                depth,
                uv,
            )
        }
    }

    private fun drawWallElementWithUv(
        vPMatrix: FloatArray,
        surface: BumpItem.Surface,
        primary: Float,
        vertical: Float,
        wallX: Float,
        wallZ: Float,
        halfWidth: Float,
        halfHeight: Float,
        color: FloatArray,
        textureId: Int,
        lightPos: FloatArray,
        depth: Float,
        uv: PanelUvRect?,
    ) {
        if (uv != null) {
            folderBgPlane.updateUVRect(uv.u0, uv.v0, uv.u1, uv.v1)
        }
        when (surface) {
            BumpItem.Surface.LEFT_WALL, BumpItem.Surface.RIGHT_WALL -> drawWallElement(
                vPMatrix,
                surface,
                wallX,
                vertical,
                primary,
                halfWidth,
                halfHeight,
                color,
                textureId,
                lightPos,
                depth,
            )
            else -> drawWallElement(
                vPMatrix,
                surface,
                primary,
                vertical,
                wallZ,
                halfWidth,
                halfHeight,
                color,
                textureId,
                lightPos,
                depth,
            )
        }
        if (uv != null) {
            folderBgPlane.resetUVs()
        }
    }

    fun drawFolderUI(
        vPMatrix: FloatArray,
        pile: Pile,
        closeBtnTextureId: Int,
        nameTextureId: Int,
        lightPos: FloatArray,
        roomHalfX: Float,
        roomHalfZ: Float,
        roomSize: Float = roomHalfX,
    ) {
        if (pile.recentsOnWall() && FolderDrawerStyle.usesMaterialChrome(pile)) {
            drawWallFolderUI(vPMatrix, pile, closeBtnTextureId, nameTextureId, lightPos, roomHalfX, roomHalfZ, roomSize)
            return
        }

        val data = FolderDrawerStyle.layout(pile, roomHalfX, roomHalfZ)
        val uiX = data.pos[0]
        val uiY = data.pos[1]
        val uiZ = data.pos[2]
        val chromeY = FolderDrawerStyle.floorChromeY(pile, data)
        val material = FolderDrawerStyle.usesMaterialChrome(pile)

        if (material) {
            if (FolderDrawerStyle.usesNineSlicePanel(pile)) {
                drawFloorMaterialPanel(vPMatrix, pile, data, lightPos)
            } else {
                drawFloorStandardMaterialPanel(vPMatrix, data, lightPos)
            }
        } else {
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, uiX, uiY, uiZ)
            Matrix.scaleM(modelMatrix, 0, data.halfDimX, 1f, data.halfDimZ)
            folderBgPlane.draw(vPMatrix, modelMatrix, floatArrayOf(0.1f, 0.1f, 0.1f, 0.8f), -1, lightPos, 1.0f, false)
        }

        if (nameTextureId != -1) {
            val title = FolderDrawerStyle.titleBarLayout(pile, data, pile.scale)
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, title[0], chromeY, title[1])
            val titleDepth = when {
                FolderDrawerStyle.usesFloorPinnedRecents(pile) ->
                    FolderDrawerStyle.compactRecentsTitleHalfDepth(pile)
                material -> title[2] / FolderDrawerStyle.MATERIAL_TITLE_ASPECT
                else -> 0.3f * pile.scale
            }
            Matrix.scaleM(modelMatrix, 0, title[2], 1f, titleDepth)
            folderBgPlane.draw(
                vPMatrix,
                modelMatrix,
                FolderDrawerStyle.onSurfaceColor(),
                nameTextureId,
                lightPos,
                1.0f,
                false,
            )
        }

        val buttonSize = FolderDrawerStyle.floorChromeHalfSize(pile, data, pile.scale)
        val close = FolderDrawerStyle.closeButtonCenter(pile, data, pile.scale)
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, close[0], chromeY, close[1])
        Matrix.scaleM(modelMatrix, 0, buttonSize, 1f, buttonSize)
        folderBgPlane.draw(
            vPMatrix,
            modelMatrix,
            if (material) FolderDrawerStyle.buttonContainerColor() else floatArrayOf(0.8f, 0.2f, 0.2f, 1.0f),
            closeBtnTextureId,
            lightPos,
            1.0f,
            false,
        )

        if (FolderDrawerStyle.usesFloorPinnedRecents(pile)) {
            ensureResizeHandleTexture()
            val resize = FolderDrawerStyle.recentsFloorResizeHandleCenter(pile, data)
            val handleHalf = FolderDrawerStyle.recentsDrawerResizeHandleHalfSize(pile, data)
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, resize[0], chromeY, resize[1])
            Matrix.scaleM(modelMatrix, 0, handleHalf, 1f, handleHalf)
            folderBgPlane.draw(
                vPMatrix,
                modelMatrix,
                FolderDrawerStyle.buttonContainerColor(),
                resizeHandleTextureId,
                lightPos,
                1.0f,
                false,
            )
        }
    }

    fun drawPaginationUI(
        vPMatrix: FloatArray,
        pile: Pile,
        arrowLeftId: Int,
        arrowRightId: Int,
        lightPos: FloatArray,
        roomHalfX: Float,
        roomHalfZ: Float,
        roomSize: Float = roomHalfX,
    ) {
        if (pile.recentsOnWall() && FolderDrawerStyle.usesMaterialChrome(pile)) {
            drawWallPaginationUI(vPMatrix, pile, arrowLeftId, arrowRightId, lightPos, roomHalfX, roomHalfZ, roomSize)
            return
        }

        val data = FolderDrawerStyle.layout(pile, roomHalfX, roomHalfZ)
        val totalPages = FolderDrawerStyle.totalPages(pile)
        val currentPage = pile.scrollIndex
        val material = FolderDrawerStyle.usesMaterialChrome(pile)
        val chromeY = FolderDrawerStyle.floorChromeY(pile, data)
        val buttonSize = FolderDrawerStyle.floorChromeHalfSize(pile, data, pile.scale)

        if (arrowLeftId != -1 && currentPage > 0) {
            val prev = FolderDrawerStyle.prevButtonCenter(pile, data, pile.scale)
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, prev[0], chromeY, prev[1])
            Matrix.scaleM(modelMatrix, 0, buttonSize, 1f, buttonSize)
            folderBgPlane.draw(
                vPMatrix,
                modelMatrix,
                if (material) FolderDrawerStyle.buttonContainerColor() else floatArrayOf(1f, 1f, 1f, 1f),
                arrowLeftId,
                lightPos,
                1.0f,
                false,
            )
        }

        if (arrowRightId != -1 && currentPage < totalPages - 1) {
            val next = FolderDrawerStyle.nextButtonCenter(pile, data, pile.scale)
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, next[0], chromeY, next[1])
            Matrix.scaleM(modelMatrix, 0, buttonSize, 1f, buttonSize)
            folderBgPlane.draw(
                vPMatrix,
                modelMatrix,
                if (material) FolderDrawerStyle.buttonContainerColor() else floatArrayOf(1f, 1f, 1f, 1f),
                arrowRightId,
                lightPos,
                1.0f,
                false,
            )
        }

        for (i in 0 until totalPages) {
            val dot = FolderDrawerStyle.pageIndicatorCenter(pile, data, i, totalPages)
            drawPageIndicatorDot(
                vPMatrix,
                dot[0],
                chromeY,
                dot[1],
                pile,
                i == currentPage,
                lightPos,
            )
        }
    }

    private fun drawWallFolderUI(
        vPMatrix: FloatArray,
        pile: Pile,
        closeBtnTextureId: Int,
        nameTextureId: Int,
        lightPos: FloatArray,
        roomHalfX: Float,
        roomHalfZ: Float,
        roomSize: Float,
    ) {
        val data = FolderDrawerStyle.layoutForPile(pile, roomHalfX, roomHalfZ, roomSize)
        drawWallMaterialPanel(
            vPMatrix,
            pile,
            data,
            FolderDrawerStyle.surfaceColor(),
            lightPos,
            FolderDrawerStyle.WALL_PANEL_DEPTH,
            nineSlice = FolderDrawerStyle.usesNineSlicePanel(pile),
        )

        if (nameTextureId != -1) {
            val title = FolderDrawerStyle.wallTitleBarLayout(pile, data, pile.scale)
            val titleDepth = if (FolderDrawerStyle.usesCompactWallDrawer(pile)) {
                FolderDrawerStyle.compactRecentsTitleHalfDepth(pile)
            } else {
                title[2] / FolderDrawerStyle.MATERIAL_TITLE_ASPECT
            }
            drawWallChrome(
                vPMatrix,
                pile.surface,
                data,
                title[0],
                title[1],
                title[2],
                titleDepth,
                FolderDrawerStyle.onSurfaceColor(),
                nameTextureId,
                lightPos,
                FolderDrawerStyle.WALL_CHROME_DEPTH,
            )
        }

        val buttonSize = FolderDrawerStyle.chromeButtonHalfSize(pile, data, pile.scale)
        val close = FolderDrawerStyle.wallCloseButtonCenter(pile, pile.surface, data, pile.scale)
        drawWallChrome(
            vPMatrix,
            pile.surface,
            data,
            close[0],
            close[1],
            buttonSize,
            buttonSize,
            FolderDrawerStyle.buttonContainerColor(),
            closeBtnTextureId,
            lightPos,
            FolderDrawerStyle.WALL_CHROME_DEPTH + 0.02f,
        )

        if (pile.showsDesktopPinnedDrawer() && pile.recentsOnWall()) {
            ensureResizeHandleTexture()
            val resize = FolderDrawerStyle.recentsWallResizeHandleCenter(pile, pile.surface, data)
            val handleHalf = FolderDrawerStyle.recentsDrawerResizeHandleHalfSize(pile, data)
            drawWallChrome(
                vPMatrix,
                pile.surface,
                data,
                resize[0],
                resize[1],
                handleHalf,
                handleHalf,
                FolderDrawerStyle.buttonContainerColor(),
                resizeHandleTextureId,
                lightPos,
                FolderDrawerStyle.WALL_CHROME_DEPTH + 0.04f,
            )
        }
    }

    private var resizeHandleTextureId = -1

    private fun ensureResizeHandleTexture() {
        if (resizeHandleTextureId != -1) return
        val bitmap = TextRenderer.createMaterialHandleBitmap(
            glyph = "⤡",
            backgroundColor = WidgetHandleStyle.resizeBackground,
            foregroundColor = WidgetHandleStyle.resizeForeground,
            strokeColor = WidgetHandleStyle.strokeColor,
        )
        resizeHandleTextureId = TextRenderer.loadTextTexture(bitmap)
        bitmap.recycle()
    }

    private fun drawWallChrome(
        vPMatrix: FloatArray,
        surface: BumpItem.Surface,
        data: FolderDrawerStyle.Layout,
        primary: Float,
        vertical: Float,
        halfWidth: Float,
        halfHeight: Float,
        color: FloatArray,
        textureId: Int,
        lightPos: FloatArray,
        depth: Float,
    ) {
        when (surface) {
            BumpItem.Surface.LEFT_WALL, BumpItem.Surface.RIGHT_WALL -> drawWallElement(
                vPMatrix,
                surface,
                data.pos[0],
                vertical,
                primary,
                halfWidth,
                halfHeight,
                color,
                textureId,
                lightPos,
                depth,
            )
            else -> drawWallElement(
                vPMatrix,
                surface,
                primary,
                vertical,
                data.pos[2],
                halfWidth,
                halfHeight,
                color,
                textureId,
                lightPos,
                depth,
            )
        }
    }

    private fun drawWallPaginationUI(
        vPMatrix: FloatArray,
        pile: Pile,
        arrowLeftId: Int,
        arrowRightId: Int,
        lightPos: FloatArray,
        roomHalfX: Float,
        roomHalfZ: Float,
        roomSize: Float,
    ) {
        val data = FolderDrawerStyle.layoutForPile(pile, roomHalfX, roomHalfZ, roomSize)
        val totalPages = FolderDrawerStyle.totalPages(pile)
        val currentPage = pile.scrollIndex
        val buttonSize = FolderDrawerStyle.chromeButtonHalfSize(pile, data, pile.scale)

        if (arrowLeftId != -1 && currentPage > 0) {
            val prev = FolderDrawerStyle.wallPrevButtonCenter(pile, pile.surface, data, pile.scale)
            drawWallChrome(
                vPMatrix,
                pile.surface,
                data,
                prev[0],
                prev[1],
                buttonSize,
                buttonSize,
                FolderDrawerStyle.buttonContainerColor(),
                arrowLeftId,
                lightPos,
                FolderDrawerStyle.WALL_CHROME_DEPTH + 0.01f,
            )
        }

        if (arrowRightId != -1 && currentPage < totalPages - 1) {
            val next = FolderDrawerStyle.wallNextButtonCenter(pile, pile.surface, data, pile.scale)
            drawWallChrome(
                vPMatrix,
                pile.surface,
                data,
                next[0],
                next[1],
                buttonSize,
                buttonSize,
                FolderDrawerStyle.buttonContainerColor(),
                arrowRightId,
                lightPos,
                FolderDrawerStyle.WALL_CHROME_DEPTH + 0.01f,
            )
        }

        for (i in 0 until totalPages) {
            val dot = FolderDrawerStyle.wallPageIndicatorCenter(pile, pile.surface, data, pile.scale, i, totalPages)
            val isCurrent = i == currentPage
            val radius = FolderDrawerStyle.pageIndicatorDotHalfSize(pile, isCurrent)
            val dotTexture = pageDotTextureId(isCurrent)
            if (dotTexture <= 0) continue
            drawWallChrome(
                vPMatrix,
                pile.surface,
                data,
                dot[0],
                dot[1],
                radius,
                radius,
                floatArrayOf(1f, 1f, 1f, 1f),
                dotTexture,
                lightPos,
                FolderDrawerStyle.WALL_CHROME_DEPTH,
            )
        }
    }

    private fun drawWallElement(
        vPMatrix: FloatArray,
        surface: BumpItem.Surface,
        x: Float,
        y: Float,
        z: Float,
        halfWidth: Float,
        halfHeight: Float,
        color: FloatArray,
        textureId: Int,
        lightPos: FloatArray,
        depth: Float,
    ) {
        val (drawX, drawY, drawZ) = FolderDrawerStyle.offsetFromWallSurface(surface, x, y, z, depth)
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, drawX, drawY, drawZ)
        when (surface) {
            BumpItem.Surface.BACK_WALL -> Matrix.rotateM(modelMatrix, 0, 90f, 1f, 0f, 0f)
            BumpItem.Surface.LEFT_WALL -> {
                Matrix.rotateM(modelMatrix, 0, 90f, 0f, 1f, 0f)
                Matrix.rotateM(modelMatrix, 0, 180f, 0f, 1f, 0f)
                Matrix.rotateM(modelMatrix, 0, 90f, 1f, 0f, 0f)
            }
            BumpItem.Surface.RIGHT_WALL -> {
                Matrix.rotateM(modelMatrix, 0, -90f, 0f, 1f, 0f)
                Matrix.rotateM(modelMatrix, 0, 180f, 0f, 1f, 0f)
                Matrix.rotateM(modelMatrix, 0, 90f, 1f, 0f, 0f)
            }
            else -> Matrix.rotateM(modelMatrix, 0, 90f, 1f, 0f, 0f)
        }
        Matrix.scaleM(modelMatrix, 0, halfWidth, 1f, halfHeight)
        folderBgPlane.draw(vPMatrix, modelMatrix, color, textureId, lightPos, 1.0f, false)
    }

    fun drawRecentsOverlay(vPMatrix: FloatArray, pile: Pile, arrowLeftTextureId: Int, arrowRightTextureId: Int, lightPos: FloatArray) {
        val width = 6f * pile.scale
        val height = 4f * pile.scale

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, pile.position[0], pile.position[1], pile.position[2] - 0.05f)
        Matrix.rotateM(modelMatrix, 0, 90f, 1f, 0f, 0f)
        Matrix.scaleM(modelMatrix, 0, width, 1f, height)
        folderBgPlane.draw(vPMatrix, modelMatrix, floatArrayOf(0.1f, 0.1f, 0.1f, 0.7f), -1, lightPos, 1.0f, false)

        drawArrow(vPMatrix, pile.position[0] - width + 0.5f, pile.position[1], pile.position[2] + 0.05f, arrowLeftTextureId, lightPos)
        drawArrow(vPMatrix, pile.position[0] + width - 0.5f, pile.position[1], pile.position[2] + 0.05f, arrowRightTextureId, lightPos)
    }

    private fun drawArrow(vPMatrix: FloatArray, x: Float, y: Float, z: Float, textureId: Int, lightPos: FloatArray) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, x, y, z)
        Matrix.rotateM(modelMatrix, 0, 90f, 1f, 0f, 0f)
        Matrix.scaleM(modelMatrix, 0, 0.4f, 1f, 0.4f)
        folderBgPlane.draw(vPMatrix, modelMatrix, floatArrayOf(1f, 1f, 1f, 1f), textureId, lightPos, 1.0f, false)
    }

    fun getConstrainedFolderUI(
        pile: Pile,
        roomHalfX: Float,
        roomHalfZ: Float,
        roomSize: Float = roomHalfX,
    ): FolderDrawerStyle.Layout =
        FolderDrawerStyle.layoutForPile(pile, roomHalfX, roomHalfZ, roomSize)
}
