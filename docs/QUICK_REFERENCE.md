# Developer Quick Reference

## Common Commands

### Build & Run
```bash
# Build the project
./gradlew build

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Install on connected device
./gradlew installDebug

# Run tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest

# Clean build
./gradlew clean
```

### Code Quality
```bash
# Run lint
./gradlew lint

# Generate lint report
./gradlew lintDebug

# Check for dependency updates
./gradlew dependencyUpdates
```

## Project Structure Quick Reference

```
com.shottracker/
├── MainActivity.kt              # Single activity host
├── Navigation.kt                # Navigation graph
├── ShotTrackerApplication.kt    # Hilt application
├── ui/
│   ├── home/                    # Home screen
│   ├── session/                 # Active session
│   ├── summary/                 # Session results
│   ├── history/                 # Past sessions
│   └── theme/                   # App theme
├── domain/
│   ├── model/                   # Business models
│   ├── repository/              # Repository interfaces
│   └── usecase/                 # Business logic
├── data/
│   ├── local/                   # Room database
│   └── repository/              # Repository implementations
└── camera/
    ├── detector/                # Shot detection
    └── processor/               # Image processing
```

## Adding a New Screen

1. Create composable in `ui/[screen-name]/`
2. Add route to `Navigation.kt`
3. Add navigation actions
4. Create ViewModel if needed
5. Update navigation graph

## Adding a Database Entity

1. Create entity in `data/local/entity/`
2. Create DAO in `data/local/dao/`
3. Update database class
4. Create migration if needed
5. Implement repository

## Adding a Use Case

1. Create use case in `domain/usecase/`
2. Define interface if needed
3. Inject repository dependencies
4. Add to ViewModel

## Hilt Modules

To be created as needed:
- `DatabaseModule` - Room database
- `RepositoryModule` - Repository bindings
- `CameraModule` - Camera components
- `NetworkModule` - Future cloud sync

## Useful Resources

- [Compose Documentation](https://developer.android.com/jetpack/compose)
- [CameraX Guide](https://developer.android.com/training/camerax)
- [Room Documentation](https://developer.android.com/training/data-storage/room)
- [Hilt Documentation](https://dagger.dev/hilt/)
- [OpenCV Android](https://opencv.org/android/)

## Troubleshooting

### Gradle Sync Issues
1. File → Invalidate Caches → Restart
2. Delete `.gradle` folder
3. Run `./gradlew clean`

### Build Errors
1. Check Android SDK installation
2. Verify JDK version (17+)
3. Update Gradle if needed
4. Check ProGuard rules

### Camera Issues (Phase 2+)
1. Verify camera permission granted
2. Check physical device (not emulator)
3. Verify camera hardware available
4. Check manifest permissions

## Code Style

- Use Kotlin official style guide
- Follow Material Design 3 guidelines
- Keep composables small and focused
- Use meaningful variable names
- Document complex logic with comments
