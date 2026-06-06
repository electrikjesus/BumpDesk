package com.bass.bumpdesk

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Bundle
import android.util.SizeF
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** Launcher-style resize limits derived from [AppWidgetProviderInfo]. */
data class WidgetResizeGrid(
    val cellDp: Float,
    val minWidthDp: Int,
    val minHeightDp: Int,
    val maxWidthDp: Int,
    val maxHeightDp: Int,
    val defaultWidthDp: Int,
    val defaultHeightDp: Int,
    val resizeMode: Int,
    val aspectRatio: Float,
)

object WidgetUtils {
    private const val MIN_CAPTURE_PX = 320
    private const val MAX_CAPTURE_PX = 2048
    private const val LAUNCHER_CELL_DP = 70f
    private const val FALLBACK_MAX_CELLS = 12

    fun aspectRatioFromProvider(info: AppWidgetProviderInfo): Float {
        val cellDp = dpPerCell(info)
        val w = defaultWidthDp(info, cellDp).coerceAtLeast(1)
        val h = defaultHeightDp(info, cellDp).coerceAtLeast(1)
        return w.toFloat() / h.toFloat()
    }

    fun supportsFreeformResize(info: AppWidgetProviderInfo): Boolean {
        return (info.resizeMode and AppWidgetProviderInfo.RESIZE_BOTH) == AppWidgetProviderInfo.RESIZE_BOTH
    }

    fun resizeGridFrom(info: AppWidgetProviderInfo): WidgetResizeGrid {
        val cellDp = dpPerCell(info)
        val minW = if (info.minResizeWidth > 0) info.minResizeWidth else info.minWidth
        val minH = if (info.minResizeHeight > 0) info.minResizeHeight else info.minHeight
        val defaultW = defaultWidthDp(info, cellDp)
        val defaultH = defaultHeightDp(info, cellDp)
        val maxW = if (info.maxResizeWidth > 0) {
            info.maxResizeWidth
        } else {
            (minW + FALLBACK_MAX_CELLS * cellDp).roundToInt().coerceAtLeast(defaultW + cellDp.roundToInt())
        }
        val maxH = if (info.maxResizeHeight > 0) {
            info.maxResizeHeight
        } else {
            (minH + FALLBACK_MAX_CELLS * cellDp).roundToInt().coerceAtLeast(defaultH + cellDp.roundToInt())
        }
        return WidgetResizeGrid(
            cellDp = cellDp,
            minWidthDp = minW,
            minHeightDp = minH,
            maxWidthDp = maxW,
            maxHeightDp = maxH,
            defaultWidthDp = defaultW,
            defaultHeightDp = defaultH,
            resizeMode = info.resizeMode,
            aspectRatio = aspectRatioFromProvider(info),
        )
    }

    private fun dpPerCell(info: AppWidgetProviderInfo): Float {
        if (info.targetCellWidth > 0 && info.minWidth > 0) {
            return (info.minWidth + 30f) / info.targetCellWidth
        }
        return LAUNCHER_CELL_DP
    }

    private fun defaultWidthDp(info: AppWidgetProviderInfo, cellDp: Float): Int {
        if (info.targetCellWidth > 0) {
            return (info.targetCellWidth * cellDp).roundToInt().coerceAtLeast(info.minWidth)
        }
        return info.minWidth
    }

    private fun defaultHeightDp(info: AppWidgetProviderInfo, cellDp: Float): Int {
        if (info.targetCellHeight > 0) {
            return (info.targetCellHeight * cellDp).roundToInt().coerceAtLeast(info.minHeight)
        }
        if (info.minResizeHeight > info.minHeight) {
            return info.minResizeHeight
        }
        // Strip minHeight (e.g. At a Glance 450×56 dp) is not the launcher default footprint.
        if (info.minWidth > info.minHeight * 3) {
            val wideDefault = (info.minWidth / 2.5f).roundToInt()
            val cellDefault = (2f * cellDp).roundToInt()
            val estimated = maxOf(cellDefault, wideDefault).coerceAtLeast(info.minHeight)
            val maxH = if (info.maxResizeHeight > 0) info.maxResizeHeight else estimated
            return estimated.coerceAtMost(maxH)
        }
        return info.minHeight
    }

    fun snapDpToCellGrid(dp: Int, cellDp: Float, minDp: Int, maxDp: Int): Int {
        val cells = (dp.toFloat() / cellDp).roundToInt().coerceAtLeast(1)
        val snapped = (cells * cellDp).roundToInt()
        return snapped.coerceIn(minDp, maxDp)
    }

    private const val REFERENCE_HALF_WIDTH = 2.5f

    fun normalizeGridSize(info: AppWidgetProviderInfo, size: Vector3): Vector3 {
        val grid = resizeGridFrom(info)
        var (widthDp, heightDp) = dpSizeFromWorldHalf(grid, size)
        widthDp = widthDp.coerceIn(grid.minWidthDp, grid.maxWidthDp)
        heightDp = heightDp.coerceIn(grid.minHeightDp, grid.maxHeightDp)
        // Legacy saves used mismatched axes; restore sensible height for wide widgets.
        if (widthDp >= grid.defaultWidthDp / 2 && heightDp < grid.defaultHeightDp / 2) {
            heightDp = (widthDp.toFloat() / grid.defaultWidthDp * grid.defaultHeightDp)
                .roundToInt()
                .coerceIn(grid.minHeightDp, grid.maxHeightDp)
        }
        return worldHalfSizeFromDp(grid, widthDp, heightDp)
    }

    fun defaultWorldSize(info: AppWidgetProviderInfo): Vector3 {
        val grid = resizeGridFrom(info)
        return worldHalfSizeFromDp(grid, grid.defaultWidthDp, grid.defaultHeightDp)
    }

    fun needsAspectCorrection(info: AppWidgetProviderInfo, size: Vector3): Boolean {
        if (size.x <= 0.01f || size.z <= 0.01f) return true
        val providerAspect = aspectRatioFromProvider(info)
        val actualAspect = size.x / size.z.coerceAtLeast(0.01f)
        // Only fix legacy corrupt saves that were stored as a square blob.
        val isNearlySquare = actualAspect in 0.85f..1.18f
        val providerIsNotSquare = providerAspect > 1.5f || providerAspect < 0.67f
        return isNearlySquare && providerIsNotSquare
    }

    fun worldHalfSizeFromDp(grid: WidgetResizeGrid, widthDp: Int, heightDp: Int): Vector3 {
        val halfW = REFERENCE_HALF_WIDTH * widthDp / grid.defaultWidthDp.coerceAtLeast(1)
        val halfH = REFERENCE_HALF_WIDTH * heightDp / grid.defaultWidthDp.coerceAtLeast(1)
        return Vector3(halfW, 0f, halfH.coerceAtLeast(0.05f))
    }

    fun dpSizeFromWorldHalf(grid: WidgetResizeGrid, size: Vector3): Pair<Int, Int> {
        val widthDp = (size.x / REFERENCE_HALF_WIDTH * grid.defaultWidthDp).roundToInt()
        val heightDp = (size.z / REFERENCE_HALF_WIDTH * grid.defaultWidthDp).roundToInt()
        return widthDp to heightDp
    }

    fun computeResizedSize(
        startSize: Vector3,
        du: Float,
        dv: Float,
        info: AppWidgetProviderInfo,
    ): Vector3 = computeResizedSize(startSize, du, dv, resizeGridFrom(info))

    fun computeResizedSize(
        startSize: Vector3,
        du: Float,
        dv: Float,
        grid: WidgetResizeGrid,
    ): Vector3 {
        val (startWDp, startHDp) = dpSizeFromWorldHalf(grid, startSize)
        val deltaWDp = (du / REFERENCE_HALF_WIDTH * grid.defaultWidthDp).roundToInt()
        val deltaHDp = (dv / REFERENCE_HALF_WIDTH * grid.defaultWidthDp).roundToInt()
        val aspect = grid.aspectRatio.coerceIn(0.35f, 8f)
        val canResizeWidth = (grid.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL) != 0
        val canResizeDepth = (grid.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL) != 0

        var targetW = startWDp
        var targetH = startHDp

        when {
            canResizeWidth && canResizeDepth -> {
                targetW = snapDpToCellGrid(startWDp + deltaWDp, grid.cellDp, grid.minWidthDp, grid.maxWidthDp)
                targetH = snapDpToCellGrid(startHDp + deltaHDp, grid.cellDp, grid.minHeightDp, grid.maxHeightDp)
            }
            canResizeWidth -> {
                targetW = snapDpToCellGrid(startWDp + deltaWDp, grid.cellDp, grid.minWidthDp, grid.maxWidthDp)
                targetH = (targetW / aspect).roundToInt().coerceIn(grid.minHeightDp, grid.maxHeightDp)
            }
            canResizeDepth -> {
                targetH = snapDpToCellGrid(startHDp + deltaHDp, grid.cellDp, grid.minHeightDp, grid.maxHeightDp)
                targetW = (targetH * aspect).roundToInt().coerceIn(grid.minWidthDp, grid.maxWidthDp)
            }
            else -> {
                val deltaDp = if (abs(du) >= abs(dv)) deltaWDp else deltaHDp
                targetW = snapDpToCellGrid(startWDp + deltaDp, grid.cellDp, grid.minWidthDp, grid.maxWidthDp)
                targetH = (targetW / aspect).roundToInt().coerceIn(grid.minHeightDp, grid.maxHeightDp)
            }
        }
        return worldHalfSizeFromDp(grid, targetW, targetH)
    }

    fun defaultSizeForAspect(aspect: Float, baseWidth: Float = 2.5f): Vector3 {
        val safeAspect = aspect.coerceIn(0.35f, 8f)
        return Vector3(baseWidth, 0f, baseWidth / safeAspect)
    }

    fun measureSizeForProvider(context: Context, info: AppWidgetProviderInfo): Pair<Int, Int> {
        val density = context.resources.displayMetrics.density
        val widthPx = (info.minWidth * density).roundToInt().coerceIn(MIN_CAPTURE_PX, MAX_CAPTURE_PX)
        val heightPx = (info.minHeight * density).roundToInt().coerceIn(MIN_CAPTURE_PX, MAX_CAPTURE_PX)
        return widthPx to heightPx
    }

    fun captureSizePx(context: Context, widget: WidgetItem, info: AppWidgetProviderInfo): Pair<Int, Int> {
        val grid = resizeGridFrom(info)
        var (widthDp, heightDp) = dpSizeFromWorldHalf(grid, widget.size)
        widthDp = widthDp.coerceIn(grid.minWidthDp, grid.maxWidthDp)
        heightDp = heightDp.coerceIn(grid.minHeightDp, grid.maxHeightDp)
        val density = context.resources.displayMetrics.density
        val widthPx = (widthDp * density).roundToInt().coerceIn(MIN_CAPTURE_PX, MAX_CAPTURE_PX)
        val heightPx = (heightDp * density).roundToInt().coerceIn(MIN_CAPTURE_PX, MAX_CAPTURE_PX)
        return widthPx to heightPx
    }

    fun sizeWithAspect(baseSize: Vector3, aspect: Float): Vector3 {
        val safeAspect = aspect.coerceIn(0.35f, 8f)
        val dominant = max(baseSize.x, baseSize.z)
        return if (dominant == baseSize.x) {
            Vector3(dominant, 0f, (dominant / safeAspect).coerceIn(0.2f, 10f))
        } else {
            Vector3((dominant * safeAspect).coerceIn(0.2f, 10f), 0f, dominant)
        }
    }

    /**
     * Sizes the host view and tells the provider how much space it has.
     * Only calls updateAppWidgetSize when the dp size actually changed to avoid provider ping-pong.
     */
    fun configureHostView(
        hostView: AppWidgetHostView,
        context: Context,
        info: AppWidgetProviderInfo,
        widget: WidgetItem,
    ) {
        val (widthPx, heightPx) = captureSizePx(context, widget, info)
        val density = context.resources.displayMetrics.density
        val widthDp = max((widthPx / density).roundToInt(), info.minWidth)
        val heightDp = max((heightPx / density).roundToInt(), info.minHeight)

        hostView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        hostView.setPadding(0, 0, 0, 0)
        // Keep each host view in its own off-screen slot so stacked layouts cannot bleed captures.
        hostView.translationX = widget.appWidgetId * 4096f
        hostView.translationY = 0f

        if (WidgetCaptureCoordinator.shouldUpdateAppWidgetSize(widget.appWidgetId, widthDp, heightDp)) {
            val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(widget.appWidgetId)
            val newOptions = options ?: Bundle()
            hostView.updateAppWidgetSize(
                newOptions,
                listOf(SizeF(widthDp.toFloat(), heightDp.toFloat())),
            )
        }

        if (hostView.width != widthPx || hostView.height != heightPx) {
            val widthSpec = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
            hostView.measure(widthSpec, heightSpec)
            hostView.layout(0, 0, widthPx, heightPx)
        }

        BumpDeskLog.d(
            BumpDeskLog.Tag.WIDGET,
            "configureHostView",
            "id=${widget.appWidgetId} px=${widthPx}x$heightPx dp=${widthDp}x$heightDp " +
                "grid=${widthDp}x$heightDp/${gridSummary(info)} provider=${info.provider.className}",
        )
    }

    private fun gridSummary(info: AppWidgetProviderInfo): String {
        val grid = resizeGridFrom(info)
        return "min=${grid.minWidthDp}x${grid.minHeightDp} max=${grid.maxWidthDp}x${grid.maxHeightDp}"
    }
}
