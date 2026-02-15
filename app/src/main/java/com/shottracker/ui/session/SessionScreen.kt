package com.shottracker.ui.session

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.shottracker.camera.CameraPreview
import com.shottracker.camera.detector.DetectionState
import com.shottracker.camera.rememberCameraPermissionState

@Composable
fun SessionScreen(
    onEndSession: (Long) -> Unit,
    viewModel: SessionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val permissionState = rememberCameraPermissionState()
    
    LaunchedEffect(Unit) {
        if (!permissionState.hasPermission) {
            permissionState.requestPermission()
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        if (permissionState.hasPermission) {
            // Camera preview (full screen)
            CameraPreview(
                onFrameAnalyzed = { imageProxy ->
                    viewModel.onFrameAnalyzed(imageProxy)
                }
            )
            
            // Shot detection visual feedback
            ShotDetectionFeedback(
                detectionState = uiState.detectionState
            )
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
}

@Composable
fun ShotDetectionFeedback(detectionState: DetectionState) {
    val alpha by animateFloatAsState(
        targetValue = when (detectionState) {
            DetectionState.CONFIRMED -> 0.8f
            DetectionState.DETECTING -> 0.3f
            DetectionState.IDLE -> 0f
        },
        animationSpec = tween(durationMillis = 200),
        label = "detection_alpha"
    )
    
    val color = when (detectionState) {
        DetectionState.CONFIRMED -> Color.Green
        DetectionState.DETECTING -> Color.Yellow
        DetectionState.IDLE -> Color.Transparent
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(alpha)
            .background(color.copy(alpha = 0.3f))
    )
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
