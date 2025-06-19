package com.example.arcore

import android.content.Context
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer

class GLTFLoader(private val context: Context) {

    fun createCubeModel(): GLTFModel {
        val vertices = floatArrayOf(
            // Face avant
            -0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f,
            // Face arrière
            -0.5f, -0.5f, -0.5f, 0.5f, -0.5f, -0.5f, 0.5f, 0.5f, -0.5f, -0.5f, 0.5f, -0.5f
        )

        val indices = intArrayOf(
            0, 1, 2, 2, 3, 0, // Face avant
            4, 5, 6, 6, 7, 4, // Face arrière
            0, 3, 7, 7, 4, 0, // Face gauche
            1, 2, 6, 6, 5, 1, // Face droite
            3, 2, 6, 6, 7, 3, // Face supérieure
            0, 1, 5, 5, 4, 0  // Face inférieure
        )

        val colors = floatArrayOf(
            // Face avant (rouge)
            1f, 0f, 0f, 1f, 1f, 0f, 0f, 1f, 1f, 0f, 0f, 1f, 1f, 0f, 0f, 1f,
            // Face arrière (vert)
            0f, 1f, 0f, 1f, 0f, 1f, 0f, 1f, 0f, 1f, 0f, 1f, 0f, 1f, 0f, 1f
        )

        return GLTFModel(
            name = "Cube",
            vertices = vertices,
            indices = indices,
            normals = vertices.copyOf(), // Simplification
            colors = colors
        ).apply {
            initializeBuffers()
        }
    }

    companion object {
        fun createFloatBuffer(array: FloatArray): FloatBuffer {
            return ByteBuffer
                .allocateDirect(array.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply {
                    put(array)
                    position(0)
                }
        }
    }
}