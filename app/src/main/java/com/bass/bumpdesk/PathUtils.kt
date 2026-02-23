package com.bass.bumpdesk

object PathUtils {
    /**
     * Smoothes a path of points using Chaikin's corner-cutting algorithm.
     */
    fun smoothPath(points: List<Vector3>, iterations: Int = 2): List<Vector3> {
        if (points.size < 3) return points
        
        var current = points
        repeat(iterations) {
            val next = mutableListOf<Vector3>()
            next.add(current.first())
            
            for (i in 0 until current.size - 1) {
                val p0 = current[i]
                val p1 = current[i + 1]
                
                val q = Vector3(
                    0.75f * p0.x + 0.25f * p1.x,
                    0.75f * p0.y + 0.25f * p1.y,
                    0.75f * p0.z + 0.25f * p1.z
                )
                val r = Vector3(
                    0.25f * p0.x + 0.75f * p1.x,
                    0.25f * p0.y + 0.75f * p1.y,
                    0.25f * p0.z + 0.75f * p1.z
                )
                next.add(q)
                next.add(r)
            }
            
            next.add(current.last())
            current = next
        }
        return current
    }
}
