package com.shottracker.ui.session

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GpsFixed
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.shottracker.camera.CameraPreview
import com.shottracker.camera.HoopPreferences
import com.shottracker.camera.HoopRegion
import com.shottracker.camera.detector.BallDetection
import com.shottracker.camera.detector.BallPosition
import com.shottracker.camera.rememberCameraPermissionState

@Composable
fun SessionScreen(
    onEndSession: (Long) -> Unit,
    viewModel: SessionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val detection by viewModel.detection.collectAsState()
    val ballTrail by viewModel.ballTrail.collectAsState()
    val inferenceTimeMs by viewModel.inferenceTimeMs.collectAsState()
    val permissionState = rememberCameraPermissionState()
    val context = LocalContext.current
    var debugOverlayEnabled by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val audioPermissionGranted = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> audioPermissionGranted.value = granted }

    // Show snackbar on capture feedback
    LaunchedEffect(uiState.lastCaptureFeedback) {
        uiState.lastCaptureFeedback?.let { msg ->
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
            viewModel.clearCaptureFeedback()
        }
    }

    // Flash snackbar on auto-detected shot
    LaunchedEffect(uiState.shotDetectedFeedback) {
        if (uiState.shotDetectedFeedback) {
            snackbarHostState.showSnackbar("🏀 Shot detected! (tap − to undo)", duration = SnackbarDuration.Short)
            viewModel.clearShotDetectedFeedback()
        }
    }

    LaunchedEffect(Unit) {
        if (!permissionState.hasPermission) permissionState.requestPermission()
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (permissionState.hasPermission) {
                CameraPreview(
                    captureController = viewModel.captureController,
                    onFrameAnalyzed = { imageProxy -> viewModel.onFrameAnalyzed(imageProxy) }
                )

                // Ball bounding-box + trail debug overlay
                if (debugOverlayEnabled) {
                    BallDetectionOverlay(detection = detection, trail = ballTrail)
                    ConfidenceSlider(
                        value = uiState.confidenceThreshold,
                        onValueChange = { viewModel.setConfidenceThreshold(it) },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 8.dp, top = 48.dp)
                    )
                    FrameSkipSlider(
                        value = uiState.frameSkip,
                        onValueChange = { viewModel.setFrameSkip(it) },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 8.dp, top = 160.dp)
                    )
                    // Inference timing badge
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 8.dp, top = 220.dp),
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "Inference: %.1f ms".format(inferenceTimeMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Hoop region overlay (always visible when calibrated)
                uiState.hoopRegion?.let { region ->
                    HoopOverlay(region = region)
                }

                // Hoop calibration overlay (active when isCalibrating)
                if (uiState.isCalibrating) {
                    HoopCalibrationOverlay(
                        initial = uiState.hoopRegion,
                        onSave   = { viewModel.saveHoopRegion(it) },
                        onCancel = { viewModel.toggleCalibration() }
                    )
                }

                // Top-right toolbar
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Capture frame
                    IconButton(onClick = { viewModel.captureFrame() }) {
                        Icon(Icons.Default.CameraAlt, "Capture frame", tint = Color.White)
                    }
                    // Record video
                    IconButton(onClick = {
                        if (!audioPermissionGranted.value) {
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
                    // Set hoop calibration
                    IconButton(onClick = { viewModel.toggleCalibration() }) {
                        Icon(
                            imageVector = Icons.Default.GpsFixed,
                            contentDescription = "Set hoop position",
                            tint = when {
                                uiState.isCalibrating  -> Color.Yellow
                                uiState.hoopRegion == null -> Color.Red.copy(alpha = 0.8f)
                                else -> Color.Green.copy(alpha = 0.8f)
                            }
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
                CameraPermissionScreen(
                    shouldShowRationale = permissionState.shouldShowRationale,
                    onRequestPermission = permissionState.requestPermission,
                    onOpenSettings = permissionState.openSettings
                )
            }

            // Stats overlay at bottom (hidden during hoop calibration)
            if (!uiState.isCalibrating) Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
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

                        // Auto-detect status chip
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                        ) {
                            val autoLabel = if (uiState.autoDetectEnabled) "Auto-detect ON" else "Auto-detect OFF"
                            val autoColor = if (uiState.autoDetectEnabled) Color(0xFF4CAF50) else Color.Gray
                            Surface(
                                color = autoColor.copy(alpha = 0.15f),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = autoLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = autoColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            if (uiState.hoopRegion == null && uiState.autoDetectEnabled) {
                                Surface(
                                    color = Color.Red.copy(alpha = 0.15f),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = "⚠ Set hoop first",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.Red,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Made controls
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Made", style = MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    IconButton(onClick = { viewModel.decrementMade() }, modifier = Modifier.size(40.dp)) {
                                        Icon(Icons.Default.Remove, "Decrease made")
                                    }
                                    IconButton(onClick = { viewModel.incrementMade() }, modifier = Modifier.size(40.dp)) {
                                        Icon(Icons.Default.Add, "Increase made")
                                    }
                                }
                            }
                            // Missed controls
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Missed", style = MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    IconButton(onClick = { viewModel.decrementMissed() }, modifier = Modifier.size(40.dp)) {
                                        Icon(Icons.Default.Remove, "Decrease missed")
                                    }
                                    IconButton(onClick = { viewModel.incrementMissed() }, modifier = Modifier.size(40.dp)) {
                                        Icon(Icons.Default.Add, "Increase missed")
                                    }
                                }
                            }
                            // Auto-detect toggle
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Auto", style = MaterialTheme.typography.bodySmall)
                                Switch(
                                    checked = uiState.autoDetectEnabled,
                                    onCheckedChange = { viewModel.toggleAutoDetect() },
                                    modifier = Modifier.height(40.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        viewModel.endSession()
                        onEndSession(0L)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("END SESSION")
                }
            }
        }
    }
}

// ── Overlays ──────────────────────────────────────────────────────────────────

@Composable
fun BallDetectionOverlay(detection: BallDetection?, trail: List<BallPosition> = emptyList()) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        // Draw fading ball trail
        if (trail.isNotEmpty()) {
            val frameW = trail.last().boundingBox.let { it.right - it.left }
                .let { detection?.frameWidth?.toFloat() ?: size.width }
            val frameH = detection?.frameHeight?.toFloat() ?: size.height
            val scale = maxOf(size.width / frameW, size.height / frameH)
            val displayedW = frameW * scale
            val displayedH = frameH * scale
            val offsetX = (displayedW - size.width) / 2f
            val offsetY = (displayedH - size.height) / 2f

            trail.forEachIndexed { index, pos ->
                val alpha = (index + 1).toFloat() / trail.size * 0.6f
                drawCircle(
                    color = Color.Yellow.copy(alpha = alpha),
                    radius = 6f,
                    center = Offset(
                        pos.centerX * displayedW - offsetX,
                        pos.centerY * displayedH - offsetY
                    )
                )
            }
        }

        // Draw current bounding box
        if (detection != null) {
            val box = detection.boundingBox
            val frameW = detection.frameWidth.toFloat()
            val frameH = detection.frameHeight.toFloat()
            val scale = maxOf(size.width / frameW, size.height / frameH)
            val displayedW = frameW * scale
            val displayedH = frameH * scale
            val offsetX = (displayedW - size.width) / 2f
            val offsetY = (displayedH - size.height) / 2f

            drawRect(
                color = Color.Cyan,
                topLeft = Offset(box.left * displayedW - offsetX, box.top * displayedH - offsetY),
                size = Size(
                    (box.right - box.left) * displayedW,
                    (box.bottom - box.top) * displayedH
                ),
                style = Stroke(width = 4f)
            )
        }
    }

    if (detection != null) {
        Box(modifier = Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 8.dp),
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

/** Renders the saved hoop region as a translucent ring on the live preview. */
@Composable
fun HoopOverlay(region: HoopRegion) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = region.centerX * size.width
        val cy = region.centerY * size.height
        val rx = region.width  * size.width  / 2f
        val ry = region.height * size.height / 2f
        drawOval(
            color = Color(0xFFFF6600).copy(alpha = 0.6f),
            topLeft = Offset(cx - rx, cy - ry),
            size = Size(rx * 2f, ry * 2f),
            style = Stroke(width = 4f)
        )
    }
}

/**
 * Full-screen overlay for interactively positioning the hoop ring.
 * The user drags to move the ring center; a Save/Cancel toolbar is shown at the top.
 */
@Composable
fun HoopCalibrationOverlay(
    initial: HoopRegion?,
    onSave: (HoopRegion) -> Unit,
    onCancel: () -> Unit
) {
    // Start at the saved region or a sensible default (upper-third center)
    var cx by remember { mutableFloatStateOf(initial?.centerX ?: 0.5f) }
    var cy by remember { mutableFloatStateOf(initial?.centerY ?: 0.33f) }
    var rw by remember { mutableFloatStateOf(initial?.width  ?: HoopPreferences.DEFAULT_WIDTH) }
    var rh by remember { mutableFloatStateOf(initial?.height ?: HoopPreferences.DEFAULT_HEIGHT) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
    ) {
        // Draggable hoop ring
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { _, dragAmount ->
                        cx = (cx + dragAmount.x / size.width).coerceIn(0f, 1f)
                        cy = (cy + dragAmount.y / size.height).coerceIn(0f, 1f)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        cx = (offset.x / size.width).coerceIn(0f, 1f)
                        cy = (offset.y / size.height).coerceIn(0f, 1f)
                    }
                }
        ) {
            val screenCx = cx * size.width
            val screenCy = cy * size.height
            val rx = rw * size.width  / 2f
            val ry = rh * size.height / 2f
            // Shadow
            drawOval(
                color = Color.Black.copy(alpha = 0.4f),
                topLeft = Offset(screenCx - rx + 2, screenCy - ry + 2),
                size = Size(rx * 2f, ry * 2f),
                style = Stroke(width = 6f)
            )
            // Ring
            drawOval(
                color = Color(0xFFFF6600),
                topLeft = Offset(screenCx - rx, screenCy - ry),
                size = Size(rx * 2f, ry * 2f),
                style = Stroke(width = 4f)
            )
            // Center dot
            drawCircle(color = Color(0xFFFF6600), radius = 6f, center = Offset(screenCx, screenCy))
        }

        // Instruction label
        Surface(
            modifier = Modifier.align(Alignment.Center).offset(y = 60.dp),
            color = Color.Black.copy(alpha = 0.6f),
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                text = "Drag ring to hoop position",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        // Size sliders
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp, start = 32.dp, end = 32.dp)
        ) {
            Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), shape = MaterialTheme.shapes.medium) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Width: %.0f%%".format(rw * 100), style = MaterialTheme.typography.labelSmall)
                    Slider(value = rw, onValueChange = { rw = it }, valueRange = 0.04f..0.35f, modifier = Modifier.height(24.dp))
                    Text("Height: %.0f%%".format(rh * 100), style = MaterialTheme.typography.labelSmall)
                    Slider(value = rh, onValueChange = { rh = it }, valueRange = 0.02f..0.15f, modifier = Modifier.height(24.dp))
                }
            }
        }

        // Save / Cancel buttons
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text("Cancel")
            }
            Button(
                onClick = { onSave(HoopRegion(cx, cy, rw, rh)) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Save hoop")
            }
        }
    }
}

@Composable
fun ConfidenceSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.width(220.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = "Min confidence: %.0f%%".format(value * 100),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = 0.05f..0.95f,
                modifier = Modifier.height(24.dp)
            )
        }
    }
}

@Composable
fun FrameSkipSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.width(220.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = "Frame skip: $value",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Slider(
                value = value.toFloat(),
                onValueChange = { onValueChange(it.toInt()) },
                valueRange = 1f..8f,
                steps = 6,
                modifier = Modifier.height(24.dp)
            )
        }
    }
}

@Composable
fun CameraPermissionScreen(
    shouldShowRationale: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
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
            Text("Camera Permission Required", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "This app needs camera access to detect basketball shots. Your camera is only used during active sessions and no data is stored or shared.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(32.dp))
            if (shouldShowRationale) {
                Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) { Text("Open Settings") }
            } else {
                Button(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth()) { Text("Grant Permission") }
            }
        }
    }
}
