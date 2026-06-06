package com.bass.bumpdesk

import kotlin.math.abs
import kotlin.math.ceil

/** Shared layout and Material-style chrome for the floor All Apps drawer (and floor folders). */
object FolderDrawerStyle {
    const val GRID_COLUMNS = 4
    const val GRID_ROWS = 4
    const val ITEMS_PER_PAGE = GRID_COLUMNS * GRID_ROWS

    const val GRID_SPACING = 2.35f
    const val HORIZONTAL_PADDING = 0.95f
    const val TITLE_BAND = 0.72f
    const val PAGINATION_BAND = 0.82f
    const val CONTENT_INSET_Z = 0.28f
    const val GRID_VERTICAL_BIAS = 0.12f

    const val PANEL_Y = 2.80f
    const val CHROME_Y = 2.96f
    const val ICON_Y = 3.06f
    const val MATERIAL_TITLE_ASPECT = 768f / 128f

    /** Compact wall-mounted Recents drawer (widget-style 2×2 cell grid). */
    const val RECENTS_WALL_CELL = 1.35f
    /** Icon scale as a fraction of compact cell spacing — tracks Grow/Shrink with the grid. */
    const val RECENTS_ICON_CELL_FRACTION = 0.36f
    const val WALL_TITLE_BAND = 0.38f
    const val WALL_PAGINATION_BAND = 0.42f
    const val WALL_HORIZONTAL_PADDING = 0.18f
    const val RECENTS_DRAWER_MIN_COLUMNS = 1
    const val RECENTS_DRAWER_MAX_COLUMNS = 12
    const val RECENTS_DRAWER_MIN_ROWS = 1
    const val RECENTS_DRAWER_MAX_ROWS = 4
    /** Reference width for chrome buttons so they do not grow with column count. */
    const val RECENTS_CHROME_REF_COLUMNS = 2

    fun compactRecentsReferenceHalfDimX(pile: Pile): Float =
        RECENTS_CHROME_REF_COLUMNS * recentsWallCellSpacing(pile) / 2f +
            WALL_HORIZONTAL_PADDING * pile.scale

    /** Title glyph half-width — fixed for compact Recents, independent of grid columns. */
    fun compactRecentsTitleHalfWidth(pile: Pile): Float {
        val refHalfX = compactRecentsReferenceHalfDimX(pile)
        val buttonHalf = refHalfX * 0.14f
        val leftInset = 0.16f * pile.scale
        val span = refHalfX * 2f - leftInset * 2f - buttonHalf * 2.35f
        return (span / 2f).coerceAtLeast(0.55f * pile.scale) * 1.10f
    }

    /** Title glyph half-depth — fixed height in the title band, not derived from width. */
    fun compactRecentsTitleHalfDepth(pile: Pile): Float =
        WALL_TITLE_BAND * 0.44f * pile.scale

    /** World-space corner radius for Material drawer panels (9-slice caps). */
    fun panelCornerRadius(halfDimX: Float, halfDimZ: Float, scale: Float): Float {
        val preferred = 0.22f * scale
        return preferred.coerceAtMost(halfDimX * 0.48f).coerceAtMost(halfDimZ * 0.48f)
    }

    /** Distance from room wall plane to the drawer anchor (world units). */
    const val WALL_DRAWER_INSET = 0.6f
    /** Staggered depth offsets so panel, icons, and chrome do not z-fight on walls. */
    const val WALL_PANEL_DEPTH = 0.06f
    const val WALL_ICON_DEPTH = 0.12f
    const val WALL_CHROME_DEPTH = 0.20f

    /** ~14% of drawer half-width → finger-sized chrome on floor / fixed-grid drawers. */
    fun touchButtonSize(halfDimX: Float, @Suppress("UNUSED_PARAMETER") scale: Float): Float =
        halfDimX * 0.14f

    fun touchHitHalf(halfDimX: Float, scale: Float): Float = touchButtonSize(halfDimX, scale) * 0.95f

    /**
     * Chrome button half-size: scales with [Pile.scale] and Grow/Shrink, not grid column count.
     * Wall Recents uses a 2-column reference width so resize-to-4×2 does not inflate buttons.
     */
    fun chromeButtonHalfSize(pile: Pile, layout: Layout, scale: Float): Float {
        if (usesFixedChromeScale(pile)) {
            val refHalfX = RECENTS_CHROME_REF_COLUMNS * recentsWallCellSpacing(pile) / 2f +
                WALL_HORIZONTAL_PADDING * pile.scale
            return refHalfX * 0.14f
        }
        return touchButtonSize(layout.halfDimX, scale)
    }

    fun chromeHitHalf(pile: Pile, layout: Layout, scale: Float): Float =
        chromeButtonHalfSize(pile, layout, scale) * 0.95f

    /** Inactive page-dot radius in world units at pile.scale = 1 (drawn as equal width × depth). */
    const val PAGE_DOT_RADIUS = 0.065f
    const val PAGE_DOT_CURRENT_SCALE = 1.4f
    /** Preferred center-to-center spacing between page dots at pile.scale = 1. */
    const val PAGE_DOT_SPACING = 0.38f

    fun pageIndicatorDotHalfSize(pile: Pile, isCurrent: Boolean): Float {
        val base = PAGE_DOT_RADIUS * pile.scale
        return if (isCurrent) base * PAGE_DOT_CURRENT_SCALE else base
    }

    fun pageIndicatorSpacing(pile: Pile, layout: Layout, totalPages: Int): Float {
        if (totalPages <= 1) return 0f
        val inactiveHalf = pageIndicatorDotHalfSize(pile, isCurrent = false)
        val preferred = PAGE_DOT_SPACING * pile.scale
        val minSpacing = inactiveHalf * 2.8f
        val chromeHalf = if (usesFixedChromeScale(pile)) {
            chromeButtonHalfSize(pile, layout, pile.scale)
        } else {
            floorChromeHalfSize(pile, layout, pile.scale)
        }
        val availableSpan = layout.halfDimX * 2f - chromeHalf * 4.2f
        val maxSpacing = if (availableSpan > 0f) {
            availableSpan / (totalPages - 1)
        } else {
            preferred
        }
        return preferred.coerceIn(minSpacing, maxSpacing.coerceAtLeast(minSpacing))
    }

    fun pageIndicatorHalfWidth(pile: Pile, @Suppress("UNUSED_PARAMETER") layout: Layout, isCurrent: Boolean): Float =
        pageIndicatorDotHalfSize(pile, isCurrent)

    fun pageIndicatorHalfDepth(pile: Pile, @Suppress("UNUSED_PARAMETER") layout: Layout, isCurrent: Boolean): Float =
        pageIndicatorDotHalfSize(pile, isCurrent)

    /** @deprecated Use [pageIndicatorDotHalfSize]; kept for callers that only have layout width. */
    fun pageIndicatorHalfWidth(halfDimX: Float, isCurrent: Boolean): Float =
        halfDimX * if (isCurrent) 0.06f else 0.03f

    /** @deprecated Use [pageIndicatorDotHalfSize]. */
    fun pageIndicatorHalfDepth(halfDimX: Float, isCurrent: Boolean): Float =
        pageIndicatorHalfWidth(halfDimX, isCurrent)

    /** Resize handle matches close/pagination chrome (fixed scale on compact Recents). */
    fun recentsDrawerResizeHandleHalfSize(pile: Pile, layout: Layout): Float =
        chromeButtonHalfSize(pile, layout, pile.scale)

    fun recentsDrawerResizeHandleHitRadius(pile: Pile, layout: Layout): Float =
        chromeHitHalf(pile, layout, pile.scale)

    fun recentsWallHandleHalfSize(pile: Pile): Float =
        recentsDrawerResizeHandleHalfSize(
            pile,
            layout(pile, roomHalfX = 30f, roomHalfZ = 30f),
        )

    fun recentsWallHandleHitRadius(pile: Pile): Float =
        recentsDrawerResizeHandleHitRadius(
            pile,
            layout(pile, roomHalfX = 30f, roomHalfZ = 30f),
        )

    data class Layout(val halfDimX: Float, val halfDimZ: Float, val pos: FloatArray)

    enum class Hit { CLOSE, TITLE, PREV_PAGE, NEXT_PAGE, PAGE_DOT, NONE }

    data class HitResult(val kind: Hit, val pageIndex: Int = -1)

    /**
     * Drawer layout profiles — shared chrome helpers branch on this so Recents features
     * (compact grid, nine-slice panel, fixed chrome, grow/shrink) do not affect standard folders.
     */
    enum class DrawerProfile {
        STANDARD_FLOOR,
        COMPACT_RECENTS,
    }

    fun drawerProfile(pile: Pile): DrawerProfile? = when {
        !pile.layoutAsExpandedDrawer() -> null
        usesCompactRecentsCellGrid(pile) -> DrawerProfile.COMPACT_RECENTS
        pile.surface == BumpItem.Surface.FLOOR -> DrawerProfile.STANDARD_FLOOR
        else -> null
    }

    fun usesNineSlicePanel(pile: Pile): Boolean =
        drawerProfile(pile) == DrawerProfile.COMPACT_RECENTS

    fun usesFixedChromeScale(pile: Pile): Boolean =
        usesCompactRecentsCellGrid(pile)

    fun floorChromeHalfSize(pile: Pile, layout: Layout, scale: Float): Float =
        if (usesFixedChromeScale(pile)) {
            chromeButtonHalfSize(pile, layout, scale)
        } else {
            touchButtonSize(layout.halfDimX, scale)
        }

    fun floorChromeHitHalf(pile: Pile, layout: Layout, scale: Float): Float =
        floorChromeHalfSize(pile, layout, scale) * 0.95f

    fun isMaterialDrawer(pile: Pile): Boolean = drawerProfile(pile) != null

    fun usesMaterialChrome(pile: Pile): Boolean = drawerProfile(pile) != null

    /** Pile whose folder chrome (panel, title, pagination) should render for the current view. */
    fun resolveOverlayPile(piles: List<Pile>, viewMode: CameraManager.ViewMode): Pile? {
        if (viewMode == CameraManager.ViewMode.FOLDER_EXPANDED) {
            return piles.firstOrNull { pile ->
                pile.layoutAsExpandedDrawer() && !pile.showsDesktopPinnedDrawer()
            }
        }
        return piles.firstOrNull { pile ->
            pile.showsDesktopPinnedDrawer() ||
                (pile.isRecentsPile() && pile.recentsOnWall() && pile.layoutAsExpandedDrawer())
        }
    }

    fun intersectFloorDrawerChromePlane(
        pile: Pile,
        rayStart: FloatArray,
        rayEnd: FloatArray,
        roomHalfX: Float,
        roomHalfZ: Float,
    ): FloatArray? {
        val layout = layout(pile, roomHalfX, roomHalfZ)
        val planeY = floorChromeY(pile, layout)
        val t = (planeY - rayStart[1]) / (rayEnd[1] - rayStart[1])
        if (t <= 0f) return null
        return floatArrayOf(
            rayStart[0] + t * (rayEnd[0] - rayStart[0]),
            rayStart[2] + t * (rayEnd[2] - rayStart[2]),
        )
    }

    fun coerceRecentsDrawerColumns(columns: Int): Int =
        columns.coerceIn(RECENTS_DRAWER_MIN_COLUMNS, RECENTS_DRAWER_MAX_COLUMNS)

    fun coerceRecentsDrawerRows(rows: Int): Int =
        rows.coerceIn(RECENTS_DRAWER_MIN_ROWS, RECENTS_DRAWER_MAX_ROWS)

    fun gridColumns(pile: Pile): Int = when {
        pile.isRecentsPile() && usesCompactRecentsCellGrid(pile) ->
            coerceRecentsDrawerColumns(pile.drawerGridColumns)
        pile.isRecentsPile() -> 2
        else -> GRID_COLUMNS
    }

    fun gridRows(pile: Pile): Int = when {
        pile.isRecentsPile() && usesCompactRecentsCellGrid(pile) ->
            coerceRecentsDrawerRows(pile.drawerGridRows)
        pile.isRecentsPile() -> 2
        else -> GRID_ROWS
    }

    fun itemsPerPage(pile: Pile): Int = gridColumns(pile) * gridRows(pile)

    fun gridSpacing(scale: Float): Float = GRID_SPACING * scale

    fun gridSpacing(pile: Pile): Float =
        when {
            pile.showsRecentsTaskCards() -> 5.0f * pile.scale
            usesCompactRecentsCellGrid(pile) -> recentsWallCellSpacing(pile)
            else -> gridSpacing(pile.scale)
        }

    fun recentsWallCellSpacing(pile: Pile): Float = RECENTS_WALL_CELL * pile.scale

    fun compactRecentsIconScale(pile: Pile): Float =
        RECENTS_ICON_CELL_FRACTION * recentsWallCellSpacing(pile)

    /** Target world scale for one Recents icon-grid tile (matches [PhysicsEngine.expandedPileItemScale] for icons). */
    fun recentsDrawerIconScale(pile: Pile): Float =
        if (usesCompactRecentsCellGrid(pile)) {
            compactRecentsIconScale(pile)
        } else {
            1.05f * pile.scale
        }

    /** Snap every visible page tile to the same scale; off-page tiles shrink for paging animation. */
    fun syncRecentsDrawerItemScales(pile: Pile) {
        if (!pile.isRecentsPile() || !pile.showsRecentsIconGrid() || !pile.layoutAsExpandedDrawer()) return
        val visibleScale = recentsDrawerIconScale(pile)
        val pageSize = itemsPerPage(pile)
        val pageStart = pile.scrollIndex * pageSize
        val pageEnd = pageStart + pageSize
        pile.items.forEachIndexed { index, item ->
            item.transform.scale = if (index in pageStart until pageEnd) visibleScale else 0.01f
        }
    }

    fun wallDrawerPlaneZ(roomSize: Float): Float = -roomSize + WALL_DRAWER_INSET

    /** Push geometry outward from the wall surface along the view-facing axis. */
    fun offsetFromWallSurface(
        surface: BumpItem.Surface,
        x: Float,
        y: Float,
        z: Float,
        depth: Float,
    ): Triple<Float, Float, Float> = when (surface) {
        BumpItem.Surface.LEFT_WALL -> Triple(x + depth, y, z)
        BumpItem.Surface.RIGHT_WALL -> Triple(x - depth, y, z)
        else -> Triple(x, y, z + depth)
    }

    fun usesCompactWallDrawer(pile: Pile): Boolean =
        pile.isRecentsPile() && pile.recentsOnWall()

    fun usesFloorPinnedRecents(pile: Pile): Boolean =
        pile.isRecentsPile() && pile.showsDesktopPinnedDrawer() && pile.surface == BumpItem.Surface.FLOOR

    fun usesCompactRecentsCellGrid(pile: Pile): Boolean =
        usesCompactWallDrawer(pile) || usesFloorPinnedRecents(pile)

    fun panelHalfDimX(pile: Pile): Float =
        if (usesCompactRecentsCellGrid(pile)) recentsWallHalfDimX(pile) else halfDimX(pile)

    fun panelHalfDimY(pile: Pile): Float =
        if (usesCompactRecentsCellGrid(pile)) recentsWallHalfDimY(pile) else halfDimZ(pile)

    fun recentsWallHalfDimX(pile: Pile): Float {
        val columns = gridColumns(pile)
        val span = columns * recentsWallCellSpacing(pile)
        return span / 2f + WALL_HORIZONTAL_PADDING * pile.scale
    }

    fun recentsWallHalfDimY(pile: Pile): Float {
        val rows = gridRows(pile)
        val gridHalf = rows * recentsWallCellSpacing(pile) / 2f
        return gridHalf + compactWallChrome(pile)
    }

    fun compactWallChrome(pile: Pile): Float {
        val title = WALL_TITLE_BAND * pile.scale
        val pagination = if (totalPages(pile) > 1) WALL_PAGINATION_BAND * pile.scale else 0f
        return title + pagination
    }

    fun computeRecentsWallGridResize(
        pile: Pile,
        deltaPrimary: Float,
        deltaVertical: Float,
        startCols: Int,
        startRows: Int,
    ): Pair<Int, Int> {
        val cell = recentsWallCellSpacing(pile).coerceAtLeast(0.01f)
        val colDelta = kotlin.math.round(deltaPrimary / cell).toInt()
        val rowDelta = kotlin.math.round(-deltaVertical / cell).toInt()
        val cols = coerceRecentsDrawerColumns(startCols + colDelta)
        val rows = coerceRecentsDrawerRows(startRows + rowDelta)
        return cols to rows
    }

    fun computeRecentsFloorGridResize(
        pile: Pile,
        deltaX: Float,
        deltaZ: Float,
        startCols: Int,
        startRows: Int,
    ): Pair<Int, Int> {
        val cell = recentsWallCellSpacing(pile).coerceAtLeast(0.01f)
        val colDelta = kotlin.math.round(deltaX / cell).toInt()
        val rowDelta = kotlin.math.round(deltaZ / cell).toInt()
        val cols = coerceRecentsDrawerColumns(startCols + colDelta)
        val rows = coerceRecentsDrawerRows(startRows + rowDelta)
        return cols to rows
    }

    private fun recentsDrawerResizeHitRadius(pile: Pile, layout: Layout): Float =
        recentsDrawerResizeHandleHitRadius(pile, layout)
            .coerceAtLeast(WidgetHandleStyle.HANDLE_SIZE * 0.45f)

    fun hitTestRecentsWallResizeHandle(
        pile: Pile,
        hitPrimary: Float,
        hitVertical: Float,
        roomHalfX: Float,
        roomHalfZ: Float,
        roomSize: Float,
    ): Boolean {
        if (!pile.showsDesktopPinnedDrawer() || !pile.recentsOnWall()) return false
        val layout = layoutForPile(pile, roomHalfX, roomHalfZ, roomSize)
        val handle = recentsWallResizeHandleCenter(pile, pile.surface, layout)
        val radius = recentsDrawerResizeHitRadius(pile, layout)
        return abs(hitPrimary - handle[0]) < radius && abs(hitVertical - handle[1]) < radius
    }

    fun recentsFloorResizeHandleCenter(pile: Pile, layout: Layout): FloatArray {
        val inset = recentsDrawerResizeHandleHalfSize(pile, layout) + WidgetHandleStyle.HANDLE_INSET * pile.scale
        // Sit just outside the bottom-right corner (matches wall: resize opposite the close control).
        return floatArrayOf(
            layout.pos[0] + layout.halfDimX + inset * 0.2f,
            layout.pos[2] + layout.halfDimZ + inset * 0.2f,
        )
    }

    fun hitTestRecentsFloorResizeHandle(
        pile: Pile,
        hitX: Float,
        hitZ: Float,
        roomHalfX: Float,
        roomHalfZ: Float,
    ): Boolean {
        if (!usesFloorPinnedRecents(pile)) return false
        val layout = layout(pile, roomHalfX, roomHalfZ)
        val handle = recentsFloorResizeHandleCenter(pile, layout)
        val radius = recentsDrawerResizeHitRadius(pile, layout)
        return abs(hitX - handle[0]) < radius && abs(hitZ - handle[1]) < radius
    }

    fun recentsWallResizeHandleCenter(pile: Pile, surface: BumpItem.Surface, layout: Layout): FloatArray {
        val inset = recentsDrawerResizeHandleHalfSize(pile, layout) + WidgetHandleStyle.HANDLE_INSET * pile.scale
        return when (surface) {
            BumpItem.Surface.LEFT_WALL -> floatArrayOf(
                layout.pos[2] + layout.halfDimX - inset,
                layout.pos[1] - layout.halfDimZ + inset,
            )
            BumpItem.Surface.RIGHT_WALL -> floatArrayOf(
                layout.pos[2] - layout.halfDimX + inset,
                layout.pos[1] - layout.halfDimZ + inset,
            )
            else -> floatArrayOf(
                layout.pos[0] + layout.halfDimX - inset,
                layout.pos[1] - layout.halfDimZ + inset,
            )
        }
    }

    fun halfDimX(pile: Pile): Float {
        val columns = gridColumns(pile)
        val span = columns * gridSpacing(pile)
        return span / 2f + HORIZONTAL_PADDING * pile.scale
    }

    fun halfDimZ(pile: Pile): Float {
        val rows = gridRows(pile)
        val span = rows * gridSpacing(pile)
        return span / 2f + (TITLE_BAND + PAGINATION_BAND + CONTENT_INSET_Z) * pile.scale
    }

    fun halfDimX(scale: Float): Float {
        val span = GRID_COLUMNS * gridSpacing(scale)
        return span / 2f + HORIZONTAL_PADDING * scale
    }

    fun halfDimZ(scale: Float): Float {
        val span = GRID_ROWS * gridSpacing(scale)
        return span / 2f + (TITLE_BAND + PAGINATION_BAND + CONTENT_INSET_Z) * scale
    }

    fun layout(pile: Pile, roomHalfX: Float, roomHalfZ: Float): Layout {
        val halfX = panelHalfDimX(pile)
        val halfZ = panelHalfDimY(pile)
        val uiX = constrainDrawerCenter(pile.position.x, roomHalfX, halfX)
        val uiZ = constrainDrawerCenter(pile.position.z, roomHalfZ, halfZ)
        val uiY = if (usesFloorPinnedRecents(pile)) {
            pile.position.y + 0.04f
        } else {
            PANEL_Y
        }
        return Layout(halfX, halfZ, floatArrayOf(uiX, uiY, uiZ))
    }

    fun floorChromeY(pile: Pile, layout: Layout): Float =
        if (usesFloorPinnedRecents(pile)) {
            layout.pos[1] + TITLE_BAND * 0.16f * pile.scale
        } else {
            CHROME_Y
        }

    fun floorIconY(pile: Pile, layout: Layout): Float =
        if (usesFloorPinnedRecents(pile)) {
            layout.pos[1] + 0.14f * pile.scale
        } else {
            ICON_Y
        }

    /** Keeps saved Recents pile coords aligned with clamped drawer layout (wall height / floor inset). */
    fun constrainRecentsPilePosition(
        pile: Pile,
        roomHalfX: Float,
        roomHalfZ: Float,
        roomSize: Float,
    ) {
        if (!pile.isRecentsPile()) return
        val layout = layoutForPile(pile, roomHalfX, roomHalfZ, roomSize)
        pile.position = when (pile.surface) {
            BumpItem.Surface.FLOOR -> Vector3(
                layout.pos[0],
                0.05f,
                layout.pos[2],
            )
            BumpItem.Surface.BACK_WALL -> Vector3(
                layout.pos[0],
                layout.pos[1],
                layout.pos[2],
            )
            BumpItem.Surface.LEFT_WALL -> Vector3(
                layout.pos[0],
                layout.pos[1],
                layout.pos[2],
            )
            BumpItem.Surface.RIGHT_WALL -> Vector3(
                layout.pos[0],
                layout.pos[1],
                layout.pos[2],
            )
        }
    }

    fun layoutForPile(pile: Pile, roomHalfX: Float, roomHalfZ: Float, roomSize: Float): Layout =
        when (pile.surface) {
            BumpItem.Surface.BACK_WALL -> backWallLayout(pile, roomHalfX, roomSize)
            BumpItem.Surface.LEFT_WALL -> leftWallLayout(pile, roomHalfZ, roomSize)
            BumpItem.Surface.RIGHT_WALL -> rightWallLayout(pile, roomHalfZ, roomSize)
            else -> layout(pile, roomHalfX, roomHalfZ)
        }

    fun wallRayT(surface: BumpItem.Surface, roomSize: Float, rS: FloatArray, rE: FloatArray): Float? {
        val t = when (surface) {
            BumpItem.Surface.BACK_WALL -> (-roomSize + 0.6f - rS[2]) / (rE[2] - rS[2])
            BumpItem.Surface.LEFT_WALL -> (-roomSize + 0.6f - rS[0]) / (rE[0] - rS[0])
            BumpItem.Surface.RIGHT_WALL -> (roomSize - 0.6f - rS[0]) / (rE[0] - rS[0])
            else -> null
        }
        return t?.takeIf { it > 0f }
    }

    fun backWallLayout(pile: Pile, roomHalfX: Float, roomSize: Float): Layout {
        val halfX = panelHalfDimX(pile)
        val halfY = panelHalfDimY(pile)
        val uiX = constrainDrawerCenter(pile.position.x, roomHalfX, halfX)
        val minY = halfY + 0.5f
        val maxY = roomSize - 2f - halfY
        val uiY = if (minY <= maxY) pile.position.y.coerceIn(minY, maxY) else pile.position.y
        val uiZ = wallDrawerPlaneZ(roomSize)
        return Layout(halfX, halfY, floatArrayOf(uiX, uiY, uiZ))
    }

    fun leftWallLayout(pile: Pile, roomHalfZ: Float, roomSize: Float): Layout {
        val halfAlong = panelHalfDimX(pile)
        val halfVertical = panelHalfDimY(pile)
        val uiZ = constrainDrawerCenter(pile.position.z, roomHalfZ, halfAlong)
        val minY = halfVertical + 0.5f
        val maxY = roomSize - 2f - halfVertical
        val uiY = if (minY <= maxY) pile.position.y.coerceIn(minY, maxY) else pile.position.y
        val uiX = -roomSize + WALL_DRAWER_INSET
        return Layout(halfAlong, halfVertical, floatArrayOf(uiX, uiY, uiZ))
    }

    fun rightWallLayout(pile: Pile, roomHalfZ: Float, roomSize: Float): Layout {
        val halfAlong = panelHalfDimX(pile)
        val halfVertical = panelHalfDimY(pile)
        val uiZ = constrainDrawerCenter(pile.position.z, roomHalfZ, halfAlong)
        val minY = halfVertical + 0.5f
        val maxY = roomSize - 2f - halfVertical
        val uiY = if (minY <= maxY) pile.position.y.coerceIn(minY, maxY) else pile.position.y
        val uiX = roomSize - WALL_DRAWER_INSET
        return Layout(halfAlong, halfVertical, floatArrayOf(uiX, uiY, uiZ))
    }

    fun gridAnchorY(layout: Layout, pile: Pile): Float {
        if (usesCompactWallDrawer(pile)) {
            val pagBand = if (totalPages(pile) > 1) WALL_PAGINATION_BAND * pile.scale else 0f
            val contentTop = layout.pos[1] + layout.halfDimZ - WALL_TITLE_BAND * pile.scale
            val contentBottom = layout.pos[1] - layout.halfDimZ + pagBand
            return (contentTop + contentBottom) / 2f
        }
        return layout.pos[1] + layout.halfDimZ -
            (TITLE_BAND + (TITLE_BAND - PAGINATION_BAND) * GRID_VERTICAL_BIAS) * pile.scale
    }

    fun gridAnchorY(layout: Layout, scale: Float): Float =
        layout.pos[1] + layout.halfDimZ - (TITLE_BAND + (TITLE_BAND - PAGINATION_BAND) * GRID_VERTICAL_BIAS) * scale

    fun itemGridPositionOnBackWall(
        pile: Pile,
        itemIndex: Int,
        layout: Layout,
    ): Pair<Float, Float> = wallItemGridPosition(pile, itemIndex, layout, horizontalAxis = 0)

    fun itemGridPositionOnSideWall(
        pile: Pile,
        itemIndex: Int,
        layout: Layout,
    ): Pair<Float, Float> = wallItemGridPosition(pile, itemIndex, layout, horizontalAxis = 2)

    private fun wallItemGridPosition(
        pile: Pile,
        itemIndex: Int,
        layout: Layout,
        horizontalAxis: Int,
    ): Pair<Float, Float> {
        val columns = gridColumns(pile)
        val rows = gridRows(pile)
        val pageSize = itemsPerPage(pile)
        val itemInPage = itemIndex % pageSize
        val row = itemInPage / columns
        val col = itemInPage % columns
        val spacing = gridSpacing(pile)
        val anchorY = gridAnchorY(layout, pile)
        val horizontalCenter = if (horizontalAxis == 0) layout.pos[0] else layout.pos[2]
        val alongWall = horizontalCenter + (col - (columns - 1) / 2f) * spacing
        val vertical = anchorY - (row - (rows - 1) / 2f) * spacing
        return alongWall to vertical
    }

    fun isInsideWallContentArea(pile: Pile, item: BumpItem, layout: Layout, scale: Float): Boolean {
        val bounds = wallContentBounds(pile, layout, scale)
        return when (pile.surface) {
            BumpItem.Surface.LEFT_WALL, BumpItem.Surface.RIGHT_WALL ->
                item.transform.position.z in bounds[0]..bounds[1] &&
                    item.transform.position.y in bounds[2]..bounds[3]
            else ->
                item.transform.position.x in bounds[0]..bounds[1] &&
                    item.transform.position.y in bounds[2]..bounds[3]
        }
    }

    private fun wallContentBounds(pile: Pile, layout: Layout, scale: Float): FloatArray {
        val titleBand = if (usesCompactWallDrawer(pile)) WALL_TITLE_BAND else TITLE_BAND
        val pagBand = if (usesCompactWallDrawer(pile)) WALL_PAGINATION_BAND else PAGINATION_BAND
        val topY = layout.pos[1] + layout.halfDimZ - titleBand * scale
        val bottomY = layout.pos[1] - layout.halfDimZ + pagBand * scale
        val sideInset = if (usesCompactWallDrawer(pile)) 0.12f * scale else 0.35f * scale
        return when (pile.surface) {
            BumpItem.Surface.LEFT_WALL, BumpItem.Surface.RIGHT_WALL -> floatArrayOf(
                layout.pos[2] - layout.halfDimX + sideInset,
                layout.pos[2] + layout.halfDimX - sideInset,
                bottomY,
                topY,
            )
            else -> floatArrayOf(
                layout.pos[0] - layout.halfDimX + sideInset,
                layout.pos[0] + layout.halfDimX - sideInset,
                bottomY,
                topY,
            )
        }
    }

    fun backWallContentBounds(layout: Layout, scale: Float): FloatArray {
        val topY = layout.pos[1] + layout.halfDimZ - TITLE_BAND * scale
        val bottomY = layout.pos[1] - layout.halfDimZ + PAGINATION_BAND * scale
        val sideInset = 0.35f * scale
        return floatArrayOf(
            layout.pos[0] - layout.halfDimX + sideInset,
            layout.pos[0] + layout.halfDimX - sideInset,
            bottomY,
            topY,
        )
    }

    fun isInsideBackWallContentArea(item: BumpItem, layout: Layout, scale: Float): Boolean {
        val b = backWallContentBounds(layout, scale)
        return item.transform.position.x in b[0]..b[1] &&
            item.transform.position.y in b[2]..b[3]
    }

    /** Only icons on the current drawer page and inside the visible content band are tappable. */
    fun isItemInteractableInDrawer(
        pile: Pile,
        item: BumpItem,
        roomHalfX: Float,
        roomHalfZ: Float,
        roomSize: Float,
    ): Boolean {
        if (!pile.layoutAsExpandedDrawer()) return true
        val index = pile.items.indexOf(item)
        if (index < 0) return true
        val pageSize = itemsPerPage(pile)
        if (index / pageSize != pile.scrollIndex) return false
        val layout = layoutForPile(pile, roomHalfX, roomHalfZ, roomSize)
        return when (pile.surface) {
            BumpItem.Surface.FLOOR -> isInsideContentArea(item, layout, pile.scale)
            else -> isInsideWallContentArea(pile, item, layout, pile.scale)
        }
    }

    fun closeButtonCenterBackWall(layout: Layout, scale: Float): FloatArray {
        val inset = touchButtonSize(layout.halfDimX, scale) * 0.92f
        return floatArrayOf(
            layout.pos[0] + layout.halfDimX - inset,
            layout.pos[1] + layout.halfDimZ - inset,
        )
    }

    fun titleBarLayoutBackWall(layout: Layout, scale: Float): FloatArray {
        val buttonReserve = touchButtonSize(layout.halfDimX, scale) * 2.55f
        val titleHalfWidth = (layout.halfDimX - buttonReserve * 0.55f - 0.15f * scale)
            .coerceIn(layout.halfDimX * 0.32f, layout.halfDimX * 0.78f)
        val centerX = layout.pos[0] - buttonReserve * 0.48f
        val centerY = layout.pos[1] + layout.halfDimZ - TITLE_BAND * 0.5f * scale
        return floatArrayOf(centerX, centerY, titleHalfWidth)
    }

    fun prevButtonCenterBackWall(layout: Layout, scale: Float): FloatArray {
        val inset = touchButtonSize(layout.halfDimX, scale) * 1.05f
        return floatArrayOf(
            layout.pos[0] - layout.halfDimX + inset,
            layout.pos[1] - layout.halfDimZ + inset,
        )
    }

    fun nextButtonCenterBackWall(layout: Layout, scale: Float): FloatArray {
        val inset = touchButtonSize(layout.halfDimX, scale) * 1.05f
        return floatArrayOf(
            layout.pos[0] + layout.halfDimX - inset,
            layout.pos[1] - layout.halfDimZ + inset,
        )
    }

    fun pageIndicatorCenterBackWall(layout: Layout, scale: Float, pageIndex: Int, totalPages: Int): FloatArray {
        val spacing = 0.42f * scale
        val startX = layout.pos[0] - ((totalPages - 1) * spacing) / 2f
        return floatArrayOf(
            startX + pageIndex * spacing,
            layout.pos[1] - layout.halfDimZ + 0.58f * scale,
        )
    }

    fun containsPointInWallDrawer(
        pile: Pile,
        hitPrimary: Float,
        hitVertical: Float,
        roomHalfX: Float,
        roomHalfZ: Float,
        roomSize: Float,
    ): Boolean {
        val layout = layoutForPile(pile, roomHalfX, roomHalfZ, roomSize)
        return when (pile.surface) {
            BumpItem.Surface.LEFT_WALL, BumpItem.Surface.RIGHT_WALL ->
                abs(hitPrimary - layout.pos[2]) <= layout.halfDimX &&
                    abs(hitVertical - layout.pos[1]) <= layout.halfDimZ
            BumpItem.Surface.BACK_WALL ->
                abs(hitPrimary - layout.pos[0]) <= layout.halfDimX &&
                    abs(hitVertical - layout.pos[1]) <= layout.halfDimZ
            else -> false
        }
    }

    fun hitTestWallDrawer(
        pile: Pile,
        hitPrimary: Float,
        hitVertical: Float,
        roomHalfX: Float,
        roomHalfZ: Float,
        roomSize: Float,
    ): HitResult {
        val layout = layoutForPile(pile, roomHalfX, roomHalfZ, roomSize)
        val scale = pile.scale
        val hitHalf = chromeHitHalf(pile, layout, scale)
        val close = wallCloseButtonCenter(pile, pile.surface, layout, scale)
        if (abs(hitPrimary - close[0]) < hitHalf && abs(hitVertical - close[1]) < hitHalf) {
            return HitResult(Hit.CLOSE)
        }

        val titleBand = if (usesCompactWallDrawer(pile)) WALL_TITLE_BAND else TITLE_BAND
        val title = wallTitleBarLayout(pile, layout, scale)
        val titleHalfDepth = if (usesCompactWallDrawer(pile)) {
            compactRecentsTitleHalfDepth(pile)
        } else {
            titleBand * 0.42f * scale
        }
        if (abs(hitPrimary - title[0]) < title[2] && abs(hitVertical - title[1]) < titleHalfDepth) {
            return HitResult(Hit.TITLE)
        }

        val pages = totalPages(pile)
        if (pile.scrollIndex > 0) {
            val prev = wallPrevButtonCenter(pile, pile.surface, layout, scale)
            if (abs(hitPrimary - prev[0]) < hitHalf && abs(hitVertical - prev[1]) < hitHalf) {
                return HitResult(Hit.PREV_PAGE)
            }
        }
        if (pile.scrollIndex < pages - 1) {
            val next = wallNextButtonCenter(pile, pile.surface, layout, scale)
            if (abs(hitPrimary - next[0]) < hitHalf && abs(hitVertical - next[1]) < hitHalf) {
                return HitResult(Hit.NEXT_PAGE)
            }
        }

        for (i in 0 until pages) {
            val dot = wallPageIndicatorCenter(pile, pile.surface, layout, scale, i, pages)
            val dotHitHalf = pageIndicatorDotHalfSize(pile, i == pile.scrollIndex)
            if (abs(hitPrimary - dot[0]) < dotHitHalf && abs(hitVertical - dot[1]) < dotHitHalf) {
                return HitResult(Hit.PAGE_DOT, pageIndex = i)
            }
        }
        return HitResult(Hit.NONE)
    }

    fun wallCloseButtonCenter(pile: Pile, surface: BumpItem.Surface, layout: Layout, scale: Float): FloatArray {
        val inset = chromeButtonHalfSize(pile, layout, scale) * 0.92f
        return when (surface) {
            BumpItem.Surface.LEFT_WALL -> floatArrayOf(
                layout.pos[2] + layout.halfDimX - inset,
                layout.pos[1] + layout.halfDimZ - inset,
            )
            BumpItem.Surface.RIGHT_WALL -> floatArrayOf(
                layout.pos[2] - layout.halfDimX + inset,
                layout.pos[1] + layout.halfDimZ - inset,
            )
            else -> floatArrayOf(
                layout.pos[0] + layout.halfDimX - inset,
                layout.pos[1] + layout.halfDimZ - inset,
            )
        }
    }

    fun wallTitleBarLayout(pile: Pile, layout: Layout, scale: Float): FloatArray {
        val surface = pile.surface
        val titleBand = if (usesCompactWallDrawer(pile)) WALL_TITLE_BAND else TITLE_BAND
        val buttonHalf = chromeButtonHalfSize(pile, layout, scale)
        val titleHalfWidth = if (usesCompactWallDrawer(pile)) {
            compactRecentsTitleHalfWidth(pile)
        } else {
            val buttonReserve = buttonHalf * 2.55f
            (layout.halfDimX - buttonReserve * 0.55f - 0.15f * scale)
                .coerceIn(layout.halfDimX * 0.32f, layout.halfDimX * 0.78f)
        }
        val centerVertical = layout.pos[1] + layout.halfDimZ - titleBand * 0.5f * scale
        val centerPrimary = if (usesCompactWallDrawer(pile)) {
            when (surface) {
                BumpItem.Surface.LEFT_WALL -> layout.pos[2] - layout.halfDimX + 0.16f * scale + titleHalfWidth
                BumpItem.Surface.RIGHT_WALL -> layout.pos[2] + layout.halfDimX - 0.16f * scale - titleHalfWidth
                else -> layout.pos[0] - layout.halfDimX + 0.16f * scale + titleHalfWidth
            }
        } else {
            when (surface) {
                BumpItem.Surface.LEFT_WALL -> layout.pos[2] + buttonHalf * 2.55f * 0.48f
                BumpItem.Surface.RIGHT_WALL -> layout.pos[2] - buttonHalf * 2.55f * 0.48f
                else -> layout.pos[0] - buttonHalf * 2.55f * 0.48f
            }
        }
        return floatArrayOf(centerPrimary, centerVertical, titleHalfWidth)
    }

    fun wallPaginationVerticalInset(pile: Pile, layout: Layout, scale: Float): Float =
        chromeButtonHalfSize(pile, layout, scale) * 1.05f

    /** Pulls prev/next arrows in from the side edges; next side clears the resize handle. */
    fun wallPaginationHorizontalInset(pile: Pile, layout: Layout, scale: Float, isNext: Boolean): Float {
        val corner = chromeButtonHalfSize(pile, layout, scale) * 1.05f
        val sidePull = chromeButtonHalfSize(pile, layout, scale) * 0.45f
        val resizeClearance = if (
            isNext &&
            usesCompactWallDrawer(pile) &&
            pile.showsDesktopPinnedDrawer()
        ) {
            recentsDrawerResizeHandleHalfSize(pile, layout) * 2.2f + WidgetHandleStyle.HANDLE_INSET * pile.scale
        } else {
            0f
        }
        return corner + sidePull + resizeClearance
    }

    fun wallPrevButtonCenter(pile: Pile, surface: BumpItem.Surface, layout: Layout, scale: Float): FloatArray {
        val horizontal = wallPaginationHorizontalInset(pile, layout, scale, isNext = false)
        val vertical = wallPaginationVerticalInset(pile, layout, scale)
        return when (surface) {
            BumpItem.Surface.LEFT_WALL -> floatArrayOf(
                layout.pos[2] - layout.halfDimX + horizontal,
                layout.pos[1] - layout.halfDimZ + vertical,
            )
            BumpItem.Surface.RIGHT_WALL -> floatArrayOf(
                layout.pos[2] + layout.halfDimX - horizontal,
                layout.pos[1] - layout.halfDimZ + vertical,
            )
            else -> floatArrayOf(
                layout.pos[0] - layout.halfDimX + horizontal,
                layout.pos[1] - layout.halfDimZ + vertical,
            )
        }
    }

    fun wallNextButtonCenter(pile: Pile, surface: BumpItem.Surface, layout: Layout, scale: Float): FloatArray {
        val horizontal = wallPaginationHorizontalInset(pile, layout, scale, isNext = true)
        val vertical = wallPaginationVerticalInset(pile, layout, scale)
        return when (surface) {
            BumpItem.Surface.LEFT_WALL -> floatArrayOf(
                layout.pos[2] + layout.halfDimX - horizontal,
                layout.pos[1] - layout.halfDimZ + vertical,
            )
            BumpItem.Surface.RIGHT_WALL -> floatArrayOf(
                layout.pos[2] - layout.halfDimX + horizontal,
                layout.pos[1] - layout.halfDimZ + vertical,
            )
            else -> floatArrayOf(
                layout.pos[0] + layout.halfDimX - horizontal,
                layout.pos[1] - layout.halfDimZ + vertical,
            )
        }
    }

    fun wallPageIndicatorCenter(
        pile: Pile,
        surface: BumpItem.Surface,
        layout: Layout,
        scale: Float,
        pageIndex: Int,
        totalPages: Int,
    ): FloatArray {
        val spacing = pageIndicatorSpacing(pile, layout, totalPages)
        val pagInset = if (usesCompactWallDrawer(pile)) WALL_PAGINATION_BAND * 0.5f else 0.58f
        val centerPrimary = when (surface) {
            BumpItem.Surface.LEFT_WALL, BumpItem.Surface.RIGHT_WALL -> layout.pos[2]
            else -> layout.pos[0]
        }
        val startPrimary = centerPrimary - ((totalPages - 1) * spacing) / 2f
        return floatArrayOf(
            startPrimary + pageIndex * spacing,
            layout.pos[1] - layout.halfDimZ + pagInset * scale,
        )
    }

    fun containsPointInBackWallDrawer(
        pile: Pile,
        hitX: Float,
        hitY: Float,
        roomHalfX: Float,
        roomSize: Float,
    ): Boolean = containsPointInWallDrawer(pile, hitX, hitY, roomHalfX, roomHalfX, roomSize)

    fun hitTestBackWallDrawer(
        pile: Pile,
        hitX: Float,
        hitY: Float,
        roomHalfX: Float,
        roomSize: Float,
    ): HitResult = hitTestWallDrawer(pile, hitX, hitY, roomHalfX, roomHalfX, roomSize)

    /** Keeps drawer center in bounds; centers on origin when the panel is wider than the room. */
    internal fun constrainDrawerCenter(value: Float, roomHalf: Float, panelHalf: Float): Float {
        val min = -roomHalf + panelHalf
        val max = roomHalf - panelHalf
        return if (min <= max) value.coerceIn(min, max) else 0f
    }

    fun totalPages(pile: Pile): Int =
        ceil(pile.items.size.toFloat() / itemsPerPage(pile)).toInt().coerceAtLeast(1)

    fun totalPages(itemCount: Int): Int =
        ceil(itemCount.toFloat() / ITEMS_PER_PAGE).toInt().coerceAtLeast(1)

    fun gridAnchorZ(layout: Layout, scale: Float): Float =
        layout.pos[2] + (TITLE_BAND - PAGINATION_BAND) * GRID_VERTICAL_BIAS * scale

    fun itemGridPosition(
        pile: Pile,
        itemIndex: Int,
        layout: Layout,
    ): Pair<Float, Float> {
        val columns = gridColumns(pile)
        val rows = gridRows(pile)
        val pageSize = itemsPerPage(pile)
        val itemInPage = itemIndex % pageSize
        val row = itemInPage / columns
        val col = itemInPage % columns
        val spacing = gridSpacing(pile)
        val anchorZ = gridAnchorZ(layout, pile.scale)
        val x = layout.pos[0] + (col - (columns - 1) / 2f) * spacing
        val z = anchorZ + (row - (rows - 1) / 2f) * spacing
        return x to z
    }

    fun contentBounds(layout: Layout, scale: Float): FloatArray {
        val topZ = layout.pos[2] - layout.halfDimZ + TITLE_BAND * scale
        val bottomZ = layout.pos[2] + layout.halfDimZ - PAGINATION_BAND * scale
        val sideInset = 0.35f * scale
        return floatArrayOf(
            layout.pos[0] - layout.halfDimX + sideInset,
            layout.pos[0] + layout.halfDimX - sideInset,
            topZ,
            bottomZ,
        )
    }

    fun isInsideContentArea(item: BumpItem, layout: Layout, scale: Float): Boolean {
        val b = contentBounds(layout, scale)
        return item.transform.position.x in b[0]..b[1] &&
            item.transform.position.z in b[2]..b[3]
    }

    fun closeButtonCenter(pile: Pile, layout: Layout, scale: Float): FloatArray {
        val inset = floorChromeHalfSize(pile, layout, scale) * 0.92f
        return floatArrayOf(
            layout.pos[0] + layout.halfDimX - inset,
            layout.pos[2] - layout.halfDimZ + inset,
        )
    }

    /** Title bar center X, center Z, and half-width (plane scale X) — leaves room for the close button. */
    fun titleBarLayout(pile: Pile, layout: Layout, scale: Float): FloatArray {
        val buttonReserve = touchButtonSize(layout.halfDimX, scale) * 2.55f
        val titleHalfWidth = if (usesFloorPinnedRecents(pile)) {
            compactRecentsTitleHalfWidth(pile)
        } else {
            (layout.halfDimX - buttonReserve * 0.55f - 0.15f * scale)
                .coerceIn(layout.halfDimX * 0.32f, layout.halfDimX * 0.78f)
        }
        val centerX = if (usesFloorPinnedRecents(pile)) {
            layout.pos[0] - layout.halfDimX + 0.16f * scale + titleHalfWidth
        } else {
            layout.pos[0] - buttonReserve * 0.48f
        }
        val centerZ = layout.pos[2] - layout.halfDimZ + TITLE_BAND * 0.5f * scale
        return floatArrayOf(centerX, centerZ, titleHalfWidth)
    }

    fun prevButtonCenter(pile: Pile, layout: Layout, scale: Float): FloatArray {
        val inset = floorChromeHalfSize(pile, layout, scale) * 1.05f
        return floatArrayOf(
            layout.pos[0] - layout.halfDimX + inset,
            layout.pos[2] + layout.halfDimZ - inset,
        )
    }

    fun nextButtonCenter(pile: Pile, layout: Layout, scale: Float): FloatArray {
        val inset = floorChromeHalfSize(pile, layout, scale) * 1.05f
        return floatArrayOf(
            layout.pos[0] + layout.halfDimX - inset,
            layout.pos[2] + layout.halfDimZ - inset,
        )
    }

    fun pageIndicatorCenter(pile: Pile, layout: Layout, pageIndex: Int, totalPages: Int): FloatArray {
        val spacing = pageIndicatorSpacing(pile, layout, totalPages)
        val startX = layout.pos[0] - ((totalPages - 1) * spacing) / 2f
        return floatArrayOf(
            startX + pageIndex * spacing,
            layout.pos[2] + layout.halfDimZ - 0.58f * pile.scale,
        )
    }

    fun containsPointInFloorDrawer(
        pile: Pile,
        hitX: Float,
        hitZ: Float,
        roomHalfX: Float,
        roomHalfZ: Float,
    ): Boolean {
        val layout = layout(pile, roomHalfX, roomHalfZ)
        return abs(hitX - layout.pos[0]) <= layout.halfDimX &&
            abs(hitZ - layout.pos[2]) <= layout.halfDimZ
    }

    fun hitTestFloorDrawer(pile: Pile, hitX: Float, hitZ: Float, roomHalfX: Float, roomHalfZ: Float): HitResult {
        val layout = layout(pile, roomHalfX, roomHalfZ)
        val scale = pile.scale
        val hitHalf = floorChromeHitHalf(pile, layout, scale)
        val close = closeButtonCenter(pile, layout, scale)
        if (abs(hitX - close[0]) < hitHalf && abs(hitZ - close[1]) < hitHalf) {
            return HitResult(Hit.CLOSE)
        }

        val title = titleBarLayout(pile, layout, scale)
        val titleHalfDepth = if (usesFloorPinnedRecents(pile)) {
            compactRecentsTitleHalfDepth(pile)
        } else {
            TITLE_BAND * 0.42f * scale
        }
        if (abs(hitX - title[0]) < title[2] && abs(hitZ - title[1]) < titleHalfDepth) {
            return HitResult(Hit.TITLE)
        }

        val pages = totalPages(pile)
        if (pile.scrollIndex > 0) {
            val prev = prevButtonCenter(pile, layout, scale)
            if (abs(hitX - prev[0]) < hitHalf && abs(hitZ - prev[1]) < hitHalf) {
                return HitResult(Hit.PREV_PAGE)
            }
        }
        if (pile.scrollIndex < pages - 1) {
            val next = nextButtonCenter(pile, layout, scale)
            if (abs(hitX - next[0]) < hitHalf && abs(hitZ - next[1]) < hitHalf) {
                return HitResult(Hit.NEXT_PAGE)
            }
        }

        for (i in 0 until pages) {
            val dot = pageIndicatorCenter(pile, layout, i, pages)
            val dotHitHalf = pageIndicatorDotHalfSize(pile, i == pile.scrollIndex)
            if (abs(hitX - dot[0]) < dotHitHalf && abs(hitZ - dot[1]) < dotHitHalf) {
                return HitResult(Hit.PAGE_DOT, pageIndex = i)
            }
        }
        return HitResult(Hit.NONE)
    }

    fun surfaceColor(): FloatArray = floatArrayOf(0.13f, 0.14f, 0.17f, 0.94f)

    fun onSurfaceColor(): FloatArray = floatArrayOf(0.96f, 0.97f, 0.99f, 1f)

    fun primaryColor(): FloatArray = floatArrayOf(0.55f, 0.74f, 1f, 1f)

    fun buttonContainerColor(): FloatArray = floatArrayOf(0.24f, 0.26f, 0.33f, 0.98f)

    fun inactiveIndicatorColor(): FloatArray = floatArrayOf(0.45f, 0.47f, 0.52f, 0.75f)
}
