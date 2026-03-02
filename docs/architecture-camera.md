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
| File                              | Purpose                                              |
|-----------------------------------|------------------------------------------------------|
| `detector/BallDetector.kt`        | Orchestrates inference, exposes detection + state; includes Kalman-predicted detections on skipped frames |
| `detector/BallKalmanFilter.kt`    | 2D Kalman filter for ball position interpolation     |
| `detector/DetectionState.kt`      | `IDLE` / `DETECTING` / `CONFIRMED` enum              |
| `detector/TFLiteInferenceWrapper.kt` | TF Lite `Interpreter` wrapper with GPU delegate + CPU fallback |
| `detector/TrajectoryTracker.kt`   | Ring buffer of recent ball positions; velocity helpers |
| `detector/ShotAnalyzer.kt`        | Shot detection state machine; arc + confidence scoring |
| `HoopPreferences.kt`              | DataStore persistence for hoop region (normalized coords) |

### Flow

```
ImageProxy (from CameraX ImageAnalysis)
    │
    ▼
BallDetector.analyzeFrame()
    ├── frameCount check (skip every other frame; FRAME_SKIP=2)
    │       on skipped frames: Kalman filter predicts position → emits predicted BallDetection (conf=0)
    ├── toBitmap() → resize 640×640 → normalize [0,1]
    ├── TFLiteInferenceWrapper.run()  (GPU delegate preferred, CPU fallback)
    │       model: assets/ml/yolo11n.tflite
    │       input: [1, 640, 640, 3] float32
    │       output: [1, 5, 8400] float32  (cx, cy, w, h, conf — normalized)
    ├── parseBestDetection()
    │       scans 8400 anchors for conf > 0.3
    │       returns BallDetection(boundingBox: RectF, confidence: Float)
    │       boundingBox is in normalized [0,1] coordinates
    ├── Kalman filter: correct() with measured position (or predict() if no detection)
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
| `FRAME_SKIP`             | 2     | Analyze every 2nd frame                 |
| `DETECTION_COOLDOWN_MS`  | 2000  | Min ms between CONFIRMED events         |

### State Exposed to UI
| StateFlow             | Type               | Consumer                              |
|-----------------------|--------------------|---------------------------------------|
| `detection`           | `BallDetection?`   | `SessionScreen` (debug bounding box)  |
| `detectionState`      | `DetectionState`   | `SessionViewModel` (for UI state)     |
| `inferenceTimeMs`     | `Float`            | `SessionScreen` (debug overlay badge) |

### TFLiteInferenceWrapper
A wrapper around `org.tensorflow.lite.Interpreter` with GPU acceleration:
- Loads model from `assets/` via memory-mapped file
- Tries `GpuDelegate()` first for 2–5× faster inference; falls back to CPU if unavailable
- Exposes `isUsingGpu: Boolean` for diagnostics
- Exposes `run(input, output)` and `runForMultipleInputsOutputs`
- Default: 2 CPU threads (used as fallback or alongside GPU)
- Must call `close()` when done (releases both interpreter and GPU delegate)

---

## Trajectory Tracker

`detector/TrajectoryTracker.kt` maintains a ring buffer of recent `BallPosition` values, giving the shot analyzer a time-series view of the ball's movement.

```kotlin
data class BallPosition(
    val centerX: Float,          // normalized [0,1]
    val centerY: Float,          // normalized [0,1] — increases downward
    val boundingBox: RectF,      // normalized [0,1]
    val confidence: Float,
    val timestampMs: Long
)
```

### API
| Method / Property             | Description                                              |
|-------------------------------|----------------------------------------------------------|
| `add(detection: BallDetection)` | Push new position into the ring buffer                 |
| `recentPositions(withinMs)`   | Returns positions within the last N milliseconds         |
| `verticalVelocity(): Float?`  | Positive = moving down; looks back 500ms                 |
| `latestPosition: BallPosition?` | Most recent position                                   |
| `clear()`                     | Reset buffer (called on session start)                   |

Buffer size: 30 positions. At FRAME_SKIP=2 with Kalman interpolation on skipped frames, the tracker receives ~30 positions/sec at 30fps camera, covering ~1 second of ball trajectory. Kalman-predicted positions have `confidence = 0f` to distinguish them from real detections.

---

## Kalman Filter

`detector/BallKalmanFilter.kt` provides position interpolation between detection frames.

- **State vector:** `[x, y, vx, vy]` (position + velocity in normalized [0,1] coords)
- **On detection frames:** `predict()` then `correct(measuredX, measuredY)`
- **On skipped frames:** `predict()` only → emits estimated position as `BallDetection` with `confidence = 0`
- Reset on session end via `BallDetector.reset()`

This fills trajectory gaps so `TrajectoryTracker` and `ShotAnalyzer` see smooth, dense position data even when inference only runs on every other frame.

---

## Shot Analyzer

`detector/ShotAnalyzer.kt` implements the shot detection state machine. It is called per analyzed frame from `SessionViewModel` after the trajectory tracker is updated.

### Detection Criteria
1. **Overlap**: ball bounding box overlaps hoop rect by ≥ 25%
2. **Direction**: `verticalVelocity()` is positive (ball moving downward)
3. **Arc check**: ball was above `hoop.top` within the last 1500ms (came from above)

All three must be satisfied. On detection, a `ShotEvent` is returned and a 1500ms cooldown begins.

### Confidence Scoring
- `overlapFraction`: how much of the hoop rect is covered by the ball bbox (0–1)
- `arcQuality`: 1.0 if arc peak was above hoop top, else 0.5
- `velocityFactor`: clamped to [0, 1] from vertical velocity
- `initialConfidence = overlapFraction * 0.5 + arcQuality * 0.3 + velocityFactor * 0.2`

### ShotEvent
```kotlin
data class ShotEvent(
    val positions: List<BallPosition>,   // trajectory snapshot at detection time
    val entryOverlap: Float,             // overlap fraction [0,1]
    val entryVelocity: Float,            // downward vertical velocity
    val hadProperArc: Boolean,           // ball came from above hoop
    val initialConfidence: Float,        // 0–1 confidence score
    val timestampMs: Long
)
```

---

## Hoop Preferences

`HoopPreferences.kt` persists the user-calibrated hoop region using DataStore (separate from `DetectionPreferences`).

```kotlin
data class HoopRegion(
    val centerX: Float,    // normalized [0,1]
    val centerY: Float,    // normalized [0,1]
    val width: Float,      // normalized, default 0.12
    val height: Float      // normalized, default 0.06
) {
    val rect: RectF  // derived bounding rectangle
}
```

The DataStore property is `Context.hoopDataStore` (name: `"hoop_settings"`) to avoid collision with `DetectionPreferences`'s `Context.dataStore`.

### HoopCalibrationOverlay (SessionScreen.kt)
- Activated via "GPS" toolbar button in `SessionScreen`
- Drag gesture to reposition the orange ring
- Two sliders for width and height adjustment
- Save/Cancel buttons — Save commits to DataStore, Cancel reverts
- When not calibrating, `HoopOverlay` draws the saved ring as a persistent translucent overlay

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
    │       │       includes both real detections AND Kalman-predicted ones (conf=0)
    │       ├── .detectionState → collected into uiState.detectionState
    │       └── .inferenceTimeMs → exposed as val inferenceTimeMs (debug overlay)
    │
    ├── trajectoryTracker: TrajectoryTracker (created in constructor)
    │       └── updated on every non-null BallDetection (real + predicted)
    │
    ├── shotAnalyzer: ShotAnalyzer (created in constructor)
    │       └── .analyze(trajectory, hoop) → ShotEvent? on each frame
    │
    ├── hoopPreferences: HoopPreferences (@Inject)
    │       └── .hoopRegion → collected into uiState.hoopRegion
    │
    ├── captureController: TrainingCaptureController (created in constructor)
    │       ├── .isRecording → collected into uiState.isRecording
    │       └── passed to CameraPreview to bind capture use cases
    │
    ├── onFrameAnalyzed(ImageProxy)
    │       ├── ballDetector.analyzeFrame()
    │       │       on inference frames: run model → Kalman correct → emit real detection
    │       │       on skipped frames: Kalman predict → emit predicted detection (conf=0)
    │       ├── trajectoryTracker.add(detection)
    │       └── shotAnalyzer.analyze() → onShotDetected()
    │
    ├── onShotDetected(ShotEvent)
    │       ├── incrementMissed()       (conservative default)
    │       ├── show snackbar feedback
    │       └── launch pendingOutcomeJob (600ms window)
    │               ball below hoop + disappeared → incrementMade()
    │               ball reverses upward          → keep as missed
    │               else                          → AMBIGUOUS (keep as missed)
    │
    ├── captureFrame() → captureController.captureFrame() → CaptureRepository.insert()
    └── toggleRecording() → start/stop → CaptureRepository.insert() on finish
```

### Key Constants (SessionViewModel)
| Constant                    | Value  | Purpose                                          |
|-----------------------------|--------|--------------------------------------------------|
| `OUTCOME_WINDOW_MS`         | 600    | Time to observe ball after shot detection        |
| `DISAPPEARANCE_THRESHOLD_MS`| 350    | Max gap without detection to count as disappeared |
