package com.shottracker.ui.library

import android.app.Activity
import android.media.MediaMetadataRetriever
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickableimport androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.shottracker.media.CaptureEntity
import com.shottracker.media.CaptureType
import com.shottracker.media.UploadStatus

@Composable
fun LibraryScreen(
    onBack: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val captures by viewModel.captures.collectAsState()
    val isSignedIn by viewModel.isSignedIn.collectAsState()

    // Google Sign-In launcher
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

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Training Library") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                },
                actions = {
                    if (captures.isNotEmpty() && isSignedIn) {
                        TextButton(onClick = { viewModel.uploadAll() }) {
                            Text("Upload All")
                        }
                    }
                    if (isSignedIn) {
                        TextButton(onClick = { viewModel.signOut() }) { Text("Sign Out") }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Sign-in banner
            if (!isSignedIn) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Sign in to upload to Google Drive",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            signInLauncher.launch(viewModel.driveService.signInClient.signInIntent)
                        }) {
                            Text("Sign In")
                        }
                    }
                }
            }

            if (captures.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No captures yet.\nUse the camera and session screen to capture frames or record videos.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(captures, key = { it.id }) { capture ->
                        CaptureItem(
                            capture = capture,
                            onUpload = { viewModel.uploadCapture(capture) },
                            onDelete = { viewModel.deleteCapture(capture) },
                            isSignedIn = isSignedIn
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CaptureItem(
    capture: CaptureEntity,
    onUpload: () -> Unit,
    onDelete: () -> Unit,
    isSignedIn: Boolean
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete capture?") },
            text = { Text("This will remove the file from your device.") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDelete() }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .combinedClickable(
                onClick = {},
                onLongClick = { showDeleteDialog = true }
            )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Thumbnail
            if (capture.captureType == CaptureType.FRAME) {
                AsyncImage(
                    model = capture.filePath,
                    contentDescription = "Frame thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                VideoThumbnail(filePath = capture.filePath)
            }

            // Type badge (top-left)
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp),
                color = Color.Black.copy(alpha = 0.65f),
                shape = MaterialTheme.shapes.extraSmall
            ) {
                Text(
                    text = if (capture.captureType == CaptureType.FRAME) "FRAME" else "VIDEO",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            // Upload status icon (top-right)
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                when (capture.uploadStatus) {
                    UploadStatus.UPLOADED -> Icon(Icons.Default.CloudDone, null, tint = Color.Green, modifier = Modifier.size(20.dp))
                    UploadStatus.UPLOADING -> Icon(Icons.Default.HourglassEmpty, null, tint = Color.Yellow, modifier = Modifier.size(20.dp))
                    UploadStatus.FAILED -> Icon(Icons.Default.ErrorOutline, null, tint = Color.Red, modifier = Modifier.size(20.dp))
                    UploadStatus.LOCAL -> if (isSignedIn) {
                        IconButton(onClick = onUpload, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.CloudUpload, "Upload", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoThumbnail(filePath: String) {
    val bitmap = remember(filePath) {
        runCatching {
            MediaMetadataRetriever().use { mmr ->
                mmr.setDataSource(filePath)
                mmr.getFrameAtTime(0)?.asImageBitmap()
            }
        }.getOrNull()
    }

    if (bitmap != null) {
        androidx.compose.foundation.Image(
            bitmap = bitmap,
            contentDescription = "Video thumbnail",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("▶", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        }
    }
}
