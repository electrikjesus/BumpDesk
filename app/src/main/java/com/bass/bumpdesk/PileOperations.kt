package com.bass.bumpdesk

import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Pure pile/group mutations shared by LauncherActivity, DialogManager, and BumpRenderer.
 */
object PileOperations {

    fun createPileFromCaptured(sceneState: SceneState, capturedItems: List<BumpItem>): Pile? {
        BumpDeskLog.enter(BumpDeskLog.Tag.ICON_GROUP, "createPileFromCaptured", "count=${capturedItems.size}")
        if (capturedItems.size < 2) {
            BumpDeskLog.w(BumpDeskLog.Tag.ICON_GROUP, "createPileFromCaptured", "skipped | need at least 2 items")
            return null
        }

        sceneState.bumpItems.removeAll(capturedItems)
        sceneState.piles.forEach { pile -> pile.items.removeAll(capturedItems) }
        pruneEmptyPiles(sceneState)

        val centerX = capturedItems.map { it.transform.position.x }.average().toFloat()
        val centerZ = capturedItems.map { it.transform.position.z }.average().toFloat()
        val pile = Pile(
            items = capturedItems.toMutableList(),
            position = Vector3(centerX, 0.05f, centerZ)
        )
        capturedItems.forEach { item ->
            item.transform.surface = BumpItem.Surface.FLOOR
            item.transform.position = item.transform.position.copy(y = 0.05f)
        }
        sceneState.piles.add(pile)

        BumpDeskLog.exit(
            BumpDeskLog.Tag.ICON_GROUP,
            "createPileFromCaptured",
            "pile=${pile.name} items=${pile.items.size} piles=${sceneState.piles.size}"
        )
        return pile
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
        if (pile.items.contains(item)) {
            BumpDeskLog.w(BumpDeskLog.Tag.ICON_GROUP, "addItemToPile", "skipped | already in pile")
            return false
        }

        sceneState.bumpItems.remove(item)
        sceneState.piles.forEach { other ->
            if (other !== pile) other.items.remove(item)
        }
        pile.items.add(item)
        item.transform.surface = pile.surface
        item.transform.position = pile.position.copy(y = item.transform.position.y)

        BumpDeskLog.exit(BumpDeskLog.Tag.ICON_GROUP, "addItemToPile", "pileSize=${pile.items.size}")
        return true
    }

    fun breakPile(sceneState: SceneState, pile: Pile): Int {
        BumpDeskLog.enter(BumpDeskLog.Tag.ICON_GROUP, "breakPile", "pile=${pile.name} items=${pile.items.size}")
        if (pile.isSystem) {
            BumpDeskLog.w(BumpDeskLog.Tag.ICON_GROUP, "breakPile", "skipped | system pile")
            return 0
        }

        val items = pile.items.toList()
        sceneState.piles.remove(pile)
        pile.isExpanded = false

        items.forEach { item ->
            if (!sceneState.bumpItems.contains(item)) {
                sceneState.bumpItems.add(item)
            }
            item.transform.surface = BumpItem.Surface.FLOOR
            item.transform.position = item.transform.position.copy(
                y = 0.05f,
                x = item.transform.position.x + (Math.random().toFloat() - 0.5f) * 2f,
                z = item.transform.position.z + (Math.random().toFloat() - 0.5f) * 2f
            )
        }

        BumpDeskLog.exit(BumpDeskLog.Tag.ICON_GROUP, "breakPile", "released=${items.size}")
        return items.size
    }

    fun removeItemFromExpandedPile(sceneState: SceneState, pile: Pile, item: BumpItem): Boolean {
        BumpDeskLog.enter(
            BumpDeskLog.Tag.ICON_GROUP,
            "removeItemFromExpandedPile",
            "pile=${pile.name} item=${item.appInfo?.label ?: item.appearance.type}"
        )
        if (!pile.isExpanded || !pile.items.contains(item)) {
            BumpDeskLog.d(BumpDeskLog.Tag.ICON_GROUP, "removeItemFromExpandedPile", "skipped | not expanded or not in pile")
            return false
        }

        val dx = item.transform.position.x - pile.position.x
        val dz = item.transform.position.z - pile.position.z
        val side = ceil(sqrt(pile.items.size.toDouble())).toInt().coerceAtLeast(1)
        val spacing = 1.2f
        val halfDim = ((side * spacing) / 2f + 0.5f) * pile.scale

        if (kotlin.math.abs(dx) <= halfDim && kotlin.math.abs(dz) <= halfDim) {
            BumpDeskLog.d(BumpDeskLog.Tag.ICON_GROUP, "removeItemFromExpandedPile", "skipped | still inside bounds")
            return false
        }

        val appInfo = item.appData?.appInfo
        if (appInfo != null && sceneState.isAlreadyOnDesktop(appInfo)) {
            BumpDeskLog.w(BumpDeskLog.Tag.ICON_GROUP, "removeItemFromExpandedPile", "skipped | duplicate app on desktop")
            return false
        }

        pile.items.remove(item)
        if (!sceneState.bumpItems.contains(item)) {
            sceneState.bumpItems.add(item)
        }
        if (item.transform.surface == BumpItem.Surface.FLOOR) {
            item.transform.position = item.transform.position.copy(y = 0.05f)
        }
        pruneEmptyPiles(sceneState)

        BumpDeskLog.exit(
            BumpDeskLog.Tag.ICON_GROUP,
            "removeItemFromExpandedPile",
            "pileSize=${pile.items.size} bumpItems=${sceneState.bumpItems.size}"
        )
        return true
    }

    fun pruneEmptyPiles(sceneState: SceneState) {
        val removed = sceneState.piles.removeAll { it.items.size < 2 && !it.isSystem }
        if (removed) {
            BumpDeskLog.d(BumpDeskLog.Tag.ICON_GROUP, "pruneEmptyPiles", "removed empty non-system piles")
        }
    }
}
