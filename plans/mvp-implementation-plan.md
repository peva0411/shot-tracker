# Basketball Shot Tracker - MVP Implementation Plan

**Based on:** Product Requirements Document v1.0  
**Target:** Initial MVP Release  
**Last Updated:** February 15, 2026

---

## Executive Summary

This document outlines the technical implementation plan for the Basketball Shot Tracker MVP. The plan follows the PRD's vision of a simple, focused Android app that automatically tracks basketball shots using camera-based motion detection.

**Core MVP Scope:**
- Camera-based shot detection using motion tracking
- Session management (start/stop/track)
- Basic statistics and history
- Local data persistence

---

## Technology Stack

### Core Technologies
- **Language:** Kotlin (100%)
- **Min SDK:** Android 8.0 (API 26)
- **Target SDK:** Latest stable Android API
- **Build System:** Gradle with Kotlin DSL

### Key Libraries & Frameworks
- **UI Framework:** Jetpack Compose (modern, declarative UI)
- **Camera:** CameraX API (easier than Camera2, better abstraction)
- **Image Processing:** OpenCV Android SDK 4.x (motion detection)
- **Database:** Room Persistence Library (type-safe SQLite wrapper)
- **Architecture Components:** 
  - ViewModel (MVVM pattern)
  - LiveData/StateFlow
  - Lifecycle-aware components
- **Dependency Injection:** Hilt (simplified Dagger)
- **Coroutines:** For async operations

### Development Tools
- **IDE:** Android Studio (latest stable)
- **Version Control:** Git
- **Testing:** JUnit, Espresso, Compose UI Testing

---

## Architecture Overview

### MVVM Pattern Structure
```
app/
├── ui/
│   ├── home/           (Home screen - start session)
│   ├── session/        (Active session with camera)
│   ├── summary/        (Session summary)
│   └── history/        (Past sessions list)
├── domain/
│   ├── model/          (Data models)
│   ├── repository/     (Data layer abstraction)
│   └── usecase/        (Business logic)
├── data/
│   ├── local/          (Room database)
│   └── repository/     (Repository implementations)
└── camera/
    ├── detector/       (Shot detection logic)
    └── processor/      (Image processing)
```

### Data Flow
1. **UI Layer** (Compose) → ViewModel (state management)
2. **ViewModel** → Use Cases (business logic)
3. **Use Cases** → Repository (data operations)
4. **Repository** → Local Database (Room)

---

## Implementation Phases

## Phase 1: Project Setup & Foundation (Week 1-2)

### Workplan
- [ ] Initialize Android project with Kotlin
- [ ] Configure Gradle dependencies (Compose, CameraX, Room, Hilt)
- [ ] Set up project structure (packages, modules)
- [ ] Configure ProGuard/R8 rules for OpenCV
- [ ] Set up version control (.gitignore for Android)
- [ ] Create base architecture components (BaseViewModel, etc.)
- [ ] Set up dependency injection with Hilt
- [ ] Configure build variants (debug, release)
- [ ] Add required permissions to AndroidManifest (CAMERA)
- [ ] Create basic navigation structure (Compose Navigation)

### Deliverables
- Working Android project that compiles
- Basic app skeleton with navigation
- Dependencies integrated and verified
- Project structure documented

### Technical Notes
- Use Jetpack Compose BOM for version management
- CameraX version: 1.3.x or latest stable
- OpenCV Android SDK: download and add to libs/
- Room schema export location configured

---

## Phase 2: Camera Integration & Shot Detection (Week 3-4)

### Workplan
- [ ] Implement CameraX preview display
- [ ] Create camera permission handling flow
- [ ] Set up camera lifecycle management
- [ ] Integrate OpenCV for Android
- [ ] Implement motion detection algorithm
  - [ ] Frame differencing logic
  - [ ] Region of Interest (ROI) selection for hoop
  - [ ] Threshold tuning for motion sensitivity
- [ ] Create shot detection state machine (idle → detecting → confirmed)
- [ ] Add visual feedback for detected shots (flash, border)
- [ ] Add audio feedback (beep or ding sound)
- [ ] Implement manual calibration for hoop region
- [ ] Test on real device with basketball hoop

### Deliverables
- Working camera preview in app
- Basic motion detection functional
- Shot detection triggers with feedback
- Calibration UI for hoop positioning

### Technical Details

#### Motion Detection Algorithm
1. Capture frame from camera
2. Convert to grayscale
3. Apply Gaussian blur to reduce noise
4. Compare with previous frame (frame differencing)
5. Threshold the difference
6. Detect motion in ROI (hoop area)
7. If significant downward motion → trigger shot detection

#### Camera Configuration
- Resolution: 1280x720 (balance between quality and performance)
- Frame rate: 30 fps
- Focus mode: Continuous auto-focus
- Exposure: Auto

#### Performance Targets
- Frame processing: < 500ms
- Detection latency: < 1 second
- False positive rate: < 15%

---

## Phase 3: Session Management (Week 5-6)

### Workplan
- [ ] Design database schema (Session, Shot entities)
- [ ] Implement Room database setup
  - [ ] Create DAO interfaces
  - [ ] Define entities with relationships
  - [ ] Create database migrations
- [ ] Build session repository
- [ ] Implement session state management
  - [ ] Start session logic
  - [ ] Track makes/attempts in real-time
  - [ ] End session and save data
- [ ] Create session ViewModel
- [ ] Build Active Session UI screen
  - [ ] Camera preview (full screen)
  - [ ] Counter overlay (makes/attempts/percentage)
  - [ ] Manual adjustment buttons (+/-)
  - [ ] End session button
- [ ] Implement manual shot adjustment logic
- [ ] Add session duration tracking
- [ ] Implement auto-save on app backgrounding
- [ ] Handle edge cases (phone calls, screen rotation)

### Deliverables
- Complete session flow working
- Data persists to local database
- Manual controls functional
- Session state survives app lifecycle

### Database Schema

#### Session Table
```kotlin
@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,          // Unix timestamp
    val endTime: Long?,
    val shotsMade: Int,
    val shotsAttempted: Int,
    val durationSeconds: Int,
    val notes: String? = null
)
```

#### Shot Table (for future granular tracking)
```kotlin
@Entity(tableName = "shots")
data class Shot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,          // Foreign key
    val timestamp: Long,
    val isMade: Boolean,
    val isManual: Boolean         // Track if manually added
)
```

---

## Phase 4: Statistics & History (Week 7-8)

### Workplan
- [ ] Build Home Screen UI
  - [ ] "Start Session" prominent button
  - [ ] "View History" button
  - [ ] Today's stats summary widget
- [ ] Build Session Summary Screen
  - [ ] Display final percentage (large)
  - [ ] Show makes/attempts breakdown
  - [ ] Show session duration
  - [ ] "Save" and "Discard" options
- [ ] Build History Screen
  - [ ] List past sessions (RecyclerView or LazyColumn)
  - [ ] Display date, makes, attempts, percentage per item
  - [ ] Sort by date (newest first)
  - [ ] Pull to refresh
  - [ ] Tap to view session details
- [ ] Create statistics calculation logic
  - [ ] Current session stats (real-time)
  - [ ] Historical aggregations
  - [ ] Today's total stats
- [ ] Implement session details view
- [ ] Add session deletion capability
- [ ] Create export functionality (future: CSV/JSON)

### Deliverables
- All four main screens complete
- Navigation between screens working
- Statistics calculated correctly
- History persists and displays properly

### UI Components

#### Home Screen
```
┌─────────────────────────┐
│  Basketball Tracker     │
│                         │
│  Today's Stats          │
│  15/25 - 60%            │
│                         │
│  ┌─────────────────┐   │
│  │  START SESSION  │   │
│  └─────────────────┘   │
│                         │
│  ┌─────────────────┐   │
│  │  VIEW HISTORY   │   │
│  └─────────────────┘   │
└─────────────────────────┘
```

#### Active Session Screen
```
┌─────────────────────────┐
│ [Camera Preview]        │
│                         │
│         🏀              │
│                         │
│    15 / 25 - 60%       │
│                         │
│ [-]              [+]    │
│                         │
│   [End Session]         │
└─────────────────────────┘
```

---

## Phase 5: Testing, Polish & Optimization (Week 9-10)

### Workplan
- [ ] Write unit tests for business logic
  - [ ] Session management use cases
  - [ ] Statistics calculations
  - [ ] Repository operations
- [ ] Write instrumentation tests
  - [ ] Database operations (Room)
  - [ ] UI flows (Compose testing)
- [ ] Perform end-to-end testing
  - [ ] Complete session workflows
  - [ ] Edge cases (interruptions, low battery)
- [ ] Test on multiple devices
  - [ ] Different screen sizes
  - [ ] Different Android versions (API 26+)
  - [ ] Different camera hardware
- [ ] Performance optimization
  - [ ] Profile battery usage
  - [ ] Optimize image processing pipeline
  - [ ] Reduce memory allocations
  - [ ] Test 30-minute session stability
- [ ] UI/UX polish
  - [ ] Smooth animations
  - [ ] Proper loading states
  - [ ] Error handling with user-friendly messages
  - [ ] Accessibility improvements
- [ ] Bug fixing from testing
- [ ] Code cleanup and documentation
- [ ] Generate release APK
- [ ] Create user documentation

### Deliverables
- Test coverage > 70% for business logic
- Stable app on 3+ devices
- Release-ready APK
- User guide document

### Testing Checklist

#### Functional Testing
- [ ] Session starts and camera displays
- [ ] Shot detection triggers correctly
- [ ] Manual buttons add/subtract counts
- [ ] Session ends and saves data
- [ ] Statistics calculate correctly
- [ ] History displays all sessions
- [ ] App survives phone call interruption
- [ ] App survives screen rotation
- [ ] App survives low memory condition

#### Performance Testing
- [ ] App launches in < 3 seconds
- [ ] Camera preview latency < 100ms
- [ ] 30-minute session without crash
- [ ] Battery drain < 25% per 30 minutes
- [ ] APK size < 30MB

#### Compatibility Testing
- [ ] Works on Android 8.0 (API 26)
- [ ] Works on Android 14+ (latest)
- [ ] Small screen (5" phone)
- [ ] Large screen (6.5"+ phone)
- [ ] Different aspect ratios

---

## Technical Implementation Details

### Shot Detection Algorithm (Detailed)

#### Motion Detection Approach
```kotlin
class ShotDetector(context: Context) {
    private var previousFrame: Mat? = null
    private val roiRect: Rect  // Region of Interest for hoop
    
    fun detectShot(currentFrame: Mat): Boolean {
        val gray = convertToGrayscale(currentFrame)
        val blurred = applyGaussianBlur(gray)
        
        previousFrame?.let { prev ->
            val diff = frameDifference(prev, blurred)
            val motion = detectMotionInROI(diff, roiRect)
            
            if (motion > MOTION_THRESHOLD) {
                return isDownwardMotion(diff, roiRect)
            }
        }
        
        previousFrame = blurred.clone()
        return false
    }
}
```

#### Calibration Process
1. User points camera at hoop
2. App displays rectangle overlay
3. User adjusts rectangle to encompass hoop
4. Save ROI coordinates
5. Use saved ROI for detection

### Performance Optimization Strategies

#### 1. Image Processing
- Reduce frame resolution for processing (while keeping preview quality)
- Process every 2nd or 3rd frame instead of every frame
- Use native OpenCV operations (faster than pure Kotlin)
- Release Mat objects promptly to avoid memory leaks

#### 2. Battery Optimization
- Use wake lock only when session is active
- Reduce camera frame rate when no motion detected
- Pause detection when app is backgrounded
- Optimize algorithm complexity

#### 3. Memory Management
- Limit frame buffer size
- Reuse Mat objects where possible
- Implement proper cleanup in onPause/onDestroy
- Monitor memory usage with Android Profiler

---

## Data Persistence Strategy

### Local Storage Only (MVP)
- All data stored locally using Room
- No cloud sync in MVP
- Export capability for backup

### Database Operations
```kotlin
// Repository pattern
interface SessionRepository {
    suspend fun createSession(): Session
    suspend fun updateSession(session: Session)
    suspend fun endSession(sessionId: Long)
    suspend fun getAllSessions(): Flow<List<Session>>
    suspend fun getSessionById(id: Long): Session?
    suspend fun deleteSession(id: Long)
}
```

### Data Migration
- Version 1: Initial schema
- Future versions: Use Room migrations

---

## Error Handling & Edge Cases

### Critical Scenarios
1. **Camera Permission Denied**
   - Show explanation dialog
   - Guide user to settings
   - Provide retry option

2. **No Camera Available**
   - Detect and show error message
   - Graceful degradation (manual mode)

3. **App Interrupted During Session**
   - Auto-save session state
   - Resume on return or offer to discard
   
4. **Low Storage**
   - Warn user before session
   - Implement data cleanup (old sessions)

5. **Crash During Session**
   - Implement crash recovery
   - Offer to restore last session

6. **Poor Lighting**
   - Detect and warn user
   - Suggest better positioning

---

## UI/UX Considerations

### Design Principles
1. **Camera First** - Camera view takes 80% of screen during session
2. **Minimal Distractions** - Simple, clean interface
3. **Immediate Feedback** - Visual/audio confirmation of shots
4. **Easy Correction** - Manual buttons always accessible
5. **Quick Access** - Single tap to start session from home

### Visual Design
- **Color Scheme:** Basketball orange accents, dark theme support
- **Typography:** Clear, readable fonts (Material Design 3)
- **Icons:** Material Icons for consistency
- **Animations:** Subtle, purposeful (shot confirmation)

### Accessibility
- Content descriptions for screen readers
- Sufficient touch target sizes (48dp minimum)
- High contrast text
- Haptic feedback for shot detection

---

## Risks & Mitigation Strategies

### Technical Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| Detection accuracy < 70% | High | Extensive testing, algorithm tuning, manual controls |
| OpenCV integration issues | Medium | Test early, have fallback to basic Android image processing |
| Battery drain excessive | Medium | Profile early, optimize processing, add warnings |
| Camera compatibility issues | High | Test on multiple devices, handle gracefully |
| Memory leaks with camera | High | Implement proper lifecycle management, profile regularly |

### Development Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| Timeline slips | Medium | Prioritize ruthlessly, cut nice-to-haves |
| Learning curve (OpenCV) | Low | Start with simple examples, iterate |
| Scope creep | Medium | Stick to PRD, defer features to Phase 2 |

---

## Success Criteria (MVP Launch)

### Must Have (Launch Blockers)
- [ ] App installs and launches successfully
- [ ] Camera displays and captures frames
- [ ] Shot detection works with 70%+ accuracy in daylight
- [ ] Manual adjustment buttons work correctly
- [ ] Sessions save to database reliably
- [ ] History screen displays past sessions
- [ ] App runs 30-minute session without crashing
- [ ] Works on at least 3 different Android devices
- [ ] APK size < 30MB

### Should Have
- [ ] Detection accuracy 80%+ in optimal conditions
- [ ] Battery usage < 25% per 30-minute session
- [ ] Session summary screen polished
- [ ] Smooth animations and transitions
- [ ] Proper error handling for common scenarios

### Nice to Have
- [ ] Works in moderate indoor lighting
- [ ] Landscape orientation support
- [ ] Haptic feedback
- [ ] Sound effects for shots
- [ ] Session notes feature

---

## Post-MVP Considerations

### Phase 2 Preparation
- Design database schema to support shot locations
- Consider architecture for video recording
- Plan for cloud sync infrastructure
- Research ML models for improved detection

### Metrics to Track
- Daily active users (if analytics added)
- Average session duration
- Detection accuracy reports from users
- Crash rate
- App size growth

### Technical Debt to Address
- Refactor detection algorithm based on learning
- Improve test coverage
- Optimize performance bottlenecks
- Add comprehensive logging

---

## Development Environment Setup

### Required Tools
1. **Android Studio** (latest stable)
2. **JDK 17** or newer
3. **Android SDK** (API 26-34)
4. **Git**

### Setup Steps
1. Clone repository
2. Open in Android Studio
3. Sync Gradle dependencies
4. Download OpenCV Android SDK (add to project)
5. Configure emulator or connect physical device
6. Run initial build to verify setup

### Device Requirements for Testing
- Minimum: Android 8.0 device with camera
- Recommended: Android 11+ device
- Ideal: Multiple devices with different screen sizes/cameras

---

## Build & Release Process

### Debug Builds
- Automatic via Android Studio
- Enable logging and debugging features
- Relaxed ProGuard rules

### Release Builds
1. Update version code/name in build.gradle
2. Run lint checks
3. Run all tests
4. Generate signed APK/Bundle
5. Test release build on devices
6. Tag release in Git

### Version Numbering
- Format: MAJOR.MINOR.PATCH (e.g., 1.0.0)
- MVP release: 1.0.0
- Increment PATCH for bug fixes
- Increment MINOR for new features

---

## Documentation Requirements

### Code Documentation
- KDoc comments for public APIs
- Inline comments for complex algorithms
- README.md with setup instructions

### User Documentation
- How to use the app (simple guide)
- Troubleshooting common issues
- Privacy policy (camera usage)

### Developer Documentation
- Architecture overview
- Database schema
- Detection algorithm explanation
- Testing guide

---

## Open Questions & Decisions Needed

### Pre-Implementation
1. **OpenCV vs Pure Android:** Confirm OpenCV is best choice vs Android ImageAnalysis
2. **Compose vs XML:** Finalize UI framework choice (Recommendation: Compose)
3. **Missed Shots:** Track missed shots in MVP or just makes? (Recommendation: Track both)
4. **Camera Position:** Support only portrait, or both orientations? (Recommendation: Portrait first)

### During Implementation
1. **Motion Threshold:** What sensitivity level works best?
2. **ROI Size:** How large should hoop region be?
3. **Detection Cooldown:** Minimum time between detected shots?
4. **Manual Mode:** Should there be fully manual mode without camera?

---

## Timeline Summary

### 10-Week MVP Schedule

**Weeks 1-2:** Foundation
- Project setup, architecture, dependencies

**Weeks 3-4:** Core Feature
- Camera integration, shot detection

**Weeks 5-6:** Data Layer
- Session management, database, persistence

**Weeks 7-8:** User Interface
- All screens, statistics, history

**Weeks 9-10:** Quality
- Testing, optimization, polish, release prep

### Key Milestones
- **End of Week 2:** App shell with navigation complete
- **End of Week 4:** Shot detection demo working
- **End of Week 6:** Full session flow functional
- **End of Week 8:** All MVP features implemented
- **End of Week 10:** MVP ready for release

---

## Appendix

### Useful Resources
- [CameraX Documentation](https://developer.android.com/training/camerax)
- [OpenCV Android Tutorial](https://opencv.org/android/)
- [Jetpack Compose Guides](https://developer.android.com/jetpack/compose)
- [Room Persistence Library](https://developer.android.com/training/data-storage/room)
- [Motion Detection Algorithms](https://docs.opencv.org/4.x/d7/df3/group__imgproc__motion.html)

### Dependencies (Preliminary)
```kotlin
// build.gradle.kts
dependencies {
    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    
    // CameraX
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")
    
    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    
    // Hilt
    implementation("com.google.dagger:hilt-android:2.50")
    ksp("com.google.dagger:hilt-compiler:2.50")
    
    // OpenCV (manual download and add)
    implementation(files("libs/opencv-4.9.0.aar"))
}
```

---

## Conclusion

This implementation plan provides a structured approach to building the Basketball Shot Tracker MVP. The plan prioritizes:

1. **Simplicity** - Start with motion detection, not complex ML
2. **Reliability** - Focus on stable core functionality
3. **Iteration** - Build, test, learn, improve
4. **User Value** - Deliver working shot tracking in 10 weeks

The phased approach allows for early testing and course correction while maintaining focus on the MVP scope defined in the PRD.

**Next Steps:**
1. Review and approve this implementation plan
2. Set up development environment
3. Begin Phase 1: Project setup
4. Schedule weekly progress reviews

---

**Plan Status:** Ready for Review  
**Last Updated:** February 15, 2026
