package com.shottracker.ui.datacollection

import android.Manifest
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.shottracker.camera.CameraPreview
import com.shottracker.camera.rememberCameraPermissionState

@Composable
fun DataCollectionScreen(
    onBack: () -> Unit,
    viewModel: DataCollectionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val permissionState = rememberCameraPermissionState()
    val context = LocalContext.current

    val audioPermissionGranted = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> audioPermissionGranted.value = granted }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            task.addOnSuccessListener { account ->
                viewModel.driveService.onSignInSuccess(account)
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!permissionState.hasPermission) permissionState.requestPermission()
        if (!audioPermissionGranted.value) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Stop collection when leaving the screen
    DisposableEffect(Unit) {
        onDispose { viewModel.stopCollection() }
    }

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Data Collection") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.stopCollection()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!uiState.isSignedIn) {
                        TextButton(onClick = {
                            signInLauncher.launch(viewModel.driveService.signInClient.signInIntent)
                        }) { Text("Sign In") }
                    } else {
                        if (uiState.pendingUploads > 0) {
                            TextButton(
                                onClick = { viewModel.uploadAll() },
                                enabled = !uiState.isUploading
                            ) {
                                Icon(
                                    Icons.Default.CloudUpload,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (uiState.isUploading) "Uploading…"
                                    else "Upload All (${uiState.pendingUploads})"
                                )
                            }
                        }
                        TextButton(onClick = { viewModel.signOut() }) { Text("Sign Out") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.5f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (permissionState.hasPermission) {
                CameraPreview(
                    captureController = viewModel.captureController,
                    onFrameAnalyzed = { imageProxy -> imageProxy.close() }
                )

                StatusOverlay(
                    uiState = uiState,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )

                val fabColor = if (uiState.isCollecting)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.primary

                FloatingActionButton(
                    onClick = {
                        if (uiState.isCollecting) viewModel.stopCollection()
                        else viewModel.startCollection()
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(24.dp),
                    containerColor = fabColor
                ) {
                    Icon(
                        imageVector = if (uiState.isCollecting) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (uiState.isCollecting) "Stop" else "Start",
                        modifier = Modifier.size(32.dp)
                    )
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("Camera permission required")
                        Button(onClick = {
                            if (permissionState.shouldShowRationale) permissionState.requestPermission()
                            else permissionState.openSettings()
                        }) {
                            Text(if (permissionState.shouldShowRationale) "Grant Permission" else "Open Settings")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusOverlay(
    uiState: DataCollectionUiState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 80.dp, bottom = 24.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.65f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = statusText(uiState),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Clips: ${uiState.clipsCollected}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.75f)
                )
                if (uiState.pendingUploads > 0) {
                    Text(
                        text = "Pending upload: ${uiState.pendingUploads}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                }
            }
            if (uiState.isRecording) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(
                            MaterialTheme.colorScheme.error,
                            shape = RoundedCornerShape(2.dp)
                        )
                )
            }
        }
    }
}

private fun statusText(uiState: DataCollectionUiState): String = when {
    !uiState.isCollecting -> "Tap ▶ to start auto-collection"
    uiState.isRecording -> {
        val secs = uiState.recordingSecondsLeft
        if (secs != null) "⏺ Recording… ${secs}s remaining" else "⏺ Recording…"
    }
    uiState.countdownSeconds != null -> "Next clip in ${uiState.countdownSeconds}s"
    else -> "Starting…"
}
