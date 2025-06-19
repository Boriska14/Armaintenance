package com.example.arcore

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.google.ar.core.Frame
import com.google.ar.core.Session
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class GLTFRenderer(
    private val context: Context,
    private val modelManager: GLTFModelManager
) : GLSurfaceView.Renderer {

    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelViewProjectionMatrix = FloatArray(16)

    private var arSession: Session? = null
    private var frame: Frame? = null
    private var shaderProgram = 0

    fun setArSession(session: Session?) {
        arSession = session
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

        val vertexShaderCode = """
            uniform mat4 u_MVPMatrix;
            attribute vec4 a_Position;
            attribute vec4 a_Color;
            varying vec4 v_Color;
            void main() {
                v_Color = a_Color;
                gl_Position = u_MVPMatrix * a_Position;
            }
        """.trimIndent()

        val fragmentShaderCode = """
            precision mediump float;
            varying vec4 v_Color;
            void main() {
                gl_FragColor = v_Color;
            }
        """.trimIndent()

        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        shaderProgram = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val aspectRatio = width.toFloat() / height
        Matrix.perspectiveM(projectionMatrix, 0, 45f, aspectRatio, 0.1f, 100f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        arSession?.let { session ->
            frame = session.update()
            frame?.let { frame ->
                // Correction ici: utiliser frame.camera.getViewMatrix()
                frame.camera.getViewMatrix(viewMatrix, 0)

                modelManager.getAllModels().forEach { model ->
                    drawModel(model)
                }
            }
        }
    }

    private fun drawModel(model: GLTFModel) {
        Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
        Matrix.multiplyMM(modelViewProjectionMatrix, 0, modelViewProjectionMatrix, 0, model.transformMatrix, 0)

        GLES20.glUseProgram(shaderProgram)

        val positionHandle = GLES20.glGetAttribLocation(shaderProgram, "a_Position")
        val colorHandle = GLES20.glGetAttribLocation(shaderProgram, "a_Color")
        val mvpMatrixHandle = GLES20.glGetUniformLocation(shaderProgram, "u_MVPMatrix")

        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, modelViewProjectionMatrix, 0)

        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(
            positionHandle, 3, GLES20.GL_FLOAT, false, 0, model.vertexBuffer
        )

        GLES20.glEnableVertexAttribArray(colorHandle)
        GLES20.glVertexAttribPointer(
            colorHandle, 4, GLES20.GL_FLOAT, false, 0, model.colorBuffer
        )

        GLES20.glDrawElements(
            GLES20.GL_TRIANGLES,
            model.indices.size,
            GLES20.GL_UNSIGNED_INT,
            model.indexBuffer
        )

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(colorHandle)
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
    }

    fun cleanup() {
        GLES20.glDeleteProgram(shaderProgram)
    }
}