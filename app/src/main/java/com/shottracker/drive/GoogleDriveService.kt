package com.shottracker.drive

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GoogleDriveService"
private const val APP_NAME = "ShotTracker"
private const val TRAINING_FOLDER_NAME = "ShotTracker Training Data"

@Singleton
class GoogleDriveService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _isSignedIn = MutableStateFlow(false)
    val isSignedIn: StateFlow<Boolean> = _isSignedIn.asStateFlow()

    private val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(Scope(DriveScopes.DRIVE_FILE))
        .build()

    val signInClient: GoogleSignInClient = GoogleSignIn.getClient(context, signInOptions)

    init {
        // Restore sign-in state on startup
        val account = GoogleSignIn.getLastSignedInAccount(context)
        _isSignedIn.value = account != null && GoogleSignIn.hasPermissions(
            account, Scope(DriveScopes.DRIVE_FILE)
        )
    }

    /** Call this after a successful Google Sign-In result to confirm auth state. */
    fun onSignInSuccess(account: GoogleSignInAccount) {
        _isSignedIn.value = true
        Log.d(TAG, "Signed in as ${account.email}")
    }

    fun signOut() {
        signInClient.signOut()
        _isSignedIn.value = false
    }

    /**
     * Upload [file] to the "ShotTracker Training Data" folder on Google Drive.
     * Creates the folder if it does not exist.
     *
     * @return The Drive file ID on success, or throws on failure.
     */
    suspend fun uploadFile(file: File, mimeType: String): String = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getLastSignedInAccount(context)
            ?: error("Not signed in to Google")

        val credential = GoogleAccountCredential.usingOAuth2(
            context, listOf(DriveScopes.DRIVE_FILE)
        ).apply { selectedAccount = account.account }

        val driveService = Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName(APP_NAME).build()

        val folderId = getOrCreateFolder(driveService)

        val fileMetadata = DriveFile().apply {
            name = file.name
            parents = listOf(folderId)
        }
        val mediaContent = FileContent(mimeType, file)
        val uploaded = driveService.files().create(fileMetadata, mediaContent)
            .setFields("id")
            .execute()

        Log.d(TAG, "Uploaded ${file.name} → Drive id=${uploaded.id}")
        uploaded.id
    }

    private fun getOrCreateFolder(driveService: Drive): String {
        val query = "mimeType='application/vnd.google-apps.folder' and name='$TRAINING_FOLDER_NAME' and trashed=false"
        val result = driveService.files().list()
            .setQ(query)
            .setFields("files(id)")
            .execute()

        return if (result.files.isNotEmpty()) {
            result.files[0].id
        } else {
            val folderMetadata = DriveFile().apply {
                name = TRAINING_FOLDER_NAME
                mimeType = "application/vnd.google-apps.folder"
            }
            driveService.files().create(folderMetadata).setFields("id").execute().id
                .also { Log.d(TAG, "Created Drive folder id=$it") }
        }
    }
}
