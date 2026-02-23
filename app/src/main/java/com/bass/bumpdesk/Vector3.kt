package com.bass.bumpdesk

import kotlin.math.sqrt

/**
 * A type-safe 3D vector for physics and rendering.
 * Replaces raw FloatArray(3) to prevent indexing errors and improve readability.
 */
data class Vector3(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f
) {
    operator fun plus(v: Vector3) = Vector3(x + v.x, y + v.y, z + v.z)
    operator fun minus(v: Vector3) = Vector3(x - v.x, y - v.y, z - v.z)
    operator fun times(s: Float) = Vector3(x * s, y * s, z * s)
    operator fun div(s: Float) = if (s != 0f) Vector3(x / s, y / s, z / s) else Vector3()
    
    operator fun unaryMinus() = Vector3(-x, -y, -z)

    fun length() = sqrt(x * x + y * y + z * z)
    fun lengthSq() = x * x + y * y + z * z
    
    fun normalize(): Vector3 {
        val l = length()
        return if (l > 0) this / l else Vector3()
    }

    fun dot(v: Vector3) = x * v.x + y * v.y + z * v.z
    
    fun cross(v: Vector3) = Vector3(
        y * v.z - z * v.y,
        z * v.x - x * v.z,
        x * v.y - y * v.x
    )

    fun distance(v: Vector3) = (this - v).length()
    fun distanceSq(v: Vector3) = (this - v).lengthSq()

    fun toFloatArray() = floatArrayOf(x, y, z)

    operator fun get(index: Int): Float {
        return when (index) {
            0 -> x
            1 -> y
            2 -> z
            else -> throw IndexOutOfBoundsException("Vector3 index must be 0, 1, or 2")
        }
    }

    companion object {
        fun fromArray(a: FloatArray) = if (a.size >= 3) Vector3(a[0], a[1], a[2]) else Vector3()
    }
}
