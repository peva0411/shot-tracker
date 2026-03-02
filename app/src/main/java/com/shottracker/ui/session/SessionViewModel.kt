package com.shottracker.ui.session

import android.content.Context
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shottracker.camera.HoopPreferences
import com.shottracker.camera.HoopRegion
import com.shottracker.camera.TrainingCaptureController
import com.shottracker.camera.detector.BallDetector
import com.shottracker.camera.detector.BallPosition
import com.shottracker.camera.detector.DetectionState
import com.shottracker.camera.detector.ShotAnalyzer
import com.shottracker.camera.detector.TrajectoryTracker
import com.shottracker.data.DetectionPreferences
import com.shottracker.media.CaptureRepository
import com.shottracker.media.CaptureType
import com.shottracker.media.ShotOutcome
import com.shottracker.media.ShotRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "SessionViewModel"
private const val OUTCOME_WINDOW_MS = 600L   // ms to wait for made/missed signal after shot

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
    val lastCaptureFeedback: String? = null,
    val confidenceThreshold: Float = DetectionPreferences.DEFAULT_CONFIDENCE,
    /** The persisted hoop region, or null if not yet calibrated. */
    val hoopRegion: HoopRegion? = null,
    /** When true, detected shots automatically increment the attempted count. */
    val autoDetectEnabled: Boolean = true,
    /** True while the calibration overlay is shown. */
    val isCalibrating: Boolean = false,
    /** Fired when a shot is auto-detected; UI clears it after showing feedback. */
    val shotDetectedFeedback: Boolean = false,
    /** How many frames to skip between inference runs (debug tuning). */
    val frameSkip: Int = 2,
)

@HiltViewModel
class SessionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val captureRepository: CaptureRepository,
    private val shotRepository: ShotRepository,
    private val detectionPreferences: DetectionPreferences,
    private val hoopPreferences: HoopPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    private val ballDetector = BallDetector(context)

    /** Live bounding-box detections — collect in the UI to draw a debug overlay. */
    val detection = ballDetector.detection

    /** Rolling average inference time (ms) for the debug overlay. */
    val inferenceTimeMs = ballDetector.inferenceTimeMs

    val captureController = TrainingCaptureController(context)

    private val trajectoryTracker = TrajectoryTracker()
    private val shotAnalyzer = ShotAnalyzer(trajectoryTracker)

    /** Exposed so the UI can render the ball trail in the debug overlay. */
    private val _ballTrail = MutableStateFlow<List<BallPosition>>(emptyList())
    val ballTrail: StateFlow<List<BallPosition>> = _ballTrail.asStateFlow()

    // Held separately so it can be read from the detection coroutine without StateFlow overhead.
    @Volatile private var currentHoopRegion: HoopRegion? = null

    /** Coroutine job that resolves made/missed after the post-shot observation window. */
    private var pendingOutcomeJob: Job? = null

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
        viewModelScope.launch {
            detectionPreferences.confidenceThreshold.collect { threshold ->
                ballDetector.confidenceThreshold = threshold
                _uiState.value = _uiState.value.copy(confidenceThreshold = threshold)
            }
        }
        viewModelScope.launch {
            hoopPreferences.hoopRegion.collect { region ->
                currentHoopRegion = region
                _uiState.value = _uiState.value.copy(hoopRegion = region)
            }
        }
        // Track trajectory and analyze for shots on every new ball detection.
        viewModelScope.launch {
            ballDetector.detection.collect { det ->
                if (det != null) {
                    trajectoryTracker.add(det)
                    _ballTrail.value = trajectoryTracker.snapshot()

                    if (_uiState.value.autoDetectEnabled) {
                        val event = shotAnalyzer.analyze(currentHoopRegion)
                        if (event != null) {
                            onShotDetected(event)
                        }
                    }
                }
            }
        }
    }

    /**
     * Called when [ShotAnalyzer] fires a [ShotEvent].
     *
     * Immediately counts a miss (conservative), then starts a [OUTCOME_WINDOW_MS] observation
     * window (Step 4a).  After the window:
     * - Ball disappeared below hoop → upgrade to MADE (call [incrementMade]; attempts unchanged).
     * - Ball still visible and moving upward → confirm MISSED.
     * - Ball passes below hoop bottom during window → MADE.
     * - Ball moves back above hoop top → rim/board bounce → MISSED.
     * - Otherwise → AMBIGUOUS (keep as missed count, save with AMBIGUOUS outcome).
     */
    private fun onShotDetected(event: com.shottracker.camera.detector.ShotEvent) {
        incrementMissed()
        _uiState.value = _uiState.value.copy(shotDetectedFeedback = true)
        Log.d(TAG, "Shot detected (initialConf=%.2f arc=${event.hadProperArc})".format(event.initialConfidence))

        pendingOutcomeJob?.cancel()
        val capturedHoop = currentHoopRegion

        pendingOutcomeJob = viewModelScope.launch {
            delay(OUTCOME_WINDOW_MS)

            // Positions recorded during the observation window
            val windowPositions = trajectoryTracker.recentPositions(OUTCOME_WINDOW_MS)
            val hoopBottom = capturedHoop?.rect?.bottom ?: 0f
            val hoopTop   = capturedHoop?.rect?.top   ?: 0f

            val outcome: ShotOutcome
            val finalConfidence: Float

            when {
                // Ball passed below the hoop bottom → went through the net → MADE
                windowPositions.any { it.centerY > hoopBottom + 0.02f } -> {
                    outcome = ShotOutcome.MADE
                    finalConfidence = (event.initialConfidence + 0.15f).coerceAtMost(1f)
                    incrementMade()   // attempts already counted; only increments shotsMade
                    Log.d(TAG, "Outcome: MADE (ball passed below hoop bottom)")
                }

                // Ball moved back above hoop top → bounced off rim/backboard → MISSED
                windowPositions.isNotEmpty() &&
                        windowPositions.last().centerY < hoopTop -> {
                    outcome = ShotOutcome.MISSED
                    finalConfidence = (event.initialConfidence - 0.1f).coerceAtLeast(0f)
                    Log.d(TAG, "Outcome: MISSED (ball moved above hoop top)")
                }

                // Ambiguous — keep as missed count (already incremented), log as AMBIGUOUS
                else -> {
                    outcome = ShotOutcome.AMBIGUOUS
                    finalConfidence = event.initialConfidence
                    Log.d(TAG, "Outcome: AMBIGUOUS")
                }
            }

            shotRepository.insertShot(
                event          = event,
                outcome        = outcome,
                hoopRegion     = capturedHoop,
                madeConfidence = finalConfidence,
            )
        }
    }

    /** Clear the shot-detected feedback flag after the UI has consumed it. */
    fun clearShotDetectedFeedback() {
        _uiState.value = _uiState.value.copy(shotDetectedFeedback = false)
    }

    /**
     * Analyze a camera frame for ball detection.
     */
    fun onFrameAnalyzed(imageProxy: ImageProxy) {
        ballDetector.analyzeFrame(imageProxy)
    }

    /** Toggle auto shot detection on/off. */
    fun toggleAutoDetect() {
        _uiState.value = _uiState.value.copy(autoDetectEnabled = !_uiState.value.autoDetectEnabled)
    }

    /** Enter/exit the hoop calibration overlay. */
    fun toggleCalibration() {
        _uiState.value = _uiState.value.copy(isCalibrating = !_uiState.value.isCalibrating)
    }

    /** Save a new hoop region (called from the calibration overlay). */
    fun saveHoopRegion(region: HoopRegion) {
        viewModelScope.launch {
            hoopPreferences.setHoopRegion(region)
        }
        _uiState.value = _uiState.value.copy(isCalibrating = false)
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

    /** Update the detection confidence threshold. */
    fun setConfidenceThreshold(value: Float) {
        viewModelScope.launch {
            detectionPreferences.setConfidenceThreshold(value)
        }
    }

    /** Update how many frames are skipped between inference runs. */
    fun setFrameSkip(value: Int) {
        ballDetector.frameSkip = value
        _uiState.value = _uiState.value.copy(frameSkip = value)
    }

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

    private fun calculatePercentage(made: Int, attempted: Int): Int =
        if (attempted > 0) ((made.toFloat() / attempted) * 100).toInt() else 0

    override fun onCleared() {
        super.onCleared()
        ballDetector.close()
    }
}

data class SessionResult(
    val shotsMade: Int,
    val shotsAttempted: Int,
    val durationSeconds: Long
)
