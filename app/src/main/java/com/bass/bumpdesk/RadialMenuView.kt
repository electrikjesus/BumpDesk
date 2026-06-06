package com.bass.bumpdesk

import android.content.Context
import android.graphics.*
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.*

class RadialMenuView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var items = listOf<RadialMenuItem>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(245, 246, 250)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private var centerX = 0f
    private var centerY = 0f
    private var layout = RadialLayout()

    private var selectedIndex = -1
    private var selectedSubIndex = -1

    private var onItemSelected: ((RadialMenuItem) -> Unit)? = null
    private var onDismiss: (() -> Unit)? = null

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        alpha = 70
        maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.OUTER)
    }

    private var isFirstUpAfterShow = false

    private data class RadialLayout(
        val innerRadius: Float = 56f,
        val outerRadius: Float = 152f,
        val secondaryOuterRadius: Float = 244f,
        val totalArc: Float = 160f,
        val startAngle: Float = 190f,
        val sweepAngle: Float = 32f,
    )

    fun setItems(items: List<RadialMenuItem>, x: Float, y: Float, onSelected: (RadialMenuItem) -> Unit, onDismiss: () -> Unit) {
        this.items = items
        val spanW = if (width > 0) width.toFloat() else resources.displayMetrics.widthPixels.toFloat()
        val spanH = if (height > 0) height.toFloat() else resources.displayMetrics.heightPixels.toFloat()
        layout = computeLayout(items.size, spanW, spanH)
        textPaint.textSize = ScreenMetrics.dpToPx(context, 14f)

        this.centerX = RadialMenuGeometry.clampMenuCenter(x, layout.secondaryOuterRadius, spanW)
        this.centerY = RadialMenuGeometry.clampMenuCenter(y, layout.secondaryOuterRadius, spanH)

        this.onItemSelected = onSelected
        this.onDismiss = onDismiss
        this.selectedIndex = -1
        this.selectedSubIndex = -1
        this.isFirstUpAfterShow = true
        visibility = VISIBLE
        invalidate()
    }

    private fun computeLayout(itemCount: Int, spanW: Float, spanH: Float): RadialLayout {
        val count = itemCount.coerceAtLeast(1)
        val menuScale = RadialMenuPreferences.sizeScale(context)
        val baseInner = ScreenMetrics.radialInnerRadiusPx(context) * menuScale
        val baseOuter = ScreenMetrics.radialOuterRadiusPx(context) * menuScale
        val scaleFactor = RadialMenuPreferences.itemCountScaleFactor(count, menuScale)
        val inner = baseInner * scaleFactor
        val outer = baseOuter * scaleFactor
        val secondary = outer + ScreenMetrics.radialSecondaryOffsetPx(context) * menuScale
        val fitted = RadialMenuGeometry.fitRadiiToScreen(inner, outer, secondary, spanW, spanH)
        val totalArc = RadialMenuStyle.totalArcForItemCount(count)
        val startAngle = 270f - totalArc / 2f
        val sweepAngle = totalArc / count
        return RadialLayout(
            fitted.inner,
            fitted.outer,
            fitted.secondary,
            totalArc,
            startAngle,
            sweepAngle,
        )
    }

    override fun onDraw(canvas: Canvas) {
        if (items.isEmpty()) return

        val rect = RectF(centerX - layout.outerRadius, centerY - layout.outerRadius, centerX + layout.outerRadius, centerY + layout.outerRadius)
        val innerRect = RectF(centerX - layout.innerRadius, centerY - layout.innerRadius, centerX + layout.innerRadius, centerY + layout.innerRadius)

        paint.color = Color.argb(40, 0, 0, 0)
        canvas.drawCircle(centerX, centerY, layout.outerRadius + 14f, paint)

        for (i in items.indices) {
            val angle = layout.startAngle + i * layout.sweepAngle
            drawItem(canvas, items[i], angle, layout.sweepAngle, rect, innerRect, i == selectedIndex, false)

            if (i == selectedIndex && items[i].subItems != null) {
                drawSubItems(canvas, items[i].subItems!!, angle)
            }
        }
    }

    private fun drawSubItems(canvas: Canvas, subItems: List<RadialMenuItem>, parentAngle: Float) {
        val subLayout = RadialMenuGeometry.subMenuLayout(
            parentAngle,
            layout.sweepAngle,
            subItems.size,
            RadialMenuStyle.MIN_SUB_SWEEP_DEG,
        )
        val subRect = RectF(
            centerX - layout.secondaryOuterRadius,
            centerY - layout.secondaryOuterRadius,
            centerX + layout.secondaryOuterRadius,
            centerY + layout.secondaryOuterRadius,
        )
        val subInnerRect = RectF(
            centerX - (layout.outerRadius + 8f),
            centerY - (layout.outerRadius + 8f),
            centerX + (layout.outerRadius + 8f),
            centerY + (layout.outerRadius + 8f),
        )
        for (j in subItems.indices) {
            val subAngle = subLayout.subStart + j * subLayout.subSweep
            drawItem(canvas, subItems[j], subAngle, subLayout.subSweep, subRect, subInnerRect, j == selectedSubIndex, true)
        }
    }

    private fun drawItem(
        canvas: Canvas,
        item: RadialMenuItem,
        angle: Float,
        sweepAngle: Float,
        rect: RectF,
        innerRect: RectF,
        isSelected: Boolean,
        isSecondary: Boolean,
    ) {
        paint.style = Paint.Style.FILL
        if (isSelected) {
            val selectionColor = ThemeManager.getSelectionColor()
            val colorInt = Color.argb(
                (selectionColor[3] * 255).toInt(),
                (selectionColor[0] * 255).toInt(),
                (selectionColor[1] * 255).toInt(),
                (selectionColor[2] * 255).toInt(),
            )
            paint.shader = LinearGradient(
                centerX,
                centerY - rect.width() / 2,
                centerX,
                centerY - innerRect.width() / 2,
                intArrayOf(colorInt, adjustAlpha(colorInt, 0.82f)),
                null,
                Shader.TileMode.CLAMP,
            )
        } else {
            paint.shader = null
            paint.color = if (isSecondary) RadialMenuStyle.secondaryFill else RadialMenuStyle.surfaceFill
        }

        val path = Path()
        path.arcTo(rect, angle, sweepAngle)
        path.arcTo(innerRect, angle + sweepAngle, -sweepAngle)
        path.close()

        if (isSelected) canvas.drawPath(path, shadowPaint)
        canvas.drawPath(path, paint)

        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = ScreenMetrics.dpToPx(context, 1.25f)
        paint.color = if (isSelected) Color.argb(220, 245, 246, 250) else RadialMenuStyle.strokeColor
        canvas.drawPath(path, paint)

        val midAngle = angle + sweepAngle / 2f
        val rad = Math.toRadians(midAngle.toDouble())
        val ringSpan = rect.width() / 2f - innerRect.width() / 2f
        val iconRadius = innerRect.width() / 2f + ringSpan * 0.38f
        val labelRadius = innerRect.width() / 2f + ringSpan * 0.78f

        val iconX = centerX + cos(rad).toFloat() * iconRadius
        val iconY = centerY + sin(rad).toFloat() * iconRadius
        val labelX = centerX + cos(rad).toFloat() * labelRadius
        val labelY = centerY + sin(rad).toFloat() * labelRadius

        item.iconRes?.let { iconRes ->
            ContextCompat.getDrawable(context, iconRes)?.let { icon ->
                val iconSize = ScreenMetrics.dpToPx(context, if (isSecondary) 32f else 36f).toInt()
                val left = (iconX - iconSize / 2).toInt()
                val top = (iconY - iconSize / 2).toInt()
                icon.setBounds(left, top, left + iconSize, top + iconSize)
                icon.setTint(Color.WHITE)
                icon.draw(canvas)
            }
        }

        val maxLabelWidth = ringSpan * 0.95f
        textPaint.textSize = ScreenMetrics.dpToPx(context, if (isSecondary) 13f else 14f)
        drawLabelChip(canvas, item.label, labelX, labelY, maxLabelWidth)
    }

    private fun drawLabelChip(canvas: Canvas, label: String, cx: Float, cy: Float, maxWidth: Float) {
        val display = TextUtils.ellipsize(label, textPaint, maxWidth, TextUtils.TruncateAt.END).toString()
        val textWidth = textPaint.measureText(display)
        val padH = ScreenMetrics.dpToPx(context, 10f)
        val padV = ScreenMetrics.dpToPx(context, 5f)
        val fm = textPaint.fontMetrics
        val chipW = (textWidth + padH * 2).coerceAtMost(maxWidth + padH * 2)
        val chipH = (fm.descent - fm.ascent) + padV * 2
        val rect = RectF(cx - chipW / 2f, cy - chipH / 2f, cx + chipW / 2f, cy + chipH / 2f)
        chipPaint.color = RadialMenuStyle.labelChipFill
        canvas.drawRoundRect(rect, chipH / 2f, chipH / 2f, chipPaint)
        canvas.drawText(display, cx, cy - (fm.ascent + fm.descent) / 2f, textPaint)
    }

    private fun adjustAlpha(color: Int, factor: Float): Int =
        Color.argb(
            (Color.alpha(color) * factor).toInt(),
            Color.red(color),
            Color.green(color),
            Color.blue(color),
        )

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        val dx = x - centerX
        val dy = y - centerY
        val dist = sqrt(dx * dx + dy * dy)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (dist > layout.secondaryOuterRadius * 1.2f || (dist < layout.innerRadius && !isFirstUpAfterShow)) {
                    dismiss()
                    return true
                }
                updateSelection(x, y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                updateSelection(x, y)
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (selectedSubIndex != -1 && selectedIndex != -1) {
                    val subItem = items[selectedIndex].subItems!![selectedSubIndex]
                    BumpDeskLog.d(
                        BumpDeskLog.Tag.RADIAL_MENU,
                        "selectSubItem",
                        "parent=${items[selectedIndex].label} sub=${subItem.label}",
                    )
                    subItem.action?.invoke()
                    dismiss()
                } else if (selectedIndex != -1) {
                    val item = items[selectedIndex]
                    if (item.subItems == null) {
                        item.action?.invoke()
                        dismiss()
                    }
                } else if (!isFirstUpAfterShow) {
                    dismiss()
                }
                isFirstUpAfterShow = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                dismiss()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateSelection(x: Float, y: Float) {
        val dx = x - centerX
        val dy = y - centerY
        val dist = sqrt(dx * dx + dy * dy)

        if (dist < layout.innerRadius || dist > layout.secondaryOuterRadius) {
            selectedIndex = -1
            selectedSubIndex = -1
            invalidate()
            return
        }

        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()).toDouble()).toFloat()
        if (angle < 0) angle += 360f

        var normalizedAngle = angle - layout.startAngle
        while (normalizedAngle < 0) normalizedAngle += 360f

        if (dist <= layout.outerRadius) {
            selectedIndex = if (normalizedAngle < layout.totalArc) {
                (normalizedAngle / layout.sweepAngle).toInt().coerceIn(0, items.size - 1)
            } else {
                -1
            }
            selectedSubIndex = -1
            invalidate()
            return
        }

        if (normalizedAngle >= layout.totalArc) {
            selectedIndex = -1
            selectedSubIndex = -1
            invalidate()
            return
        }

        // Outer ring: match drawn sub-item segments (Column/Grid/Row share one parent wedge).
        for (i in items.indices) {
            val subItems = items[i].subItems ?: continue
            val parentAngle = layout.startAngle + i * layout.sweepAngle
            val subLayout = RadialMenuGeometry.subMenuLayout(
                parentAngle,
                layout.sweepAngle,
                subItems.size,
                RadialMenuStyle.MIN_SUB_SWEEP_DEG,
            )
            val hit = RadialMenuGeometry.hitSubMenuItem(angle, subLayout, subItems.size)
            if (hit >= 0) {
                selectedIndex = i
                selectedSubIndex = hit
                invalidate()
                return
            }
        }

        val parentIndex = (normalizedAngle / layout.sweepAngle).toInt().coerceIn(0, items.size - 1)
        selectedIndex = parentIndex
        selectedSubIndex = -1
        invalidate()
    }

    private fun dismiss() {
        visibility = GONE
        items = emptyList()
        onDismiss?.invoke()
    }
}
