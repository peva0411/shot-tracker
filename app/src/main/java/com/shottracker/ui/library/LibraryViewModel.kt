package com.shottracker.ui.library

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shottracker.drive.GoogleDriveService
import com.shottracker.media.CaptureEntity
import com.shottracker.media.CaptureRepository
import com.shottracker.media.UploadStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "LibraryViewModel"

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val captureRepository: CaptureRepository,
    val driveService: GoogleDriveService
) : ViewModel() {

    val captures: StateFlow<List<CaptureEntity>> = captureRepository.getAllCaptures()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isSignedIn = driveService.isSignedIn

    fun uploadCapture(capture: CaptureEntity) {
        viewModelScope.launch {
            try {
                captureRepository.markUploading(capture.id)
                val driveFileId = driveService.uploadFile(
                    java.io.File(capture.filePath),
                    capture.mimeType
                )
                captureRepository.markUploaded(capture.id, driveFileId)
            } catch (e: Exception) {
                Log.e(TAG, "Upload failed for id=${capture.id}", e)
                captureRepository.markFailed(capture.id)
            }
        }
    }

    fun uploadAll() {
        viewModelScope.launch {
            captures.value
                .filter { it.uploadStatus != UploadStatus.UPLOADED && it.uploadStatus != UploadStatus.UPLOADING }
                .forEach { uploadCapture(it) }
        }
    }

    fun deleteCapture(capture: CaptureEntity) {
        viewModelScope.launch {
            captureRepository.deleteCapture(capture.id, capture.filePath)
        }
    }

    fun signOut() = driveService.signOut()
}
