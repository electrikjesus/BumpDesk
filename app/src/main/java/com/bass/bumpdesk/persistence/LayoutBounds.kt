package com.bass.bumpdesk.persistence

/** Active desktop extents used to map world units ↔ normalized layout fractions. */
data class LayoutBounds(
    val boundX: Float,
    val boundZ: Float,
    val roomSize: Float,
    val roomHeight: Float,
) {
    fun isSameGeometry(other: LayoutBounds): Boolean =
        boundX == other.boundX &&
            boundZ == other.boundZ &&
            roomSize == other.roomSize &&
            roomHeight == other.roomHeight
}
