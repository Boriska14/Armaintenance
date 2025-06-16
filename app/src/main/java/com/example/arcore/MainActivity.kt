package com.example.arcore

import android.Manifest
import android.content.pm.PackageManager
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.ar.core.ArCoreApk
import com.google.ar.core.ArCoreApk.Availability
import com.google.ar.core.Camera
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.exceptions.UnavailableException
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import android.opengl.GLES20
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class MainActivity : ComponentActivity() {
    private var arSession: Session? = null
    private var installRequested by mutableStateOf(false)
    private var glSurfaceView: GLSurfaceView? = null
    private var arRenderer: ARRenderer? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startArSession()
        } else {
            Toast.makeText(this, "Camera permission required for AR", Toast.LENGTH_LONG).show()
        }
    }

    private val arSupported = mutableStateOf(false)
    private val arStatusText = mutableStateOf("Checking AR availability...")
    private val arSessionStarted = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ArCoreAppTheme {
                ARScreen(
                    arSupported = arSupported.value,
                    statusText = arStatusText.value,
                    arSessionStarted = arSessionStarted.value,
                    onArButtonClick = ::handleArButtonClick,
                    onCreateGLSurfaceView = ::createGLSurfaceView
                )
            }
        }

        checkArCoreAvailability()
    }

    private fun createGLSurfaceView(): GLSurfaceView {
        return GLSurfaceView(this).apply {
            setEGLContextClientVersion(2)
            arRenderer = ARRenderer(arSession)
            setRenderer(arRenderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            glSurfaceView = this
        }
    }

    private fun handleArButtonClick() {
        when {
            checkCameraPermission() -> startArSession()
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                Toast.makeText(this, "AR requires camera access", Toast.LENGTH_LONG).show()
                requestCameraPermission()
            }
            else -> requestCameraPermission()
        }
    }

    private fun checkArCoreAvailability() {
        ArCoreApk.getInstance().checkAvailabilityAsync(this) { availability ->
            arSupported.value = availability.isSupported
            arStatusText.value = when {
                availability.isSupported -> "ARCore is supported on this device"
                availability.isTransient -> "Checking ARCore availability..."
                else -> "ARCore not supported"
            }
        }
    }

    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        requestPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun startArSession() {
        try {
            when (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                ArCoreApk.InstallStatus.INSTALLED -> {
                    installRequested = false
                    arSession = Session(this).also { session ->
                        arStatusText.value = "AR session started successfully"
                        arSessionStarted.value = true
                        arRenderer?.updateSession(session)
                    }
                }
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                    installRequested = true
                    arStatusText.value = "ARCore installation required"
                }
            }
        } catch (e: UnavailableException) {
            arStatusText.value = "ARCore failed: ${e.message}"
        } catch (e: Exception) {
            arStatusText.value = "Error starting AR: ${e.localizedMessage}"
        }
    }

    override fun onResume() {
        super.onResume()
        arSession?.resume()
        glSurfaceView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        glSurfaceView?.onPause()
        arSession?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        arSession?.close()
        arSession = null
    }
}

class ARRenderer(private var session: Session?) : GLSurfaceView.Renderer {
    private var cubeRenderer: CubeRenderer? = null
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    fun updateSession(newSession: Session) {
        session = newSession
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

        cubeRenderer = CubeRenderer()
        cubeRenderer?.initialize()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / height.toFloat()
        Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1f, 1f, 3f, 7f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        // Configuration de la caméra
        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 3f, 0f, 0f, 0f, 0f, 1f, 0f)

        // Position du modèle (cube flottant)
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, 0f, 0f, -5f)
        Matrix.rotateM(modelMatrix, 0, System.currentTimeMillis() * 0.1f, 1f, 1f, 0f)

        // Calcul de la matrice MVP
        Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvpMatrix, 0)

        // Rendu du cube
        cubeRenderer?.draw(mvpMatrix)
    }
}

class CubeRenderer {
    private var program: Int = 0
    private var positionHandle: Int = 0
    private var colorHandle: Int = 0
    private var mvpMatrixHandle: Int = 0

    private var vertexBuffer: FloatBuffer? = null
    private var colorBuffer: FloatBuffer? = null

    // Vertices d'un cube
    private val cubeVertices = floatArrayOf(
        // Face avant
        -0.5f, -0.5f,  0.5f,
        0.5f, -0.5f,  0.5f,
        0.5f,  0.5f,  0.5f,
        -0.5f,  0.5f,  0.5f,
        // Face arrière
        -0.5f, -0.5f, -0.5f,
        0.5f, -0.5f, -0.5f,
        0.5f,  0.5f, -0.5f,
        -0.5f,  0.5f, -0.5f
    )

    // Couleurs des vertices
    private val cubeColors = floatArrayOf(
        1.0f, 0.0f, 0.0f, 1.0f, // Rouge
        0.0f, 1.0f, 0.0f, 1.0f, // Vert
        0.0f, 0.0f, 1.0f, 1.0f, // Bleu
        1.0f, 1.0f, 0.0f, 1.0f, // Jaune
        1.0f, 0.0f, 1.0f, 1.0f, // Magenta
        0.0f, 1.0f, 1.0f, 1.0f, // Cyan
        1.0f, 1.0f, 1.0f, 1.0f, // Blanc
        0.5f, 0.5f, 0.5f, 1.0f  // Gris
    )

    private val vertexShaderCode = """
        attribute vec4 vPosition;
        attribute vec4 vColor;
        uniform mat4 uMVPMatrix;
        varying vec4 fColor;
        void main() {
            gl_Position = uMVPMatrix * vPosition;
            fColor = vColor;
        }
    """.trimIndent()

    private val fragmentShaderCode = """
        precision mediump float;
        varying vec4 fColor;
        void main() {
            gl_FragColor = fColor;
        }
    """.trimIndent()

    fun initialize() {
        // Initialiser les buffers
        vertexBuffer = ByteBuffer.allocateDirect(cubeVertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(cubeVertices)
                position(0)
            }

        colorBuffer = ByteBuffer.allocateDirect(cubeColors.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(cubeColors)
                position(0)
            }

        // Compiler les shaders
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        // Créer le programme
        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }

        // Obtenir les handles
        positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
        colorHandle = GLES20.glGetAttribLocation(program, "vColor")
        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
    }

    fun draw(mvpMatrix: FloatArray) {
        GLES20.glUseProgram(program)

        // Passer les données des vertices
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        // Passer les données des couleurs
        GLES20.glEnableVertexAttribArray(colorHandle)
        GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 0, colorBuffer)

        // Passer la matrice MVP
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)

        // Dessiner le cube (mode points pour simplifier)
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, cubeVertices.size / 3)

        // Désactiver les arrays
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(colorHandle)
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
    }
}

@Composable
fun ArCoreAppTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color(0xFFFFFFFF)
)

@Composable
fun ARScreen(
    arSupported: Boolean,
    statusText: String,
    arSessionStarted: Boolean,
    onArButtonClick: () -> Unit,
    onCreateGLSurfaceView: () -> GLSurfaceView,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Vue OpenGL pour le rendu 3D
            if (arSessionStarted) {
                AndroidView(
                    factory = { onCreateGLSurfaceView() },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Interface utilisateur par-dessus
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = if (arSessionStarted) Arrangement.Top else Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    modifier = Modifier.padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    )
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                if (arSupported && !arSessionStarted) {
                    Button(
                        onClick = onArButtonClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp)
                    ) {
                        Text("Start AR Experience")
                    }
                }

                if (arSessionStarted) {
                    Spacer(modifier = Modifier.weight(1f))
                    Card(
                        modifier = Modifier.padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        )
                    ) {
                        Text(
                            text = "3D Model is now rendering!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ARScreenPreview() {
    val context = LocalContext.current // Obtenir le contexte dans le scope composable

    ArCoreAppTheme {
        ARScreen(
            arSupported = true,
            statusText = "ARCore is ready",
            arSessionStarted = false,
            onArButtonClick = {},
            onCreateGLSurfaceView = { GLSurfaceView(context) } // Utiliser le contexte capturé
        )
    }
}