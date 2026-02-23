package com.bass.bumpdesk

import android.opengl.GLES20
import java.nio.FloatBuffer

class DefaultShader : BaseShader(
    vertexShaderCode = """
        uniform mat4 uVPMatrix;
        uniform mat4 uModelMatrix;
        attribute vec4 vPosition;
        attribute vec3 vNormal;
        attribute vec2 vTexCoord;
        varying vec2 fTexCoord;
        varying vec3 fNormal;
        varying vec3 fPosition;
        void main() {
          vec4 worldPos = uModelMatrix * vPosition;
          gl_Position = uVPMatrix * worldPos;
          fTexCoord = vTexCoord;
          fNormal = vec3(uModelMatrix * vec4(vNormal, 0.0));
          fPosition = vec3(worldPos);
        }
    """.trimIndent(),
    fragmentShaderCode = """
        precision mediump float;
        uniform vec4 vColor;
        uniform sampler2D uTexture;
        uniform bool uUseTexture;
        uniform bool uUseLighting;
        uniform vec3 uLightPos;
        uniform float uAmbient;
        varying vec2 fTexCoord;
        varying vec3 fNormal;
        varying vec3 fPosition;
        void main() {
          vec4 baseColor;
          if (uUseTexture) {
            baseColor = texture2D(uTexture, fTexCoord);
          } else {
            baseColor = vColor;
          }
          
          // Discard fragments with very low alpha to prevent depth-buffer occlusion
          if (baseColor.a < 0.1) {
            discard;
          }
          
          if (uUseLighting) {
            vec3 normal = normalize(fNormal);
            vec3 lightDir = normalize(uLightPos - fPosition);
            float diffuse = max(dot(normal, lightDir), 0.0);
            gl_FragColor = vec4(baseColor.rgb * (uAmbient + diffuse), baseColor.a);
          } else {
            gl_FragColor = baseColor;
          }
        }
    """.trimIndent()
) {
    val posHandle = getAttrib("vPosition")
    val normalHandle = getAttrib("vNormal")
    val texCoordHandle = getAttrib("vTexCoord")
    val vPMatrixHandle = getUniform("uVPMatrix")
    val modelMatrixHandle = getUniform("uModelMatrix")
    val colorHandle = getUniform("vColor")
    val textureHandle = getUniform("uTexture")
    val useTextureHandle = getUniform("uUseTexture")
    val useLightingHandle = getUniform("uUseLighting")
    val lightPosHandle = getUniform("uLightPos")
    val ambientHandle = getUniform("uAmbient")

    fun draw(
        vertexBuffer: FloatBuffer,
        normalBuffer: FloatBuffer?,
        texCoordBuffer: FloatBuffer?,
        vPMatrix: FloatArray,
        modelMatrix: FloatArray,
        color: FloatArray,
        textureId: Int = -1,
        lightPos: FloatArray = floatArrayOf(0f, 10f, 0f),
        ambient: Float = 0.3f,
        useLighting: Boolean = true
    ) {
        use()
        
        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        if (normalBuffer != null && normalHandle != -1) {
            GLES20.glEnableVertexAttribArray(normalHandle)
            GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 0, normalBuffer)
        }

        if (texCoordBuffer != null && texCoordHandle != -1) {
            GLES20.glEnableVertexAttribArray(texCoordHandle)
            GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)
        }

        GLES20.glUniformMatrix4fv(vPMatrixHandle, 1, false, vPMatrix, 0)
        GLES20.glUniformMatrix4fv(modelMatrixHandle, 1, false, modelMatrix, 0)
        GLES20.glUniform4fv(colorHandle, 1, color, 0)
        
        val hasTexture = textureId > 0
        GLES20.glUniform1i(useTextureHandle, if (hasTexture) 1 else 0)
        if (hasTexture) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            GLES20.glUniform1i(textureHandle, 0)
        }

        GLES20.glUniform1i(useLightingHandle, if (useLighting) 1 else 0)
        GLES20.glUniform3fv(lightPosHandle, 1, lightPos, 0)
        GLES20.glUniform1f(ambientHandle, ambient)
    }
}
