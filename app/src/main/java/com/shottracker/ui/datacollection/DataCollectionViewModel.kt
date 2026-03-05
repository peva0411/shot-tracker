package com.shottracker.ui.datacollection

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shottracker.camera.TrainingCaptureController
import com.shottracker.drive.GoogleDriveService
import com.shottracker.media.CaptureRepository
import com.shottracker.media.CaptureType
import com.shottracker.media.UploadStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

private const val TAG = "DataCollectionViewModel"

private const val IDLE_MIN_S = 5
private const val IDLE_MAX_S = 15
private const val REC_MIN_S = 5
private const val REC_MAX_S = 30

data class DataCollectionUiState(
    val isCollecting: Boolean = false,
    val isRecording: Boolean = false,
    /** Seconds remaining until next recording starts; null when recording or idle. */
    val countdownSeconds: Int? = null,
    /** Seconds remaining in the current recording; null when not recording. */
    val recordingSecondsLeft: Int? = null,
    val clipsCollected: Int = 0,
    val pendingUploads: Int = 0,
    val isSignedIn: Boolean = false,
    val isUploading: Boolean = false,
)

@HiltViewModel
class DataCollectionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val captureRepository: CaptureRepository,
    val driveService: GoogleDriveService,
) : ViewModel() {

    val captureController = TrainingCaptureController(context)

    private val _uiState = MutableStateFlow(DataCollectionUiState())
    val uiState: StateFlow<DataCollectionUiState> = _uiState.asStateFlow()

    private var collectionJob: Job? = null

    init {
        viewModelScope.launch {
            captureController.isRecording.collect { recording ->
                _uiState.value = _uiState.value.copy(isRecording = recording)
            }
        }
        viewModelScope.launch {
            captureRepository.getAllCaptures().collect { captures ->
                val pending = captures.count {
                    it.uploadStatus == UploadStatus.LOCAL || it.uploadStatus == UploadStatus.FAILED
                }
                _uiState.value = _uiState.value.copy(pendingUploads = pending)
            }
        }
        viewModelScope.launch {
            driveService.isSignedIn.collect { signedIn ->
                _uiState.value = _uiState.value.copy(isSignedIn = signedIn)
            }
        }
    }

    fun startCollection() {
        if (_uiState.value.isCollecting) return
        _uiState.value = _uiState.value.copy(isCollecting = true)

        collectionJob = viewModelScope.launch {
            while (isActive) {
                // Idle phase: count down a random wait before the next clip
                val idleSecs = (IDLE_MIN_S..IDLE_MAX_S).random()
                repeat(idleSecs) { i ->
                    _uiState.value = _uiState.value.copy(countdownSeconds = idleSecs - i)
                    delay(1_000L)
                }
                _uiState.value = _uiState.value.copy(countdownSeconds = null)

                // Recording phase: record for a random duration
                val recSecs = (REC_MIN_S..REC_MAX_S).random()
                val recordingComplete = CompletableDeferred<Unit>()

                captureController.startRecording(
                    onFinished = { file ->
                        viewModelScope.launch {
                            captureRepository.insertCapture(
                                file.absolutePath, "video/mp4", CaptureType.VIDEO
                            )
                            _uiState.value = _uiState.value.copy(
                                clipsCollected = _uiState.value.clipsCollected + 1
                            )
                            recordingComplete.complete(Unit)
                        }
                    },
                    onError = { msg ->
                        Log.e(TAG, "Auto-recording error: $msg")
                        recordingComplete.complete(Unit)
                    }
                )

                repeat(recSecs) { i ->
                    _uiState.value = _uiState.value.copy(recordingSecondsLeft = recSecs - i)
                    delay(1_000L)
                }
                _uiState.value = _uiState.value.copy(recordingSecondsLeft = null)

                captureController.stopRecording()
                recordingComplete.await()
            }
        }
    }

    fun stopCollection() {
        collectionJob?.cancel()
        captureController.stopRecording()
        _uiState.value = _uiState.value.copy(
            isCollecting = false,
            countdownSeconds = null,
            recordingSecondsLeft = null,
        )
    }

    fun uploadAll() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploading = true)
            val timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
            val folderName = "shot-tracker-training_$timestamp"
            val captures = captureRepository.getAllCaptures().first()
            captures
                .filter {
                    it.uploadStatus != UploadStatus.UPLOADED &&
                            it.uploadStatus != UploadStatus.UPLOADING
                }
                .forEach { capture ->
                    try {
                        captureRepository.markUploading(capture.id)
                        val driveFileId = driveService.uploadFile(
                            File(capture.filePath), capture.mimeType, folderName
                        )
                        captureRepository.markUploaded(capture.id, driveFileId)
                    } catch (e: Exception) {
                        Log.e(TAG, "Upload failed for id=${capture.id}", e)
                        captureRepository.markFailed(capture.id)
                    }
                }
            _uiState.value = _uiState.value.copy(isUploading = false)
        }
    }

    fun signOut() = driveService.signOut()
}
