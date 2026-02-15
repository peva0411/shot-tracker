# Basketball Shot Tracker

An Android app that automatically tracks basketball shots using camera-based motion detection.

## Project Structure

```
app/
├── src/main/
│   ├── java/com/shottracker/
│   │   ├── ui/
│   │   │   ├── home/        # Home screen
│   │   │   ├── session/     # Active session with camera
│   │   │   ├── summary/     # Session summary
│   │   │   ├── history/     # Past sessions list
│   │   │   └── theme/       # Compose theme
│   │   ├── domain/
│   │   │   ├── model/       # Data models
│   │   │   ├── repository/  # Data layer abstraction
│   │   │   └── usecase/     # Business logic
│   │   ├── data/
│   │   │   ├── local/       # Room database
│   │   │   └── repository/  # Repository implementations
│   │   ├── camera/
│   │   │   ├── detector/    # Shot detection logic
│   │   │   └── processor/   # Image processing
│   │   ├── MainActivity.kt
│   │   ├── Navigation.kt
│   │   └── ShotTrackerApplication.kt
│   ├── res/                 # Resources
│   └── AndroidManifest.xml
└── build.gradle.kts
```

## Technology Stack

- **Language:** Kotlin 100%
- **Min SDK:** Android 8.0 (API 26)
- **Target SDK:** Android 14 (API 34)
- **Build System:** Gradle with Kotlin DSL

### Key Libraries
- **UI:** Jetpack Compose with Material 3
- **Camera:** CameraX API
- **Image Processing:** OpenCV Android SDK
- **Database:** Room Persistence Library
- **Architecture:** MVVM with ViewModel, StateFlow
- **DI:** Hilt (Dagger)
- **Async:** Kotlin Coroutines

## Setup Instructions

### Prerequisites
1. Android Studio (latest stable version)
2. JDK 17 or newer
3. Android SDK (API 26-34)

### Build Steps
1. Clone the repository
   ```bash
   git clone <repository-url>
   cd shot-tracker
   ```

2. Open the project in Android Studio
   - File → Open → Select the project folder

3. Sync Gradle dependencies
   - Click "Sync Project with Gradle Files" or wait for auto-sync

4. Download OpenCV Android SDK (when implementing Phase 2)
   - Download from: https://opencv.org/releases/
   - Extract and copy the AAR file to `app/libs/`
   - Uncomment the OpenCV dependency in `app/build.gradle.kts`

5. Run the app
   - Connect an Android device or start an emulator
   - Click Run or press Shift+F10

## Development Status

### Phase 1: Project Setup & Foundation ✅
- [x] Android project initialized
- [x] Gradle dependencies configured
- [x] Project structure created
- [x] ProGuard rules configured
- [x] .gitignore updated
- [x] Base architecture components created
- [x] Hilt dependency injection set up
- [x] Build variants configured
- [x] Permissions added to manifest
- [x] Basic navigation structure created

### Phase 2: Camera Integration (Next)
- [ ] CameraX preview display
- [ ] Camera permission handling
- [ ] OpenCV integration
- [ ] Motion detection algorithm
- [ ] Shot detection state machine

### Phase 3: Session Management
- [ ] Database schema design
- [ ] Room database implementation
- [ ] Session state management
- [ ] Active session UI

### Phase 4: Statistics & History
- [ ] Statistics calculations
- [ ] History screen
- [ ] Session summary screen

### Phase 5: Testing & Polish
- [ ] Unit tests
- [ ] UI tests
- [ ] Performance optimization
- [ ] Bug fixes

## Building for Release

```bash
./gradlew assembleRelease
```

The APK will be generated at: `app/build/outputs/apk/release/app-release.apk`

## Testing

Run unit tests:
```bash
./gradlew test
```

Run instrumentation tests:
```bash
./gradlew connectedAndroidTest
```

## License

[To be determined]

## Contact

[To be determined]
