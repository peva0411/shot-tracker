package com.shottracker.media

import android.util.Log
import java.io.File
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CaptureRepository"

@Singleton
class CaptureRepository @Inject constructor(private val dao: CaptureDao) {

    fun getAllCaptures(): Flow<List<CaptureEntity>> = dao.getAllCaptures()

    suspend fun insertCapture(filePath: String, mimeType: String, type: CaptureType): Long {
        val entity = CaptureEntity(filePath = filePath, mimeType = mimeType, captureType = type)
        return dao.insert(entity).also { Log.d(TAG, "Inserted capture id=$it path=$filePath") }
    }

    suspend fun markUploading(id: Long) {
        dao.updateUploadStatus(id, UploadStatus.UPLOADING, null)
    }

    suspend fun markUploaded(id: Long, driveFileId: String) {
        dao.updateUploadStatus(id, UploadStatus.UPLOADED, driveFileId)
        Log.d(TAG, "Marked id=$id as uploaded driveFileId=$driveFileId")
    }

    suspend fun markFailed(id: Long) {
        dao.updateUploadStatus(id, UploadStatus.FAILED, null)
    }

    suspend fun deleteCapture(id: Long, filePath: String) {
        dao.deleteById(id)
        File(filePath).takeIf { it.exists() }?.delete()
        Log.d(TAG, "Deleted capture id=$id")
    }
}
