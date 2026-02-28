package com.shottracker.media

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CaptureDao {

    @Query("SELECT * FROM captures ORDER BY capturedAt DESC")
    fun getAllCaptures(): Flow<List<CaptureEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(capture: CaptureEntity): Long

    @Query("UPDATE captures SET uploadStatus = :status, driveFileId = :driveFileId WHERE id = :id")
    suspend fun updateUploadStatus(id: Long, status: UploadStatus, driveFileId: String?)

    @Query("DELETE FROM captures WHERE id = :id")
    suspend fun deleteById(id: Long)
}
