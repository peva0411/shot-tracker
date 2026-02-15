package com.shottracker.ui.session

import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shottracker.camera.detector.DetectionState
import com.shottracker.camera.detector.ShotDetector
import com.shottracker.camera.feedback.FeedbackManager
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val detectionState: DetectionState = DetectionState.IDLE
)

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val feedbackManager: FeedbackManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()
    
    private val shotDetector = ShotDetector()
    private var sessionStartTime = System.currentTimeMillis()
    
    init {
        viewModelScope.launch {
            shotDetector.detectionState.collect { state ->
                _uiState.value = _uiState.value.copy(detectionState = state)
                
                // Auto-increment when shot is confirmed
                if (state == DetectionState.CONFIRMED) {
                    incrementMade()
                    feedbackManager.vibrate() // Haptic feedback
                    // Clear confirmed state after a delay
                    kotlinx.coroutines.delay(500)
                    shotDetector.clearConfirmedState()
                }
            }
        }
    }
    
    /**
     * Analyze a camera frame for shot detection.
     */
    fun onFrameAnalyzed(imageProxy: ImageProxy) {
        shotDetector.analyzeFrame(imageProxy)
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
            // Can't have fewer attempts than makes
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
        shotDetector.reset()
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
