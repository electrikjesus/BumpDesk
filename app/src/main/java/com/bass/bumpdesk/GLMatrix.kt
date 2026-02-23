package com.bass.bumpdesk

/**
 * A lightweight replacement for android.opengl.Matrix to allow unit testing of ray-casting logic.
 */
object GLMatrix {
    fun setIdentityM(m: FloatArray, offset: Int) {
        for (i in 0..15) m[offset + i] = 0f
        m[offset + 0] = 1f
        m[offset + 5] = 1f
        m[offset + 10] = 1f
        m[offset + 15] = 1f
    }

    fun multiplyMV(result: FloatArray, resultOffset: Int, lhs: FloatArray, lhsOffset: Int, rhs: FloatArray, rhsOffset: Int) {
        val x = rhs[rhsOffset + 0]
        val y = rhs[rhsOffset + 1]
        val z = rhs[rhsOffset + 2]
        val w = rhs[rhsOffset + 3]
        
        result[resultOffset + 0] = lhs[lhsOffset + 0] * x + lhs[lhsOffset + 4] * y + lhs[lhsOffset + 8] * z + lhs[lhsOffset + 12] * w
        result[resultOffset + 1] = lhs[lhsOffset + 1] * x + lhs[lhsOffset + 5] * y + lhs[lhsOffset + 9] * z + lhs[lhsOffset + 13] * w
        result[resultOffset + 2] = lhs[lhsOffset + 2] * x + lhs[lhsOffset + 6] * y + lhs[lhsOffset + 10] * z + lhs[lhsOffset + 14] * w
        result[resultOffset + 3] = lhs[lhsOffset + 3] * x + lhs[lhsOffset + 7] * y + lhs[lhsOffset + 11] * z + lhs[lhsOffset + 15] * w
    }
    
    // Stub for invertM if needed for tests later, currently using Identity in tests
}
