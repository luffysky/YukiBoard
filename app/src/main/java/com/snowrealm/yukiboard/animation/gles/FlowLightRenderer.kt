package com.snowrealm.yukiboard.animation.gles

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * OpenGL ES 2.0 流光特效 Renderer。
 *
 * 採用全螢幕四邊形（Quad）覆蓋鍵盤，
 * 由 [FlowLightShader] 中的 Fragment Shader 以時間函式產生流光效果。
 */
class FlowLightRenderer : GLSurfaceView.Renderer {

    private var programHandle: Int = 0
    private var quadBuffer: FloatBuffer = createQuadBuffer()

    private var uTimeLoc: Int   = -1
    private var uAlphaLoc: Int  = -1
    private var uAspectLoc: Int = -1
    private var uColorLoc: Int  = -1
    private var aPositionLoc: Int = -1
    private var aTexCoordLoc: Int = -1

    // 由外部控制
    var alpha: Float = 0.25f  // 水波紋整體透明度
    private var aspect: Float = 1.0f  // 寬高比
    private var colorR: Float = 0.9f
    private var colorG: Float = 0.95f
    private var colorB: Float = 1.0f

    /** 設定流光顏色（ARGB Int） */
    fun setFlowColor(colorInt: Int) {
        colorR = ((colorInt shr 16) and 0xFF) / 255f
        colorG = ((colorInt shr 8) and 0xFF) / 255f
        colorB = (colorInt and 0xFF) / 255f
    }
    private var startTimeMs: Long = System.currentTimeMillis()

    // -------------------------------------------------------------------------
    // GLSurfaceView.Renderer 實作
    // -------------------------------------------------------------------------

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 0f)   // 透明背景
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        programHandle = buildProgram(FlowLightShader.VERTEX_SHADER, FlowLightShader.FRAGMENT_SHADER)

        uTimeLoc     = GLES20.glGetUniformLocation(programHandle, "uTime")
        uAlphaLoc    = GLES20.glGetUniformLocation(programHandle, "uAlpha")
        uAspectLoc   = GLES20.glGetUniformLocation(programHandle, "uAspect")
        uColorLoc    = GLES20.glGetUniformLocation(programHandle, "uColor")
        aPositionLoc = GLES20.glGetAttribLocation(programHandle,  "aPosition")
        aTexCoordLoc = GLES20.glGetAttribLocation(programHandle,  "aTexCoord")

        startTimeMs = System.currentTimeMillis()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        aspect = if (height > 0) width.toFloat() / height.toFloat() else 1f
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        GLES20.glUseProgram(programHandle)

        val elapsedSec = (System.currentTimeMillis() - startTimeMs) / 1000f
        GLES20.glUniform1f(uTimeLoc, elapsedSec)
        GLES20.glUniform1f(uAlphaLoc, alpha)
        GLES20.glUniform1f(uAspectLoc, aspect)
        GLES20.glUniform3f(uColorLoc, colorR, colorG, colorB)

        // 綁定頂點資料（每頂點：x, y, u, v → 4 floats）
        val stride = 4 * FLOAT_SIZE
        quadBuffer.position(0)
        GLES20.glEnableVertexAttribArray(aPositionLoc)
        GLES20.glVertexAttribPointer(aPositionLoc, 2, GLES20.GL_FLOAT, false, stride, quadBuffer)

        quadBuffer.position(2)
        GLES20.glEnableVertexAttribArray(aTexCoordLoc)
        GLES20.glVertexAttribPointer(aTexCoordLoc, 2, GLES20.GL_FLOAT, false, stride, quadBuffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(aPositionLoc)
        GLES20.glDisableVertexAttribArray(aTexCoordLoc)
    }

    // -------------------------------------------------------------------------
    // Shader 編譯輔助
    // -------------------------------------------------------------------------

    private fun buildProgram(vertSrc: String, fragSrc: String): Int {
        val vert = compileShader(GLES20.GL_VERTEX_SHADER, vertSrc)
        val frag = compileShader(GLES20.GL_FRAGMENT_SHADER, fragSrc)
        return GLES20.glCreateProgram().also { prog ->
            GLES20.glAttachShader(prog, vert)
            GLES20.glAttachShader(prog, frag)
            GLES20.glLinkProgram(prog)
        }
    }

    private fun compileShader(type: Int, source: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
        }
    }

    // -------------------------------------------------------------------------
    // 靜態輔助
    // -------------------------------------------------------------------------

    private fun createQuadBuffer(): FloatBuffer {
        // 全螢幕 Quad（Triangle Strip）：position(x,y) + texcoord(u,v)
        val vertices = floatArrayOf(
            -1f,  1f,  0f, 1f,   // top-left
            -1f, -1f,  0f, 0f,   // bottom-left
             1f,  1f,  1f, 1f,   // top-right
             1f, -1f,  1f, 0f    // bottom-right
        )
        return ByteBuffer.allocateDirect(vertices.size * FLOAT_SIZE)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(vertices); position(0) }
    }

    companion object {
        private const val FLOAT_SIZE = 4
    }
}
