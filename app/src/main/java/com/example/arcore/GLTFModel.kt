// Ajoutez ces imports en haut du fichier
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import kotlin.math.sqrt
import kotlin.math.abs

class GLTFModel(
    val name: String,
    val vertices: FloatArray,
    val indices: IntArray,
    val normals: FloatArray,
    val colors: FloatArray
) {
    private var vertexBuffer: FloatBuffer? = null
    private var indexBuffer: IntBuffer? = null
    private var colorBuffer: FloatBuffer? = null

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
        vertexBuffer = ByteBuffer
            .allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(vertices)
                position(0)
            }

        indexBuffer = ByteBuffer
            .allocateDirect(indices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asIntBuffer()
            .apply {
                put(indices)
                position(0)
            }

        colorBuffer = ByteBuffer
            .allocateDirect(colors.size * 4)
            .order(ByteOrder.nativeOrder())
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

    fun getVertexBuffer(): FloatBuffer {
        return vertexBuffer ?: throw IllegalStateException("Vertex buffer not initialized")
    }

    fun getIndexBuffer(): IntBuffer {
        return indexBuffer ?: throw IllegalStateException("Index buffer not initialized")
    }

    fun getColorBuffer(): FloatBuffer {
        return colorBuffer ?: throw IllegalStateException("Color buffer not initialized")
    }
}

class GLTFModelManager {
    private val models = mutableListOf<GLTFModel>()

    fun addModel(model: GLTFModel): Int {
        models.add(model)
        return models.size - 1
    }

    fun getModel(index: Int): GLTFModel? {
        return if (index in models.indices) models[index] else null
    }

    fun getAllModels(): List<GLTFModel> = models.toList()

    fun clearAllModels() {
        models.forEach { it.cleanup() }
        models.clear()
    }
}