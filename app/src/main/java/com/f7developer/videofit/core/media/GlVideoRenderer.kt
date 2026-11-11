package com.f7developer.videofit.core.media

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.util.Log
import android.view.Surface
import com.f7developer.videofit.core.model.HdrTransfer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Decoder surface -> GPU (scale + rotation + optional HDR tone map) -> encoder
 * input surface. This is what keeps the pipeline on the hardware blocks: no
 * pixel ever reaches the CPU, so RAM stays flat and the SoC can use its video
 * engine and encoder at the same time.
 */
class GlVideoRenderer {

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    private var program = 0
    private var textureId = 0
    private var positionBuffer: FloatBuffer? = null
    private var texCoordBuffer: FloatBuffer? = null

    private var uTexture = 0
    private var uTexMatrix = 0
    private var uMvp = 0
    private var uRotation = 0
    private var uToneMap = 0
    private var uColorMatrix = 0

    private var surfaceTexture: SurfaceTexture? = null
    private var inputSurface: Surface? = null

    private val frameLock = Object()
    private var frameAvailable = false
    private var rotationDegrees = 0
    private var outputWidth = 1280
    private var outputHeight = 720
    private val texMatrix = FloatArray(16)
    private var toneMapMode = 0
    private var initialized = false
    private var frames = 0L

    /** The Surface the decoder must render into. */
    val decoderSurface: Surface
        get() = inputSurface ?: error("Renderer not initialised")

    fun init(
        encoderInputSurface: Surface,
        outputWidth: Int,
        outputHeight: Int,
        sourceWidth: Int,
        sourceHeight: Int,
        rotation: Int,
        hdr: HdrTransfer,
    ) {
        this.outputWidth = outputWidth
        this.outputHeight = outputHeight
        this.rotationDegrees = ((rotation % 360) + 360) % 360

        setupEgl(encoderInputSurface)
        setupProgram(hdr)
        setupQuad()
        setupTexture(sourceWidth, sourceHeight, hdr)
        initialized = true
        Log.d(TAG, "GL ready ${outputWidth}x$outputHeight rotation=$rotation hdr=$hdr")
    }

    private fun setupEgl(encoderInputSurface: Surface) {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(eglDisplay != EGL14.EGL_NO_DISPLAY) { "No EGL display" }
        val version = IntArray(2)
        check(EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) { "eglInitialize failed" }
        Log.d(TAG, "EGL ${version[0]}.${version[1]}")

        val configAttrs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        check(
            EGL14.eglChooseConfig(eglDisplay, configAttrs, 0, configs, 0, 1, numConfigs, 0) &&
                numConfigs[0] > 0,
        ) { "No EGL config" }

        val contextAttrs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        eglContext = EGL14.eglCreateContext(
            eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, contextAttrs, 0,
        )
        check(eglContext != EGL14.EGL_NO_CONTEXT) { "eglCreateContext failed" }

        val surfaceAttrs = intArrayOf(EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreateWindowSurface(
            eglDisplay, configs[0], encoderInputSurface, surfaceAttrs, 0,
        )
        check(eglSurface != EGL14.EGL_NO_SURFACE) { "eglCreateWindowSurface failed" }

        check(EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            "eglMakeCurrent failed"
        }
    }

    private fun setupProgram(hdr: HdrTransfer) {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShader(hdr))
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)
        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        check(status[0] == GLES20.GL_TRUE) {
            "Program link failed: " + GLES20.glGetProgramInfoLog(program)
        }
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)

        uTexture = GLES20.glGetUniformLocation(program, "uTexture")
        uTexMatrix = GLES20.glGetUniformLocation(program, "uTexMatrix")
        uMvp = GLES20.glGetUniformLocation(program, "uMvp")
        uRotation = GLES20.glGetUniformLocation(program, "uRotation")
        uToneMap = GLES20.glGetUniformLocation(program, "uToneMap")
        uColorMatrix = GLES20.glGetUniformLocation(program, "uColorMatrix")
        toneMapMode = when (hdr) {
            HdrTransfer.PQ -> 1
            HdrTransfer.HLG -> 2
            HdrTransfer.NONE -> 0
        }
    }

    private fun setupQuad() {
        val quad = floatArrayOf(
            -1f, -1f,
            1f, -1f,
            -1f, 1f,
            1f, 1f,
        )
        val tex = floatArrayOf(
            0f, 0f,
            1f, 0f,
            0f, 1f,
            1f, 1f,
        )
        positionBuffer = ByteBuffer.allocateDirect(quad.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(quad); position(0)
            }
        texCoordBuffer = ByteBuffer.allocateDirect(tex.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(tex); position(0)
            }
    }

    private fun setupTexture(sourceWidth: Int, sourceHeight: Int, hdr: HdrTransfer) {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR,
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR,
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE,
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE,
        )

        val st = SurfaceTexture(textureId)
        st.setDefaultBufferSize(sourceWidth.coerceAtLeast(2), sourceHeight.coerceAtLeast(2))
        st.setOnFrameAvailableListener {
            synchronized(frameLock) {
                frameAvailable = true
                frameLock.notifyAll()
            }
        }
        surfaceTexture = st
        inputSurface = Surface(st)
        Log.d(TAG, "texture ${sourceWidth}x$sourceHeight hdr=$hdr")
    }

    /** Waits for the decode of one frame; false on timeout. */
    fun awaitFrame(timeoutMs: Long): Boolean {
        synchronized(frameLock) {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (!frameAvailable) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) return false
                try {
                    frameLock.wait(remaining)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return false
                }
            }
            frameAvailable = false
            return true
        }
    }

    fun drawFrame(presentationTimeNs: Long) {
        val st = surfaceTexture ?: return
        st.updateTexImage()
        st.getTransformMatrix(texMatrix)

        GLES20.glViewport(0, 0, outputWidth, outputHeight)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        GLES20.glUseProgram(program)

        val pos = positionBuffer!!
        pos.position(0)
        val posLoc = GLES20.glGetAttribLocation(program, "aPosition")
        GLES20.glEnableVertexAttribArray(posLoc)
        GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 0, pos)

        val tex = texCoordBuffer!!
        tex.position(0)
        val texLoc = GLES20.glGetAttribLocation(program, "aTexCoord")
        GLES20.glEnableVertexAttribArray(texLoc)
        GLES20.glVertexAttribPointer(texLoc, 2, GLES20.GL_FLOAT, false, 0, tex)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniform1i(uTexture, 0)

        GLES20.glUniformMatrix4fv(uTexMatrix, 1, false, texMatrix, 0)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, IDENTITY, 0)
        GLES20.glUniform1f(uRotation, rotationDegrees.toFloat())
        GLES20.glUniform1i(uToneMap, toneMapMode)
        GLES20.glUniformMatrix3fv(uColorMatrix, 1, false, COLOR_MATRIX, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(posLoc)
        GLES20.glDisableVertexAttribArray(texLoc)

        EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, presentationTimeNs)
        if (!EGL14.eglSwapBuffers(eglDisplay, eglSurface)) {
            Log.w(TAG, "eglSwapBuffers failed: ${EGL14.eglGetError()}")
        }
        frames++
    }

    fun release() {
        if (!initialized) return
        initialized = false
        runCatching { surfaceTexture?.release() }
        runCatching { inputSurface?.release() }
        surfaceTexture = null
        inputSurface = null
        if (program != 0) runCatching { GLES20.glDeleteProgram(program) }
        if (textureId != 0) runCatching { GLES20.glDeleteTextures(1, intArrayOf(textureId), 0) }
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            runCatching {
                EGL14.eglMakeCurrent(
                    eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT,
                )
                EGL14.eglDestroySurface(eglDisplay, eglSurface)
                EGL14.eglDestroyContext(eglDisplay, eglContext)
                EGL14.eglReleaseThread()
                EGL14.eglTerminate(eglDisplay)
            }
        }
        eglDisplay = EGL14.EGL_NO_DISPLAY
        eglContext = EGL14.EGL_NO_CONTEXT
        eglSurface = EGL14.EGL_NO_SURFACE
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        check(compiled[0] != 0) { "Shader compile failed: " + GLES20.glGetShaderInfoLog(shader) }
        return shader
    }

    @Suppress("unused")
    private fun fragmentShader(hdr: HdrTransfer): String {
        return """
            #extension GL_OES_EGL_image_external : require
            precision highp float;
            varying vec2 vTexCoord;
            uniform samplerExternalOES uTexture;
            uniform float uRotation;
            uniform int uToneMap;
            uniform mat3 uColorMatrix;

            vec3 pqToLinear(vec3 v) {
                const float m1 = 0.1593017578125;
                const float m2 = 78.84375;
                const float c1 = 0.8359375;
                const float c2 = 18.8515625;
                const float c3 = 18.6875;
                vec3 e = pow(max(v, vec3(0.0)), vec3(1.0 / m2));
                vec3 num = max(e - c1, vec3(0.0));
                vec3 den = max(c2 - c3 * e, vec3(1e-5));
                return pow(num / den, vec3(1.0 / m1));
            }

            vec3 hlgToLinear(vec3 v) {
                const float a = 0.17883277;
                const float b = 1.0 - 4.0 * a;
                const float c = 0.5 - a * log(4.0 * a);
                vec3 lo = (v * v) / 3.0;
                vec3 hi = (exp((v - c) / a) + b) / 12.0;
                return mix(lo, hi, step(vec3(0.5), v));
            }

            float hableCurve(float x) {
                const float A = 0.15;
                const float B = 0.50;
                const float C = 0.10;
                const float D = 0.20;
                const float E = 0.02;
                const float F = 0.30;
                return ((x * (A * x + C * B) + D * E) / (x * (A * x + B) + D * F)) - E / F;
            }

            float hable(float x) {
                float white = hableCurve(11.2);
                return hableCurve(max(x, 0.0)) / white;
            }

            void main() {
                vec2 uv = vTexCoord;
                // Bake display rotation so the MP4 is upright for every player.
                float rad = radians(uRotation);
                float c = cos(rad);
                float s = sin(rad);
                uv = mat2(c, -s, s, c) * (uv - 0.5) + 0.5;

                vec4 texel = texture2D(uTexture, uv);
                vec3 rgb = texel.rgb;

                if (uToneMap == 1 || uToneMap == 2) {
                    vec3 linear = (uToneMap == 1) ? pqToLinear(rgb) : hlgToLinear(rgb);
                    // PQ is absolute (10,000 nits); HLG is relative to 100 nits.
                    linear *= (uToneMap == 1) ? 0.1 : 1.0;
                    linear = uColorMatrix * linear;
                    vec3 mapped = vec3(hable(linear.r), hable(linear.g), hable(linear.b));
                    rgb = pow(mapped, vec3(1.0 / 2.2));
                }

                gl_FragColor = vec4(clamp(rgb, 0.0, 1.0), 1.0);
            }
        """.trimIndent()
    }

    companion object {
        private const val TAG = "GlVideoRenderer"
        private const val EGL_RECORDABLE_ANDROID = 0x3142

        private val IDENTITY: FloatArray = FloatArray(16).also {
            android.opengl.Matrix.setIdentityM(it, 0)
        }

        /**
         * BT.2020 -> BT.709 primaries, column-major for glUniformMatrix3fv.
         * Applied in linear light, before the tone curve.
         */
        private val COLOR_MATRIX = floatArrayOf(
            1.6605f, -0.5876f, -0.0728f,
            -0.1246f, 1.1329f, -0.0083f,
            -0.0182f, -0.1006f, 1.1187f,
        )

        private const val VERTEX_SHADER = """
            uniform mat4 uMvp;
            uniform mat4 uTexMatrix;
            attribute vec4 aPosition;
            attribute vec4 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = uMvp * aPosition;
                vTexCoord = (uTexMatrix * aTexCoord).xy;
            }
        """
    }
}

/** EGL extensions that are only exposed through the NDK header on some devices. */
private object EGLExt {
    fun eglPresentationTimeANDROID(display: EGLDisplay, surface: EGLSurface, time: Long) {
        try {
            egPresentationTime(display, surface, time)
        } catch (t: Throwable) {
            Log.w("EGLExt", "presentation time unavailable", t)
        }
    }

    private fun egPresentationTime(display: EGLDisplay, surface: EGLSurface, time: Long) {
        val method = Class.forName("android.opengl.EGLExt")
            .getMethod(
                "eglPresentationTimeANDROID",
                EGLDisplay::class.java,
                EGLSurface::class.java,
                Long::class.javaPrimitiveType,
            )
        method.invoke(null, display, surface, time)
    }
}
