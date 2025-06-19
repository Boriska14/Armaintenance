package com.example.arcore

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.ar.core.*
import com.google.ar.core.exceptions.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class MainActivity : ComponentActivity() {
    private var arSession: Session? = null
    private lateinit var gltfRenderer: GLTFRenderer
    private val modelManager = GLTFModelManager()
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            initializeAR()
        } else {
            showToast("Camera permission required for AR", true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkCameraPermission()

        setContent {
            ARViewerApp()
        }
    }

    @Composable
    fun ARViewerApp() {
        ARGLTFViewerTheme {
            var selectedModel by remember { mutableStateOf(-1) }
            var showControls by remember { mutableStateOf(true) }

            Box(modifier = Modifier.fillMaxSize()) {
                ARSurfaceView(
                    modifier = Modifier.fillMaxSize(),
                    modelManager = modelManager,
                    onRendererCreated = { renderer ->
                        this@MainActivity.gltfRenderer = renderer
                        arSession?.let { renderer.setArSession(it) }
                    }
                )

                if (showControls) {
                    ARControls(
                        selectedModel = selectedModel,
                        showControls = showControls,
                        onToggleControls = { showControls = !showControls },
                        onLoadModel = { fileName ->
                            coroutineScope.launch {
                                loadGLTFModel(fileName)?.let { model ->
                                    selectedModel = modelManager.addModel(model)
                                }
                            }
                        },
                        onModelSelect = { selectedModel = it },
                        onTransformModel = { transform ->
                            if (selectedModel >= 0) {
                                modelManager.getModel(selectedModel)?.let { model ->
                                    model.setScale(transform.scale)
                                    model.rotate(0f, transform.rotation, 0f)
                                    model.translate(
                                        transform.translation[0],
                                        transform.translation[1],
                                        transform.translation[2]
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    @Composable
    private fun ARSurfaceView(
        modifier: Modifier,
        modelManager: GLTFModelManager,
        onRendererCreated: (GLTFRenderer) -> Unit
    ) {
        val context = LocalContext.current
        AndroidView(
            factory = { ctx ->
                GLSurfaceView(ctx).apply {
                    setEGLContextClientVersion(2)
                    preserveEGLContextOnPause = true

                    val renderer = GLTFRenderer(context, modelManager)
                    setRenderer(renderer)
                    renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                    onRendererCreated(renderer)
                }
            },
            modifier = modifier
        )
    }

    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                initializeAR()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                showToast("Camera permission is required for AR features", true)
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            else -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun initializeAR() {
        try {
            when (ArCoreApk.getInstance().checkAvailability(this)) {
                ArCoreApk.Availability.SUPPORTED_INSTALLED -> {
                    setupARSession()
                }
                ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED -> {
                    try {
                        ArCoreApk.getInstance().requestInstall(this, true)
                    } catch (e: Exception) {
                        showError("Failed to install ARCore: ${e.message}")
                    }
                }
                ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE -> {
                    showToast("ARCore is not supported on this device", true)
                }
                else -> {
                    showToast("ARCore is not available", true)
                }
            }
        } catch (e: UnavailableException) {
            showError("ARCore error: ${e.message}")
        } catch (e: Exception) {
            showError("Unexpected error: ${e.message}")
        }
    }

    private fun setupARSession() {
        try {
            arSession = Session(this).apply {
                configure(
                    Config(this).apply {
                        planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                        lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                        updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                        focusMode = Config.FocusMode.AUTO
                    }
                )
            }
            gltfRenderer.setArSession(arSession)
            showToast("AR initialized successfully")
        } catch (e: UnavailableException) {
            showError("ARCore unavailable: ${e.message}")
        } catch (e: Exception) {
            showError("Failed to setup AR session: ${e.message}")
        }
    }

    private suspend fun loadGLTFModel(fileName: String): GLTFModel? {
        return try {
            when (fileName.lowercase()) {
                "cube" -> createCubeModel()
                "sphere" -> createSphereModel()
                "pyramid" -> createPyramidModel()
                else -> createDefaultModel(fileName)
            }.also {
                showToast("Model ${it.name} loaded")
            }
        } catch (e: Exception) {
            showError("Failed to load model: ${e.message}")
            null
        }
    }

    private fun createDefaultModel(name: String): GLTFModel {
        val (vertices, indices, colors) = createTriangleGeometry()
        return GLTFModel(
            name = name,
            vertices = vertices,
            indices = indices,
            normals = generateNormals(vertices, indices),
            colors = colors
        ).apply { initializeBuffers() }
    }

    private fun createCubeModel(): GLTFModel {
        val (vertices, indices, colors) = createCubeGeometry()
        return GLTFModel(
            name = "Cube",
            vertices = vertices,
            indices = indices,
            normals = generateNormals(vertices, indices),
            colors = colors
        ).apply { initializeBuffers() }
    }

    private fun createSphereModel(): GLTFModel {
        val (vertices, indices, colors) = createSphereGeometry()
        return GLTFModel(
            name = "Sphere",
            vertices = vertices,
            indices = indices,
            normals = generateNormals(vertices, indices),
            colors = colors
        ).apply { initializeBuffers() }
    }

    private fun createPyramidModel(): GLTFModel {
        val (vertices, indices, colors) = createPyramidGeometry()
        return GLTFModel(
            name = "Pyramid",
            vertices = vertices,
            indices = indices,
            normals = generateNormals(vertices, indices),
            colors = colors
        ).apply { initializeBuffers() }
    }

    private fun createTriangleGeometry(): Triple<FloatArray, IntArray, FloatArray> {
        val vertices = floatArrayOf(
            -0.5f, -0.5f, 0f,
            0.5f, -0.5f, 0f,
            0f, 0.5f, 0f
        )
        val indices = intArrayOf(0, 1, 2)
        val colors = floatArrayOf(
            1f, 0f, 0f, 1f,
            0f, 1f, 0f, 1f,
            0f, 0f, 1f, 1f
        )
        return Triple(vertices, indices, colors)
    }

    private fun createCubeGeometry(): Triple<FloatArray, IntArray, FloatArray> {
        val vertices = floatArrayOf(
            // Front face
            -0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f,
            // Back face
            -0.5f, -0.5f, -0.5f, -0.5f, 0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f, -0.5f, -0.5f
        )
        val indices = intArrayOf(
            0, 1, 2, 2, 3, 0,  // Front
            4, 5, 6, 6, 7, 4    // Back
        )
        val colors = FloatArray(vertices.size / 3 * 4) { index ->
            when (index % 4) {
                0 -> 0.8f  // R
                1 -> 0.2f  // G
                2 -> 0.2f  // B
                else -> 1f // A
            }
        }
        return Triple(vertices, indices, colors)
    }

    private fun createSphereGeometry(): Triple<FloatArray, IntArray, FloatArray> {
        // Simple octahedron approximation
        val vertices = floatArrayOf(
            0f, 0.5f, 0f,    // Top
            0.5f, 0f, 0f,     // Right
            0f, -0.5f, 0f,    // Bottom
            -0.5f, 0f, 0f,    // Left
            0f, 0f, 0.5f,     // Front
            0f, 0f, -0.5f     // Back
        )
        val indices = intArrayOf(
            0, 1, 4, 0, 4, 3, 0, 3, 5, 0, 5, 1,
            2, 4, 1, 2, 3, 4, 2, 5, 3, 2, 1, 5
        )
        val colors = FloatArray(vertices.size / 3 * 4) { index ->
            when (index % 4) {
                0 -> 0.2f + (index % 3) * 0.3f
                1 -> 0.5f - (index % 3) * 0.1f
                2 -> 0.3f + (index % 3) * 0.2f
                else -> 1f
            }
        }
        return Triple(vertices, indices, colors)
    }

    private fun createPyramidGeometry(): Triple<FloatArray, IntArray, FloatArray> {
        val vertices = floatArrayOf(
            0f, 0.5f, 0f,     // Top
            -0.5f, -0.5f, 0.5f, // Base front-left
            0.5f, -0.5f, 0.5f,  // Base front-right
            0.5f, -0.5f, -0.5f, // Base back-right
            -0.5f, -0.5f, -0.5f  // Base back-left
        )
        val indices = intArrayOf(
            0, 1, 2, 0, 2, 3, 0, 3, 4, 0, 4, 1,  // Faces
            1, 4, 3, 1, 3, 2                      // Base
        )
        val colors = floatArrayOf(
            1f, 0f, 0f, 1f,    // Top (red)
            0f, 1f, 0f, 1f,    // Front-left (green)
            0f, 0f, 1f, 1f,    // Front-right (blue)
            1f, 1f, 0f, 1f,    // Back-right (yellow)
            1f, 0f, 1f, 1f     // Back-left (purple)
        )
        return Triple(vertices, indices, colors)
    }

    private fun generateNormals(vertices: FloatArray, indices: IntArray): FloatArray {
        // Simplified normal generation (all facing outward)
        return FloatArray(vertices.size) { i ->
            when (i % 3) {
                2 -> 1f  // Z component
                else -> 0f
            }
        }
    }

    private fun showToast(message: String, long: Boolean = false) {
        runOnUiThread {
            Toast.makeText(this, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
        }
    }

    private fun showError(message: String) {
        Log.e("MainActivity", message)
        showToast("Error: $message", true)
    }

    override fun onResume() {
        super.onResume()
        try {
            arSession?.resume()
        } catch (e: CameraNotAvailableException) {
            showError("Camera not available: ${e.message}")
        }
    }

    override fun onPause() {
        super.onPause()
        arSession?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        arSession?.close()
        modelManager.models.forEach { it.cleanup() }
        modelManager.models.clear()
        gltfRenderer.cleanup()
    }
}

@Composable
private fun ARControls(
    selectedModel: Int,
    showControls: Boolean,
    onToggleControls: () -> Unit,
    onLoadModel: (String) -> Unit,
    onModelSelect: (Int) -> Unit,
    onTransformModel: (ModelTransform) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentScale by remember { mutableStateOf(1f) }
    var currentRotation by remember { mutableStateOf(0f) }
    var currentTranslation by remember { mutableStateOf(floatArrayOf(0f, 0f, 0f)) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top controls - Model Selection
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.7f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "AR Models",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onLoadModel("cube") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cube")
                    }
                    Button(
                        onClick = { onLoadModel("sphere") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Sphere")
                    }
                    Button(
                        onClick = { onLoadModel("pyramid") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Pyramid")
                    }
                }
            }
        }

        // Bottom controls - Model Manipulation
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.7f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Scale Control
                Text(
                    text = "Scale: ${"%.1f".format(currentScale)}",
                    color = Color.White
                )
                Slider(
                    value = currentScale,
                    onValueChange = { newScale ->
                        currentScale = newScale
                        onTransformModel(
                            ModelTransform(
                                scale = newScale,
                                rotation = currentRotation,
                                translation = currentTranslation
                            )
                        )
                    },
                    valueRange = 0.1f..3f,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Rotation Control
                Text(
                    text = "Rotation: ${currentRotation.toInt()}°",
                    color = Color.White
                )
                Slider(
                    value = currentRotation,
                    onValueChange = { newRotation ->
                        currentRotation = newRotation
                        onTransformModel(
                            ModelTransform(
                                scale = currentScale,
                                rotation = newRotation,
                                translation = currentTranslation
                            )
                        )
                    },
                    valueRange = 0f..360f,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Movement Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = {
                            currentTranslation = floatArrayOf(-0.1f, currentTranslation[1], currentTranslation[2])
                            onTransformModel(
                                ModelTransform(
                                    scale = currentScale,
                                    rotation = currentRotation,
                                    translation = currentTranslation
                                )
                            )
                        },
                        modifier = Modifier.size(48.dp)
                    ) { Text("←") }

                    Button(
                        onClick = {
                            currentTranslation = floatArrayOf(0.1f, currentTranslation[1], currentTranslation[2])
                            onTransformModel(
                                ModelTransform(
                                    scale = currentScale,
                                    rotation = currentRotation,
                                    translation = currentTranslation
                                )
                            )
                        },
                        modifier = Modifier.size(48.dp)
                    ) { Text("→") }

                    Button(
                        onClick = {
                            currentTranslation = floatArrayOf(currentTranslation[0], 0.1f, currentTranslation[2])
                            onTransformModel(
                                ModelTransform(
                                    scale = currentScale,
                                    rotation = currentRotation,
                                    translation = currentTranslation
                                )
                            )
                        },
                        modifier = Modifier.size(48.dp)
                    ) { Text("↑") }

                    Button(
                        onClick = {
                            currentTranslation = floatArrayOf(currentTranslation[0], -0.1f, currentTranslation[2])
                            onTransformModel(
                                ModelTransform(
                                    scale = currentScale,
                                    rotation = currentRotation,
                                    translation = currentTranslation
                                )
                            )
                        },
                        modifier = Modifier.size(48.dp)
                    ) { Text("↓") }
                }
            }
        }
    }
}

@Composable
private fun ARGLTFViewerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = darkColorScheme(),
        content = content
    )
}

data class ModelTransform(
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val translation: FloatArray = floatArrayOf(0f, 0f, 0f)
)

class GLTFModelManager {
    val models = mutableListOf<GLTFModel>()

    fun addModel(model: GLTFModel): Int {
        models.add(model)
        return models.size - 1
    }

    fun getModel(index: Int): GLTFModel? {
        return if (index in models.indices) models[index] else null
    }

    fun getAllModels(): List<GLTFModel> = models.toList()
}

data class GLTFModel(
    val name: String,
    val vertices: FloatArray,
    val indices: IntArray,
    val normals: FloatArray,
    val colors: FloatArray
) {
    internal var vertexBuffer: java.nio.FloatBuffer? = null
    internal var indexBuffer: java.nio.IntBuffer? = null
    internal var colorBuffer: java.nio.FloatBuffer? = null

    private var scale = 1f
    private var rotationX = 0f
    private var rotationY = 0f
    private var rotationZ = 0f
    private var translationX = 0f
    private var translationY = 0f
    private var translationZ = 0f
    val transformMatrix = FloatArray(16).apply {
        Matrix.setIdentityM(this, 0)
    }

    fun initializeBuffers() {
        vertexBuffer = java.nio.ByteBuffer
            .allocateDirect(vertices.size * 4)
            .order(java.nio.ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(vertices)
                position(0)
            }

        indexBuffer = java.nio.ByteBuffer
            .allocateDirect(indices.size * 4)
            .order(java.nio.ByteOrder.nativeOrder())
            .asIntBuffer()
            .apply {
                put(indices)
                position(0)
            }

        colorBuffer = java.nio.ByteBuffer
            .allocateDirect(colors.size * 4)
            .order(java.nio.ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(colors)
                position(0)
            }

        updateTransformMatrix()
    }

    fun setScale(newScale: Float) {
        scale = newScale
        updateTransformMatrix()
    }

    fun rotate(x: Float, y: Float, z: Float) {
        rotationX += x
        rotationY += y
        rotationZ += z
        updateTransformMatrix()
    }

    fun translate(x: Float, y: Float, z: Float) {
        translationX += x
        translationY += y
        translationZ += z
        updateTransformMatrix()
    }

    private fun updateTransformMatrix() {
        Matrix.setIdentityM(transformMatrix, 0)
        Matrix.translateM(transformMatrix, 0, translationX, translationY, translationZ)
        Matrix.rotateM(transformMatrix, 0, rotationX, 1f, 0f, 0f)
        Matrix.rotateM(transformMatrix, 0, rotationY, 0f, 1f, 0f)
        Matrix.rotateM(transformMatrix, 0, rotationZ, 0f, 0f, 1f)
        Matrix.scaleM(transformMatrix, 0, scale, scale, scale)
    }

    fun cleanup() {
        // Clean up buffers if needed
    }


}