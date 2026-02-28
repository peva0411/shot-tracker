# Data & Upload Pipeline — Sub-Architecture

> Part of: [Architecture Overview](./ARCHITECTURE.md)

## Overview

Captured training media (JPEG frames, MP4 videos) follows this pipeline:

```
CameraX capture
    │
    ▼
App-private storage (filesDir/training/)
    │
    ▼
Room DB (CaptureEntity — tracks metadata + upload status)
    │
    ▼
Library screen (grid view, upload controls)
    │
    ▼
Google Drive (ShotTracker Training Data folder)
```

---

## Package: `com.shottracker.media`

### Room Database

**`AppDatabase.kt`**
- `@Database` with entities: `[CaptureEntity]`, version 1
- Singleton pattern via `getInstance(context)`
- Database file: `shot_tracker.db`
- `@TypeConverters` for `CaptureType` and `UploadStatus` enums (stored as strings)

**`CaptureEntity.kt`**
```kotlin
@Entity(tableName = "captures")
data class CaptureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filePath: String,        // absolute path to JPEG/MP4
    val mimeType: String,        // "image/jpeg" or "video/mp4"
    val captureType: CaptureType,// FRAME or VIDEO
    val capturedAt: Long,        // epoch millis
    val uploadStatus: UploadStatus, // LOCAL, UPLOADING, UPLOADED, FAILED
    val driveFileId: String?     // Google Drive file ID when uploaded
)
```

**Enums:**
- `CaptureType`: `FRAME`, `VIDEO`
- `UploadStatus`: `LOCAL`, `UPLOADING`, `UPLOADED`, `FAILED`

**`CaptureDao.kt`**
| Query                       | Returns                       | Purpose                          |
|-----------------------------|-------------------------------|----------------------------------|
| `getAllCaptures()`          | `Flow<List<CaptureEntity>>`   | Reactive list, ordered by capturedAt DESC |
| `insert(capture)`          | `Long` (row ID)               | Insert new capture               |
| `updateUploadStatus(id, status, driveFileId)` | — | Update upload state   |
| `deleteById(id)`           | —                             | Remove from DB                   |

**`DatabaseModule.kt`** — Hilt `@Module` providing `AppDatabase` (@Singleton) and `CaptureDao`.

### CaptureRepository

`CaptureRepository.kt` (`@Singleton`, `@Inject`) — thin layer over `CaptureDao`:

| Method                               | Purpose                                          |
|--------------------------------------|--------------------------------------------------|
| `getAllCaptures(): Flow<List>`        | Passthrough to DAO                               |
| `insertCapture(path, mime, type)`    | Creates entity, inserts, returns ID              |
| `markUploading(id)`                  | Sets status = `UPLOADING`                        |
| `markUploaded(id, driveFileId)`      | Sets status = `UPLOADED`, stores Drive file ID   |
| `markFailed(id)`                     | Sets status = `FAILED`                           |
| `deleteCapture(id, filePath)`        | Deletes from DB **and** deletes the local file   |

---

## Package: `com.shottracker.drive`

### GoogleDriveService

`GoogleDriveService.kt` (`@Singleton`, `@Inject`) handles OAuth2 authentication and file uploads.

**Authentication:**
- Uses `GoogleSignIn` with `GoogleSignInOptions` requesting `email` + `drive.file` scope
- `drive.file` scope only allows access to files created by this app (not full Drive access)
- `signInClient: GoogleSignInClient` — exposed for the UI to launch the sign-in intent
- `isSignedIn: StateFlow<Boolean>` — reactive auth state; auto-restores on app startup from `GoogleSignIn.getLastSignedInAccount()`
- `onSignInSuccess(account)` — call after `ActivityResult` succeeds
- `signOut()` — clears auth

**Upload:**
```
uploadFile(file: File, mimeType: String): String  // returns Drive file ID
```
- Runs on `Dispatchers.IO`
- Creates a `Drive` service via `GoogleAccountCredential`
- Calls `getOrCreateFolder()` to find/create `"ShotTracker Training Data"` folder
- Uploads file into that folder
- Returns the Drive file ID

**DriveModule.kt** — Empty Hilt `@Module` placeholder. `GoogleDriveService` is resolved via its `@Inject constructor`.

### Google Cloud Setup Requirements
1. Enable **Google Drive API** in Cloud Console
2. Create **OAuth2 Client ID** (Android type) with:
   - Package name: `com.shottracker.debug` (debug) or `com.shottracker` (release)
   - SHA-1 from: `./gradlew signingReport`
3. Add your Google account as a **test user** in OAuth consent screen (while app is in test mode)

---

## Package: `com.shottracker.ui.library`

### LibraryViewModel

`LibraryViewModel.kt` (`@HiltViewModel`) — orchestrates the library screen:

| Property/Method     | Type / Return               | Purpose                                     |
|---------------------|-----------------------------|---------------------------------------------|
| `captures`          | `StateFlow<List<CaptureEntity>>` | All captures, reactive                 |
| `isSignedIn`        | `StateFlow<Boolean>`        | From GoogleDriveService                     |
| `driveService`      | `GoogleDriveService`        | Exposed for sign-in intent in UI            |
| `uploadCapture(c)`  | —                           | Mark uploading → upload → mark uploaded/failed |
| `uploadAll()`       | —                           | Uploads all non-uploaded captures           |
| `deleteCapture(c)`  | —                           | Deletes from DB + disk                      |
| `signOut()`         | —                           | Clears Google auth                          |

### LibraryScreen

`LibraryScreen.kt` — Compose screen with:

| UI Element              | Details                                              |
|-------------------------|------------------------------------------------------|
| Sign-in banner          | Shown when `isSignedIn == false`; launches Google Sign-In |
| Top bar actions         | "Upload All" (when signed in + has captures), "Sign Out" |
| Grid (`LazyVerticalGrid`)| 3 columns; each item is a `CaptureItem` card        |
| CaptureItem             | Thumbnail (Coil for JPEG, `MediaMetadataRetriever` for video), type badge (FRAME/VIDEO), upload status icon, upload button |
| Long-press              | Shows delete confirmation dialog                     |
| Empty state             | Message when no captures exist                       |

### Upload Status Flow

```
[LOCAL] ──tap upload──► [UPLOADING] ──success──► [UPLOADED]
                            │
                            └──failure──► [FAILED]
```

Failed uploads can be retried by tapping the upload button again.

---

## Data Flow: End-to-End Capture + Upload

```
1. User taps 📷 in SessionScreen
2. SessionViewModel.captureFrame()
3. TrainingCaptureController.captureFrame()
4. CameraX ImageCapture → saves JPEG to filesDir/training/
5. onSaved callback → CaptureRepository.insertCapture()
6. Room inserts CaptureEntity (status = LOCAL)
7. User navigates to Library screen
8. LibraryViewModel.captures emits updated list
9. User taps upload icon on item
10. LibraryViewModel.uploadCapture()
11. CaptureRepository.markUploading(id)
12. GoogleDriveService.uploadFile(file, mimeType)
13. Drive API creates file in "ShotTracker Training Data" folder
14. CaptureRepository.markUploaded(id, driveFileId)
15. UI updates to show ✓ icon
```
