package com.bass.bumpdesk

import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.opengles.GL10
import org.mockito.Mockito.mock

/**
 * Integration test that verifies the OpenGL rendering pipeline in a headless environment.
 * Runs on a device/emulator using a pbuffer surface.
 */
class HeadlessGLTest {

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    @Test
    fun testRendererInitializationAndFrame() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val width = 512
        val height = 512

        setupHeadlessGL(width, height)

        try {
            val renderer = BumpRenderer(context)
            
            // We need to pass valid non-null mocks for the legacy GL interface if required,
            // though ES 2.0+ usually ignores the GL10 parameter.
            val mockGl = mock(GL10::class.java)
            val mockConfig = mock(javax.microedition.khronos.egl.EGLConfig::class.java)
            
            // 1. Verify Surface Created
            renderer.onSurfaceCreated(mockGl, mockConfig)
            
            // 2. Verify Surface Changed (Viewport setup)
            renderer.onSurfaceChanged(mockGl, width, height)
            
            // 3. Verify Draw Frame
            renderer.onDrawFrame(mockGl)
            
            // 4. Capture Output and Verify
            val bitmap = captureFrame(width, height)
            assertNotNull("Captured frame should not be null", bitmap)
            
            // Check if clear color is applied (Renderer sets clear color to 0.02, 0.02, 0.02)
            val pixel = bitmap.getPixel(width / 2, height / 2)
            val r = android.graphics.Color.red(pixel)
            val g = android.graphics.Color.green(pixel)
            val b = android.graphics.Color.blue(pixel)
            
            // 0.02 * 255 is approximately 5. We allow some range for different GPU implementations.
            assertTrue("Clear color Red should be near 5, got $r", r in 0..15)
            assertTrue("Clear color Green should be near 5, got $g", g in 0..15)
            assertTrue("Clear color Blue should be near 5, got $b", b in 0..15)

        } finally {
            destroyHeadlessGL()
        }
    }

    private fun setupHeadlessGL(width: Int, height: Int) {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        EGL14.eglInitialize(eglDisplay, version, 0, version, 1)

        val configAttribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0)
        val config = configs[0] ?: throw RuntimeException("No EGL config found")

        val contextAttribs = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE
        )
        eglContext = EGL14.eglCreateContext(eglDisplay, config, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)

        val pbufferAttribs = intArrayOf(
            EGL14.EGL_WIDTH, width,
            EGL14.EGL_HEIGHT, height,
            EGL14.EGL_NONE
        )
        eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, config, pbufferAttribs, 0)

        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
    }

    private fun captureFrame(width: Int, height: Int): Bitmap {
        val buffer = ByteBuffer.allocateDirect(width * height * 4)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)
        
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        buffer.rewind()
        bitmap.copyPixelsFromBuffer(buffer)
        
        // GL origin is bottom-left, Bitmap origin is top-left
        val matrix = android.graphics.Matrix()
        matrix.postScale(1f, -1f, width / 2f, height / 2f)
        return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
    }

    private fun destroyHeadlessGL() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
            if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
            EGL14.eglTerminate(eglDisplay)
        }
    }
}
