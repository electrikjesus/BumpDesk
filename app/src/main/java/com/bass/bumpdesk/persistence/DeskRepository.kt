package com.bass.bumpdesk.persistence

import android.content.Context
import com.bass.bumpdesk.AppInfo
import com.bass.bumpdesk.BumpItem
import com.bass.bumpdesk.SceneState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DeskRepository(context: Context) {
    private val db = DeskDatabase.getDatabase(context)
    private val dao = db.deskItemDao()

    suspend fun saveState(sceneState: SceneState) = withContext(Dispatchers.IO) {
        val items = sceneState.bumpItems.map { item ->
            DeskItem(
                packageName = item.appInfo?.packageName ?: "",
                posX = item.position[0],
                posY = item.position[1],
                posZ = item.position[2],
                surface = item.surface.name,
                isPinned = item.isPinned,
                scale = item.scale
            )
        }
        dao.deleteAll()
        dao.insertAll(items)
    }

    suspend fun loadState(allApps: List<AppInfo>): List<BumpItem> = withContext(Dispatchers.IO) {
        val savedItems = dao.getAll()
        savedItems.mapNotNull { saved ->
            val appInfo = allApps.find { it.packageName == saved.packageName } ?: return@mapNotNull null
            BumpItem(
                appInfo = appInfo,
                position = floatArrayOf(saved.posX, saved.posY, saved.posZ),
                surface = BumpItem.Surface.valueOf(saved.surface),
                isPinned = saved.isPinned,
                scale = saved.scale
            )
        }
    }
}
