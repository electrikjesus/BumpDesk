package com.bass.bumpdesk

import android.opengl.GLES20
import android.util.Log
import java.nio.FloatBuffer

abstract class BaseShader(
    protected val vertexShaderCode: String,
    protected val fragmentShaderCode: String
) {
    protected var program: Int = 0

    init {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
            
            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(it, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] == 0) {
                Log.e("BaseShader", "Error linking program: " + GLES20.glGetProgramInfoLog(it))
                GLES20.glDeleteProgram(it)
                program = 0
            }
        }
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
            
            val compiled = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
            if (compiled[0] == 0) {
                Log.e("BaseShader", "Could not compile shader $type: " + GLES20.glGetShaderInfoLog(shader))
                GLES20.glDeleteShader(shader)
            }
        }
    }

    fun use() {
        GLES20.glUseProgram(program)
    }

    protected fun getAttrib(name: String): Int = GLES20.glGetAttribLocation(program, name)
    protected fun getUniform(name: String): Int = GLES20.glGetUniformLocation(program, name)

    protected fun setAttribute(location: Int, size: Int, buffer: FloatBuffer) {
        GLES20.glEnableVertexAttribArray(location)
        GLES20.glVertexAttribPointer(location, size, GLES20.GL_FLOAT, false, 0, buffer)
    }
}
