package com.bass.bumpdesk

import android.opengl.GLES20

class LassoShader : BaseShader(
    vertexShaderCode = """
        uniform mat4 uVPMatrix;
        attribute vec4 vPosition;
        void main() {
          gl_Position = uVPMatrix * vPosition;
          gl_PointSize = 12.0;
        }
    """.trimIndent(),
    fragmentShaderCode = """
        precision mediump float;
        uniform vec4 vColor;
        void main() {
          gl_FragColor = vColor;
        }
    """.trimIndent()
) {
    val posHandle = getAttrib("vPosition")
    val colorHandle = getUniform("vColor")
    val vPMatrixHandle = getUniform("uVPMatrix")
}
