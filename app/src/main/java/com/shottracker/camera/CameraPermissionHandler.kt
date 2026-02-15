package com.shottracker.camera

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Handles camera permission requests and status checking.
 */
object CameraPermissionHandler {
    
    fun hasPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        context.startActivity(intent)
    }
}

/**
 * Composable function to manage camera permission state.
 */
@Composable
fun rememberCameraPermissionState(
    onPermissionResult: (Boolean) -> Unit = {}
): CameraPermissionState {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(CameraPermissionHandler.hasPermission(context))
    }
    var shouldShowRationale by remember { mutableStateOf(false) }
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        onPermissionResult(isGranted)
        if (!isGranted) {
            shouldShowRationale = true
        }
    }
    
    return remember(hasPermission, shouldShowRationale) {
        CameraPermissionState(
            hasPermission = hasPermission,
            shouldShowRationale = shouldShowRationale,
            requestPermission = { launcher.launch(Manifest.permission.CAMERA) },
            openSettings = { CameraPermissionHandler.openAppSettings(context) }
        )
    }
}

/**
 * State holder for camera permission.
 */
data class CameraPermissionState(
    val hasPermission: Boolean,
    val shouldShowRationale: Boolean,
    val requestPermission: () -> Unit,
    val openSettings: () -> Unit
)
