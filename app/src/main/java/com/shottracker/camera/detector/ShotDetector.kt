package com.shottracker.camera.detector

import android.graphics.Rect
import android.util.Log
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "ShotDetector"

/**
 * Shot detection states.
 */
enum class DetectionState {
    IDLE,           // Waiting for motion
    DETECTING,      // Motion detected, analyzing
    CONFIRMED       // Shot confirmed
}

/**
 * Shot detection result.
 */
data class ShotDetectionResult(
    val isShot: Boolean = false,
    val confidence: Float = 0f,
    val state: DetectionState = DetectionState.IDLE
)

/**
 * Detects basketball shots using motion detection.
 * 
 * This is a simplified implementation for MVP. Future versions will integrate OpenCV
 * for more sophisticated motion analysis.
 */
class ShotDetector {
    
    private val _detectionState = MutableStateFlow(DetectionState.IDLE)
    val detectionState: StateFlow<DetectionState> = _detectionState.asStateFlow()
    
    private var previousLuminance: ByteArray? = null
    private var lastDetectionTime = 0L
    private val detectionCooldownMs = 2000L // 2 seconds between detections
    
    // Region of Interest for hoop detection (configurable)
    private var roiRect: Rect = Rect(0, 0, 1280, 360) // Top portion of frame
    
    /**
     * Analyze a camera frame for shot detection.
     */
    fun analyzeFrame(imageProxy: ImageProxy): ShotDetectionResult {
        val currentTime = System.currentTimeMillis()
        
        // Cooldown period between detections
        if (currentTime - lastDetectionTime < detectionCooldownMs) {
            return ShotDetectionResult(state = _detectionState.value)
        }
        
        // Get luminance plane (Y plane in YUV)
        val yPlane = imageProxy.planes[0]
        val yBuffer = yPlane.buffer
        val ySize = yBuffer.remaining()
        val luminance = ByteArray(ySize)
        yBuffer.get(luminance)
        
        val result = previousLuminance?.let { prevLuminance ->
            detectMotion(prevLuminance, luminance, imageProxy.width, imageProxy.height)
        } ?: ShotDetectionResult()
        
        previousLuminance = luminance
        
        if (result.isShot) {
            _detectionState.value = DetectionState.CONFIRMED
            lastDetectionTime = currentTime
            Log.d(TAG, "Shot detected! Confidence: ${result.confidence}")
        } else if (result.confidence > 0.3f) {
            _detectionState.value = DetectionState.DETECTING
        } else {
            _detectionState.value = DetectionState.IDLE
        }
        
        return result.copy(state = _detectionState.value)
    }
    
    /**
     * Simple motion detection using frame differencing.
     * 
     * TODO: Integrate OpenCV for more sophisticated detection:
     * - Gaussian blur for noise reduction
     * - Better motion vector analysis
     * - Directional motion detection (upward trajectory)
     */
    private fun detectMotion(
        prev: ByteArray,
        current: ByteArray,
        width: Int,
        height: Int
    ): ShotDetectionResult {
        var totalDiff = 0L
        var pixelCount = 0
        val threshold = 30 // Sensitivity threshold
        
        // Sample every 4th pixel for performance
        for (i in prev.indices step 4) {
            val diff = Math.abs((current[i].toInt() and 0xFF) - (prev[i].toInt() and 0xFF))
            if (diff > threshold) {
                totalDiff += diff
                pixelCount++
            }
        }
        
        // Calculate motion intensity
        val avgDiff = if (pixelCount > 0) totalDiff.toFloat() / pixelCount else 0f
        val confidence = (avgDiff / 255f).coerceIn(0f, 1f)
        
        // Detect significant motion (tune this threshold)
        val motionThreshold = 0.15f
        val isShot = confidence > motionThreshold && pixelCount > (prev.size / 20)
        
        return ShotDetectionResult(
            isShot = isShot,
            confidence = confidence
        )
    }
    
    /**
     * Set the Region of Interest for hoop detection.
     */
    fun setROI(rect: Rect) {
        roiRect = rect
        Log.d(TAG, "ROI updated: $rect")
    }
    
    /**
     * Reset detection state.
     */
    fun reset() {
        previousLuminance = null
        _detectionState.value = DetectionState.IDLE
        Log.d(TAG, "Detector reset")
    }
    
    /**
     * Clear confirmed state after feedback is shown.
     */
    fun clearConfirmedState() {
        if (_detectionState.value == DetectionState.CONFIRMED) {
            _detectionState.value = DetectionState.IDLE
        }
    }
}
