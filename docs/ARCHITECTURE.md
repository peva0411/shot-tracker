# Shot Tracker — Architecture Overview

> **Last updated:** March 2026
> Sub-architecture docs: [Camera & Detection](./architecture-camera.md) · [Data & Upload Pipeline](./architecture-data.md)

## What This App Does

An Android app for basketball shot tracking. Users open a camera session, point the phone at the hoop, and the app auto-detects shots using a YOLO-based ball detector and trajectory analysis. Counts can also be adjusted manually via +/− buttons at any time. Optionally, users can capture frames (JPEG) and video clips (MP4) as training data for improving the ML model, stored locally and uploadable to Google Drive.

The shot detection pipeline: ball detector → trajectory ring buffer → hoop-intersection check → downward arc + confidence scoring → post-shot outcome window (made/missed/ambiguous) → persisted to Room DB with trajectory JSON.

---

## Technology Stack

| Area              | Technology                                     |
|-------------------|------------------------------------------------|
| Language          | Kotlin 100%                                    |
| Min / Target SDK  | API 26 (Android 8.0) / API 34 (Android 14)    |
| Build             | Gradle 8.2.2 + Kotlin DSL, KSP                |
| UI                | Jetpack Compose + Material 3                   |
| Camera            | CameraX (camera2, lifecycle, view, video)      |
| ML Inference      | TensorFlow Lite 2.14 + Support Library + GPU Delegate |
| Database          | Room 2.6.1                                     |
| DI                | Hilt (Dagger) 2.50                             |
| Navigation        | Jetpack Compose Navigation 2.7.6              |
| Async             | Kotlin Coroutines + StateFlow                  |
| Image Loading     | Coil 2.5                                       |
| Cloud             | Google Sign-In + Google Drive REST API v3      |

---

## Project Layout

```
shot-tracker/
├── app/
│   ├── build.gradle.kts              # App-level dependencies & config
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/ml/
│       │   ├── yolo11n.tflite        # YOLO11n TFLite model (ball detection)
│       │   └── coco_labels.txt       # Class labels
│       ├── java/com/shottracker/
│       │   ├── MainActivity.kt       # Single-activity entry point (@AndroidEntryPoint)
│       │   ├── Navigation.kt         # Nav graph: Screen sealed class + NavHost
│       │   ├── ShotTrackerApplication.kt  # @HiltAndroidApp
│       │   │
│       │   ├── camera/               # ← see architecture-camera.md
│       │   │   ├── CameraPermissionHandler.kt
│       │   │   ├── CameraPreview.kt
│       │   │   ├── HoopPreferences.kt        # DataStore: hoop region (normalized coords)
│       │   │   ├── TrainingCaptureController.kt
│       │   │   ├── detector/
│       │   │   │   ├── BallDetector.kt
│       │   │   │   ├── BallKalmanFilter.kt   # Kalman filter for position interpolation
│       │   │   │   ├── DetectionState.kt
│       │   │   │   ├── ShotAnalyzer.kt       # Shot detection state machine
│       │   │   │   ├── TrajectoryTracker.kt  # Ball position ring buffer
│       │   │   │   └── TFLiteInferenceWrapper.kt
│       │   │   └── feedback/
│       │   │       └── FeedbackManager.kt
│       │   │
│       │   ├── data/
│       │   │   └── DetectionPreferences.kt   # DataStore: confidence threshold
│       │   │
│       │   ├── media/                # ← see architecture-data.md
│       │   │   ├── AppDatabase.kt
│       │   │   ├── CaptureDao.kt
│       │   │   ├── CaptureEntity.kt
│       │   │   ├── CaptureRepository.kt
│       │   │   ├── DatabaseModule.kt
│       │   │   ├── ShotDao.kt
│       │   │   ├── ShotEntity.kt             # Per-shot trajectory snapshot
│       │   │   └── ShotRepository.kt
│       │   │
│       │   ├── drive/                # ← see architecture-data.md
│       │   │   ├── GoogleDriveService.kt
│       │   │   └── DriveModule.kt
│       │   │
│       │   └── ui/
│       │       ├── home/HomeScreen.kt
│       │       ├── session/
│       │       │   ├── SessionScreen.kt
│       │       │   └── SessionViewModel.kt
│       │       ├── summary/SummaryScreen.kt
│       │       ├── history/HistoryScreen.kt
│       │       ├── library/
│       │       │   ├── LibraryScreen.kt
│       │       │   └── LibraryViewModel.kt
│       │       └── theme/
│       │           ├── Color.kt
│       │           ├── Theme.kt
│       │           └── Type.kt
│       └── res/
│           ├── values/{colors,strings,themes}.xml
│           └── mipmap-*/             # Launcher icons
│
├── build.gradle.kts                  # Root plugins: AGP 8.2.2, Kotlin 1.9.22, KSP, Hilt
├── settings.gradle.kts
├── gradle.properties
├── docs/                             # Documentation (this file + sub-docs)
├── plans/                            # PRD, implementation plans
├── datasets/                         # Training image datasets (gitignored: *.pt, *.onnx)
├── test-media/                       # Test images for ML experiments
└── *.pt / *.onnx                     # YOLO model weights (gitignored)
```

---

## Architecture Pattern

**MVVM** with unidirectional data flow. State flows downward via `StateFlow`, events flow upward via ViewModel function calls.

```
┌─────────────────────────────────────────────────────────┐
│  Composable Screen                                      │
│  (collects StateFlow, calls ViewModel functions)        │
└──────────────────────┬──────────────────────────────────┘
                       │ events ↑   state ↓
┌──────────────────────▼──────────────────────────────────┐
│  ViewModel (@HiltViewModel)                             │
│  (owns MutableStateFlow, orchestrates logic)            │
└──────────────────────┬──────────────────────────────────┘
                       │
          ┌────────────┼──────────────┬──────────────┐
          ▼            ▼              ▼              ▼
   BallDetector  CaptureRepo    ShotRepo       DriveService
   (camera/ML)   (Room DB)    (Room DB)       (Google API)
        │
        ▼
  TrajectoryTracker → ShotAnalyzer
```

---

## Navigation Graph

```
                ┌──────────┐
          ┌────►│   Home   │◄────┐
          │     └────┬─┬─┬─┘    │
          │          │ │ │      │
          │   ┌──────┘ │ └───┐  │
          │   ▼        ▼     ▼  │
          │ Session  History Library
          │   │
          │   ▼
          │ Summary
          │   │
          └───┘
```

| Route                | Screen         | ViewModel          | Purpose                                      |
|----------------------|----------------|--------------------|----------------------------------------------|
| `home`               | HomeScreen     | —                  | Start session, view history, open library     |
| `session`            | SessionScreen  | SessionViewModel   | Live camera, +/− counting, capture media      |
| `summary/{sessionId}`| SummaryScreen  | —                  | Post-session stats (placeholder)              |
| `history`            | HistoryScreen  | —                  | Past sessions list (placeholder)              |
| `library`            | LibraryScreen  | LibraryViewModel   | Grid of captured media, upload to Drive       |

---

## Dependency Injection (Hilt)

| Component                  | Scope        | Provided by       |
|----------------------------|--------------|--------------------|
| `FeedbackManager`          | @Singleton   | Self (`@Inject constructor`) |
| `AppDatabase`              | @Singleton   | `DatabaseModule`   |
| `CaptureDao`               | Unscoped     | `DatabaseModule`   |
| `ShotDao`                  | Unscoped     | `DatabaseModule`   |
| `CaptureRepository`        | @Singleton   | Self (`@Inject constructor`) |
| `ShotRepository`           | @Singleton   | Self (`@Inject constructor`) |
| `DetectionPreferences`     | @Singleton   | Self (`@Inject constructor`) |
| `HoopPreferences`          | @Singleton   | Self (`@Inject constructor`) |
| `GoogleDriveService`       | @Singleton   | Self (`@Inject constructor`) |
| `SessionViewModel`         | ViewModel    | Hilt auto         |
| `LibraryViewModel`         | ViewModel    | Hilt auto         |

**Note:** `BallDetector`, `BallKalmanFilter`, `TrajectoryTracker`, `ShotAnalyzer`, and `TrainingCaptureController` are **not** Hilt-managed — they are created directly in `SessionViewModel` because their lifecycle is tied to the session.

---

## Permissions

| Permission       | Type      | When Requested                          |
|------------------|-----------|-----------------------------------------|
| `CAMERA`         | Runtime   | On entering SessionScreen               |
| `RECORD_AUDIO`   | Runtime   | On first tap of record button           |
| `INTERNET`       | Normal    | Always (for Google Drive uploads)       |
| `WAKE_LOCK`      | Normal    | Always (keep screen on during session)  |
| `VIBRATE`        | Normal    | Always (haptic feedback)                |

---

## Build Variants

| Variant  | Application ID           | Notes                                   |
|----------|---------------------------|-----------------------------------------|
| debug    | `com.shottracker.debug`   | Debug suffix, no minification           |
| release  | `com.shottracker`         | ProGuard minification enabled           |

**Build command:** `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew assembleDebug`
**Install on device:** `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew installDebug`

---

## Key Design Decisions

1. **Auto shot detection with manual override** — The YOLO11n ball detector feeds a trajectory tracker and hoop-intersection analyzer. Shots are auto-counted; manual +/− buttons remain for corrections. An auto-detect toggle lets users disable it entirely.
2. **Manual hoop calibration** — User places an orange ring over the hoop before shooting. The region is persisted in DataStore. YOLO-based hoop detection is planned once the model is retrained.
3. **Shot outcome resolution window** — On shot detection, a 600ms coroutine window observes the ball trajectory: disappearance below hoop → MADE; upward reversal → MISSED; otherwise → AMBIGUOUS.
4. **No domain/use-case layer** — The app is simple enough that ViewModels call repositories directly. A domain layer can be introduced when business logic grows.
5. **Local-first media** — Frames/videos save to app-private storage (`context.filesDir/training/`). Upload to Drive is manual from the Library screen.
6. **Room for upload and shot state** — `CaptureEntity` tracks upload status per file; `ShotEntity` stores per-shot trajectory JSON and outcome for offline analysis.
7. **OAuth2 for Drive** — Uses `play-services-auth` + Drive REST API. Requires Google Cloud Console setup (Drive API enabled + Android OAuth client ID with SHA-1).

---

## Incomplete / Placeholder Screens

- **HistoryScreen** — Shows "No sessions yet" stub. No Room persistence for sessions exists yet.
- **SummaryScreen** — Displays hardcoded zeros. Not connected to `SessionViewModel` result data.
- **FeedbackManager** — Wired in Hilt but not used. Available for re-enabling haptic feedback on shot detection.
