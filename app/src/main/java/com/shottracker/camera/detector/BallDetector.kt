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
private const val MODEL_ASSET = "ml/yolo11n.tflite"
private const val INPUT_SIZE = 640
private const val DEFAULT_CONFIDENCE_THRESHOLD = 0.3f
private const val DEFAULT_FRAME_SKIP = 2        // run inference every Nth frame
private const val DETECTION_COOLDOWN_MS = 2000L // minimum ms between CONFIRMED events

/**
 * A detected basketball with a bounding box in normalised [0,1] coordinates relative
 * to the oriented (upright) camera frame, plus the frame dimensions needed to map
 * the box correctly onto the FILL_CENTER preview overlay.
 */
data class BallDetection(
    val boundingBox: RectF,
    val confidence: Float,
    val frameWidth: Int,
    val frameHeight: Int,
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

    /** Confidence threshold for showing a detection. Mutable at runtime. */
    @Volatile
    var confidenceThreshold: Float = DEFAULT_CONFIDENCE_THRESHOLD

    /** How many frames to skip between inference runs. 1 = every frame, 2 = every other, etc. */
    @Volatile
    var frameSkip: Int = DEFAULT_FRAME_SKIP

    // Resize to 640×640, normalize to [0, 1] float32 (model expects normalized input)
    private val imageProcessor = ImageProcessor.Builder()
        .add(ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
        .add(NormalizeOp(0f, 255f))
        .build()

    // Output tensor: [batch=1][rows=5 (cx,cy,w,h,conf)][anchors=8400]
    private val outputBuffer = Array(1) { Array(5) { FloatArray(8400) } }

    private var frameCount = 0
    private var lastShotTime = 0L
    private val kalman = BallKalmanFilter()

    /** Size of the last real detection, used for Kalman-predicted bounding boxes. */
    private var lastDetectionHalfW = 0.03f
    private var lastDetectionHalfH = 0.03f

    // Inference timing telemetry
    private val _inferenceTimeMs = MutableStateFlow(0f)
    /** Rolling average inference time in milliseconds. */
    val inferenceTimeMs: StateFlow<Float> = _inferenceTimeMs.asStateFlow()
    private var inferenceSum = 0L
    private var inferenceCount = 0

    /**
     * Analyse one camera frame. Safe to call on any thread.
     * Must be called before the [ImageProxy] is closed.
     */
    fun analyzeFrame(imageProxy: ImageProxy) {
        if (++frameCount % frameSkip != 0) {
            // Emit a Kalman-predicted position on skipped frames
            if (kalman.isInitialized) {
                val (px, py) = kalman.predict()
                val frameW = imageProxy.width
                val frameH = imageProxy.height
                // Use the rotation to determine oriented dimensions
                val rotation = imageProxy.imageInfo.rotationDegrees
                val orientedW = if (rotation == 90 || rotation == 270) frameH else frameW
                val orientedH = if (rotation == 90 || rotation == 270) frameW else frameH
                _detection.value = BallDetection(
                    boundingBox = RectF(
                        px - lastDetectionHalfW, py - lastDetectionHalfH,
                        px + lastDetectionHalfW, py + lastDetectionHalfH
                    ),
                    confidence = 0f,  // marks this as predicted, not measured
                    frameWidth = orientedW,
                    frameHeight = orientedH,
                )
            }
            return
        }

        val bitmap = imageProxy.toBitmap()
        // toBitmap() does not apply rotation; rotate so the model always sees an upright image
        // and output coordinates align with the portrait overlay canvas.
        val rotation = imageProxy.imageInfo.rotationDegrees
        val orientedBitmap = if (rotation != 0) {
            val matrix = android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }
            android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else bitmap

        try {
            val tensorImage = TensorImage(DataType.FLOAT32)
            tensorImage.load(orientedBitmap)
            val processed = imageProcessor.process(tensorImage)

            val inferStart = System.nanoTime()
            tflite.run(processed.buffer, outputBuffer)
            val inferMs = (System.nanoTime() - inferStart) / 1_000_000f
            inferenceCount++
            inferenceSum += inferMs.toLong()
            _inferenceTimeMs.value = inferenceSum.toFloat() / inferenceCount
            if (inferenceCount % 30 == 0) {
                Log.d(TAG, "Inference: %.1fms avg (last=%.1fms)".format(_inferenceTimeMs.value, inferMs))
            }

            val best = parseBestDetection(outputBuffer, orientedBitmap.width, orientedBitmap.height)
            _detection.value = best

            // Update Kalman filter
            if (best != null) {
                val cx = (best.boundingBox.left + best.boundingBox.right) / 2f
                val cy = (best.boundingBox.top + best.boundingBox.bottom) / 2f
                kalman.predict()  // advance state to current time
                kalman.correct(cx, cy)
                lastDetectionHalfW = (best.boundingBox.right - best.boundingBox.left) / 2f
                lastDetectionHalfH = (best.boundingBox.bottom - best.boundingBox.top) / 2f
            } else {
                kalman.predict()  // keep predicting even when no detection
            }

            val shotThreshold = confidenceThreshold + 0.2f
            val now = System.currentTimeMillis()
            _detectionState.value = when {
                best != null
                        && best.confidence >= shotThreshold
                        && now - lastShotTime >= DETECTION_COOLDOWN_MS -> {
                    lastShotTime = now
                    Log.d(TAG, "Ball detected: conf=%.2f box=${best.boundingBox}".format(best.confidence))
                    DetectionState.CONFIRMED
                }
                best != null -> DetectionState.DETECTING
                else -> DetectionState.IDLE
            }
        } finally {
            if (orientedBitmap !== bitmap) orientedBitmap.recycle()
            bitmap.recycle()
        }
    }

    /**
     * Scan the 8 400 YOLO anchors and return the highest-confidence detection
     * above [confidenceThreshold], or null if none found.
     *
     * Output layout – output[0][row][anchor]:
     *   row 0 = cx, row 1 = cy, row 2 = w, row 3 = h  (normalised [0, 1])
     *   row 4 = basketball confidence
     */
    private fun parseBestDetection(output: Array<Array<FloatArray>>, frameWidth: Int, frameHeight: Int): BallDetection? {
        val anchors = output[0][0].size
        var bestConf = confidenceThreshold
        var bestIdx = -1

        for (i in 0 until anchors) {
            val conf = output[0][4][i]
            if (conf > bestConf) {
                bestConf = conf
                bestIdx = i
            }
        }

        if (bestIdx < 0) return null

        // Coordinates are already normalised [0, 1] from the onnx2tf export
        val cx = output[0][0][bestIdx]
        val cy = output[0][1][bestIdx]
        val w  = output[0][2][bestIdx]
        val h  = output[0][3][bestIdx]

        return BallDetection(
            boundingBox = RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f),
            confidence = bestConf,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
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
        kalman.reset()
        Log.d(TAG, "BallDetector reset")
    }

    /** Release the TFLite interpreter. Call when the detector is no longer needed. */
    fun close() {
        tflite.close()
    }
}
