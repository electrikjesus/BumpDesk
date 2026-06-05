package com.bass.bumpdesk

/**
 * Prevents widget capture/configure feedback loops from exhausting memory.
 * updateAppWidgetSize and RemoteViews updates can ping-pong indefinitely without throttling.
 */
object WidgetCaptureCoordinator {
    private const val MIN_CAPTURE_INTERVAL_MS = 750L
    private const val MIN_INVALIDATE_INTERVAL_MS = 1500L

    private val lastCaptureMs = mutableMapOf<Int, Long>()
    private val lastInvalidateMs = mutableMapOf<Int, Long>()
    private val captureInFlight = mutableSetOf<Int>()
    private val lastConfiguredDp = mutableMapOf<Int, Pair<Int, Int>>()

    fun shouldCapture(appWidgetId: Int, force: Boolean = false): Boolean {
        if (captureInFlight.contains(appWidgetId)) return false
        if (force) return true
        val now = System.currentTimeMillis()
        val last = lastCaptureMs[appWidgetId] ?: 0L
        return now - last >= MIN_CAPTURE_INTERVAL_MS
    }

    fun markCaptureStarted(appWidgetId: Int) {
        captureInFlight.add(appWidgetId)
        lastCaptureMs[appWidgetId] = System.currentTimeMillis()
    }

    fun markCaptureFinished(appWidgetId: Int) {
        captureInFlight.remove(appWidgetId)
    }

    fun shouldInvalidateTexture(appWidgetId: Int): Boolean {
        val now = System.currentTimeMillis()
        val last = lastInvalidateMs[appWidgetId] ?: 0L
        if (now - last < MIN_INVALIDATE_INTERVAL_MS) return false
        lastInvalidateMs[appWidgetId] = now
        return true
    }

    fun shouldUpdateAppWidgetSize(appWidgetId: Int, widthDp: Int, heightDp: Int): Boolean {
        val next = widthDp to heightDp
        val previous = lastConfiguredDp[appWidgetId]
        if (previous == next) return false
        lastConfiguredDp[appWidgetId] = next
        return true
    }

    fun clear(appWidgetId: Int) {
        lastCaptureMs.remove(appWidgetId)
        lastInvalidateMs.remove(appWidgetId)
        captureInFlight.remove(appWidgetId)
        lastConfiguredDp.remove(appWidgetId)
    }
}
