package com.shottracker.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "TrainingCaptureController"

/**
 * Controls training data capture (JPEG frames and MP4 videos) using CameraX use cases.
 *
 * Obtain [imageCapture] and [videoCapture] and bind them to the camera alongside
 * the preview and analysis use cases. Then call [captureFrame] / [startRecording] /
 * [stopRecording] from the UI.
 */
class TrainingCaptureController(private val context: Context) {

    val imageCapture: ImageCapture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .build()

    private val recorder: Recorder = Recorder.Builder()
        .setQualitySelector(QualitySelector.from(Quality.HD))
        .build()

    val videoCapture: VideoCapture<Recorder> = VideoCapture.withOutput(recorder)

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private var activeRecording: Recording? = null

    private val captureDir: File get() = File(context.filesDir, "training").also { it.mkdirs() }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())

    /**
     * Capture a single JPEG frame. Returns the saved [File] via [onSaved], or
     * calls [onError] on failure.
     */
    fun captureFrame(onSaved: (File) -> Unit, onError: (String) -> Unit) {
        val file = File(captureDir, "frame_${timestamp()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Log.d(TAG, "Frame saved: ${file.absolutePath}")
                    onSaved(file)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Frame capture failed", exception)
                    onError(exception.message ?: "Capture failed")
                }
            }
        )
    }

    /**
     * Start recording a video. No-op if already recording.
     * Records with audio only if RECORD_AUDIO permission is granted.
     * [onFinished] is called with the saved [File] when recording is stopped.
     */
    fun startRecording(onFinished: (File) -> Unit, onError: (String) -> Unit) {
        if (_isRecording.value) return
        val file = File(captureDir, "video_${timestamp()}.mp4")
        val outputOptions = FileOutputOptions.Builder(file).build()

        val hasAudio = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        val pendingRecording = recorder.prepareRecording(context, outputOptions)
        val startedRecording = if (hasAudio) pendingRecording.withAudioEnabled() else pendingRecording

        activeRecording = startedRecording
            .start(ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        _isRecording.value = true
                        Log.d(TAG, "Recording started (audio=$hasAudio): ${file.absolutePath}")
                    }
                    is VideoRecordEvent.Finalize -> {
                        _isRecording.value = false
                        if (event.hasError()) {
                            Log.e(TAG, "Recording error: ${event.error}")
                            onError("Recording failed (error ${event.error})")
                        } else {
                            Log.d(TAG, "Recording saved: ${file.absolutePath}")
                            onFinished(file)
                        }
                        activeRecording = null
                    }
                    else -> Unit
                }
            }
    }

    /** Stop an active recording. No-op if not recording. */
    fun stopRecording() {
        activeRecording?.stop()
    }
}
