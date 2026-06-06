package com.bass.bumpdesk

/** Pure geometry for radial menu sectors (no Android dependencies — JVM-testable). */
object RadialMenuGeometry {
    data class SubMenuLayout(val subSweep: Float, val subArc: Float, val subStart: Float)

    fun subMenuLayout(parentAngle: Float, parentSweep: Float, subItemCount: Int, minSubSweepDeg: Float): SubMenuLayout {
        val count = subItemCount.coerceAtLeast(1)
        val natural = parentSweep / count
        val subSweep = when {
            natural >= minSubSweepDeg -> natural
            minSubSweepDeg * count <= parentSweep -> minSubSweepDeg
            else -> natural
        }
        val subArc = subSweep * count
        val subStart = parentAngle + (parentSweep - subArc) / 2f
        return SubMenuLayout(subSweep, subArc, subStart)
    }

    fun hitSubMenuItem(touchAngleDeg: Float, layout: SubMenuLayout, subItemCount: Int): Int {
        for (j in 0 until subItemCount) {
            val segStart = layout.subStart + j * layout.subSweep
            val segEnd = segStart + layout.subSweep
            if (isAngleInSweep(touchAngleDeg, segStart, segEnd)) return j
        }
        return -1
    }

    fun totalArcForItemCount(count: Int, minSweepDeg: Float, baseArcDeg: Float, maxArcDeg: Float): Float =
        (count * minSweepDeg).coerceIn(baseArcDeg, maxArcDeg)

    data class RadialRadii(val inner: Float, val outer: Float, val secondary: Float)

    /** Scale radii down when the menu would not fit on screen (foldables, dense displays). */
    fun fitRadiiToScreen(
        inner: Float,
        outer: Float,
        secondary: Float,
        maxWidth: Float,
        maxHeight: Float,
        insetFraction: Float = 0.92f,
    ): RadialRadii {
        val maxRadius = minOf(maxWidth, maxHeight) / 2f * insetFraction
        if (secondary <= maxRadius) return RadialRadii(inner, outer, secondary)
        val scale = maxRadius / secondary
        return RadialRadii(inner * scale, outer * scale, secondary * scale)
    }

    /** Avoid Kotlin coerceIn crash when margin * 2 exceeds the available span. */
    fun clampMenuCenter(value: Float, margin: Float, span: Float): Float {
        val min = margin
        val max = span - margin
        if (min >= max) return span / 2f
        return value.coerceIn(min, max)
    }

    private fun isAngleInSweep(angle: Float, start: Float, end: Float): Boolean {
        val a = normalizeAngle(angle)
        val s = normalizeAngle(start)
        val e = normalizeAngle(end)
        return if (s <= e) a in s..e else a >= s || a <= e
    }

    private fun normalizeAngle(angle: Float): Float {
        var a = angle % 360f
        if (a < 0f) a += 360f
        return a
    }
}
