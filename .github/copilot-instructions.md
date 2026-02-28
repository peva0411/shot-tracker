# Shot Tracker — Copilot Custom Instructions

## Architecture Reference

Before making changes, read the architecture documentation:

- **[docs/ARCHITECTURE.md](../docs/ARCHITECTURE.md)** — Overall structure, tech stack, navigation graph, DI setup, build instructions, and key design decisions.
- **[docs/architecture-camera.md](../docs/architecture-camera.md)** — CameraX use case binding, YOLO ball detection pipeline, training data capture controller, permission handling.
- **[docs/architecture-data.md](../docs/architecture-data.md)** — Room database schema, capture repository, Google Drive upload service, library screen data flow.

## Project Essentials

- **Language:** Kotlin only. No Java source files.
- **Package:** `com.shottracker` (debug variant: `com.shottracker.debug`)
- **Architecture:** MVVM with Jetpack Compose, StateFlow, Hilt DI
- **Build:** `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew assembleDebug`
- **Install:** `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew installDebug`
- **Source root:** `app/src/main/java/com/shottracker/`

## Key Conventions

1. **UI state** is a single `data class` (e.g., `SessionUiState`) exposed as `StateFlow` from the ViewModel. Never expose `MutableStateFlow` to the UI.
2. **Navigation** is defined in `Navigation.kt` via the `Screen` sealed class. Add new screens there.
3. **Hilt modules** live alongside the code they provide: `media/DatabaseModule.kt`, `drive/DriveModule.kt`.
4. **Camera objects** (`BallDetector`, `TrainingCaptureController`) are created directly in ViewModels, not Hilt-managed, because their lifecycle is tied to the session.
5. **Runtime permissions** (CAMERA, RECORD_AUDIO) must be requested before use. See `CameraPermissionHandler.kt` for the camera pattern.
6. **ML model** is at `assets/ml/yolov8n.tflite`. Input: `[1, 640, 640, 3]` float32. Output: `[1, 5, 8400]` float32 (YOLO anchors).
7. **Captured media** goes to `context.filesDir/training/` (app-private). Upload status tracked in Room.

## What's Placeholder / Incomplete

- `HistoryScreen` — static "No sessions yet" stub. No session persistence exists.
- `SummaryScreen` — hardcoded zeros. Not connected to actual session data.
- `FeedbackManager` — fully implemented but unused. Was for auto-detection haptics.

## Things to Watch Out For

- The debug build variant has `applicationIdSuffix = ".debug"` — Google OAuth client IDs must match this package name.
- `packaging.resources.excludes` in `build.gradle.kts` is needed to avoid META-INF conflicts from Google API JARs.
- CameraX can bind at most 4 use cases concurrently; the current setup uses all 4 (Preview, ImageAnalysis, ImageCapture, VideoCapture).
- TFLite model files must have `noCompress += "tflite"` in `androidResources` block.
