package com.shottracker.media

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class CaptureType { FRAME, VIDEO }
enum class UploadStatus { LOCAL, UPLOADING, UPLOADED, FAILED }

@Entity(tableName = "captures")
data class CaptureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filePath: String,
    val mimeType: String,
    val captureType: CaptureType,
    val capturedAt: Long = System.currentTimeMillis(),
    val uploadStatus: UploadStatus = UploadStatus.LOCAL,
    val driveFileId: String? = null
)
