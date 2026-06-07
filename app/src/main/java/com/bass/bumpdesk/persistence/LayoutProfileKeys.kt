package com.bass.bumpdesk.persistence

/** Keys for per-posture desk layouts (Phase 2). */
object LayoutProfileKeys {
    /** Pre–Phase 2 saves migrated from the single global desk table. */
    const val LEGACY = "legacy"

    /** @deprecated One-release shared layout; kept for reading old DB rows only. */
    const val SHARED = "shared"

    private const val PREFS_LEGACY_MIGRATED = "layout_legacy_migrated_v1"

    fun orientationSuffix(layoutProfileKey: String): String =
        layoutProfileKey.substringAfterLast('_', layoutProfileKey)

    /** Explicit copy sources (Settings); not used for automatic load seeding. */
    fun copySourceCandidates(targetKey: String): List<String> {
        val orientation = orientationSuffix(targetKey)
        val posture = targetKey.substringBefore('_', targetKey)
        val candidates = mutableListOf<String>()
        if (orientation.isNotEmpty()) {
            candidates.add("inner_$orientation")
            if (posture == "cover") {
                candidates.add("inner_landscape")
                candidates.add("inner_portrait")
            }
        }
        candidates.add(LEGACY)
        return candidates.filter { it != targetKey }.distinct()
    }

    fun legacyMigratedKey(layoutProfileKey: String): String =
        "${PREFS_LEGACY_MIGRATED}_$layoutProfileKey"

    /** One-time legacy import applies to inner layouts only; cover stays independent. */
    fun acceptsLegacyMigration(layoutProfileKey: String): Boolean =
        layoutProfileKey.startsWith("inner_") || layoutProfileKey == LEGACY

    /** Room row key — always per posture; normalized coords are an encoding, not a shared row. */
    fun storageKey(activeProfileKey: String, @Suppress("UNUSED_PARAMETER") usesNormalizedCoords: Boolean): String =
        activeProfileKey
}
