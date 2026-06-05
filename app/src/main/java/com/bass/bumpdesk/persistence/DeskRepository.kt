package com.bass.bumpdesk.persistence

import android.content.Context
import com.bass.bumpdesk.AppInfo
import com.bass.bumpdesk.BumpItem
import com.bass.bumpdesk.SceneState
import com.bass.bumpdesk.WidgetItem
import com.bass.bumpdesk.Vector3
import com.bass.bumpdesk.Pile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class DeskRepository(context: Context) {
    private val db = DeskDatabase.getDatabase(context)
    private val dao = db.deskItemDao()

    suspend fun saveState(sceneState: SceneState) = withContext(Dispatchers.IO) {
        val snapshot = sceneState.withReadLockResult { buildPersistenceSnapshot(sceneState) }
        dao.replaceAll(snapshot.deskItems, snapshot.deskPiles)
    }

    private data class PersistenceSnapshot(
        val deskItems: List<DeskItem>,
        val deskPiles: List<DeskPile>,
    )

    /** Copy desk data under SceneState read lock so IO persistence cannot race physics/recents. */
    private fun buildPersistenceSnapshot(sceneState: SceneState): PersistenceSnapshot {
        val deskItems = mutableListOf<DeskItem>()
        val deskPiles = mutableListOf<DeskPile>()
        val itemsInPiles = mutableSetOf<BumpItem>()

        sceneState.piles.forEach { pile ->
            if (pile.isSystem) return@forEach

            deskPiles.add(
                DeskPile(
                    name = pile.name,
                    posX = pile.position.x,
                    posY = pile.position.y,
                    posZ = pile.position.z,
                    layoutMode = pile.layoutMode.name,
                    surface = pile.surface.name,
                    scale = pile.scale,
                    isSystem = pile.isSystem,
                    isFannedOut = pile.isFannedOut,
                ),
            )

            pile.items.forEach { item ->
                itemsInPiles.add(item)
                deskItems.add(createDeskItem(item, pile.name))
            }
        }

        sceneState.bumpItems.forEach { item ->
            if (item !in itemsInPiles) {
                deskItems.add(createDeskItem(item, null))
            }
        }

        sceneState.widgetItems.forEach { widget ->
            deskItems.add(
                DeskItem(
                    id = "widget_${widget.appWidgetId}",
                    type = "WIDGET",
                    packageName = null,
                    appWidgetId = widget.appWidgetId,
                    text = null,
                    posX = widget.position.x,
                    posY = widget.position.y,
                    posZ = widget.position.z,
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

    private fun createDeskItem(item: BumpItem, pileId: String?): DeskItem {
        val stableId = when (item.appearance.type) {
            BumpItem.Type.APP_DRAWER -> "app_drawer_icon"
            else -> item.appData?.appInfo?.packageName ?: UUID.randomUUID().toString()
        }
        
        return DeskItem(
            id = stableId,
            type = item.appearance.type.name,
            packageName = item.appData?.appInfo?.packageName,
            appWidgetId = null,
            text = item.textData?.text,
            posX = item.transform.position.x,
            posY = item.transform.position.y,
            posZ = item.transform.position.z,
            sizeX = 1.0f,
            sizeZ = 1.0f,
            surface = item.transform.surface.name,
            isPinned = item.transform.isPinned,
            scale = item.transform.scale,
            pileId = pileId
        )
    }

    suspend fun loadState(allApps: List<AppInfo>): Triple<List<BumpItem>, List<WidgetItem>, List<Pile>> = withContext(Dispatchers.IO) {
        val savedItems = dao.getAllItems()
        val savedPiles = dao.getAllPiles()
        
        val deskItems = mutableListOf<BumpItem>()
        val widgetItems = mutableListOf<WidgetItem>()
        val piles = mutableMapOf<String, Pile>()

        // 1. Reconstruct user piles (skip ephemeral system drawers from legacy saves)
        savedPiles.forEach { saved ->
            if (saved.isSystem) return@forEach
            val pile = Pile(
                name = saved.name,
                position = Vector3(saved.posX, saved.posY, saved.posZ),
                layoutMode = Pile.LayoutMode.valueOf(saved.layoutMode),
                surface = BumpItem.Surface.valueOf(saved.surface),
                scale = saved.scale,
                isSystem = saved.isSystem,
                isFannedOut = saved.isFannedOut
            )
            piles[saved.name] = pile
        }

        // 2. Load Items and assign to Piles
        savedItems.forEach { saved ->
            when (saved.type) {
                "WIDGET" -> {
                    widgetItems.add(WidgetItem(
                        appWidgetId = saved.appWidgetId ?: 0,
                        position = Vector3(saved.posX, saved.posY, saved.posZ),
                        size = Vector3(saved.sizeX, 0f, saved.sizeZ),
                        surface = BumpItem.Surface.valueOf(saved.surface),
                        scale = saved.scale.coerceIn(0.5f, 2.5f),
                    ))
                }
                else -> {
                    val type = BumpItem.Type.valueOf(saved.type)
                    if (saved.pileId == "All Apps" || saved.pileId == "Recents") {
                        return@forEach
                    }
                    val appInfo = if (type == BumpItem.Type.APP || type == BumpItem.Type.RECENT_APP) {
                        allApps.find { it.packageName == saved.packageName }
                    } else null

                    val item = BumpItem(
                        type = type,
                        appInfo = appInfo,
                        text = saved.text ?: "",
                        position = Vector3(saved.posX, saved.posY, saved.posZ),
                        surface = BumpItem.Surface.valueOf(saved.surface),
                        isPinned = saved.isPinned,
                        scale = saved.scale
                    )

                    if (saved.pileId != null && piles.containsKey(saved.pileId)) {
                        piles[saved.pileId]?.items?.add(item)
                    } else {
                        deskItems.add(item)
                    }
                }
            }
        }
        
        Triple(deskItems, widgetItems, piles.values.toList())
    }
}
