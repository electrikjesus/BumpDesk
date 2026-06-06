package com.bass.bumpdesk

/** Tracks Taskbar-style freeform workspace hack state (see farmerbb/Taskbar FreeformHackHelper). */
object FreeformHackHelper {
    @Volatile
    var isFreeformHackActive: Boolean = false

    @Volatile
    var isInFreeformWorkspace: Boolean = false

    fun reset() {
        isFreeformHackActive = false
        isInFreeformWorkspace = false
    }
}
