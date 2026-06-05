package com.bass.bumpdesk

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentsSnapshotCapabilityTest {

    @After
    fun tearDown() {
        RecentsSnapshotCapability.resetForTests()
    }

    @Test
    fun staysUnknownWhenNoRealTasks() {
        RecentsSnapshotCapability.updateFromRecents(
            listOf(
                AppInfo(
                    packageName = "com.example",
                    label = "Example",
                    icon = null,
                    taskId = -1,
                ),
            ),
        )
        assertEquals(RecentsSnapshotCapability.Status.UNKNOWN, RecentsSnapshotCapability.status())
    }

    @Test
    fun marksAvailableWhenAnySnapshotPresent() {
        RecentsSnapshotCapability.setStatusForTests(RecentsSnapshotCapability.Status.AVAILABLE)
        assertTrue(RecentsSnapshotCapability.isAvailable())
    }

    @Test
    fun marksUnavailableWhenTasksExistButNoSnapshots() {
        RecentsSnapshotCapability.updateFromRecents(
            listOf(
                AppInfo(
                    packageName = "com.one",
                    label = "One",
                    icon = null,
                    taskId = 10,
                ),
            ),
        )
        assertEquals(RecentsSnapshotCapability.Status.UNAVAILABLE, RecentsSnapshotCapability.status())
        assertFalse(RecentsSnapshotCapability.isAvailable())
    }
}
