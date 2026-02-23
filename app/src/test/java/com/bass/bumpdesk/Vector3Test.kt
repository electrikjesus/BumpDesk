package com.bass.bumpdesk

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class Vector3Test {

    @Test
    fun testVectorArithmetic() {
        val v1 = Vector3(1f, 2f, 3f)
        val v2 = Vector3(4f, 5f, 6f)
        
        val sum = v1 + v2
        assertEquals(5f, sum.x)
        assertEquals(7f, sum.y)
        assertEquals(9f, sum.z)
        
        val diff = v2 - v1
        assertEquals(3f, diff.x)
        assertEquals(3f, diff.y)
        assertEquals(3f, diff.z)
        
        val scaled = v1 * 2f
        assertEquals(2f, scaled.x)
        assertEquals(4f, scaled.y)
        assertEquals(6f, scaled.z)
    }

    @Test
    fun testLengthAndNormalization() {
        val v = Vector3(3f, 0f, 4f)
        assertEquals(5f, v.length(), 0.001f)
        
        val norm = v.normalize()
        assertEquals(0.6f, norm.x, 0.001f)
        assertEquals(0f, norm.y, 0.001f)
        assertEquals(0.8f, norm.z, 0.001f)
        assertEquals(1f, norm.length(), 0.001f)
        
        val zero = Vector3(0f, 0f, 0f).normalize()
        assertEquals(0f, zero.length(), 0.001f)
    }

    @Test
    fun testDotAndCrossProduct() {
        val v1 = Vector3(1f, 0f, 0f)
        val v2 = Vector3(0f, 1f, 0f)
        
        assertEquals(0f, v1.dot(v2), 0.001f)
        
        val cross = v1.cross(v2)
        assertEquals(0f, cross.x)
        assertEquals(0f, cross.y)
        assertEquals(1f, cross.z)
    }

    @Test
    fun testDistance() {
        val v1 = Vector3(0f, 0f, 0f)
        val v2 = Vector3(1f, 1f, 1f)
        assertEquals(sqrt(3f), v1.distance(v2), 0.001f)
    }
}
