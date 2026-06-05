package com.bass.bumpdesk

import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Pure pile/group mutations shared by LauncherActivity, DialogManager, and BumpRenderer.
 * All list mutations run under SceneState write lock to avoid PhysicsThread CMEs.
 */
object PileOperations {

    fun createPileFromCaptured(
        sceneState: SceneState,
        capturedItems: List<BumpItem>,
        layoutMode: Pile.LayoutMode = Pile.LayoutMode.STACK,
    ): Pile? {
        BumpDeskLog.enter(
            BumpDeskLog.Tag.ICON_GROUP,
            "createPileFromCaptured",
            "count=${capturedItems.size} mode=$layoutMode",
        )
        if (capturedItems.size < 2) {
            BumpDeskLog.w(BumpDeskLog.Tag.ICON_GROUP, "createPileFromCaptured", "skipped | need at least 2 items")
            return null
        }

        return sceneState.withWriteLockResult {
            sceneState.bumpItems.removeAll(capturedItems)
            sceneState.piles.forEach { pile -> pile.items.removeAll(capturedItems) }
            pruneEmptyPilesUnlocked(sceneState)

            val centerX = capturedItems.map { it.transform.position.x }.average().toFloat()
            val centerZ = capturedItems.map { it.transform.position.z }.average().toFloat()
            val pile = Pile(
                items = capturedItems.toMutableList(),
                position = Vector3(centerX, 0.05f, centerZ),
                layoutMode = layoutMode,
            )
            capturedItems.forEach { item ->
                item.transform.surface = BumpItem.Surface.FLOOR
                item.transform.position = item.transform.position.copy(y = 0.05f)
            }
            sceneState.piles.add(pile)

            BumpDeskLog.exit(
                BumpDeskLog.Tag.ICON_GROUP,
                "createPileFromCaptured",
                "pile=${pile.name} items=${pile.items.size} mode=${pile.layoutMode} piles=${sceneState.piles.size}",
            )
            pile
        }
    }

    fun addItemToPile(sceneState: SceneState, item: BumpItem, pile: Pile): Boolean {
        BumpDeskLog.enter(
            BumpDeskLog.Tag.ICON_GROUP,
            "addItemToPile",
            "item=${item.appInfo?.label ?: item.appearance.type} pile=${pile.name}"
        )
        if (pile.isSystem) {
            BumpDeskLog.w(BumpDeskLog.Tag.ICON_GROUP, "addItemToPile", "skipped | system pile")
            return false
        }

        return sceneState.withWriteLockResult {
            if (pile.items.contains(item)) {
                BumpDeskLog.w(BumpDeskLog.Tag.ICON_GROUP, "addItemToPile", "skipped | already in pile")
                return@withWriteLockResult false
            }

            sceneState.bumpItems.remove(item)
            sceneState.piles.forEach { other ->
                if (other !== pile) other.items.remove(item)
            }
            pile.items.add(item)
            item.transform.surface = pile.surface
            item.transform.position = pile.position.copy(y = item.transform.position.y)
            PileFolderIcons.invalidatePreview(pile)

            BumpDeskLog.exit(BumpDeskLog.Tag.ICON_GROUP, "addItemToPile", "pileSize=${pile.items.size}")
            true
        }
    }

    fun breakPile(sceneState: SceneState, pile: Pile, releaseSpacing: Float = 0f): Int {
        BumpDeskLog.enter(BumpDeskLog.Tag.ICON_GROUP, "breakPile", "pile=${pile.name} items=${pile.items.size}")
        if (pile.isSystem) {
            BumpDeskLog.w(BumpDeskLog.Tag.ICON_GROUP, "breakPile", "skipped | system pile")
            return 0
        }

        return sceneState.withWriteLockResult {
            val items = pile.items.toList()
            sceneState.piles.remove(pile)
            pile.isExpanded = false

            val spacing = releaseSpacing.coerceAtLeast(0f)
            val rowOffset = if (spacing > 0f && items.isNotEmpty()) {
                (items.size - 1) * spacing / 2f
            } else {
                0f
            }

            items.forEachIndexed { index, item ->
                if (!sceneState.bumpItems.contains(item)) {
                    sceneState.bumpItems.add(item)
                }
                item.transform.surface = BumpItem.Surface.FLOOR
                item.transform.isPinned = false
                item.transform.velocity = Vector3()
                item.transform.position = if (spacing > 0f) {
                    Vector3(
                        pile.position.x - rowOffset + index * spacing,
                        0.05f,
                        pile.position.z,
                    )
                } else {
                    item.transform.position.copy(
                        y = 0.05f,
                        x = item.transform.position.x + (Math.random().toFloat() - 0.5f) * 2f,
                        z = item.transform.position.z + (Math.random().toFloat() - 0.5f) * 2f,
                    )
                }
            }

            BumpDeskLog.exit(
                BumpDeskLog.Tag.ICON_GROUP,
                "breakPile",
                "released=${items.size} spacing=${"%.2f".format(spacing)}",
            )
            items.size
        }
    }

    /** Pulls items off piles and back onto the desktop list before layout or similar ops. */
    fun releaseItemsToDesktop(sceneState: SceneState, items: List<BumpItem>): List<BumpItem> {
        return sceneState.withWriteLockResult {
            releaseItemsToDesktopUnlocked(sceneState, items)
        }
    }

    internal fun releaseItemsToDesktopUnlocked(sceneState: SceneState, items: List<BumpItem>): List<BumpItem> {
        val targets = items.distinct()
        targets.forEach { item ->
            sceneState.piles.forEach { pile -> pile.items.remove(item) }
            if (!sceneState.bumpItems.contains(item)) {
                sceneState.bumpItems.add(item)
            }
            item.transform.isPinned = false
        }
        sceneState.piles.forEach { PileFolderIcons.invalidatePreview(it) }
        pruneEmptyPilesUnlocked(sceneState)
        return targets
    }

    fun removeItemFromExpandedPile(
        sceneState: SceneState,
        pile: Pile,
        item: BumpItem,
        roomHalfX: Float = 30f,
        roomHalfZ: Float = 30f,
        roomSize: Float = 30f,
    ): Boolean {
        BumpDeskLog.enter(
            BumpDeskLog.Tag.ICON_GROUP,
            "removeItemFromExpandedPile",
            "pile=${pile.name} item=${item.appInfo?.label ?: item.appearance.type}"
        )

        return sceneState.withWriteLockResult {
            if (!pile.isExpanded || !pile.items.contains(item)) {
                BumpDeskLog.d(BumpDeskLog.Tag.ICON_GROUP, "removeItemFromExpandedPile", "skipped | not expanded or not in pile")
                return@withWriteLockResult false
            }

            if (pile.layoutAsExpandedDrawer()) {
                val layout = FolderDrawerStyle.layoutForPile(
                    pile,
                    roomHalfX = roomHalfX,
                    roomHalfZ = roomHalfZ,
                    roomSize = roomSize,
                )
                val stillInside = if (pile.surface == BumpItem.Surface.FLOOR) {
                    FolderDrawerStyle.isInsideContentArea(item, layout, pile.scale)
                } else {
                    FolderDrawerStyle.isInsideWallContentArea(pile, item, layout, pile.scale)
                }
                if (stillInside) {
                    BumpDeskLog.d(BumpDeskLog.Tag.ICON_GROUP, "removeItemFromExpandedPile", "skipped | still inside drawer content")
                    return@withWriteLockResult false
                }
            } else {
                val dx = item.transform.position.x - pile.position.x
                val dz = item.transform.position.z - pile.position.z
                val side = ceil(sqrt(pile.items.size.toDouble())).toInt().coerceAtLeast(1)
                val spacing = 1.2f
                val halfDim = ((side * spacing) / 2f + 0.5f) * pile.scale

                if (kotlin.math.abs(dx) <= halfDim && kotlin.math.abs(dz) <= halfDim) {
                    BumpDeskLog.d(BumpDeskLog.Tag.ICON_GROUP, "removeItemFromExpandedPile", "skipped | still inside bounds")
                    return@withWriteLockResult false
                }
            }

            val appInfo = item.appData?.appInfo
            if (appInfo != null && sceneState.isAppPlacedOnDesktop(appInfo.packageName)) {
                val onFloor = sceneState.bumpItems.any {
                    it.appData?.appInfo?.packageName == appInfo.packageName
                }
                val inUserPile = sceneState.piles.any { other ->
                    !other.isSystem && other.items.any {
                        it.appData?.appInfo?.packageName == appInfo.packageName
                    }
                }
                BumpDeskLog.w(
                    BumpDeskLog.Tag.ICON_GROUP,
                    "removeItemFromExpandedPile",
                    "skipped | already on user desktop pkg=${appInfo.packageName} floor=$onFloor userPile=$inUserPile",
                )
                return@withWriteLockResult false
            }

            pile.items.remove(item)
            if (!sceneState.bumpItems.contains(item)) {
                sceneState.bumpItems.add(item)
            }
            if (item.transform.surface == BumpItem.Surface.FLOOR) {
                item.transform.position = item.transform.position.copy(y = 0.05f)
            }
            pruneEmptyPilesUnlocked(sceneState)

            BumpDeskLog.exit(
                BumpDeskLog.Tag.ICON_GROUP,
                "removeItemFromExpandedPile",
                "pileSize=${pile.items.size} bumpItems=${sceneState.bumpItems.size}"
            )
            true
        }
    }

    fun pruneEmptyPiles(sceneState: SceneState) {
        sceneState.withWriteLock {
            pruneEmptyPilesUnlocked(sceneState)
        }
    }

    private fun pruneEmptyPilesUnlocked(sceneState: SceneState) {
        val singletonPiles = sceneState.piles.filter { it.items.size == 1 && !it.isSystem }
        singletonPiles.forEach { pile ->
            val item = pile.items.first()
            pile.items.clear()
            if (!sceneState.bumpItems.contains(item)) {
                sceneState.bumpItems.add(item)
            }
            item.transform.surface = pile.surface
            item.transform.position = when (pile.surface) {
                BumpItem.Surface.FLOOR -> pile.position.copy(y = 0.05f)
                BumpItem.Surface.BACK_WALL -> pile.position.copy(z = pile.position.z)
                BumpItem.Surface.LEFT_WALL -> pile.position.copy(x = pile.position.x)
                BumpItem.Surface.RIGHT_WALL -> pile.position.copy(x = pile.position.x)
            }
        }
        val removed = sceneState.piles.removeAll { it.items.size < 2 && !it.isSystem }
        if (removed || singletonPiles.isNotEmpty()) {
            BumpDeskLog.d(
                BumpDeskLog.Tag.ICON_GROUP,
                "pruneEmptyPiles",
                "releasedSingletons=${singletonPiles.size} removedEmpty=$removed",
            )
        }
    }
}
