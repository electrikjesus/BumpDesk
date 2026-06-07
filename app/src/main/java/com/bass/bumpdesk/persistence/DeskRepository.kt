package com.bass.bumpdesk.persistence

import android.content.Context
import com.bass.bumpdesk.AppInfo
import com.bass.bumpdesk.BumpItem
import com.bass.bumpdesk.SceneState
import com.bass.bumpdesk.WidgetItem
import com.bass.bumpdesk.StickyNoteStyle
import com.bass.bumpdesk.Vector3
import com.bass.bumpdesk.Pile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class DeskRepository(context: Context) {
    private val appContext = context.applicationContext
    private val db = DeskDatabase.getDatabase(appContext)
    private val dao = db.deskItemDao()
    private val prefs = appContext.getSharedPreferences("bump_prefs", Context.MODE_PRIVATE)

    suspend fun saveState(
        sceneState: SceneState,
        layoutProfileKey: String,
        bounds: LayoutBounds,
    ) = withContext(Dispatchers.IO) {
        val normalize = true
        if (!LayoutCoordSpace.usesNormalized(prefs)) {
            LayoutCoordSpace.markNormalized(prefs)
        }
        val snapshot = sceneState.withReadLockResult {
            buildPersistenceSnapshot(sceneState, layoutProfileKey, bounds, normalize = normalize)
        }
        dao.replaceProfile(layoutProfileKey, snapshot.deskItems, snapshot.deskPiles)
    }

    suspend fun copyLayoutProfile(sourceKey: String, targetKey: String) = withContext(Dispatchers.IO) {
        if (sourceKey == targetKey) return@withContext
        val items = dao.getItemsForProfile(sourceKey).map { it.copy(layoutProfileKey = targetKey) }
        val piles = dao.getPilesForProfile(sourceKey).map { it.copy(layoutProfileKey = targetKey) }
        dao.replaceProfile(targetKey, items, piles)
    }

    suspend fun clearLayoutProfile(layoutProfileKey: String) = withContext(Dispatchers.IO) {
        dao.replaceProfile(layoutProfileKey, emptyList(), emptyList())
    }

    private data class PersistenceSnapshot(
        val deskItems: List<DeskItem>,
        val deskPiles: List<DeskPile>,
    )

    private fun buildPersistenceSnapshot(
        sceneState: SceneState,
        storageKey: String,
        bounds: LayoutBounds,
        normalize: Boolean,
    ): PersistenceSnapshot {
        val deskItems = mutableListOf<DeskItem>()
        val deskPiles = mutableListOf<DeskPile>()
        val itemsInPiles = mutableSetOf<BumpItem>()

        sceneState.piles.forEach { pile ->
            if (pile.isSystem) return@forEach

            val pilePos = encodePosition(pile.position, pile.surface, bounds, normalize)
            deskPiles.add(
                DeskPile(
                    name = pile.name,
                    layoutProfileKey = storageKey,
                    posX = pilePos.x,
                    posY = pilePos.y,
                    posZ = pilePos.z,
                    layoutMode = pile.layoutMode.name,
                    surface = pile.surface.name,
                    scale = pile.scale,
                    isSystem = pile.isSystem,
                    isFannedOut = pile.isFannedOut,
                ),
            )

            pile.items.forEach { item ->
                itemsInPiles.add(item)
                deskItems.add(createDeskItem(item, pile.name, storageKey, bounds, normalize))
            }
        }

        sceneState.bumpItems.forEach { item ->
            if (item !in itemsInPiles) {
                deskItems.add(createDeskItem(item, null, storageKey, bounds, normalize))
            }
        }

        sceneState.widgetItems.forEach { widget ->
            val pos = encodePosition(widget.position, widget.surface, bounds, normalize)
            deskItems.add(
                DeskItem(
                    id = "widget_${widget.appWidgetId}",
                    layoutProfileKey = storageKey,
                    type = "WIDGET",
                    packageName = null,
                    appWidgetId = widget.appWidgetId,
                    text = widget.aspectRatio.toString(),
                    posX = pos.x,
                    posY = pos.y,
                    posZ = pos.z,
                    sizeX = widget.size.x,
                    sizeZ = widget.size.z,
                    surface = widget.surface.name,
                    isPinned = true,
                    scale = widget.scale,
                ),
            )
        }

        return PersistenceSnapshot(deskItems, deskPiles)
    }

    private fun createDeskItem(
        item: BumpItem,
        pileId: String?,
        storageKey: String,
        bounds: LayoutBounds,
        normalize: Boolean,
    ): DeskItem {
        val stableId = when (item.appearance.type) {
            BumpItem.Type.APP_DRAWER -> "app_drawer_icon"
            else -> item.appData?.appInfo?.packageName ?: UUID.randomUUID().toString()
        }
        val pos = encodePosition(item.transform.position, item.transform.surface, bounds, normalize)
        val (sizeX, sizeZ) = if (item.appearance.type == BumpItem.Type.STICKY_NOTE) {
            if (normalize) {
                NormalizedLayout.normalizeHalfSize(
                    item.transform.shapeHalfX,
                    item.transform.shapeHalfZ,
                    item.transform.surface,
                    bounds,
                )
            } else {
                item.transform.shapeHalfX to item.transform.shapeHalfZ
            }
        } else {
            1.0f to 1.0f
        }

        return DeskItem(
            id = stableId,
            layoutProfileKey = storageKey,
            type = item.appearance.type.name,
            packageName = item.appData?.appInfo?.packageName,
            appWidgetId = null,
            text = item.textData?.text,
            posX = pos.x,
            posY = pos.y,
            posZ = pos.z,
            sizeX = sizeX,
            sizeZ = sizeZ,
            surface = item.transform.surface.name,
            isPinned = item.transform.isPinned,
            scale = item.transform.scale,
            pileId = pileId,
        )
    }

    private fun encodePosition(
        position: Vector3,
        surface: BumpItem.Surface,
        bounds: LayoutBounds,
        normalize: Boolean,
    ): Vector3 {
        if (!normalize) return position.copy()
        val encoded = NormalizedLayout.normalizePosition(position, surface, bounds)
        return Vector3(encoded.nx, encoded.ny, encoded.nz)
    }

    private fun decodePosition(
        stored: Vector3,
        surface: BumpItem.Surface,
        bounds: LayoutBounds,
        normalized: Boolean,
    ): Vector3 {
        if (!normalized || looksLikeWorldCoord(stored, surface)) return stored.copy()
        return NormalizedLayout.denormalizePosition(
            NormalizedLayout.NormalizedVector(stored.x, stored.y, stored.z),
            surface,
            bounds,
        )
    }

    private fun looksLikeWorldCoord(stored: Vector3, surface: BumpItem.Surface): Boolean {
        return when (surface) {
            BumpItem.Surface.FLOOR ->
                kotlin.math.abs(stored.x) > 1.2f || kotlin.math.abs(stored.z) > 1.2f
            BumpItem.Surface.BACK_WALL ->
                kotlin.math.abs(stored.x) > 1.2f || stored.y > 2f
            BumpItem.Surface.LEFT_WALL,
            BumpItem.Surface.RIGHT_WALL,
            -> kotlin.math.abs(stored.z) > 1.2f || stored.y > 2f
        }
    }

    suspend fun loadState(
        allApps: List<AppInfo>,
        layoutProfileKey: String,
        bounds: LayoutBounds,
    ): Triple<List<BumpItem>, List<WidgetItem>, List<Pile>> = withContext(Dispatchers.IO) {
        val usesNormalized = LayoutCoordSpace.usesNormalized(prefs)
        val (savedItems, savedPiles) = loadOrMigrateProfile(layoutProfileKey, usesNormalized)
        reconstructScene(allApps, savedItems, savedPiles, bounds, usesNormalized)
    }

    /**
     * Loads a saved profile. One-time legacy import for inner profiles only; cover/tablet stay empty until saved.
     */
    private suspend fun loadOrMigrateProfile(
        layoutProfileKey: String,
        usesNormalized: Boolean,
    ): Pair<List<DeskItem>, List<DeskPile>> {
        val items = dao.getItemsForProfile(layoutProfileKey)
        val piles = dao.getPilesForProfile(layoutProfileKey)
        if (items.isNotEmpty() || piles.isNotEmpty()) {
            return items to piles
        }

        if (usesNormalized || !LayoutProfileKeys.acceptsLegacyMigration(layoutProfileKey) ||
            layoutProfileKey == LayoutProfileKeys.LEGACY ||
            prefs.getBoolean(LayoutProfileKeys.legacyMigratedKey(layoutProfileKey), false) ||
            (!dao.hasItemsForProfile(LayoutProfileKeys.LEGACY) &&
                !dao.hasPilesForProfile(LayoutProfileKeys.LEGACY))
        ) {
            return emptyList<DeskItem>() to emptyList()
        }

        val migratedItems = dao.getItemsForProfile(LayoutProfileKeys.LEGACY)
            .map { it.copy(layoutProfileKey = layoutProfileKey) }
        val migratedPiles = dao.getPilesForProfile(LayoutProfileKeys.LEGACY)
            .map { it.copy(layoutProfileKey = layoutProfileKey) }
        if (migratedItems.isNotEmpty() || migratedPiles.isNotEmpty()) {
            dao.replaceProfile(layoutProfileKey, migratedItems, migratedPiles)
        }
        prefs.edit()
            .putBoolean(LayoutProfileKeys.legacyMigratedKey(layoutProfileKey), true)
            .apply()
        return migratedItems to migratedPiles
    }

    suspend fun resolveCopySourceKey(targetKey: String): String? = withContext(Dispatchers.IO) {
        LayoutProfileKeys.copySourceCandidates(targetKey)
            .firstOrNull { dao.hasItemsForProfile(it) || dao.hasPilesForProfile(it) }
    }

    private fun decodeWidgetSize(
        storedX: Float,
        storedZ: Float,
        surface: BumpItem.Surface,
        bounds: LayoutBounds,
        normalized: Boolean,
    ): Pair<Float, Float> {
        if (!normalized) return storedX to storedZ
        // Brief Phase 3 stored widget sizes normalized to bounds; recover to world units once.
        if (storedX <= 1f && storedZ <= 1f) {
            return NormalizedLayout.denormalizeHalfSize(storedX, storedZ, surface, bounds)
        }
        return storedX to storedZ
    }

    private fun reconstructScene(
        allApps: List<AppInfo>,
        savedItems: List<DeskItem>,
        savedPiles: List<DeskPile>,
        bounds: LayoutBounds,
        normalized: Boolean,
    ): Triple<List<BumpItem>, List<WidgetItem>, List<Pile>> {
        val deskItems = mutableListOf<BumpItem>()
        val widgetItems = mutableListOf<WidgetItem>()
        val piles = mutableMapOf<String, Pile>()

        savedPiles.forEach { saved ->
            if (saved.isSystem) return@forEach
            val surface = BumpItem.Surface.valueOf(saved.surface)
            val position = decodePosition(
                Vector3(saved.posX, saved.posY, saved.posZ),
                surface,
                bounds,
                normalized,
            )
            val pile = Pile(
                name = saved.name,
                position = position,
                layoutMode = Pile.LayoutMode.valueOf(saved.layoutMode),
                surface = surface,
                scale = saved.scale,
                isSystem = saved.isSystem,
                isFannedOut = saved.isFannedOut,
            )
            piles[saved.name] = pile
        }

        savedItems.forEach { saved ->
            when (saved.type) {
                "WIDGET" -> {
                    val surface = BumpItem.Surface.valueOf(saved.surface)
                    val position = decodePosition(
                        Vector3(saved.posX, saved.posY, saved.posZ),
                        surface,
                        bounds,
                        normalized,
                    )
                    val (sizeX, sizeZ) = decodeWidgetSize(saved.sizeX, saved.sizeZ, surface, bounds, normalized)
                    val aspect = saved.text?.toFloatOrNull()?.takeIf { it > 0.01f }
                    widgetItems.add(
                        WidgetItem(
                            appWidgetId = saved.appWidgetId ?: 0,
                            position = position,
                            size = Vector3(sizeX, 0f, sizeZ),
                            surface = surface,
                            scale = saved.scale.coerceIn(0.5f, 2.5f),
                            aspectRatio = aspect ?: 1f,
                        ),
                    )
                }
                else -> {
                    val type = BumpItem.Type.valueOf(saved.type)
                    if (saved.pileId == "All Apps" || saved.pileId == "Recents") {
                        return@forEach
                    }
                    val surface = BumpItem.Surface.valueOf(saved.surface)
                    val appInfo = if (type == BumpItem.Type.APP || type == BumpItem.Type.RECENT_APP) {
                        allApps.find { it.packageName == saved.packageName }
                    } else {
                        null
                    }

                    val item = BumpItem(
                        type = type,
                        appInfo = appInfo,
                        text = saved.text ?: "",
                        position = decodePosition(
                            Vector3(saved.posX, saved.posY, saved.posZ),
                            surface,
                            bounds,
                            normalized,
                        ),
                        surface = surface,
                        isPinned = saved.isPinned,
                        scale = saved.scale,
                    )
                    if (type == BumpItem.Type.STICKY_NOTE) {
                        val (hx, hz) = if (normalized) {
                            NormalizedLayout.denormalizeHalfSize(saved.sizeX, saved.sizeZ, surface, bounds)
                        } else {
                            saved.sizeX to saved.sizeZ
                        }
                        item.transform.shapeHalfX = StickyNoteStyle.clampShapeHalf(hx)
                        item.transform.shapeHalfZ = StickyNoteStyle.clampShapeHalf(hz)
                    }

                    if (saved.pileId != null && piles.containsKey(saved.pileId)) {
                        piles[saved.pileId]?.items?.add(item)
                    } else {
                        deskItems.add(item)
                    }
                }
            }
        }

        return Triple(deskItems, widgetItems, piles.values.toList())
    }
}
