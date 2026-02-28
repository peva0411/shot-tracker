package com.shottracker.ui.session

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.shottracker.camera.CameraPreview
import com.shottracker.camera.detector.BallDetection
import com.shottracker.camera.rememberCameraPermissionState

@Composable
fun SessionScreen(
    onEndSession: (Long) -> Unit,
    viewModel: SessionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val detection by viewModel.detection.collectAsState()
    val permissionState = rememberCameraPermissionState()
    val context = LocalContext.current
    var debugOverlayEnabled by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Runtime RECORD_AUDIO permission request
    var audioPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> audioPermissionGranted = granted }

    // Show snackbar on capture feedback
    LaunchedEffect(uiState.lastCaptureFeedback) {
        uiState.lastCaptureFeedback?.let { msg ->
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
            viewModel.clearCaptureFeedback()
        }
    }
    
    LaunchedEffect(Unit) {
        if (!permissionState.hasPermission) {
            permissionState.requestPermission()
        }
    }
    
    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        if (permissionState.hasPermission) {
            // Camera preview (full screen) with capture use cases bound
            CameraPreview(
                captureController = viewModel.captureController,
                onFrameAnalyzed = { imageProxy ->
                    viewModel.onFrameAnalyzed(imageProxy)
                }
            )
            
            // Ball bounding-box debug overlay
            if (debugOverlayEnabled) {
                BallDetectionOverlay(detection = detection)
            }

            // Top-right toolbar: debug toggle + capture buttons
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Capture frame button
                IconButton(onClick = { viewModel.captureFrame() }) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Capture frame",
                        tint = Color.White
                    )
                }
                // Record video toggle
                IconButton(onClick = {
                    if (!audioPermissionGranted) {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        viewModel.toggleRecording()
                    }
                }) {
                    Icon(
                        imageVector = if (uiState.isRecording) Icons.Default.Stop
                                      else Icons.Default.FiberManualRecord,
                        contentDescription = if (uiState.isRecording) "Stop recording" else "Start recording",
                        tint = if (uiState.isRecording) Color.Red else Color.White
                    )
                }
                // Debug overlay toggle
                IconButton(onClick = { debugOverlayEnabled = !debugOverlayEnabled }) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = "Toggle debug overlay",
                        tint = if (debugOverlayEnabled) Color.Yellow else Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            // Permission required screen
            CameraPermissionScreen(
                shouldShowRationale = permissionState.shouldShowRationale,
                onRequestPermission = permissionState.requestPermission,
                onOpenSettings = permissionState.openSettings
            )
        }
        
        // Stats overlay at bottom
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {
            // Stats card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${uiState.shotsMade} / ${uiState.shotsAttempted} - ${uiState.percentage}%",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Manual controls
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Made controls
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Made",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IconButton(
                                    onClick = { viewModel.decrementMade() },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(Icons.Default.Remove, "Decrease made")
                                }
                                IconButton(
                                    onClick = { viewModel.incrementMade() },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(Icons.Default.Add, "Increase made")
                                }
                            }
                        }
                        
                        // Missed controls
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Missed",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IconButton(
                                    onClick = { viewModel.decrementMissed() },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(Icons.Default.Remove, "Decrease missed")
                                }
                                IconButton(
                                    onClick = { viewModel.incrementMissed() },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(Icons.Default.Add, "Increase missed")
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // End session button
            Button(
                onClick = {
                    val result = viewModel.endSession()
                    onEndSession(0L) // Navigate to summary
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("END SESSION")
            }
        }
    }
    } // end Scaffold
}

@Composable
fun BallDetectionOverlay(detection: BallDetection?) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Bounding box drawn on Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (detection == null) return@Canvas

            val box = detection.boundingBox
            val left   = box.left   * size.width
            val top    = box.top    * size.height
            val right  = box.right  * size.width
            val bottom = box.bottom * size.height

            drawRect(
                color = Color.Cyan,
                topLeft = Offset(left, top),
                size = Size(right - left, bottom - top),
                style = Stroke(width = 4f)
            )
        }

        // Confidence badge fixed in top-left so it's always readable
        if (detection != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 8.dp, top = 8.dp),
                color = Color.Cyan.copy(alpha = 0.85f),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "🏀 %.0f%%".format(detection.confidence * 100),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Black,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}


@Composable
fun CameraPermissionScreen(
    shouldShowRationale: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Camera Permission Required",
                style = MaterialTheme.typography.headlineMedium
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "This app needs camera access to detect basketball shots. Your camera is only used during active sessions and no data is stored or shared.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            if (shouldShowRationale) {
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open Settings")
                }
            } else {
                Button(
                    onClick = onRequestPermission,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Grant Permission")
                }
            }
        }
    }
}
