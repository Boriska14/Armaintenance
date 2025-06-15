package com.example.arcore

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.core.content.ContextCompat
import com.google.ar.core.ArCoreApk
import com.google.ar.core.ArCoreApk.Availability
import com.google.ar.core.Session
import com.google.ar.core.exceptions.UnavailableException

class MainActivity : ComponentActivity() {
    private var arSession: Session? = null
    private var installRequested by mutableStateOf(false)

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ArCoreAppTheme {
                ARScreen(
                    arSupported = arSupported.value,
                    statusText = arStatusText.value,
                    onArButtonClick = ::handleArButtonClick
                )
            }
        }

        checkArCoreAvailability()
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
                else -> "ARCore not supported" // Removed 'message' reference
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
                    arSession = Session(this).also {
                        arStatusText.value = "AR session started successfully"
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
    }

    override fun onPause() {
        super.onPause()
        arSession?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        arSession?.close()
        arSession = null
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
    primary = Color(0xFF6750A4),  // Initialized color value
    onPrimary = Color(0xFFFFFFFF)  // Initialized color value
)

@Composable
fun ARScreen(
    arSupported: Boolean,
    statusText: String,
    onArButtonClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (arSupported) {
                Button(
                    onClick = onArButtonClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                ) {
                    Text("Start AR Experience")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ARScreenPreview() {
    ArCoreAppTheme {
        ARScreen(
            arSupported = true,
            statusText = "ARCore is ready",
            onArButtonClick = {}
        )
    }
}