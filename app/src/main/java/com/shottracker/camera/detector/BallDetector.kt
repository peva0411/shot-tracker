package com.shottracker.camera.detector

import android.content.Context
import android.graphics.RectF
import android.util.Log
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ops.ResizeOp

private const val TAG = "BallDetector"
private const val MODEL_ASSET = "ml/yolov8n.tflite"
private const val INPUT_SIZE = 640
private const val CONFIDENCE_THRESHOLD = 0.3f   // minimum to show as DETECTING
private const val SHOT_CONFIDENCE_THRESHOLD = 0.5f  // minimum to fire CONFIRMED
private const val FRAME_SKIP = 3                // run inference every 3rd frame
private const val DETECTION_COOLDOWN_MS = 2000L // minimum ms between CONFIRMED events

/** A detected basketball, with a bounding box in normalised [0,1] coordinates. */
data class BallDetection(
    val boundingBox: RectF,
    val confidence: Float
)

/**
 * Detects a basketball in camera frames using the on-device YOLO11n TFLite model.
 *
 * Exposes:
 * - [detection]       – the best bounding-box each processed frame (null = nothing found)
 * - [detectionState]  – simplified [DetectionState] for the session UI / shot logic
 *
 * Call [analyzeFrame] from the CameraX analysis executor, [reset] when a session ends,
 * and [close] when the detector is no longer needed to free TFLite resources.
 */
class BallDetector(context: Context) {

    private val tflite = TFLiteInferenceWrapper(context, MODEL_ASSET)

    private val _detection = MutableStateFlow<BallDetection?>(null)
    val detection: StateFlow<BallDetection?> = _detection.asStateFlow()

    private val _detectionState = MutableStateFlow(DetectionState.IDLE)
    val detectionState: StateFlow<DetectionState> = _detectionState.asStateFlow()

    // Resize + normalise to float32 [0, 1] in a single pipeline
    private val imageProcessor = ImageProcessor.Builder()
        .add(ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
        .add(NormalizeOp(0f, 255f))
        .build()

    // Output tensor: [batch=1][rows=5 (cx,cy,w,h,conf)][anchors=8400]
    private val outputBuffer = Array(1) { Array(5) { FloatArray(8400) } }

    private var frameCount = 0
    private var lastShotTime = 0L

    /**
     * Analyse one camera frame. Safe to call on any thread.
     * Must be called before the [ImageProxy] is closed.
     */
    fun analyzeFrame(imageProxy: ImageProxy) {
        if (++frameCount % FRAME_SKIP != 0) return

        val bitmap = imageProxy.toBitmap()

        try {
            val tensorImage = TensorImage(DataType.FLOAT32)
            tensorImage.load(bitmap)
            val processed = imageProcessor.process(tensorImage)

            tflite.run(processed.buffer, outputBuffer)

            val best = parseBestDetection(outputBuffer)
            _detection.value = best

            val now = System.currentTimeMillis()
            _detectionState.value = when {
                best != null
                        && best.confidence >= SHOT_CONFIDENCE_THRESHOLD
                        && now - lastShotTime >= DETECTION_COOLDOWN_MS -> {
                    lastShotTime = now
                    Log.d(TAG, "Ball detected: conf=%.2f box=${best.boundingBox}".format(best.confidence))
                    DetectionState.CONFIRMED
                }
                best != null -> DetectionState.DETECTING
                else -> DetectionState.IDLE
            }
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Scan the 8 400 YOLO anchors and return the highest-confidence detection
     * above [CONFIDENCE_THRESHOLD], or null if none found.
     *
     * Output layout – output[0][row][anchor]:
     *   row 0 = cx, row 1 = cy, row 2 = w, row 3 = h  (all in 640-px space)
     *   row 4 = basketball confidence
     */
    private fun parseBestDetection(output: Array<Array<FloatArray>>): BallDetection? {
        val anchors = output[0][0].size
        var bestConf = CONFIDENCE_THRESHOLD
        var bestIdx = -1

        for (i in 0 until anchors) {
            val conf = output[0][4][i]
            if (conf > bestConf) {
                bestConf = conf
                bestIdx = i
            }
        }

        if (bestIdx < 0) return null

        // Convert from 640-px space to normalised [0, 1]
        val cx = output[0][0][bestIdx] / INPUT_SIZE
        val cy = output[0][1][bestIdx] / INPUT_SIZE
        val w  = output[0][2][bestIdx] / INPUT_SIZE
        val h  = output[0][3][bestIdx] / INPUT_SIZE

        return BallDetection(
            boundingBox = RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f),
            confidence = bestConf
        )
    }

    /** Call after the UI has consumed a CONFIRMED event to return to IDLE. */
    fun clearConfirmedState() {
        if (_detectionState.value == DetectionState.CONFIRMED) {
            _detectionState.value = DetectionState.IDLE
        }
    }

    /** Reset state between sessions. */
    fun reset() {
        _detection.value = null
        _detectionState.value = DetectionState.IDLE
        frameCount = 0
        lastShotTime = 0L
        Log.d(TAG, "BallDetector reset")
    }

    /** Release the TFLite interpreter. Call when the detector is no longer needed. */
    fun close() {
        tflite.close()
    }
}
