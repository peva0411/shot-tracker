# Camera & Detection — Sub-Architecture

> Part of: [Architecture Overview](./ARCHITECTURE.md)

## Package: `com.shottracker.camera`

This package owns everything related to the device camera, ML-based ball detection, and training data capture.

---

## CameraX Use Case Binding

`CameraPreview.kt` is the single Compose component that sets up CameraX. It binds up to **4 use cases** simultaneously to the back camera:

```
CameraProvider.bindToLifecycle(
    lifecycleOwner, backCamera,
    Preview,                                   // always
    ImageAnalysis,                             // always (feeds BallDetector)
    ImageCapture,                              // when captureController != null
    VideoCapture<Recorder>                     // when captureController != null
)
```

### Parameters
| Param              | Type                         | Purpose                            |
|--------------------|------------------------------|------------------------------------|
| `modifier`         | `Modifier`                   | Layout modifier                    |
| `captureController`| `TrainingCaptureController?` | If non-null, binds capture use cases |
| `onFrameAnalyzed`  | `(ImageProxy) -> Unit`       | Called for every analysis frame    |

### Image Analysis Config
- Resolution: 1280×720
- Backpressure: `STRATEGY_KEEP_ONLY_LATEST`
- Executor: single-thread pool
- Frames are closed after `onFrameAnalyzed` returns

---

## Ball Detection Pipeline

### Files
| File                       | Purpose                                           |
|----------------------------|---------------------------------------------------|
| `detector/BallDetector.kt` | Orchestrates inference, exposes detection + state  |
| `detector/DetectionState.kt` | `IDLE` / `DETECTING` / `CONFIRMED` enum         |
| `detector/TFLiteInferenceWrapper.kt` | Thin wrapper around TF Lite `Interpreter` |

### Flow

```
ImageProxy (from CameraX ImageAnalysis)
    │
    ▼
BallDetector.analyzeFrame()
    ├── frameCount check (skip 2 of every 3 frames)
    ├── toBitmap() → resize 640×640 → normalize [0,1]
    ├── TFLiteInferenceWrapper.run()
    │       model: assets/ml/yolov8n.tflite
    │       input: [1, 640, 640, 3] float32
    │       output: [1, 5, 8400] float32
    ├── parseBestDetection()
    │       scans 8400 anchors for conf > 0.3
    │       returns BallDetection(boundingBox: RectF, confidence: Float)
    │       boundingBox is in normalized [0,1] coordinates
    └── state update:
            conf ≥ 0.5 + cooldown → CONFIRMED
            conf ≥ 0.3           → DETECTING
            no detection         → IDLE
```

### Key Constants
| Constant                 | Value | Meaning                                 |
|--------------------------|-------|-----------------------------------------|
| `INPUT_SIZE`             | 640   | YOLO input dimension                    |
| `CONFIDENCE_THRESHOLD`   | 0.3   | Min confidence to report DETECTING      |
| `SHOT_CONFIDENCE_THRESHOLD` | 0.5 | Min confidence to fire CONFIRMED       |
| `FRAME_SKIP`             | 3     | Analyze every 3rd frame                 |
| `DETECTION_COOLDOWN_MS`  | 2000  | Min ms between CONFIRMED events         |

### State Exposed to UI
| StateFlow             | Type               | Consumer                              |
|-----------------------|--------------------|---------------------------------------|
| `detection`           | `BallDetection?`   | `SessionScreen` (debug bounding box)  |
| `detectionState`      | `DetectionState`   | `SessionViewModel` (for UI state)     |

### TFLiteInferenceWrapper
A minimal wrapper around `org.tensorflow.lite.Interpreter`:
- Loads model from `assets/` via memory-mapped file
- Exposes `run(input, output)` and `runForMultipleInputsOutputs`
- Default: 2 threads
- Must call `close()` when done

---

## Training Capture Controller

`TrainingCaptureController.kt` wraps CameraX `ImageCapture` and `VideoCapture<Recorder>` use cases. It is **not** Hilt-managed — created by `SessionViewModel` and lives for the session duration.

### API
| Method           | Input                     | Output                                 |
|------------------|---------------------------|----------------------------------------|
| `captureFrame()` | `onSaved: (File) -> Unit, onError: (String) -> Unit` | Saves JPEG to `filesDir/training/` |
| `startRecording()` | `onFinished: (File) -> Unit, onError: (String) -> Unit` | Saves MP4; records with audio if `RECORD_AUDIO` granted |
| `stopRecording()` | —                         | Stops active recording                 |

### State
| StateFlow      | Type      | Meaning              |
|----------------|-----------|----------------------|
| `isRecording`  | `Boolean` | True while recording |

### File Storage
- Directory: `context.filesDir/training/` (app-private, survives app restarts, not user-visible)
- Naming: `frame_yyyyMMdd_HHmmss_SSS.jpg` / `video_yyyyMMdd_HHmmss_SSS.mp4`

---

## Camera Permission Handling

`CameraPermissionHandler.kt` provides:
- `CameraPermissionHandler` object — static `hasPermission()` check
- `rememberCameraPermissionState()` — composable that returns `CameraPermissionState` with:
  - `hasPermission: Boolean`
  - `shouldShowRationale: Boolean`
  - `requestPermission: () -> Unit`
  - `openSettings: () -> Unit`

Used by `SessionScreen` to gate the camera preview.

`RECORD_AUDIO` is requested separately in `SessionScreen` via an `ActivityResultContracts.RequestPermission()` launcher triggered on first record button tap.

---

## Feedback Manager

`FeedbackManager.kt` (`@Singleton`) provides haptic vibration and sound feedback. Currently **not actively used** — it was previously wired to auto-shot-detection. Still registered in Hilt and available for future use.

---

## SessionViewModel ↔ Camera Integration

```
SessionViewModel
    ├── ballDetector: BallDetector (created in constructor)
    │       ├── .detection → exposed as val detection (for debug overlay)
    │       └── .detectionState → collected into uiState.detectionState
    │
    ├── captureController: TrainingCaptureController (created in constructor)
    │       ├── .isRecording → collected into uiState.isRecording
    │       └── passed to CameraPreview to bind capture use cases
    │
    ├── onFrameAnalyzed(ImageProxy) → ballDetector.analyzeFrame()
    ├── captureFrame() → captureController.captureFrame() → CaptureRepository.insert()
    └── toggleRecording() → start/stop → CaptureRepository.insert() on finish
```
