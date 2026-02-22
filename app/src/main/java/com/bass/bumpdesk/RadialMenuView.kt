package com.bass.bumpdesk

import android.content.Context
import android.graphics.*
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
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 22f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private var centerX = 0f
    private var centerY = 0f
    
    private var innerRadius = 55f
    private var outerRadius = 145f
    private var secondaryOuterRadius = 235f
    
    private var selectedIndex = -1
    private var selectedSubIndex = -1

    private var onItemSelected: ((RadialMenuItem) -> Unit)? = null
    private var onDismiss: (() -> Unit)? = null

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        alpha = 60
        maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.OUTER)
    }

    private var isFirstUpAfterShow = false

    fun setItems(items: List<RadialMenuItem>, x: Float, y: Float, onSelected: (RadialMenuItem) -> Unit, onDismiss: () -> Unit) {
        this.items = items
        
        val count = items.size
        val baseInnerRadius = 55f
        val baseOuterRadius = 145f
        
        if (count > 4) {
            val scaleFactor = 1f + (count - 4) * 0.15f
            this.innerRadius = baseInnerRadius * scaleFactor
            this.outerRadius = baseOuterRadius * scaleFactor
        } else {
            this.innerRadius = baseInnerRadius
            this.outerRadius = baseOuterRadius
        }
        this.secondaryOuterRadius = this.outerRadius + 90f
        
        this.centerX = x.coerceIn(secondaryOuterRadius, resources.displayMetrics.widthPixels - secondaryOuterRadius)
        this.centerY = y.coerceIn(secondaryOuterRadius, resources.displayMetrics.heightPixels - secondaryOuterRadius)
        
        this.onItemSelected = onSelected
        this.onDismiss = onDismiss
        this.selectedIndex = -1
        this.selectedSubIndex = -1
        this.isFirstUpAfterShow = true
        visibility = VISIBLE
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (items.isEmpty()) return

        val count = items.size
        val totalArc = 160f
        val startAngle = 270f - (totalArc / 2f)
        val sweepAngle = totalArc / count

        val rect = RectF(centerX - outerRadius, centerY - outerRadius, centerX + outerRadius, centerY + outerRadius)
        val innerRect = RectF(centerX - innerRadius, centerY - innerRadius, centerX + innerRadius, centerY + innerRadius)

        paint.color = Color.argb(25, 0, 0, 0)
        canvas.drawCircle(centerX, centerY, outerRadius + 10f, paint)

        for (i in items.indices) {
            val angle = startAngle + i * sweepAngle
            drawItem(canvas, items[i], angle, sweepAngle, rect, innerRect, i == selectedIndex, false)
            
            // Draw sub-items if this item is selected and has sub-items
            if (i == selectedIndex && items[i].subItems != null) {
                val subItems = items[i].subItems!!
                val subSweepAngle = sweepAngle / subItems.size
                val subRect = RectF(centerX - secondaryOuterRadius, centerY - secondaryOuterRadius, centerX + secondaryOuterRadius, centerY + secondaryOuterRadius)
                val subInnerRect = RectF(centerX - (outerRadius + 5f), centerY - (outerRadius + 5f), centerX + (outerRadius + 5f), centerY + (outerRadius + 5f))
                
                for (j in subItems.indices) {
                    val subAngle = angle + j * subSweepAngle
                    drawItem(canvas, subItems[j], subAngle, subSweepAngle, subRect, subInnerRect, j == selectedSubIndex, true)
                }
            }
        }
    }

    private fun drawItem(canvas: Canvas, item: RadialMenuItem, angle: Float, sweepAngle: Float, rect: RectF, innerRect: RectF, isSelected: Boolean, isSecondary: Boolean) {
        paint.style = Paint.Style.FILL
        if (isSelected) {
            // Task: Use theme selection color for radial menu highlights
            val selectionColor = ThemeManager.getSelectionColor()
            val colorInt = Color.argb((selectionColor[3] * 255).toInt(), (selectionColor[0] * 255).toInt(), (selectionColor[1] * 255).toInt(), (selectionColor[2] * 255).toInt())
            
            // Use a gradient based on theme color
            paint.shader = LinearGradient(centerX, centerY - rect.width()/2, centerX, centerY - innerRect.width()/2,
                intArrayOf(colorInt, adjustAlpha(colorInt, 0.8f)),
                null, Shader.TileMode.CLAMP)
        } else {
            paint.shader = null
            paint.color = if (isSecondary) Color.argb(235, 40, 40, 40) else Color.argb(235, 20, 20, 20)
        }
        
        val path = Path()
        path.arcTo(rect, angle, sweepAngle)
        path.arcTo(innerRect, angle + sweepAngle, -sweepAngle)
        path.close()
        
        if (isSelected) canvas.drawPath(path, shadowPaint)
        canvas.drawPath(path, paint)

        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = if (isSelected) Color.WHITE else Color.argb(70, 200, 200, 200)
        canvas.drawPath(path, paint)

        val midAngle = angle + sweepAngle / 2f
        val rad = Math.toRadians(midAngle.toDouble())
        val contentRadius = (innerRect.width()/2 + rect.width()/2) / 2f
        val tx = centerX + cos(rad).toFloat() * contentRadius
        val ty = centerY + sin(rad).toFloat() * contentRadius
        
        val fontMetrics = textPaint.fontMetrics
        val textOffset = if (item.iconRes != null) 12f else 0f
        val baseline = ty - (fontMetrics.ascent + fontMetrics.descent) / 2f + textOffset
        
        canvas.drawText(item.label, tx, baseline, textPaint)

        item.iconRes?.let { iconRes ->
            val icon = ContextCompat.getDrawable(context, iconRes)
            icon?.let {
                val iconSize = 28
                val left = (tx - iconSize / 2).toInt()
                val top = (ty - iconSize - 4).toInt()
                it.setBounds(left, top, left + iconSize, top + iconSize)
                it.setTint(Color.WHITE)
                it.draw(canvas)
            }
        }
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = (Color.alpha(color) * factor).toInt()
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        return Color.argb(alpha, red, green, blue)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        val dx = x - centerX
        val dy = y - centerY
        val dist = sqrt(dx * dx + dy * dy)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (dist > secondaryOuterRadius * 1.2f || (dist < innerRadius && !isFirstUpAfterShow)) {
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
                    subItem.action?.invoke()
                    dismiss()
                } else if (selectedIndex != -1) {
                    val item = items[selectedIndex]
                    if (item.subItems == null) {
                        item.action?.invoke()
                        dismiss()
                    }
                } else {
                    if (!isFirstUpAfterShow) dismiss()
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

        if (dist < innerRadius || dist > secondaryOuterRadius) {
            selectedIndex = -1
            selectedSubIndex = -1
            invalidate()
            return
        }

        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()).toDouble()).toFloat()
        if (angle < 0) angle += 360f

        val totalArc = 160f
        val startAngle = 270f - (totalArc / 2f)
        var normalizedAngle = angle - startAngle
        while (normalizedAngle < 0) normalizedAngle += 360f

        if (dist <= outerRadius) {
            if (normalizedAngle < totalArc) {
                val sweepAngle = totalArc / items.size
                selectedIndex = (normalizedAngle / sweepAngle).toInt().coerceIn(0, items.size - 1)
                selectedSubIndex = -1
            } else {
                selectedIndex = -1
            }
        } else {
            if (selectedIndex != -1 && items[selectedIndex].subItems != null) {
                val subItems = items[selectedIndex].subItems!!
                val sweepAngle = totalArc / items.size
                val itemStartAngle = selectedIndex * sweepAngle
                val itemEndAngle = (selectedIndex + 1) * sweepAngle
                
                if (normalizedAngle in itemStartAngle..itemEndAngle) {
                    val subSweepAngle = sweepAngle / subItems.size
                    val relAngle = normalizedAngle - itemStartAngle
                    selectedSubIndex = (relAngle / subSweepAngle).toInt().coerceIn(0, subItems.size - 1)
                } else {
                    if (normalizedAngle < totalArc) {
                        selectedIndex = (normalizedAngle / sweepAngle).toInt().coerceIn(0, items.size - 1)
                        val newSubItems = items[selectedIndex].subItems
                        if (newSubItems != null) {
                            val newSubSweep = sweepAngle / newSubItems.size
                            val newRelAngle = normalizedAngle - (selectedIndex * sweepAngle)
                            selectedSubIndex = (newRelAngle / newSubSweep).toInt().coerceIn(0, newSubItems.size - 1)
                        } else {
                            selectedSubIndex = -1
                        }
                    } else {
                        selectedIndex = -1
                        selectedSubIndex = -1
                    }
                }
            } else {
                if (normalizedAngle < totalArc) {
                    val sweepAngle = totalArc / items.size
                    selectedIndex = (normalizedAngle / sweepAngle).toInt().coerceIn(0, items.size - 1)
                    val subItems = items[selectedIndex].subItems
                    if (subItems != null) {
                        val subSweepAngle = sweepAngle / subItems.size
                        val relAngle = normalizedAngle - (selectedIndex * sweepAngle)
                        selectedSubIndex = (relAngle / subSweepAngle).toInt().coerceIn(0, subItems.size - 1)
                    } else {
                        selectedSubIndex = -1
                    }
                } else {
                    selectedIndex = -1
                    selectedSubIndex = -1
                }
            }
        }
        invalidate()
    }

    private fun dismiss() {
        visibility = GONE
        items = emptyList()
        onDismiss?.invoke()
    }
}
