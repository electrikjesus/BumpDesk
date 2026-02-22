package com.bass.bumpdesk.persistence

import android.content.Context
import com.bass.bumpdesk.AppInfo
import com.bass.bumpdesk.BumpItem
import com.bass.bumpdesk.SceneState
import com.bass.bumpdesk.WidgetItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class DeskRepository(context: Context) {
    private val db = DeskDatabase.getDatabase(context)
    private val dao = db.deskItemDao()

    suspend fun saveState(sceneState: SceneState) = withContext(Dispatchers.IO) {
        val items = mutableListOf<DeskItem>()
        
        // Save BumpItems (Apps, Notes, Frames, etc.)
        sceneState.bumpItems.forEach { item ->
            items.add(DeskItem(
                id = item.appInfo?.packageName ?: UUID.randomUUID().toString(),
                type = item.type.name,
                packageName = item.appInfo?.packageName,
                appWidgetId = null,
                text = item.text,
                posX = item.position[0],
                posY = item.position[1],
                posZ = item.position[2],
                sizeX = 1.0f,
                sizeZ = 1.0f,
                surface = item.surface.name,
                isPinned = item.isPinned,
                scale = item.scale,
                pileId = sceneState.getPileOf(item)?.name
            ))
        }

        // Save Widgets
        sceneState.widgetItems.forEach { widget ->
            items.add(DeskItem(
                id = "widget_${widget.appWidgetId}",
                type = "WIDGET",
                packageName = null,
                appWidgetId = widget.appWidgetId,
                text = null,
                posX = widget.position[0],
                posY = widget.position[1],
                posZ = widget.position[2],
                sizeX = widget.size[0],
                sizeZ = widget.size[1],
                surface = widget.surface.name,
                isPinned = true,
                scale = 1.0f
            ))
        }

        dao.deleteAll()
        dao.insertAll(items)
    }

    suspend fun loadState(allApps: List<AppInfo>): Pair<List<BumpItem>, List<WidgetItem>> = withContext(Dispatchers.IO) {
        val savedItems = dao.getAll()
        val bumpItems = mutableListOf<BumpItem>()
        val widgetItems = mutableListOf<WidgetItem>()

        savedItems.forEach { saved ->
            when (saved.type) {
                "WIDGET" -> {
                    widgetItems.add(WidgetItem(
                        appWidgetId = saved.appWidgetId ?: 0,
                        position = floatArrayOf(saved.posX, saved.posY, saved.posZ),
                        size = floatArrayOf(saved.sizeX, saved.sizeZ),
                        surface = BumpItem.Surface.valueOf(saved.surface)
                    ))
                }
                else -> {
                    val type = BumpItem.Type.valueOf(saved.type)
                    val appInfo = if (type == BumpItem.Type.APP || type == BumpItem.Type.RECENT_APP) {
                        allApps.find { it.packageName == saved.packageName }
                    } else null

                    bumpItems.add(BumpItem(
                        type = type,
                        appInfo = appInfo,
                        text = saved.text ?: "",
                        position = floatArrayOf(saved.posX, saved.posY, saved.posZ),
                        surface = BumpItem.Surface.valueOf(saved.surface),
                        isPinned = saved.isPinned,
                        scale = saved.scale
                    ))
                }
            }
        }
        Pair(bumpItems, widgetItems)
    }
}
