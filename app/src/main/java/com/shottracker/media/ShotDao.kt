package com.shottracker.media

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ShotDao {
    @Insert
    suspend fun insert(shot: ShotEntity): Long

    @Query("SELECT * FROM shots ORDER BY capturedAt DESC")
    fun getAllShots(): Flow<List<ShotEntity>>

    @Query("DELETE FROM shots WHERE id = :id")
    suspend fun deleteById(id: Long)
}
