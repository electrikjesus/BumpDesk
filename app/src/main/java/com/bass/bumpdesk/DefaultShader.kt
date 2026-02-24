package com.bass.bumpdesk

import android.opengl.GLES20
import java.nio.FloatBuffer

class DefaultShader(
    private val environmentCode: String = ""
) : BaseShader(
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
          fNormal = normalize(vec3(uModelMatrix * vec4(vNormal, 0.0)));
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
        uniform float uTime;
        uniform bool uAnimated;
        
        varying vec2 fTexCoord;
        varying vec3 fNormal;
        varying vec3 fPosition;

        // Custom environment code injected here
        ${if (environmentCode.isNotEmpty()) environmentCode else "void applyEnvironment(inout vec4 color, vec3 pos, vec3 normal, float time) {}"}

        void main() {
          vec4 baseColor;
          if (uUseTexture) {
            baseColor = texture2D(uTexture, fTexCoord);
          } else {
            baseColor = vColor;
          }
          
          if (baseColor.a < 0.1) {
            discard;
          }
          
          if (uAnimated) {
            applyEnvironment(baseColor, fPosition, fNormal, uTime);
          }
          
          if (uUseLighting) {
            vec3 normal = normalize(fNormal);
            vec3 lightDir = normalize(uLightPos - fPosition);
            float diffuse = max(dot(normal, lightDir), 0.0);
            float shadow = mix(0.4, 1.0, diffuse);
            
            // Specular highlight for wet look
            float specular = 0.0;
            if (uAnimated) {
                vec3 viewDir = normalize(-fPosition);
                vec3 reflectDir = reflect(-lightDir, normal);
                specular = pow(max(dot(viewDir, reflectDir), 0.0), 32.0) * 0.3;
            }
            
            gl_FragColor = vec4(baseColor.rgb * (uAmbient + diffuse * shadow) + specular, baseColor.a);
          } else {
            gl_FragColor = baseColor;
          }
        }
    """.trimIndent()
) {
    // Attributes
    val vPositionHandle = getAttrib("vPosition")
    val vNormalHandle = getAttrib("vNormal")
    val vTexCoordHandle = getAttrib("vTexCoord")
    
    // Compatibility aliases
    val posHandle = vPositionHandle
    val normalHandle = vNormalHandle
    val texCoordHandle = vTexCoordHandle

    // Uniforms
    val vPMatrixHandle = getUniform("uVPMatrix")
    val modelMatrixHandle = getUniform("uModelMatrix")
    val colorHandle = getUniform("vColor")
    val textureHandle = getUniform("uTexture")
    val useTextureHandle = getUniform("uUseTexture")
    val useLightingHandle = getUniform("uUseLighting")
    val lightPosHandle = getUniform("uLightPos")
    val ambientHandle = getUniform("uAmbient")
    val timeHandle = getUniform("uTime")
    val animatedHandle = getUniform("uAnimated")

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
        useLighting: Boolean = true,
        time: Float = 0f,
        isAnimated: Boolean = false
    ) {
        if (program == 0) return
        use()
        
        GLES20.glEnableVertexAttribArray(vPositionHandle)
        GLES20.glVertexAttribPointer(vPositionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        if (normalBuffer != null && vNormalHandle != -1) {
            GLES20.glEnableVertexAttribArray(vNormalHandle)
            GLES20.glVertexAttribPointer(vNormalHandle, 3, GLES20.GL_FLOAT, false, 0, normalBuffer)
        }

        if (texCoordBuffer != null && vTexCoordHandle != -1) {
            GLES20.glEnableVertexAttribArray(vTexCoordHandle)
            GLES20.glVertexAttribPointer(vTexCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)
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
        
        GLES20.glUniform1f(timeHandle, time)
        GLES20.glUniform1i(animatedHandle, if (isAnimated) 1 else 0)
        
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, 4)
        
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }
}
