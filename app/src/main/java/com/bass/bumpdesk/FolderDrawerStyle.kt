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

    /** ~14% of drawer half-width → ~28% of panel width per button (finger-sized at folder zoom). */
    fun touchButtonSize(halfDimX: Float, scale: Float): Float = halfDimX * 0.14f * scale

    fun touchHitHalf(halfDimX: Float, scale: Float): Float = touchButtonSize(halfDimX, scale) * 0.95f

    data class Layout(val halfDimX: Float, val halfDimZ: Float, val pos: FloatArray)

    enum class Hit { CLOSE, PREV_PAGE, NEXT_PAGE, PAGE_DOT, NONE }

    data class HitResult(val kind: Hit, val pageIndex: Int = -1)

    fun isMaterialDrawer(pile: Pile): Boolean =
        pile.isExpanded && pile.surface == BumpItem.Surface.FLOOR &&
            pile.isSystem && pile.name == "All Apps"

    fun usesMaterialChrome(pile: Pile): Boolean =
        pile.isExpanded && pile.surface == BumpItem.Surface.FLOOR

    fun gridSpacing(scale: Float): Float = GRID_SPACING * scale

    fun halfDimX(scale: Float): Float {
        val span = GRID_COLUMNS * gridSpacing(scale)
        return span / 2f + HORIZONTAL_PADDING * scale
    }

    fun halfDimZ(scale: Float): Float {
        val span = GRID_ROWS * gridSpacing(scale)
        return span / 2f + (TITLE_BAND + PAGINATION_BAND + CONTENT_INSET_Z) * scale
    }

    fun layout(pile: Pile, roomHalfX: Float, roomHalfZ: Float): Layout {
        val halfX = halfDimX(pile.scale)
        val halfZ = halfDimZ(pile.scale)
        val uiX = constrainDrawerCenter(pile.position.x, roomHalfX, halfX)
        val uiZ = constrainDrawerCenter(pile.position.z, roomHalfZ, halfZ)
        return Layout(halfX, halfZ, floatArrayOf(uiX, PANEL_Y, uiZ))
    }

    /** Keeps drawer center in bounds; centers on origin when the panel is wider than the room. */
    internal fun constrainDrawerCenter(value: Float, roomHalf: Float, panelHalf: Float): Float {
        val min = -roomHalf + panelHalf
        val max = roomHalf - panelHalf
        return if (min <= max) value.coerceIn(min, max) else 0f
    }

    fun totalPages(itemCount: Int): Int =
        ceil(itemCount.toFloat() / ITEMS_PER_PAGE).toInt().coerceAtLeast(1)

    fun gridAnchorZ(layout: Layout, scale: Float): Float =
        layout.pos[2] + (TITLE_BAND - PAGINATION_BAND) * GRID_VERTICAL_BIAS * scale

    fun itemGridPosition(
        pile: Pile,
        itemIndex: Int,
        layout: Layout,
    ): Pair<Float, Float> {
        val itemInPage = itemIndex % ITEMS_PER_PAGE
        val row = itemInPage / GRID_COLUMNS
        val col = itemInPage % GRID_COLUMNS
        val spacing = gridSpacing(pile.scale)
        val anchorZ = gridAnchorZ(layout, pile.scale)
        val x = layout.pos[0] + (col - (GRID_COLUMNS - 1) / 2f) * spacing
        val z = anchorZ + (row - (GRID_ROWS - 1) / 2f) * spacing
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

    fun closeButtonCenter(layout: Layout, scale: Float): FloatArray {
        val inset = touchButtonSize(layout.halfDimX, scale) * 0.92f
        return floatArrayOf(
            layout.pos[0] + layout.halfDimX - inset,
            layout.pos[2] - layout.halfDimZ + inset,
        )
    }

    /** Title bar center X, center Z, and half-width (plane scale X) — leaves room for the close button. */
    fun titleBarLayout(layout: Layout, scale: Float): FloatArray {
        val buttonReserve = touchButtonSize(layout.halfDimX, scale) * 2.55f
        val titleHalfWidth = (layout.halfDimX - buttonReserve * 0.55f - 0.15f * scale)
            .coerceIn(layout.halfDimX * 0.32f, layout.halfDimX * 0.78f)
        val centerX = layout.pos[0] - buttonReserve * 0.48f
        val centerZ = layout.pos[2] - layout.halfDimZ + TITLE_BAND * 0.5f * scale
        return floatArrayOf(centerX, centerZ, titleHalfWidth)
    }

    fun prevButtonCenter(layout: Layout, scale: Float): FloatArray {
        val inset = touchButtonSize(layout.halfDimX, scale) * 1.05f
        return floatArrayOf(
            layout.pos[0] - layout.halfDimX + inset,
            layout.pos[2] + layout.halfDimZ - inset,
        )
    }

    fun nextButtonCenter(layout: Layout, scale: Float): FloatArray {
        val inset = touchButtonSize(layout.halfDimX, scale) * 1.05f
        return floatArrayOf(
            layout.pos[0] + layout.halfDimX - inset,
            layout.pos[2] + layout.halfDimZ - inset,
        )
    }

    fun pageIndicatorCenter(layout: Layout, scale: Float, pageIndex: Int, totalPages: Int): FloatArray {
        val spacing = 0.42f * scale
        val startX = layout.pos[0] - ((totalPages - 1) * spacing) / 2f
        return floatArrayOf(
            startX + pageIndex * spacing,
            layout.pos[2] + layout.halfDimZ - 0.58f * scale,
        )
    }

    fun hitTestFloorDrawer(pile: Pile, hitX: Float, hitZ: Float, roomHalfX: Float, roomHalfZ: Float): HitResult {
        val layout = layout(pile, roomHalfX, roomHalfZ)
        val scale = pile.scale
        val hitHalf = touchHitHalf(layout.halfDimX, scale)
        val close = closeButtonCenter(layout, scale)
        if (abs(hitX - close[0]) < hitHalf && abs(hitZ - close[1]) < hitHalf) {
            return HitResult(Hit.CLOSE)
        }

        val pages = totalPages(pile.items.size)
        if (pile.scrollIndex > 0) {
            val prev = prevButtonCenter(layout, scale)
            if (abs(hitX - prev[0]) < hitHalf && abs(hitZ - prev[1]) < hitHalf) {
                return HitResult(Hit.PREV_PAGE)
            }
        }
        if (pile.scrollIndex < pages - 1) {
            val next = nextButtonCenter(layout, scale)
            if (abs(hitX - next[0]) < hitHalf && abs(hitZ - next[1]) < hitHalf) {
                return HitResult(Hit.NEXT_PAGE)
            }
        }

        for (i in 0 until pages) {
            val dot = pageIndicatorCenter(layout, scale, i, pages)
            val dotHitHalf = if (i == pile.scrollIndex) 0.22f * scale else 0.14f * scale
            if (abs(hitX - dot[0]) < dotHitHalf && abs(hitZ - dot[1]) < 0.18f * scale) {
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
