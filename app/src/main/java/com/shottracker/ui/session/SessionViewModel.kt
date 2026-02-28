package com.shottracker.ui.session

import android.content.Context
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shottracker.camera.TrainingCaptureController
import com.shottracker.camera.detector.BallDetector
import com.shottracker.camera.detector.DetectionState
import com.shottracker.media.CaptureRepository
import com.shottracker.media.CaptureType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "SessionViewModel"

/**
 * UI state for the active session screen.
 */
data class SessionUiState(
    val shotsMade: Int = 0,
    val shotsAttempted: Int = 0,
    val percentage: Int = 0,
    val durationSeconds: Long = 0,
    val isActive: Boolean = true,
    val detectionState: DetectionState = DetectionState.IDLE,
    val isRecording: Boolean = false,
    val lastCaptureFeedback: String? = null
)

@HiltViewModel
class SessionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val captureRepository: CaptureRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()
    
    private val ballDetector = BallDetector(context)

    /** Live bounding-box detections — collect in the UI to draw a debug overlay. */
    val detection = ballDetector.detection

    val captureController = TrainingCaptureController(context)

    private var sessionStartTime = System.currentTimeMillis()
    
    init {
        viewModelScope.launch {
            ballDetector.detectionState.collect { state ->
                _uiState.value = _uiState.value.copy(detectionState = state)
            }
        }
        viewModelScope.launch {
            captureController.isRecording.collect { recording ->
                _uiState.value = _uiState.value.copy(isRecording = recording)
            }
        }
    }
    
    /**
     * Analyze a camera frame for ball detection (debug overlay only).
     */
    fun onFrameAnalyzed(imageProxy: ImageProxy) {
        ballDetector.analyzeFrame(imageProxy)
    }

    /** Capture a single JPEG training frame. */
    fun captureFrame() {
        captureController.captureFrame(
            onSaved = { file ->
                viewModelScope.launch {
                    captureRepository.insertCapture(file.absolutePath, "image/jpeg", CaptureType.FRAME)
                    _uiState.value = _uiState.value.copy(lastCaptureFeedback = "Frame saved")
                }
            },
            onError = { msg ->
                _uiState.value = _uiState.value.copy(lastCaptureFeedback = "Capture failed: $msg")
                Log.e(TAG, "Frame capture error: $msg")
            }
        )
    }

    /** Toggle video recording on/off. */
    fun toggleRecording() {
        if (_uiState.value.isRecording) {
            captureController.stopRecording()
        } else {
            captureController.startRecording(
                onFinished = { file ->
                    viewModelScope.launch {
                        captureRepository.insertCapture(file.absolutePath, "video/mp4", CaptureType.VIDEO)
                        _uiState.value = _uiState.value.copy(lastCaptureFeedback = "Video saved")
                    }
                },
                onError = { msg ->
                    _uiState.value = _uiState.value.copy(lastCaptureFeedback = "Recording failed: $msg")
                    Log.e(TAG, "Recording error: $msg")
                }
            )
        }
    }

    /** Clear the last capture feedback message after it has been shown. */
    fun clearCaptureFeedback() {
        _uiState.value = _uiState.value.copy(lastCaptureFeedback = null)
    }
    
    /**
     * Manually increment made shots.
     */
    fun incrementMade() {
        _uiState.value = _uiState.value.let { current ->
            val newMade = current.shotsMade + 1
            val newAttempted = maxOf(current.shotsAttempted, newMade)
            current.copy(
                shotsMade = newMade,
                shotsAttempted = newAttempted,
                percentage = calculatePercentage(newMade, newAttempted)
            )
        }
        Log.d(TAG, "Made incremented: ${_uiState.value.shotsMade}/${_uiState.value.shotsAttempted}")
    }
    
    /**
     * Manually decrement made shots.
     */
    fun decrementMade() {
        _uiState.value = _uiState.value.let { current ->
            if (current.shotsMade > 0) {
                val newMade = current.shotsMade - 1
                current.copy(
                    shotsMade = newMade,
                    percentage = calculatePercentage(newMade, current.shotsAttempted)
                )
            } else current
        }
    }
    
    /**
     * Manually increment missed shots.
     */
    fun incrementMissed() {
        _uiState.value = _uiState.value.let { current ->
            val newAttempted = current.shotsAttempted + 1
            current.copy(
                shotsAttempted = newAttempted,
                percentage = calculatePercentage(current.shotsMade, newAttempted)
            )
        }
        Log.d(TAG, "Missed incremented: ${_uiState.value.shotsMade}/${_uiState.value.shotsAttempted}")
    }
    
    /**
     * Manually decrement missed shots.
     */
    fun decrementMissed() {
        _uiState.value = _uiState.value.let { current ->
            if (current.shotsAttempted > current.shotsMade) {
                val newAttempted = current.shotsAttempted - 1
                current.copy(
                    shotsAttempted = newAttempted,
                    percentage = calculatePercentage(current.shotsMade, newAttempted)
                )
            } else current
        }
    }
    
    /**
     * End the current session.
     */
    fun endSession(): SessionResult {
        captureController.stopRecording()
        val duration = (System.currentTimeMillis() - sessionStartTime) / 1000
        _uiState.value = _uiState.value.copy(
            isActive = false,
            durationSeconds = duration
        )
        
        return SessionResult(
            shotsMade = _uiState.value.shotsMade,
            shotsAttempted = _uiState.value.shotsAttempted,
            durationSeconds = duration
        )
    }
    
    private fun calculatePercentage(made: Int, attempted: Int): Int {
        return if (attempted > 0) {
            ((made.toFloat() / attempted) * 100).toInt()
        } else 0
    }
    
    override fun onCleared() {
        super.onCleared()
        ballDetector.close()
    }
}

/**
 * Result data from a completed session.
 */
data class SessionResult(
    val shotsMade: Int,
    val shotsAttempted: Int,
    val durationSeconds: Long
)
