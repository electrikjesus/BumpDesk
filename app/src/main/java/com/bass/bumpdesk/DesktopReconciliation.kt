package com.bass.bumpdesk

/**
 * Repairs inconsistent desktop state after load — duplicate list entries, icons off visible bounds.
 */
object DesktopReconciliation {

    data class Result(
        val removedDuplicateListEntries: Int = 0,
        val clampedFloorItems: Int = 0,
        val clampedPiles: Int = 0,
    )

    fun reconcile(sceneState: SceneState, boundX: Float, boundZ: Float): Result {
        var removedDuplicates = 0
        var clampedItems = 0
        var clampedPiles = 0
        val margin = 0.5f
        val limitX = (boundX - margin).coerceAtLeast(0f)
        val limitZ = (boundZ - margin).coerceAtLeast(0f)

        sceneState.withWriteLock {
            val inAnyPile = sceneState.piles.flatMap { it.items }.toSet()
            val duplicateEntries = sceneState.bumpItems.filter { it in inAnyPile }
            if (duplicateEntries.isNotEmpty()) {
                sceneState.bumpItems.removeAll(duplicateEntries.toSet())
                removedDuplicates = duplicateEntries.size
            }

            sceneState.bumpItems.forEach { item ->
                if (clampFloorItem(item, limitX, limitZ)) clampedItems++
            }

            sceneState.piles.filter { !it.isSystem }.forEach { pile ->
                if (pile.surface == BumpItem.Surface.FLOOR) {
                    val beforeX = pile.position.x
                    val beforeZ = pile.position.z
                    pile.position = pile.position.copy(
                        x = pile.position.x.coerceIn(-limitX, limitX),
                        z = pile.position.z.coerceIn(-limitZ, limitZ),
                        y = 0.05f,
                    )
                    if (beforeX != pile.position.x || beforeZ != pile.position.z) clampedPiles++
                }
                pile.items.forEach { item ->
                    if (clampFloorItem(item, limitX, limitZ)) clampedItems++
                }
            }
        }

        return Result(
            removedDuplicateListEntries = removedDuplicates,
            clampedFloorItems = clampedItems,
            clampedPiles = clampedPiles,
        )
    }

    private fun clampFloorItem(item: BumpItem, limitX: Float, limitZ: Float): Boolean {
        if (item.transform.surface != BumpItem.Surface.FLOOR) return false
        val beforeX = item.transform.position.x
        val beforeZ = item.transform.position.z
        item.transform.position = item.transform.position.copy(
            x = item.transform.position.x.coerceIn(-limitX, limitX),
            z = item.transform.position.z.coerceIn(-limitZ, limitZ),
            y = 0.05f,
        )
        return beforeX != item.transform.position.x || beforeZ != item.transform.position.z
    }
}
