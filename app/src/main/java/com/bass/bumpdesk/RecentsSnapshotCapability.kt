package com.bass.bumpdesk

import android.content.Context

/**
 * Detects whether [ActivityManager.getTaskSnapshot] works on this install.
 * Play Store builds typically cannot; system/privileged builds can.
 */
object RecentsSnapshotCapability {
    enum class Status { UNKNOWN, AVAILABLE, UNAVAILABLE }

    @Volatile
    private var status: Status = Status.UNKNOWN

    fun status(): Status = status

    fun isAvailable(): Boolean = status == Status.AVAILABLE

    fun updateFromRecents(apps: List<AppInfo>) {
        val withTaskId = apps.filter { it.taskId != -1 }
        if (withTaskId.isEmpty()) return

        val next = if (withTaskId.any { it.snapshot != null }) {
            Status.AVAILABLE
        } else {
            Status.UNAVAILABLE
        }
        if (status != next) {
            BumpDeskLog.d(
                BumpDeskLog.Tag.RECENTS,
                "snapshotCapability",
                "status=$next taskCount=${withTaskId.size}",
            )
        }
        status = next
    }

    fun settingsLabel(context: Context): String = when (status) {
        Status.AVAILABLE -> context.getString(R.string.recents_snapshots_available)
        Status.UNAVAILABLE -> context.getString(R.string.recents_snapshots_unavailable)
        Status.UNKNOWN -> context.getString(R.string.recents_snapshots_unknown)
    }

    /** Visible to unit tests only. */
    internal fun resetForTests() {
        status = Status.UNKNOWN
    }

    internal fun setStatusForTests(next: Status) {
        status = next
    }
}
