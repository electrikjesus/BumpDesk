package com.bass.bumpdesk

/**
 * Pure mutations for multi-item (lasso) selection.
 */
object SelectionOperations {

    const val SCALE_FACTOR = 1.25f
    const val MIN_ITEM_SCALE = 0.2f
    const val MAX_ITEM_SCALE = 2.0f

    fun scaleItems(items: List<BumpItem>, grow: Boolean) {
        val factor = if (grow) SCALE_FACTOR else 1f / SCALE_FACTOR
        items.forEach { item ->
            item.transform.scale = (item.transform.scale * factor)
                .coerceIn(MIN_ITEM_SCALE, MAX_ITEM_SCALE)
        }
    }

    fun setPinned(items: List<BumpItem>, pinned: Boolean) {
        items.forEach { it.transform.isPinned = pinned }
    }
}
