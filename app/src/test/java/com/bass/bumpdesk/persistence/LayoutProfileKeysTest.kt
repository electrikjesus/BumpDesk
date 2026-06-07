package com.bass.bumpdesk.persistence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutProfileKeysTest {
    @Test
    fun copySourceCandidates_includesCrossOrientationInnerForCover() {
        val candidates = LayoutProfileKeys.copySourceCandidates("cover_portrait")
        assertTrue(candidates.contains("inner_portrait"))
        assertTrue(candidates.contains("inner_landscape"))
        assertTrue(candidates.contains(LayoutProfileKeys.LEGACY))
    }

    @Test
    fun copySourceCandidates_excludesTargetKey() {
        assertFalse(LayoutProfileKeys.copySourceCandidates("inner_portrait").contains("inner_portrait"))
    }

    @Test
    fun orientationSuffix_parsesProfileKey() {
        assertEquals("portrait", LayoutProfileKeys.orientationSuffix("cover_portrait"))
        assertEquals("landscape", LayoutProfileKeys.orientationSuffix("inner_landscape"))
    }

    @Test
    fun acceptsLegacyMigration_forInnerOnly() {
        assertTrue(LayoutProfileKeys.acceptsLegacyMigration("inner_landscape"))
        assertFalse(LayoutProfileKeys.acceptsLegacyMigration("cover_portrait"))
    }

    @Test
    fun storageKey_alwaysUsesActiveProfile() {
        assertEquals(
            "cover_portrait",
            LayoutProfileKeys.storageKey("cover_portrait", usesNormalizedCoords = true),
        )
        assertEquals(
            "inner_landscape",
            LayoutProfileKeys.storageKey("inner_landscape", usesNormalizedCoords = false),
        )
    }

    @Test
    fun legacyMigratedKey_isStablePerProfile() {
        assertEquals(
            "layout_legacy_migrated_v1_cover_portrait",
            LayoutProfileKeys.legacyMigratedKey("cover_portrait"),
        )
    }
}
